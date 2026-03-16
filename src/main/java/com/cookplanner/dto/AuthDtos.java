package com.cookplanner.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

// ── Auth DTOs ──

@Data @NoArgsConstructor @AllArgsConstructor @Builder
class SignupRequest {
    private String first_name;
    private String last_name;
    private String name;
    private String email;
    private String password;
}

@Data @NoArgsConstructor @AllArgsConstructor @Builder
class SigninRequest {
    private String email;
    private String password;
}

@Data @NoArgsConstructor @AllArgsConstructor @Builder
class GoogleLoginRequest {
    private String token;
}

@Data @NoArgsConstructor @AllArgsConstructor @Builder
class Auth0LoginRequest {
    private String idToken;
    private String accessToken;
}

@Data @NoArgsConstructor @AllArgsConstructor @Builder
class AuthResponseData {
    private String token;
    private String message;
    private UserInfo user;

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class UserInfo {
        private UUID id;
        private String email;
        private String name;
    }
}
