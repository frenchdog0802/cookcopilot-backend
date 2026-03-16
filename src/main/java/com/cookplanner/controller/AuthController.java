package com.cookplanner.controller;

import com.cookplanner.common.ApiResponse;
import com.cookplanner.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<Map<String, Object>>> signup(@RequestBody Map<String, String> body) {
        Map<String, Object> data = authService.signup(
                body.get("first_name"), body.get("last_name"),
                body.get("email"), body.get("password"));
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    @PostMapping("/signin")
    public ResponseEntity<ApiResponse<Map<String, Object>>> signin(@RequestBody Map<String, String> body) {
        Map<String, Object> data = authService.signin(body.get("email"), body.get("password"));
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    @GetMapping("/signout")
    public ResponseEntity<ApiResponse<Map<String, String>>> signout() {
        return ResponseEntity.ok(ApiResponse.success(Map.of("message", "signed out")));
    }

    @PostMapping("/google-login")
    public ResponseEntity<ApiResponse<Map<String, Object>>> googleLogin(@RequestBody Map<String, String> body) {
        Map<String, Object> data = authService.googleLogin(body.get("token"));
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    @PostMapping("/auth0")
    public ResponseEntity<ApiResponse<Map<String, Object>>> auth0Login(@RequestBody Map<String, String> body) {
        Map<String, Object> data = authService.auth0Login(body.get("idToken"));
        return ResponseEntity.ok(ApiResponse.success(data));
    }
}
