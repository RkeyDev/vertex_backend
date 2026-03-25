package com.rkey.vertex_backend.modules.auth.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AccountRole {
    ADMIN("ADMIN"),
    USER("USER");

    private final String value;

    @Override
    public String toString() {
        return value;
    }
}