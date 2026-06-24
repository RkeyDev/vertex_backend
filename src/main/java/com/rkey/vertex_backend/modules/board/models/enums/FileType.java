package com.rkey.vertex_backend.modules.board.models.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

@Getter
@RequiredArgsConstructor
public enum FileType {
    JPEG_ZIP("JPEG_ZIP"),
    PDF("PDF"),
    VERTEX("VERTEX"),
    JPEG_THUMBNAIL("JPEG_THUMBNAIL");

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
