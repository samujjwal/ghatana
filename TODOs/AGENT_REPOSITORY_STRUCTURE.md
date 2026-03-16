# Agent Repository Structure & Capability Catalog

> **Date**: March 3, 2026  
> **Purpose**: Centralized repository for agent discovery, capabilities, and reuse  
> **Location**: `/platform/agent-catalog/` and `/platform/java/agent-registry/`  
> **Architecture**: Clean integration with AEP (event processing) and Data-Cloud (storage)

---

## Repository Architecture

### Clean Architecture Principles

**NO backward compatibility** - all agents use new unified architecture:

| Component | Responsibility | Integration |
|-----------|---------------|-------------|
| **Agent Catalog** | Agent definitions, templates, examples | Stored in Data-Cloud |
| **Agent Registry** | Runtime discovery, capability matching | Uses Data-Cloud for persistence |
| **AEP Operators** | Event processing execution | Each catalog agent = AEP operator |
| **Data-Cloud** | ALL storage (metadata, state, memory) | Single storage backend |

---

## Agent Registry Implementation

### Module: `platform/java/agent-registry`

**Storage**: Uses **Data-Cloud exclusively** (no separate PostgreSQL/Redis)

```java
package com.ghatana.agent.registry;

/**
 * Central registry for agent discovery and capability management.
 * Uses Data-Cloud for ALL persistence - no separate storage layer.
 */
public interface AgentRegistry {
    
    Promise<AgentRegistrationResult> register(
        String tenantId, 
        AgentDescriptor descriptor
    );
    
    Promise<List<AgentMatch>> findByCapabilities(
        String tenantId,
        Set<Capability> requiredCapabilities,
        CapabilityMatchStrategy strategy
    );
    
    Promise<Optional<AgentDescriptor>> getAgent(
        String tenantId, 
        String agentId
    );
    
    Promise<AgentSearchResult> search(
        String tenantId,
        AgentSearchQuery query
    );
    
    Promise<Void> updateAgent(
        String tenantId,
        String agentId, 
        AgentUpdate update
    );
    
    Promise<Void> deregister(
        String tenantId,
        String agentId
    );
    
    Promise<RegistryStats> getStats(String tenantId);
}
```

#### Implementation Using Data-Cloud

```java
public class DataCloudAgentRegistry implements AgentRegistry {
    private final DataCloudClient dataCloud;
    
    // Collections in Data-Cloud
    private static final String REGISTRY_COLLECTION = "agent-registry";
    private static final String CAPABILITY_COLLECTION = "agent-capabilities";
    private static final String EXECUTION_LOG_COLLECTION = "agent-executions";
    
    @Override
    public Promise<AgentRegistrationResult> register(
            String tenantId, 
            AgentDescriptor descriptor) {
        
        // Store as Data-Cloud EntityRecord
        EntityRecord record = EntityRecord.builder()
            .tenantId(TenantId.of(tenantId))
            .type("agent-descriptor")
            .data(descriptor.toMap())
            .build();
            
        return dataCloud.save(REGISTRY_COLLECTION, record)
            .thenCompose(id -> indexCapabilities(tenantId, descriptor, id));
    }
    
    @Override
    public Promise<List<AgentMatch>> findByCapabilities(
            String tenantId,
            Set<Capability> requiredCapabilities,
            CapabilityMatchStrategy strategy) {
        
        // Query using Data-Cloud's query engine
        RecordQuery query = RecordQuery.builder()
            .tenantId(tenantId)
            .type("agent-descriptor")
            .filter(capabilityFilter(requiredCapabilities, strategy))
            .build();
            
        return dataCloud.query(REGISTRY_COLLECTION, query)
            .map(records -> records.stream()
                .map(r -> AgentMatch.fromRecord(r))
                .collect(Collectors.toList()));
    }
}
```

#### Data Storage in Data-Cloud

| Registry Data | Data-Cloud Record Type | Collection |
|--------------|----------------------|------------|
| Agent descriptors | `EntityRecord` | `agent-registry` |
| Capability index | `GraphRecord` | `agent-capabilities` |
| Execution history | `EventRecord` | `agent-executions` |
| Search cache | In-memory with Data-Cloud backing | N/A |

**NO separate storage infrastructure** - single Data-Cloud backend for all registry data.

