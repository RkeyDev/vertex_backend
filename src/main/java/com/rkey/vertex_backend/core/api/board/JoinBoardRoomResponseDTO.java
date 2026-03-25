package com.rkey.vertex_backend.core.api.board;

import com.rkey.vertex_backend.modules.auth.model.dto.UserSummary;

public record JoinBoardRoomResponseDTO(
    String boardName,
    UserSummary boardOwnerData,
    String boardData
) {}