package com.pocketops.backend.auth;

import com.pocketops.backend.common.error.ApiException;
import com.pocketops.backend.common.error.ErrorCode;
import com.pocketops.backend.security.JwtService;
import com.pocketops.backend.session.DeviceService;
import com.pocketops.backend.session.SessionService;
import com.pocketops.backend.session.UserSessionEntity;
import com.pocketops.backend.user.UserEntity;
import com.pocketops.backend.user.UserRepository;
import io.jsonwebtoken.JwtException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

@Service
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final SessionService sessionService;
    private final DeviceService deviceService;
    private final GitHubOAuthClient gitHubOAuthClient;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            SessionService sessionService,
            DeviceService deviceService,
            GitHubOAuthClient gitHubOAuthClient
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.sessionService = sessionService;
        this.deviceService = deviceService;
        this.gitHubOAuthClient = gitHubOAuthClient;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, "Email is already registered.");
        }

        UserEntity user = new UserEntity();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user = userRepository.save(user);
        deviceService.recordDevice(user, request.platform());
        return issueTokens(user, request.deviceName());
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        UserEntity user = userRepository.findByEmail(normalizeEmail(request.email()))
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials."));
        if (user.getPasswordHash() == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid credentials.");
        }
        deviceService.recordDevice(user, request.platform());
        return issueTokens(user, request.deviceName());
    }

    @Transactional
    public AuthResponse loginWithGitHub(GitHubLoginRequest request) {
        GitHubUserProfile profile = gitHubOAuthClient.exchangeAuthorizationCode(
                request.code(),
                request.redirectUri()
        );

        UserEntity user = userRepository.findByGithubId(profile.githubId())
                .or(() -> userRepository.findByEmail(normalizeEmail(profile.email())))
                .orElseGet(UserEntity::new);
        user.setEmail(normalizeEmail(profile.email()));
        user.setGithubId(profile.githubId());
        user = userRepository.save(user);
        deviceService.recordDevice(user, request.platform());
        return issueTokens(user, request.deviceName());
    }

    @Transactional(noRollbackFor = BadCredentialsException.class)
    public AuthResponse refresh(RefreshRequest request) {
        JwtService.RefreshTokenClaims claims;
        try {
            claims = jwtService.parseRefreshToken(request.refreshToken());
        } catch (JwtException | IllegalArgumentException ex) {
            throw new BadCredentialsException("Invalid refresh token.", ex);
        }

        UserSessionEntity session = sessionService.findActiveSessionForRotation(claims.userId(), claims.sessionId());
        String presentedHash = jwtService.hashToken(request.refreshToken());
        if (!presentedHash.equals(session.getRefreshTokenHash())) {
            sessionService.revoke(session);
            throw new BadCredentialsException("Refresh token has already been rotated.");
        }

        UserEntity user = session.getUser();
        String accessToken = jwtService.issueAccessToken(user.getId(), session.getId(), user.getEmail());
        String refreshToken = jwtService.issueRefreshToken(user.getId(), session.getId());
        session.setRefreshTokenHash(jwtService.hashToken(refreshToken));
        session.setLastUsedAt(Instant.now());
        session.setExpiresAt(Instant.now().plus(jwtService.refreshTokenDays(), ChronoUnit.DAYS));
        return new AuthResponse(accessToken, refreshToken, UserResponse.from(user));
    }

    private AuthResponse issueTokens(UserEntity user, String deviceName) {
        UserSessionEntity session = sessionService.createSession(user, safeDeviceName(deviceName));
        String accessToken = jwtService.issueAccessToken(user.getId(), session.getId(), user.getEmail());
        String refreshToken = jwtService.issueRefreshToken(user.getId(), session.getId());
        session.setRefreshTokenHash(jwtService.hashToken(refreshToken));
        return new AuthResponse(accessToken, refreshToken, UserResponse.from(user));
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String safeDeviceName(String deviceName) {
        if (deviceName == null || deviceName.isBlank()) {
            return "Unknown device";
        }
        return deviceName.trim();
    }
}
