package com.rkey.vertex_backend.modules.board.service;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rkey.vertex_backend.modules.board.models.dto.BoardStateDTO;
import com.rkey.vertex_backend.modules.board.models.dto.ComponentTransformDTO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Patches component position/size in the cached board JSON after transform broadcasts.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BoardStatePatchService {

    private final ObjectMapper objectMapper;
    private final BoardRoomCacheService boardRoomCacheService;
    private final BoardStatePersistenceScheduler boardStatePersistenceScheduler;
    private final ExecutorService patchExecutor = Executors.newVirtualThreadPerTaskExecutor();

    public void applyTransformAsync(String boardToken, ComponentTransformDTO transform) {
        patchExecutor.submit(() -> {
            try {
                String current = boardRoomCacheService.getBoardData(boardToken);
                if (current == null || current.isBlank()) {
                    return;
                }
                String patched = patchComponentPosition(current, transform);
                if (patched == null) {
                    return;
                }
                boardRoomCacheService.saveBoardData(boardToken, patched);
                boardStatePersistenceScheduler.scheduleDbPersist(
                        boardToken,
                        new BoardStateDTO(patched, transform.senderId()));
            } catch (Exception e) {
                log.warn("Failed to patch board {} for component {}: {}", boardToken, transform.componentId(), e.getMessage());
            }
        });
    }

    /**
     * @return patched JSON, or null if the component was not found or JSON is invalid
     */
    public String patchComponentPosition(String boardJson, ComponentTransformDTO transform) {
        try {
            JsonNode root = objectMapper.readTree(boardJson);
            if (!root.isObject()) {
                return null;
            }
            JsonNode componentsNode = root.get("components");
            if (componentsNode == null || !componentsNode.isArray()) {
                return null;
            }

            boolean updated = false;
            for (JsonNode component : componentsNode) {
                if (!component.isObject() || !matchesComponentId(component, transform.componentId())) {
                    continue;
                }
                ObjectNode obj = (ObjectNode) component;
                setPositionFields(obj, transform);
                if (transform.width() != null) {
                    obj.put("width", transform.width());
                }
                if (transform.height() != null) {
                    obj.put("height", transform.height());
                }
                updated = true;
                break;
            }

            if (!updated) {
                return null;
            }
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            log.debug("Could not patch board JSON: {}", e.getMessage());
            return null;
        }
    }

    private static boolean matchesComponentId(JsonNode component, String componentId) {
        if (componentId == null || componentId.isBlank()) {
            return false;
        }
        if (component.has("id") && componentId.equals(component.get("id").asText())) {
            return true;
        }
        if (component.has("componentId") && componentId.equals(component.get("componentId").asText())) {
            return true;
        }
        return component.has("id") && component.get("id").isNumber()
                && componentId.equals(String.valueOf(component.get("id").asLong()));
    }

    private static void setPositionFields(ObjectNode component, ComponentTransformDTO transform) {
        if (component.has("xPos") || !component.has("x")) {
            component.put("xPos", transform.xPos());
        } else {
            component.put("x", transform.xPos());
        }
        if (component.has("yPos") || !component.has("y")) {
            component.put("yPos", transform.yPos());
        } else {
            component.put("y", transform.yPos());
        }
    }
}
