# Agentic Framework Hardening - Consolidated Plan

> **Date**: March 3, 2026  
> **Scope**: Complete agentic framework hardening with clean architecture (NO backward compatibility)  
> **Repository**: /Users/samujjwal/Development/ghatana-new  
> **Architecture Principles**: Clean separation - AEP handles events, Data-Cloud handles data, Agent-Framework handles agents

---

## Executive Summary

This plan establishes a **clean, unified architecture** for the Ghatana agentic framework with **no backward compatibility constraints**. The architecture enforces clear boundaries:

| Domain | Primary Module | Responsibility |
|--------|---------------|----------------|
| **Event Processing** | `products/aep` | All event ingestion, streaming, pattern detection, backpressure |
| **Data Management** | `products/data-cloud` | All persistence, storage, querying, consistency |
| **Agent Framework** | `platform/java/agent-framework` | Agent lifecycle, orchestration, resilience |
| **Agent Memory** | `platform/java/agent-memory` | Memory consolidation, retention (uses Data-Cloud for storage) |
| **Agent Registry** | `platform/java/agent-registry` | Agent discovery, catalog (uses Data-Cloud for metadata) |

**Key Principle**: Each functionality uses the NEW approach. Legacy and deprecated code is identified for removal.

---

## Architecture Overview

### Clean Module Dependencies

```
┌─────────────────────────────────────────────────────────────────┐
│                      AGENT ORCHESTRATION LAYER                     │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐   │
│  │  Agent Registry │  │  Agent Memory   │  │Agent Resilience │   │
│  │  (Discovery)    │  │  (Consolidation)│  │  (Circuit Break)│   │
│  └────────┬────────┘  └────────┬────────┘  └────────┬────────┘   │
│           │                    │                    │           │
│           └────────────────────┴────────────────────┘           │
│                           │                                     │
│                    ┌──────┴──────┐                            │
│                    │Agent Engine │                            │
│                    │(Lifecycle)  │                            │
│                    └──────┬──────┘                            │
└───────────────────────────┼─────────────────────────────────────┘
                            │
┌───────────────────────────┼─────────────────────────────────────┐
│                      EVENT LAYER (AEP)                           │
│                           │                                      │
│                    ┌──────┴──────┐                            │
│                    │  AEP Engine │                            │
│                    │ (Operators) │                            │
│                    └──────┬──────┘                            │
│                           │                                      │
│           ┌───────────────┼───────────────┐                   │
│           │               │               │                      │
│      ┌────┴────┐    ┌───┴───┐    ┌────┴────┐               │
│      │Ingestion│    │Stream │    │ Pattern │               │
│      │Operator │    │Operator│    │ Detection│               │
│      └─────────┘    └───────┘    └─────────┘               │
└─────────────────────────────────────────────────────────────────┘
                            │
┌───────────────────────────┼─────────────────────────────────────┐
│                   DATA LAYER (Data-Cloud)                        │
│                           │                                      │
│              ┌────────────┼────────────┐                      │
│              │            │            │                         │
│         ┌────┴───┐  ┌───┴──┐  ┌──────┴────┐                │
│         │ Entity │  │Event │  │   Vector  │                │
│         │ Store  │  │ Store│  │   Store   │                │
│         └────────┘  └──────┘  └───────────┘                │
└─────────────────────────────────────────────────────────────────┘
```

---

## Phase 1: Foundation Cleanup (Week 1)

### 1.1 Remove Legacy Code

**Action**: Identify and mark all legacy/deprecated code for removal.

| Legacy Component | Location | Replacement | Action |
|-----------------|----------|-------------|--------|
| `Agent` (untyped) | `platform/java/domain` | `TypedAgent<I,O>` | **REMOVE** |
| `Memory` (domain) | `platform/java/domain` | `MemoryStore` (agent-framework) | **REMOVE** |
| Legacy operator interfaces | `products/aep/legacy` | `UnifiedOperator` | **REMOVE** |
| Old MemoryStore implementations | `products/virtual-org` | `PersistentMemoryPlane` | **REMOVE** |
| Deprecated event adapters | `platform/java/event-cloud` | Native `EventCloud` | **REMOVE** |