---

## Agent Catalog Structure

### Physical Location: `platform/agent-catalog/`

```
platform/agent-catalog/
├── README.md                        # Catalog overview
├── catalog-schema.yaml              # Agent descriptor schema
├── core-agents/                     # Platform-provided agents
│   ├── data-processing/            # Data validation, transformation
│   │   ├── schema-validator/
│   │   ├── data-quality-checker/
│   │   └── duplicate-detector/
│   ├── event-processing/           # AEP-compatible operators
│   │   ├── event-transformer/
│   │   ├── event-enricher/
│   │   ├── event-filter/
│   │   └── event-router/
│   ├── monitoring/                 # Observability agents
│   │   ├── health-checker/
│   │   ├── metrics-collector/
│   │   └── anomaly-detector/
│   └── security/                   # Security agents
│       ├── access-control/
│       └── audit-logger/
├── domain-agents/                   # Domain-specific agents
│   ├── finance/
│   ├── healthcare/
│   ├── retail/
│   └── manufacturing/
├── composite-agents/                # Multi-agent workflows
│   └── data-pipelines/
├── templates/                       # Agent templates
│   ├── deterministic-agent/
│   ├── adaptive-agent/
│   └── composite-agent/
└── capabilities/                    # Capability definitions
    ├── taxonomy.yaml
    └── standard-capabilities.yaml
```

### Agent Definition Schema (AEP-Integrated)

Each agent in the catalog is an **AEP Operator**:

```yaml
# core-agents/data-processing/schema-validator/agent.yaml
agent:
  id: "schema-validator-v1"
  name: "Schema Validation Agent"
  version: "1.0.0"
  type: "DETERMINISTIC"
  description: "Validates JSON data against JSON Schema"
  
  # AEP Operator Configuration
  aep:
    operatorType: "TRANSFORMATION"
    inputEventTypes: 
      - "raw.json"
      - "unvalidated.data"
    outputEventTypes:
      - "validated.json"
      - "validation.errors"
    
    # Backpressure configuration
    backpressure:
      bufferSize: 1000
      overflowStrategy: "DROP_OLDEST"
      
    # Reliability configuration
    reliability:
      checkpointInterval: "30s"
      maxRetries: 3
      deadLetterQueue: "validation-errors-dlq"
  
  # Capabilities this agent provides
  capabilities:
    - name: "json-schema-validation"
      version: "1.0"
      type: "VALIDATION"
      parameters:
        strictMode: true
        
  # Input/Output specifications
  io:
    inputs:
      - name: "data"
        type: "JSON"
        schema: "https://json-schema.org/draft-07/schema#"
        required: true
    outputs:
      - name: "result"
        type: "VALIDATION_RESULT"
        schema: "https://schema.ghatana.com/validation-result/v1"
      
  # Resource requirements
  resources:
    memory: "512MB"
    cpu: "1 unit"
    timeout: "30s"
    maxConcurrency: 10
  
  # Metadata
  metadata:
    author: "platform-team@ghatana.ai"
    tags: ["validation", "schema", "json"]
    maturity: "production"
```

### Java Implementation (AEP Operator)

```java
package com.ghatana.agent.catalog.data.processing;

/**
 * Schema Validator Agent - AEP Operator implementation.
 * Executes as part of AEP pipeline with full resilience support.
 */
public class SchemaValidatorAgent 
    extends AbstractTypedAgent<JsonNode, ValidationResult> 
    implements StreamOperator {
    
    private final JsonSchemaValidator validator;
    private final ResilientTypedAgent<JsonNode, ValidationResult> resilientWrapper;
    
    @Override
    public AgentDescriptor descriptor() {
        return AgentDescriptor.builder()
            .agentId("schema-validator-v1")
            .name("Schema Validation Agent")
            .type(AgentType.DETERMINISTIC)
            .capability(Capability.jsonSchemaValidation())
            .build();
    }
    
    @Override
    public Promise<AgentResult<ValidationResult>> process(
            AgentContext ctx, 
            JsonNode input) {
        
        // Execute with circuit breaker
        return resilientWrapper.process(ctx, input);
    }
    
    // AEP Operator interface
    @Override
    public Promise<OperatorResult> process(Event event) {
        // Transform event to agent input
        JsonNode input = eventTransformer.transform(event);
        
        return process(agentContext, input)
            .map(result -> OperatorResult.success(
                eventTransformer.transformOutput(result)))
            .catchException(ex -> OperatorResult.failure(ex));
    }
}
```

