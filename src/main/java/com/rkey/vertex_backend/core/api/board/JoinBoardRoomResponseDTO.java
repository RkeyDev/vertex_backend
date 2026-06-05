package com.rkey.vertex_backend.core.api.board;

import java.util.List;

import com.rkey.vertex_backend.modules.auth.model.dto.UserSummary;
import com.rkey.vertex_backend.modules.board.models.dto.CursorProfileDTO;

public record JoinBoardRoomResponseDTO(
    String boardName,
    String boardToken,
    UserSummary boardOwnerData,
    String boardData,
    List<CursorProfileDTO> profiles,
    String currentUserProfileId
) {}