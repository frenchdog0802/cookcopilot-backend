package com.lardermind.service;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.memory.ChatMemory;

import java.util.ArrayList;
import java.util.List;

/**
 * Decorates a {@link ChatMemory} so incomplete tool-call sequences are repaired
 * before they are sent to the LLM. In-flight tool blocks are left alone while
 * {@link ToolExecutionResultMessage}s are still being appended.
 */
public final class SanitizingChatMemory implements ChatMemory {

    private final ChatMemory delegate;

    public SanitizingChatMemory(ChatMemory delegate) {
        this.delegate = delegate;
    }

    @Override
    public Object id() {
        return delegate.id();
    }

    @Override
    public void add(ChatMessage message) {
        if (!(message instanceof ToolExecutionResultMessage)) {
            repairStoredMessages();
        }
        delegate.add(message);
    }

    @Override
    public List<ChatMessage> messages() {
        repairStoredMessages();
        return delegate.messages();
    }

    @Override
    public void clear() {
        delegate.clear();
    }

    private void repairStoredMessages() {
        List<ChatMessage> current = new ArrayList<>(delegate.messages());
        List<ChatMessage> cleaned = ToolCallMessageSanitizer.sanitize(current);
        if (cleaned.size() == current.size()) {
            return;
        }
        delegate.clear();
        for (ChatMessage message : cleaned) {
            delegate.add(message);
        }
    }
}
