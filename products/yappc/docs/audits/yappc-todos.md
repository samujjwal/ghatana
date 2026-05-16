Below is a **granular, file-by-file implementation plan** grounded in the current `samujjwal/ghatana` repo state at commit `43da69dfe38815c41e385c67b193db94eeaba227`.

The plan follows the repo rules: reuse before creating, keep platform/product boundaries explicit, avoid silent failures, keep TypeScript strict, make tests part of every meaningful change, and make observability first-class.  It also follows the hardened blueprint’s ownership model: Kernel owns lifecycle truth, YAPPC owns creation/intelligence, Data Cloud owns runtime truth/provenance/memory, Studio owns unified UX, products own domain behavior, and shared libraries remain product-neutral. 

---

# Implementation Plan: Ghatana World-Class Product Development Platform

## Execution Order

Implement in this order to avoid rework:

1. **Foundation safety and truth hardening**
2. **Kernel lifecycle/API correctness**
3. **Toolchain and artifact contract hardening**
4. **Data Cloud platform-mode provider contracts**
5. **YAPPC ProductUnitIntent and artifact-intelligence handoff**
6. **Ghatana Studio unified UX**
7. **Digital Marketing lifecycle pilot completion**
8. **Future product shape readiness**
9. **CI/CD, governance, cleanup, and regression gates**

---

# 1. Platform Coherence & Governance Foundation

The repo already has a strong governance base: `config/domain-registry.json` defines domains, classifications, primary locations, source-of-truth files, required checks, forbidden dependencies, and reason codes.  The root `package.json` already contains many relevant checks, including domain boundaries, deprecated imports, current-state claims, Kernel provider mode, Data Cloud providers, product manifests, security, observability, UI conformance, and cleanup gates. 

## 1.1 `package.json`

### Current issue

Some important checks exist as scripts or nested checks but are not consistently exposed as direct root commands. For example, `check-kernel-platform-lifecycle.mjs` runs `check-digital-marketing-lifecycle-pilot.mjs`, `check-product-shape-capability-matrix.mjs`, and `check-lifecycle-registry-config-drift.mjs`, but direct root aliases should exist for every core validation command so the implementation plan and CI command suite are stable. 

### Tasks

Add these root scripts:

```json
{
  "check:digital-marketing-lifecycle-pilot": "node ./scripts/check-digital-marketing-lifecycle-pilot.mjs",
  "check:product-shape-capability-matrix": "node ./scripts/check-product-shape-capability-matrix.mjs",
  "check:lifecycle-registry-config-drift": "node ./scripts/check-lifecycle-registry-config-drift.mjs",
  "check:toolchain-adapter-contracts": "node ./scripts/check-toolchain-adapter-contracts.mjs",
  "check:toolchain-adapter-registry-schema": "node ./scripts/check-toolchain-adapter-registry-schema.mjs",
  "check:product-artifact-contracts": "node ./scripts/check-product-artifact-contracts.mjs",
  "check:product-deployment-contracts": "node ./scripts/check-product-deployment-contracts.mjs",
  "check:product-environment-contracts": "node ./scripts/check-product-environment-contracts.mjs"
}
```

### Validation

```bash
pnpm check:kernel-platform-lifecycle
pnpm check:digital-marketing-lifecycle-pilot
pnpm check:product-shape-capability-matrix
pnpm check:lifecycle-registry-config-drift
```

---

## 1.2 `config/domain-registry.json`

### Current issue

The domain registry is strong, but some classifications appear overly optimistic. For example, `product-development-kernel-lifecycle` and `toolchain-adapter-runtime` are marked `existing-executable`, while important adapters and execution details are still partial. 

### Tasks

Update classifications to be more precise:

| Domain                                 |               Current |                                                                           Target |
| -------------------------------------- | --------------------: | -------------------------------------------------------------------------------: |
| `product-development-kernel-lifecycle` | `existing-executable` |             `existing-partial` until API authz + read-truth diagnostics are done |
| `toolchain-adapter-runtime`            | `existing-executable` |      `existing-partial` until mobile adapters and output validation are hardened |
| `security-privacy-policy-compliance`   | `existing-executable` |  keep executable for base platform, but add reason code for Kernel API authz gap |
| `observability-health-operations`      | `existing-executable` | keep executable, but add reason code for Studio/UI runtime health visibility gap |

Add these reason codes:

```json
[
  "kernel-api-authz-partial",
  "lifecycle-read-truth-diagnostics-partial",
  "mobile-adapters-output-validation-partial",
  "studio-runtime-truth-visibility-partial"
]
```

### Validation

```bash
pnpm check:domain-registry
pnpm check:architecture-boundaries
pnpm check:current-state-claims
```

---

## 1.3 `docs/architecture/DOMAIN_WORKSTREAM_MAP.md`

### Current issue

The uploaded workstream map correctly identifies the Platform Coherence & Governance domain as necessary to prevent a god product, duplicate ownership, stale target-state claims, and repeated TODO churn. 

### Tasks

Update the repo document to reflect this implementation plan:

1. Add a section: **Implementation Order and Exit Gates**.
2. Add explicit “do not enable lifecycle yet” statements for:

   * PHR
   * Finance
   * FlashIt
   * Data Cloud
   * YAPPC
   * TutorPutor
   * DCMAAR
   * Aura
3. Add a “fake success prevention” rule:

   * no apply without Kernel
   * no valid artifact output without artifact discovery
   * no platform mode without Data Cloud-backed provider context
4. Link every domain to the direct root validation command added in `package.json`.

### Validation

```bash
pnpm check:doc-truth
pnpm check:doc-claims-evidence
pnpm check:current-state-claims
```

---

# 2. Kernel Lifecycle and API Correctness

Kernel already has real contracts and lifecycle service exports. `@ghatana/kernel-product-contracts` exports ProductUnit, ProductUnitIntent, provider, event, health, plugin, artifact-intelligence, and agentic contracts.  `@ghatana/kernel-lifecycle` exports planner, executor, API handlers, provider context validation, manifest writer, gate executor, service, IO writers, and validators. 

The work here is not to invent Kernel. It is to harden it.

---

## 2.1 `platform/typescript/kernel-lifecycle/src/service/KernelLifecycleService.ts`

### Current issue

