package com.lardermind.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.lardermind.config.JwtUtil;
import com.lardermind.common.GlobalExceptionHandler.*;
import com.lardermind.dto.*;
import com.lardermind.entity.User;
import com.lardermind.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Authentication service — email/password and Google OAuth.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;

    @Value("${app.google.client-id}")
    private String googleClientId;

    // ── Signup ──

    public SignupResponse signup(SignupRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("Email is taken");
        }

        String salt = makeSalt();
        String hashedPassword = encryptPassword(request.getPassword(), salt);

        User user = User.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .name(request.getFirstName() + " " + request.getLastName())
                .email(request.getEmail())
                .salt(salt)
                .hashedPassword(hashedPassword)
                .role("user")
                .build();

        user = userRepository.save(user);

        String token = jwtUtil.generateToken(user.getId());
        return SignupResponse.builder()
                .token(token)
                .user(toUserDto(user))
                .build();
    }

    // ── Signin ──

    public SigninResponse signin(SigninRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BadRequestException("User not found"));

        if (!authenticate(request.getPassword(), user.getSalt(), user.getHashedPassword())) {
            throw new BadRequestException("Email and password don't match.");
        }

        String token = jwtUtil.generateToken(user.getId());
        return SigninResponse.builder()
                .token(token)
                .user(toUserDto(user))
                .build();
    }

    // ── Google Login (GIS ID token — signature + audience verified) ──

    public GoogleLoginResponse googleLogin(GoogleLoginRequest request) {
        String raw = request.getToken();
        if (raw == null || raw.isBlank()) {
            throw new BadRequestException("Missing Google ID token");
        }
        String token = raw.trim();
        // Access tokens (ya29.*) are not JWTs — verifier.parse throws IllegalArgumentException.
        if (token.chars().filter(ch -> ch == '.').count() != 2) {
            throw new BadRequestException(
                    "Expected a Google ID token (JWT). Access tokens are not accepted.");
        }
        if (googleClientId == null || googleClientId.isBlank()) {
            log.error("app.google.client-id / GOOGLE_CLIENT_ID is not configured");
            throw new BadRequestException("Google login is not configured on the server");
        }

        try {
            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(
                    new NetHttpTransport(),
                    GsonFactory.getDefaultInstance())
                    .setAudience(Collections.singletonList(googleClientId))
                    .build();

            GoogleIdToken idToken = verifier.verify(token);
            if (idToken == null) {
                throw new BadRequestException("Invalid Google ID token");
            }

            GoogleIdToken.Payload payload = idToken.getPayload();

            if (!Boolean.TRUE.equals(payload.getEmailVerified())) {
                throw new BadRequestException("Google email is not verified");
            }

            String googleId = payload.getSubject();
            if (googleId == null || googleId.isBlank()) {
                throw new BadRequestException("Invalid Google token: missing user id");
            }

            String email = payload.getEmail();
            String name = (String) payload.getOrDefault("name", "User");
            String givenName = (String) payload.getOrDefault("given_name", "User");
            String familyName = (String) payload.getOrDefault("family_name", "");
            String picture = (String) payload.get("picture");

            return findOrCreateByGoogleId(googleId, email, name, givenName, familyName, picture);
        } catch (BadRequestException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            log.warn("Google ID token parse failed: {}", e.getMessage());
            throw new BadRequestException("Invalid Google ID token format");
        } catch (Exception e) {
            log.error("Google login error", e);
            throw new BadRequestException("Authentication failed: Invalid or expired Google ID token.");
        }
    }

    /**
     * Finds an existing user by Google ID or email, or creates a new one.
     */
    public GoogleLoginResponse findOrCreateByGoogleId(String googleId, String email,
                                                       String name, String firstName,
                                                       String lastName, String picture) {
        // 1. Try finding by googleId
        Optional<User> existingUser = userRepository.findByGoogleId(googleId);

        // 2. Try linking by email if no googleId match
        if (existingUser.isEmpty() && email != null) {
            existingUser = userRepository.findByEmail(email);
            if (existingUser.isPresent()) {
                User user = existingUser.get();
                user.setGoogleId(googleId);
                if (picture != null && user.getPicture() == null) {
                    user.setPicture(picture);
                }
                userRepository.save(user);
            }
        }

        if (existingUser.isPresent()) {
            User user = existingUser.get();
            String token = jwtUtil.generateToken(user.getId());
            return GoogleLoginResponse.builder()
                    .token(token)
                    .user(toUserDto(user))
                    .build();
        }

        // 3. Create new user
        String safeName = name != null ? name : (firstName != null ? firstName : "User");
        String safeFirst = firstName != null ? firstName : safeName.split(" ")[0];
        String safeLast = lastName != null ? lastName : "";

        String salt = makeSalt();
        User newUser = User.builder()
                .googleId(googleId)
                .email(email)
                .firstName(safeFirst)
                .lastName(safeLast)
                .name(safeName)
                .picture(picture)
                .salt(salt)
                .hashedPassword(encryptPassword(UUID.randomUUID().toString(), salt))
                .connectAccount("Google")
                .role("user")
                .build();

        newUser = userRepository.save(newUser);
        String token = jwtUtil.generateToken(newUser.getId());
        return GoogleLoginResponse.builder()
                .token(token)
                .user(toUserDto(newUser))
                .build();
    }

    // ── Helper: Convert User entity to UserDto ──

    private UserDto toUserDto(User user) {
        return UserDto.builder()
                .id(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .name(user.getName())
                .build();
    }

    // ── Password Helpers (mirrors Node.js crypto HMAC SHA-1) ──

    private String makeSalt() {
        return String.valueOf(Math.round(System.currentTimeMillis() * Math.random()));
    }

    private String encryptPassword(String password, String salt) {
        if (password == null || password.isEmpty()) return "";
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(salt.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            byte[] bytes = mac.doFinal(password.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            log.error("Error encrypting password", e);
            return "";
        }
    }

    private boolean authenticate(String plainText, String salt, String hashedPassword) {
        return encryptPassword(plainText, salt).equals(hashedPassword);
    }
}