**Files to Delete**:
- `/platform/java/domain/src/main/java/com/ghatana/platform/domain/agent/Agent.java` (legacy untyped)
- `/products/virtual-org/engine/service/src/main/java/com/ghatana/virtualorg/memory/AgentMemory.java`
- Any files marked `@Deprecated` in `agent-framework`

### 1.2 Consolidate Agent Interfaces

**Single Source of Truth**: `platform/java/agent-framework/src/main/java/com/ghatana/agent/TypedAgent.java`

Remove duplicates:
- `platform/java/domain/src/main/java/com/ghatana/platform/domain/agent/Agent.java` → **DELETE**
- `products/virtual-org/engine/service/src/main/java/com/ghatana/virtualorg/agent/*` → Consolidate to `agent-framework`

---

## Phase 2: Agent Framework Hardening (Weeks 2-5)

### 2.1 Agent Resilience Module

**New Module**: `platform/java/agent-resilience`

**Dependencies**:
```kotlin
dependencies {
    api(project(":platform:java:agent-framework"))
    api(project(":platform:java:observability"))
    // Uses Resilience4j for circuit breakers
    implementation("io.github.resilience4j:resilience4j-circuitbreaker:2.2.0")
    implementation("io.github.resilience4j:resilience4j-retry:2.2.0")
    implementation("io.github.resilience4j:resilience4j-bulkhead:2.2.0")
}
```

**Components** (NO duplication with existing):

| Component | Purpose | Reuse From |
|-----------|---------|------------|
| `ResilientTypedAgent<I,O>` | Decorator with circuit breaker | New - wraps TypedAgent |
| `AgentCircuitBreaker` | Per-agent circuit breaker | New - uses Resilience4j |
| `AgentHealthMonitor` | Health aggregation | Extends existing `observability` |
| `AgentRetryPolicy` | Retry with backoff | New - uses Resilience4j |
| `AgentBulkhead` | Concurrency limiting | New - uses Resilience4j |

**Integration with AEP**: AEP operators that wrap agents use `ResilientTypedAgent` decorator.

### 2.2 Agent Checkpointing

**Location**: `platform/java/agent-framework/src/main/java/com/ghatana/agent/framework/checkpoint/`

**Components**:
- `AgentCheckpointStore` - SPI for state persistence
- `CheckpointedTypedAgent` - Base class with automatic checkpointing
- `CheckpointPolicy` - Configurable checkpoint frequency

**Data Storage**: Uses **Data-Cloud** for checkpoint persistence (NOT new database module):
```java
// Checkpoint storage via Data-Cloud
public class DataCloudCheckpointStore implements AgentCheckpointStore {
    private final DataCloudClient dataCloud;
    private final String checkpointCollection = "agent-checkpoints";
    
    @Override
    public Promise<Void> saveCheckpoint(String agentId, AgentState state) {
        CheckpointRecord record = CheckpointRecord.builder()
            .agentId(agentId)
            .state(state)
            .timestamp(Instant.now())
            .build();
            
        return dataCloud.save(checkpointCollection, record.toMap());
    }
}
```

### 2.3 Agent Memory Integration

**Location**: `platform/java/agent-memory/`

**Uses Data-Cloud for ALL storage**:
- Episodes stored in Data-Cloud `EventRecord`
- Facts stored in Data-Cloud `EntityRecord` with vector embeddings
- Policies stored in Data-Cloud `DocumentRecord`
- Task states stored in Data-Cloud `EntityRecord`

**NO duplicate storage implementations** - single Data-Cloud backend.

---

## Phase 3: AEP Event Processing Hardening (Weeks 6-9)

