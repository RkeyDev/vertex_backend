package com.rkey.vertex_backend.modules.board.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rkey.vertex_backend.core.api.ApiResponse;
import com.rkey.vertex_backend.core.api.board.JoinBoardRoomResponseDTO;
import com.rkey.vertex_backend.core.api.board.OwnedBoardsResponse;
import com.rkey.vertex_backend.modules.board.models.dto.*;
import com.rkey.vertex_backend.modules.board.models.enums.FileType;
import com.rkey.vertex_backend.modules.board.service.BoardService;
import com.rkey.vertex_backend.modules.board.service.BoardStatePatchService;
import com.rkey.vertex_backend.modules.board.service.BoardStatePersistenceScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.security.Principal;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class BoardControllerTest {

    @Mock
    private BoardService boardService;

    @Mock
    private BoardStatePersistenceScheduler boardStatePersistenceScheduler;

    @Mock
    private BoardStatePatchService boardStatePatchService;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private BoardController boardController;

    private Principal principal;
    private final String testEmail = "test@example.com";

    @BeforeEach
    void setUp() {
        principal = () -> testEmail;
    }

    @Test
    void testGetCursorProfiles() {
        ResponseEntity<List<CursorProfileDTO>> response = boardController.getCursorProfiles("token-123");
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void testHandleBoardSync_initialSync() {
        BoardStateDTO dto = new BoardStateDTO("INITIAL_SYNC", "CLIENT");
        when(boardService.getLatestBoardState("token-123")).thenReturn(new BoardStateDTO("{\"valid\":\"json\"}", "SERVER"));

        boardController.handleBoardSync("token-123", dto, principal);

        verify(boardService).ensureUserInActiveSet("token-123", testEmail);
        verify(messagingTemplate).convertAndSend(eq("/topic/board/token-123"), any(BoardStateDTO.class));
    }

    @Test
    void testHandleBoardSync_liveSync() {
        BoardStateDTO dto = new BoardStateDTO("{\"components\":[1]}", "CLIENT");
        when(boardService.canUserSyncBoard("token-123", testEmail)).thenReturn(true);

        boardController.handleBoardSync("token-123", dto, principal);

        verify(messagingTemplate).convertAndSend("/topic/board/token-123", dto);
        verify(boardService).cacheBoardStateForLiveSync("token-123", dto);
        verify(boardStatePersistenceScheduler).scheduleDbPersist("token-123", dto);
    }

    @Test
    void testHandleComponentTransform() {
        ComponentTransformDTO dto = new ComponentTransformDTO("comp-1", 10.0, 20.0, 100.0, 100.0, "sender-1");
        when(boardService.canUserSyncBoard("token-123", testEmail)).thenReturn(true);

        boardController.handleComponentTransform("token-123", dto, principal);

        verify(messagingTemplate).convertAndSend("/topic/board/token-123/transform", dto);
        verify(boardStatePatchService).applyTransformAsync("token-123", dto);
    }

    @Test
    void testHandleJoinBoardRoom_unauthorized() {
        JoinBoardRequestDTO dto = new JoinBoardRequestDTO("token-123", null, null);
        ResponseEntity<?> response = boardController.handleJoinBoardRoom(dto, null);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    void testHandleJoinBoardRoom_success() {
        JoinBoardRequestDTO dto = new JoinBoardRequestDTO("token-123", null, null);
        ApiResponse<JoinBoardRoomResponseDTO> mockRes = new ApiResponse<>("Success", "OK", null, "200", null);
        when(boardService.joinBoardRoom(dto, testEmail)).thenReturn(mockRes);

        ResponseEntity<ApiResponse<JoinBoardRoomResponseDTO>> response = boardController.handleJoinBoardRoom(dto, principal);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void testHandleCreateNewBoard_success() {
        NewBoardDTO dto = new NewBoardDTO("Test Board");
        ApiResponse<Void> mockRes = new ApiResponse<>("Success", "OK", null, "200", null);
        when(boardService.createNewBoard(dto, testEmail)).thenReturn(mockRes);

        ResponseEntity<ApiResponse<Void>> response = boardController.handleCreateNewBoard(dto, principal);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void testHandleImportBoard_success() {
        MockMultipartFile file = new MockMultipartFile("file", "test.vertex", "application/octet-stream", "content".getBytes());
        ApiResponse<JoinBoardRoomResponseDTO> mockRes = new ApiResponse<>("Success", "OK", null, "200", null);
        when(boardService.importBoard(file, testEmail)).thenReturn(mockRes);

        ResponseEntity<ApiResponse<JoinBoardRoomResponseDTO>> response = boardController.handleImportBoard(file, principal);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void testHandleGetOwnedBoards_unauthorized() {
        ResponseEntity<?> response = boardController.handleGetOwnedBoards(null);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void testHandleGetOwnedBoards_success() {
        UserDetails userDetails = User.withUsername(testEmail).password("pass").roles("USER").build();
        ApiResponse<OwnedBoardsResponse> mockRes = new ApiResponse<>("Success", "OK", null, "200", null);
        when(boardService.getOwnedBoards(testEmail)).thenReturn(mockRes);

        ResponseEntity<ApiResponse<OwnedBoardsResponse>> response = boardController.handleGetOwnedBoards(userDetails);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void testHandleExportBoard_success() {
        ExportBoardRequestDTO dto = new ExportBoardRequestDTO("board-123", FileType.PDF);
        ApiResponse<Void> mockRes = new ApiResponse<>("Success", "OK", null, "200", null);
        when(boardService.queueBoardExport("board-123", FileType.PDF, testEmail, "jwt-123")).thenReturn(mockRes);

        ResponseEntity<ApiResponse<Void>> response = boardController.handleExportBoard(dto, "Bearer jwt-123", principal);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void testHandleDeleteBoard_success() {
        ApiResponse<Void> mockRes = new ApiResponse<>("Success", "OK", null, "200", null);
        when(boardService.deleteBoard(1L, testEmail)).thenReturn(mockRes);

        ResponseEntity<ApiResponse<Void>> response = boardController.handleDeleteBoard(1L, principal);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }
}
