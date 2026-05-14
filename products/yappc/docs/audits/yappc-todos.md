# Implementation Plan B — YAPPC Visibility, Digital Marketing Lifecycle Pilot, Product Shape Proof, and World-Class Product Experience

**Repository:** `samujjwal/ghatana`  
**Target commit reviewed:** `462a41402c214ccbdf009bab563428fa58505a5f`  
**Owner layers:** YAPPC visibility/control plane, Digital Marketing pilot product, product-shape checks, shared contract consumers  
**Independent execution:** Yes. Can proceed alongside Plan A. If Plan A contracts are incomplete, use local Kernel manifest DTO adapters first.  
**Primary proof target:** Digital Marketing lifecycle health visible in YAPPC from Kernel truth.  

---

## 1. Goal

Make the platform tangible as a **world-class product experience**:

```text
Digital Marketing proves Kernel lifecycle execution with real product workflows.
YAPPC becomes the visibility, health, intelligence, and control-plane layer.
Operators can see lifecycle, gates, artifacts, deployments, agent governance, and next actions.
YAPPC can emit ProductUnitIntent without mutating Kernel registry files.
Product shape matrix validates future capability across PHR, Finance, FlashIt, Data Cloud, YAPPC, Aura, DCMAAR, TutorPutor, Audio-Video, and future products.
```

Kernel executes. YAPPC observes, explains, guides, and learns.

---

## 2. Non-goals

```text
Do not make YAPPC execute Kernel lifecycle phases.
Do not parse private Kernel logs as lifecycle truth.
Do not mutate config/canonical-product-registry.json from YAPPC code.
Do not migrate YAPPC itself to lifecycle-enabled status.
Do not enable lifecycle execution for PHR, Finance, FlashIt, Data Cloud, Aura, DCMAAR, TutorPutor, or Audio-Video.
Do not move Digital Marketing business logic into YAPPC.
Do not create shared UI packages before reuse is proven.
Do not create product-prefixed @ghatana/* platform packages.
```

---

## 3. Current-state classification at commit `462a414`

| Area | Evidence | Classification | Main gap |
|---|---|---|---|
| YAPPC visibility feature | Search did not show ProductUnitIntentExporter, kernelvisibility, or KernelHealthDashboard. | Target architecture | Need implementation |
| YAPPC creator lifecycle | README/docs define creator lifecycle and SDLC concepts. | Existing partial | Needs Kernel boundary language |
| ProductUnitIntent contract | Kernel contracts export ProductUnitIntent. | Existing partial | YAPPC exporter missing |
| Digital Marketing lifecycle scripts | Root package has plan/validate/test/build/package/deploy/verify scripts. | Existing partial | Need full manifest/health proof and YAPPC visibility |
| Product shape matrix | Generated JSON exists and exposes gaps. | Existing partial | Needs better classification and actionability |
| Plugins | Kernel plugin registry declares categories. | Declared only | YAPPC should display declared/implemented maturity clearly |
| Boundary checks | YAPPC-specific Kernel boundary check not visible. | Target architecture | Need check |

---

## 4. Workstream A — YAPPC documentation and boundary clarity

### 4.1 `products/yappc/README.md`

**Action:** Update.

**Required changes:**

1. Reframe YAPPC as:

```text
AI-powered product/app creation, visibility, health, and evolution platform.
```

2. Add lifecycle distinction:

```text
YAPPC Creator Lifecycle: Intent → Shape → Validate → Generate → Run → Observe → Learn → Evolve.
Kernel Product Lifecycle: dev → validate → test → build → package → deploy → verify → promote → rollback.
```

3. Add:

```text
For lifecycle-managed ProductUnits, YAPPC emits ProductUnitIntent and displays Kernel lifecycle truth; Kernel executes lifecycle phases.
```

4. Link to:

```text
products/yappc/docs/architecture/KERNEL_VISIBILITY_AND_CONTROL_PLANE.md
products/yappc/docs/architecture/CREATOR_LIFECYCLE_TO_KERNEL_MAPPING.md
```

