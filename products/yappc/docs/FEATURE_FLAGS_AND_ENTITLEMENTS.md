# YAPPC Feature Flags and Entitlements

This document is the operator and developer reference for YAPPC feature flags, lifecycle entitlements, and capability gates. It is evidence-backed by the server-side action authorizer, tenant-scoped Data Cloud flag API, frontend route inventory, and focused tests.

## Sources of Truth

| Layer | Source | Purpose | Evidence |
| --- | --- | --- | --- |
| Tenant feature flags | `yappc_feature_flags` Data Cloud collection through `AdminFeatureFlagController.FLAG_COLLECTION` | Audited tenant-level flag records managed by admins. | `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/api/AdminFeatureFlagController.java`; `products/yappc/core/yappc-services/src/test/java/com/ghatana/yappc/api/AdminFeatureFlagControllerTest.java` |
| Feature flag audit | `yappc_feature_flag_audit` Data Cloud collection through `AdminFeatureFlagController.AUDIT_COLLECTION` | Stores flag changes with actor, reason, correlation ID, and timestamps. | `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/api/AdminFeatureFlagController.java`; `products/yappc/frontend/web/src/components/admin/__tests__/FeatureFlagsPage.test.tsx` |
| Phase packet enabled flags | `PhasePacketServiceImpl.determineEnabledFlags` plus enabled tenant flags from Data Cloud | Merges project flags, project entitlements, and tenant-tier defaults into the backend packet contract. | `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/phase/PhasePacketServiceImpl.java`; `products/yappc/core/yappc-services/src/test/java/com/ghatana/yappc/services/phase/PhasePacketServiceImplTest.java` |
| Server action authorization | `PhaseActionAuthorizationService` | Enforces phase action availability from backend capabilities, readiness, policy, tier, and enabled flags. | `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/phase/PhaseActionAuthorizationService.java`; `products/yappc/core/yappc-services/src/test/java/com/ghatana/yappc/services/phase/PhaseActionAuthorizationServiceTest.java` |
| Frontend route/capability inventory | `frontend/web/docs/route-inventory.md` and `useCapabilityGate` | Documents route owners, route flag labels, capabilities, auth, navigation visibility, and tests. | `products/yappc/frontend/web/docs/route-inventory.md`; `products/yappc/frontend/web/src/hooks/useCapabilityGate.ts`; `products/yappc/frontend/web/src/hooks/__tests__/useCapabilityGate.test.ts` |
| GrowthBook client flags | `FeatureFlagProvider` | Client-side rollout and UX toggles. These are not authorization controls. | `products/yappc/frontend/web/src/providers/FeatureFlagProvider.tsx`; `products/yappc/frontend/web/src/components/admin/__tests__/FeatureFlagsPage.test.tsx` |
| Environment safety flags | Runtime environment variables and JVM properties | Guard non-default adapters and local-only diagnostics. | `products/yappc/frontend/web/src/services/canvas/PreviewModeGuard.ts`; `products/yappc/core/yappc-services/src/test/java/com/ghatana/yappc/services/run/GitHubActionsCiCdAdapterFeatureFlagTest.java`; `products/yappc/core/yappc-services/src/test/java/com/ghatana/yappc/services/artifact/ArtifactGraphServiceUnsupportedParserTest.java` |

## Server-Enforced Lifecycle Entitlements

These keys can appear in project `enabledPhaseFlags`, project `featureFlags`, project `entitlements`, or enabled tenant `yappc_feature_flags` records. Backend action authorization must remain the final authority.

