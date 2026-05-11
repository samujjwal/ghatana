# DMOS Production MVP Contract

## Purpose

This document defines the production MVP contract for DMOS. It freezes one complete executable loop that must be fully implemented, tested, and production-ready before DMOS can be deployed to production.

**Last Updated:** 2026-05-11
**Commit Baseline:** current working tree

## MVP Loop Definition

The production MVP loop is a single end-to-end path:

```
Intake
  → Website Audit/Research
  → Strategy Generation
  → Budget Recommendation
  → Content Generation (Ad Copy + Landing Page)
  → Approval Workflow
  → Google Search Launch/Export (durable command, not direct connector success)
  → Lead Capture
  → Reporting
  → Next-Best Action
```

Anything outside this loop is a boundary capability until it has the same route, API,
service, persistence, authorization, audit, telemetry, tests, and operations proof.

## Readiness Criteria

Every step in the MVP loop must satisfy ALL of the following:

- **UI Route**: React page with full typing, no `any`, proper error states
- **API Contract**: OpenAPI-documented endpoint with request/response schemas
- **Backend Service**: Application service with domain logic, no production stubs
- **Persistence**: PostgreSQL adapter with Flyway migration (no in-memory adapters in production)
- **Authorization**: Backend-enforced capability and role checks
- **Audit**: All mutating operations emit audit events via kernel bridge
- **Telemetry**: Structured logs, metrics, traces with correlation IDs
- **Tests**: Unit tests + integration tests + E2E tests with meaningful assertions
- **Error Handling**: Fail-closed behavior, no silent failures, proper error envelopes
- **Observability**: Health checks, readiness probes, operational runbooks

## Step-by-Step Contract

### Step 1: Workspace Setup/Intake

**Route:** `/v1/workspaces/{workspaceId}` (GET/POST)
**UI Page:** Workspace settings page
**Capability:** None (tenant-scoped)
**Minimum Role:** admin

**Requirements:**
- Tenant and workspace isolation enforced on all operations
- Workspace creation requires tenant admin role
- Workspace metadata includes: name, description, industry, business goals
- Audit events for workspace create/update/delete
- PostgreSQL persistence with unique constraints
- Tests: create workspace, list workspaces (tenant-scoped), delete workspace

**Status:** Partial - Intake/workspace surfaces exist, but production proof is incomplete.

---

### Step 2: Website Audit/Research

**Route:** `/v1/workspaces/{workspaceId}/audit/run` (POST)
**UI Page:** Website audit page (boundary - marked as unavailable until backend ready)
**Capability:** `dmos.market_research`
**Minimum Role:** brand-manager
**Lifecycle:** Boundary

**Requirements:**
- Website audit service analyzes SEO, performance, content gaps
- Research service generates competitor insights, keyword opportunities
- Audit results persisted with versioning
- AI-generated insights with provenance (model, prompt version, confidence)
- Approval workflow for high-risk recommendations
- Tests: audit generation, result retrieval, approval flow

**Status:** ⚠️ Partial - Backend service exists but not fully production-hardened

**Production Blocker:**
- [ ] Add durable workflow for audit generation
- [ ] Add AI provenance tracking
- [ ] Add approval workflow integration
- [ ] Add integration tests with real audit provider
- [ ] Mark as unavailable in UI until backend complete

---

### Step 3: Strategy Generation

**Route:** `/v1/workspaces/{workspaceId}/strategy` (POST)
**UI Page:** Strategy page
**Capability:** `dmos.strategy`
**Minimum Role:** brand-manager
**Lifecycle:** Stable

**Requirements:**
- Strategy generation service with AI/deterministic hybrid
- Strategy includes: target audience, channel mix, value proposition, messaging
- AI provenance: model version, prompt version, evidence links, confidence
- Approval workflow for strategy approval
- Strategy versioning with immutable snapshots
- Tests: generate strategy, submit for approval, approve/reject, retrieve approved

**Status:** Partial - Service exists, but full provenance, approval, and real-provider proof is incomplete.

---

### Step 4: Budget Recommendation

**Route:** `/v1/workspaces/{workspaceId}/budget-recommendation` (POST)
**UI Page:** Budget page
**Capability:** `dmos.budget`
**Minimum Role:** marketing-director
**Lifecycle:** Stable

**Requirements:**
- Budget recommendation service with financial assumptions
- Budget includes: total spend, channel allocation, pacing, ROI targets
- Assumptions persisted: CAC targets, conversion rates, seasonality
- Approval workflow for budget approval
- Budget enforcement: campaign launch blocked if over budget
- Tests: generate budget, submit for approval, approve, over-budget launch block

