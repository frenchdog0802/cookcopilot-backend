package com.cookcopilot.controller;

import com.cookcopilot.common.ApiResponse;
import com.cookcopilot.dto.*;
import com.cookcopilot.entity.AIMessage;
import com.cookcopilot.service.*;
import com.cookcopilot.service.ai.CookingAssistant;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.tool.ToolExecution;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@Slf4j
public class ChatController {

    private static final long STREAM_TIMEOUT_MS = 300_000L;

    private static final List<String> FORBIDDEN_PATTERNS = List.of(
            "ignore previous", "system prompt", "act as", "jailbreak", "developer message", "openai policy");

    private static final List<String> AVAILABLE_TOOLS = List.of(
            "listMyRecipes",
            "getRecipeDetails",
            "createRecipe",
            "updateRecipe",
            "importRecipeFromUrl",
            "listMealPlans",
            "addRecipeToMenu",
            "planMeals",
            "updateMealPlan",
            "removeRecipeFromMenu",
            "clearMealPlans",
            "listPantry",
            "addPantryItems",
            "updatePantryItem",
            "removePantryItem",
            "organizePantry",
            "addItemsToShoppingList",
            "suggestMealsFromPantry",
            "getPreferences",
            "updatePreferences");

    private static final String GUARD_MESSAGE = "I can only help with cooking 😊";
    private static final String GENERIC_ERROR_MESSAGE = "Something went wrong. Please try again.";
    private static final String TOOL_JSON_ERROR_MESSAGE =
            "I couldn't finish that action (incomplete tool data). Please try again with fewer recipes or meals at once — for example create one recipe per request, then schedule meals in batches of 8.";
    private static final String TOOL_STATE_ERROR_MESSAGE =
            "That request got interrupted mid-action. Please try again (or start a new chat if it keeps failing).";

    private final CookingAssistant cookingAssistant;
    private final ChatHistoryService chatHistoryService;
    private final ChatMemorySeeder chatMemorySeeder;
    private final UserContext userContext;
    private final ToolResultCollector toolResultCollector;