### 3.1 Backpressure in AEP Operators

**Location**: `products/aep/platform/src/main/java/com/ghatana/aep/operator/`

AEP already has operator framework - extend it:

| Component | Purpose | Location |
|-----------|---------|----------|
| `BackpressureOperator` | Base operator with flow control | `com.ghatana.aep.operator.backpressure` |
| `BufferedOperator` | Operator with bounded buffer | `com.ghatana.aep.operator.buffered` |
| `RateLimitingOperator` | Rate limiting decorator | `com.ghatana.aep.operator.ratelimit` |
| `FlowControlMetrics` | Buffer metrics | `com.ghatana.aep.metrics` |

**Integration**: 
- Uses `EventCloud.subscribe()` with backpressure-aware consumer
- Buffer sizes configurable per operator
- Metrics exposed via existing `observability` module

**NO new `event-reliability` module** - all reliability features go into AEP operators.

### 3.2 Event Processing Reliability

**Within AEP Module**:

| Feature | Implementation | Reuse |
|---------|---------------|-------|
| At-least-once processing | AEP operator checkpointing | Data-Cloud storage |
| Event deduplication | AEP `DeduplicationOperator` | Data-Cloud for idempotency keys |
| Dead letter queue | AEP `DeadLetterOperator` | Data-Cloud collection |
| Processing metrics | AEP `OperatorMetrics` | Existing `observability` |

### 3.3 Agent-as-Operator Integration

**New AEP Operator**: `AgentOperator` that wraps `TypedAgent`

```java
// AEP operator that executes agents
public class AgentOperator<I, O> extends AbstractStreamOperator {
    private final TypedAgent<I, O> agent;
    private final ResilientTypedAgent<I, O> resilientAgent;
    
    @Override
    public Promise<OperatorResult> process(Event event) {
        // Convert event to agent input
        I input = eventTransformer.transform(event);
        
        // Execute agent with resilience patterns
        return resilientAgent.process(context, input)
            .map(result -> OperatorResult.success(transformOutput(result)))
            .catchException(ex -> OperatorResult.failure(ex));
    }
}
```

---

## Phase 4: Data-Cloud Integration (Weeks 10-13)

### 4.1 Agent Memory Storage

**Agent Memory uses Data-Cloud exclusively**:

| Memory Tier | Data-Cloud Record Type | Collection |
|-------------|----------------------|------------|
| Episodes | `EventRecord` | `agent-episodes` |
| Facts | `EntityRecord` + vector embeddings | `agent-facts` |
| Procedures | `DocumentRecord` | `agent-procedures` |
| Task States | `EntityRecord` | `agent-task-states` |
| Working Memory | In-memory only (bounded) | N/A |

**NO separate persistence layer** - all memory stored via Data-Cloud SPI.

### 4.2 Agent Registry Storage

**Agent Registry uses Data-Cloud**:

```java
public class DataCloudAgentRegistry implements AgentRegistry {
    private final DataCloudClient dataCloud;
    private final String registryCollection = "agent-registry";
    private final String capabilityIndex = "agent-capabilities";
    
    @Override
    public Promise<AgentRegistrationResult> register(String tenantId, AgentDescriptor descriptor) {
        // Store in Data-Cloud
        return dataCloud.save(registryCollection, descriptor.toMap())
            .thenCompose(id -> indexCapabilities(tenantId, descriptor));
    }
}
```

| Registry Data | Data-Cloud Component |
|-------------|---------------------|
| Agent descriptors | `EntityRecord` |
| Capability index | `GraphRecord` (relationships) |
| Search index | Data-Cloud query engine |
| Metadata cache | In-memory with Data-Cloud backing |

**NO separate PostgreSQL/Redis** - uses Data-Cloud's existing storage.

### 4.3 Data Consistency

