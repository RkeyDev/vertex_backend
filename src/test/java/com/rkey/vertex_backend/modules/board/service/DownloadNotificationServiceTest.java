package com.rkey.vertex_backend.modules.board.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rkey.vertex_backend.modules.board.entity.BoardEntity;
import com.rkey.vertex_backend.modules.board.models.dto.DownloadNotificationDTO;
import com.rkey.vertex_backend.modules.board.models.dto.DownloadReadyDTO;
import com.rkey.vertex_backend.modules.board.repository.BoardRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for DownloadNotificationService covering download ready handling,
 * Redis persistence, STOMP notifications, thumbnail processing, and error handling.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DownloadNotificationServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private BoardRepository boardRepository;

    @TempDir
    private Path tempDir;

    private DownloadNotificationService notificationService;

    @BeforeEach
    void setUp() throws Exception {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        notificationService = new DownloadNotificationService(
                redisTemplate, messagingTemplate, objectMapper, boardRepository
        );
        ReflectionTestUtils.setField(notificationService, "exportOutputRoot", tempDir.toString());
        doReturn("{}").when(objectMapper).writeValueAsString(any());
    }

    @Test
    void handleDownloadReady_withPdfExport() {
        // Arrange
        DownloadReadyDTO dto = new DownloadReadyDTO(
                "req_123",
                "board_456",
                "user@example.com",
                "PDF",
                "/app/exports/output/file.pdf",
                "2024-01-01T10:00:00Z"
        );

        // Act
        notificationService.handleDownloadReady(dto);

        // Assert
        verify(valueOps, times(1)).set(anyString(), anyString(), any(Duration.class));
        verify(messagingTemplate, times(1)).convertAndSend((String) anyString(), (Object) argThat(n -> n instanceof DownloadNotificationDTO));
    }

    @Test
    void handleDownloadReady_withJpegZipExport() {
        // Arrange
        DownloadReadyDTO dto = new DownloadReadyDTO(
                "req_456",
                "board_789",
                "user@example.com",
                "JPEG_ZIP",
                "/app/exports/output/images.zip",
                "2024-01-01T11:00:00Z"
        );

        // Act
        notificationService.handleDownloadReady(dto);

        // Assert
        verify(valueOps, times(1)).set(anyString(), anyString(), any(Duration.class));
        verify(messagingTemplate, times(1)).convertAndSend((String) anyString(), (Object) argThat(n -> n instanceof DownloadNotificationDTO));
    }

    @Test
    void handleDownloadReady_withThumbnailFileType() {
        // Arrange
        DownloadReadyDTO dto = new DownloadReadyDTO(
                "req_thumb",
                "board_123",
                "user@example.com",
                "JPEG_THUMBNAIL",
                "output/thumbnail.jpg",
                "2024-01-01T12:00:00Z"
        );

        Path thumbnailPath = tempDir.resolve("output/thumbnail.jpg");
        try {
            Files.createDirectories(thumbnailPath.getParent());
            Files.write(thumbnailPath, new byte[] {1, 2, 3});
        } catch (Exception exception) {
            fail(exception);
        }

        BoardEntity board = new BoardEntity();
        board.setId(1L);
        board.setBoardName("Test Board");
        when(boardRepository.findByToken("board_123")).thenReturn(Optional.of(board));

        // Act - Thumbnail processing requires file existence, mock it
        notificationService.handleDownloadReady(dto);

        // Assert - should attempt to process as thumbnail
        verify(boardRepository, times(1)).findByToken("board_123");
    }

    @Test
    void getPendingDownload_withExistingRequest() throws Exception {
        // Arrange
        String requestId = "req_123";
        DownloadReadyDTO expectedDto = new DownloadReadyDTO(
                requestId, "board_456", "user@example.com", "PDF",
                "/app/exports/output/file.pdf", "2024-01-01T10:00:00Z"
        );
        String serialized = "{\"requestId\":\"req_123\"}";
        when(valueOps.get("download:pending:req_123")).thenReturn(serialized);
        when(objectMapper.readValue(serialized, DownloadReadyDTO.class)).thenReturn(expectedDto);

        // Act
        DownloadReadyDTO result = notificationService.getPendingDownload(requestId);

        // Assert
        assertNotNull(result);
        assertEquals(requestId, result.requestId());
        verify(valueOps, times(1)).get("download:pending:req_123");
    }

    @Test
    void getPendingDownload_withNonExistentRequest() {
        // Arrange
        String requestId = "nonexistent_req";
        when(valueOps.get("download:pending:nonexistent_req")).thenReturn(null);

        // Act
        DownloadReadyDTO result = notificationService.getPendingDownload(requestId);

        // Assert
        assertNull(result);
    }

    @Test
    void getPendingDownload_withInvalidJson() throws Exception {
        // Arrange
        String requestId = "req_invalid";
        String invalidJson = "not valid json";
        when(valueOps.get("download:pending:req_invalid")).thenReturn(invalidJson);
        when(objectMapper.readValue(invalidJson, DownloadReadyDTO.class))
                .thenThrow(new RuntimeException("Invalid JSON"));

        // Act
        DownloadReadyDTO result = notificationService.getPendingDownload(requestId);

        // Assert - exception is handled gracefully
        assertNull(result);
    }

    @Test
    void removePendingDownload_withValidRequest() {
        // Arrange
        String requestId = "req_123";
        when(redisTemplate.delete("download:pending:req_123")).thenReturn(true);

        // Act
        notificationService.removePendingDownload(requestId);

        // Assert
        verify(redisTemplate, times(1)).delete("download:pending:req_123");
    }

    @Test
    void removePendingDownload_withRedisException() {
        // Arrange
        String requestId = "error_req";
        when(redisTemplate.delete(anyString())).thenThrow(new RuntimeException("Redis error"));

        // Act - should not throw
        notificationService.removePendingDownload(requestId);

        // Assert - exception handled gracefully
        verify(redisTemplate, times(1)).delete(anyString());
    }

    @Test
    void handleDownloadReady_emailWithSpecialCharacters() {
        // Arrange
        DownloadReadyDTO dto = new DownloadReadyDTO(
                "req_special",
                "board_special",
                "user+tag@example.co.uk",
                "PDF",
                "/app/exports/output/file.pdf",
                "2024-01-01T10:00:00Z"
        );

        // Act
        notificationService.handleDownloadReady(dto);

        // Assert
        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        verify(messagingTemplate, times(1)).convertAndSend((String) topicCaptor.capture(), (Object) any());
        String topic = topicCaptor.getValue();
        assertTrue(topic.contains("user_at_tag_"));
    }

    @Test
    void handleDownloadReady_emailSanitizationInTopic() {
        // Arrange
        DownloadReadyDTO dto = new DownloadReadyDTO(
                "req_123",
                "board_123",
                "john.doe@example.com",
                "PDF",
                "/app/exports/output/file.pdf",
                "2024-01-01T10:00:00Z"
        );

        // Act
        notificationService.handleDownloadReady(dto);

        // Assert - email should be sanitized for STOMP topic
        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        verify(messagingTemplate, times(1)).convertAndSend((String) topicCaptor.capture(), (Object) argThat(n -> n instanceof DownloadNotificationDTO));
        String topic = topicCaptor.getValue();
        assertTrue(topic.contains("john_doe_at_example_com"));
    }

    @Test
    void getPendingDownload_multipleCalls() throws Exception {
        // Arrange
        DownloadReadyDTO dto = new DownloadReadyDTO(
                "req_123", "board_456", "user@example.com", "PDF",
                "/app/exports/output/file.pdf", "2024-01-01T10:00:00Z"
        );
        String serialized = "{\"requestId\":\"req_123\"}";
        when(valueOps.get("download:pending:req_123")).thenReturn(serialized);
        when(objectMapper.readValue(serialized, DownloadReadyDTO.class)).thenReturn(dto);

        // Act
        DownloadReadyDTO result1 = notificationService.getPendingDownload("req_123");
        DownloadReadyDTO result2 = notificationService.getPendingDownload("req_123");

        // Assert
        assertEquals(result1, result2);
        verify(valueOps, times(2)).get("download:pending:req_123");
    }

    @Test
    void handleDownloadReady_vertexFileType() {
        // Arrange
        DownloadReadyDTO dto = new DownloadReadyDTO(
                "req_vertex",
                "board_vertex",
                "user@example.com",
                "VERTEX",
                "/app/exports/output/board.vertex",
                "2024-01-01T13:00:00Z"
        );

        // Act
        notificationService.handleDownloadReady(dto);

        // Assert
        verify(valueOps, times(1)).set(anyString(), anyString(), any(Duration.class));
        verify(messagingTemplate, times(1)).convertAndSend((String) anyString(), (Object) any());
    }

    @Test
    void handleDownloadReady_nullFileType() {
        // Arrange
        DownloadReadyDTO dto = new DownloadReadyDTO(
                "req_null",
                "board_null",
                "user@example.com",
                null,
                "/app/exports/output/file",
                "2024-01-01T14:00:00Z"
        );

        // Act
        notificationService.handleDownloadReady(dto);

        // Assert
        verify(valueOps, times(1)).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void getPendingDownload_withEmptyRequestId() {
        // Arrange
        when(valueOps.get("download:pending:")).thenReturn(null);

        // Act
        DownloadReadyDTO result = notificationService.getPendingDownload("");

        // Assert
        assertNull(result);
    }

    @Test
    void handleDownloadReady_withLongRequestId() {
        // Arrange
        String longRequestId = "req_" + "x".repeat(1000);
        DownloadReadyDTO dto = new DownloadReadyDTO(
                longRequestId,
                "board_456",
                "user@example.com",
                "PDF",
                "/app/exports/output/file.pdf",
                "2024-01-01T10:00:00Z"
        );

        // Act
        notificationService.handleDownloadReady(dto);

        // Assert
        verify(valueOps, times(1)).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void handleDownloadReady_broadcastToCorrectTopic() {
        // Arrange
        DownloadReadyDTO dto = new DownloadReadyDTO(
                "req_123",
                "board_456",
                "test@example.com",
                "PDF",
                "/app/exports/output/file.pdf",
                "2024-01-01T10:00:00Z"
        );

        // Act
        notificationService.handleDownloadReady(dto);

        // Assert
        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<DownloadNotificationDTO> notificationCaptor = ArgumentCaptor.forClass(DownloadNotificationDTO.class);
        verify(messagingTemplate, times(1)).convertAndSend(topicCaptor.capture(), notificationCaptor.capture());

        DownloadNotificationDTO notification = notificationCaptor.getValue();
        assertNotNull(notification);
        assertEquals("req_123", notification.requestId());
        assertEquals("board_456", notification.boardId());
        assertEquals("PDF", notification.fileType());
        assertTrue(notification.downloadUrl().contains("req_123"));
    }

    @Test
    void removePendingDownload_multipleRequests() {
        // Arrange
        when(redisTemplate.delete(anyString())).thenReturn(true);

        // Act
        notificationService.removePendingDownload("req_1");
        notificationService.removePendingDownload("req_2");
        notificationService.removePendingDownload("req_3");

        // Assert
        verify(redisTemplate, times(3)).delete(anyString());
    }

    @Test
    void getPendingDownload_caseInsensitiveFileType() throws Exception {
        // Arrange
        String requestId = "req_123";
        DownloadReadyDTO expectedDto = new DownloadReadyDTO(
                requestId, "board_456", "user@example.com", "pdf",  // lowercase
                "/app/exports/output/file.pdf", "2024-01-01T10:00:00Z"
        );
        String serialized = "{\"requestId\":\"req_123\"}";
        when(valueOps.get("download:pending:req_123")).thenReturn(serialized);
        when(objectMapper.readValue(serialized, DownloadReadyDTO.class)).thenReturn(expectedDto);

        // Act
        DownloadReadyDTO result = notificationService.getPendingDownload(requestId);

        // Assert
        assertNotNull(result);
        assertEquals("pdf", result.fileType());
    }

    @Test
    void handleDownloadReady_validToRedisKeyPattern() {
        // Arrange
        DownloadReadyDTO dto = new DownloadReadyDTO(
                "req_123",
                "board_456",
                "user@example.com",
                "PDF",
                "/app/exports/output/file.pdf",
                "2024-01-01T10:00:00Z"
        );

        // Act
        notificationService.handleDownloadReady(dto);

        // Assert
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOps, times(1)).set(keyCaptor.capture(), anyString(), any(Duration.class));
        String key = keyCaptor.getValue();
        assertTrue(key.startsWith("download:pending:"));
        assertTrue(key.contains("req_123"));
    }
}
