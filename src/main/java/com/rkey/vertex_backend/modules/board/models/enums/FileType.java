package com.rkey.vertex_backend.modules.board.models.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

@Getter
@RequiredArgsConstructor
public enum FileType {
    JPEG("JPEG"),
    PDF("PDF"),
    VERTEX("VERTEX");

    @JsonValue
    private final String value;

    public static FileType fromValue(String text) {
        return Arrays.stream(FileType.values())
                .filter(type -> type.value.equalsIgnoreCase(text))
                .findFirst()
                .orElse(null);
    }

    @Override
    public String toString() {
        return String.valueOf(value);
    }
}
