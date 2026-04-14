package com.rkey.vertex_backend.core.api.board;


public record NewBoardRoomResponseDTO(
    String boardToken,
    String boardData
) {
}