| Flag or entitlement | Owner | Default | Behavior | Validation |
| --- | --- | --- | --- | --- |
| `phase.advance` | Lifecycle platform | Enabled automatically for `PRO` and `ENTERPRISE`; otherwise disabled unless supplied by tenant/project flags or entitlements. | Enables the `advance-phase` action when backend update capability, readiness, and governance also allow it. | `PhaseActionAuthorizationServiceTest`, `AdvancePhaseUseCaseTest`, `PhasePacketServiceImplTest`, `YappcSecurityMatrixTest` |
| `phase.governance.configure` | Governance platform | Enabled automatically for `ENTERPRISE`; otherwise disabled unless supplied by tenant/project flags or entitlements. | Enables the `configure-phase` action when approval capability and policy allow governance changes. | `PhaseActionAuthorizationServiceTest`, `PhasePacketServiceImplTest` |
| `phase.report.export` | Lifecycle reporting | Enabled automatically for `ENTERPRISE`; otherwise disabled unless supplied by tenant/project flags or entitlements. | Enables the `export-report` action only for Enterprise tenants with read capability. | `PhaseActionAuthorizationServiceTest`, `PhasePacketContractTest`, `PhasePacketServiceImplTest` |
| `phase.generate.enabled` | Generate phase | Disabled unless present in the backend packet flags for the project. | Unlocks the Generate phase primary action in the adaptive frontend phase cockpit. | `PhaseBuilders.test.ts`, `PhaseStatusPanels.test.tsx`, `phase-cockpit-routes.test.tsx` |
| `phase.run.preview.enabled` | Run phase | Disabled unless present in the backend packet flags for the project. | Unlocks the Run phase primary action in the adaptive frontend phase cockpit. | `PhaseBuilders.test.ts`, `PhaseStatusPanels.test.tsx`, `phase-cockpit-routes.test.tsx` |
| `phase.observe.enabled` | Observe phase | Disabled unless present in the backend packet flags for the project. | Unlocks the Observe phase primary action in the adaptive frontend phase cockpit. | `PhaseBuilders.test.ts`, `PhaseStatusPanels.test.tsx`, `phase-cockpit-routes.test.tsx` |
| `phase.learn.patterns.enabled` | Learn phase | Disabled unless present in the backend packet flags for the project. | Unlocks the Learn phase primary action in the adaptive frontend phase cockpit for eligible tiers. | `PhaseBuilders.test.ts`, `PhaseStatusPanels.test.tsx`, `phase-cockpit-routes.test.tsx` |
| `phase.evolve.enabled` | Evolve phase | Disabled unless present in the backend packet flags for the project. | Unlocks the Evolve phase primary action in the adaptive frontend phase cockpit for eligible tiers. | `PhaseBuilders.test.ts`, `PhaseStatusPanels.test.tsx`, `phase-cockpit-routes.test.tsx` |

## Route and Capability Gates

Route feature flag labels come from `frontend/web/docs/route-inventory.md`. They describe route availability and ownership, but backend handlers still enforce authorization and scopes.

| Route flag label | Owner | Default | Behavior | Validation |
| --- | --- | --- | --- | --- |
| `intent` | Intent phase | Enabled for authenticated project access. | Shows the Intent phase route and capture actions. | `phase-cockpit-routes.test.tsx` |
| `shape` | Shape phase | Enabled for authenticated project access. | Shows the Shape phase route and canvas/builder actions. | `phase-cockpit-routes.test.tsx`, `canvas.integration.test.tsx` |
| `validate` | Validate phase | Enabled for authenticated project access. | Shows the Validate phase route and approval-gate actions. | `phase-cockpit-routes.test.tsx` |
| `generate` | Generate phase | Enabled for authenticated project access; primary action still depends on backend packet flags and gates. | Shows generation, diff, assurance, and retry surfaces. | `phase-cockpit-routes.test.tsx`, `PhaseStatusPanels.test.tsx` |
| `run` | Run phase | Enabled for authenticated project access; primary action still depends on backend packet flags and gates. | Shows runtime status, retry, rollback, and promote surfaces. | `phase-cockpit-routes.test.tsx`, `PhaseStatusPanels.test.tsx` |
| `observe` | Observe phase | Enabled for authenticated project access; primary action still depends on backend packet flags and gates. | Shows Kernel/app/agent health and recommendations. | `phase-cockpit-routes.test.tsx`, `PhaseStatusPanels.test.tsx` |
| `learn` | Learn phase | Enabled for authenticated project access; primary action still depends on backend packet flags and tier. | Shows learning evidence, recommendations, and agent governance. | `PhaseStatusPanels.test.tsx` |
| `evolve` | Evolve phase | Enabled for authenticated project access; primary action still depends on backend packet flags and tier. | Shows proposals, impact analysis, diff review, and approval controls. | `PhaseStatusPanels.test.tsx` |
| `kernel visibility` | Kernel visibility | Enabled in `useCapabilityGate` for `OWNER` and `ADMIN`. | Shows Kernel health list and detail pages. | `kernel-health-gate.test.tsx`, `KernelHealthDashboardPage.test.tsx` |
| `product family` | Product-family control plane | Enabled in `useCapabilityGate` for product-family control-plane roles. | Shows product-family assets, releases, promotions, and permissions. | `product-family-gate.test.tsx`, `ProductFamilyPage.test.tsx` |
| `admin prompts` | Prompt admin | Enabled in `useCapabilityGate` for `OWNER` and `ADMIN`. | Shows prompt version lifecycle, rollback, score, and weight controls. | `PromptVersionsPage.test.tsx` |
| `admin experiments` | Experiment admin | Enabled in `useCapabilityGate` for `OWNER` and `ADMIN`. | Shows A/B experiment create, promote, pause, metrics, and rollback controls. | `ABTestingDashboardPage.test.tsx` |
| `admin feature flags` | Feature flag admin | Enabled in `useCapabilityGate` for `OWNER` and `ADMIN`. | Shows tenant flag list, update dialog, and audit drawer. | `FeatureFlagsPage.test.tsx`, `useCapabilityGate.test.ts` |
| `admin observability` | Admin observability | Enabled in `useCapabilityGate` for `OWNER` and `ADMIN`. | Shows SLO, cost, domain, and OpenAPI release-gate evidence. | `ObservabilityDashboard.test.tsx` |
| `legacy route policy` | Route compatibility | Deep-link only. | Keeps legacy canvas, preview, deploy, and lifecycle links intentional. | `canvas.integration.test.tsx`, `preview.test.tsx`, `deploy.test.tsx`, `lifecycle.test.tsx` |

