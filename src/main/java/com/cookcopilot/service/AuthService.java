package com.cookcopilot.service;

import com.cookcopilot.config.JwtUtil;
import com.cookcopilot.common.GlobalExceptionHandler.*;
import com.cookcopilot.dto.*;
import com.cookcopilot.entity.User;
import com.cookcopilot.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Authentication service — mirrors services/auth.service.js
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;

    @Value("${app.auth0.domain}")
    private String auth0Domain;

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

    // ── Google Login (Google Identity Services access token) ──

    public GoogleLoginResponse googleLogin(GoogleLoginRequest request) {
        try {
            RestTemplate restTemplate = new RestTemplate();

            @SuppressWarnings("unchecked")
            Map<String, Object> tokenInfo = restTemplate.getForObject(
                    "https://www.googleapis.com/oauth2/v3/tokeninfo?access_token=" + request.getToken(),
                    Map.class);

            if (tokenInfo == null) throw new BadRequestException("Invalid Google token");

            @SuppressWarnings("unchecked")
            Map<String, Object> userInfo = restTemplate.getForObject(
                    "https://www.googleapis.com/oauth2/v3/userinfo?access_token=" + request.getToken(),
                    Map.class);

            if (userInfo == null) throw new BadRequestException("Failed to fetch Google user info");

            String googleId = (String) userInfo.get("sub");
            if (googleId == null) {
                googleId = (String) tokenInfo.get("sub");
            }
            if (googleId == null) throw new BadRequestException("Invalid Google token: missing user id");

            return findOrCreateByGoogleId(
                    googleId,
                    (String) userInfo.get("email"),
                    (String) userInfo.getOrDefault("name", "User"),
                    (String) userInfo.getOrDefault("given_name", "User"),
                    (String) userInfo.getOrDefault("family_name", ""),
                    (String) userInfo.get("picture"));
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.error("Google login error", e);
            throw new BadRequestException("Authentication failed: Invalid or expired Google Access Token.");
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

    // ── Auth0 Login ──

    public Auth0LoginResponse auth0Login(Auth0LoginRequest request) {
        try {
            // Decode JWT payload
            String[] parts = request.getIdToken().split("\\.");
            if (parts.length != 3) throw new BadRequestException("Invalid JWT format");

            String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = new com.fasterxml.jackson.databind.ObjectMapper().readValue(payloadJson, Map.class);

            // Verify issuer
            String expectedIssuer = "https://" + auth0Domain + "/";
            if (!expectedIssuer.equals(payload.get("iss"))) {
                throw new BadRequestException("Invalid token issuer");
            }

            // Check expiry
            long exp = payload.get("exp") instanceof Number ? ((Number) payload.get("exp")).longValue() : 0;
            if (exp > 0 && exp < System.currentTimeMillis() / 1000) {
                throw new BadRequestException("Token has expired");
            }

            String auth0Id = (String) payload.get("sub");
            String email = (String) payload.get("email");
            String name = (String) payload.getOrDefault("name",
                    payload.getOrDefault("nickname", email != null ? email.split("@")[0] : "User"));
            String picture = (String) payload.get("picture");

            // Try auth0Id
            Optional<User> existingUser = userRepository.findByAuth0Id(auth0Id);

            // Try email
            if (existingUser.isEmpty() && email != null) {
                existingUser = userRepository.findByEmail(email);
                if (existingUser.isPresent()) {
                    User user = existingUser.get();
                    user.setAuth0Id(auth0Id);
                    if (picture != null && user.getPicture() == null) user.setPicture(picture);
                    userRepository.save(user);
                }
            }

            if (existingUser.isPresent()) {
                User user = existingUser.get();
                String token = jwtUtil.generateToken(user.getId());
                return Auth0LoginResponse.builder()
                        .token(token)
                        .user(toUserDto(user))
                        .build();
            }

            // Create new user
            String[] nameParts = name.split(" ");
            String firstName = nameParts[0];
            String lastName = nameParts.length > 1 ? String.join(" ", Arrays.copyOfRange(nameParts, 1, nameParts.length)) : firstName;

            String salt = makeSalt();
            User newUser = User.builder()
                    .auth0Id(auth0Id)
                    .email(email)
                    .firstName(firstName)
                    .lastName(lastName)
                    .name(name)
                    .picture(picture)
                    .salt(salt)
                    .hashedPassword(encryptPassword(UUID.randomUUID().toString(), salt))
                    .connectAccount("Auth0")
                    .role("user")
                    .build();

            newUser = userRepository.save(newUser);
            String token = jwtUtil.generateToken(newUser.getId());
            return Auth0LoginResponse.builder()
                    .token(token)
                    .user(toUserDto(newUser))
                    .build();
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.error("Auth0 login error", e);
            throw new BadRequestException("Authentication failed: Invalid Auth0 token.");
        }
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
