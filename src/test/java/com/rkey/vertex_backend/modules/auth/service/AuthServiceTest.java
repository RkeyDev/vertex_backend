package com.rkey.vertex_backend.modules.auth.service;

import com.rkey.vertex_backend.core.api.ApiResponse;
import com.rkey.vertex_backend.core.api.auth.RegistrationResponseDTO;
import com.rkey.vertex_backend.modules.auth.entity.UserEntity;
import com.rkey.vertex_backend.modules.auth.entity.VerificationTokenEntity;
import com.rkey.vertex_backend.modules.auth.model.dto.AccountVerificationDTO;
import com.rkey.vertex_backend.modules.auth.model.dto.UserRegistrationDTO;
import com.rkey.vertex_backend.modules.auth.model.enums.AccountRole;
import com.rkey.vertex_backend.modules.auth.repository.UserRepository;
import com.rkey.vertex_backend.modules.auth.repository.VerificationTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AuthService business logic.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    
    @Mock
    private VerificationTokenRepository verificationTokenRepository;
    
    @Mock
    private PasswordEncoder passwordEncoder;
    
    @Mock
    private EmailService emailService;
    
    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthService authService;

    private UserRegistrationDTO registrationDTO;

    @BeforeEach
    void setUp() {
        registrationDTO = new UserRegistrationDTO(
                "John", "Doe", "john@example.com", "johndoe", "password1234", false
        );
    }

    @Test
    void registerUser_success() {
        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(userRepository.existsByUsername(any())).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("encodedPassword");
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        
        ApiResponse<RegistrationResponseDTO> response = authService.registerUser(registrationDTO);
        
        assertEquals("201", response.responseCode());
        verify(userRepository).save(any(UserEntity.class));
        verify(verificationTokenRepository).save(any(VerificationTokenEntity.class));
        verify(emailService).sendVerificationEmail(eq("john@example.com"), anyString());
    }

    @Test
    void registerUser_emailAlreadyExists() {
        when(userRepository.existsByEmail(any())).thenReturn(true);
        
        ApiResponse<RegistrationResponseDTO> response = authService.registerUser(registrationDTO);
        
        assertEquals("400", response.responseCode());
        // Fixed assertion to match the actual message in AuthService
        assertTrue(response.message().contains("Email already in use"));
        verify(userRepository, never()).save(any());
    }

    @Test
    void verifyAccount_success() {
        UserEntity user = UserEntity.builder()
                .email("john@example.com")
                .isLocked(true)
                .build();
        
        VerificationTokenEntity tokenEntity = VerificationTokenEntity.builder()
                .token("valid_token")
                .user(user)
                .expiryDate(OffsetDateTime.now().plusHours(24))
                .build();
        
        when(verificationTokenRepository.findByToken("valid_token")).thenReturn(Optional.of(tokenEntity));
        
        AccountVerificationDTO verificationDTO = new AccountVerificationDTO("valid_token", "john@example.com");
        ApiResponse<AccountVerificationDTO> response = authService.verifyAccount(verificationDTO);
        
        assertEquals("200", response.responseCode());
        assertFalse(user.isLocked());
        verify(userRepository).save(user);
        verify(verificationTokenRepository).delete(tokenEntity);
    }

    @Test
    void verifyAccount_invalidToken() {
        when(verificationTokenRepository.findByToken("invalid_token")).thenReturn(Optional.empty());
        
        AccountVerificationDTO verificationDTO = new AccountVerificationDTO("invalid_token", "john@example.com");
        ApiResponse<AccountVerificationDTO> response = authService.verifyAccount(verificationDTO);
        
        assertEquals("400", response.responseCode());
        assertTrue(response.message().contains("Invalid token"));
    }

    @Test
    void verifyAccount_expiredToken() {
        UserEntity user = UserEntity.builder().email("john@example.com").build();
        VerificationTokenEntity tokenEntity = VerificationTokenEntity.builder()
                .token("expired_token")
                .user(user)
                .expiryDate(OffsetDateTime.now().minusHours(1))
                .build();
        
        when(verificationTokenRepository.findByToken("expired_token")).thenReturn(Optional.of(tokenEntity));
        
        AccountVerificationDTO verificationDTO = new AccountVerificationDTO("expired_token", "john@example.com");
        ApiResponse<AccountVerificationDTO> response = authService.verifyAccount(verificationDTO);
        
        assertEquals("400", response.responseCode());
        assertTrue(response.message().contains("Verification token has expired"));
    }
}