---

## Pluggable Catalog Infrastructure

### Design Principle

**Catalog infrastructure in platform, agent definitions distributed across products.**

```
┌────────────────────────────────────────────────────────────────┐
│           PLATFORM: Catalog Infrastructure (SPI)                 │
│  platform/java/agent-framework/src/main/java/.../catalog/        │
│  ├── AgentCatalog.java              # SPI for catalog providers │
│  ├── CatalogLoader.java             # Discovers product catalogs│
│  ├── CatalogRegistry.java           # Aggregates all catalogs   │
│  └── AgentDescriptor.java           # Shared descriptor model   │
└────────────────────────────────────────────────────────────────┘
                              │
        ┌─────────────────────┼─────────────────────┐
        │                     │                     │
        ▼                     ▼                     ▼
┌──────────────┐     ┌──────────────┐     ┌──────────────┐
│   Platform   │     │     AEP      │     │  Data-Cloud  │
│   Catalog    │     │   Catalog    │     │   Catalog    │
│  platform/   │     │  products/   │     │  products/   │
│ /agent-catalog/│   │ /aep/agent-catalog/│ │ /data-cloud/ │
│              │     │              │     │ /agent-catalog/│
│ • Core agents│     │ • Operators  │     │ • Storage    │
│ • Templates  │     │ • Ingestion  │     │ • Query      │
│ • Base libs  │     │ • Routing    │     │ • Migration  │
└──────────────┘     └──────────────┘     └──────────────┘
```

### Core SPI (Platform)

**Location**: `platform/java/agent-framework/src/main/java/com/ghatana/agent/catalog/`

#### AgentCatalog Interface

```java
package com.ghatana.agent.catalog;

/**
 * SPI implemented by each catalog (platform, aep, data-cloud, products).
 * Enables runtime discovery and loading of agents from any module.
 */
public interface AgentCatalog {
    
    /** Unique catalog ID */
    String getCatalogId();
    
    /** Human-readable name */
    String getDisplayName();
    
    /** All agent descriptors in this catalog */
    Promise<List<AgentDescriptor>> listAgents();
    
    /** Get specific agent by ID (catalog-scoped) */
    Promise<Optional<AgentDescriptor>> getAgent(String agentId);
    
    /** Find agents by capability */
    Promise<List<AgentDescriptor>> findByCapability(Capability capability);
    
    /** Get agent implementation class for instantiation */
    Promise<Class<? extends TypedAgent<?, ?>>> getAgentClass(String agentId);
    
    /** Catalog metadata including taxonomy */
    CatalogMetadata getMetadata();
    
    /** Parent catalogs this extends (for capability inheritance) */
    List<String> getExtends();
}
```

#### CatalogRegistry (Aggregates All Catalogs)

```java
package com.ghatana.agent.catalog;

/**
 * Discovers and aggregates all AgentCatalog implementations.
 * Provides unified search across platform and product catalogs.
 */
public class CatalogRegistry {
    
    private final Map<String, AgentCatalog> catalogs = new ConcurrentHashMap<>();
    private final CatalogLoader loader;
    
    /**
     * Discovers catalogs via ServiceLoader at startup.
     */
    public Promise<Void> discoverCatalogs(Path repositoryRoot) {
        return loader.discoverCatalogFiles(repositoryRoot)
            .map(paths -> paths.stream()
                .map(this::createCatalogFromFile)
                .forEach(catalog -> catalogs.put(catalog.getCatalogId(), catalog)));
    }
    
    /**
     * Search across ALL catalogs with merged results.
     */
    public Promise<List<AgentMatch>> searchAll(AgentSearchQuery query) {
        List<Promise<List<AgentDescriptor>>> searches = catalogs.values().stream()
            .map(catalog -> catalog.listAgents())
            .collect(Collectors.toList());
            
        return Promises.toList(searches)
            .map(lists -> {
                List<AgentMatch> allMatches = new ArrayList<>();
                
                lists.stream()
                    .flatMap(List::stream)
                    .filter(agent -> matchesQuery(agent, query))
                    .forEach(agent -> {
                        String sourceCatalog = findSourceCatalog(agent);
                        allMatches.add(new AgentMatch(agent, sourceCatalog));
                    });
                    
                return allMatches;
            });
    }
    
    public Optional<AgentCatalog> getCatalog(String catalogId) {
        return Optional.ofNullable(catalogs.get(catalogId));
    }
    
    public Set<String> getAllCatalogIds() {
        return Collections.unmodifiableSet(catalogs.keySet());
    }
}
```

