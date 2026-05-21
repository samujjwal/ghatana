# Data Cloud Route/Runtime-Truth Drift Gate

**Purpose**: Document the preservation of Data Cloud route manifest generation and drift detection as a platform-provider release criteria.

**Last Updated**: 2026-05-20

---

## Overview

Data Cloud route manifest generation and runtime-truth drift detection is a critical platform-provider boundary gate. The source of truth is `RouteSecurityRegistry.java`, which generates both a canonical route manifest and UI runtime truth TypeScript code.

---

## Source of Truth

### RouteSecurityRegistry.java
- **Location**: `products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/RouteSecurityRegistry.java`
- **Purpose**: Authoritative registry of Data Cloud runtime routes and security metadata
- **Responsibility**: Every live runtime route must be represented here; production-like profiles fail closed when metadata is missing
- **Metadata**: Includes method, path, sensitivity (PUBLIC/INTERNAL/SENSITIVE/CRITICAL), auth requirements, tenant requirements, policy requirements, idempotency, description, runtime truth surface

---

## Generation Pipeline

### Route Manifest Generator Script
- **Location**: `products/data-cloud/scripts/generate-route-manifest.mjs`
- **Purpose**: Parses RouteSecurityRegistry.java and generates:
  1. Canonical route manifest (JSON)
  2. UI RuntimeTruthPosture.generated.ts
  3. OpenAPI validation report

### Generated Artifacts

#### 1. Route Manifest (JSON)
- **Location**: `products/data-cloud/config/route-manifest.json`
- **Schema**: 
  ```json
  {
    "version": "generated-YYYY-MM-DD",
    "lastUpdated": "ISO-8601 timestamp",
    "generatedFrom": "RouteSecurityRegistry.java",
    "routes": [...]
  }
  ```
- **Route Entry**: method, path, sensitivity, requiresAuth, requiresTenant, requiresPolicy, idempotent, description, runtimeTruthSurface, category

#### 2. UI Runtime Truth (TypeScript)
- **Location**: `products/data-cloud/delivery/ui/src/lib/routing/RuntimeTruthPosture.generated.ts`
- **Purpose**: Canonical runtime truth for Data Cloud HTTP routes
- **Used For**:
  - UI feature gating (show/hide actions based on sensitivity)
  - Client SDK generation (route availability per profile)
  - Documentation generation (API matrix)
  - CI/CD validation (drift detection)

---

## Drift Detection

### Check Mode
The generator script supports `--check` mode for drift detection:

```bash
node scripts/generate-route-manifest.mjs --check
```

**Behavior**:
- Parses RouteSecurityRegistry.java
- Generates expected manifest and UI truth
- Compares with existing files
- Exits with error if drift detected
- Reports which artifacts are out of date

### Package Scripts
- `generate:route-manifest`: Regenerates artifacts (write mode)
- `check:route-manifest`: Checks for drift (check mode)

---

## CI Integration

### GitHub Actions
- **Workflow**: `.github/workflows/data-cloud-ci.yml`
- **Step**: Line 286 - `node scripts/generate-route-manifest.mjs --check`
- **Trigger**: Runs on every Data Cloud CI pipeline
- **Failure Interpretation**: Drift detected - route manifest or UI truth is out of date with RouteSecurityRegistry.java

### Validation Commands
As documented in comp-decomp-todo.md Phase 5.3:

```bash
cd products/data-cloud
node scripts/generate-route-manifest.mjs --check
./gradlew :products:data-cloud:delivery:launcher:test --tests com.ghatana.datacloud.launcher.arch.DataCloudPlaneBoundaryTest --no-daemon
```

---

## Release Criteria

Per comp-decomp-todo.md Section 5.3:

> Promote Data Cloud route manifest generation to platform-provider release criteria. The source is RouteSecurityRegistry.java; generated route manifest and UI runtime truth must be committed; route sensitivity/auth/tenant/policy metadata must be non-stale.

