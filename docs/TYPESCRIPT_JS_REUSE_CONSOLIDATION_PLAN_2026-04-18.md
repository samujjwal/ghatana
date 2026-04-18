# TypeScript/JavaScript Reuse Consolidation Plan

Date: 2026-04-18
Scope: Full repository TS/JS audit
Scan basis: 8114 TS/JS source and config files, 120 package manifests, 138 tsconfig files

## Audit rules used

- Review TS/JS code only.
- Prefer existing shared libraries before creating new ones.
- Treat generated, compiled, and router output as structural cleanup targets, not abstraction targets.
- Favor a single source of truth over parallel product-local copies.
- Do not recommend generic utility dumping grounds.

## A. Executive summary

Biggest duplication clusters:

1. DCMAAR has parallel app trees instead of shared product libraries. The clearest exact duplication is between `products/dcmaar/apps/child-mobile` and `products/dcmaar/apps/parent-mobile`, plus repeated extension/browser infrastructure between `products/dcmaar/apps/device-health` and existing DCMAAR TypeScript libraries.
2. YAPPC has multiple overlapping "shared" libraries that duplicate each other exactly: notifications, shortcuts, AEP config, theme tokens, and CRDT ownership are split across separate packages.
3. Several products still carry local UI primitives and state helpers even though platform packages already exist: `@ghatana/design-system`, `@ghatana/platform-utils`, `@ghatana/tokens`, `@ghatana/realtime`, and `@yappc/collab`.
4. Software Org Web contains same-intent duplication inside a single app: notification centers, loading states, API wrappers, and page-level feature scaffolds repeat the same patterns with different names.
5. Generated and compiled TS/JS outputs are checked into the repo alongside source in multiple products, which inflates duplication noise and hides real consolidation work.

Highest-value consolidation opportunities:

1. Extract a single DCMAAR mobile shared library for parent and child mobile apps.
2. Collapse YAPPC notifications, shortcuts, and AEP config into one source each.
3. Finish YAPPC CRDT ownership migration to `@yappc/collab` and remove state-owned CRDT code.
4. Move DCMAAR device-health app implementations onto existing product-local libraries instead of keeping app-local copies of monitors, pipelines, and browser-extension infrastructure.
5. Replace local utility and primitive reimplementations with platform imports where governance rules already point to canonical packages.

Fastest safe wins:

1. Delete exact duplicate YAPPC notification module under `products/yappc/frontend/libs/yappc-ai/src/messaging/notifications` after redirecting imports to `products/yappc/frontend/libs/yappc-ai/src/notifications`.
2. Delete exact duplicate YAPPC AEP config under `products/yappc/frontend/libs/yappc-ai/src/aep-config` and keep `products/yappc/frontend/libs/aep-config`.
3. Delete exact duplicate YAPPC shortcuts implementation under `products/yappc/frontend/libs/yappc-ui/src/components/shortcuts` and keep `products/yappc/frontend/libs/shortcuts`.
4. Remove app-local re-export shims that only point to canonical packages, such as `products/dcmaar/apps/device-health/src/shared/utils/logger.ts` and `products/dcmaar/apps/parent-dashboard/src/components/withErrorBoundary.tsx`.
5. Resolve DCMAAR `*New` and legacy component pairs by promoting one implementation and deleting the other.

Risky areas needing care:

1. Platform `DataGrid` exists both in `platform/typescript/data-grid` and `platform/typescript/design-system`; it needs one canonical implementation without breaking current consumers.
2. Software Org Web feature hooks and API clients are same-intent but not exact duplicates; they need consolidation by domain, not by filename.
3. Flashit and PHR have shared types and contracts opportunities, but the current product semantics are still diverging enough that a broad extraction could over-abstract.
4. YAPPC theme/token duplication touches many imports and Storybook stories; refactoring needs import-boundary validation and snapshot review.

## B. Reuse inventory

### 1. DCMAAR mobile app core duplicated across parent and child apps

