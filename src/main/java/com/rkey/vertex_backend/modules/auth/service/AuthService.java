package com.rkey.vertex_backend.modules.auth.service;

import com.rkey.vertex_backend.core.api.ApiResponse;
import com.rkey.vertex_backend.core.api.auth.LoginResponseDTO;
import com.rkey.vertex_backend.core.api.auth.RegistrationResponseDTO;
import com.rkey.vertex_backend.modules.auth.model.dto.AccountVerificationDTO;
import com.rkey.vertex_backend.modules.auth.model.dto.UserLoginDTO;
import com.rkey.vertex_backend.modules.auth.model.dto.UserRegistrationDTO;
import com.rkey.vertex_backend.modules.auth.repository.RefreshTokenRepository;
import com.rkey.vertex_backend.modules.auth.repository.UserRepository;
import com.rkey.vertex_backend.modules.auth.entity.UserEntity;
import com.rkey.vertex_backend.modules.auth.entity.VerificationTokenEntity;
import com.rkey.vertex_backend.modules.auth.model.enums.AccountRole;
import com.rkey.vertex_backend.modules.auth.repository.VerificationTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Core business logic for authentication and user management.
 */
@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final VerificationTokenRepository verificationTokenRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;

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

    public ApiResponse<LoginResponseDTO> loginUser(UserLoginDTO dto) {
        throw new UnsupportedOperationException("Method is not implemented yet");
    }

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

    public LoginResponseDTO refreshJwtToken(String refreshToken) {
        throw new UnsupportedOperationException("Method is not implemented yet");
    }

    public boolean logout(Long userId) {
        throw new UnsupportedOperationException("Method is not implemented yet");
    }
}