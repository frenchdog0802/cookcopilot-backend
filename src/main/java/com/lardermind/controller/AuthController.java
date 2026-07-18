package com.lardermind.controller;

import com.lardermind.common.ApiResponse;
import com.lardermind.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import com.lardermind.dto.*;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

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
}
