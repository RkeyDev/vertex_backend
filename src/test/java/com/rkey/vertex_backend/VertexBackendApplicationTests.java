package com.rkey.vertex_backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Sanity check to ensure the Spring Application Context loads correctly.
 * * We use @MockBean for StringRedisTemplate to prevent the context from failing
 * in CI environments (like GitHub Actions) where a Redis server is not available.
 */
@SpringBootTest
@ActiveProfiles("test")
class VertexBackendApplicationTests {

    @MockBean
    private StringRedisTemplate stringRedisTemplate;

    @Test
    void contextLoads() {
    }

}