# ADR-001: Six-Type Agent Framework with Generic TypedAgent Interface

**Status:** Accepted  
**Date:** 2026-01-20  
**Decision Makers:** Platform Team  
**Phase:** 2 — Agent Framework  

## Context

The original codebase had multiple fragmented agent abstractions across packages:
- `com.ghatana.agent.Agent` — untyped, single-method interface
- Various ad-hoc processing classes in different products
- No common lifecycle, configuration, or result model

We needed a unified agent framework that supports diverse processing paradigms (rule-based, ML-inference, bandit-based, ensemble) while sharing a common lifecycle and configuration surface.

## Decision

Adopt a **six-type agent taxonomy** governed by a single generic interface `TypedAgent<I, O>`:

| Type | Determinism | Use Case |
|------|------------|----------|
| DETERMINISTIC | 100% | Rules, thresholds, FSMs, pattern matching |
| PROBABILISTIC | 0% | ML model inference, Bayesian, LLM |
| HYBRID | Partial | Fast-path deterministic + probabilistic fallback |
| ADAPTIVE | 0% | Multi-armed bandits, Thompson Sampling, self-tuning |
| COMPOSITE | Varies | Ensemble voting, fan-out/fan-in, sub-agent DAGs |
| REACTIVE | 100% | Low-latency triggers, circuit breakers, ReflexEngine |

The `TypedAgent<I, O>` interface enforces:
- **Type-safe processing**: `process(AgentContext ctx, I input) → Promise<AgentResult<O>>`
- **Structured results**: `AgentResult<O>` carries output, confidence score, explanation, and metrics
- **Standard lifecycle**: construct → `initialize(AgentConfig)` → `process()*` → `shutdown()`
- **Health monitoring**: `healthCheck() → Promise<HealthStatus>`
- **Batch support**: `processBatch(AgentContext, List<I>)` with default sequential implementation

## Rationale

- **Generic typing** prevents runtime ClassCastException and enables compile-time safety
- **Six types** cover the full spectrum from deterministic to learning-based agents
- **Common `AgentResult`** with confidence + explanation enables uniform downstream decision-making
- **ActiveJ `Promise`** aligns with the existing async execution model
- **Lombok `@SuperBuilder`** on `AgentConfig` enables clean extensibility for type-specific configs

## Consequences

- All agent implementations must implement `TypedAgent<I, O>` — migration cost for legacy code
- The `AgentType` enum is closed; new types require framework changes
- Type-specific behaviors (e.g., bandit arm selection, voting strategies) live in concrete implementations, not the interface
- `AbstractTypedAgent<I, O>` provides default lifecycle, metrics, and error handling — concrete agents extend this

## Alternatives Considered

1. **Single `Agent` interface with `Object` input/output** — rejected due to type-safety concerns
2. **Separate interfaces per agent type** — rejected due to registry and pipeline integration complexity
3. **Marker interfaces + runtime type checks** — rejected; compile-time safety is preferred
