package com.rkey.vertex_backend.modules.board.models.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.rkey.vertex_backend.modules.board.models.enums.FileType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * DTO representing a board export request.
 * Field names deliberately mirror the Python ExportRequest dataclass
 * so the JSON payload is consumed by the worker without any mapping layer.
 */
public record ExportRequestDTO(

    @JsonProperty("request_id")
    String requestId,

    @NotBlank
    @JsonProperty("board_id")
    String boardId,

    @JsonProperty("sender_jwt")
    String senderJwt,

    @NotBlank
    @JsonProperty("sender_email")
    String senderEmail,

    @NotNull
    @JsonProperty("file_type")
    FileType fileType,

    @JsonProperty("board_metadata")
    String boardMetadata,

    @JsonProperty("canvas_data")
    String canvasData,

    @JsonProperty("request_time_stamp")
    String requestTimeStamp

) {
    // ── Canonical timestamp format matching Python's strftime("%Y-%m-%dT%H-%M-%S") ──
    private static final DateTimeFormatter TIMESTAMP_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss");

    /**
     * Factory: called from the service layer with values it has resolved
     * (JWT, email, board state). The controller passes only what the client sent.
     */
    public static ExportRequestDTO create(
            String boardId,
            String senderJwt,
            String senderEmail,
            FileType fileType,
            String boardMetadata,
            String canvasData
    ) {
        return new ExportRequestDTO(
            UUID.randomUUID().toString(),
            boardId,
            senderJwt,
            senderEmail,
            fileType,
            boardMetadata,
            canvasData,
            OffsetDateTime.now().format(TIMESTAMP_FORMATTER)
        );
    }
}