#### CatalogLoader

```java
package com.ghatana.agent.catalog.loader;

/**
 * Loads catalog definitions from agent-catalog.yaml files.
 */
public class CatalogLoader {
    
    private static final String CATALOG_FILE = "agent-catalog.yaml";
    
    /** Scans repository for all agent-catalog.yaml files. */
    public Promise<List<Path>> discoverCatalogFiles(Path root) {
        return FileScanner.find(root, "**/" + CATALOG_FILE);
    }
    
    /** Creates FileBasedCatalog from YAML definition. */
    public AgentCatalog loadFromFile(Path catalogYamlPath) {
        CatalogDefinition def = yamlMapper.readValue(catalogYamlPath.toFile(), 
            CatalogDefinition.class);
            
        Path basePath = catalogYamlPath.getParent();
        
        return FileBasedCatalog.builder()
            .catalogId(def.getId())
            .basePath(basePath)
            .agentPaths(def.getAgents())
            .metadata(def.getMetadata())
            .extends(def.getExtends())
            .build();
    }
}
```

### Platform Catalog Structure

**Platform provides base infrastructure and core agents**:

```
platform/agent-catalog/
├── agent-catalog.yaml               # Defines "platform" catalog
├── catalog-schema.yaml              # Shared schema for descriptors
├── capabilities/
│   ├── taxonomy.yaml               # Base capability taxonomy
│   └── standard-capabilities.yaml   # Platform capabilities
├── core-agents/                    # Platform-provided agents
│   ├── data-processing/
│   ├── event-processing/
│   ├── monitoring/
│   └── security/
└── templates/                      # Base templates
    ├── deterministic-agent/
    ├── adaptive-agent/
    └── composite-agent/
```

**platform/agent-catalog/agent-catalog.yaml**:
```yaml
catalog:
  id: "platform"
  name: "Ghatana Platform Core"
  version: "1.0.0"
  
  # This is the base catalog
  extends: []
  
  # Agent definition files (glob patterns)
  agents:
    - "core-agents/**/agent.yaml"
    
  # Templates for agent generation
  templates:
    - "templates/**/"
    
  # Capability taxonomy
  capabilities:
    taxonomy: "capabilities/taxonomy.yaml"
    definitions: "capabilities/standard-capabilities.yaml"
    
  metadata:
    maintainer: "platform-team@ghatana.ai"
    scope: "platform-wide"
```

### Product-Specific Catalogs

Each product defines its own catalog in `products/<product>/agent-catalog/`:

**Example: AEP Catalog** (`products/aep/agent-catalog/`)
```
products/aep/agent-catalog/
├── agent-catalog.yaml
├── capabilities/
│   └── aep-capabilities.yaml       # Extends platform taxonomy
├── operators/                      # AEP-specific agents
│   ├── ingestion/
│   │   ├── kafka-ingestion/
│   │   ├── http-ingestion/
│   │   └── file-ingestion/
│   ├── pattern-detection/
│   └── routing/
└── templates/
    └── aep-operator/
```

**products/aep/agent-catalog/agent-catalog.yaml**:
```yaml
catalog:
  id: "aep"
  name: "Agentic Event Processing"
  version: "1.0.0"
  
  # Extends platform catalog
  extends: ["platform"]
  
  agents:
    - "operators/**/agent.yaml"
    
  # AEP-specific capabilities extending base taxonomy
  capabilities:
    taxonomy: "capabilities/aep-capabilities.yaml"
    
  metadata:
    maintainer: "aep-team@ghatana.ai"
    scope: "event-processing"
```

**Example: Product Catalog (Finance)**
```
products/finance/agent-catalog/
├── agent-catalog.yaml
├── risk-agents/
│   ├── credit-risk/
│   └── market-risk/
├── compliance-agents/
└── trading-agents/
```

