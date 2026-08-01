package com.pocketops.backend.agent;

import com.pocketops.backend.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class AgentController {
    private final AgentRegistrationService agentRegistrationService;
    private final AgentLifecycleService agentLifecycleService;

    public AgentController(
            AgentRegistrationService agentRegistrationService,
            AgentLifecycleService agentLifecycleService
    ) {
        this.agentRegistrationService = agentRegistrationService;
        this.agentLifecycleService = agentLifecycleService;
    }

    @PostMapping("/infrastructures/{id}/agent-registration")
    public RegistrationCredentialResponse createRegistrationCredential(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable String id
    ) {
        return agentRegistrationService.createRegistrationCredential(user.userId(), id);
    }

    @PostMapping("/infrastructures/{id}/agent/revoke")
    public void revokeAgent(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable String id
    ) {
        agentLifecycleService.revokeOwnedAgent(user.userId(), id);
    }

    @PostMapping("/agents/register")
    public AgentRegistrationResponse register(@Valid @RequestBody AgentRegistrationRequest request) {
        return agentRegistrationService.register(request);
    }
}