- Title: DCMAAR mobile service, fixture, and screen duplication
- Duplicate locations:
  - `products/dcmaar/apps/child-mobile/src/services/api.ts`
  - `products/dcmaar/apps/parent-mobile/src/services/api.ts`
  - `products/dcmaar/apps/child-mobile/src/services/storage.ts`
  - `products/dcmaar/apps/parent-mobile/src/services/storage.ts`
  - `products/dcmaar/apps/child-mobile/src/services/notifications.ts`
  - `products/dcmaar/apps/parent-mobile/src/services/notifications.ts`
  - `products/dcmaar/apps/child-mobile/src/hooks/useApi.ts`
  - `products/dcmaar/apps/parent-mobile/src/hooks/useApi.ts`
  - Matching fixtures, mocks, navigation setup, and screen tests under both `src/__tests__` trees
- Current intent: two React Native apps with almost identical transport, persistence, fixtures, and navigation support.
- Duplication type: exact and near duplicate
- Recommended target shared location: create one product-local shared library at `products/dcmaar/libs/typescript/mobile-shared`
- Recommended abstraction name: `@dcmaar/mobile-shared`
- Generic API shape:
  - `createGuardianApiClient(config)`
  - `storageService`
  - `notificationService`
  - `createUseApi(apiClient)`
  - `fixtures/{device,alert,policy}`
  - `test/{renderApp,mocks,navigation}`
- Variability points to parameterize:
  - persona-specific labels
  - app navigation composition
  - screen set differences
- What should remain specialized:
  - parent-only screens and policy flows
  - child-only usage and block views
- Callers/usages to update:
  - both mobile apps' services, hooks, fixtures, jest setup, and matching tests
- Expected benefits:
  - remove exact duplicate maintenance
  - one API/storage contract for both mobile apps
  - simpler testing surface
- Migration complexity: high
- Confidence: high

### 2. DCMAAR device-health duplicates existing browser-extension libraries

- Title: Device-health app reimplements monitors, pipelines, and browser-extension infrastructure already present in product libraries
- Duplicate locations:
  - `products/dcmaar/apps/device-health/src/plugins/monitors/CPUMonitor.ts`
  - `products/dcmaar/libs/typescript/plugin-extension/src/CPUMonitor.ts`
  - `products/dcmaar/apps/device-health/src/plugins/monitors/BatteryMonitor.ts`
  - `products/dcmaar/libs/typescript/plugin-extension/src/BatteryMonitor.ts`
  - `products/dcmaar/apps/device-health/src/plugins/monitors/MemoryMonitor.ts`
  - `products/dcmaar/libs/typescript/plugin-extension/src/MemoryMonitor.ts`
  - `products/dcmaar/apps/device-health/src/app/background/pipeline/EventPipeline.ts`
  - `products/dcmaar/libs/typescript/browser-extension-core/src/pipeline/EventPipeline.ts`
  - `products/dcmaar/apps/device-health/src/browser/controller/ExtensionController.ts`
  - `products/dcmaar/libs/typescript/browser-extension-core/src/controller/BaseExtensionController.ts`
  - `products/dcmaar/apps/device-health/src/connectors/*`
  - `products/dcmaar/libs/typescript/connectors/src/*`
- Current intent: the app embeds its own versions of product-local shared extension infrastructure.
- Duplication type: conceptual and near duplicate
- Recommended target shared location:
  - keep monitors in `products/dcmaar/libs/typescript/plugin-extension`
  - keep pipeline and controller abstractions in `products/dcmaar/libs/typescript/browser-extension-core`
  - keep connector implementations in `products/dcmaar/libs/typescript/connectors`
- Recommended abstraction name: no new abstraction; finish migration onto existing DCMAAR libraries
- Generic API shape:
  - `createMonitor(config)`
  - `EventPipeline` with app-provided sources/sinks
  - `ExtensionController` subclass or composition layer
  - connector factories from `connectors`
- Variability points to parameterize:
  - manifest/config defaults
  - app-specific wiring
  - device-health analytics views
- What should remain specialized:
  - device-health page composition and app-specific UX
  - app-specific onboarding/configuration flows
- Callers/usages to update:
  - device-health app imports under `src/plugins`, `src/app/background`, `src/browser`, `src/connectors`
- Expected benefits:
  - one extension runtime model for DCMAAR
  - less drift between app and libraries
  - smaller test matrix
- Migration complexity: high
- Confidence: high

### 3. DCMAAR parent-dashboard keeps legacy and replacement components in parallel