    @PostMapping("/send")
    public ApiResponse<ChatSendResponse> send(Authentication auth, @Valid @RequestBody ChatSendRequest request) {
        UUID userId = (UUID) auth.getPrincipal();
        String userMessage = request.getMessage();

        String lower = userMessage.toLowerCase();
        for (String pattern : FORBIDDEN_PATTERNS) {
            if (lower.contains(pattern)) {
                return ApiResponse.success(new ChatSendResponse("error", GUARD_MESSAGE, Map.of()));
            }
        }

        userContext.setUserId(userId);
        toolResultCollector.begin(userId);
        String enrichedMessage = enrichMessage(userMessage, request.getRecipeContext());

        try {
            chatMemorySeeder.seedFromDatabase(userId);
            String aiResponse = cookingAssistant.chat(userId, enrichedMessage);

            chatHistoryService.saveUserMessage(userId, userMessage);
            chatHistoryService.saveAssistantMessage(userId, aiResponse, 0, 0);

            if (toolResultCollector.hasResult(userId)) {
                return ApiResponse.success(mapToolResult(userId, aiResponse));
            }
            return ApiResponse.success(new ChatSendResponse("text", aiResponse, Map.of()));
        } catch (Exception ex) {
            log.error("Chat request failed for user {}", userId, ex);
            chatMemorySeeder.clearMemory(userId);
            chatHistoryService.saveUserMessage(userId, userMessage);
            return ApiResponse.success(new ChatSendResponse("error", userFacingError(ex), Map.of()));
        } finally {
            toolResultCollector.end(userId);
            userContext.clear();
        }
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(Authentication auth, @Valid @RequestBody ChatSendRequest request) {
        UUID userId = (UUID) auth.getPrincipal();
        String userMessage = request.getMessage();
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        AtomicBoolean streamClosed = new AtomicBoolean(false);

        String lower = userMessage.toLowerCase();
        for (String pattern : FORBIDDEN_PATTERNS) {
            if (lower.contains(pattern)) {
                sendDoneEvent(emitter, streamClosed, new ChatSendResponse("error", GUARD_MESSAGE, Map.of()));
                return emitter;
            }
        }

        userContext.setUserId(userId);
        toolResultCollector.begin(userId);
        String enrichedMessage = enrichMessage(userMessage, request.getRecipeContext());
        chatHistoryService.saveUserMessage(userId, userMessage);

        try {
            chatMemorySeeder.seedFromDatabase(userId);
            StringBuilder fullText = new StringBuilder();
            TokenStream tokenStream = cookingAssistant.streamChat(userId, enrichedMessage);
            RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
            SecurityContext securityContext = SecurityContextHolder.getContext();

            tokenStream
                    .onPartialResponse(token -> runWithRequestContext(userId, requestAttributes, securityContext, () -> {
                        if (streamClosed.get()) {
                            return;
                        }
                        fullText.append(token);
                        if (!safeSend(emitter, streamClosed,
                                SseEmitter.event().name("token").data(token, MediaType.TEXT_PLAIN))) {
                            // Client gone or emitter already closed — keep collecting text for history.
                        }
                    }))
                    .onToolExecuted(toolExecution -> runWithRequestContext(userId, requestAttributes, securityContext,
                            () -> sendToolProgress(emitter, streamClosed, toolExecution)))
                    .onCompleteResponse(response -> runWithRequestContext(userId, requestAttributes, securityContext,
                            () -> handleStreamComplete(userId, emitter, streamClosed, fullText, response)))
                    .onError(error -> runWithRequestContext(userId, requestAttributes, securityContext,
                            () -> handleStreamError(userId, emitter, streamClosed, error)))
                    .start();
        } catch (Exception ex) {
            log.error("Chat stream failed to start for user {}", userId, ex);
            chatMemorySeeder.clearMemory(userId);
            toolResultCollector.end(userId);
            userContext.clear();
            sendErrorEvent(emitter, streamClosed, userFacingError(ex));
        }

        emitter.onTimeout(() -> {
            if (!streamClosed.compareAndSet(false, true)) {
                return;
            }
            chatMemorySeeder.clearMemory(userId);
            toolResultCollector.end(userId);
            userContext.clear();
            try {
                synchronized (emitter) {
                    emitter.send(SseEmitter.event().name("error")
                            .data(Map.of("message", "Request timed out. Please try again with a smaller request.")));
                    emitter.complete();
                }
            } catch (Exception ex) {
                if (!isEmitterClosed(ex)) {
                    log.warn("Failed to send timeout event for user {}", userId, ex);
                }
                quietlyComplete(emitter);
            }
        });
        emitter.onError(error -> {
            streamClosed.set(true);
            chatMemorySeeder.clearMemory(userId);
            toolResultCollector.end(userId);
            userContext.clear();
            if (!isEmitterClosed(error)) {
                log.warn("SSE connection error for user {}", userId, error);
            }
        });

        return emitter;
    }

    @GetMapping("/history")
    public ApiResponse<ChatHistoryResponse> history(Authentication auth) {
        UUID userId = (UUID) auth.getPrincipal();
        List<ChatHistoryMessageDto> messages = chatHistoryService.loadHistoryForUi(userId).stream()
                .map(this::toHistoryDto)
                .toList();
        return ApiResponse.success(ChatHistoryResponse.builder().messages(messages).build());
    }

    @DeleteMapping("/history")
    public ApiResponse<Map<String, Object>> clearHistory(Authentication auth) {
        UUID userId = (UUID) auth.getPrincipal();
        chatHistoryService.clearHistory(userId);
        chatMemorySeeder.clearMemory(userId);
        return ApiResponse.success(Map.of("cleared", true));
    }

    @GetMapping("/actions")
    public ApiResponse<ListActionsResponse> listActions() {
        return ApiResponse.success(new ListActionsResponse(AVAILABLE_TOOLS,
                "Available tools that can be triggered via chat"));
    }

    private void handleStreamComplete(
            UUID userId,
            SseEmitter emitter,
            AtomicBoolean streamClosed,
            StringBuilder fullText,
            ChatResponse response) {
        try {
            String aiResponse = fullText.toString();
            if (aiResponse.isBlank() && response != null && response.aiMessage() != null) {
                aiResponse = response.aiMessage().text();
            }
            if (aiResponse == null) {
                aiResponse = "";
            }

            // Persist even if the SSE client already disconnected.
            if (!aiResponse.isBlank()) {
                chatHistoryService.saveAssistantMessage(userId, aiResponse, 0, 0);
            }

            if (streamClosed.get()) {
                return;
            }

            ChatSendResponse result;
            if (toolResultCollector.hasResult(userId)) {
                result = mapToolResult(userId, aiResponse);
            } else {
                result = new ChatSendResponse("text", aiResponse, Map.of());
            }

            sendDoneEvent(emitter, streamClosed, result);
        } catch (Exception ex) {
            log.error("Chat stream completion failed for user {}", userId, ex);
            chatMemorySeeder.clearMemory(userId);
            sendErrorEvent(emitter, streamClosed, GENERIC_ERROR_MESSAGE);
        } finally {
            toolResultCollector.end(userId);
            userContext.clear();
        }
    }

    private void handleStreamError(UUID userId, SseEmitter emitter, AtomicBoolean streamClosed, Throwable error) {
        // LangChain4j may invoke onError repeatedly after the emitter is already closed;
        // only the first failure should clear memory and notify the client.
        if (!streamClosed.compareAndSet(false, true)) {
            return;
        }

        // Late tokens after a successful/closed SSE must not wipe chat memory mid-tool-chain.
        if (isEmitterClosed(error)) {
            toolResultCollector.end(userId);
            userContext.clear();
            quietlyComplete(emitter);
            return;
        }

        log.error("Chat stream failed for user {}", userId, error);

        try {
            chatMemorySeeder.clearMemory(userId);
            try {
                synchronized (emitter) {
                    emitter.send(SseEmitter.event().name("error").data(Map.of("message", userFacingError(error))));
                    emitter.complete();
                }
            } catch (Exception sendEx) {
                if (!isEmitterClosed(sendEx)) {
                    log.warn("Failed to send error event for user {}", userId, sendEx);
                }
                quietlyComplete(emitter);
            }
        } finally {
            toolResultCollector.end(userId);
            userContext.clear();
        }
    }

    private void sendToolProgress(SseEmitter emitter, AtomicBoolean streamClosed, ToolExecution toolExecution) {
        if (streamClosed.get() || toolExecution == null || toolExecution.request() == null) {
            return;
        }
        String toolName = toolExecution.request().name();
        if (toolName == null || toolName.isBlank()) {
            return;
        }
        safeSend(emitter, streamClosed, SseEmitter.event()
                .name("status")
                .data(Map.of(
                        "tool", toolName,
                        "message", statusMessageForTool(toolName))));
    }

    private static String statusMessageForTool(String toolName) {
        return switch (toolName) {
            case "listMealPlans" -> "Checking your meal plan…";
            case "clearMealPlans" -> "Clearing scheduled meals…";
            case "removeRecipeFromMenu" -> "Removing a meal…";
            case "planMeals", "addRecipeToMenu" -> "Scheduling meals…";
            case "updateMealPlan" -> "Updating meal plan…";
            case "listMyRecipes", "getRecipeDetails" -> "Looking up recipes…";
            case "createRecipe", "updateRecipe", "importRecipeFromUrl" -> "Working on recipes…";
            case "listPantry", "addPantryItems", "updatePantryItem", "removePantryItem", "organizePantry" ->
                    "Updating pantry…";
            case "addItemsToShoppingList" -> "Updating shopping list…";
            case "suggestMealsFromPantry" -> "Suggesting meals…";
            case "getPreferences", "updatePreferences" -> "Updating preferences…";
            default -> "Working on it…";
        };
    }

    /**
     * Thread-safe SSE send. Returns false if the stream is already closed or the send failed
     * because the emitter completed — callers should keep going without treating it as a fatal LLM error.
     */
    private boolean safeSend(SseEmitter emitter, AtomicBoolean streamClosed, SseEmitter.SseEventBuilder event) {
        if (streamClosed.get()) {
            return false;
        }
        try {
            synchronized (emitter) {
                if (streamClosed.get()) {
                    return false;
                }
                emitter.send(event);
            }
            return true;
        } catch (IOException | IllegalStateException ex) {
            streamClosed.set(true);
            return false;
        } catch (Exception ex) {
            if (isEmitterClosed(ex)) {
                streamClosed.set(true);
                return false;
            }
            log.warn("Failed to send SSE event", ex);
            streamClosed.set(true);
            return false;
        }
    }

    private static boolean isEmitterClosed(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof IllegalStateException
                    && current.getMessage() != null
                    && current.getMessage().contains("already completed")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static void quietlyComplete(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (Exception ignored) {
            // already completed
        }
    }

    private static String userFacingError(Throwable error) {
        if (isEmitterClosed(error)) {
            return GENERIC_ERROR_MESSAGE;
        }
        Throwable current = error;
        while (current != null) {
            String name = current.getClass().getName();
            String message = current.getMessage() != null ? current.getMessage() : "";
            if (name.contains("JsonEOFException")
                    || name.contains("JsonParseException")
                    || message.contains("Unexpected end-of-input")
                    || message.contains("argumentsAsMap")) {
                return TOOL_JSON_ERROR_MESSAGE;
            }
            if (name.contains("InvalidRequestException")
                    || message.contains("role 'tool'")
                    || message.contains("tool_calls")) {
                return TOOL_STATE_ERROR_MESSAGE;
            }
            current = current.getCause();
        }
        return GENERIC_ERROR_MESSAGE;
    }

    private void runWithRequestContext(
            UUID userId,
            RequestAttributes requestAttributes,
            SecurityContext securityContext,
            Runnable action) {
        SecurityContext previousContext = SecurityContextHolder.getContext();
        RequestAttributes previousAttributes = RequestContextHolder.getRequestAttributes();
        try {
            if (requestAttributes != null) {
                RequestContextHolder.setRequestAttributes(requestAttributes);
            }
            SecurityContextHolder.setContext(securityContext);
            userContext.setUserId(userId);
            action.run();
        } finally {
            SecurityContextHolder.setContext(previousContext);
            if (previousAttributes != null) {
                RequestContextHolder.setRequestAttributes(previousAttributes);
            } else {
                RequestContextHolder.resetRequestAttributes();
            }
        }
    }

    private void sendDoneEvent(SseEmitter emitter, AtomicBoolean streamClosed, ChatSendResponse payload) {
        if (!streamClosed.compareAndSet(false, true)) {
            return;
        }
        try {
            synchronized (emitter) {
                emitter.send(SseEmitter.event().name("done").data(payload));
                emitter.complete();
            }
        } catch (Exception ex) {
            if (!isEmitterClosed(ex)) {
                log.warn("Failed to send done event", ex);
            }
            quietlyComplete(emitter);
        }
    }

    private void sendErrorEvent(SseEmitter emitter, AtomicBoolean streamClosed, String message) {
        if (!streamClosed.compareAndSet(false, true)) {
            return;
        }
        try {
            synchronized (emitter) {
                emitter.send(SseEmitter.event().name("error").data(Map.of("message", message)));
                emitter.complete();
            }
        } catch (Exception ex) {
            if (!isEmitterClosed(ex)) {
                log.warn("Failed to send error event", ex);
            }
            quietlyComplete(emitter);
        }
    }

    private String enrichMessage(String userMessage, Map<String, Object> recipeContext) {
        StringBuilder enriched = new StringBuilder();
        enriched.append("[Today's date: ").append(java.time.LocalDate.now()).append("]\n");
        if (recipeContext != null) {
            Object recipeId = recipeContext.get("recipeId");
            Object recipeName = recipeContext.get("recipeName");
            enriched.append("[Context: User is viewing recipe with ID: ").append(recipeId)
                    .append(", name: \"").append(recipeName).append("\"]\n");
        }
        enriched.append('\n').append(userMessage);
        return enriched.toString();
    }

    private ChatSendResponse mapToolResult(UUID userId, String aiText) {
        String responseType = toolResultCollector.primaryResponseType(userId);
        Map<String, Object> data = toolResultCollector.toAggregatedData(userId);

        if ("multi_action".equals(responseType)) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("actions", data.get("actions"));
            payload.put("actionCount", data.get("actionCount"));
            return new ChatSendResponse("multi_action", aiText, payload);
        }

        return new ChatSendResponse(responseType, aiText, data);
    }

    private ChatHistoryMessageDto toHistoryDto(AIMessage message) {
        return ChatHistoryMessageDto.builder()
                .id(message.getId())
                .role(message.getRole())
                .content(message.getContent())
                .createdAt(message.getCreatedAt())
                .build();
    }
}
