package com.rkey.vertex_backend.modules.auth.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.rkey.vertex_backend.core.api.ApiResponse;
import com.rkey.vertex_backend.core.api.auth.LoginResponseDTO;
import com.rkey.vertex_backend.core.api.auth.RegistrationResponseDTO;
import com.rkey.vertex_backend.core.api.auth.UpdateProfileResponse;
import com.rkey.vertex_backend.modules.auth.entity.UserEntity;
import com.rkey.vertex_backend.modules.auth.model.dto.AccountVerificationDTO;
import com.rkey.vertex_backend.modules.auth.model.dto.ResendVerificationLinkDTO;
import com.rkey.vertex_backend.modules.auth.model.dto.UpdateProfileDTO;
import com.rkey.vertex_backend.modules.auth.model.dto.UserLoginDTO;
import com.rkey.vertex_backend.modules.auth.model.dto.UserLogoutDTO;
import com.rkey.vertex_backend.modules.auth.model.dto.UserRegistrationDTO;
import com.rkey.vertex_backend.modules.auth.service.AuthService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * REST Controller for handling authentication endpoints.
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    
    private final AuthService authService;
    
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<RegistrationResponseDTO>> handleRegistration(@Valid @RequestBody UserRegistrationDTO dto){
        ApiResponse<RegistrationResponseDTO> response = authService.registerUser(dto);
        
        if ("400".equals(response.responseCode())) {
            return ResponseEntity.badRequest().body(response);
        }
        return ResponseEntity.status(201).body(response);
    }

    @PostMapping("/profile/update")
    public ResponseEntity<ApiResponse<UpdateProfileResponse>> handleUpdateProfile(
            @Valid @RequestBody UpdateProfileDTO dto,
            @org.springframework.security.core.annotation.AuthenticationPrincipal UserEntity user) {
        
        ApiResponse<UpdateProfileResponse> response = authService.updateProfile(dto, user);

        return "200".equals(response.responseCode()) 
                ? ResponseEntity.ok(response) 
                : ResponseEntity.status(Integer.parseInt(response.responseCode())).body(response);
    }

    @PostMapping("/email-verification") 
        public ResponseEntity<ApiResponse<AccountVerificationDTO>> handleVerification(
                @Valid @RequestBody AccountVerificationDTO dto) {
            
            ApiResponse<AccountVerificationDTO> response = authService.verifyAccount(dto);
            
            return "200".equals(response.responseCode()) 
                ? ResponseEntity.ok(response) 
                : ResponseEntity.badRequest().body(response);
        }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponseDTO>> handleLogin(@Valid @RequestBody UserLoginDTO dto) {
        ApiResponse<LoginResponseDTO> response = authService.loginUser(dto);

        return "200".equals(response.responseCode()) 
                ? ResponseEntity.ok(response) 
                : ResponseEntity.badRequest().body(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> handleLogout(@Valid @RequestBody UserLogoutDTO dto) {
        ApiResponse<Void> response = authService.logoutUser(dto);
        return ResponseEntity.ok(response);
    }        


    @PostMapping("/send-verification-link")
    public ResponseEntity<ApiResponse<Void>> handleSendVerificationLink(@Valid @RequestBody ResendVerificationLinkDTO dto){
        ApiResponse<Void> response = authService.sendVerificationLink(dto.emailAddress(),null);
        
        if ("404".equals(response.responseCode())) {
            return ResponseEntity.badRequest().body(response);
        }
        return ResponseEntity.status(200).body(response);
    }
}