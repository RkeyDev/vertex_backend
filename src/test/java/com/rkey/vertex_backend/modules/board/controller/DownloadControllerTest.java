package com.rkey.vertex_backend.modules.board.controller;

import com.rkey.vertex_backend.modules.board.entity.BoardEntity;
import com.rkey.vertex_backend.modules.board.models.dto.DownloadReadyDTO;
import com.rkey.vertex_backend.modules.board.repository.BoardRepository;
import com.rkey.vertex_backend.modules.board.service.DownloadNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for DownloadController covering file streaming, authentication,
 * access control, and error handling.
 */
@ExtendWith(MockitoExtension.class)
class DownloadControllerTest {

    @Mock
    private DownloadNotificationService notificationService;

    @Mock
    private BoardRepository boardRepository;

    private DownloadController downloadController;

    @TempDir
    private Path tempDir;

    @BeforeEach
    void setUp() {
        downloadController = new DownloadController(notificationService, boardRepository);
        ReflectionTestUtils.setField(downloadController, "exportOutputRoot", tempDir.toString());
    }

    @Test
    void downloadExport_withValidRequest() throws Exception {
        // Arrange
        String requestId = "req_123";
        String userEmail = "user@example.com";
        UserDetails userDetails = User.builder()
                .username(userEmail)
                .password("password")
                .roles("USER")
                .build();

        // Create a test file
        Path outputDir = tempDir.resolve("output/user@example.com/board_123");
        Files.createDirectories(outputDir);
        Path filePath = outputDir.resolve("export.pdf");
        Files.write(filePath, "PDF content".getBytes());

        DownloadReadyDTO dto = new DownloadReadyDTO(
                requestId, "board_123", userEmail, "PDF",
                "output/user@example.com/board_123/export.pdf", "2024-01-01T10:00:00Z"
        );

        when(notificationService.getPendingDownload(requestId)).thenReturn(dto);
        when(boardRepository.findByToken("board_123")).thenReturn(
            Optional.of(BoardEntity.builder().boardName("Test Board").build())
        );

        // Act
        ResponseEntity<Resource> response = downloadController.downloadExport(requestId, userDetails);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        verify(notificationService, times(1)).removePendingDownload(requestId);
    }

    @Test
    void downloadExport_withoutAuthentication() {
        // Arrange
        String requestId = "req_123";

        // Act
        ResponseEntity<Resource> response = downloadController.downloadExport(requestId, null);

        // Assert
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        verify(notificationService, never()).getPendingDownload(anyString());
    }

