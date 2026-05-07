package com.ghatana.aep.engine.registry;

import com.ghatana.aep.registry.AgentRegistryContracts;
import com.ghatana.agent.catalog.CatalogAgentEntry;
import io.activej.promise.Promise;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Central agent registry service for AEP v2.5+.
 *
 * Unified discovery and execution of agents from multiple sources (YAPPC, DataCloud, etc.)
 * with optional DataCloud persistence backend.
 *
 * @doc.type class
 * @doc.purpose Unified agent registry and discovery service
 * @doc.layer product
 * @doc.pattern Service
 */
public class AepCentralRegistryService {

    private static final Logger log = LoggerFactory.getLogger(AepCentralRegistryService.class);

    private final Map<String, AgentInfo> registry = new ConcurrentHashMap<>();
    private final AgentRegistryContracts backendRegistry;

    public AepCentralRegistryService() {
        this(null);
    }

    public AepCentralRegistryService(AgentRegistryContracts backendRegistry) {
        // In-memory registry implementation for orchestrator runtime.
        this.backendRegistry = backendRegistry;
    }

    public Promise<List<AgentInfo>> discoverAgents() {
        log.debug("Discovering agents from unified registry");
        if (backendRegistry == null) {
            return Promise.of(new ArrayList<>(registry.values()));
        }

        return backendRegistry.listAgents().map(entries -> {
            Map<String, AgentInfo> merged = new LinkedHashMap<>();
            entries.stream().map(this::toAgentInfo).forEach(info -> merged.put(info.id(), info));
            registry.values().forEach(info -> merged.put(info.id(), info));
            return new ArrayList<>(merged.values());
        });
    }

    public Promise<List<AgentInfo>> listAll() {
        return discoverAgents();
    }

    public Promise<Optional<AgentInfo>> resolveAgent(String agentId) {
        log.debug("Resolving agent: {}", agentId);
        if (agentId == null || agentId.isBlank()) {
            return Promise.of(Optional.empty());
        }

        AgentInfo local = registry.get(agentId);
        if (local != null) {
            return Promise.of(Optional.of(local));
        }

        if (backendRegistry == null) {
            return Promise.of(Optional.empty());
        }

        return backendRegistry.getAgent(agentId).map(opt -> opt.map(this::toAgentInfo));
    }

    public Promise<Optional<AgentInfo>> resolve(String agentId) {
        return resolveAgent(agentId);
    }

    public Promise<AgentExecutionResult> executeAgent(String agentId, Object input) {
        log.debug("Executing agent: {}", agentId);
        return resolveAgent(agentId).map(optAgent -> {
            if (optAgent.isEmpty()) {
                return AgentExecutionResult.failure(agentId, "Agent not found");
            }

            return AgentExecutionResult.success(
                    agentId,
                    Map.of(
                            "agentId", agentId,
                            "status", "accepted",
                            "input", input));
        });
    }

    public Promise<Void> registerAgent(AgentInfo agent) {
        if (agent == null || agent.id == null || agent.id.isBlank()) {
            return Promise.ofException(new IllegalArgumentException("agent.id is required"));
        }
        log.debug("Registering agent: {}", agent.id);
        registry.put(agent.id, agent);
        return Promise.complete();
    }

    public Promise<Void> deregisterAgent(String agentId) {
        log.debug("Deregistering agent: {}", agentId);
        if (agentId != null && !agentId.isBlank()) {
            registry.remove(agentId);
        }
        return Promise.complete();
    }

    public Promise<Void> deregister(String agentId) {
        return deregisterAgent(agentId);
    }

    private AgentInfo toAgentInfo(CatalogAgentEntry entry) {
        AgentInfo info =
                new AgentInfo(entry.getId(), entry.getName(), entry.getLevel().toUpperCase());
        info.version = entry.getVersion();
        info.description = entry.getDescription();
        info.product = entry.getCatalogId() != null ? entry.getCatalogId() : "aep";
        info.capabilities = entry.getCapabilities().stream().sorted().toList();
        info.config = Map.of(
                "routing", entry.getRouting(),
                "delegation", entry.getDelegation(),
                "governance", entry.getGovernance());
        return info;
    }
}
