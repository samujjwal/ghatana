Below is a **granular implementation plan** grounded in the current `samujjwal/ghatana` repo at commit `1334d4e26f0e854352ddae12e6019e9cc6cd8d9b`. I am not adding invented modules or new stack choices; every task maps to existing files, scripts, packages, routes, providers, or workflow surfaces already present in the repo.

The current repo already has the right foundation pieces: root scripts for Kernel lifecycle, Studio/Kernel API checks, agentic lifecycle checks, YAPPC handoff checks, Data Cloud platform provider checks, Digital Marketing lifecycle pilot commands, and product-shape readiness checks exist in `package.json`.  The workspace already includes platform TypeScript packages, Data Cloud, YAPPC, Digital Marketing, PHR, FlashIt, TutorPutor, DCMAAR, Audio-Video, and shared service packages.  The Gradle settings already include platform Java modules, platform Kernel modules, plugins, product modules, Data Cloud/YAPPC kernel bridges, shared services, and integration tests. 

---

# Implementation strategy

## North-star implementation order

Implement in this order so the foundation becomes **extensible, future-ready, scalable, and simple to use**:

```text
1. Stabilize contracts and provider-mode rules.
2. Make ProductUnitIntent handoff durable.
3. Make Kernel lifecycle truth fail-closed and auditable.
4. Make Digital Marketing the first full Studio-driven lifecycle pilot.
5. Make Data Cloud platform mode real.
6. Make agentic lifecycle governance provider-backed.
7. Make artifact intelligence flow through Data Cloud and Kernel gates.
8. Expand future products only through the product-shape matrix readiness gate.
9. Consolidate docs and remove stale/dead/drift-prone artifacts.
```

Do **not** enable PHR, Finance, FlashIt, Data Cloud, YAPPC, Aura, DCMAAR, TutorPutor, or Audio-Video lifecycle execution until their readiness is proven by the generated product-shape capability matrix. The existing matrix already marks Digital Marketing as the pilot and future products as disabled, partial, or shape-only.   

---

# Workstream 0 — Foundation rules and implementation guardrails

## Goal

Prevent the implementation from creating another scattered set of features. All work must preserve these boundaries:

```text
Ghatana Studio = unified customer experience
Product Development Kernel = lifecycle truth and execution
YAPPC = ideation, blueprinting, canvas, artifact intelligence, learning, evolution
Data Cloud = runtime truth, events, memory, provenance, governance, Action Plane / AEP
Products = domain behavior and product-specific configuration
Shared libraries = reusable product-neutral primitives
```

## Granular tasks

### 0.1 Root package scripts

**File:** `package.json`

The root already defines lifecycle, platform, Studio, YAPPC, Data Cloud, Digital Marketing, product-shape, production-readiness, observability, data-access, and agentic checks. Keep these as the canonical command entrypoints and do not add duplicate scripts under ad hoc names. 

Tasks:

1. Keep `check:kernel-product-boundary-audit` as the aggregate boundary/production-readiness command.
2. Add new checks only when they validate a real invariant not already covered.
3. Do not add product-specific Kernel scripts outside the existing `kernel-product.mjs` / `product` command pattern.
4. Add any new lifecycle commands as wrappers around existing Kernel commands, not separate implementations.
5. Keep Digital Marketing commands aligned with existing names:

   * `validate:digital-marketing`
   * `test:digital-marketing`
   * `build:digital-marketing`
   * `package:digital-marketing`
   * `deploy:local:digital-marketing`
   * `verify:local:digital-marketing`
6. Add only these missing aggregate scripts if not already present after implementation:

   * `check:studio-e2e-product-lifecycle`
   * `check:data-cloud-kernel-provider-e2e`
   * `check:yappc-product-unit-intent-apply-e2e`
7. Make each new script compose existing package-level commands and checks.
8. Avoid shell-specific behavior unless already present in scripts.
9. Ensure new scripts are used by workflow or documentation; delete unused scripts.
10. Validation:

```bash
pnpm check:kernel-product-boundary-audit
pnpm check:production-readiness
pnpm check:product-shape-capability-matrix
```

---

### 0.2 Workspace registration

**File:** `pnpm-workspace.yaml`

The workspace is generated from `canonical-product-registry.json` and already includes platform packages and product packages. 

Tasks:

1. Do not manually edit generated workspace entries.
2. When adding package-level implementation, ensure the package is covered by an existing workspace glob.
3. If a new package is truly needed, add it to the canonical registry/generator source, not directly to `pnpm-workspace.yaml`.
4. Confirm all touched packages have:

   * `package.json`
   * `tsconfig.json`
   * build script
   * typecheck script
   * test script where meaningful
   * README if package-level public surface
5. Validation:

   ```bash
   pnpm check:product-registry-artifacts
   pnpm check:product-workspace-registration
   pnpm check:product-package-metadata
   ```

---

### 0.3 Gradle module registration

**File:** `settings.gradle.kts`

The Gradle settings already include platform Java modules, platform Kernel modules, platform plugins, product-generated includes, Data Cloud/YAPPC kernel bridges, shared services, and integration tests. 

Tasks:

1. Do not add Java modules outside the existing convention structure.
2. If a Java provider or bridge is added, register it through existing Gradle include patterns.
3. Use existing `build-logic` convention plugins, not root `buildSrc`.
4. Keep Data Cloud Kernel bridge Java code under:

   ```text
   products/data-cloud/extensions/kernel-bridge
   ```
5. Keep platform-generic Java code under:

   ```text
   platform/java/*
   ```
6. Do not move product-named Java code into `platform/java`.
7. Validation:

   ```bash
   ./gradlew check --no-daemon --stacktrace
   ./gradlew build --no-daemon --stacktrace
   ```

---

# Workstream 1 — ProductUnitIntent durable handoff

## Goal

Convert YAPPC → Kernel ProductUnitIntent handoff from “validated request accepted/queued” into a durable, auditable, provider-backed handoff that can actually produce or update a Kernel ProductUnit.

The current YAPPC route validates ProductUnitIntent, authentication, scope, evidence, platform-mode Data Cloud evidence refs, and apply permission, but it returns accepted/queued responses without proving durable Kernel application.  The shared ProductUnitIntent contract is strict and already validates schema, governance hints, provenance, missing evidence, secret-like fields, and supported lifecycle phases.  

---

## 1.1 Add explicit ProductUnitIntent application contract

**File:** `platform/typescript/kernel-product-contracts/src/product-unit/ProductUnitIntent.ts`

Tasks:

1. Add `ProductUnitIntentApplicationStatus`:

   ```ts
   export type ProductUnitIntentApplicationStatus =
     | "previewed"
     | "queued"
     | "applied"
     | "blocked"
     | "failed";
   ```
2. Add `ProductUnitIntentApplicationResult`:

   ```ts
   export interface ProductUnitIntentApplicationResult {
     readonly schemaVersion: "1.0.0";
     readonly intentId: string;
     readonly status: ProductUnitIntentApplicationStatus;
     readonly productUnitId: string;
     readonly correlationId: string;
     readonly providerMode: "bootstrap" | "platform";
     readonly registryProviderId: string;
     readonly sourceProviderId: string;
     readonly previewRef?: string;
     readonly applicationRef?: string;
     readonly lifecycleEventRefs: readonly string[];
     readonly provenanceRefs: readonly string[];
     readonly runtimeTruthRefs: readonly string[];
     readonly blockedReasons: readonly string[];
     readonly errors: readonly string[];
     readonly appliedAt?: string;
   }
   ```
3. Add a Zod schema for this result.
4. Add reason codes for:

   * `target-provider-mismatch`
   * `missing-apply-permission`
   * `provider-mode-not-available`
   * `registry-apply-failed`
   * `runtime-truth-write-failed`
   * `provenance-write-failed`
   * `event-write-failed`
5. Add strict output validation so the route cannot return loosely shaped objects.
6. Export the result type/schema from:

   ```text
   platform/typescript/kernel-product-contracts/src/product-unit/index.ts
   platform/typescript/kernel-product-contracts/src/index.ts
   ```
7. Add tests:

   ```text
   platform/typescript/kernel-product-contracts/src/product-unit/__tests__/ProductUnitIntentApplicationResult.test.ts
   ```