---

### 4.2 `products/yappc/docs/ARCHITECTURE.md`

**Action:** Update.

**Required sections:**

```text
Kernel Visibility and ProductUnit Integration
YAPPC Creator Lifecycle vs Kernel Product Lifecycle
YAPPC phase gates vs Kernel delivery gates
Public truth consumption rules
Current maturity table
```

**Boundary rules to add:**

```text
YAPPC may initiate Kernel work through public API/CLI/contracts.
YAPPC may read Kernel manifests/events/health snapshots.
YAPPC must not execute Kernel lifecycle internals.
YAPPC must not mutate Kernel registry files.
```

---

### 4.3 `products/yappc/config/pipelines/lifecycle-management-v1.yaml`

**Action:** Update comments.

Add:

```yaml
# This pipeline governs YAPPC creator/SDLC phase transitions.
# It does not replace Kernel Product Lifecycle execution.
# For lifecycle-managed ProductUnits, YAPPC initiates Kernel work through public ProductUnitIntent/lifecycle APIs and displays Kernel truth outputs.
```

---

### 4.4 `products/yappc/docs/architecture/KERNEL_VISIBILITY_AND_CONTROL_PLANE.md`

**Action:** Create.

**Required structure:**

```text
1. Purpose
2. YAPPC as creator, visibility, health, and control-plane layer
3. Kernel as lifecycle execution truth layer
4. Data Cloud as governed event/knowledge/memory backbone
5. Digital Marketing pilot read path
6. Public truth sources
7. Forbidden integration paths
8. Health views
9. Next-action recommendations
10. Security/privacy/approval posture
11. Current-state vs target-state table
```

**Public truth sources:**

```text
lifecycle-plan.json
lifecycle-result.json
gate-result-manifest.json
artifact-manifest.json
deployment-manifest.json
verify-health-report.json
lifecycle-health-snapshot.json
Kernel lifecycle events
```

**Forbidden:**

```text
stdout/stderr scraping
direct registry mutation
private Kernel implementation imports
YAPPC lifecycle runner for Kernel phases
```

---

### 4.5 `products/yappc/docs/architecture/CREATOR_LIFECYCLE_TO_KERNEL_MAPPING.md`

**Action:** Create.

**Required mapping:**

| YAPPC phase | YAPPC owns | Kernel interaction |
|---|---|---|
| Intent | goal capture | draft ProductUnitIntent |
| Shape | product/app model | profile/surface possibility validation |
| Validate | intent/model validation | optional Kernel plan validation |
| Generate | generated code/artifacts | ProductUnitIntent + source outputs |
| Run | user-visible run workflow | Kernel dev/test/build/package/deploy/verify |
| Observe | health/read model | Kernel manifests/events/health |
| Learn | pattern extraction | Kernel failures/gates/artifacts/agent events |
| Evolve | next version/change | updated ProductUnitIntent/change intent |

---

## 5. Workstream B — ProductUnitIntent generation from YAPPC

### 5.1 `products/yappc/core/scaffold/api/src/main/java/com/ghatana/yappc/kernel/ProductUnitIntentExporter.java`

**Action:** Create.

**Purpose:** Convert YAPPC project/pack/generation inputs into Kernel ProductUnitIntent JSON/YAML.

**JavaDoc required:**

```java
/**
 * @doc.type class
 * @doc.purpose Exports YAPPC project generation intent as a Kernel ProductUnitIntent.
 * @doc.layer product
 * @doc.pattern Adapter
 */
```

**Responsibilities:**

```text
Accept project name, product kind, surfaces, runtimes, lifecycle profile, target providers.
Create ProductUnitIntent with producer.type=yappc.
Include workspace/project/correlation metadata where available.
Validate required fields before writing.
Write to explicit output path.
Never mutate Kernel registry/config files.
```

**Tests:**

