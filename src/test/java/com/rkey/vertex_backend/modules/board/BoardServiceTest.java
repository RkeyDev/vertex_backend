package com.rkey.vertex_backend.modules.board;

import com.rkey.vertex_backend.core.api.ApiResponse;
import com.rkey.vertex_backend.core.api.board.JoinBoardRoomResponseDTO;
import com.rkey.vertex_backend.core.api.board.OwnedBoardsResponse;
import com.rkey.vertex_backend.modules.board.entity.BoardEntity;
import com.rkey.vertex_backend.modules.board.models.dto.BoardStateDTO;
import com.rkey.vertex_backend.modules.board.models.dto.JoinBoardRequestDTO;
import com.rkey.vertex_backend.modules.board.models.dto.NewBoardDTO;
import com.rkey.vertex_backend.modules.board.repository.BoardRepository;
import com.rkey.vertex_backend.modules.board.service.BoardRoomCacheService;
import com.rkey.vertex_backend.modules.board.service.BoardService;
import com.rkey.vertex_backend.core.api.board.NewBoardRoomResponseDTO;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class BoardServiceTest {

    @Mock
    private BoardRepository boardRepository;

    @Mock
    private BoardRoomCacheService boardRoomCacheService;

    @InjectMocks
    private BoardService boardService;

    private static final String TEST_USER_EMAIL = "john@example.com";
    private static final String TEST_BOARD_TOKEN = "test-board-token-123";
    private static final String TEST_BOARD_NAME  = "My Test Board";
    private static final String TEST_BOARD_JSON  = "{\"elements\":[],\"version\":1}";

    @Nested
    class GetOwnedBoards {

        @Test
        void whenBoardsExist_shouldReturnSuccessResponse() {
            BoardEntity mockBoard = mock(BoardEntity.class);
            when(boardRepository.findAllByOwnerEmail(TEST_USER_EMAIL))
                    .thenReturn(List.of(mockBoard));

            ApiResponse<OwnedBoardsResponse> response = boardService.getOwnedBoards(TEST_USER_EMAIL);

            assertNotNull(response);
            assertEquals("200", response.responseCode());
            assertEquals(OwnedBoardsResponse.SUCCESS_RESPONSE_NAME, response.name());
            assertEquals(OwnedBoardsResponse.SUCCESS_RESPONSE_DESCRIPTION, response.message());
            assertNotNull(response.data());
            verify(boardRepository).findAllByOwnerEmail(TEST_USER_EMAIL);
        }

        @Test
        void whenNoBoardsExist_shouldReturnSuccessResponseWithEmptyList() {
            when(boardRepository.findAllByOwnerEmail(TEST_USER_EMAIL))
                    .thenReturn(Collections.emptyList());

            ApiResponse<OwnedBoardsResponse> response = boardService.getOwnedBoards(TEST_USER_EMAIL);

            assertNotNull(response);
            assertEquals("200", response.responseCode());
            assertEquals(OwnedBoardsResponse.SUCCESS_RESPONSE_NAME, response.name());
            assertNotNull(response.data());
            verify(boardRepository).findAllByOwnerEmail(TEST_USER_EMAIL);
        }

        @Test
        void whenRepositoryReturnsNull_shouldReturnFailedResponse() {
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

    @Nested
    class UpdateBoardState {

        @Test
        void whenUserIsActive_shouldPersistAndReturnSuccess() {
            BoardStateDTO dto = new BoardStateDTO(TEST_BOARD_JSON, "CLIENT");
            when(boardRoomCacheService.getActiveUsers(TEST_BOARD_TOKEN))
                    .thenReturn(Set.of(TEST_USER_EMAIL));

            ApiResponse<NewBoardRoomResponseDTO> response =
                    boardService.updateBoardState(dto, TEST_USER_EMAIL, TEST_BOARD_TOKEN);

            assertEquals("200", response.responseCode());
            verify(boardRoomCacheService).saveBoardData(TEST_BOARD_TOKEN, TEST_BOARD_JSON);
        }

        @Test
        void whenUserIsAnonymous_shouldPersistAndReturnSuccess() {
            BoardStateDTO dto = new BoardStateDTO(TEST_BOARD_JSON, "CLIENT");
            when(boardRoomCacheService.getActiveUsers(TEST_BOARD_TOKEN))
                    .thenReturn(Collections.emptySet());

            ApiResponse<NewBoardRoomResponseDTO> response =
                    boardService.updateBoardState(dto, "anonymous", TEST_BOARD_TOKEN);

            assertEquals("200", response.responseCode());
            verify(boardRoomCacheService).saveBoardData(TEST_BOARD_TOKEN, TEST_BOARD_JSON);
        }

        @Test
        void whenUserIsNotInActiveSet_shouldReturnFailure() {
            BoardStateDTO dto = new BoardStateDTO(TEST_BOARD_JSON, "CLIENT");
            when(boardRoomCacheService.getActiveUsers(TEST_BOARD_TOKEN))
                    .thenReturn(Set.of("other@example.com"));

            ApiResponse<NewBoardRoomResponseDTO> response =
                    boardService.updateBoardState(dto, TEST_USER_EMAIL, TEST_BOARD_TOKEN);

            assertEquals("500", response.responseCode());
            verify(boardRoomCacheService, never()).saveBoardData(anyString(), anyString());
        }

        @Test
        void whenBoardStateDtoIsNull_shouldReturnFailure() {
            ApiResponse<NewBoardRoomResponseDTO> response =
                    boardService.updateBoardState(null, TEST_USER_EMAIL, TEST_BOARD_TOKEN);

            assertEquals("500", response.responseCode());
            verifyNoInteractions(boardRoomCacheService);
        }

        @Test
        void whenBoardStateJsonIsNull_shouldReturnFailure() {
            BoardStateDTO dto = new BoardStateDTO(null, "CLIENT");

            ApiResponse<NewBoardRoomResponseDTO> response =
                    boardService.updateBoardState(dto, TEST_USER_EMAIL, TEST_BOARD_TOKEN);

            assertEquals("500", response.responseCode());
            verifyNoInteractions(boardRoomCacheService);
        }
    }

    @Nested
    class CreateNewBoard {

        @Test
        void whenValidInput_shouldSaveAndReturnSuccess() {
            NewBoardDTO dto = new NewBoardDTO(TEST_BOARD_NAME);
            when(boardRepository.save(any(BoardEntity.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            ApiResponse<Void> response = boardService.createNewBoard(dto, TEST_USER_EMAIL);

            assertEquals("200", response.responseCode());
            verify(boardRepository).save(any(BoardEntity.class));
        }

        @Test
        void whenDataAccessExceptionThrown_shouldReturnDatabaseError() {
            NewBoardDTO dto = new NewBoardDTO(TEST_BOARD_NAME);
            when(boardRepository.save(any(BoardEntity.class)))
                    .thenThrow(new DataIntegrityViolationException("constraint violation"));

            ApiResponse<Void> response = boardService.createNewBoard(dto, TEST_USER_EMAIL);

            assertEquals("500", response.responseCode());
            assertEquals("Database Error", response.name());
        }

        @Test
        void whenUnexpectedExceptionThrown_shouldReturnInternalServerError() {
            NewBoardDTO dto = new NewBoardDTO(TEST_BOARD_NAME);
            when(boardRepository.save(any(BoardEntity.class)))
                    .thenThrow(new RuntimeException("unexpected"));

            ApiResponse<Void> response = boardService.createNewBoard(dto, TEST_USER_EMAIL);

            assertEquals("500", response.responseCode());
            assertEquals("Internal Server Error", response.name());
        }
    }

    @Nested
    class JoinBoardRoom {

        private BoardEntity buildBoard() {
            BoardEntity board = mock(BoardEntity.class);
            when(board.getToken()).thenReturn(TEST_BOARD_TOKEN);
            when(board.getBoardName()).thenReturn(TEST_BOARD_NAME);
            when(board.getOwnerEmail()).thenReturn(TEST_USER_EMAIL);
            when(board.getJsonData()).thenReturn(TEST_BOARD_JSON);
            return board;
        }

        @Test
        void whenResolvingByToken_andRoomIsActive_shouldJoinSuccessfully() {
            JoinBoardRequestDTO request = new JoinBoardRequestDTO(TEST_BOARD_TOKEN, null, null);
            BoardEntity board = buildBoard();
            when(boardRepository.findByToken(TEST_BOARD_TOKEN)).thenReturn(Optional.of(board));
            when(boardRoomCacheService.isRoomActive(TEST_BOARD_TOKEN)).thenReturn(true);

            ApiResponse<JoinBoardRoomResponseDTO> response =
                    boardService.joinBoardRoom(request, TEST_USER_EMAIL);

            assertEquals("200", response.responseCode());
            assertNotNull(response.data());
            assertEquals(TEST_BOARD_TOKEN, response.data().boardToken());
            verify(boardRoomCacheService).addUserToActiveSet(TEST_BOARD_TOKEN, TEST_USER_EMAIL);
            verify(boardRoomCacheService, never()).saveBoardData(anyString(), anyString());
        }

        @Test
        void whenResolvingByToken_andRoomIsCold_shouldInitializeCacheWithPersistedData() {
            JoinBoardRequestDTO request = new JoinBoardRequestDTO(TEST_BOARD_TOKEN, null, null);
            BoardEntity board = buildBoard();
            when(boardRepository.findByToken(TEST_BOARD_TOKEN)).thenReturn(Optional.of(board));
            when(boardRoomCacheService.isRoomActive(TEST_BOARD_TOKEN)).thenReturn(false);

            boardService.joinBoardRoom(request, TEST_USER_EMAIL);

            verify(boardRoomCacheService).saveBoardData(TEST_BOARD_TOKEN, TEST_BOARD_JSON);
        }

        @Test
        void whenResolvingByToken_andRoomIsCold_andBoardHasNoData_shouldNotWriteToCache() {
            JoinBoardRequestDTO request = new JoinBoardRequestDTO(TEST_BOARD_TOKEN, null, null);
            BoardEntity board = mock(BoardEntity.class);
            when(board.getToken()).thenReturn(TEST_BOARD_TOKEN);
            when(board.getOwnerEmail()).thenReturn(TEST_USER_EMAIL);
            when(board.getJsonData()).thenReturn(null);
            when(boardRepository.findByToken(TEST_BOARD_TOKEN)).thenReturn(Optional.of(board));
            when(boardRoomCacheService.isRoomActive(TEST_BOARD_TOKEN)).thenReturn(false);

            boardService.joinBoardRoom(request, TEST_USER_EMAIL);

            verify(boardRoomCacheService, never()).saveBoardData(anyString(), anyString());
        }

        @Test
        void whenResolvingByNameAndOwnerEmail_shouldJoinSuccessfully() {
            JoinBoardRequestDTO request = new JoinBoardRequestDTO(null, TEST_BOARD_NAME, TEST_USER_EMAIL);
            BoardEntity board = buildBoard();
            when(boardRepository.findByOwnerEmailAndBoardName(TEST_USER_EMAIL, TEST_BOARD_NAME))
                    .thenReturn(Optional.of(board));
            when(boardRoomCacheService.isRoomActive(TEST_BOARD_TOKEN)).thenReturn(true);

            ApiResponse<JoinBoardRoomResponseDTO> response =
                    boardService.joinBoardRoom(request, TEST_USER_EMAIL);

            assertEquals("200", response.responseCode());
            verify(boardRepository).findByOwnerEmailAndBoardName(TEST_USER_EMAIL, TEST_BOARD_NAME);
        }

        @Test
        void whenBoardNotFoundByToken_shouldReturn404() {
            JoinBoardRequestDTO request = new JoinBoardRequestDTO(TEST_BOARD_TOKEN, null, null);
            when(boardRepository.findByToken(TEST_BOARD_TOKEN)).thenReturn(Optional.empty());

            ApiResponse<JoinBoardRoomResponseDTO> response =
                    boardService.joinBoardRoom(request, TEST_USER_EMAIL);

            assertEquals("404", response.responseCode());
            assertNull(response.data());
        }

        @Test
        void whenBothTokenAndNameAreBlank_shouldReturn404() {
            JoinBoardRequestDTO request = new JoinBoardRequestDTO("", null, null);

            ApiResponse<JoinBoardRoomResponseDTO> response =
                    boardService.joinBoardRoom(request, TEST_USER_EMAIL);

            assertEquals("404", response.responseCode());
        }
    }

    @Nested
    class GetLatestBoardState {

        @Test
        void whenCacheHasData_shouldReturnCachedState() {
            when(boardRoomCacheService.getBoardData(TEST_BOARD_TOKEN)).thenReturn(TEST_BOARD_JSON);

            BoardStateDTO result = boardService.getLatestBoardState(TEST_BOARD_TOKEN);

            assertEquals(TEST_BOARD_JSON, result.boardStateJson());
            verifyNoInteractions(boardRepository);
        }

        @Test
        void whenCacheIsEmpty_shouldFallBackToDb_andRepopulateCache() {
            BoardEntity board = mock(BoardEntity.class);
            when(board.getJsonData()).thenReturn(TEST_BOARD_JSON);
            when(boardRoomCacheService.getBoardData(TEST_BOARD_TOKEN)).thenReturn(null);
            when(boardRepository.findByToken(TEST_BOARD_TOKEN)).thenReturn(Optional.of(board));

            BoardStateDTO result = boardService.getLatestBoardState(TEST_BOARD_TOKEN);

            assertEquals(TEST_BOARD_JSON, result.boardStateJson());
            verify(boardRoomCacheService).saveBoardData(TEST_BOARD_TOKEN, TEST_BOARD_JSON);
        }

        @Test
        void whenCacheIsEmpty_andDbBoardHasNullData_shouldReturnEmptyJson() {
            BoardEntity board = mock(BoardEntity.class);
            when(board.getJsonData()).thenReturn(null);
            when(boardRoomCacheService.getBoardData(TEST_BOARD_TOKEN)).thenReturn(null);
            when(boardRepository.findByToken(TEST_BOARD_TOKEN)).thenReturn(Optional.of(board));

            BoardStateDTO result = boardService.getLatestBoardState(TEST_BOARD_TOKEN);

            assertEquals("{}", result.boardStateJson());
            verify(boardRoomCacheService, never()).saveBoardData(anyString(), anyString());
        }

        @Test
        void whenCacheIsBlank_shouldFallBackToDb() {
            BoardEntity board = mock(BoardEntity.class);
            when(board.getJsonData()).thenReturn(TEST_BOARD_JSON);
            when(boardRoomCacheService.getBoardData(TEST_BOARD_TOKEN)).thenReturn("   ");
            when(boardRepository.findByToken(TEST_BOARD_TOKEN)).thenReturn(Optional.of(board));

            BoardStateDTO result = boardService.getLatestBoardState(TEST_BOARD_TOKEN);

            assertEquals(TEST_BOARD_JSON, result.boardStateJson());
        }

        @Test
        void whenCacheIsEmpty_andBoardNotInDb_shouldReturnEmptyJson() {
            when(boardRoomCacheService.getBoardData(TEST_BOARD_TOKEN)).thenReturn(null);
            when(boardRepository.findByToken(TEST_BOARD_TOKEN)).thenReturn(Optional.empty());

            BoardStateDTO result = boardService.getLatestBoardState(TEST_BOARD_TOKEN);

            assertEquals("{}", result.boardStateJson());
        }
    }

    @Nested
    class SaveBoardInDb {

        @Test
        void whenValidInputAndBoardExists_shouldSaveAndReturnTrue() {
            BoardStateDTO dto = new BoardStateDTO(TEST_BOARD_JSON, "SERVER");
            BoardEntity board = mock(BoardEntity.class);
            when(boardRepository.findByToken(TEST_BOARD_TOKEN)).thenReturn(Optional.of(board));

            boolean result = boardService.saveBoardInDb(TEST_BOARD_TOKEN, dto);

            assertTrue(result);
            verify(board).setJsonData(TEST_BOARD_JSON);
            verify(boardRepository).save(board);
        }

        @Test
        void whenBoardNotFound_shouldThrowAndReturnFalse() {
            BoardStateDTO dto = new BoardStateDTO(TEST_BOARD_JSON, "SERVER");
            when(boardRepository.findByToken(TEST_BOARD_TOKEN)).thenReturn(Optional.empty());

            boolean result = boardService.saveBoardInDb(TEST_BOARD_TOKEN, dto);

            assertFalse(result);
        }

        @Test
        void whenDtoIsNull_shouldReturnFalse() {
            boolean result = boardService.saveBoardInDb(TEST_BOARD_TOKEN, null);

            assertFalse(result);
            verifyNoInteractions(boardRepository);
        }

        @Test
        void whenTokenIsNull_shouldReturnFalse() {
            BoardStateDTO dto = new BoardStateDTO(TEST_BOARD_JSON, "SERVER");

            boolean result = boardService.saveBoardInDb(null, dto);

            assertFalse(result);
            verifyNoInteractions(boardRepository);
        }

        @Test
        void whenJsonIsEmpty_shouldReturnFalse() {
            BoardStateDTO dto = new BoardStateDTO("", "SERVER");

            boolean result = boardService.saveBoardInDb(TEST_BOARD_TOKEN, dto);

            assertFalse(result);
            verifyNoInteractions(boardRepository);
        }

        @Test
        void whenRepositoryThrows_shouldReturnFalse() {
            BoardStateDTO dto = new BoardStateDTO(TEST_BOARD_JSON, "SERVER");
            BoardEntity board = mock(BoardEntity.class);
            when(boardRepository.findByToken(TEST_BOARD_TOKEN)).thenReturn(Optional.of(board));
            doThrow(new RuntimeException("db error")).when(boardRepository).save(board);

            boolean result = boardService.saveBoardInDb(TEST_BOARD_TOKEN, dto);

            assertFalse(result);
        }
    }

    @Nested
    class EnsureUserInActiveSet {

        @Test
        void shouldDelegateToCache() {
            boardService.ensureUserInActiveSet(TEST_BOARD_TOKEN, TEST_USER_EMAIL);

            verify(boardRoomCacheService).addUserToActiveSet(TEST_BOARD_TOKEN, TEST_USER_EMAIL);
        }

        @Test
        void shouldDelegateWithCorrectArguments() {
            String anotherToken = "another-token";
            String anotherEmail = "alice@example.com";

            boardService.ensureUserInActiveSet(anotherToken, anotherEmail);

            verify(boardRoomCacheService).addUserToActiveSet(eq(anotherToken), eq(anotherEmail));
        }
    }
}