# Data Cloud Agent Catalog

The Data Cloud Agent Catalog defines agents specific to the Data Cloud product. It extends the platform catalog with data management, storage, and operational capabilities.

## Overview

This catalog contains YAML-based agent definitions for Data Cloud-specific functionality including:
- **Storage Agents**: Entity storage, event stream storage, data compaction
- **Query Agents**: Cache management, query optimization
- **Migration Agents**: Data migration, schema evolution
- **Replication Agents**: Data replication across storage systems
- **Observability Agents**: Data lineage, data quality monitoring
- **Orchestration Agents**: Data pipeline orchestration, unified data orchestration
- **Archival Agents**: Data archival and retention policies

## Catalog Structure

```
agent-catalog/
├── agent-catalog.yaml              # Main catalog configuration
├── capabilities/                   # Data Cloud-specific capabilities
│   └── data-cloud-capabilities.yaml
├── definitions/                    # Agent definition files
│   ├── archival/
│   ├── migration/
│   ├── observability/
│   ├── orchestration/
│   ├── query/
│   ├── replication/
│   └── storage/
└── src/main/java/.../validation/   # Validation logic
    └── AgentDefinitionValidator.java
```

## Agent Definition Schema

Each agent definition YAML file contains the following sections:

### Required Fields

- `id`: Unique identifier (lowercase alphanumeric with hyphens)
- `namespace`: Namespace for the agent (e.g., `data-cloud.storage`)
- `name`: Human-readable name
- `version`: Semantic version (e.g., `1.0.0`)
- `status`: One of `active`, `deprecated`, `experimental`, `draft`

### Identity Section

- `agentType`: One of `deterministic`, `adaptive`, `probabilistic`, `hybrid`
- `subtype`: One of `rule-based`, `ml-based`, `hybrid`, `orchestrated`
- `determinismGuarantee`: One of `full`, `partial`, `none`
- `stateMutability`: One of `stateless`, `internal-state`, `external-state`
- `failureMode`: One of `fail-fast`, `graceful-degradation`, `best-effort`, `circuit-breaker`
- `criticality`: One of `low`, `medium`, `high`, `critical`
- `autonomyLevel`: One of `manual`, `semi-autonomous`, `autonomous`, `fully-autonomous`

### Capabilities

List of capabilities provided by the agent. These must be defined in the capabilities taxonomy file.

### Tools

List of tools used by the agent:
- `name`: Tool identifier
- `type`: One of `SERVICE`, `DATABASE`, `FILE`, `EXTERNAL_API`, `PLUGIN`
- `endpoint`: Tool endpoint or resource path

### Interfaces

Define input and output schemas:
- `inputs`: Array of input interface definitions
- `outputs`: Array of output interface definitions

### Memory

Memory configuration:
- `type`: One of `NONE`, `WORKING`, `EPISODIC`, `LONG_TERM`
- `scope`: Memory scope identifier
- `retention`: One of `none`, `short`, `medium`, `long`, `permanent`

### Resources

Resource requirements:
- `memory`: Memory allocation (e.g., `2GB`, `512MB`)
- `cpu`: CPU cores (e.g., `4`, `2.5`)
- `timeout`: Operation timeout (e.g., `30s`, `500ms`)

## Validation

Agent definitions are validated using the `AgentDefinitionValidator` class. The validator checks:

- **Required fields**: All required fields are present and non-empty
- **Format validation**: IDs, versions, and other fields match expected formats
- **Enum validation**: Enum fields use valid values
- **Structure validation**: Arrays and nested structures are correctly formatted
- **Business rules**: Email addresses, resource formats, and other business rules

### Running Validation

```java
import com.ghatana.datacloud.agent.catalog.validation.AgentDefinitionValidator;

Path definitionPath = Path.of("definitions/storage/entity-storage-agent.yaml");
AgentDefinitionValidator.ValidationResult result = AgentDefinitionValidator.validate(definitionPath);

if (result.isValid()) {
    System.out.println("Definition is valid");
} else {
    System.out.println("Errors: " + result.getErrorMessage());
}

if (result.hasWarnings()) {
    System.out.println("Warnings: " + result.getWarningMessage());
}
```

## Adding a New Agent

1. Create a new YAML file in the appropriate `definitions/` subdirectory
2. Follow the agent definition schema
3. Run validation to ensure correctness
4. Add any new capabilities to the capabilities taxonomy if needed
5. Update the catalog version in `agent-catalog.yaml`

### Example Agent Definition

```yaml
id: my-new-agent
namespace: data-cloud.example
name: My New Agent
version: 1.0.0
status: active
owners:
  - team: data-cloud-platform
    contact: data-cloud-team@ghatana.com
summary: Brief description of the agent
description: Detailed description of the agent's purpose and behavior.

metadata:
  level: 3
  domain: example
  tags: [example, agent, capability]

identity:
  agentType: deterministic
  subtype: rule-based
  determinismGuarantee: full
  stateMutability: external-state
  failureMode: fail-fast
  criticality: medium
  autonomyLevel: semi-autonomous

capabilities:
  - example-capability

tools:
  - name: exampleTool
    type: SERVICE
    endpoint: /data-cloud/example/tool

interfaces:
  inputs:
    - name: exampleInput
      schema: object
      required: true
  outputs:
    - name: exampleOutput
      schema: object

memory:
  type: WORKING
  scope: example_cache
  retention: short

resources:
  memory: "1GB"
  cpu: "2"
  timeout: "10s"
```

## Extending the Platform Catalog

The Data Cloud catalog extends the platform catalog. This means:

- All platform capabilities are available to Data Cloud agents
- Data Cloud-specific capabilities are defined in `capabilities/data-cloud-capabilities.yaml`
- Agents can reference both platform and Data Cloud capabilities

## Maintenance

- **Versioning**: Increment agent definition versions when making changes
- **Deprecation**: Mark deprecated agents with `status: deprecated`
- **Documentation**: Keep summaries and descriptions up to date
- **Validation**: Always run validation before committing changes

## Related Documentation

- [Platform Agent Catalog](/platform/agent-catalog/)
- [Agent Registry](/products/data-cloud/extensions/agent-registry/)
- [Agent Core Documentation](/platform/java/agent-core/)

## Contact

- **Maintainer**: data-cloud-team@ghatana.ai
- **Scope**: data-management
- **Priority**: 200
