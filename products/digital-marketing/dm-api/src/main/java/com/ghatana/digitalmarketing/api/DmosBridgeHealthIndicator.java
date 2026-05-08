package com.ghatana.digitalmarketing.api;

import com.ghatana.kernel.bridge.port.BridgeHealthIndicator;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stateful DMOS bridge health indicator used by readiness endpoints.
 *
 * @doc.type class
 * @doc.purpose Captures bridge health transitions for health/readiness responses
 * @doc.layer product
 * @doc.pattern Adapter
 */
public final class DmosBridgeHealthIndicator implements BridgeHealthIndicator {

    /**
     * Immutable snapshot of the latest bridge health report.
     */
    public record BridgeStatus(String status, String reason, Instant updatedAt) {
    }

    private final Map<String, BridgeStatus> bridgeStatuses = new ConcurrentHashMap<>();

    @Override
    public void reportHealthy(String bridgeId) {
        update(bridgeId, "UP", "operational");
    }

    @Override
    public void reportDegraded(String bridgeId, String reason) {
        update(bridgeId, "DEGRADED", reason);
    }

    @Override
    public void reportUnhealthy(String bridgeId, String reason) {
        update(bridgeId, "DOWN", reason);
    }

    public Map<String, BridgeStatus> snapshot() {
        return Map.copyOf(bridgeStatuses);
    }

    private void update(String bridgeId, String status, String reason) {
        String safeBridgeId = Objects.requireNonNull(bridgeId, "bridgeId must not be null").trim();
        String safeReason = reason == null || reason.isBlank() ? "n/a" : reason.trim();
        bridgeStatuses.put(safeBridgeId, new BridgeStatus(status, safeReason, Instant.now()));
    }
}