**YAPPC Agent System** (`config/agents/`) — **154 agents** (142 existing + 12 new)

YAPPC has a fully operational agent system under `config/agents/` using a **3-level hierarchy** with `id/generator/memory/tools/capabilities/routing/delegation` format. The catalog defines 12 new agents that fill gaps not covered by the existing 142.

**Lifecycle** (from `config/lifecycle/stages.yaml`): `intent → context → plan → execute → verify → observe → learn → institutionalize`

| Level | Existing | New | Total |
|-------|:--------:|:---:|:-----:|
| L1 Strategic | 13 | 1 (`full-lifecycle-orchestrator`) | 14 |
| L2 Domain Expert | 57 | 3 (security-posture, cloud-security-audit, compliance-audit orchestrators) | 60 |
| L3 Worker | 72 | 8 (cloud discovery/risk, compliance controls/gaps, vuln-scoring, project-onboarding, context-gathering, institutionalize) | 80 |
| **Total** | **142** | **12** | **154** |

New agents fill these **gaps in the existing 142**:
- **Cloud security**: no cloud resource inventory or risk scoring agents existed
- **Compliance**: no compliance framework/control evaluation agents existed  
- **Lifecycle routing**: `context` and `institutionalize` stages had no dedicated routing agents
- **Orchestration**: no cross-tool security posture aggregation or end-to-end lifecycle coordination

Full details: [`products/yappc/YAPPC_AGENT_CATALOG.md`](products/yappc/YAPPC_AGENT_CATALOG.md)

### Agent Descriptor Format

**Standard format used across ALL catalogs**:

```yaml
# Example: products/aep/agent-catalog/operators/kafka-ingestion/agent.yaml
agent:
  id: "kafka-ingestion-v1"            # Unique within catalog
  catalog: "aep"                        # Source catalog
  name: "Kafka Ingestion Operator"
  version: "1.0.0"
  type: "DETERMINISTIC"
  description: "Ingests events from Kafka topics"
  
  # AEP integration (optional)
  aep:
    operatorType: "INGESTION"
    inputEventTypes: []
    outputEventTypes: ["raw.kafka"]
    backpressure:
      bufferSize: 10000
      
  # Capabilities
  capabilities:
    - name: "kafka-ingestion"
      type: "INGESTION"
      
  # Resource requirements
  resources:
    memory: "1GB"
    cpu: "2"
    timeout: "0"  # Long-running
    
  # Implementation location
  implementation:
    class: "com.ghatana.aep.operator.ingestion.KafkaIngestionAgent"
    module: "products:aep:platform"     # Gradle module path
    
  # Metadata
  metadata:
    author: "aep-team@ghatana.ai"
    tags: ["kafka", "ingestion"]
    maturity: "production"
```

### Capability Categories

```yaml
# capabilities/taxonomy.yaml
capability_categories:
  - id: "data-processing"
    name: "Data Processing"
    description: "Capabilities for data manipulation and transformation"
    subcategories:
      - "validation"
      - "transformation" 
      - "aggregation"
      - "filtering"
      - "enrichment"
      
  - id: "event-processing"
    name: "Event Processing"
    description: "Capabilities for event stream processing"
    subcategories:
      - "routing"
      - "transformation"
      - "aggregation"
      - "pattern-matching"
      - "correlation"
      
  - id: "analytics"
    name: "Analytics"
    description: "Capabilities for data analysis and insights"
    subcategories:
      - "statistical-analysis"
      - "machine-learning"
      - "anomaly-detection"
      - "forecasting"
      - "reporting"
      
  - id: "integration"
    name: "Integration"
    description: "Capabilities for system integration"
    subcategories:
      - "database-connectors"
      - "api-integrations"
      - "message-queues"
      - "file-systems"
      - "external-services"
      
  - id: "monitoring"
    name: "Monitoring"
    description: "Capabilities for system monitoring and alerting"
    subcategories:
      - "health-checks"
      - "metrics-collection"
      - "log-analysis"
      - "alerting"
      - "performance-monitoring"
```

### Standard Capability Definitions