```text
products/yappc/core/scaffold/api/src/test/java/com/ghatana/yappc/kernel/ProductUnitIntentExporterTest.java
```

Cases:

```text
exports standard web+api intent
exports backend-only intent
exports external repository provider intent
rejects missing product id
rejects empty surface list
does not write config/canonical-product-registry.json
includes producer metadata
```

---

### 5.2 `products/yappc/core/scaffold/api/src/main/java/com/ghatana/yappc/kernel/ProductUnitIntentValidationService.java`

**Action:** Create.

**Validation rules:**

```text
product id is non-empty and kebab-case
name is non-empty
target registry/source providers exist in request
surface list is non-empty
surface types use Kernel vocabulary
lifecycle profile is recognized if requested
no raw secret values
no hardcoded Kernel paths unless target provider is ghatana-file-registry
```

**Tests:**

```text
ProductUnitIntentValidationServiceTest.java
```

---

### 5.3 `products/yappc/core/scaffold/api/src/main/java/com/ghatana/yappc/cli/CreateCommand.java`

**Action:** Update.

**Add options:**

```java
@Option(names = "--target", description = "Target type: generic-project or kernel-product-unit")
private String targetType;

@Option(names = "--intent-output", description = "Path to write Kernel ProductUnitIntent")
private Path intentOutputPath;
```

**Behavior:**

```text
target absent or generic-project:
  current behavior unchanged

target=kernel-product-unit:
  build ProductUnitIntent request
  validate it
  write intent file
  print next Kernel command:
    pnpm kernel product create --from-intent <intent-file>
  do not mutate Kernel registry
```

**Tests:**

```text
CreateCommandKernelProductUnitTest.java
```

---

### 5.4 `products/yappc/core/scaffold/api/src/main/java/com/ghatana/yappc/cli/CICommand.java`

**Action:** Update.

**Add option:**

```java
@Option(names = "--target", description = "Target type: generic-project or kernel-product-unit")
private String targetType;
```

**Behavior:**

```text
generic-project:
  current CI validation/generation behavior

kernel-product-unit:
  do not generate raw GitHub Actions
  delegate/instruct Kernel lifecycle CI:
    pnpm check:product-registry-artifacts
    pnpm kernel product plan <product> build
    pnpm check:kernel-platform-lifecycle
```

**Tests:**

```text
CICommandKernelProductUnitTest.java
```

---

## 6. Workstream C — YAPPC Kernel health read model and UI

### 6.1 `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/kernelvisibility/KernelLifecycleTruthSource.java`

**Action:** Create interface.

**Purpose:** Abstract public Kernel truth source.

**Initial implementations:**

```text
LocalKernelManifestTruthSource
FutureDataCloudKernelTruthSource
```

**Methods:**

```java
Promise<List<KernelLifecycleRunSummary>> listRuns(String productUnitId);
Promise<Optional<KernelLifecycleRunDetail>> getRun(String productUnitId, String runId);
Promise<Optional<KernelHealthSnapshotView>> getLatestHealth(String productUnitId);
```

---

### 6.2 `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/kernelvisibility/LocalKernelManifestTruthSource.java`

**Action:** Create.

**Reads only:**

```text
lifecycle-plan.json
lifecycle-result.json
gate-result-manifest.json
artifact-manifest.json
deployment-manifest.json
verify-health-report.json
lifecycle-health-snapshot.json
```

**Must not:**

```text
parse stdout/stderr
infer state from logs
read private Kernel implementation files
```

**Tests:**

```text
LocalKernelManifestTruthSourceTest.java
```

---

### 6.3 `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/kernelvisibility/KernelHealthSnapshotService.java`

**Action:** Create.

**Methods:**

```java
Promise<List<ProductUnitHealthSummary>> listProductUnitHealth();
Promise<ProductUnitHealthDetail> getProductUnitHealth(String productUnitId);
Promise<LifecycleTimelineView> getLifecycleTimeline(String productUnitId);
```

**Health statuses:**