**Handled by Data-Cloud**:
- All agent state changes use Data-Cloud transactions
- Consistency guarantees provided by Data-Cloud layer
- NO separate `TransactionalDatabaseClient` - use Data-Cloud's native transactions

---

## Phase 5: Agent Repository & Catalog (Weeks 14-17)

### 5.1 Pluggable Catalog Architecture

**Design Principle**: Catalog infrastructure in platform, agent definitions distributed across products.

```
┌────────────────────────────────────────────────────────────────┐
│              PLATFORM: Catalog Infrastructure                     │
│  platform/java/agent-framework/src/main/java/.../catalog/        │
│  ├── AgentCatalog.java              # SPI for catalog providers   │
│  ├── CatalogLoader.java             # Discovers product catalogs  │
│  ├── CatalogRegistry.java           # Aggregates all catalogs   │
│  └── AgentDescriptor.java           # Shared descriptor model   │
└────────────────────────────────────────────────────────────────┘
                              │
            ┌─────────────────┼─────────────────┐
            │                 │                 │
            ▼                 ▼                 ▼
┌──────────────────┐ ┌──────────────────┐ ┌──────────────────┐
│   Platform Catalog │ │   AEP Catalog    │ │ Data-Cloud Cat.  │
│   platform/        │ │   products/aep/  │ │   products/data/   │
│   /agent-catalog/  │ │   /agent-catalog/│ │   /agent-catalog/  │
├──────────────────┤ ├──────────────────┤ ├──────────────────┤
│ • Core agents      │ │ • Ingestion ops  │ │ • Storage agents   │
│ • Base templates   │ │ • Pattern ops    │ │ • Query agents     │
│ • Shared libs      │ │ • Routing ops    │ │ • Migration ops  │
└──────────────────┘ └──────────────────┘ └──────────────────┘
```

### 5.2 Framework Infrastructure

**Location**: `platform/java/agent-framework/src/main/java/com/ghatana/agent/catalog/`

#### Core SPI

```java
package com.ghatana.agent.catalog;

/**
 * SPI for pluggable agent catalogs.
 * Each product/module implements this to expose their agents.
 */
public interface AgentCatalog {
    
    /** Unique catalog identifier (e.g., "aep", "data-cloud", "finance") */
    String getCatalogId();
    
    /** All agents in this catalog */
    Promise<List<AgentDescriptor>> listAgents();
    
    /** Find agents by capability */
    Promise<List<AgentDescriptor>> findByCapability(Capability capability);
    
    /** Get specific agent */
    Promise<Optional<AgentDescriptor>> getAgent(String agentId);
    
    /** Get agent implementation class */
    Promise<Class<? extends TypedAgent<?, ?>>> getAgentClass(String agentId);
    
    /** Catalog metadata */
    CatalogMetadata getMetadata();
}

/**
 * Registry that aggregates all discovered catalogs.
 */
public class CatalogRegistry {
    
    private final Map<String, AgentCatalog> catalogs = new ConcurrentHashMap<>();
    
    /** Discovers catalogs via ServiceLoader */
    public Promise<Void> discoverCatalogs() {
        ServiceLoader<AgentCatalog> loader = ServiceLoader.load(AgentCatalog.class);
        loader.forEach(catalog -> catalogs.put(catalog.getCatalogId(), catalog));
        return Promise.complete();
    }
    
    /** Search across ALL catalogs */
    public Promise<List<AgentMatch>> searchAll(AgentSearchQuery query) {
        List<Promise<List<AgentDescriptor>>> searches = catalogs.values().stream()
            .map(catalog -> catalog.listAgents())
            .collect(Collectors.toList());
            
        return Promises.toList(searches)
            .map(lists -> lists.stream()
                .flatMap(List::stream)
                .filter(agent -> matchesQuery(agent, query))
                .map(agent -> new AgentMatch(agent, findSourceCatalog(agent)))
                .collect(Collectors.toList()));
    }
}
```

#### Catalog Discovery

