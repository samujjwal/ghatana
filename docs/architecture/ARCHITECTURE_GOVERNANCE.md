# Architecture Governance

> **Authoritative source**: this file.  
> **Status**: Active  
> **Maintained by**: Platform Engineering / Architecture Team  
> **Last Updated**: 2026-05-18

---

## Overview

This document describes automated governance checks that enforce architectural invariants in the Ghatana repository. The canonical root PR command is `pnpm check:required`; the script-level governance orchestrator can be run with `node scripts/governance/run-governance-checks.mjs`.

---

## Governance Check Registry

| Check Name                     | Command                                                          | Source Files Read                                                                                                                          | Owner                | Failure Mode                                                                                                                              |
| ------------------------------ | ---------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------ | -------------------- | ----------------------------------------------------------------------------------------------------------------------------------------- |
| `domain-registry`              | `pnpm check:domain-registry`                                     | `config/domain-registry.json`, `config/canonical-product-registry.json`                                                                    | Architecture Team    | Non-zero exit — invalid domain classifications or missing registry entries                                                                |
| `product-registry-consistency` | `node scripts/governance/check-product-registry-consistency.mjs` | `config/canonical-product-registry.json`, `config/canonical-product-registry-schema.json`, `config/generated/settings-gradle-includes.kts` | Platform Engineering | Non-zero exit — blocked product has `lifecycleExecutionAllowed: true`, missing `reasonCodes`, or generated settings file is missing       |
| `package-governance`           | `node scripts/governance/check-package-governance.mjs`           | `platform/typescript/*/package.json`, `platform/typescript/*/README.md`                                                                    | Platform Engineering | Non-zero exit — missing `package.json`, missing `README.md`, deprecated package name, or missing entry point                              |
| `boundaries`                   | `node scripts/governance/check-boundaries.mjs`                   | All `platform/`, `products/`, `shared-services/` source files                                                                              | Architecture Team    | Non-zero exit — product code imports platform internals, kernel code imports product code, or cross-domain violations                     |
| `doc-claims`                   | `node scripts/governance/check-doc-current-state-claims.mjs`     | All `docs/**/*.md`, `products/**/*.md`, `config/documentation-authority-map.json`                                                          | Architecture Team    | Non-zero exit — documentation makes current-state claims that contradict reality, or references authoritative sources without citing them |
| `duplication-exceptions`       | `pnpm check:duplication-exceptions`                              | `config/duplication-exceptions.json`, `config/duplication-exceptions.schema.json`                                                          | Platform Engineering | Non-zero exit — invalid exception schema or expired exception entry                                                                       |

---

## Master Orchestrator

Run all governance checks at once:

```bash
node scripts/governance/run-governance-checks.mjs
```

This runs all six checks in order and reports a combined pass/fail result.

---

## Individual Check Details

### 1. Domain Registry (`check-domain-registry`)

**Script**: `scripts/governance/check-domain-registry.mjs`  
**Delegates to**: `scripts/validate-domain-registry.mjs`  
**Configuration**: `config/domain-registry.json`  
**Authoritative source**: `config/canonical-product-registry.json`

**What it enforces**:

- All domains use valid `classification` values (`existing-executable`, `existing-partial`, `declared-only`, `target-architecture`, `anti-pattern`)
- All referenced products exist in the canonical product registry
- No orphaned domain entries

**Vocabulary** (stable — do not change without updating the validator):

- `classification`: field name (NOT `status`)
- Valid values: `existing-executable`, `existing-partial`, `declared-only`, `target-architecture`, `anti-pattern`

---

### 2. Product Registry Consistency (`check-product-registry-consistency`)

**Script**: `scripts/governance/check-product-registry-consistency.mjs`  
**Delegates to**: `scripts/validate-product-registry.mjs`  
**Configuration**: `config/canonical-product-registry.json`

**What it enforces**:

- `digital-marketing` is the ONLY product with `lifecycleExecutionAllowed: true` (lifecycle pilot)
- Products with `lifecycleReadiness.status: "blocked"` must have `reasonCodes`
- `lifecycleConfigPath` must reference a file that exists
- `config/generated/settings-gradle-includes.kts` must exist with the "DO NOT EDIT MANUALLY" header

**Blocked products** (must NOT have `lifecycleExecutionAllowed: true`):

- `phr`, `finance`, `flashit`, `data-cloud`, `yappc`

---

### 3. Package Governance (`check-package-governance`)