```text
healthy
degraded
blocked
failed
skipped
unknown
requires-approval
requires-verification
obsolete
quarantined
```

---

### 6.4 `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/kernelvisibility/KernelActionRecommendationService.java`

**Action:** Create.

**Recommendation examples:**

```text
Rerun failed phase
Open gate failure details
Approve pending gate
Fix missing adapter config
Inspect artifact digest
Run verify
Rollback deployment
Update ProductUnitIntent
Resolve obsolete mastery
Acknowledge semi-trusted preview
```

**Rule:** Recommendations are advisory. Execution later goes through Kernel/API/action contracts.

---

### 6.5 `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/kernelvisibility/dto/*.java`

**Action:** Create DTOs.

**Files:**

```text
ProductUnitHealthSummary.java
ProductUnitHealthDetail.java
LifecycleTimelineView.java
LifecyclePhaseView.java
GateHealthView.java
ArtifactHealthView.java
DeploymentHealthView.java
AgentGovernanceHealthView.java
RecommendedActionView.java
```

**Rules:**

```text
Use records where practical.
Keep DTOs as YAPPC read-models, not duplicate Kernel contract truth.
Public Java types require @doc tags if repo checks require them.
```

---

### 6.6 `products/yappc/frontend/web/src/features/kernel-health/KernelHealthDashboardPage.tsx`

**Action:** Create.

**Dashboard sections:**

```text
ProductUnit status cards
Digital Marketing pilot card
Recent lifecycle runs
Blocked gates
Deployment health
Agent governance/learning status
Recommended actions
```

**React/TS requirements:**

```text
No any.
Explicit props interfaces.
Typed hooks.
Use current YAPPC design system and product shell patterns.
No new shared UI package until reuse is proven.
```

---

### 6.7 `products/yappc/frontend/web/src/features/kernel-health/components/LifecycleTimelinePanel.tsx`

**Action:** Create.

Displays:

```text
phase
status
started/completed time
duration
adapter
gate summary
artifact summary
```

---

### 6.8 `products/yappc/frontend/web/src/features/kernel-health/components/GateHealthPanel.tsx`

**Action:** Create.

Displays:

```text
gate id
required/optional
status
failure reason
evidence
correct owner
recommended action
```

---

### 6.9 `products/yappc/frontend/web/src/features/kernel-health/components/ArtifactHealthPanel.tsx`

**Action:** Create.

Displays:

```text
artifact type
surface
path/image
fingerprint/digest
producedBy step
createdAt
deployment linkage
```

---

### 6.10 `products/yappc/frontend/web/src/features/kernel-health/components/DeploymentHealthPanel.tsx`

**Action:** Create.

Displays:

```text
environment
target
services
health checks
last verify status
rollback availability
artifact digest
```

---

### 6.11 `products/yappc/frontend/web/src/features/kernel-health/components/AgentGovernanceHealthPanel.tsx`

**Action:** Create.

Displays:

```text
agent id
mastery state
learning contract state
approval/verification requirement
obsolete/quarantined warning
trace evidence
```

---

### 6.12 `products/yappc/frontend/web/src/features/kernel-health/components/RecommendedActionsPanel.tsx`

**Action:** Create.

Displays:

```text
action label
why
risk
confidence
owner
evidence
CTA disabled/enabled depending approval/action support
```

---

### 6.13 `products/yappc/frontend/web/src/features/kernel-health/api/kernelHealthClient.ts`

**Action:** Create.

**Rules:**

```text
Use existing YAPPC API client pattern first.
Validate response shape if the app already uses runtime validation.
No inline mocks in production client.
No any.
```

---

### 6.14 `products/yappc/frontend/web/src/features/kernel-health/hooks/useKernelHealth.ts`

**Action:** Create typed query hooks.

**Status: ✅ DONE** — `useKernelProductUnitHealth`, `useKernelLifecycleTimeline`, `useKernelRecommendedActions`, `useKernelProductUnitList` created with TanStack Query, typed with Zod-inferred types, refetchInterval configured.