8. Test cases:

   * valid preview result
   * valid applied result with event/provenance refs
   * invalid result missing correlationId
   * invalid result with unknown status
   * invalid result with empty productUnitId
   * blocked result with reason codes
9. Validation:

   ```bash
   pnpm --dir platform/typescript/kernel-product-contracts test --run
   pnpm check:agentic-lifecycle-action-contracts
   pnpm check:yappc-product-unit-intent-handoff
   ```

---

## 1.2 Add Kernel service method to apply ProductUnitIntent

**File:** `platform/typescript/kernel-lifecycle/src/service/KernelLifecycleService.ts`

The current service can list/get ProductUnits, create plans, execute plans, list runs, get manifests, request approvals, and write runtime truth/provenance.  It already records runtime truth and provenance for lifecycle plans/results. 

Tasks:

1. Add method:

   ```ts
   async applyProductUnitIntent(
     intent: ProductUnitIntent,
     options: {
       readonly mode: ProductUnitIntentApplyMode;
       readonly providerMode?: KernelProviderMode;
       readonly allowWrite: boolean;
       readonly actorId: string;
       readonly correlationId?: string;
     }
   ): Promise<ProductUnitIntentApplicationResult>
   ```
2. Validate `ProductUnitIntentSchema` at method boundary.
3. Resolve provider mode:

   * default to `this.providerContext.mode`
   * reject invalid mode
   * fail closed in platform mode if required providers are missing
4. Require registry provider to support:

   * `previewApplyProductUnitIntent`
   * `applyProductUnitIntent`
5. If current `RegistryProvider` interface does not expose these methods, add a narrower optional interface:

   ```ts
   export interface ProductUnitIntentRegistryProvider extends RegistryProvider { ... }
   ```
6. In preview mode:

   * call provider preview
   * record runtime truth as `product-unit-intent-previewed`
   * record provenance with evidence refs
   * append lifecycle event
   * return `previewed`
7. In apply mode:

   * require `allowWrite: true`
   * call provider apply
   * record runtime truth as `product-unit-intent-applied`
   * record provenance with evidence refs
   * append lifecycle event
   * clear or refresh registry cache if supported
   * return `applied`
8. If any required provider write fails in platform mode:

   * fail the operation
   * return `failed`
   * include reason code and provider error
9. Do not mutate registry directly in YAPPC.
10. Add tests:

    ```text
    platform/typescript/kernel-lifecycle/src/service/__tests__/KernelLifecycleService.product-unit-intent.test.ts
    ```
11. Test cases:

    * preview mode returns previewed and does not write registry
    * apply mode writes registry when allowWrite true
    * apply mode blocked when allowWrite false
    * provider mismatch returns blocked
    * platform mode missing provenance/runtimeTruth/events fails closed
    * bootstrap mode records file-backed truth
    * corrupt provider result is rejected
12. Validation:

    ```bash
    pnpm check:kernel-lifecycle-service
    pnpm check:kernel-product-unit-provider-contracts
    pnpm check:kernel-lifecycle-truth
    ```

---

## 1.3 Extend registry provider contract safely

**File:** `platform/typescript/kernel-product-contracts/src/provider/RegistryProvider.ts`

Current file-backed provider already has preview/apply methods, but they are implementation-specific rather than guaranteed by a shared contract.  

Tasks:

1. Add optional shared interface:

   ```ts
   export interface ProductUnitIntentCapableRegistryProvider extends RegistryProvider {
     previewApplyProductUnitIntent(intent: ProductUnitIntent): Promise<ProductUnitIntentPreviewResult>;
     applyProductUnitIntent(
       intent: ProductUnitIntent,
       options: ProductUnitIntentApplyOptions
     ): Promise<ProductUnitIntentApplyResult>;
   }
   ```
2. Move or mirror provider-specific preview/apply result types from `GhatanaFileRegistryProvider.ts` into shared contracts if they are product-neutral.
3. Keep file-provider-specific registry entry types in `kernel-providers`.
4. Export from:

   ```text
   platform/typescript/kernel-product-contracts/src/provider/index.ts
   platform/typescript/kernel-product-contracts/src/index.ts
   ```
5. Add type guard:

   ```ts
   isProductUnitIntentCapableRegistryProvider(value: unknown)
   ```
6. Validation:

   ```bash
   pnpm --dir platform/typescript/kernel-product-contracts typecheck
   pnpm check:kernel-product-unit-provider-contracts
   ```

---

## 1.4 Harden `GhatanaFileRegistryProvider`

**File:** `platform/typescript/kernel-providers/src/registry/GhatanaFileRegistryProvider.ts`

The provider currently loads `config/canonical-product-registry.json`, validates registry entries, converts entries to ProductUnits, previews intent application, and applies intent by atomically writing registry JSON.  

Tasks:

1. Implement the shared ProductUnitIntent-capable provider interface.
2. Return shared application/preview result types.
3. Add provider compatibility validation:

   * registry provider must match `ghatana-file-registry`
   * source provider may be file-backed, GitHub, local path, or external only if a registered `SourceProvider` supports it
4. Do **not** reject `sourceProvider: "github"` blindly if Kernel has a compatible source provider.
5. Add `sourceProvider` compatibility check through Kernel service rather than inside file registry if source ownership is external.
6. Add conflict detection:

   * create when product exists → blocked
   * update when missing → blocked
   * promote-candidate without evidence → blocked
7. Add atomic write lock or per-process queue for registry writes.
8. Preserve registry formatting and schema version.
9. After write, re-read and validate registry.
10. Add event-friendly result refs:

    * `registry://canonical-product-registry/<productUnitId>`
    * `file://config/canonical-product-registry.json`
11. Add tests:

    ```text
    platform/typescript/kernel-providers/src/registry/__tests__/GhatanaFileRegistryProvider.intent-apply.test.ts
    ```
12. Test cases:

    * create preview diff
    * create apply writes file
    * update apply preserves existing non-overwritten fields
    * promote-candidate warns but applies only through explicit mode
    * invalid provider blocked
    * product-specific platform metadata blocked
    * registry revalidation failure rolls back or fails without corrupting file
13. Validation:

    ```bash
    pnpm --dir platform/typescript/kernel-providers test --run
    pnpm check:kernel-product-unit-provider-contracts
    ```

---

## 1.5 Wire YAPPC route to Kernel application

**File:** `products/yappc/frontend/apps/api/src/routes/product-unit-intents.ts`

Tasks:

1. Add route dependency injection options:

   ```ts
   interface ProductUnitIntentRouteOptions {
     readonly kernelLifecycleService?: {
       applyProductUnitIntent(...): Promise<ProductUnitIntentApplicationResult>;
     };
   }
   ```
2. If `mode = "preview"`:

   * call Kernel service preview if available
   * otherwise return `503` instead of pretending success in production
   * allow explicit test/bootstrap fallback only in test setup
3. If `mode = "apply"`:

   * require explicit apply permission
   * call Kernel service apply
   * return Kernel result directly after schema validation
4. Preserve existing validation:

   * authentication
   * ProductUnitIntent schema
   * scope check
   * evidence normalization
   * platform-mode Data Cloud evidence ref requirement
5. Add correlation ID propagation:

   * request header `x-correlation-id`
   * fallback to intent producer correlationId
6. Add structured error response:

   ```ts
   {
     error,
     message,
     correlationId,
     blockedReasons,
     issues
   }
   ```
7. Add audit event hook or provider call if available.
8. Do not write registry files directly in this route.
9. Add tests in:

   ```text
   products/yappc/frontend/apps/api/src/__tests__/product-unit-intents.test.ts
   ```
10. Add test cases:

    * preview invokes Kernel application service
    * apply invokes Kernel application service
    * unavailable Kernel service returns 503
    * Kernel blocked result propagates blocked reasons
    * Kernel failed result returns 409 or 500 based on reason
    * platform mode requires Data Cloud evidence and Kernel service
    * target provider mismatch is not swallowed
11. Validation:

    ```bash
    pnpm check:yappc-product-unit-intent-handoff
    pnpm --dir products/yappc/frontend/apps/api test --run
    ```

---

# Workstream 2 — Kernel lifecycle truth, provider modes, and failure semantics

## Goal

Make Kernel lifecycle truth explicit, auditable, and fail-closed. Bootstrap mode may use file-backed providers. Platform mode must use Data Cloud-backed providers and must not silently fall back to files.

