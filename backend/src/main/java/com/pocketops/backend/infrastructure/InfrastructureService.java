package com.pocketops.backend.infrastructure;

import com.pocketops.backend.common.error.ApiException;
import com.pocketops.backend.common.error.ErrorCode;
import com.pocketops.backend.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Service
public class InfrastructureService {
    private static final Set<Capability> SELF_HOSTED_CAPABILITIES = EnumSet.allOf(Capability.class);

    private final InfrastructureRepository infrastructureRepository;
    private final UserRepository userRepository;

    public InfrastructureService(
            InfrastructureRepository infrastructureRepository,
            UserRepository userRepository
    ) {
        this.infrastructureRepository = infrastructureRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public InfrastructureResponse create(String userId, CreateInfrastructureRequest request) {
        var user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.AUTHENTICATION_REQUIRED, HttpStatus.UNAUTHORIZED, "Authentication required."));

        InfrastructureEntity entity = new InfrastructureEntity();
        entity.setUser(user);
        entity.setName(request.name().trim());
        entity.setType(request.type());
        entity.setProviderType(normalizeProviderType(request));
        entity.setHealthStatus(HealthStatus.UNKNOWN);
        entity.setCapabilities(defaultCapabilities(request.type()));
        return InfrastructureResponse.from(infrastructureRepository.save(entity));
    }

    @Transactional(readOnly = true)
    public List<InfrastructureResponse> list(String userId) {
        return infrastructureRepository.findByUser_IdOrderByCreatedAtDesc(userId)
                .stream()
                .map(InfrastructureResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public InfrastructureResponse get(String userId, String infrastructureId) {
        return InfrastructureResponse.from(resolveOwned(userId, infrastructureId));
    }

    @Transactional
    public void delete(String userId, String infrastructureId) {
        infrastructureRepository.delete(resolveOwned(userId, infrastructureId));
    }

    public InfrastructureEntity resolveOwned(String userId, String infrastructureId) {
        return infrastructureRepository.findByIdAndUser_Id(infrastructureId, userId)
                .orElseThrow(() -> new ApiException(
                        ErrorCode.INFRASTRUCTURE_NOT_FOUND,
                        HttpStatus.NOT_FOUND,
                        "Infrastructure not found."
                ));
    }

    private String normalizeProviderType(CreateInfrastructureRequest request) {
        if (request.type() == InfrastructureType.SELF_HOSTED || request.providerType() == null) {
            return null;
        }
        return request.providerType().trim();
    }

    private Set<Capability> defaultCapabilities(InfrastructureType type) {
        if (type == InfrastructureType.SELF_HOSTED) {
            return EnumSet.copyOf(SELF_HOSTED_CAPABILITIES);
        }
        return EnumSet.noneOf(Capability.class);
    }
}
