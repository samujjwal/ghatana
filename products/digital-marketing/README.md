# Digital Marketing Operating System (DMOS)

DMOS is the Ghatana product workspace for an AI-native, governance-first digital marketing platform. It covers campaign management, budget planning, approval workflows, Google Ads connector, content generation, analytics, and AI action transparency.

## Documentation

For detailed documentation, see the canonical documentation in [docs/canonical/](docs/canonical/):
- [docs/canonical/00-VISION.md](docs/canonical/00-VISION.md) - Product vision and positioning
- [docs/canonical/01-ARCHITECTURE.md](docs/canonical/01-ARCHITECTURE.md) - System architecture and module design
- [docs/canonical/02-API_CONTRACTS.md](docs/canonical/02-API_CONTRACTS.md) - API contracts and endpoints
- [docs/canonical/03-UX_WORKFLOWS.md](docs/canonical/03-UX_WORKFLOWS.md) - UX workflows and user journeys
- [docs/canonical/04-TESTING.md](docs/canonical/04-TESTING.md) - Testing strategy and guidelines
- [docs/canonical/05-OPERATIONS.md](docs/canonical/05-OPERATIONS.md) - Operations and deployment
- [docs/canonical/06-PRODUCTION_MVP_CONTRACT.md](docs/canonical/06-PRODUCTION_MVP_CONTRACT.md) - Production MVP contract and readiness

Archived documentation (stale or superseded) is available in [docs/audits/archived/](docs/audits/archived/).

## Module Overview

| Module | Purpose | Production Status |
|---|---|---|
| `dm-core-contracts` | Canonical typed IDs, `DmOperationContext`, actor/context propagation, bridge context conversion | Ready |
| `dm-domain-packs` | Boundary policy rules, compliance rule packs, plugin startup bindings, pack validations | Ready |
| `dm-kernel-bridge` | Product-owned adapter over kernel bridge ports (feature flags, audit, compliance, human-approval plugin) | Partial |
| `dm-domain` | Domain model, aggregates, invariants, approval target types, AI action log | Partial |
| `dm-application` | Application services and orchestrations: campaign, strategy, budget, approval, content, connector, workflow, AI actions | MVP scaffold — deterministic adapters are production-blocked via ProductionBootstrapValidator (P0-006) |
| `dm-infra` | In-memory repository adapters for local dev, tests, and single-instance staging (ConcurrentHashMap-backed) | Dev/test only — production uses PostgreSQL adapters from dm-persistence |
| `dm-persistence` | Persistence module (Flyway migrations, PostgreSQL adapters) | Partial |
| `dm-connector-google-ads` | OkHttp adapters for Google Ads OAuth, campaign creation, and performance retrieval (REST API v14) | Adapter layer complete; command/workflow runtime wiring complete (P0-007) |
| `dm-api` | ActiveJ HTTP servlets for all DMOS routes; rate limiting, correlation ID, tenant header enforcement | MVP complete |
| `dm-integration-tests` | DMOS integration test suites | Partial |
| `ui` | React 19 + TypeScript frontend (`@dmos/ui`) | MVP complete — E2E pending |

## Local Development

### Prerequisites

- Java 21 (set via Gradle toolchain)
- Node.js 22+ and `pnpm` 9+
- No external services required for local/test mode (in-memory adapters)

### Backend — run from repo root

```bash
# Build all DMOS Java modules
./gradlew :products:digital-marketing:dm-api:build

# Run a specific module's tests
./gradlew :products:digital-marketing:dm-application:test
./gradlew :products:digital-marketing:dm-connector-google-ads:test

# Or run from the product directory (standalone mode)
cd products/digital-marketing
../../gradlew :dm-api:build
```

### UI

```bash
cd products/digital-marketing/ui

pnpm install          # install dependencies
pnpm dev              # start Vite dev server at http://localhost:5174
pnpm build            # type-check + production build
pnpm type-check       # tsc --noEmit
pnpm lint             # ESLint strict (zero warnings)
pnpm test             # Vitest unit tests
pnpm test:e2e         # Playwright E2E tests
pnpm test:e2e:a11y    # Playwright a11y-tagged tests only
```

### Sign in (local)

Use any non-empty values for Bearer Token, Workspace ID, Tenant ID, and User/Principal ID on the login page. The backend validates `X-Tenant-ID` on every request.

## Build and Check Gates

Run from repo root:

```bash
./gradlew :products:digital-marketing:dm-core-contracts:check
./gradlew :products:digital-marketing:dm-domain-packs:check
./gradlew :products:digital-marketing:dm-kernel-bridge:check
./gradlew :products:digital-marketing:dm-domain:check
./gradlew :products:digital-marketing:dm-application:check
./gradlew :products:digital-marketing:dm-infra:check
./gradlew :products:digital-marketing:dm-connector-google-ads:check
./gradlew :products:digital-marketing:dm-api:check
./gradlew :products:digital-marketing:dm-integration-tests:check
```