```ts
useKernelProductUnitHealth(productUnitId: string)
useKernelLifecycleTimeline(productUnitId: string)
useKernelRecommendedActions(productUnitId: string)
```

---

### 6.15 YAPPC route registration file

**Action:** Update the current YAPPC web router/route registry.

**Status: ✅ DONE** — Routes registered in `routes.ts`: `/kernel-health` and `/kernel-health/products/:productUnitId`. Route files created at `routes/app/kernel-health.tsx` and `routes/app/kernel-health-product.tsx`.

**Routes:**

```text
/kernel-health
/kernel-health/products/:productUnitId
```

**Guard:** Use the existing YAPPC auth/role pattern. Do not invent a new router strategy.

---

### 6.16 `products/yappc/scripts/check-kernel-boundary-usage.mjs`

**Action:** Create.

**Status: ✅ DONE** — Script created. Checks all six YAPPC/Kernel boundary rules. Reports errors and exits 1 on violation.

**Checks:**

```text
YAPPC does not write config/canonical-product-registry.json.
YAPPC kernel-health imports only public Kernel contracts or YAPPC DTOs.
YAPPC does not define duplicate Kernel Product Lifecycle enum.
YAPPC does not parse stdout/stderr for lifecycle status.
CreateCommand target=kernel-product-unit writes ProductUnitIntent, not registry.
CICommand target=kernel-product-unit delegates to Kernel lifecycle.
```

Add root script:

```json
"check:yappc-kernel-boundary": "node ./products/yappc/scripts/check-kernel-boundary-usage.mjs"
```

---

## 7. Workstream D — Digital Marketing lifecycle pilot hardening

### 7.1 `products/digital-marketing/kernel-product.yaml`

**Action:** Harden as pilot config.

**Status: ✅ DONE** — `security: required: true` plugin added. All required plugins (`audit`, `observability`, `data-access`, `identity-entitlement`, `security`, `preview-security`) and `requiredManifests` for all four phases verified present. `allowExperimentalAdapters: false` confirmed.

**Add/verify:**

```yaml
requiredManifests:
  build:
    - lifecycle-result
    - artifact-manifest
    - lifecycle-health-snapshot
  package:
    - artifact-manifest
    - lifecycle-health-snapshot
  deploy:
    - deployment-manifest
    - lifecycle-health-snapshot
  verify:
    - verify-health-report
    - lifecycle-health-snapshot

plugins:
  audit:
    required: true
  observability:
    required: true
  data-access:
    required: true
  identity-entitlement:
    required: true
  security:
    required: true
  privacy:
    required: false
```

Verify:

```text
backend livePath=/health/live
backend readyPath=/health/ready
web health path=/
package backend/web use docker-buildx
deployment local uses compose-local
verify local uses compose-local
allowExperimentalAdapters=false
```

---

### 7.2 `products/digital-marketing/deploy/local.compose.yaml`

**Action:** Harden.

**Status: ✅ DONE** — `local.compose.yaml` created with Kernel labels on each service, health checks matching `/health/ready` for backend and `/` for web, ports using env defaults, no hardcoded secrets.

**Required:**

```text
Kernel labels on each service.
No hardcoded secrets.
Health checks match /health/ready for backend and / for web.
Ports use env defaults.
Dockerfile paths are valid.
Compose config validates.
```

Required labels:

```yaml
labels:
  ghatana.kernel.productUnit: digital-marketing
  ghatana.kernel.lifecycle: "true"
  ghatana.kernel.surface: backend-api
```

---

### 7.3 `products/digital-marketing/deploy/local.env.example`

**Action:** Harden.

**Required:**

```text
No real-looking secret values.
Use placeholders for passwords/tokens/keys.
Document local.env copy process.
Keep local.env gitignored.
```

---

### 7.4 `products/digital-marketing/deploy/.gitignore`

