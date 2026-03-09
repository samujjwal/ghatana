package com.ghatana.virtualorg.framework.collaboration;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory directory of VirtualOrg agents for collaboration routing.
 *
 * <p>
 * <b>Purpose</b><br>
 * Maintains a runtime directory of active agents with their capabilities,
 * roles, status, and metadata — used by {@link DefaultDelegationManager}
 * for capability-based routing and workload-aware delegation.
 *
 * <p>This is distinct from:
 * <ul>
 *   <li>{@code com.ghatana.virtualorg.framework.agent.AgentRegistry} — factory registry
 *       for agent creation from YAML config</li>
 *   <li>{@code com.ghatana.agent.registry.AgentRegistry} — platform SPI for persistent,
 *       distributed agent registration backed by Data-Cloud</li>
 * </ul>
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * VirtualOrgAgentDirectory directory = new VirtualOrgAgentDirectory();
 *
 * directory.register(AgentInfo.builder()
 *     .id("code-reviewer")
 *     .name("Code Review Agent")
 *     .role("reviewer")
 *     .capabilities(Set.of("code-review", "security-audit"))
 *     .build());
 *
 * List<String> reviewers = directory.findByCapability("code-review");
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose VirtualOrg in-memory agent capability directory for collaboration routing
 * @doc.layer product
 * @doc.pattern Registry
 */
public class VirtualOrgAgentDirectory {

    private final ConcurrentHashMap<String, AgentInfo> agents = new ConcurrentHashMap<>();

    /**
     * Registers an agent.
     *
     * @param info Agent information
     */
    public void register(AgentInfo info) {
        agents.put(info.id(), info);
    }

    /**
     * Unregisters an agent.
     *
     * @param agentId Agent ID
     */
    public void unregister(String agentId) {
        agents.remove(agentId);
    }

    /**
     * Gets agent information by ID.
     *
     * @param agentId Agent ID
     * @return Agent info or null if not found
     */
    public AgentInfo get(String agentId) {
        return agents.get(agentId);
    }

    /**
     * Gets all registered agents.
     *
     * @return List of all agents
     */
    public List<AgentInfo> getAll() {
        return List.copyOf(agents.values());
    }

    /**
     * Finds agents by capability.
     *
     * @param capability Capability to search for
     * @return List of agent IDs with the capability
     */
    public List<String> findByCapability(String capability) {
        return agents.values().stream()
                .filter(a -> a.capabilities().contains(capability))
                .filter(a -> a.status() == AgentInfo.Status.ONLINE
                || a.status() == AgentInfo.Status.BUSY)
                .map(AgentInfo::id)
                .toList();
    }

    /**
     * Finds agents by role.
     *
     * @param role Role to search for
     * @return List of agent IDs with the role
     */
    public List<String> findByRole(String role) {
        return agents.values().stream()
                .filter(a -> a.role().equals(role))
                .filter(a -> a.status() == AgentInfo.Status.ONLINE
                || a.status() == AgentInfo.Status.BUSY)
                .map(AgentInfo::id)
                .toList();
    }

    /**
     * Finds available agents.
     *
     * @return List of available agent IDs
     */
    public List<String> findAvailable() {
        return agents.values().stream()
                .filter(a -> a.status() == AgentInfo.Status.ONLINE)
                .map(AgentInfo::id)
                .toList();
    }

    /**
     * Updates agent status.
     *
     * @param agentId Agent ID
     * @param status New status
     */
    public void updateStatus(String agentId, AgentInfo.Status status) {
        AgentInfo info = agents.get(agentId);
        if (info != null) {
            AgentInfo updated = info.withStatus(status);
            agents.put(agentId, updated);
        }
    }

    /**
     * Gets count of agents by status.
     *
     * @return Map of status to count
     */
    public Map<AgentInfo.Status, Long> getStatusCounts() {
        return agents.values().stream()
                .collect(Collectors.groupingBy(
                        AgentInfo::status,
                        Collectors.counting()
                ));
    }

    /**
     * Agent information record.
     */
    public record AgentInfo(
            String id,
            String name,
            String role,
            String department,
            Set<String> capabilities,
            Status status,
            Map<String, Object> metadata
    ) {

        public enum Status {
            ONLINE,
            BUSY,
            OFFLINE,
            MAINTENANCE
        }

        public static Builder builder() {
            return new Builder();
        }

        public AgentInfo withStatus(Status newStatus) {
            return new AgentInfo(id, name, role, department, capabilities, newStatus, metadata);
        }

        public static class Builder {

            private String id;
            private String name;
            private String role;
            private String department;
            private Set<String> capabilities = Set.of();
            private Status status = Status.ONLINE;
            private Map<String, Object> metadata = Map.of();

            public Builder id(String id) {
                this.id = id;
                return this;
            }

            public Builder name(String name) {
                this.name = name;
                return this;
            }

            public Builder role(String role) {
                this.role = role;
                return this;
            }

            public Builder department(String department) {
                this.department = department;
                return this;
            }

            public Builder capabilities(Set<String> capabilities) {
                this.capabilities = capabilities;
                return this;
            }

            public Builder capability(String... capabilities) {
                this.capabilities = Set.of(capabilities);
                return this;
            }

            public Builder status(Status status) {
                this.status = status;
                return this;
            }

            public Builder metadata(Map<String, Object> metadata) {
                this.metadata = metadata;
                return this;
            }

            public AgentInfo build() {
                return new AgentInfo(id, name, role, department,
                        Set.copyOf(capabilities), status, Map.copyOf(metadata));
            }
        }
    }
}
