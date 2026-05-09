- [x] [P0] [UI/UX] Fix BudgetPage type/import blocker
  - Where: products/digital-marketing/ui/src/pages/BudgetPage.tsx; products/digital-marketing/ui/src/hooks/useBudget.ts
  - Problem: BudgetPage imports useBudget but calls useBudgetRecommendation; hook exports useBudgetRecommendation.
  - Required fix: Replace useBudget import with useBudgetRecommendation and remove unused/invalid imports.
  - Acceptance criteria: pnpm type-check passes; /budget route renders for authorized users.
  - Required tests: Type-check; BudgetPage render test; budget load/generate/submit/approve component tests.

- [x] [P0] [UI/UX] Fix ApprovalDetailPage missing Link import
  - Where: products/digital-marketing/ui/src/pages/ApprovalDetailPage.tsx
  - Problem: Page renders Link but does not import it.
  - Required fix: Import Link from react-router-dom; remove unused imports.
  - Acceptance criteria: pnpm type-check and lint pass; approval detail route renders.
  - Required tests: Approval detail route render; back-to-queue link; AI action log link.

- [x] [P0] [UI/UX] Fix AiActionLogPage missing useAiActionDetail import
  - Where: products/digital-marketing/ui/src/pages/AiActionLogPage.tsx
  - Problem: Page calls useAiActionDetail but imports only useAiActionLog.
  - Required fix: Import useAiActionDetail; remove unused imports.
  - Acceptance criteria: pnpm type-check and lint pass; /ai-actions/:actionId renders details.
  - Required tests: AI action list render; detail route render; empty/error/loading states.

- [x] [P0] [API] Remove fake dashboard budget and connector health data
  - Where: products/digital-marketing/ui/src/pages/DashboardPage.tsx
  - Problem: BudgetTrackingWidget and ConnectorHealthWidget receive hardcoded production-looking values.
  - Required fix: Wire real API hooks or show explicit unavailable/empty states with data freshness.
  - Acceptance criteria: Dashboard never renders fake spend, budget, connector status, or sync timestamp.
  - Required tests: Dashboard with empty data; dashboard with real budget; connector disabled; connector unhealthy; API failure.

- [ ] [P0] [API] Align canonical API error envelope across docs, server, client, and tests
  - Where: products/digital-marketing/docs/canonical/02-API_CONTRACTS.md; dm-api servlets; UI ApiError handling
  - Problem: Canonical docs define nested error object while servlet comments indicate flat error shape.
  - Required fix: Choose one canonical shape, update OpenAPI/docs/server/client/tests.
  - Acceptance criteria: Every servlet error response matches OpenAPI and UI parses it safely.
  - Required tests: Contract tests for 400/401/403/404/409/422/423/429/500.
  - Progress update: Canonical shape aligned to flat envelope in docs/spec/server core path; added `DmosApiErrorResponses`, updated `StandardErrorEnvelope` codes (including `LOCKED`), migrated rate-limiter + localization + strategy + website-audit + proposal + sow + lead-scoring + landing-page + email-follow-up + next-best-action-recommendation + content-version + intake-questionnaire + competitor-research + public-intake + workspace + content-validation + ai-action-log + ad-copy + budget-recommendation + approval + campaign + anomaly-detection + experiment-suggestion + budget-reallocation-proposal servlets, and kept module-wide validation green (latest run: full `:products:digital-marketing:dm-api:test`; prior focused runs in this stream: Campaign + Approval + AiActionLog/AdCopy/BudgetRecommendation + PublicIntake/Workspace/ContentValidation + ContentVersion/IntakeQuestionnaire/CompetitorResearch + LandingPage/EmailFollowUp/NextBestActionRecommendation + Proposal/Sow/LeadScoring + Strategy/WebsiteAudit + HttpContextFactory + RateLimiter). Remaining work: complete any non-servlet dm-api envelope cleanup and keep the full status-code contract test matrix green.
  - Progress update: UI `ApiError` now parses the canonical flat envelope (`error`, `message`, `status`, `correlationId`, `details`) and surfaces user-safe messages for 400/401/403/404/409/422/423/429/500. Added focused matrix coverage in `ui/src/lib/__tests__/http-client.test.ts` to keep client-side parsing behavior aligned with canonical status-code handling.
  - Progress update: Migrated the remaining agency/demo servlet family to the canonical flat envelope helper (`DmosAgencyRetainerServlet`, `DmosAgencyDeliverableServlet`, `DmosAgencyContractServlet`, `DmosAgencyApprovalSLAServlet`, `DmosDemoWorkspaceServlet`), removed ad hoc JSON 400/404/500 responses, threaded correlation IDs through `mapServiceError`, and fixed the demo-workspace route parameter bug so `demoWorkspaceId` is routed and read correctly. Focused backend validation passed via `:products:digital-marketing:dm-api:compileJava`.
  - Progress update: Verified remaining dm-api main Java sources for raw ad hoc `{"error":...}` responses via targeted source scan; no additional non-servlet outliers found in `products/digital-marketing/dm-api/src/main/java`.

