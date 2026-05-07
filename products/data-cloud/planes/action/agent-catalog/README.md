# AEP Operator Catalog

**Version:** 1.0.0  
**Last Updated:** 2026-05-02  
**Maintainer:** AEP Platform Team

---

## Overview

The AEP Operator Catalog is a comprehensive collection of reusable operators for building event processing pipelines in the Agentic Event Processing (AEP) platform. Operators are the fundamental building blocks that perform specific transformations, routing, pattern detection, or orchestration tasks within pipelines.

---

## Operator Categories

### Ingestion Operators
Operators that ingest events from external sources into AEP.

| Operator | Description | Status |
|-----------|-------------|--------|
| HTTP Ingestion Agent | Receives events via HTTP/REST endpoints with webhook support | ✅ Active |
| Kafka Ingestion Agent | Consumes events from Kafka topics with consumer group management | ✅ Active |

### Transformation Operators
Operators that transform, parse, or enrich event data.

| Operator | Description | Status |
|-----------|-------------|--------|
| Field Mapper Operator | Maps and renames event fields based on configuration schema | ✅ Active |
| JSON Parser Operator | Parses JSON payloads and validates against schema definitions | ✅ Active |
| Enrichment Operator | Enriches events with additional data from external sources | ✅ Active |

### Routing Operators
Operators that route events based on conditions or patterns.

| Operator | Description | Status |
|-----------|-------------|--------|
| Filter Operator | Filters events based on configurable conditions and expressions | ✅ Active |
| Split Operator | Splits event streams into multiple branches based on routing rules | ✅ Active |

### Pattern Operators
Operators that detect patterns, anomalies, or correlations in event streams.

| Operator | Description | Status |
|-----------|-------------|--------|
| Anomaly Detection Agent | Detects statistical and behavioral anomalies using multi-model scoring | ✅ Active |
| Correlation Agent | Correlates events across time windows to find relationships | ✅ Active |
| Pattern Detection Agent | Matches event patterns using configurable rule sets | ✅ Active |

### Orchestration Operators
Operators that coordinate complex workflows and manage dependencies.

| Operator | Description | Status |
|-----------|-------------|--------|
| DAG Executor Operator | Executes directed acyclic graph (DAG) workflows with dependency management | ✅ Active |

### Aggregation Operators
Operators that aggregate events over time windows.

| Operator | Description | Status |
|-----------|-------------|--------|
| Count Operator | Counts events within time windows with configurable grouping | ✅ Active |
| Sum Operator | Sums numeric field values within time windows | ✅ Active |
| Average Operator | Calculates average of numeric field values within time windows | ✅ Active |

---

## Catalog Statistics

- **Total Operators:** 13
- **Categories:** 6
- **Active Operators:** 13 (100%)
- **Deprecated Operators:** 0
- **Experimental Operators:** 0

---

## Using the Catalog

### Discovering Operators

Operators are organized by category in the `operators/` directory:

```
agent-catalog/
├── operators/
│   ├── ingestion/
│   │   ├── http-ingestion-agent.yaml
│   │   └── kafka-ingestion-agent.yaml
│   ├── transformation/
│   │   ├── field-mapper-operator.yaml
│   │   ├── json-parser-operator.yaml
│   │   └── enrichment-operator.yaml
│   ├── routing/
│   │   ├── filter-operator.yaml
│   │   └── split-operator.yaml
│   ├── pattern/
│   │   ├── anomaly-detection-agent.yaml
│   │   ├── correlation-agent.yaml
│   │   └── pattern-detection-agent.yaml
│   ├── orchestration/
│   │   └── dag-executor-operator.yaml
│   └── aggregation/
│       ├── count-operator.yaml
│       ├── sum-operator.yaml
│       └── average-operator.yaml
```

### Loading Operators

Operators are automatically loaded by the AEP platform through the catalog configuration in `agent-catalog.yaml`:

```yaml
catalog:
  id: "aep"
  name: "Agentic Event Processing Catalog"
  version: "1.0.0"
  
  agents:
    - "operators/**/*-agent.yaml"
    - "operators/**/*-operator.yaml"
```

### Using Operators in Pipelines

Operators can be referenced in pipeline definitions by their ID:

```yaml
pipeline:
  id: my-pipeline
  name: My Event Processing Pipeline
  operators:
    - id: http-ingestion-agent
      config:
        endpoint: /events
        validation: true
    - id: json-parser-operator
      config:
        schema: event-schema.json
    - id: field-mapper-operator
      config:
        mappings:
          sourceField: targetField
    - id: anomaly-detection-agent
      config:
        threshold: 0.8
        window: "10m"
```

---

## Operator Development

### Creating Custom Operators

