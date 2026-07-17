package com.cookplanner.controller;

import com.cookplanner.common.ApiResponse;
import com.cookplanner.dto.*;
import com.cookplanner.entity.AIMessage;
import com.cookplanner.service.*;
import com.cookplanner.service.ai.CookingAssistant;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.TokenStream;
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
            "listPantry",
            "addPantryItems",
            "updatePantryItem",
            "removePantryItem",
            "organizePantry",
            "addItemsToShoppingList",
            "suggestMealsFromPantry");

    private static final String GUARD_MESSAGE = "I can only help with cooking 😊";
    private static final String GENERIC_ERROR_MESSAGE = "Something went wrong. Please try again.";

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
        String enrichedMessage = enrichMessage(userMessage, request.getRecipeContext());

        try {
            chatMemorySeeder.seedFromDatabase(userId);
            String aiResponse = cookingAssistant.chat(userId, enrichedMessage);

            chatHistoryService.saveUserMessage(userId, userMessage);
            chatHistoryService.saveAssistantMessage(userId, aiResponse, 0, 0);

            if (toolResultCollector.hasResult()) {
                return ApiResponse.success(mapToolResult(toolResultCollector, aiResponse));
            }
            return ApiResponse.success(new ChatSendResponse("text", aiResponse, Map.of()));
        } catch (Exception ex) {
            log.error("Chat request failed for user {}", userId, ex);
            chatHistoryService.saveUserMessage(userId, userMessage);
            return ApiResponse.success(new ChatSendResponse("error", GENERIC_ERROR_MESSAGE, Map.of()));
        }
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(Authentication auth, @Valid @RequestBody ChatSendRequest request) {
        UUID userId = (UUID) auth.getPrincipal();
        String userMessage = request.getMessage();
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);

        String lower = userMessage.toLowerCase();
        for (String pattern : FORBIDDEN_PATTERNS) {
            if (lower.contains(pattern)) {
                sendDoneEvent(emitter, new ChatSendResponse("error", GUARD_MESSAGE, Map.of()));
                return emitter;
            }
        }

        userContext.setUserId(userId);
        String enrichedMessage = enrichMessage(userMessage, request.getRecipeContext());
        chatHistoryService.saveUserMessage(userId, userMessage);

        try {
            chatMemorySeeder.seedFromDatabase(userId);
            StringBuilder fullText = new StringBuilder();
            TokenStream tokenStream = cookingAssistant.streamChat(userId, enrichedMessage);
            RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
            SecurityContext securityContext = SecurityContextHolder.getContext();

            tokenStream
                    .onPartialResponse(token -> runWithRequestContext(requestAttributes, securityContext, () -> {
                        fullText.append(token);
                        try {
                            emitter.send(SseEmitter.event().name("token").data(token));
                        } catch (IOException ex) {
                            log.warn("Failed to send token event for user {}", userId, ex);
                            emitter.completeWithError(ex);
                        }
                    }))
                    .onCompleteResponse(response -> runWithRequestContext(requestAttributes, securityContext,
                            () -> handleStreamComplete(userId, emitter, fullText, response)))
                    .onError(error -> runWithRequestContext(requestAttributes, securityContext,
                            () -> handleStreamError(userId, emitter, error)))
                    .start();
        } catch (Exception ex) {
            log.error("Chat stream failed to start for user {}", userId, ex);
            sendErrorEvent(emitter, GENERIC_ERROR_MESSAGE);
        }

        emitter.onTimeout(emitter::complete);
        emitter.onError(error -> log.warn("SSE connection error for user {}", userId, error));

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

    @GetMapping("/actions")
    public ApiResponse<ListActionsResponse> listActions() {
        return ApiResponse.success(new ListActionsResponse(AVAILABLE_TOOLS,
                "Available tools that can be triggered via chat"));
    }

    private void handleStreamComplete(UUID userId, SseEmitter emitter, StringBuilder fullText, ChatResponse response) {
        try {
            String aiResponse = fullText.toString();
            if (aiResponse.isBlank() && response != null && response.aiMessage() != null) {
                aiResponse = response.aiMessage().text();
            }

            chatHistoryService.saveAssistantMessage(userId, aiResponse, 0, 0);

            ChatSendResponse result;
            if (toolResultCollector.hasResult()) {
                result = mapToolResult(toolResultCollector, aiResponse);
            } else {
                result = new ChatSendResponse("text", aiResponse, Map.of());
            }

            sendDoneEvent(emitter, result);
        } catch (Exception ex) {
            log.error("Chat stream completion failed for user {}", userId, ex);
            sendErrorEvent(emitter, GENERIC_ERROR_MESSAGE);
        }
    }

    private void handleStreamError(UUID userId, SseEmitter emitter, Throwable error) {
        log.error("Chat stream failed for user {}", userId, error);
        sendErrorEvent(emitter, GENERIC_ERROR_MESSAGE);
    }

    private void runWithRequestContext(
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

    private void sendDoneEvent(SseEmitter emitter, ChatSendResponse payload) {
        try {
            emitter.send(SseEmitter.event().name("done").data(payload));
            emitter.complete();
        } catch (IOException ex) {
            log.warn("Failed to send done event", ex);
            emitter.completeWithError(ex);
        }
    }

    private void sendErrorEvent(SseEmitter emitter, String message) {
        try {
            emitter.send(SseEmitter.event().name("error").data(Map.of("message", message)));
            emitter.complete();
        } catch (IOException ex) {
            log.warn("Failed to send error event", ex);
            emitter.completeWithError(ex);
        }
    }

    private String enrichMessage(String userMessage, Map<String, Object> recipeContext) {
        if (recipeContext == null) {
            return userMessage;
        }
        Object recipeId = recipeContext.get("recipeId");
        Object recipeName = recipeContext.get("recipeName");
        return "[Context: User is viewing recipe with ID: " + recipeId
                + ", name: \"" + recipeName + "\"]\n\n" + userMessage;
    }

    private ChatSendResponse mapToolResult(ToolResultCollector collector, String aiText) {
        String responseType = collector.primaryResponseType();
        Map<String, Object> data = collector.toAggregatedData();

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
