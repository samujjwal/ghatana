# ADR-009: Configuration-First Architecture with YAML and JSON Schema Validation

**Status:** Accepted  
**Date:** 2026-02-01  
**Decision Makers:** Platform Team  
**Phase:** 3 — Configuration Engine  

## Context

Agent and pipeline behavior must be customizable without code changes. Operations teams need to modify thresholds, routing rules, model endpoints, and pipeline topology through configuration files. The configuration must be validated before being applied to prevent runtime failures.

## Decision

Adopt a **configuration-first** model where all runtime behavior is declaratively defined in YAML:

**Configuration layers:**

| Layer | Format | Validation |
|-------|--------|------------|
| Agent config | YAML → `AgentConfig` (Lombok `@SuperBuilder`) | JSON Schema + builder validation |
| Pipeline config | YAML → `Pipeline` (builder API) | DAG validation (acyclicity, stage references) |
| System config | YAML / properties | Type-safe accessors |

**Agent configuration hierarchy:**
```
AgentConfig (base: agentId, type, timeout, confidenceThreshold, maxRetries, properties)
├── DeterministicAgentConfig (subtype, rules, thresholds)
├── ProbabilisticAgentConfig (modelEndpoint, inferenceTimeout, batchSize)
├── HybridAgentConfig (deterministic + probabilistic configs, routingStrategy)
├── AdaptiveAgentConfig (arms, explorationRate, decayFactor)
├── CompositeAgentConfig (subAgents, votingStrategy)
└── ReactiveAgentConfig (triggers, circuitBreakerConfig)
```

**Key capabilities:**
1. **YAML parsing** via Jackson (`jackson-dataformat-yaml`)
2. **Type-safe materialization** via Lombok `@SuperBuilder` — YAML maps directly to Java objects
3. **`${ENV:default}` interpolation** — environment variables in config values
4. **Hot-reload** via file watcher — config changes apply within 5 seconds without restart
5. **Version tracking** — config versions for rollback support

**Pipeline YAML structure:**
```yaml
pipeline:
  id: fraud-detection
  version: "1.0.0"
  stages:
    - id: filter
      operator: stream:filter:1.0
    - id: detect
      operator: pattern:seq:1.0
  edges:
    - from: filter
      to: detect
      label: primary
```

## Rationale

- **YAML** is human-readable, widely adopted, and supports comments
- **Lombok `@SuperBuilder`** enables clean extensibility without boilerplate
- **Jackson YAML** reuses the existing Jackson infrastructure (already used for JSON throughout)
- **Hot-reload** enables zero-downtime configuration updates
- Configuration validation at load time (not execution time) catches errors early

## Consequences

- YAML parsing errors surface at startup, not compile time — JSON Schema provides early validation
- `@SuperBuilder` requires all subclasses to also use `@SuperBuilder` — inheritance tax
- Hot-reload changes are not atomic across multiple config files — potential for partial states
- Environment variable interpolation values are not validated against expected types

## Alternatives Considered

1. **JSON only** — rejected; YAML is more readable for nested configurations
2. **HOCON (Lightbend Config)** — rejected; adds a dependency, team unfamiliar
3. **TOML** — rejected; less suited for deeply nested structures
4. **Annotation-based configuration** — rejected; violates configuration-first principle