`KernelLifecycleService` creates plans, executes lifecycle phases, records runtime truth/provenance, handles approvals, applies ProductUnitIntent, and validates provider mode.  However, lifecycle read paths can silently degrade. `listPhaseDirectories` catches all errors and returns `[]`; `readJsonIfExists` catches all errors and returns `null`. 

### Target behavior

Do not hide lifecycle truth corruption. Missing files are acceptable; unreadable or malformed truth must be observable and testable.

### Tasks

#### Add error helpers

Add private helpers:

```ts
private isNodeFileNotFound(error: unknown): boolean
private isJsonParseError(error: unknown): boolean
private lifecycleTruthReadError(...)
```

#### Replace broad catches

Change `listPhaseDirectories`:

* Return `[]` only for `ENOENT`.
* Throw `KernelLifecycleError` for:

  * permission errors
  * invalid path state
  * unexpected fs failures

Use reason codes:

```ts
'lifecycle-run-index-unavailable'
'lifecycle-truth-read-failed'
```

Change `readJsonIfExists`:

* Return `null` only for `ENOENT`.
* Throw `KernelLifecycleError` for malformed JSON.

Use reason code:

```ts
'lifecycle-manifest-corrupt'
```

#### Add structured logs

When read failure occurs, call:

```ts
this.logger.error('Lifecycle truth read failed', {
  reasonCode,
  productUnitId,
  phase,
  runId,
  filePath,
});
```

#### Add safe response details

Include only safe fields:

```ts
safeDetails: {
  filePath,
  operation,
  reasonCode
}
```

### Tests

Create or update:

```text
platform/typescript/kernel-lifecycle/src/service/__tests__/KernelLifecycleService.test.ts
```

Add tests:

1. missing run directory returns empty list
2. unreadable directory throws `KernelLifecycleError`
3. malformed `lifecycle-result.json` throws `lifecycle-manifest-corrupt`
4. malformed `lifecycle-plan.json` throws `lifecycle-manifest-corrupt`
5. logger receives reason code and file path
6. correlation ID is preserved where available

### Validation

```bash
pnpm --dir platform/typescript/kernel-lifecycle test
pnpm --dir platform/typescript/kernel-lifecycle typecheck
pnpm check:kernel-lifecycle-truth
```

---

## 2.2 `platform/typescript/kernel-lifecycle/src/service/KernelLifecycleErrors.ts`

### Current issue

The service already uses `KernelLifecycleError`, `ProviderUnavailableError`, `ProductUnitNotFoundError`, and related typed errors.  New lifecycle truth diagnostics should not be string-only ad hoc errors.

### Tasks

Add specific error classes or reason-code helpers:

```ts
LifecycleTruthReadError
LifecycleManifestCorruptError
LifecycleRunIndexUnavailableError
```

Each must include:

```ts
reasonCode
message
correlationId
productUnitId
runId
phase
safeDetails
```

Do not expose raw stack traces or internal fs errors in API-safe responses.

### Tests

Add tests for `.toSafeResponse()`.

### Validation

```bash
pnpm --dir platform/typescript/kernel-lifecycle test
```

---

## 2.3 `platform/typescript/kernel-lifecycle/src/api/KernelLifecycleApiHandlers.ts`

### Current issue

The API handler validates params, query, body, scope headers, and returns correlation IDs. It maps reason codes to status codes.  But it does not enforce full authentication and authorization. Scope headers are not enough.

### Target behavior

Every Kernel API operation must enforce an authorization decision before calling the service.

### Tasks

#### Add interfaces

Add:

```ts
export interface KernelLifecycleActor {
  readonly actorId: string;
  readonly tenantId?: string;
  readonly workspaceId?: string;
  readonly projectId?: string;
  readonly roles: readonly string[];
  readonly capabilities: readonly string[];
}

export interface KernelLifecycleAuthorizer {
  authenticate(request: KernelLifecycleApiRequest): Promise<KernelLifecycleActor | null>;

  authorizeProductUnitRead(actor, context): Promise<boolean>;
  authorizeLifecyclePlan(actor, context): Promise<boolean>;
  authorizeLifecycleExecute(actor, context): Promise<boolean>;
  authorizeManifestRead(actor, context): Promise<boolean>;
  authorizeApprovalRequest(actor, context): Promise<boolean>;
  authorizeApprovalDecision(actor, context): Promise<boolean>;
}
```

#### Extend handler options

```ts
readonly authorizer?: KernelLifecycleAuthorizer;
readonly requireAuthentication?: boolean;
```

Default:

```ts
requireAuthentication: true
```

Only allow unauthenticated behavior when:

```ts
allowUnscopedLocalDevelopment === true
```

#### Apply authorization per route

| Handler                  | Required authorization      |
| ------------------------ | --------------------------- |
| `listProductUnits`       | `authorizeProductUnitRead`  |
| `getProductUnit`         | `authorizeProductUnitRead`  |
| `createLifecyclePlan`    | `authorizeLifecyclePlan`    |
| `executeLifecyclePhase`  | `authorizeLifecycleExecute` |
| `listLifecycleRuns`      | `authorizeProductUnitRead`  |
| `getLifecycleRun`        | `authorizeProductUnitRead`  |
| manifest handlers        | `authorizeManifestRead`     |
| `requestApproval`        | `authorizeApprovalRequest`  |
| `submitApprovalDecision` | `authorizeApprovalDecision` |

#### Add reason codes

```ts
'authentication-required'
'authorization-failed'
```

Map:

```ts
authentication-required -> 401
authorization-failed -> 403
```

### Tests

Create or update:

```text
platform/typescript/kernel-lifecycle/src/api/__tests__/KernelLifecycleApiHandlers.test.ts
```

Add tests:

1. missing actor returns 401
2. denied lifecycle execute returns 403
3. allowed plan calls service
4. allowed manifest read returns response
5. local dev mode bypass works only when explicitly configured
6. all error responses include `x-correlation-id`
7. scope mismatch remains 403

### Validation

```bash
pnpm --dir platform/typescript/kernel-lifecycle test
pnpm check:studio-kernel-api
pnpm check:route-entitlement-contracts
pnpm check:security-workflow-coverage
```

---

## 2.4 `platform/typescript/kernel-lifecycle/src/api/__tests__/KernelLifecycleApiHandlers.test.ts`

### Tasks

Add focused tests with real handler subject under test. Do not use object-literal-only tests. The repo explicitly forbids test theater and requires tests to exercise production code. 

