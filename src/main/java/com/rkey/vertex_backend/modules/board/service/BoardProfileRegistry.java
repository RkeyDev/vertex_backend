package com.rkey.vertex_backend.modules.board.service;

import com.rkey.vertex_backend.modules.board.models.dto.CursorProfileDTO;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory cursor profile registry, scoped per board room.
 */
public final class BoardProfileRegistry {

    private static final Map<String, Map<String, CursorProfileDTO>> profilesByBoard = new ConcurrentHashMap<>();

    private BoardProfileRegistry() {
    }

    public static CursorProfileDTO registerProfile(
            String boardToken,
            String preferredId,
            String username,
            String avatar
    ) {
        Map<String, CursorProfileDTO> boardProfiles = profilesByBoard.computeIfAbsent(
                boardToken,
                ignored -> new ConcurrentHashMap<>()
        );

        String safeUsername = username != null ? username : "";
        String safeAvatar = avatar != null ? avatar : "";

        synchronized (boardProfiles) {
            for (CursorProfileDTO existing : boardProfiles.values()) {
                if (safeUsername.equals(existing.username())) {
                    return existing;
                }
            }

            String assignedId = resolveAvailableProfileId(boardProfiles, preferredId);
            CursorProfileDTO profile = new CursorProfileDTO(assignedId, safeUsername, safeAvatar);
            boardProfiles.put(assignedId, profile);
            return profile;
        }
    }

    public static List<CursorProfileDTO> getProfilesForBoard(String boardToken) {
        Map<String, CursorProfileDTO> boardProfiles = profilesByBoard.get(boardToken);
        if (boardProfiles == null) {
            return List.of();
        }

        synchronized (boardProfiles) {
            return new ArrayList<>(boardProfiles.values());
        }
    }

    public static void removeProfile(String boardToken, String profileId) {
        Map<String, CursorProfileDTO> boardProfiles = profilesByBoard.get(boardToken);
        if (boardProfiles == null || profileId == null) {
            return;
        }

        synchronized (boardProfiles) {
            boardProfiles.remove(profileId);
            if (boardProfiles.isEmpty()) {
                profilesByBoard.remove(boardToken);
            }
        }
    }

    /** Clears all in-memory profiles. Used by tests to reset state between runs. */
    public static void clearAll() {
        profilesByBoard.clear();
    }

    private static String resolveAvailableProfileId(
            Map<String, CursorProfileDTO> boardProfiles,
            String preferredId
    ) {
        if (preferredId != null) {
            try {
                int parsedId = Integer.parseInt(preferredId);
                if (parsedId > 0 && !boardProfiles.containsKey(String.valueOf(parsedId))) {
                    return String.valueOf(parsedId);
                }
            } catch (NumberFormatException ignored) {
                // Fall through to auto-assign.
            }
        }

        int nextId = 1;
        while (boardProfiles.containsKey(String.valueOf(nextId))) {
            nextId++;
        }
        return String.valueOf(nextId);
    }
}
