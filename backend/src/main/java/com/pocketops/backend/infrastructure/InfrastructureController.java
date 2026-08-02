package com.pocketops.backend.infrastructure;

import com.pocketops.backend.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/infrastructures")
public class InfrastructureController {
    private final InfrastructureService infrastructureService;
    private final InfrastructureResourceService infrastructureResourceService;

    public InfrastructureController(
            InfrastructureService infrastructureService,
            InfrastructureResourceService infrastructureResourceService
    ) {
        this.infrastructureService = infrastructureService;
        this.infrastructureResourceService = infrastructureResourceService;
    }

    @PostMapping
    public InfrastructureResponse create(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody CreateInfrastructureRequest request
    ) {
        return infrastructureService.create(user.userId(), request);
    }

    @GetMapping
    public List<InfrastructureResponse> list(@AuthenticationPrincipal AuthenticatedUser user) {
        return infrastructureService.list(user.userId());
    }

    @GetMapping("/{id}")
    public InfrastructureResponse get(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable String id
    ) {
        return infrastructureService.get(user.userId(), id);
    }

    @GetMapping("/{id}/resources")
    public List<InfrastructureResourceResponse> resources(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable String id
    ) {
        return infrastructureResourceService.listOwned(user.userId(), id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable String id
    ) {
        infrastructureService.delete(user.userId(), id);
    }
}