Test matrix:

| Case                             | Expected       |
| -------------------------------- | -------------- |
| no auth header, auth required    | 401            |
| no scope headers, scope required | 403            |
| valid scope, denied action       | 403            |
| valid actor and scope            | service called |
| invalid phase                    | 400            |
| provider unavailable             | 503            |
| missing product unit             | 404            |
| manifest missing                 | 404            |
| approval required                | 409            |

---

# 3. Toolchain Adapter and Execution Runtime Hardening

The toolchain package exports core adapter contracts, registry, output validator, command runners, and many adapters.  The safer `SpawnCommandRunner` already supports `spawn` with `shell: false`, timeouts, cancellation, process tree termination, capped output, and redaction.  All adapters should use that pattern.

---

## 3.1 `platform/typescript/kernel-toolchains/src/adapters/XcodeIosAdapter.ts`

### Current issue

The adapter uses `execAsync(step.command.join(' '))`, validates outputs as always valid, and returns no artifact paths.  This is not production-ready.

### Target behavior

Until fully implemented, the adapter must fail closed as `adapter-not-ready`. When implemented, it must use `CommandRunner`, validate Xcode availability, and prove `.app`/`.ipa` outputs.

### Tasks

1. Remove `promisify(require('node:child_process').exec)`.
2. Inject or create `CommandRunner`.
3. Use command and args separately:

   ```ts
   runner.run('xcodebuild', ['-project', project, '-scheme', scheme, ...], options)
   ```
4. Add preflight:

   * platform is `darwin`
   * `xcodebuild` available
   * configured project/workspace exists
   * scheme declared
5. Add output validation:

   * `.app` for build
   * `.ipa` for package
6. If required config is missing:

   * return failed result
   * reason code `adapter-not-ready`
   * no fake artifacts
7. Add `validateOutputs` with real file checks.
8. Add redaction for env and command output.

### Tests

Create:

```text
platform/typescript/kernel-toolchains/src/adapters/__tests__/XcodeIosAdapter.test.ts
```

Test:

1. dry run returns skipped with no artifact success claim
2. non-darwin returns blocked/failed with `adapter-not-ready`
3. missing project returns failed
4. output validator fails when `.ipa` missing
5. command runner receives command/args separately
6. stdout/stderr are capped/redacted

### Validation

```bash
pnpm --dir platform/typescript/kernel-toolchains test
pnpm check:toolchain-adapter-contracts
```

---

## 3.2 `platform/typescript/kernel-toolchains/src/adapters/GradleAndroidAdapter.ts`

### Current issue

Same class of issue as iOS: `execAsync(step.command.join(' '))`, always-valid output validation, no real artifact extraction. 

### Tasks

1. Replace `execAsync` with `CommandRunner`.
2. Validate:

   * Gradle wrapper exists
   * Android module is configured
   * task is valid for phase
   * Android SDK requirements are declared
3. Support:

   * `assembleRelease`
   * `bundleRelease`
   * test task mapping
4. Validate outputs:

   * `.apk`
   * `.aab`
5. Add explicit result reason codes:

   * `android-gradle-wrapper-missing`
   * `android-sdk-unavailable`
   * `android-artifact-missing`
   * `adapter-not-ready`
6. Never return `valid` if artifacts are empty for build/package.

### Tests

Create:

```text
platform/typescript/kernel-toolchains/src/adapters/__tests__/GradleAndroidAdapter.test.ts
```

Test:

1. dry run
2. missing Gradle wrapper
3. missing output artifact
4. valid `.aab`
5. failed command result maps to adapter failure
6. no shell expansion

### Validation

```bash
pnpm --dir platform/typescript/kernel-toolchains test
pnpm check:toolchain-adapter-contracts
```

---

## 3.3 `platform/typescript/kernel-toolchains/src/index.ts`

### Current issue

Incomplete mobile/cloud adapters are exported as if they are generally usable. 

### Tasks

After hardening adapters, keep exports. Before hardening, ensure registry marks incomplete adapters as disabled or experimental. Do not remove exports if tests/imports depend on them; instead make execution fail closed.

### Validation

```bash
pnpm --dir platform/typescript/kernel-toolchains typecheck
pnpm check:toolchain-adapter-registry-schema
```

---

# 4. Artifact, Deployment, Release, and Provenance Contracts

The artifact manifest already includes schema version, product/run identifiers, provider mode, phase, generatedBy, timestamp, artifacts, metadata, fingerprint, expected/found, and deploymentRefs.  It needs stronger supply-chain and provenance fields.

---

## 4.1 `platform/typescript/kernel-artifacts/src/domain/ArtifactManifest.ts`

### Current issue

The schema has fingerprints and basic deployment refs, but does not make SBOM, signing, attestations, retention, trust state, and provenance first-class.

### Tasks

Add types:

```ts
export type ArtifactTrustState =
  | 'trusted'
  | 'unverified'
  | 'failed'
  | 'revoked'
  | 'quarantined';

export interface ArtifactSbomRef {
  readonly format: 'cyclonedx-json' | 'spdx-json';
  readonly ref: string;
  readonly digest?: ArtifactFingerprint;
}

export interface ArtifactSignature {
  readonly algorithm: string;
  readonly signatureRef: string;
  readonly keyRef?: string;
}

export interface ArtifactAttestation {
  readonly predicateType: string;
  readonly attestationRef: string;
  readonly issuer?: string;
}

export interface ArtifactRetention {
  readonly policyId: string;
  readonly expiresAt?: string;
  readonly legalHold?: boolean;
}
```

Extend `ArtifactMetadata` or `ArtifactEntry`:

```ts
trustState?: ArtifactTrustState;
sbomRefs?: readonly ArtifactSbomRef[];
signature?: ArtifactSignature;
attestations?: readonly ArtifactAttestation[];
provenanceRefs?: readonly string[];
retention?: ArtifactRetention;
```

Update `ArtifactManifestSchema` with Zod equivalents.

### Tests

Create or update:

```text
platform/typescript/kernel-artifacts/src/domain/__tests__/ArtifactManifest.test.ts
```

Test:

1. existing 1.0 manifest remains valid
2. manifest with SBOM/signature is valid
3. invalid trust state fails
4. invalid digest fails
5. missing required policy-gated supply-chain evidence fails when validator requires it

### Validation

```bash
pnpm --dir platform/typescript/kernel-artifacts test
pnpm check:product-artifact-contracts
```

