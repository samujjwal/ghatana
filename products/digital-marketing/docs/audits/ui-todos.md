## DMOS UI/UX + API Audit — commit `9e87a778d5a5b0761f44daaf8b537aa85650f351`

I audited the Digital Marketing product statically from the GitHub code at the requested commit. I did **not** run the app, browser tests, Gradle, or pnpm commands, so runtime results are marked as unverified where relevant.

**Overall readiness: `Not Ready`**

The product has a solid direction: centralized route manifest, product shell integration, capability-driven route gates, shared HTTP client with tenant/principal/session/correlation/idempotency headers, and real API-backed flows for approvals, campaigns, strategy, budget, and AI action logs. However, the primary dashboard still contains hardcoded operational data, campaign UI/API contracts drift, one UI action calls a backend endpoint that does not exist, boundary routes can render placeholder experiences if capabilities are enabled, error envelopes are inconsistent across servlets, voice is not implemented, and the CI/release gate does not reliably prove real end-to-end UI→API→Postgres behavior.

---

## 1. Executive Summary

| Area                  |                                                Rating | Evidence / Reason                                                                                                                                                           |
| --------------------- | ----------------------------------------------------: | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| UI/API integration    |                    **Partial / Not production-ready** | `DashboardPage` wires approvals and AI actions to hooks, but hardcodes campaign status, budget, strategy insight, and connector health values directly in the page.         |
| Route coverage        |          **Good manifest, incomplete implementation** | `routeManifest.tsx` declares stable and boundary DMOS routes with roles, personas, tiers, actions, cards, lifecycle, and capability keys.                                   |
| Permission gating     |                                           **Partial** | `App.tsx` checks authentication, route minimum role, and capability key, but action/card/data-scope gates are not uniformly enforced in UI.                                 |
| Backend authorization | **Better than UI, still incomplete for capabilities** | `CampaignServiceImpl` performs server-side authorization for campaign create/read/launch/pause/complete/archive/rollback actions.                                           |
| Design system         |                                             **Mixed** | Pages use `@ghatana/design-system`, but layout and styling still rely heavily on ad hoc Tailwind classes and raw HTML in product pages/shell.                               |
| AI/ML                 |                                           **Partial** | Strategy and budget pages show API-backed generation and provenance panels, but AI optimization is placeholder/static and AI action detail is shallow.                      |
| Voice                 |               **Not implemented / no evidence found** | Search for voice/mic/speech-related DMOS UI/API code returned no relevant results.                                                                                          |
| Observability         |                                           **Partial** | HTTP client sends correlation IDs and idempotency keys, but several backend responses do not consistently return standard envelopes/correlation headers.                    |
| Privacy/security      |                                           **Partial** | Production auth intent exists, but UI still manages tenant/principal/session context client-side; backend context derivation mitigates this only where consistently used.   |
| Tests/CI              |                          **Partial / not sufficient** | Release gate exists, but real-backend E2E is conditional and not included in the final release-gate `needs`; route tests mock APIs.                                         |

---

## 2. Top 10 Highest-Risk Issues

