package com.cookplanner.service;

import com.cookplanner.config.JwtUtil;
import com.cookplanner.common.GlobalExceptionHandler.*;
import com.cookplanner.entity.User;
import com.cookplanner.repository.UserRepository;
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

    public Map<String, Object> signup(String firstName, String lastName,  String email, String password) {
        if (userRepository.existsByEmail(email)) {
            throw new BadRequestException("Email is taken");
        }

        String salt = makeSalt();
        String hashedPassword = encryptPassword(password, salt);

        User user = User.builder()
                .firstName(firstName)
                .lastName(lastName)
                .name(firstName + " " + lastName)
                .email(email)
                .salt(salt)
                .hashedPassword(hashedPassword)
                .role("user")
                .build();

        user = userRepository.save(user);

        String token = jwtUtil.generateToken(user.getId());
        return Map.of(
                "token", token,
                "user", Map.of("id", user.getId(), "name", user.getName(), "email", user.getEmail())
        );
    }

    // ── Signin ──

    public Map<String, Object> signin(String email, String password) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadRequestException("User not found"));

        if (!authenticate(password, user.getSalt(), user.getHashedPassword())) {
            throw new BadRequestException("Email and password don't match.");
        }

        String token = jwtUtil.generateToken(user.getId());
        return Map.of(
                "token", token,
                "user", Map.of("id", user.getId(), "name", user.getName(), "email", user.getEmail())
        );
    }

    // ── Google Login ──

    public Map<String, Object> googleLogin(String accessToken) {
        try {
            RestTemplate restTemplate = new RestTemplate();

            // Step 1: Validate token
            @SuppressWarnings("unchecked")
            Map<String, Object> tokenInfo = restTemplate.getForObject(
                    "https://www.googleapis.com/oauth2/v3/tokeninfo?access_token=" + accessToken,
                    Map.class);

            if (tokenInfo == null) throw new BadRequestException("Invalid Google token");

            String googleId = (String) tokenInfo.get("sub");

            // Check if user exists
            Optional<User> existingUser = userRepository.findByGoogleId(googleId);
            if (existingUser.isPresent()) {
                User user = existingUser.get();
                String token = jwtUtil.generateToken(user.getId());
                return Map.of(
                        "token", token,
                        "message", "User authenticated via Google (existing account).",
                        "user", Map.of("id", user.getId(), "email", user.getEmail(), "name", user.getName())
                );
            }

            // Step 2: Get user info
            @SuppressWarnings("unchecked")
            Map<String, Object> userInfo = restTemplate.getForObject(
                    "https://www.googleapis.com/oauth2/v3/userinfo?access_token=" + accessToken,
                    Map.class);

            if (userInfo == null) throw new BadRequestException("Failed to fetch Google user info");

            String salt = makeSalt();
            User newUser = User.builder()
                    .googleId(googleId)
                    .email((String) userInfo.get("email"))
                    .firstName((String) userInfo.getOrDefault("given_name", "User"))
                    .lastName((String) userInfo.getOrDefault("family_name", ""))
                    .name((String) userInfo.getOrDefault("name", "User"))
                    .picture((String) userInfo.get("picture"))
                    .salt(salt)
                    .hashedPassword(encryptPassword(UUID.randomUUID().toString(), salt))
                    .connectAccount("Google")
                    .role("user")
                    .build();

            newUser = userRepository.save(newUser);
            String token = jwtUtil.generateToken(newUser.getId());
            return Map.of(
                    "token", token,
                    "message", "User successfully authenticated and registered via Google.",
                    "user", Map.of("id", newUser.getId(), "email", newUser.getEmail(), "name", newUser.getName())
            );
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.error("Google login error", e);
            throw new BadRequestException("Authentication failed: Invalid or expired Google Access Token.");
        }
    }

    // ── Auth0 Login ──

    public Map<String, Object> auth0Login(String idToken) {
        try {
            // Decode JWT payload
            String[] parts = idToken.split("\\.");
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
                return Map.of(
                        "token", token,
                        "message", "User authenticated via Auth0.",
                        "user", Map.of("id", user.getId(), "email", user.getEmail(), "name", user.getName())
                );
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
            return Map.of(
                    "token", token,
                    "message", "User registered via Auth0.",
                    "user", Map.of("id", newUser.getId(), "email", newUser.getEmail(), "name", newUser.getName())
            );
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.error("Auth0 login error", e);
            throw new BadRequestException("Authentication failed: Invalid Auth0 token.");
        }
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