```java
package com.ghatana.agent.catalog.loader;

/**
 * Loads agent definitions from catalog.yaml files across the codebase.
 */
public class CatalogLoader {
    
    private static final String CATALOG_FILE = "agent-catalog.yaml";
    
    /**
     * Scans all products for agent-catalog.yaml files.
     */
    public Promise<List<DiscoveredCatalog>> discoverCatalogs(Path repositoryRoot) {
        return fileScanner.findFiles(repositoryRoot, "**/" + CATALOG_FILE)
            .map(paths -> paths.stream()
                .map(this::loadCatalog)
                .collect(Collectors.toList()));
    }
    
    /**
     * Creates in-memory catalog from catalog.yaml definition.
     */
    public AgentCatalog loadCatalog(Path catalogYamlPath) {
        CatalogDefinition definition = yamlMapper.readValue(catalogYamlPath);
        
        return AgentCatalog.builder()
            .catalogId(definition.getId())
            .agents(loadAgents(definition.getAgentPaths()))
            .metadata(definition.getMetadata())
            .build();
    }
}
```

### 5.3 Platform Catalog (platform/agent-catalog/)

**Purpose**: Core agents and templates that all products can use.

```
platform/agent-catalog/
├── agent-catalog.yaml               # Catalog definition
├── catalog-schema.yaml              # Schema for agent descriptors
├── core-agents/                     # Platform agents
│   ├── data-processing/
│   │   ├── schema-validator/
│   │   │   ├── agent.yaml           # Descriptor
│   │   │   ├── SchemaValidator.java # Implementation
│   │   │   └── tests/
│   │   └── data-quality-checker/
│   ├── event-processing/
│   │   ├── event-transformer/
│   │   └── event-filter/
│   ├── monitoring/
│   └── security/
└── templates/                       # Base templates
    ├── deterministic-agent/
    ├── adaptive-agent/
    └── composite-agent/
```

**platform/agent-catalog/agent-catalog.yaml**:
```yaml
catalog:
  id: "platform"
  name: "Ghatana Platform Core Catalog"
  version: "1.0.0"
  
  # Paths relative to this file
  agents:
    - "core-agents/**/agent.yaml"
    
  # Templates provided
  templates:
    - "templates/**/"
    
  # Capabilities this catalog provides
  capabilities:
    taxonomy: "capabilities/taxonomy.yaml"
    definitions: "capabilities/standard-capabilities.yaml"
    
  metadata:
    maintainer: "platform-team@ghatana.ai"
    scope: "platform-wide"
    stability: "stable"
```

### 5.4 Product-Specific Catalogs

Each product can define its own `agent-catalog.yaml`:

**Example: AEP Catalog**
```
products/aep/agent-catalog/
├── agent-catalog.yaml
├── operators/                       # AEP-specific agents
│   ├── ingestion/
│   │   ├── kafka-ingestion-agent/
│   │   ├── http-ingestion-agent/
│   │   └── file-ingestion-agent/
│   ├── pattern-detection/
│   │   ├── correlation-agent/
│   │   └── anomaly-agent/
│   └── routing/
│       ├── content-based-router/
│       └── load-balancer/
└── templates/
    └── aep-operator-template/
```

**products/aep/agent-catalog/agent-catalog.yaml**:
```yaml
catalog:
  id: "aep"
  name: "Agentic Event Processing Catalog"
  version: "1.0.0"
  
  agents:
    - "operators/**/agent.yaml"
    
  # Extends platform capabilities
  extends: "platform"
  
  # AEP-specific capabilities
  capabilities:
    additional: "capabilities/aep-capabilities.yaml"
    
  metadata:
    maintainer: "aep-team@ghatana.ai"
    scope: "event-processing"
    dependencies: ["platform"]
```

