# Persistent Agent Registry Implementation

**Date:** 2026-03-07  
**Status:** 🎯 DESIGN COMPLETE  
**Timeline:** 2-3 weeks

---

## 🎯 Overview

Implement a persistent, scalable agent registry to replace the in-memory implementation, enabling agent state persistence across restarts, distributed deployments, and disaster recovery.

---

## 📐 Architecture

```
┌─────────────────────────────────────────────────────────┐
│           Persistent Agent Registry                      │
├─────────────────────────────────────────────────────────┤
│                                                          │
│  ┌──────────────┐         ┌──────────────┐             │
│  │   Registry   │────────▶│  PostgreSQL  │             │
│  │   Service    │         │   (Primary)  │             │
│  └──────┬───────┘         └──────────────┘             │
│         │                                                │
│         │                 ┌──────────────┐             │
│         └────────────────▶│    Redis     │             │
│                           │   (Cache)    │             │
│                           └──────────────┘             │
│                                                          │
└─────────────────────────────────────────────────────────┘
```

---

## 🗄️ Database Schema

### PostgreSQL Schema

```sql
-- Agent registry table
CREATE TABLE agent_registry (
    id VARCHAR(255) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    version VARCHAR(50) NOT NULL,
    type VARCHAR(100) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'INACTIVE',
    capabilities JSONB NOT NULL DEFAULT '[]'::jsonb,
    config JSONB NOT NULL DEFAULT '{}'::jsonb,
    metadata JSONB DEFAULT '{}'::jsonb,
    health_status VARCHAR(50) DEFAULT 'UNKNOWN',
    last_heartbeat TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    tenant_id VARCHAR(255) NOT NULL,
    CONSTRAINT unique_agent_version UNIQUE (id, version, tenant_id)
);

-- Indexes for performance
CREATE INDEX idx_agent_status ON agent_registry(status);
CREATE INDEX idx_agent_type ON agent_registry(type);
CREATE INDEX idx_agent_tenant ON agent_registry(tenant_id);
CREATE INDEX idx_agent_capabilities ON agent_registry USING GIN(capabilities);
CREATE INDEX idx_agent_health ON agent_registry(health_status);
CREATE INDEX idx_agent_heartbeat ON agent_registry(last_heartbeat DESC);

-- Agent execution history
CREATE TABLE agent_execution_history (
    id BIGSERIAL PRIMARY KEY,
    agent_id VARCHAR(255) NOT NULL,
    execution_id VARCHAR(255) NOT NULL UNIQUE,
    tenant_id VARCHAR(255) NOT NULL,
    started_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP,
    status VARCHAR(50) NOT NULL,
    input JSONB,
    output JSONB,
    error TEXT,
    duration_ms BIGINT,
    metadata JSONB,
    FOREIGN KEY (agent_id) REFERENCES agent_registry(id) ON DELETE CASCADE
);

CREATE INDEX idx_execution_agent ON agent_execution_history(agent_id);
CREATE INDEX idx_execution_tenant ON agent_execution_history(tenant_id);
CREATE INDEX idx_execution_status ON agent_execution_history(status);
CREATE INDEX idx_execution_started ON agent_execution_history(started_at DESC);

-- Agent metrics
CREATE TABLE agent_metrics (
    id BIGSERIAL PRIMARY KEY,
    agent_id VARCHAR(255) NOT NULL,
    tenant_id VARCHAR(255) NOT NULL,
    metric_name VARCHAR(100) NOT NULL,
    metric_value DOUBLE PRECISION NOT NULL,
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    tags JSONB DEFAULT '{}'::jsonb,
    FOREIGN KEY (agent_id) REFERENCES agent_registry(id) ON DELETE CASCADE
);

CREATE INDEX idx_metrics_agent ON agent_metrics(agent_id);
CREATE INDEX idx_metrics_tenant ON agent_metrics(tenant_id);
CREATE INDEX idx_metrics_name ON agent_metrics(metric_name);
CREATE INDEX idx_metrics_timestamp ON agent_metrics(timestamp DESC);

-- Agent dependencies
CREATE TABLE agent_dependencies (
    agent_id VARCHAR(255) NOT NULL,
    depends_on_agent_id VARCHAR(255) NOT NULL,
    dependency_type VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (agent_id, depends_on_agent_id),
    FOREIGN KEY (agent_id) REFERENCES agent_registry(id) ON DELETE CASCADE,
    FOREIGN KEY (depends_on_agent_id) REFERENCES agent_registry(id) ON DELETE CASCADE
);

CREATE INDEX idx_dependency_agent ON agent_dependencies(agent_id);
CREATE INDEX idx_dependency_depends_on ON agent_dependencies(depends_on_agent_id);

-- Update trigger for updated_at
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_agent_registry_updated_at
    BEFORE UPDATE ON agent_registry
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();
```

