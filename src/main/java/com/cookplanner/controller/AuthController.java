package com.cookplanner.controller;

import com.cookplanner.common.ApiResponse;
import com.cookplanner.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import com.cookplanner.dto.*;
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

    @PostMapping("/auth0")
    public ApiResponse<Auth0LoginResponse> auth0Login(@Valid @RequestBody Auth0LoginRequest request) {
        Auth0LoginResponse data = authService.auth0Login(request);
        return ApiResponse.success(data);
    }
}
