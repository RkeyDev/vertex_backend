package com.rkey.vertex_backend.modules.board.service;

import com.rkey.vertex_backend.core.api.ApiResponse;
import com.rkey.vertex_backend.core.api.board.JoinBoardRoomResponseDTO;
import com.rkey.vertex_backend.core.api.board.NewBoardRoomResponseDTO;
import com.rkey.vertex_backend.core.api.board.OwnedBoardsResponse;
import com.rkey.vertex_backend.modules.board.entity.BoardEntity;
import com.rkey.vertex_backend.modules.board.models.dto.JoinBoardRoomDTO;
import com.rkey.vertex_backend.modules.board.models.dto.NewBoardDTO;
import com.rkey.vertex_backend.modules.board.models.dto.NewBoardRoomDTO;
import com.rkey.vertex_backend.modules.board.repository.BoardRepository;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class BoardService {
    private final BoardRepository boardRepository;
    private final BoardRoomCacheService boardRoomCacheService;

    public ApiResponse<NewBoardRoomResponseDTO> createNewBoardRoom(NewBoardRoomDTO newBoardRoomDTO, String ownerEmail){
        BoardEntity board = boardRepository.findByOwnerEmailAndBoardName(ownerEmail,newBoardRoomDTO.boardName()).orElse(null);
        
        if(board != null){

            boardRoomCacheService.addUserToActiveSet(board.getToken(), ownerEmail); // Save owner email in redis


            NewBoardRoomResponseDTO responseData = new NewBoardRoomResponseDTO(board.getToken(),board.getJosnData());
            return new 
            ApiResponse<NewBoardRoomResponseDTO>(
                "Board Room Created",
                 "Successfully created board room",
                  responseData,
                   "200",
                    null
                );
        }
        return new 
            ApiResponse<NewBoardRoomResponseDTO>(
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

    public ApiResponse<JoinBoardRoomResponseDTO> joinBoardRoom(JoinBoardRoomDTO joinBoardRoomDTO){
        throw new UnsupportedOperationException("Method is not implemented yet");
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
