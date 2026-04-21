# AEP — Agentic Execution Runtime

**Product Owner:** @ghatana/aep-team  
**Status:** Active  
**Stack:** Java 21 + ActiveJ 6.0 / Kotlin

## Purpose

The **Agentic Event Processor** is the execution runtime for Ghatana’s agentic workloads. It is not just an older event-pattern engine and should not be described that way in new documentation.

AEP owns the runtime surfaces that let operators and other products:

- register and inspect agents
- define and execute pipelines
- track run state and execution evidence
- route human-in-the-loop review and learning flows
- expose governance, compliance, and audit summaries
- publish analytics, reporting, lifecycle, and deployment controls around the runtime

The current product shape is best understood as an **agentic execution plane with operator tooling**, not as a generic CEP or pattern-only subsystem.

## Boundary With Data Cloud

Data Cloud and AEP are tightly integrated but intentionally asymmetric:

- **Data Cloud owns** data management, canonical event storage, analytics persistence, reporting stores, and general-purpose platform data capabilities
- **AEP owns** execution orchestration, pipeline runs, agent invocation, HITL review, policy promotion, and runtime governance/operator surfaces
- **Dependency direction** is one-way at compile time: AEP may depend on Data Cloud public contracts and APIs; Data Cloud must not import AEP modules
- **Runtime integration** happens through Data Cloud-backed persistence and event-log surfaces where configured

## Capability Model

| Capability | What AEP owns |
| --- | --- |
| Agent runtime | Agent listing, execution, memory views, and marketplace/runtime metadata |
| Pipeline runtime | Pipeline CRUD, versioning, publish/rollback, run listing, run detail, and cancellation |
| HITL | Review queue, approve/reject/escalate flows, learning reflection triggers |
| Governance | Kill-switch, degradation mode, policy evaluation, compliance summary, audit summary, security scans |
| Analytics and reporting | Anomalies, forecasting, reporting, deployment lifecycle endpoints |

## HTTP Surface

The documented public route families live in the AEP OpenAPI specs at:

- `products/aep/contracts/openapi.yaml`
- `products/aep/server/src/main/resources/openapi.yaml`

Core public families exercised by server tests include:

- health and observability: `/health`, `/ready`, `/live`, `/info`, `/metrics`, `/health/deep`, `/metrics/slo`
- pipelines: `/api/v1/pipelines`, `/api/v1/pipelines/{pipelineId}`, `/api/v1/pipelines/{pipelineId}/versions`, `/api/v1/pipelines/{pipelineId}/publish`, `/api/v1/pipelines/{pipelineId}/rollback`
- agents: `/api/v1/agents`, `/api/v1/agents/{agentId}`, `/api/v1/agents/{agentId}/execute`, `/api/v1/agents/{agentId}/memory`
- runs: `/api/v1/runs`, `/api/v1/runs/{runId}`, `/api/v1/runs/{runId}/cancel`
- HITL and learning: `/api/v1/hitl/*`, `/api/v1/learning/*`
- governance and compliance: `/governance/*`, `/api/v1/compliance/*`
- analytics, reporting, deployments, and patterns: `/api/v1/analytics/*`, `/api/v1/reports`, `/api/v1/deployments`, `/api/v1/patterns`

## Runtime Truth

- **Production Run History Requirement**: Data Cloud with EventLogStore is required for durable run history in production. Without Data Cloud, run history is stored in-memory (max 1,000 entries) and will be lost on restart.
- **Explicit Production Guard**: when `AEP_PROFILE=production`, startup fails closed if EventLogStore is unavailable unless `AEP_ALLOW_IN_MEMORY_RUN_HISTORY=true` is intentionally set for embedded/library-only deployments where non-durable history is acceptable.
- **Trusted Proxy Requirement**: `X-Forwarded-For` is ignored unless the immediate caller matches `AEP_TRUSTED_PROXY_CIDRS`. Direct and embedded/library deployments should normally leave this unset; proxied deployments must set it to the ingress/load-balancer CIDRs to preserve accurate rate limiting.
- **Trusted Proxy Metrics**: Prometheus now exposes `aep_security_proxy_forwarded_accepted_total` for trusted forwarded-IP usage and `aep_security_proxy_forwarded_rejected_total{reason=...}` when AEP ignores spoofed or malformed `X-Forwarded-For` headers.
- **Pipeline Update Concurrency**: `PUT /api/v1/pipelines/{pipelineId}` requires the caller to send the last observed version via request-body `version`, request-body `expectedVersion`, or `If-Match`. AEP returns `409 PIPELINE_VERSION_CONFLICT` for stale writes and `428 PIPELINE_VERSION_REQUIRED` when the concurrency token is missing.
- Production startup must fail closed when required secrets and DB-backed runtime dependencies are absent.
- Governance and compliance endpoints remain operationally useful, but full production-grade durability still depends on the configured backing stores.
- `GET /metrics` serves Prometheus text only when a Prometheus registry is wired; otherwise embedded and fixture-backed modes return JSON fallback metrics.
- `GET /health` is the shallow service signal, while `GET /health/deep` reports dependency state across persistence and runtime integrations.

