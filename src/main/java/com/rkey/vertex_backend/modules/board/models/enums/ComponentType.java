package com.rkey.vertex_backend.modules.board.models.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

@Getter
@RequiredArgsConstructor
public enum ComponentType {
    // Infrastructure
    SERVER("SERVER"),
    DATABASE("DATABASE"),
    CLOUD("CLOUD"),
    
    // UML Logic
    CLASS("CLASS"),
    INTERFACE("INTERFACE"),
    ENUM("ENUM"),
    
    // Connections
    ARROW("ARROW"),
    DASHED_ARROW("DASHED_ARROW"),
    
    // General
    NOTE("NOTE"),
    ACTOR("ACTOR"),
    PACKAGE("PACKAGE");

    @JsonValue
    private final String value;

    /**
     * Safe lookup for incoming frontend strings.
     * Prevents IllegalArgumentException if the frontend sends a typo.
     */
    public static ComponentType fromValue(String text) {
        return Arrays.stream(ComponentType.values())
                .filter(type -> type.value.equalsIgnoreCase(text))
                .findFirst()
                .orElse(null);
    }

    @Override
    public String toString() {
        return String.valueOf(value);
    }
}