**Example: Data-Cloud Catalog**
```
products/data-cloud/agent-catalog/
├── agent-catalog.yaml
├── storage-agents/
│   ├── backup-agent/
│   ├── compaction-agent/
│   └── replication-agent/
├── query-agents/
│   ├── query-optimizer/
│   └── cache-manager/
└── migration-agents/
    └── schema-migrator/
```

**Example: Domain Catalog (Finance)**
```
products/finance/agent-catalog/
├── agent-catalog.yaml
├── risk-agents/
│   ├── credit-risk-agent/
│   └── market-risk-agent/
├── compliance-agents/
│   └── audit-agent/
└── trading-agents/
    └── order-validator/
```

**YAPPC Agent System (`config/agents/`)** — 154 agents total (after additions)

YAPPC already has **142 agents** across 13 phases in `config/agents/registry.yaml`. The catalog defines **12 new agents** filling identified gaps, using the identical YAML format as existing agents (`id/generator/memory/tools/capabilities/routing/delegation`).

**Lifecycle stages** (from `config/lifecycle/stages.yaml`):
```
intent → context → plan → execute → verify → observe → learn → institutionalize
```

**3-Level agent hierarchy** (existing + additions):
```
config/agents/definitions/
├── [phase0–phase12]/          # 142 existing agents (Strategic L1 / Expert L2 / Worker L3)
├── phase13-devsecops/         # NEW: 8 worker agents (L3)
│   ├── cloud-resource-discovery-agent.yaml
│   ├── cloud-resource-risk-agent.yaml
│   ├── compliance-control-evaluation-agent.yaml
│   ├── compliance-gap-analysis-agent.yaml
│   ├── vulnerability-scoring-agent.yaml
│   ├── project-onboarding-agent.yaml
│   ├── context-gathering-agent.yaml
│   └── institutionalize-agent.yaml
└── orchestrators/             # NEW: 4 orchestrator agents (L1/L2)
    ├── full-lifecycle-orchestrator.yaml       # L1: routes all 8 stages
    ├── security-posture-orchestrator.yaml     # L2: delegates to sentinel+vuln-scorer
    ├── cloud-security-audit-orchestrator.yaml # L2: delegates to cloud workers
    └── compliance-audit-orchestrator.yaml     # L2: delegates to compliance workers
```

**Gap analysis** — new agents fill what the existing 142 do NOT cover:

| New Agent | Gap Filled |
|-----------|------------|
| `cloud-resource-discovery-agent` | Cloud inventory (no prior cloud agents) |
| `cloud-resource-risk-agent` | Cloud risk scoring |
| `compliance-control-evaluation-agent` | No compliance agents existed |
| `compliance-gap-analysis-agent` | No compliance agents existed |
| `vulnerability-scoring-agent` | Extends `sentinel` with CVSS/business-context scoring |
| `project-onboarding-agent` | No project onboarding agent |
| `context-gathering-agent` | `context` stage had no dedicated routing agent |
| `institutionalize-agent` | `institutionalize` stage not covered in routing |
| `security-posture-orchestrator` | No cross-tool posture aggregation |
| `cloud-security-audit-orchestrator` | No cloud audit orchestration |
| `compliance-audit-orchestrator` | No compliance orchestration |
| `full-lifecycle-orchestrator` | No end-to-end 8-stage lifecycle coordination |

Full details: `products/yappc/YAPPC_AGENT_CATALOG.md`

### 5.5 Agent Descriptor Format

**Standard schema used across all catalogs**:

```yaml
# agents/data-processing/schema-validator/agent.yaml
agent:
  id: "schema-validator-v1"           # Unique ID (catalog-scoped)
  name: "Schema Validation Agent"
  version: "1.0.0"
  type: "DETERMINISTIC"
  
  # AEP integration (optional - only for event-driven agents)
  aep:
    operatorType: "TRANSFORMATION"
    inputEventTypes: ["raw.json"]
    outputEventTypes: ["validated.json"]
    backpressure:
      bufferSize: 1000
      
  # Capabilities
  capabilities:
    - name: "json-schema-validation"
      type: "VALIDATION"
      
  # Resource requirements
  resources:
    memory: "512MB"
    cpu: "1"
    timeout: "30s"
    
  # Implementation reference
  implementation:
    class: "com.ghatana.agent.catalog.data.SchemaValidatorAgent"
    module: "platform:agent-catalog"  # Gradle module reference
```

