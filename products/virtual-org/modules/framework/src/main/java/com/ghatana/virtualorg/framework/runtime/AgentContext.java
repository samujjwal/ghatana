package com.ghatana.virtualorg.framework.runtime;

import com.ghatana.virtualorg.framework.config.PersonaConfig;
import com.ghatana.virtualorg.framework.hierarchy.Role;
import com.ghatana.virtualorg.framework.memory.OrganizationalMemory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Runtime context for agent execution, providing access to memory, tools, and
 * configuration.
 *
 * <p>
 * <b>Purpose</b><br>
 * Encapsulates all contextual information an agent needs during execution: -
 * Agent identity and role - Memory systems (working, episodic, semantic) -
 * Available tools - Current task context - Organization context
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * AgentContext context = AgentContext.builder()
 *     .agentId("eng-001")
 *     .role(Role.of("Senior Engineer", Layer.INDIVIDUAL_CONTRIBUTOR))
 *     .persona(personaConfig)
 *     .memory(organizationalMemory)
 *     .tools(List.of("github_create_pr", "code_search"))
 *     .build();
 *
 * // Access context during execution
 * String agentId = context.getAgentId();
 * context.setWorkingMemory("current_pr", prData);
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Agent runtime context container
 * @doc.layer product
 * @doc.pattern Context Object
 */
public final class AgentContext {

    private final String agentId;
    private final String agentName;
    private final Role role;
    private final PersonaConfig persona;
    private final OrganizationalMemory memory;
    private final List<String> availableTools;
    private final String organizationId;
    private final String departmentId;
    private final Map<String, Object> workingMemory;
    private final Map<String, Object> metadata;

    private AgentContext(Builder builder) {
        this.agentId = Objects.requireNonNull(builder.agentId, "agentId required");
        this.agentName = builder.agentName != null ? builder.agentName : builder.agentId;
        this.role = builder.role;
        this.persona = builder.persona;
        this.memory = builder.memory;
        this.availableTools = builder.availableTools != null ? List.copyOf(builder.availableTools) : List.of();
        this.organizationId = builder.organizationId;
        this.departmentId = builder.departmentId;
        this.workingMemory = new HashMap<>();
        this.metadata = builder.metadata != null ? new HashMap<>(builder.metadata) : new HashMap<>();
    }

    // ========== Identity ==========
    public String getAgentId() {
        return agentId;
    }

    public String getAgentName() {
        return agentName;
    }

    public Role getRole() {
        return role;
    }

    public PersonaConfig getPersona() {
        return persona;
    }

    public String getOrganizationId() {
        return organizationId;
    }

    public String getDepartmentId() {
        return departmentId;
    }

    // ========== Memory ==========
    public OrganizationalMemory getMemory() {
        return memory;
    }

    /**
     * Sets a value in working memory (scratch space for current task).
     *
     * @param key The key
     * @param value The value
     */
    public void setWorkingMemory(String key, Object value) {
        workingMemory.put(key, value);
    }

    /**
     * Gets a value from working memory.
     *
     * @param key The key
     * @param type The expected type
     * @param <T> Type parameter
     * @return Optional containing the value if present and correct type
     */
    @SuppressWarnings("unchecked")
    public <T> Optional<T> getWorkingMemory(String key, Class<T> type) {
        Object value = workingMemory.get(key);
        if (value != null && type.isInstance(value)) {
            return Optional.of((T) value);
        }
        return Optional.empty();
    }

    /**
     * Clears all working memory.
     */
    public void clearWorkingMemory() {
        workingMemory.clear();
    }

    /**
     * Gets a snapshot of current working memory.
     *
     * @return Immutable copy of working memory
     */
    public Map<String, Object> getWorkingMemorySnapshot() {
        return Map.copyOf(workingMemory);
    }

    // ========== Tools ==========
    public List<String> getAvailableTools() {
        return availableTools;
    }

    /**
     * Checks if a tool is available to this agent.
     *
     * @param toolName The tool name
     * @return true if the tool is available
     */
    public boolean hasToolAccess(String toolName) {
        return availableTools.contains(toolName);
    }

    // ========== Metadata ==========
    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(String key, Object value) {
        metadata.put(key, value);
    }

    @SuppressWarnings("unchecked")
    public <T> Optional<T> getMetadata(String key, Class<T> type) {
        Object value = metadata.get(key);
        if (value != null && type.isInstance(value)) {
            return Optional.of((T) value);
        }
        return Optional.empty();
    }

    // ========== Builder ==========
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private String agentId;
        private String agentName;
        private Role role;
        private PersonaConfig persona;
        private OrganizationalMemory memory;
        private List<String> availableTools;
        private String organizationId;
        private String departmentId;
        private Map<String, Object> metadata;

        private Builder() {
        }

        public Builder agentId(String agentId) {
            this.agentId = agentId;
            return this;
        }

        public Builder agentName(String agentName) {
            this.agentName = agentName;
            return this;
        }

        public Builder role(Role role) {
            this.role = role;
            return this;
        }

        public Builder persona(PersonaConfig persona) {
            this.persona = persona;
            return this;
        }

        public Builder memory(OrganizationalMemory memory) {
            this.memory = memory;
            return this;
        }

        public Builder availableTools(List<String> tools) {
            this.availableTools = tools;
            return this;
        }

        public Builder organizationId(String organizationId) {
            this.organizationId = organizationId;
            return this;
        }

        public Builder departmentId(String departmentId) {
            this.departmentId = departmentId;
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        public AgentContext build() {
            return new AgentContext(this);
        }
    }
}
