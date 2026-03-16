# YAPPC Configuration Module Structure

## Overview

The YAPPC configuration module provides centralized configuration management for domains, personas, agent capabilities, and workflows. This document outlines the enhanced structure and organization.

## Current Structure

```
config/
├── domains.yaml              # Domain definitions and configurations
├── personas.yaml             # User persona definitions
├── agents/                   # Agent-related configurations
│   ├── capabilities.yaml     # Agent capability definitions
│   └── mappings.yaml        # Agent-to-capability mappings
├── tasks/                    # Task definitions by domain
│   └── domains/             # Domain-specific task configurations
│       ├── frontend/        # Frontend development tasks
│       ├── backend/         # Backend development tasks
│       ├── database/        # Database management tasks
│       ├── devops/          # DevOps and infrastructure tasks
│       ├── testing/         # Testing and QA tasks
│       └── design/          # UX/UI design tasks
├── workflows/                # Workflow definitions
│   └── yappc-lifecycle.yaml # YAPPC lifecycle workflow
└── schemas/                  # Configuration validation schemas
    └── config-schema.json   # JSON schema for validation
```

## Configuration Files

### Core Configuration

#### `domains.yaml`
Defines high-level work domains with their properties:
- Domain ID, name, description
- Associated frameworks and technologies
- Relevant personas and phases
- Supported capabilities

#### `personas.yaml`
Defines user personas and their characteristics:
- Persona ID, label, description
- Category (TECHNICAL, BUSINESS, CREATIVE)
- Focus areas and permissions
- Icon and color coding

### Agent Configuration

#### `agents/capabilities.yaml`
Defines agent capabilities:
- Capability ID, name, description
- Category and agent capability mapping
- Input/output types
- Required parameters and constraints

#### `agents/mappings.yaml`
Maps agents to capabilities:
- Agent definitions and properties
- Capability assignments
- Priority and confidence levels
- Performance metrics

### Task Configuration

#### `tasks/domains/*/`
Domain-specific task definitions:
- Task ID, name, description
- Required capabilities
- Input/output specifications
- Dependencies and constraints
- Performance requirements

### Workflow Configuration

#### `workflows/yappc-lifecycle.yaml`
Defines the YAPPC lifecycle workflow:
- Phase definitions and transitions
- Agent assignments per phase
- Validation requirements
- Success criteria

## Enhanced Organization

### Proposed Improvements

#### 1. Add Configuration Validation
```yaml
# schemas/validation-rules.yaml
validation:
  domains:
    required: [id, name, description]
    unique: [id]
  personas:
    required: [id, label, category]
    unique: [id]
  capabilities:
    required: [id, name, agent_capability]
    unique: [id]
```

#### 2. Environment-Specific Configuration
```
config/
├── base/                     # Base configuration
│   ├── domains.yaml
│   ├── personas.yaml
│   └── agents/
├── environments/            # Environment-specific overrides
│   ├── development/
│   ├── staging/
│   └── production/
└── schemas/                  # Validation schemas
```

#### 3. Configuration Management Service
```java
// Enhanced configuration loading with validation
public class YAPPCConfigurationService {
    private final ConfigurationValidator validator;
    private final EnvironmentConfigLoader loader;
    
    public YAPPCConfiguration loadConfiguration(String environment) {
        Configuration config = loader.load(environment);
        validator.validate(config);
        return config;
    }
}
```

## Configuration Relationships

```
Domains ←→ Personas ←→ Capabilities ←→ Tasks ←→ Workflows
   ↓           ↓           ↓           ↓           ↓
Validation ← Permissions ← Agents ← Execution ← Orchestration
```

## Usage Patterns

### Loading Configuration
```java
// Current approach
YamlLoader loader = new YamlLoader();
DomainsConfig domains = loader.load("config/domains.yaml");

// Enhanced approach
YAPPCConfigurationService configService = new YAPPCConfigurationService();
YAPPCConfiguration config = configService.loadConfiguration("production");
List<Domain> domains = config.getDomains();
List<Persona> personas = config.getPersonas();
```

