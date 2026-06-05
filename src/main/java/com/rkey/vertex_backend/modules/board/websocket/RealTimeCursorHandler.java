package com.rkey.vertex_backend.modules.board.websocket;

import com.rkey.vertex_backend.modules.board.service.BoardProfileRegistry;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
public class RealTimeCursorHandler extends BinaryWebSocketHandler implements MessageListener {

    private final RedisTemplate<String, String> redisTemplate;
    private final RedisMessageListenerContainer redisContainer;

    // Local tracking of sockets connected specifically to this JVM instance
    private final Map<String, Set<WebSocketSession>> localBoardSessions = new ConcurrentHashMap<>();

    // Unique ID for this node so we can tag outgoing Redis messages and skip
    // re-broadcasting to local sessions that already received the message directly.
    private final String nodeId = java.util.UUID.randomUUID().toString().replace("-", "");

    // Virtual-thread executor (Java 21+). Used for non-blocking asynchronous submissions 
    // to prevent network IO or slow sockets from stalling the netty/tomcat selector loops.
    private final ExecutorService fanOutExecutor = Executors.newVirtualThreadPerTaskExecutor();

    private static final String REDIS_TOPIC_PREFIX = "board:cursor:";
    
    // Delimiter that cannot appear in a UUID node-id (hex chars only) or session ID
    private static final byte DELIMITER = (byte) '|';
    private static final int CURSOR_PACKET_SIZE = 12;

    public RealTimeCursorHandler(RedisTemplate<String, String> redisTemplate,
                                 RedisMessageListenerContainer redisContainer) {
        this.redisTemplate = redisTemplate;
        this.redisContainer = redisContainer;
    }

    // -------------------------------------------------------------------------
    // Connection lifecycle
    // -------------------------------------------------------------------------

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String boardToken = extractBoardToken(session);
        if (boardToken == null) {
            session.close(org.springframework.web.socket.CloseStatus.BAD_DATA);
            return;
        }

        boolean isFirstLocalUser = !localBoardSessions.containsKey(boardToken)
                || localBoardSessions.get(boardToken).isEmpty();

        localBoardSessions.computeIfAbsent(boardToken, k -> ConcurrentHashMap.newKeySet()).add(session);

        // Extract user info from query parameters
        String query = session.getUri().getQuery();
        String userId = null;
        String username = "";
        String avatar = "";

        if (query != null) {
            userId = getQueryParam(query, "userId");
            if (userId == null) userId = getQueryParam(query, "id");
            username = getQueryParam(query, "username");
            avatar = getQueryParam(query, "avatar");
            if (avatar == null) avatar = getQueryParam(query, "avatarUrl");
        }

        String safeUsername = username != null ? username : "";
        String safeAvatar = avatar != null ? avatar : "";
        var profile = BoardProfileRegistry.registerProfile(boardToken, userId, safeUsername, safeAvatar);
        session.getAttributes().put("userId", profile.id());

        // Dynamically listen to this board's Redis channel only once per node
        if (isFirstLocalUser) {
            redisContainer.addMessageListener(this, new ChannelTopic(REDIS_TOPIC_PREFIX + boardToken));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session,
                                      org.springframework.web.socket.CloseStatus status) throws Exception {
        String boardToken = extractBoardToken(session);
        if (boardToken != null && localBoardSessions.containsKey(boardToken)) {
            Set<WebSocketSession> sessions = localBoardSessions.get(boardToken);
            sessions.remove(session);

            if (sessions.isEmpty()) {
                localBoardSessions.remove(boardToken);
                redisContainer.removeMessageListener(this, new ChannelTopic(REDIS_TOPIC_PREFIX + boardToken));
            }
        }

        String userId = (String) session.getAttributes().get("userId");
        if (boardToken != null && userId != null) {
            BoardProfileRegistry.removeProfile(boardToken, userId);
        }
    }

