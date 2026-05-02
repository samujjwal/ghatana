/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.kernel.bridge.port;

/**
 * Kernel-owned health-indicator port for bridge adapters.
 *
 * <p>The kernel defines this port; the runtime product wiring binds it to the
 * canonical observability infrastructure (e.g. Micrometer health registry, Actuator).
 * Bridge adapters must not depend directly on concrete health libraries — they
 * call this port instead.</p>
 *
 * <p>Bridge adapters should call {@link #reportHealthy(String)} when they
 * successfully complete a round-trip and {@link #reportDegraded(String, String)}
 * or {@link #reportUnhealthy(String, String)} on consistent failures or timeouts.</p>
 *
 * @doc.type interface
 * @doc.purpose Kernel-owned health port for bridge adapter status reporting
 * @doc.layer core
 * @doc.pattern Port
 * @author Ghatana Kernel Team
 * @since 1.3.0
 */
public interface BridgeHealthIndicator {

    /**
     * Reports the named bridge as fully operational.
     *
     * @param bridgeId identifier of the bridge reporting health
     */
    void reportHealthy(String bridgeId);

    /**
     * Reports the named bridge as degraded — still functional but with reduced
     * capacity or elevated latency.
     *
     * @param bridgeId identifier of the bridge reporting degradation
     * @param reason   human-readable explanation of the degraded state
     */
    void reportDegraded(String bridgeId, String reason);

    /**
     * Reports the named bridge as unhealthy — not functional.
     *
     * @param bridgeId identifier of the bridge reporting failure
     * @param reason   human-readable explanation of the failure
     */
    void reportUnhealthy(String bridgeId, String reason);

    /**
     * A no-op implementation that discards all health signals; use in tests or
     * development environments where no health backend is wired.
     */
    static BridgeHealthIndicator noOp() {
        return new BridgeHealthIndicator() {
            @Override
            public void reportHealthy(String bridgeId) { /* no-op */ }

            @Override
            public void reportDegraded(String bridgeId, String reason) { /* no-op */ }

            @Override
            public void reportUnhealthy(String bridgeId, String reason) { /* no-op */ }
        };
    }
}
