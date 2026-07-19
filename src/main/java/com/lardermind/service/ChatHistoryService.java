package com.lardermind.service;

import com.lardermind.entity.AIMessage;
import com.lardermind.repository.AIMessageRepository;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChatHistoryService {

    private final AIMessageRepository aiMessageRepository;

    public List<ChatMessage> loadRecent(UUID userId, int limit) {
        List<AIMessage> rows = loadRowsDescending(userId, limit);
        List<ChatMessage> messages = new ArrayList<>();
        for (AIMessage row : rows) {
            ChatMessage message = toChatMessage(row);
            if (message != null) {
                messages.add(message);
            }
        }
        return messages;
    }

    public List<AIMessage> loadHistoryForUi(UUID userId) {
        return loadRowsDescending(userId, ChatLimits.UI_HISTORY_MESSAGE_LIMIT);
    }

    private List<AIMessage> loadRowsDescending(UUID userId, int limit) {
        List<AIMessage> descending = limit > ChatLimits.AI_CONTEXT_SEED_LIMIT
                ? aiMessageRepository.findTop50ByUserIdOrderByCreatedAtDesc(userId)
                : aiMessageRepository.findTop20ByUserIdOrderByCreatedAtDesc(userId);
        if (descending.isEmpty()) {
            return List.of();
        }
        List<AIMessage> ascending = new ArrayList<>(descending);
        Collections.reverse(ascending);
        if (ascending.size() > limit) {
            ascending = new ArrayList<>(ascending.subList(ascending.size() - limit, ascending.size()));
        }
        return ascending;
    }

    public void saveUserMessage(UUID userId, String content) {
        aiMessageRepository.save(AIMessage.builder()
                .userId(userId)
                .role("user")
                .content(content)
                .build());
    }

    public void saveAssistantMessage(UUID userId, String content, int tokenIn, int tokenOut) {
        aiMessageRepository.save(AIMessage.builder()
                .userId(userId)
                .role("assistant")
                .content(content)
                .tokenIn(tokenIn)
                .tokenOut(tokenOut)
                .build());
    }

    @Transactional
    public void clearHistory(UUID userId) {
        aiMessageRepository.deleteByUserId(userId);
    }

    private ChatMessage toChatMessage(AIMessage row) {
        return switch (row.getRole()) {
            case "user" -> UserMessage.from(row.getContent());
            case "assistant" -> AiMessage.from(row.getContent());
            default -> null;
        };
    }
}
