package com.rkey.vertex_backend.modules.board.controller;

import java.security.Principal;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.rkey.vertex_backend.core.api.ApiResponse;
import com.rkey.vertex_backend.core.api.board.JoinBoardRoomResponseDTO;
import com.rkey.vertex_backend.core.api.board.NewBoardRoomResponseDTO;
import com.rkey.vertex_backend.core.api.board.OwnedBoardsResponse;
import com.rkey.vertex_backend.modules.board.models.dto.BoardStateDTO;
import com.rkey.vertex_backend.modules.board.models.dto.JoinBoardRoomDTO;
import com.rkey.vertex_backend.modules.board.models.dto.NewBoardDTO;
import com.rkey.vertex_backend.modules.board.models.dto.NewBoardRoomDTO;
import com.rkey.vertex_backend.modules.board.service.BoardRoomService;
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
            @Payload String payload, 
            Principal principal
    ) {
        String userEmail = (principal != null) ? principal.getName() : "anonymous";
        
        // --- DIAGNOSTIC LOGS ---
        log.info("[WS HIT] Received sync request for board: {}", boardToken);
        log.info("[WS USER] Executing as: {}", userEmail);
        log.info("[WS PAYLOAD] Payload length: {}", payload != null ? payload.length() : "NULL");

        BoardStateDTO stateDto = new BoardStateDTO(payload);

        ApiResponse<NewBoardRoomResponseDTO> response = boardService.updateBoardState(
                stateDto, 
                userEmail, 
                boardToken
        );

        log.info("🛠️ [SERVICE RESPONSE] Code: {}, Message: {}", response.responseCode(), response.message());

        if ("200".equals(response.responseCode())) {
            String topicDestination = "/topic/board/" + boardToken;
            messagingTemplate.convertAndSend(topicDestination, payload);
            log.info("[BROADCAST] Successfully sent to {}", topicDestination);
        } else {
            log.warn("[REJECTED] Board sync rejected by service layer.");
        }
    }



    @PostMapping("/new-room")
    public ResponseEntity<ApiResponse<NewBoardRoomResponseDTO>> handleNewBoardRoom(
            @Valid @RequestBody NewBoardRoomDTO newBoardRoomDTO, 
            Principal principal) {
        
                ApiResponse<NewBoardRoomResponseDTO> response = boardService.createNewBoardRoom(newBoardRoomDTO,principal.getName());

            if ("200".equals(response.responseCode())) {
                return ResponseEntity.ok(response);
            } else if ("500".equals(response.responseCode())) {
                return ResponseEntity.internalServerError().body(response);
            }
            return ResponseEntity.badRequest().body(response);
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
    
    @PostMapping("/join-room")
    public ApiResponse<JoinBoardRoomResponseDTO> handleJoinBoardRoom(
            @Valid @RequestBody JoinBoardRoomDTO joinBoardRoomDTO, 
            Principal principal) {
        return boardService.joinBoardRoom(joinBoardRoomDTO, principal.getName());
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