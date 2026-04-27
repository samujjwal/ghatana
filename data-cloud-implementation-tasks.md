# Data Cloud — Implementation Task List

**Source:** Data Cloud Architecture Review 2026-04-25  
**Product:** `products/data-cloud` in `samujjwal/ghatana`  
**Status key:** `[ ]` not started · `[~]` in progress · `[x]` done

---

## P0 — Trust and Correctness Closure
> Required before claiming enterprise readiness. Nothing else ships until these are done.

### P0.1 Runtime Capability Truth

- [ ] Define capability registry schema: `status` (live/degraded/preview/unavailable), `mode`, `dependency`, `probe`, `lastCheckedAt`, `degradedReason`, `docsLink`
- [ ] Make `/api/v1/capabilities` the single source of truth for all feature gating
- [ ] Wire every UI route to capability registry — hide or mark unavailable controls dynamically
- [ ] Wire SDK feature flags to capability registry
- [ ] Split health endpoints: `/live` (liveness), `/ready` (readiness), `/health/detail` (subsystem capability truth)
- [ ] Generate current route inventory from `DataCloudRouterBuilder` and diff against OpenAPI, REST docs, UI clients, route truth matrix
- [ ] Mark every route in route truth matrix as live / partial / preview / degraded / unavailable
- [ ] Write ADR-DC-002: Runtime capability truth — `/api/v1/capabilities` is mandatory gating authority

### P0.2 Query and Entity Correctness

- [ ] Define canonical `EntityStore.QuerySpec` schema: filters, search, sort, projection, pagination cursor, total count, consistency level, freshness hint
- [ ] Implement server-side filter, search, sort, pagination, and total count in `EntityCrudHandler`
- [ ] Fix `hasMore` and total count in all list/search responses
- [ ] Implement first-class collection registry: schema, owner, lifecycle, quality, retention, lineage, status
- [ ] Add schema validation and schema evolution to entity writes
- [ ] Add idempotency keys for entity writes
- [ ] Clarify and document batch semantics (transactional vs per-item)
- [ ] Add automatic semantic indexing and policy classification on entity ingest
- [ ] Add entity-level provenance envelope (see §8.3 metadata schema)
- [ ] Update OpenAPI, generated SDK clients, and tests to match new query contract
- [ ] Write ADR-DC-003: Canonical query/filter/sort/pagination/freshness/cost schema

### P0.3 Temporal / Event Truth

- [ ] Expand event envelope to required fields: `eventId`, `tenantId`, `type`, `version`, `occurredAt`, `actor`, `resource`, `operation`, `before`, `after`, `patch`, `policyDecision`, `traceId`, `correlationId`, `provenance`
- [ ] Ensure every entity mutation (create/update/delete/redact/purge/classify) emits a rich canonical event
- [ ] Implement point-in-time entity history from event snapshots (not just route stub)
- [ ] Validate event replay semantics (offset-based tail, seek, replay from checkpoint)
- [ ] Write ADR-DC-004: Required event fields for replay, audit, provenance, and context

### P0.4 Governance Correctness

- [ ] Reconcile collection inventory with policy inventory — stop marking unknown data as compliant
- [ ] Replace optimistic compliance summaries with real evidence from collection + policy inventory
- [ ] Implement legal hold enforcement: block purge/redaction/export when hold is active
- [ ] Add approval flow for all destructive actions (purge, redaction, bulk delete, export of PII)
- [ ] Ensure governance routes fail closed when backing services are unavailable
- [ ] Write ADR-DC-005: Production policy/audit/tenant requirements (fail-closed)

### P0.5 Production Security — Fail Closed

- [ ] Block production startup if auth provider, tenant resolver, policy engine, or audit log is unavailable
- [ ] Remove all default/implicit tenant fallback paths in production profile
- [ ] Make policy and audit dependencies non-nullable — throw on null, do not degrade silently
- [ ] Enforce explicit `X-Tenant-ID` / JWT tenant claim on every production request
- [ ] Verify no cross-tenant data leakage in entity, event, context, memory, or analytics queries
- [ ] Add tenant quota enforcement: rate, storage, compute, AI tokens, connector load, streaming subscriptions

### P0.6 OpenAPI and SDK Contract Truth

- [ ] Remove or clearly mark all placeholder/stub SDK client methods that return fake success
- [ ] Generate all SDK clients (Java, TypeScript, Python) from OpenAPI spec only
- [ ] Run contract tests against generated clients on every build
- [ ] Enforce OpenAPI drift check in CI — fail build on drift
- [ ] Write ADR-DC-011: OpenAPI-generated clients only; no placeholder success clients