- Title: Parent dashboard maintains duplicated legacy and `New` implementations
- Duplicate locations:
  - `products/dcmaar/apps/parent-dashboard/src/components/DeviceManagement.tsx`
  - `products/dcmaar/apps/parent-dashboard/src/components/DeviceManagementNew.tsx`
  - `products/dcmaar/apps/parent-dashboard/src/components/PolicyManagement.tsx`
  - `products/dcmaar/apps/parent-dashboard/src/components/PolicyManagementNew.tsx`
  - `products/dcmaar/apps/parent-dashboard/src/components/Analytics.tsx`
  - `products/dcmaar/apps/parent-dashboard/src/components/AnalyticsNew.tsx`
  - `products/dcmaar/apps/parent-dashboard/src/components/Reports.tsx`
  - `products/dcmaar/apps/parent-dashboard/src/components/ReportsNew.tsx`
  - `products/dcmaar/apps/parent-dashboard/src/components/UsageMonitor.tsx`
  - `products/dcmaar/apps/parent-dashboard/src/components/UsageMonitorNew.tsx`
  - `products/dcmaar/apps/parent-dashboard/src/components/BlockNotifications.tsx`
  - `products/dcmaar/apps/parent-dashboard/src/components/BlockNotificationsNew.tsx`
  - `products/dcmaar/apps/parent-dashboard/src/pages/Dashboard.tsx`
  - `products/dcmaar/apps/parent-dashboard/src/pages/DashboardNew.tsx`
- Current intent: design-system based replacements were added without deleting legacy versions.
- Duplication type: conceptual and near duplicate
- Recommended target shared location: remain feature-local in parent-dashboard
- Recommended abstraction name: none; choose one implementation per feature
- Generic API shape: not applicable
- Variability points to parameterize: none; this is a deletion and route-cutover task
- What should remain specialized: the chosen feature component only
- Callers/usages to update:
  - dashboard routes
  - tests under `src/test`
  - storybook stories as needed
- Expected benefits:
  - delete dead parallel codepaths
  - reduce bug-fix duplication
  - force one source of truth per feature
- Migration complexity: medium
- Confidence: high

### 4. YAPPC notifications duplicated exactly inside one library

- Title: YAPPC notification UI duplicated under two paths
- Duplicate locations:
  - `products/yappc/frontend/libs/yappc-ai/src/notifications/components/NotificationPanel.tsx`
  - `products/yappc/frontend/libs/yappc-ai/src/messaging/notifications/components/NotificationPanel.tsx`
  - matching `NotificationBell.tsx`, `NotificationItem.tsx`, `hooks/useNotificationBackend.ts`, and `index.ts`
- Current intent: same notification feature exposed from two different entry trees.
- Duplication type: exact duplicate
- Recommended target shared location: `products/yappc/frontend/libs/yappc-ai/src/notifications`
- Recommended abstraction name: `notifications`
- Generic API shape:
  - `useNotificationBackend()`
  - `NotificationBell`
  - `NotificationPanel`
  - `NotificationItem`
- Variability points to parameterize: none
- What should remain specialized: route-level messaging composition only
- Callers/usages to update:
  - `products/yappc/frontend/libs/yappc-ai/src/messaging/index.ts`
  - any imports into `messaging/notifications/*`
- Expected benefits:
  - immediate duplicate removal
  - one component test target
  - zero semantic risk
- Migration complexity: low
- Confidence: high

### 5. YAPPC AEP config duplicated exactly across two libraries

- Title: AEP client/config factory exists twice
- Duplicate locations:
  - `products/yappc/frontend/libs/aep-config/index.ts`
  - `products/yappc/frontend/libs/yappc-ai/src/aep-config/index.ts`
  - matching `aep-client-factory.ts`, `aep-mode.ts`, and examples
- Current intent: shared AEP configuration for YAPPC frontend consumers.
- Duplication type: exact duplicate
- Recommended target shared location: `products/yappc/frontend/libs/aep-config`
- Recommended abstraction name: `@yappc/aep-config`
- Generic API shape:
  - `createAepClient(config)`
  - `getAepConfig(mode)`
  - `validateAepConfig(config)`