- [ ] [P0] [Permission] Implement action-level permission gates
  - Where: routeManifest.tsx; api/capabilities.ts; CampaignsPage; StrategyPage; BudgetPage; approval components; backend servlets/services
  - Problem: Route-level gates exist, but user actions are not consistently gated by explicit action permission.
  - Required fix: Create canonical action-permission map for every routeManifest action and enforce in UI and backend.
  - Acceptance criteria: Read-only/partial-permission users can view but cannot execute disallowed actions.
  - Required tests: Viewer, brand-manager, marketing-director, exec-sponsor, admin matrix; direct API call denial.
  - Progress update: Added canonical UI action map in `ui/src/lib/action-permissions.ts`, enforced action-level gating in `CampaignsPage`, `StrategyPage`, and `BudgetPage` (mutation controls now role-gated while preserving view access), and added contract coverage requiring every route-manifest action to exist in the canonical map (`route-contracts.test.tsx`). Added viewer-role gating tests for campaigns/strategy/budget and validated focused suites (`CampaignsPage`, `StrategyPage`, `BudgetPage`, `route-contracts`) all passing.
  - Progress update: Added backend canonical action-role registry (`dm-api/src/main/java/com/ghatana/digitalmarketing/api/security/DmosActionPermissionRegistry.java`), integrated action enforcement in shared context factory (`DmosHttpContextFactory`) and wired mutation endpoints in campaign/strategy/budget plus approval decide flow to explicit actions. Added direct API role-denial/allow tests for viewer/brand-manager/marketing-director/exec-sponsor cases across `DmosCampaignServletTest`, `DmosStrategyServletTest`, `DmosBudgetRecommendationServletTest`, and `DmosApprovalServletTest`; focused backend validation passed via `:products:digital-marketing:dm-api:test --tests ...` for those four suites. Remaining work: extend matrix coverage to admin and read-only view cases where needed.
  - Progress update: Extended backend permission matrix to include admin mutation allow-cases and explicit viewer read-only allow-cases for campaign list, strategy get, budget recommendation get, and approval status get; added corresponding assertions in the four servlet suites and revalidated focused backend suites successfully (`BUILD SUCCESSFUL` for Campaign/Strategy/BudgetRecommendation/Approval tests).
  - Progress update: Approval UI now uses the canonical action-permission registry on both queue and detail surfaces instead of the older generic approver helpers. `ApprovalQueuePage` preserves review access while warning users who lack `approve`/`reject`; `ApprovalDetailPage` now requires both canonical action permission and the request's `requiredApproverRole` hierarchy before enabling decisions. Added focused viewer/admin/higher-required-role coverage in `ApprovalQueuePage.test.tsx` and `ApprovalDetailPage.test.tsx`; targeted validation passed (`pnpm vitest run src/pages/__tests__/ApprovalQueuePage.test.tsx src/pages/__tests__/ApprovalDetailPage.test.tsx`, `pnpm type-check`).

