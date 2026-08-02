package com.pocketops.backend.monitoring;

public record ContainerMetricUpdate(
        String type,
        String infrastructureId,
        String resourceId,
        double cpuPercent,
        long memoryUsageBytes,
        long memoryLimitBytes,
        long networkRxBytes,
        long networkTxBytes,
        long uptimeSeconds,
        long timestampUnixMs
) {
}