### P0.7 UI Build and Test Gate

- [ ] Restore TypeScript type-check to green (zero type errors)
- [ ] Restore UI test suite to green
- [ ] Hide all UI controls that are not backed by a live API capability
- [ ] Add page-level error boundaries that use runtime capability truth, not static fallback text
- [ ] Validate UI route disclosure by role (analyst / operator / admin) using auth and capability registry

---

## P1 — Data Fabric Foundation

### P1.1 Connector Framework MVP

- [ ] Define Connector SPI: source/sink lifecycle, schema inference, credential store, health probe, tenancy, residency policy
- [ ] Implement connector types: relational DB, file/object, REST API, event stream
- [ ] Implement source registry: register, enable, disable, credential rotation, health status
- [ ] Add connector-level schema inference with AI assist (type detection, PII detection, field mapping)
- [ ] Tenant-scope all connector credentials, schedules, quotas, and data residency policies
- [ ] Write ADR-DC-008: Connector SPI — source/sink lifecycle, schema inference, credentials, health, tenancy

### P1.2 Query Broker

- [ ] Build unified query broker routing: entity store → event store → external connectors → analytics tiers
- [ ] Implement source/tier selection: hot cache → warm DB → search/analytics → archive → external
- [ ] Add query explain: source used, freshness, cost estimate, confidence, warnings
- [ ] Add partial-result warnings when some sources are unavailable or stale
- [ ] Implement NLQ (natural language query) → SQL/filter plan translation
- [ ] Implement voice query intent routing to query broker
- [ ] Enforce tenant and policy guards before every query execution
- [ ] Expose query plan/cost in API response metadata

### P1.3 Data Product Lifecycle

- [ ] Define data product lifecycle states: draft → published → deprecated → retired
- [ ] Implement publish endpoint: attach schema, quality, freshness, lineage, retention, policy to a collection/query/stream
- [ ] Implement consumer discovery and subscription
- [ ] Implement SLA monitoring and alert on degradation
- [ ] Add consumer-specific access policy per subscription
- [ ] Add contract compatibility checks on schema/SLA changes
- [ ] Write ADR-DC-010: Data product lifecycle — publish/discover/subscribe/SLA/deprecate

### P1.4 Storage Tier Routing and Cost

- [ ] Implement storage tier router: hot (Redis/cache) → warm (PG/H2) → cool (ClickHouse/OpenSearch) → cold (S3/archive)
- [ ] Implement automatic tier migration based on age, access frequency, and retention policy
- [ ] Add storage cost reporting per tenant, collection, and tier
- [ ] Add cost transparency in query responses
- [ ] Document tier routing policy and expose it in capability registry

### P1.5 Lineage Graph

- [ ] Implement end-to-end lineage tracking: ingestion source → transformation → entity → query → export
- [ ] Store lineage edges as events in the event log
- [ ] Build lineage graph query API: ancestors, descendants, full trace
- [ ] Expose lineage in entity provenance envelope
- [ ] Surface lineage in Trust Center UI

### P1.6 RAG with Citations

- [ ] Implement context snapshot versioning
- [ ] Add freshness metadata to all entity embeddings
- [ ] Enforce tenant, PII, retention, and sovereignty policies in retrieval paths
- [ ] Add RAG response citations: source entity/event IDs, ingestion time, confidence, staleness warning
- [ ] Implement feedback loop: user corrections update semantic/context confidence scores
- [ ] Add agent memory retention policies and deletion support
- [ ] Write ADR-DC-009: Sovereign profile — air-gap guarantees, disabled external services, backup/restore

---

## P2 — Automation and AI-Native Differentiation

### P2.1 Unified AI Action Contract

- [ ] Define AI action record schema: `actionId`, `tenantId`, `domain`, `intent`, `inputs`, `model` (provider/name/version), `confidence` (score/band/reason), `risk` (level/reasons), `decision` (mode/recommendedAction/alternatives), `provenance`
- [ ] Refactor `AiAssistHandler`, alert suggestions, and workflow draft endpoints to emit structured AI action records
- [ ] Store all AI action records in the audit/event log
- [ ] Expose AI action history in Trust Center
- [ ] Enforce model/provider exposure policy: log all external LLM calls with tenant consent check
- [ ] Write ADR-DC-007: Required AI suggestion/action evidence model

### P2.2 Intent-to-Workflow Builder

