# AEP Operator SDK Guide

**Version:** 1.0.0  
**Last Updated:** 2026-05-02  
**Target Audience:** Operators Developers, Pipeline Engineers

---

## Overview

The AEP Operator SDK provides tools, templates, and guidelines for creating custom operators for the Agentic Event Processing platform. Operators are the building blocks of event processing pipelines, performing specific transformations, routing, or analysis tasks.

---

## Operator Types

AEP supports the following operator types:

| Type | Purpose | Examples |
|------|---------|----------|
| **INGESTION** | Ingest events from external sources | HTTP Ingestion, Kafka Ingestion |
| **TRANSFORMATION** | Transform event data | JSON Parser, Field Mapper, Enrichment |
| **ROUTING** | Route events based on conditions | Filter, Split, Router |
| **PATTERN** | Detect patterns and anomalies | Anomaly Detection, Correlation, Pattern Matching |
| **ORCHESTRATION** | Coordinate complex workflows | DAG Executor, Fan-out/Fan-in |
| **AGGREGATION** | Aggregate events over time windows | Count, Sum, Average, Session Window |

---

## Operator Definition Schema

Every operator is defined using a YAML file with the following structure:

### Required Fields

```yaml
id: my-operator                    # Unique operator identifier
namespace: aep.operators.category  # Namespace for categorization
name: My Operator                  # Human-readable name
version: 1.0.0                     # Semantic version
status: active                     # active | deprecated | experimental
owners:                            # Ownership information
  - team: your-team
    contact: your-team@ghatana.com
summary: Brief one-line description
description: |
  Detailed multi-line description
  of what the operator does.
```

### Identity Section

```yaml
identity:
  agentType: stream_processor      # Agent type from taxonomy
  subtype: transformation           # Specific subtype
  determinismGuarantee: full       # full | config-scoped | none
  stateMutability: local-state     # local-state | stateless | shared-state
  failureMode: dead-letter         # dead-letter | drop | retry
  criticality: high               # high | medium | low
  autonomyLevel: autonomous        # autonomous | semi-autonomous | manual
```

### AEP-Specific Configuration

```yaml
aep:
  operatorType: TRANSFORMATION     # Operator type category
  inputEventTypes: ["raw.http"]    # Accepted event types
  outputEventTypes: ["transformed"] # Produced event types
  backpressure:
    bufferSize: 5000                # Buffer size for backpressure
    overflowStrategy: DROP_LATEST  # DROP_LATEST | BLOCK | DROP_OLDEST
  reliability:
    checkpointInterval: "10s"       # Checkpoint interval
    maxRetries: 2                   # Maximum retry attempts
    deadLetterQueue: "operator-dlq" # Dead letter queue name
```

### Capabilities

```yaml
capabilities:
  - capability-name-1
  - capability-name-2
```

### Tools and Services

```yaml
tools:
  - name: myService
    type: SERVICE                   # SERVICE | MODEL | EXTERNAL_API
    endpoint: /aep/my/service
```

### Interfaces

```yaml
interfaces:
  inputs:
    - name: inputEvent
      schema: raw.http
      required: true
  outputs:
    - name: outputEvent
      schema: transformed
```

### Memory Configuration

```yaml
memory:
  type: WORKING                     # WORKING | EPISODIC | LONG_TERM
  scope: request_buffer             # Memory scope
  retention: short                  # short | medium | long
  ttl: "1h"                        # Time-to-live (optional)
  stateKeys:                        # State keys (optional)
    - "buffer"
    - "metadata"
```

### Resources

```yaml
resources:
  memory: "512MB"                  # Memory requirement
  cpu: "1"                         # CPU requirement
  timeout: "0"                     # Timeout (0 = no timeout)
  scaling:                         # Scaling configuration (optional)
    minReplicas: 1
    maxReplicas: 4
    metric: "queue_depth"
    threshold: 1000
```

---

## Creating a Custom Operator