---

## 4.2 `platform/typescript/kernel-artifacts/src/validator/ProductArtifactValidator.ts`

### Tasks

Add policy-aware validation:

```ts
validateManifest(manifest, {
  requireSbom,
  requireSignature,
  requireProvenance,
  requireDeploymentLinkage
})
```

Reason codes:

```ts
'artifact-sbom-missing'
'artifact-signature-missing'
'artifact-provenance-missing'
'artifact-deployment-linkage-missing'
```

### Validation

```bash
pnpm --dir platform/typescript/kernel-artifacts test
```

---

## 4.3 `platform/typescript/kernel-deployment/src/**`

### Tasks

Grounded by the existing package and root checks, harden deployment manifest validation:

1. Require artifact refs for every deployed surface.
2. Require environment safety classification.
3. Require rollback strategy.
4. Require health check refs.
5. Require approval refs for risky environments.

### Validation

```bash
pnpm --dir platform/typescript/kernel-deployment test
pnpm check:product-deployment-contracts
pnpm check:product-environment-contracts
```

---

## 4.4 `platform/typescript/kernel-release/src/**`

### Tasks

Harden release/promotion/rollback:

1. Promotion plan must reference artifact manifest and deployment manifest.
2. Rollback plan must reference previous deployment manifest.
3. Approval gate must include approver roles and evidence refs.
4. Release health must include diagnostics and correlation ID.

### Validation

```bash
pnpm --dir platform/typescript/kernel-release test
```

---

# 5. Data Cloud Platform-Mode Provider Contracts

The Data Cloud bridge provider package exists and exposes a platform-mode provider context with events, artifacts, health, approvals, provenance, memory, and runtime truth providers.   The registry correctly marks Data Cloud as a platform-provider product with conformance currently false/partial and bootstrap/platform-mode requirements. 

---

## 5.1 `products/data-cloud/libs/kernel-bridge-providers/package.json`

### Current issue

This package uses local version pins for TypeScript/Vitest instead of root catalog alignment. 

### Tasks

Change:

```json
"@types/node": "^22.10.2",
"typescript": "^5.6.3",
"vitest": "^4.1.5"
```

To catalog versions:

```json
"@types/node": "catalog:",
"typescript": "catalog:",
"vitest": "catalog:"
```

Keep `@ghatana/kernel-product-contracts` as `workspace:*`.

### Validation

```bash
pnpm --dir products/data-cloud/libs/kernel-bridge-providers typecheck
pnpm check:cross-workspace-deps
pnpm check:product-package-metadata
```

---

## 5.2 `products/data-cloud/libs/kernel-bridge-providers/src/index.ts`

### Current issue

The provider client has useful instrumentation and redaction, but schemas are shallow. For example, `KernelLifecycleEventSchema` only checks `metadata.eventId`, and runtime truth only checks `productUnitId` and `observedAt`.  Provider classes write to `/api/v1/kernel/providers/*`, but endpoint contracts are not strongly validated. 

### Tasks

#### Split file into focused modules

Create:

```text
products/data-cloud/libs/kernel-bridge-providers/src/client/DataCloudKernelProviderClient.ts
products/data-cloud/libs/kernel-bridge-providers/src/client/DataCloudKernelProviderInstrumentation.ts
products/data-cloud/libs/kernel-bridge-providers/src/schemas/providerResponses.ts
products/data-cloud/libs/kernel-bridge-providers/src/schemas/lifecycleProviderPayloads.ts
products/data-cloud/libs/kernel-bridge-providers/src/providers/DataCloudLifecycleEventProvider.ts
products/data-cloud/libs/kernel-bridge-providers/src/providers/DataCloudArtifactProvider.ts
products/data-cloud/libs/kernel-bridge-providers/src/providers/DataCloudHealthProvider.ts
products/data-cloud/libs/kernel-bridge-providers/src/providers/DataCloudApprovalProvider.ts
products/data-cloud/libs/kernel-bridge-providers/src/providers/DataCloudProvenanceProvider.ts
products/data-cloud/libs/kernel-bridge-providers/src/providers/DataCloudMemoryProvider.ts
products/data-cloud/libs/kernel-bridge-providers/src/providers/DataCloudRuntimeTruthProvider.ts
products/data-cloud/libs/kernel-bridge-providers/src/createDataCloudKernelProviderContext.ts
```

Keep `src/index.ts` as a clean export barrel only.

#### Strengthen schemas

Use actual Kernel contracts from `@ghatana/kernel-product-contracts` where possible:

```ts
KernelLifecycleEventSchema
LifecycleArtifactManifestRefSchema
LifecycleHealthSnapshotRefSchema
LifecycleProvenanceRecordSchema
LifecycleMemoryRecordSchema
LifecycleRuntimeTruthSnapshotSchema
```

If not currently exported, add exports in `kernel-product-contracts`.

#### Strengthen response validation

Provider write response must include:

```ts
success
ref when success === true
error when success === false
correlationId
providerId
observedAt
```

#### Make optional/required behavior explicit

Current `fail(error, required)` returns a failed result for both required and optional writes, with a different message.  Improve it:

* required provider failure:

  * `success: false`
  * `reasonCode: provider-required-write-failed`
* optional provider failure:

  * `success: false`
  * `reasonCode: provider-optional-write-skipped`
  * emit instrumentation failure event

#### Redaction hardening

Current redaction checks `authToken`, `authorization`, `token`, and restricted evidence refs.  Add:

```ts
password
secret
apiKey
privateKey
refreshToken
accessToken
credential
```

Use case-insensitive matching.

### Tests

Create:

```text
products/data-cloud/libs/kernel-bridge-providers/src/__tests__/DataCloudKernelProviderClient.test.ts
products/data-cloud/libs/kernel-bridge-providers/src/__tests__/DataCloudProviderSchemas.test.ts
products/data-cloud/libs/kernel-bridge-providers/src/__tests__/createDataCloudKernelProviderContext.test.ts
```

Test:

1. provider context includes all platform-mode providers
2. missing auth token is not silently accepted where required
3. timeout aborts request
4. non-2xx maps reason code and correlation ID
5. sensitive values are redacted
6. invalid provider response fails validation
7. runtime truth schema rejects incomplete snapshot
8. required provider write failure fails closed

### Validation