## Client Rollout Flags

`FeatureFlagProvider` configures GrowthBook-backed client flags for rollout and UX experiments. These flags can hide or reveal UI affordances, but they must not be used as the only guard for mutating backend work.

| Flag | Owner | Default | Behavior | Validation |
| --- | --- | --- | --- | --- |
| `onboarding` | Onboarding | Enabled. | Controls onboarding experience availability. | `EndToEndOnboarding.test.tsx` |
| `canvas-calm-mode` | Canvas UX | Disabled. | Controls calmer canvas presentation. | `PhaseBuilders.test.ts`, canvas route tests |
| `command-palette` | Shell UX | Enabled. | Controls command palette availability. | route shell tests |
| `ai-suggestions` | AI assist | Enabled in dev, controlled by GrowthBook elsewhere. | Controls AI suggestion UI. | phase route/component tests |
| `ai-canvas-assistant` | Canvas AI assist | Enabled in dev, controlled by GrowthBook elsewhere. | Controls AI canvas assistant UI. | canvas route/component tests |
| `ai-code-review` | Generate AI assist | Disabled. | Controls AI code-review affordances. | generate route/component tests |
| `real-time-collaboration` | Collaboration | Enabled. | Controls realtime collaboration affordances. | collaboration/canvas tests |
| `canvas-comments` | Collaboration | Enabled. | Controls canvas comment affordances. | canvas route/component tests |
| `approval-workflows` | Governance | Enabled. | Controls approval-workflow affordances. | approval and phase route tests |
| `agent-orchestration` | Agent runtime | Enabled in dev, controlled by GrowthBook elsewhere. | Controls agent orchestration affordances. | agent governance tests |
| `canvas-versioning` | Canvas persistence | Disabled. | Controls canvas versioning affordances. | canvas route/component tests |
| `ops-alerts`, `ops-incidents`, `ops-runbooks`, `ops-postmortems`, `ops-oncall`, `ops-warroom`, `ops-service-map`, `ops-logs`, `ops-metrics`, `ops-dashboards` | Operations surfaces | Disabled. | Keeps operations pages hidden until their backend surfaces are explicitly live. | `useCapabilityGate.test.ts`, route inventory check |
| `admin-billing` | Admin billing | Disabled. | Keeps billing hidden until billing integration is live. | `useCapabilityGate.test.ts` |
| `phase-run` | Run phase rollout | Disabled. | Legacy client rollout flag for Run UI; backend packet and route tests are authoritative for current Run behavior. | `FeatureFlagProvider.tsx`, `phase-cockpit-routes.test.tsx` |
| `canvas-3d-mode` | Canvas experiments | Disabled. | Controls 3D canvas experiment affordances. | canvas route/component tests |
| `voice-commands` | Voice UX | Disabled. | Controls voice command affordances. | shell/component tests |

## Environment Safety Flags

| Flag | Owner | Default | Behavior | Validation |
| --- | --- | --- | --- | --- |
| `VITE_ENABLE_DEV_PREVIEW_MODE` | Preview runtime | Disabled. | Allows dev preview mode only when `MODE=development` is also true. | `PreviewModeGuard.ts`, `preview-builder-security.test.ts`, `LivePreviewPanel.test.tsx` |
| `GITHUB_CI_CD_ENABLED` | Run/CICD adapter | Disabled. | Selects the GitHub Actions adapter only when token, repository, and explicit enablement are present; otherwise uses `NoOpCiCdAdapter`. | `GitHubActionsCiCdAdapterFeatureFlagTest.java` |
| `artifactCompiler.unsupportedParserDiagnostics.enabled` | Artifact compiler diagnostics | Disabled. | Emits unsupported-parser diagnostics only when explicitly enabled; otherwise unsupported files become residual islands. | `ArtifactGraphServiceUnsupportedParserTest.java` |

## Change Rules

1. Add or update the flag in this document with owner, default, behavior, and validation.
2. If the flag affects backend behavior, add or update server tests that exercise the real action, route, repository, or adapter.
3. If the flag affects route availability, regenerate `frontend/web/docs/route-inventory.md` and keep route coverage linked.
4. If the flag affects client rollout only, keep GrowthBook/default values in `FeatureFlagProvider` and add a component or route test for the visible behavior.
5. Never rely on a client flag as the only authorization control for a mutating backend path.
