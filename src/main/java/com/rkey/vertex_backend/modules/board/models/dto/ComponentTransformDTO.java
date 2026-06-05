package com.rkey.vertex_backend.modules.board.models.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

/**
 * Lightweight drag update — send on pointermove instead of the full board JSON.
 * Subscribe to {@code /topic/board/{boardToken}/transform}.
 */
public record ComponentTransformDTO(
    @JsonAlias("id") String componentId,
    @JsonAlias("x") double xPos,
    @JsonAlias("y") double yPos,
    Double width,
    Double height,
    String senderId
) {}