```bash
pnpm --dir products/data-cloud/libs/kernel-bridge-providers test
pnpm --dir products/data-cloud/libs/kernel-bridge-providers typecheck
pnpm check:data-cloud-platform-providers
pnpm check:data-access-contract
pnpm check:kernel-provider-mode
```

---

## 5.3 `products/data-cloud/**/kernel provider API routes`

### Current issue

The provider library writes to `/api/v1/kernel/providers/events`, `/artifacts`, `/health`, `/approvals`, `/provenance`, `/memory`, and `/runtime-truth`.  The plan must ensure Data Cloud actually owns corresponding API contracts and implementations.

### Tasks

Locate existing route modules under Data Cloud delivery/action gateway. If missing, add product-owned routes under the existing Data Cloud delivery/API structure, not under platform.

Implement endpoints:

```text
POST /api/v1/kernel/providers/events
GET  /api/v1/kernel/providers/events

POST /api/v1/kernel/providers/artifacts
GET  /api/v1/kernel/providers/artifacts

POST /api/v1/kernel/providers/health
GET  /api/v1/kernel/providers/health/:productUnitId/latest

POST /api/v1/kernel/providers/approvals/requests
POST /api/v1/kernel/providers/approvals/decisions

POST /api/v1/kernel/providers/provenance
GET  /api/v1/kernel/providers/provenance

POST /api/v1/kernel/providers/memory
GET  /api/v1/kernel/providers/memory

POST /api/v1/kernel/providers/runtime-truth
GET  /api/v1/kernel/providers/runtime-truth/:productUnitId/latest
```

Each endpoint must:

1. validate tenant/workspace/project headers
2. validate correlation ID
3. validate payload schema
4. write via Data Cloud-owned storage/runtime truth service
5. return provider response schema
6. emit logs/metrics/traces
7. apply privacy classification and retention metadata

### Validation

```bash
pnpm check:data-cloud-platform-providers
pnpm check:data-access-contract
pnpm check:observability-conformance
```

---

# 6. YAPPC ProductUnitIntent and Artifact Intelligence Handoff

YAPPC has a ProductUnitIntent route with schema validation, evidence validation, scope checking, platform-mode evidence handling, and apply permission.  The check script enforces important boundary rules: no direct `writeFileSync`, no `kernel-product.yaml` mutation, Kernel apply service usage, and Data Cloud evidence requirements. 

The main problem is fake apply fallback.

---

## 6.1 `products/yappc/frontend/apps/api/src/routes/product-unit-intents.ts`

### Current issue

If `KERNEL_LIFECYCLE_BASE_URL` is missing, `applyProductUnitIntent` falls back locally. In apply mode, `buildFallbackApplicationResult` can return `applied`.  This violates Kernel lifecycle truth ownership.

### Tasks

#### Replace fallback behavior

Change:

```ts
if (options.kernelBaseUrl === undefined || options.kernelBaseUrl.trim().length === 0) {
  return buildFallbackApplicationResult(intent, options, correlationId, []);
}
```

To:

```ts
if (options.kernelBaseUrl === undefined || options.kernelBaseUrl.trim().length === 0) {
  return options.allowWrite
    ? buildBlockedApplicationResult(intent, options, correlationId, [
        'kernel-lifecycle-service-unavailable'
      ])
    : buildPreviewOnlyApplicationResult(intent, options, correlationId);
}
```

#### Split fallback helpers

Replace `buildFallbackApplicationResult` with:

```ts
buildPreviewOnlyApplicationResult(...)
buildBlockedApplicationResult(...)
```

Rules:

* preview may return `previewed`
* apply must never return `applied` without Kernel response
* invalid Kernel response must be `blocked` or `failed`
* HTTP non-2xx must be `blocked` with reason code
* platform mode requires Data Cloud evidence refs before Kernel call

#### Strengthen Kernel request

Add headers:

```ts
'x-correlation-id'
'x-ghatana-tenant-id'
'x-ghatana-workspace-id'
'x-ghatana-project-id'
```

Use the intent scope.

#### Strengthen response mapping

Do not map blocked Kernel result to accepted YAPPC status.

### Tests

Update:

```text
products/yappc/frontend/apps/api/src/__tests__/product-unit-intents.test.ts
```

Add tests:

1. preview without Kernel base URL returns accepted/preview only
2. apply without Kernel base URL returns blocked/503
3. apply with Kernel HTTP 500 returns blocked
4. apply with invalid Kernel JSON returns blocked
5. apply with valid Kernel applied response returns queued
6. platform mode without Data Cloud evidence returns 409
7. apply without permission returns 403
8. product scope mismatch returns 403

### Validation

```bash
pnpm check:yappc-product-unit-intent-handoff
pnpm check:yappc-artifact-intelligence-boundary
```

---

## 6.2 `products/yappc/frontend/web/src/services/canvas/commands/ProductUnitIntentExportService.ts`

### Current check evidence

The handoff check requires this service to use `DEFAULT_INTENT_ENDPOINT = '/api/v1/yappc/product-unit-intents'`, include `dataCloudEvidenceEndpoint`, parse Data Cloud evidence persistence responses, and pass `evidenceRefs: persistedEvidenceRefs`. 

### Tasks

1. Ensure the service persists artifact intelligence evidence before platform-mode handoff.
2. Ensure platform mode cannot submit raw local evidence only.
3. Add retry/timeout behavior for Data Cloud persistence.
4. Add user-safe errors:

   * `evidence-persistence-failed`
   * `platform-mode-requires-data-cloud`
   * `intent-handoff-denied`
5. Add correlation ID propagation.

### Tests

Update:

```text
products/yappc/frontend/web/src/services/canvas/commands/__tests__/ProductUnitIntentExportService.test.ts
```

Add tests:

1. platform mode persists evidence first
2. missing Data Cloud refs blocks handoff
3. Data Cloud invalid response fails
4. bootstrap mode can preview without Data Cloud
5. apply mode requires explicit permission metadata

### Validation

```bash
pnpm check:yappc-product-unit-intent-handoff
```

---

## 6.3 `platform/typescript/kernel-product-contracts/src/artifact-intelligence/**`

### Current state

Artifact intelligence schemas are exported through Kernel product contracts. 

### Tasks

Ensure schemas contain enough fields for Data Cloud and Kernel to consume evidence by reference:

1. `evidenceId`
2. `schemaVersion`
3. `productUnitId`
4. `tenantId`
5. `workspaceId`
6. `projectId`
7. `sourceRef`
8. `artifactKind`
9. `confidence`
10. `provenanceRefs`
11. `privacyClassification`
12. `retentionPolicyId`
13. `createdAt`
14. `correlationId`

Add schema tests for each evidence type:

```text
SemanticArtifactReference
ArtifactGraphSummary
ProductShapeEvidence
DependencyGraphEvidence
ResidualIslandReport
RiskHotspotReport
GeneratedChangeSetSummary
```

### Validation

```bash
pnpm --dir platform/typescript/kernel-product-contracts test
pnpm check:yappc-artifact-intelligence-boundary
```

---

# 7. Ghatana Studio Unified UX and API Integration

Studio exists as `@ghatana/ghatana-studio` and depends on API, canvas, design system, ds-generator, ds-registry, Kernel packages, i18n, theme, tokens, and UI builder.  It has the canonical initial navigation: Home, Ideas, Blueprints, Canvas, Develop, Lifecycle, Agents, Artifacts, Deployments, Health, Learn, Settings.  The navigation model includes ownership and route status. 

Now make it production-grade.

---

## 7.1 `platform/typescript/ghatana-studio/src/main.tsx`

### Current issue

Kernel API client is created only when `VITE_GHATANA_KERNEL_API_BASE_URL` exists. Tenant/workspace/project/auth are not passed. 

### Tasks

1. Add `studioRuntimeConfig.ts`:

   ```text
   platform/typescript/ghatana-studio/src/config/studioRuntimeConfig.ts
   ```
2. Read:

   * `VITE_GHATANA_KERNEL_API_BASE_URL`
   * `VITE_GHATANA_TENANT_ID`
   * `VITE_GHATANA_WORKSPACE_ID`
   * `VITE_GHATANA_PROJECT_ID`
3. Do not read long-lived auth token from static build env. Add a placeholder interface for runtime auth provider:

   ```ts
   StudioAuthTokenProvider
   ```
4. Pass scope fields into `createKernelLifecycleClient`.
5. If config is incomplete, pass an explicit `configurationStatus` to `StudioLifecycleDataProvider`.

### Tests

Create:

```text
platform/typescript/ghatana-studio/src/config/__tests__/studioRuntimeConfig.test.ts
```

Test:

1. complete config creates Kernel client options
2. missing base URL blocks lifecycle execution
3. missing scope values reports configuration issue
4. no token is required for local dev only when explicitly configured

### Validation

```bash
pnpm --dir platform/typescript/ghatana-studio test
pnpm --dir platform/typescript/ghatana-studio type-check
```

---

## 7.2 `platform/typescript/ghatana-studio/src/data/StudioLifecycleDataContext.tsx`

### Current issue

The context has a client abstraction and loads product units/runs/manifests, but it stores only one `productUnit`, not the full product unit list. It also swallows manifest fetch failures with `.catch(() => undefined)`. 

### Tasks

#### Extend snapshot

Add:

```ts
readonly productUnits: readonly ProductUnit[];
readonly manifestLoadStates: Record<ManifestType, ManifestLoadState>;
readonly configurationIssues: readonly StudioConfigurationIssue[];
readonly approvalQueue: readonly ApprovalSummary[];
```

Add types:

```ts
type ManifestLoadState =
  | { status: 'not-requested' }
  | { status: 'loaded' }
  | { status: 'missing'; reasonCode: string }
  | { status: 'failed'; reasonCode: string; message: string }
  | { status: 'unauthorized'; reasonCode: string };
```

#### Stop swallowing manifest failures

Replace:

```ts
client.getArtifactManifest(...).catch(() => undefined)
```

with a helper:

```ts
loadManifestWithState(...)
```

#### Add dynamic ProductUnit selection

`selectProductUnit(productUnitId)` must validate against loaded product units or set a degraded state.

#### Add lifecycle readiness

Expose whether selected product is:

```ts
enabled
planned
partial
disabled
blocked
```

Use product metadata returned by Kernel contracts or extend the Kernel product unit response if missing.

### Tests

Create or update:

```text
platform/typescript/ghatana-studio/src/data/__tests__/StudioLifecycleDataContext.test.tsx
```

Test:

1. no client => configured blocked/unconfigured state
2. product units load successfully
3. selected product changes run list
4. manifest 404 becomes `missing`
5. manifest 403 becomes `unauthorized`
6. manifest 500 becomes `failed`
7. refresh preserves selected product/run when still valid
8. polling cleanup clears interval

### Validation

```bash
pnpm --dir platform/typescript/ghatana-studio test
pnpm check:studio-kernel-api
```

---

## 7.3 `platform/typescript/ghatana-studio/src/routes/LifecyclePage.tsx`

### Current issue

Lifecycle page has useful controls, but it hardcodes Digital Marketing, uses `production` while registry uses `prod`, displays raw JSON, has placeholder approval queue, and contains hardcoded English strings in manifest tab titles and phase labels.  

### Tasks

1. Replace hardcoded ProductUnit option with `snapshot.productUnits`.
2. Show readiness:

   * enabled
   * planned
   * partial
   * disabled
   * blocked
3. Disable execute button if product lifecycle is not enabled.
4. Replace environment list:

   ```ts
   ['local', 'dev', 'staging', 'prod']
   ```
5. Add reusable components:

   ```text
   platform/typescript/ghatana-studio/src/components/lifecycle/ProductUnitSelector.tsx
   platform/typescript/ghatana-studio/src/components/lifecycle/LifecyclePhasePicker.tsx
   platform/typescript/ghatana-studio/src/components/lifecycle/ProviderModeSelector.tsx
   platform/typescript/ghatana-studio/src/components/lifecycle/LifecycleRunList.tsx
   platform/typescript/ghatana-studio/src/components/lifecycle/LifecycleRunSummaryPanel.tsx
   platform/typescript/ghatana-studio/src/components/lifecycle/ManifestStatusPanel.tsx
   platform/typescript/ghatana-studio/src/components/lifecycle/ApprovalQueuePanel.tsx
   platform/typescript/ghatana-studio/src/components/lifecycle/FailureDiagnosticsPanel.tsx
   ```
6. Replace raw JSON-only panels with structured data and collapsible raw JSON.
7. Move all visible labels into `studioTranslations`.
8. Add accessible status labels that are not color-only.

