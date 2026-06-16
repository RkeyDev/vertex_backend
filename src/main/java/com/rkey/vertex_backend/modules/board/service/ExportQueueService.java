package com.rkey.vertex_backend.modules.board.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rkey.vertex_backend.modules.board.models.dto.ExportRequestDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Owns all interactions with the export Redis queue.
 * Mirrors the Python RedisManager.EXPORT_QUEUE_KEY / rpush contract
 * so the vertex_export_worker can blpop items off the left end.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExportQueueService {

    /** Must match RedisManager.EXPORT_QUEUE_KEY in the Python worker. */
    public static final String EXPORT_QUEUE_KEY = "export:queue";

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Serialises the export request to JSON and pushes it to the right end
     * of the list — matching Python's {@code rpush}. The worker uses
     * {@code blpop} (left-pop), giving FIFO semantics.
     *
     * @param request the fully-built export request
     * @throws ExportQueueException if serialisation or the Redis write fails
     */
    public void enqueue(ExportRequestDTO request) {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException e) {
            throw new ExportQueueException(
                "Failed to serialise ExportRequest " + request.requestId(), e);
        }

        try {
            Long queueLength = stringRedisTemplate.opsForList().rightPush(EXPORT_QUEUE_KEY, payload);
            log.info("Enqueued export request [id={}, board={}, type={}] — queue depth: {}",
                     request.requestId(), request.boardId(), request.fileType(), queueLength);
        } catch (Exception e) {
            throw new ExportQueueException(
                "Redis write failed for export request " + request.requestId(), e);
        }
    }

    /** Checked domain exception — callers decide whether to surface a 500 or retry. */
    public static final class ExportQueueException extends RuntimeException {
        public ExportQueueException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}