For guidance on creating custom operators, see the [Operator SDK Guide](./docs/OPERATOR_SDK_GUIDE.md).

### Operator Definition Schema

All operators follow a consistent YAML schema with the following sections:

- **Identity:** Operator type, subtype, determinism guarantee, state mutability
- **AEP Configuration:** Operator type, input/output event types, backpressure, reliability
- **Capabilities:** Supported capabilities and features
- **Tools:** Services and endpoints used by the operator
- **Interfaces:** Input and output schemas
- **Memory:** Memory type, scope, retention, and state keys
- **Resources:** Memory, CPU, timeout, and scaling configuration

### Testing Operators

All operators should include:
- Unit tests for core logic
- Integration tests with real event data
- Performance tests for expected load
- Documentation with usage examples

---

## Capabilities

The catalog defines the following capabilities:

### AEP Capabilities

Defined in `capabilities/aep-capabilities.yaml`:
- `http-ingestion`: HTTP webhook ingestion
- `event-validation`: Schema-based validation
- `field-mapping`: Field renaming and mapping
- `type-conversion`: Data type conversion
- `default-values`: Default value handling
- `json-parsing`: JSON parsing and validation
- `schema-validation`: Schema-based validation
- `field-extraction`: Nested field extraction
- `data-enrichment`: External data enrichment
- `external-lookup`: External service lookups
- `caching`: Result caching
- `event-filtering`: Conditional event filtering
- `condition-evaluation`: Expression evaluation
- `expression-parsing`: Expression parsing
- `stream-splitting`: Stream branching
- `rule-based-routing`: Rule-based routing
- `branch-management`: Branch management
- `dag-execution`: DAG workflow execution
- `dependency-resolution`: Dependency management
- `checkpoint-recovery`: Checkpoint-based recovery
- `failure-handling`: Failure handling
- `event-counting`: Event counting
- `time-windowing`: Time-based windowing
- `grouping`: Field-based grouping
- `numeric-aggregation`: Numeric field aggregation
- `sum-calculation`: Sum calculation
- `average-calculation`: Average calculation
- `multi-field`: Multi-field processing
- `anomaly-detection`: Anomaly detection
- `statistical-baseline`: Statistical baseline maintenance
- `alert-generation`: Alert generation

---

## Versioning

Operators follow semantic versioning (MAJOR.MINOR.PATCH):

- **MAJOR:** Breaking changes to operator interface or behavior
- **MINOR:** New features or capabilities added
- **PATCH:** Bug fixes or documentation updates

When upgrading operators in pipelines:
- MAJOR version changes require manual review and testing
- MINOR version changes are backward compatible
- PATCH version changes are safe to apply automatically

---

## Deprecation Policy

Operators may be deprecated when:
- A newer operator provides better functionality
- The operator is no longer maintained
- Security vulnerabilities are discovered

Deprecation process:
1. Mark operator as `status: deprecated` in YAML
2. Add migration guide to documentation
3. Maintain deprecated operator for at least 2 minor versions
4. Remove operator after migration period

---

## Support and Contributing

### Getting Help

- Documentation: [Operator SDK Guide](./docs/OPERATOR_SDK_GUIDE.md)
- Issues: [GitHub Issues](https://github.com/ghatana/aep/issues)
- Contact: aep-team@ghatana.com

### Contributing Operators

To contribute a new operator to the catalog:

1. Follow the [Operator SDK Guide](./docs/OPERATOR_SDK_GUIDE.md)
2. Create operator YAML file in appropriate category directory
3. Implement operator logic in Java
4. Add comprehensive tests
5. Submit PR with documentation
6. Request review from AEP platform team

### Contribution Checklist

Before submitting, ensure:
- [ ] Operator YAML follows the schema
- [ ] All required fields are present
- [ ] Description is clear and comprehensive
- [ ] Capabilities are defined
- [ ] Interfaces match implementation
- [ ] Resource requirements are realistic
- [ ] Unit tests cover happy path
- [ ] Unit tests cover error cases
- [ ] Integration tests included
- [ ] Performance tests included
- [ ] Documentation includes examples
- [ ] Owner contact is current

---

## Roadmap

### Upcoming Operators (Planned)

- WebSocket Ingestion Agent
- Router Operator (advanced routing)
- Fan-out/Fan-in Operator
- Session Window Operator
- Join Operator
- Deduplication Operator
- Rate Limiting Operator
- Schema Evolution Operator

### Enhancements

- Operator performance optimization
- Enhanced caching strategies
- Advanced windowing functions
- Machine learning integration
- Real-time operator metrics

---

## License

Copyright (c) 2026 Ghatana Inc. All rights reserved.

---

**Catalog Version:** 1.0.0  
**Last Updated:** 2026-05-02  
**Maintained By:** AEP Platform Team
