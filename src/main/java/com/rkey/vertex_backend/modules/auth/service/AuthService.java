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
import com.rkey.vertex_backend.modules.auth.model.enums.AccountRole;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
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
        throw new UnsupportedOperationException("Method is not implemented yet");
    }

    public LoginResponseDTO refreshJwtToken(String refreshToken) {
        throw new UnsupportedOperationException("Method is not implemented yet");
    }

    public boolean logout(Long userId) {
        throw new UnsupportedOperationException("Method is not implemented yet");
    }
}
