package com.lardermind.service;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Removes incomplete or orphaned tool-call sequences from chat memory.
 * OpenAI rejects histories where an assistant {@code tool_calls} message is not
 * followed by a tool result for every {@code tool_call_id}.
 *
 * <p>Trailing in-flight tool blocks (AiMessage with tool requests at the end,
 * results still being collected) are preserved so tool execution can finish.
 */
public final class ToolCallMessageSanitizer {

    private ToolCallMessageSanitizer() {
    }

    public static List<ChatMessage> sanitize(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return messages == null ? List.of() : messages;
        }

        List<ChatMessage> cleaned = new ArrayList<>(messages.size());
        int i = 0;
        while (i < messages.size()) {
            ChatMessage message = messages.get(i);

            if (message instanceof ToolExecutionResultMessage) {
                // Orphan tool result — drop.
                i++;
                continue;
            }

            if (message instanceof AiMessage ai && ai.hasToolExecutionRequests()) {
                List<ToolExecutionRequest> requests = ai.toolExecutionRequests();
                Set<String> expectedIds = new HashSet<>();
                boolean hasIds = false;
                for (ToolExecutionRequest request : requests) {
                    if (request.id() != null && !request.id().isBlank()) {
                        expectedIds.add(request.id());
                        hasIds = true;
                    }
                }

                int resultStart = i + 1;
                int resultEnd = resultStart;
                Set<String> seenIds = new HashSet<>();
                while (resultEnd < messages.size()
                        && messages.get(resultEnd) instanceof ToolExecutionResultMessage result) {
                    if (result.id() != null && !result.id().isBlank()) {
                        seenIds.add(result.id());
                    }
                    resultEnd++;
                }

                int resultCount = resultEnd - resultStart;
                boolean complete;
                if (hasIds) {
                    complete = seenIds.containsAll(expectedIds);
                } else {
                    // No IDs available — require one result per request (legacy / contiguous).
                    complete = resultCount >= requests.size();
                }

                boolean inFlight = resultEnd >= messages.size();
                if (complete || inFlight) {
                    cleaned.add(ai);
                    for (int j = resultStart; j < resultEnd; j++) {
                        cleaned.add(messages.get(j));
                    }
                }
                // else: drop incomplete block that is already followed by other messages
                i = resultEnd;
                continue;
            }

            cleaned.add(message);
            i++;
        }

        return cleaned;
    }

    public static boolean needsSanitize(List<ChatMessage> messages) {
        List<ChatMessage> cleaned = sanitize(messages);
        return cleaned.size() != messages.size();
    }
}
