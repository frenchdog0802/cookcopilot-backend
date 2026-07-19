package com.lardermind.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lardermind.common.ApiResponse;
import com.lardermind.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import com.lardermind.dto.*;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthService authService;
    private final ObjectMapper objectMapper;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @PostMapping("/signup")
    public ApiResponse<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
        SignupResponse data = authService.signup(request);
        return ApiResponse.success(data);
    }

    @PostMapping("/signin")
    public ApiResponse<SigninResponse> signin(@Valid @RequestBody SigninRequest request) {
        SigninResponse data = authService.signin(request);
        return ApiResponse.success(data);
    }

    @GetMapping("/signout")
    public ApiResponse<SignoutResponse> signout() {
        return ApiResponse.success(new SignoutResponse("signed out"));
    }

    @PostMapping("/google-login")
    public ApiResponse<GoogleLoginResponse> googleLogin(@Valid @RequestBody GoogleLoginRequest request) {
        GoogleLoginResponse data = authService.googleLogin(request);
        return ApiResponse.success(data);
    }

    /**
     * Google Identity Services redirect UX posts the ID token here (mobile browsers).
     * We verify it, then send the user back to the SPA with a one-shot payload in the hash.
     */
    @PostMapping("/google-callback")
    public void googleCallback(@RequestParam("credential") String credential,
                               HttpServletResponse response) throws Exception {
        String base = frontendUrl.endsWith("/") ? frontendUrl.substring(0, frontendUrl.length() - 1) : frontendUrl;
        try {
            if (credential == null || credential.isBlank()) {
                throw new IllegalArgumentException("Missing credential from Google");
            }
            log.info("Google redirect callback received (credential length={})", credential.length());

            GoogleLoginResponse data = authService.googleLogin(
                    GoogleLoginRequest.builder().token(credential).build());

            String json = objectMapper.writeValueAsString(Map.of(
                    "token", data.getToken(),
                    "user", data.getUser()
            ));
            String encoded = Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(json.getBytes(StandardCharsets.UTF_8));

            response.sendRedirect(base + "/#google_auth=" + encoded);
        } catch (Exception e) {
            log.warn("Google redirect callback failed: {}", e.getMessage());
            String msg = URLEncoder.encode(
                    e.getMessage() != null ? e.getMessage() : "Google login failed",
                    StandardCharsets.UTF_8);
            response.sendRedirect(base + "/#google_auth_error=" + msg);
        }
    }
}