---

## 💻 Implementation

### 1. Agent Registry Entity

```java
package com.ghatana.agent.registry.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Map;
import java.util.List;

@Entity
@Table(name = "agent_registry")
public class AgentRegistryEntity {
    
    @Id
    private String id;
    
    @Column(nullable = false)
    private String name;
    
    @Column(nullable = false)
    private String version;
    
    @Column(nullable = false)
    private String type;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AgentStatus status = AgentStatus.INACTIVE;
    
    @Column(columnDefinition = "jsonb")
    private List<String> capabilities;
    
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> config;
    
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> metadata;
    
    @Enumerated(EnumType.STRING)
    private HealthStatus healthStatus = HealthStatus.UNKNOWN;
    
    private Instant lastHeartbeat;
    
    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
    
    @Column(nullable = false)
    private Instant updatedAt = Instant.now();
    
    private String createdBy;
    private String updatedBy;
    
    @Column(nullable = false)
    private String tenantId;
    
    // Getters and setters
}

enum AgentStatus {
    ACTIVE, INACTIVE, SUSPENDED, TERMINATED
}

enum HealthStatus {
    HEALTHY, DEGRADED, UNHEALTHY, UNKNOWN
}
```

### 2. Repository Interface

```java
package com.ghatana.agent.registry.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AgentRegistryRepository extends JpaRepository<AgentRegistryEntity, String> {
    
    List<AgentRegistryEntity> findByTenantId(String tenantId);
    
    List<AgentRegistryEntity> findByTenantIdAndStatus(String tenantId, AgentStatus status);
    
    List<AgentRegistryEntity> findByTenantIdAndType(String tenantId, String type);
    
    @Query("SELECT a FROM AgentRegistryEntity a WHERE a.tenantId = ?1 AND " +
           "jsonb_contains(a.capabilities, ?2) = true")
    List<AgentRegistryEntity> findByTenantIdAndCapability(String tenantId, String capability);
    
    @Query("SELECT a FROM AgentRegistryEntity a WHERE a.tenantId = ?1 AND " +
           "a.lastHeartbeat < ?2")
    List<AgentRegistryEntity> findStaleAgents(String tenantId, Instant threshold);
    
    Optional<AgentRegistryEntity> findByIdAndTenantId(String id, String tenantId);
}
```

### 3. Persistent Agent Registry Service

