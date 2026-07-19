package com.lardermind.service;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class ChatMemorySeeder {

    private final ChatHistoryService chatHistoryService;
    private final Map<Object, ChatMemory> memoryCache = new ConcurrentHashMap<>();

    public ChatMemory getOrCreateMemory(Object memoryId) {
        return memoryCache.computeIfAbsent(memoryId, id -> new SanitizingChatMemory(
                MessageWindowChatMemory.builder()
                        .id(id)
                        .maxMessages(ChatLimits.AI_CONTEXT_MESSAGE_LIMIT)
                        .build()));
    }

    /**
     * Reloads short-term memory from persisted user/assistant text.
     *
     * @param excludeLatestSavedUserMessage when true, drops the newest DB row so
     *                                      LangChain4j can add the enriched user
     *                                      message for this turn (stream path saves
     *                                      the raw user message before seeding).
     */
    public void seedFromDatabase(UUID userId, boolean excludeLatestSavedUserMessage) {
        ChatMemory memory = getOrCreateMemory(userId);
        memory.clear();
        List<ChatMessage> history = chatHistoryService.loadRecent(userId, ChatLimits.AI_CONTEXT_SEED_LIMIT);
        if (excludeLatestSavedUserMessage && !history.isEmpty()) {
            history = history.subList(0, history.size() - 1);
        }
        history.forEach(memory::add);
    }

    public void seedFromDatabase(UUID userId) {
        seedFromDatabase(userId, false);
    }

    public void clearMemory(UUID userId) {
        ChatMemory memory = memoryCache.get(userId);
        if (memory != null) {
            memory.clear();
        }
        memoryCache.remove(userId);
    }
}
