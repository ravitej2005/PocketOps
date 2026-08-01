package com.pocketops.backend.auth;

import com.pocketops.backend.security.AuthenticatedUser;
import com.pocketops.backend.session.SessionResponse;
import com.pocketops.backend.session.SessionService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class AuthController {
    private final AuthService authService;
    private final SessionService sessionService;

    public AuthController(AuthService authService, SessionService sessionService) {
        this.authService = authService;
        this.sessionService = sessionService;
    }

    @PostMapping("/auth/register")
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/auth/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/auth/github")
    public AuthResponse github(@Valid @RequestBody GitHubLoginRequest request) {
        return authService.loginWithGitHub(request);
    }

    @PostMapping("/auth/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request);
    }

    @PostMapping("/auth/logout")
    public void logout(@AuthenticationPrincipal AuthenticatedUser user) {
        sessionService.revokeCurrentSession(user);
    }

    @PostMapping("/auth/logout-all")
    public void logoutAll(@AuthenticationPrincipal AuthenticatedUser user) {
        sessionService.revokeAllSessions(user.userId());
    }

    @GetMapping("/sessions")
    public List<SessionResponse> sessions(@AuthenticationPrincipal AuthenticatedUser user) {
        return sessionService.listSessions(user.userId(), user.sessionId());
    }

    @DeleteMapping("/sessions/{sessionId}")
    public void revokeSession(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable String sessionId
    ) {
        sessionService.revokeSession(user.userId(), sessionId);
    }

    @DeleteMapping("/sessions")
    public void revokeAllSessions(@AuthenticationPrincipal AuthenticatedUser user) {
        sessionService.revokeAllSessions(user.userId());
    }
}