    @Test
    void downloadExport_withNonExistentRequest() {
        // Arrange
        String requestId = "nonexistent_req";
        UserDetails userDetails = User.builder()
                .username("user@example.com")
                .password("password")
                .roles("USER")
                .build();

        when(notificationService.getPendingDownload(requestId)).thenReturn(null);

        // Act
        ResponseEntity<Resource> response = downloadController.downloadExport(requestId, userDetails);

        // Assert
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void downloadExport_withUnauthorizedUser() throws Exception {
        // Arrange
        String requestId = "req_123";
        UserDetails userDetails = User.builder()
                .username("attacker@example.com")
                .password("password")
                .roles("USER")
                .build();

        DownloadReadyDTO dto = new DownloadReadyDTO(
                requestId, "board_123", "owner@example.com", "PDF",
                "output/owner@example.com/board_123/export.pdf", "2024-01-01T10:00:00Z"
        );

        when(notificationService.getPendingDownload(requestId)).thenReturn(dto);

        // Act
        ResponseEntity<Resource> response = downloadController.downloadExport(requestId, userDetails);

        // Assert
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        verify(notificationService, never()).removePendingDownload(anyString());
    }

    @Test
    void downloadExport_withThumbnailFileType() {
        // Arrange
        String requestId = "req_thumb";
        UserDetails userDetails = User.builder()
                .username("user@example.com")
                .password("password")
                .roles("USER")
                .build();

        DownloadReadyDTO dto = new DownloadReadyDTO(
                requestId, "board_123", "user@example.com", "JPEG_THUMBNAIL",
                "output/user@example.com/board_123/thumb.jpg", "2024-01-01T10:00:00Z"
        );

        when(notificationService.getPendingDownload(requestId)).thenReturn(dto);

        // Act
        ResponseEntity<Resource> response = downloadController.downloadExport(requestId, userDetails);

        // Assert
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void downloadExport_withCaseInsensitiveEmailMatch() throws Exception {
        // Arrange
        String requestId = "req_123";
        UserDetails userDetails = User.builder()
                .username("User@Example.COM")  // Different case
                .password("password")
                .roles("USER")
                .build();

        Path outputDir = tempDir.resolve("output/user@example.com/board_123");
        Files.createDirectories(outputDir);
        Path filePath = outputDir.resolve("export.pdf");
        Files.write(filePath, "PDF content".getBytes());

        DownloadReadyDTO dto = new DownloadReadyDTO(
                requestId, "board_123", "user@example.com", "PDF",
                "output/user@example.com/board_123/export.pdf", "2024-01-01T10:00:00Z"
        );

        when(notificationService.getPendingDownload(requestId)).thenReturn(dto);
        when(boardRepository.findByToken("board_123")).thenReturn(
            Optional.of(BoardEntity.builder().boardName("Test Board").build())
        );

        // Act
        ResponseEntity<Resource> response = downloadController.downloadExport(requestId, userDetails);

        // Assert - should work with case-insensitive matching
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void downloadExport_withPathTraversalAttempt() throws Exception {
        // Arrange
        String requestId = "req_attack";
        UserDetails userDetails = User.builder()
                .username("user@example.com")
                .password("password")
                .roles("USER")
                .build();

        DownloadReadyDTO dto = new DownloadReadyDTO(
                requestId, "board_123", "user@example.com", "PDF",
                "../../../../../../etc/passwd", "2024-01-01T10:00:00Z"
        );

        when(notificationService.getPendingDownload(requestId)).thenReturn(dto);

        // Act
        ResponseEntity<Resource> response = downloadController.downloadExport(requestId, userDetails);

        // Assert - path should be normalized and checked
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    }

    @Test
    void downloadExport_withNonExistentFile() {
        // Arrange
        String requestId = "req_missing";
        UserDetails userDetails = User.builder()
                .username("user@example.com")
                .password("password")
                .roles("USER")
                .build();

        DownloadReadyDTO dto = new DownloadReadyDTO(
                requestId, "board_123", "user@example.com", "PDF",
                "output/user@example.com/board_123/nonexistent.pdf", "2024-01-01T10:00:00Z"
        );

        when(notificationService.getPendingDownload(requestId)).thenReturn(dto);

        // Act
        ResponseEntity<Resource> response = downloadController.downloadExport(requestId, userDetails);

        // Assert
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    }

    @Test
    void downloadExport_withPdfFileType() throws Exception {
        // Arrange
        String requestId = "req_pdf";
        UserDetails userDetails = User.builder()
                .username("user@example.com")
                .password("password")
                .roles("USER")
                .build();

        Path outputDir = tempDir.resolve("output/user@example.com/board_123");
        Files.createDirectories(outputDir);
        Path filePath = outputDir.resolve("export.pdf");
        Files.write(filePath, "%PDF-1.4 content".getBytes());

        DownloadReadyDTO dto = new DownloadReadyDTO(
                requestId, "board_123", "user@example.com", "PDF",
                "output/user@example.com/board_123/export.pdf", "2024-01-01T10:00:00Z"
        );

        when(notificationService.getPendingDownload(requestId)).thenReturn(dto);
        when(boardRepository.findByToken("board_123")).thenReturn(
            Optional.of(BoardEntity.builder().boardName("Test Board").build())
        );

        // Act
        ResponseEntity<Resource> response = downloadController.downloadExport(requestId, userDetails);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("application/pdf", response.getHeaders().getContentType().toString());
    }

    @Test
    void downloadExport_withJpegZipFileType() throws Exception {
        // Arrange
        String requestId = "req_zip";
        UserDetails userDetails = User.builder()
                .username("user@example.com")
                .password("password")
                .roles("USER")
                .build();

        Path outputDir = tempDir.resolve("output/user@example.com/board_123");
        Files.createDirectories(outputDir);
        Path filePath = outputDir.resolve("images.zip");
        Files.write(filePath, "PK content".getBytes());

        DownloadReadyDTO dto = new DownloadReadyDTO(
                requestId, "board_123", "user@example.com", "JPEG_ZIP",
                "output/user@example.com/board_123/images.zip", "2024-01-01T10:00:00Z"
        );

        when(notificationService.getPendingDownload(requestId)).thenReturn(dto);
        when(boardRepository.findByToken("board_123")).thenReturn(
            Optional.of(BoardEntity.builder().boardName("Test Board").build())
        );

        // Act
        ResponseEntity<Resource> response = downloadController.downloadExport(requestId, userDetails);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void downloadExport_withBoardNameContainingSpecialCharacters() throws Exception {
        // Arrange
        String requestId = "req_special";
        UserDetails userDetails = User.builder()
                .username("user@example.com")
                .password("password")
                .roles("USER")
                .build();

        Path outputDir = tempDir.resolve("output/user@example.com/board_123");
        Files.createDirectories(outputDir);
        Path filePath = outputDir.resolve("export.pdf");
        Files.write(filePath, "PDF content".getBytes());

        DownloadReadyDTO dto = new DownloadReadyDTO(
                requestId, "board_123", "user@example.com", "PDF",
                "output/user@example.com/board_123/export.pdf", "2024-01-01T10:00:00Z"
        );

        when(notificationService.getPendingDownload(requestId)).thenReturn(dto);
        when(boardRepository.findByToken("board_123")).thenReturn(
            Optional.of(BoardEntity.builder().boardName("Test @#$% Board").build())
        );

        // Act
        ResponseEntity<Resource> response = downloadController.downloadExport(requestId, userDetails);

        // Assert - special characters should be sanitized
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getHeaders().getContentDisposition());
    }

    @Test
    void downloadExport_withBoardNameFallback() throws Exception {
        // Arrange
        String requestId = "req_fallback";
        UserDetails userDetails = User.builder()
                .username("user@example.com")
                .password("password")
                .roles("USER")
                .build();

        Path outputDir = tempDir.resolve("output/user@example.com/board_123");
        Files.createDirectories(outputDir);
        Path filePath = outputDir.resolve("export.pdf");
        Files.write(filePath, "PDF content".getBytes());

        DownloadReadyDTO dto = new DownloadReadyDTO(
                requestId, "board_123", "user@example.com", "PDF",
                "output/user@example.com/board_123/export.pdf", "2024-01-01T10:00:00Z"
        );

        when(notificationService.getPendingDownload(requestId)).thenReturn(dto);
        when(boardRepository.findByToken("board_123")).thenReturn(Optional.empty());

        // Act
        ResponseEntity<Resource> response = downloadController.downloadExport(requestId, userDetails);

        // Assert - should fallback to boardId
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getHeaders().getContentDisposition().getFilename().contains("board_123"));
    }

    @Test
    void downloadExport_withLargeFile() throws Exception {
        // Arrange
        String requestId = "req_large";
        UserDetails userDetails = User.builder()
                .username("user@example.com")
                .password("password")
                .roles("USER")
                .build();

        Path outputDir = tempDir.resolve("output/user@example.com/board_123");
        Files.createDirectories(outputDir);
        Path filePath = outputDir.resolve("export.zip");
        // Create a 10MB file
        byte[] largeContent = new byte[10 * 1024 * 1024];
        Files.write(filePath, largeContent);

        DownloadReadyDTO dto = new DownloadReadyDTO(
                requestId, "board_123", "user@example.com", "JPEG_ZIP",
                "output/user@example.com/board_123/export.zip", "2024-01-01T10:00:00Z"
        );

        when(notificationService.getPendingDownload(requestId)).thenReturn(dto);
        when(boardRepository.findByToken("board_123")).thenReturn(
            Optional.of(BoardEntity.builder().boardName("Test Board").build())
        );

        // Act
        ResponseEntity<Resource> response = downloadController.downloadExport(requestId, userDetails);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(10 * 1024 * 1024, response.getHeaders().getContentLength());
    }

    @Test
    void downloadExport_cleansUpPendingEntry() throws Exception {
        // Arrange
        String requestId = "req_cleanup";
        UserDetails userDetails = User.builder()
                .username("user@example.com")
                .password("password")
                .roles("USER")
                .build();

        Path outputDir = tempDir.resolve("output/user@example.com/board_123");
        Files.createDirectories(outputDir);
        Path filePath = outputDir.resolve("export.pdf");
        Files.write(filePath, "PDF".getBytes());

        DownloadReadyDTO dto = new DownloadReadyDTO(
                requestId, "board_123", "user@example.com", "PDF",
                "output/user@example.com/board_123/export.pdf", "2024-01-01T10:00:00Z"
        );

        when(notificationService.getPendingDownload(requestId)).thenReturn(dto);
        when(boardRepository.findByToken("board_123")).thenReturn(
            Optional.of(BoardEntity.builder().boardName("Test Board").build())
        );

        // Act
        ResponseEntity<Resource> response = downloadController.downloadExport(requestId, userDetails);

        // Assert - should clean up the entry
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(notificationService, times(1)).removePendingDownload(requestId);
    }
}
