package com.rkey.vertex_backend.modules.auth.service;

import com.rkey.vertex_backend.core.api.ApiResponse;
import com.rkey.vertex_backend.core.api.auth.LoginResponseDTO;
import com.rkey.vertex_backend.core.api.auth.RegistrationResponseDTO;
import com.rkey.vertex_backend.core.api.auth.UpdateProfileResponse;
import com.rkey.vertex_backend.modules.auth.model.dto.AccountVerificationDTO;
import com.rkey.vertex_backend.modules.auth.model.dto.UpdateProfileDTO;
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

import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.ResponseEntity;
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
 * Adheres to industry best practices for transaction management and security.
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

    @Transactional
    public ApiResponse<UpdateProfileResponse> updateProfile(UpdateProfileDTO dto, UserEntity user) {
        if (user == null) {
            log.warn("Attempted to update profile with null user context");
            return new ApiResponse<>(
                "Update profile failed",
                "User context is missing or unauthenticated",
                new UpdateProfileResponse(new UserSummary("", "", "", "", "")),
                "401",
                null
            );
        }

        // Apply partial updates only if values are provided and not blank
        if (dto.firstName() != null && !dto.firstName().isBlank()) {
            user.setFirstName(dto.firstName().trim());
        }

        if (dto.lastName() != null && !dto.lastName().isBlank()) {
            user.setLastName(dto.lastName().trim());
        }

        if (dto.avatarUrl() != null && !dto.avatarUrl().isBlank()) {
            user.setAvatarUrl(dto.avatarUrl().trim());
        }

        UserEntity updatedUser = userRepository.save(user);
        log.info("Profile successfully updated for user: {}", updatedUser.getEmail());

        UserSummary userSummary = new UserSummary(
                updatedUser.getFirstName(),
                updatedUser.getLastName(),
                updatedUser.getEmail(),
                updatedUser.getUsername(),
                updatedUser.getAvatarUrl()
        );

        return new ApiResponse<>(
                "Update Profile Successful",
                "Your profile information has been updated successfully",
                new UpdateProfileResponse(userSummary),
                "200",
                null
        );
    }


    @Transactional
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
                .isLocked(true) // Account is locked until email verification
                .build();

        userRepository.save(user);

        // Dispatches the initial verification link
        sendVerificationLink(user.getEmail(), user); 

        return new ApiResponse<>(
                "Registration Successful",
                "User has been successfully registered. Please check your email to verify your account.",
                new RegistrationResponseDTO(user.getEmail(), null),
                "201",
                null
        );
    }

    /**
     * Generates and sends a new verification link. 
     * Explicitly clears existing tokens for the user to prevent unique constraint violations.
     */
    @Transactional
    public ApiResponse<Void> sendVerificationLink(String emailAddress, UserEntity user) {
        if (user == null) {
            user = userRepository.findByEmail(emailAddress)
                    .orElseThrow(() -> new EntityNotFoundException("User not found with email: " + emailAddress));
        }

        // Clean up any existing tokens for this user before generating a new one.
        // This is safer than an update-or-create logic for sensitive security tokens.
        verificationTokenRepository.deleteByUser(user);
        verificationTokenRepository.flush(); 

        VerificationTokenEntity tokenEntity = VerificationTokenEntity.builder()
                .token(UUID.randomUUID().toString())
                .user(user)
                .expiryDate(OffsetDateTime.now().plusHours(24))
                .build();

        verificationTokenRepository.save(tokenEntity);
        
        // Dispatch email via asynchronous/external service logic
        emailService.sendVerificationEmail(user.getEmail(), tokenEntity.getToken());

        log.info("New verification link dispatched for user: {}", user.getEmail());
        return new ApiResponse<>("Success", "Verification link has been sent.", null, "200", null);
    }

    @Transactional
    public ApiResponse<LoginResponseDTO> loginUser(UserLoginDTO dto) {
        try {
            // Authenticate via Spring Security
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(dto.email(), dto.password())
            );

            UserEntity user = userRepository.findByEmail(dto.email())
                    .orElseThrow(() -> new IllegalStateException("User context lost post-authentication"));

            // Check if the user has completed email verification
            if (user.isLocked()) {
                return new ApiResponse<>("Login Failed", "Account is not verified. Please check your email.", null, "403", null);
            }

            UserSummary userSummary = new UserSummary(
                    user.getFirstName(),
                    user.getLastName(),
                    user.getEmail(),
                    user.getUsername(),
                    user.getAvatarUrl()
            );

            String accessToken = jwtService.generateToken(user);
            String refreshToken = UUID.randomUUID().toString();

            long daysValid = dto.rememberMe() ? REMEMBER_ME_VALID_DAYS : VALID_DAYS;
            
            // Clear existing refresh tokens to support single-session-per-user or security cleanup
            refreshTokenRepository.deleteAllByUser(user);
            refreshTokenRepository.flush(); 

            RefreshTokenEntity refreshTokenEntity = RefreshTokenEntity.builder()
                    .token(refreshToken)
                    .user(user)
                    .expiryDate(OffsetDateTime.now().plusDays(daysValid))
                    .build();
            
            refreshTokenRepository.save(refreshTokenEntity);

            log.info("Authentication successful for user: {}", user.getEmail());

            return new ApiResponse<>(
                    "Login Successful",
                    "User authenticated successfully",
                    new LoginResponseDTO(accessToken, refreshToken, null, userSummary),
                    "200",
                    null
            );

        } catch (AuthenticationException e) {
            log.warn("Authentication failed for email: {}", dto.email());
            return new ApiResponse<>("Login Failed", "Invalid email or password", null, "401", null);
        } catch (Exception e) {
            log.error("Unhandled exception during login flow: ", e);
            return new ApiResponse<>("Error", "Internal server error during login", null, "500", null);
        }
    }

    @Transactional
    public ApiResponse<AccountVerificationDTO> verifyAccount(AccountVerificationDTO dto){
        VerificationTokenEntity tokenEntity = verificationTokenRepository.findByToken(dto.verificationToken())
                .orElse(null);

        if (tokenEntity == null || !tokenEntity.getUser().getEmail().equals(dto.email())) {
            return new ApiResponse<>("Verification Failed", "Invalid token or email association", null, "400", null);
        }

        if (tokenEntity.isExpired()) {
            return new ApiResponse<>("Verification Failed", "Verification token has expired", null, "400", null);
        }

        UserEntity user = tokenEntity.getUser();
        user.setLocked(false);
        userRepository.save(user);

        // One-time use: consume the token immediately upon success
        verificationTokenRepository.delete(tokenEntity);
        
        log.info("Account verified successfully for user: {}", user.getEmail());
        return new ApiResponse<>("Verification Successful", "Your account is now active.", null, "200", null);
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
            log.info("User {} logged out, session invalidated.", user.getEmail());
            return true;
        } catch (Exception e) {
            log.error("Logout failure: ", e);
            return false;
        }
    }
}