### Tests

Create:

```text
platform/typescript/ghatana-studio/src/routes/__tests__/LifecyclePage.test.tsx
platform/typescript/ghatana-studio/src/components/lifecycle/__tests__/ProductUnitSelector.test.tsx
platform/typescript/ghatana-studio/src/components/lifecycle/__tests__/ManifestStatusPanel.test.tsx
platform/typescript/ghatana-studio/src/components/lifecycle/__tests__/ApprovalQueuePanel.test.tsx
```

Test:

1. product selector renders all ProductUnits
2. disabled product cannot execute lifecycle
3. Digital Marketing can execute enabled phase
4. platform mode disabled if providers unavailable
5. manifest missing/failure/unauthorized states render
6. approval queue renders pending items
7. keyboard navigation works
8. no hardcoded untranslated visible text

### Validation

```bash
pnpm --dir platform/typescript/ghatana-studio test
pnpm --dir platform/typescript/ghatana-studio type-check
pnpm check:shared-ui-state-coverage
pnpm check:design-system-conformance
```

---

## 7.4 `platform/typescript/ghatana-studio/src/api/kernelLifecycleClient.ts`

### Current state

The client is typed and validates API responses using Zod. It sends tenant/workspace/project headers if options are provided.  

### Tasks

1. Add `listApprovals(productUnitId?)`.
2. Add `getRuntimeTruth(productUnitId)`.
3. Add `getProvenance(productUnitId, runId)`.
4. Add `getLifecycleHealthSnapshot(productUnitId, runId)`.
5. Use stricter error parsing with `KernelLifecycleApiErrorSchema`.
6. Ensure all methods accept optional query:

   ```ts
   providerMode
   environment
   correlationId
   ```
7. Add response validation for approval queue and runtime truth.

### Tests

Create or update:

```text
platform/typescript/ghatana-studio/src/api/__tests__/kernelLifecycleClient.test.ts
```

Test:

1. headers include correlation ID and scope
2. auth token is sent only if provided
3. invalid response fails
4. error response parses safe details
5. approval queue endpoint parses

### Validation

```bash
pnpm --dir platform/typescript/ghatana-studio test
pnpm check:studio-kernel-api
```

---

# 8. Digital Marketing Lifecycle Pilot Completion

Digital Marketing is the validated lifecycle pilot with lifecycle enabled, backend-api/web surfaces, bridge adapter conformance, artifacts, compose-local deployment, and health checks.  Its `kernel-product.yaml` contains required manifests, plugins, policy packs, environments, surfaces, phases, approvals, artifact definitions, gates, packaging, deployment, provider modes, retention policy, and verify report fields. 

---

## 8.1 `products/digital-marketing/kernel-product.yaml`

### Current state

This file is strong and should remain the canonical pilot manifest. 

### Tasks

1. Add supply-chain policy refs:

   ```yaml
   policyPacks:
     security:
       - kernel://policy-packs/web-api-security-baseline
       - kernel://policy-packs/container-image-integrity
       - kernel://policy-packs/artifact-sbom-required
       - kernel://policy-packs/artifact-signature-required
   ```
2. Add required package manifest fields:

   ```yaml
   requiredManifests:
     package:
       - artifact-manifest
       - lifecycle-health-snapshot
       - sbom-manifest
       - artifact-attestation
   ```
3. Add artifact trust requirements:

   ```yaml
   artifactTrust:
     requireSbom: true
     requireSignature: true
     requireProvenance: true
   ```
4. Add deployment evidence requirements:

   ```yaml
   deployment:
     local:
       requiredEvidence:
         - artifact-manifest
         - deployment-manifest
         - verify-health-report
   ```
5. Keep `allowExperimentalAdapters: false`.

### Validation

```bash
pnpm check:digital-marketing-lifecycle-pilot
pnpm check:product-artifact-contracts
pnpm check:product-deployment-contracts
```

---

## 8.2 `scripts/check-digital-marketing-lifecycle-pilot.mjs`

### Current state

The check already validates registry lifecycle status, parsed manifest, required manifest versions, approvals, policy packs, plugin bindings, health paths, packaging adapter, deployment config, compose labels, verify fields, env safety, plan generation, rollback approval, gitignore, and absence of product-local lifecycle scripts.  

### Tasks

Add checks for:

1. SBOM policy pack
2. signature policy pack
3. artifact trust requirements
4. package phase emits artifact manifest with trust fields in smoke mode
5. deployment manifest links to artifact manifest
6. verify report includes evidence refs
7. lifecycle events include correlation ID
8. Data Cloud platform mode is not required for bootstrap smoke

### Validation

```bash
pnpm check:digital-marketing-lifecycle-pilot --smoke
```

---

# 9. Future Product Shape Readiness

The registry correctly keeps PHR, Finance, and FlashIt disabled/planned until their gates and adapters are ready. PHR requires consent, PII classification, audit evidence, FHIR validation, and data sovereignty gates. Finance requires regulatory gates, risk controls, promotion approval, and multi-module build validation.  FlashIt requires mobile adapters, preview security, personal-data classification, and mobile artifact contracts. 

The goal is **readiness without fake enablement**.

---

## 9.1 `config/canonical-product-registry.json`

### Tasks

Keep lifecycle disabled for:

```text
phr
finance
flashit
data-cloud
yappc
tutorputor
dcmaar
aura
audio-video
```

until their gates are implemented.

For each disabled/partial product, ensure:

1. `lifecycleStatus` is not `enabled`
2. `lifecycle.enabled` is false or absent
3. `lifecycleReadiness.reasonCodes` are present
4. `requiredGates` are present
5. `nextRequiredWork` is present
6. `evidenceRefs` point to real files/directories

### Validation

```bash
pnpm check:product-registry
pnpm check:product-registry-drift
pnpm check:product-shape-capability-matrix
```

---

## 9.2 `products/phr/kernel-product.yaml`

### Tasks

If present, harden but do not enable:

1. consent gate
2. PII classification gate
3. FHIR contract validation gate
4. tenant-data-sovereignty gate
5. audit evidence gate
6. regulated deployment policy

Add explicit:

```yaml
lifecycle:
  enabled: false
readiness:
  status: planned
```

### Validation

```bash
pnpm check:product-manifest-contracts
pnpm check:product-shape-capability-matrix
```

---

## 9.3 `products/finance/kernel-product.yaml`