|  # | Risk                                                                    | Evidence                                                                                                                              | Impact                                                                |
| -: | ----------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------- |
|  1 | **Dashboard has hardcoded production-facing data**                      | Campaign counts, budget values, strategy recommendation, confidence score, and connector health are static props in `DashboardPage`.  | Primary command center can mislead users.                             |
|  2 | **Campaign create UI sends fields backend ignores**                     | UI sends objective, budget, dates, audience, landing page; backend create DTO only accepts `name` and `type`.                         | Data loss; UX suggests capability that API does not persist.          |
|  3 | **UI calls duplicate campaign endpoint that backend does not register** | UI client calls `/duplicate`; servlet routes do not include duplicate and service interface has no duplicate method.                  | User-facing action fails at runtime.                                  |
|  4 | **Campaign pagination count is incorrect**                              | Servlet returns `count = campaigns.size()` rather than total count.                                                                   | UI total pages/counts become wrong for datasets larger than one page. |
|  5 | **AI Optimization page is placeholder/static if capability enabled**    | Page creates empty arrays and shows integration placeholder copy instead of calling APIs.                                             | Enterprise AI route can appear enabled but non-functional.            |
|  6 | **Error envelope is inconsistent with canonical contract**              | API contract requires standard envelope/correlation behavior; `DmosAiActionLogServlet` uses `{code,message}` only.                    | Clients cannot handle errors uniformly; observability degrades.       |
|  7 | **API path drift for budget**                                           | Canonical docs define `/v1/workspaces/{workspaceId}/budget`; UI uses `/budget-recommendation`.                                        | OpenAPI/client/backend drift risk.                                    |
|  8 | **Route gating is not action-level permission gating**                  | `GuardedProductRoute` checks role/capability, but product actions still need per-action gates.                                        | Viewer/partial-permission users may see actions they cannot perform.  |
|  9 | **Google Ads command payload uses hardcoded defaults**                  | Campaign launch emits Google Ads command with `"50.00"`, `"US"`, and `"general"` defaults.                                            | External execution will not match strategy/budget/audience intent.    |
| 10 | **Release gate does not guarantee real-backend UI coverage**            | Real-backend E2E job is conditional and not included in release-gate `needs`.                                                         | Production can pass without validating critical UI→API→backend flows. |

---

## 3. Route / Page Coverage Map

| Route                                                                                          | Lifecycle | UI Status                      | API Status                                      | Gate Status                          | Issues                                                                                                   |
| ---------------------------------------------------------------------------------------------- | --------- | ------------------------------ | ----------------------------------------------- | ------------------------------------ | -------------------------------------------------------------------------------------------------------- |
| `/login`                                                                                       | Stable    | Present                        | Auth provider integration partially implemented | Public                               | Runtime production auth flow not verified.                                                               |
| `/workspaces/:workspaceId/dashboard`                                                           | Stable    | **Partial**                    | **Partial**                                     | Viewer                               | Contains hardcoded campaign, budget, strategy, connector data.                                           |
| `/workspaces/:workspaceId/approvals`                                                           | Stable    | Partial                        | API-backed queue                                | Viewer                               | Non-approver banner exists, but decision-action gating in table not fully verified.                      |
| `/workspaces/:workspaceId/approvals/:requestId`                                                | Stable    | Unverified                     | API client exists                               | Viewer                               | Detail page not inspected in this pass.                                                                  |
| `/workspaces/:workspaceId/ai-actions`                                                          | Stable    | API-backed                     | API-backed                                      | Viewer                               | Detail display is shallow; backend error envelope inconsistent.                                          |
| `/workspaces/:workspaceId/ai-actions/:actionId`                                                | Stable    | API-backed                     | API-backed                                      | Viewer                               | Same page handles detail, but evidence links/policy checks are not richly exposed.                       |
| `/workspaces/:workspaceId/campaigns`                                                           | Stable    | **Partial**                    | **Partial**                                     | `brand-manager` + `dmos.campaigns`   | DTO drift, duplicate endpoint missing, pagination count wrong.                                           |
| `/workspaces/:workspaceId/strategy`                                                            | Stable    | API-backed                     | API-backed                                      | `brand-manager` + `dmos.strategy`    | Good base flow; AI quality/governed execution not fully verified.                                        |
| `/workspaces/:workspaceId/budget`                                                              | Stable    | API-backed                     | API-backed                                      | `marketing-director` + `dmos.budget` | Path naming drift with canonical docs.                                                                   |
| `/funnel-analytics`, `/attribution`, `/roi-roas`                                               | Boundary  | Mostly unavailable/placeholder | Not verified                                    | `dmos.reporting`                     | Should remain disabled until real backend exists. `FunnelAnalyticsPage` only renders unavailable state.  |
| `/ai-optimization`                                                                             | Boundary  | **Placeholder**                | Missing                                         | `dmos.ai_optimization`               | Renders empty placeholder data if enabled; should be blocked or API-backed.                              |
| `/self-marketing-funnel`, `/market-research`, `/advanced-channels`, `/localization`, `/agency` | Boundary  | Unverified                     | Unverified                                      | Capability-gated                     | Manifest hides boundary routes from nav by default, but direct URL depends on capability endpoint.       |

