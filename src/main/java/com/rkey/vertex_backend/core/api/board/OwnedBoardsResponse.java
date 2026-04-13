package com.rkey.vertex_backend.core.api.board;

import java.util.List;

import com.rkey.vertex_backend.modules.board.entity.BoardEntity;

public record OwnedBoardsResponse(
    List<BoardEntity> boards
) {
    public static final String SUCCESS_RESPONSE_NAME = "Owned Boards"; 
    public static final String SUCCESS_RESPONSE_DESCRIPTION = "Successfully fetched owned boards"; 

    public static final String FAILED_RESPONSE_NAME = "Owned Boards Failed"; 
    public static final String FAILED_RESPONSE_DESCRIPTION = "Failed to fetch owned boards"; 
}
