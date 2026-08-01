package com.pocketops.backend.session;

import com.pocketops.backend.common.error.ApiException;
import com.pocketops.backend.common.error.ErrorCode;
import com.pocketops.backend.security.AuthProperties;
import com.pocketops.backend.security.AuthenticatedUser;
import com.pocketops.backend.user.UserEntity;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class SessionService {
    private final UserSessionRepository userSessionRepository;
    private final AuthProperties authProperties;

    public SessionService(UserSessionRepository userSessionRepository, AuthProperties authProperties) {
        this.userSessionRepository = userSessionRepository;
        this.authProperties = authProperties;
    }

    public UserSessionEntity createSession(UserEntity user, String deviceName) {
        UserSessionEntity session = new UserSessionEntity();
        session.setUser(user);
        session.setDeviceName(deviceName);
        session.setRefreshTokenHash("pending");
        session.setExpiresAt(Instant.now().plus(authProperties.refreshTokenDays(), ChronoUnit.DAYS));
        return userSessionRepository.save(session);
    }

    @Transactional(readOnly = true)
    public List<SessionResponse> listSessions(String userId) {
        return listSessions(userId, "");
    }

    @Transactional(readOnly = true)
    public List<SessionResponse> listSessions(String userId, String currentSessionId) {
        return userSessionRepository.findByUser_IdAndRevokedAtIsNullOrderByCreatedAtDesc(userId)
                .stream()
                .map(session -> SessionResponse.from(session, currentSessionId))
                .toList();
    }

    @Transactional
    public void revokeCurrentSession(AuthenticatedUser user) {
        revokeSession(user.userId(), user.sessionId());
    }

    @Transactional
    public void revokeSession(String userId, String sessionId) {
        UserSessionEntity session = userSessionRepository.findByIdAndUser_Id(sessionId, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.AUTHENTICATION_REQUIRED, HttpStatus.NOT_FOUND, "Session not found."));
        revoke(session);
    }

    @Transactional
    public void revokeAllSessions(String userId) {
        Instant now = Instant.now();
        userSessionRepository.findByUser_IdAndRevokedAtIsNull(userId)
                .forEach(session -> session.setRevokedAt(now));
    }

    @Transactional
    public UserSessionEntity findActiveSessionForRotation(String userId, String sessionId) {
        UserSessionEntity session = userSessionRepository.findByIdAndUser_IdForUpdate(sessionId, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.AUTHENTICATION_REQUIRED, HttpStatus.UNAUTHORIZED, "Authentication required."));
        if (session.isRevoked() || session.getExpiresAt().isBefore(Instant.now())) {
            revoke(session);
            throw new ApiException(ErrorCode.AUTHENTICATION_REQUIRED, HttpStatus.UNAUTHORIZED, "Authentication required.");
        }
        return session;
    }

    public void revoke(UserSessionEntity session) {
        if (!session.isRevoked()) {
            session.setRevokedAt(Instant.now());
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revokeImmediately(String sessionId) {
        userSessionRepository.revokeById(sessionId, Instant.now());
    }
}