### Configuration Validation
```java
// Add validation schema
public class ConfigurationValidator {
    public ValidationResult validate(YAPPCConfiguration config) {
        // Validate domain-persona relationships
        // Validate capability mappings
        // Validate task dependencies
        // Validate workflow consistency
    }
}
```

## Migration Plan

### Phase 1: Documentation (Current)
- Document current structure
- Create configuration relationships diagram
- Define validation requirements

### Phase 2: Schema Definition
- Create JSON schemas for all configuration types
- Implement validation service
- Add configuration tests

### Phase 3: Environment Management
- Separate base vs environment-specific configs
- Implement configuration overrides
- Add configuration versioning

### Phase 4: Service Enhancement
- Create configuration management service
- Add hot-reload capabilities
- Implement configuration caching

## Best Practices

### Configuration Design
- Use clear, descriptive IDs
- Maintain consistent naming conventions
- Provide comprehensive documentation
- Include examples and usage patterns

### Validation
- Validate all configurations on load
- Provide clear error messages
- Support incremental validation
- Maintain backward compatibility

### Environment Management
- Separate base configuration from environment overrides
- Use configuration inheritance
- Support configuration merging
- Maintain environment-specific defaults

## Conclusion

The current configuration structure is **well-organized** with clear separation of concerns. The proposed enhancements focus on:

1. **Validation**: Add schema-based validation
2. **Environment Management**: Support environment-specific configurations
3. **Service Enhancement**: Create configuration management service
4. **Documentation**: Improve configuration documentation and examples

These improvements will make the configuration system more robust, maintainable, and scalable while preserving the current good structure.

---

# JSON Schema Validation Framework (YAPPC-8.4)

> **Introduced:** v2.4.0 | **Framework:** Generic Adaptive Agent (GAA)  
> **Purpose:** Runtime validation of configuration YAML files against JSON schemas

## Overview

All YAPPC configuration files now must validate against their corresponding JSON schemas during the build process. This ensures type safety, required field presence, and semantic correctness before runtime.

## Schema Files

### Location
```
products/yappc/config/schemas/
├── agent-schema.json                    # Agent configuration schema
├── policies-schema.json                 # Policy definitions schema
├── lifecycle-transitions-schema.json    # Lifecycle phases & transitions schema
└── memory-items-schema.json            # Memory system configuration schema
```

### 1. Agent Configuration Schema

**File:** `agent-schema.json`  
**Validates:** Files in `agents/definitions/*.yaml` and `agents/*-catalog.yaml`

**Key Fields:**
- `id` (required): Immutable kebab-case identifier
- `name` (required): Human-readable name (1-255 chars)
- `version` (required): Semantic version (e.g., `1.0` or `1.0.5`)
- `generator` (required): Generation strategy (LLM, RULE_BASED, PIPELINE, HYBRID)
- `capabilities` (required): Non-empty array of capability strings
- `tools`: Optional array of tool definitions with parameters
- `supervisedBy` / `escalatesTo`: Optional agent relationship definitions
- `resilience`: Optional retry policy (maxAttempts, backoffMs, timeoutMs)

**Example Validation:**
```bash
# Valid agent configuration
$ cat agents/definitions/dependency-auditor.yaml | jq
{
  "id": "dependency-auditor",
  "name": "Dependency Auditor",
  "version": "1.0",
  "generator": { "type": "PIPELINE", "steps": [...] },
  "capabilities": ["dependency_analysis", "license_compliance"]
}
✓ Valid against agent-schema.json

# Invalid: missing required 'id' field
$ cat agents/bad-agent.yaml | jq
{
  "name": "Bad Agent"
}
✗ Validation error: Missing required field 'id'
```

### 2. Policies Configuration Schema

**File:** `policies-schema.json`  
**Validates:** Files in `policies/*.yaml`

