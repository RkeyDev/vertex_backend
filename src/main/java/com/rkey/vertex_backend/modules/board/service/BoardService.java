package com.rkey.vertex_backend.modules.board.service;

import com.rkey.vertex_backend.core.api.ApiResponse;
import com.rkey.vertex_backend.core.api.board.JoinBoardRoomResponseDTO;
import com.rkey.vertex_backend.core.api.board.NewBoardRoomResponseDTO;
import com.rkey.vertex_backend.core.api.board.OwnedBoardsResponse;
import com.rkey.vertex_backend.modules.board.entity.BoardEntity;
import com.rkey.vertex_backend.modules.board.models.dto.JoinBoardRoomDTO;
import com.rkey.vertex_backend.modules.board.models.dto.NewBoardRoomDTO;
import com.rkey.vertex_backend.modules.board.repository.BoardRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class BoardService {
    private final BoardRepository boardRepository;

    public ApiResponse<NewBoardRoomResponseDTO> createNewBoardRoom(NewBoardRoomDTO newBoardRoomDTO){
        throw new UnsupportedOperationException("Method is not implemented yet");
    }
    public ApiResponse<Void> createNewBoard(NewBoardRoomDTO newBoardDTO,String ownerEmail){
        throw new UnsupportedOperationException("Method is not implemented yet");
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
