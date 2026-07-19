package com.lardermind.service;

import com.lardermind.entity.AIMessage;
import com.lardermind.repository.AIMessageRepository;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatHistoryServiceTest {

    @Mock
    private AIMessageRepository aiMessageRepository;

    @InjectMocks
    private ChatHistoryService chatHistoryService;

    @Test
    void loadRecent_returnsAscendingOrder() {
        UUID userId = UUID.randomUUID();
        List<AIMessage> descending = new ArrayList<>();
        for (int index = 20; index >= 1; index--) {
            descending.add(AIMessage.builder()
                    .userId(userId)
                    .role(index % 2 == 0 ? "assistant" : "user")
                    .content("message-" + index)
                    .createdAt((long) index)
                    .build());
        }
        when(aiMessageRepository.findTop20ByUserIdOrderByCreatedAtDesc(userId)).thenReturn(descending);

        List<ChatMessage> messages = chatHistoryService.loadRecent(userId, ChatLimits.AI_CONTEXT_SEED_LIMIT);

        assertEquals(16, messages.size());
        assertInstanceOf(UserMessage.class, messages.get(0));
        assertEquals("message-5", ((UserMessage) messages.get(0)).singleText());
    }

    @Test
    void saveUserAndAssistantMessages_persistExpectedRoles() {
        UUID userId = UUID.randomUUID();

        chatHistoryService.saveUserMessage(userId, "hello");
        chatHistoryService.saveAssistantMessage(userId, "hi there", 0, 0);

        ArgumentCaptor<AIMessage> captor = ArgumentCaptor.forClass(AIMessage.class);
        verify(aiMessageRepository, times(2)).save(captor.capture());

        List<AIMessage> saved = captor.getAllValues();
        assertEquals("user", saved.get(0).getRole());
        assertEquals("hello", saved.get(0).getContent());
        assertEquals("assistant", saved.get(1).getRole());
        assertEquals("hi there", saved.get(1).getContent());
    }

    @Test
    void loadRecent_emptyHistoryReturnsEmptyList() {
        UUID userId = UUID.randomUUID();
        when(aiMessageRepository.findTop20ByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of());

        List<ChatMessage> messages = chatHistoryService.loadRecent(userId, ChatLimits.AI_CONTEXT_SEED_LIMIT);

        assertTrue(messages.isEmpty());
        verify(aiMessageRepository, never()).save(any());
    }
}
