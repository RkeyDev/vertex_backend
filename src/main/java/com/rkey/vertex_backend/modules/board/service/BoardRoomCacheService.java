package com.rkey.vertex_backend.modules.board.service;

import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import redis.clients.jedis.UnifiedJedis;

/**
 * Service responsible for managing real-time board state and active user sessions in Redis.
 * This implementation uses Redis Hashes (HSET) to store multiple fields (state, data) 
 * under a single board key for better memory efficiency and organization.
 */
@Service
@Slf4j
public class BoardRoomCacheService {

    private final UnifiedJedis jedis;
    
    // Prefix for the Hash containing board metadata and JSON data
    private static final String BOARD_PREFIX = "board:";
    // Suffix for the Set containing active user emails
    private static final String USER_SET_SUFFIX = ":users";
    
    // Hash field names
    private static final String FIELD_STATE = "state";
    private static final String FIELD_DATA = "data";

    private static final int DEFAULT_TTL = 86400; // 24 Hours in seconds

    public BoardRoomCacheService(UnifiedJedis jedis) {
        this.jedis = jedis;
    }

    /**
     * Checks if a board room exists in the cache.
     * * @param boardId Unique identifier of the board.
     * @return true if the board hash key exists and has not expired.
     */
    public boolean isRoomActive(String boardId) {
        String key = BOARD_PREFIX + boardId;
        try {
            return jedis.exists(key);
        } catch (Exception e) {
            log.error("Error checking activity status for board {}: {}", boardId, e.getMessage());
            return false;
        }
    }

    /**
     * Stores the raw JSON data string representing board components.
     * The frontend is responsible for the serialization/deserialization logic.
     * * @param boardId Unique identifier of the board session.
     * @param jsonData Raw JSON string from the frontend drawing canvas.
     */
    public void saveBoardData(String boardId, String jsonData) {
        String key = BOARD_PREFIX + boardId;
        try {
            // Store the raw JSON string in the 'data' field of the board's hash
            jedis.hset(key, FIELD_DATA, jsonData);
            jedis.expire(key, DEFAULT_TTL);
            
            log.debug("Successfully updated component data for board: {}", boardId);
        } catch (Exception e) {
            log.error("Failed to update board data for ID {}: {}", boardId, e.getMessage());
        }
    }

    /**
     * Retrieves the raw JSON string from the 'data' field.
     * * @param boardId Unique identifier of the board.
     * @return The raw JSON string or null if not found.
     */
    public String getBoardData(String boardId) {
        String key = BOARD_PREFIX + boardId;
        try {
            return jedis.hget(key, FIELD_DATA);
        } catch (Exception e) {
            log.error("Error retrieving board data for {}: {}", boardId, e.getMessage());
            return null;
        }
    }

    /**
     * Updates the general board state (e.g., metadata or high-level status).
     * * @param boardId The unique identifier of the board.
     * @param boardStateJson The JSON string representing the state.
     * @return true if the operation succeeded.
     */
    public boolean updateBoardState(String boardId, String boardStateJson) {
        if (boardStateJson == null || boardStateJson.isEmpty()) {
            return false;
        }

        String key = BOARD_PREFIX + boardId;
        try {
            jedis.hset(key, FIELD_STATE, boardStateJson);
            jedis.expire(key, DEFAULT_TTL);
            return true;
        } catch (Exception e) {
            log.error("Error updating hash state for board {}: {}", boardId, e.getMessage());
            return false;
        }
    }

    /**
     * Adds a user email to the set of active collaborators for a specific board.
     * This uses a separate Redis SET to handle unique user lists efficiently.
     */
    public boolean addUserToActiveSet(String boardId, String userEmail) {
        String key = BOARD_PREFIX + boardId + USER_SET_SUFFIX;
        try {
            long result = jedis.sadd(key, userEmail);
            jedis.expire(key, DEFAULT_TTL);
            return result > 0;
        } catch (Exception e) {
            log.error("Error adding user {} to board {}: {}", userEmail, boardId, e.getMessage());
            return false;
        }
    }

    /**
     * Retrieves all active users currently working on the board.
     */
    public Set<String> getActiveUsers(String boardId) {
        String key = BOARD_PREFIX + boardId + USER_SET_SUFFIX;
        try {
            return jedis.smembers(key);
        } catch (Exception e) {
            log.error("Error fetching active users for board {}: {}", boardId, e.getMessage());
            return Set.of();
        }
    }

    /**
     * Removes a user from the active set (e.g., on WebSocket disconnect).
     */
    public void removeUserFromActiveSet(String boardId, String userEmail) {
        String key = BOARD_PREFIX + boardId + USER_SET_SUFFIX;
        try {
            jedis.srem(key, userEmail);
        } catch (Exception e) {
            log.error("Error removing user {} from active set for board {}: {}", userEmail, boardId, e.getMessage());
        }
    }

    /**
     * Wipes all cached data related to a board (Hash and User Set).
     */
    public boolean clearCache(String boardId) {
        String stateKey = BOARD_PREFIX + boardId;
        String userKey = BOARD_PREFIX + boardId + USER_SET_SUFFIX;
        try {
            return jedis.del(stateKey, userKey) > 0;
        } catch (Exception e) {
            log.error("Error clearing cache for board {}: {}", boardId, e.getMessage());
            return false;
        }
    }
}