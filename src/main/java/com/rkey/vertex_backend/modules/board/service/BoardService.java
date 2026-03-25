package com.rkey.vertex_backend.modules.board.service;

import com.rkey.vertex_backend.core.api.ApiResponse;
import com.rkey.vertex_backend.core.api.board.JoinBoardRoomResponseDTO;
import com.rkey.vertex_backend.core.api.board.NewBoardRoomResponseDTO;
import com.rkey.vertex_backend.core.api.board.OwnedBoardsResponse;
import com.rkey.vertex_backend.modules.board.models.dto.JoinBoardRoomDTO;
import com.rkey.vertex_backend.modules.board.models.dto.NewBoardRoomDTO;
import com.rkey.vertex_backend.modules.board.repository.BoardRepository;

import lombok.RequiredArgsConstructor;

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
    public ApiResponse<OwnedBoardsResponse> getOwnedBoards(String userId){
        throw new UnsupportedOperationException("Method is not implemented yet");
    }
}
