package com.rkey.vertex_backend.modules.board.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for BoardRoomCacheService covering Redis operations for board state,
 * component data, and active user management with edge cases and error handling.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BoardRoomCacheServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private HashOperations<String, Object, Object> hashOps;

    @Mock
    private SetOperations<String, String> setOps;

    private BoardRoomCacheService cacheService;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForHash()).thenReturn((HashOperations) hashOps);
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        cacheService = new BoardRoomCacheService(redisTemplate);
    }

    @Test
    void isRoomActive_withExistingBoard() {
        // Arrange
        String boardId = "board_123";
        when(redisTemplate.hasKey("board:" + boardId)).thenReturn(true);

        // Act
        boolean result = cacheService.isRoomActive(boardId);

        // Assert
        assertTrue(result);
        verify(redisTemplate, times(1)).hasKey("board:" + boardId);
    }

    @Test
    void isRoomActive_withNonExistentBoard() {
        // Arrange
        String boardId = "nonexistent_board";
        when(redisTemplate.hasKey("board:" + boardId)).thenReturn(false);

        // Act
        boolean result = cacheService.isRoomActive(boardId);

        // Assert
        assertFalse(result);
    }

    @Test
    void isRoomActive_withException() {
        // Arrange
        String boardId = "error_board";
        when(redisTemplate.hasKey(anyString())).thenThrow(new RuntimeException("Redis error"));

        // Act
        boolean result = cacheService.isRoomActive(boardId);

        // Assert
        assertFalse(result);
    }

    @Test
    void saveBoardData_withValidData() {
        // Arrange
        String boardId = "board_123";
        String jsonData = "{\"components\":[]}";
        when(redisTemplate.expire(anyString(), any(Duration.class))).thenReturn(true);

        // Act
        cacheService.saveBoardData(boardId, jsonData);

        // Assert
        verify(hashOps, times(1)).put("board:" + boardId, "data", jsonData);
        verify(redisTemplate, times(1)).expire("board:" + boardId, Duration.ofSeconds(86400));
    }

    @Test
    void saveBoardData_withComplexJson() {
        // Arrange
        String boardId = "board_456";
        String complexJson = "{\"version\":1,\"components\":[{\"id\":\"comp1\",\"type\":\"box\",\"x\":10,\"y\":20}]}";
        when(redisTemplate.expire(anyString(), any(Duration.class))).thenReturn(true);

        // Act
        cacheService.saveBoardData(boardId, complexJson);

        // Assert
        verify(hashOps, times(1)).put("board:" + boardId, "data", complexJson);
    }

    @Test
    void saveBoardData_withNullData() {
        // Arrange
        String boardId = "board_789";

        // Act
        cacheService.saveBoardData(boardId, null);

        // Assert - invalid input is handled gracefully
        verify(hashOps, never()).put(anyString(), anyString(), any());
    }

    @Test
    void saveBoardData_withEmptyJson() {
        // Arrange
        String boardId = "board_empty";
        String emptyJson = "{}";
        when(redisTemplate.expire(anyString(), any(Duration.class))).thenReturn(true);

        // Act
        cacheService.saveBoardData(boardId, emptyJson);

        // Assert
        verify(hashOps, times(1)).put("board:" + boardId, "data", emptyJson);
    }

    @Test
    void saveBoardData_withRedisException() {
        // Arrange
        String boardId = "error_board";
        String jsonData = "{\"data\":[]}";
        doThrow(new RuntimeException("Redis error")).when(hashOps).put(anyString(), anyString(), any());

        // Act - should not throw
        cacheService.saveBoardData(boardId, jsonData);

        // Assert - exception is handled gracefully
        verify(hashOps, times(1)).put(anyString(), anyString(), any());
    }

    @Test
    void getBoardData_withExistingData() {
        // Arrange
        String boardId = "board_123";
        String jsonData = "{\"components\":[]}";
        when(hashOps.get("board:" + boardId, "data")).thenReturn(jsonData);

        // Act
        String result = cacheService.getBoardData(boardId);

        // Assert
        assertEquals(jsonData, result);
    }

    @Test
    void getBoardData_withNonExistentBoard() {
        // Arrange
        String boardId = "nonexistent_board";
        when(hashOps.get("board:" + boardId, "data")).thenReturn(null);

        // Act
        String result = cacheService.getBoardData(boardId);

        // Assert
        assertNull(result);
    }

    @Test
    void getBoardData_withRedisException() {
        // Arrange
        String boardId = "error_board";
        when(hashOps.get(anyString(), anyString())).thenThrow(new RuntimeException("Redis error"));

        // Act
        String result = cacheService.getBoardData(boardId);

        // Assert
        assertNull(result);
    }

    @Test
    void updateBoardState_withValidState() {
        // Arrange
        String boardId = "board_123";
        String boardState = "{\"state\":\"active\"}";
        when(redisTemplate.expire(anyString(), any(Duration.class))).thenReturn(true);

        // Act
        boolean result = cacheService.updateBoardState(boardId, boardState);

        // Assert
        assertTrue(result);
        verify(hashOps, times(1)).put("board:" + boardId, "state", boardState);
    }

    @Test
    void updateBoardState_withNullState() {
        // Arrange
        String boardId = "board_123";

        // Act
        boolean result = cacheService.updateBoardState(boardId, null);

        // Assert
        assertFalse(result);
        verify(hashOps, never()).put(anyString(), anyString(), any());
    }

    @Test
    void updateBoardState_withEmptyState() {
        // Arrange
        String boardId = "board_123";

        // Act
        boolean result = cacheService.updateBoardState(boardId, "");

        // Assert
        assertFalse(result);
        verify(hashOps, never()).put(anyString(), anyString(), any());
    }

    @Test
    void updateBoardState_withRedisException() {
        // Arrange
        String boardId = "error_board";
        String boardState = "{\"state\":\"active\"}";
        doThrow(new RuntimeException("Redis error")).when(hashOps).put(anyString(), anyString(), any());

        // Act
        boolean result = cacheService.updateBoardState(boardId, boardState);

        // Assert
        assertFalse(result);
    }

    @Test
    void addUserToActiveSet_withValidUser() {
        // Arrange
        String boardId = "board_123";
        String userEmail = "user@example.com";
        when(setOps.add("board:board_123:users", userEmail)).thenReturn(1L);
        when(redisTemplate.expire(anyString(), any(Duration.class))).thenReturn(true);

        // Act
        boolean result = cacheService.addUserToActiveSet(boardId, userEmail);

        // Assert
        assertTrue(result);
        verify(setOps, times(1)).add("board:" + boardId + ":users", userEmail);
    }

    @Test
    void addUserToActiveSet_withDuplicateUser() {
        // Arrange
        String boardId = "board_123";
        String userEmail = "user@example.com";
        when(setOps.add("board:board_123:users", userEmail)).thenReturn(0L);
        when(redisTemplate.expire(anyString(), any(Duration.class))).thenReturn(true);

        // Act
        boolean result = cacheService.addUserToActiveSet(boardId, userEmail);

        // Assert
        assertFalse(result);
    }

    @Test
    void addUserToActiveSet_withNullUser() {
        // Arrange
        String boardId = "board_123";

        // Act
        boolean result = cacheService.addUserToActiveSet(boardId, null);

        // Assert - invalid input is handled gracefully
        assertFalse(result);
    }

    @Test
    void addUserToActiveSet_withRedisException() {
        // Arrange
        String boardId = "error_board";
        String userEmail = "user@example.com";
        when(setOps.add(anyString(), anyString())).thenThrow(new RuntimeException("Redis error"));

        // Act
        boolean result = cacheService.addUserToActiveSet(boardId, userEmail);

        // Assert
        assertFalse(result);
    }

    @Test
    void getActiveUsers_withUsers() {
        // Arrange
        String boardId = "board_123";
        Set<String> users = Set.of("user1@example.com", "user2@example.com", "user3@example.com");
        when(setOps.members("board:" + boardId + ":users")).thenReturn(users);

        // Act
        Set<String> result = cacheService.getActiveUsers(boardId);

        // Assert
        assertEquals(3, result.size());
        assertTrue(result.contains("user1@example.com"));
        assertTrue(result.contains("user2@example.com"));
        assertTrue(result.contains("user3@example.com"));
    }

    @Test
    void getActiveUsers_withNoUsers() {
        // Arrange
        String boardId = "board_empty";
        when(setOps.members("board:" + boardId + ":users")).thenReturn(null);

        // Act
        Set<String> result = cacheService.getActiveUsers(boardId);

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void getActiveUsers_withRedisException() {
        // Arrange
        String boardId = "error_board";
        when(setOps.members(anyString())).thenThrow(new RuntimeException("Redis error"));

        // Act
        Set<String> result = cacheService.getActiveUsers(boardId);

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void removeUserFromActiveSet_withValidUser() {
        // Arrange
        String boardId = "board_123";
        String userEmail = "user@example.com";

        // Act
        cacheService.removeUserFromActiveSet(boardId, userEmail);

        // Assert
        verify(setOps, times(1)).remove("board:" + boardId + ":users", userEmail);
    }

    @Test
    void removeUserFromActiveSet_withRedisException() {
        // Arrange
        String boardId = "error_board";
        String userEmail = "user@example.com";
        doThrow(new RuntimeException("Redis error")).when(setOps).remove(anyString(), anyString());

        // Act - should not throw
        cacheService.removeUserFromActiveSet(boardId, userEmail);

        // Assert - exception is handled gracefully
        verify(setOps, times(1)).remove(anyString(), anyString());
    }

    @Test
    void clearCache_success() {
        // Arrange
        String boardId = "board_123";
        when(redisTemplate.delete(any(java.util.Collection.class))).thenReturn(2L);

        // Act
        boolean result = cacheService.clearCache(boardId);

        // Assert
        assertTrue(result);
        verify(redisTemplate, times(1)).delete(any(java.util.Collection.class));
    }

    @Test
    void clearCache_withRedisException() {
        // Arrange
        String boardId = "error_board";
        when(redisTemplate.delete(any(java.util.Collection.class))).thenThrow(new RuntimeException("Redis error"));

        // Act
        boolean result = cacheService.clearCache(boardId);

        // Assert
        assertFalse(result);
    }

    @Test
    void clearCache_noResults() {
        // Arrange
        String boardId = "board_nonexistent";
        when(redisTemplate.delete(any(java.util.Collection.class))).thenReturn(0L);

        // Act
        boolean result = cacheService.clearCache(boardId);

        // Assert
        assertFalse(result);
    }

    @Test
    void saveBoardData_withLargeJson() {
        // Arrange
        String boardId = "board_large";
        StringBuilder largeJson = new StringBuilder("{\"components\":[");
        for (int i = 0; i < 1000; i++) {
            if (i > 0) largeJson.append(",");
            largeJson.append("{\"id\":\"comp").append(i).append("\",\"type\":\"box\"}");
        }
        largeJson.append("]}");
        when(redisTemplate.expire(anyString(), any(Duration.class))).thenReturn(true);

        // Act
        cacheService.saveBoardData(boardId, largeJson.toString());

        // Assert
        verify(hashOps, times(1)).put(anyString(), anyString(), any());
    }

    @Test
    void addUserToActiveSet_withMultipleUsers() {
        // Arrange
        String boardId = "board_123";
        String[] users = {"user1@example.com", "user2@example.com", "user3@example.com"};
        when(setOps.add(anyString(), anyString())).thenReturn(1L);
        when(redisTemplate.expire(anyString(), any(Duration.class))).thenReturn(true);

        // Act
        for (String user : users) {
            boolean result = cacheService.addUserToActiveSet(boardId, user);
            assertTrue(result);
        }

        // Assert
        verify(setOps, times(3)).add(anyString(), anyString());
    }
}
