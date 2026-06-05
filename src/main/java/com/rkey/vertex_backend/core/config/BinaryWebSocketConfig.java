package com.rkey.vertex_backend.core.config;

import com.rkey.vertex_backend.modules.board.websocket.RealTimeCursorHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class BinaryWebSocketConfig implements WebSocketConfigurer {

    private final RealTimeCursorHandler realTimeCursorHandler;

    // Inject the handler as a Spring-managed Bean to ensure dependency injection works inside it later
    public BinaryWebSocketConfig(RealTimeCursorHandler realTimeCursorHandler) {
        this.realTimeCursorHandler = realTimeCursorHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(realTimeCursorHandler, "/sync-cursor")
                .setAllowedOrigins("http://localhost:5174", "http://127.0.0.1:5174", "http://localhost:3000")
                .setAllowedOriginPatterns("*");
    }
}