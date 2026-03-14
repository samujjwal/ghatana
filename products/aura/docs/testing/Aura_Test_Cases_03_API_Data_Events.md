# Aura Test Cases 03: API, Data, Database, and Event Contracts

Version: 1.0
Date: March 13, 2026

## Scope

This suite covers:

- GraphQL query and mutation contracts
- REST endpoint behavior
- Prisma and persistence contracts
- enum and reason-code coverage
- event topics, schemas, idempotency, and replay behavior
- data export, deletion, and consent audit paths

---

## A. GraphQL Query Contracts

### AURA-ADE-001 `me` query returns profile and consent summary for authenticated user only
Level: Contract
Priority: P0
Source Docs: `Aura_API_Contracts.md`
Preconditions: Two users exist; one is authenticated.
Steps:
1. Call `me` with valid token.
2. Call `me` without token.
3. Attempt access to another user's data through `me`.
Expected:
- Authenticated request returns only current user's profile and consents.
- Unauthenticated request fails with `UNAUTHORIZED`.
- Cross-user leakage is impossible.

### AURA-ADE-002 `product(id)` query returns full trust-relevant source metadata
Level: Contract
Priority: P0
Source Docs: `Aura_API_Contracts.md`, `Aura_UI_UX_Blueprint.md`
Preconditions: Product has multiple merchant sources with mixed affiliate and freshness states.
Steps:
1. Query product detail.
Expected:
- `sources` contain merchant, price, availability, affiliate, freshness, and verification fields.
- `shades` contain canonical depth and mapping confidence when applicable.

### AURA-ADE-003 `recommendations` query always returns required explainability fields
Level: Contract
Priority: P0
Source Docs: `Aura_API_Contracts.md`, `Aura_Master_Platform_Specification.md`
Preconditions: Valid recommendation request.
Steps:
1. Query recommendations.
Expected:
- Every recommendation includes `id`, `score`, `confidence`, `confidenceLabel`, `reasons`, `explanation`, `trustFlags`, `evidence`, and `servedAt`.

### AURA-ADE-004 `compareProducts` returns per-product trust flags and reasons
Level: Contract
Priority: P1
Source Docs: `Aura_API_Contracts.md`, `Aura_UI_UX_Blueprint.md`
Preconditions: Multiple supported products.
Steps:
1. Query compare for valid IDs.
Expected:
- Each compared product includes compatibility score, reasons, and trust flags.

### AURA-ADE-005 `recommendationHistory` paginates deterministically
Level: Contract
Priority: P1
Source Docs: `Aura_API_Contracts.md`
Preconditions: User has more history items than page size.
Steps:
1. Query first page.
2. Query next page using returned cursor.
Expected:
- Pages are stable and non-overlapping.
- Ordering is deterministic.

### AURA-ADE-006 `searchProducts` respects filter contract and total count
Level: Contract
Priority: P1
Source Docs: `Aura_API_Contracts.md`
Preconditions: Searchable catalog.
Steps:
1. Search with and without filters.
Expected:
- Filter semantics match contract.
- `total` reflects filtered result count.

---

## B. GraphQL Mutation Contracts

### AURA-ADE-007 `updateProfile` accepts core service edits without optional consent
Level: Contract
Priority: P0
Source Docs: `Aura_API_Contracts.md`, `Aura_PRD_v1.md`
Preconditions: Authenticated user, no optional consents.
Steps:
1. Call `updateProfile` with declared core fields.
Expected:
- Mutation succeeds.
- No `CONSENT_REQUIRED` error is returned.

### AURA-ADE-008 `updateProfile` rejects malformed spending and array payloads
Level: Contract
Priority: P0
Source Docs: `Aura_API_Contracts.md`
Preconditions: Authenticated user.
Steps:
1. Send invalid `spendingPreference`.
2. Send malformed `skinConcerns` or allergy arrays.
Expected:
- Validation errors identify offending field.