- [ ] Implement intent parsing: user goal → workflow DAG draft
- [ ] Add DAG validation: detect cycles, missing dependencies, unsupported operations
- [ ] Implement risk/cost estimation before execution
- [ ] Implement human approval step for medium/high-risk workflows
- [ ] Implement durable workflow execution with event-backed checkpoints
- [ ] Add job center: list executions, retry, cancel, view logs/progress/output
- [ ] Implement failure detection and automated remediation proposal
- [ ] Add failure compensation/rollback model per workflow step

### P2.3 Autonomy Controller

- [ ] Define autonomy policy model: domain, level (L0–L5), conditions, thresholds, approval rules
- [ ] Implement per-domain autonomy levels: query, data quality, governance, operations, storage, workflow, connectors, AI context
- [ ] Implement human takeover API: pause, stop, take-over, delegate-again, view-plan, approve, reject, edit
- [ ] Expose current automation state, plan, inputs, confidence, risk, expected impact, cost estimate, and trace ID to human operators
- [ ] Implement rollback/compensation where reversible
- [ ] Write ADR-DC-006: Autonomy levels, human takeover, rollback, audit

### P2.4 Alert Grouping and Autonomic Remediation

- [ ] Implement alert grouping: correlate alerts with traces, events, jobs, and deployments
- [ ] Add root-cause context generation using event history and lineage
- [ ] Implement remediation suggestion engine with risk/confidence annotation
- [ ] Implement auto-remediation for low-risk actions within autonomy policy
- [ ] Add human approval path for high-risk remediation
- [ ] Implement incident lifecycle: open → investigating → mitigating → resolved → post-mortem
- [ ] Add alert SLA and total count to all alert list responses
- [ ] Add remediation action registry and rollback support

### P2.5 Embedded AI Pervasiveness

- [ ] Entity ingestion: schema inference, dedupe detection, type detection, PII detection
- [ ] Query: NLQ, explain, optimization suggestion, source selection, cost warnings
- [ ] Context: retrieval ranking, stale-context detection, semantic embedding refresh
- [ ] Governance: policy recommendation, data classification, redaction suggestion
- [ ] Operations: anomaly grouping, capacity forecasting
- [ ] Connectors: field mapping suggestion, sync issue diagnosis, source reliability scoring
- [ ] UI: next-best-action surface, zero-cognitive-load progressive disclosure
- [ ] Testing/quality: contract drift detection, evidence gap detection

### P2.6 Learning Loop

- [ ] Capture accepted/rejected AI suggestions as training signal
- [ ] Update autonomy policies and playbooks based on operator feedback
- [ ] Implement confidence model update from verified outcomes
- [ ] Store learning events in event log with provenance

---

## P3 — Enterprise-Grade Scale and Ecosystem

### P3.1 HA Durable Providers and Conformance Suite

- [ ] Define provider conformance suite: required behaviors for `EntityStore` and `EventLogStore` SPI implementations
- [ ] Validate PostgreSQL provider for entity/settings/policy (warm tier)
- [ ] Validate Kafka/Redpanda provider for event log
- [ ] Validate Redis provider for hot cache/rate/session
- [ ] Validate ClickHouse provider for analytics/traces (cool tier)
- [ ] Validate OpenSearch/vector provider for search/RAG
- [ ] Document HA topology: replication, failover, backup, SLOs

### P3.2 Multi-Region / Multi-Sovereignty

- [ ] Implement per-tenant region policy enforcement in API middleware
- [ ] Add per-tenant encryption key management (KMS reference model)
- [ ] Implement cross-border transfer rules validation before export/connector push
- [ ] Add data residency audit: where tenant data lives vs where policy allows
- [ ] Support multi-region Kubernetes deployment topology (reference architecture)

### P3.3 Sovereign / Air-Gapped Profile

- [ ] Validate DR for embedded H2 (entity + event stores): backup, restore, point-in-time recovery
- [ ] Add compaction and tombstone cleanup for embedded stores
- [ ] Build air-gapped connector catalog (no external SaaS calls)
- [ ] Implement local model registry and inference (Ollama/local-only) with default-off external LLM
- [ ] Add sovereign policy pack (retention, purge, legal hold, audit without external dependencies)
- [ ] Generate exportable compliance evidence package from local audit log

### P3.4 Plugin Marketplace

- [ ] Implement real plugin install/activate/migrate/rollback lifecycle
- [ ] Add plugin schema version contract and compatibility check
- [ ] Build plugin marketplace catalog: discovery, version, trust score, vendor, capabilities
- [ ] Add plugin sandbox: resource quotas, tenant isolation, audit of plugin actions

