package com.ghatana.yappc.agent;

import java.util.Map;

/**
 * Read-only health view over any YAPPC agent registry implementation.
 *
 * <p>Allows {@link AgentHeartbeatService} to accept both
 * {@link YAPPCAgentRegistry} (in-memory, test-friendly) and
 * {@link YappcAgentRegistryAdapter} (platform-backed, production) without
 * tying the monitoring service to a concrete registry class.
 *
 * @doc.type interface
 * @doc.purpose Unified health-status contract for YAPPC agent registries
 * @doc.layer product
 * @doc.pattern Strategy
 */
public interface AgentHealthProvider {

    /**
     * Returns a snapshot of every registered agent's current lifecycle status.
     *
     * @return unmodifiable map from agent ID to status
     */
    Map<String, YAPPCAgentRegistry.AgentStatus> getHealthStatus();

    /**
     * Returns the number of registered agents.
     *
     * @return agent count (≥ 0)
     */
    int getAgentCount();
}
