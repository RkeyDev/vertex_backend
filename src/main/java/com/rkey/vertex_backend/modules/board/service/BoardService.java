package com.rkey.vertex_backend.modules.board.service;

import com.rkey.vertex_backend.core.api.ApiResponse;
import com.rkey.vertex_backend.core.api.board.JoinBoardRoomResponseDTO;
import com.rkey.vertex_backend.core.api.board.NewBoardRoomResponseDTO;
import com.rkey.vertex_backend.core.api.board.OwnedBoardsResponse;
import com.rkey.vertex_backend.modules.auth.entity.UserEntity;
import com.rkey.vertex_backend.modules.auth.mapper.UserMapper;
import com.rkey.vertex_backend.modules.auth.model.dto.UserSummary;
import com.rkey.vertex_backend.modules.auth.repository.UserRepository;
import com.rkey.vertex_backend.modules.board.entity.BoardEntity;
import com.rkey.vertex_backend.modules.board.models.dto.BoardStateDTO;
import com.rkey.vertex_backend.modules.board.models.dto.CursorProfileDTO;
import com.rkey.vertex_backend.modules.board.models.dto.ExportRequestDTO;
import com.rkey.vertex_backend.modules.board.models.dto.JoinBoardRequestDTO;
import com.rkey.vertex_backend.modules.board.models.dto.NewBoardDTO;
import com.rkey.vertex_backend.modules.board.models.enums.FileType;
import com.rkey.vertex_backend.modules.board.repository.BoardRepository;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