### AURA-ADE-009 `overrideProfileAttribute` preserves origin and auditability
Level: Contract
Priority: P1
Source Docs: `Aura_API_Contracts.md`, `Aura_Event_Architecture.md`
Preconditions: Existing inferred attribute.
Steps:
1. Override inferred attribute.
Expected:
- Returned profile reflects overridden value.
- Updated origin/history is persisted.

### AURA-ADE-010 `deleteProfileAttribute` removes only requested attribute branch
Level: Contract
Priority: P1
Source Docs: `Aura_API_Contracts.md`
Preconditions: Multiple profile attributes exist.
Steps:
1. Delete one inferred attribute by key and origin.
Expected:
- Only targeted attribute is removed.
- Other attributes remain unchanged.

### AURA-ADE-011 `saveProduct` and `unsaveProduct` are idempotent
Level: Contract
Priority: P1
Source Docs: `Aura_API_Contracts.md`
Preconditions: Authenticated user and known product.
Steps:
1. Save twice.
2. Unsave twice.
Expected:
- Repeated calls do not create duplicate states or errors.

### AURA-ADE-012 `submitFeedback` accepts lightweight interaction types only
Level: Contract
Priority: P1
Source Docs: `Aura_API_Contracts.md`
Preconditions: Authenticated user and recommendation.
Steps:
1. Submit each supported feedback type.
2. Submit unsupported type.
Expected:
- Supported types are accepted.
- Unsupported type is rejected.

### AURA-ADE-013 `submitRecommendationOutcome` accepts post-use outcomes separately from interaction feedback
Level: Contract
Priority: P0
Source Docs: `Aura_API_Contracts.md`
Preconditions: Authenticated user and recommendation history.
Steps:
1. Submit `PURCHASE_CONFIRMED`, `RETURN_REPORTED`, `ADVERSE_REACTION_REPORTED`, and `SHADE_FEEDBACK`.
Expected:
- Outcome payloads succeed.
- Interaction feedback and outcome reporting remain distinct.

### AURA-ADE-014 `submitRecommendationOutcome` validates shade feedback payload shape
Level: Contract
Priority: P1
Source Docs: `Aura_API_Contracts.md`
Preconditions: Outcome type `SHADE_FEEDBACK`.
Steps:
1. Submit valid shade result.
2. Submit shade outcome without required shade result.
Expected:
- Valid request succeeds.
- Invalid request fails with clear validation detail.

### AURA-ADE-015 `requestDataExport` creates retrievable export request record
Level: Contract
Priority: P0
Source Docs: `Aura_API_Contracts.md`, `Aura_Event_Architecture.md`
Preconditions: Authenticated user.
Steps:
1. Call `requestDataExport`.
Expected:
- Export request ID and status are returned.
- Event and persistence records are created.

### AURA-ADE-016 `grantConsent` accepts only declared scopes
Level: Contract
Priority: P0
Source Docs: `Aura_API_Contracts.md`, `Aura_Data_Architecture.md`
Preconditions: Authenticated user.
Steps:
1. Grant each valid consent scope.
2. Attempt invalid scope.
Expected:
- Valid scopes succeed.
- Invalid scope is rejected.

### AURA-ADE-017 `revokeConsent` only revokes target scope
Level: Contract
Priority: P0
Source Docs: `Aura_API_Contracts.md`
Preconditions: Multiple scopes granted.
Steps:
1. Revoke one scope.
Expected:
- Only target scope becomes revoked.
- Other scopes remain granted.

### AURA-ADE-018 `deleteAccount` enforces re-authentication
Level: Contract
Priority: P0
Source Docs: `Aura_API_Contracts.md`
Preconditions: Authenticated user.
Steps:
1. Attempt deletion without fresh auth.
2. Repeat with required re-authentication.
Expected:
- First attempt fails safely.
- Second succeeds.

---

## C. REST Endpoint Contracts

### AURA-ADE-019 `GET /v1/products/{id}` mirrors GraphQL product trust fields
Level: Contract
Priority: P1
Source Docs: `Aura_API_Contracts.md`
Preconditions: Known product.
Steps:
1. Call REST product endpoint.
Expected:
- Response contains equivalent trust-relevant fields needed by clients.

