package com.rkey.vertex_backend.modules.board.service;

import com.rkey.vertex_backend.core.api.ApiResponse;
import com.rkey.vertex_backend.core.api.board.JoinBoardRoomResponseDTO;
import com.rkey.vertex_backend.core.api.board.NewBoardRoomResponseDTO;
import com.rkey.vertex_backend.core.api.board.OwnedBoardsResponse;
import com.rkey.vertex_backend.modules.auth.model.dto.UserSummary;
import com.rkey.vertex_backend.modules.board.entity.BoardEntity;
import com.rkey.vertex_backend.modules.board.models.dto.BoardStateDTO;
import com.rkey.vertex_backend.modules.board.models.dto.JoinBoardRequestDTO;
import com.rkey.vertex_backend.modules.board.models.dto.NewBoardDTO;
import com.rkey.vertex_backend.modules.board.repository.BoardRepository;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Set;

import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class BoardService {
    
    private final BoardRepository boardRepository;
    private final BoardRoomCacheService boardRoomCacheService;

    /**
     * Updates the board state in Redis. Verified against the active user set.
     */
    public ApiResponse<NewBoardRoomResponseDTO> updateBoardState(BoardStateDTO boardStateDTO, String updaterEmail, String boardToken) {
        if (boardStateDTO != null && boardStateDTO.boardStateJson() != null) {
            Set<String> activeUsers = boardRoomCacheService.getActiveUsers(boardToken);
            
            // Ensure user is actually in the room
            if (activeUsers.contains(updaterEmail) || "anonymous".equals(updaterEmail)) {
                // Persist the actual JSON data to Redis
                boardRoomCacheService.saveBoardData(boardToken, boardStateDTO.boardStateJson());
                
                return new ApiResponse<>(
                    "Board State Updated", 
                    "Successfully updated board state",
                    null, 
                    "200",
                    null
                );
            } else {
                log.warn("Sync rejected: User {} not in active set for board {}.", updaterEmail, boardToken);
            }
        }

        return new ApiResponse<>(
            "Board State Update Failed", 
            "Failed to update board state",
            null, 
            "500",
            null
        );
    }

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

            return new ApiResponse<>(
                "Board Created",
                "The board was successfully persisted",
                null,
                "200",
                null
            );
        } catch (DataAccessException e) {
            log.error("Database error while creating board '{}' for user '{}': {}", 
                newBoardDTO.boardName(), ownerEmail, e.getMessage());
            
            return new ApiResponse<>(
                "Database Error",
                "Could not save the board due to a persistence issue",
                null,
                "500",
                null
            );
        } catch (Exception e) {
            log.error("Unexpected error during board creation: ", e);
            
            return new ApiResponse<>(
                "Internal Server Error",
                "An unexpected error occurred",
                null,
                "500",
                null
            );
        }
    }

    /**
     * Handles joining an existing board. Resolves by token OR (name + email).
     * Initializes the Redis cache if the room is not currently active.
     */
    public ApiResponse<JoinBoardRoomResponseDTO> joinBoardRoom(JoinBoardRequestDTO requestDTO, String userEmail) {
        BoardEntity board = null;

        // Resolve the Board Entity
        if (requestDTO.boardToken() != null && !requestDTO.boardToken().isBlank()) {
            board = boardRepository.findByToken(requestDTO.boardToken()).orElse(null);
        } else if (requestDTO.boardName() != null && requestDTO.ownerEmail() != null) {
            board = boardRepository.findByOwnerEmailAndBoardName(requestDTO.ownerEmail(), requestDTO.boardName()).orElse(null);
        }

        // Handle Not Found
        if (board == null) {
            log.warn("Failed to join room: Board not found for request.");
            return new ApiResponse<>(
                "Board Room Join Failed",
                "The requested board does not exist or you do not have access.",
                null,
                "404",
                null
            );
        }

        String boardToken = board.getToken();

        // Initialize cache if the room is dead/cold
        if (!boardRoomCacheService.isRoomActive(boardToken)) {
            log.info("Initializing cold cache for board room: {}", boardToken);
            // Load the persisted state from PostgreSQL into Redis for the canvas
            if (board.getJosnData() != null) {
                boardRoomCacheService.saveBoardData(boardToken, board.getJosnData());
            }
        }

        // Add the current user to the active session set
        boardRoomCacheService.addUserToActiveSet(boardToken, userEmail);


        UserSummary ownerData = new UserSummary("First", "Last", board.getOwnerEmail(), board.getOwnerEmail(), null);
        JoinBoardRoomResponseDTO responseData = new JoinBoardRoomResponseDTO(
            board.getBoardName(),
            boardToken, 
            ownerData, 
            board.getJosnData()
        );

        return new ApiResponse<>(
            "Board Room Joined",
            "Successfully joined board room",
            responseData,
            "200",
            null
        );
    }

    /**
     * Retrieves all boards owned by a specific user.
     */
    public ApiResponse<OwnedBoardsResponse> getOwnedBoards(String userEmail){
        List<BoardEntity> boards = boardRepository.findAllByOwnerEmail(userEmail);

        if(boards != null){
            OwnedBoardsResponse ownedBoardsResponse = new OwnedBoardsResponse(boards);
            log.info(OwnedBoardsResponse.SUCCESS_RESPONSE_NAME);
            return new ApiResponse<>(
                OwnedBoardsResponse.SUCCESS_RESPONSE_NAME, 
                OwnedBoardsResponse.SUCCESS_RESPONSE_DESCRIPTION, 
                ownedBoardsResponse, 
                "200", 
                null
            );
        }

        log.warn(OwnedBoardsResponse.FAILED_RESPONSE_DESCRIPTION);
        return new ApiResponse<>(
            OwnedBoardsResponse.FAILED_RESPONSE_NAME, 
            OwnedBoardsResponse.FAILED_RESPONSE_DESCRIPTION, 
            null, 
            "400", 
            null
        );
    }
}