---

## 4. UI/API Integration Findings

| Finding                                                   | Evidence                                                                                                                          | Required Fix                                                                                                                                                       |
| --------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| Dashboard widgets must be API-backed.                     | `DashboardPage` hardcodes active/paused/pending campaign counts, budget totals, strategy recommendation, and connector statuses.  | Add dashboard summary API or compose from campaigns/budget/strategy/connectors APIs. Remove all production hardcoded metrics.                                      |
| Campaign create contract is incomplete.                   | UI collects rich campaign fields; backend only creates from `name` and `type`.                                                    | Extend backend command/domain/persistence/API DTO to include objective, budget, start/end dates, audience, landing page, or remove fields from UI until supported. |
| Duplicate campaign action is broken.                      | UI client calls `/duplicate`; backend servlet/service lacks duplicate route/method.                                               | Implement duplicate endpoint/service/domain behavior/tests or remove the button/client method.                                                                     |
| Campaign list pagination cannot support real total pages. | API returns `count = campaigns.size()` after paginated query.                                                                     | Repository/service must return `items`, `totalCount`, `limit`, `offset`; UI must use total count.                                                                  |
| Budget API path drifts from canonical docs.               | Docs list `/budget`; UI uses `/budget-recommendation`.                                                                            | Decide canonical path, update OpenAPI/docs/servlets/client/tests together.                                                                                         |
| AI Action Log error format is not standard.               | Contract requires standard envelope and correlation; servlet returns simple `ErrorBody(int code, String message)`.                | Use shared `StandardErrorEnvelope` and `X-Correlation-ID` consistently.                                                                                            |

---

## 5. Permission, Entitlement, and Access-Control Findings

| Finding                                                         | Evidence                                                                                                            | Risk                                                                          | Required Fix                                                                                     |
| --------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------ |
| Route gates exist, but action gates are incomplete.             | `GuardedProductRoute` checks auth, `minimumRole`, and capability key.                                               | Users may see or initiate actions not permitted by function-level permission. | Add shared `Can/ActionGate` based on backend capability/action matrix.                           |
| Capability endpoint exists and UI consumes it.                  | UI calls `/v1/workspaces/:workspaceId/capabilities`; workspace servlet exposes that route.                          | Good base, but service-side workspace authorization must be proven.           | Add tests for cross-tenant/cross-workspace capability fetch.                                     |
| `isRouteActionPermitted` is not a trustworthy permission model. | It checks whether an enabled capability key includes the action string.                                             | False positives/negatives; cannot represent action permissions.               | Replace with explicit backend-provided action grants.                                            |
| Backend campaign authorization is stronger.                     | `CampaignServiceImpl` checks `kernelAdapter.isAuthorized` for write/read/lifecycle actions.                         | Good pattern, but not uniformly proven across all DMOS APIs.                  | Apply same pattern to strategy, budget, approvals, AI actions, connectors, reporting.            |
| UI still sends principal/session/roles context.                 | Auth context stores workspace/tenant/principal/session and sets request context; HTTP client sends those headers.   | Client headers must not be trusted in production.                             | Ensure `DmosHttpContextFactory` derives identity from trusted token/session for all prod routes. |

---

## 6. Design System and UX Findings

| Finding                                                    | Evidence                                                                                                                           | Required Fix                                                                                                                     |
| ---------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------- |
| Product shell reuse is good.                               | `DmosProductShell` uses shared `ProductShell` and route manifest.                                                                  | Keep this as canonical shell/navigation path.                                                                                    |
| Page styling is still ad hoc.                              | Product pages use many raw Tailwind utility classes alongside design-system components.                                            | Introduce DMOS page templates/cards/form sections/status panels from design-system/product-shell.                                |
| Dashboard hierarchy is not yet a true DMOS command center. | It shows cards but with fake data for core operating metrics.                                                                      | Build one real “what needs attention / what AI did / what is blocked / what changed / business impact” dashboard API and layout. |
| Boundary routes can create UX confusion.                   | Product shell hides boundary routes unless discoverable, but direct access can show placeholder route UI when capability exists.   | Boundary routes should render unavailable unless real API + tests exist.                                                         |

