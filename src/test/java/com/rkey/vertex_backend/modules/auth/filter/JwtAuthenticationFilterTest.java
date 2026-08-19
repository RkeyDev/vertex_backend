package com.rkey.vertex_backend.modules.auth.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rkey.vertex_backend.modules.auth.service.JwtService;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for JwtAuthenticationFilter covering JWT validation,
 * token extraction, and error handling for expired/invalid tokens.
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtService jwtService;

    @Mock
    private UserDetailsService userDetailsService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    @Mock
    private UserDetails userDetails;

    private JwtAuthenticationFilter jwtAuthenticationFilter;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        objectMapper = new ObjectMapper();
        jwtAuthenticationFilter = new JwtAuthenticationFilter(jwtService, userDetailsService, objectMapper);
    }

    @Test
    void doFilterInternal_withoutAuthorizationHeader() throws ServletException, IOException {
        // Arrange
        when(request.getHeader("Authorization")).thenReturn(null);

        // Act
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Assert
        verify(filterChain, times(1)).doFilter(request, response);
        verify(jwtService, never()).extractUsername(anyString());
    }

    @Test
    void doFilterInternal_withoutBearerPrefix() throws ServletException, IOException {
        // Arrange
        when(request.getHeader("Authorization")).thenReturn("Basic credentials");

        // Act
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Assert
        verify(filterChain, times(1)).doFilter(request, response);
        verify(jwtService, never()).extractUsername(anyString());
    }

    @Test
    void doFilterInternal_withValidBearerToken() throws ServletException, IOException {
        // Arrange
        String token = "valid_jwt_token";
        String email = "user@example.com";
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(jwtService.extractUsername(token)).thenReturn(email);
        when(userDetailsService.loadUserByUsername(email)).thenReturn(userDetails);
        when(jwtService.isTokenValid(token, userDetails)).thenReturn(true);

        // Act
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Assert
        verify(jwtService, times(1)).extractUsername(token);
        verify(userDetailsService, times(1)).loadUserByUsername(email);
        verify(filterChain, times(1)).doFilter(request, response);
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void doFilterInternal_withInvalidToken() throws ServletException, IOException {
        // Arrange
        String token = "invalid_jwt_token";
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(jwtService.extractUsername(token)).thenThrow(
            new JwtException("Invalid JWT token")
        );
        StringWriter stringWriter = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(stringWriter));

        // Act
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Assert
        verify(response, times(1)).setStatus(403);
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void doFilterInternal_withExpiredToken() throws ServletException, IOException {
        // Arrange
        String token = "expired_jwt_token";
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(jwtService.extractUsername(token)).thenThrow(
            new ExpiredJwtException(null, null, "JWT token has expired")
        );
        StringWriter stringWriter = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(stringWriter));

        // Act
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Assert
        verify(response, times(1)).setStatus(401);
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void doFilterInternal_withInvalidTokenNotValidForUser() throws ServletException, IOException {
        // Arrange
        String token = "valid_but_not_for_user";
        String email = "user@example.com";
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(jwtService.extractUsername(token)).thenReturn(email);
        when(userDetailsService.loadUserByUsername(email)).thenReturn(userDetails);
        when(jwtService.isTokenValid(token, userDetails)).thenReturn(false);

        // Act
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Assert
        verify(filterChain, times(1)).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void doFilterInternal_withNullEmailExtracted() throws ServletException, IOException {
        // Arrange
        String token = "token_without_email";
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(jwtService.extractUsername(token)).thenReturn(null);

        // Act
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Assert
        verify(userDetailsService, never()).loadUserByUsername(anyString());
        verify(filterChain, times(1)).doFilter(request, response);
    }

    @Test
    void doFilterInternal_withEmptyBearerToken() throws ServletException, IOException {
        // Arrange
        when(request.getHeader("Authorization")).thenReturn("Bearer ");

        // Act
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Assert
        verify(filterChain, times(1)).doFilter(request, response);
    }

    @Test
    void doFilterInternal_withUserDetailsNotFound() throws ServletException, IOException {
        // Arrange
        String token = "valid_token";
        String email = "nonexistent@example.com";
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(jwtService.extractUsername(token)).thenReturn(email);
        when(userDetailsService.loadUserByUsername(email)).thenThrow(
            new RuntimeException("User not found")
        );

        // Act & Assert
        assertThrows(RuntimeException.class, () ->
            jwtAuthenticationFilter.doFilterInternal(request, response, filterChain)
        );
    }

    @Test
    void doFilterInternal_withMultipleAuthorizationHeaders() throws ServletException, IOException {
        // Arrange
        String token = "valid_jwt_token";
        String email = "user@example.com";
        // Mock expects the first call only
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(jwtService.extractUsername(token)).thenReturn(email);
        when(userDetailsService.loadUserByUsername(email)).thenReturn(userDetails);
        when(jwtService.isTokenValid(token, userDetails)).thenReturn(true);

        // Act
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Assert
        verify(jwtService, times(1)).extractUsername(token);
        verify(filterChain, times(1)).doFilter(request, response);
    }

    @Test
    void doFilterInternal_preservesExistingAuthentication() throws ServletException, IOException {
        // Arrange
        String token = "valid_jwt_token";
        String email = "user@example.com";
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(jwtService.extractUsername(token)).thenReturn(email);
        when(userDetailsService.loadUserByUsername(email)).thenReturn(userDetails);
        when(jwtService.isTokenValid(token, userDetails)).thenReturn(true);

        // Act - first call
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);
        var firstAuth = SecurityContextHolder.getContext().getAuthentication();

        // Act - second call (should not change authentication)
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);
        var secondAuth = SecurityContextHolder.getContext().getAuthentication();

        // Assert
        assertNotNull(firstAuth);
        assertNotNull(secondAuth);
        assertEquals(firstAuth.getName(), secondAuth.getName());
    }

    @Test
    void doFilterInternal_tokenWithSpecialCharacters() throws ServletException, IOException {
        // Arrange
        String token = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.dozjgNryP4J3jVmNHl0w5N_XgL0n3I9PlFUP0THsR8U";
        String email = "user@example.com";
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(jwtService.extractUsername(token)).thenReturn(email);
        when(userDetailsService.loadUserByUsername(email)).thenReturn(userDetails);
        when(jwtService.isTokenValid(token, userDetails)).thenReturn(true);

        // Act
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Assert
        verify(jwtService, times(1)).extractUsername(token);
        verify(filterChain, times(1)).doFilter(request, response);
    }

    @Test
    void doFilterInternal_expiredJwtExceptionResponse() throws ServletException, IOException {
        // Arrange
        String token = "expired_token";
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(jwtService.extractUsername(token)).thenThrow(
            new ExpiredJwtException(null, null, "JWT token has expired")
        );
        StringWriter stringWriter = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(stringWriter));

        // Act
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Assert
        verify(response, times(1)).setStatus(401);
        verify(response, times(1)).setContentType("application/json");
    }

    @Test
    void doFilterInternal_jwtExceptionResponse() throws ServletException, IOException {
        // Arrange
        String token = "malformed_token";
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(jwtService.extractUsername(token)).thenThrow(
            new JwtException("Invalid JWT token")
        );
        StringWriter stringWriter = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(stringWriter));

        // Act
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Assert
        verify(response, times(1)).setStatus(403);
        verify(response, times(1)).setContentType("application/json");
    }

    @Test
    void doFilterInternal_filterChainContinues() throws ServletException, IOException {
        // Arrange
        when(request.getHeader("Authorization")).thenReturn(null);

        // Act
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Assert - filter chain must continue even without token
        verify(filterChain, times(1)).doFilter(request, response);
    }
}
