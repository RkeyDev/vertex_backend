package com.rkey.vertex_backend.modules.board.models.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Mirrors the Python {@code DownloadReadyPayload} dataclass pushed by the
 * {@code vertex_export_worker} onto the {@code download:queue} Redis list.
 *
 * <p>All fields are nullable so Jackson can deserialise partial payloads
 * without throwing; the consumer validates before acting.
 *
 * @param requestId    Correlates back to the originating {@code ExportRequest}.
 * @param boardId      The board that was exported.
 * @param senderEmail  Used to route the STOMP notification to the correct user.
 * @param fileType     {@code "JPEG"} | {@code "PDF"} | {@code "VERTEX"}.
 * @param outputPath   Absolute path (shared Docker volume) to the finished artifact.
 * @param createdAt    ISO-8601 UTC timestamp for TTL / staleness checks.
 */
public record DownloadReadyDTO(
        @JsonProperty("request_id")   String requestId,
        @JsonProperty("board_id")     String boardId,
        @JsonProperty("sender_email") String senderEmail,
        @JsonProperty("file_type")    String fileType,
        @JsonProperty("output_path")  String outputPath,
        @JsonProperty("created_at")   String createdAt
) {}