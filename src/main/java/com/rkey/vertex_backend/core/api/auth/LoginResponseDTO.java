package com.rkey.vertex_backend.core.api.auth;

import java.time.OffsetTime;

import com.rkey.vertex_backend.modules.auth.model.dto.UserSummary;

public record LoginResponseDTO(
    String accessToken, 
    String refreshToken, 
    OffsetTime expiryDate,
    UserSummary userSummary
) {
    public LoginResponseDTO{
        // TODO currently hardcoded, needs to be read from a config file later
        expiryDate = OffsetTime.now().plusMinutes(15) ;
    }
}
