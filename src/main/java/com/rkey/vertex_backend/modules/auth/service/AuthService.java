package com.rkey.vertex_backend.modules.auth.service;

import com.rkey.vertex_backend.core.api.ApiResponse;
import com.rkey.vertex_backend.core.api.auth.LoginResponseDTO;
import com.rkey.vertex_backend.core.api.auth.RegistrationResponseDTO;
import com.rkey.vertex_backend.modules.auth.model.dto.AccountVerificationDTO;
import com.rkey.vertex_backend.modules.auth.model.dto.UserLoginDTO;
import com.rkey.vertex_backend.modules.auth.model.dto.UserRegistrationDTO;
import com.rkey.vertex_backend.modules.auth.repository.RefreshTokenRepository;
import com.rkey.vertex_backend.modules.auth.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;

    private final RefreshTokenRepository refreshTokenRepository;

    public ApiResponse<RegistrationResponseDTO> registerUser(UserRegistrationDTO dto) {
        throw new UnsupportedOperationException("Method is not implemented yet");
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
