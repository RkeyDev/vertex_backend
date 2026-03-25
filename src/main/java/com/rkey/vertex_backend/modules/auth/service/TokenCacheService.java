package com.rkey.vertex_backend.modules.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class TokenCacheService {

    private final StringRedisTemplate redisTemplate;

    public boolean isExists(String token) {
        throw new UnsupportedOperationException("Method is not implemented yet");
    }

    public void save(String token, long duration, TimeUnit unit) {
        throw new UnsupportedOperationException("Method is not implemented yet");
    }

    public void delete(String token) {
        throw new UnsupportedOperationException("Method is not implemented yet");
    }
}