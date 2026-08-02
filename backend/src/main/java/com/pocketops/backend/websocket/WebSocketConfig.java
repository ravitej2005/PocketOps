package com.pocketops.backend.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    private final InfrastructureUpdatesWebSocketHandler infrastructureUpdatesWebSocketHandler;

    public WebSocketConfig(InfrastructureUpdatesWebSocketHandler infrastructureUpdatesWebSocketHandler) {
        this.infrastructureUpdatesWebSocketHandler = infrastructureUpdatesWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(infrastructureUpdatesWebSocketHandler, "/ws/infrastructures/{infrastructureId}")
                .addInterceptors(infrastructureUpdatesWebSocketHandler)
                .setAllowedOrigins("*");
    }
}
