package com.rkey.vertex_backend.modules.board.models.dto;

import jakarta.validation.constraints.AssertTrue;

/**
 * Unified DTO for joining a board room.
 * The client can provide EITHER the boardToken (if joining via URL)
 * OR the boardName and ownerEmail (if joining via dashboard).
 */
public record JoinBoardRequestDTO(
        String boardToken,
        String boardName,
        String ownerEmail
) {
    @AssertTrue(message = "Must provide either a boardToken OR both boardName and ownerEmail")
    public boolean isValid() {
        boolean hasToken = boardToken != null && !boardToken.trim().isEmpty();
        boolean hasNameAndEmail = boardName != null && !boardName.trim().isEmpty() && 
                                  ownerEmail != null && !ownerEmail.trim().isEmpty();
        return hasToken || hasNameAndEmail;
    }
}