### 5.6 Catalog Aggregation in Agent Registry

**Unified view across all catalogs**:

```java
public class UnifiedAgentRegistry implements AgentRegistry {
    
    private final CatalogRegistry catalogRegistry;
    private final DataCloudClient dataCloud;  // For runtime state
    
    @Override
    public Promise<List<AgentMatch>> findByCapabilities(
            String tenantId,
            Set<Capability> requiredCapabilities,
            CapabilityMatchStrategy strategy) {
        
        // Search across ALL discovered catalogs
        return catalogRegistry.searchAll(AgentSearchQuery.builder()
                .capabilities(requiredCapabilities)
                .strategy(strategy)
                .build())
            .map(matches -> filterByTenantAccess(matches, tenantId));
    }
    
    @Override
    public Promise<AgentRegistrationResult> register(
            String tenantId,
            AgentDescriptor descriptor) {
        
        // Store runtime registration in Data-Cloud
        // (catalog.yaml defines static agents, this tracks runtime usage)
        return dataCloud.save("agent-registrations", 
            AgentRegistrationRecord.builder()
                .tenantId(tenantId)
                .agentId(descriptor.getId())
                .sourceCatalog(descriptor.getCatalogId())
                .registeredAt(Instant.now())
                .build());
    }
}
```

### 5.7 Catalog Development Workflow

**Adding agents to a product catalog**:

```bash
# 1. Create catalog.yaml if doesn't exist
products/myproduct/agent-catalog/agent-catalog.yaml

# 2. Create agent directory
products/myproduct/agent-catalog/my-agents/
├── data-processor/
│   ├── agent.yaml          # Descriptor
│   ├── DataProcessor.java  # Implementation in product module
│   └── tests/

# 3. Agent descriptor references product implementation
# agent.yaml:
agent:
  id: "myproduct-data-processor"
  implementation:
    class: "com.ghatana.myproduct.agent.DataProcessorAgent"
    module: "products:myproduct:service"  # Implementation location
```

**Catalog inheritance**:
```yaml
catalog:
  id: "myproduct"
  extends: ["platform", "aep"]  # Inherits capabilities from these
  
  agents:
    - "my-agents/**/agent.yaml"
```

---

## Consolidated Module List

### Platform Modules (to create/modify)

| Module | Purpose | Dependencies | Status |
|--------|---------|--------------|--------|
| `agent-framework` | Agent lifecycle (existing) | core, observability | ✅ Keep |
| `agent-resilience` | Circuit breakers, retry | agent-framework, resilience4j | 🆕 Create |
| `agent-memory` | Memory consolidation | agent-framework, data-cloud | 🆕 Create |
| `agent-registry` | Agent discovery | agent-framework, data-cloud | 🆕 Create |
| `event-cloud` | Event streaming (existing) | core | ✅ Keep (no changes) |

### Product Modules (use as-is)

| Module | Responsibility | Integration |
|--------|---------------|-------------|
| `products/aep` | ALL event processing | Use AEP operators for agent execution |
| `products/data-cloud` | ALL data storage | Store agent memory, registry, checkpoints |

### Deleted/Duplicate Modules (DO NOT CREATE)

| Module | Reason |
|--------|--------|
| `event-reliability` | AEP handles this |
| `database` enhancements | Data-Cloud handles this |
| Separate registry storage | Use Data-Cloud |
| Separate memory storage | Use Data-Cloud |

---

## Implementation Phases (Revised)

