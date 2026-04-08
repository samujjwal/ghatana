# ADR-001: Nine-Type Agent Framework with Generic TypedAgent Interface

**Status:** Accepted  
**Date:** 2026-01-20  
**Decision Makers:** Platform Team  
**Phase:** 2 — Agent Framework  

## Context

The original codebase had multiple fragmented agent abstractions across packages:
- `com.ghatana.agent.Agent` — untyped, single-method interface
- Various ad-hoc processing classes in different products
- No common lifecycle, configuration, or result model

We needed a unified agent framework that supports diverse processing paradigms across deterministic logic, probabilistic inference, stream processing, planning, adaptation, composition, and product-specific extension while sharing a common lifecycle and configuration surface.

## Decision

Adopt a **nine-type agent taxonomy** governed by a single generic interface `TypedAgent<I, O>`, while retaining deprecated `LLM` only for backward compatibility during migration:

| Type | Determinism | Use Case |
|------|------------|----------|
| DETERMINISTIC | 100% | Rules, thresholds, FSMs, pattern matching |
| PROBABILISTIC | 0% | ML model inference, Bayesian, LLM |
| STREAM_PROCESSOR | Varies | Stateful event streams, CEP, ingestion, transformation |
| PLANNING | Varies | Goal-directed orchestration, HTN, ReAct, workflow compilation |
| HYBRID | Partial | Fast-path deterministic + probabilistic fallback |
| ADAPTIVE | 0% | Multi-armed bandits, Thompson Sampling, self-tuning |
| COMPOSITE | Varies | Ensemble voting, fan-out/fan-in, sub-agent DAGs |
| REACTIVE | 100% | Low-latency triggers, circuit breakers, reflex actions |
| CUSTOM | Varies | Domain-specific types registered through platform extension points |

Deprecated compatibility type:

| Type | Status | Migration |
|------|--------|-----------|
| LLM | Deprecated | Use `PROBABILISTIC` with LLM subtype/reasoning profile |

The `TypedAgent<I, O>` interface enforces:
- **Type-safe processing**: `process(AgentContext ctx, I input) → Promise<AgentResult<O>>`
- **Structured results**: `AgentResult<O>` carries output, confidence score, explanation, and metrics
- **Standard lifecycle**: construct → `initialize(AgentConfig)` → `process()*` → `shutdown()`
- **Health monitoring**: `healthCheck() → Promise<HealthStatus>`
- **Batch support**: `processBatch(AgentContext, List<I>)` with default sequential implementation

## Rationale

- **Generic typing** prevents runtime ClassCastException and enables compile-time safety
- **Nine canonical types** cover deterministic, probabilistic, stream, planning, adaptive, ensemble, reactive, and custom extension use cases already present in the repo
- **Common `AgentResult`** with confidence + explanation enables uniform downstream decision-making
- **ActiveJ `Promise`** aligns with the existing async execution model
- **Backward-compatible `LLM` deprecation** keeps older descriptors readable while converging on `PROBABILISTIC`
- **`CUSTOM` plus subtype/SPI registration** allows domain-specific extension without expanding the top-level taxonomy for every product case

## Consequences

- All agent implementations must implement `TypedAgent<I, O>` — migration cost for legacy code
- The `AgentType` enum is intentionally governed; new canonical top-level types require framework changes, while product-specific extensions should prefer `CUSTOM` plus subtype/SPI registration
- Type-specific behaviors (e.g., bandit arm selection, voting strategies) live in concrete implementations, not the interface
- `AbstractTypedAgent<I, O>` provides default lifecycle, metrics, and error handling — concrete agents extend this
- Deprecated `LLM` remains readable for compatibility but should not be used in new descriptors or docs

## Alternatives Considered

1. **Single `Agent` interface with `Object` input/output** — rejected due to type-safety concerns
2. **Separate interfaces per agent type** — rejected due to registry and pipeline integration complexity
3. **Marker interfaces + runtime type checks** — rejected; compile-time safety is preferred
