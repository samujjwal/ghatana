package com.ghatana.virtualorg.framework.agent;

import com.ghatana.contracts.agent.v1.AgentInputProto;
import com.ghatana.contracts.agent.v1.AgentResultProto;
import com.ghatana.platform.domain.agent.registry.AgentExecutionContext;
import com.ghatana.platform.domain.agent.registry.AgentMetrics;
import com.ghatana.platform.domain.domain.event.Event;
import com.ghatana.virtualorg.framework.hierarchy.Authority;
import com.ghatana.virtualorg.framework.hierarchy.EscalationPath;
import com.ghatana.virtualorg.framework.hierarchy.Role;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Framework-local Agent adapter that implements the canonical core Agent
 * interface. Provides backward-compatible builder API while satisfying the core
 * contract.
 *
 */
public class Agent implements OrganizationalAgent {

    private final String id;
    private final String name;
    private final String department;
    private final String[] capabilities;
    private volatile boolean available = true;
    private volatile int currentWorkload = 0;

    private Agent(String id, String name, String department, String[] capabilities) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = Objects.requireNonNull(name, "name");
        this.department = department == null ? "" : department;
        this.capabilities = capabilities == null ? new String[0] : capabilities.clone();
    }

    // --- OrganizationalAgent interface implementations ---

    @Override
    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDepartment() {
        return department;
    }

    public String[] getFrameworkCapabilities() {
        return capabilities.clone();
    }  // Renamed to avoid conflict with core interface

    public boolean isAvailable() {
        return available;
    }

    public int getCurrentWorkload() {
        return currentWorkload;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    public void setCurrentWorkload(int wl) {
        this.currentWorkload = wl;
    }

    // Builder for convenience
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private String id;
        private String name;
        private String department;
        private String[] capabilities;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder department(String department) {
            this.department = department;
            return this;
        }

        public Builder capabilities(String... caps) {
            this.capabilities = caps;
            return this;
        }

        public Agent build() {
            if (id == null) {
                id = java.util.UUID.randomUUID().toString();
            }
            if (name == null) {
                name = "agent-" + id;
            }
            return new Agent(id, name, department, capabilities);
        }
    }

    @Override
    public String toString() {
        return "Agent{" + id + ':' + name + '}';
    }

    @Override
    public String getVersion() {
        return "1.0";
    }

    @Override
    public Set<String> getSupportedEventTypes() {
        return Collections.emptySet();
    }

    @Override
    public Set<String> getOutputEventTypes() {
        return Collections.emptySet();
    }

    @Override
    public List<Event> handle(Event event, AgentExecutionContext context) {
        return Collections.emptyList();
    }

    @Override
    public AgentResultProto execute(AgentInputProto input) {
        return null; // Not supported by lightweight framework agent
    }

    @Override
    public boolean isHealthy() {
        return true;
    }

    @Override
    public AgentMetrics getMetrics() {
        return null;
    }

    @Override
    public Role getRole() {
        return null; // Lightweight framework agent has no role
    }

    @Override
    public Authority getAuthority() {
        return null; // Lightweight framework agent has no authority
    }

    @Override
    public EscalationPath getEscalationPath() {
        return null; // Lightweight framework agent has no escalation path
    }
}
