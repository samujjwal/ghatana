package com.ghatana.yappc.agent;

import java.util.List;

/**
 * Read-only query view over any YAPPC agent registry implementation.
 *
 * <p>Allows coordinator classes like {@link com.ghatana.yappc.agent.coordinator.DeliveryCoordinatorGenerator}
 * and {@link com.ghatana.yappc.agent.coordinator.PlatformDeliveryCoordinator} to work with
 * {@link YappcAgentRegistryAdapter} and test doubles through one contract.
 *
 * @doc.type interface
 * @doc.purpose Unified query contract for YAPPC agent registries
 * @doc.layer product
 * @doc.pattern Strategy
 */
public interface AgentRegistryView {

    /**
     * Returns all agents registered for the given SDLC phase.
     *
     * @param phase the SDLC phase (e.g., "architecture", "implementation")
     * @return unmodifiable list of agents (empty if none found)
     */
    List<YAPPCAgentBase<?, ?>> getAgentsByPhase(String phase);
}
