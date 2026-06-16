package com.rkey.vertex_backend.modules.board.models.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.rkey.vertex_backend.modules.board.models.enums.FileType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Inbound HTTP body for POST /api/v1/board/export-board.
 * Intentionally minimal — sensitive fields (JWT, email) are resolved
 * server-side from the security context, never trusted from the client.
 */
public record ExportBoardRequestDTO(

    @NotBlank
    @JsonProperty("board_id")
    String boardId,

    @NotNull
    @JsonProperty("file_type")
    FileType fileType

) {}