The current bootstrap provider factory creates file-backed events, artifacts, health, approvals, provenance, memory, runtime truth, and optional registry provider under `.kernel/out`.  The current lifecycle provider contract already defines bootstrap/platform modes and required providers. 

---

## 2.1 Make provider mode fail-closed

**File:** `platform/typescript/kernel-lifecycle/src/planning/ProductLifecyclePlanner.ts`

The planner currently tries the provider registry first but falls back to file-based validation if provider loading fails. 

Tasks:

1. Replace provider failure fallback with mode-aware behavior:

   * bootstrap mode: fallback allowed with explicit warning record
   * platform mode: throw `ProviderUnavailableError`
2. Add planner option:

   ```ts
   readonly allowBootstrapFallback?: boolean;
   ```
3. Default fallback:

   * `true` only for bootstrap
   * `false` for platform
4. Do not use `console.warn`; use injected logger or return structured error.
5. Add provider failure reason code:

   * `product-unit-provider-unavailable`
   * `platform-mode-file-fallback-forbidden`
6. Add tests:

   ```text
   platform/typescript/kernel-lifecycle/src/planning/__tests__/ProductLifecyclePlanner.provider-mode.test.ts
   ```
7. Test cases:

   * bootstrap provider failure falls back only when allowed
   * platform provider failure throws
   * platform missing ProductUnit throws
   * bootstrap no provider still loads config from registry
8. Validation:

   ```bash
   pnpm --dir platform/typescript/kernel-lifecycle test --run
   pnpm check:kernel-provider-mode
   ```

---

## 2.2 Strengthen `KernelLifecycleService` provider validation

**File:** `platform/typescript/kernel-lifecycle/src/service/KernelLifecycleService.ts`

Tasks:

1. Add `providerMode` to all relevant public methods:

   * `listProductUnits`
   * `getProductUnit`
   * `createLifecyclePlan`
   * `executeLifecyclePlan`
   * `runLifecyclePhase`
   * `listLifecycleRuns`
   * `getLifecycleRun`
   * `getManifest`
2. For platform mode:

   * require Data Cloud-backed `events`
   * require `artifacts`
   * require `health`
   * require `approvals`
   * require `provenance`
   * require `memory`
   * require `runtimeTruth`
3. Reject a platform-mode run when provider context mode is bootstrap.
4. Record runtime truth for:

   * plan created
   * plan failed
   * execution started
   * execution failed
   * execution succeeded
   * manifest write failed
   * approval required
   * approval rejected
   * verification required
5. Add event append calls where missing:

   * ProductUnitIntent preview/apply
   * plan creation
   * execution start
   * execution complete
   * manifest written
   * provider write failed
6. Replace `readJsonIfExists` silent null behavior with:

   * `readJsonIfExists` only for optional lookups
   * required manifest reads return structured `KernelLifecycleError`
7. Add JSON schema validation for persisted lifecycle-result and lifecycle-plan before returning.
8. Add tests:

   ```text
   platform/typescript/kernel-lifecycle/src/service/__tests__/KernelLifecycleService.provider-mode.test.ts
   platform/typescript/kernel-lifecycle/src/service/__tests__/KernelLifecycleService.manifest-errors.test.ts
   ```
9. Test cases:

   * platform mode missing memory fails
   * bootstrap mode missing memory allowed only where not required
   * corrupt lifecycle-result returns structured error
   * missing required manifest returns `manifest-not-found`
   * runtime truth write failure blocks platform execution
   * runtime truth write failure is visible in bootstrap result
10. Validation:

```bash
pnpm check:kernel-lifecycle-service
pnpm check:kernel-lifecycle-truth
pnpm check:kernel-platform-lifecycle
```

---

## 2.3 Enforce lifecycle provider contract completeness

**File:** `platform/typescript/kernel-product-contracts/src/provider/LifecycleProviders.ts`

The contract already has `KernelProviderModeRequirements` for bootstrap and platform. 

Tasks:

1. Add `events` to bootstrap requirements if not already required for all plan/execution operations.
2. Confirm platform mode requires:

   * events
   * artifacts
   * health
   * approvals
   * provenance
   * memory
   * runtimeTruth
3. Add `providerKind` or `backingStore` metadata:

   ```ts
   readonly backingStore: "file" | "data-cloud" | "external";
   ```
4. Add validation:

   * platform mode cannot use `backingStore: "file"`
   * bootstrap mode can use file providers
5. Add helper:

   ```ts
   validateProviderBackingForMode(context)
   ```
6. Add tests:

   ```text
   platform/typescript/kernel-product-contracts/src/provider/__tests__/LifecycleProviders.test.ts
   ```
7. Validation:

   ```bash
   pnpm --dir platform/typescript/kernel-product-contracts test --run
   pnpm check:kernel-provider-mode
   ```

---

## 2.4 Improve file-backed bootstrap providers

**Files:**

```text
platform/typescript/kernel-providers/src/events/FileLifecycleEventProvider.ts
platform/typescript/kernel-providers/src/artifacts/FileArtifactProvider.ts
platform/typescript/kernel-providers/src/health/FileHealthProvider.ts
platform/typescript/kernel-providers/src/approvals/FileApprovalProvider.ts
platform/typescript/kernel-providers/src/provenance/FileProvenanceProvider.ts
platform/typescript/kernel-providers/src/memory/FileMemoryProvider.ts
platform/typescript/kernel-providers/src/runtime-truth/FileRuntimeTruthProvider.ts
```

The event provider already validates events, checks correlation IDs, appends through a queue, scopes event paths by tenant/workspace/project when scope is present, and warns at high event count. 

Tasks:

1. Add shared file provider base utility for:

   * safe JSON read
   * schema validation
   * atomic write
   * per-file queue
   * scoped path generation
   * retention cleanup
   * redaction hook
2. Do not over-abstract until at least three providers share identical logic.
3. Add privacy/retention metadata support consistently.
4. Implement real retention cleanup instead of success-noop where applicable.
5. Ensure every write returns:

   * stable `ref`
   * `correlationId`
   * provider ID
6. Add provider-level tests for:

   * scoped writes
   * corrupt JSON
   * concurrent writes
   * retention cleanup
   * required vs optional write failure
7. Validation:

   ```bash
   pnpm --dir platform/typescript/kernel-providers test --run
   pnpm check:kernel-provider-mode
   ```

---

# Workstream 3 — Kernel API and Data Cloud gateway integration

## Goal

Make the Kernel lifecycle API reliable, scoped, and consistently exposed through Data Cloud Action Plane / gateway without unsafe fallback.

The gateway already has JWT authentication, tenant mismatch checks, rate limiting, retries, circuit breaker, correlation IDs, traceparent propagation, `/health`, `/ready`, `/metrics`, agentic lifecycle route, and injected Kernel lifecycle routes.   

---

## 3.1 Harden Kernel API handlers

**File:** `platform/typescript/kernel-lifecycle/src/api/KernelLifecycleApiHandlers.ts`

The API handlers exist for ProductUnits, plans, execution, lifecycle runs, manifests, approvals, and approval decisions. 

Tasks:

1. Default `requireScopeHeaders` to `true` in production mode.
2. Add explicit option:

   ```ts
   readonly allowUnscopedLocalDevelopment?: boolean;
   ```
3. Parse and validate:

   * `x-ghatana-tenant-id`
   * `x-ghatana-workspace-id`
   * `x-ghatana-project-id`
   * `x-correlation-id`
4. Validate request body with Zod for:

   * create lifecycle plan
   * execute lifecycle phase
   * request approval
   * submit approval decision
5. Standardize error response:

   ```ts
   {
     error,
     message,
     reasonCode,
     correlationId,
     safeDetails?
   }
   ```
6. Map service errors consistently:

   * `ProductUnitNotFoundError` → 404
   * `ProviderUnavailableError` → 503
   * `ApprovalRequiredError` → 409
   * validation error → 400
   * authz/scope error → 403
7. Add `providerMode` request support.
8. Add `dryRun` support consistently.
9. Add tests:

   ```text
   platform/typescript/kernel-lifecycle/src/api/__tests__/KernelLifecycleApiHandlers.scope.test.ts
   platform/typescript/kernel-lifecycle/src/api/__tests__/KernelLifecycleApiHandlers.errors.test.ts
   ```