- [ ] [P0] [Testing] Make DMOS build/type/lint/route/API contract gates mandatory in CI
  - Where: products/digital-marketing/ui/package.json; .github workflows; Gradle DMOS modules
  - Problem: Scripts exist, but inspected commit had no usable CI evidence and source has type blockers.
  - Required fix: Add/verify CI jobs for pnpm build/type/lint/test/e2e and Gradle DMOS checks.
  - Acceptance criteria: PR cannot merge when UI type-check, lint, route contract, API contract, or backend checks fail.
  - Required tests: CI workflow validation and failing fixture test.
  - Progress update: `.github/workflows/dmos-release-gate.yml` now includes mandatory `ui-quality-gates` (type-check, lint, route-contract) wired into release `needs` with explicit per-job release summary checks.
  - Progress update: Completed equivalent gate wiring across other DMOS workflows: `dmos-test-matrix.yml` now has dedicated `ui-quality-gates` (type-check/lint/route-contract) as a required dependency of `test-gate`; `dmos-openapi-client-gen.yml` now requires `ui-quality-gates` and `api-contract-tests` alongside OpenAPI/client generation before final drift gate passes; release-gate contract parity now also executes backend `*ContractTest` suite. Remaining work: verify repository branch-protection required-check settings in GitHub so these workflow gates are enforced as merge blockers at policy level.
  - Progress update: Verified repository codebase has no settings-as-code branch protection file under `.github/` in this workspace; branch protection required-check enforcement remains an external GitHub repository setting and cannot be enforced or confirmed solely via in-repo code changes.
  - Progress update: Identified concrete branch-protection required-check candidates from current DMOS workflows (job `name` values): `Test Gate` from `.github/workflows/dmos-test-matrix.yml`, `P0 Release Gate` from `.github/workflows/dmos-release-gate.yml`, and `Contract Drift Check` from `.github/workflows/dmos-openapi-client-gen.yml`. Apply these as required status checks in GitHub branch protection for `main` (and `release/*` where applicable).
  - Progress update: Ran wider regression validation after focused passes: UI gates succeeded (`pnpm lint`, `pnpm type-check`, `pnpm test:route-contract`), `:products:digital-marketing:dm-api:test` passed, and combined backend run surfaced 4 failures in `:products:digital-marketing:dm-persistence:test` under `FlywayMigrationValidationTest` (`Fresh migration from empty database succeeds`, `Migration from V20 to current succeeds`, `Migration from V10 to current succeeds`, `Migration info shows correct version history`). Focused Flyway test had passed earlier in isolation, so this is currently tracked as a suite-order/environment-sensitive regression to resolve before claiming full green broad backend matrix.
  - Progress update: Closed the persistence regression by scoping Flyway test migration discovery to the DMOS module migration directory (instead of global `classpath:db/migration`, which was pulling plugin migrations with duplicate versions). Revalidated `:products:digital-marketing:dm-persistence:test --tests "*FlywayMigrationValidationTest"` and full `:products:digital-marketing:dm-persistence:test`; both now pass (`BUILD SUCCESSFUL`).

