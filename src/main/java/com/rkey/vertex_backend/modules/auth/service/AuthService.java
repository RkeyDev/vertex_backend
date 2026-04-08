package com.rkey.vertex_backend.modules.auth.service;

import com.rkey.vertex_backend.core.api.ApiResponse;
import com.rkey.vertex_backend.core.api.auth.LoginResponseDTO;
import com.rkey.vertex_backend.core.api.auth.RegistrationResponseDTO;
import com.rkey.vertex_backend.modules.auth.model.dto.AccountVerificationDTO;
import com.rkey.vertex_backend.modules.auth.model.dto.UserLoginDTO;
import com.rkey.vertex_backend.modules.auth.model.dto.UserRegistrationDTO;
import com.rkey.vertex_backend.modules.auth.model.dto.UserSummary;
import com.rkey.vertex_backend.modules.auth.repository.RefreshTokenRepository;
import com.rkey.vertex_backend.modules.auth.repository.UserRepository;
import com.rkey.vertex_backend.modules.auth.entity.RefreshTokenEntity;
import com.rkey.vertex_backend.modules.auth.entity.UserEntity;
import com.rkey.vertex_backend.modules.auth.entity.VerificationTokenEntity;
import com.rkey.vertex_backend.modules.auth.model.enums.AccountRole;
import com.rkey.vertex_backend.modules.auth.repository.VerificationTokenRepository;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Core business logic for authentication and user management.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final int REMEMBER_ME_VALID_DAYS = 30;
    private final int VALID_DAYS = 1;

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final VerificationTokenRepository verificationTokenRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public ApiResponse<RegistrationResponseDTO> registerUser(UserRegistrationDTO dto) {
        if (userRepository.existsByEmail(dto.email())) {
            return new ApiResponse<>("Registration Failed", "Email already in use", null, "400", null);
        }
        if (userRepository.existsByUsername(dto.username())) {
            return new ApiResponse<>("Registration Failed", "Username already in use", null, "400", null);
        }

        UserEntity user = UserEntity.builder()
                .firstName(dto.firstName())
                .lastName(dto.lastName())
                .email(dto.email())
                .username(dto.username())
                .encodedPassword(passwordEncoder.encode(dto.password()))
                .role(AccountRole.USER)
                .isLocked(true)
                .build();

        userRepository.save(user);

        String token = UUID.randomUUID().toString();
        VerificationTokenEntity verificationToken = VerificationTokenEntity.builder()
                .token(token)
                .user(user)
                .expiryDate(OffsetDateTime.now().plusHours(24))
                .build();
                
        verificationTokenRepository.save(verificationToken);
        emailService.sendVerificationEmail(user.getEmail(), token);

        return new ApiResponse<>(
                "Registration Successful",
                "User has been successfully registered",
                new RegistrationResponseDTO(user.getEmail(), null),
                "201",
                null
        );
    }

    @Transactional
    public ApiResponse<LoginResponseDTO> loginUser(UserLoginDTO dto) {
        try {
            // Authenticate via Spring Security
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(dto.email(), dto.password())
            );

            // Load User
            UserEntity user = userRepository.findByEmail(dto.email())
                    .orElseThrow(() -> new IllegalStateException("User not found post-authentication"));

            // Verify Account status
            if (user.isLocked()) {
                return new ApiResponse<>("Login Failed", "Account is not verified", null, "403", null);
            }

            // Create User Summary for the Frontend
            UserSummary userSummary = new UserSummary(
                    user.getFirstName(),
                    user.getLastName(),
                    user.getEmail(),
                    user.getUsername(),
                    user.getAvatarUrl()
            );

            // Generate Tokens using the Entity (UserDetails)
            String accessToken = jwtService.generateToken(user);
            String refreshToken = UUID.randomUUID().toString();

            // Manage Refresh Token session
            long daysValid = dto.rememberMe() ? REMEMBER_ME_VALID_DAYS : VALID_DAYS;
            

            refreshTokenRepository.deleteAllByUser(user);
            refreshTokenRepository.flush(); 

            RefreshTokenEntity refreshTokenEntity = RefreshTokenEntity.builder()
                    .token(refreshToken)
                    .user(user)
                    .expiryDate(OffsetDateTime.now().plusDays(daysValid))
                    .build();
            
            refreshTokenRepository.save(refreshTokenEntity);

            log.info("User {} successfully logged in.", user.getEmail());

            return new ApiResponse<>(
                    "Login Successful",
                    "User authenticated successfully",
                    new LoginResponseDTO(accessToken, refreshToken, null, userSummary),
                    "200",
                    null
            );

        } catch (AuthenticationException e) {
            log.warn("Login failed for email: {}", dto.email());
            return new ApiResponse<>("Login Failed", "Invalid email or password", null, "401", null);
        } catch (Exception e) {
            log.error("Critical login error: ", e);
            return new ApiResponse<>("Error", "Internal server error during login", null, "500", null);
        }
    }

    @Transactional
    public ApiResponse<AccountVerificationDTO> verifyAccount(AccountVerificationDTO dto){
        VerificationTokenEntity tokenEntity = verificationTokenRepository.findByToken(dto.verificationToken())
                .orElse(null);

        if (tokenEntity == null || !tokenEntity.getUser().getEmail().equals(dto.email())) {
            return new ApiResponse<>("Verification Failed", "Invalid token or email", null, "400", null);
        }

        if (tokenEntity.isExpired()) {
            return new ApiResponse<>("Verification Failed", "Verification token has expired", null, "400", null);
        }

        UserEntity user = tokenEntity.getUser();
        user.setLocked(false);
        userRepository.save(user);

        verificationTokenRepository.delete(tokenEntity);
        return new ApiResponse<>("Verification Successful", "Account has been verified successfully", null, "200", null);
    }

    @Transactional
    public ApiResponse<LoginResponseDTO> refreshJwtToken(String refreshToken) {
        RefreshTokenEntity tokenEntity = refreshTokenRepository.findByToken(refreshToken)
                .orElseThrow(() -> new IllegalArgumentException("Invalid refresh token"));

        if (tokenEntity.getExpiryDate().isBefore(OffsetDateTime.now())) {
            refreshTokenRepository.delete(tokenEntity);
            return new ApiResponse<>("Token Refresh Failed", "Refresh token expired", null, "401", null);
        }

        UserEntity user = tokenEntity.getUser();
        UserSummary userSummary = new UserSummary(
            user.getFirstName(), user.getLastName(), user.getEmail(), user.getUsername(), user.getAvatarUrl()
        );
        
        String newAccessToken = jwtService.generateToken(user);

        return new ApiResponse<>(
                "Token Refreshed",
                "New access token generated",
                new LoginResponseDTO(newAccessToken, refreshToken, null, userSummary),
                "200",
                null
        );
    }

    @Transactional
    public boolean logout() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null) return false;

            UserEntity user = userRepository.findByEmail(auth.getName())
                    .orElseThrow(() -> new IllegalArgumentException("User not found"));

            refreshTokenRepository.deleteAllByUser(user);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}