10. Validation:

```bash
pnpm check:kernel-api-contracts
```

---

## 3.2 Wire Data Cloud gateway Kernel lifecycle routes to strict handlers

**File:** `products/data-cloud/planes/action/gateway/src/app.ts`

The gateway currently refuses Kernel API fallback when `kernelLifecycleApi` is not injected, which is correct. 

Tasks:

1. Keep fail-closed behavior when `kernelLifecycleApi` is missing.
2. Add schema validation for gateway-to-Kernel handler responses.
3. Add authz metadata extraction from JWT:

   * user ID
   * roles
   * tenant
   * workspace
   * project
4. Pass authz metadata to `KernelLifecycleApiRequest`.
5. Reject requests where:

   * JWT tenant does not match header tenant
   * project/workspace scope is missing for lifecycle operations
6. Add audit log metadata:

   * operation
   * productUnitId
   * phase
   * runId
   * decision
   * status
   * correlationId
7. Add metrics labels:

   * operation
   * statusCode
   * providerMode
   * reasonCode
8. Add tests:

   ```text
   products/data-cloud/planes/action/gateway/src/__tests__/kernel-lifecycle-routes.authz.test.ts
   products/data-cloud/planes/action/gateway/src/__tests__/kernel-lifecycle-routes.errors.test.ts
   ```
9. Validation:

   ```bash
   pnpm --dir products/data-cloud/planes/action/gateway exec vitest run src/__tests__/kernel-lifecycle-routes.test.ts
   pnpm check:kernel-api-contracts
   ```

---

## 3.3 Add provider endpoint backing for Data Cloud Kernel providers

**Files:**

```text
products/data-cloud/planes/action/gateway/src/app.ts
products/data-cloud/libs/kernel-bridge-providers/src/index.ts
products/data-cloud/planes/action/gateway/src/__tests__/*
```

The TS bridge providers currently call these endpoints:

```text
/api/v1/kernel/providers/events
/api/v1/kernel/providers/artifacts
/api/v1/kernel/providers/health
/api/v1/kernel/providers/approvals/requests
/api/v1/kernel/providers/approvals/decisions
/api/v1/kernel/providers/provenance
/api/v1/kernel/providers/memory
/api/v1/kernel/providers/runtime-truth
```

Those clients exist and construct a platform provider context. 

Tasks:

1. Add explicit gateway routes for each provider endpoint, or route them to dedicated Data Cloud plane services.
2. Do not rely on generic `/api/*` reverse proxy for Kernel provider semantics.
3. Validate request body per provider schema.
4. Validate scope headers and JWT tenant.
5. Persist provider records through Data Cloud plane storage abstraction.
6. Return strict provider result:

   ```ts
   { success: true, ref }
   { success: false, error, reasonCode }
   ```
7. Add list endpoints for:

   * events
   * artifacts
   * provenance
   * memory
8. Add latest endpoints for:

   * health
   * runtime truth
9. Add retention/redaction hooks.
10. Add tests:

    ```text
    products/data-cloud/planes/action/gateway/src/__tests__/kernel-provider-events.test.ts
    products/data-cloud/planes/action/gateway/src/__tests__/kernel-provider-runtime-truth.test.ts
    products/data-cloud/planes/action/gateway/src/__tests__/kernel-provider-memory.test.ts
    ```
11. Validation:

    ```bash
    pnpm check:data-cloud-platform-providers
    pnpm check:data-access-contract
    pnpm check:observability-conformance
    ```

---

# Workstream 4 — Data Cloud platform providers

## Goal

Turn the Data Cloud Kernel bridge from a typed client facade into a robust platform provider layer with strict schema validation, privacy metadata, retention, and integration proof.

The current `DataCloudKernelProviderClient` and provider classes exist for events, artifacts, health, approvals, provenance, memory, and runtime truth. They send tenant/workspace/project headers and correlation IDs, support bearer auth, and use timeouts.  Tests already validate provider context construction, scoped headers, event append/list, artifact/runtime truth/memory writes, and required/optional failure behavior. 

---

## 4.1 Replace lightweight type guards with schemas

**File:** `products/data-cloud/libs/kernel-bridge-providers/src/index.ts`

Tasks:

1. Add Zod schemas for:

   * `DataCloudProviderResponse`
   * event list response
   * artifact manifest ref
   * health snapshot ref
   * approval request/decision response
   * provenance record
   * memory record
   * runtime truth snapshot
2. Replace:

   * `isKernelLifecycleEvent`
   * `isArtifactManifestRef`
   * `isHealthSnapshotRef`
   * `isProvenanceRecord`
   * `isMemoryRecord`
   * `isRuntimeTruthSnapshot`
     with schema `safeParse` helpers.
3. When list response has invalid items:

   * do not silently filter invalid records
   * fail the query with a structured validation error
4. Add `privacyClassification` and `retention` validation to all write bodies.
5. Add safe error mapping:

   * HTTP status
   * provider reason code
   * correlation ID
6. Add `x-ghatana-provider-mode: platform` header.
7. Add tests:

   ```text
   products/data-cloud/libs/kernel-bridge-providers/src/__tests__/DataCloudKernelProviders.schemas.test.ts
   ```
8. Test cases:

   * invalid list item fails
   * missing correlation ID is handled
   * provider error includes reasonCode
   * privacy/retention metadata preserved
   * timeout returns provider failure
9. Validation:

   ```bash
   pnpm --dir products/data-cloud/libs/kernel-bridge-providers test
   pnpm --dir products/data-cloud/libs/kernel-bridge-providers typecheck
   ```

---

## 4.2 Add provider observability hooks

**File:** `products/data-cloud/libs/kernel-bridge-providers/src/index.ts`

Tasks:

1. Add optional instrumentation interface:

   ```ts
   interface DataCloudKernelProviderInstrumentation {
     recordRequestStart(...)
     recordRequestComplete(...)
     recordRequestFailure(...)
   }
   ```
2. Track:

   * provider ID
   * operation
   * method
   * path
   * status code
   * duration
   * success/failure
   * reason code
3. Do not log raw body content.
4. Redact:

   * auth token
   * evidence payload fields marked restricted
   * memory content refs if privacy level is restricted
5. Add tests for instrumentation calls.
6. Validation:

   ```bash
   pnpm check:observability-conformance
   pnpm check:secret-default-credentials
   ```

---

## 4.3 Harden Java Data Cloud Kernel bridge

**Files:**

```text
products/data-cloud/extensions/kernel-bridge/src/main/java/com/ghatana/datacloud/kernel/DataCloudKernelProviderSupport.java
products/data-cloud/extensions/kernel-bridge/src/main/java/com/ghatana/datacloud/kernel/DataCloudEventProvider.java
products/data-cloud/extensions/kernel-bridge/src/main/java/com/ghatana/datacloud/kernel/DataCloudRuntimeTruthProvider.java
products/data-cloud/extensions/kernel-bridge/src/main/java/com/ghatana/datacloud/kernel/DataCloudProvenanceProvider.java
products/data-cloud/extensions/kernel-bridge/src/main/java/com/ghatana/datacloud/kernel/DataCloudMemoryProvider.java
products/data-cloud/extensions/kernel-bridge/src/main/java/com/ghatana/datacloud/kernel/DataCloudHealthProvider.java
```

The Java bridge files exist under the correct Data Cloud extension boundary.      

Tasks:

1. Ensure each public Java class has required `@doc.*` tags.
2. Add typed request/response records.
3. Add explicit tenant/workspace/project context propagation.
4. Add failure reason code enum.
5. Add retry/timeout/circuit-breaker behavior only through existing platform Java modules.
6. Add structured logs and metrics.
7. Add tests:

   ```text
   products/data-cloud/extensions/kernel-bridge/src/test/java/com/ghatana/datacloud/kernel/*Test.java
   products/data-cloud/extensions/kernel-bridge/src/test/java/com/ghatana/datacloud/kernel/*IT.java
   ```
8. Test cases:

   * event append/list
   * runtime truth latest
   * provenance record/list
   * memory write/list
   * health latest
   * tenant isolation
   * provider failure reason code
9. Validation:

   ```bash
   ./gradlew :products:data-cloud:extensions:kernel-bridge:check --no-daemon --stacktrace
   ```

---

# Workstream 5 — Digital Marketing end-to-end lifecycle pilot

