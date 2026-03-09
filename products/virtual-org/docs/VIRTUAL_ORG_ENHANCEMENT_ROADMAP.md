# Virtual-Org Enhancement Roadmap

**Version:** 1.5.0  
**Created:** 2025-11-26  
**Updated:** 2025-11-30  
**Status:** Strategic Planning Document  
**Timeframe:** 6 Months

---

## 📋 Table of Contents

1. [Architecture Review & Deduplication](#architecture-review--deduplication)
2. [data-cloud Integration Mandate](#-critical-data-cloud-integration-mandate)
3. [Executive Summary](#executive-summary)
4. [Gap Analysis & Cross-Product Dependencies](#gap-analysis--cross-product-dependencies)
5. [Software-Org Validation Analysis](#software-org-validation-analysis)
6. [Software-Org Operations Configuration Analysis](#software-org-operations-configuration-analysis) *(NEW)*
7. [Agentic Event Processor (AEP) Cross-Product Dependencies](#agentic-event-processor-aep-cross-product-dependencies)
8. [Enhancement Phases](#enhancement-phases)
   - [Phase 1: Memory & Knowledge Foundation](#phase-1-memory--knowledge-foundation-weeks-1-6)
   - [Phase 2: Observability & Monitoring](#phase-2-observability--monitoring-weeks-7-10)
   - [Phase 3: Simulation & Replay](#phase-3-simulation--replay-weeks-11-14)
   - [Phase 4: Advanced Agent Capabilities](#phase-4-advanced-agent-capabilities-weeks-15-18)
   - [Phase 5: Additional Domain Plugins](#phase-5-additional-domain-plugins-weeks-19-24)
9. [data-cloud Required Enhancements](#data-cloud-required-enhancements)
10. [Virtual-Org Core Enhancements (For Software-Org Support)](#virtual-org-core-enhancements-for-software-org-support)
11. [Virtual-Org SPI Design](#virtual-org-spi-design)
12. [Implementation Summary](#implementation-summary)
13. [Appendices](#appendix-a-data-cloud-integration-checklist)

---

## 🔍 Architecture Review & Deduplication

> **CRITICAL FINDING:** Code review reveals significant duplication across `libs/java/*`, 
> `products/data-cloud`, and `products/virtual-org`. This section documents what EXISTS 
> and what the roadmap MUST REUSE vs create new.

### Existing Components to REUSE (Do NOT Recreate)

#### 1. AI/LLM Integration (`libs/java/ai-integration`)

| Component | Location | Status | Roadmap Action |
|-----------|----------|--------|----------------|
| `LLMGateway` | `libs/java/ai-integration/llm/LLMGateway.java` | ✅ EXISTS | **REUSE** - multi-provider gateway with routing |
| `CompletionService` | `libs/java/ai-integration/llm/CompletionService.java` | ✅ EXISTS | **REUSE** - base completion interface |
| `OpenAICompletionService` | `libs/java/ai-integration/llm/OpenAICompletionService.java` | ✅ EXISTS | **REUSE** - OpenAI implementation |
| `ToolAwareCompletionService` | `libs/java/ai-integration/llm/ToolAwareCompletionService.java` | ✅ EXISTS | **REUSE** - function calling support |
| `EmbeddingService` | `libs/java/ai-integration/embedding/EmbeddingService.java` | ✅ EXISTS | **REUSE** - embedding interface |
| `OpenAIEmbeddingService` | `libs/java/ai-integration/embedding/OpenAIEmbeddingService.java` | ✅ EXISTS | **REUSE** - OpenAI embeddings |
| `VectorStore` | `libs/java/ai-integration/vectorstore/VectorStore.java` | ✅ EXISTS | **REUSE** - vector storage SPI |
| `PgVectorStore` | `libs/java/ai-integration/vectorstore/PgVectorStore.java` | ✅ EXISTS | **REUSE** - PostgreSQL pgvector |
| `PromptTemplateManager` | `libs/java/ai-integration/prompts/PromptTemplateManager.java` | ✅ EXISTS | **REUSE** - prompt management |

**🔴 ROADMAP CORRECTION:** The roadmap proposes creating `LLMProvider SPI` in `libs/ai-integration`. 
This is **PARTIALLY DONE** - `LLMGateway` exists. Need to:
- Add Anthropic provider (new)
- Add cost tracking to existing gateway (enhancement)
- Virtual-org MUST use `libs/java/ai-integration`, NOT create its own

#### 2. State Management

| Component | Location | Status | Issue |
|-----------|----------|--------|-------|
| `StateStore<K,V>` | `libs/java/state/StateStore.java` | ✅ EXISTS | General-purpose state store |
| `StateStore<K,V>` | `libs/java/operator/state/StateStore.java` | ⚠️ DUPLICATE | Operator-specific duplicate |
| `HybridStateStore` | `libs/java/state/HybridStateStore.java` | ✅ EXISTS | Local+central hybrid store |
| `StorageCapability<K,V>` | `products/data-cloud/spi/StorageCapability.java` | ✅ EXISTS | Plugin storage SPI |

**🔴 CLEANUP REQUIRED:**
- `libs/java/operator/state/StateStore.java` → **DELETE** (use `libs/java/state/StateStore.java`)
- `virtual-org` → Use `libs/java/state/StateStore` via adapter
- **DO NOT** create new StateStore in data-cloud; use existing in `libs/java/state`

#### 3. Operator Catalog & Pipelines

| Component | Location | Status | Roadmap Action |
|-----------|----------|--------|----------------|
| `OperatorCatalog` | `libs/java/operator-catalog/OperatorCatalog.java` | ✅ EXISTS | **REUSE** for virtual-org workflow operators |
| `InMemoryOperatorCatalog` | `libs/java/operator-catalog/InMemoryOperatorCatalog.java` | ✅ EXISTS | **REUSE** for tests |
| `EventCloudOperatorCatalog` | `libs/java/operator-catalog/EventCloudOperatorCatalog.java` | ✅ EXISTS | **REUSE** for production |
| `UnifiedOperator` | `libs/java/operator/UnifiedOperator.java` | ✅ EXISTS | **REUSE** - base operator class |
| `Pipeline` | `libs/java/operator/pipeline/Pipeline.java` | ✅ EXISTS | **REUSE** for workflow pipelines |

**🔴 ROADMAP CORRECTION:** The roadmap proposes `PipelineRegistry` in virtual-org. 
Instead: **REUSE** `OperatorCatalog` + `Pipeline` from `libs/java/operator-catalog`.

#### 4. Observability

| Component | Location | Status | Roadmap Action |
|-----------|----------|--------|----------------|
| `MetricsCollector` | `libs/java/observability/MetricsCollector.java` | ✅ EXISTS | **REUSE** |
| `TraceStorage` | `libs/java/observability/trace/TraceStorage.java` | ✅ EXISTS | **REUSE** |
| `SpanData` | `libs/java/observability/trace/SpanData.java` | ✅ EXISTS | **REUSE** |
| `CorrelationContext` | `libs/java/observability/CorrelationContext.java` | ✅ EXISTS | **REUSE** |

### Existing Duplications to CONSOLIDATE

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                       IDENTIFIED DUPLICATIONS (MUST CONSOLIDATE)                     │
├─────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                     │
│  1. STATE STORE DUPLICATION                                                         │
│     ├── libs/java/state/StateStore.java           ← CANONICAL (keep)               │
│     ├── libs/java/operator/state/StateStore.java  ← DELETE (duplicate)             │
│     └── data-cloud/spi/StorageCapability.java     ← DIFFERENT PURPOSE (keep)       │
│                                                                                     │
│  2. EMBEDDING SERVICE DUPLICATION                                                   │
│     ├── libs/java/ai-integration/embedding/EmbeddingService.java  ← CANONICAL      │
│     └── virtual-org/framework/memory/EmbeddingService.java        ← DELETE         │
│                                                                                     │
│  3. VECTOR STORE CONSIDERATION                                                      │
│     ├── libs/java/ai-integration/vectorstore/VectorStore.java     ← CANONICAL      │
│     └── data-cloud/plugins/analytics/ml-intelligence              ← EXTEND this    │
│                                                                                     │
│  4. AGENT RUNTIME OVERLAP                                                           │
│     ├── aep/libs/agent-runtime/BaseAgent.java                     ← AEP agents     │
│     └── virtual-org/framework/agent/BaseOrganizationalAgent.java  ← Org agents     │
│     RESOLUTION: virtual-org agents CAN extend AEP BaseAgent for compatibility      │
│                                                                                     │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

### Cleanup Tasks (Pre-Roadmap)

| Task | Location | Action | Priority | Effort |
|------|----------|--------|----------|--------|
| Delete duplicate StateStore | `libs/java/operator/state/` | Delete, update imports | P0 | 1 day |
| Delete duplicate EmbeddingService | `virtual-org/framework/memory/` | Delete, use libs/ai-integration | P0 | 0.5 day |
| Consolidate VectorStore | Review both locations | Decide canonical location | P1 | 1 day |
| Update virtual-org AgentMemory | `virtual-org/framework/memory/` | Use libs/java/state/StateStore | P0 | 2 days |
| Document canonical locations | All libs | Create ARCHITECTURE.md | P1 | 1 day |

### Corrected Component Ownership

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                          CORRECTED COMPONENT OWNERSHIP                               │
├─────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                     │
│  libs/java/ai-integration    (CANONICAL for AI)                                     │
│  ├── LLMGateway              → Multi-provider LLM access                           │
│  ├── CompletionService       → Completion interface                                │
│  ├── EmbeddingService        → Embedding interface                                 │
│  ├── VectorStore             → Vector storage SPI                                  │
│  └── PromptTemplateManager   → Prompt management                                   │
│                                                                                     │
│  libs/java/state             (CANONICAL for State)                                  │
│  ├── StateStore<K,V>         → Generic state storage                               │
│  ├── HybridStateStore        → Local + central hybrid                              │
│  └── SyncStrategy            → Sync behavior                                       │
│                                                                                     │
│  libs/java/operator          (CANONICAL for Operators)                              │
│  ├── UnifiedOperator         → Base operator                                       │
│  ├── Pipeline                → Pipeline abstraction                                │
│  └── [DELETE state/]         → Remove duplicate StateStore                         │
│                                                                                     │
│  libs/java/operator-catalog  (CANONICAL for Catalog)                                │
│  └── OperatorCatalog         → Operator registry                                   │
│                                                                                     │
│  libs/java/observability     (CANONICAL for Observability)                          │
│  ├── MetricsCollector        → Metrics                                             │
│  ├── TraceStorage            → Distributed tracing                                 │
│  └── CorrelationContext      → Request correlation                                 │
│                                                                                     │
│  products/data-cloud         (CANONICAL for Data Layer)                             │
│  ├── EventCloud              → Event streaming                                     │
│  ├── StorageCapability       → Plugin storage SPI                                  │
│  └── plugins/*               → Storage backends                                    │
│                                                                                     │
│  products/agentic-event-processor (CANONICAL for AEP)                               │
│  ├── agent-runtime           → Agent lifecycle, BaseAgent                          │
│  ├── agent-registry          → Agent registration                                  │
│  ├── orchestrator            → Pipeline orchestration                              │
│  └── pattern-system          → Pattern operators                                   │
│                                                                                     │
│  products/virtual-org        (FRAMEWORK - Uses all above)                           │
│  ├── Agent abstraction       → USES libs/java/ai-integration for LLM              │
│  ├── Memory abstraction      → USES libs/java/state for storage                   │
│  ├── Event publishing        → USES data-cloud EventCloud                          │
│  ├── Workflow engine         → USES libs/java/operator for pipelines              │
│  └── [DELETE duplicates]     → Remove EmbeddingService duplicate                  │
│                                                                                     │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

### Roadmap Impact

These findings reduce the roadmap effort significantly:

| Original Plan | Corrected Plan | Savings |
|---------------|----------------|---------|
| Create `LLMProvider` SPI in ai-integration | Enhance existing `LLMGateway` | 5 days → 2 days |
| Create `EmbeddingService` in data-cloud | Already exists in ai-integration | 3 days → 0 days |
| Create `VectorSearchCapability` in data-cloud | Enhance `VectorStore` in ai-integration | 5 days → 2 days |
| Create `PipelineRegistry` in virtual-org | Use `OperatorCatalog` from libs | 3 days → 1 day |
| Create `StateStore` adapters | Already exists in libs/java/state | 4 days → 1 day |

**Total Effort Reduction:** ~15 engineering days saved by reusing existing components

---

## ⚠️ CRITICAL: Libs + data-cloud Integration Mandate

> **REUSE FIRST PRINCIPLE:** All implementations MUST check `libs/java/*` first, then `data-cloud`.
> Virtual-Org is a CONSUMER of platform services, NOT a creator of duplicate implementations.

### 🔧 Canonical Component Locations (UPDATED)

| Virtual-Org Need | Canonical Location | Notes |
|-----------------|-------------------|-------|
| **LLM/AI Access** | `libs/java/ai-integration/llm/LLMGateway.java` | Multi-provider gateway EXISTS |
| **Embeddings** | `libs/java/ai-integration/embedding/EmbeddingService.java` | OpenAI impl EXISTS |
| **Vector Storage** | `libs/java/ai-integration/vectorstore/VectorStore.java` | PgVector impl EXISTS |
| **State Store** | `libs/java/state/StateStore.java` | HybridStateStore EXISTS |
| **Events/Telemetry** | `products/data-cloud/event-cloud/` | EventCloud |
| **Event Storage** | `products/data-cloud/spi/StorageCapability.java` | Plugin SPI |
| **Operator Catalog** | `libs/java/operator-catalog/OperatorCatalog.java` | Registration EXISTS |
| **Pipelines** | `libs/java/operator/pipeline/Pipeline.java` | Pipeline abstraction EXISTS |
| **Metrics** | `libs/java/observability/MetricsCollector.java` | Observability EXISTS |
| **Tracing** | `libs/java/observability/trace/TraceStorage.java` | Trace storage EXISTS |

### Forbidden Patterns (EXPANDED)

❌ **DO NOT** create `EmbeddingService.java` in virtual-org - **ALREADY EXISTS** in `libs/java/ai-integration`  
❌ **DO NOT** create `VectorStore.java` in virtual-org - **ALREADY EXISTS** in `libs/java/ai-integration`  
❌ **DO NOT** create `LLMProvider.java` in virtual-org - USE existing `LLMGateway` in `libs/java/ai-integration`  
❌ **DO NOT** create `StateStore.java` in virtual-org - USE existing in `libs/java/state`  
❌ **DO NOT** create `PipelineRegistry.java` - USE existing `OperatorCatalog` in `libs/java/operator-catalog`  
❌ **DO NOT** create event storage in virtual-org - USE data-cloud EventCloud  
❌ **DO NOT** create metrics/tracing - USE `libs/java/observability`

### Correct Integration Pattern (UPDATED)

```java
// ✅ CORRECT: Virtual-Org uses existing libs
public class AgentMemoryStore implements AgentMemory {
    
    // REUSE libs/java/state/StateStore
    private final StateStore<String, MemoryEntry> stateStore;
    
    // REUSE libs/java/ai-integration/embedding/EmbeddingService
    private final com.ghatana.ai.embedding.EmbeddingService embeddingService;
    
    // REUSE libs/java/ai-integration/vectorstore/VectorStore
    private final com.ghatana.ai.vectorstore.VectorStore vectorStore;
    
    public AgentMemoryStore(
            StateStore<String, MemoryEntry> stateStore,
            com.ghatana.ai.embedding.EmbeddingService embeddingService,
            com.ghatana.ai.vectorstore.VectorStore vectorStore
    ) {
        this.stateStore = stateStore;           // From libs/java/state
        this.embeddingService = embeddingService; // From libs/java/ai-integration
        this.vectorStore = vectorStore;          // From libs/java/ai-integration
    }
    
    @Override
    public Promise<Void> store(MemoryEntry entry) {
        // Use existing StateStore
        return stateStore.put(entry.key(), entry);
    }
    
    @Override
    public Promise<List<MemoryEntry>> searchSimilar(String query, int limit) {
        // Use existing EmbeddingService + VectorStore
        return embeddingService.createEmbedding(query)
            .then(result -> vectorStore.search(
                result.getVector().stream().mapToDouble(f -> f).toArray(),
                limit, 0.7
            ))
            .map(results -> results.stream()
                .map(r -> stateStore.get(r.getId()).getResult().orElse(null))
                .filter(Objects::nonNull)
                .collect(Collectors.toList()));
    }
}
```

---

## Executive Summary

Based on the completeness assessment AND architecture review, this roadmap outlines strategic enhancements to elevate Virtual-Org from **87% to 95%+ completeness**, while ensuring **REUSE of existing platform components**.

**Key Changes:**
- **v1.1.0:** All data layer implementations now explicitly use **data-cloud** as the unified storage/event platform.
- **v1.2.0:** Added comprehensive gap analysis, data-cloud enhancement requirements, and virtual-org SPI design for pluggability.
- **v1.3.0:** Added AEP cross-product dependencies and software-org validation analysis.
- **v1.4.0:** **Architecture review identifying existing components to REUSE**, deduplication tasks, and effort reduction (~15 days saved).

---

## Gap Analysis & Cross-Product Dependencies

### Critical Gaps Identified (UPDATED after Architecture Review)

After comprehensive review against modern multi-agent frameworks (AutoGen, CrewAI, LangGraph) and **existing codebase components**, the following gaps require attention:

#### 1. Virtual-Org Gaps (Framework Level) - REVISED

| Gap Area | Current State | Required State | Impact | Note |
|----------|--------------|----------------|--------|------|
| **Agent Pluggability** | Hard-coded agent implementations | SPI-based agent creation | High | NEW SPI needed |
| **Tool Extensibility** | Fixed tool registry | Plugin-based tool discovery | High | NEW SPI needed |
| **LLM Provider Abstraction** | `LLMGateway` EXISTS in ai-integration | Add Anthropic, cost tracking | Medium | **ENHANCE existing** |
| **Message Bus SPI** | In-memory only | Pluggable message transports | High | NEW SPI needed |
| **Workflow Persistence** | In-memory workflow state | Event-sourced workflows | High | Use EventCloud |
| **Organization Templates** | Manual YAML creation | Template registry/marketplace | Medium | NEW capability |
| **Multi-Modal Agents** | Text only | Vision/Audio/Code execution | Medium | Extend LLMGateway |
| **Agent Marketplace** | No sharing mechanism | Reusable agent templates | Low | NEW capability |

#### 2. Existing Components to ENHANCE (Not Create New)

| Component | Location | Enhancement Needed | Owner |
|-----------|----------|-------------------|-------|
| `LLMGateway` | `libs/java/ai-integration` | Add Anthropic provider, cost tracking | ai-integration |
| `EmbeddingService` | `libs/java/ai-integration` | Add caching layer | ai-integration |
| `VectorStore` | `libs/java/ai-integration` | Add namespace support | ai-integration |
| `StateStore` | `libs/java/state` | Add TTL, namespace isolation | state |
| `OperatorCatalog` | `libs/java/operator-catalog` | Add workflow operators | operator-catalog |

#### 3. data-cloud Gaps (Required for Virtual-Org) - REVISED

| Gap Area | Current State | Required Enhancement | Owner |
|----------|--------------|---------------------|-------|
| **Namespace Isolation** | Tenant-level only | Per-simulation namespaces | data-cloud |
| **Real-time Subscriptions** | Basic pub/sub | WebSocket-ready event streams | data-cloud |
| **Cost Attribution** | No cost tracking | Token/API cost event types | data-cloud |
| **Document Ingestion** | No document processing | Document chunking pipeline | data-cloud |

**Note:** Vector Search and Embedding already exist in `libs/java/ai-integration` - do NOT duplicate in data-cloud.

#### 4. Cross-Product Integration Gaps - REVISED

| Integration Point | Current | Required | Products Affected |
|-------------------|---------|----------|-------------------|
| **Agent Memory ↔ StateStore** | Not integrated | Adapter using `libs/java/state` | virtual-org |
| **Telemetry ↔ EventCloud** | Not integrated | Event-based observability | virtual-org, data-cloud |
| **AI Service ↔ LLM** | **EXISTS in libs** | Virtual-org adapter to `LLMGateway` | virtual-org |
| **Workflow ↔ EventCloud** | Not integrated | Event-sourced workflows | virtual-org, data-cloud |
| **Tool Registry ↔ Plugin** | Static registry | Dynamic plugin discovery | virtual-org |

### Modern Framework Feature Comparison
| **LLM Provider Abstraction** | No standardized LLM SPI | Pluggable LLM providers | Critical |
| **Message Bus SPI** | In-memory only | Pluggable message transports | High |
| **Workflow Persistence** | In-memory workflow state | Event-sourced workflows | High |
| **Organization Templates** | Manual YAML creation | Template registry/marketplace | Medium |
| **Multi-Modal Agents** | Text only | Vision/Audio/Code execution | Medium |
| **Agent Marketplace** | No sharing mechanism | Reusable agent templates | Low |

#### 2. data-cloud Gaps (Required for Virtual-Org)

| Gap Area | Current State | Required Enhancement | Owner |
|----------|--------------|---------------------|-------|
| **Vector Search SPI** | No vector storage abstraction | `VectorSearchPlugin` SPI | data-cloud |
| **Embedding Service** | Not available | `EmbeddingService` in ml-intelligence | data-cloud |
| **Document Ingestion** | No document processing | Document chunking pipeline | data-cloud |
| **Semantic Search** | Basic text search | RAG-ready semantic retrieval | data-cloud |
| **Namespace Isolation** | Tenant-level only | Per-simulation namespaces | data-cloud |
| **Real-time Subscriptions** | Basic pub/sub | WebSocket-ready event streams | data-cloud |
| **Cost Attribution** | No cost tracking | Token/API cost event types | data-cloud |

#### 3. Cross-Product Integration Gaps

| Integration Point | Current | Required | Products Affected |
|-------------------|---------|----------|-------------------|
| **Agent Memory ↔ StateStore** | Not integrated | Full integration via SPI | virtual-org, data-cloud |
| **Telemetry ↔ EventCloud** | Not integrated | Event-based observability | virtual-org, data-cloud |
| **AI Service ↔ LLM** | No abstraction | LLM Provider SPI | virtual-org, libs:ai-integration |
| **Workflow ↔ EventCloud** | Not integrated | Event-sourced workflows | virtual-org, data-cloud |
| **Tool Registry ↔ Plugin** | Static registry | Dynamic plugin discovery | virtual-org, data-cloud |

### Modern Framework Feature Comparison

| Feature | AutoGen | CrewAI | LangGraph | Virtual-Org Current | Virtual-Org Target |
|---------|---------|--------|-----------|---------------------|-------------------|
| **Streaming Responses** | ✅ | ✅ | ✅ | ❌ | ✅ Phase 2 |
| **Parallel Tool Calls** | ✅ | ✅ | ✅ | 🔄 Partial | ✅ Phase 1 |
| **Function Calling** | ✅ | ✅ | ✅ | ✅ | ✅ |
| **Memory Persistence** | 🔄 | 🔄 | ✅ | ❌ | ✅ Phase 1 |
| **RAG Integration** | 🔄 External | ✅ | 🔄 External | ❌ | ✅ Phase 1 |
| **Checkpointing** | ❌ | ❌ | ✅ | ❌ | ✅ Phase 3 |
| **Human-in-the-Loop** | ✅ | 🔄 | ✅ | ✅ | ✅ |
| **Cost Tracking** | ❌ | ❌ | ❌ | ❌ | ✅ Phase 2 |
| **Multi-Agent Groups** | ✅ | ✅ | ✅ | ✅ | ✅ |
| **Hierarchical Teams** | 🔄 | ✅ | 🔄 | ✅ | ✅ |
| **Agent Learning** | ❌ | ❌ | ❌ | ❌ | ✅ Phase 4 |
| **Simulation/Replay** | ❌ | ❌ | ✅ | ❌ | ✅ Phase 3 |
| **Visual Builder** | ❌ | ❌ | ✅ LangGraph Studio | ❌ | 🔶 Future |

**Legend:** ✅ Full Support | 🔄 Partial | ❌ Not Available | 🔶 Future Consideration

---

## Software-Org Validation Analysis

> **Purpose:** Validate that virtual-org serves its purpose as a framework by examining 
> software-org as a **concrete, real-world implementation**. Software-org is built upon 
> virtual-org with configurations + org-specific hooks, handlers, and logic.

### Current Software-Org Architecture

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                          SOFTWARE-ORG PRODUCT STRUCTURE                              │
├─────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                     │
│  config/                               (YAML-driven configuration)                  │
│  ├── organization.yaml                 → Org definition, settings, references      │
│  ├── departments/                      → 10 department configs                      │
│  │   ├── engineering.yaml                                                          │
│  │   ├── qa.yaml, devops.yaml, support.yaml, sales.yaml                           │
│  │   ├── marketing.yaml, product.yaml, finance.yaml, hr.yaml, compliance.yaml     │
│  ├── agents/                           → 11 agent configs                          │
│  │   ├── ceo-agent.yaml, cto-agent.yaml, cpo-agent.yaml                           │
│  │   ├── architect-lead-agent.yaml, senior-engineer-agent.yaml                    │
│  │   ├── engineer-agent.yaml, junior-engineer-agent.yaml                          │
│  │   ├── devops-lead-agent.yaml, devops-engineer-agent.yaml                       │
│  │   ├── product-manager-agent.yaml, qa-engineer-agent.yaml                       │
│  └── workflows/                        → 4 workflow configs                        │
│      ├── sprint-planning.yaml                                                      │
│      ├── incident-response.yaml                                                    │
│      ├── release-management.yaml                                                   │
│      └── feature-delivery.yaml                                                     │
│                                                                                     │
│  libs/java/                            (Java implementation)                        │
│  ├── software-org/                     → Domain-specific agents & logic            │
│  │   └── ConfigDrivenAgent.java        → Extends BaseOrganizationalAgent           │
│  ├── framework/                        → Software-org extension framework          │
│  │   └── BaseSoftwareOrgDepartment.java → Extends Department                       │
│  └── bootstrap/                        → Initialization & orchestration            │
│      ├── SoftwareOrgOrchestrator.java  → Multi-department orchestration            │
│      ├── AllDepartmentPipelinesRegistrar.java → Pipeline registration              │
│      └── DepartmentFactory.java        → Department instantiation                  │
│                                                                                     │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

### What Virtual-Org Provides (✅ Working)

| Capability | Virtual-Org Class/Module | Software-Org Usage |
|------------|-------------------------|-------------------|
| **Organization Base** | `AbstractOrganization` | Extended by software-org |
| **Department Base** | `Department` | Extended by `BaseSoftwareOrgDepartment` |
| **Agent Base** | `BaseOrganizationalAgent` | Extended by `ConfigDrivenAgent` |
| **Config Loading** | `OrganizationConfigLoader` | Loads YAML configs |
| **Hierarchy Model** | `Role`, `Authority`, `EscalationPath` | Used for agent hierarchy |
| **Workflow Engine** | `WorkflowEngine`, `WorkflowDefinition` | Executes sprint-planning, etc. |
| **HITL Controls** | `ApprovalGateway`, `ConfidenceRouter` | Human approval checkpoints |
| **Event Publishing** | `EventPublisher`, `EventBuilder` | Department events |
| **Tool Framework** | `AgentTool`, `ToolRegistry` | Agent capabilities |

### What Software-Org Needs But Virtual-Org Lacks (❌ Gaps)

| Gap | Software-Org Need | Current Workaround | Required Virtual-Org Enhancement |
|-----|-------------------|-------------------|--------------------------------|
| **Pipeline Registration** | Register department pipelines with AEP | `AllDepartmentPipelinesRegistrar` in software-org | `PipelineRegistry` SPI in virtual-org |
| **Cross-Dept Orchestration** | Coordinate multi-department operations | `SoftwareOrgOrchestrator` in software-org | `OrganizationOrchestrator` in virtual-org |
| **Department Factory** | Create departments from config | `DepartmentFactory` in software-org | `DepartmentProvider` SPI in virtual-org |
| **Agent Factory** | Create agents from config | `RoleAgentFactory` in software-org | `AgentProvider` SPI in virtual-org |
| **Interaction Handlers** | Cross-department interactions | Not implemented | `InteractionHandler` SPI in virtual-org |
| **KPI Aggregation** | Org-wide KPI computation | Stub in `SoftwareOrgOrchestrator` | `KpiAggregator` in virtual-org |
| **Event Bus Integration** | Connect to AEP/EventCloud | `SoftwareOrgAepInitializer` | `EventBusConnector` SPI in virtual-org |
| **Workflow Triggers** | Schedule/event-based triggers | Manual triggers only | `WorkflowTriggerEngine` in virtual-org |
| **Agent Runtime Context** | Runtime state for agents | Basic `AgentContext` | Enhanced `AgentRuntime` with data-cloud |

### Required Virtual-Org Enhancements for Software-Org

#### 1. Organization Lifecycle Management

**Problem:** Software-org has `SoftwareOrgOrchestrator` that should be generic.

```java
// CURRENT: Software-org specific orchestrator
public class SoftwareOrgOrchestrator {
    public int initialize() { ... }
    public Object getDepartment(String type) { ... }
    public Map<String, Object> aggregateKpis() { ... }
}

// NEEDED: Virtual-org generic orchestrator
public interface OrganizationLifecycleManager {
    Promise<Void> initialize(OrganizationConfig config);
    Promise<Void> start();
    Promise<Void> stop();
    Promise<Void> reload(OrganizationConfig newConfig);
    
    <T extends Department> T getDepartment(String departmentId);
    List<Department> getAllDepartments();
    
    Promise<Map<String, Object>> aggregateKpis();
    Promise<HealthStatus> healthCheck();
}
```

#### 2. Pipeline/Workflow Registration SPI

**Problem:** Software-org manually registers pipelines; should be declarative.

```java
// NEEDED: Virtual-org pipeline SPI
public interface PipelineRegistry {
    Promise<Void> registerFromConfig(WorkflowConfig config);
    Promise<Void> registerAll(List<WorkflowConfig> configs);
    
    Optional<Pipeline> getPipeline(String pipelineId);
    List<Pipeline> getPipelinesForDepartment(String departmentId);
    
    // Integration with AEP/data-cloud
    Promise<Void> connectToEventProcessor(EventProcessorConfig config);
}
```

#### 3. Cross-Department Interaction SPI

**Problem:** Software-org has interaction configs but no handler.

```java
// NEEDED: Virtual-org interaction handler
public interface InteractionHandler extends Plugin {
    String getInteractionType();  // "handoff", "escalation", "collaboration"
    
    Promise<InteractionResult> handle(InteractionRequest request);
    
    // Example interaction types:
    // - eng-qa-handoff: Engineering → QA code handoff
    // - support-engineering-escalation: Support → Engineering bug escalation
    // - sales-product-feedback: Sales → Product feature requests
}

public interface InteractionRegistry {
    void registerHandler(InteractionHandler handler);
    Promise<InteractionResult> executeInteraction(String interactionType, InteractionRequest request);
}
```

#### 4. Workflow Trigger Engine

**Problem:** Software-org workflows have triggers in YAML but no runtime support.

```yaml
# sprint-planning.yaml defines triggers:
trigger:
  schedule:
    cron: "0 9 * * 1/2"
  events:
    - type: sprint.completed
  manual:
    enabled: true
```

```java
// NEEDED: Virtual-org trigger engine
public interface WorkflowTriggerEngine {
    // Schedule-based triggers
    Promise<Void> scheduleTrigger(String workflowId, CronExpression cron);
    
    // Event-based triggers
    Promise<Void> registerEventTrigger(String workflowId, String eventType);
    
    // Manual triggers
    Promise<WorkflowExecution> triggerManually(String workflowId, AgentId triggeredBy);
    
    // Integration with EventCloud
    Promise<Void> connectToEventCloud(EventCloud eventCloud);
}
```

#### 5. Organization Template Instantiation

**Problem:** Creating a new org type requires significant code duplication.

```java
// NEEDED: Virtual-org template system (see SPI section above)
// This allows: new HealthcareOrg() to be as easy as new SoftwareOrg()
public interface OrganizationTemplate {
    String getTemplateId();           // "software-org", "healthcare-org"
    String getDomain();               // "software", "healthcare"
    
    List<DepartmentConfig> getDefaultDepartments();
    List<AgentConfig> getDefaultAgents();
    List<WorkflowConfig> getDefaultWorkflows();
    List<InteractionConfig> getDefaultInteractions();
    
    ValidationResult validate(Map<String, Object> customization);
    OrganizationConfig instantiate(Map<String, Object> customization);
}
```

### Software-Org as Reference Implementation

Software-org should serve as the **reference implementation** demonstrating:

1. **Config-First Development**
   - Organization defined entirely in YAML
   - Departments, agents, workflows as config references
   - No Java code for adding new departments/agents

2. **Extension Points**
   - `BaseSoftwareOrgDepartment` shows how to extend `Department`
   - `ConfigDrivenAgent` shows how to extend `BaseOrganizationalAgent`
   - Hooks: `onInitialize()`, `onShutdown()`, `onTaskAssigned()`

3. **Integration Patterns**
   - AEP integration via `SoftwareOrgAepInitializer`
   - Event publishing via `EventPublisher`
   - Pipeline registration via `AllDepartmentPipelinesRegistrar`

4. **Validation Test Cases**
   - Organization loads correctly from YAML
   - All 10 departments register
   - All 11 agents created with correct roles
   - All 4 workflows execute
   - Cross-department interactions work

### Gap Summary: Virtual-Org Must Provide

| Category | Required Enhancement | Priority | Effort |
|----------|---------------------|----------|--------|
| **Lifecycle** | `OrganizationLifecycleManager` | P0 | 3 days |
| **Orchestration** | Generic `OrganizationOrchestrator` | P0 | 3 days |
| **Pipelines** | `PipelineRegistry` SPI | P1 | 2 days |
| **Interactions** | `InteractionHandler` SPI | P1 | 3 days |
| **Triggers** | `WorkflowTriggerEngine` | P1 | 4 days |
| **Templates** | `OrganizationTemplate` SPI | P2 | 3 days |
| **KPIs** | `KpiAggregator` service | P1 | 2 days |
| **Event Bus** | `EventBusConnector` SPI | P0 | 2 days |

**Total:** ~22 engineering days to make software-org fully leverage virtual-org

---

## Software-Org Operations Configuration Analysis

> **Purpose:** Analyze the existing 420+ agent specifications in software-org's `devsecops/specs/agents/`
> to identify redundancies, extract reusable patterns, and reframe as **default organizational operations**
> rather than DevSecOps-specific stages.

### Configuration Inventory

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                    SOFTWARE-ORG CONFIGURATION STRUCTURE                              │
├─────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                     │
│  src/main/resources/                                                                │
│  ├── config/                                                                        │
│  │   └── departments/          (5 department configs)                               │
│  │       ├── engineering.yaml  → Architect, Senior/Junior Engineers                 │
│  │       ├── devops.yaml       → DevOps Lead, SRE, DevOps Engineers                │
│  │       ├── product.yaml      → Product Manager, Designers                        │
│  │       ├── qa.yaml           → QA Lead, Test Engineers                           │
│  │       └── executive.yaml    → CEO, CTO, CPO                                     │
│  │                                                                                  │
│  └── devsecops/                                                                     │
│      ├── mappings/             (3 mapping files)                                    │
│      │   ├── persona_registry.yaml      → 30 persona definitions                   │
│      │   ├── phase_personas.yaml        → Phase-to-persona assignments             │
│      │   └── stage_phase_mapping.yaml   → 17 stage-to-phase mappings               │
│      │                                                                              │
│      ├── specs/agents/         (420 agent specifications!)                          │
│      │   ├── [stage]_*.yaml    → Stage-specific cross-cutting agents               │
│      │   └── *.yaml            → Domain-specific agents                            │
│      │                                                                              │
│      ├── specs2/stages/        (17 stage workflow definitions)                      │
│      │   ├── 1_plan.yaml       → 1,428 lines! Full workflow definition             │
│      │   ├── 2_solution.yaml → 3_design.yaml → ... → 16_retrospective.yaml         │
│      │   └── 0_audit_trail.yaml, 17_dashboard.yaml                                 │
│      │                                                                              │
│      └── specs2/pipelines/     (2 pipeline orchestration files)                     │
│          ├── devsecops_pipeline.yaml    → Includes all 17 stages                   │
│          └── audit_trail.yaml           → Cross-cutting audit                      │
│                                                                                     │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

### 🔴 REDUNDANCY ANALYSIS: Cross-Cutting Agent Patterns

The 420 agent specs contain **MASSIVE DUPLICATION**. Analysis reveals 15+ agent types 
that are duplicated across 10-16 stages with only stage-name prefix changes:

| Cross-Cutting Agent | Duplicate Count | Example Files |
|---------------------|-----------------|---------------|
| `*_audit_log_agent` | 16 copies | `build_audit_log_agent.yaml`, `test_audit_log_agent.yaml`, `design_audit_log_agent.yaml` |
| `*_progress_dashboard` | 15 copies | `build_progress_dashboard.yaml`, `test_progress_dashboard.yaml` |
| `*_observability_export` | 15 copies | `build_observability_export.yaml`, `deploy_observability_export.yaml` |
| `*_feedback_loop` | 15 copies | `build_feedback_loop.yaml`, `test_feedback_loop.yaml` |
| `*_evidence_collector` | 14 copies | `build_evidence_collector.yaml`, `secure_evidence_collector.yaml` |
| `*_notification_agent` | 13 copies | `build_notification_agent.yaml`, `release_notification_agent.yaml` |
| `*_success_criteria_tracker` | 11 copies | `build_success_criteria_tracker.yaml`, `test_success_criteria_tracker.yaml` |
| `*_risk_trend_analyzer` | 11 copies | `build_risk_trend_analyzer.yaml`, `deploy_risk_trend_analyzer.yaml` |
| `*_milestone_tracker` | 11 copies | `build_milestone_tracker.yaml`, `design_milestone_tracker.yaml` |
| `*_kpi_agent` | 11 copies | `build_kpi_agent.yaml`, `test_kpi_agent.yaml`, `design_kpi_agent.yaml` |
| `*_best_practice_enforcement` | 11 copies | `build_best_practice_enforcement.yaml`, `secure_best_practice_enforcement.yaml` |
| `*_readiness_checker` | 10 copies | `build_readiness_checker.yaml`, `compliance_readiness_checker.yaml` |
| `*_go_nogo` | 8 copies | `build_go_nogo.yaml`, `design_go_nogo.yaml`, `test_go_nogo.yaml` |
| `*_escalation_handler` | 7 copies | `build_escalation_handler.yaml`, `design_escalation_handler.yaml` |

**Impact:** ~180 files are duplicates with only stage-name prefixes changed!

### ✅ STREAMLINED ARCHITECTURE: Parameterized Cross-Cutting Operators

Instead of 420 files, we need **~80 unique specifications** organized as:

#### 1. Cross-Cutting Operators (Parameterized by Stage)

These become **generic organizational operators** that work across ANY stage/operation:

```yaml
# NEW: ops/cross-cutting/audit_log_operator.yaml
id: audit_log_operator
operator: GenericAuditLogOperator
parameters:
  - name: stage_id
    type: string
    description: "The stage/operation this audit log covers"
  - name: artifact_types
    type: list
    description: "Types of artifacts to audit"
role: Compliance Bot
agent_tasks:
  - "Record every agent action and transition with timestamp, user/agent, and artifact link."
  - "Log major events (signoffs, escalations, notifications, exceptions)."
  - "Cryptographically sign and archive logs in secure, tamper-evident location."
  - "Ensure audit log is accessible for compliance review."
acceptance_criteria:
  - "Every agent action and transition is recorded with timestamp."
  - "Major events are explicitly logged."
  - "Logs are cryptographically signed and archived."
inputs_spec:
  - name: "${stage_id}_progress_dashboard"
    description: "Progress dashboard for ${stage_id}"
  - name: "${stage_id}_notification_log"
    description: "Notification delivery logs"
outputs_spec:
  - name: "${stage_id}_audit_log"
    description: "Complete audit log for ${stage_id}"
```

#### 2. Consolidated Cross-Cutting Operator Inventory

| Operator Type | Purpose | Consolidates |
|---------------|---------|--------------|
| `AuditLogOperator` | Record actions, sign & archive logs | 16 `*_audit_log_agent` files |
| `ProgressDashboardOperator` | Real-time progress visualization | 15 `*_progress_dashboard` files |
| `ObservabilityExportOperator` | Export metrics, traces, logs | 15 `*_observability_export` files |
| `FeedbackLoopOperator` | Collect and route feedback | 15 `*_feedback_loop` files |
| `EvidenceCollectorOperator` | Gather evidence for compliance | 14 `*_evidence_collector` files |
| `NotificationOperator` | Multi-channel notifications | 13 `*_notification_agent` files |
| `SuccessCriteriaTrackerOperator` | Track success criteria completion | 11 `*_success_criteria_tracker` files |
| `RiskTrendAnalyzerOperator` | Analyze risk patterns over time | 11 `*_risk_trend_analyzer` files |
| `MilestoneTrackerOperator` | Track milestone progress | 11 `*_milestone_tracker` files |
| `KpiOperator` | Compute and report KPIs | 11 `*_kpi_agent` files |
| `BestPracticeEnforcementOperator` | Enforce best practices | 11 `*_best_practice_enforcement` files |
| `ReadinessCheckerOperator` | Check readiness for gate passage | 10 `*_readiness_checker` files |
| `GoNoGoOperator` | Go/No-Go decision gate | 8 `*_go_nogo` files |
| `EscalationHandlerOperator` | Handle escalations | 7 `*_escalation_handler` files |

**Result:** 14 parameterized operators replace ~180 duplicate files

#### 3. Domain-Specific Operators (Unique Logic)

These are **genuinely unique** and should remain as separate specifications:

| Category | Operators | Count |
|----------|-----------|-------|
| **Planning** | `clarify_product_idea`, `business_goals`, `success_criteria`, `stakeholder_mapping`, `risk_dependency` | 12 |
| **Solution** | `solution_architecture`, `threat_model`, `design_patterns`, `interface_specification` | 8 |
| **Design** | `component_design`, `data_model`, `ux_design`, `architecture_review`, `design_lint` | 10 |
| **Development** | `coding_guidelines`, `peer_review`, `code_duplication`, `precommit_automation` | 8 |
| **Build** | `build_pipeline`, `container_build`, `artifact_signing`, `sbom`, `supply_chain_risk` | 10 |
| **Test** | `unit_test`, `integration_test`, `e2e_test`, `performance_test`, `chaos_test`, `test_coverage` | 15 |
| **Security** | `sast`, `dependency_scan`, `container_scan`, `secret_scan`, `vulnerability_watch`, `iac_scan` | 12 |
| **Compliance** | `compliance_validation`, `license_compliance`, `regulatory_evidence`, `policy_gate` | 8 |
| **Release** | `release_validation`, `canary_rollout`, `changelog_generator`, `release_notes` | 8 |
| **Operations** | `deployment_strategy`, `incident_response`, `health_check`, `self_healing`, `disaster_recovery` | 15 |
| **Monitoring** | `synthetic_monitoring`, `anomaly_detection`, `slo_tracking`, `alerting` | 8 |
| **Retrospective** | `retrospective`, `continuous_improvement`, `postmortem` | 5 |

**Total Unique Domain Operators:** ~119 specifications

### 🔄 REFRAME: DevSecOps → Default Organizational Operations

Instead of treating these as "DevSecOps stages", reframe as **standard organizational operations**:

```yaml
# NEW TAXONOMY: Organization Operations (not DevSecOps-specific)
organizational_operations:
  # STRATEGIC OPERATIONS (Planning & Governance)
  planning:
    - clarify_initiative      # Was: clarify_product_idea
    - define_goals            # Was: business_goals  
    - success_criteria
    - stakeholder_engagement
    - risk_assessment
    
  governance:
    - compliance_validation
    - policy_enforcement
    - audit_trail
    - evidence_collection
    - regulatory_reporting
    
  # EXECUTION OPERATIONS (Work Processing)
  design:
    - architecture_design
    - component_design
    - interface_specification
    - security_design
    - ux_design
    
  implementation:
    - coding_standards
    - peer_review
    - artifact_build
    - quality_gates
    
  validation:
    - functional_testing
    - security_testing
    - performance_testing
    - compliance_testing
    - acceptance_testing
    
  # DELIVERY OPERATIONS (Release & Deploy)
  release:
    - release_validation
    - change_management
    - deployment_orchestration
    - rollback_management
    
  # OPERATIONAL OPERATIONS (Run & Monitor)  
  operations:
    - health_monitoring
    - incident_response
    - capacity_management
    - disaster_recovery
    
  # IMPROVEMENT OPERATIONS (Learn & Improve)
  improvement:
    - retrospective
    - continuous_improvement
    - metrics_analysis
    - process_optimization
```

### Proposed Directory Structure (Streamlined)

```
src/main/resources/
├── config/
│   ├── organization.yaml                    # Org-level settings
│   └── departments/                         # Department definitions (keep as-is)
│
├── operations/                              # RENAMED from devsecops/
│   ├── cross-cutting/                       # 14 parameterized operators
│   │   ├── audit_log_operator.yaml
│   │   ├── progress_dashboard_operator.yaml
│   │   ├── observability_export_operator.yaml
│   │   ├── feedback_loop_operator.yaml
│   │   ├── evidence_collector_operator.yaml
│   │   ├── notification_operator.yaml
│   │   ├── success_criteria_tracker_operator.yaml
│   │   ├── risk_trend_analyzer_operator.yaml
│   │   ├── milestone_tracker_operator.yaml
│   │   ├── kpi_operator.yaml
│   │   ├── best_practice_enforcement_operator.yaml
│   │   ├── readiness_checker_operator.yaml
│   │   ├── go_nogo_operator.yaml
│   │   └── escalation_handler_operator.yaml
│   │
│   ├── domain/                              # Domain-specific operators
│   │   ├── planning/                        # 12 unique specs
│   │   ├── solution/                        # 8 unique specs
│   │   ├── design/                          # 10 unique specs
│   │   ├── implementation/                  # 8 unique specs
│   │   ├── build/                           # 10 unique specs
│   │   ├── testing/                         # 15 unique specs
│   │   ├── security/                        # 12 unique specs
│   │   ├── compliance/                      # 8 unique specs
│   │   ├── release/                         # 8 unique specs
│   │   ├── operations/                      # 15 unique specs
│   │   ├── monitoring/                      # 8 unique specs
│   │   └── improvement/                     # 5 unique specs
│   │
│   ├── workflows/                           # Workflow compositions
│   │   ├── planning_workflow.yaml           # Composes planning + cross-cutting
│   │   ├── delivery_workflow.yaml           # Build → Test → Release
│   │   └── operational_workflow.yaml        # Monitor → Incident → Recovery
│   │
│   └── mappings/                            # Keep existing mappings
│       ├── persona_registry.yaml
│       ├── phase_personas.yaml
│       └── operation_phase_mapping.yaml     # RENAMED from stage_phase_mapping
```

### Implementation Plan: Configuration Cleanup

| Task | Description | Effort | Priority |
|------|-------------|--------|----------|
| **1. Create Cross-Cutting Templates** | Extract 14 parameterized operator templates | 3 days | P0 |
| **2. Consolidate Domain Operators** | Deduplicate ~300 files → ~119 unique specs | 5 days | P0 |
| **3. Update Workflow Definitions** | Modify `specs2/stages/*.yaml` to reference templates | 3 days | P1 |
| **4. Create Operator Registry** | Build registry that instantiates cross-cutting operators per stage | 4 days | P1 |
| **5. Update Mappings** | Rename stage → operation, update references | 1 day | P2 |
| **6. Migration Script** | Script to migrate existing configs to new structure | 2 days | P2 |
| **7. Validation Tests** | Ensure all workflows still execute correctly | 2 days | P0 |

**Total Cleanup Effort:** ~20 engineering days

### Integration with Virtual-Org Framework

The streamlined operations become **default organization capabilities**:

```java
// Virtual-org provides the cross-cutting operator implementations
public class CrossCuttingOperatorFactory {
    
    public AuditLogOperator createAuditLogOperator(String stageId, OperatorConfig config) {
        return new AuditLogOperator(stageId, config);
    }
    
    public ProgressDashboardOperator createProgressDashboard(String stageId, OperatorConfig config) {
        return new ProgressDashboardOperator(stageId, config);
    }
    
    // ... 12 more cross-cutting operators
}

// Software-org (and any org) uses them via configuration
public class OrganizationOperationsLoader {
    
    public List<Operator> loadOperationsForStage(String stageId, StageConfig config) {
        List<Operator> operators = new ArrayList<>();
        
        // Always include cross-cutting operators for every stage
        operators.add(crossCuttingFactory.createAuditLogOperator(stageId, config));
        operators.add(crossCuttingFactory.createProgressDashboard(stageId, config));
        operators.add(crossCuttingFactory.createKpiOperator(stageId, config));
        // ...
        
        // Load domain-specific operators from config
        for (OperatorRef ref : config.getDomainOperators()) {
            operators.add(operatorRegistry.get(ref.getId()));
        }
        
        return operators;
    }
}
```

### Summary: Configuration Streamlining

| Metric | Before | After | Reduction |
|--------|--------|-------|-----------|
| Total YAML files | 420 | ~135 | **68%** |
| Cross-cutting agent files | ~180 | 14 | **92%** |
| Lines of YAML (estimated) | ~25,000 | ~8,000 | **68%** |
| Maintenance burden | High (change in 16 places) | Low (change in 1 template) | **Significant** |

---

## Agentic Event Processor (AEP) Cross-Product Dependencies

> **Purpose:** Define comprehensive integration requirements between virtual-org and AEP.
> AEP is the platform's **core event processing and pattern detection engine** that virtual-org
> MUST leverage for event-driven workflows, operator pipelines, and intelligent event routing.

### AEP Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                     AGENTIC EVENT PROCESSOR (AEP) ARCHITECTURE                      │
├─────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                     │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐  ┌───────────────┐ │
│  │  ORCHESTRATOR   │  │  AGENT-REGISTRY │  │  PATTERN-SYSTEM │  │   LEARNING    │ │
│  │                 │  │                 │  │                 │  │               │ │
│  │ • Pipelines     │  │ • Registration  │  │ • Operator SPI  │  │ • Recommender │ │
│  │ • Execution     │  │ • Discovery     │  │ • OperatorReg   │  │ • Synthesizer │ │
│  │ • Caching       │  │ • Health Check  │  │ • SEQ/AND/OR    │  │ • Correlation │ │
│  │ • Scheduling    │  │ • Execution     │  │ • Window/Filter │  │ • Mining      │ │
│  └────────┬────────┘  └────────┬────────┘  └────────┬────────┘  └───────┬───────┘ │
│           │                    │                    │                   │         │
│           └──────────┬─────────┴──────────┬─────────┴──────────┬────────┘         │
│                      │                    │                    │                  │
│                      ▼                    ▼                    ▼                  │
│  ┌───────────────────────────────────────────────────────────────────────────┐   │
│  │                       AGENT-RUNTIME LAYER                                  │   │
│  │                                                                            │   │
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  │   │
│  │  │  BaseAgent   │  │AgentLifecycle│  │EventOperator │  │OperatorResult│  │   │
│  │  │              │  │              │  │              │  │              │  │   │
│  │  │ • id/version │  │ • States     │  │ • process()  │  │ • success()  │  │   │
│  │  │ • handle()   │  │ • Listeners  │  │ • canProcess │  │ • failure()  │  │   │
│  │  │ • execute()  │  │ • Transitions│  │ • isStateful │  │ • events     │  │   │
│  │  │ • isHealthy()│  │ • Errors     │  │ • getName()  │  │              │  │   │
│  │  └──────────────┘  └──────────────┘  └──────────────┘  └──────────────┘  │   │
│  └───────────────────────────────────────────────────────────────────────────┘   │
│                                        │                                         │
│                                        ▼                                         │
│  ┌───────────────────────────────────────────────────────────────────────────┐   │
│  │                    EVENT-CLOUD OPERATORS (data-cloud)                      │   │
│  │                                                                            │   │
│  │  ┌─────────────────────────────────────────────────────────────────────┐  │   │
│  │  │  EventCloudTailOperator                                              │  │   │
│  │  │  • Real-time streaming from EventCloud                               │  │   │
│  │  │  • Automatic offset tracking                                         │  │   │
│  │  │  • Reconnection with backoff                                         │  │   │
│  │  │  • Partition-aware consumption                                       │  │   │
│  │  └─────────────────────────────────────────────────────────────────────┘  │   │
│  └───────────────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

### AEP Components Virtual-Org Must Leverage

#### 1. Agent Runtime Layer

| Component | Location | Purpose | Virtual-Org Integration |
|-----------|----------|---------|------------------------|
| `BaseAgent` | `aep/libs/agent-runtime` | Base agent with lifecycle | Virtual-org agents SHOULD extend for AEP compatibility |
| `AgentLifecycle` | `aep/libs/agent-runtime` | State machine (CREATED→RUNNING→STOPPED) | Use for agent state management |
| `EventOperator` | `aep/libs/agent-runtime` | Stateless/stateful event processor | Implement for workflow steps |
| `OperatorResult` | `aep/libs/agent-runtime` | Success/failure with output events | Use for workflow step results |
| `AgentStatus` | `aep/libs/agent-runtime` | Agent health states | Report to virtual-org dashboard |

**Integration Pattern:**
```java
// Virtual-Org organizational agents can extend AEP BaseAgent
public abstract class BaseOrganizationalAgent extends BaseAgent {
    // Virtual-org specific: department context, memory, tools
    private final AgentContext organizationalContext;
    
    @Override
    public List<Event> handle(Event event, AgentExecutionContext context) {
        // Delegate to virtual-org workflow engine
        return processWithOrganizationalContext(event);
    }
}
```

#### 2. Agent Registry Service

| Feature | API | Virtual-Org Use Case |
|---------|-----|---------------------|
| **Registration** | `register(AgentManifestProto)` | Register organizational agents |
| **Discovery** | `findByEventType(eventTypeId)` | Find agents for workflow routing |
| **Capabilities** | `findByCapabilities(capabilities)` | Match agents to tasks |
| **Execution** | `executeAgent(agentId, event, context)` | Invoke agents in pipelines |
| **Batch** | `executeBatch(agentId, events, context)` | Bulk task processing |

**Integration Requirement:**
- Virtual-org agents MUST be registered with AEP Agent Registry
- Use AEP discovery for dynamic agent routing in workflows
- Leverage AEP execution for cross-product agent invocation

#### 3. Pattern System (Operator SPI)

| Operator | Type | Virtual-Org Use Case |
|----------|------|---------------------|
| `SeqOperator` | Pattern | Sequence detection in workflows |
| `AndOperator` | Pattern | Multi-condition workflow triggers |
| `OrOperator` | Pattern | Alternative workflow paths |
| `NotOperator` | Pattern | Absence detection (SLA violations) |
| `WindowOperator` | Aggregation | Time-windowed KPI calculation |
| `FilterOperator` | Stream | Event filtering for routing |
| `WithinOperator` | Temporal | Time-bound pattern matching |
| `RepeatOperator` | Quantifier | Recurring event patterns |

**OperatorRegistry Integration:**
```java
// Virtual-org workflow steps can be registered as AEP operators
public class WorkflowStepOperator implements Operator {
    private final WorkflowStep step;
    
    @Override
    public String getType() { return "WORKFLOW_STEP"; }
    
    @Override
    public OperatorMetadata getMetadata() {
        return new OperatorMetadata(
            "WORKFLOW_STEP",
            OperatorCategory.CUSTOM,
            1, 1,  // min/max operands
            "Virtual-org workflow step as AEP operator"
        );
    }
}
```

#### 4. Event Cloud Integration

| Feature | Component | Virtual-Org Requirement |
|---------|-----------|------------------------|
| **Real-time Streaming** | `EventCloudTailOperator` | Subscribe to organization events |
| **History Replay** | EventCloud API | Replay workflows for debugging |
| **Offset Tracking** | `OffsetTracker` | Checkpoint workflow progress |
| **Reconnection** | `ReconnectionPolicy` | Handle network failures gracefully |
| **Partitioning** | `PartitionAssignmentStrategy` | Scale event consumption |

#### 5. Learning Operators

| Operator | Purpose | Virtual-Org Use Case |
|----------|---------|---------------------|
| `PatternRecommender` | Suggest patterns from event data | Recommend workflow optimizations |
| `PatternSynthesizer` | Generate patterns from sequences | Auto-create workflow triggers |
| `FrequentSequenceMiner` | Discover common event sequences | Identify workflow bottlenecks |
| `TemporalCorrelationAnalyzer` | Find time-based correlations | Optimize task scheduling |

### AEP Integration Requirements for Virtual-Org

#### Required SPIs

| SPI | Owner | Purpose | Priority |
|-----|-------|---------|----------|
| `OrganizationalAgentProvider` | virtual-org | Register org agents with AEP | P0 |
| `WorkflowOperatorProvider` | virtual-org | Expose workflow steps as operators | P1 |
| `OrganizationEventTypeProvider` | virtual-org | Register org event schemas | P0 |
| `PatternLearningConsumer` | virtual-org | Receive learning recommendations | P2 |

#### Required AEP Enhancements

| Enhancement | Description | Owner | Effort |
|-------------|-------------|-------|--------|
| **Multi-Tenant Agent Registry** | Per-org agent namespaces | AEP | 3 days |
| **Workflow-Aware Orchestrator** | Support workflow pipelines | AEP | 5 days |
| **Organization Event Types** | Standard org event schemas | AEP/virtual-org | 2 days |
| **Agent Group Execution** | Execute agent teams | AEP | 4 days |
| **Pipeline Templating** | Parameterized pipeline defs | AEP | 3 days |

### Current Integration Analysis: Software-Org ↔ AEP

#### Existing Integration Points

| Component | Location | Integration Method | Status |
|-----------|----------|-------------------|--------|
| `SoftwareOrgAepInitializer` | `software-org/bootstrap` | Coordinates AEP startup | ✅ Exists |
| `AllDepartmentPipelinesRegistrar` | `software-org/bootstrap` | Registers 30 pipelines | ✅ Exists |
| `AepEventPublisherAdapter` | `virtual-org/framework` | Adapts to AEP publisher | ✅ Exists |
| `OrganizationEventPublisher` | `virtual-org/framework` | Publishes org events | ✅ Exists |

#### Gap: These Should Be in Virtual-Org

| Current Location | Should Be | Reason |
|------------------|-----------|--------|
| `SoftwareOrgAepInitializer` | `virtual-org/framework` | Generic org→AEP initialization |
| `AllDepartmentPipelinesRegistrar` | `virtual-org/framework` | Generic pipeline registration |
| 10 department `*PipelinesRegistrar` | Config-driven | Should be auto-generated from YAML |

#### Target Integration Architecture

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                     VIRTUAL-ORG ↔ AEP INTEGRATION ARCHITECTURE                      │
├─────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                     │
│  SOFTWARE-ORG (Product Layer)                                                       │
│  ┌───────────────────────────────────────────────────────────────────────────────┐ │
│  │  organization.yaml  →  No Custom Java for AEP Integration                      │ │
│  │  departments/*.yaml →  Pipelines auto-discovered from config                   │ │
│  │  agents/*.yaml      →  Agents auto-registered with AEP                         │ │
│  │  workflows/*.yaml   →  Workflows converted to AEP operators                    │ │
│  └───────────────────────────────────────────────────────────────────────────────┘ │
│                                        │                                           │
│                                        ▼                                           │
│  VIRTUAL-ORG (Framework Layer)                                                      │
│  ┌───────────────────────────────────────────────────────────────────────────────┐ │
│  │  ┌─────────────────────┐  ┌─────────────────────┐  ┌────────────────────────┐ │ │
│  │  │OrganizationLifecycle│  │ PipelineRegistry    │  │ AepConnectorSPI        │ │ │
│  │  │Manager              │  │                     │  │                        │ │ │
│  │  │                     │  │ • Auto-discover from│  │ • AgentRegistration    │ │ │
│  │  │ • initializeOrg()   │  │   config            │  │ • OperatorRegistration │ │ │
│  │  │ • startOrg()        │  │ • Register with AEP │  │ • EventTypeRegistration│ │ │
│  │  │ • shutdownOrg()     │  │ • Per-tenant        │  │ • PipelineSubmission   │ │ │
│  │  └─────────┬───────────┘  └─────────┬───────────┘  └────────────┬───────────┘ │ │
│  │            │                        │                           │             │ │
│  │            └────────────┬───────────┴───────────────────────────┘             │ │
│  │                         │                                                     │ │
│  │                         ▼                                                     │ │
│  │  ┌───────────────────────────────────────────────────────────────────────┐   │ │
│  │  │                    EventBusConnector SPI                               │   │ │
│  │  │                                                                        │   │ │
│  │  │  interface EventBusConnector {                                         │   │ │
│  │  │    Promise<Void> publishEvent(OrganizationEvent event);                │   │ │
│  │  │    Promise<Subscription> subscribe(String pattern, Handler handler);  │   │ │
│  │  │    Promise<Void> registerAgentWithAep(AgentManifest manifest);        │   │ │
│  │  │    Promise<Void> registerPipelineWithAep(PipelineSpec pipeline);      │   │ │
│  │  │  }                                                                     │   │ │
│  │  └───────────────────────────────────────────────────────────────────────┘   │ │
│  └───────────────────────────────────────────────────────────────────────────────┘ │
│                                        │                                           │
│                                        ▼                                           │
│  AEP (Platform Layer)                                                               │
│  ┌───────────────────────────────────────────────────────────────────────────────┐ │
│  │  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────────────────────┐│ │
│  │  │Agent Registry   │  │ Orchestrator    │  │ Pattern System                  ││ │
│  │  │                 │  │                 │  │                                 ││ │
│  │  │• Org agents     │  │• Org pipelines  │  │• Workflow operators             ││ │
│  │  │• Capabilities   │  │• Caching        │  │• Pattern detection              ││ │
│  │  │• Health checks  │  │• Scheduling     │  │• Learning recommendations       ││ │
│  │  └─────────────────┘  └─────────────────┘  └─────────────────────────────────┘│ │
│  └───────────────────────────────────────────────────────────────────────────────┘ │
│                                        │                                           │
│                                        ▼                                           │
│  DATA-CLOUD (Storage Layer)                                                         │
│  ┌───────────────────────────────────────────────────────────────────────────────┐ │
│  │  EventCloud  │  StateStore  │  Vector Search  │  Checkpointing                │ │
│  └───────────────────────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

### AEP Integration Roadmap

#### Phase 1: Foundation (Weeks 1-4)
- [ ] Create `EventBusConnector` SPI in virtual-org
- [ ] Implement `AepEventBusConnector` as default implementation
- [ ] Move `AepEventPublisherAdapter` to use new SPI
- [ ] Register organization event types with AEP catalog

#### Phase 2: Agent Integration (Weeks 5-8)
- [ ] Create `OrganizationalAgentProvider` SPI
- [ ] Auto-register agents from YAML config with AEP Agent Registry
- [ ] Implement agent discovery using AEP capabilities API
- [ ] Enable cross-org agent invocation via AEP

#### Phase 3: Pipeline Integration (Weeks 9-12)
- [ ] Create `PipelineRegistry` in virtual-org
- [ ] Auto-generate pipelines from workflow YAML
- [ ] Register pipelines with AEP Orchestrator
- [ ] Support pipeline templating for departments

#### Phase 4: Operator Integration (Weeks 13-16)
- [ ] Create `WorkflowOperatorProvider` SPI
- [ ] Expose workflow steps as AEP operators
- [ ] Register operators with AEP OperatorRegistry
- [ ] Enable pattern detection on workflow events

#### Phase 5: Learning Integration (Weeks 17-20)
- [ ] Create `PatternLearningConsumer` SPI
- [ ] Receive workflow optimization recommendations from AEP
- [ ] Auto-suggest workflow trigger patterns
- [ ] Feed workflow metrics to AEP learning operators

### Effort Summary: AEP Integration

| Phase | Deliverables | Effort (Days) |
|-------|-------------|---------------|
| Phase 1: Foundation | EventBusConnector SPI, Event Types | 8 |
| Phase 2: Agent Integration | AgentProvider SPI, Auto-registration | 12 |
| Phase 3: Pipeline Integration | PipelineRegistry, Auto-generation | 15 |
| Phase 4: Operator Integration | WorkflowOperatorProvider, Registration | 10 |
| Phase 5: Learning Integration | PatternLearningConsumer, Recommendations | 10 |
| **Total** | | **55 days (~11 weeks)** |

---

## Enhancement Phases

### Phase 1: Memory & Knowledge Foundation (Weeks 1-6)

**Objective:** Enable persistent, intelligent agent memory with knowledge retrieval

> **CRITICAL**: Virtual-Org MUST leverage **data-cloud** as the unified data layer.
> Do NOT create duplicate storage implementations.

#### 1.1 Persistent Memory System (via data-cloud)

**Current State:** In-memory storage with `AgentMemory` interface  
**Target State:** Multi-tier persistent memory backed by **data-cloud StateStore SPI**

```
┌─────────────────────────────────────────────────────────────────────────────┐
│              VIRTUAL-ORG MEMORY ARCHITECTURE (via data-cloud)               │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  VIRTUAL-ORG LAYER (Memory Abstraction)                                     │
│  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐         │
│  │   WORKING MEM    │  │   EPISODIC MEM   │  │   SEMANTIC MEM   │         │
│  │   (Hot Cache)    │  │   (Conversation) │  │   (Knowledge)    │         │
│  │                  │  │                  │  │                  │         │
│  │  - Current task  │  │  - Recent chats  │  │  - Org knowledge │         │
│  │  - Active ctx    │  │  - Past tasks    │  │  - Domain facts  │         │
│  │  - Temp state    │  │  - Decisions     │  │  - Best practices│         │
│  └────────┬─────────┘  └────────┬─────────┘  └────────┬─────────┘         │
│           │                     │                     │                    │
│           │  AgentMemoryStore   │                     │                    │
│           │  (adapts to data-cloud StateStore)        │                    │
│           ▼                     ▼                     ▼                    │
├─────────────────────────────────────────────────────────────────────────────┤
│  DATA-CLOUD LAYER (Existing Infrastructure - DO NOT DUPLICATE)              │
│                                                                             │
│  ┌──────────────────────────────────────────────────────────────────┐     │
│  │             data-cloud StateStore SPI (REUSE)                     │     │
│  │                                                                   │     │
│  │   ┌─────────────┐    ┌─────────────┐    ┌─────────────┐         │     │
│  │   │  RocksDB    │    │   Redis/    │    │  EventCloud │         │     │
│  │   │StateStore   │    │  Dragonfly  │    │  (Events)   │         │     │
│  │   │  (Local)    │    │StateStore   │    │             │         │     │
│  │   └─────────────┘    └─────────────┘    └─────────────┘         │     │
│  │                                                                   │     │
│  │   HybridStateStore (local + central + EventCloud reconciliation) │     │
│  │                                                                   │     │
│  └──────────────────────────────────────────────────────────────────┘     │
│                                                                             │
│  ┌──────────────────────────────────────────────────────────────────┐     │
│  │             data-cloud StoragePlugin SPI (for long-term)          │     │
│  │                                                                   │     │
│  │   L0 (Hot)  → Redis/Memory     → Real-time reads (<10ms)         │     │
│  │   L1 (Warm) → PostgreSQL       → Recent history (days)           │     │
│  │   L2 (Cool) → Iceberg/Parquet  → Analytics (months)              │     │
│  │   L4 (Cold) → S3/Glacier       → Archive (years)                 │     │
│  │                                                                   │     │
│  └──────────────────────────────────────────────────────────────────┘     │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

**Integration Approach:**

| data-cloud Component | Virtual-Org Usage |
|---------------------|-------------------|
| `StateStore<K, V>` SPI | Agent working/episodic memory |
| `HybridStateStore` | Local cache + cross-instance sync |
| `EventCloudHybridStateStore` | State reconciliation via events |
| `EventCloud` | Memory change events, audit trail |
| `StoragePlugin` (PostgreSQL/Iceberg) | Long-term conversation history |

**Implementation Tasks:**

| Task | Priority | Effort | Dependencies |
|------|----------|--------|--------------|
| Create `AgentMemoryStore` adapter over data-cloud `StateStore` | P0 | 2d | data-cloud |
| Configure `HybridStateStore` for agent memory | P0 | 1d | AgentMemoryStore |
| Emit `agent.memory.updated` events to EventCloud | P1 | 2d | EventCloud |
| Add memory TTL via data-cloud config | P1 | 1d | StateStore config |
| Create memory schema in EventCloud | P1 | 1d | EventCloud |

**New Files to Create:**

```
libs/java/framework/src/main/java/com/ghatana/virtualorg/framework/memory/
├── datacloud/
│   ├── AgentMemoryStateStore.java       # Adapter over data-cloud StateStore
│   ├── DataCloudMemoryConfig.java       # Configuration for data-cloud integration
│   └── MemoryEventPublisher.java        # Emits memory events to EventCloud
├── rag/
│   ├── EmbeddingService.java            # Vector embedding generation
│   ├── DataCloudVectorAdapter.java      # Uses data-cloud for vector storage
│   └── SemanticRetriever.java           # RAG query via data-cloud
└── knowledge/
    ├── KnowledgeBase.java               # Organization knowledge store (via data-cloud Entity)
    ├── DocumentIngester.java            # Document processing
    └── KnowledgeEventStore.java         # Stores knowledge as EventCloud events
```

**data-cloud Configuration (virtual-org memory):**

```yaml
# config/data-cloud/virtual-org-memory.yaml
stateStore:
  type: hybrid
  local:
    type: rocksdb
    path: /data/virtual-org/agent-memory
    options:
      writeBufferSize: 64MB
      maxOpenFiles: 1000
  central:
    type: dragonfly  # or redis/kvrocks
    host: ${DRAGONFLY_HOST:localhost}
    port: 6379
    keyPrefix: "virtualorg:memory:"
  sync:
    strategy: write-through
    reconciliationInterval: 5s
    eventCloudEnabled: true
    eventStream: "virtualorg.agent.memory"
```

#### 1.2 RAG Integration (via data-cloud)

**Current State:** No knowledge base integration  
**Target State:** Full RAG pipeline using **data-cloud Entity + EventCloud + ML-Intelligence**

> **REUSE**: data-cloud already has `ml-intelligence` plugin with `QueryPatternClassifier`, 
> `AnomalyDetector`, and `MaterializedViewRecommender`. Extend for RAG.

```
┌─────────────────────────────────────────────────────────────────────────────┐
│              RAG ARCHITECTURE (via data-cloud)                               │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  VIRTUAL-ORG LAYER                                                          │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  Agent Knowledge Query                                               │   │
│  │  agent.queryKnowledge("How to handle security incidents?")          │   │
│  └────────────────────────────────┬────────────────────────────────────┘   │
│                                   │                                         │
│                                   ▼                                         │
│  DATA-CLOUD LAYER                                                           │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  data-cloud Entity Subsystem                                         │   │
│  │  • KnowledgeDocument (Entity with embeddings)                       │   │
│  │  • MetaCollection for org-specific knowledge schemas                │   │
│  │  • Semantic search via data-cloud search/ module                    │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                   │                                         │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  data-cloud ml-intelligence Plugin (EXTEND)                          │   │
│  │  • EmbeddingService (NEW: add to ml-intelligence)                   │   │
│  │  • VectorSearchPlugin (NEW: add to ml-intelligence)                 │   │
│  │  • SemanticRetriever (NEW: add to ml-intelligence)                  │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                   │                                         │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  data-cloud StoragePlugin (Existing)                                 │   │
│  │  • PostgreSQL with pgvector extension (vector storage)              │   │
│  │  • Iceberg for large document storage                               │   │
│  │  • Redis for embedding cache                                        │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

```yaml
# Example agent configuration with RAG (via data-cloud)
apiVersion: virtualorg.ghatana.com/v1
kind: Agent
metadata:
  name: senior-engineer-agent
spec:
  ai:
    enabled: true
    model: gpt-4-turbo
    
  # Knowledge configuration (backed by data-cloud)
  knowledge:
    # data-cloud Entity collections to query
    sources:
      - type: datacloud-entity
        collection: company-handbook
        tenant: ${TENANT_ID}
      - type: datacloud-entity
        collection: engineering-wiki
        tenant: ${TENANT_ID}
      - type: eventcloud-stream
        stream: org.decisions
        lookbackDays: 90
    
    # Retrieval settings (uses data-cloud ml-intelligence)
    retrieval:
      strategy: semantic  # semantic | keyword | hybrid
      provider: datacloud-ml-intelligence
      topK: 5
      minScore: 0.7
      contextWindow: 4000  # tokens
      embeddingModel: text-embedding-3-small
      cacheEnabled: true
      cacheTtl: 3600  # seconds
```

**Implementation Tasks:**

| Task | Priority | Effort | Dependencies |
|------|----------|--------|--------------|
| Add `EmbeddingService` to data-cloud ml-intelligence plugin | P0 | 3d | data-cloud |
| Add `VectorSearchPlugin` to data-cloud (pgvector support) | P0 | 4d | data-cloud PostgreSQL plugin |
| Create `KnowledgeDocumentEntity` schema in data-cloud | P1 | 2d | data-cloud Entity |
| Build `SemanticRetriever` in data-cloud ml-intelligence | P0 | 3d | EmbeddingService |
| Create Virtual-Org `AgentKnowledgeClient` (thin adapter) | P1 | 2d | data-cloud SemanticRetriever |
| Add document ingestion pipeline to data-cloud | P1 | 4d | data-cloud |
| Update agent config schema for knowledge | P1 | 1d | SemanticRetriever |

**data-cloud Extensions Required:**

```
products/data-cloud/plugins/analytics/ml-intelligence/src/main/java/
├── embedding/
│   ├── EmbeddingService.java           # Vector embedding generation
│   ├── OpenAIEmbeddingProvider.java    # OpenAI embeddings
│   └── EmbeddingCache.java             # Redis-backed cache
├── vectorsearch/
│   ├── VectorSearchPlugin.java         # SPI for vector search
│   ├── PgVectorSearchPlugin.java       # PostgreSQL pgvector
│   └── VectorSearchResult.java         # Search results
└── rag/
    ├── SemanticRetriever.java          # RAG query interface
    ├── DocumentChunker.java            # Text chunking strategies
    └── ContextBuilder.java             # LLM context assembly
```

---

### Phase 2: Observability & Monitoring (Weeks 7-10)

**Objective:** Full visibility into organization operations

> **CRITICAL**: Emit all observability events to **data-cloud EventCloud**.
> Use EventCloud as the single source of truth for all agent/workflow telemetry.

#### 2.1 Agent Tracing (via data-cloud EventCloud)

**Current State:** Basic logging  
**Target State:** OpenTelemetry distributed tracing with events stored in **EventCloud**

```
┌─────────────────────────────────────────────────────────────────────────────┐
│              OBSERVABILITY ARCHITECTURE (via data-cloud)                     │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  VIRTUAL-ORG LAYER (Trace Generation)                                       │
│  ┌──────────────────────────────────────────────────────────────────┐      │
│  │                     AGENT EXECUTION FLOW                          │      │
│  │                                                                   │      │
│  │  [Agent: CTO]                                                     │      │
│  │     │                                                             │      │
│  │     ├─ [Span: Receive Task] ────────────────────────────────────  │      │
│  │     │    └─ task: "Review architecture proposal"                  │      │
│  │     │                                                             │      │
│  │     ├─ [Span: LLM Call] ────────────────────────────────────────  │      │
│  │     │    ├─ model: gpt-4-turbo                                    │      │
│  │     │    ├─ tokens: 1,234 input / 567 output                      │      │
│  │     │    ├─ latency: 2.3s                                         │      │
│  │     │    └─ cost: $0.023                                          │      │
│  │     │                                                             │      │
│  │     ├─ [Span: Tool Call: github_review] ────────────────────────  │      │
│  │     │    ├─ repo: org/main-repo                                   │      │
│  │     │    └─ result: approved                                      │      │
│  │     │                                                             │      │
│  │     └─ [Span: Delegate to Engineer] ────────────────────────────  │      │
│  │          └─ [Agent: Senior Engineer]                              │      │
│  └──────────────────────────────────────────────────────────────────┘      │
│                                   │                                         │
│                                   │ Emit Events                             │
│                                   ▼                                         │
├─────────────────────────────────────────────────────────────────────────────┤
│  DATA-CLOUD EVENTCLOUD (Telemetry Storage)                                  │
│                                                                             │
│  ┌──────────────────────────────────────────────────────────────────┐      │
│  │  Event Streams (append-only, immutable)                           │      │
│  │                                                                   │      │
│  │  virtualorg.agent.execution    → Agent task start/complete        │      │
│  │  virtualorg.agent.llm          → LLM calls, tokens, cost          │      │
│  │  virtualorg.agent.tool         → Tool invocations                 │      │
│  │  virtualorg.workflow.execution → Workflow step transitions        │      │
│  │  virtualorg.approval           → HITL approval events             │      │
│  │  virtualorg.delegation         → Agent-to-agent delegation        │      │
│  │                                                                   │      │
│  └──────────────────────────────────────────────────────────────────┘      │
│                                   │                                         │
│  ┌──────────────────────────────────────────────────────────────────┐      │
│  │  Storage Tiers (via data-cloud StoragePlugin)                     │      │
│  │                                                                   │      │
│  │  L0 Hot  → Redis      → Real-time dashboards (<10ms)             │      │
│  │  L1 Warm → PostgreSQL → Recent queries (days)                    │      │
│  │  L2 Cool → Iceberg    → Analytics, Trino queries (months)        │      │
│  │  L4 Cold → S3         → Compliance archive (years)               │      │
│  └──────────────────────────────────────────────────────────────────┘      │
│                                                                             │
│  ┌──────────────────────────────────────────────────────────────────┐      │
│  │  Query Layer                                                      │      │
│  │                                                                   │      │
│  │   ┌─────────┐   ┌─────────┐   ┌─────────┐   ┌─────────┐        │      │
│  │   │  Trino  │   │ Grafana │   │ Custom  │   │ OTel    │        │      │
│  │   │ (SQL)   │   │ (Viz)   │   │   UI    │   │ Export  │        │      │
│  │   └─────────┘   └─────────┘   └─────────┘   └─────────┘        │      │
│  │                                                                   │      │
│  └──────────────────────────────────────────────────────────────────┘      │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

**EventCloud Event Schemas:**

```yaml
# Event type definitions for virtual-org telemetry
---
apiVersion: datacloud.ghatana.com/v1
kind: EventType
metadata:
  name: virtualorg.agent.llm
  namespace: virtualorg
spec:
  schema:
    type: object
    properties:
      agentId: { type: string }
      agentRole: { type: string }
      departmentId: { type: string }
      organizationId: { type: string }
      traceId: { type: string }
      spanId: { type: string }
      model: { type: string }
      inputTokens: { type: integer }
      outputTokens: { type: integer }
      latencyMs: { type: integer }
      cost: { type: number }
      prompt: { type: string }
      response: { type: string }
      success: { type: boolean }
      errorMessage: { type: string }
    required: [agentId, model, inputTokens, outputTokens, latencyMs, cost]
  retention:
    hot: 24h      # Redis
    warm: 30d     # PostgreSQL
    cool: 365d    # Iceberg
    cold: 7y      # S3 (compliance)
```

**New Files:**

```
libs/java/framework/src/main/java/com/ghatana/virtualorg/framework/observability/
├── eventcloud/
│   ├── TelemetryEventPublisher.java    # Publishes to data-cloud EventCloud
│   ├── AgentTelemetryEvent.java        # Event models
│   ├── LLMCallEvent.java               # LLM invocation event
│   ├── ToolCallEvent.java              # Tool execution event
│   └── WorkflowStepEvent.java          # Workflow transition event
├── tracing/
│   ├── AgentTracer.java                # Agent-specific tracing (emits to EventCloud)
│   ├── WorkflowTracer.java             # Workflow execution tracing
│   ├── LLMCallTracer.java              # LLM invocation tracing
│   └── OTelExporter.java               # Export EventCloud → OpenTelemetry
├── metrics/
│   ├── AgentMetrics.java               # Aggregates from EventCloud
│   ├── CostMetrics.java                # Token/cost tracking
│   └── KpiCollector.java               # Department KPI from EventCloud
└── dashboard/
    ├── EventCloudDashboardQuery.java   # Trino/SQL queries on EventCloud
    └── WebSocketUpdater.java           # Real-time via EventCloud subscription
```

#### 2.2 Cost Tracking (via data-cloud EventCloud)

**Implementation:**

All cost events are stored in **EventCloud** and queried via **Trino connector**.

```java
/**
 * Tracks LLM API costs per agent, department, and organization.
 * Stores all cost events in data-cloud EventCloud.
 */
public class EventCloudCostTracker implements CostTracker {
    
    private final EventCloud eventCloud;
    private final TrinoClient trinoClient; // data-cloud Trino connector
    
    /**
     * Record an LLM invocation cost by appending to EventCloud
     */
    @Override
    public Promise<Void> recordLLMCost(LLMCostEvent event) {
        EventRecord record = EventRecord.builder()
            .tenantId(TenantId.of(event.getOrganizationId()))
            .typeRef(EventTypeRef.of("virtualorg.agent.llm", "1.0.0"))
            .streamName("virtualorg.costs")
            .payload(JsonCodec.toJson(event))
            .metadata(Map.of(
                "agentId", event.getAgentId(),
                "departmentId", event.getDepartmentId(),
                "model", event.getModel()
            ))
            .build();
        
        return eventCloud.append(record).map(offset -> null);
    }
    
    /**
     * Query costs via data-cloud Trino connector (SQL over EventCloud)
     */
    @Override
    public Promise<CostSummary> getAgentCost(AgentId agentId, TimeRange range) {
        String sql = """
            SELECT 
                SUM(CAST(json_extract_scalar(payload, '$.cost') AS DOUBLE)) as total_cost,
                SUM(CAST(json_extract_scalar(payload, '$.inputTokens') AS BIGINT)) as input_tokens,
                SUM(CAST(json_extract_scalar(payload, '$.outputTokens') AS BIGINT)) as output_tokens,
                COUNT(*) as invocation_count
            FROM eventcloud.virtualorg.agent_llm
            WHERE metadata['agentId'] = ?
              AND occurrence_time BETWEEN ? AND ?
            """;
        
        return trinoClient.query(sql, agentId.value(), range.start(), range.end())
            .map(this::toCostSummary);
    }
    
    /**
     * Get cost breakdown by department (aggregated from EventCloud)
     */
    @Override
    public Promise<Map<DepartmentId, CostSummary>> getDepartmentCosts(TimeRange range) {
        String sql = """
            SELECT 
                metadata['departmentId'] as department_id,
                SUM(CAST(json_extract_scalar(payload, '$.cost') AS DOUBLE)) as total_cost,
                COUNT(*) as invocation_count
            FROM eventcloud.virtualorg.agent_llm
            WHERE occurrence_time BETWEEN ? AND ?
            GROUP BY metadata['departmentId']
            """;
        
        return trinoClient.query(sql, range.start(), range.end())
            .map(this::toDepartmentCostMap);
    }
}

@Value
public class LLMCostEvent {
    String agentId;
    String departmentId;
    String organizationId;
    String model;
    int inputTokens;
    int outputTokens;
    BigDecimal cost;
    Instant timestamp;
}
```

**data-cloud Integration Benefits:**
- **Single storage** - All cost data in EventCloud
- **Multi-tier retention** - Hot (Redis) → Warm (PostgreSQL) → Cool (Iceberg)
- **SQL analytics** - Trino connector for complex cost analysis
- **Real-time dashboards** - EventCloud subscriptions for live cost tracking
- **Compliance** - S3 archive for long-term cost audit trails

---

### Phase 3: Simulation & Replay (Weeks 11-14)

**Objective:** Enable testing of organizational changes without production impact

> **CRITICAL**: Use **EventCloud** as the replay/checkpoint source of truth.
> All workflow state is event-sourced - replay by replaying events.

#### 3.1 Simulation Mode (via data-cloud EventCloud)

**Architecture:**
- All agent/workflow events are in **EventCloud** (append-only, immutable)
- Simulation creates **isolated EventCloud namespace** per simulation run
- Replay uses **EventCloud subscription** from a specific offset/time

```
┌─────────────────────────────────────────────────────────────────────────────┐
│              SIMULATION ARCHITECTURE (via data-cloud)                        │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  SIMULATION CONTROLLER                                               │   │
│  │                                                                      │   │
│  │  • Creates isolated EventCloud namespace: simulation-{runId}        │   │
│  │  • Configures mock AI providers                                     │   │
│  │  • Injects scenario events at specified times                       │   │
│  │  • Captures all outputs back to EventCloud                          │   │
│  └──────────────────────────────────┬──────────────────────────────────┘   │
│                                     │                                       │
│                                     ▼                                       │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  DATA-CLOUD EVENTCLOUD (Simulation Namespace)                        │   │
│  │                                                                      │   │
│  │  namespace: simulation-{runId}                                      │   │
│  │                                                                      │   │
│  │  Streams:                                                           │   │
│  │  ├── virtualorg.simulation.input    → Scenario/trigger events       │   │
│  │  ├── virtualorg.agent.execution     → Agent activity (isolated)     │   │
│  │  ├── virtualorg.workflow.execution  → Workflow steps (isolated)     │   │
│  │  └── virtualorg.simulation.output   → Results/assertions            │   │
│  │                                                                      │   │
│  │  Isolation: No writes to production namespace                       │   │
│  │  Retention: Auto-delete after simulation TTL                        │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

```yaml
# Example simulation configuration
apiVersion: virtualorg.ghatana.com/v1
kind: SimulationRun
metadata:
  name: new-qa-workflow-test
spec:
  organization: software-org
  
  # EventCloud isolation
  eventCloud:
    namespace: simulation-${RUN_ID}
    isolation: full  # No production writes
    retention: 24h   # Auto-cleanup
  
  # Mock AI responses
  aiConfig:
    mockMode: true
    mockProvider: deterministic  # deterministic | random | recorded
    mockResponses:
      - agentPattern: "*-engineer-*"
        prompt: "*code review*"
        response: "Approved with minor comments..."
  
  # Scenario injection (events into EventCloud)
  scenarios:
    - name: high-load-incident
      trigger: 
        time: "+5m"  # 5 minutes into simulation
      events:
        - type: incident.created
          stream: virtualorg.simulation.input
          data:
            severity: critical
            service: payment-service
  
  # Duration and limits
  limits:
    maxDuration: 30m
    maxWorkflowRuns: 100
    maxLLMCalls: 500
  
  # Output (all captured in EventCloud)
  output:
    captureAll: true
    exportFormat: json
    exportStream: virtualorg.simulation.output
```

#### 3.2 Workflow Replay (via EventCloud)

Since all workflow events are stored in **EventCloud**, replay is simply:
1. Query events from a specific time/offset
2. Re-process events through workflow engine
3. Compare outputs

```java
/**
 * Enables replaying workflows from EventCloud checkpoints.
 * Uses EventCloud as the source-of-truth for all state transitions.
 */
public class EventCloudWorkflowReplayEngine implements WorkflowReplayEngine {
    
    private final EventCloud eventCloud;
    private final WorkflowEngine workflowEngine;
    
    /**
     * Create checkpoint = record the EventCloud offset
     */
    @Override
    public Promise<CheckpointId> createCheckpoint(WorkflowInstanceId instanceId) {
        // Get current offset for the workflow's event stream
        return eventCloud.getLatestOffset(
            TenantId.current(),
            "virtualorg.workflow." + instanceId.value()
        ).map(offset -> new CheckpointId(instanceId, offset));
    }
    
    /**
     * Replay workflow by replaying events from checkpoint offset
     */
    @Override
    public Promise<WorkflowReplayResult> replayFromCheckpoint(
        CheckpointId checkpointId,
        ReplayConfig config
    ) {
        // Create isolated replay namespace
        String replayNamespace = "replay-" + UUID.randomUUID();
        
        // Subscribe to events from checkpoint offset
        EventStream events = eventCloud.subscribe(
            TenantId.current(),
            Selection.byStream("virtualorg.workflow." + checkpointId.instanceId()),
            StartingPositions.fromOffset(checkpointId.offset())
        );
        
        // Re-process each event through workflow engine
        return events
            .map(event -> workflowEngine.processReplayEvent(event, config))
            .collectList()
            .map(results -> new WorkflowReplayResult(results, replayNamespace));
    }
    
    /**
     * Compare executions by diffing event streams in EventCloud
     */
    @Override
    public Promise<WorkflowDiff> compareExecutions(
        WorkflowInstanceId original,
        WorkflowInstanceId replayed
    ) {
        // Query both event streams via Trino
        String sql = """
            SELECT 
                o.event_id as original_event,
                r.event_id as replay_event,
                o.payload as original_payload,
                r.payload as replay_payload
            FROM eventcloud.virtualorg.workflow_original o
            FULL OUTER JOIN eventcloud.virtualorg.workflow_replay r
                ON o.step_name = r.step_name
            WHERE o.workflow_instance = ? OR r.workflow_instance = ?
            ORDER BY COALESCE(o.occurrence_time, r.occurrence_time)
            """;
        
        return trinoClient.query(sql, original.value(), replayed.value())
            .map(this::buildDiff);
    }
}

@Value
public class ReplayConfig {
    boolean mockAI;              // Use mock AI responses
    boolean preserveTimestamps;  // Keep original timestamps
    String replayNamespace;      // EventCloud namespace for replay
    Map<String, Object> overrides;  // Override specific values
}
```

**data-cloud Integration Benefits:**
- **No separate checkpoint storage** - EventCloud IS the checkpoint
- **Event-sourced replay** - Deterministic replay by re-processing events
- **Isolated namespaces** - Safe simulation without production impact
- **Diff via Trino** - SQL-based comparison of event streams
- **Auto-cleanup** - TTL-based namespace deletion
```

---

### Phase 4: Advanced Agent Capabilities (Weeks 15-18)

**Objective:** Enable agent learning and adaptation

#### 4.1 Agent Feedback Loop

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    AGENT LEARNING & ADAPTATION                               │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌──────────────────────────────────────────────────────────────────┐      │
│  │                     FEEDBACK COLLECTION                           │      │
│  │                                                                   │      │
│  │  ┌─────────────┐   ┌─────────────┐   ┌─────────────┐            │      │
│  │  │   Human     │   │  Automated  │   │   Outcome   │            │      │
│  │  │  Feedback   │   │  Metrics    │   │  Analysis   │            │      │
│  │  │             │   │             │   │             │            │      │
│  │  │ - Ratings   │   │ - Success   │   │ - Task      │            │      │
│  │  │ - Comments  │   │ - Speed     │   │   results   │            │      │
│  │  │ - Edits     │   │ - Accuracy  │   │ - Follow-up │            │      │
│  │  └──────┬──────┘   └──────┬──────┘   └──────┬──────┘            │      │
│  │         │                 │                 │                    │      │
│  │         └─────────────────┼─────────────────┘                    │      │
│  │                           ▼                                      │      │
│  │             ┌─────────────────────────────┐                     │      │
│  │             │    FEEDBACK AGGREGATOR      │                     │      │
│  │             └──────────────┬──────────────┘                     │      │
│  │                            ▼                                     │      │
│  └──────────────────────────────────────────────────────────────────┘      │
│                                                                             │
│  ┌──────────────────────────────────────────────────────────────────┐      │
│  │                    ADAPTATION STRATEGIES                          │      │
│  │                                                                   │      │
│  │   ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐ │      │
│  │   │  Prompt         │  │  Few-shot       │  │  Model          │ │      │
│  │   │  Refinement     │  │  Examples       │  │  Selection      │ │      │
│  │   │                 │  │                 │  │                 │ │      │
│  │   │  - Add context  │  │  - Success     │  │  - Per task     │ │      │
│  │   │  - Clarify      │  │    examples    │  │    type         │ │      │
│  │   │  - Constrain    │  │  - Edge cases  │  │  - Cost opt     │ │      │
│  │   └─────────────────┘  └─────────────────┘  └─────────────────┘ │      │
│  │                                                                   │      │
│  └──────────────────────────────────────────────────────────────────┘      │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

#### 4.2 Dynamic Agent Configuration

```yaml
# Agent with adaptive configuration
apiVersion: virtualorg.ghatana.com/v1
kind: Agent
metadata:
  name: adaptive-engineer
spec:
  ai:
    enabled: true
    
    # Adaptive model selection
    modelSelection:
      strategy: adaptive  # fixed | adaptive | cost-optimized
      rules:
        - condition: "task.complexity >= 8"
          model: gpt-4-turbo
        - condition: "task.type == 'code_review'"
          model: claude-3-opus
        - default:
          model: gpt-3.5-turbo
    
    # Dynamic prompt enhancement
    promptEnhancement:
      enabled: true
      sources:
        - type: feedback
          weight: 0.3
        - type: success-patterns
          weight: 0.5
        - type: error-patterns
          weight: 0.2
    
    # Few-shot injection
    fewShotLearning:
      enabled: true
      maxExamples: 5
      selectionStrategy: semantic-similarity
      source: successful-completions
```

---

### Phase 5: Additional Domain Plugins (Weeks 19-24)

**Objective:** Prove framework universality with additional domains

#### 5.1 Healthcare-Org Plugin

```yaml
# healthcare-org/config/organization.yaml
apiVersion: virtualorg.ghatana.com/v1
kind: Organization
metadata:
  name: healthcare-org
  labels:
    domain: healthcare
    compliance: hipaa
spec:
  type: HEALTHCARE
  displayName: "Healthcare Organization"
  
  departments:
    - ref: departments/clinical.yaml
    - ref: departments/nursing.yaml
    - ref: departments/pharmacy.yaml
    - ref: departments/administration.yaml
    - ref: departments/medical-records.yaml
    - ref: departments/billing.yaml
    
  compliance:
    frameworks:
      - HIPAA
      - HITECH
    dataClassification:
      phi: protected  # Protected Health Information
      pii: sensitive
    auditRequirements:
      accessLogging: required
      retentionYears: 7
```

#### 5.2 Finance-Org Plugin

```yaml
# finance-org/config/organization.yaml
apiVersion: virtualorg.ghatana.com/v1
kind: Organization
metadata:
  name: finance-org
  labels:
    domain: finance
    compliance: sox
spec:
  type: FINANCIAL_SERVICES
  displayName: "Financial Services Organization"
  
  departments:
    - ref: departments/trading.yaml
    - ref: departments/risk-management.yaml
    - ref: departments/compliance.yaml
    - ref: departments/wealth-management.yaml
    - ref: departments/operations.yaml
    
  compliance:
    frameworks:
      - SOX
      - FINRA
      - GDPR
    segregationOfDuties: required
    tradeApprovals:
      thresholds:
        - amount: 100000
          approvers: 1
        - amount: 1000000
          approvers: 2
          requiredRoles: [risk-manager, senior-trader]
```

---

## data-cloud Required Enhancements

> **IMPORTANT**: Virtual-Org depends on data-cloud. The following enhancements MUST be made to 
> data-cloud to support Virtual-Org requirements. data-cloud is designed as a **highly pluggable,
> extensible system** - these additions follow existing SPI patterns.

### 1. Vector Search SPI (NEW)

**Location:** `products/data-cloud/spi/src/main/java/com/ghatana/datacloud/spi/capability/`

```java
/**
 * Capability interface for plugins that support vector similarity search.
 * 
 * <p>Enables RAG (Retrieval-Augmented Generation) workflows by providing
 * semantic search over embeddings stored in various backends.
 * 
 * <p><b>Implementations:</b>
 * <ul>
 *   <li>PgVectorSearchCapability - PostgreSQL with pgvector extension</li>
 *   <li>MilvusSearchCapability - Milvus vector database</li>
 *   <li>QdrantSearchCapability - Qdrant vector database</li>
 *   <li>ChromaSearchCapability - Chroma embedded vector store</li>
 * </ul>
 * 
 * @doc.type interface
 * @doc.purpose Vector similarity search capability for RAG
 * @doc.layer spi
 * @doc.pattern Capability
 */
public interface VectorSearchCapability {

    /**
     * Store an embedding vector with associated metadata.
     *
     * @param id Unique identifier for the embedding
     * @param embedding Vector of floats (e.g., 1536 dimensions for OpenAI)
     * @param metadata Associated metadata (document source, chunk index, etc.)
     * @param namespace Isolation namespace (tenant, collection)
     * @return Promise completing when stored
     */
    Promise<Void> store(String id, float[] embedding, Map<String, Object> metadata, String namespace);

    /**
     * Search for similar vectors.
     *
     * @param queryEmbedding The query vector
     * @param topK Number of results to return
     * @param minScore Minimum similarity score threshold (0.0-1.0)
     * @param namespace Isolation namespace
     * @param filter Optional metadata filter
     * @return Promise with list of similar results
     */
    Promise<List<VectorSearchResult>> search(
        float[] queryEmbedding,
        int topK,
        float minScore,
        String namespace,
        Map<String, Object> filter
    );

    /**
     * Delete vectors by ID.
     *
     * @param ids Vector IDs to delete
     * @param namespace Isolation namespace
     * @return Promise with count of deleted vectors
     */
    Promise<Integer> delete(List<String> ids, String namespace);

    /**
     * Get supported embedding dimensions.
     *
     * @return List of supported dimension counts
     */
    List<Integer> getSupportedDimensions();
}

@Value
public class VectorSearchResult {
    String id;
    float score;
    Map<String, Object> metadata;
    String content;  // Optional: original text content
}
```

**Implementation Files to Create:**

```
products/data-cloud/spi/src/main/java/com/ghatana/datacloud/spi/capability/
├── VectorSearchCapability.java          # SPI interface
└── VectorSearchResult.java              # Result record

products/data-cloud/plugins/analytics/vector-search/
├── build.gradle
└── src/main/java/com/ghatana/datacloud/plugins/vector/
    ├── PgVectorSearchPlugin.java        # PostgreSQL pgvector
    ├── EmbeddedVectorPlugin.java        # In-memory for dev/test
    └── package-info.java
```

---

### 2. Embedding Service (ml-intelligence Extension)

**Location:** `products/data-cloud/plugins/analytics/ml-intelligence/`

```java
/**
 * Service for generating text embeddings for RAG and semantic search.
 * 
 * <p>Supports multiple embedding providers with caching and batching.
 * Part of ml-intelligence plugin - extends existing ML capabilities.
 * 
 * @doc.type interface
 * @doc.purpose Text embedding generation for semantic operations
 * @doc.layer plugin
 * @doc.pattern Service
 */
public interface EmbeddingService {

    /**
     * Generate embedding for a single text.
     *
     * @param text The text to embed
     * @param model The embedding model (e.g., "text-embedding-3-small")
     * @return Promise with embedding vector
     */
    Promise<float[]> embed(String text, String model);

    /**
     * Generate embeddings for multiple texts (batched).
     *
     * @param texts List of texts to embed
     * @param model The embedding model
     * @return Promise with list of embedding vectors (same order as input)
     */
    Promise<List<float[]>> embedBatch(List<String> texts, String model);

    /**
     * Get embedding dimension for a model.
     *
     * @param model The model name
     * @return Dimension count (e.g., 1536 for text-embedding-3-small)
     */
    int getDimension(String model);

    /**
     * List available embedding models.
     *
     * @return List of model names
     */
    List<String> availableModels();
}

/**
 * Embedding provider SPI - pluggable embedding backends.
 */
public interface EmbeddingProvider extends Plugin {
    
    String getProviderName();  // "openai", "cohere", "huggingface"
    
    Promise<float[]> generateEmbedding(String text, EmbeddingConfig config);
    
    Promise<List<float[]>> generateBatchEmbeddings(List<String> texts, EmbeddingConfig config);
}
```

**Implementation Files:**

```
products/data-cloud/plugins/analytics/ml-intelligence/src/main/java/com/ghatana/datacloud/plugins/ml/
├── embedding/
│   ├── EmbeddingService.java            # Service interface
│   ├── EmbeddingServiceImpl.java        # Default implementation with caching
│   ├── EmbeddingProvider.java           # Provider SPI
│   ├── EmbeddingCache.java              # Redis-backed embedding cache
│   └── providers/
│       ├── OpenAIEmbeddingProvider.java
│       ├── CohereEmbeddingProvider.java
│       └── LocalEmbeddingProvider.java  # For offline/testing
└── rag/
    ├── DocumentChunker.java             # Text chunking strategies
    ├── SemanticRetriever.java           # RAG query orchestration
    └── ContextBuilder.java              # LLM context assembly
```

---

### 3. EventCloud Namespace Isolation Enhancement

**Location:** `products/data-cloud/event-cloud/`

```java
/**
 * Extended EventCloud API for namespace isolation.
 * 
 * <p>Enables simulation/replay scenarios where events are written
 * to isolated namespaces that don't affect production data.
 * 
 * <p><b>Use Cases:</b>
 * <ul>
 *   <li>Simulation runs (isolated event streams)</li>
 *   <li>A/B testing (parallel namespaces)</li>
 *   <li>Replay debugging (read from production, write to isolated)</li>
 * </ul>
 */
public interface NamespaceManager {

    /**
     * Create an isolated namespace with TTL.
     *
     * @param namespaceName Unique namespace name (e.g., "simulation-{runId}")
     * @param config Namespace configuration (retention, isolation level)
     * @return Promise completing when namespace created
     */
    Promise<NamespaceInfo> createNamespace(String namespaceName, NamespaceConfig config);

    /**
     * Delete namespace and all its data.
     *
     * @param namespaceName The namespace to delete
     * @return Promise completing when deleted
     */
    Promise<Void> deleteNamespace(String namespaceName);

    /**
     * List all namespaces for a tenant.
     *
     * @param tenantId The tenant ID
     * @return Promise with list of namespace info
     */
    Promise<List<NamespaceInfo>> listNamespaces(TenantId tenantId);

    /**
     * Fork a namespace for replay/simulation.
     *
     * @param sourceNamespace Source namespace to fork from
     * @param targetNamespace Target namespace name
     * @param fromOffset Start offset (or time) for fork
     * @return Promise with fork info
     */
    Promise<ForkInfo> forkNamespace(String sourceNamespace, String targetNamespace, Long fromOffset);
}

@Value
public class NamespaceConfig {
    Duration ttl;                    // Auto-delete after TTL
    IsolationLevel isolationLevel;   // FULL, READ_THROUGH, WRITE_THROUGH
    int maxEvents;                   // Max events before rejection
    boolean inheritParentSchema;     // Inherit event types from parent
    
    public enum IsolationLevel {
        FULL,          // Complete isolation
        READ_THROUGH,  // Can read from parent, writes isolated
        WRITE_THROUGH  // Writes go to both (for gradual migration)
    }
}
```

---

### 4. LLM Provider SPI (NEW - libs/ai-integration)

> **Note:** This may belong in `libs/ai-integration` rather than data-cloud.
> Virtual-org needs a pluggable LLM abstraction.

**Location:** `libs/java/ai-integration/src/main/java/com/ghatana/ai/`

```java
/**
 * LLM Provider SPI for pluggable AI model backends.
 * 
 * <p>Enables virtual-org to switch between LLM providers without code changes.
 * Supports streaming, function calling, and cost tracking.
 * 
 * @doc.type interface
 * @doc.purpose Pluggable LLM provider abstraction
 * @doc.layer libs
 * @doc.pattern Plugin, Strategy
 */
public interface LLMProvider extends Plugin {

    /**
     * Send a chat completion request.
     *
     * @param request The completion request
     * @return Promise with completion response
     */
    Promise<CompletionResponse> complete(CompletionRequest request);

    /**
     * Send a streaming chat completion request.
     *
     * @param request The completion request
     * @return Stream of completion chunks
     */
    Flow.Publisher<CompletionChunk> completeStream(CompletionRequest request);

    /**
     * Get available models for this provider.
     *
     * @return List of model info
     */
    List<ModelInfo> availableModels();

    /**
     * Get provider capabilities.
     *
     * @return Provider capabilities (function calling, vision, etc.)
     */
    ProviderCapabilities getCapabilities();
}

@Builder
@Value
public class CompletionRequest {
    String model;
    List<Message> messages;
    double temperature;
    int maxTokens;
    List<FunctionDefinition> functions;  // For function calling
    ResponseFormat responseFormat;        // JSON mode, etc.
}

@Value
public class CompletionResponse {
    String content;
    String finishReason;
    List<FunctionCall> functionCalls;
    UsageInfo usage;  // Token counts
    
    @Value
    public static class UsageInfo {
        int promptTokens;
        int completionTokens;
        BigDecimal estimatedCost;
    }
}

/**
 * Registry for LLM providers - enables dynamic provider selection.
 */
public interface LLMProviderRegistry {
    
    void register(LLMProvider provider);
    
    Optional<LLMProvider> getProvider(String providerName);
    
    LLMProvider getDefaultProvider();
    
    List<LLMProvider> getAllProviders();
}
```

**Implementation Files:**

```
libs/java/ai-integration/src/main/java/com/ghatana/ai/
├── spi/
│   ├── LLMProvider.java                 # Provider SPI
│   ├── LLMProviderRegistry.java         # Provider registry
│   ├── CompletionRequest.java           # Request model
│   └── CompletionResponse.java          # Response model
├── providers/
│   ├── OpenAIProvider.java              # OpenAI implementation
│   ├── AnthropicProvider.java           # Anthropic/Claude
│   ├── AzureOpenAIProvider.java         # Azure OpenAI
│   ├── OllamaProvider.java              # Local Ollama
│   └── MockProvider.java                # For testing
└── routing/
    ├── ModelRouter.java                 # Route to optimal model
    ├── CostOptimizer.java               # Select cheapest capable model
    └── FallbackChain.java               # Provider fallback chain
```

---

### 5. Document Ingestion Pipeline (data-cloud)

**Location:** `products/data-cloud/plugins/analytics/document-ingestion/`

```java
/**
 * Document ingestion pipeline for RAG knowledge bases.
 * 
 * <p>Processes documents into chunked, embedded vectors ready for semantic search.
 * Supports multiple document formats and chunking strategies.
 */
public interface DocumentIngestionPipeline {

    /**
     * Ingest a document into a knowledge base.
     *
     * @param document The document to ingest
     * @param config Ingestion configuration
     * @return Promise with ingestion result
     */
    Promise<IngestionResult> ingest(Document document, IngestionConfig config);

    /**
     * Ingest multiple documents in batch.
     *
     * @param documents Documents to ingest
     * @param config Ingestion configuration
     * @return Promise with batch result
     */
    Promise<BatchIngestionResult> ingestBatch(List<Document> documents, IngestionConfig config);

    /**
     * Delete document and its chunks from knowledge base.
     *
     * @param documentId Document ID
     * @param namespace Knowledge base namespace
     * @return Promise completing when deleted
     */
    Promise<Void> delete(String documentId, String namespace);
}

@Builder
@Value
public class IngestionConfig {
    String namespace;               // Target knowledge base
    ChunkingStrategy chunkingStrategy;
    String embeddingModel;
    Map<String, Object> metadata;   // Additional metadata to attach
    boolean updateIfExists;         // Overwrite existing or skip
}

public enum ChunkingStrategy {
    FIXED_SIZE,          // Fixed token count
    SENTENCE,            // Sentence boundaries
    PARAGRAPH,           // Paragraph boundaries
    SEMANTIC,            // Semantic similarity boundaries
    RECURSIVE_SPLIT      // LangChain-style recursive splitting
}

@Value
public class IngestionResult {
    String documentId;
    int chunkCount;
    int totalTokens;
    Duration processingTime;
    List<String> warnings;
}
```

---

### data-cloud Enhancement Summary

| Enhancement | Priority | Effort | Dependency |
|-------------|----------|--------|------------|
| **VectorSearchCapability SPI** | P0 | 3 days | None |
| **PgVectorSearchPlugin** | P0 | 4 days | VectorSearchCapability |
| **EmbeddingService** | P0 | 3 days | ml-intelligence plugin |
| **EmbeddingProvider SPI** | P0 | 2 days | EmbeddingService |
| **OpenAIEmbeddingProvider** | P0 | 2 days | EmbeddingProvider |
| **DocumentChunker** | P1 | 2 days | None |
| **SemanticRetriever** | P1 | 3 days | EmbeddingService, VectorSearch |
| **NamespaceManager** | P1 | 4 days | EventCloud core |
| **Namespace Forking** | P2 | 3 days | NamespaceManager |
| **Document Ingestion Pipeline** | P1 | 5 days | Chunker, EmbeddingService |

**Total data-cloud Effort:** ~31 engineering days

---

## Virtual-Org Core Enhancements (For Software-Org Support)

> **Purpose:** These enhancements move functionality from software-org back into virtual-org,
> making the framework complete and ensuring software-org is purely configuration + domain logic.

### 1. Organization Lifecycle Manager

**Location:** `virtual-org/libs/java/framework/src/main/java/com/ghatana/virtualorg/framework/lifecycle/`

```java
/**
 * Manages organization lifecycle: initialization, runtime, shutdown.
 * 
 * <p>Replaces software-org's SoftwareOrgOrchestrator with a generic implementation
 * that any organization type can use.
 * 
 * @doc.type interface
 * @doc.purpose Organization lifecycle management
 * @doc.layer framework
 * @doc.pattern Lifecycle Manager
 */
public interface OrganizationLifecycleManager {

    /**
     * Initialize organization from configuration.
     * - Loads all config files (organization, departments, agents, workflows)
     * - Creates department instances
     * - Registers agents with departments
     * - Sets up workflow triggers
     */
    Promise<InitializationResult> initialize(Path configPath);
    
    /**
     * Start the organization (begin accepting work).
     * - Starts all departments
     * - Activates workflow triggers
     * - Connects to event bus
     */
    Promise<Void> start();
    
    /**
     * Stop the organization gracefully.
     * - Completes in-flight work
     * - Deactivates triggers
     * - Disconnects from event bus
     */
    Promise<Void> stop();
    
    /**
     * Hot-reload configuration.
     * - Detects config changes
     * - Updates departments/agents without restart
     */
    Promise<ReloadResult> reload();
    
    /**
     * Health check across all components.
     */
    Promise<HealthStatus> healthCheck();
    
    /**
     * Get organization runtime state.
     */
    OrganizationState getState();
}

public enum OrganizationState {
    UNINITIALIZED,
    INITIALIZING,
    INITIALIZED,
    STARTING,
    RUNNING,
    STOPPING,
    STOPPED,
    FAILED
}

@Value
public class InitializationResult {
    int departmentsLoaded;
    int agentsRegistered;
    int workflowsRegistered;
    int pipelinesCreated;
    List<String> warnings;
    Duration initializationTime;
}
```

**Implementation:**

```
virtual-org/libs/java/framework/src/main/java/com/ghatana/virtualorg/framework/lifecycle/
├── OrganizationLifecycleManager.java    # Interface
├── DefaultLifecycleManager.java         # Default implementation
├── OrganizationState.java               # State enum
├── InitializationResult.java            # Result model
├── HealthStatus.java                    # Health model
└── ReloadResult.java                    # Reload result
```

### 2. Pipeline Registry

**Location:** `virtual-org/libs/java/framework/src/main/java/com/ghatana/virtualorg/framework/pipeline/`

```java
/**
 * Registry for organizational pipelines.
 * 
 * <p>Provides declarative pipeline registration from workflow configs.
 * Integrates with AEP (Agentic Event Processor) for execution.
 * 
 * <p>Replaces software-org's AllDepartmentPipelinesRegistrar.
 */
public interface PipelineRegistry {

    /**
     * Register a pipeline from workflow configuration.
     */
    Promise<PipelineInfo> register(WorkflowConfig config);
    
    /**
     * Register all pipelines from organization config.
     */
    Promise<List<PipelineInfo>> registerAll(ResolvedOrganizationConfig config);
    
    /**
     * Get pipeline by ID.
     */
    Optional<PipelineInfo> getPipeline(String pipelineId);
    
    /**
     * Get all pipelines for a department.
     */
    List<PipelineInfo> getPipelinesForDepartment(String departmentId);
    
    /**
     * Connect registry to event processor (AEP).
     */
    Promise<Void> connectToEventProcessor(EventProcessorConnector connector);
    
    /**
     * Unregister a pipeline.
     */
    Promise<Void> unregister(String pipelineId);
}

/**
 * SPI for connecting to different event processors.
 */
public interface EventProcessorConnector extends Plugin {
    String getProcessorType();  // "aep", "flink", "kafka-streams"
    
    Promise<Void> registerPipeline(PipelineInfo pipeline);
    Promise<Void> unregisterPipeline(String pipelineId);
    Promise<PipelineMetrics> getMetrics(String pipelineId);
}
```

### 3. Interaction Handler Framework

**Location:** `virtual-org/libs/java/framework/src/main/java/com/ghatana/virtualorg/framework/interaction/`

```java
/**
 * Handles cross-department interactions.
 * 
 * <p>Software-org defines interactions in config (eng-qa-handoff, support-engineering-escalation).
 * This framework executes them.
 */
public interface InteractionHandler extends Plugin {
    
    /**
     * Get interaction types this handler supports.
     */
    Set<String> getSupportedInteractionTypes();
    
    /**
     * Execute an interaction between departments/agents.
     */
    Promise<InteractionResult> execute(InteractionRequest request);
    
    /**
     * Validate interaction request.
     */
    ValidationResult validate(InteractionRequest request);
}

@Builder
@Value
public class InteractionRequest {
    String interactionType;       // "handoff", "escalation", "collaboration"
    DepartmentId sourceDepartment;
    DepartmentId targetDepartment;
    AgentId sourceAgent;
    AgentId targetAgent;
    Map<String, Object> payload;
    Priority priority;
    Duration timeout;
}

@Value
public class InteractionResult {
    InteractionStatus status;
    AgentId handledBy;
    Map<String, Object> response;
    Duration processingTime;
    List<Event> emittedEvents;
    
    public enum InteractionStatus {
        COMPLETED, PENDING_APPROVAL, ESCALATED, FAILED, TIMED_OUT
    }
}

/**
 * Registry for interaction handlers.
 */
public interface InteractionRegistry {
    void registerHandler(InteractionHandler handler);
    Optional<InteractionHandler> getHandler(String interactionType);
    Promise<InteractionResult> execute(InteractionRequest request);
}

// Built-in handlers
public class HandoffInteractionHandler implements InteractionHandler { ... }
public class EscalationInteractionHandler implements InteractionHandler { ... }
public class CollaborationInteractionHandler implements InteractionHandler { ... }
```

### 4. Workflow Trigger Engine

**Location:** `virtual-org/libs/java/framework/src/main/java/com/ghatana/virtualorg/framework/workflow/trigger/`

```java
/**
 * Executes workflow triggers defined in configuration.
 * 
 * <p>Software-org workflows define triggers (cron, events, manual).
 * This engine activates them.
 */
public interface WorkflowTriggerEngine {

    /**
     * Register a scheduled trigger.
     */
    Promise<TriggerInfo> scheduleWorkflow(String workflowId, CronExpression cron);
    
    /**
     * Register an event-based trigger.
     */
    Promise<TriggerInfo> registerEventTrigger(String workflowId, String eventType);
    
    /**
     * Manually trigger a workflow.
     */
    Promise<WorkflowExecution> triggerManually(String workflowId, AgentId triggeredBy, Map<String, Object> inputs);
    
    /**
     * Register all triggers from workflow config.
     */
    Promise<List<TriggerInfo>> registerTriggersFromConfig(WorkflowConfig config);
    
    /**
     * Cancel a scheduled trigger.
     */
    Promise<Void> cancelTrigger(String triggerId);
    
    /**
     * Connect to EventCloud for event-based triggers.
     */
    Promise<Void> connectToEventCloud(EventCloud eventCloud);
    
    /**
     * Get trigger status.
     */
    Optional<TriggerInfo> getTrigger(String triggerId);
    List<TriggerInfo> getTriggersForWorkflow(String workflowId);
}

@Value
public class TriggerInfo {
    String triggerId;
    String workflowId;
    TriggerType type;
    String schedule;           // For cron triggers
    String eventType;          // For event triggers
    boolean active;
    Instant nextFireTime;      // For scheduled triggers
    int executionCount;
}

public enum TriggerType {
    SCHEDULED,    // Cron-based
    EVENT,        // EventCloud event
    MANUAL,       // API/UI triggered
    WEBHOOK       // External webhook
}
```

### 5. KPI Aggregation Service

**Location:** `virtual-org/libs/java/framework/src/main/java/com/ghatana/virtualorg/framework/kpi/`

```java
/**
 * Aggregates KPIs across departments and organization.
 * 
 * <p>Software-org's organization.yaml defines org-level KPIs that aggregate
 * from department KPIs. This service computes them.
 */
public interface KpiAggregationService {

    /**
     * Get aggregated KPIs for organization.
     */
    Promise<Map<String, KpiValue>> getOrganizationKpis();
    
    /**
     * Get KPIs for a department.
     */
    Promise<Map<String, KpiValue>> getDepartmentKpis(String departmentId);
    
    /**
     * Get historical KPI values.
     */
    Promise<List<KpiSnapshot>> getKpiHistory(String kpiName, TimeRange range);
    
    /**
     * Compute a specific aggregated KPI.
     */
    Promise<KpiValue> computeKpi(OrgKpiDefinition definition);
    
    /**
     * Register KPI definitions from config.
     */
    Promise<Void> registerKpiDefinitions(List<OrgKpiDefinition> definitions);
}

@Value
public class OrgKpiDefinition {
    String name;
    String displayName;
    KpiType type;                    // gauge, counter, percentage
    AggregationType aggregation;     // sum, average, weighted_average
    List<KpiSource> sources;         // Source department KPIs
}

@Value
public class KpiSource {
    String departmentId;
    String kpiName;
    double weight;                   // For weighted averages
}

@Value
public class KpiValue {
    String name;
    Object value;
    Instant computedAt;
    List<KpiSource> contributingSources;
}
```

### 6. Event Bus Connector SPI

**Location:** `virtual-org/libs/java/framework/src/main/java/com/ghatana/virtualorg/framework/event/bus/`

```java
/**
 * SPI for connecting to event buses.
 * 
 * <p>Software-org uses SoftwareOrgAepInitializer for AEP integration.
 * This SPI makes it pluggable.
 */
public interface EventBusConnector extends Plugin {
    
    String getConnectorType();  // "aep", "eventcloud", "kafka"
    
    /**
     * Connect to event bus.
     */
    Promise<Void> connect(EventBusConfig config);
    
    /**
     * Publish event.
     */
    Promise<Void> publish(Event event);
    
    /**
     * Subscribe to events.
     */
    Promise<Subscription> subscribe(String eventPattern, EventHandler handler);
    
    /**
     * Disconnect from event bus.
     */
    Promise<Void> disconnect();
}

// Built-in connectors
public class AepEventBusConnector implements EventBusConnector { ... }
public class EventCloudConnector implements EventBusConnector { ... }
public class InMemoryEventBusConnector implements EventBusConnector { ... }  // For testing
```

### Virtual-Org Core Enhancement Summary

| Enhancement | Priority | Effort | Software-Org Benefit |
|-------------|----------|--------|---------------------|
| **OrganizationLifecycleManager** | P0 | 3 days | Removes `SoftwareOrgOrchestrator` |
| **PipelineRegistry** | P0 | 3 days | Removes `AllDepartmentPipelinesRegistrar` |
| **InteractionHandler SPI** | P1 | 4 days | Enables interaction configs |
| **InteractionRegistry** | P1 | 2 days | Manages interaction handlers |
| **WorkflowTriggerEngine** | P1 | 4 days | Enables trigger configs |
| **KpiAggregationService** | P1 | 3 days | Enables org-level KPIs |
| **EventBusConnector SPI** | P0 | 3 days | Removes `SoftwareOrgAepInitializer` |
| **AepEventBusConnector** | P0 | 2 days | AEP integration |
| **EventCloudConnector** | P1 | 2 days | data-cloud integration |

**Total Virtual-Org Core Effort:** ~26 engineering days

---

---

## Virtual-Org SPI Design

> **IMPORTANT**: Virtual-Org should be **pluggable and extensible** like data-cloud.
> The following SPIs enable domain-specific customization without modifying core framework.

### 1. Agent Provider SPI

```java
/**
 * SPI for pluggable agent implementations.
 * 
 * <p>Enables custom agent behaviors for specific domains (healthcare, finance)
 * without modifying the core framework.
 */
public interface AgentProvider extends Plugin {

    /**
     * Get agent types this provider supports.
     */
    Set<String> getSupportedAgentTypes();

    /**
     * Create an agent instance.
     */
    Agent createAgent(AgentConfig config, OrganizationContext context);

    /**
     * Validate agent configuration.
     */
    ValidationResult validateConfig(AgentConfig config);
}

// Registry for agent providers
public interface AgentProviderRegistry {
    void register(AgentProvider provider);
    Optional<AgentProvider> getProvider(String agentType);
    Agent createAgent(AgentConfig config, OrganizationContext context);
}
```

### 2. Tool Provider SPI

```java
/**
 * SPI for pluggable tool implementations.
 * 
 * <p>Enables domain-specific tools (HIPAA-compliant data access, trading APIs)
 * to be added without modifying core framework.
 */
public interface ToolProvider extends Plugin {

    /**
     * Get tools this provider offers.
     */
    List<ToolDefinition> getAvailableTools();

    /**
     * Create a tool instance.
     */
    AgentTool createTool(String toolName, ToolConfig config);

    /**
     * Get required permissions for a tool.
     */
    Set<Permission> getRequiredPermissions(String toolName);
}

// Discovery via ServiceLoader
public interface ToolProviderRegistry {
    void register(ToolProvider provider);
    List<ToolDefinition> discoverTools();
    Optional<AgentTool> getTool(String toolName);
}
```

### 3. Workflow Step SPI

```java
/**
 * SPI for custom workflow step implementations.
 * 
 * <p>Enables domain-specific workflow steps (approval chains, regulatory checks)
 * to be added declaratively.
 */
public interface WorkflowStepProvider extends Plugin {

    /**
     * Get step types this provider supports.
     */
    Set<String> getSupportedStepTypes();

    /**
     * Execute a workflow step.
     */
    Promise<StepResult> executeStep(WorkflowStep step, WorkflowContext context);

    /**
     * Validate step configuration.
     */
    ValidationResult validateStep(WorkflowStep step);
}
```

### 4. Organization Template SPI

```java
/**
 * SPI for organization templates/blueprints.
 * 
 * <p>Enables pre-built organization structures (software company, hospital, bank)
 * that can be instantiated with customization.
 */
public interface OrganizationTemplateProvider extends Plugin {

    /**
     * Get available templates.
     */
    List<OrganizationTemplate> getTemplates();

    /**
     * Instantiate a template with customization.
     */
    OrganizationConfig instantiate(String templateId, Map<String, Object> customization);

    /**
     * Validate customization against template constraints.
     */
    ValidationResult validateCustomization(String templateId, Map<String, Object> customization);
}

@Value
public class OrganizationTemplate {
    String id;
    String name;
    String description;
    String domain;                    // "software", "healthcare", "finance"
    List<String> requiredTools;
    List<String> optionalFeatures;
    Map<String, Object> defaults;
}
```

### 5. Message Transport SPI

```java
/**
 * SPI for agent-to-agent message transport.
 * 
 * <p>Default: In-memory. Extensions: Redis Pub/Sub, Kafka, NATS.
 */
public interface MessageTransport extends Plugin {

    /**
     * Send message to an agent.
     */
    Promise<Void> send(AgentId target, Message message);

    /**
     * Subscribe to messages for an agent.
     */
    Promise<Subscription> subscribe(AgentId agentId, MessageHandler handler);

    /**
     * Broadcast to a department.
     */
    Promise<Void> broadcast(DepartmentId department, Message message);
}

// Default in-memory, plugin for distributed
public class InMemoryMessageTransport implements MessageTransport { ... }
public class RedisMessageTransport implements MessageTransport { ... }
public class KafkaMessageTransport implements MessageTransport { ... }
```

### Virtual-Org SPI Summary

| SPI | Purpose | Default Implementation | Plugin Examples |
|-----|---------|----------------------|-----------------|
| **AgentProvider** | Custom agent types | `DefaultAgentProvider` | Healthcare agents, Trading agents |
| **ToolProvider** | Custom tools | `CoreToolProvider` | FHIR API, Bloomberg API |
| **WorkflowStepProvider** | Custom workflow steps | `StandardStepProvider` | Regulatory approval, Multi-sign |
| **OrganizationTemplate** | Pre-built org structures | None | SoftwareOrgTemplate, HospitalTemplate |
| **MessageTransport** | Agent communication | `InMemoryTransport` | Redis, Kafka, NATS |
| **MemoryStore** | Agent memory | `DataCloudMemoryStore` | Custom domain memory |

---

## Implementation Summary

### Cross-Product Dependencies

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                        CROSS-PRODUCT DEPENDENCY MAP                                  │
├─────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                     │
│  ┌─────────────────────────────────────────────────────────────────────────────┐   │
│  │                           VIRTUAL-ORG                                        │   │
│  │   (Consumer of data-cloud + libs/ai-integration)                            │   │
│  │                                                                              │   │
│  │   NEW SPIs:                     NEW Adapters:                               │   │
│  │   • AgentProvider SPI           • AgentMemoryStateStore (→ data-cloud)      │   │
│  │   • ToolProvider SPI            • TelemetryEventPublisher (→ EventCloud)    │   │
│  │   • WorkflowStepProvider SPI    • LLMClientAdapter (→ ai-integration)       │   │
│  │   • MessageTransport SPI        • KnowledgeRetriever (→ data-cloud RAG)     │   │
│  │   • OrgTemplate SPI                                                         │   │
│  └───────────────────────────────────┬─────────────────────────────────────────┘   │
│                                      │                                             │
│                   ┌──────────────────┼──────────────────┐                         │
│                   │                  │                  │                         │
│                   ▼                  ▼                  ▼                         │
│  ┌────────────────────┐  ┌────────────────────┐  ┌────────────────────────────┐ │
│  │   libs/ai-         │  │    data-cloud      │  │   libs/java/common-utils   │ │
│  │   integration      │  │                    │  │                            │ │
│  │                    │  │  EXISTING:         │  │   • Exception handling     │ │
│  │  NEW:              │  │  • StateStore SPI  │  │   • Validation utils       │ │
│  │  • LLMProvider SPI │  │  • EventCloud      │  │   • JSON/YAML parsing      │ │
│  │  • OpenAIProvider  │  │  • StoragePlugin   │  │                            │ │
│  │  • AnthropicProv.  │  │  • Trino connector │  │                            │ │
│  │  • ModelRouter     │  │                    │  │                            │ │
│  │  • CostTracker     │  │  NEW:              │  │                            │ │
│  │                    │  │  • VectorSearch SPI│  │                            │ │
│  │                    │  │  • EmbeddingService│  │                            │ │
│  │                    │  │  • NamespaceMgr    │  │                            │ │
│  │                    │  │  • DocIngestion    │  │                            │ │
│  └────────────────────┘  └────────────────────┘  └────────────────────────────┘ │
│                                                                                     │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

### data-cloud Dependency

**Virtual-Org MUST declare dependency on data-cloud modules:**

```groovy
// products/virtual-org/build.gradle
dependencies {
    // data-cloud core dependencies
    implementation project(':products:data-cloud:spi')           // StateStore, StoragePlugin SPIs
    implementation project(':products:data-cloud:core')          // EventCloud core
    implementation project(':products:data-cloud:domain')        // HybridStateStore, StateAdapter
    implementation project(':products:data-cloud:event-cloud:common')
    implementation project(':products:data-cloud:event-cloud:domain')
    
    // libs dependencies
    implementation project(':libs:java:ai-integration')          // LLMProvider SPI (NEW)
    implementation project(':libs:java:common-utils')            // Core utilities
    implementation project(':libs:java:observability')           // Metrics, tracing
    
    // data-cloud plugins (runtime)
    runtimeOnly project(':products:data-cloud:plugins:storage:rocksdb')
    runtimeOnly project(':products:data-cloud:plugins:storage:redis')
    runtimeOnly project(':products:data-cloud:plugins:storage:postgres')
    runtimeOnly project(':products:data-cloud:plugins:analytics:ml-intelligence')
    runtimeOnly project(':products:data-cloud:plugins:analytics:trino-connector')
    runtimeOnly project(':products:data-cloud:plugins:analytics:vector-search')  // NEW
}
```

### Complete Enhancement Effort Summary (REVISED after Architecture Review)

> **Note:** Effort reduced by ~15 days due to discovery of existing components in `libs/java/*`.

| Product/Library | Category | Components | Original | Revised | Notes |
|-----------------|----------|------------|----------|---------|-------|
| **Pre-Work (Cleanup)** | Deduplication | Delete duplicates, update imports | 0 days | **5 days** | NEW - must do first |
| **virtual-org (Core)** | Lifecycle & Orchestration | `OrganizationLifecycleManager`, ~~`PipelineRegistry`~~, `EventBusConnector` | 11 days | **8 days** | Use `OperatorCatalog` instead |
| **virtual-org (Core)** | Interactions & Triggers | `InteractionHandler`, `WorkflowTriggerEngine`, `KpiAggregationService` | 15 days | **15 days** | No change |
| **virtual-org (SPIs)** | Extensibility | `AgentProvider`, `ToolProvider`, `WorkflowStepProvider`, `MessageTransport`, `OrgTemplate` | 12 days | **12 days** | No change |
| **virtual-org (AEP)** | AEP Integration | `OrganizationalAgentProvider`, `WorkflowOperatorProvider`, `PatternLearningConsumer` | 55 days | **50 days** | Reuse more from AEP |
| **virtual-org (Phases)** | Enhancement Phases 1-5 | Memory, Observability, Simulation, Learning, Domains | 18 weeks | **15 weeks** | Reuse ai-integration |
| **data-cloud** | Events Only | ~~`VectorSearchCapability`~~, ~~`EmbeddingService`~~, Namespace isolation | 31 days | **10 days** | Vector/Embedding EXISTS in libs |
| **agentic-event-processor** | Multi-Tenancy & Workflow | `Multi-Tenant Agent Registry`, `Workflow-Aware Orchestrator`, `Pipeline Templating` | 17 days | **17 days** | No change |
| **libs/ai-integration** | LLM Enhancements | ~~`LLMProvider` SPI~~, Add Anthropic, Cost tracking | 11 days | **5 days** | `LLMGateway` EXISTS |
| **software-org** | Refactoring | Remove duplicated code, use virtual-org SPIs | 5 days | **5 days** | No change |

**Original Grand Total:** ~157 engineering days (~31 weeks) + 18 weeks enhancement phases  
**Revised Grand Total:** ~**127 engineering days (~25 weeks)** + **15 weeks enhancement phases** (~30 days saved)

### Software-Org Configuration Cleanup (NEW - Priority 0)

> **CRITICAL:** Before starting enhancement phases, the software-org configuration 
> redundancy MUST be addressed to avoid propagating duplicate patterns.

| Task | Description | Effort | Priority |
|------|-------------|--------|----------|
| **Create Cross-Cutting Templates** | Extract 14 parameterized operator templates from 180+ duplicates | 3 days | P0 |
| **Consolidate Domain Operators** | Deduplicate ~300 files → ~119 unique specs | 5 days | P0 |
| **Update Workflow Definitions** | Modify `specs2/stages/*.yaml` to reference templates | 3 days | P1 |
| **Create Operator Registry** | Build registry that instantiates cross-cutting operators per stage | 4 days | P1 |
| **Migration Script** | Script to migrate existing configs to new structure | 2 days | P2 |
| **Validation Tests** | Ensure all workflows still execute correctly | 3 days | P0 |

**Configuration Cleanup Total:** ~20 engineering days

**UPDATED Grand Total:** ~**147 engineering days (~29 weeks)** + **15 weeks enhancement phases**

### Cross-Product Coordination Required (UPDATED)

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                    CROSS-PRODUCT COORDINATION MAP (REVISED)                          │
├─────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                     │
│                           ┌─────────────────────┐                                   │
│                           │    software-org     │                                   │
│                           │  (Reference Impl)   │                                   │
│                           └──────────┬──────────┘                                   │
│                                      │ uses                                         │
│                                      ▼                                              │
│                           ┌─────────────────────┐                                   │
│                           │    virtual-org      │                                   │
│                           │    (Framework)      │                                   │
│                           └─┬───────┬───────┬───┘                                   │
│                             │       │       │                                       │
│              ┌──────────────┘       │       └────────────┐                         │
│              │                      │                    │                         │
│              ▼                      ▼                    ▼                         │
│  ┌───────────────────┐  ┌───────────────────┐  ┌───────────────────┐              │
│  │  libs/java/*      │  │       AEP         │  │   data-cloud      │              │
│  │                   │  │                   │  │                   │              │
│  │ • StateStore ✅   │  │ • Agent Registry  │  │ • EventCloud      │              │
│  │ • LLMGateway ✅   │  │ • Orchestrator    │  │ • StoragePlugin   │              │
│  │ • VectorStore ✅  │  │ • Pattern System  │  │ • Namespaces (new)│              │
│  │ • EmbeddingService│  │ • Learning Ops    │  │                   │              │
│  │ • OperatorCatalog │  │                   │  │                   │              │
│  │ • Observability ✅│  │                   │  │                   │              │
│  └───────────────────┘  └───────────────────┘  └───────────────────┘              │
│                                                                                     │
│  OWNERSHIP (CLARIFIED):                                                            │
│  • libs/java/* team: CANONICAL for StateStore, AI, Operators, Observability        │
│  • virtual-org team: SPIs, adapters to libs, domain templates                      │
│  • data-cloud team: EventCloud, StoragePlugin, namespaces (NOT vector/embedding)   │
│  • AEP team: Agent runtime, orchestrator, pattern system                           │
│                                                                                     │
│  ✅ = Already exists, REUSE                                                        │
│                                                                                     │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

### Timeline (REVISED)

```
┌────────────────────────────────────────────────────────────────────────────────────┐
│                         25-WEEK REVISED TIMELINE                                    │
├────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                    │
│  CLEANUP (Week -2 to 0):                                                           │
│  • Delete duplicate StateStore from libs/java/operator                             │
│  • Delete duplicate EmbeddingService from virtual-org                              │
│  • Update imports across codebase                                                  │
│  • Document canonical locations                                                    │
│                                                                                    │
│  FOUNDATION (Week 0 to 2):                                                         │
│  • virtual-org Core: LifecycleManager, EventBusConnector                           │
│  • virtual-org: Adapter to libs/java/state/StateStore                              │
│  • virtual-org: Adapter to libs/java/ai-integration/LLMGateway                     │
│  • libs/ai-integration: Add Anthropic provider, cost tracking                      │
│  • software-org: Refactor to use virtual-org SPIs                                  │
│                                                                                    │
│  ├─────────┼─────────┼─────────┼─────────┼─────────┼─────────┤                    │
│  W1      W4       W7      W10     W13     W16     W21                             │
│  │         │         │         │         │         │                               │
│  │◄── Phase 1 ──►│◄── P2 ──►│◄─ P3 ─►│◄─ P4 ─►│◄── P5 ──►│                       │
│  │  Memory/RAG   │  Obs/Mon  │ Sim/Rep │ Adv Agt │ Domains  │                       │
│  │ (use libs)    │(use libs) │         │         │          │                       │
│  │               │           │         │         │          │                       │
│  │  +Interactions│ +Triggers │         │         │          │                       │
│  │  +KPIs        │           │         │         │          │                       │
│  │               │           │         │         │          │                       │
│  └───────────────┴───────────┴─────────┴─────────┴──────────┘                      │
│                                                                                    │
│  Phase 1 (W1-5):  Memory using libs/state + RAG using libs/ai-integration          │
│  Phase 2 (W6-8):  Observability using libs/observability + EventCloud              │
│  Phase 3 (W9-12): Simulation via EventCloud namespaces                             │
│  Phase 4 (W13-15): Advanced Agent Learning & Adaptation                            │
│  Phase 5 (W16-21): Healthcare-Org + Finance-Org domain plugins                     │
│                                                                                    │
└────────────────────────────────────────────────────────────────────────────────────┘
```
│                                                                                    │
│  Foundation (W-4 to 0): Core infrastructure for all products                       │
│  Phase 1 (W1-6):  Memory & Knowledge + Interactions + KPI Aggregation              │
│  Phase 2 (W7-10): Observability + Workflow Triggers                                │
│  Phase 3 (W11-14): Simulation via EventCloud namespaces                            │
│  Phase 4 (W15-18): Advanced Agent Learning & Adaptation                            │
│  Phase 5 (W19-24): Healthcare-Org + Finance-Org domain plugins                     │
│                                                                                    │
└────────────────────────────────────────────────────────────────────────────────────┘
```

### Resource Requirements

| Phase | Engineering Effort | Skills Required | Products Affected |
|-------|-------------------|-----------------|-------------------|
| **Prep** | 2 engineers, 2 weeks | Java/ActiveJ, AI APIs | data-cloud, ai-integration |
| Phase 1 | 2 engineers, 6 weeks | Java/ActiveJ, **data-cloud integration**, RAG | virtual-org, data-cloud |
| Phase 2 | 1 engineer, 4 weeks | **data-cloud EventCloud**, Trino, Dashboards | virtual-org |
| Phase 3 | 2 engineers, 4 weeks | **data-cloud EventCloud**, Event Sourcing | virtual-org, data-cloud |
| Phase 4 | 1 engineer, 4 weeks | ML Ops, Prompt Engineering | virtual-org |
| Phase 5 | 2 engineers, 6 weeks | Domain Expertise, YAML Config | virtual-org |

### Expected Outcomes

| Metric | Current | After Roadmap |
|--------|---------|---------------|
| **Completeness Score** | 87% | 95%+ |
| **Domain Coverage** | 1 (Software) | 3+ (Software, Healthcare, Finance) |
| **Memory Persistence** | None | Full (via **data-cloud StateStore**) |
| **Observability** | Basic Logs | Full Tracing (via **data-cloud EventCloud**) |
| **Simulation Support** | None | Full (via **data-cloud EventCloud namespaces**) |
| **Agent Learning** | None | Adaptive |
| **Pluggability (SPIs)** | 0 | **8 SPIs** (fully extensible) |
| **data-cloud Reuse** | 0% | **100%** (no duplicate data layer) |
| **LLM Providers** | Hard-coded | **4+ providers** (pluggable) |

---

## Success Criteria

1. **Phase 1 Success:**
   - Agents persist memory across restarts
   - RAG queries return relevant context
   - < 100ms retrieval latency
   - **data-cloud VectorSearchCapability SPI complete**

2. **Phase 2 Success:**
   - All agent calls traced with spans
   - Cost tracking accurate to $0.01
   - Real-time dashboard updates
   - **EventCloud integration complete**

3. **Phase 3 Success:**
   - Can replay any workflow from checkpoint
   - Simulation mode runs 10x faster than real-time
   - A/B comparison reports generated
   - **EventCloud namespace isolation working**

4. **Phase 4 Success:**
   - Agents improve task success rate by 10%
   - Prompt optimization reduces token usage by 20%
   - Model selection reduces cost by 30%
   - **LLMProvider SPI with multiple backends**

5. **Phase 5 Success:**
   - Healthcare-Org plugin fully functional
   - Finance-Org plugin fully functional
   - New plugins created in < 1 week
   - **All virtual-org SPIs implemented**

---

*This roadmap transforms Virtual-Org from an excellent framework to a best-in-class enterprise organizational AI platform, fully integrated with **data-cloud** as the unified data layer and designed with **pluggable SPIs** for maximum extensibility.*

---

## Appendix A: Deduplication & Cleanup Tasks (MUST DO FIRST)

> **CRITICAL:** These tasks MUST be completed before starting any roadmap phases.
> Failure to complete cleanup will result in further code duplication.

### Task 1: Delete Duplicate StateStore (P0 - Day 1)

**Location:** `libs/java/operator/src/main/java/com/ghatana/core/operator/state/StateStore.java`  
**Action:** DELETE file  
**Canonical Location:** `libs/java/state/src/main/java/com/ghatana/core/state/StateStore.java`

```bash
# Commands to execute
rm libs/java/operator/src/main/java/com/ghatana/core/operator/state/StateStore.java
# Update all imports from:
#   com.ghatana.core.operator.state.StateStore
# To:
#   com.ghatana.core.state.StateStore
```

**Affected Files (update imports):**
- [ ] `libs/java/operator/src/test/java/**/JoinOperatorTest.java`
- [ ] `libs/java/operator/src/test/java/**/AggregationOperatorTest.java`
- [ ] `libs/java/operator/src/main/java/**/AndOperator.java`
- [ ] `libs/java/operator/src/main/java/**/AbstractLearningOperator.java`

### Task 2: Delete Duplicate EmbeddingService (P0 - Day 1)

**Location:** `products/virtual-org/libs/java/framework/src/main/java/com/ghatana/virtualorg/framework/memory/EmbeddingService.java`  
**Action:** DELETE file  
**Canonical Location:** `libs/java/ai-integration/src/main/java/com/ghatana/ai/embedding/EmbeddingService.java`

```bash
# Commands to execute
rm products/virtual-org/libs/java/framework/src/main/java/com/ghatana/virtualorg/framework/memory/EmbeddingService.java
# Update all imports from:
#   com.ghatana.virtualorg.framework.memory.EmbeddingService
# To:
#   com.ghatana.ai.embedding.EmbeddingService
```

**Affected Files:**
- [ ] All files in `virtual-org/framework/memory/` that use embeddings

### Task 3: Update build.gradle Dependencies (P0 - Day 1-2)

**virtual-org/libs/java/framework/build.gradle:**
```groovy
dependencies {
    // ADD these dependencies to use canonical libs
    implementation project(':libs:java:ai-integration')
    implementation project(':libs:java:state')
    implementation project(':libs:java:operator-catalog')
    implementation project(':libs:java:observability')
    
    // KEEP existing dependencies
    implementation project(':products:data-cloud:event-cloud:common')
}
```

### Task 4: Create Architecture Documentation (P1 - Day 2-3)

**Create File:** `libs/java/CANONICAL_COMPONENTS.md`

```markdown
# Canonical Component Locations

## AI / LLM
- **LLMGateway**: libs/java/ai-integration/llm/LLMGateway.java
- **EmbeddingService**: libs/java/ai-integration/embedding/EmbeddingService.java
- **VectorStore**: libs/java/ai-integration/vectorstore/VectorStore.java

## State Management
- **StateStore<K,V>**: libs/java/state/StateStore.java
- **HybridStateStore**: libs/java/state/HybridStateStore.java

## Operators & Pipelines
- **OperatorCatalog**: libs/java/operator-catalog/OperatorCatalog.java
- **UnifiedOperator**: libs/java/operator/UnifiedOperator.java
- **Pipeline**: libs/java/operator/pipeline/Pipeline.java

## Observability
- **MetricsCollector**: libs/java/observability/MetricsCollector.java
- **TraceStorage**: libs/java/observability/trace/TraceStorage.java
```

### Task 5: Verification Tests (P1 - Day 3)

- [ ] Run `./gradlew :libs:java:operator:test` - verify no StateStore import errors
- [ ] Run `./gradlew :products:virtual-org:libs:java:framework:test` - verify no EmbeddingService errors
- [ ] Run `./gradlew :libs:java:ai-integration:test` - verify canonical EmbeddingService works
- [ ] Run full build: `./gradlew clean build`

### Cleanup Definition of Done

- [ ] No file named `StateStore.java` exists in `libs/java/operator/`
- [ ] No file named `EmbeddingService.java` exists in `virtual-org/framework/memory/`
- [ ] All imports updated to canonical locations
- [ ] `CANONICAL_COMPONENTS.md` created and committed
- [ ] Full build passes
- [ ] CI/CD passes

---

## Appendix B: Revised data-cloud Integration Checklist

> **IMPORTANT:** This checklist is REVISED based on architecture review.
> Items marked ~~strikethrough~~ are NO LONGER NEEDED (already exist elsewhere).

### Preparation Phase Checklist (REVISED)
- [ ] ~~**data-cloud:** Create `VectorSearchCapability` SPI~~ → **EXISTS** in `libs/java/ai-integration/vectorstore/`
- [ ] ~~**data-cloud:** Implement `PgVectorSearchPlugin`~~ → **EXISTS** as `PgVectorStore` in libs
- [ ] ~~**data-cloud:** Add `EmbeddingService`~~ → **EXISTS** in `libs/java/ai-integration/embedding/`
- [ ] ~~**libs/ai-integration:** Create `LLMProvider` SPI~~ → **EXISTS** as `LLMGateway`
- [ ] **libs/ai-integration:** Add `AnthropicProvider` implementation (NEW)
- [ ] **libs/ai-integration:** Add cost tracking to `LLMGateway` (ENHANCE)
- [ ] **data-cloud:** Add namespace isolation to EventCloud (NEW)

### Phase 1 Checklist (REVISED)
- [ ] Create `AgentMemoryStateStoreAdapter` using **libs/java/state/StateStore** (not data-cloud)
- [ ] Configure `HybridStateStore` from **libs/java/state** with RocksDB + Redis
- [ ] Emit `agent.memory.updated` events to data-cloud EventCloud
- [ ] Create `VectorStoreAdapter` using **libs/java/ai-integration/VectorStore** 
- [ ] Create `EmbeddingServiceAdapter` using **libs/java/ai-integration/EmbeddingService**
- [ ] Build document ingestion pipeline

### Phase 2 Checklist (REVISED)
- [ ] Use **libs/java/observability** for metrics and tracing
- [ ] Define EventCloud event types for virtual-org telemetry
- [ ] Create `TelemetryEventPublisher` that writes to data-cloud EventCloud
- [ ] Add cost tracking to **libs/java/ai-integration/LLMGateway** (enhancement)
- [ ] Build dashboard queries using data-cloud Trino connector

### Virtual-Org SPI Checklist (NO CHANGE)
- [ ] Implement `AgentProvider` SPI
- [ ] Implement `ToolProvider` SPI
- [ ] Implement `WorkflowStepProvider` SPI
- [ ] Implement `OrganizationTemplateProvider` SPI
- [ ] Implement `MessageTransport` SPI
- [ ] Create ServiceLoader discovery for all SPIs

### Validation Checklist (REVISED)
- [ ] All state storage goes through **libs/java/state/StateStore**
- [ ] All events go through data-cloud EventCloud
- [ ] All analytics use data-cloud Trino connector
- [ ] All LLM calls go through **libs/java/ai-integration/LLMGateway**
- [ ] All embeddings go through **libs/java/ai-integration/EmbeddingService**
- [ ] All vectors stored via **libs/java/ai-integration/VectorStore**
- [ ] All metrics/tracing via **libs/java/observability**
- [ ] No duplicate implementations exist in virtual-org or data-cloud

---

## Appendix C: File Structure Changes (REVISED)

### data-cloud New Files

```
products/data-cloud/
├── spi/src/main/java/com/ghatana/datacloud/spi/capability/
│   ├── VectorSearchCapability.java       # NEW: Vector search SPI
│   └── VectorSearchResult.java           # NEW: Search result model
│
├── plugins/analytics/
│   ├── ml-intelligence/src/main/java/com/ghatana/datacloud/plugins/ml/
│   │   ├── embedding/
│   │   │   ├── EmbeddingService.java         # NEW: Embedding generation
│   │   │   ├── EmbeddingServiceImpl.java     # NEW: With caching
│   │   │   ├── EmbeddingProvider.java        # NEW: Provider SPI
│   │   │   ├── EmbeddingCache.java           # NEW: Redis cache
│   │   │   └── providers/
│   │   │       ├── OpenAIEmbeddingProvider.java    # NEW
│   │   │       └── LocalEmbeddingProvider.java     # NEW: For testing
│   │   └── rag/
│   │       ├── DocumentChunker.java          # NEW: Chunking strategies
│   │       ├── SemanticRetriever.java        # NEW: RAG orchestration
│   │       └── ContextBuilder.java           # NEW: LLM context
│   │
│   └── vector-search/                        # NEW PLUGIN
│       ├── build.gradle
│       └── src/main/java/com/ghatana/datacloud/plugins/vector/
│           ├── PgVectorSearchPlugin.java     # NEW: PostgreSQL pgvector
│           └── EmbeddedVectorPlugin.java     # NEW: In-memory
│
└── event-cloud/
    └── core/src/main/java/com/ghatana/datacloud/event/
        └── namespace/
            ├── NamespaceManager.java         # NEW: Namespace isolation
            ├── NamespaceConfig.java          # NEW: Config model
            └── NamespaceForkService.java     # NEW: Fork support
```

### libs/ai-integration New Files

```
libs/java/ai-integration/src/main/java/com/ghatana/ai/
├── spi/
│   ├── LLMProvider.java                  # NEW: Provider SPI
│   ├── LLMProviderRegistry.java          # NEW: Provider registry
│   ├── CompletionRequest.java            # NEW: Request model
│   ├── CompletionResponse.java           # NEW: Response model
│   └── ProviderCapabilities.java         # NEW: Capability flags
├── providers/
│   ├── OpenAIProvider.java               # NEW
│   ├── AnthropicProvider.java            # NEW
│   ├── AzureOpenAIProvider.java          # NEW
│   ├── OllamaProvider.java               # NEW: Local
│   └── MockProvider.java                 # NEW: Testing
└── routing/
    ├── ModelRouter.java                  # NEW: Model selection
    ├── CostOptimizer.java                # NEW: Cost-based routing
    └── FallbackChain.java                # NEW: Provider fallback
```

### virtual-org New Files

```
products/virtual-org/libs/java/framework/src/main/java/com/ghatana/virtualorg/framework/
├── spi/
│   ├── AgentProvider.java                # NEW: Agent SPI
│   ├── AgentProviderRegistry.java        # NEW
│   ├── ToolProvider.java                 # NEW: Tool SPI
│   ├── ToolProviderRegistry.java         # NEW
│   ├── WorkflowStepProvider.java         # NEW: Workflow step SPI
│   ├── MessageTransport.java             # NEW: Message transport SPI
│   └── OrganizationTemplateProvider.java # NEW: Template SPI
├── memory/
│   └── datacloud/
│       ├── AgentMemoryStateStore.java    # NEW: data-cloud adapter
│       ├── DataCloudMemoryConfig.java    # NEW
│       └── MemoryEventPublisher.java     # NEW: EventCloud integration
├── observability/
│   └── eventcloud/
│       ├── TelemetryEventPublisher.java  # NEW
│       ├── AgentTelemetryEvent.java      # NEW
│       ├── LLMCallEvent.java             # NEW
│       └── CostTracker.java              # NEW
├── knowledge/
│   ├── KnowledgeBase.java                # NEW
│   ├── DataCloudVectorAdapter.java       # NEW: Uses data-cloud
│   └── SemanticRetriever.java            # NEW
└── simulation/
    ├── SimulationController.java         # NEW
    ├── EventCloudWorkflowReplayEngine.java # NEW
    └── SimulationConfig.java             # NEW
```

---

## Appendix C: Software-Org Validation Checklist

### Pre-Enhancement Baseline
- [ ] Software-org loads organization from YAML config
- [ ] 10 departments register correctly
- [ ] 11 agents created with correct roles/authorities
- [ ] 4 workflows defined and executable
- [ ] Events publish to AEP

### Post-Enhancement Validation

**Virtual-Org Core Enhancements:**
- [ ] `OrganizationLifecycleManager.initialize()` loads software-org correctly
- [ ] All 30 pipelines registered via `PipelineRegistry`
- [ ] `EventBusConnector` replaces `SoftwareOrgAepInitializer`
- [ ] `InteractionHandler` executes cross-department interactions
- [ ] `WorkflowTriggerEngine` fires cron-based triggers
- [ ] `KpiAggregationService` computes org-level KPIs

**Software-Org Code Reduction:**
- [ ] Delete `SoftwareOrgOrchestrator` (use `OrganizationLifecycleManager`)
- [ ] Delete `AllDepartmentPipelinesRegistrar` (use `PipelineRegistry`)
- [ ] Delete `SoftwareOrgAepInitializer` (use `EventBusConnector`)
- [ ] Delete `DepartmentFactory` (use `DepartmentProvider` SPI)
- [ ] Delete `RoleAgentFactory` (use `AgentProvider` SPI)

**Software-Org Simplification Target:**
```
Before Enhancement:
  software-org/libs/java/bootstrap/          → 6 classes (~1500 LOC)
  software-org/libs/java/software-org/       → 8 classes (~2000 LOC)
  software-org/libs/java/framework/          → 5 classes (~1000 LOC)
  
After Enhancement:
  software-org/libs/java/domain/             → 2 classes (~300 LOC) [Domain-specific only]
  software-org/libs/java/hooks/              → 3 classes (~400 LOC) [Extension hooks]
  
Code Reduction: ~60% (from ~4500 LOC to ~700 LOC)
```

---

## Appendix D: Migration Guide

### For Existing Virtual-Org Users

1. **No Breaking Changes** - All enhancements are additive
2. **Memory Migration** - Existing in-memory state will need manual migration to persistent stores
3. **Configuration** - Add data-cloud configuration to enable new features

### For Existing Software-Org Users

1. **Dependency Update** - Update virtual-org dependency to include new SPIs
2. **Remove Duplicated Code** - Delete bootstrap classes that virtual-org now provides
3. **Update Config** - Add virtual-org SPI provider references
4. **Test Integration** - Run full integration tests

### Feature Flags

```yaml
# virtual-org feature flags
features:
  persistence:
    enabled: true
    backend: data-cloud  # "data-cloud" | "in-memory"
  
  rag:
    enabled: true
    embeddingProvider: openai
    vectorStore: pgvector
  
  observability:
    enabled: true
    eventCloud: true
    costTracking: true
  
  simulation:
    enabled: false  # Enable for dev/test environments
  
  learning:
    enabled: false  # Enable after Phase 4
    
  # NEW: Core feature flags
  lifecycle:
    autoInitialize: true
    hotReload: true
    
  interactions:
    enabled: true
    handlers: [handoff, escalation, collaboration]
    
  triggers:
    enabled: true
    scheduler: quartz  # quartz | simple
    
  # NEW: AEP integration flags
  aep:
    enabled: true
    agentRegistry: true
    pipelineOrchestrator: true
    patternLearning: false  # Enable after Phase 5
```

---

## Appendix E: AEP Integration Checklist

### Phase 1: Foundation (EventBusConnector SPI)

**Virtual-Org Tasks:**
- [ ] Create `EventBusConnector` interface in `virtual-org/framework/spi/`
- [ ] Define event types for organization events (`org.created`, `dept.registered`, etc.)
- [ ] Implement `AepEventBusConnector` using existing `OrganizationEventPublisher`
- [ ] Migrate `AepEventPublisherAdapter` to use new SPI
- [ ] Add configuration for AEP connection settings
- [ ] Write integration tests for event publishing

**AEP Tasks:**
- [ ] Register organization event types in AEP catalog
- [ ] Create event type schemas for org events
- [ ] Test event ingestion from virtual-org

### Phase 2: Agent Integration

**Virtual-Org Tasks:**
- [ ] Create `OrganizationalAgentProvider` interface
- [ ] Define agent manifest schema for organizational agents
- [ ] Implement auto-registration of agents from YAML config
- [ ] Create adapter for AEP `AgentRegistryService`
- [ ] Implement agent capability discovery
- [ ] Add health check delegation to AEP

**AEP Tasks:**
- [ ] Extend `AgentRegistryService` for multi-tenant namespaces
- [ ] Add organization context to `AgentExecutionContext`
- [ ] Support agent grouping/teams
- [ ] Implement cross-org agent invocation controls

### Phase 3: Pipeline Integration

**Virtual-Org Tasks:**
- [ ] Create `PipelineRegistry` in virtual-org
- [ ] Implement pipeline auto-generation from workflow YAML
- [ ] Create `PipelineSpec` builder for workflows
- [ ] Register pipelines with AEP Orchestrator on startup
- [ ] Support pipeline lifecycle (start/stop/restart)
- [ ] Implement pipeline metrics collection

**AEP Tasks:**
- [ ] Extend `Orchestrator` for external pipeline submission
- [ ] Add pipeline templating support
- [ ] Create pipeline validation API
- [ ] Support parameterized pipelines

### Phase 4: Operator Integration

**Virtual-Org Tasks:**
- [ ] Create `WorkflowOperatorProvider` interface
- [ ] Implement `WorkflowStepOperator` adapter
- [ ] Register workflow steps as AEP operators
- [ ] Define operator metadata for workflow steps
- [ ] Support operator composition

**AEP Tasks:**
- [ ] Extend `OperatorRegistry` for external operators
- [ ] Add operator namespace support
- [ ] Create operator validation hooks
- [ ] Support dynamic operator loading

### Phase 5: Learning Integration

**Virtual-Org Tasks:**
- [ ] Create `PatternLearningConsumer` interface
- [ ] Implement workflow recommendation receiver
- [ ] Create UI for pattern suggestions
- [ ] Support auto-trigger creation from patterns
- [ ] Implement feedback loop to AEP

**AEP Tasks:**
- [ ] Expose `PatternRecommender` via API
- [ ] Create recommendation event types
- [ ] Add organization-scoped recommendations
- [ ] Implement feedback ingestion

### AEP Integration Test Matrix

| Test Case | Virtual-Org | AEP | Data-Cloud | Priority |
|-----------|-------------|-----|------------|----------|
| Org events publish to AEP | ✓ | ✓ | | P0 |
| Agents register with AEP | ✓ | ✓ | | P0 |
| Pipelines submit to AEP | ✓ | ✓ | | P0 |
| Agent discovery via AEP | ✓ | ✓ | | P1 |
| Workflow operators register | ✓ | ✓ | | P1 |
| Pattern recommendations | ✓ | ✓ | ✓ | P2 |
| Cross-org agent invoke | ✓ | ✓ | | P2 |
| Pipeline templates work | ✓ | ✓ | | P2 |

---

## Appendix F: Risk Assessment

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|------------|
| data-cloud changes break virtual-org | Medium | High | Semantic versioning, integration tests |
| RAG latency too high | Medium | Medium | Caching, embedding batch APIs |
| LLM provider rate limits | High | Medium | Multiple providers, fallback chains |
| EventCloud namespace overhead | Low | Medium | TTL-based cleanup, quotas |
| SPI complexity for users | Medium | Low | Good documentation, examples |
| Software-org migration breaks prod | Medium | High | Phased rollout, feature flags |
| Interaction handlers bottleneck | Low | Medium | Async handlers, queue-based |
| AEP agent registry namespace conflicts | Medium | Medium | Per-org namespaces, validation |
| AEP pipeline submission failures | Medium | High | Circuit breaker, retry logic |
| AEP operator version mismatches | Low | Medium | Semantic versioning, compatibility checks |

---

## Appendix G: Validation Test Matrix

| Test Category | Test Case | Products Involved | Priority |
|---------------|-----------|-------------------|----------|
| **Lifecycle** | Initialize org from YAML | virtual-org | P0 |
| **Lifecycle** | Hot-reload config changes | virtual-org | P1 |
| **Lifecycle** | Graceful shutdown | virtual-org | P0 |
| **Pipelines** | Register 30 software-org pipelines | virtual-org, software-org | P0 |
| **Pipelines** | Pipeline metrics collection | virtual-org, data-cloud | P1 |
| **Interactions** | Engineering → QA handoff | virtual-org, software-org | P1 |
| **Interactions** | Support → Engineering escalation | virtual-org, software-org | P1 |
| **Triggers** | Cron trigger fires workflow | virtual-org | P1 |
| **Triggers** | Event trigger fires workflow | virtual-org, data-cloud | P1 |
| **KPIs** | Aggregate org-level KPIs | virtual-org, software-org | P1 |
| **Memory** | Agent memory persists | virtual-org, data-cloud | P0 |
| **RAG** | Knowledge retrieval | virtual-org, data-cloud | P0 |
| **Observability** | Trace agent execution | virtual-org, data-cloud | P1 |
| **Simulation** | Replay workflow | virtual-org, data-cloud | P2 |
| **AEP-Events** | Org events publish to AEP | virtual-org, AEP | P0 |
| **AEP-Agents** | Agents register with AEP | virtual-org, AEP | P0 |
| **AEP-Pipelines** | Pipelines submit to AEP | virtual-org, AEP | P0 |
| **AEP-Discovery** | Agent discovery via AEP | virtual-org, AEP | P1 |
| **AEP-Operators** | Workflow operators register | virtual-org, AEP | P1 |
| **AEP-Learning** | Pattern recommendations | virtual-org, AEP, data-cloud | P2 |
| **AEP-Cross-Org** | Cross-org agent invoke | virtual-org, AEP | P2 |
| **AEP-Templates** | Pipeline templates work | virtual-org, AEP | P2 |

---