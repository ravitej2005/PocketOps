package com.pocketops.backend.monitoring;

import com.pocketops.backend.infrastructure.InfrastructureResourceRepository;
import com.pocketops.backend.proto.ContainerMetric;
import com.pocketops.backend.websocket.InfrastructureUpdatesWebSocketHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MonitoringService {
    private final InfrastructureResourceRepository resourceRepository;
    private final InfrastructureUpdatesWebSocketHandler webSocketHandler;
    private final Map<String, ContainerMetricUpdate> latestMetrics = new ConcurrentHashMap<>();

    public MonitoringService(
            InfrastructureResourceRepository resourceRepository,
            InfrastructureUpdatesWebSocketHandler webSocketHandler
    ) {
        this.resourceRepository = resourceRepository;
        this.webSocketHandler = webSocketHandler;
    }

    @Transactional(readOnly = true)
    public Optional<ContainerMetricUpdate> latestMetric(String infrastructureId, String externalResourceId) {
        return Optional.ofNullable(latestMetrics.get(key(infrastructureId, externalResourceId)));
    }

    @Transactional(readOnly = true)
    public void recordMetric(String infrastructureId, ContainerMetric metric, long timestampUnixMs) {
        if (resourceRepository.findByInfrastructure_IdAndExternalResourceId(
                infrastructureId,
                metric.getExternalResourceId()
        ).isEmpty()) {
            return;
        }
        ContainerMetricUpdate update = new ContainerMetricUpdate(
                "MetricUpdate",
                infrastructureId,
                metric.getExternalResourceId(),
                metric.getCpuPercent(),
                metric.getMemoryUsageBytes(),
                metric.getMemoryLimitBytes(),
                metric.getNetworkRxBytes(),
                metric.getNetworkTxBytes(),
                metric.getUptimeSeconds(),
                metric.getStartedAtUnixMs(),
                timestampUnixMs
        );
        latestMetrics.put(key(infrastructureId, metric.getExternalResourceId()), update);
        webSocketHandler.broadcast(infrastructureId, update);
    }

    private String key(String infrastructureId, String externalResourceId) {
        return infrastructureId + ":" + externalResourceId;
    }
}
