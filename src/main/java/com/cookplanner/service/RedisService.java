package com.cookplanner.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Redis caching helper — mirrors helpers/redisHelper.js
 */
@Service
@RequiredArgsConstructor
public class RedisService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public static String pantryItemsKey(String userId) {
        return "pantryItems:" + userId;
    }

    public <T> T getCache(String key, Class<T> type) {
        String data = redisTemplate.opsForValue().get(key);
        if (data == null) return null;
        try {
            return objectMapper.readValue(data, type);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    public void setCache(String key, Object value, long ttlSeconds) {
        try {
            String json = objectMapper.writeValueAsString(value);
            redisTemplate.opsForValue().set(key, json, Duration.ofSeconds(ttlSeconds));
        } catch (JsonProcessingException e) {
            // ignore serialization errors
        }
    }

    public void deleteCache(String key) {
        redisTemplate.delete(key);
    }
}