**Key Fields:**
- `apiVersion` (required): Version marker (e.g., `v1.0`)
- `policies` (required): Non-empty array of policy objects
  - `id` (required): Unique policy identifier
  - `version` (required): Semantic version
  - `rules` (required): Non-empty array of rule definitions
    - `id`: Unique rule identifier
    - `condition`: { type, value } — evaluated at runtime
    - `action`: { type, target, parameters }
    - `appliesTo`: Array of applicability filters
    - `enabledOn`: Array of GAA lifecycle phases (PERCEIVE, REASON, ACT, CAPTURE, REFLECT)

**Example:**
```yaml
apiVersion: v1.0

policies:
  - id: phase_advance_policy
    version: "1.0"
    policyType: LIFECYCLE
    rules:
      - id: require_test_coverage
        condition:
          type: EXPRESSION
          value: "test_coverage >= 80"
        action:
          type: BLOCK
          target: phase_transition
        enabledOn:
          - REASON
```

### 3. Lifecycle Transitions Schema

**File:** `lifecycle-transitions-schema.json`  
**Validates:** `lifecycle/lifecycle-transitions.yaml`

**Key Sections:**
- `lifecycle`: Lifecycle metadata (name, version, description)
- `stages`: Ordered array of lifecycle phases
  - `id` (PERCEIVE, REASON, ACT, CAPTURE, REFLECT)
  - `order`: Execution order
  - `isCritical`: Whether phase failure halts lifecycle
  - `timeoutMs`: Maximum execution time
  - `retryPolicy`: { maxAttempts, backoffMs, backoffMultiplier }
- `transitions`: Valid stage-to-stage transitions
- `errorHandling`: Global error strategy (HALT, SKIP, FALLBACK_STAGE, ESCALATE)
- `metrics`: Observability flags

**Example:**
```yaml
apiVersion: v1.0

lifecycle:
  name: GAA_STANDARD
  version: "1.0"

stages:
  - id: PERCEIVE
    order: 0
    isCritical: true
    timeoutMs: 5000

transitions:
  - from: PERCEIVE
    to: REASON
    onSuccess: true

errorHandling:
  onStageFail: HALT
  dlqEnabled: true
```

### 4. Memory Configuration Schema

**File:** `memory-items-schema.json`  
**Validates:** `memory/memory-config.yaml`

**Key Sections:**
- `memory.episodic`: Experience/event storage
  - `maxItems`: Retention limit (default: 100,000)
  - `retentionDays`: Archive threshold (default: 365)
  - `redaction`: PII protection rules
- `memory.semantic`: Knowledge/facts storage
  - `embeddingModel`: Vector embedding provider
  - `similarityThreshold`: Match threshold (0-1)
  - `indexing.vectorIndex`: HNSW | FAISS | ANNOY
- `memory.procedural`: Learned policies
  - `confidenceThreshold`: Minimum policy confidence (default: 0.7)
  - `learningRate`: Update rate (0-1)
  - `decayFactor`: Confidence decay rate
- `memory.storage`: Backend configuration
  - `backend`: POSTGRESQL | ROCKSDB | REDIS | HYBRID
  - `hybrid.localStore`: RocksDB | MEMORY
  - `hybrid.centralStore`: POSTGRESQL | REDIS
- `memory.eventSourcing`: Event log configuration
  - `snapshotIntervalEvents`: Frequency of snapshots

## Validation Task

### Running Validation

**Automatic (during build):**
```bash
./gradlew :products:yappc:services:lifecycle:compileJava
# Validation runs before Java compilation
```

**Explicit:**
```bash
./gradlew :products:yappc:services:lifecycle:validateConfigSchemas
```

### Task Implementation

**Location:** `products/yappc/services/lifecycle/build.gradle.kts`

**Behavior:**
- Walks all YAML files in `config/` subdirectories
- Loads corresponding JSON Schema
- Validates each YAML against schema
- Reports errors with descriptive messages
- Fails build if validation errors detected

