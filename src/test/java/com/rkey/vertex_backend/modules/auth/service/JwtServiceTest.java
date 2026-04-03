package com.rkey.vertex_backend.modules.auth.service;
 
 import com.rkey.vertex_backend.modules.auth.model.dto.UserSummary;
 import org.junit.jupiter.api.BeforeEach;
 import org.junit.jupiter.api.Test;
 import org.springframework.test.util.ReflectionTestUtils;
 
 import static org.junit.jupiter.api.Assertions.*;
 
 class JwtServiceTest {
 
     private JwtService jwtService;
     private final String SECRET_KEY = "dGVzdFNlY3JldEtleUZvclZlcnRleEJhY2tlbmRUZXN0aW5nMTIzNA==";
     private final long EXPIRATION = 3600000;
 
     @BeforeEach
     void setUp() {
         jwtService = new JwtService();
         ReflectionTestUtils.setField(jwtService, "secretKey", SECRET_KEY);
         ReflectionTestUtils.setField(jwtService, "jwtExpiration", EXPIRATION);
     }
 
     @Test
     void generateToken_containsEmail() {
         UserSummary summary = new UserSummary("John", "Doe", "test@example.com", "testuser", "avatar.png");
         String token = jwtService.generateToken(summary);
         
         assertNotNull(token);
         assertEquals("test@example.com", jwtService.extractEmail(token));
     }
 
     @Test
     void isTokenValid_validToken() {
         UserSummary summary = new UserSummary("John", "Doe", "test@example.com", "testuser", "avatar.png");
         String token = jwtService.generateToken(summary);
         
         assertTrue(jwtService.isTokenValid(token, summary));
     }
 
     @Test
     void isTokenValid_wrongEmail() {
         UserSummary summary = new UserSummary("John", "Doe", "test@example.com", "testuser", "avatar.png");
         String token = jwtService.generateToken(summary);
         
         UserSummary anotherSummary = new UserSummary("Jane", "Smith", "wrong@example.com", "wronguser", "avatar.png");
         assertFalse(jwtService.isTokenValid(token, anotherSummary));
     }
 }
