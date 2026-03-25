package com.rkey.vertex_backend.modules.auth.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.rkey.vertex_backend.core.api.ApiResponse;
import com.rkey.vertex_backend.core.api.auth.LoginResponseDTO;
import com.rkey.vertex_backend.core.api.auth.RegistrationResponseDTO;
import com.rkey.vertex_backend.modules.auth.model.dto.UserLoginDTO;
import com.rkey.vertex_backend.modules.auth.model.dto.UserLogoutDTO;
import com.rkey.vertex_backend.modules.auth.model.dto.UserRegistrationDTO;
import com.rkey.vertex_backend.modules.auth.service.AuthService;
import com.rkey.vertex_backend.modules.auth.service.TokenCacheService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    
    private final AuthService authService;

    
    private final TokenCacheService tokenCashService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<RegistrationResponseDTO>> handleRegistration(@Valid @RequestBody UserRegistrationDTO dto){
        throw new UnsupportedOperationException("Method is not implemented yet");
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponseDTO>> handleLogin(@Valid @RequestBody UserLoginDTO dto)
    {
        throw new UnsupportedOperationException("Method is not implemented yet");
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> handleLogout(@Valid @RequestBody UserLogoutDTO dto)
    {
        throw new UnsupportedOperationException("Method is not implemented yet");
    }        
    
    

}