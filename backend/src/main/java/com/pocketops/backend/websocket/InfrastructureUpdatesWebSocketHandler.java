package com.pocketops.backend.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pocketops.backend.common.error.ApiException;
import com.pocketops.backend.infrastructure.InfrastructureService;
import com.pocketops.backend.security.JwtService;
import com.pocketops.backend.session.UserSessionRepository;
import io.jsonwebtoken.JwtException;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InfrastructureUpdatesWebSocketHandler extends TextWebSocketHandler implements HandshakeInterceptor {
    public static final String INFRASTRUCTURE_ID_ATTRIBUTE = "infrastructureId";
    private final ObjectMapper objectMapper;
    private final JwtService jwtService;
    private final UserSessionRepository userSessionRepository;
    private final InfrastructureService infrastructureService;
    private final Map<String, Set<WebSocketSession>> sessionsByInfrastructure = new ConcurrentHashMap<>();

    public InfrastructureUpdatesWebSocketHandler(
            JwtService jwtService,
            UserSessionRepository userSessionRepository,
            InfrastructureService infrastructureService
    ) {
        this.objectMapper = new ObjectMapper();
        this.jwtService = jwtService;
        this.userSessionRepository = userSessionRepository;
        this.infrastructureService = infrastructureService;
    }

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes
    ) {
        URI uri = request.getURI();
        String infrastructureId = infrastructureId(uri.getPath());
        String token = queryParam(uri, "token");
        if (infrastructureId == null || token == null || token.isBlank()) {
            return false;
        }
        try {
            JwtService.AccessTokenClaims claims = jwtService.parseAccessToken(token);
            var session = userSessionRepository.findByIdAndUser_Id(claims.sessionId(), claims.userId())
                    .orElse(null);
            if (session == null || session.isRevoked() || session.getExpiresAt().isBefore(Instant.now())) {
                return false;
            }
            infrastructureService.resolveOwned(claims.userId(), infrastructureId);
            attributes.put(INFRASTRUCTURE_ID_ATTRIBUTE, infrastructureId);
            return true;
        } catch (ApiException | JwtException | IllegalArgumentException ex) {
            return false;
        }
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception
    ) {
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String infrastructureId = (String) session.getAttributes().get(INFRASTRUCTURE_ID_ATTRIBUTE);
        sessionsByInfrastructure
                .computeIfAbsent(infrastructureId, ignored -> ConcurrentHashMap.newKeySet())
                .add(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String infrastructureId = (String) session.getAttributes().get(INFRASTRUCTURE_ID_ATTRIBUTE);
        Set<WebSocketSession> sessions = sessionsByInfrastructure.get(infrastructureId);
        if (sessions != null) {
            sessions.remove(session);
        }
    }

    public void broadcast(String infrastructureId, Object payload) {
        Set<WebSocketSession> sessions = sessionsByInfrastructure.get(infrastructureId);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }
        try {
            TextMessage message = new TextMessage(objectMapper.writeValueAsString(payload));
            for (WebSocketSession session : List.copyOf(sessions)) {
                if (session.isOpen()) {
                    session.sendMessage(message);
                } else {
                    sessions.remove(session);
                }
            }
        } catch (Exception ignored) {
        }
    }

    private String infrastructureId(String path) {
        String prefix = "/ws/infrastructures/";
        if (!path.startsWith(prefix)) {
            return null;
        }
        String suffix = path.substring(prefix.length());
        int nextSlash = suffix.indexOf('/');
        return nextSlash >= 0 ? suffix.substring(0, nextSlash) : suffix;
    }

    private String queryParam(URI uri, String name) {
        return UriComponentsBuilder.fromUri(uri)
                .build()
                .getQueryParams()
                .getFirst(name);
    }
}
