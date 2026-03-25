package com.rkey.vertex_backend.modules.auth.model.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserRegistrationDTO(
    @NotBlank
    String firstName,

    @NotBlank
    String lastName,

    @NotBlank
    @Email
    String email,

    @NotBlank
    @Size(min = 3, max = 20) //TODO get values from config files later
    String username,

    @NotBlank
    @Size(min = 8) //TODO get value from config files later
    String password,

    boolean rememberMe
) {}