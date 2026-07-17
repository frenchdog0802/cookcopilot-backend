package com.cookplanner.service;

import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

import java.util.UUID;

@Component
@RequestScope
public class UserContext {

    private UUID userId;

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }
}
