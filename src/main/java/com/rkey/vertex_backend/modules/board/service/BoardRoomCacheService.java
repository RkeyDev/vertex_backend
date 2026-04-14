package com.rkey.vertex_backend.modules.board.service;

import java.util.Set;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rkey.vertex_backend.modules.board.models.dto.BoardStateDTO;

import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import redis.clients.jedis.UnifiedJedis;

/**
 * Service responsible for managing real-time board state and active user sessions in Redis.
 * Uses Jackson for JSON serialization to maintain board persistence.
 */
@Service
@Slf4j
public class BoardRoomCacheService {

    private final UnifiedJedis jedis;
    private final ObjectMapper objectMapper;
    
    private static final String BOARD_PREFIX = "board:";
    private static final String USER_SET_SUFFIX = ":users";

    public BoardRoomCacheService(UnifiedJedis jedis, ObjectMapper objectMapper) {
        this.jedis = jedis;
        this.objectMapper = objectMapper;
    }

    /**
     * Serializes the current board state to JSON and updates the cache.
     * * @param boardId The unique identifier of the board.
     * @param boardState The DTO containing the current canvas/UML state.
     * @return true if the update was successful.
     */
    public boolean updateBoardState(String boardId, BoardStateDTO boardState) {

        if (boardState == null || boardState.boardStateJson().isEmpty())
            return false;

        try{
            String key = BOARD_PREFIX + boardId;
            jedis.set(key, boardState.boardStateJson());
            
            // if the board isn't being used, expire after 24 hours
            jedis.expire(key, 86400); 
            
            return true;
        }
        catch(Exception e){
            log.error(e.toString());
            return false;
        }
    }

    /**
     * Adds a user email to the set of active collaborators for a specific board.
     */
    public boolean addUserToActiveSet(String boardId, String userEmail) {
        String key = BOARD_PREFIX + boardId + USER_SET_SUFFIX;
        long result = jedis.sadd(key, userEmail);
        return result > 0;
    }

    /**
     * Retrieves all active users currently working on the board.
     */
    public Set<String> getActiveUsers(String boardId) {
        String key = BOARD_PREFIX + boardId + USER_SET_SUFFIX;
        return jedis.smembers(key);
    }

    /**
     * Wipes all cached data related to a board. 
     * Useful when a session ends or the board is deleted.
     */
    public boolean clearCache(String boardId) {
        String stateKey = BOARD_PREFIX + boardId;
        String userKey = BOARD_PREFIX + boardId + USER_SET_SUFFIX;
        return jedis.del(stateKey, userKey) > 0;
    }
}