**Status:** ⚠️ Partial - Service exists but budget enforcement not complete

**Production Blocker:**
- [ ] Persist budget assumptions with source linkage
- [ ] Add budget enforcement on campaign launch
- [ ] Add budget pacing alerts
- [ ] Add integration tests for enforcement logic
- [ ] Add budget variance reporting

---

### Step 5: Content Generation

**Route:** `/v1/workspaces/{workspaceId}/content-items/{itemId}/ad-copy/generate` (POST)
**Route:** `/v1/workspaces/{workspaceId}/content-items/{itemId}/landing-page/generate` (POST)
**UI Page:** Content management page
**Capability:** `dmos.campaigns`
**Minimum Role:** brand-manager
**Lifecycle:** Stable

**Requirements:**
- Content generation service for ad copy and landing pages
- Content validation: brand safety, policy checks, compliance validation
- Content versioning with approval workflow
- AI provenance: model version, prompt version, evidence, confidence
- Tests: generate content, validate, approve, retrieve approved version

**Status:** Partial - Services exist, but mandatory AI provenance, validation proof, and approval coverage are incomplete.

---

### Step 6: Approval Workflow

**Route:** `/v1/workspaces/{workspaceId}/approvals` (POST)
**Route:** `/v1/workspaces/{workspaceId}/approvals/:requestId/decide` (POST)
**UI Page:** Approval queue page
**Capability:** None (governance)
**Minimum Role:** viewer (view), role-specific (decide)
**Lifecycle:** Stable

**Requirements:**
- Approval request creation with immutable snapshot
- Approval decision with notes, decision timestamp, decision maker
- Risk level assignment: low/medium/high
- Required approver role enforcement
- Audit trail for all approval decisions
- Notification integration for pending approvals
- Tests: create approval, approve, reject, duplicate decision prevention

**Status:** Partial - Core workflow exists; immutable snapshot, duplicate-decision, and resume/block proof remain required.

---

### Step 7: Google Search Launch/Export

**Route:** `/v1/workspaces/{workspaceId}/campaigns/:id/launch` (POST)
**UI Page:** Campaigns page
**Capability:** `dmos.campaigns`
**Minimum Role:** brand-manager
**Lifecycle:** Stable

**Requirements:**
- Campaign launch creates durable command with idempotency key
- Preflight checks: budget approval, content approval, connector health
- Google Ads connector execution with OAuth
- Outbox pattern for external execution
- Retry policy with exponential backoff
- Dead-letter queue for failed executions
- Kill switch at campaign/workspace/tenant/global level
- Rollback support for supported operations
- External ID mapping (DMOS campaign ID ↔ Google Ads campaign ID)
- Tests: launch success, launch failure with retry, duplicate launch prevention, kill switch, rollback

**Status:** Partial - Launch now records explicit `PENDING_LAUNCH`, `LAUNCH_FAILED`, and `EXTERNAL_EXECUTION_BLOCKED` states; workflow replay, sandbox E2E, and external-ID proof remain required.

**Production Blocker:**
- [x] Persist a durable command/outbox record before treating paid-search launch as externally executable
- [x] Represent kill-switch blocked launch as an explicit campaign state
- [x] Remove hardcoded paid-search launch defaults for missing budget/targeting
- [ ] Add retry policy with exponential backoff
- [ ] Add dead-letter queue for failed executions
- [ ] Add rollback support
- [ ] Add external ID mapping persistence
- [ ] Add integration tests with real Google Ads sandbox
- [ ] Add workflow replay tests

---

### Step 8: Lead Capture

**Route:** `/public/v1/workspaces/{workspaceId}/intake/leads` (POST)
**UI Page:** Public landing page form
**Capability:** None (public endpoint)
**Minimum Role:** None (public)
**Lifecycle:** Stable

**Requirements:**
- Public lead capture endpoint with rate limiting
- Lead validation: email format, required fields
- Consent capture: marketing consent, privacy policy acceptance
- Lead persistence with tenant/workspace isolation
- PII handling: hashed identifiers, encrypted contact data (P1-013 pending)
- Suppression check before CRM export
- Tests: lead capture, consent validation, suppression enforcement, rate limiting

**Status:** ⚠️ Partial - Endpoint exists but PII hardening pending

**Production Blocker:**
- [ ] Implement PII hashing (HMAC-SHA256)
- [ ] Implement PII encryption (AES-GCM)
- [ ] Add suppression list enforcement
- [ ] Add DSAR export/delete/anonymize endpoints
- [ ] Add consent revocation handling
- [ ] Add integration tests for PII flows

---

### Step 9: Reporting