- Variability points to parameterize: none
- What should remain specialized: AI feature consumers only
- Callers/usages to update:
  - all imports from `libs/yappc-ai/src/aep-config/*`
- Expected benefits:
  - single product-wide AEP integration surface
  - lower packaging drift
- Migration complexity: low
- Confidence: high

### 6. YAPPC shortcuts duplicated exactly across two libraries

- Title: Keyboard shortcut system implemented twice
- Duplicate locations:
  - `products/yappc/frontend/libs/shortcuts/src/hooks/useKeyboardShortcuts.ts`
  - `products/yappc/frontend/libs/yappc-ui/src/components/shortcuts/hooks/useKeyboardShortcuts.ts`
  - matching `components/CommandPalette.tsx`, `ShortcutHelper.tsx`, `types.ts`, `utils.ts`, and index barrels
- Current intent: reusable keyboard shortcuts and command palette support.
- Duplication type: exact duplicate
- Recommended target shared location: `products/yappc/frontend/libs/shortcuts`
- Recommended abstraction name: `@yappc/shortcuts`
- Generic API shape:
  - `useKeyboardShortcuts(options)`
  - `shortcutRegistry`
  - `CommandPalette`
  - `ShortcutHelper`
- Variability points to parameterize:
  - shortcut registry seed data
  - UI styling wrapper if necessary
- What should remain specialized:
  - app-specific shortcut declarations
- Callers/usages to update:
  - all imports from `libs/yappc-ui/src/components/shortcuts/*`
- Expected benefits:
  - removes one full duplicate shared library subtree
  - centralizes shortcut behavior and docs
- Migration complexity: low
- Confidence: high

### 7. YAPPC token and theme trees duplicate platform tokens and local theme ownership

- Title: YAPPC UI token trees overlap platform tokens and contain internal duplication
- Duplicate locations:
  - `products/yappc/frontend/libs/yappc-ui/src/components/theme/tokens/*`
  - `products/yappc/frontend/libs/yappc-ui/src/components/tokens/*`
  - `platform/typescript/tokens/src/*`
  - `products/yappc/frontend/libs/yappc-ui/src/components/theme/theme/*`
  - `products/yappc/frontend/libs/yappc-product-theme/src/*`
- Current intent: shared visual tokens and product theme runtime.
- Duplication type: exact, near, and conceptual duplicate
- Recommended target shared location:
  - global tokens in `platform/typescript/tokens`
  - product-specific presets in `products/yappc/frontend/libs/yappc-product-theme`
- Recommended abstraction name:
  - `@ghatana/tokens`
  - `@yappc/product-theme`
- Generic API shape:
  - token import from `@ghatana/tokens`
  - theme preset factory from `@yappc/product-theme`
- Variability points to parameterize:
  - product palette presets
  - theme provider wrappers
- What should remain specialized:
  - YAPPC preset composition
  - MUI bridge if still product-specific
- Callers/usages to update:
  - all imports from `libs/yappc-ui/src/components/theme/tokens/*`
  - all imports from duplicate theme/theme subtrees
- Expected benefits:
  - true single source of truth for tokens
  - less bundle duplication
  - clearer ownership boundary
- Migration complexity: high
- Confidence: medium

### 8. YAPPC CRDT ownership is split between collab and state

- Title: CRDT code lives under both state and collaboration packages
- Duplicate locations:
  - `products/yappc/frontend/libs/yappc-state/src/store/crdt/*`
  - `products/yappc/frontend/libs/collab/src/crdt/*`
  - `products/yappc/frontend/libs/yappc-state/src/store/crdt/index.ts` already marks `@yappc/collab` as canonical
- Current intent: collaboration/CRDT primitives for YAPPC canvas and IDE.
- Duplication type: conceptual duplicate with partial re-export migration already in place
- Recommended target shared location: `products/yappc/frontend/libs/collab/src/crdt`
- Recommended abstraction name: `@yappc/collab/crdt`
- Generic API shape:
  - `CRDTCore`
  - `ConflictResolutionEngine`
  - IDE adapters and websocket sync
- Variability points to parameterize: none at package ownership level
- What should remain specialized:
  - state package hooks that consume collaboration state
- Callers/usages to update:
  - any remaining imports from `@yappc/state` CRDT paths
  - internal state package references to owned CRDT files
