/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */
package com.ghatana.agent.registry.domain;

import java.time.Instant;
import java.util.Map;
import java.util.List;

/**
 * Represents information about an agent in the registry.
 */
public class AgentInfo {
    private String agentId;
    private String name;
    private String description;
    private String version;
    private String type;
    private Map<String, Object> capabilities;
    private List<String> supportedEventTypes;
    private String status;
    private Map<String, String> metadata;
    private Instant createdAt;
    private Instant updatedAt;

    public AgentInfo() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
        this.status = "ACTIVE";
    }

    public AgentInfo(String agentId, String name, String type) {
        this();
        this.agentId = agentId;
        this.name = name;
        this.type = type;
    }

    public AgentInfo(String agentId, String name, String type, String version,
                     String status, String description, String endpoint) {
        this(agentId, name, type);
        this.version = version;
        this.status = status;
        this.description = description;
        if (this.metadata == null) {
            this.metadata = new java.util.HashMap<>();
        }
        if (endpoint != null) {
            this.metadata.put("endpoint", endpoint);
        }
    }

    /**
     * Add a capability with its attributes.
     */
    public void addCapability(String key, Map<String, Object> attributes) {
        if (this.capabilities == null) {
            this.capabilities = new java.util.HashMap<>();
        }
        this.capabilities.put(key, attributes);
        this.updatedAt = Instant.now();
    }

    // Getters and setters
    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(String agentId) {
        this.agentId = agentId;
        this.updatedAt = Instant.now();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
        this.updatedAt = Instant.now();
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
        this.updatedAt = Instant.now();
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
        this.updatedAt = Instant.now();
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
        this.updatedAt = Instant.now();
    }

    public Map<String, Object> getCapabilities() {
        return capabilities;
    }

    public void setCapabilities(Map<String, Object> capabilities) {
        this.capabilities = capabilities;
        this.updatedAt = Instant.now();
    }

    public List<String> getSupportedEventTypes() {
        return supportedEventTypes;
    }

    public void setSupportedEventTypes(List<String> supportedEventTypes) {
        this.supportedEventTypes = supportedEventTypes;
        this.updatedAt = Instant.now();
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
        this.updatedAt = Instant.now();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AgentInfo agentInfo = (AgentInfo) o;
        return agentId != null ? agentId.equals(agentInfo.agentId) : agentInfo.agentId == null;
    }

    @Override
    public int hashCode() {
        return agentId != null ? agentId.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "AgentInfo{" +
                "agentId='" + agentId + '\'' +
                ", name='" + name + '\'' +
                ", type='" + type + '\'' +
                ", status='" + status + '\'' +
                '}';
    }
}
