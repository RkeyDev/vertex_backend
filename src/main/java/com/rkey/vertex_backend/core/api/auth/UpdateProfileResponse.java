package com.rkey.vertex_backend.core.api.auth;

import com.rkey.vertex_backend.modules.auth.model.dto.UserSummary;

public record UpdateProfileResponse(
    UserSummary userSummary
) {
    
}