### AURA-ADE-020 `POST /v1/recommendations/query` returns evidence and trust flags
Level: Contract
Priority: P0
Source Docs: `Aura_API_Contracts.md`
Preconditions: Valid supported query.
Steps:
1. Call endpoint with category and filters.
Expected:
- Returned recommendations include explanation, reasons, evidence, confidence, and trust flags.

### AURA-ADE-021 `POST /v1/feedback` enforces auth and validation
Level: Contract
Priority: P1
Source Docs: `Aura_API_Contracts.md`
Preconditions: Product and recommendation fixtures.
Steps:
1. Call with valid token.
2. Call without token.
3. Call with malformed payload.
Expected:
- Authenticated valid request succeeds.
- Missing auth fails.
- Malformed request fails with validation error.

### AURA-ADE-022 `POST /v1/recommendations/outcomes` persists post-use signals
Level: Contract
Priority: P0
Source Docs: `Aura_API_Contracts.md`, `Aura_Event_Architecture.md`
Preconditions: Known recommendation.
Steps:
1. Submit adverse reaction and return outcomes.
Expected:
- Outcome is accepted.
- Persistence and event publication occur.

### AURA-ADE-023 `POST /v1/consents` is required only for optional/high-sensitivity features
Level: Integration
Priority: P0
Source Docs: `Aura_API_Contracts.md`, `Aura_Data_Architecture.md`
Preconditions: User can use core service without optional consents.
Steps:
1. Use core flows without consent endpoint.
2. Attempt optional feature before and after consent.
Expected:
- Core service works without consent endpoint.
- Optional feature fails before grant and succeeds after grant.

### AURA-ADE-024 `POST /v1/data-export` rejects unauthorized callers
Level: Contract
Priority: P0
Source Docs: `Aura_API_Contracts.md`
Preconditions: None.
Steps:
1. Call endpoint without auth.
Expected:
- Request fails with `UNAUTHORIZED`.

### AURA-ADE-025 Error envelope is stable across API failures
Level: Contract
Priority: P0
Source Docs: `Aura_API_Contracts.md`
Preconditions: Ability to trigger auth, validation, not-found, and consent errors.
Steps:
1. Trigger each error class.
Expected:
- All errors use consistent `error.code`, `message`, and optional `details`.

---

## D. Database and Persistence Contracts

### AURA-ADE-026 `UserProfile` persists skin concerns and spending preference
Level: Integration
Priority: P0
Source Docs: `Aura_Database_Schema_Prisma.md`, `Aura_Data_Architecture.md`
Preconditions: Profile persistence path.
Steps:
1. Save profile with `skinConcerns` and `spendingPreference`.
2. Reload from DB.
Expected:
- Fields round-trip without loss or type corruption.

### AURA-ADE-027 `UserProfileAttribute` stores JSON values and origin metadata
Level: Integration
Priority: P0
Source Docs: `Aura_Database_Schema_Prisma.md`
Preconditions: Mixed declared, inferred, and imported attributes.
Steps:
1. Persist array-like and object-like attribute values.
Expected:
- JSON values persist correctly.
- `origin`, `sourceScope`, and `updatedAt` are preserved.

### AURA-ADE-028 `ProductShade` persists canonical depth and mapping confidence
Level: Integration
Priority: P1
Source Docs: `Aura_Database_Schema_Prisma.md`, `Aura_Shade_Color_Ontology.md`
Preconditions: Ingestion pipeline produces mapped shades.
Steps:
1. Persist mapped shade.
2. Reload.
Expected:
- `canonicalDepth`, `coverage`, and `mappingConfidence` remain intact.