`dm-domain-packs` check includes:

- `validateDomainPackManifest`
- `validatePolicyPack`
- `validateComplianceRulePack`
- `validateReferenceConsumerHygiene`

## API Surface

All routes served by `dm-api`. Required headers on every request:
- `X-Tenant-ID` (mandatory)
- `X-Principal-ID` (mandatory)
- `X-Session-ID` (mandatory)
- `X-Correlation-ID` (mandatory)
- `X-Roles` (optional; in production, derived from token/session via DmosHttpContextFactory - client-provided values are ignored for security)
- `X-Permissions` (optional; in production, derived from token/session via DmosHttpContextFactory - client-provided values are ignored for security)
- `X-Idempotency-Key` (recommended for write operations to prevent duplicate execution)

P0-005: Production identity derivation uses `DmosHttpContextFactory` to derive principal, roles, and permissions from trusted token/session. Spoofed `X-Principal-ID`, `X-Roles`, and `X-Permissions` headers never grant access in production.

## Security & Privacy (DMOS-P0-1)

P1-013: PII model and token hardening is implemented in PrivacyService. Current security posture:
- **Tenant Isolation**: All data is scoped to tenant with workspace-level isolation
- **Header Enforcement**: Production identity derivation uses `DmosHttpContextFactory` to derive principal, roles, and permissions from trusted token/session (P0-005)
- **Rate Limiting**: API rate limiting via `DmosApiRateLimiter`
- **Audit Logging**: All mutating operations record audit events via kernel bridge

Pending P1-013 implementation:
- **Hashed Identifiers**: Email addresses and other identifiers should be hashed using HMAC-SHA256 before storage
- **Encrypted PII**: Raw PII data should be encrypted using AES-GCM encryption
- **DSAR Compliance**: Data Subject Access Request (DSAR) endpoints to support GDPR/CCPA right-to-be-forgotten
- **PII Redaction**: Logs and traces should redact PII data
- **Key Management**: Production requires secure key material management

| Module | Route prefix | Servlet |
|---|---|---|
| Campaigns | `/v1/workspaces/:workspaceId/campaigns` | `DmosCampaignServlet` |
| Approvals | `/v1/workspaces/:workspaceId/approvals` | `DmosApprovalServlet` |
| AI Action Log | `/v1/workspaces/:workspaceId/ai-actions` | `DmosAiActionLogServlet` |
| Ad Copy | `/v1/workspaces/:workspaceId/ad-copy` | `DmosAdCopyServlet` |
| Landing Pages | `/v1/workspaces/:workspaceId/landing-pages` | `DmosLandingPageServlet` |
| Email Follow-up | `/v1/workspaces/:workspaceId/email-followup` | `DmosEmailFollowupServlet` |
| Content Validation | `/v1/workspaces/:workspaceId/content-validation` | `DmosContentValidationServlet` |
| Website Audit | `/v1/workspaces/:workspaceId/website-audit` | `DmosWebsiteAuditServlet` |
| Workspace | `/v1/workspaces/:workspaceId` | `DmosWorkspaceServlet` |

Standard error codes: `400` bad request / missing header, `403` not authorised, `404` not found, `409` state conflict, `422` compliance violation, `423` feature/connector disabled, `429` rate limited, `500` internal error.

## UI Routes

### Stable (Implemented Surface)

| Path | Page | Description | Capability |
|---|---|---|---|
| `/login` | `LoginPage` | Authentication entry point | - |
| `/workspaces/:workspaceId/dashboard` | `DashboardPage` | Workspace overview (approval widget, workflow status, risk/compliance, AI actions) | - |
| `/workspaces/:workspaceId/approvals` | `ApprovalQueuePage` | Pending approval queue — filter by type, permission-aware | - |
| `/workspaces/:workspaceId/approvals/:requestId` | `ApprovalDetailPage` | Approval detail — snapshot, risk level, approve/reject | - |
| `/workspaces/:workspaceId/ai-actions` | `AiActionLogPage` | AI action log | - |
| `/workspaces/:workspaceId/campaigns` | `CampaignsPage` | Campaign planning and orchestration | dmos.campaigns |
| `/workspaces/:workspaceId/strategy` | `StrategyPage` | Strategy generation, review, and approvals | dmos.strategy |
| `/workspaces/:workspaceId/budget` | `BudgetPage` | Budget recommendations and approval decisions | dmos.budget |

