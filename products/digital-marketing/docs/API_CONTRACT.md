# DMOS API Contract

**Status**: CANONICAL  
**Version**: v1  
**Base URL**: `https://<host>/` (all authenticated routes require `X-Tenant-ID` and `X-Principal-ID`)  
**Last Updated**: 2026-03-27

---

## Global Headers

All authenticated endpoints (every route except `/public/*`) require:

| Header            | Required | Description                                         |
|-------------------|----------|-----------------------------------------------------|
| `X-Tenant-ID`     | Yes      | Tenant identifier. Used for auth and rate limiting. |
| `X-Principal-ID`  | Yes      | Principal (user/service) identifier.                |
| `Content-Type`    | When body | `application/json` for all request bodies.          |

Public intake endpoints require no auth headers.

---

## Rate Limiting

All routes are wrapped by `DmosApiRateLimiter`.

- Default: **60 requests / 60 seconds** per tenant (env-configurable via `DMOS_RATE_LIMIT_MAX_REQUESTS` / `DMOS_RATE_LIMIT_WINDOW_SECONDS`).
- Keyed by `X-Tenant-ID` first; falls back to `X-Forwarded-For` IP.
- On limit exceeded: `429 Too Many Requests` with JSON body and `Retry-After` header (seconds).

```json
{
  "error": "TOO_MANY_REQUESTS",
  "message": "Rate limit exceeded. Retry after 42 seconds.",
  "retryAfterSeconds": 42
}
```

---

## Error Envelope

All error responses follow a consistent JSON shape:

```json
{
  "error": "ERROR_CODE",
  "message": "Human-readable detail."
}
```

| HTTP Status | Meaning                                              |
|-------------|------------------------------------------------------|
| `400`       | Bad request — missing or invalid field.              |
| `401`       | Missing or invalid authentication headers.           |
| `403`       | Authorised tenant but insufficient permission.       |
| `404`       | Resource not found.                                  |
| `409`       | Conflict — e.g. duplicate create.                    |
| `429`       | Rate limit exceeded.                                 |
| `500`       | Internal server error.                               |

---

## Workspaces

> Servlet: `DmosWorkspaceServlet`

### `POST /v1/workspaces`

Create a new workspace for a tenant.

**Request body**
```json
{
  "name": "Acme Q1 Campaign",
  "description": "Optional description"
}
```

**Response `201 Created`**
```json
{
  "id": "ws-abc123",
  "tenantId": "tenant-1",
  "name": "Acme Q1 Campaign",
  "description": "Optional description",
  "status": "ACTIVE",
  "createdAt": "2026-01-01T00:00:00Z",
  "updatedAt": "2026-01-01T00:00:00Z",
  "createdBy": "user-1"
}
```

---

### `GET /v1/workspaces`

List all workspaces for the caller's tenant.

**Response `200 OK`**
```json
[
  {
    "id": "ws-abc123",
    "name": "Acme Q1 Campaign",
    "status": "ACTIVE"
  }
]
```

---

### `GET /v1/workspaces/:workspaceId`

Get a single workspace by ID.

**Response `200 OK`** — workspace object (same shape as create response).  
**Response `404`** — workspace not found or not owned by caller's tenant.

---

### `POST /v1/workspaces/:workspaceId/suspend`

Suspend an active workspace.

**Request body**: none  
**Response `200 OK`** — updated workspace with `"status": "SUSPENDED"`.

---

### `POST /v1/workspaces/:workspaceId/reactivate`

Reactivate a suspended workspace.

**Request body**: none  
**Response `200 OK`** — updated workspace with `"status": "ACTIVE"`.

---

## Campaigns

> Servlet: `DmosCampaignServlet`

### `POST /v1/workspaces/:workspaceId/campaigns`

Create a new campaign.

**Request body**
```json
{
  "name": "Spring Promo",
  "type": "PAID_SEARCH"
}
```

Valid `type` values: `PAID_SEARCH`, `EMAIL`, `SOCIAL`, `PUSH`, `DISPLAY`.

**Response `201 Created`** — campaign object with `id`, `status: "DRAFT"`, `workspaceId`, timestamps.

---

### `GET /v1/workspaces/:workspaceId/campaigns/:id`

Get a single campaign.

**Response `200 OK`** — campaign object.  
**Response `404`** — not found.

---

### `POST /v1/workspaces/:workspaceId/campaigns/:id/launch`

Launch a campaign that has passed preflight.

**Request body**: none  
**Response `200 OK`** — updated campaign with `"status": "LAUNCHED"`.  
**Response `409`** — campaign not in launchable state.

