package com.rkey.vertex_backend.modules.board.models.dto;

/**
 * Payload broadcast over STOMP to the requesting client when their export is
 * ready to download.
 *
 * <p>The frontend uses {@code downloadUrl} to trigger an automatic file
 * download without polling.
 *
 * @param requestId   Correlates the notification to the original export action,
 *                    allowing the UI to dismiss the in-progress indicator for the
 *                    correct board.
 * @param boardId     The exported board's token.
 * @param fileType    {@code "JPEG"} | {@code "PDF"} | {@code "VERTEX"} — lets
 *                    the frontend choose an appropriate icon / label.
 * @param downloadUrl Relative REST URL the client calls to stream the file;
 *                    e.g. {@code /api/v1/board/download/abc123}.
 */
public record DownloadNotificationDTO(
        String requestId,
        String boardId,
        String fileType,
        String downloadUrl
) {}