# Quarterly Monorepo Boundary Review Process

> **Owner**: Platform Architecture  
> **Cadence**: Every quarter (first two weeks of January, April, July, October)  
> **Attendees**: Platform Leads, Product Architects, DevEx  
> **Tooling Reference**: `docs/audits/MONOREPO_BOUNDARY_RIGHT_SIZING_AUDIT_2026-03-22.md`

---

## Purpose

Prevent boundary drift from accumulating between architectural sprints.  
Each review produces a health score and an action list that feeds directly into the next sprint's P0/P1 planning.

---

## Pre-Review Checklist (complete before the meeting)

Run the automated boundary checks and save their output as the baseline for the
discussion:

```bash
# 1. Generate dependency-alignment report
./gradlew dependencyInsight 2>&1 | tee build/reports/dependency-alignment.txt

# 2. CI boundary checks (same checks that run in CI)
pnpm install --frozen-lockfile
./scripts/check-deprecated-ui.sh
node scripts/check-architecture-compliance.js
bash scripts/check-cross-product-deps.sh

# 3. Shared-service ownership audit
for dir in shared-services/*/; do
  [ -f "${dir}OWNER.md" ] && echo "✓ ${dir}" || echo "MISSING OWNER.md: ${dir}"
done

# 4. Stale cross-product Gradle edges
bash scripts/check-cross-product-deps.sh | grep "UNAPPROVED" | wc -l

# 5. Orphaned platform modules (no product consumers)
./gradlew projects 2>/dev/null | grep platform | while read mod; do
  count=$(grep -r "$mod" products/ --include="build.gradle.kts" 2>/dev/null | wc -l)
  [ "$count" -eq 0 ] && echo "ORPHANED: $mod"
done
```

---

## Review Agenda (2 hours)

### 1. Drift Scan (30 min)

Walk through the output of the pre-review scripts and triage each new issue.

- [ ] New cross-product Java dependencies since last review
- [ ] New cross-product TypeScript/pnpm workspace dependencies
- [ ] New shared-service candidates (services used by 3+ products)
- [ ] Deprecated packages still referenced anywhere
- [ ] Products missing OWNER.md

**Decision Criteria for New Cross-Product Deps:**

| Signal | Action |
|---|---|
| One-off by a single product | Reject — localise the code |
| 2+ products, stable interface | Consider promotion to `libs/*` |
| Runtime coupling (e.g. sidecar) | Formalise as a shared-service with OWNER.md |

### 2. Shared-Services Admission (20 min)

Review any new shared-service proposals raised since last review.

Apply the [Module Admission Checklist](../MODULE_ADMISSION_CHECKLIST.md) to each
candidate.

- [ ] Does it satisfy the "3+ independent product consumers" rule?
- [ ] Is it operationally real (deployed, not just referenced)?
- [ ] Does it have a clear owner?
- [ ] Does it have a documented API surface (OpenAPI / Protobuf)?

### 3. Deprecation Enforcement (20 min)

Check the sunset dates in `docs/DEPRECATION_POLICY.md`.

- [ ] Are any packages past their sunset date?
- [ ] Are migration guides complete?
- [ ] Schedule removal work if not yet scheduled.

### 4. Health Score Update (30 min)

Update the boundary health score in the master audit document
(`docs/audits/MONOREPO_BOUNDARY_RIGHT_SIZING_AUDIT_2026-03-22.md` or its
successor).

Score dimensions (0–10 each):

| Dimension | Scoring Guide |
|---|---|
| **Cross-product isolation** | 10 = zero unapproved cross-product deps |
| **Shared-service ownership** | 10 = every shared-service has OWNER.md + ADR |
| **Package sprawl** | 10 = zero packages with < 3 consumers AND no clear plan |
| **Deprecated package count** | 10 = zero deprecated packages in active use |
| **CI guardrail coverage** | 10 = all agreed rules are in `.github/workflows/boundary-check.yml` |

### 5. Action Items (20 min)

Produce a prioritised list of issues for the next sprint.

Use the P0/P1/P2/P3 priority framework from the execution plan:

- **P0** (do this sprint): Active drift, broken policies, deprecated packages past
  sunset date.
- **P1** (next sprint): Major boundary violations that block product independence.
- **P2** (this quarter): Cosmetic/organizational improvements.
- **P3** (ongoing): Governance tooling enhancements.

---

## Post-Review Outputs

1. Updated audit document with new health scores and date.
2. Jira/GitHub issues created for P0 and P1 items.
3. `docs/execution-plans/` entry for the current quarter's execution plan.
4. Minutes sent to `#platform-architecture` channel.

---

## Historical Records

| Quarter | Health Score | Key Actions Taken |
|---|---|---|
| Q1 2026 | 68/100 | P0 drift stopped, AEP bundle split, kernel-capabilities narrowed, YAPPC libs decomposed, DCMAAR thin wrappers consolidated |

*(Add a new row after each quarterly review.)*

---

## Reference Commands

```bash
# Count total Gradle modules
./gradlew projects 2>/dev/null | grep "Project '" | wc -l

# Count pnpm workspace packages
pnpm list --depth=0 --json 2>/dev/null | jq 'length'

# Find all cross-product Gradle edges (quick scan)
grep -r "products:" products/ --include="build.gradle.kts" \
  | grep -v "^products/[^/]*/[^/]*/[^/]*/\|^Binary" \
  | grep -oP "products:[^\"')]*" | sort -u

# Find TypeScript packages with no consumers
for pkg in $(ls products/*/frontend/libs/ 2>/dev/null); do
  count=$(grep -r "@${pkg}" products/ --include="package.json" | wc -l)
  [ "$count" -le 1 ] && echo "Low-consumer package: $pkg (consumers: $count)"
done
```