**Route:** `/v1/workspaces/{workspaceId}/dashboard` (GET)
**UI Page:** Dashboard page
**Capability:** None (dashboard)
**Minimum Role:** viewer
**Lifecycle:** Stable

**Requirements:**
- Backend computes dashboard summary (no UI-side aggregation)
- Metrics: campaign count, approval queue size, active campaigns, budget pacing, lead count
- Freshness indicator: data timestamp, staleness alerts
- Confidence labels: high/medium/low based on data quality
- Dashboard/report/export parity
- Tests: dashboard accuracy, stale data handling, empty state, export parity

**Status:** Partial - Dashboard summary service exists; UI still must consume only this canonical API and stop local KPI aggregation.

**Production Blocker:**
- [ ] Implement DashboardSummary API contract
- [ ] Add backend metric computation
- [ ] Add freshness tracking
- [ ] Add confidence labeling
- [ ] Add dashboard/report parity tests
- [ ] Add stale data alerting

---

### Step 10: Next-Best Action

**Route:** `/v1/workspaces/{workspaceId}/ai-optimization` (GET/POST)
**UI Page:** AI optimization page
**Capability:** `dmos.ai_optimization`
**Minimum Role:** brand-manager
**Lifecycle:** Boundary

**Requirements:**
- Next-best action recommendation engine
- Recommendations based on: campaign performance, budget pacing, lead quality
- AI provenance: model version, evidence, assumptions, confidence
- Approval workflow for high-risk recommendations
- Tests: generate recommendations, approve, reject, evidence validation

**Status:** ⚠️ Partial - Service exists but not production-hardened

**Production Blocker:**
- [ ] Add durable workflow for recommendation generation
- [ ] Add AI provenance tracking
- [ ] Add approval workflow integration
- [ ] Add integration tests
- [ ] Mark as unavailable in UI until backend complete

---

## Production Readiness Matrix

| Step | UI Route | API Contract | Backend Service | Persistence | Authorization | Audit | Telemetry | Tests | Status |
|------|----------|-------------|-----------------|-------------|----------------|-------|-----------|-------|--------|
| 1. Intake/Workspace | Partial | Partial | Partial | Partial | Partial | Partial | Partial | Partial | Partial |
| 2. Website Audit | Boundary | Partial | Partial | Partial | Partial | Partial | Partial | Partial | Partial |
| 3. Strategy Generation | Partial | Partial | Partial | Partial | Partial | Partial | Partial | Partial | Partial |
| 4. Budget Recommendation | Partial | Partial | Partial | Partial | Partial | Partial | Partial | Partial | Partial |
| 5. Content Generation | Partial | Partial | Partial | Partial | Partial | Partial | Partial | Partial | Partial |
| 6. Approval Workflow | Partial | Partial | Partial | Partial | Partial | Partial | Partial | Partial | Partial |
| 7. Google Search Launch | Partial | Partial | Partial | Partial | Partial | Partial | Partial | Partial | Partial |
| 8. Lead Capture | Partial | Partial | Partial | Partial | Partial | Partial | Partial | Partial | Partial |
| 9. Reporting | Partial | Partial | Partial | Partial | Partial | Partial | Partial | Partial | Partial |
| 10. Next-Best Action | Boundary | Boundary | Partial | Boundary | Partial | Partial | Partial | Partial | Boundary |

## Production Deployment Decision

DMOS is **NOT** production-ready until:

1. All 10 MVP steps are marked `Ready`
2. All production blockers are resolved
3. Real-backend E2E tests pass for the complete loop
4. Privacy/consent/DSAR implementation is complete (P1-013)
5. Release gates are mandatory in CI and fail on security/privacy/dashboard warnings

## Boundary Route Policy

Routes marked as **Boundary** in this contract must:

- Return explicit locked/unavailable state in UI
- Return 423 (Locked) or 403 (Forbidden) from API
- Include clear error message: "This feature is not yet available"
- Have backend gate that prevents execution
- Be excluded from production readiness claims until complete

## Next Steps

The implementation sequence for resolving production blockers is:

1. Complete route/action/capability generation so UI, API, docs, and tests consume one manifest.
2. Complete durable workflow execution for campaign launch, including replay, DLQ, rollback, and external-ID mapping proof.
3. Complete privacy/consent/DSAR implementation and enforce it before all contact-data actions.
4. Move dashboard UI to the canonical `DashboardSummary` API.
5. Govern AI workflows end to end with mandatory provenance, evidence, risk, redaction, and approval metadata.
6. Make release gates mandatory and warning-free.
7. Keep duplicate/stale docs and UI surfaces archived or out of production builds.
