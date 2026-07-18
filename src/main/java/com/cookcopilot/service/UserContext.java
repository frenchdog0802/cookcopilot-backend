package com.cookcopilot.service;

import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Holds the current chat user for the calling thread.
 * Not request-scoped — SSE/LangChain4j callbacks run on worker threads after the HTTP
 * request attributes are already inactive.
 */
@Component
public class UserContext {

    private final ThreadLocal<UUID> userId = new ThreadLocal<>();

    public UUID getUserId() {
        return userId.get();
    }

    public void setUserId(UUID userId) {
        this.userId.set(userId);
    }

    public void clear() {
        userId.remove();
    }
}
