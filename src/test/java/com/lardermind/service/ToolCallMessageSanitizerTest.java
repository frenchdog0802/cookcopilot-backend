package com.lardermind.service;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ToolCallMessageSanitizerTest {

    @Test
    void keepsCompleteToolBlocks() {
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .id("call_1")
                .name("listPantry")
                .arguments("{}")
                .build();
        List<ChatMessage> messages = List.of(
                UserMessage.from("what do I have?"),
                AiMessage.from(List.of(request)),
                ToolExecutionResultMessage.from("call_1", "listPantry", "eggs"),
                AiMessage.from("You have eggs."));

        List<ChatMessage> cleaned = ToolCallMessageSanitizer.sanitize(messages);

        assertEquals(4, cleaned.size());
        assertEquals(messages, cleaned);
    }

    @Test
    void dropsIncompleteToolBlockFollowedByUserMessage() {
        ToolExecutionRequest a = ToolExecutionRequest.builder()
                .id("call_a")
                .name("listPantry")
                .arguments("{}")
                .build();
        ToolExecutionRequest b = ToolExecutionRequest.builder()
                .id("call_b")
                .name("listMyRecipes")
                .arguments("{}")
                .build();
        List<ChatMessage> messages = List.of(
                UserMessage.from("plan dinner"),
                AiMessage.from(List.of(a, b)),
                ToolExecutionResultMessage.from("call_a", "listPantry", "eggs"),
                // missing call_b result
                UserMessage.from("try again"));

        List<ChatMessage> cleaned = ToolCallMessageSanitizer.sanitize(messages);

        assertEquals(2, cleaned.size());
        assertInstanceOf(UserMessage.class, cleaned.get(0));
        assertInstanceOf(UserMessage.class, cleaned.get(1));
        assertEquals("try again", ((UserMessage) cleaned.get(1)).singleText());
    }

    @Test
    void preservesInFlightToolBlockAtEnd() {
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .id("call_1")
                .name("createRecipe")
                .arguments("{\"name\":\"Soup\"}")
                .build();
        List<ChatMessage> messages = List.of(
                UserMessage.from("make soup"),
                AiMessage.from(List.of(request)));

        List<ChatMessage> cleaned = ToolCallMessageSanitizer.sanitize(messages);

        assertEquals(2, cleaned.size());
        assertTrue(((AiMessage) cleaned.get(1)).hasToolExecutionRequests());
    }

    @Test
    void dropsOrphanToolResults() {
        List<ChatMessage> messages = List.of(
                ToolExecutionResultMessage.from("call_x", "listPantry", "eggs"),
                UserMessage.from("hello"));

        List<ChatMessage> cleaned = ToolCallMessageSanitizer.sanitize(messages);

        assertEquals(1, cleaned.size());
        assertInstanceOf(UserMessage.class, cleaned.get(0));
    }
}
