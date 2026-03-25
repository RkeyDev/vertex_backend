package com.rkey.vertex_backend.modules.auth.model.dto;

import jakarta.validation.constraints.NotBlank;

public record UserLogoutDTO(
    @NotBlank(message = "Refresh token is required to perform logout")
    String refreshToken
) {}