### Phase 1: Cleanup (Week 1)
- [ ] Remove legacy `Agent` interface
- [ ] Remove old `Memory` implementations
- [ ] Consolidate to single `TypedAgent<I,O>`
- [ ] Remove deprecated code from `agent-framework`

### Phase 2: Agent Resilience (Weeks 2-3)
- [ ] Create `agent-resilience` module
- [ ] Implement `ResilientTypedAgent`
- [ ] Integrate Resilience4j patterns
- [ ] Add health monitoring

### Phase 3: Agent Checkpointing (Weeks 4-5)
- [ ] Implement `CheckpointedTypedAgent`
- [ ] Create `DataCloudCheckpointStore`
- [ ] Add checkpoint policies
- [ ] Test recovery scenarios

### Phase 4: AEP Integration (Weeks 6-8)
- [ ] Create `AgentOperator` for AEP
- [ ] Add backpressure operators
- [ ] Implement reliability operators
- [ ] Integrate with AEP pipeline

### Phase 5: Agent Memory (Weeks 9-11)
- [ ] Create `agent-memory` module
- [ ] Implement memory tiers using Data-Cloud
- [ ] Add consolidation pipeline
- [ ] Add retention policies

### Phase 6: Agent Registry (Weeks 12-14)
- [ ] Create `agent-registry` module
- [ ] Implement registry using Data-Cloud
- [ ] Add capability indexing
- [ ] Create registry client SDK

### Phase 7: Agent Catalog (Weeks 15-17)
- [ ] Create `platform/agent-catalog/` structure
- [ ] Migrate existing agents to catalog format
- [ ] Create agent templates
- [ ] Add governance workflows

---

## Integration Points

### Agent Framework ↔ AEP
- Agents execute as AEP operators via `AgentOperator`
- AEP provides event processing, backpressure, reliability
- Agent framework provides agent lifecycle, resilience

### Agent Framework ↔ Data-Cloud
- Agent memory stored in Data-Cloud
- Agent registry stored in Data-Cloud
- Checkpoints stored in Data-Cloud
- NO separate storage layers

### Agent Memory ↔ AEP
- Episodes stored as AEP events via Data-Cloud
- Event-driven memory updates
- Memory retrieval triggered by AEP operators

---

## Success Metrics (Revised)

| Metric | Target | Measurement |
|--------|--------|-------------|
| **Code Duplication** | Zero | Static analysis |
| **Module Count** | 4 new modules | Total count |
| **Legacy Code** | 0% | Deleted lines |
| **AEP Integration** | 100% agents as operators | Integration tests |
| **Data-Cloud Usage** | 100% storage | Storage metrics |
| **Agent Recovery** | < 5s | Recovery tests |
| **Event Throughput** | > 100k/s | AEP metrics |

---

## Risk Mitigation (No Backward Compatibility)

| Risk | Mitigation |
|------|------------|
| **Breaking Changes** | Full migration required - plan cutover |
| **Data Migration** | Migrate old memory to Data-Cloud format |
| **Testing** | Comprehensive integration test suite |
| **Rollback** | Blue-green deployment with quick rollback |

---

## Summary of Changes

**REMOVED from original plan**:
- ❌ `event-reliability` module (AEP handles this)
- ❌ `database` enhancements (Data-Cloud handles this)
- ❌ Backward compatibility adapters
- ❌ Separate storage for registry/memory
- ❌ Deprecated code retention

**RETAINED from original plan**:
- ✅ `agent-resilience` module
- ✅ `agent-memory` module (with Data-Cloud storage)
- ✅ `agent-registry` module (with Data-Cloud storage)
- ✅ Agent checkpointing
- ✅ Agent catalog structure

**ENHANCED**:
- 🔧 Clear AEP/Data-Cloud/Agent boundaries
- 🔧 All storage via Data-Cloud
- 🔧 All event processing via AEP
- 🔧 Clean architecture with no duplication
