package com.rkey.vertex_backend.core.api.auth;

import java.time.OffsetTime;

public record NewTokenResponseDTO(
     String accessToken,
     OffsetTime expiryDate

) {
    public NewTokenResponseDTO{
     // TODO currently hardcoded, needs to be read from a config file later     
     expiryDate = OffsetTime.now().plusMinutes(15);
    }
}
