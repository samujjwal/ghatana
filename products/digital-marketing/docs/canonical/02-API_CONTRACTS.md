# DMOS API Contracts

## Purpose

This document defines the self-contained API contract rules for DMOS. It complements the generated OpenAPI specification and explains endpoint families, headers, error semantics, authorization, idempotency, versioning, and contract validation.

## Canonical Source of Truth

The canonical machine-readable API contract is:

- `products/digital-marketing/docs/api-contract.yaml`

Human-readable contract documentation may be generated from the OpenAPI specification, but the generated spec must remain aligned with backend servlet routes. Backend implementation and OpenAPI must not drift.

## API Principles

1. **Contract-first behavior, code-derived validation:** The API must be described by OpenAPI and validated against route implementation.
2. **Fail closed:** Missing identity, tenant, workspace, permission, or feature state must fail explicitly.
3. **Backend enforcement:** UI route gating is not security. API must enforce authorization and capabilities.
4. **Idempotent writes:** Mutating operations should require `X-Idempotency-Key`.
5. **Consistent errors:** Every error must use a standard envelope.
6. **No fake success:** Disabled, unavailable, unimplemented, or connector-blocked features must not return success.
7. **Observable requests:** Every request must carry or receive a correlation ID.
8. **Tenant isolation:** Every workspace-scoped route must validate tenant and workspace scope.
9. **Versioned stability:** Breaking changes require explicit versioning or migration.

## Required Headers

| Header | Required | Purpose |
|---|---:|---|
| `Authorization` | Production yes | Bearer token or trusted session credential |
| `X-Tenant-ID` | Yes | Tenant isolation boundary |
| `X-Principal-ID` | Dev/local only unless derived | Actor identity; production must derive from token/session |
| `X-Session-ID` | Dev/local only unless derived | Session continuity; production must derive from token/session |
| `X-Correlation-ID` | Recommended / generated if absent | Cross-system request tracing |
| `X-Idempotency-Key` | Writes | Duplicate submission prevention |
| `X-Roles` | Dev/local only | Must not grant access in production |
| `X-Permissions` | Dev/local only | Must not grant access in production |

Production code must not trust client-provided roles or permissions when a trusted identity provider is configured.

## Route Families

All routes are workspace-scoped unless explicitly documented otherwise.

| Area | Route Prefix | Responsibility |
|---|---|---|
| Workspace | `/v1/workspaces/{workspaceId}` | Workspace state, settings, capabilities |
| Campaigns | `/v1/workspaces/{workspaceId}/campaigns` | Campaign list/detail/create/lifecycle |
| Strategy | `/v1/workspaces/{workspaceId}/strategy` | Strategy generation, review, approval |
| Budget | `/v1/workspaces/{workspaceId}/budget` | Budget recommendation and approval |
| Approvals | `/v1/workspaces/{workspaceId}/approvals` | Approval queue, detail, decision |
| AI Actions | `/v1/workspaces/{workspaceId}/ai-actions` | AI action log and provenance |
| Ad Copy | `/v1/workspaces/{workspaceId}/ad-copy` | Ad copy generation and validation |
| Landing Pages | `/v1/workspaces/{workspaceId}/landing-pages` | Landing-page content generation |
| Email Follow-up | `/v1/workspaces/{workspaceId}/email-followup` | Follow-up content generation |
| Content Validation | `/v1/workspaces/{workspaceId}/content-validation` | Brand, policy, compliance validation |
| Website Audit | `/v1/workspaces/{workspaceId}/website-audit` | Website audit and recommendations |
| Funnel Analytics | `/v1/workspaces/{workspaceId}/funnel-analytics` | Funnel metrics and drop-off analysis |
| Attribution | `/v1/workspaces/{workspaceId}/attribution` | Multi-touch attribution |
| ROI/ROAS | `/v1/workspaces/{workspaceId}/roi-roas` | Financial performance reporting |
| Market Research | `/v1/workspaces/{workspaceId}/market-research` | Market, persona, competitor research |
| Connectors | `/v1/workspaces/{workspaceId}/connectors` | Connector setup, status, execution control |

## Standard Resource Semantics

### List endpoints

List endpoints must support, where relevant:

- Pagination.
- Sorting.
- Filtering.
- Tenant/workspace scope.
- Empty state response.
- Permission-aware visibility.
- Stable response schema.
- No cross-tenant totals or counts.

### Detail endpoints

Detail endpoints must:

- Return 404 when resource does not exist in the caller's scope.
- Return 403 when caller lacks permission.
- Return 423 when feature/connector is locked or disabled.
- Include IDs, status, timestamps, and relevant metadata.
- Avoid leaking existence of out-of-scope resources when security requires it.

### Create/update endpoints

Write endpoints must:

- Validate body schema.
- Validate lifecycle transition.
- Validate idempotency key.
- Validate capability and permission.
- Persist data transactionally.
- Emit audit and telemetry.
- Return safe, user-actionable errors.

### Decision endpoints

Approval and review endpoints must:

- Ensure decision maker is authorized.
- Preserve immutable requested snapshot.
- Store decision, notes, decided-by, decided-at, and resulting status.
- Prevent duplicate conflicting decisions.
- Resume or block downstream workflow correctly.

## Error Envelope

All errors should use a consistent shape:

```json
{
  "error": "DMOS_ERROR_CODE",
  "message": "User-safe message",
  "status": 400,
  "correlationId": "corr-123",
  "details": {
    "field": "optional context"
  }
}
```

Canonical error codes:
- `BAD_REQUEST`
- `UNAUTHORIZED`
- `FORBIDDEN`
- `NOT_FOUND`
- `CONFLICT`
- `UNPROCESSABLE_ENTITY`
- `LOCKED`
- `RATE_LIMITED`
- `INTERNAL_ERROR`

## Standard Status Codes

| Code | Meaning |
|---:|---|
| 400 | Bad request, missing header, malformed body, validation error |
| 401 | Missing or invalid authentication |
| 403 | Authenticated but not authorized |
| 404 | Resource not found in caller's visible scope |
| 409 | State conflict, duplicate action, invalid lifecycle transition |
| 422 | Domain/compliance/policy violation |
| 423 | Feature, connector, or capability locked/disabled |
| 429 | Rate limited or abuse control triggered |
| 500 | Unexpected internal error with safe message |
| 502 | External connector/provider failed |
| 503 | Dependency unavailable |
| 504 | Dependency timeout |

## Capability and Permission Model

Each route must declare:

- Required product capability, such as `dmos.campaigns`, `dmos.strategy`, `dmos.budget`, or `dmos.reporting`.
- Required role or permission for action.
- Whether the action is read-only or mutating.
- Whether approval is required.
- Whether feature flags or connector states must be enabled.

The API must always enforce this server-side. UI route availability is an optimization for user experience, not an authority.

## Idempotency

Mutating operations should require `X-Idempotency-Key`. The idempotency store must be scoped by:

- Tenant.
- Workspace.
- Principal/session where appropriate.
- HTTP method.
- Route/action.
- Request hash or equivalent semantic input.

Duplicate requests with the same semantic input should return the original result. Duplicate keys with conflicting input should return 409.

## Versioning

The API version prefix is `/v1`. Breaking changes require one of:

- New route version.
- Backward-compatible transition window.
- Explicit migration plan and client regeneration.
- Contract tests proving old and new behavior where compatibility is required.

Fix-forward refactoring is allowed for incomplete or incorrect internal behavior, but public contracts must remain deliberate and documented.

## OpenAPI Generation and Validation

The OpenAPI specification must be regenerated when route signatures, DTOs, errors, or headers change. Validation must prove:

- Every registered servlet endpoint appears in the spec.
- No orphaned documented endpoint exists.
- Methods, paths, parameters, request bodies, responses, and errors match.
- Client types are generated or validated from the spec.
- Contract drift fails CI.

## API Acceptance Criteria

An endpoint is production-ready only when:

- It is documented in OpenAPI.
- It validates headers, body, auth, tenant, workspace, permissions, feature flags, and idempotency.
- It calls real services and persistence or fails closed.
- It emits audit/telemetry for mutating or sensitive operations.
- It has unit, servlet/API integration, negative-path, and E2E coverage where user-visible.
- It has no production mock, stub, fake success, or hardcoded demo behavior.