- [x] [P1] [Design System] Replace native confirm/prompt with design-system dialogs
  - Where: CampaignsPage archive/rollback/duplicate actions
  - Problem: window.confirm/window.prompt are inaccessible and inconsistent.
  - Required fix: Use shared Dialog/Modal/Form components with clear risk, confirmation, and validation.
  - Acceptance criteria: Keyboard accessible dialogs; destructive action confirmation; duplicate name validation.
  - Required tests: Component tests; a11y tests; E2E destructive action confirmation.
  - Progress update: Replaced `window.confirm`/`window.prompt` in `CampaignsPage` with design-system `Dialog` flows for archive, rollback, and duplicate actions; added duplicate-name validation and focused component coverage in `CampaignsPage.test.tsx` (10/10 passing). Remaining work: add dedicated a11y assertions and E2E destructive-action confirmation coverage.
  - Progress update: Added campaign dialog accessibility and destructive-confirmation component coverage in `CampaignsPage.test.tsx` (archive dialog semantics, duplicate dialog labeling, and archive confirm callback assertions); focused suite now passes with 14/14 tests.
  - Progress update: Added Playwright spec `ui/e2e/campaign-dialogs.spec.ts` covering archive destructive confirm and duplicate-name validation/confirm flow.
  - Progress update: Resolved shared E2E harness crash by fixing router primitive imports in shared product shell (`react-router-dom` for `NavLink`/`Outlet`) and aligned approval/campaign specs with current dialog testids and behavior; targeted runtime validation now passes (`pnpm playwright test e2e/approval-detail.spec.ts e2e/campaign-dialogs.spec.ts --project=chromium`: 11/11).

- [x] [P1] [UI/UX] Redesign StrategyPage as outcome-first guided workflow
  - Where: StrategyPage and related strategy hooks/API
  - Problem: Users manually enter audit/keyword/competitor counts, which violates low-cognitive-load AI-native design.
  - Required fix: Ask for business objective, offer, geography, constraints, budget, and audience; derive audit/market inputs from real services.
  - Acceptance criteria: Non-expert user can generate a strategy without precomputed metrics.
  - Required tests: Guided form flow; missing data states; generated strategy review; provenance display.
  - Progress update: Replaced manual metric entry in `StrategyPage` with a guided outcome-first form (`objective`, `offer`, `audience`, `geography`, `constraints`, `budget`, optional website URL) and derived strategy generation inputs from real services via new hooks (`useIntakeProfile`, `useLatestWebsiteAudit`, `useLatestCompetitorResearch`).
  - Progress update: Added explicit missing-data notices for absent audit/research snapshots while keeping safe conservative defaults, preserved generated strategy review/provenance rendering, and added focused test coverage in `StrategyPage.test.tsx` for guided fields, derived inputs, and missing-data states.

- [x] [P1] [AI-ML] Expand AI action detail and provenance surface
  - Where: AiActionLogPage; AI action DTOs/API; AIProvenancePanel
  - Problem: AI log details do not show full model/provider/version/confidence/risk/policy/human-edit/execution state.
  - Required fix: Extend DTOs and UI to match design requirements.
  - Acceptance criteria: Every AI-generated/recommended output has traceable provenance and risk/policy context.
  - Required tests: AI action detail render; redaction; missing provenance blocked/flagged.
  - Progress update: Expanded `AiActionLogPage` detail panel with execution state, initiation source (AI vs human), confidence surface, derived risk signal, policy checks, evidence-link rendering, and empty-state handling via shared state primitive; integrated `AIProvenancePanel` into selected action detail with explicit fallback metadata when model/provider fields are not present in DTOs.
  - Progress update: Added focused unit coverage in `AiActionLogPage.test.tsx` for rich detail render, policy/evidence list rendering, and missing provenance/evidence fallback behavior; suite passing.
  - Progress update: Added provenance completeness classification (`COMPLETE`/`PARTIAL`/`MISSING`) with warning state for incomplete metadata, plus explicit redaction notice when action detail payload is masked (`REDACTED`). Added focused tests for both missing-provenance warning and redaction handling.
  - Progress update: Extended backend/domain/API contract to carry provenance metadata (`provider`, `modelVersion`, `humanEdited`) through `AiActionLogEntry`, `AiActionLogService`, `DmosAiActionLogServlet` response mapping, persistence SQL mapping, and Flyway schema migration (`V35__add_ai_action_provenance_columns.sql`). Updated UI DTO/type and detail panel rendering to use real model/provider/human-edit values and removed placeholder-only provenance behavior.

