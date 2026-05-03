package com.rkey.vertex_backend.modules.board.controller;

import java.security.Principal;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.rkey.vertex_backend.core.api.ApiResponse;
import com.rkey.vertex_backend.core.api.board.JoinBoardRoomResponseDTO;
import com.rkey.vertex_backend.core.api.board.OwnedBoardsResponse;
import com.rkey.vertex_backend.modules.board.models.dto.BoardStateDTO;
import com.rkey.vertex_backend.modules.board.models.dto.JoinBoardRequestDTO;
import com.rkey.vertex_backend.modules.board.models.dto.NewBoardDTO;
import com.rkey.vertex_backend.modules.board.service.BoardService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/board")
public class BoardController {

    private final BoardService boardService;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/board/{boardToken}/sync")
    public void handleBoardSync(
            @DestinationVariable String boardToken, 
            @Payload BoardStateDTO stateDto, 
            Principal principal
    ) {
        String userEmail = (principal != null) ? principal.getName() : "anonymous";

        // Better check: Use the 'type' field you're already sending from the frontend
        // or check if the incoming JSON actually contains data.
        boolean isInitialSync = stateDto.boardStateJson().contains("INITIAL_SYNC");

        if (isInitialSync) {
            log.info("User {} requested initial sync for board {}", userEmail, boardToken);
            
            // Ensure user is in the active set so they can edit the board after a refresh
            boardService.ensureUserInActiveSet(boardToken, userEmail);

            // Fetch the truth from Redis/DB
            BoardStateDTO currentState = boardService.getLatestBoardState(boardToken);
            
            // Only broadcast if the stored state actually has content.
            if (currentState != null && currentState.boardStateJson() != null) {
                messagingTemplate.convertAndSend("/topic/board/" + boardToken, currentState);
            }
            return;
        }

        // Data Integrity Guard: Don't let an empty state overwrite a populated board
        if (stateDto.boardStateJson().equals("{\"components\":[],\"arrows\":[]}")) {
            log.debug("Ignoring empty sync from {}", userEmail);
            return;
        }

        // Standard path: Save and Broadcast
        boardService.updateBoardState(stateDto, userEmail, boardToken);
        messagingTemplate.convertAndSend("/topic/board/" + boardToken, stateDto);
    }

    @PostMapping("/join-room")
    public ResponseEntity<ApiResponse<JoinBoardRoomResponseDTO>> handleJoinBoardRoom(
            @Valid @RequestBody JoinBoardRequestDTO joinRequest, 
            Principal principal) {
        
        if (principal == null) {
            return ResponseEntity.status(401).build();
        }

        ApiResponse<JoinBoardRoomResponseDTO> response = boardService.joinBoardRoom(joinRequest, principal.getName());

        return switch (response.responseCode()) {
            case "200" -> ResponseEntity.ok(response);
            case "404" -> ResponseEntity.status(404).body(response);
            default -> ResponseEntity.internalServerError().body(response);
        };
    }
    
    @PostMapping("/new-board")
    public ResponseEntity<ApiResponse<Void>> handleCreateNewBoard(
            @Valid @RequestBody NewBoardDTO newBoardDTO, 
            Principal principal) {
        
        if (principal == null) {
            return ResponseEntity.status(401).build();
        }

        try {
            ApiResponse<Void> response = boardService.createNewBoard(newBoardDTO, principal.getName());
            
            if ("200".equals(response.responseCode())) {
                return ResponseEntity.ok(response);
            } else if ("500".equals(response.responseCode())) {
                return ResponseEntity.internalServerError().body(response);
            }
            return ResponseEntity.badRequest().body(response);
            
        } catch (Exception e) {
            log.error("Critical error in handleCreateNewBoard for user {}: ", principal.getName(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    @GetMapping("/boards")
    public ResponseEntity<ApiResponse<OwnedBoardsResponse>> handleGetOwnedBoards(
            @AuthenticationPrincipal UserDetails userDetails) {
        
        if (userDetails == null) {
            log.warn("Unauthorized attempt to access boards - UserDetails is null");
            return ResponseEntity.status(403).build();
        }

        log.info("Fetching boards for user: {}", userDetails.getUsername());
        ApiResponse<OwnedBoardsResponse> response = boardService.getOwnedBoards(userDetails.getUsername());

        return "200".equals(response.responseCode())
                ? ResponseEntity.ok(response)
                : ResponseEntity.badRequest().body(response);
    }
}