### P3.5 Settings and API Key Lifecycle

- [ ] Implement persistent settings storage (general, security, notifications, preferences)
- [ ] Implement API key lifecycle: create, rotate, revoke, list, scope, expiry, audit
- [ ] Add admin approval for sensitive settings changes
- [ ] Expose settings contract in OpenAPI

### P3.6 Compliance Evidence Packages

- [ ] Build DSAR (data subject access request) workflow: find all data by subject, export, delete
- [ ] Generate compliance evidence package: data inventory, policy decisions, audit log, lineage, retention proof
- [ ] Implement legal hold management: apply, extend, release, audit
- [ ] Add compliance posture dashboard (evidence-first, not summary-first)

### P3.7 SDK, DevEx, and Observability

- [ ] Validate generated SDK remote client against live production routes (all endpoints)
- [ ] Add streaming adapter for SSE and WebSocket in SDK
- [ ] Publish SDK with versioned changelog and deprecation notices
- [ ] Export traces to configurable backend (OTLP/Jaeger/Zipkin) and validate in CI
- [ ] Add Prometheus/Grafana dashboard definitions for key SLOs
- [ ] Build production reference deployment: K8s Helm chart with all services, tested end-to-end
- [ ] Add benchmarks vs. incumbent fragmented stacks (entity, event, query, governance)

---

## Architecture Decisions (ADRs) to Write

| ADR | Topic | Priority |
|---|---|---|
| ADR-DC-002 | Runtime capability truth — `/api/v1/capabilities` mandatory for UI/SDK gating | P0 |
| ADR-DC-003 | Canonical query contract: filter/sort/pagination/freshness/cost | P0 |
| ADR-DC-004 | Event envelope: required fields for replay, audit, provenance, context | P0 |
| ADR-DC-005 | Governance fail-closed: production policy/audit/tenant requirements | P0 |
| ADR-DC-006 | Automation control: autonomy levels, human takeover, rollback, audit | P2 |
| ADR-DC-007 | AI action provenance: required AI suggestion/action evidence model | P2 |
| ADR-DC-008 | Connector SPI: source/sink lifecycle, schema inference, credentials, health, tenancy | P1 |
| ADR-DC-009 | Sovereign profile: air-gap guarantees, disabled external services, backup/restore | P1/P3 |
| ADR-DC-010 | Data product lifecycle: publish/discover/subscribe/SLA/deprecate | P1 |
| ADR-DC-011 | SDK generation: OpenAPI-generated clients only; no placeholder success clients | P0 |

---

## Critical Risks to Close First (from §4.3)

| Risk | Severity | Blocking tasks |
|---|---|---|
| Query/filter/sort/pagination semantics incomplete or dropped | Critical | P0.2 |
| Temporal history / event sourcing promise not fully met | Critical | P0.3 |
| Governance summaries can be optimistic | Critical | P0.4 |
| Policy/audit dependencies can be nullable | Critical | P0.5 |
| Capability registry is not the universal truth source | High | P0.1 |
| Settings/API key lifecycle incomplete | High | P3.5 |
| AI/ML fragmented by route family | High | P2.1 |
| Workflow execution not yet full orchestration plane | High | P2.2 |
| SDK/client contract drift | High | P0.6 |
| UI type-check/test gates not green | High | P0.7 |

---

## Acceptance Criteria for "Disruptive Enterprise-Ready Data Cloud"

All of the following must be verifiably true before claiming the target position:

- [ ] A tenant can ingest, connect, query, govern, and automate without leaving Data Cloud
- [ ] Every UI control is backed by a real route with a tested backend behavior
- [ ] Every runtime capability has a live / degraded / unavailable truth state
- [ ] Every entity mutation emits enough event/provenance data to reconstruct history
- [ ] Every query result carries source, freshness, tenant, policy, and trace metadata
- [ ] Every destructive action has approval, policy, audit, and verification
- [ ] AI actions include confidence, provenance, model/provider, risk, and rollback status
- [ ] Human users can interrupt or take over automation at any point
- [ ] Non-local deployments fail closed without durable providers, auth, policy, and audit
- [ ] OpenAPI, SDKs, UI clients, tests, and docs are generated/validated from the same contract
- [ ] Production deployment has validated backup/restore, HA, SLOs, observability, and incident response
- [ ] Product pages and docs never claim more than runtime evidence supports