```java
package com.ghatana.agent.registry;

import com.ghatana.agent.registry.persistence.*;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PersistentAgentRegistry implements AgentRegistry {
    
    private final AgentRegistryRepository repository;
    private final AgentExecutionHistoryRepository historyRepository;
    private final AgentMetricsRepository metricsRepository;
    
    public PersistentAgentRegistry(
            AgentRegistryRepository repository,
            AgentExecutionHistoryRepository historyRepository,
            AgentMetricsRepository metricsRepository) {
        this.repository = repository;
        this.historyRepository = historyRepository;
        this.metricsRepository = metricsRepository;
    }
    
    @Override
    @Transactional
    @CacheEvict(value = "agents", key = "#agent.id + ':' + #tenantId")
    public void register(Agent agent, String tenantId) {
        AgentRegistryEntity entity = new AgentRegistryEntity();
        entity.setId(agent.getId());
        entity.setName(agent.getName());
        entity.setVersion(agent.getVersion());
        entity.setType(agent.getClass().getSimpleName());
        entity.setStatus(AgentStatus.ACTIVE);
        entity.setCapabilities(agent.getCapabilities());
        entity.setConfig(agent.getConfig());
        entity.setTenantId(tenantId);
        entity.setHealthStatus(HealthStatus.HEALTHY);
        entity.setLastHeartbeat(Instant.now());
        
        repository.save(entity);
    }
    
    @Override
    @Cacheable(value = "agents", key = "#agentId + ':' + #tenantId")
    public Optional<Agent> getAgent(String agentId, String tenantId) {
        return repository.findByIdAndTenantId(agentId, tenantId)
                .map(this::toAgent);
    }
    
    @Override
    public List<Agent> getAgentsByCapability(String capability, String tenantId) {
        return repository.findByTenantIdAndCapability(tenantId, capability)
                .stream()
                .map(this::toAgent)
                .toList();
    }
    
    @Override
    @Transactional
    public void recordExecution(String agentId, String executionId, 
                               AgentExecutionResult result, String tenantId) {
        AgentExecutionHistoryEntity history = new AgentExecutionHistoryEntity();
        history.setAgentId(agentId);
        history.setExecutionId(executionId);
        history.setTenantId(tenantId);
        history.setStartedAt(result.getStartTime());
        history.setCompletedAt(result.getEndTime());
        history.setStatus(result.getStatus().name());
        history.setInput(result.getInput());
        history.setOutput(result.getOutput());
        history.setError(result.getError());
        history.setDurationMs(result.getDurationMs());
        
        historyRepository.save(history);
    }
    
    @Override
    @Transactional
    public void updateHeartbeat(String agentId, String tenantId) {
        repository.findByIdAndTenantId(agentId, tenantId)
                .ifPresent(entity -> {
                    entity.setLastHeartbeat(Instant.now());
                    entity.setHealthStatus(HealthStatus.HEALTHY);
                    repository.save(entity);
                });
    }
    
    @Override
    @Transactional
    public void recordMetric(String agentId, String metricName, 
                            double value, String tenantId) {
        AgentMetricsEntity metric = new AgentMetricsEntity();
        metric.setAgentId(agentId);
        metric.setTenantId(tenantId);
        metric.setMetricName(metricName);
        metric.setMetricValue(value);
        metric.setTimestamp(Instant.now());
        
        metricsRepository.save(metric);
    }
    
    private Agent toAgent(AgentRegistryEntity entity) {
        // Convert entity to Agent instance
        // This would use reflection or a factory pattern
        return null; // Implementation details
    }
}
```

### 4. Redis Caching Configuration

```java
package com.ghatana.agent.registry.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import java.time.Duration;

@Configuration
@EnableCaching
public class CacheConfiguration {
    
    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10))
                .disableCachingNullValues();
        
        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(config)
                .build();
    }
}
```

---

## 🔄 Migration Strategy

### Phase 1: Parallel Running (Week 1)
1. Deploy persistent registry alongside in-memory
2. Write to both registries
3. Read from in-memory (existing behavior)
4. Monitor for discrepancies

### Phase 2: Gradual Cutover (Week 2)
1. Start reading from persistent registry
2. Keep writing to both
3. Monitor performance and errors
4. Rollback capability maintained

### Phase 3: Full Migration (Week 3)
1. Remove in-memory registry
2. Clean up dual-write code
3. Optimize queries
4. Final testing

---

## 📊 Performance Targets

| Operation | Target | Notes |
|-----------|--------|-------|
| Register Agent | <50ms | With caching |
| Get Agent | <10ms | Cache hit |
| Get Agent | <100ms | Cache miss |
| Query by Capability | <200ms | Indexed |
| Record Execution | <50ms | Async |
| Update Heartbeat | <20ms | Batch updates |

---

## ✅ Success Criteria

- [ ] All agents persist across restarts
- [ ] Query performance meets targets
- [ ] Cache hit rate >80%
- [ ] Zero data loss
- [ ] Supports distributed deployments
- [ ] Disaster recovery tested

---

**Status:** Ready for Implementation  
**Next Step:** Create database schema and entity classes
