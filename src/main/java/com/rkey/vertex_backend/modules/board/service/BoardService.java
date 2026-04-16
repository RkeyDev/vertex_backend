package com.rkey.vertex_backend.modules.board.service;

import com.rkey.vertex_backend.core.api.ApiResponse;
import com.rkey.vertex_backend.core.api.board.JoinBoardRoomResponseDTO;
import com.rkey.vertex_backend.core.api.board.NewBoardRoomResponseDTO;
import com.rkey.vertex_backend.core.api.board.OwnedBoardsResponse;
import com.rkey.vertex_backend.modules.auth.model.dto.UserSummary;
import com.rkey.vertex_backend.modules.board.entity.BoardEntity;
import com.rkey.vertex_backend.modules.board.models.dto.BoardStateDTO;
import com.rkey.vertex_backend.modules.board.models.dto.JoinBoardRoomDTO;
import com.rkey.vertex_backend.modules.board.models.dto.NewBoardDTO;
import com.rkey.vertex_backend.modules.board.models.dto.NewBoardRoomDTO;
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

    public ApiResponse<NewBoardRoomResponseDTO> createNewBoardRoom(NewBoardRoomDTO newBoardRoomDTO, String ownerEmail){
        BoardEntity board = boardRepository.findByOwnerEmailAndBoardName(ownerEmail,newBoardRoomDTO.boardName()).orElse(null);
        
        if(board != null){
            boardRoomCacheService.addUserToActiveSet(board.getToken(), ownerEmail); // Save owner email in redis

            NewBoardRoomResponseDTO responseData = new NewBoardRoomResponseDTO(board.getToken(),board.getJosnData());
            return new ApiResponse<NewBoardRoomResponseDTO>(
                "Board Room Created",
                "Successfully created board room",
                responseData,
                "200",
                null
            );
        }
        return new ApiResponse<NewBoardRoomResponseDTO>(
            "Board Room Creation Failed",
            "Failed to create board room",
            null,
            "500",
            null
        );
    }

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

    public ApiResponse<JoinBoardRoomResponseDTO> joinBoardRoom(JoinBoardRoomDTO joinBoardRoomDTO, String userEmail){
        BoardEntity board = boardRepository.findByToken(joinBoardRoomDTO.boardToken()).orElse(null);
        
        if(board != null){
            boardRoomCacheService.addUserToActiveSet(board.getToken(), userEmail);
            
            // Placeholder UserSummary for owner
            UserSummary ownerData = new UserSummary("First", "Last", board.getOwnerEmail(), board.getOwnerEmail(), null);
            
            JoinBoardRoomResponseDTO responseData = new JoinBoardRoomResponseDTO(board.getBoardName(), ownerData, board.getJosnData());
            return new ApiResponse<JoinBoardRoomResponseDTO>(
                "Board Room Joined",
                "Successfully joined board room",
                responseData,
                "200",
                null
            );
        }
        return new ApiResponse<JoinBoardRoomResponseDTO>(
            "Board Room Join Failed",
             "Failed to join board room",
              null,
               "400",
                null
            );
    }

    public ApiResponse<OwnedBoardsResponse> getOwnedBoards(String userEmail){
        List<BoardEntity> boards = boardRepository.findAllByOwnerEmail(userEmail);

        if(boards != null){
            OwnedBoardsResponse ownedBoardsResponse = new OwnedBoardsResponse(boards);
            log.info(OwnedBoardsResponse.SUCCESS_RESPONSE_NAME);
            return new ApiResponse<OwnedBoardsResponse>(OwnedBoardsResponse.SUCCESS_RESPONSE_NAME, OwnedBoardsResponse.SUCCESS_RESPONSE_DESCRIPTION, ownedBoardsResponse, "200", null);
        }

        log.warn(OwnedBoardsResponse.FAILED_RESPONSE_DESCRIPTION);
        return new ApiResponse<OwnedBoardsResponse>(OwnedBoardsResponse.FAILED_RESPONSE_NAME, OwnedBoardsResponse.FAILED_RESPONSE_DESCRIPTION, null, "400", null);
       }
}