---

## 7. AI/ML and Voice Findings

| Workflow              | Current Behavior                                                    | Gap                                                                                                           |
| --------------------- | ------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------- |
| Strategy generation   | API-backed UI with generate/submit/approve and provenance panel.    | Need verify real governed AI path, model trace, confidence, override, and audit.                              |
| Budget recommendation | API-backed UI, approved-strategy auto-selection, provenance panel.  | Needs stronger explanation, scenario comparison, and real performance data linkage.                           |
| AI action log         | API-backed list/detail.                                             | Detail does not fully expose evidence links/policy checks; backend errors not standard.                       |
| AI optimization       | Placeholder arrays and integration copy.                            | Must remain disabled until backed by real recommendations/anomaly/budget APIs.                                |
| Voice                 | No inspected implementation evidence.                               | Add voice command model, permission mapping, confirmation, transcript/audit, privacy controls, and E2E tests. |

---

## 8. Observability, Privacy, and Security Findings

| Finding                                                                   | Evidence                                                                                                            | Required Fix                                                                             |
| ------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------- |
| HTTP client does the right baseline header work.                          | `apiRequest` sets `X-Correlation-ID`, required tenant/principal/session headers, auth, and idempotency for writes.  | Keep but ensure production derives identity server-side.                                 |
| Contract requires standard errors/correlation, but implementation varies. | Canonical docs require standard envelope; AI action servlet returns simple code/message.                            | Centralize error response helper across all servlets.                                    |
| Campaign servlet has observability spans for key mutations.               | Campaign servlet creates spans and maps correlation-aware errors for several operations.                            | Add consistent success response correlation header and contract tests.                   |
| Google Ads command payload is unsafe for real execution.                  | Hardcoded default budget/serviceArea/keywordTheme in launch command.                                                | Drive external command payload from approved campaign/strategy/budget/targeting records. |
| Load error UI can expose raw API messages.                                | Pages render `error.message` for load failures.                                                                     | Render `ApiError.getUserMessage()` consistently and keep raw body in telemetry only.     |

---

## 9. Test and CI Findings

| Area                | Current State                                                                                                        | Gap                                                                                                                  |
| ------------------- | -------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------- |
| UI scripts          | Build, type-check, lint, unit, coverage, E2E, a11y, real-backend scripts exist.                                      | Scripts exist, but coverage quality must be enforced.                                                                |
| Route contracts     | Route metadata tests compare manifest to backend route-capabilities JSON and check capability keys.                  | Tests mock APIs; they do not validate UI→API behavior.                                                               |
| Release gate        | Good intent: unit, repo integration, API integration, E2E, production bootstrap, security, contract, feature flags.  | Real-backend E2E is conditional and excluded from final `needs`; release can pass without it.                        |
| API contract parity | Redocly lint and TypeScript compile are present.                                                                     | Must also verify servlet routes, response envelopes, and generated client types match the product canonical OpenAPI. |

---

## 10. Actionable TODO List