## Goal

Make Digital Marketing the first complete proof that Studio + Kernel + providers can develop, validate, test, build, package, deploy, verify, observe, and diagnose a ProductUnit.

The Digital Marketing lifecycle config already defines backend/web surfaces, plugins, policy packs, local deployment, approvals, artifacts, gates, Docker Buildx packaging, and verification health checks.  The generated matrix already treats it as the current pilot and identifies platform provider gaps.

---

## 5.1 Complete product lifecycle manifest expectations

**File:** `products/digital-marketing/kernel-product.yaml`

Tasks:

1. Keep lifecycle profile:

   ```yaml
   lifecycleProfile: standard-web-api-product
   ```
2. Add `expectedServices` under deployment config if not already represented by Compose adapter conventions:

   ```yaml
   deployment:
     local:
       expectedServices:
         - backend-api
         - web
   ```
3. Add explicit platform-mode provider requirements:

   ```yaml
   providerModes:
     bootstrap:
       requiredProviders: [events, artifacts, health, approvals, provenance, runtimeTruth]
     platform:
       requiredProviders: [events, artifacts, health, approvals, provenance, memory, runtimeTruth]
   ```
4. Ensure each approval has:

   * action
   * riskLevel
   * required
   * requiredApprovers
   * source
5. Add privacy/security gate references:

   * `marketing-consent-boundary`
   * `non-regulated-customer-data-minimization`
   * `container-image-integrity`
6. Add `retentionPolicyId` for lifecycle evidence if product-local policy exists.
7. Do not add production deploy until local verify is reliable.
8. Validation:

   ```bash
   pnpm check:digital-marketing-lifecycle-pilot
   node ./scripts/kernel-product.mjs product plan digital-marketing verify --env local --json
   ```

---

## 5.2 Harden Compose local deployment

**Files:**

```text
products/digital-marketing/deploy/local.compose.yaml
products/digital-marketing/deploy/local.env.example
products/digital-marketing/deploy/local.env
```

The Compose adapter enforces lifecycle labels for Digital Marketing managed services. It requires `ghatana.kernel.productUnit`, `ghatana.kernel.surface`, and `ghatana.kernel.lifecycle=true` labels. 

Tasks:

1. Ensure every managed service declares:

   ```yaml
   labels:
     ghatana.kernel.productUnit: digital-marketing
     ghatana.kernel.surface: backend-api|web
     ghatana.kernel.lifecycle: "true"
   ```
2. Ensure health checks match `kernel-product.yaml`.
3. Do not use hardcoded secrets in env files.
4. Keep `local.env.example` safe and complete.
5. Keep `local.env` out of source if it contains real values.
6. Add verify check that local Compose services map to ProductUnit surfaces.
7. Validation:

   ```bash
   pnpm deploy:local:digital-marketing
   pnpm verify:local:digital-marketing
   pnpm check:secret-default-credentials
   ```

---

## 5.3 Make Studio drive the pilot

**Files:**

```text
platform/typescript/ghatana-studio/src/routes/DevelopPage.tsx
platform/typescript/ghatana-studio/src/routes/LifecyclePage.tsx
platform/typescript/ghatana-studio/src/routes/ArtifactsPage.tsx
platform/typescript/ghatana-studio/src/routes/DeploymentsPage.tsx
platform/typescript/ghatana-studio/src/routes/HealthPage.tsx
platform/typescript/ghatana-studio/src/data/StudioLifecycleDataContext.tsx
platform/typescript/ghatana-studio/src/api/kernelLifecycleClient.ts
```

The Studio app already has these routes and a Kernel lifecycle client/data context.   

Tasks:

1. Add ProductUnit selector; default to Digital Marketing only if no selection exists.
2. Add phase buttons:

   * Validate
   * Test
   * Build
   * Package
   * Deploy Local
   * Verify Local
3. Add dry-run toggle.
4. Add environment selector.
5. Add provider-mode selector:

   * bootstrap
   * platform
6. Disable platform mode unless Data Cloud provider context is ready.
7. Add lifecycle run list.
8. Add selected run detail.
9. Add manifest tabs:

   * lifecycle plan
   * lifecycle result
   * lifecycle events
   * gate result manifest
   * artifact manifest
   * deployment manifest
   * verify health report
10. Add approval queue panel:

    * pending approvals
    * approvers
    * risk level
    * evidence refs
    * approve/reject actions
11. Add failure diagnostics:

    * reason code
    * failed phase
    * failed step
    * provider mode
    * correlation ID
    * manifest refs
12. Add a11y:

    * keyboard-accessible tabs
    * visible focus
    * `aria-live` for status changes
    * non-color-only statuses
13. Move all hardcoded text into `studioTranslations.ts`.
14. Add tests:

    ```text
    platform/typescript/ghatana-studio/src/routes/__tests__/LifecyclePage.test.tsx
    platform/typescript/ghatana-studio/src/routes/__tests__/DeploymentsPage.test.tsx
    platform/typescript/ghatana-studio/src/data/__tests__/StudioLifecycleDataContext.actions.test.tsx
    ```
15. Validation:

    ```bash
    pnpm check:studio-kernel-api
    pnpm check:studio-e2e-digital-marketing
    pnpm check:design-system-conformance
    ```

---

# Workstream 6 — Ghatana Studio foundation and UX consistency

## Goal

Make Studio simple, coherent, and powerful without turning it into a disconnected dashboard. The shell already has Home, Ideas, Blueprints, Canvas, Develop, Lifecycle, Agents, Artifacts, Deployments, Health, Learn, and Settings routes.  Navigation statuses already identify ready/degraded/empty/blocked route state. 

---

## 6.1 Replace static Lifecycle page with real workflow UI

**File:** `platform/typescript/ghatana-studio/src/routes/LifecyclePage.tsx`

Current page renders static lifecycle steps and placeholder panels. 

Tasks:

1. Remove hardcoded `LIFECYCLE_STEPS` or derive it from selected lifecycle plan.
2. Add real run controls:

   * create plan
   * execute selected phase
   * dry run
   * refresh
   * cancel/retry if supported
3. Add run status strip:

   * productUnitId
   * runId
   * phase
   * status
   * correlationId
   * providerMode
4. Add gate result panel:

   * required gates
   * optional gates
   * passed/failed/skipped
   * evidence refs
5. Add artifact panel:

   * expected artifacts
   * found artifacts
   * missing artifacts
   * fingerprints/digests
6. Add deployment panel:

   * environment
   * services
   * health checks
   * rollback plan
7. Add verification panel:

   * health report
   * checks
   * evidence refs
8. Add approval panel:

   * pending approvals
   * approve/reject buttons
   * disabled state when user lacks permission
9. Add empty/loading/error/degraded/blocked states.
10. Add route-level test coverage.
11. Validation:

    ```bash
    pnpm --dir platform/typescript/ghatana-studio exec vitest run src/routes/__tests__/LifecyclePage.test.tsx
    ```

---

## 6.2 Make Studio data context action-capable

**File:** `platform/typescript/ghatana-studio/src/data/StudioLifecycleDataContext.tsx`

Current provider loads ProductUnits, runs, and manifests but does not expose actions. 

Tasks:

1. Replace single snapshot context with state + actions:

   ```ts
   interface StudioLifecycleDataContextValue {
     readonly snapshot: StudioLifecycleSnapshot;
     readonly selectProductUnit(...): void;
     readonly selectRun(...): void;
     readonly createPlan(...): Promise<void>;
     readonly executePhase(...): Promise<void>;
     readonly requestApproval(...): Promise<void>;
     readonly submitApprovalDecision(...): Promise<void>;
     readonly refresh(...): Promise<void>;
   }
   ```
2. Track:

   * selected productUnitId
   * selected runId
   * selected environment
   * selected providerMode
   * last correlationId
3. Add per-resource loading state:

   * productUnits
   * runs
   * gate manifest
   * artifact manifest
   * deployment manifest
   * health report
4. Add per-resource errors.
5. Avoid failing the whole page when one manifest is missing.
6. Add polling only for running/pending states.
7. Add `AbortController` for in-flight refresh.
8. Add tests for:

   * initial load
   * no ProductUnits
   * partial manifest failure
   * create plan success
   * execute phase success
   * approval decision success
   * degraded Kernel API
