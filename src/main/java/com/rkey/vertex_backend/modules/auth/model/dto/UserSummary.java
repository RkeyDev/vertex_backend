package com.rkey.vertex_backend.modules.auth.model.dto;

public record UserSummary(
    String firstName,
    String lastName,
    String email,
    String username,
    String avatarUrl
) {}
