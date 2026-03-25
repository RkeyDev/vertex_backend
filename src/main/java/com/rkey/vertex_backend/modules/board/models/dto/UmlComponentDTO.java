package com.rkey.vertex_backend.modules.board.models.dto;

import java.util.HashMap;

import com.rkey.vertex_backend.modules.board.models.enums.ComponentType;

public class UmlComponentDTO {
    private double xPos;
    private double yPos;
    private double width;
    private double height;
    private ComponentType type;
    private HashMap<String, Object> data;
}