- Expected benefits:
  - finish an in-progress consolidation cleanly
  - remove ambiguous ownership
- Migration complexity: medium
- Confidence: high

### 9. Platform DataGrid has two implementations

- Title: `DataGrid` exists in both platform packages
- Duplicate locations:
  - `platform/typescript/data-grid/src/DataGrid.tsx`
  - `platform/typescript/design-system/src/organisms/DataGrid.tsx`
- Current intent: reusable data table/grid for platform and product UIs.
- Duplication type: conceptual duplicate
- Recommended target shared location:
  - keep low-level implementation in `platform/typescript/data-grid`
  - make `@ghatana/design-system` re-export or wrap it, not reimplement it
- Recommended abstraction name: `@ghatana/data-grid` as implementation, `@ghatana/design-system` as public ergonomic entry if needed
- Generic API shape:
  - one core `DataGrid<T>` component
  - optional design-system wrapper for product-friendly presets
- Variability points to parameterize:
  - stats card header layout
  - filter toolbar slots
  - styling presets
- What should remain specialized:
  - product feature-specific grid columns and actions
- Callers/usages to update:
  - design-system internals
  - DCMAAR parent-dashboard new components
  - any direct `@ghatana/data-grid` consumers
- Expected benefits:
  - prevent future divergence in sorting/filtering/pagination semantics
  - one test suite for grid behavior
- Migration complexity: high
- Confidence: medium

### 10. Software Org Web duplicates notification and feedback patterns inside one app

- Title: Software Org Web has multiple local feedback systems
- Duplicate locations:
  - `products/software-org/client/web/src/shared/components/NotificationCenter.tsx`
  - `products/software-org/client/web/src/features/notifications/NotificationCenter.tsx`
  - `products/software-org/client/web/src/components/LoadingState.tsx`
  - `products/software-org/client/web/src/components/LoadingStates.tsx`
  - `products/software-org/client/web/src/components/layouts/page/PageStates.tsx`
  - `products/software-org/client/web/src/components/ErrorBoundary.tsx`
- Current intent: app-level notification, loading, and error feedback surfaces.
- Duplication type: conceptual and near duplicate
- Recommended target shared location: remain local to Software Org Web under one app-shared folder, for example `src/shared/ui/feedback`
- Recommended abstraction name: `feedback`
- Generic API shape:
  - `NotificationCenter`
  - `PageState` with loading/error/empty variants
  - `AppErrorBoundary`
- Variability points to parameterize:
  - toast vs inbox notification rendering
  - page-level vs inline state variants
- What should remain specialized:
  - domain-specific copy and action buttons per feature
- Callers/usages to update:
  - `src/features/notifications/*`
  - `src/components/*Loading*`
  - page layout components
- Expected benefits:
  - fewer competing UX patterns in one app
  - simpler visual consistency enforcement
- Migration complexity: medium
- Confidence: medium

### 11. Software Org Web duplicates data-access layers by domain and by transport wrapper

- Title: Software Org Web maintains overlapping hooks and API client wrappers
- Duplicate locations:
  - `products/software-org/client/web/src/services/api/*.ts`
  - `products/software-org/client/web/src/hooks/use*Api.ts`
  - `products/software-org/client/web/src/lib/api/client.ts`
  - `products/software-org/client/web/src/lib/utils/apiService.ts`
  - `products/software-org/client/web/src/services/api.service.ts`
- Current intent: fetch and cache domain data across many pages.
- Duplication type: conceptual duplicate
- Recommended target shared location: remain local to Software Org Web under one app-shared data layer, for example `src/shared/data`
- Recommended abstraction name: `domain clients + query hooks`
- Generic API shape:
  - one transport client
  - one domain client per bounded area
  - one query hook per domain resource built on that client
- Variability points to parameterize:
  - route prefixes
  - query keys
  - optimistic updates
- What should remain specialized:
  - orchestration hooks that combine multiple resources for a page
- Callers/usages to update:
  - `src/hooks/use*Api.ts`
  - `src/services/api/*.ts`
  - `src/lib/api/*`
- Expected benefits:
  - simpler request lifecycle ownership
  - consistent error handling and caching
- Migration complexity: high
- Confidence: medium

