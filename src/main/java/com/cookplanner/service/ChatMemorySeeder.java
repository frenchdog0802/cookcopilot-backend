package com.cookplanner.service;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class ChatMemorySeeder {

    private final ChatHistoryService chatHistoryService;
    private final Map<Object, ChatMemory> memoryCache = new ConcurrentHashMap<>();

    public ChatMemory getOrCreateMemory(Object memoryId) {
        return memoryCache.computeIfAbsent(memoryId, id -> MessageWindowChatMemory.builder()
                .id(id)
                .maxMessages(ChatLimits.AI_CONTEXT_MESSAGE_LIMIT)
                .build());
    }

    public void seedFromDatabase(UUID userId) {
        ChatMemory memory = getOrCreateMemory(userId);
        memory.clear();
        chatHistoryService.loadRecent(userId, ChatLimits.AI_CONTEXT_MESSAGE_LIMIT)
                .forEach(memory::add);
    }
}
