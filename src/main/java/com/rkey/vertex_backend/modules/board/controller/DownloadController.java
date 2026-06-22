package com.rkey.vertex_backend.modules.board.controller;

import com.rkey.vertex_backend.modules.board.models.dto.DownloadReadyDTO;
import com.rkey.vertex_backend.modules.board.service.DownloadNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

/**
 * Streams a completed export artifact to the authenticated client.
 *
 * <p>Flow:
 * <ol>
 *   <li>Client receives a {@code DownloadNotificationDTO} over STOMP.</li>
 *   <li>Client calls {@code GET /api/v1/board/download/{requestId}}.</li>
 *   <li>This controller looks up the pending-download metadata in Redis via
 *       {@link DownloadNotificationService#getPendingDownload}.</li>
 *   <li>The requesting user's email is verified against the stored
 *       {@code senderEmail} — preventing other users from claiming someone
 *       else's export.</li>
 *   <li>The file is resolved against {@code EXPORT_OUTPUT_ROOT} (the shared
 *       Docker volume mount point) and streamed as an attachment.</li>
 *   <li>The Redis entry is removed after a successful stream to prevent
 *       duplicate downloads and free memory eagerly.</li>
 * </ol>
 *
 * <p><b>Docker volume requirement:</b> both the {@code vertex_export_worker}
 * and this service must mount the same host directory. Set the env var
 * {@code EXPORT_OUTPUT_ROOT} to the mount point inside this container
 * (e.g. {@code /app/exports}). The worker writes paths like
 * {@code output/{email}/{boardId}/{boardId}.zip}; this controller prepends
 * the root so the full resolved path becomes
 * {@code /app/exports/output/{email}/{boardId}/{boardId}.zip}.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/board")
@RequiredArgsConstructor
public class DownloadController {

    private static final Map<String, MediaType> MIME_MAP = Map.of(
            "PDF",    MediaType.APPLICATION_PDF,
            "JPEG",   MediaType.parseMediaType("application/zip"),
            "VERTEX", MediaType.APPLICATION_OCTET_STREAM
    );

    private static final Map<String, String> EXTENSION_MAP = Map.of(
            "PDF",    ".pdf",
            "JPEG",   ".zip",
            "VERTEX", ".vertex"
    );

    /**
     * Absolute path to the shared export volume inside this container.
     * Must match the mount point declared in docker-compose.yml.
     * Defaults to {@code /app/exports} if the env var is not set.
     */
    @Value("${export.output.root:/app/exports}")
    private String exportOutputRoot;

    private final DownloadNotificationService notificationService;

    /**
     * Streams the export artifact identified by {@code requestId}.
     *
     * @param requestId   the export request ID from the STOMP notification
     * @param userDetails resolved from the JWT - must match the stored sender
     */
    @GetMapping("/download/{requestId}")
    public ResponseEntity<Resource> downloadExport(
            @PathVariable String requestId,
            @AuthenticationPrincipal UserDetails userDetails) {

        if (userDetails == null) {
            return ResponseEntity.status(401).build();
        }

        DownloadReadyDTO pending = notificationService.getPendingDownload(requestId);

        if (pending == null) {
            log.warn("Download requested for unknown or expired requestId='{}' by user='{}'",
                    requestId, userDetails.getUsername());
            return ResponseEntity.notFound().build();
        }

        if (!pending.senderEmail().equalsIgnoreCase(userDetails.getUsername())) {
            log.warn("Forbidden download attempt: requestId='{}' belongs to '{}', requested by '{}'",
                    requestId, pending.senderEmail(), userDetails.getUsername());
            return ResponseEntity.status(403).build();
        }

        // outputPath from the worker is relative (e.g. output/email/boardId/file.zip).
        // Prepend the shared-volume root so Spring resolves it absolutely.
        Path filePath = Paths.get(exportOutputRoot, pending.outputPath()).normalize();
        File file = filePath.toFile();

        log.debug("Resolving export artifact [requestId='{}', root='{}', relative='{}', absolute='{}']",
                requestId, exportOutputRoot, pending.outputPath(), filePath);

        if (!file.exists() || !file.isFile()) {
            log.error("Export artifact not found on disk [requestId='{}', path='{}']",
                    requestId, filePath);
            return ResponseEntity.internalServerError().build();
        }

        // ── 4. Build response headers
        String fileType  = pending.fileType().toUpperCase();
        MediaType mime   = MIME_MAP.getOrDefault(fileType, MediaType.APPLICATION_OCTET_STREAM);
        String extension = EXTENSION_MAP.getOrDefault(fileType, "");
        String filename  = pending.boardId() + extension;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(
                ContentDisposition.attachment().filename(filename).build());
        headers.setContentType(mime);
        headers.setContentLength(file.length());

        // ── 5. Stream and clean up 
        log.info("Streaming export [requestId='{}', file='{}', size={} bytes, user='{}']",
                requestId, filename, file.length(), userDetails.getUsername());

        notificationService.removePendingDownload(requestId);

        return ResponseEntity.ok()
                .headers(headers)
                .body(new FileSystemResource(file));
    }
}