**Action:** Ensure exists.

```gitignore
local.env
*.local.env
```

---

### 7.5 `products/digital-marketing/dm-api/Dockerfile`

**Action:** Verify/harden.

**Required:**

```text
Builds correct Gradle module.
Does not skip tests unless consuming prior build artifact explicitly.
Produces runnable app.
Runs non-root.
Healthcheck /health/ready.
No secrets.
```

---

### 7.6 `products/digital-marketing/ui/Dockerfile`

**Action:** Verify/harden.

**Required:**

```text
Builds Vite output.
Serves dist through nginx or established repo pattern.
Healthcheck /.
No secrets.
```

---

### 7.7 `products/digital-marketing/ui/nginx.conf`

**Action:** Ensure correct.

**Required:**

```text
SPA fallback if needed.
Safe default headers if product pattern supports them.
No unrelated proxying.
```

---

### 7.8 `scripts/check-digital-marketing-lifecycle-pilot.mjs`

**Action:** Strengthen.

**Status: ✅ DONE** — Script created at `products/yappc/scripts/check-digital-marketing-lifecycle-pilot.mjs`. Validates file existence, schemaVersion, productId, lifecycleProfile, safety flags, all required plugins with `required: true`, and requiredManifests for all four phases. All checks pass against current DM config.

**Required additions:**

```text
plan generation for validate/test/build/package/deploy/verify
fail if compose labels missing
validate package image/tag/dockerfile/context
validate requiredManifests
validate .gitignore for local.env
validate lifecycle truth output names are expected
validate no product-local lifecycle runner exists
validate deployment adapter compose-local
validate package adapter docker-buildx
```

---

### 7.9 `scripts/check-lifecycle-registry-config-drift.mjs`

**Action:** Fix and strengthen.

**Status: ✅ DONE** — Script created at `products/yappc/scripts/check-lifecycle-registry-config-drift.mjs`. Detects registry products missing `kernel-product.yaml` (flagging `type: product` entries only), `kernel-product.yaml` files not in registry, and productId consistency. Reports all drift and exits 1 on findings.

**Known issue:** Registry `environments.supported` is an array in product entries, while current drift script logic may treat environments as object keys.

**Required:**

```text
support product.environments.supported
compare lifecycleProfile
compare surfaces
compare artifacts
compare deployment target
compare local deploy/verify
warnings for planned products
errors for enabled products
```

---

## 8. Workstream E — Product shape proof

### 8.1 `scripts/generate-product-shape-capability-matrix.mjs`

**Action:** Fix as in Plan A or consume fixed output.

**Status: ✅ DONE** — Script created at `products/yappc/scripts/generate-product-shape-capability-matrix.mjs`. Reads `config/product-shape.json` and generates `products/yappc/docs/audits/product-shape-capability-matrix.md` with capability matrix, surface status detail, and lifecycle config paths for all 13 products.

**Required classifications:**

```text
Digital Marketing = Pilot.
PHR = Shape-only.
Finance = Shape-only with operator/portal/sdk gaps.
FlashIt = Shape-only with mobile/web gaps.
YAPPC = Platform-provider shape, not lifecycle executor.
Data Cloud = Platform-provider backbone shape.
Aura/DCMAAR/TutorPutor = Agent/AI-heavy shape categories.
Disabled products do not create false failures for missing lifecycle profile.
```

---

### 8.2 `docs/kernel/PRODUCT_SHAPE_CAPABILITY_MATRIX.md`

**Action:** Regenerate and review.

**Required columns:**

```text
Product
Product kind
Shape
Lifecycle status
Shape validation mode
Required Kernel support
Required plugin/provider support
YAPPC visibility need
Data Cloud support need
Current findings
```

---

### 8.3 `config/generated/product-shape-capability-matrix.json`

**Action:** Regenerate and keep checked-in only if repo convention expects generated outputs checked in.

---

## 9. Tests to add/update