### 12. Flashit shared library is underused while mobile and web keep local copies

- Title: Flashit clients still duplicate product types, API access, and helpers despite existing shared package
- Duplicate locations:
  - `products/flashit/libs/ts/shared/src/api/client.ts`
  - `products/flashit/client/web/src/lib/api-client.ts`
  - `products/flashit/libs/ts/shared/src/types/*`
  - client-local screen/component contracts in both web and mobile apps
  - `products/flashit/libs/ts/shared/src/validation/*`
  - client-local request/validation flows in both apps
- Current intent: shared Flashit domain contracts and client capabilities.
- Duplication type: conceptual duplicate
- Recommended target shared location: `products/flashit/libs/ts/shared`
- Recommended abstraction name: `@flashit/shared`
- Generic API shape:
  - shared API client factory
  - shared domain types and validation schemas
  - shared offline-safe request models
- Variability points to parameterize:
  - platform-specific media capture and upload primitives
- What should remain specialized:
  - camera, recorder, haptics, and mobile-native services
  - browser media capture UI
- Callers/usages to update:
  - Flashit web and mobile app data layer imports
- Expected benefits:
  - one domain contract for both clients
  - easier backend compatibility updates
- Migration complexity: medium
- Confidence: medium

### 13. Generated and compiled TS/JS is committed beside source

- Title: Generated and built outputs create structural duplication noise
- Duplicate locations:
  - `products/dcmaar/modules/ai-platform-adapters/src-ts/*`
  - `products/dcmaar/modules/ai-platform-adapters/dist-ts/*`
  - `products/software-org/services/management-api/generated/prisma-client/*`
  - `products/tutorputor/libs/tutorputor-core/generated/prisma/*`
  - `products/yappc/frontend/apps/api/prisma/generated/client/*`
  - `products/software-org/client/web/.react-router/types/*`
  - `products/yappc/frontend/e2e/helpers/*.ts`, `*.js`, and `*.d.ts` parallel outputs
- Current intent: checked-in generated code, build output, and router types.
- Duplication type: exact derivative output
- Recommended target shared location: none; remove from source-truth audit surface
- Recommended abstraction name: none
- Generic API shape: not applicable
- Variability points to parameterize: not applicable
- What should remain specialized: generated output only where repo policy explicitly requires it
- Callers/usages to update:
  - build scripts and ignore rules
  - import paths that point directly into generated output
- Expected benefits:
  - clearer duplication audits
  - smaller review surface
  - fewer accidental edits in generated trees
- Migration complexity: medium
- Confidence: high

## C. Shared library placement plan

Promote to local shared library:

1. DCMAAR mobile cross-app code -> `products/dcmaar/libs/typescript/mobile-shared`
   Reason: exact duplication across sibling mobile apps, but semantics remain product-specific.
2. Software Org Web feedback components -> one app-shared `src/shared/ui/feedback`
   Reason: repeated within one app only; not stable enough for platform.
3. Software Org Web data layer -> one app-shared `src/shared/data`
   Reason: repeated transport/query logic is app-local, not platform-generic.
4. PHR shared web/mobile contracts -> future `products/phr/libs/ts/app-core`
   Reason: medium-value local reuse across only one product; do not push to platform yet.

Promote to global shared library or canonical platform package:

1. Utility functions -> `@ghatana/platform-utils`
   Reason: governance already marks this as canonical.
2. Reusable UI primitives and common feedback UI -> `@ghatana/design-system`
   Reason: governance already blocks reimplementation and products already consume it.
3. Global design tokens -> `@ghatana/tokens`
   Reason: repo already describes this as the canonical token source.
4. WebSocket hooks -> `@ghatana/realtime`
   Reason: platform package already exists and should own generic connection lifecycle.

Keep feature-local:

1. DCMAAR parent-dashboard feature views after choosing one version per feature
   Reason: replacement pairs should be deleted, not extracted.
2. Software Org feature orchestration hooks
   Reason: they encode page composition, not generic transport.
3. Canvas element implementations and node renderers
   Reason: broad shared abstraction already exists; element-level differences are product/domain specific.
4. Flashit media capture and native upload services
   Reason: platform differences are still real and behavior is unstable.

## D. Migration sequence

