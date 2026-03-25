package com.rkey.vertex_backend.core.api.auth;

import java.time.OffsetTime;

public record RegistrationResponseDTO(
    String email, 
    OffsetTime expiryDate
) {
    public RegistrationResponseDTO{
        // TODO currently hardcoded, needs to be read from a config file later
        expiryDate = OffsetTime.now().plusMinutes(15); 
    }
}