## Operator Surface

- Phase-4 operator cockpit is live in `products/aep/ui` with dedicated monitoring, cost, and run-detail pages. The route family includes `/operate` and `/operate/costs`, and the UI test suite already exercises the shipped operator surfaces.
- Phase-5 learning is live in `EpisodeLearningPipeline`: reflection groups episodes by skill, evaluates them through the configured evaluation gate, submits review items when needed, and tags auto-promotable policy changes without requiring fake placeholder flows.
- Phase-6 observability is live for both standalone server and embedded/library modes: `/health/deep` exposes dependency state, `/metrics/slo` publishes run, replay, and agent-execution snapshots, and the monitoring assets under `monitoring/` ship the matching alert rules and Grafana dashboards.

## HITL And Runtime Config

- `GET /api/v1/hitl/pending` supports `thresholdSeconds` and `autoEscalate` so operators can scan overdue items and optionally trigger SLA-breach escalations during queue inspection. If HITL is not configured, AEP responds truthfully with `configured=false` instead of pretending the queue exists.
- The server-level default overdue threshold is controlled by `AEP_HITL_ESCALATION_TIMEOUT_SECONDS`; embedded/library deployments inherit the same default without requiring a separate runtime surface.
- Tenant-specific HITL timeout policies can be supplied through `AEP_HITL_TIMEOUT_POLICIES` using `tenant=thresholdSeconds:action[:destinationType[:destination]]` entries separated by `;`. Supported actions are `escalate`, `auto_approve`, and `auto_reject`. Embedded/library deployments can use the same property without changing the public API surface.
- `POST /api/v1/hitl/{reviewId}/escalate` accepts optional `reason`, `destinationType`, and `destination` fields so manual escalations can carry explicit routing metadata for manager or queue handoff.
- The documented dynamic-config overlay schema lives at `products/aep/docs/config/aep-dynamic-config.schema.json`. It captures the runtime keys validated by `AepDynamicConfigService` today. AEP does not currently expose a public remote config mutation endpoint, so this schema is the authoritative automation surface for local operator tooling and deployment packaging.

## Local Development

```bash
./gradlew :products:aep:build
./gradlew :products:aep:test
./gradlew :products:aep:server:run
```

## Focused Verification

```bash
./gradlew :products:aep:server:test --tests com.ghatana.aep.server.AepGoldenPathSystemTest
./gradlew :products:aep:server:test --tests com.ghatana.aep.server.http.AepHttpServerGovernanceTest
./gradlew :products:aep:contracts:validateAepSpec
```

## Design Constraints

- **ActiveJ only**: no Spring Reactor/WebFlux. Async flows stay on `Promise`.
- **Explicit boundaries**: runtime logic, transport controllers, and persistence adapters remain separate.
- **Truthful operator surfaces**: UI pages and docs must not present placeholder capability as live runtime evidence.
- **Cross-product discipline**: AEP consumes Data Cloud contracts; Data Cloud does not take compile-time dependencies on AEP.

## Related Docs

- [products/aep/docs/API_DOCUMENTATION.md](/Users/samujjwal/Development/ghatana/products/aep/docs/API_DOCUMENTATION.md)
- [products/aep/docs/OPERATIONAL_RUNBOOK.md](/Users/samujjwal/Development/ghatana/products/aep/docs/OPERATIONAL_RUNBOOK.md)