1. Freeze canonical ownership first.
   - Mark one source of truth per cluster before moving code.
   - Add import restrictions where ownership is already obvious.

2. Consolidate exact duplicates first.
   - YAPPC notifications
   - YAPPC AEP config
   - YAPPC shortcuts
   - DCMAAR logger shims and other pure re-exports

3. Extract local shared libraries where two apps are truly sharing behavior.
   - DCMAAR mobile shared library
   - Software Org shared feedback/data layers

4. Migrate product-local app implementations onto existing libraries.
   - DCMAAR device-health onto `browser-extension-core`, `plugin-extension`, and `connectors`
   - YAPPC state onto `@yappc/collab`

5. Collapse parallel old/new feature trees.
   - DCMAAR parent-dashboard `*New` vs legacy components

6. Rationalize platform overlap.
   - `DataGrid` canonical implementation
   - any remaining design-system primitive duplicates called out by governance rules

7. Delete duplicates immediately after migration.
   - Do not keep old and new paths alive in parallel.

8. Validate boundaries and health.
   - run lint and typecheck in touched workspaces
   - run targeted unit/integration tests for migrated clusters
   - verify no deep-import or deprecated-import violations

## E. Concrete refactor list

Extract:

- DCMAAR mobile shared code from:
  - `products/dcmaar/apps/child-mobile/src/services/*`
  - `products/dcmaar/apps/parent-mobile/src/services/*`
  - `products/dcmaar/apps/child-mobile/src/hooks/useApi.ts`
  - `products/dcmaar/apps/parent-mobile/src/hooks/useApi.ts`
  - both apps' `src/__tests__/fixtures/*`, `src/__tests__/mocks/*`, and setup helpers

Merge:

- `products/yappc/frontend/libs/yappc-ai/src/messaging/notifications/*` into `products/yappc/frontend/libs/yappc-ai/src/notifications/*`
- `products/yappc/frontend/libs/yappc-ai/src/aep-config/*` into `products/yappc/frontend/libs/aep-config/*`
- `products/yappc/frontend/libs/yappc-ui/src/components/shortcuts/*` into `products/yappc/frontend/libs/shortcuts/*`
- `products/yappc/frontend/libs/yappc-ui/src/components/theme/tokens/*` into `platform/typescript/tokens/src/*` and `products/yappc/frontend/libs/yappc-product-theme/src/*`
- `products/yappc/frontend/libs/yappc-state/src/store/crdt/*` into `products/yappc/frontend/libs/collab/src/crdt/*`

Rename or move:

- Replace app-local imports in device-health to product-local libs:
  - monitors to `products/dcmaar/libs/typescript/plugin-extension/src/*`
  - browser extension runtime pieces to `products/dcmaar/libs/typescript/browser-extension-core/src/*`
  - connectors to `products/dcmaar/libs/typescript/connectors/src/*`

Delete:

- one side of each DCMAAR parent-dashboard pair:
  - `DeviceManagement.tsx` or `DeviceManagementNew.tsx`
  - `PolicyManagement.tsx` or `PolicyManagementNew.tsx`
  - `Analytics.tsx` or `AnalyticsNew.tsx`
  - `Reports.tsx` or `ReportsNew.tsx`
  - `UsageMonitor.tsx` or `UsageMonitorNew.tsx`
  - `BlockNotifications.tsx` or `BlockNotificationsNew.tsx`
  - `Dashboard.tsx` or `DashboardNew.tsx`
- `products/yappc/frontend/libs/yappc-ai/src/messaging/notifications/*`
- `products/yappc/frontend/libs/yappc-ai/src/aep-config/*`
- `products/yappc/frontend/libs/yappc-ui/src/components/shortcuts/*`
- stale and archival leftovers where not referenced:
  - `products/software-org/client/web/src/pages/org/RestructurePage.old.tsx`
  - `products/yappc/docs/archive/index-old.ts`

Update imports for:

- any `@yappc/state` CRDT path -> `@yappc/collab/crdt`
- any YAPPC local shortcut imports -> `@yappc/shortcuts`
- any YAPPC local AEP config imports -> `@yappc/aep-config`
- any local `cn` reimplementations -> `@ghatana/platform-utils`
- any local primitive imports reimplemented in-product -> `@ghatana/design-system`