- [x] [P1] [Observability] Add DataFreshnessBadge and source metadata to dashboard/reporting cards
  - Where: Dashboard widgets; reporting/boundary pages
  - Problem: Users cannot tell whether data is real, stale, partial, or unavailable.
  - Required fix: Add source, lastUpdated, freshness, partial-data, and unavailable states.
  - Acceptance criteria: Every KPI/reporting card shows source/freshness or explicit unavailable state.
  - Required tests: Fresh data, stale data, partial data, unavailable data.

- [x] [P1] [Security] Prove backend ignores client-provided roles/permissions in production
  - Where: DmosHttpContextFactory; API tests; UI http-client
  - Problem: UI sends X-Roles/X-Permissions when present; production must not trust them.
  - Required fix: Add tests that spoof headers and verify permissions are derived server-side.
  - Acceptance criteria: Spoofed client headers never grant access.
  - Required tests: API security tests for spoofed role/permission escalation.

- [x] [P1] [UI/UX] Standardize approval pages on design-system components
  - Where: ApprovalQueuePage; ApprovalDetailPage; DecideDialog; ApprovalSnapshotPanel
  - Problem: Approval UX uses raw select/button/styles and lacks full decision-workflow polish.
  - Required fix: Use shared Select/Button/Dialog/Badge/Table components and consistent approval layout.
  - Acceptance criteria: Approval queue/detail match design-system and are keyboard/a11y safe.
  - Required tests: Component, a11y, approval decision E2E.
  - Progress update: Refactored approval surfaces to shared design-system primitives: `ApprovalQueuePage` now uses design-system `Select`; `ApprovalQueueTable` now uses design-system `Table` and `Badge`; `ApprovalDetailPage` now uses design-system `Badge` and `Button`; `DecideDialog` now uses design-system `Dialog`, `Select`, `TextArea`, and `Button` with retained role/keyboard flows. Updated approval page tests for the workspace-scoped queue API mock and dialog decision select, then revalidated focused suites (`ApprovalQueuePage.test.tsx`, `ApprovalDetailPage.test.tsx`) plus `pnpm type-check` and `pnpm lint`.
  - Progress update: Added explicit accessibility assertions for decision dialog semantics and labels in `ApprovalDetailPage.test.tsx`, and verified approval decision E2E coverage remains in place (`ui/e2e/approval-detail.spec.ts` includes approve/reject and high-risk comment-required flows).

- [x] [P1] [API] Add real connector health API and dashboard integration
  - Where: Connector service/API; DashboardPage; ConnectorHealthWidget
  - Problem: Connector health is currently hardcoded on dashboard.
  - Required fix: Expose connector status, OAuth health, last sync, error, rate-limit, kill-switch state.
  - Acceptance criteria: Dashboard reflects real Google Ads connector state; Meta Ads not shown unless real.
  - Required tests: Healthy, disabled, expired token, rate-limited, kill-switch active.
  - Progress update: Added `ui/src/api/connectors.ts` + `ui/src/hooks/useConnectorHealth.ts` backed by DMOS `/health` bridge signals, mapped bridge status (`UP/DEGRADED/DOWN`) to connector health states, surfaced reason + last sync in `ConnectorHealthWidget`, and wired `DashboardPage` to live connector health source/freshness metadata.
  - Progress update: Added dashboard test coverage for connector unavailable, unhealthy/expired-token, and degraded rate-limit/kill-switch conditions in `DashboardPage.test.tsx`.

- [x] [P1] [API] Add real budget tracking source for dashboard
  - Where: Budget service/API; DashboardPage; BudgetTrackingWidget
  - Problem: Dashboard budget values are static.
  - Required fix: Expose approved budget, spend, remaining, utilization, source/freshness.
  - Acceptance criteria: Dashboard budget matches backend budget/spend source.
  - Required tests: No budget, draft budget, approved budget, overspend, API failure.
  - Progress update: Budget tracking now derives from real APIs in `DashboardPage` using `useBudgetRecommendation` + `useCampaigns`: approved budget cap, computed spend/remaining/utilization, partial/draft visibility rules, and explicit unavailable states when recommendation or spend telemetry is absent.
  - Progress update: Expanded `DashboardPage.test.tsx` with state-matrix coverage for budget no-budget/draft/approved/overspend/API-failure scenarios. Focused validation passed (`pnpm vitest run src/pages/__tests__/DashboardPage.test.tsx src/pages/__tests__/ApprovalDetailPage.test.tsx`; `pnpm run lint`).

