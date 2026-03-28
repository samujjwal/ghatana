# ADR-017: AEP Runtime Hardening and Extension Seams

**Status:** Accepted  
**Date:** 2026-03-27  
**Decision Makers:** AEP maintainers  
**Phase:** 6 - Audit remediation

## Context

The 2026-03-27 AEP audit identified a set of correctness and maintainability gaps spread across the engine, connectors, identity facade, event-cloud adapter, and runtime-core pipeline engine. Most of the gaps were not missing subsystems; they were mismatches between the existing design intent and the runtime behavior actually wired into production-facing code.

The main issues were:
- idempotency retention was unbounded in memory
- validation exposed only string errors instead of structured failure details
- consent selection had no named provider seam
- connector-backed event publishing used a fragile synchronous bridge
- delivery failures were not categorized for retry handling
- sequence matching could fall back to a shared global correlation key
- lightweight engine pipeline steps used raw string literals
- pipeline execution internals had accumulated too many responsibilities in one class
- connector strategy coverage was concentrated in smoke tests instead of behavior-focused lifecycle tests

## Decision

Keep the existing public AEP APIs, but harden the runtime through small compatible seams and internal decomposition instead of broad rewrites.

The accepted decisions are:

1. Keep `Aep.AepConfig` as the compatibility boundary and introduce audit-driven behavior via explicit config keys and builder helpers rather than widening the engine surface with separate registries or factories.
2. Treat structured validation details as part of the engine contract while preserving legacy summary accessors for compatibility.
3. Resolve consent implementations through a named `ConsentProvider` SPI and `ConsentServiceFactory`, with config and environment override support plus explicit fallback to `DefaultConsentService`.
4. Keep `ConnectorBackedEventCloud.append()` synchronous at the interface boundary, but return a stable local event ID immediately and treat connector completion as asynchronous observability, not as a blocking lookup.
5. Categorize delivery failures as `RETRYABLE`, `NON_RETRYABLE`, or `UNKNOWN` so downstream policies can distinguish transport retries from bad payloads.
6. Require a real correlation key for sequence and correlation-sensitive matching. Missing keys must skip matching rather than sharing anonymous state.
7. Centralize lightweight engine pipeline step names in `PipelineStepTypes` to avoid literal drift.
8. Decompose `PipelineExecutionEngine` into focused package-private collaborators for graph building, stage execution, and event routing while preserving the existing public engine entrypoint.
9. Raise connector coverage first with lightweight lifecycle tests around standard-library-backed strategies before adding heavier broker integration suites.

## Rationale

- This preserves existing entrypoints used across AEP modules while fixing the actual runtime risks found by the audit.
- Internal extraction was the lowest-risk way to reduce pipeline engine complexity without destabilizing runtime-core behavior.
- The new consent SPI matches existing repo patterns that prefer ServiceLoader-based extension seams over product-local hardcoding.
- Returning a local event ID immediately avoids eventloop blocking in the connector-backed event-cloud adapter while keeping the current `EventCloud` interface stable.
- Structured failure data improves observability and later policy decisions without forcing callers to parse log lines.
- Lightweight connector tests improve coverage now without making the connectors module depend on always-on Testcontainers infrastructure.

## Consequences

- Cluster-wide idempotency is still not provided by the in-memory engine; durable cross-instance deduplication remains a future persistence concern.
- The default consent implementation remains a development-safe fallback, not a substitute for real external consent platforms.
- Connector strategy coverage is better but still uneven for Kafka, SQS, and RabbitMQ implementations; heavier integration tests can be added incrementally.
- The pipeline execution engine is easier to reason about internally, but its collaborators remain package-private implementation details rather than standalone public APIs.

## Alternatives Considered

1. Rewrite AEP engine and pipeline execution around new public abstractions. Rejected because it would add migration risk without solving the immediate audit issues faster.
2. Introduce a separate persistence service for every remediation item. Rejected because several findings were wiring and contract issues, not missing infrastructure.
3. Add only documentation without runtime changes. Rejected because the audit included concrete correctness and reliability bugs that needed code fixes first.
4. Require Testcontainers-based integration tests for every connector before merging any coverage improvement. Deferred because standard-library-backed lifecycle tests close part of the regression gap immediately with much lower maintenance cost.