### AURA-ADE-029 `ProductSource` supports commerce and trust use cases without opaque-only payload dependence
Level: Integration
Priority: P0
Source Docs: `Aura_Database_Schema_Prisma.md`, `Aura_Data_Architecture.md`
Preconditions: Merchant source data fixture.
Steps:
1. Persist source with price, affiliate, freshness, and availability fields.
2. Query back through ORM.
Expected:
- All source-level trust and commerce fields are queryable directly.

### AURA-ADE-030 `Recommendation` record stores audit-critical fields
Level: Integration
Priority: P0
Source Docs: `Aura_Database_Schema_Prisma.md`, `Aura_Intelligence_Platform_Architecture.md`
Preconditions: Recommendation generated.
Steps:
1. Persist recommendation.
2. Inspect DB record.
Expected:
- Score, confidence, trust flags, evidence, profile snapshot, context, model version, and served timestamp are stored.

### AURA-ADE-031 `RecommendationReasonCode` enum covers all documented codes
Level: Unit
Priority: P0
Source Docs: `Aura_Database_Schema_Prisma.md`, `Aura_AI_Engine_Design.md`
Preconditions: Enum definitions available.
Steps:
1. Compare enum to documented reason code set.
Expected:
- No documented code is missing from persistence layer.

### AURA-ADE-032 `FeedbackEventType` enum covers interaction and post-use branches where intended
Level: Unit
Priority: P0
Source Docs: `Aura_Database_Schema_Prisma.md`, `Aura_API_Contracts.md`
Preconditions: Enum definitions available.
Steps:
1. Compare enum to documented feedback and outcome pathways.
Expected:
- Lightweight interaction types and outcome types are either explicitly separated or intentionally modeled.
- No unsupported branch is silently dropped.

### AURA-ADE-033 `DataExportRequest` lifecycle persists requested and completed states
Level: Integration
Priority: P0
Source Docs: `Aura_Database_Schema_Prisma.md`, `Aura_API_Contracts.md`
Preconditions: Export worker path.
Steps:
1. Create export request.
2. Mark complete.
Expected:
- Lifecycle timestamps and artifact reference persist correctly.

---

## E. Event Schema and Workflow Contracts

### AURA-ADE-034 `ProfileUpdated` event contains changed keys and origin
Level: Contract
Priority: P0
Source Docs: `Aura_Event_Architecture.md`
Preconditions: Profile mutation path.
Steps:
1. Update profile.
Expected:
- Event includes `changedKeys`, `origin`, `userId`, `eventId`, and `occurredAt`.

### AURA-ADE-035 Consent grant and revoke emit distinct events
Level: Integration
Priority: P0
Source Docs: `Aura_Event_Architecture.md`, `Aura_API_Contracts.md`
Preconditions: Consent scope lifecycle.
Steps:
1. Grant consent.
2. Revoke same scope.
Expected:
- `ConsentGranted` and `ConsentRevoked` are emitted separately.
- Payload scope matches requested scope.

### AURA-ADE-036 `DataExportRequested` is emitted exactly once per export request
Level: Integration
Priority: P1
Source Docs: `Aura_Event_Architecture.md`
Preconditions: Export request submitted.
Steps:
1. Trigger export creation.
2. Inspect event topic and deduplication behavior.
Expected:
- Single logical request maps to single export-request event despite retries.

### AURA-ADE-037 `RecommendationGenerated` contains trust fields required by downstream consumers
Level: Contract
Priority: P0
Source Docs: `Aura_Event_Architecture.md`
Preconditions: Recommendation generation path.
Steps:
1. Inspect event payload.
Expected:
- Event includes recommendation ID, product, score, confidence, model version, reasons, and trust flags.

### AURA-ADE-038 `FeedbackCaptured` carries decision latency when available
Level: Contract
Priority: P1
Source Docs: `Aura_Event_Architecture.md`
Preconditions: Save or purchase action following served recommendation.
Steps:
1. Capture user action with measurable latency.
Expected:
- `decisionLatencyMs` is present for qualifying actions.

