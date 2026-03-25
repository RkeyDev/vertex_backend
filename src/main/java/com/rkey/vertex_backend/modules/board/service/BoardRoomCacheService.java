package com.rkey.vertex_backend.modules.board.service;

import java.util.Set;

import com.rkey.vertex_backend.modules.board.models.dto.BoardStateDTO;

public class BoardRoomCacheService {
    public boolean updateBoardState(String boardId,BoardStateDTO boardState){
        throw new UnsupportedOperationException("Method is not implemented yet");
    }
    public boolean addUserToActiveSet(String boardId, String userEmail){
        throw new UnsupportedOperationException("Method is not implemented yet");
    }
    public Set<String> getActiveUsers(String boardId) {
        throw new UnsupportedOperationException("Method is not implemented yet");
    }
    public boolean clearCache(String boardId){
        throw new UnsupportedOperationException("Method is not implemented yet");
    }

}