**Script**: `scripts/governance/check-package-governance.mjs`  
**Configuration**: `platform/typescript/*/package.json`

**What it enforces**:

- Every `platform/typescript/<name>/` directory must have a `package.json`
- Every package must have a `README.md`
- No deprecated package names (`@ghatana/ui`, `@ghatana/utils`, `@ghatana/accessibility-audit`, canvas split variants)
- Entry point must exist: `src/index.ts`, `src/index.tsx`, `index.ts`, `index.tsx`, `index.mjs`, `index.js`

**Excluded from entry point check** (app shells + tooling):

- `ghatana-studio`, `platform-events`, `eslint-plugin`, `ds-governance`, `ds-schema`, `ds-generator`, `ds-registry`

**Orphan-only directories** (excluded from package governance checks):

- `audit-ui`, `nlp-ui`, `privacy-ui`, `security-ui`, `voice-ui`, `selection-ui`, `browser-events`, `router-facade`

---

### 4. Architecture Boundaries (`check-boundaries`)

**Script**: `scripts/governance/check-boundaries.mjs`  
**Delegates to**:

- `scripts/check-domain-boundaries.mjs`
- `scripts/check-kernel-boundaries.mjs`
- `scripts/check-platform-product-boundaries.mjs`

**What it enforces**:

- Domain boundary rules from `config/domain-registry.json`
- Kernel boundary rules: products must not import kernel internals directly
- Platform/product boundary rules: platform packages must not import product code

**Configuration**: `config/architecture-compliance-allowlist.json`

---

### 5. Documentation Current-State Claims (`check-doc-claims`)

**Script**: `scripts/governance/check-doc-current-state-claims.mjs`  
**Delegates to**:

- `scripts/check-current-state-claims.mjs`
- `scripts/check-doc-authority.mjs`
- `scripts/check-doc-truth.mjs`

**What it enforces**:

- Documentation "current state" claims match the actual codebase state
- Documentation that redefines an authoritative rule must reference the authoritative source
- Files that mention canonical patterns (e.g. "lifecycle contract") must reference the authoritative document

**Authoritative documents**:

- `config/canonical-product-registry.json` — product registry rules
- `docs/kernel/PRODUCT_LIFECYCLE_CONTRACT.md` — lifecycle contract rules
- `config/documentation-authority-map.json` — full authority map

**Note**: `check-doc-authority.mjs` scans the full document tree and may take 30–60 seconds.

---

### 6. Duplication Exceptions (`check-duplication-exceptions`)

**Script**: `scripts/governance/check-duplication-exceptions.mjs`  
**Delegates to**: `scripts/validate-duplication-exceptions.mjs`  
**Configuration**: `config/duplication-exceptions.json`, `config/duplication-exceptions.schema.json`

**What it enforces**:

- Exception schema is valid
- No expired exceptions remain without renewal or resolution
- Exception entries reference real files that still exist

---

## Coverage Gaps

The following existing scripts are NOT included in the master governance orchestrator, due to pre-existing failures or excessive runtime:

| Script                                                 | Reason Not Included                                                                                                 | Tracked Fix                |
| ------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------- | -------------------------- |
| `scripts/check-platform-package-governance.js`         | Pre-existing failures: `tokens/tsconfig.json` must extend `tsconfig.base.json`; `wizard` missing `"type": "module"` | Follow-on remediation task |
| `scripts/generate-product-shape-capability-matrix.mjs` | Generator, not a check — run separately via `pnpm generate:product-shape-capability-matrix`                         | N/A                        |

---

## Adding a New Governance Check

1. Create `scripts/governance/check-<name>.mjs`
2. Export a `run<Name>Checks()` function
3. Add the check to `GOVERNANCE_CHECKS` in `scripts/governance/run-governance-checks.mjs`
4. Add a `check:<name>` script to `package.json`
5. Add a row to the registry table above
6. Write at least one happy-path test in `scripts/governance/__tests__/`

---

## Related Documents

- [Domain Registry](../../config/domain-registry.json) — canonical domain classifications
- [Product Registry](../../config/canonical-product-registry.json) — canonical product lifecycle state
- [Documentation Authority Map](../../config/documentation-authority-map.json) — authoritative source map
- [Duplication Exceptions](../../config/duplication-exceptions.json) — approved duplication exceptions
- [Architecture Rules](ARCHITECTURE_RULES.md) — design-level rules