### Requirements for Data Cloud Release
1. Route manifest must be generated from current RouteSecurityRegistry.java
2. UI runtime truth must be generated from current route manifest
3. Both artifacts must be committed to the repository
4. Route metadata (sensitivity, auth, tenant, policy) must be non-stale
5. Drift check must pass in CI
6. Plane boundary tests must pass

---

## Current Status

### Artifacts
- **Route Manifest**: Exists at `products/data-cloud/config/route-manifest.json`
  - Version: generated-2026-05-20
  - Last Updated: 2026-05-20T21:37:42.754Z
  - Generated From: RouteSecurityRegistry.java
  - Total Routes: (see manifest for count)

- **UI Runtime Truth**: Exists at `products/data-cloud/delivery/ui/src/lib/routing/RuntimeTruthPosture.generated.ts`
  - Generated At: 2026-05-20T21:37:42.754Z
  - Source: RouteSecurityRegistry.java
  - Purpose: DC-P0-03: Runtime truth for UI feature gating and route visibility

### CI Integration
- **GitHub Actions**: Drift check integrated in data-cloud-ci.yml
- **Status**: Active and enforced

### Validation
- **Script**: Functional with --check mode
- **Package Scripts**: Available in products/data-cloud/delivery/ui/package.json
- **Documentation**: Documented in products/data-cloud/docs/ROUTE_MANIFEST_SYSTEM.md

---

## Gate Enforcement

### Fail-Closed Behavior
- If RouteSecurityRegistry.java is modified but artifacts are not regenerated, CI will fail
- If artifacts are manually edited (not generated), CI will fail
- If route metadata is stale, CI will fail
- If plane boundary tests fail, CI will fail

### Remediation
1. Regenerate artifacts: `cd products/data-cloud && node scripts/generate-route-manifest.mjs`
2. Commit both artifacts to repository
3. Ensure all route metadata is current and accurate
4. Run plane boundary tests to verify
5. Push changes to trigger CI validation

---

## Platform-Provider Boundary Implications

Per comp-decomp-todo.md Section 9.1:

> Hard rules:
> - Kernel must not import Data Cloud plane internals.
> - Data Cloud must not implement product lifecycle execution.
> - Platform mode must fail closed if Data Cloud provider health is missing.
> - Tenant identity must derive from authenticated identity in production/staging/sovereign profiles.
> - Route manifest/runtime-truth drift must block Data Cloud provider release.

### Route Truth as Platform-Provider Boundary
The route manifest and runtime truth serve as:
1. **Contract**: Define the external API surface of Data Cloud
2. **Validation**: Ensure API surface changes are intentional and reviewed
3. **Integration Point**: Allow Kernel and other products to discover Data Cloud capabilities
4. **Gate**: Block releases if API surface drifts from source of truth

---

## Maintenance

### When to Regenerate
- When RouteSecurityRegistry.java is modified (add/remove/change routes)
- When route metadata is updated (sensitivity, auth, tenant, policy changes)
- When route descriptions are updated
- When runtime truth surface classifications change

### Verification
- Run `node scripts/generate-route-manifest.mjs --check` locally before committing
- Ensure CI passes after pushing changes
- Verify generated artifacts match expected state
- Review route metadata for accuracy and completeness

---

## Conclusion

The Data Cloud route/runtime-truth drift gate is preserved and enforced:
- Source of truth: RouteSecurityRegistry.java
- Generation pipeline: generate-route-manifest.mjs
- Artifacts: route-manifest.json + RuntimeTruthPosture.generated.ts
- Drift detection: --check mode integrated in CI
- Release criteria: Enforced via data-cloud-ci.yml
- Platform-provider boundary: Serves as API surface contract

This gate ensures that Data Cloud API surface changes are intentional, reviewed, and consistent with the source of truth, preventing silent drift and maintaining platform-provider boundary discipline.