**Output Example:**
```
> Task :products:yappc:services:lifecycle:validateConfigSchemas
✓ Valid: config/policies/lifecycle-policies.yaml (against policies schema)
✓ Valid: config/agents/definitions/dependency-auditor.yaml (against agent schema)
✓ Valid: config/lifecycle/lifecycle-transitions.yaml (against lifecycle schema)
✓ All configuration files validated successfully

BUILD SUCCESSFUL
```

**Error Output:**
```
> Task :products:yappc:services:lifecycle:validateConfigSchemas
Validation errors in config/policies/lifecycle-policies.yaml:
  - $.policies[0] Missing required field 'id'
  - $.policies[0].version Pattern '^\d+\.\d+(\.\d+)?$' does not match '1'

FAILURE: Build failed with an exception.
Configuration schema validation failed with 2 error(s)
```

## IDE Integration

### VS Code

1. Install "YAML" extension by Red Hat
2. Create `.vscode/settings.json` in workspace root:

```json
{
  "yaml.schemas": {
    "file://${workspaceFolder}/products/yappc/config/schemas/policies-schema.json": 
      "products/yappc/config/policies/**/*.yaml",
    "file://${workspaceFolder}/products/yappc/config/schemas/agent-schema.json": 
      "products/yappc/config/agents/**/*.yaml",
    "file://${workspaceFolder}/products/yappc/config/schemas/lifecycle-transitions-schema.json": 
      "products/yappc/config/lifecycle/**/*.yaml",
    "file://${workspaceFolder}/products/yappc/config/schemas/memory-items-schema.json": 
      "products/yappc/config/memory/**/*.yaml"
  }
}
```

3. YAML files now show real-time validation errors and autocomplete

### IntelliJ IDEA

1. Go to Settings → Languages & Frameworks → Schemas and DTDs → JSON Schema Mappings
2. Click + and add mappings:
   - Schema: File → point to schema JSON
   - Path: Select `config/policies/**/*.yaml`
3. Repeat for other schemas

## Configuration Update Checklist

When adding new configuration:

- [ ] Create YAML file with `apiVersion: v1.0` at top
- [ ] Validate locally: `./gradlew validateConfigSchemas`
- [ ] Ensure required fields present (per schema)
- [ ] Add `@doc` tags to Java config loader classes
- [ ] Document in this README
- [ ] Test in both development and selected environment
- [ ] Commit to git with clear message

## Common Validation Errors

| Error | Cause | Solution |
|-------|-------|----------|
| `Missing required field 'id'` | Field not present in YAML | Add `id: value` to config object |
| `Pattern '^\d+\.\d+'` does not match X | Version format incorrect | Use `X.Y` or `X.Y.Z` format |
| `'type' must be one of [...]` | Invalid enum value | Check allowed values in schema |
| `'timeoutMs' must be >= 1000` | Numeric constraint violation | Increase value to meet minimum |
| `'maxItems' must be integer` | Type mismatch | Remove quotes around numbers |

## Schema Management

### Adding a New Schema

1. Create `new-config-schema.json` in `config/schemas/`
2. Define top-level `$id`, `$schema`, `title` fields
3. Add required properties and validation rules
4. Update Gradle task to validate new schema family
5. Document schema in this README
6. Update IDE schema mappings
7. Add example config file for testing

### Schema Evolution

**Policy:** Backward-compatible changes only

Allowed:
- Add new optional properties
- Add values to enums
- Loosen numeric constraints
- Change descriptions

Disallowed (breaking):
- Remove required fields
- Remove enum values
- Restrict numeric constraints
- Change field types

When breaking changes needed:
- Bump schema version in `$id` (e.g., v1 → v2)
- Create new schema file
- Update configs to use new schema
- Document migration path

## References

- **JSON Schema Spec:** https://json-schema.org/draft-07/
- **Validator Lib:** `com.networknt:json-schema-validator:1.0.95`
- **GAA Framework:** `libs:agent-framework` (reference implementation)
- **YAPPC Implementation Plan:** Dimension 8.4 — Config Schema Validation

---

**End of Configuration Documentation**

_Document Version: 2.0 | Schema Version: v1.0 | Last Updated: 2026-01-19_