## F. Risk list

- Over-abstraction risk:
  - Do not create a cross-product mobile library for DCMAAR, Flashit, and PHR. Their native concerns are not stable enough.
- Unstable domain semantics risk:
  - Software Org feature hooks often encode persona-specific orchestration; collapse transport wrappers, not page orchestration.
- Type-level breakage risk:
  - YAPPC theme/token refactors touch many Storybook and app imports.
  - DCMAAR mobile extraction touches alias paths, jest config, and React Native metro resolution.
- Tree-shaking and bundle-size risk:
  - Consolidating onto design-system is good only if consumers import public entrypoints and dead code elimination still works.
- Circular dependency risk:
  - Keep YAPPC shared packages layered: `tokens -> theme -> UI`, not `UI -> theme -> UI`.
  - Keep DCMAAR app wiring separate from product libraries.
- Test fragility risk:
  - DCMAAR mobile tests are duplicated today; extraction will break brittle path-based mocks.
  - Software Org Web notification and loading state tests may depend on current component-local copy.

## Top 10 immediate TS/JS consolidations

1. Delete `products/yappc/frontend/libs/yappc-ai/src/messaging/notifications/*` after redirecting imports to `products/yappc/frontend/libs/yappc-ai/src/notifications/*`.
2. Delete `products/yappc/frontend/libs/yappc-ai/src/aep-config/*` after redirecting imports to `products/yappc/frontend/libs/aep-config/*`.
3. Delete `products/yappc/frontend/libs/yappc-ui/src/components/shortcuts/*` after redirecting imports to `products/yappc/frontend/libs/shortcuts/*`.
4. Migrate remaining YAPPC CRDT imports to `@yappc/collab/crdt` and remove `products/yappc/frontend/libs/yappc-state/src/store/crdt/*` ownership.
5. Create `products/dcmaar/libs/typescript/mobile-shared` and move duplicated parent/child mobile services, fixtures, and test setup into it.
6. Replace app-local device-health monitors and pipeline code with imports from DCMAAR product libraries.
7. Choose and keep one implementation from each DCMAAR parent-dashboard `*New` pair.
8. Collapse Software Org Web notification components into one app-shared feedback module.
9. Collapse Software Org Web loading and page-state components into one app-shared feedback module.
10. Decide one canonical platform `DataGrid` implementation and make the other a wrapper or re-export.

## Top 10 structural consolidations

1. Enforce `@ghatana/platform-utils` for generic utility ownership.
2. Enforce `@ghatana/design-system` for product-consumable UI primitives and common feedback components.
3. Enforce `@ghatana/tokens` as the only token source; keep product presets separate.
4. Remove checked-in compiled `dist-ts` trees where source already exists and build can regenerate them.
5. Remove generated router type output from review surface where it is not required as source.
6. Replace local app-level WebSocket hooks with `@ghatana/realtime` where behavior is generic.
7. Stop keeping legacy and replacement feature trees in parallel after migration.
8. Keep product-local shared libraries product-local; do not promote unstable abstractions to platform.
9. Add lint rules for exact duplicate package ownership in YAPPC shared libs.
10. Add duplicate-file and stale-fork checks to CI for `*.old.*`, `*New.tsx`, and known parallel library paths.

## Do not abstract yet

1. Platform canvas element modules under `platform/typescript/canvas/src/elements/*`. They already sit in the right shared layer and differ by behavior, not just naming.
2. Software Org feature orchestration hooks such as multi-resource dashboard hooks. Consolidate transport, not page composition.
3. Flashit media capture, upload, and recorder services across web and mobile. Share contracts, not platform-specific runtime code.
4. PHR screens and page components. Extract shared contracts first; do not force shared UI until domain flows stabilize.
5. Product-specific dashboard cards and domain widgets in Tutorputor, Flashit, and Software Org. These are still domain-owned, even when they look visually similar.

## Recommended execution order

1. YAPPC exact duplicates
2. DCMAAR stale forks and mobile extraction
3. DCMAAR device-health migration onto product libs
4. Software Org app-local feedback/data layer cleanup
5. Platform `DataGrid` unification
6. Generated-output cleanup and CI enforcement