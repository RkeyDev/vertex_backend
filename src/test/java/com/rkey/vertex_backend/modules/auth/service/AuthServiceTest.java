package com.rkey.vertex_backend.modules.auth.service;

import com.rkey.vertex_backend.core.api.ApiResponse;
import com.rkey.vertex_backend.core.api.auth.LoginResponseDTO;
import com.rkey.vertex_backend.core.api.auth.RegistrationResponseDTO;
import com.rkey.vertex_backend.modules.auth.entity.RefreshTokenEntity;
import com.rkey.vertex_backend.modules.auth.entity.UserEntity;
import com.rkey.vertex_backend.modules.auth.entity.VerificationTokenEntity;
import com.rkey.vertex_backend.modules.auth.model.dto.AccountVerificationDTO;
import com.rkey.vertex_backend.modules.auth.model.dto.UserLoginDTO;
import com.rkey.vertex_backend.modules.auth.model.dto.UserRegistrationDTO;
import com.rkey.vertex_backend.modules.auth.repository.RefreshTokenRepository;
import com.rkey.vertex_backend.modules.auth.repository.UserRepository;
import com.rkey.vertex_backend.modules.auth.repository.VerificationTokenRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private VerificationTokenRepository verificationTokenRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private EmailService emailService;

    @Mock
    private JwtService jwtService;

    @Mock
    private AuthenticationManager authenticationManager;

    @InjectMocks
    private AuthService authService;

    private UserRegistrationDTO registrationDTO;
    private UserEntity activeUser;
    private UserEntity lockedUser;

    @BeforeEach
    void setUp() {
        registrationDTO = new UserRegistrationDTO(
                "John", "Doe", "john@example.com", "johndoe", "password1234", false
        );

        activeUser = UserEntity.builder()
                .id(1L)
                .firstName("John")
                .lastName("Doe")
                .email("john@example.com")
                .username("johndoe")
                .isLocked(false)
                .build();

        lockedUser = UserEntity.builder()
                .id(2L)
                .email("locked@example.com")
                .isLocked(true)
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void registerUser_success() {
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("encodedPassword");
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ApiResponse<RegistrationResponseDTO> response = authService.registerUser(registrationDTO);

        assertEquals("201", response.responseCode());
        verify(userRepository).save(any(UserEntity.class));
        verify(verificationTokenRepository).save(any(VerificationTokenEntity.class));
        verify(emailService).sendVerificationEmail(eq("john@example.com"), anyString());
    }

    @Test
    void registerUser_emailAlreadyExists() {
        when(userRepository.existsByEmail(anyString())).thenReturn(true);

        ApiResponse<RegistrationResponseDTO> response = authService.registerUser(registrationDTO);

        assertEquals("400", response.responseCode());
        assertTrue(response.message().contains("Email already in use"));
        verify(userRepository, never()).save(any());
    }

    @Test
    void registerUser_usernameAlreadyExists() {
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(userRepository.existsByUsername(anyString())).thenReturn(true);

        ApiResponse<RegistrationResponseDTO> response = authService.registerUser(registrationDTO);

        assertEquals("400", response.responseCode());
        assertTrue(response.message().contains("Username already in use"));
        verify(userRepository, never()).save(any());
    }

    @Test
    void verifyAccount_success() {
        VerificationTokenEntity tokenEntity = VerificationTokenEntity.builder()
                .token("valid_token")
                .user(lockedUser)
                .expiryDate(OffsetDateTime.now().plusHours(24))
                .build();

        when(verificationTokenRepository.findByToken("valid_token")).thenReturn(Optional.of(tokenEntity));

        AccountVerificationDTO verificationDTO = new AccountVerificationDTO("valid_token", "locked@example.com");
        ApiResponse<AccountVerificationDTO> response = authService.verifyAccount(verificationDTO);

        assertEquals("200", response.responseCode());
        assertFalse(lockedUser.isLocked());
        verify(userRepository).save(lockedUser);
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
        VerificationTokenEntity tokenEntity = VerificationTokenEntity.builder()
                .token("expired_token")
                .user(lockedUser)
                .expiryDate(OffsetDateTime.now().minusHours(1))
                .build();

        when(verificationTokenRepository.findByToken("expired_token")).thenReturn(Optional.of(tokenEntity));

        AccountVerificationDTO verificationDTO = new AccountVerificationDTO("expired_token", "locked@example.com");
        ApiResponse<AccountVerificationDTO> response = authService.verifyAccount(verificationDTO);

        assertEquals("400", response.responseCode());
        assertTrue(response.message().contains("Verification token has expired"));
    }

    @Test
    void loginUser_success() {
        UserLoginDTO loginDTO = new UserLoginDTO("john@example.com", "password123", true);

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(null);
        when(userRepository.findByEmail(loginDTO.email())).thenReturn(Optional.of(activeUser));
        when(jwtService.generateToken(activeUser)).thenReturn("mock_access_token");

        ApiResponse<LoginResponseDTO> response = authService.loginUser(loginDTO);

        assertEquals("200", response.responseCode());
        assertNotNull(response.data());
        assertEquals("mock_access_token", response.data().accessToken());
        assertNotNull(response.data().refreshToken());
        
        verify(refreshTokenRepository).deleteAllByUser(activeUser);
        verify(refreshTokenRepository).flush();
        verify(refreshTokenRepository).save(any(RefreshTokenEntity.class));
    }

    @Test
    void loginUser_accountLocked() {
        UserLoginDTO loginDTO = new UserLoginDTO("locked@example.com", "password123", false);

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(null);
        when(userRepository.findByEmail(loginDTO.email())).thenReturn(Optional.of(lockedUser));

        ApiResponse<LoginResponseDTO> response = authService.loginUser(loginDTO);

        assertEquals("403", response.responseCode());
        assertTrue(response.message().contains("not verified"));
        verify(jwtService, never()).generateToken(any());
    }

    @Test
    void loginUser_badCredentials() {
        UserLoginDTO loginDTO = new UserLoginDTO("john@example.com", "wrongpassword", false);

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        ApiResponse<LoginResponseDTO> response = authService.loginUser(loginDTO);

        assertEquals("401", response.responseCode());
        assertTrue(response.message().contains("Invalid email or password"));
    }

    @Test
    void loginUser_internalError() {
        UserLoginDTO loginDTO = new UserLoginDTO("john@example.com", "password123", false);

        when(authenticationManager.authenticate(any())).thenThrow(new RuntimeException("Database down"));

        ApiResponse<LoginResponseDTO> response = authService.loginUser(loginDTO);

        assertEquals("500", response.responseCode());
    }

    @Test
    void refreshJwtToken_success() {
        String mockRefreshToken = "valid_refresh_token";
        RefreshTokenEntity tokenEntity = RefreshTokenEntity.builder()
                .token(mockRefreshToken)
                .user(activeUser)
                .expiryDate(OffsetDateTime.now().plusDays(1))
                .build();

        when(refreshTokenRepository.findByToken(mockRefreshToken)).thenReturn(Optional.of(tokenEntity));
        when(jwtService.generateToken(activeUser)).thenReturn("new_access_token");

        ApiResponse<LoginResponseDTO> response = authService.refreshJwtToken(mockRefreshToken);

        assertEquals("200", response.responseCode());
        assertEquals("new_access_token", response.data().accessToken());
    }

    @Test
    void refreshJwtToken_expiredToken() {
        String mockRefreshToken = "expired_refresh_token";
        RefreshTokenEntity tokenEntity = RefreshTokenEntity.builder()
                .token(mockRefreshToken)
                .user(activeUser)
                .expiryDate(OffsetDateTime.now().minusDays(1))
                .build();

        when(refreshTokenRepository.findByToken(mockRefreshToken)).thenReturn(Optional.of(tokenEntity));

        ApiResponse<LoginResponseDTO> response = authService.refreshJwtToken(mockRefreshToken);

        assertEquals("401", response.responseCode());
        verify(refreshTokenRepository).delete(tokenEntity);
        verify(jwtService, never()).generateToken(any());
    }

    @Test
    void refreshJwtToken_invalidToken() {
        when(refreshTokenRepository.findByToken(anyString())).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> authService.refreshJwtToken("invalid_token"));
    }

    @Test
    void logout_success() {
        SecurityContext securityContext = mock(SecurityContext.class);
        Authentication authentication = mock(Authentication.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getName()).thenReturn("john@example.com");
        SecurityContextHolder.setContext(securityContext);

        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(activeUser));

        boolean result = authService.logout();

        assertTrue(result);
        verify(refreshTokenRepository).deleteAllByUser(activeUser);
    }

    @Test
    void logout_noAuthentication() {
        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(null);
        SecurityContextHolder.setContext(securityContext);

        boolean result = authService.logout();

        assertFalse(result);
        verify(refreshTokenRepository, never()).deleteAllByUser(any());
    }

    @Test
    void logout_userNotFound() {
        SecurityContext securityContext = mock(SecurityContext.class);
        Authentication authentication = mock(Authentication.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getName()).thenReturn("unknown@example.com");
        SecurityContextHolder.setContext(securityContext);

        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        boolean result = authService.logout();

        assertFalse(result);
    }
}