# Quarterly Boundary Audit Checklist

**Purpose:** Systematic review of monorepo health and boundary compliance  
**Frequency:** Quarterly (Q1: Jan, Q2: Apr, Q3: Jul, Q4: Oct)  
**Owner:** Platform Architecture Team  
**First Audit:** Q2 2026 (April 2026)  
**Time Required:** ~4 hours for full audit  

---

## Pre-Audit Setup

```bash
# 1. Ensure you're on a fresh main branch
git checkout main && git pull

# 2. Run all automated checks first
./scripts/architecture-score-gate.sh
./scripts/check-cross-product-deps.sh
./gradlew :platform:java:kernel:compileJava :platform:java:agent-core:compileJava

# 3. Get module count baseline
grep -c 'include(' settings.gradle.kts
```

---

## Section 1: Module Count & Growth

| Item | Check | Pass Criteria | Status |
|------|-------|---------------|--------|
| 1.1 | Java module count (`grep -c 'include(' settings.gradle.kts`) | < 60 modules | ⬜ |
| 1.2 | New modules added since last audit | Each has MODULE_ADMISSION_CHECKLIST sign-off | ⬜ |
| 1.3 | Modules with < 5 Java files | Candidate for merging; document rationale if kept | ⬜ |
| 1.4 | Modules with > 200 Java files | Candidate for splitting; document rationale if kept | ⬜ |

**Commands:**
```bash
# Count Java modules
grep 'include(":platform:java\|include(":products' settings.gradle.kts | wc -l

# Find tiny modules (< 5 files)
for dir in platform/java/*/; do
  count=$(find "$dir/src/main" -name "*.java" 2>/dev/null | wc -l | tr -d ' ')
  [[ "$count" -lt 5 && "$count" -gt 0 ]] && echo "$count $dir"
done | sort -n
```

---

## Section 2: Boundary Violations

| Item | Check | Pass Criteria | Status |
|------|-------|---------------|--------|
| 2.1 | Run `./scripts/check-cross-product-deps.sh` | PASS (no new violations) | ⬜ |
| 2.2 | Run `./scripts/architecture-score-gate.sh` | Score ≥ 80/100 | ⬜ |
| 2.3 | Deprecated `shared:*` module references | 0 references | ⬜ |
| 2.4 | Products importing from `platform/java/internal` packages | 0 violations | ⬜ |
| 2.5 | REMEDIATION: Count of legacy approved cross-product deps | Decreasing trend | ⬜ |

**Commands:**
```bash
# Check for deprecated shared references
grep -r ':shared:' products/ --include="*.gradle.kts" | wc -l

# Check for upward dependency violations
grep -r 'project(":products:' platform/ --include="*.gradle.kts"
```

---

## Section 3: Dependency Hygiene

| Item | Check | Pass Criteria | Status |
|------|-------|---------------|--------|
| 3.1 | Version catalog completeness | All external deps use `libs.*` aliases | ⬜ |
| 3.2 | Direct version strings in build files | 0 occurrences | ⬜ |
| 3.3 | Circular dependency check | `./gradlew build --continue` no cycle errors | ⬜ |
| 3.4 | Duplicate library versions | Run `./gradlew dependencyInsight` spot check | ⬜ |

**Commands:**
```bash
# Find hardcoded version strings (should use libs.* catalog)
grep -r 'version "' products/ platform/ --include="*.gradle.kts" | grep -v '#' | head -20

# Check for version conflicts (sample a few key libs)
./gradlew :products:aep:platform-bundle:dependencyInsight --dependency activej --no-daemon
```

---

## Section 4: Code Quality Gates

| Item | Check | Pass Criteria | Status |
|------|-------|---------------|--------|
| 4.1 | JavaDoc coverage on platform libs | All public classes documented | ⬜ |
| 4.2 | `@doc.*` tags on new platform classes | 100% coverage for additions | ⬜ |
| 4.3 | Test coverage for platform modules | All platform modules have tests | ⬜ |
| 4.4 | Async tests use `EventloopTestBase` | No direct `.getResult()` calls | ⬜ |
| 4.5 | TypeScript strict mode | No `any` types in new code | ⬜ |