---

### `POST /v1/workspaces/:workspaceId/campaigns/:id/pause`

Pause a launched campaign.

**Request body**: none  
**Response `200 OK`** — updated campaign with `"status": "PAUSED"`.

---

## Strategy

> Servlet: `DmosStrategyServlet`

### `POST /v1/workspaces/:workspaceId/strategy`

Generate an AI marketing strategy for the workspace.

**Request body**
```json
{
  "goal": "Increase local awareness",
  "targetAudience": "Homeowners aged 35-55",
  "budgetHint": 5000
}
```

**Response `201 Created`** — strategy object with `strategyId`, `status: "DRAFT"`, `content`.

---

### `POST /v1/workspaces/:workspaceId/strategy/:strategyId/submit`

Submit a strategy draft for human approval.

**Request body**: none  
**Response `200 OK`** — strategy with `"status": "PENDING_APPROVAL"` and `approvalRequestId`.

---

### `POST /v1/workspaces/:workspaceId/strategy/:strategyId/approve`

Approve a strategy (requires approver role).

**Request body**
```json
{
  "comment": "Looks good"
}
```

**Response `200 OK`** — strategy with `"status": "APPROVED"`.

---

### `GET /v1/workspaces/:workspaceId/strategy`

Get the latest strategy for the workspace.

**Response `200 OK`** — strategy object.  
**Response `404`** — no strategy exists yet.

---

## Approvals

> Servlet: `DmosApprovalServlet`

### `POST /v1/workspaces/:workspaceId/approvals`

Submit a new approval request.

**Request body**
```json
{
  "targetType": "STRATEGY",
  "targetId": "strat-abc",
  "snapshotSummary": "Strategy for Acme Q1",
  "riskLevel": 3,
  "requiredApproverRole": "brand-manager"
}
```

Valid `targetType` values: `STRATEGY`, `PROPOSAL`, `SOW`, `CONTENT_VERSION`, `BUDGET`, `CAMPAIGN_LAUNCH`, `CONNECTOR_WRITE`, `OVERRIDE`.

**Response `201 Created`** — approval record with `requestId`, `status: "PENDING"`.

---

### `POST /v1/workspaces/:workspaceId/approvals/:requestId/decide`

Record an approval decision (approve or reject).

**Request body**
```json
{
  "decision": "APPROVED",
  "comment": "Reviewed and confirmed."
}
```

Valid `decision` values: `APPROVED`, `REJECTED`.

**Response `200 OK`** — updated approval record with `status: "APPROVED"` or `"REJECTED"`.  
**Response `403`** — caller lacks the required approver role.  
**Response `404`** — approval request not found.  
**Response `409`** — already decided.

---

### `GET /v1/workspaces/:workspaceId/approvals/:requestId`

Get the current status of an approval request.

**Response `200 OK`**
```json
{
  "requestId": "apr-xyz",
  "status": "PENDING",
  "targetType": "STRATEGY",
  "targetId": "strat-abc",
  "createdAt": "2026-01-01T10:00:00Z",
  "decidedAt": null,
  "decidedBy": null
}
```

---

### `GET /v1/workspaces/:workspaceId/approvals/:requestId/snapshot`

Get the immutable approval snapshot captured at submission time.

**Response `200 OK`** — `ApprovalSnapshot` with `requestId`, `targetType`, `targetId`, `snapshotSummary`, `validationResultId`, `riskLevel`, `requiredApproverRole`, `snapshotAt`.  
**Response `404`** — no snapshot found.

---

### `GET /v1/workspaces/:workspaceId/approvals/pending/:subjectId`

List all pending approvals for a given subject entity.

**Response `200 OK`** — array of pending approval records.

---

## AI Action Log

> Servlet: `DmosAiActionLogServlet`

### `POST /v1/workspaces/:workspaceId/ai-actions`

Record an AI or system action in the transparency timeline.

**Request body**
```json
{
  "correlationId": "corr-123",
  "actionType": "RECOMMENDATION_GENERATED",
  "status": "PROPOSED",
  "actor": "dmos-strategy-agent",
  "initiatedByAi": true,
  "confidence": 0.92,
  "evidenceLinks": ["https://internal/evidence/1"],
  "policyChecks": ["GDPR_CONSENT_VERIFIED"],
  "summary": "Budget recommendation generated.",
  "details": "Recommended $5,000/month based on market data.",
  "relatedEntityId": "budget-rec-456"
}
```

