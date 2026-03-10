/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Domain entity representing a registered agent in the persistent registry.
 *
 * <p>Replaces the in-memory agent registry with a durable, queryable record.
 * Scoped to a tenant to support multi-tenant deployments.
 *
 * @doc.type class
 * @doc.purpose Persistent agent registry entry
 * @doc.layer domain
 * @doc.pattern Entity
 */
public class AgentRegistryEntry {

    public enum AgentStatus { ACTIVE, INACTIVE, SUSPENDED, TERMINATED }
    public enum HealthStatus { HEALTHY, DEGRADED, UNHEALTHY, UNKNOWN }

    private String id;
    private String name;
    private String version;
    private String agentType;
    private AgentStatus status;
    private List<String> capabilities;
    private Map<String, Object> config;
    private Map<String, Object> metadata;
    private HealthStatus healthStatus;
    private Instant lastHeartbeat;
    private String tenantId;
    private Instant createdAt;
    private Instant updatedAt;

    public AgentRegistryEntry() {}

    // Getters
    public String getId()                        { return id; }
    public String getName()                      { return name; }
    public String getVersion()                   { return version; }
    public String getAgentType()                 { return agentType; }
    public AgentStatus getStatus()               { return status; }
    public List<String> getCapabilities()        { return capabilities; }
    public Map<String, Object> getConfig()       { return config; }
    public Map<String, Object> getMetadata()     { return metadata; }
    public HealthStatus getHealthStatus()        { return healthStatus; }
    public Instant getLastHeartbeat()            { return lastHeartbeat; }
    public String getTenantId()                  { return tenantId; }
    public Instant getCreatedAt()                { return createdAt; }
    public Instant getUpdatedAt()                { return updatedAt; }

    // Setters
    public void setId(String id)                              { this.id = id; }
    public void setName(String name)                          { this.name = name; }
    public void setVersion(String version)                    { this.version = version; }
    public void setAgentType(String agentType)                { this.agentType = agentType; }
    public void setStatus(AgentStatus status)                 { this.status = status; }
    public void setCapabilities(List<String> capabilities)    { this.capabilities = capabilities; }
    public void setConfig(Map<String, Object> config)         { this.config = config; }
    public void setMetadata(Map<String, Object> metadata)     { this.metadata = metadata; }
    public void setHealthStatus(HealthStatus healthStatus)    { this.healthStatus = healthStatus; }
    public void setLastHeartbeat(Instant lastHeartbeat)       { this.lastHeartbeat = lastHeartbeat; }
    public void setTenantId(String tenantId)                  { this.tenantId = tenantId; }
    public void setCreatedAt(Instant createdAt)               { this.createdAt = createdAt; }
    public void setUpdatedAt(Instant updatedAt)               { this.updatedAt = updatedAt; }
}