    // -------------------------------------------------------------------------
    // Inbound binary message from a WebSocket client
    // -------------------------------------------------------------------------

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) throws Exception {
        String boardToken = extractBoardToken(session);
        if (boardToken == null) return;

        ByteBuffer byteBuffer = message.getPayload();
        if (byteBuffer == null || byteBuffer.remaining() < CURSOR_PACKET_SIZE) return;

        // Strict 12-byte cursor packet: [Id:int32, X:float32, Y:float32] little-endian
        byte[] rawBytes = new byte[byteBuffer.remaining()];
        byteBuffer.get(rawBytes);
        byte[] cursorPacket = rawBytes.length == CURSOR_PACKET_SIZE ? rawBytes : java.util.Arrays.copyOf(rawBytes, CURSOR_PACKET_SIZE);

        // Validate fields
        ByteBuffer parsed = ByteBuffer.wrap(cursorPacket).order(ByteOrder.LITTLE_ENDIAN);
        int incomingId = parsed.getInt();
        float x = parsed.getFloat();
        float y = parsed.getFloat();
        if (Float.isNaN(x) || Float.isNaN(y)) return;

        // Enforce sender identity from authenticated session attribute
        String userId = (String) session.getAttributes().get("userId");
        int idToUse = incomingId;
        if (userId != null) {
            try {
                idToUse = Integer.parseInt(userId);
            } catch (NumberFormatException ignored) {
                // fall back to incomingId if registration uses non-integer profiles
            }
        }
        if (idToUse != incomingId) {
            ByteBuffer rebuilt = ByteBuffer.allocate(CURSOR_PACKET_SIZE).order(ByteOrder.LITTLE_ENDIAN);
            rebuilt.putInt(idToUse).putFloat(x).putFloat(y);
            cursorPacket = rebuilt.array();
        }

        final byte[] finalPacket = cursorPacket;
        final BinaryMessage binaryMessage = new BinaryMessage(finalPacket);
        final String senderSid = session.getId();

        // ---- 1. Fast-path: fan out directly to every local session on this node ----
        Set<WebSocketSession> localSessions = localBoardSessions.get(boardToken);
        if (localSessions != null) {
            for (WebSocketSession s : localSessions) {
                if (s.isOpen() && !s.getId().equals(senderSid)) {
                    fanOutExecutor.submit(() -> {
                        try {
                            s.sendMessage(binaryMessage);
                        } catch (IOException ignored) {
                            // Session lifecycle cleanup handles closed states gracefully
                        }
                    });
                }
            }
        }

        // ---- 2. Publish to Redis for cross-node delivery (Asynchronous) ----
        // Jedis blocks the execution context during publishes. Offloading this execution
        // ensures a network lag spike to Redis won't slow down the native WebSocket worker threads.
        fanOutExecutor.submit(() -> publishToRedis(boardToken, senderSid, finalPacket));
    }

    // -------------------------------------------------------------------------
    // Inbound Redis Pub/Sub message from another node
    // -------------------------------------------------------------------------

    @Override
    public void onMessage(Message message, byte[] pattern) {
        byte[] channelBytes = message.getChannel();
        if (channelBytes == null) return;

        String channel = new String(channelBytes, StandardCharsets.UTF_8);
        if (channel.length() <= REDIS_TOPIC_PREFIX.length()) return;
        String boardToken = channel.substring(REDIS_TOPIC_PREFIX.length());

        Set<WebSocketSession> localSessions = localBoardSessions.get(boardToken);
        if (localSessions == null || localSessions.isEmpty()) return;

        byte[] body = message.getBody();
        if (body == null) return;

        int[] delimiters = findDelimiters(body);
        if (delimiters == null) return;

        int nodeIdEnd = delimiters[0];
        int sessionIdEnd = delimiters[1];

        // short-circuit execution if frame originated from this node instance
        String senderNodeId = new String(body, 0, nodeIdEnd, StandardCharsets.UTF_8);
        if (nodeId.equals(senderNodeId)) return;

        String senderSessionId = new String(body, nodeIdEnd + 1, sessionIdEnd - nodeIdEnd - 1, StandardCharsets.UTF_8);

        int packetStart = sessionIdEnd + 1;
        if (body.length - packetStart < CURSOR_PACKET_SIZE) return;
        byte[] cursorPacket = new byte[CURSOR_PACKET_SIZE];
        System.arraycopy(body, packetStart, cursorPacket, 0, CURSOR_PACKET_SIZE);

        BinaryMessage binaryMessage = new BinaryMessage(cursorPacket);

        for (WebSocketSession s : localSessions) {
            if (s.isOpen() && !s.getId().equals(senderSessionId)) {
                fanOutExecutor.submit(() -> {
                    try {
                        s.sendMessage(binaryMessage);
                    } catch (IOException ignored) {
                        // Cleanup handled by afterConnectionClosed
                    }
                });
            }
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void publishToRedis(String boardToken, String senderSessionId, byte[] cursorPacket) {
        byte[] nodeIdBytes = nodeId.getBytes(StandardCharsets.UTF_8);
        byte[] sessionIdBytes = senderSessionId.getBytes(StandardCharsets.UTF_8);
        byte[] channelBytes = (REDIS_TOPIC_PREFIX + boardToken).getBytes(StandardCharsets.UTF_8);

        int frameLen = nodeIdBytes.length + 1 + sessionIdBytes.length + 1 + CURSOR_PACKET_SIZE;
        byte[] frame = new byte[frameLen];
        int pos = 0;
        System.arraycopy(nodeIdBytes, 0, frame, pos, nodeIdBytes.length);
        pos += nodeIdBytes.length;
        frame[pos++] = DELIMITER;
        System.arraycopy(sessionIdBytes, 0, frame, pos, sessionIdBytes.length);
        pos += sessionIdBytes.length;
        frame[pos++] = DELIMITER;
        System.arraycopy(cursorPacket, 0, frame, pos, CURSOR_PACKET_SIZE);

        redisTemplate.execute((RedisCallback<Void>) (RedisConnection conn) -> {
            conn.publish(channelBytes, frame);
            return null;
        });
    }

    private int[] findDelimiters(byte[] body) {
        int first = -1;
        int second = -1;
        for (int i = 0; i < body.length; i++) {
            if (body[i] == DELIMITER) {
                if (first == -1) {
                    first = i;
                } else {
                    second = i;
                    break;
                }
            }
        }
        if (first == -1 || second == -1) return null;
        return new int[]{ first, second };
    }

    private String getQueryParam(String query, String paramName) {
        for (String pair : query.split("&")) {
            String[] parts = pair.split("=");
            if (parts.length > 0 && parts[0].equals(paramName)) {
                try {
                    return parts.length > 1 ? java.net.URLDecoder.decode(parts[1], "UTF-8") : "";
                } catch (Exception e) {
                    return parts.length > 1 ? parts[1] : "";
                }
            }
        }
        return null;
    }

    private String extractBoardToken(WebSocketSession session) {
        if (session.getUri() == null) return null;
        String query = session.getUri().getQuery();
        if (query == null) return null;
        return getQueryParam(query, "board");
    }
}