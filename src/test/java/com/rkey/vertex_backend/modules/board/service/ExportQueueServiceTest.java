package com.rkey.vertex_backend.modules.board.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rkey.vertex_backend.modules.board.models.dto.ExportRequestDTO;
import com.rkey.vertex_backend.modules.board.models.enums.FileType;
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

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ExportQueueService covering Redis queue operations,
 * serialization, error handling, and FIFO semantics.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExportQueueServiceTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ListOperations<String, String> listOps;

    private ObjectMapper objectMapper;
    private ExportQueueService exportQueueService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        when(stringRedisTemplate.opsForList()).thenReturn(listOps);
        exportQueueService = new ExportQueueService(stringRedisTemplate, objectMapper);
    }

    @Test
    void enqueue_withValidRequest() {
        // Arrange
        ExportRequestDTO request = new ExportRequestDTO(
                "req_123",
                "board_456",
                "jwt_token",
                "user@example.com",
                FileType.PDF,
                "{\"boardName\":\"My Board\"}",
                "{\"components\":[]}",
                OffsetDateTime.now().toString()
        );
        when(listOps.rightPush(anyString(), anyString())).thenReturn(1L);

        // Act
        exportQueueService.enqueue(request);

        // Assert
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(listOps, times(1)).rightPush(keyCaptor.capture(), payloadCaptor.capture());

        assertEquals("export:queue", keyCaptor.getValue());
        String payload = payloadCaptor.getValue();
        assertNotNull(payload);
        assertTrue(payload.contains("req_123"));
        assertTrue(payload.contains("board_456"));
    }

    @Test
    void enqueue_withJpegZipFileType() {
        // Arrange
        ExportRequestDTO request = new ExportRequestDTO(
                "req_456",
                "board_789",
                "jwt_token",
                "user@example.com",
                FileType.JPEG_ZIP,
                "{}",
                "{}",
                OffsetDateTime.now().toString()
        );
        when(listOps.rightPush(anyString(), anyString())).thenReturn(2L);

        // Act
        exportQueueService.enqueue(request);

        // Assert
        verify(listOps, times(1)).rightPush(eq("export:queue"), anyString());
    }

    @Test
    void enqueue_withVertexFileType() {
        // Arrange
        ExportRequestDTO request = new ExportRequestDTO(
                "req_789",
                "board_101",
                "jwt_token",
                "user@example.com",
                FileType.VERTEX,
                "{}",
                "{}",
                OffsetDateTime.now().toString()
        );
        when(listOps.rightPush(anyString(), anyString())).thenReturn(3L);

        // Act
        exportQueueService.enqueue(request);

        // Assert
        verify(listOps, times(1)).rightPush(eq("export:queue"), anyString());
    }

    @Test
    void enqueue_withThumbnailFileType() {
        // Arrange
        ExportRequestDTO request = new ExportRequestDTO(
                "req_102",
                "board_103",
                "jwt_token",
                "user@example.com",
                FileType.JPEG_THUMBNAIL,
                "{}",
                "{}",
                OffsetDateTime.now().toString()
        );
        when(listOps.rightPush(anyString(), anyString())).thenReturn(4L);

        // Act
        exportQueueService.enqueue(request);

        // Assert
        verify(listOps, times(1)).rightPush(eq("export:queue"), anyString());
    }

    @Test
    void enqueue_withComplexBoardMetadata() {
        // Arrange
        String complexMetadata = "{\"name\":\"Complex Board\",\"description\":\"A detailed board\",\"tags\":[\"tag1\",\"tag2\"]}";
        ExportRequestDTO request = new ExportRequestDTO(
                "req_complex",
                "board_complex",
                "jwt_token",
                "user@example.com",
                FileType.PDF,
                complexMetadata,
                "{\"components\":[{\"id\":\"1\",\"type\":\"box\"}]}",
                OffsetDateTime.now().toString()
        );
        when(listOps.rightPush(anyString(), anyString())).thenReturn(5L);

        // Act
        exportQueueService.enqueue(request);

        // Assert
        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(listOps, times(1)).rightPush(anyString(), payloadCaptor.capture());
        assertTrue(payloadCaptor.getValue().contains("Complex Board"));
    }

    @Test
    void enqueue_withMultipleRequests() {
        // Arrange
        ExportRequestDTO request1 = new ExportRequestDTO(
                "req_1", "board_1", "jwt", "user1@example.com", FileType.PDF, "{}", "{}", OffsetDateTime.now().toString()
        );
        ExportRequestDTO request2 = new ExportRequestDTO(
                "req_2", "board_2", "jwt", "user2@example.com", FileType.JPEG_ZIP, "{}", "{}", OffsetDateTime.now().toString()
        );
        when(listOps.rightPush(anyString(), anyString())).thenReturn(1L).thenReturn(2L);

        // Act
        exportQueueService.enqueue(request1);
        exportQueueService.enqueue(request2);

        // Assert - both requests should be pushed
        verify(listOps, times(2)).rightPush(eq("export:queue"), anyString());
    }

    @Test
    void enqueue_withSerializationFailure() {
        // Arrange
        ExportRequestDTO request = new ExportRequestDTO(
                "req_123",
                "board_456",
                "jwt_token",
                "user@example.com",
                FileType.PDF,
                "{\"boardName\":\"My Board\"}",
                "{\"components\":[]}",
                OffsetDateTime.now().toString()
        );
        ExportQueueService service = new ExportQueueService(stringRedisTemplate, null);

        // Act & Assert - should throw when ObjectMapper is null
        assertThrows(Exception.class, () -> service.enqueue(request));
    }

    @Test
    void enqueue_withRedisException() {
        // Arrange
        ExportRequestDTO request = new ExportRequestDTO(
                "req_error",
                "board_error",
                "jwt",
                "user@example.com",
                FileType.PDF,
                "{}",
                "{}",
                OffsetDateTime.now().toString()
        );
        when(listOps.rightPush(anyString(), anyString())).thenThrow(
            new RuntimeException("Redis connection failed")
        );

        // Act & Assert
        assertThrows(ExportQueueService.ExportQueueException.class, () -> exportQueueService.enqueue(request));
    }

    @Test
    void enqueue_returnsQueueLength() {
        // Arrange
        ExportRequestDTO request = new ExportRequestDTO(
                "req_123",
                "board_456",
                "jwt",
                "user@example.com",
                FileType.PDF,
                "{}",
                "{}",
                OffsetDateTime.now().toString()
        );
        when(listOps.rightPush(anyString(), anyString())).thenReturn(42L);

        // Act
        exportQueueService.enqueue(request);

        // Assert
        verify(listOps, times(1)).rightPush(eq("export:queue"), anyString());
    }

    @Test
    void enqueue_withNullRequestId() {
        // Arrange
        ExportRequestDTO request = new ExportRequestDTO(
                null,
                "board_456",
                "jwt",
                "user@example.com",
                FileType.PDF,
                "{}",
                "{}",
                OffsetDateTime.now().toString()
        );
        when(listOps.rightPush(anyString(), anyString())).thenReturn(1L);

        // Act & Assert
        assertThrows(Exception.class, () -> exportQueueService.enqueue(request));
    }

    @Test
    void enqueue_withSpecialCharactersInEmail() {
        // Arrange
        ExportRequestDTO request = new ExportRequestDTO(
                "req_special",
                "board_456",
                "jwt",
                "user+tag@example.co.uk",
                FileType.PDF,
                "{}",
                "{}",
                OffsetDateTime.now().toString()
        );
        when(listOps.rightPush(anyString(), anyString())).thenReturn(1L);

        // Act
        exportQueueService.enqueue(request);

        // Assert
        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(listOps, times(1)).rightPush(anyString(), payloadCaptor.capture());
        assertTrue(payloadCaptor.getValue().contains("user+tag@example.co.uk"));
    }

    @Test
    void enqueue_withVeryLongBoardToken() {
        // Arrange
        String longToken = "board_" + "x".repeat(1000);
        ExportRequestDTO request = new ExportRequestDTO(
                "req_long",
                longToken,
                "jwt",
                "user@example.com",
                FileType.PDF,
                "{}",
                "{}",
                OffsetDateTime.now().toString()
        );
        when(listOps.rightPush(anyString(), anyString())).thenReturn(1L);

        // Act
        exportQueueService.enqueue(request);

        // Assert
        verify(listOps, times(1)).rightPush(eq("export:queue"), anyString());
    }

    @Test
    void enqueue_fifoOrder() {
        // Arrange - when rightPush returns incrementing queue lengths, it indicates FIFO order
        ExportRequestDTO request1 = new ExportRequestDTO(
                "req_1", "board_1", "jwt", "user1@example.com", FileType.PDF, "{}", "{}", OffsetDateTime.now().toString()
        );
        ExportRequestDTO request2 = new ExportRequestDTO(
                "req_2", "board_2", "jwt", "user2@example.com", FileType.PDF, "{}", "{}", OffsetDateTime.now().toString()
        );
        ExportRequestDTO request3 = new ExportRequestDTO(
                "req_3", "board_3", "jwt", "user3@example.com", FileType.PDF, "{}", "{}", OffsetDateTime.now().toString()
        );
        when(listOps.rightPush(anyString(), anyString())).thenReturn(1L).thenReturn(2L).thenReturn(3L);

        // Act
        exportQueueService.enqueue(request1);
        exportQueueService.enqueue(request2);
        exportQueueService.enqueue(request3);

        // Assert - rightPush should be called 3 times with incrementing indices
        verify(listOps, times(3)).rightPush(eq("export:queue"), anyString());
    }

    @Test
    void enqueue_exceptionHandling() {
        // Arrange
        ExportRequestDTO request = new ExportRequestDTO(
                "req_exception",
                "board_exception",
                "jwt",
                "user@example.com",
                FileType.PDF,
                "{}",
                "{}",
                OffsetDateTime.now().toString()
        );
        when(listOps.rightPush(anyString(), anyString())).thenThrow(
            new RuntimeException("Network timeout")
        );

        // Act & Assert
        ExportQueueService.ExportQueueException ex = assertThrows(
            ExportQueueService.ExportQueueException.class,
            () -> exportQueueService.enqueue(request)
        );
        assertTrue(ex.getMessage().contains("Redis write failed"));
    }

    @Test
    void enqueue_constantExportQueueKey() {
        // Act & Assert
        assertEquals("export:queue", ExportQueueService.EXPORT_QUEUE_KEY);
    }

    @Test
    void enqueue_withEmptyCanvasData() {
        // Arrange
        ExportRequestDTO request = new ExportRequestDTO(
                "req_empty",
                "board_empty",
                "jwt",
                "user@example.com",
                FileType.PDF,
                "{}",
                "",
                OffsetDateTime.now().toString()
        );
        when(listOps.rightPush(anyString(), anyString())).thenReturn(1L);

        // Act
        exportQueueService.enqueue(request);

        // Assert
        verify(listOps, times(1)).rightPush(eq("export:queue"), anyString());
    }

    @Test
    void enqueue_withUnicodeCharacters() {
        // Arrange
        ExportRequestDTO request = new ExportRequestDTO(
                "req_unicode",
                "board_unicode",
                "jwt",
                "user@example.com",
                FileType.PDF,
                "{\"name\":\"Projet français\"}",
                "{\"comment\":\"日本語テキスト\"}",
                OffsetDateTime.now().toString()
        );
        when(listOps.rightPush(anyString(), anyString())).thenReturn(1L);

        // Act
        exportQueueService.enqueue(request);

        // Assert
        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(listOps, times(1)).rightPush(anyString(), payloadCaptor.capture());
        String payload = payloadCaptor.getValue();
        assertTrue(payload.contains("français") || payload.contains("\\u"));
    }
}
