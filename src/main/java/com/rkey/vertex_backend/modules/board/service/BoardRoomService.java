package com.rkey.vertex_backend.modules.board.service;

import java.security.Principal;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

import com.rkey.vertex_backend.modules.board.models.dto.MousePositionDTO;
import com.rkey.vertex_backend.modules.board.models.dto.UmlComponentDTO;
import com.rkey.vertex_backend.modules.board.repository.BoardRepository;


@Service
@RequiredArgsConstructor 
public class BoardRoomService {

    private final BoardRoomCacheService boardRoomCacheService;
    private final SimpMessagingTemplate simpMessagingTemplate;
    private final BoardRepository boardRepository;

    public void subscribeToRoom(String boardId, Principal principal) {
        throw new UnsupportedOperationException("Method is not implemented yet");
    }

    public void processComponentChange(String boardId, UmlComponentDTO dto, String email) {
        throw new UnsupportedOperationException("Method is not implemented yet");
    }

    public void processMouseLocationChange(String boardId, MousePositionDTO dto, String email, String username) {
        throw new UnsupportedOperationException("Method is not implemented yet");
    }
}