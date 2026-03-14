# Aura Shared Platform Integration Specification

Version: 1.0
Date: March 2026

## Purpose

This document defines the implementation contract between Aura and the shared platform layers it
depends on:

- `products/aep/platform` for cross-process event communication
- `products/data-cloud/platform` and `products/data-cloud/spi` for managed data handling
- shared security, auth, audit, and observability capabilities

If there is a conflict between this document and `Aura_Master_Platform_Specification.md`, the Master
spec wins. This document exists to make the shared-platform boundary implementable.

---

## Ownership Model

### Aura Owns

- product-domain entities, rules, and user-facing behavior
- event business semantics, topic names, and payload meaning
- data-product semantics, retention requirements, and trust policies
- recommendation logic, ranking, explainability, and outcome handling

### Shared Platforms Own

- AEP transport integration, replay, DLQ, and fan-out runtime behavior
- Data Cloud persistence lifecycle, restore, retention execution, lineage, and plugin lifecycle
- shared auth, request security, service identity, audit plumbing, and observability pipelines

### Implementation Rule

Aura code must not bypass AEP or Data Cloud for production cross-process communication or managed
data paths. Any exception requires an explicit ADR and a time-bounded removal plan.

---

## AEP Integration Contract

## Event Envelope

All Aura cross-process events must use a stable envelope with these minimum fields:

- `eventType`
- `schemaVersion`
- `eventId`
- `occurredAt`
- `producer`
- `traceId`
- `tenantId` if multi-tenant context exists
- `actorContext` when user-initiated
- `idempotencyKey` when side effects can be retried independently

Business payload fields live under the event-specific body defined in `Aura_Event_Architecture.md`.

## Topic Registration

Aura topics must be registered in AEP before production use. Each topic registration must define:

- topic name
- owning Aura module
- schema source
- ordering key / partition key
- replay expectations
- DLQ policy
- PII classification

## Partitioning and Ordering Rules

| Topic | Ordering Key | Why |
| ----- | ------------ | --- |
| `aura.profile` | `userId` | preserves per-user profile and consent order |
| `aura.recommendation` | `recommendationId` | keeps generated/served/outcome lifecycle ordered |
| `aura.feedback` | `recommendationId` or `userId` fallback | keeps recommendation interaction lineage intact |
| `aura.ingestion` | `sourceId` or `productRef` | stabilizes source processing and retries |
| `aura.catalog` | `productId` | keeps canonical updates ordered per product |
| `aura.governance` | `resourceId` | preserves audit and model-governance event order |

Aura should not assume global ordering. Only per-key ordering is guaranteed.

## Transaction Boundary

For mutations that both change durable state and publish a cross-process event, Aura must use a
transactional outbox pattern:

1. Write the domain mutation to the primary Aura dataset through Data Cloud.
2. Write an outbox record in the same transaction.
3. Publish to AEP from an outbox relay.
4. Mark the outbox record delivered only after AEP acknowledges acceptance.

This applies to at least:

- profile updates and overrides
- consent grants/revokes
- recommendation generation/serving records
- feedback capture
- recommendation outcome capture
- export/delete workflow start events

## Producer Rules

- Producers must be idempotent with stable `eventId` or `idempotencyKey`.
- Producers must attach trace context and producer version.
- Producers must not include raw PII in event payloads.
- If AEP is unavailable, the request must fail loudly or remain queued in the outbox; it must not
  report silent success for cross-process side effects.

## Consumer Rules

- Consumers must deduplicate by `eventId` and/or `idempotencyKey`.
- Consumers must record last processed offset/checkpoint through shared platform facilities.
- Consumers must treat unsupported schema versions as a safe failure and route through DLQ.
- Consumers must emit structured telemetry with topic, event type, schema version, handler result,
  and retry count.

## Schema Evolution

- Backward-compatible additions may increment a minor schema version.
- Breaking changes require a new schema version and rollout plan.
- Aura event schemas must be validated in CI before topic registration or producer deployment.
- Producer and consumer compatibility tests are release gates for any event contract change.

---

## Data Cloud Integration Contract

## Logical Data Products

Aura logical datasets should map to Data Cloud-managed datasets or plugins with stable ownership:

| Aura Data Product | Data Cloud Shape | Primary Aura Owner |
| ----------------- | ---------------- | ------------------ |
| canonical catalog | relational dataset + raw snapshot plugin | catalog |
| source provenance | relational dataset + object-storage plugin | catalog |
| user profile intelligence | relational dataset | profile |
| consent ledger | relational/governance dataset | governance |
| recommendation ledger | relational dataset | recommendation |
| outcome dataset | relational dataset | recommendation + safety ops |
| analytics/export bundles | object-storage plugin + derived datasets | governance + analytics |
| model training snapshots | object-storage plugin + metadata dataset | ML |
| outbox / replay state | relational dataset | platform integration |

## Repository Boundary

Aura application code should depend on repository ports or service adapters, not raw storage drivers.

- `apps/api` and `apps/core-worker` may use Prisma-backed repositories for Aura-owned relational
  models only when those repositories are the Aura adapter over a Data Cloud-managed implementation.
- Cache access must go through a dedicated Aura cache abstraction backed by a Data Cloud cache plugin.
- Raw snapshot/archive access must go through Data Cloud object-storage adapters.

## Dataset Registration

Every production dataset must have registration metadata covering:

- dataset name
- owner
- schema source
- retention class
- deletion behavior
- export behavior
- PII class
- restore priority
- downstream consumers

## Export and Deletion Flows

- Data export requests assemble through Data Cloud-managed workflows.
- Account deletion and consent revocation must propagate to future training snapshots.
- Deletion does not require immediate model retraining unless legal or safety policy says otherwise,
  but the deleted subject must be excluded from future snapshot generation.

## Backup, Restore, and Lineage

- All tier-1 Aura datasets must have defined RPO/RTO targets.
- Restore drills must include recommendation ledger, profile data, consent records, and audit traces.
- Training snapshots must carry dataset hashes and lineage back to source datasets.

## Plugin Rules

Aura-specific Data Cloud plugins are allowed only when:

- the requirement is domain-specific
- no existing Data Cloud capability satisfies it cleanly
- the plugin is reusable or clearly bounded
- operational ownership is documented

Plugins must not expose direct infrastructure coupling back into Aura feature code.

---

## Shared Security, Auth, and Audit Contract

- End-user authentication flows through shared auth services.
- Service-to-service identity uses shared security patterns and short-lived credentials.
- Sensitive actions require re-auth where the product contract says so.
- Audit events must include actor, service, action, resource, outcome, and trace context.
- User-facing analytics and events must use tokenized identifiers only.

Aura should define authorization policy at the product layer, but enforcement plumbing should reuse
shared security modules wherever possible.

---

## Shared Observability Contract

All Aura deployables and background handlers must emit:

- structured logs
- traces with propagated `traceId`
- metrics with deployable, module, and handler labels
- audit correlation identifiers for governance-sensitive paths

Minimum dashboards and alerts should cover:

- recommendation latency and error rate
- ingestion freshness and failure rate
- AEP publish/consume failure rate and DLQ depth
- Data Cloud read/write latency and error rate
- export/delete workflow health
- outcome and safety triage backlog

---

## Implementation Placement

| Concern | Preferred Aura Location | Shared Dependency |
| ------- | ----------------------- | ----------------- |
| event schema definitions | `packages/event-contracts` | AEP contracts/runtime |
| outbox relay | `apps/core-worker` or shared async worker module | AEP + Data Cloud relational dataset |
| repository adapters | `apps/api`, `apps/core-worker` | Data Cloud relational/cache/object plugins |
| export assembly orchestration | `apps/core-worker` (`governance`) | Data Cloud + shared security |
| telemetry wiring | app bootstrap and handler middleware | shared o11y |
| auth / re-auth plumbing | `apps/api` gateway middleware | shared auth/security |

---

## Definition of Done For Shared-Platform Integration Work

1. The Aura module uses AEP and/or Data Cloud through documented adapters only.
2. Topic or dataset registration metadata exists before production rollout.
3. Transactional outbox behavior is implemented for cross-process side effects.
4. Contract, integration, replay, and failure-path tests exist.
5. Trace, metric, and audit correlation are visible end to end.
6. No direct broker or raw infrastructure dependency has been added to Aura feature code.