```markdown
- [ ] [P0] [UI/API] Replace dashboard hardcoded metrics with real API-backed data
  - Where: products/digital-marketing/ui/src/pages/DashboardPage.tsx
  - Problem: Campaign, budget, strategy, and connector widgets use static values.
  - Required fix: Add dashboard summary API or compose existing hooks for real data.
  - Acceptance criteria: Dashboard displays only backend-derived values or explicit unavailable states.
  - Required tests: Component tests, API integration tests, real-backend Playwright dashboard test.

- [ ] [P0] [API] Fix campaign create contract drift
  - Where: CampaignsPage.tsx, api/campaigns.ts, DmosCampaignServlet.java, CampaignService.java, Campaign domain/persistence
  - Problem: UI sends objective/budget/dates/audience/landing page; backend ignores them.
  - Required fix: Persist those fields end-to-end or remove from UI until supported.
  - Acceptance criteria: Created campaign detail/list shows all submitted fields correctly.
  - Required tests: Servlet test, repository test, UI create test, E2E real-backend test.

- [ ] [P0] [API/UI] Remove or implement duplicate campaign
  - Where: useCampaigns.ts, api/campaigns.ts, CampaignsPage.tsx, DmosCampaignServlet.java, CampaignService
  - Problem: UI calls `/duplicate`; backend has no route/service.
  - Required fix: Implement duplicate lifecycle or hide/remove action.
  - Acceptance criteria: No visible action points to nonexistent endpoint.
  - Required tests: Contract route test and E2E action test.

- [ ] [P0] [API] Fix campaign pagination response
  - Where: DmosCampaignServlet.java, CampaignRepository, CampaignService
  - Problem: `count` is current page size, not total count.
  - Required fix: Return `{items,totalCount,limit,offset}`.
  - Acceptance criteria: UI page count is correct for >20 records.
  - Required tests: Repository pagination, servlet pagination, UI pagination.

- [ ] [P0] [API Contract] Standardize all error envelopes
  - Where: DmosAiActionLogServlet.java and all DMOS servlets
  - Problem: Errors are inconsistent with canonical API contract.
  - Required fix: Shared `StandardErrorEnvelope` helper with `X-Correlation-ID`.
  - Acceptance criteria: 400/401/403/404/409/422/423/429/500 all use same shape.
  - Required tests: Contract tests per servlet.

- [ ] [P0] [Permission] Add backend-enforced capability checks for every capability-gated API route
  - Where: Workspace/campaign/strategy/budget/reporting/AI APIs
  - Problem: UI capability gate is not security.
  - Required fix: Backend validates capability + action permission before serving.
  - Acceptance criteria: Disabled capability returns 423 or 403 consistently.
  - Required tests: Capability-disabled API tests and direct URL E2E tests.

- [ ] [P0] [CI] Make real-backend E2E release-blocking
  - Where: .github/workflows/dmos-release-gate.yml
  - Problem: Real-backend E2E job is conditional and not in final release-gate `needs`.
  - Required fix: Include it in release decision or create explicit blocking alternative.
  - Acceptance criteria: Release cannot pass without stable-route UI→API→backend validation.
  - Required tests: CI dry run / workflow validation.

- [ ] [P1] [AI-ML] Keep AI Optimization disabled until real APIs exist
  - Where: AiOptimizationPage.tsx, routeManifest.tsx, capabilities backend
  - Problem: Page renders placeholder empty arrays and integration copy.
  - Required fix: Return FeatureUnavailable until backend endpoints and tests exist.
  - Acceptance criteria: Enabled route is either fully functional or blocked.
  - Required tests: Capability on/off route tests.

- [ ] [P1] [Connector] Replace Google Ads hardcoded command payload
  - Where: CampaignServiceImpl.issueGoogleAdsCommandIfNeeded
  - Problem: Uses default budget/serviceArea/keywordTheme.
  - Required fix: Use approved campaign strategy/budget/targeting data.
  - Acceptance criteria: External command payload matches approved source records.
  - Required tests: Launch paid-search campaign integration test.

- [ ] [P1] [Security] Sanitize all UI load errors
  - Where: CampaignsPage, StrategyPage, BudgetPage, AiActionLogPage, ApprovalQueuePage
  - Problem: Several pages render `error.message`.
  - Required fix: Use shared safe error component that understands `ApiError`.
  - Acceptance criteria: Backend raw body never appears in UI.
  - Required tests: API failure UI tests.

- [ ] [P1] [Access Control] Replace naive `isRouteActionPermitted`
  - Where: ui/src/api/capabilities.ts
  - Problem: Action permission is inferred from substring matching.
  - Required fix: Backend returns explicit action grants per route/card/action.
  - Acceptance criteria: UI gates actions from authoritative grants.
  - Required tests: Permission matrix tests.

- [ ] [P1] [API Contract] Resolve `/budget` vs `/budget-recommendation` drift
  - Where: docs/api-contract.yaml, canonical docs, UI client, backend servlet
  - Problem: Canonical docs and UI client disagree.
  - Required fix: Pick one route family and update all sources.
  - Acceptance criteria: OpenAPI, docs, client, servlet, tests match.
  - Required tests: OpenAPI route parity tests.

- [ ] [P1] [UX] Create reusable DMOS dashboard/widget data contracts
  - Where: dashboard components and API layer
  - Problem: Widgets accept ad hoc props and static values.
  - Required fix: Define typed summary DTOs for approval, campaign, budget, strategy, connector health.
  - Acceptance criteria: Widgets can be reused with loading/error/empty states.
  - Required tests: Component tests per widget state.

- [ ] [P1] [Voice] Add voice-native command framework
  - Where: UI shell, command API, audit log, permission layer
  - Problem: No voice support found.
  - Required fix: Voice input → intent → permission check → confirmation → action/audit.
  - Acceptance criteria: At least approval summary, campaign pause, dashboard explanation work with voice.
  - Required tests: Voice command unit tests and Playwright mocked speech tests.

- [ ] [P2] [Design System] Reduce ad hoc Tailwind styling in product pages
  - Where: CampaignsPage, StrategyPage, BudgetPage, DashboardPage, DmosProductShell
  - Problem: Styling is repeated page-level utility composition.
  - Required fix: Promote page sections/cards/forms/status panels to shared or product-level components.
  - Acceptance criteria: Consistent spacing, typography, cards, buttons, empty/error states.
  - Required tests: Visual regression and a11y tests.

- [ ] [P2] [AI Transparency] Expand AI action detail view
  - Where: AiActionLogPage.tsx
  - Problem: Evidence links are counted, not usefully displayed; policy checks are hidden.
  - Required fix: Show evidence, policy checks, confidence, related entity, approval chain, trace.
  - Acceptance criteria: User can understand what AI did and why.
  - Required tests: AI detail component tests.

- [ ] [P2] [Testing] Replace route-only UI tests with behavior tests
  - Where: ui/src/__tests__ and Playwright specs
  - Problem: Route tests mock APIs and only prove rendering/metadata.
  - Required fix: Add tests for create/launch/pause/approve/generate failure and success.
  - Acceptance criteria: Critical user journeys covered at component + E2E levels.
  - Required tests: MSW/component tests and real-backend E2E.

- [ ] [P3] [Docs] Align capability comments and naming
  - Where: AiOptimizationPage.tsx and docs
  - Problem: Comment mentions `dmos.ai-optimization` while actual key is `dmos.ai_optimization`.
  - Required fix: Use one exact capability key everywhere.
  - Acceptance criteria: Capability-key drift test catches future mismatch.
  - Required tests: Existing route contract test plus docs grep check.
```