9. Validation:

   ```bash
   pnpm --dir platform/typescript/ghatana-studio exec vitest run src/data/__tests__
   ```

---

## 6.3 Complete Studio API client

**File:** `platform/typescript/ghatana-studio/src/api/kernelLifecycleClient.ts`

Current client has typed methods for product units, lifecycle plan, execution, run details, manifests, approval request, and approval decision.  

Tasks:

1. Add `providerMode` to plan and execute requests.
2. Add `surfaceSelector` to plan requests.
3. Add `sourceRef` support if Kernel API supports it.
4. Add query/body validation before sending.
5. Add strict error parser:

   ```ts
   KernelLifecycleApiErrorSchema
   ```
6. Add `ApiClient` response metadata access:

   * correlation ID
   * traceparent
7. Add auth token support without hardcoding auth provider.
8. Ensure headers use canonical casing consistently:

   * `X-Correlation-ID`
   * `X-Ghatana-Tenant-Id`
   * `X-Ghatana-Workspace-Id`
   * `X-Ghatana-Project-Id`
9. Add tests:

   ```text
   platform/typescript/ghatana-studio/src/api/__tests__/kernelLifecycleClient.actions.test.ts
   platform/typescript/ghatana-studio/src/api/__tests__/kernelLifecycleClient.errors.test.ts
   ```
10. Validation:

    ```bash
    pnpm check:studio-kernel-api
    ```

---

## 6.4 Complete i18n coverage

**File:** `platform/typescript/ghatana-studio/src/i18n/studioTranslations.ts`

The translation file exists and already contains many route strings. 

Tasks:

1. Add keys for all new lifecycle actions.
2. Add keys for:

   * provider mode
   * dry run
   * approval states
   * gate result statuses
   * artifact found/missing
   * deployment health
   * verification checks
   * failure reason codes
3. Replace hardcoded route strings in:

   * `LifecyclePage.tsx`
   * `DevelopPage.tsx`
   * `DeploymentsPage.tsx`
   * `HealthPage.tsx`
   * `AgentsPage.tsx`
   * `ArtifactsPage.tsx`
4. Add missing status vocabulary:

   * healthy
   * degraded
   * blocked
   * failed
   * skipped
   * running
   * pending approval
   * requires verification
   * obsolete
   * quarantined
   * unknown
5. Add tests or static check to fail hardcoded customer-facing strings in Studio route files.
6. Validation:

   ```bash
   pnpm check:shared-ui-state-coverage
   pnpm check:design-system-conformance
   ```

---

# Workstream 7 — Agentic lifecycle governance

## Goal

Make agentic product development governed by Kernel/Data Cloud contracts rather than ad hoc injected checks.

The current request contract requires evidence and rollback plan refs and rejects raw command strings.  The current service fails closed by default, plans through Kernel, executes through Kernel, and records provenance/runtime truth/memory when providers exist.  Existing tests verify fail-closed behavior, bootstrap defaults, policy/mastery denial, approval pending/rejection, execution, verification, and raw command rejection. 

---

## 7.1 Add provider-backed governance interfaces

**File:** `platform/typescript/kernel-lifecycle/src/agentic/AgentLifecycleActionService.ts`

Tasks:

1. Replace generic function-only checks with provider interfaces:

   ```ts
   interface AgentPolicyProvider { evaluatePolicy(...): Promise<...> }
   interface AgentMasteryProvider { evaluateMastery(...): Promise<...> }
   interface AgentApprovalProvider { resolveApproval(...): Promise<...> }
   interface AgentVerificationProvider { verifyResult(...): Promise<...> }
   ```
2. Keep function checks only as test adapters.
3. In production config, require provider-backed governance.
4. Add `governanceMode`:

   * `test`
   * `bootstrap`
   * `platform`
5. Platform mode must require:

   * policy provider
   * mastery provider
   * approval provider
   * verification provider
   * provenance provider
   * runtime truth provider
   * memory provider
6. Add failure reason codes:

   * `policy-provider-missing`
   * `mastery-provider-missing`
   * `verification-provider-missing`
   * `approval-provider-missing`
   * `runtime-truth-required`
7. Record runtime truth before and after every decision.
8. Add tests:

   ```text
   platform/typescript/kernel-lifecycle/src/agentic/__tests__/AgentLifecycleActionService.providers.test.ts
   ```
9. Validation:

   ```bash
   pnpm check:agentic-lifecycle-action-contracts
   pnpm check:agentic-lifecycle-e2e
   ```

---

## 7.2 Expand agentic contracts

**Files:**

```text
platform/typescript/kernel-product-contracts/src/agentic/AgentLifecycleActionRequest.ts
platform/typescript/kernel-product-contracts/src/agentic/AgentLifecycleActionResult.ts
```

Tasks:

1. Add `policyEvidenceRefs`.
2. Add `masteryStateRef`.
3. Add `toolPermissionRefs`.
4. Add `approvalTicketRefs`.
5. Add `verificationEvidenceRefs`.
6. Add `privacyClassification`.
7. Add `retention`.
8. Add `modelDecisionContextRef` without raw prompt content.
9. Add `redactionRequired` flag for sensitive contexts.
10. Add validation that:

    * high/critical risk requires approval
    * execute/deploy/promote/rollback requires rollbackPlanRef
    * platform mode requires Data Cloud evidence refs
11. Add tests for each rule.
12. Validation:

    ```bash
    pnpm --dir platform/typescript/kernel-product-contracts test --run
    pnpm check:agentic-lifecycle-action-contracts
    ```

---

## 7.3 Wire gateway to real agentic service

**File:** `products/data-cloud/planes/action/gateway/src/app.ts`

The gateway already exposes `/api/v1/agentic/lifecycle-actions` and returns 503 when the governed service is absent. 

Tasks:

1. Keep fail-closed missing-service behavior.
2. Add request schema validation before calling service.
3. Add authz checks:

   * user can request agentic action for tenant/workspace/project
   * high-risk actions require appropriate role
4. Add metrics:

   * accepted
   * denied
   * requires approval
   * failed
   * execution duration
5. Add audit log event.
6. Add tests:

   ```text
   products/data-cloud/planes/action/gateway/src/__tests__/agentic-lifecycle-actions.authz.test.ts
   products/data-cloud/planes/action/gateway/src/__tests__/agentic-lifecycle-actions.contract.test.ts
   ```
7. Validation:

   ```bash
   pnpm check:agentic-lifecycle-e2e
   ```

---

## 7.4 Complete Studio Agents page

**File:** `platform/typescript/ghatana-studio/src/routes/AgentsPage.tsx`

Tasks:

1. Add typed agent lifecycle client.
2. Render proposal cards:

   * requested action
   * productUnitId
   * phase
   * risk level
   * policy decision
   * mastery decision
   * approval decision
   * required next action
3. Render evidence refs.
4. Render rollback readiness.
5. Add approve/reject controls.
6. Add execute button only after policy/mastery/approval allows.
7. Add blocked/degraded state when governance providers are unavailable.
8. Add tests:

   ```text
   platform/typescript/ghatana-studio/src/routes/__tests__/AgentsPage.test.tsx
   ```
9. Validation:

   ```bash
   pnpm check:studio-kernel-api
   pnpm check:agentic-lifecycle-e2e
   ```

---

# Workstream 8 — Artifact intelligence integration

## Goal

Make YAPPC artifact intelligence produce evidence that Data Cloud stores and Kernel consumes for lifecycle planning, gates, and Studio recommendations.

YAPPC handoff already accepts schema-backed artifact evidence such as semantic artifacts, graph summaries, residual islands, risk hotspots, and generated change sets.  The YAPPC readiness matrix already identifies artifact-intelligence evidence contracts as present while keeping YAPPC lifecycle disabled. 

---

## 8.1 Harden artifact intelligence contracts

**Files:**

```text
platform/typescript/kernel-product-contracts/src/artifact-intelligence/index.ts
platform/typescript/kernel-product-contracts/src/index.ts
```

Tasks:

1. Ensure every evidence type includes:

   * `schemaVersion`
   * `evidenceId`
   * `tenantId`
   * `workspaceId`
   * `projectId`
   * `productUnitId`
   * `createdAt`
   * `createdBy`
   * `correlationId`
   * `confidence`
   * `provenanceRefs`
   * `privacyClassification`
   * `retention`