**Commands:**
```bash
# Check for missing @doc tags in platform (spot check)
grep -r 'public class\|public interface\|public record' platform/java/ --include="*.java" -l | \
  xargs grep -L '@doc.type' | head -10

# Check for banned .getResult() on Promises
grep -r '\.getResult()' platform/ products/ --include="*.java" | grep -v test | head -10
```

---

## Section 5: Architecture Compliance

| Item | Check | Pass Criteria | Status |
|------|-------|---------------|--------|
| 5.1 | `settings.gradle.kts` reflects current shared/platform modules | Active, relocated, and archived modules documented in root settings | ⬜ |
| 5.2 | `MODULE_ADMISSION_CHECKLIST.md` used for new modules | Each new module has checklist row | ⬜ |
| 5.3 | ActiveJ-only (no Spring Reactor/WebFlux in platform) | 0 Reactor imports in platform | ⬜ |
| 5.4 | No `CompletableFuture` mixing with ActiveJ in platform | 0 violations | ⬜ |
| 5.5 | Naming conventions followed for new modules | Follows `docs/NAMING_CONVENTIONS.md` | ⬜ |

**Commands:**
```bash
# Check for Spring Reactor imports (not allowed in platform)
grep -r 'import reactor\.' platform/java/ --include="*.java" | head -10

# Check for CompletableFuture mixing
grep -r 'CompletableFuture' platform/java/ --include="*.java" | grep -v test | head -10
```

---

## Section 6: TypeScript Health

| Item | Check | Pass Criteria | Status |
|------|-------|---------------|--------|
| 6.1 | TypeScript build passes | `pnpm build` exits 0 | ⬜ |
| 6.2 | No cross-product TS imports | ESLint `no-cross-product-imports` 0 errors | ⬜ |
| 6.3 | Banned libraries not added | Check `ghatana-architecture-rules.js` list | ⬜ |
| 6.4 | Package naming follows conventions | Kebab-case, no acronyms | ⬜ |

---

## Section 7: Remediation Tracking

Track items from previous audits that are in progress:

| Issue | First Found | Target Resolution | Owner | Status |
|-------|-------------|-------------------|-------|--------|
| yappc → data-cloud cross-dep | Q1 2026 | Q3 2026 | YAPPC Team | 🔄 In Progress |
| software-org → aep cross-dep | Q1 2026 | Q3 2026 | SoftOrg Team | 🔄 In Progress |
| app-platform → aep cross-dep | Q1 2026 | Q4 2026 | Platform Team | ⬜ Not Started |
| virtual-org → aep cross-dep | Q1 2026 | Q3 2026 | VirtualOrg Team | ⬜ Not Started |

---

## Audit Summary Template

Copy this to the audit record after completing the checklist:

```markdown
## Quarterly Audit Record — [QUARTER] [YEAR]

**Date:** YYYY-MM-DD
**Auditor:** [Name]
**Architecture Score:** [X]/100
**Cross-Product Dep Violations (new):** [N]
**Java Module Count:** [N] (delta: +/- from last audit)

### Key Findings
- [Finding 1]
- [Finding 2]

### Actions Required
- [ ] [Action 1] — Owner: [Name], Due: [Date]
- [ ] [Action 2] — Owner: [Name], Due: [Date]

### Trend
- Module count: [trend]
- Boundary violations: [trend]
- Architecture score: [trend]
```

---

## Audit History

| Quarter | Date | Auditor | Score | Module Count | Cross-Prod Deps | Notes |
|---------|------|---------|-------|--------------|-----------------|-------|
| Q1 2026 | 2026-03-21 | Platform Team (initial) | — | ~55 | 27 (all approved) | Baseline after consolidation |

---

*This checklist is maintained at `docs/QUARTERLY_BOUNDARY_AUDIT_CHECKLIST.md`.*  
*Related: [../settings.gradle.kts](../settings.gradle.kts), [MODULE_ADMISSION_CHECKLIST.md](MODULE_ADMISSION_CHECKLIST.md), [NAMING_CONVENTIONS.md](NAMING_CONVENTIONS.md)*
