package com.pocketops.backend.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {
    private final AuthProperties authProperties;
    private SecretKey key;

    public JwtService(AuthProperties authProperties) {
        this.authProperties = authProperties;
    }

    @PostConstruct
    void initialize() {
        byte[] secretBytes = authProperties.secret().getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalStateException("JWT secret must be at least 32 bytes.");
        }
        key = Keys.hmacShaKeyFor(secretBytes);
    }

    public String issueAccessToken(String userId, String sessionId, String email) {
        Instant now = Instant.now();
        Instant expiry = now.plus(authProperties.accessTokenMinutes(), ChronoUnit.MINUTES);
        return Jwts.builder()
                .issuer(authProperties.issuer())
                .subject(userId)
                .id(UUID.randomUUID().toString())
                .claim("sid", sessionId)
                .claim("email", email)
                .claim("typ", "access")
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(key)
                .compact();
    }

    public String issueRefreshToken(String userId, String sessionId) {
        Instant now = Instant.now();
        Instant expiry = now.plus(authProperties.refreshTokenDays(), ChronoUnit.DAYS);
        return Jwts.builder()
                .issuer(authProperties.issuer())
                .subject(userId)
                .id(UUID.randomUUID().toString())
                .claim("sid", sessionId)
                .claim("typ", "refresh")
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(key)
                .compact();
    }

    public AccessTokenClaims parseAccessToken(String token) {
        Claims claims = parse(token);
        if (!"access".equals(claims.get("typ", String.class))) {
            throw new IllegalArgumentException("Invalid token type.");
        }
        return new AccessTokenClaims(
                claims.getSubject(),
                claims.get("sid", String.class),
                claims.get("email", String.class)
        );
    }

    public RefreshTokenClaims parseRefreshToken(String token) {
        Claims claims = parse(token);
        if (!"refresh".equals(claims.get("typ", String.class))) {
            throw new IllegalArgumentException("Invalid token type.");
        }
        return new RefreshTokenClaims(
                claims.getSubject(),
                claims.get("sid", String.class)
        );
    }

    public String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable.", e);
        }
    }

    public long refreshTokenDays() {
        return authProperties.refreshTokenDays();
    }

    private Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public record AccessTokenClaims(
            String userId,
            String sessionId,
            String email
    ) {
    }

    public record RefreshTokenClaims(
            String userId,
            String sessionId
    ) {
    }
}