2. Add `SemanticArtifactEvidenceBundleSchema`.
3. Add `ArtifactIntelligenceEvidenceEnvelopeSchema`.
4. Add evidence type enum:

   * semantic artifact
   * graph summary
   * dependency graph
   * product shape
   * residual island
   * risk hotspot
   * generated change set
5. Add tests:

   ```text
   platform/typescript/kernel-product-contracts/src/artifact-intelligence/__tests__/ArtifactIntelligenceEvidence.test.ts
   ```
6. Validation:

   ```bash
   pnpm check:yappc-artifact-intelligence-boundary
   ```

---

## 8.2 Persist YAPPC evidence through Data Cloud

**File:** `products/yappc/frontend/apps/api/src/routes/product-unit-intents.ts`

Tasks:

1. In platform mode, before Kernel apply:

   * validate evidence bundle
   * write evidence to Data Cloud provider endpoint
   * receive `datacloud://...` refs
   * pass refs into ProductUnitIntent application
2. Reject platform mode if evidence cannot be persisted.
3. In bootstrap mode:

   * allow local evidence refs
   * mark result providerMode as bootstrap
4. Add test:

   * platform evidence write success
   * platform evidence write failure blocks handoff
   * bootstrap local evidence accepted
5. Validation:

   ```bash
   pnpm check:yappc-product-unit-intent-handoff
   pnpm check:data-cloud-platform-providers
   ```

---

## 8.3 Let Kernel planner consume semantic artifact refs

**File:** `platform/typescript/kernel-lifecycle/src/planning/ProductLifecyclePlanner.ts`

Tasks:

1. Add planner option:

   ```ts
   readonly semanticArtifactRefs?: readonly string[];
   ```
2. Add `semanticArtifactRefs` to `ProductLifecyclePlan`.
3. Add gate input support:

   * residual island gate
   * risk hotspot gate
   * dependency graph gate
   * generated change set review gate
4. In platform mode, resolve semantic refs through Data Cloud provider.
5. In bootstrap mode, accept local refs only when explicitly provided.
6. Add tests:

   ```text
   platform/typescript/kernel-lifecycle/src/planning/__tests__/ProductLifecyclePlanner.artifact-intelligence.test.ts
   ```
7. Validation:

   ```bash
   pnpm check:yappc-artifact-intelligence-boundary
   pnpm check:kernel-yappc-boundary
   ```

---

## 8.4 Complete Studio Canvas / Learn evidence views

**Files:**

```text
platform/typescript/ghatana-studio/src/routes/CanvasPage.tsx
platform/typescript/ghatana-studio/src/routes/BlueprintsPage.tsx
platform/typescript/ghatana-studio/src/routes/LearnPage.tsx
```

Tasks:

1. Canvas page:

   * render artifact graph
   * render residual islands
   * render risk hotspots
   * show confidence/provenance
2. Blueprints page:

   * show ProductUnitIntent readiness
   * show target providers
   * show required evidence
   * show Kernel handoff status
3. Learn page:

   * show recommendations
   * show learning evidence refs
   * show “why recommended”
   * show risk reduction impact
4. All pages:

   * no raw AI output without evidence refs
   * no hidden AI decision
   * privacy-aware display
   * i18n keys only
5. Add tests:

   ```text
   platform/typescript/ghatana-studio/src/routes/__tests__/CanvasPage.test.tsx
   platform/typescript/ghatana-studio/src/routes/__tests__/LearnPage.test.tsx
   ```
6. Validation:

   ```bash
   pnpm check:studio-kernel-api
   pnpm check:design-system-conformance
   ```

---

# Workstream 9 — Product shape matrix and future product enablement

## Goal

Make the product-shape matrix the guardrail that prevents premature product enablement while allowing scalable expansion across PHR, Finance, FlashIt, Data Cloud, YAPPC, Aura, DCMAAR, TutorPutor, Audio-Video, and future external ProductUnits.

The generated matrix already captures lifecycle status, readiness dimensions, missing manifests, gates needed, platform-mode readiness, and next actions.   

---

## 9.1 Keep product-shape matrix generated and authoritative

**Files:**

```text
config/generated/product-shape-capability-matrix.json
scripts/generate-product-shape-capability-matrix.mjs
scripts/check-product-shape-capability-matrix.mjs
docs/kernel/PRODUCT_SHAPE_CAPABILITY_MATRIX.md
```

Tasks:

1. Do not manually edit generated JSON.
2. Add readiness dimensions if missing:

   * contract readiness
   * adapter readiness
   * manifest readiness
   * provider-mode readiness
   * privacy/security gate readiness
   * Studio UX readiness
   * E2E readiness
3. Add `minimumReleaseToEnable`.
4. Add `blockingGaps`.
5. Add `nextValidationCommand`.
6. Add `readinessEvidence`.
7. Fail check when a product is `enabled` but has:

   * missing lifecycle profile
   * missing lifecycle config
   * missing required manifests
   * missing adapters
   * missing privacy/security gates
   * missing platform provider support when platform mode is required
8. Validation:

   ```bash
   pnpm generate:product-shape-capability-matrix
   pnpm check:product-shape-capability-matrix
   pnpm check:lifecycle-registry-config-drift
   ```

---

## 9.2 Future product enablement checklist

For each future product, make these changes **only when enabling that product**.

### PHR

Files:

```text
products/phr/**
config/canonical-product-registry.json
products/phr/kernel-product.yaml
```

Tasks:

1. Add lifecycle profile only after executable surfaces are defined.
2. Add healthcare gates:

   * consent
   * PII classification
   * audit evidence
   * FHIR validation
   * tenant isolation
   * data sovereignty
3. Add manifests:

   * lifecycle-plan
   * lifecycle-result
   * artifact-manifest
   * deployment-manifest
   * verify-health-report
4. Do not enable until product-shape matrix passes.

### Finance

Files:

```text
products/finance/**
config/canonical-product-registry.json
products/finance/kernel-product.yaml
```

Tasks:

1. Add operator, portal, SDK, backend surfaces only when adapters exist.
2. Add compliance gates:

   * regulatory evidence
   * report evidence
   * approval
   * audit
   * data access
3. Add multi-module lifecycle profile.
4. Do not enable until adapter support is real.

### FlashIt

Files:

```text
products/flashit/**
products/flashit/kernel-product.yaml
```

Tasks:

1. Add web/mobile/API surfaces.
2. Add mobile adapters only when executable.
3. Add preview security and personal-data privacy gates.
4. Add AAB/IPA/package artifact manifests when build is real.

### Data Cloud

Files:

```text
products/data-cloud/**
products/data-cloud/kernel-product.yaml
products/data-cloud/extensions/kernel-bridge/**
```

Tasks:

1. Keep bootstrap build independent from Data Cloud platform providers.
2. Enable only after Kernel can build/deploy Data Cloud in bootstrap mode.
3. Then enable platform mode provider bridge.
4. Require runtime truth, memory, provenance, health, events.

### YAPPC

Files:

```text
products/yappc/**
products/yappc/kernel-product.yaml
products/yappc/kernel-bridge/**
```

Tasks:

1. Keep YAPPC creator lifecycle separate from Kernel product lifecycle.
2. Add lifecycle config only after ProductUnitIntent and artifact intelligence handoff are stable.
3. Enforce boundary checks so YAPPC does not run raw Kernel commands.

### TutorPutor

Files:

```text
products/tutorputor/**
products/tutorputor/kernel-product.yaml
```

Tasks:

1. Add learner-data privacy gates.
2. Add content-safety gates.
3. Add model-output evaluation evidence.
4. Add manifests before enablement.

Validation for all future products:

```bash
pnpm check:product-shape-capability-matrix
pnpm check:lifecycle-registry-config-drift
pnpm check:kernel-platform-lifecycle
```

---

# Workstream 10 — CI/CD and workflow hardening

## Goal

Make CI enforce the new foundation without creating slow, flaky PR workflows.

The existing lifecycle workflow builds Kernel packages, tests Kernel packages, checks contracts, checks Digital Marketing pilot, uploads lifecycle artifacts, and scans for disabled tests/TODOs in lifecycle production code. 

---

## 10.1 Extend lifecycle workflow safely

**File:** `.github/workflows/product-lifecycle.yml`

Tasks:

1. Keep PR jobs:

   * build Kernel
   * test Kernel
   * check contracts
   * Digital Marketing smoke
   * guardrail scan
2. Add PR job:

   * Studio lifecycle UI unit tests
   * YAPPC ProductUnitIntent durable handoff tests
   * Data Cloud provider schema tests
3. Add nightly or manual workflow for full local lifecycle:

   * package
   * deploy local
   * verify local
   * upload all manifests
4. Do not run Docker/Compose full deploy on every PR unless GitHub runner support is proven stable.
5. Add artifact upload for:

   * `.kernel/out/**`
   * generated product-shape matrix
   * lifecycle logs
   * Studio E2E traces
6. Add workflow-level concurrency for new jobs.
7. Validation:

   ```bash
   pnpm check:kernel-product-boundary-audit
   ```

---

## 10.2 Add new checks only when real

**Files:**

```text
scripts/check-yappc-product-unit-intent-handoff.mjs
scripts/check-data-cloud-platform-providers.mjs
scripts/check-kernel-provider-mode.mjs
scripts/check-studio-kernel-api.mjs
scripts/check-product-shape-capability-matrix.mjs
```

Tasks:

1. Extend existing checks instead of creating duplicates.
2. Add assertions:

   * YAPPC route must call Kernel service in apply mode.
   * Platform mode must use Data Cloud-backed providers.
   * Studio must not render lifecycle execute buttons without Kernel client.
   * Product enabled state must match matrix readiness.
   * ProductUnitIntent apply must produce application result schema.
3. Add tests for scripts where practical.
4. Validation:

   ```bash
   pnpm check:kernel-product-boundary-audit
   pnpm check:production-readiness
   ```

---

# Workstream 11 — Documentation and cleanup

## Goal

Remove stale, duplicated, or misleading docs and make one source of truth obvious.

The repo still contains Kernel TODO docs and YAPPC audit TODO docs that can become stale or compete with canonical truth.   

---

## 11.1 Consolidate Kernel docs

**Files:**

```text
docs/kernel/01-ARCHITECTURE.md
docs/kernel/03-TOOLCHAIN_ADAPTERS.md
docs/kernel/04-ARTIFACTS.md
docs/kernel/12-GENERIC_PLATFORM_MODEL.md
docs/kernel/CAPABILITY_MATRIX.md
docs/kernel/MONOREPO_ARCHITECTURE.md
docs/kernel/KERNEL-TODOS.md
docs/kernel/KERNEL-TODOS-REMAINING.md
```

Tasks:

1. Mark exactly one Kernel architecture doc as authoritative.
2. Move remaining details into:

   * architecture
   * lifecycle execution
   * provider modes
   * toolchains
   * product-shape readiness
3. Delete TODO docs or convert them to issue-linked implementation tracker.
4. Remove target-state claims unless classified.
5. Ensure docs match generated matrix.
6. Validation:

   ```bash
   pnpm check:doc-truth
   pnpm check:doc-claims-evidence
   pnpm check:product-doc-taxonomy
   ```

---

## 11.2 Consolidate YAPPC docs

**Files:**

```text
products/yappc/docs/architecture/KERNEL_VISIBILITY_AND_CONTROL_PLANE.md
products/yappc/docs/audits/yappc-todos.md
products/yappc/docs/archive/**
products/yappc/docs/agent-list.md
```

Tasks:

1. Keep architecture docs that define current boundaries.
2. Move audit TODOs into implementation tracker or issue references.
3. Ensure archived docs are not referenced as current truth.
4. Add current-state labels:

   * existing and executable
   * existing but partial
   * target architecture
   * deprecated
5. Validation:

   ```bash
   pnpm check:doc-truth
   pnpm check:yappc-artifact-intelligence-boundary
   ```

---

## 11.3 Cleanup encoding and generated drift

**Files:**

```text
platform/typescript/kernel-toolchains/src/ToolchainAdapterRegistry.ts
platform/typescript/kernel-lifecycle/src/planning/ProductLifecyclePlanner.ts
platform/typescript/kernel-lifecycle/src/execution/ProductLifecycleExecutor.ts
```

Some comments in these files show encoding artifacts.  

Tasks:

1. Re-save as UTF-8.
2. Replace corrupted separator comments with normal ASCII comments.
3. Do not change logic in the same cleanup commit unless required by nearby test failure.
4. Validation:

   ```bash
   pnpm --dir platform/typescript/kernel-lifecycle typecheck
   pnpm --dir platform/typescript/kernel-toolchains typecheck
   pnpm format
   ```

---

# Implementation sequence

## Phase 1 — Contract and provider-mode foundation

Implement:

1. ProductUnitIntent application result contract.
2. Registry provider capability interface.
3. Kernel service `applyProductUnitIntent`.
4. Platform-mode fail-closed planner/service changes.
5. Data Cloud provider schemas.

Validate:

```bash
pnpm build:kernel-lifecycle-platform
pnpm check:kernel-lifecycle-service
pnpm check:kernel-provider-mode
pnpm check:kernel-product-unit-provider-contracts
pnpm check:data-cloud-platform-providers
```

---

## Phase 2 — Durable YAPPC handoff

Implement:

1. YAPPC route dependency injection for Kernel service.
2. Preview/apply wiring.
3. Data Cloud evidence persistence in platform mode.
4. Route tests for durable apply.

Validate:

```bash
pnpm check:yappc-product-unit-intent-handoff
pnpm check:yappc-artifact-intelligence-boundary
pnpm check:kernel-yappc-boundary
```

---

## Phase 3 — Studio action workflow

Implement:

1. Action-capable Studio data context.
2. Kernel lifecycle client action options.
3. Lifecycle page real plan/execute/approval/manifest UI.
4. Develop/Artifacts/Deployments/Health page truth views.
5. i18n cleanup.

Validate:

```bash
pnpm check:studio-kernel-api
pnpm check:studio-e2e-digital-marketing
pnpm check:design-system-conformance
pnpm check:shared-ui-state-coverage
```

---

## Phase 4 — Digital Marketing full pilot

Implement:

1. Confirm `kernel-product.yaml` manifest/provider expectations.
2. Confirm Compose labels/health.
3. Full Studio-driven lifecycle pilot.
4. Optional nightly full local execute workflow.

Validate:

```bash
pnpm check:digital-marketing-lifecycle-pilot
pnpm check:digital-marketing-lifecycle-pilot --smoke
pnpm validate:digital-marketing
pnpm test:digital-marketing
pnpm build:digital-marketing
pnpm package:digital-marketing
pnpm deploy:local:digital-marketing
pnpm verify:local:digital-marketing
```

---

## Phase 5 — Agentic lifecycle governance

Implement:

1. Provider-backed policy/mastery/approval/verification.
2. Gateway route authz/metrics.
3. Studio Agents page.
4. Data Cloud evidence/memory integration.

Validate:

```bash
pnpm check:agentic-lifecycle-action-contracts
pnpm check:agentic-lifecycle-e2e
pnpm check:data-cloud-platform-providers
```

---

## Phase 6 — Future product readiness

Implement only readiness checks and non-executable declarations until each product is proven.

Validate:

```bash
pnpm generate:product-shape-capability-matrix
pnpm check:product-shape-capability-matrix
pnpm check:lifecycle-registry-config-drift
pnpm check:kernel-platform-lifecycle
```

---

# Final validation suite

Run after each major phase:

```bash
pnpm build
pnpm test
pnpm typecheck
pnpm build:platform
pnpm build:kernel-lifecycle-platform
pnpm check:kernel-product-boundary-audit
pnpm check:kernel-platform-lifecycle
pnpm check:digital-marketing-lifecycle-pilot
pnpm check:product-shape-capability-matrix
pnpm check:lifecycle-registry-config-drift
pnpm check:design-system-conformance
pnpm check:shared-product-shells
pnpm check:shared-layout-primitives
pnpm check:shared-ui-state-coverage
pnpm check:production-readiness
pnpm check:secret-default-credentials
pnpm check:observability-conformance
pnpm check:data-access-contract
./gradlew build
./gradlew check
```

Use this rule for merge readiness:

```text
No workstream is done until the implementation is contract-backed, provider-mode aware,
scope-aware, observable, tested at the right level, i18n/a11y ready where UI exists,
and reflected in the product-shape readiness matrix when it affects product enablement.
```
