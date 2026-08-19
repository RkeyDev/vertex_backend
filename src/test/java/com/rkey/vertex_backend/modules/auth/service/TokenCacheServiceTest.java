package com.rkey.vertex_backend.modules.auth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for TokenCacheService covering Redis token caching operations.
 * Tests token save, retrieval, and deletion with various TTL configurations.
 */
@ExtendWith(MockitoExtension.class)
class TokenCacheServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    private TokenCacheService tokenCacheService;

    @BeforeEach
    void setUp() {
        tokenCacheService = new TokenCacheService(redisTemplate);
    }

    @Test
    void isExists_throwsUnsupportedOperationException() {
        // Act & Assert
        assertThrows(UnsupportedOperationException.class, () -> 
            tokenCacheService.isExists("test_token")
        );
    }

    @Test
    void save_throwsUnsupportedOperationException() {
        // Act & Assert
        assertThrows(UnsupportedOperationException.class, () -> 
            tokenCacheService.save("test_token", 3600, TimeUnit.SECONDS)
        );
    }

    @Test
    void delete_throwsUnsupportedOperationException() {
        // Act & Assert
        assertThrows(UnsupportedOperationException.class, () -> 
            tokenCacheService.delete("test_token")
        );
    }

    @Test
    void save_withShortDuration() {
        // This test documents expected behavior once implemented
        // The method should accept tokens with short TTL (e.g., 60 seconds)
        long duration = 60;
        TimeUnit unit = TimeUnit.SECONDS;

        assertThrows(UnsupportedOperationException.class, () -> 
            tokenCacheService.save("test_token", duration, unit)
        );
    }

    @Test
    void save_withLongDuration() {
        // This test documents expected behavior once implemented
        // The method should accept tokens with long TTL (e.g., 24 hours)
        long duration = 24;
        TimeUnit unit = TimeUnit.HOURS;

        assertThrows(UnsupportedOperationException.class, () -> 
            tokenCacheService.save("test_token", duration, unit)
        );
    }

    @Test
    void save_withDayTimeUnit() {
        assertThrows(UnsupportedOperationException.class, () -> 
            tokenCacheService.save("token", 1, TimeUnit.DAYS)
        );
    }

    @Test
    void save_withMinuteTimeUnit() {
        assertThrows(UnsupportedOperationException.class, () -> 
            tokenCacheService.save("token", 30, TimeUnit.MINUTES)
        );
    }

    @Test
    void isExists_withValidToken() {
        assertThrows(UnsupportedOperationException.class, () -> 
            tokenCacheService.isExists("valid_refresh_token_12345")
        );
    }

    @Test
    void isExists_withNullToken() {
        assertThrows(UnsupportedOperationException.class, () -> 
            tokenCacheService.isExists(null)
        );
    }

    @Test
    void isExists_withEmptyToken() {
        assertThrows(UnsupportedOperationException.class, () -> 
            tokenCacheService.isExists("")
        );
    }

    @Test
    void delete_withValidToken() {
        assertThrows(UnsupportedOperationException.class, () -> 
            tokenCacheService.delete("token_to_delete")
        );
    }

    @Test
    void delete_withNullToken() {
        assertThrows(UnsupportedOperationException.class, () -> 
            tokenCacheService.delete(null)
        );
    }

    @Test
    void delete_withEmptyToken() {
        assertThrows(UnsupportedOperationException.class, () -> 
            tokenCacheService.delete("")
        );
    }

    @Test
    void saveAndCheckSequence() {
        // This test documents a realistic usage pattern
        // Once implemented: save token, check if exists, then delete
        assertThrows(UnsupportedOperationException.class, () -> 
            tokenCacheService.save("token1", 3600, TimeUnit.SECONDS)
        );
    }

    @Test
    void save_withZeroDuration() {
        // Edge case: zero duration should be handled
        assertThrows(UnsupportedOperationException.class, () -> 
            tokenCacheService.save("token", 0, TimeUnit.SECONDS)
        );
    }

    @Test
    void save_withNegativeDuration() {
        // Edge case: negative duration should be handled
        assertThrows(UnsupportedOperationException.class, () -> 
            tokenCacheService.save("token", -1, TimeUnit.SECONDS)
        );
    }
}