```yaml
# capabilities/standard-capabilities.yaml
standard_capabilities:
  - id: "json-schema-validation"
    name: "JSON Schema Validation"
    category: "data-processing.validation"
    version: "1.0"
    description: "Validates JSON data against JSON Schema specification"
    parameters:
      - name: "strictMode"
        type: "boolean"
        default: false
        description: "Enable strict validation mode"
      - name: "collectErrors"
        type: "boolean" 
        default: true
        description: "Collect all validation errors"
        
  - id: "event-routing"
    name: "Event Routing"
    category: "event-processing.routing"
    version: "1.0"
    description: "Routes events based on content-based rules"
    parameters:
      - name: "routingRules"
        type: "array"
        required: true
        description: "List of routing rules"
      - name: "defaultRoute"
        type: "string"
        description: "Default route when no rules match"
        
  - id: "statistical-analysis"
    name: "Statistical Analysis"
    category: "analytics.statistical-analysis"
    version: "1.0"
    description: "Performs statistical analysis on numeric data"
    parameters:
      - name: "analysisType"
        type: "enum"
        values: ["descriptive", "inferential", "regression"]
        required: true
      - name: "confidenceLevel"
        type: "float"
        default: 0.95
        description: "Confidence level for statistical tests"
```

---

## Agent Development Tools

### Agent Generator CLI

```bash
# Generate new agent from template
ghatana agent generate \
  --template deterministic-agent \
  --name "my-validator" \
  --package "com.mycompany.agents" \
  --capabilities "json-schema-validation,data-transformation"

# Validate agent descriptor
ghatana agent validate \
  --descriptor agent.yaml \
  --schema catalog-schema.yaml

# Test agent locally
ghatana agent test \
  --descriptor agent.yaml \
  --test-data test-inputs/

# Publish agent to registry
ghatana agent publish \
  --descriptor agent.yaml \
  --registry "production"
```

### Agent Testing Framework

```java
// testing/AgentTestFramework.java
/**
 * Testing framework for agent validation and integration testing.
 */
public class AgentTestFramework {
    
    public static <I, O> AgentTestBuilder<I, O> forAgent(Class<? extends TypedAgent<I, O>> agentClass) {
        return new AgentTestBuilder<>(agentClass);
    }
    
    public static class AgentTestBuilder<I, O> {
        public AgentTestBuilder<I, O> withInput(I input) { /* ... */ }
        public AgentTestBuilder<I, O> expectOutput(O expected) { /* ... */ }
        public AgentTestBuilder<I, O> expectError(String errorPattern) { /* ... */ }
        public AgentTestBuilder<I, O> withCapability(Capability cap) { /* ... */ }
        public TestResult run() { /* ... */ }
    }
}

// Usage example
@Test
public void testSchemaValidator() {
    TestResult result = AgentTestFramework
        .forAgent(SchemaValidator.class)
        .withInput(validJsonInput)
        .expectOutput(expectedValidationResult)
        .withCapability(Capability.jsonSchemaValidation())
        .run();
        
    assertTrue(result.isSuccess());
}
```

---

## Governance and Quality Standards

### Agent Review Process

```yaml
# governance/review-process.yaml
agent_review_process:
  stages:
    - name: "code-review"
      description: "Code quality and architecture review"
      reviewers: ["senior-developer", "architect"]
      checklist:
        - "Code follows platform conventions"
        - "Error handling is comprehensive"
        - "Performance requirements met"
        - "Security best practices followed"
        
    - name: "capability-review"  
      description: "Capability definition and interface review"
      reviewers: ["product-owner", "domain-expert"]
      checklist:
        - "Capability definition is clear"
        - "Input/output specifications are complete"
        - "Resource requirements are realistic"
        - "Documentation is comprehensive"
        
    - name: "security-review"
      description: "Security and compliance review"
      reviewers: ["security-team", "compliance-officer"]
      checklist:
        - "No sensitive data exposure"
        - "Input validation is robust"
        - "Access controls are appropriate"
        - "Compliance requirements met"
        
    - name: "performance-review"
      description: "Performance and scalability review"
      reviewers: ["performance-engineer", "ops-team"]
      checklist:
        - "Performance targets achievable"
        - "Resource usage is optimized"
        - "Scaling characteristics understood"
        - "Monitoring requirements defined"
```

### Quality Metrics

