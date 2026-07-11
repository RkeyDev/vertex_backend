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
import com.fasterxml.jackson.databind.ObjectMapper;

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
    private final ObjectMapper objectMapper;

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


    // ─────────────────────────────────────────────────────────────────────────
    // Delete
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Permanently deletes a board owned by {@code requestingUserEmail}.
     * Also evicts any live Redis state for that board token.
     *
     * @param boardId              the DB primary key of the board to delete
     * @param requestingUserEmail  email resolved from the JWT - never trusted from the client
     * @return an {@link ApiResponse} the controller can return directly
     */
    public ApiResponse<Void> deleteBoard(Long boardId, String requestingUserEmail) {
        BoardEntity board = boardRepository.findById(boardId).orElse(null);

        if (board == null) {
            log.warn("Delete rejected: Board {} not found", boardId);
            return new ApiResponse<>(
                "Board Not Found",
                "The requested board does not exist.",
                null, "404", null
            );
        }

        if (!board.getOwnerEmail().equalsIgnoreCase(requestingUserEmail)) {
            log.warn("Delete rejected: User {} is not owner of board {}", requestingUserEmail, boardId);
            return new ApiResponse<>(
                "Forbidden",
                "You do not have permission to delete this board.",
                null, "403", null
            );
        }

        // Evict from Redis before removing from DB so no stale data lingers
        if (board.getToken() != null) {
            boardRoomCacheService.clearCache(board.getToken());
            log.info("Redis cache cleared for board token {}", board.getToken());
        }

        boardRepository.deleteById(boardId);
        log.info("Board {} deleted by {}", boardId, requestingUserEmail);

        return new ApiResponse<>(
            "Board Deleted",
            "The board has been permanently deleted.",
            null, "200", null
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
     * @param fileType    desired output format (PDF / JPEG_ZIP / VERTEX)
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
                    .findFirstByOwnerEmailAndBoardName(requestDTO.ownerEmail(), requestDTO.boardName())
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

    /**
     * Imports a board from a compressed .vertex archive file.
     * Decompresses metadata.json and canvas.json, creates the board database entity,
     * and populates/caches it in Redis.
     */
    @Transactional
    public ApiResponse<JoinBoardRoomResponseDTO> importBoard(
            org.springframework.web.multipart.MultipartFile file, 
            String ownerEmail) {
        if (file == null || file.isEmpty()) {
            return new ApiResponse<>("Import Failed", "Uploaded file is empty", null, "400", null);
        }

        String filename = file.getOriginalFilename();
        if (filename == null || !filename.endsWith(".vertex")) {
            return new ApiResponse<>("Import Failed", "Only .vertex files are supported", null, "400", null);
        }

        String boardName = null;
        String canvasJson = null;

        try (java.util.zip.ZipInputStream zipStream = new java.util.zip.ZipInputStream(file.getInputStream())) {
            java.util.zip.ZipEntry entry;
            while ((entry = zipStream.getNextEntry()) != null) {
                String entryName = entry.getName();
                if (entryName.contains("..") || entryName.contains("/") || entryName.contains("\\")) {
                    continue;
                }
                if ("metadata.json".equals(entryName)) {
                    byte[] buffer = zipStream.readAllBytes();
                    String metadataJson = new String(buffer, java.nio.charset.StandardCharsets.UTF_8);
                    try {
                        java.util.Map<String, Object> metadata = objectMapper.readValue(metadataJson, java.util.Map.class);
                        if (metadata != null && metadata.get("boardName") != null) {
                            boardName = (String) metadata.get("boardName");
                        }
                    } catch (Exception e) {
                        log.warn("Failed to parse metadata.json from imported board", e);
                    }
                } else if ("canvas.json".equals(entryName)) {
                    byte[] buffer = zipStream.readAllBytes();
                    canvasJson = new String(buffer, java.nio.charset.StandardCharsets.UTF_8);
                }
                zipStream.closeEntry();
            }
        } catch (Exception e) {
            log.error("Failed to read zip archive from imported file", e);
            return new ApiResponse<>("Import Failed", "Failed to parse .vertex file", null, "500", null);
        }

        if (canvasJson == null || canvasJson.trim().isEmpty()) {
            return new ApiResponse<>("Import Failed", "Invalid .vertex file: canvas data is missing or empty", null, "400", null);
        }

        try {
            com.fasterxml.jackson.databind.JsonNode rootNode = objectMapper.readTree(canvasJson);
            // If canvas.json was written as a JSON-encoded string (double-encoded from legacy exports),
            // the root node will be a TextNode. Unwrap it to get the actual JSON object string.
            if (rootNode.isTextual()) {
                canvasJson = rootNode.asText();
                // Validate that the unwrapped value is itself a valid JSON object
                com.fasterxml.jackson.databind.JsonNode innerNode = objectMapper.readTree(canvasJson);
                if (!innerNode.isObject()) {
                    return new ApiResponse<>("Import Failed", "Invalid JSON structure in canvas file", null, "400", null);
                }
            } else if (!rootNode.isObject()) {
                return new ApiResponse<>("Import Failed", "Invalid JSON structure in canvas file", null, "400", null);
            }
        } catch (Exception e) {
            return new ApiResponse<>("Import Failed", "Invalid JSON data in canvas file", null, "400", null);
        }

        if (boardName == null || boardName.trim().isEmpty()) {
            String baseName = filename.substring(0, filename.lastIndexOf("."));
            boardName = baseName.replaceAll("[^a-zA-Z0-9-_\\s]", "").trim();
            if (boardName.isEmpty()) {
                boardName = "Imported Board";
            }
        }

        try {
            BoardEntity board = new BoardEntity();
            board.setBoardName(boardName);
            board.setOwnerEmail(ownerEmail);
            board.setJsonData(canvasJson);
            boardRepository.save(board);

            String boardToken = board.getToken();

            boardRoomCacheService.saveBoardData(boardToken, canvasJson);
            boardRoomCacheService.addUserToActiveSet(boardToken, ownerEmail);

            UserEntity joiningUser = userRepository.findByEmail(ownerEmail).orElse(null);
            String displayName = resolveDisplayName(joiningUser, ownerEmail);
            String avatarUrl = (joiningUser != null && joiningUser.getAvatarUrl() != null)
                    ? joiningUser.getAvatarUrl()
                    : "";

            CursorProfileDTO currentProfile = BoardProfileRegistry.registerProfile(
                    boardToken, null, displayName, avatarUrl);

            UserSummary ownerData = userRepository.findByEmail(ownerEmail)
                    .map(userMapper::getUserSummary)
                    .orElseGet(() -> new UserSummary(
                            "First", "Last",
                            ownerEmail, ownerEmail, null));

            JoinBoardRoomResponseDTO responseData = new JoinBoardRoomResponseDTO(
                boardName,
                boardToken,
                ownerData,
                canvasJson,
                BoardProfileRegistry.getProfilesForBoard(boardToken),
                currentProfile.id()
            );

            log.info("Imported board '{}' successfully created for owner: {}", boardName, ownerEmail);
            messagingTemplate.convertAndSend("/topic/board/" + boardToken + "/profiles", currentProfile);

            return new ApiResponse<>("Board Imported", "Successfully imported board", responseData, "200", null);

        } catch (DataAccessException e) {
            log.error("Database error while creating imported board '{}' for user '{}': {}",
                    boardName, ownerEmail, e.getMessage());
            return new ApiResponse<>("Database Error", "Could not save the board due to a persistence issue", null, "500", null);
        } catch (Exception e) {
            log.error("Unexpected error during board import: ", e);
            return new ApiResponse<>("Internal Server Error", "An unexpected error occurred", null, "500", null);
        }
    }

    private static ApiResponse<NewBoardRoomResponseDTO> boardStateUpdateFailed() {
        return new ApiResponse<>(
            "Board State Update Failed", "Failed to update board state", null, "500", null);
    }
}