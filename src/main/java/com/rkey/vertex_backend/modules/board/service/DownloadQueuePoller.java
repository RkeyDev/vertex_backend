package com.rkey.vertex_backend.modules.board.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rkey.vertex_backend.modules.board.models.dto.DownloadReadyDTO;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Background component that continuously polls the {@code download:queue}
 * Redis list for completed export notifications pushed by the Python worker.
 *
 * <p>Uses a single virtual-thread executor so the blocking BRPOP call never
 * occupies a platform thread, keeping the approach consistent with the rest of
 * the Vertex backend's async strategy.
 *
 * <p>On each item popped the work is delegated to
 * {@link DownloadNotificationService}, which stores the metadata and fans out a
 * STOMP notification to the client.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DownloadQueuePoller {

    /** Must match StorageService.DOWNLOAD_QUEUE_KEY in the Python worker. */
    public static final String DOWNLOAD_QUEUE_KEY = "download:queue";

    /**
     * How long BRPOP will block waiting for an item before returning null and
     * looping again.  A short timeout keeps shutdown responsive without
     * hammering Redis with empty polls.
     */
    private static final Duration BRPOP_TIMEOUT = Duration.ofSeconds(5);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final DownloadNotificationService notificationService;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private ExecutorService executor;

    @PostConstruct
    public void start() {
        running.set(true);
        executor = Executors.newVirtualThreadPerTaskExecutor();
        executor.submit(this::pollLoop);
        log.info("DownloadQueuePoller started — watching '{}'", DOWNLOAD_QUEUE_KEY);
    }

    @PreDestroy
    public void stop() {
        running.set(false);
        executor.shutdown();
        try {
            if (!executor.awaitTermination(BRPOP_TIMEOUT.toSeconds() + 2, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("DownloadQueuePoller stopped.");
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Private
    // ──────────────────────────────────────────────────────────────────────────

    private void pollLoop() {
        while (running.get()) {
            try {
                // BRPOP blocks up to BRPOP_TIMEOUT then returns null — keeps
                // the loop alive without spinning.
                var entry = redisTemplate.opsForList()
                        .rightPop(DOWNLOAD_QUEUE_KEY, BRPOP_TIMEOUT);

                if (entry == null) continue;

                processEntry(entry);

            } catch (Exception e) {
                Thread.currentThread().interrupt();
                log.error("Unexpected error in download queue poll loop: {}", e.getMessage(), e);
                break;
            }
        }
    }

    private void processEntry(String raw) {
        try {
            DownloadReadyDTO dto = objectMapper.readValue(raw, DownloadReadyDTO.class);

            if (!isValid(dto)) {
                log.warn("Dropped malformed download-ready payload: {}", raw);
                return;
            }

            log.info("Download-ready received [requestId={}, board={}, type={}, user={}]",
                    dto.requestId(), dto.boardId(), dto.fileType(), dto.senderEmail());

            notificationService.handleDownloadReady(dto);

        } catch (Exception e) {
            log.error("Failed to process download-ready payload '{}': {}", raw, e.getMessage(), e);
        }
    }

    private static boolean isValid(DownloadReadyDTO dto) {
        return dto != null
                && dto.requestId()   != null && !dto.requestId().isBlank()
                && dto.senderEmail() != null && !dto.senderEmail().isBlank()
                && dto.outputPath()  != null && !dto.outputPath().isBlank()
                && dto.fileType()    != null && !dto.fileType().isBlank();
    }
}