---

## 11. Validation Plan

Minimum validation before calling this near-ready:

1. Run UI checks: `pnpm build`, `pnpm type-check`, `pnpm lint`, `pnpm test`, `pnpm test:coverage`, `pnpm test:e2e`, and `pnpm test:e2e:realbackend` from `products/digital-marketing/ui`.
2. Run backend checks for `dm-api`, `dm-application`, `dm-domain`, `dm-persistence`, `dm-core-contracts`, `dm-domain-packs`, `dm-kernel-bridge`, `dm-connector-google-ads`, and `dm-integration-tests`.
3. Add real-backend Playwright coverage for: login/session, dashboard, campaigns create/list/launch/pause/complete/archive, strategy generate/submit/approve, budget generate/submit/approve, approvals queue/detail/decision, AI action log/detail, disabled capability direct URL, and cross-workspace denial.
4. Add API contract tests proving every servlet route matches OpenAPI, every error uses the same envelope, and every write requires idempotency.
5. Add permission matrix tests for viewer, brand manager, marketing director, executive sponsor, admin, disabled capability, partial capability, cross-tenant, and cross-workspace cases.

Bottom line: **DMOS has a credible skeleton, but the current UI/API state at this commit is not production-ready. The next fixes should focus on eliminating fake dashboard data, repairing campaign contract drift, standardizing API errors, proving backend capability enforcement, and making real-backend E2E release-blocking.**
