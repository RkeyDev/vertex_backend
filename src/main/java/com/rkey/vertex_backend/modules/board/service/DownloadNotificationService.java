package com.rkey.vertex_backend.modules.board.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rkey.vertex_backend.modules.board.models.dto.DownloadNotificationDTO;
import com.rkey.vertex_backend.modules.board.models.dto.DownloadReadyDTO;
import com.rkey.vertex_backend.modules.board.repository.BoardRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Base64;

/**
 * Handles the two responsibilities that follow a completed export:
 *
 * <ol>
 *   <li>Persist the {@link DownloadReadyDTO} in Redis so the
 *       {@code /api/v1/board/download/{requestId}} endpoint can locate and
 *       stream the file when the client calls it.</li>
 *   <li>Push a {@link DownloadNotificationDTO} over STOMP to the requesting
 *       user's personal topic so the frontend can trigger the download
 *       automatically without polling.</li>
 * </ol>
 *
 * <p>The Redis key uses a short TTL — it only needs to survive the round-trip
 * from the STOMP notification to the HTTP download call (typically a few
 * seconds), but we keep it generous to handle slow or mobile clients.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DownloadNotificationService {

    /**
     * Redis key pattern for pending download metadata.
     * Full key: {@code download:pending:{requestId}}
     */
    private static final String PENDING_KEY_PREFIX = "download:pending:";

    /** TTL for pending download entries — generous to handle slow clients. */
    private static final Duration PENDING_TTL = Duration.ofMinutes(30);

    /** STOMP topic the client subscribes to for download-ready events. */
    private static final String STOMP_TOPIC_PATTERN = "/topic/user/%s/download-ready";

    private final StringRedisTemplate redisTemplate;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;
    private final BoardRepository boardRepository;

    @Value("${export.output.root:/app/exports}")
    private String exportOutputRoot;

    /**
     * Called by {@link DownloadQueuePoller} for each item it pops from the
     * download queue.
     *
     * @param dto the deserialised payload from the Python worker
     */
    public void handleDownloadReady(DownloadReadyDTO dto) {
        if (isThumbnail(dto)) {
            persistBoardThumbnail(dto);
            return;
        }

        persistPendingDownload(dto);
        broadcastNotification(dto);
    }

    /**
     * Retrieves a stored {@link DownloadReadyDTO} by its request ID.
     * Called by the download controller before streaming the file.
     *
     * @param requestId the export request ID from the client's download call
     * @return the stored DTO, or {@code null} if expired / not found
     */
    public DownloadReadyDTO getPendingDownload(String requestId) {
        String key = PENDING_KEY_PREFIX + requestId;
        try {
            String raw = redisTemplate.opsForValue().get(key);
            if (raw == null) return null;
            return objectMapper.readValue(raw, DownloadReadyDTO.class);
        } catch (Exception e) {
            log.error("Failed to retrieve pending download [requestId={}]: {}", requestId, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Removes the pending-download entry once the file has been streamed.
     * Prevents duplicate downloads and cleans up Redis eagerly before TTL.
     *
     * @param requestId the export request ID
     */
    public void removePendingDownload(String requestId) {
        try {
            redisTemplate.delete(PENDING_KEY_PREFIX + requestId);
        } catch (Exception e) {
            log.warn("Failed to remove pending download entry [requestId={}]: {}", requestId, e.getMessage());
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ──────────────────────────────────────────────────────────────────────────

    private void persistBoardThumbnail(DownloadReadyDTO dto) {
        try {
            Path root = Paths.get(exportOutputRoot).toAbsolutePath().normalize();
            Path imagePath = root.resolve(dto.outputPath()).normalize();

            if (!imagePath.startsWith(root)) {
                log.warn("Rejected thumbnail outside export root [requestId={}, path={}]", dto.requestId(), imagePath);
                return;
            }

            byte[] bytes = Files.readAllBytes(imagePath);
            String dataUrl = "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(bytes);

            boardRepository.findByToken(dto.boardId()).ifPresentOrElse(board -> {
                board.setThumbnailDataUrl(dataUrl);
                boardRepository.save(board);
                log.info("Board thumbnail saved [board={}, requestId={}, bytes={}]",
                        dto.boardId(), dto.requestId(), bytes.length);
            }, () -> log.warn("Thumbnail completed for unknown board token [board={}, requestId={}]",
                    dto.boardId(), dto.requestId()));
        } catch (Exception e) {
            log.error("Failed to persist board thumbnail [requestId={}, board={}]: {}",
                    dto.requestId(), dto.boardId(), e.getMessage(), e);
        }
    }

    private static boolean isThumbnail(DownloadReadyDTO dto) {
        return dto.fileType() != null && "JPEG_THUMBNAIL".equalsIgnoreCase(dto.fileType());
    }

    private void persistPendingDownload(DownloadReadyDTO dto) {
        String key = PENDING_KEY_PREFIX + dto.requestId();
        try {
            String serialised = objectMapper.writeValueAsString(dto);
            redisTemplate.opsForValue().set(key, serialised, PENDING_TTL);
            log.debug("Persisted pending download [key={}, ttl={}]", key, PENDING_TTL);
        } catch (Exception e) {
            log.error("Failed to persist pending download [requestId={}]: {}", dto.requestId(), e.getMessage(), e);
        }
    }

    private void broadcastNotification(DownloadReadyDTO dto) {
        // The frontend Axios instance already has /api/v1 baked into its
        // baseURL, so this path must be relative to that prefix only.
        String downloadUrl = "/board/download/" + dto.requestId();
        DownloadNotificationDTO notification = new DownloadNotificationDTO(
                dto.requestId(),
                dto.boardId(),
                dto.fileType(),
                downloadUrl
        );

        String topic = String.format(STOMP_TOPIC_PATTERN, sanitiseEmailForTopic(dto.senderEmail()));
        try {
            messagingTemplate.convertAndSend(topic, notification);
            log.info("Download-ready notification sent [topic={}, requestId={}]", topic, dto.requestId());
        } catch (Exception e) {
            log.error("Failed to send STOMP download notification [topic={}]: {}", topic, e.getMessage(), e);
        }
    }

    /**
     * Sanitises an email address for use as a STOMP topic segment.
     * Replaces {@code @} and {@code .} with {@code _} to avoid path-like
     * ambiguity in the topic string.
     */
    private static String sanitiseEmailForTopic(String email) {
        return email.replace("@", "_at_").replace(".", "_");
    }
}