### AURA-ADE-039 `RecommendationOutcomeCaptured` supports all documented outcome branches
Level: Contract
Priority: P0
Source Docs: `Aura_Event_Architecture.md`, `Aura_API_Contracts.md`
Preconditions: Outcome report path.
Steps:
1. Emit return, adverse reaction, and shade feedback outcomes.
Expected:
- Outcome schema supports each branch without overloading unrelated fields.

### AURA-ADE-040 Event consumers are idempotent under duplicate delivery
Level: Integration
Priority: P0
Source Docs: `Aura_Event_Architecture.md`
Preconditions: Same event replayed more than once.
Steps:
1. Replay duplicate event to each key consumer.
Expected:
- No duplicate side effects occur.
- Seen-event or idempotency handling works.

### AURA-ADE-041 Unsupported schema version routes to DLQ path
Level: Integration
Priority: P1
Source Docs: `Aura_Event_Architecture.md`
Preconditions: Consumer receives unsupported event version.
Steps:
1. Publish incompatible schema version.
Expected:
- Consumer rejects payload safely.
- Event is routed to DLQ or investigation path.

### AURA-ADE-042 Analytics pipeline receives tokenized IDs only
Level: Integration
Priority: P0
Source Docs: `Aura_Data_Architecture.md`, `Aura_Event_Architecture.md`
Preconditions: Analytics consumer subscribed.
Steps:
1. Inspect analytics-bound payloads.
Expected:
- Raw PII is absent.
- Tokenized identifiers are used.

### AURA-ADE-043 Export and deletion workflows generate auditable governance events
Level: Integration
Priority: P0
Source Docs: `Aura_Event_Architecture.md`, `Aura_System_Architecture.md`
Preconditions: User requests export and account deletion.
Steps:
1. Execute each workflow.
Expected:
- Governance/audit events exist for both.
- Resource type and outcome values are correct.

### AURA-ADE-044 Transactional outbox publishes cross-process events only after durable state commit
Level: Integration
Priority: P0
Source Docs: `Aura_Shared_Platform_Integration_Spec.md`, `Aura_Event_Architecture.md`
Preconditions: Mutation path that writes durable state and emits an AEP event.
Steps:
1. Execute profile, consent, or outcome mutation.
2. Force publish retry after the durable write succeeds.
Expected:
- Domain state and outbox record commit atomically.
- AEP publication occurs from the outbox relay, not inline-only best effort.
- No event is published for rolled-back transactions.

### AURA-ADE-045 AEP topic registration and schema metadata exist before producer rollout
Level: Contract
Priority: P1
Source Docs: `Aura_Shared_Platform_Integration_Spec.md`, `Aura_Event_Architecture.md`
Preconditions: New or changed Aura topic/event contract.
Steps:
1. Review topic registration metadata.
2. Review schema version and ownership metadata.
Expected:
- Topic name, owner, schema source, ordering key, DLQ policy, and PII class are registered.
- Producer rollout is blocked if registration is missing.

### AURA-ADE-046 Data Cloud dataset registration includes retention, export, and deletion behavior
Level: Contract
Priority: P1
Source Docs: `Aura_Shared_Platform_Integration_Spec.md`, `Aura_Data_Architecture.md`
Preconditions: Production dataset used for catalog, profile, recommendation, outcome, or export flow.
Steps:
1. Review dataset registration metadata.
Expected:
- Dataset owner, schema source, retention class, export behavior, deletion behavior, and restore priority are recorded.
- Missing registration blocks production readiness.

### AURA-ADE-047 Trace and actor context propagate from API to Data Cloud and AEP boundaries
Level: Integration
Priority: P1
Source Docs: `Aura_Shared_Platform_Integration_Spec.md`, `Aura_API_Contracts.md`
Preconditions: User-initiated mutation that writes state and emits event.
Steps:
1. Execute mutation.
2. Inspect trace, audit, Data Cloud write metadata, and emitted AEP event.
Expected:
- `traceId` is preserved end to end.
- actor context is available in audit and event metadata where allowed.
- telemetry allows a single mutation to be traced across API, Data Cloud, and AEP.