### Step 1: Create the Operator Definition File

Create a new YAML file in the appropriate category directory:

```
agent-catalog/operators/[category]/my-operator.yaml
```

### Step 2: Define the Operator

Use the schema above to define your operator. Here's a complete example:

```yaml
id: field-mapper-operator
namespace: aep.operators.transformation
name: Field Mapper Operator
version: 1.0.0
status: active
owners:
  - team: data-engineering
    contact: data-eng@ghatana.com
summary: Maps and renames event fields based on a configuration schema
description: |
  Transforms event data by mapping source fields to destination fields
  with optional type conversion and default value support.

metadata:
  level: 2
  domain: event-processing
  tags: [transformation, mapping, field-renaming]

identity:
  agentType: deterministic
  subtype: transformation
  determinismGuarantee: full
  stateMutability: stateless
  failureMode: drop
  criticality: medium
  autonomyLevel: autonomous

aep:
  operatorType: TRANSFORMATION
  inputEventTypes: ["*"]
  outputEventTypes: ["mapped"]
  backpressure:
    bufferSize: 10000
    overflowStrategy: DROP_LATEST
  reliability:
    checkpointInterval: "30s"
    maxRetries: 1
    deadLetterQueue: "field-mapper-dlq"

capabilities:
  - field-mapping
  - type-conversion
  - default-values

tools:
  - name: fieldMapper
    type: SERVICE
    endpoint: /aep/transform/field-map

interfaces:
  inputs:
    - name: sourceEvent
      schema: "*"
      required: true
  outputs:
    - name: mappedEvent
      schema: mapped

memory:
  type: WORKING
  scope: mapping_config
  retention: medium
  ttl: "24h"
  stateKeys:
    - "mappingRules"

resources:
  memory: "1GB"
  cpu: "2"
  timeout: "30s"
  scaling:
    minReplicas: 1
    maxReplicas: 8
    metric: "mapping_queue_depth"
    threshold: 5000
```

### Step 3: Implement the Operator Logic

Implement the operator logic in Java using the AEP operator framework:

```java
/**
 * Field Mapper Operator
 *
 * @doc.type class
 * @doc.purpose Maps and renames event fields based on configuration
 * @doc.layer product
 * @doc.pattern Operator
 */
public class FieldMapperOperator extends AbstractTypedAgent<Event, Event> {
    
    private final Map<String, String> fieldMappings;
    private final Map<String, Object> defaultValues;
    
    public FieldMapperOperator(
        Map<String, String> fieldMappings,
        Map<String, Object> defaultValues
    ) {
        this.fieldMappings = fieldMappings;
        this.defaultValues = defaultValues;
    }
    
    @Override
    public Promise<AgentResult<Event>> process(AgentContext ctx, Event input) {
        try {
            Map<String, Object> mappedData = new HashMap<>();
            
            // Apply field mappings
            for (Map.Entry<String, String> entry : fieldMappings.entrySet()) {
                String sourceField = entry.getKey();
                String targetField = entry.getValue();
                
                Object value = input.getData().get(sourceField);
                if (value != null) {
                    mappedData.put(targetField, value);
                } else if (defaultValues.containsKey(targetField)) {
                    mappedData.put(targetField, defaultValues.get(targetField));
                }
            }
            
            // Create mapped event
            Event mappedEvent = Event.builder()
                .type("mapped")
                .data(mappedData)
                .timestamp(Instant.now())
                .tenantId(ctx.getTenantId())
                .build();
                
            return Promise.of(AgentResult.success(mappedEvent));
            
        } catch (Exception e) {
            return Promise.of(AgentResult.failure(e.getMessage()));
        }
    }
}
```

### Step 4: Register the Operator

Register the operator in the agent catalog by ensuring the YAML file is in the correct location and follows the naming convention `*-agent.yaml`.

### Step 5: Test the Operator

Create unit and integration tests for your operator:

