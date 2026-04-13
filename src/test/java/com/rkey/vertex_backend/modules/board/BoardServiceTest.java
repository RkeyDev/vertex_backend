package com.rkey.vertex_backend.modules.board;

import com.rkey.vertex_backend.core.api.ApiResponse;
import com.rkey.vertex_backend.core.api.board.OwnedBoardsResponse;
import com.rkey.vertex_backend.modules.board.entity.BoardEntity;
import com.rkey.vertex_backend.modules.board.repository.BoardRepository;
import com.rkey.vertex_backend.modules.board.service.BoardService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class BoardServiceTest {

    @Mock
    private BoardRepository boardRepository;

    @InjectMocks
    private BoardService boardService;

    private static final String TEST_USER_EMAIL = "john@example.com";

    @Test
    void getOwnedBoards_WhenBoardsExist_ShouldReturnSuccessResponse() {
        BoardEntity mockBoard = mock(BoardEntity.class);
        List<BoardEntity> expectedBoards = List.of(mockBoard);
        when(boardRepository.findAllByOwnerEmail(TEST_USER_EMAIL)).thenReturn(expectedBoards);


        ApiResponse<OwnedBoardsResponse> response = boardService.getOwnedBoards(TEST_USER_EMAIL);

        assertNotNull(response);
        assertEquals("200", response.responseCode());
        assertEquals(OwnedBoardsResponse.SUCCESS_RESPONSE_NAME, response.name());
        assertEquals(OwnedBoardsResponse.SUCCESS_RESPONSE_DESCRIPTION, response.message());
        assertNotNull(response.data());
        

        verify(boardRepository).findAllByOwnerEmail(TEST_USER_EMAIL);
    }

    @Test
    void getOwnedBoards_WhenNoBoardsExist_ShouldReturnSuccessResponseWithEmptyList() {

        when(boardRepository.findAllByOwnerEmail(TEST_USER_EMAIL)).thenReturn(Collections.emptyList());

        ApiResponse<OwnedBoardsResponse> response = boardService.getOwnedBoards(TEST_USER_EMAIL);

        assertNotNull(response);
        assertEquals("200", response.responseCode());
        assertEquals(OwnedBoardsResponse.SUCCESS_RESPONSE_NAME, response.name());
        assertNotNull(response.data());
        
        verify(boardRepository).findAllByOwnerEmail(TEST_USER_EMAIL);
    }

    @Test
    void getOwnedBoards_WhenRepositoryReturnsNull_ShouldReturnFailedResponse() {

        when(boardRepository.findAllByOwnerEmail(TEST_USER_EMAIL)).thenReturn(null);

        ApiResponse<OwnedBoardsResponse> response = boardService.getOwnedBoards(TEST_USER_EMAIL);

        assertNotNull(response);
        assertEquals("400", response.responseCode());
        assertEquals(OwnedBoardsResponse.FAILED_RESPONSE_NAME, response.name());
        assertEquals(OwnedBoardsResponse.FAILED_RESPONSE_DESCRIPTION, response.message());
        assertNull(response.data());
        
        verify(boardRepository).findAllByOwnerEmail(TEST_USER_EMAIL);
    }
}