Valid `actionType` values: `RECOMMENDATION_GENERATED`, `DRAFT_GENERATED`, `VALIDATION_RESULT`, `APPROVAL_DECISION`, `ACTION_EXECUTED`, `ACTION_BLOCKED`.  
Valid `status` values: `PROPOSED`, `EXECUTED`, `BLOCKED`, `APPROVED`, `REJECTED`.

**Response `201 Created`** — `AiActionLogEntry` with `actionId`.

---

### `GET /v1/workspaces/:workspaceId/ai-actions`

List AI action log entries for the workspace.

**Query parameters**

| Parameter         | Type   | Description                                    |
|-------------------|--------|------------------------------------------------|
| `correlationId`   | string | Filter by correlation ID.                      |
| `relatedEntityId` | string | Filter by related entity.                      |
| `limit`           | int    | Max results (default: 50, max: 200).           |

**Response `200 OK`** — array of `AiActionLogEntry` objects, ordered by `occurredAt` descending.  
**Note**: Entries are redacted (details shown as `"REDACTED"`) for principals without `ai-action-log-sensitive:read` permission.

---

### `GET /v1/workspaces/:workspaceId/ai-actions/:actionId`

Get a single AI action log entry.

**Response `200 OK`** — `AiActionLogEntry`.  
**Response `404`** — not found.

---

## Proposal

> Servlet: `DmosProposalServlet`

### `POST /v1/workspaces/:workspaceId/proposal`

Generate a client proposal document.

**Response `201 Created`** — proposal with `proposalId`, `status: "DRAFT"`.

### `POST /v1/workspaces/:workspaceId/proposal/:proposalId/submit`

Submit proposal for review. **Response `200 OK`**.

### `POST /v1/workspaces/:workspaceId/proposal/:proposalId/approve`

Approve a submitted proposal. **Response `200 OK`**.

### `GET /v1/workspaces/:workspaceId/proposal/:proposalId`

Get a proposal. **Response `200 OK`**.

---

## Statement of Work (SOW)

> Servlet: `DmosSowServlet`

### `POST /v1/workspaces/:workspaceId/sow`

Generate a SOW draft. **Response `201 Created`**.

### `POST /v1/workspaces/:workspaceId/sow/:sowId/submit`

Submit SOW for review. **Response `200 OK`**.

### `POST /v1/workspaces/:workspaceId/sow/:sowId/approve`

Approve a SOW. **Response `200 OK`**.

### `POST /v1/workspaces/:workspaceId/sow/:sowId/export`

Export the approved SOW (e.g., PDF). **Response `200 OK`** with binary body and `Content-Type: application/pdf`.

### `GET /v1/workspaces/:workspaceId/sow/:sowId`

Get a SOW draft. **Response `200 OK`**.

---

## Budget Recommendation

> Servlet: `DmosBudgetRecommendationServlet`

### `POST /v1/workspaces/:workspaceId/budget-recommendation`

Generate an AI budget recommendation. **Response `201 Created`**.

### `POST /v1/workspaces/:workspaceId/budget-recommendation/:recId/submit`

Submit for approval. **Response `200 OK`**.

### `POST /v1/workspaces/:workspaceId/budget-recommendation/:recId/approve`

Approve the recommendation. **Response `200 OK`**.

### `GET /v1/workspaces/:workspaceId/budget-recommendation`

Get the latest recommendation. **Response `200 OK`**.

---

## Content Versions

> Servlet: `DmosContentVersionServlet`

### `POST /v1/workspaces/:workspaceId/content-items`

Create a new content item. **Response `201 Created`** with `itemId`.

### `POST /v1/workspaces/:workspaceId/content-items/:itemId/versions`

Create a new version of a content item. **Response `201 Created`** with `versionId`.

### `GET /v1/workspaces/:workspaceId/content-items/:itemId/versions/latest-approved`

Get the latest approved version. **Response `200 OK`** or `404`.

### `POST /v1/workspaces/:workspaceId/content-items/:itemId/versions/:versionId/approve`

Approve a content version. **Response `200 OK`**.

### `GET /v1/workspaces/:workspaceId/content-items/:itemId/versions`

Get version history for a content item. **Response `200 OK`** — array of versions.

---

## Content Validation

> Servlet: `DmosContentValidationServlet`

### `POST /v1/workspaces/:workspaceId/content-versions/:versionId/validate`

Trigger policy/brand validation for a content version. **Response `201 Created`** with `validationResultId`.

### `GET /v1/workspaces/:workspaceId/content-versions/:versionId/validation-results`

