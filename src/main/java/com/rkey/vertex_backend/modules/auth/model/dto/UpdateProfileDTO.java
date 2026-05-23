package com.rkey.vertex_backend.modules.auth.model.dto;

public record UpdateProfileDTO(
    String firstName,
    String lastName,
    String username,
    String avatarUrl
) 
{}