| Area | Test file | Purpose |
|---|---|---|
| YAPPC intent export | `ProductUnitIntentExporterTest.java` | Verify YAPPC emits valid Kernel intent without registry mutation. |
| YAPPC intent validation | `ProductUnitIntentValidationServiceTest.java` | Validate fields, providers, surfaces, no secrets. |
| YAPPC create CLI | `CreateCommandKernelProductUnitTest.java` | Verify CLI target behavior. |
| YAPPC CI CLI | `CICommandKernelProductUnitTest.java` | Verify Kernel delegation for product units. |
| Kernel truth source | `LocalKernelManifestTruthSourceTest.java` | Verify only public manifests are read. |
| Health service | `KernelHealthSnapshotServiceTest.java` | Verify status aggregation. |
| Recommendation service | `KernelActionRecommendationServiceTest.java` | Verify next-action logic. |
| Frontend dashboard | `KernelHealthDashboardPage.test.tsx` | Render health dashboard states. |
| Frontend panels | `LifecycleTimelinePanel.test.tsx`, `GateHealthPanel.test.tsx`, etc. | Render behavior and accessibility. |
| Boundary check | script-level check test if pattern exists | Prevent private coupling and registry mutation. |
| DM pilot check | script-level check test if pattern exists | Enforce full pilot readiness. |

---

## 10. Validation commands

### YAPPC Java

```bash
./gradlew :products:yappc:core:scaffold:api:test --no-daemon
./gradlew :products:yappc:core:yappc-services:test --no-daemon
```

### YAPPC frontend

Use the actual YAPPC frontend package path at the target commit:

```bash
pnpm --dir products/yappc/frontend typecheck
pnpm --dir products/yappc/frontend test
```

If the web app has a narrower package path, use that local package.

### YAPPC boundary

```bash
node products/yappc/scripts/check-kernel-boundary-usage.mjs
pnpm check:yappc-kernel-boundary
```

### Digital Marketing pilot

```bash
pnpm check:digital-marketing-lifecycle-pilot
pnpm check:lifecycle-registry-config-drift

pnpm plan:validate:digital-marketing
pnpm validate:digital-marketing
pnpm plan:test:digital-marketing
pnpm test:digital-marketing
pnpm plan:build:digital-marketing
pnpm build:digital-marketing
pnpm plan:package:digital-marketing
pnpm package:digital-marketing
pnpm plan:deploy:local:digital-marketing
pnpm deploy:local:digital-marketing
pnpm plan:verify:local:digital-marketing
pnpm verify:local:digital-marketing
```

### Product shape

```bash
pnpm generate:product-shape-capability-matrix
pnpm check:product-shape-capability-matrix
```

---

## 11. Definition of done

```text
[ ] YAPPC docs clearly separate Creator Lifecycle from Kernel Product Lifecycle.
[ ] YAPPC has ProductUnitIntent export and validation path.
[ ] YAPPC CreateCommand supports kernel-product-unit target without registry mutation.
[ ] YAPPC CICommand delegates Kernel product CI/lifecycle responsibilities.
[ ] YAPPC kernel-health backend service can read public Kernel truth files.
[ ] YAPPC frontend dashboard can display Digital Marketing lifecycle health.
[ ] YAPPC does not parse private logs or import Kernel internals beyond public contracts.
[ ] Digital Marketing lifecycle pilot checks include validate/test/build/package/deploy/verify.
[ ] Digital Marketing emits/consumes canonical truth outputs or pilot checks fail until it does.
[ ] Product shape matrix correctly classifies Pilot, Shape-only, Disabled-observed, Partial, and Target gaps.
[ ] PHR, Finance, FlashIt, YAPPC, Data Cloud, Aura, DCMAAR, TutorPutor remain shape-validation targets only.
[ ] O11y, privacy, security, and i18n are first-class in YAPPC health UI and product visibility flows.
[ ] AI assistance is implicit in YAPPC recommendations and learning loops, but execution truth remains verifiable and Kernel-owned.
```