### Tasks

If present, harden but do not enable:

1. multi-module Gradle graph validation
2. regulatory compliance gate
3. promotion approval gate
4. operator/portal/SDK adapter readiness
5. domain dependency validation
6. reporting evidence gate

Add explicit:

```yaml
lifecycle:
  enabled: false
readiness:
  status: planned
```

### Validation

```bash
pnpm check:product-manifest-contracts
pnpm check:finance-transaction-workflow-proof
pnpm check:product-shape-capability-matrix
```

---

## 9.4 `products/flashit/kernel-product.yaml`

### Tasks

If present, harden but do not enable mobile lifecycle:

1. preview security gate
2. personal data classification
3. mobile IPA manifest
4. mobile AAB manifest
5. iOS adapter readiness
6. Android adapter readiness

Add:

```yaml
lifecycle:
  enabled: false
mobileLifecycle:
  enabled: false
  reasonCodes:
    - requires-mobile-adapters
    - requires-mobile-bundle-artifacts
```

### Validation

```bash
pnpm check:flashit-client-conformance
pnpm check:product-shape-capability-matrix
```

---

# 10. CI/CD and Regression Gates

## 10.1 `.github/workflows/product-lifecycle.yml`

### Tasks

Update workflow to run in tiers:

#### Tier 1 — fast governance

```bash
pnpm check:domain-registry
pnpm check:architecture-boundaries
pnpm check:deprecated-imports
pnpm check:current-state-claims
pnpm check:orphan-modules
```

#### Tier 2 — Kernel platform lifecycle

```bash
pnpm build:kernel-lifecycle-platform
pnpm check:kernel-platform-lifecycle
```

#### Tier 3 — Digital Marketing pilot

```bash
pnpm check:digital-marketing-lifecycle-pilot
pnpm check:digital-marketing-lifecycle-pilot --smoke
```

#### Tier 4 — Studio

```bash
pnpm --dir platform/typescript/ghatana-studio test
pnpm --dir platform/typescript/ghatana-studio type-check
pnpm check:studio-kernel-api
```

#### Tier 5 — Data Cloud providers

```bash
pnpm --dir products/data-cloud/libs/kernel-bridge-providers test
pnpm check:data-cloud-platform-providers
pnpm check:data-access-contract
```

#### Tier 6 — full regression

```bash
pnpm test
./gradlew check
```

### Validation

```bash
pnpm check:product-ci-matrices
pnpm check:security-workflow-coverage
```

---

# 11. Cleanup and Removal Plan

## 11.1 Deprecated/fake/unsafe code

Remove or fail closed:

| Path                                                                         | Action                                                                 |
| ---------------------------------------------------------------------------- | ---------------------------------------------------------------------- |
| `products/yappc/frontend/apps/api/src/routes/product-unit-intents.ts`        | Remove fake apply fallback                                             |
| `platform/typescript/kernel-toolchains/src/adapters/XcodeIosAdapter.ts`      | Replace unsafe exec and fake output validation                         |
| `platform/typescript/kernel-toolchains/src/adapters/GradleAndroidAdapter.ts` | Replace unsafe exec and fake output validation                         |
| `platform/typescript/kernel-lifecycle/src/service/KernelLifecycleService.ts` | Remove broad silent read catches                                       |
| `platform/typescript/ghatana-studio/src/routes/LifecyclePage.tsx`            | Remove hardcoded Digital Marketing-only selector and raw JSON-first UX |

## 11.2 Keep but harden

| Path                                                                  | Action                                                         |
| --------------------------------------------------------------------- | -------------------------------------------------------------- |
| `config/domain-registry.json`                                         | Update classifications/reason codes                            |
| `config/canonical-product-registry.json`                              | Keep disabled products disabled; strengthen readiness evidence |
| `products/digital-marketing/kernel-product.yaml`                      | Keep as pilot; add artifact trust requirements                 |
| `products/data-cloud/libs/kernel-bridge-providers/src/index.ts`       | Split into focused files                                       |
| `platform/typescript/ghatana-studio/src/api/kernelLifecycleClient.ts` | Extend API client for approvals/runtime truth/provenance       |

---

# 12. Final Validation Suite

Run this after all workstreams:

```bash
pnpm build
pnpm test
pnpm typecheck
pnpm build:platform
pnpm build:kernel-lifecycle-platform

pnpm check:architecture-boundaries
pnpm check:domain-registry
pnpm check:doc-truth
pnpm check:doc-claims-evidence
pnpm check:current-state-claims
pnpm check:deprecated-imports
pnpm check:orphan-modules
pnpm check:production-readiness
pnpm check:production-stubs
pnpm check:secret-default-credentials

pnpm check:kernel-platform-lifecycle
pnpm check:kernel-provider-mode
pnpm check:kernel-lifecycle-truth
pnpm check:agentic-lifecycle-action-contracts
pnpm check:studio-kernel-api

pnpm check:digital-marketing-lifecycle-pilot
pnpm check:digital-marketing-lifecycle-pilot --smoke

pnpm check:yappc-product-unit-intent-handoff
pnpm check:yappc-artifact-intelligence-boundary

pnpm check:data-cloud-platform-providers
pnpm check:data-access-contract

pnpm check:design-system-conformance
pnpm check:shared-product-shells
pnpm check:shared-layout-primitives
pnpm check:shared-ui-state-coverage

pnpm check:product-manifest-contracts
pnpm check:product-registry
pnpm check:product-registry-drift
pnpm check:product-shape-capability-matrix

pnpm check:observability-conformance
pnpm check:security-workflow-coverage

./gradlew build
./gradlew check
```

---

# Implementation Principle

Do **not** add new platform concepts until these foundations are hardened. The repo already has the correct major pieces: Kernel, Studio, YAPPC handoff, Data Cloud provider bridge, Digital Marketing pilot, domain registry, product registry, and validation scripts. The right next move is to make those pieces stricter, safer, more observable, and more dynamically connected.

The highest-priority fixes are:

1. **No fake ProductUnitIntent apply without Kernel**
2. **No silent lifecycle truth read failures**
3. **No executable adapter that cannot validate outputs**
4. **No Studio lifecycle action without scoped/authenticated Kernel context**
5. **No platform mode without Data Cloud-backed providers**
6. **No product lifecycle enablement before required gates are real**
7. **No artifact/deployment success without provenance and evidence**
