package com.rkey.vertex_backend.modules.board.models.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RenameBoardDTO(
    @NotBlank(message = "Board name must not be blank")
    @Size(max = 100, message = "Board name must not exceed 100 characters")
    String boardName
) {}