- [x] [P2] [Voice] Implement or explicitly exclude native voice support from current readiness claims
  - Where: UI command layer; API action layer; docs/design if needed
  - Problem: No inspected UI/API evidence of voice-native flows.
  - Required fix: Either add permission-aware voice command flow for key actions or mark voice out of MVP readiness.
  - Acceptance criteria: Voice cannot bypass permissions and risky actions require confirmation.
  - Required tests: Voice approval, voice denied action, voice cancel, transcript/audit.
  - Progress update: Explicitly excluded native voice command/speech-interaction surfaces from MVP readiness claims in canonical product vision (`docs/canonical/00-VISION.md`), keeping voice/video AI scoped to post-MVP phases.

- [x] [P2] [Boundary Routes] Add verified unavailable/feature-gated states for all boundary routes
  - Where: FunnelAnalyticsPage; AttributionPage; RoiRoasPage; SelfMarketingFunnelPage; MarketResearchPage; AdvancedChannelsPage; LocalizationPage; AgencyOperationsPage; AiOptimizationPage
  - Problem: Routes exist, but implementation/API completeness was not verified in this pass.
  - Required fix: For each boundary route, either wire real API or show honest feature-unavailable state.
  - Acceptance criteria: No boundary route shows fake analytics, fake reports, or fake operational data.
  - Required tests: Direct URL access by unauthorized/authorized users; capability disabled; capability enabled but API unavailable.

- [ ] [P2] [Reuse] Consolidate duplicated status/loading/error/card patterns
  - Where: Dashboard widgets; route pages; approval/AI/campaign pages
  - Problem: Pages implement states and styling inconsistently.
  - Required fix: Create reusable page-state, card-state, mutation-state, and feature-unavailable components.
  - Acceptance criteria: Major pages use shared primitives for loading/empty/error/unauthorized/disabled/stale.
  - Required tests: Component tests for shared states.
  - Progress update: Added shared `PageStateNotice` primitive with component tests and refactored major pages (`CampaignsPage`, `BudgetPage`, `StrategyPage`, `ApprovalQueuePage`, `ApprovalDetailPage`, `AiActionLogPage`) to use the shared loading/error/empty/warning state rendering path.
  - Progress update: Added shared `DashboardWidgetCard` primitive for dashboard card loading/error/unavailable/ready states and refactored `CampaignStatusWidget`, `StrategyInsightsWidget`, `BudgetTrackingWidget`, and `ConnectorHealthWidget` to use it, including shared footer slots for freshness/link actions. Added focused component coverage in `DashboardWidgetCard.test.tsx` (loading/error/unavailable/ready rendering).

- [ ] [P3] [Code Quality] Remove stale comments, duplicated comments, and unused imports
  - Where: useCampaigns.ts; approval/AI/budget pages
  - Problem: Duplicated comments and unused imports reduce maintainability and may fail strict lint.
  - Required fix: Clean imports/comments and enforce lint.
  - Acceptance criteria: pnpm lint has zero warnings.
  - Required tests: Lint gate.
  - Progress update: Subset completed for touched files (ApprovalDetail/AI/Budget and generated api types lint blockers); full repository-wide stale-comment and unused-import sweep remains open.
  - Progress update: Removed duplicated stale comments in `ui/src/hooks/useCampaigns.ts` pending-state logic and revalidated `pnpm run lint` (ESLint passes with zero warnings; only external TypeScript-version compatibility notice remains).