List validation results for a content version. **Response `200 OK`** — array.

---

## Ad Copy

> Servlet: `DmosAdCopyServlet`

### `POST /v1/workspaces/:workspaceId/content-items/:itemId/ad-copy/generate`

Generate ad copy draft for a content item. **Response `201 Created`**.

### `GET /v1/workspaces/:workspaceId/content-items/:itemId/ad-copy/latest-approved`

Get latest approved ad copy. **Response `200 OK`** or `404`.

---

## Landing Page

> Servlet: `DmosLandingPageServlet`

### `POST /v1/workspaces/:workspaceId/content-items/:itemId/landing-page/generate`

Generate a landing page draft. **Response `201 Created`**.

### `GET /v1/workspaces/:workspaceId/content-items/:itemId/landing-page/latest-approved`

Get latest approved landing page. **Response `200 OK`** or `404`.

---

## Email Follow-Up

> Servlet: `DmosEmailFollowUpServlet`

### `POST /v1/workspaces/:workspaceId/content-items/:itemId/email-followup/generate`

Generate an email follow-up draft. **Response `201 Created`**.

### `GET /v1/workspaces/:workspaceId/content-items/:itemId/email-followup/latest-approved`

Get latest approved email follow-up. **Response `200 OK`** or `404`.

---

## Competitor Research

> Servlet: `DmosCompetitorResearchServlet`

### `POST /v1/workspaces/:workspaceId/research/competitor`

Run competitor research for the workspace. **Response `201 Created`** with `researchId`.

### `GET /v1/workspaces/:workspaceId/research/competitor`

Get latest competitor research result. **Response `200 OK`** or `404`.

---

## Website Audit

> Servlet: `DmosWebsiteAuditServlet`

### `POST /v1/workspaces/:workspaceId/audit/run`

Run a website SEO/performance audit. **Response `201 Created`** with `auditId`.

### `GET /v1/workspaces/:workspaceId/audit/latest`

Get the latest audit result. **Response `200 OK`** or `404`.

---

## Lead Scoring

> Servlet: `DmosLeadScoringServlet`

### `POST /v1/workspaces/:workspaceId/lead-score`

Generate a lead score for the workspace's active leads. **Response `201 Created`**.

### `GET /v1/workspaces/:workspaceId/lead-score`

Get the latest lead score. **Response `200 OK`** or `404`.

---

## Intake Questionnaire

> Servlet: `DmosIntakeQuestionnaireServlet`

### `PUT /v1/workspaces/:workspaceId/intake/questionnaire/draft`

Save or update the intake questionnaire draft. **Response `200 OK`**.

### `GET /v1/workspaces/:workspaceId/intake/questionnaire/draft`

Get the current questionnaire draft. **Response `200 OK`** or `404`.

### `POST /v1/workspaces/:workspaceId/intake/questionnaire/submit`

Submit the completed questionnaire to trigger onboarding workflow. **Response `200 OK`**.

---

## Public Intake (Unauthenticated)

> Servlet: `DmosPublicIntakeServlet` — no `X-Tenant-ID` required

### `POST /public/v1/workspaces/:workspaceId/intake/leads`

Capture a public lead submission (e.g., from a landing page form).

**Request body**
```json
{
  "email": "prospect@example.com",
  "name": "Jane Smith",
  "phone": "+15551234567",
  "source": "landing-page-q1"
}
```

**Response `201 Created`** — lead record with `leadId`.

---

## Connector: Google Ads Campaign

> Managed via application service, not directly exposed as a servlet route.  
> Triggered internally when a `CAMPAIGN_LAUNCH` approval is approved.

**External API**: `POST https://googleads.googleapis.com/v14/customers/:customerId/campaigns:mutate`

**DMOS payload sent to Google Ads API**:
```json
{
  "name": "Campaign name from DMOS",
  "dailyBudgetMicros": "50000000",
  "serviceArea": "Austin TX",
  "keywordTheme": "hvac repair"
}
```

**Note**: `dailyBudgetMicros` is the daily budget in USD × 1,000,000 (micros), serialized as a string.

---

## Security Notes

- All workspace-scoped routes enforce workspace membership via `DigitalMarketingKernelAdapter.isAuthorized`.
- Approval decisions additionally enforce `requiredApproverRole` from the snapshot.
- AI action log `details` field is redacted for principals without `ai-action-log-sensitive:read` permission.
- Rate limiting cannot be bypassed via environment variable in production; test bypass is gated on the Gradle test worker system property only.