```java
@DisplayName("Field Mapper Operator Tests")
class FieldMapperOperatorTest extends EventloopTestBase {
    
    @Test
    void shouldMapFieldsCorrectly() {
        Map<String, String> mappings = Map.of(
            "sourceField", "targetField"
        );
        
        FieldMapperOperator operator = new FieldMapperOperator(mappings, Map.of());
        AgentContext ctx = AgentContext.builder()
            .tenantId("test-tenant")
            .build();
            
        Event input = Event.builder()
            .type("raw")
            .data(Map.of("sourceField", "value"))
            .timestamp(Instant.now())
            .tenantId("test-tenant")
            .build();
            
        AgentResult<Event> result = runPromise(() -> 
            operator.process(ctx, input)
        );
        
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput().getData())
            .containsEntry("targetField", "value");
    }
}
```

---

## Operator Best Practices

### 1. Determinism

- Prefer deterministic operators when possible
- Document any non-deterministic behavior (e.g., random sampling)
- Use config-scoped determinism for operators with optional LLM components

### 2. State Management

- Minimize state usage for better scalability
- Use appropriate memory types:
  - `WORKING`: Short-lived request state
  - `EPISODIC`: Learning and pattern history
  - `LONG_TERM`: Persistent configuration and baselines

### 3. Error Handling

- Always handle exceptions gracefully
- Use appropriate failure modes:
  - `dead-letter`: For critical operations where events must not be lost
  - `drop`: For non-critical operations where some loss is acceptable
  - `retry`: For transient failures

### 4. Backpressure

- Configure appropriate buffer sizes
- Choose overflow strategy based on use case:
  - `DROP_LATEST`: Prefer freshest data (real-time monitoring)
  - `BLOCK`: Ensure no data loss (analytics pipelines)
  - `DROP_OLDEST`: Maintain recent history (windowed operations)

### 5. Resource Configuration

- Set realistic memory and CPU requirements
- Configure scaling for high-throughput operators
- Use timeouts to prevent runaway operations

### 6. Documentation

- Provide clear summaries and descriptions
- Document all configuration parameters
- Include examples in the description
- Tag with relevant keywords for discoverability

---

## Operator Testing Checklist

Before submitting an operator to the catalog, ensure:

- [ ] YAML definition follows the schema
- [ ] All required fields are present
- [ ] Description is clear and comprehensive
- [ ] Capabilities are defined
- [ ] Interfaces match the implementation
- [ ] Resource requirements are realistic
- [ ] Unit tests cover happy path
- [ ] Unit tests cover error cases
- [ ] Integration tests with real event data
- [ ] Performance tests for expected load
- [ ] Documentation includes usage examples
- [ ] Owner contact information is current

---

## Operator Categories

### Ingestion Operators
- HTTP Ingestion Agent
- Kafka Ingestion Agent
- WebSocket Ingestion Agent (planned)

### Transformation Operators
- Field Mapper Operator (planned)
- JSON Parser Operator (planned)
- Enrichment Operator (planned)

### Routing Operators
- Filter Operator (planned)
- Split Operator (planned)
- Router Operator (planned)

### Pattern Operators
- Anomaly Detection Agent
- Correlation Agent
- Pattern Detection Agent

### Orchestration Operators
- DAG Executor (planned)
- Fan-out/Fan-in (planned)

### Aggregation Operators
- Count Operator (planned)
- Sum Operator (planned)
- Average Operator (planned)
- Session Window Operator (planned)

---

## Support and Contributing

For questions about operator development:
- Contact: aep-team@ghatana.com
- Documentation: [AEP Operator Documentation](./README.md)
- Issues: [GitHub Issues](https://github.com/ghatana/aep/issues)

To contribute a new operator:
1. Follow this guide to create the operator
2. Submit a PR with the operator definition
3. Include tests and documentation
4. Request review from the AEP platform team

---

**Document Version:** 1.0.0  
**Last Updated:** 2026-05-02  
**Maintained By:** AEP Platform Team