import org.springframework.dao.DataAccessException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class BoardService {

    private final BoardRepository boardRepository;
    private final BoardRoomCacheService boardRoomCacheService;
    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final SimpMessagingTemplate messagingTemplate;
    private final ExportQueueService exportQueueService;

    // ──────────────────────────────────────────────────────────────────────────
    // Board State
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Updates the board state in Redis. Verified against the active user set.
     */
    public ApiResponse<NewBoardRoomResponseDTO> updateBoardState(
            BoardStateDTO boardStateDTO,
            String updaterEmail,
            String boardToken) {

        if (boardStateDTO == null || boardStateDTO.boardStateJson() == null) {
            return boardStateUpdateFailed();
        }
        if (!canUserSyncBoard(boardToken, updaterEmail)) {
            log.warn("Sync rejected: User {} not in active set for board {}.", updaterEmail, boardToken);
            return boardStateUpdateFailed();
        }

        boardRoomCacheService.saveBoardData(boardToken, boardStateDTO.boardStateJson());
        return new ApiResponse<>("Board State Updated", "Successfully updated board state", null, "200", null);
    }

    /**
     * Hot path for live collaboration: cache in Redis without blocking on PostgreSQL.
     */
    public void cacheBoardStateForLiveSync(String boardToken, BoardStateDTO boardStateDTO) {
        if (boardStateDTO != null && boardStateDTO.boardStateJson() != null) {
            boardRoomCacheService.saveBoardData(boardToken, boardStateDTO.boardStateJson());
        }
    }

    /**
     * Fetches the latest board state — Redis first, DB fallback.
     */
    public BoardStateDTO getLatestBoardState(String boardToken) {
        log.debug("Sync Request: Fetching latest state for token {}", boardToken);

        String cachedData = boardRoomCacheService.getBoardData(boardToken);
        if (cachedData != null && !cachedData.isBlank()) {
            return new BoardStateDTO(cachedData, "SERVER");
        }

        return boardRepository.findByToken(boardToken)
                .map(board -> {
                    String dbData = board.getJsonData();
                    if (dbData != null) {
                        boardRoomCacheService.saveBoardData(boardToken, dbData);
                    }
                    return new BoardStateDTO(dbData != null ? dbData : "{}", "SERVER");
                })
                .orElseGet(() -> {
                    log.error("Sync Request Failed: Board {} not found in database", boardToken);
                    return new BoardStateDTO("{}", "SERVER");
                });
    }

    /**
     * Persists the latest board state from Redis/memory to PostgreSQL.
     */
    public boolean saveBoardInDb(String boardToken, BoardStateDTO boardStateDTO) {
        if (boardStateDTO == null || boardToken == null || boardStateDTO.boardStateJson().isEmpty()) {
            return false;
        }
        try {
            BoardEntity updatedBoard = boardRepository.findByToken(boardToken).orElse(null);
            if (updatedBoard != null) {
                updatedBoard.setJsonData(boardStateDTO.boardStateJson());
                boardRepository.save(updatedBoard);
                log.info("Successfully updated board data for token {}", boardToken);
                return true;
            }
        } catch (Exception e) {
            log.warn("An error occurred while updating board data: {}", e.getMessage());
        }
        return false;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Authorization
    // ──────────────────────────────────────────────────────────────────────────

    public boolean canUserSyncBoard(String boardToken, String updaterEmail) {
        if ("anonymous".equals(updaterEmail)) {
            return true;
        }
        return boardRoomCacheService.getActiveUsers(boardToken).contains(updaterEmail);
    }

    /**
     * Ensures the user is in the active users set — used when reconnecting via
     * STOMP after a page refresh without going through the join-room endpoint.
     */
    public void ensureUserInActiveSet(String boardToken, String userEmail) {
        boardRoomCacheService.addUserToActiveSet(boardToken, userEmail);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Board CRUD
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Creates a new persistent board entity in PostgreSQL.
     */
    @Transactional
    public ApiResponse<Void> createNewBoard(NewBoardDTO newBoardDTO, String ownerEmail) {
        try {
            BoardEntity board = new BoardEntity();
            board.setBoardName(newBoardDTO.boardName());
            board.setOwnerEmail(ownerEmail);
            boardRepository.save(board);
            log.info("Board '{}' successfully created for owner: {}", newBoardDTO.boardName(), ownerEmail);
            return new ApiResponse<>("Board Created", "The board was successfully persisted", null, "200", null);

        } catch (DataAccessException e) {
            log.error("Database error while creating board '{}' for user '{}': {}",
                    newBoardDTO.boardName(), ownerEmail, e.getMessage());
            return new ApiResponse<>("Database Error", "Could not save the board due to a persistence issue", null, "500", null);

        } catch (Exception e) {
            log.error("Unexpected error during board creation: ", e);
            return new ApiResponse<>("Internal Server Error", "An unexpected error occurred", null, "500", null);
        }
    }

    /**
     * Handles joining an existing board. Resolves by token OR (name + email).
     * Initialises the Redis cache if the room is not currently active.
     */
    public ApiResponse<JoinBoardRoomResponseDTO> joinBoardRoom(JoinBoardRequestDTO requestDTO, String userEmail) {
        BoardEntity board = resolveBoard(requestDTO);

        if (board == null) {
            log.warn("Failed to join room: Board not found for request.");
            return new ApiResponse<>(
                "Board Room Join Failed",
                "The requested board does not exist or you do not have access.",
                null, "404", null
            );
        }

        String boardToken = board.getToken();

        if (!boardRoomCacheService.isRoomActive(boardToken)) {
            log.info("Initializing cold cache for board room: {}", boardToken);
            if (board.getJsonData() != null) {
                boardRoomCacheService.saveBoardData(boardToken, board.getJsonData());
            }
        }

        boardRoomCacheService.addUserToActiveSet(boardToken, userEmail);

        UserEntity joiningUser = userRepository.findByEmail(userEmail).orElse(null);
        String displayName = resolveDisplayName(joiningUser, userEmail);
        String avatarUrl = (joiningUser != null && joiningUser.getAvatarUrl() != null)
                ? joiningUser.getAvatarUrl()
                : "";

        CursorProfileDTO currentProfile = BoardProfileRegistry.registerProfile(
                boardToken, null, displayName, avatarUrl);

        UserSummary ownerData = userRepository.findByEmail(board.getOwnerEmail())
                .map(userMapper::getUserSummary)
                .orElseGet(() -> new UserSummary(
                        "First", "Last",
                        board.getOwnerEmail(), board.getOwnerEmail(), null));

        JoinBoardRoomResponseDTO responseData = new JoinBoardRoomResponseDTO(
            board.getBoardName(),
            boardToken,
            ownerData,
            board.getJsonData(),
            BoardProfileRegistry.getProfilesForBoard(boardToken),
            currentProfile.id()
        );

        log.info("Joining user {} assigned profile id: {}", userEmail, currentProfile.id());
        messagingTemplate.convertAndSend("/topic/board/" + boardToken + "/profiles", currentProfile);

        return new ApiResponse<>("Board Room Joined", "Successfully joined board room", responseData, "200", null);
    }

    /**
     * Retrieves all boards owned by a specific user.
     */
    public ApiResponse<OwnedBoardsResponse> getOwnedBoards(String userEmail) {
        List<BoardEntity> boards = boardRepository.findAllByOwnerEmail(userEmail);
        if (boards != null) {
            log.info(OwnedBoardsResponse.SUCCESS_RESPONSE_NAME);
            return new ApiResponse<>(
                OwnedBoardsResponse.SUCCESS_RESPONSE_NAME,
                OwnedBoardsResponse.SUCCESS_RESPONSE_DESCRIPTION,
                new OwnedBoardsResponse(boards), "200", null
            );
        }

        log.warn(OwnedBoardsResponse.FAILED_RESPONSE_DESCRIPTION);
        return new ApiResponse<>(
            OwnedBoardsResponse.FAILED_RESPONSE_NAME,
            OwnedBoardsResponse.FAILED_RESPONSE_DESCRIPTION,
            null, "400", null
        );
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Export
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Builds and enqueues a board export request onto the Redis queue consumed
     * by the {@code vertex_export_worker} Python service.
     *
     * <p>The board state is resolved here so the worker receives a fully
     * self-contained payload and never needs to call back into the backend.
     *
     * @param boardId     the board's stable token
     * @param fileType    desired output format (PDF / JPEG / VERTEX)
     * @param senderEmail resolved from the JWT principal — never trusted from the client
     * @param senderJwt   raw Bearer token, forwarded so the worker can authenticate
     *                    Playwright requests if needed
     * @return an {@link ApiResponse} the controller can return directly
     */
    public ApiResponse<Void> queueBoardExport(
            String boardId,
            FileType fileType,
            String senderEmail,
            String senderJwt) {

        BoardStateDTO boardState = getLatestBoardState(boardId);
        String canvasData  = (boardState != null) ? boardState.boardStateJson() : null;
        String boardMeta   = resolveBoardMetadata(boardId);

        ExportRequestDTO request = ExportRequestDTO.create(
            boardId, senderJwt, senderEmail, fileType, boardMeta, canvasData);

        try {
            exportQueueService.enqueue(request);
            log.info("Export queued [requestId={}, board={}, type={}, user={}]",
                     request.requestId(), boardId, fileType, senderEmail);

            return new ApiResponse<>(
                "Export Queued",
                "Your export request has been queued. The file will be ready shortly.",
                null, "200", null
            );

        } catch (ExportQueueService.ExportQueueException e) {
            log.error("Failed to enqueue export [board={}, user={}]: {}",
                      boardId, senderEmail, e.getMessage(), e);
            return new ApiResponse<>(
                "Export Failed",
                "Could not queue the export request. Please try again.",
                null, "500", null
            );
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ──────────────────────────────────────────────────────────────────────────

    private BoardEntity resolveBoard(JoinBoardRequestDTO requestDTO) {
        if (requestDTO.boardToken() != null && !requestDTO.boardToken().isBlank()) {
            return boardRepository.findByToken(requestDTO.boardToken()).orElse(null);
        }
        if (requestDTO.boardName() != null && requestDTO.ownerEmail() != null) {
            return boardRepository
                    .findByOwnerEmailAndBoardName(requestDTO.ownerEmail(), requestDTO.boardName())
                    .orElse(null);
        }
        return null;
    }

    /**
     * Serialises lightweight board metadata for the export worker to embed in
     * output filenames and PDF document properties.
     */
    private String resolveBoardMetadata(String boardToken) {
        return boardRepository.findByToken(boardToken)
                .map(board -> String.format(
                    "{\"boardName\":\"%s\",\"ownerEmail\":\"%s\",\"lastSaved\":\"%s\"}",
                    board.getBoardName(), board.getOwnerEmail(), board.getLastSaved()))
                .orElse(null);
    }

    private String resolveDisplayName(UserEntity user, String fallbackEmail) {
        if (user == null) return fallbackEmail;
        String firstName = user.getFirstName() != null ? user.getFirstName().trim() : "";
        String lastName  = user.getLastName()  != null ? user.getLastName().trim()  : "";
        String fullName  = (firstName + " " + lastName).trim();
        return fullName.isEmpty() ? fallbackEmail : fullName;
    }

    private static ApiResponse<NewBoardRoomResponseDTO> boardStateUpdateFailed() {
        return new ApiResponse<>(
            "Board State Update Failed", "Failed to update board state", null, "500", null);
    }
}