### Boundary (Feature-Gated, Backend/Data Pending)

| Path | Page | Description | Capability |
|---|---|---|---|
| `/workspaces/:workspaceId/funnel-analytics` | `FunnelAnalyticsPage` | Full-funnel conversion analytics and stage drop-off reporting | dmos.reporting |
| `/workspaces/:workspaceId/attribution` | `AttributionPage` | Multi-touch attribution models and channel credit distribution | dmos.reporting |
| `/workspaces/:workspaceId/roi-roas` | `RoiRoasPage` | Return on investment and return on ad spend dashboards | dmos.reporting |
| `/workspaces/:workspaceId/self-marketing-funnel` | `SelfMarketingFunnelPage` | Product-led growth funnel management and trial onboarding flows | dmos.self_marketing |
| `/workspaces/:workspaceId/market-research` | `MarketResearchPage` | Trend analysis, buyer persona generation, and competitive intelligence | dmos.market_research |
| `/workspaces/:workspaceId/advanced-channels` | `AdvancedChannelsPage` | Programmatic advertising, Connected TV, and influencer management | dmos.advanced_channels |
| `/workspaces/:workspaceId/localization` | `LocalizationPage` | Multi-language campaign support and region-specific compliance controls | dmos.localization |
| `/workspaces/:workspaceId/agency` | `AgencyOperationsPage` | Client onboarding, white-label reports, and multi-client workspace management | dmos.agency |

P1-001: UI routes updated to include all route manifest entries with lifecycle and capability requirements.
P2-001: README UI routes section split by implementation state (stable vs boundary).

## Product Guardrails

- Product code and rule prefix: `DM` / `DM-`
- Default-deny boundary policy is mandatory (`DM-BP-999`)
- No `PHR-`/`FIN-` reference consumer tokens in DMOS pack production source
- Product code must use kernel/plugin public interfaces only
- No DMOS-specific logic in kernel/platform plugin production modules
- No `UnsupportedOperationException` in production source (enforced by `dmos-quality-gates.gradle.kts`)
- All UI TypeScript code must be fully typed; no `any` types

## Production Readiness

DMOS uses evidence-based readiness states:

- `Ready`: implemented, tested, documented, operationalized, and passing release gates.
- `Partial`: implemented in part, but missing hardening, coverage, or production integration.
- `Boundary`: product scope is defined but must remain gated or unavailable.
- `Dev/Test only`: useful for local workflows but blocked from production.
- `Not verified`: implementation may exist, but the required production proof has not been run.

| Dimension | Status | Notes |
|---|---:|---|
| Build cleanliness | Not verified | Local and CI gates must be run for the current commit before release claims are made. |
| API correctness | Partial | Core campaign/approval routes exist; generated OpenAPI/client parity is still being reconciled. |
| UI unit coverage | Partial | Unit tests exist, but dashboard and boundary-state coverage is incomplete. |
| UI E2E | Partial | Mocked E2E exists; real-backend E2E is required and must be self-contained in CI. |
| Persistence | Partial | PostgreSQL adapters exist; repository contracts are being hardened to require tenant and workspace scope. |
| Google Ads connector | Partial | Launch now records explicit pre-execution states; sandbox E2E, workflow replay, and external-ID proof remain required. |
| Observability (OTel) | Partial | Correlation and telemetry exist in key paths; source/freshness/production bootstrap proof remains required. |
| Privacy/security | Partial | Consent, suppression, PII, and DSAR surfaces exist in part; end-to-end enforcement and key-management proof remain required. |
| Approval workflow | Partial | Queue/detail/decision paths exist; immutable snapshot and resume/block semantics require complete integration proof. |
| AI action transparency | Partial | AI action log exists; mandatory provenance/evidence/risk metadata must be enforced across all AI outputs. |
| AI governance | Partial | Governance scaffolding exists; every AI workflow must persist provenance, risk, redaction, approval, and evaluation evidence. |
| Analytics/runtime | Partial | `DashboardSummaryService` exists; UI still needs to rely only on canonical backend summary data. |
| Command center | Partial | Next-best-action UI exists, but data source, freshness, confidence, and report/export parity are not fully verified. |
| Kernel bridge integration | Not verified | Platform plugin integration must be proven by production bootstrap and integration gates for the current build. |

**Current production decision:** production release is blocked until the canonical MVP loop in [docs/canonical/06-PRODUCTION_MVP_CONTRACT.md](docs/canonical/06-PRODUCTION_MVP_CONTRACT.md) has `Ready` proof for every step and `.github/workflows/dmos-release-gate.yml` passes without warning-only checks.

**Duplicate UI surface:** `dm-ui` is archived/non-built. The production frontend surface is `ui`.
