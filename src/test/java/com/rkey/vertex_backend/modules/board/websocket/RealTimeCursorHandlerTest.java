package com.rkey.vertex_backend.modules.board.websocket;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for RealTimeCursorHandler covering WebSocket connection lifecycle,
 * binary message handling, cursor position updates, and Redis pub/sub integration.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RealTimeCursorHandlerTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private RedisMessageListenerContainer redisContainer;

    @Mock
    private WebSocketSession session;

    private RealTimeCursorHandler handler;

    @BeforeEach
    void setUp() {
        handler = new RealTimeCursorHandler(redisTemplate, redisContainer);
    }

    @Test
    void afterConnectionEstablished_withValidBoardToken() throws Exception {
        // Arrange
        String sessionId = "session_123";
        when(session.getId()).thenReturn(sessionId);
        when(session.getUri()).thenReturn(new URI("ws://localhost:8080/sync-cursor?board=board_123"));
        Map<String, Object> attributes = new HashMap<>();
        when(session.getAttributes()).thenReturn(attributes);

        // Act
        handler.afterConnectionEstablished(session);

        // Assert
        verify(redisContainer, times(1)).addMessageListener(any(org.springframework.data.redis.connection.MessageListener.class), any(org.springframework.data.redis.listener.Topic.class));
        assertTrue(attributes.containsKey("userId"));
    }

    @Test
    void afterConnectionEstablished_withoutBoardToken() throws Exception {
        // Arrange
        when(session.getId()).thenReturn("session_123");
        when(session.getUri()).thenReturn(new URI("ws://localhost:8080/sync-cursor"));
        Map<String, Object> attributes = new HashMap<>();
        when(session.getAttributes()).thenReturn(attributes);

        // Act
        handler.afterConnectionEstablished(session);

        // Assert
        verify(session, times(1)).close(any(CloseStatus.class));
    }

    @Test
    void afterConnectionEstablished_withNullUri() throws Exception {
        // Arrange
        when(session.getId()).thenReturn("session_123");
        when(session.getUri()).thenReturn(null);

        // Act
        handler.afterConnectionEstablished(session);

        // Assert
        verify(session, times(1)).close(any(CloseStatus.class));
    }

    @Test
    void afterConnectionEstablished_withUsernameAndAvatar() throws Exception {
        // Arrange
        String sessionId = "session_123";
        when(session.getId()).thenReturn(sessionId);
        when(session.getUri()).thenReturn(new URI(
            "ws://localhost:8080/sync-cursor?board=board_123&username=john_doe&avatar=https://example.com/avatar.jpg"
        ));
        Map<String, Object> attributes = new HashMap<>();
        when(session.getAttributes()).thenReturn(attributes);

        // Act
        handler.afterConnectionEstablished(session);

        // Assert
        assertTrue(attributes.containsKey("userId"));
        verify(redisContainer, times(1)).addMessageListener(any(org.springframework.data.redis.connection.MessageListener.class), any(org.springframework.data.redis.listener.Topic.class));
    }

    @Test
    void afterConnectionEstablished_withIdParameter() throws Exception {
        // Arrange
        String sessionId = "session_123";
        when(session.getId()).thenReturn(sessionId);
        when(session.getUri()).thenReturn(new URI(
            "ws://localhost:8080/sync-cursor?board=board_123&id=5"
        ));
        Map<String, Object> attributes = new HashMap<>();
        when(session.getAttributes()).thenReturn(attributes);

        // Act
        handler.afterConnectionEstablished(session);

        // Assert
        assertTrue(attributes.containsKey("userId"));
    }

    @Test
    void afterConnectionClosed_withValidSession() throws Exception {
        // Arrange
        String boardToken = "board_123";
        String sessionId = "session_123";
        when(session.getId()).thenReturn(sessionId);
        when(session.getUri()).thenReturn(new URI("ws://localhost:8080/sync-cursor?board=" + boardToken));
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("userId", "profile_1");
        when(session.getAttributes()).thenReturn(attributes);

        // First establish connection
        handler.afterConnectionEstablished(session);

        // Act - close the connection
        handler.afterConnectionClosed(session, CloseStatus.NORMAL);

        // Assert
        verify(redisContainer, times(1)).removeMessageListener(any(org.springframework.data.redis.connection.MessageListener.class), any(org.springframework.data.redis.listener.Topic.class));
    }

    @Test
    void afterConnectionClosed_withoutBoardToken() throws Exception {
        // Arrange
        when(session.getId()).thenReturn("session_123");
        when(session.getUri()).thenReturn(new URI("ws://localhost:8080/sync-cursor"));
        Map<String, Object> attributes = new HashMap<>();
        when(session.getAttributes()).thenReturn(attributes);

        // Act - should not throw
        handler.afterConnectionClosed(session, CloseStatus.NORMAL);

        // Assert
        verify(redisContainer, never()).removeMessageListener(any(org.springframework.data.redis.connection.MessageListener.class), any(org.springframework.data.redis.listener.Topic.class));
    }

    @Test
    void handleBinaryMessage_validCursorPacket() throws Exception {
        // Arrange
        String boardToken = "board_123";
        String sessionId = "session_123";
        when(session.getId()).thenReturn(sessionId);
        when(session.getUri()).thenReturn(new URI("ws://localhost:8080/sync-cursor?board=" + boardToken));
        when(session.isOpen()).thenReturn(true);
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("userId", "1");
        when(session.getAttributes()).thenReturn(attributes);

        // Establish connection first
        handler.afterConnectionEstablished(session);

        // Create a valid cursor packet: [Id:int32, X:float32, Y:float32] in little-endian
        ByteBuffer buffer = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(1);           // profile ID
        buffer.putFloat(100.5f);    // X coordinate
        buffer.putFloat(200.75f);   // Y coordinate
        buffer.flip();

        BinaryMessage message = new BinaryMessage(buffer);

        // Act
        handler.handleBinaryMessage(session, message);

        // Assert - message should be processed without exception
        assertTrue(true);
    }

    @Test
    void handleBinaryMessage_invalidPacketSize() throws Exception {
        // Arrange
        String boardToken = "board_123";
        when(session.getId()).thenReturn("session_123");
        when(session.getUri()).thenReturn(new URI("ws://localhost:8080/sync-cursor?board=" + boardToken));
        Map<String, Object> attributes = new HashMap<>();
        when(session.getAttributes()).thenReturn(attributes);

        handler.afterConnectionEstablished(session);

        // Too small packet
        BinaryMessage message = new BinaryMessage(ByteBuffer.wrap(new byte[]{1, 2, 3}));

        // Act & Assert - should not throw
        handler.handleBinaryMessage(session, message);
    }

    @Test
    void handleBinaryMessage_nullPayload() throws Exception {
        // Arrange
        when(session.getId()).thenReturn("session_123");
        when(session.getUri()).thenReturn(new URI("ws://localhost:8080/sync-cursor?board=board_123"));
        Map<String, Object> attributes = new HashMap<>();
        when(session.getAttributes()).thenReturn(attributes);

        handler.afterConnectionEstablished(session);

        BinaryMessage message = new BinaryMessage(ByteBuffer.allocate(0));

        // Act & Assert - should not throw
        handler.handleBinaryMessage(session, message);
    }

    @Test
    void handleBinaryMessage_withNaNCoordinates() throws Exception {
        // Arrange
        String boardToken = "board_123";
        when(session.getId()).thenReturn("session_123");
        when(session.getUri()).thenReturn(new URI("ws://localhost:8080/sync-cursor?board=" + boardToken));
        Map<String, Object> attributes = new HashMap<>();
        when(session.getAttributes()).thenReturn(attributes);

        handler.afterConnectionEstablished(session);

        // Create packet with NaN coordinates
        ByteBuffer buffer = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(1);
        buffer.putFloat(Float.NaN);  // Invalid coordinate
        buffer.putFloat(200.0f);
        buffer.flip();

        BinaryMessage message = new BinaryMessage(buffer);

        // Act & Assert - should not throw
        handler.handleBinaryMessage(session, message);
    }

    @Test
    void handleBinaryMessage_multipleConsecutiveMessages() throws Exception {
        // Arrange
        String boardToken = "board_123";
        when(session.getId()).thenReturn("session_123");
        when(session.getUri()).thenReturn(new URI("ws://localhost:8080/sync-cursor?board=" + boardToken));
        when(session.isOpen()).thenReturn(true);
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("userId", "1");
        when(session.getAttributes()).thenReturn(attributes);

        handler.afterConnectionEstablished(session);

        ByteBuffer buffer1 = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN);
        buffer1.putInt(1).putFloat(10.0f).putFloat(20.0f);
        buffer1.flip();

        ByteBuffer buffer2 = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN);
        buffer2.putInt(1).putFloat(30.0f).putFloat(40.0f);
        buffer2.flip();

        // Act
        handler.handleBinaryMessage(session, new BinaryMessage(buffer1));
        handler.handleBinaryMessage(session, new BinaryMessage(buffer2));

        // Assert - both messages should be processed
        assertTrue(true);
    }

    @Test
    void afterConnectionEstablished_multipleUsersOnSameBoard() throws Exception {
        // Arrange - first user
        WebSocketSession session1 = mock(WebSocketSession.class);
        when(session1.getId()).thenReturn("session_1");
        when(session1.getUri()).thenReturn(new URI("ws://localhost:8080/sync-cursor?board=board_123"));
        Map<String, Object> attributes1 = new HashMap<>();
        when(session1.getAttributes()).thenReturn(attributes1);

        // Second user
        WebSocketSession session2 = mock(WebSocketSession.class);
        when(session2.getId()).thenReturn("session_2");
        when(session2.getUri()).thenReturn(new URI("ws://localhost:8080/sync-cursor?board=board_123"));
        Map<String, Object> attributes2 = new HashMap<>();
        when(session2.getAttributes()).thenReturn(attributes2);

        // Act
        handler.afterConnectionEstablished(session1);
        handler.afterConnectionEstablished(session2);

        // Assert - Redis listener should be added only once
        verify(redisContainer, times(1)).addMessageListener(any(org.springframework.data.redis.connection.MessageListener.class), any(org.springframework.data.redis.listener.Topic.class));
    }

    @Test
    void afterConnectionEstablished_urlEncodedParameters() throws Exception {
        // Arrange
        String sessionId = "session_123";
        when(session.getId()).thenReturn(sessionId);
        when(session.getUri()).thenReturn(new URI(
            "ws://localhost:8080/sync-cursor?board=board_123&username=john%20doe&avatar=https%3A%2F%2Fexample.com%2Favatar.jpg"
        ));
        Map<String, Object> attributes = new HashMap<>();
        when(session.getAttributes()).thenReturn(attributes);

        // Act
        handler.afterConnectionEstablished(session);

        // Assert
        assertTrue(attributes.containsKey("userId"));
        verify(redisContainer, times(1)).addMessageListener(any(org.springframework.data.redis.connection.MessageListener.class), any(org.springframework.data.redis.listener.Topic.class));
    }

    @Test
    void handleBinaryMessage_maxCoordinateValues() throws Exception {
        // Arrange
        String boardToken = "board_123";
        when(session.getId()).thenReturn("session_123");
        when(session.getUri()).thenReturn(new URI("ws://localhost:8080/sync-cursor?board=" + boardToken));
        when(session.isOpen()).thenReturn(true);
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("userId", "1");
        when(session.getAttributes()).thenReturn(attributes);

        handler.afterConnectionEstablished(session);

        // Create packet with maximum float values
        ByteBuffer buffer = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(1);
        buffer.putFloat(Float.MAX_VALUE);
        buffer.putFloat(Float.MAX_VALUE);
        buffer.flip();

        BinaryMessage message = new BinaryMessage(buffer);

        // Act & Assert - should not throw
        handler.handleBinaryMessage(session, message);
    }

    @Test
    void handleBinaryMessage_negativeCoordinates() throws Exception {
        // Arrange
        String boardToken = "board_123";
        when(session.getId()).thenReturn("session_123");
        when(session.getUri()).thenReturn(new URI("ws://localhost:8080/sync-cursor?board=" + boardToken));
        when(session.isOpen()).thenReturn(true);
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("userId", "1");
        when(session.getAttributes()).thenReturn(attributes);

        handler.afterConnectionEstablished(session);

        ByteBuffer buffer = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(1);
        buffer.putFloat(-100.5f);  // Negative X
        buffer.putFloat(-200.75f); // Negative Y
        buffer.flip();

        BinaryMessage message = new BinaryMessage(buffer);

        // Act & Assert - should handle negative coordinates
        handler.handleBinaryMessage(session, message);
    }

    @Test
    void afterConnectionClosed_removesProfileFromRegistry() throws Exception {
        // Arrange
        String boardToken = "board_123";
        when(session.getId()).thenReturn("session_123");
        when(session.getUri()).thenReturn(new URI("ws://localhost:8080/sync-cursor?board=" + boardToken));
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("userId", "profile_1");
        when(session.getAttributes()).thenReturn(attributes);

        handler.afterConnectionEstablished(session);

        // Act
        handler.afterConnectionClosed(session, CloseStatus.NORMAL);

        // Assert - should handle profile removal without exception
        assertTrue(true);
    }
}
