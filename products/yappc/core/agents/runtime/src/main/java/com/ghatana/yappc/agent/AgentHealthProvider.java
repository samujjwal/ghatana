package com.ghatana.yappc.agent;

import java.util.Map;

/**
 * Read-only health view over any YAPPC agent registry implementation.
 *
 * <p>Allows {@link AgentHeartbeatService} to accept a production
 * {@link YappcAgentRegistryAdapter} instance or lightweight test doubles
 * without tying the monitoring service to a concrete registry class.
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
    Map<String, AgentLifecycleStatus> getHealthStatus();

    /**
     * Returns the number of registered agents.
     *
     * @return agent count (≥ 0)
     */
    int getAgentCount();
}
