package com.rkey.vertex_backend.modules.board.controller;

import java.security.Principal;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.rkey.vertex_backend.core.api.ApiResponse;
import com.rkey.vertex_backend.core.api.board.JoinBoardRoomResponseDTO;
import com.rkey.vertex_backend.core.api.board.NewBoardRoomResponseDTO;
import com.rkey.vertex_backend.core.api.board.OwnedBoardsResponse;
import com.rkey.vertex_backend.modules.board.models.dto.JoinBoardRoomDTO;
import com.rkey.vertex_backend.modules.board.models.dto.NewBoardDTO;
import com.rkey.vertex_backend.modules.board.models.dto.NewBoardRoomDTO;
import com.rkey.vertex_backend.modules.board.service.BoardService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/board")
public class BoardController {

    private final BoardService boardService;
    
    @PostMapping("/new-room")
    public ApiResponse<NewBoardRoomResponseDTO> handleNewBoardRoom(
            @Valid @RequestBody NewBoardRoomDTO newBoardRoomDTO, 
            Principal principal) {
        throw new UnsupportedOperationException("Method is not implemented yet");
    }
    
    @PostMapping("/new-board")
    public ApiResponse<Void> handleCreateNewBoard(
            @Valid @RequestBody NewBoardDTO newBoardDTO, 
            Principal principal) {
        throw new UnsupportedOperationException("Method is not implemented yet");
    }
    
    @PostMapping("/join-room")
    public ApiResponse<JoinBoardRoomResponseDTO> handleJoinBoardRoom(
            @Valid @RequestBody JoinBoardRoomDTO joinBoardRoomDTO, 
            Principal principal) {
        throw new UnsupportedOperationException("Method is not implemented yet");
    }
    
    @GetMapping("/boards")
    public ApiResponse<OwnedBoardsResponse> handleGetOwnedBoards(Principal principal) {
        throw new UnsupportedOperationException("Method is not implemented yet");
    }
}