| Metric | Target | Measurement |
|--------|--------|-------------|
| **Code Coverage** | > 85% | Unit test coverage |
| **Performance** | Meets SLA | Benchmark tests |
| **Documentation** | 100% complete | Documentation coverage |
| **Security** | Zero critical issues | Security scan results |
| **Compliance** | 100% compliant | Compliance checklist |

---

## Integration with Existing Systems

### Registry Integration Points

```java
// Integration with existing TypedAgent framework
public class RegistryAwareTypedAgent<I, O> extends AbstractTypedAgent<I, O> {
    
    private final AgentRegistry registry;
    private final String agentId;
    
    @Override
    public Promise<Void> initialize(AgentConfig config) {
        // Auto-register with capability discovery
        return registry.register(getTenantId(), discoverDescriptor())
            .thenCompose(registration -> super.initialize(config));
    }
    
    private AgentDescriptor discoverDescriptor() {
        // Auto-generate descriptor from annotations and reflection
        return AgentDescriptorBuilder
            .fromClass(getClass())
            .withConfig(config)
            .build();
    }
}
```

### Event Cloud Integration

```java
// Agent registry events for monitoring
public class RegistryEventPublisher {
    
    public void publishAgentRegistered(AgentRegistrationEvent event) {
        EventRecord registryEvent = EventRecord.builder()
            .tenantId(event.getTenantId())
            .typeRef(EventTypeRef.of("agent.registered", Version.of("1.0")))
            .payload(ByteBuffer.wrap(toJson(event)))
            .build();
            
        eventCloud.append(new AppendRequest(registryEvent, AppendOptions.defaults()));
    }
}
```

---

## Migration Strategy

### Phase 1: Registry Foundation (Weeks 1-2)
1. Implement core `AgentRegistry` interface
2. Set up PostgreSQL schema and Redis caching
3. Create basic registry client SDK
4. Implement agent auto-discovery

### Phase 2: Catalog Seeding (Weeks 3-4)
1. Migrate existing platform agents to catalog format
2. Create agent templates and generators
3. Implement capability taxonomy
4. Set up agent testing framework

### Phase 3: Integration (Weeks 5-6)
1. Integrate with existing `TypedAgent` framework
2. Add registry events to EventCloud
3. Implement agent lifecycle management
4. Create monitoring and alerting

### Phase 4: Governance (Weeks 7-8)
1. Implement review process automation
2. Set up quality gates and CI/CD integration
3. Create documentation and examples
4. Roll out to development teams

---

## Success Metrics

### Adoption Metrics
- **Agent Registration Rate**: Number of agents registered per week
- **Catalog Usage**: Agent discovery and lookup frequency
- **Template Usage**: Number of agents created from templates

### Quality Metrics  
- **Agent Success Rate**: Percentage of successful agent executions
- **Performance Compliance**: Agents meeting performance targets
- **Documentation Coverage**: Completeness of agent documentation

### Developer Experience
- **Time to First Agent**: Time from idea to working agent
- **Discovery Efficiency**: Time to find suitable existing agents
- **Reusability Index**: Percentage of agents reused across projects

---

## Conclusion

The agent repository and capability catalog will significantly improve agent development efficiency, promote reuse, and ensure quality across the Ghatana platform. By providing standardized templates, comprehensive testing tools, and a centralized registry, developers can rapidly discover, create, and deploy high-quality agents.

The structured approach to capability management and governance ensures that agents meet platform standards while enabling innovation and rapid development cycles. This positions Ghatana as a leading platform for enterprise-grade agent development and deployment.

---

## Implementation Progress (as of 2026-01-19)

> Updated automatically by AI agent.

| Area | Completed Items | Status |
|------|----------------|--------|
| YAPPC Frontend hardening | Canvas, Backlog, Notifications, Deploy, Lifecycle, Shell | ✅ All 6 files done |
| DCMAAR RN stores | Auth, Device, Apps, Policy, WebSocket, Monitoring, Permissions, Usage | ✅ All 8 stores done |
| Tutorputor AI model config | `ASSESSMENT_MODEL_ID` env var wired into assessment-service + config schema | ✅ Done |
| AEP Java adapters | `FileBasedEventHistory` implemented; `ValidationEngine` doc updated | ✅ Done |

