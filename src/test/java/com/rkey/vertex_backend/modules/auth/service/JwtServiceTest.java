package com.rkey.vertex_backend.modules.auth.service;

import com.rkey.vertex_backend.modules.auth.entity.UserEntity;
import com.rkey.vertex_backend.modules.auth.model.enums.AccountRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for JwtService following the migration to UserDetails.
 */
class JwtServiceTest {

    private JwtService jwtService;
    // Base64 encoded 256-bit key for HMAC-SHA
    private final String SECRET_KEY = "dGVzdFNlY3JldEtleUZvclZlcnRleEJhY2tlbmRUZXN0aW5nMTIzNA==";
    private final long EXPIRATION = 3600000; // 1 hour

    private UserEntity testUser;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secretKey", SECRET_KEY);
        ReflectionTestUtils.setField(jwtService, "jwtExpiration", EXPIRATION);

        // Initialize a test user that implements UserDetails
        testUser = UserEntity.builder()
                .firstName("John")
                .lastName("Doe")
                .email("test@example.com")
                .username("testuser")
                .role(AccountRole.USER)
                .build();
    }

    @Test
    void generateToken_containsEmailAsSubject() {
        // Act
        String token = jwtService.generateToken(testUser);
        
        // Assert
        assertNotNull(token);
        // We call extractUsername because in our implementation, email is the username
        assertEquals("test@example.com", jwtService.extractUsername(token));
    }

    @Test
    void isTokenValid_success() {
        // Arrange
        String token = jwtService.generateToken(testUser);
        
        // Act & Assert
        assertTrue(jwtService.isTokenValid(token, testUser));
    }

    @Test
    void isTokenValid_failsWhenEmailMismatched() {
        // Arrange
        String token = jwtService.generateToken(testUser);
        
        UserEntity differentUser = UserEntity.builder()
                .email("wrong@example.com")
                .username("wronguser")
                .build();
        
        // Act & Assert
        assertFalse(jwtService.isTokenValid(token, differentUser));
    }

    @Test
    void extractClaim_canExtractExpiration() {
        // Arrange
        String token = jwtService.generateToken(testUser);
        
        // Act
        java.util.Date expiration = jwtService.extractClaim(token, io.jsonwebtoken.Claims::getExpiration);
        
        // Assert
        assertTrue(expiration.after(new java.util.Date()));
    }
}