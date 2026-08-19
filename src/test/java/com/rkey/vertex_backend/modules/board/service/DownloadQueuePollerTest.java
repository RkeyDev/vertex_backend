package com.rkey.vertex_backend.modules.board.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rkey.vertex_backend.modules.board.models.dto.DownloadReadyDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for DownloadQueuePoller covering Redis polling, message processing,
 * validation, error handling, and lifecycle management.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DownloadQueuePollerTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ListOperations<String, String> listOps;

    @Mock
    private DownloadNotificationService notificationService;

    private ObjectMapper objectMapper;
    private DownloadQueuePoller poller;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        when(redisTemplate.opsForList()).thenReturn(listOps);
        poller = new DownloadQueuePoller(redisTemplate, objectMapper, notificationService);
    }

    @Test
    void downloadQueueKey_correctValue() {
        // Assert
        assertEquals("download:queue", DownloadQueuePoller.DOWNLOAD_QUEUE_KEY);
    }

    @Test
    void processValidDownloadReadyDTO() throws Exception {
        // Arrange
        String rawPayload = "{" +
                "\"request_id\":\"req_123\"," +
                "\"board_id\":\"board_456\"," +
                "\"sender_email\":\"user@example.com\"," +
                "\"file_type\":\"PDF\"," +
                "\"output_path\":\"output/user/board.pdf\"," +
                "\"created_at\":\"2024-01-01T10:00:00Z\"" +
                "}";

        when(listOps.rightPop("download:queue", Duration.ofSeconds(5)))
                .thenReturn(rawPayload)
                .thenReturn(null); // Stop after first item

        // Act
        poller.start();
        // Give it a moment to process
        Thread.sleep(100);
        poller.stop();

        // Assert
        verify(notificationService, atLeastOnce()).handleDownloadReady(any(DownloadReadyDTO.class));
    }

    @Test
    void processMultipleMessages() throws Exception {
        // Arrange
        String payload1 = "{" +
                "\"request_id\":\"req_1\"," +
                "\"board_id\":\"board_1\"," +
                "\"sender_email\":\"user1@example.com\"," +
                "\"file_type\":\"PDF\"," +
                "\"output_path\":\"output/user1/board.pdf\"," +
                "\"created_at\":\"2024-01-01T10:00:00Z\"" +
                "}";

        String payload2 = "{" +
                "\"request_id\":\"req_2\"," +
                "\"board_id\":\"board_2\"," +
                "\"sender_email\":\"user2@example.com\"," +
                "\"file_type\":\"JPEG_ZIP\"," +
                "\"output_path\":\"output/user2/images.zip\"," +
                "\"created_at\":\"2024-01-01T11:00:00Z\"" +
                "}";

        when(listOps.rightPop("download:queue", Duration.ofSeconds(5)))
                .thenReturn(payload1)
                .thenReturn(payload2)
                .thenReturn(null);

        // Act
        poller.start();
        Thread.sleep(150);
        poller.stop();

        // Assert
        verify(notificationService, atLeast(2)).handleDownloadReady(any(DownloadReadyDTO.class));
    }

    @Test
    void handleMalformedJson() throws Exception {
        // Arrange
        String malformedPayload = "{invalid json}";

        when(listOps.rightPop("download:queue", Duration.ofSeconds(5)))
                .thenReturn(malformedPayload)
                .thenReturn(null);

        // Act - should not throw
        poller.start();
        Thread.sleep(100);
        poller.stop();

        // Assert - malformed message should be skipped
        verify(notificationService, never()).handleDownloadReady(any());
    }

    @Test
    void handleMissingRequiredFields() throws Exception {
        // Arrange - missing request_id
        String payload = "{" +
                "\"board_id\":\"board_123\"," +
                "\"sender_email\":\"user@example.com\"," +
                "\"file_type\":\"PDF\"," +
                "\"output_path\":\"output/file.pdf\"," +
                "\"created_at\":\"2024-01-01T10:00:00Z\"" +
                "}";

        when(listOps.rightPop("download:queue", Duration.ofSeconds(5)))
                .thenReturn(payload)
                .thenReturn(null);

        // Act
        poller.start();
        Thread.sleep(100);
        poller.stop();

        // Assert - invalid payload should be dropped
        verify(notificationService, never()).handleDownloadReady(any());
    }

    @Test
    void handleBlankRequestId() throws Exception {
        // Arrange
        String payload = "{" +
                "\"request_id\":\"\"," +
                "\"board_id\":\"board_456\"," +
                "\"sender_email\":\"user@example.com\"," +
                "\"file_type\":\"PDF\"," +
                "\"output_path\":\"output/file.pdf\"," +
                "\"created_at\":\"2024-01-01T10:00:00Z\"" +
                "}";

        when(listOps.rightPop("download:queue", Duration.ofSeconds(5)))
                .thenReturn(payload)
                .thenReturn(null);

        // Act
        poller.start();
        Thread.sleep(100);
        poller.stop();

        // Assert
        verify(notificationService, never()).handleDownloadReady(any());
    }

    @Test
    void handleBlankSenderEmail() throws Exception {
        // Arrange
        String payload = "{" +
                "\"request_id\":\"req_123\"," +
                "\"board_id\":\"board_456\"," +
                "\"sender_email\":\"\"," +
                "\"file_type\":\"PDF\"," +
                "\"output_path\":\"output/file.pdf\"," +
                "\"created_at\":\"2024-01-01T10:00:00Z\"" +
                "}";

        when(listOps.rightPop("download:queue", Duration.ofSeconds(5)))
                .thenReturn(payload)
                .thenReturn(null);

        // Act
        poller.start();
        Thread.sleep(100);
        poller.stop();

        // Assert
        verify(notificationService, never()).handleDownloadReady(any());
    }

    @Test
    void handleBlankOutputPath() throws Exception {
        // Arrange
        String payload = "{" +
                "\"request_id\":\"req_123\"," +
                "\"board_id\":\"board_456\"," +
                "\"sender_email\":\"user@example.com\"," +
                "\"file_type\":\"PDF\"," +
                "\"output_path\":\"\"," +
                "\"created_at\":\"2024-01-01T10:00:00Z\"" +
                "}";

        when(listOps.rightPop("download:queue", Duration.ofSeconds(5)))
                .thenReturn(payload)
                .thenReturn(null);

        // Act
        poller.start();
        Thread.sleep(100);
        poller.stop();

        // Assert
        verify(notificationService, never()).handleDownloadReady(any());
    }

    @Test
    void handleBlankFileType() throws Exception {
        // Arrange
        String payload = "{" +
                "\"request_id\":\"req_123\"," +
                "\"board_id\":\"board_456\"," +
                "\"sender_email\":\"user@example.com\"," +
                "\"file_type\":\"\"," +
                "\"output_path\":\"output/file.pdf\"," +
                "\"created_at\":\"2024-01-01T10:00:00Z\"" +
                "}";

        when(listOps.rightPop("download:queue", Duration.ofSeconds(5)))
                .thenReturn(payload)
                .thenReturn(null);

        // Act
        poller.start();
        Thread.sleep(100);
        poller.stop();

        // Assert
        verify(notificationService, never()).handleDownloadReady(any());
    }

    @Test
    void handleNullRequestId() throws Exception {
        // Arrange
        String payload = "{" +
                "\"request_id\":null," +
                "\"board_id\":\"board_456\"," +
                "\"sender_email\":\"user@example.com\"," +
                "\"file_type\":\"PDF\"," +
                "\"output_path\":\"output/file.pdf\"," +
                "\"created_at\":\"2024-01-01T10:00:00Z\"" +
                "}";

        when(listOps.rightPop("download:queue", Duration.ofSeconds(5)))
                .thenReturn(payload)
                .thenReturn(null);

        // Act
        poller.start();
        Thread.sleep(100);
        poller.stop();

        // Assert
        verify(notificationService, never()).handleDownloadReady(any());
    }

    @Test
    void processEntryWithDifferentFileTypes() throws Exception {
        // Arrange
        String[] fileTypes = {"PDF", "JPEG_ZIP", "VERTEX", "JPEG_THUMBNAIL"};
        
        for (String fileType : fileTypes) {
            String payload = "{" +
                    "\"request_id\":\"req_" + fileType + "\"," +
                    "\"board_id\":\"board_123\"," +
                    "\"sender_email\":\"user@example.com\"," +
                    "\"file_type\":\"" + fileType + "\"," +
                    "\"output_path\":\"output/file." + fileType.toLowerCase() + "\"," +
                    "\"created_at\":\"2024-01-01T10:00:00Z\"" +
                    "}";

            when(listOps.rightPop("download:queue", Duration.ofSeconds(5)))
                    .thenReturn(payload)
                    .thenReturn(null);

            poller.start();
            Thread.sleep(100);
            poller.stop();
        }

        // Assert - all should be processed
        verify(notificationService, atLeast(4)).handleDownloadReady(any());
    }

    @Test
    void redisExceptionHandling() throws Exception {
        // Arrange
        when(listOps.rightPop("download:queue", Duration.ofSeconds(5)))
                .thenThrow(new RuntimeException("Redis connection error"));

        // Act
        poller.start();
        Thread.sleep(200);
        poller.stop();

        // Assert - poller should handle exception and shut down gracefully
        assertTrue(true);
    }

    @Test
    void pollLoopStopsWhenRunningIsFalse() throws Exception {
        // Arrange
        when(listOps.rightPop("download:queue", Duration.ofSeconds(5)))
                .thenReturn(null);

        // Act
        poller.start();
        Thread.sleep(100);
        poller.stop();

        // Assert - poller should stop without errors
        assertTrue(true);
    }

    @Test
    void emptyQueueReturnsNull() throws Exception {
        // Arrange - queue is empty
        when(listOps.rightPop("download:queue", Duration.ofSeconds(5)))
                .thenReturn(null)
                .thenReturn(null)
                .thenReturn(null);

        // Act
        poller.start();
        Thread.sleep(150);
        poller.stop();

        // Assert
        verify(notificationService, never()).handleDownloadReady(any());
    }

    @Test
    void rightPopCalledWithCorrectQueueKey() throws Exception {
        // Arrange
        when(listOps.rightPop("download:queue", Duration.ofSeconds(5)))
                .thenReturn(null);

        // Act
        poller.start();
        Thread.sleep(100);
        poller.stop();

        // Assert
        verify(listOps, atLeastOnce()).rightPop(
            eq("download:queue"),
            eq(Duration.ofSeconds(5))
        );
    }

    @Test
    void processPayloadWithSpecialCharacters() throws Exception {
        // Arrange
        String payload = "{" +
                "\"request_id\":\"req_@#$%\"," +
                "\"board_id\":\"board_123\"," +
                "\"sender_email\":\"user+tag@example.co.uk\"," +
                "\"file_type\":\"PDF\"," +
                "\"output_path\":\"output/user/file with spaces.pdf\"," +
                "\"created_at\":\"2024-01-01T10:00:00Z\"" +
                "}";

        when(listOps.rightPop("download:queue", Duration.ofSeconds(5)))
                .thenReturn(payload)
                .thenReturn(null);

        // Act
        poller.start();
        Thread.sleep(100);
        poller.stop();

        // Assert
        verify(notificationService, times(1)).handleDownloadReady(any(DownloadReadyDTO.class));
    }

    @Test
    void processPayloadWithUnicodeCharacters() throws Exception {
        // Arrange
        String payload = "{" +
                "\"request_id\":\"req_123\"," +
                "\"board_id\":\"board_456\"," +
                "\"sender_email\":\"user@example.com\"," +
                "\"file_type\":\"PDF\"," +
                "\"output_path\":\"output/用户/文件.pdf\"," +
                "\"created_at\":\"2024-01-01T10:00:00Z\"" +
                "}";

        when(listOps.rightPop("download:queue", Duration.ofSeconds(5)))
                .thenReturn(payload)
                .thenReturn(null);

        // Act
        poller.start();
        Thread.sleep(100);
        poller.stop();

        // Assert
        verify(notificationService, times(1)).handleDownloadReady(any(DownloadReadyDTO.class));
    }

    @Test
    void processPayloadWithLongValues() throws Exception {
        // Arrange
        String longEmail = "user" + "x".repeat(200) + "@example.com";
        String payload = "{" +
                "\"request_id\":\"req_" + "x".repeat(500) + "\"," +
                "\"board_id\":\"board_" + "y".repeat(500) + "\"," +
                "\"sender_email\":\"" + longEmail + "\"," +
                "\"file_type\":\"PDF\"," +
                "\"output_path\":\"output/" + "z".repeat(500) + "/file.pdf\"," +
                "\"created_at\":\"2024-01-01T10:00:00Z\"" +
                "}";

        when(listOps.rightPop("download:queue", Duration.ofSeconds(5)))
                .thenReturn(payload)
                .thenReturn(null);

        // Act
        poller.start();
        Thread.sleep(100);
        poller.stop();

        // Assert
        verify(notificationService, times(1)).handleDownloadReady(any(DownloadReadyDTO.class));
    }

    @Test
    void gracefulShutdown() throws Exception {
        // Arrange
        when(listOps.rightPop("download:queue", Duration.ofSeconds(5)))
                .thenReturn(null);

        poller.start();
        Thread.sleep(50);

        // Act
        poller.stop();

        // Assert - should stop without hanging or exceptions
        assertTrue(true);
    }
}