## Recovered API Surface Requirements

The historical canonical architecture included additional product domains that must be represented in API contracts as they mature.

## Additional Route Families

| Area | Route Family | Purpose |
|---|---|---|
| Intake | `/v1/workspaces/{workspaceId}/intake` | Business profile, growth goal, constraints, consent capture |
| Free Audit | `/v1/workspaces/{workspaceId}/audits` | Website/market/competitor audit generation |
| Proposals | `/v1/workspaces/{workspaceId}/proposals` | Proposal creation, review, approval, PDF/export |
| Contracts | `/v1/workspaces/{workspaceId}/contracts` | Template-based SOW/MSA draft and approval tracking |
| Leads | `/v1/workspaces/{workspaceId}/leads` | CRM-lite lead capture and status |
| Opportunities | `/v1/workspaces/{workspaceId}/opportunities` | Pipeline tracking and proposal linkage |
| Consent | `/v1/workspaces/{workspaceId}/consents` | Consent proof, revocation, suppression checks |
| Suppression | `/v1/workspaces/{workspaceId}/suppression-lists` | Unsubscribe, opt-out, do-not-contact |
| Experiments | `/v1/workspaces/{workspaceId}/experiments` | A/B tests and learning records |
| Playbooks | `/v1/workspaces/{workspaceId}/playbooks` | Reusable growth playbook versions |
| Commands | `/v1/workspaces/{workspaceId}/commands` | Durable execution command creation/status |
| Workflows | `/v1/workspaces/{workspaceId}/workflows` | Workflow definitions and executions |

These routes must remain feature-gated until backend, persistence, security, and tests exist.

## Command API Pattern

Sensitive operations should not be arbitrary side effects. They should be represented as durable commands.

Required command fields:

- `commandId`
- `tenantId`
- `workspaceId`
- `commandType`
- `targetType`
- `targetId`
- `payload`
- `requestedBy`
- `idempotencyKey`
- `correlationId`
- `status`
- `requiresApproval`
- `rollbackSupported`
- `createdAt`

Example command types:

- `LaunchCampaignCommand`
- `PublishLandingPageCommand`
- `UpdateBudgetCommand`
- `SendEmailCommand`
- `CreateProposalCommand`
- `GenerateContractCommand`
- `PauseCampaignCommand`
- `RollbackCampaignCommand`

## Event Contract Requirements

Every important domain event must include:

- `eventId`
- `eventType`
- `schemaVersion`
- `tenantId`
- `workspaceId`
- `actor`
- `actorType`
- `correlationId`
- `causationId`
- `idempotencyKey`
- `occurredAt`
- `sourceService`
- `externalRefs`
- `policySnapshotId`
- `consentSnapshotId`
- `piiClassification`
- `payloadSchema`
- `payload`

Event families include:

- `CampaignCreated`
- `CampaignStarted`
- `CampaignStopped`
- `CreativeGenerated`
- `CreativeApproved`
- `CreativeRejected`
- `LeadCaptured`
- `LeadQualified`
- `LeadConverted`
- `ContractDrafted`
- `ContractSigned`
- `ContractRenewed`
- `BudgetAllocated`
- `BudgetReallocated`
- `BudgetExhausted`
- `ComplianceViolation`
- `ApprovalRequired`
- `ApprovalGranted`
- `ExperimentStarted`
- `ExperimentConcluded`
- `WinnerDeclared`

## Consent and Suppression API Requirements

Consent APIs must support:

- Recording consent with source, timestamp, consent text, policy version, region, IP/user-agent if legally appropriate.
- Revoking consent.
- Checking consent for channel and purpose.
- Exporting consent proof.
- Enforcing suppression before email, SMS, audience sync, or CRM export.
- Returning blocked state with reason when consent is insufficient.

## Proposal and Contract API Requirements

Proposal/contract APIs must enforce:

- Template-based generation only.
- Human approval before customer-facing send/signature.
- Explicit “not legal advice” metadata.
- Versioned templates and clauses.
- Risk flags for unsupported guarantees, privacy issues, missing approvals, or unrealistic claims.
- Audit events for generation, edit, approval, send, signature, renewal, and rejection.
