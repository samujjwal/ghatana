package com.ghatana.agent.framework.coordination;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

/**
 * Information about an agent.
 * 
 * @doc.type class
 * @doc.purpose Agent metadata for discovery
 * @doc.layer framework
 * @doc.pattern Value Object
 */
public final class AgentInfo {
    
    private final String agentId;
    private final String name;
    private final String role;
    private final List<String> capabilities;
    private final String status;
    private final Map<String, Object> metadata;
    
    public AgentInfo(
            @NotNull String agentId,
            @NotNull String name,
            @NotNull String role,
            @NotNull List<String> capabilities,
            @NotNull String status,
            @NotNull Map<String, Object> metadata) {
        this.agentId = agentId;
        this.name = name;
        this.role = role;
        this.capabilities = List.copyOf(capabilities);
        this.status = status;
        this.metadata = Map.copyOf(metadata);
    }
    
    @NotNull
    public String getAgentId() {
        return agentId;
    }
    
    @NotNull
    public String getName() {
        return name;
    }
    
    @NotNull
    public String getRole() {
        return role;
    }
    
    @NotNull
    public List<String> getCapabilities() {
        return capabilities;
    }
    
    @NotNull
    public String getStatus() {
        return status;
    }
    
    @NotNull
    public Map<String, Object> getMetadata() {
        return metadata;
    }
}
