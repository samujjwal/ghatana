# Validation & Audit Update Strategy
## How to Validate Implementation & Update PLATFORM_TEST_AUDIT.md

**Created**: 2026-04-04  
**Purpose**: Ensure 100% coverage through systematic validation and documentation  
**Frequency**: Weekly audit runs + updates

---

## 1. Validation Workflow

### 1.1 Pre-Merge Validation (Per Module)

Before merging any module's test implementation:

```bash
#!/bin/bash
# Validate single module before merge

MODULE="platform:java:identity"

# Step 1: Build
./gradlew ${MODULE}:clean ${MODULE}:build

# Step 2: Run all tests
./gradlew ${MODULE}:test ${MODULE}:integrationTest

# Step 3: Coverage report
./gradlew ${MODULE}:jacocoTestReport

# Step 4: Lint & type checks
./gradlew ${MODULE}:check ${MODULE}:spotbugs ${MODULE}:pmd

# Step 5: Architecture validation
./gradlew checkArchitect checkDoc

# Step 6: Extract coverage metrics
./gradlew ${MODULE}:coverageReport --info \
  | grep -E "^(Line|Branch|Instruction).*Coverage:" > /tmp/coverage.txt

# Display summary
echo "✅ Pre-merge validation complete"
cat /tmp/coverage.txt
```

### 1.2 Post-Implementation Audit Run (Weekly)

After module implementation completes:

```bash
#!/bin/bash
# Run full audit on completed module

MODULE=$1  # e.g., "identity"

echo "🔍 Running full audit on ${MODULE}..."

# 1. Coverage metrics
COVERAGE=$(./gradlew platform:java:${MODULE}:jacocoTestReport \
  --quiet 2>/dev/null | grep "CLASS" | awk '{print $NF}' | sed 's/%//')

# 2. Test count
UNIT_TESTS=$(find platform/java/${MODULE}/src/test -name "*Test.java" | wc -l)
INTEGRATION_TESTS=$(find platform/java/${MODULE}/src/test -name "*IT.java" | wc -l)
TOTAL_TESTS=$((UNIT_TESTS + INTEGRATION_TESTS))

# 3. Build validation
BUILD_RESULT=$(./gradlew platform:java:${MODULE}:build 2>&1 | tail -1)

# 4. Lint validation
LINT_RESULT=$(./gradlew platform:java:${MODULE}:check 2>&1 | tail -1)

# 5. Doc validation
DOC_RESULT=$(./gradlew checkDoc platform:java:${MODULE} 2>&1 | tail -1)

# Output results
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "AUDIT RESULTS: ${MODULE}"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "Coverage: ${COVERAGE}%"
echo "Tests: ${TOTAL_TESTS} (Unit: ${UNIT_TESTS}, Integration: ${INTEGRATION_TESTS})"
echo "Build: $(echo ${BUILD_RESULT} | grep -q 'BUILD SUCCESS' && echo '✅ PASS' || echo '❌ FAIL')"
echo "Lint: $(echo ${LINT_RESULT} | grep -q 'errors' && echo '❌ FAIL' || echo '✅ PASS')"
echo "Docs: $(echo ${DOC_RESULT} | grep -q 'FAIL' && echo '❌ FAIL' || echo '✅ PASS')"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

# Return exit code based on all passing
[[ "${COVERAGE}" -ge "95" ]] && \
[[ "${BUILD_RESULT}" =~ "BUILD SUCCESS" ]] && \
[[ ! "${LINT_RESULT}" =~ "errors" ]] && \
exit 0 || exit 1
```

### 1.3 Full Platform Audit (Weekly)

Run complete audit on all 47 modules:

```bash
#!/bin/bash
# Full platform test audit - run every Friday

echo "📊 Full Platform Test Audit - $(date)"
echo "Scope: All 47 modules"
echo ""

declare -A results

# Java modules (28)
JAVA_MODULES=(
  "agent-catalog" "agent-core" "agent-memory" "ai-integration" "audit" "audio-video"
  "billing" "cache" "config" "connectors" "core" "data-governance" "database"
  "distributed-cache" "domain" "governance" "http" "identity" "incident-response"
  "kernel" "kernel-persistence" "observability" "plugin" "policy-as-code"
  "runtime" "security" "security-analytics" "testing" "tool-runtime" "workflow"
)

# TypeScript packages (14)
TS_PACKAGES=(
  "accessibility-audit" "api" "canvas" "charts" "code-editor" "design-system"
  "i18n" "platform-shell" "realtime" "sso-client" "theme" "tokens"
  "ui-integration" "contracts"
)

# Run Java audit
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "JAVA MODULES (28)"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

for module in "${JAVA_MODULES[@]}"; do
  ./scripts/audit-module.sh platform:java:${module} | tee -a /tmp/audit-${module}.log
  COVERAGE=$(grep "Coverage:" /tmp/audit-${module}.log | awk '{print $NF}' | sed 's/%//')
  
  if [[ $COVERAGE -ge 95 ]]; then
    results["${module}"]="✅ ${COVERAGE}%"
  elif [[ $COVERAGE -ge 80 ]]; then
    results["${module}"]="⚠️ ${COVERAGE}%"
  else
    results["${module}"]="❌ ${COVERAGE}%"
  fi
done

# Run TypeScript audit
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "TYPESCRIPT PACKAGES (14)"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

for pkg in "${TS_PACKAGES[@]}"; do
  cd platform/typescript/${pkg} 2>/dev/null || continue
  COVERAGE=$(pnpm test:coverage 2>/dev/null | grep "Statements" | awk '{print $NF}' | sed 's/%//')
  cd - > /dev/null
  
  if [[ $COVERAGE -ge 95 ]]; then
    results["${pkg}"]="✅ ${COVERAGE}%"
  elif [[ $COVERAGE -ge 80 ]]; then
    results["${pkg}"]="⚠️ ${COVERAGE}%"
  else
    results["${pkg}"]="❌ ${COVERAGE}%"
  fi
done

# Summary report
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "COVERAGE SUMMARY"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

PASS=0
YELLOW=0
FAIL=0

for module in "${!results[@]}"; do
  status="${results[$module]}"
  echo "${status} ${module}"
  
  if [[ $status =~ "✅" ]]; then
    ((PASS++))
  elif [[ $status =~ "⚠️" ]]; then
    ((YELLOW++))
  else
    ((FAIL++))
  fi
done

echo ""
echo "📊 TOTALS"
echo "  ✅ PASS (≥95%): ${PASS}/47"
echo "  ⚠️ PARTIAL (80-94%): ${YELLOW}/47"
echo "  ❌ FAIL (<80%): ${FAIL}/47"
echo ""

# Save audit report
cp /tmp/audit-*.log ./reports/audit-$(date +%Y-%m-%d).log
```

---

## 2. Updating PLATFORM_TEST_AUDIT.md

### 2.1 Weekly Update Process

Every Friday, run the full audit and update the markdown file:

```python
#!/usr/bin/env python3
"""
Update PLATFORM_TEST_AUDIT.md with latest coverage metrics.
Run after full platform audit completes.
"""

import json
import re
from datetime import datetime
from pathlib import Path
import subprocess

class AuditUpdater:
    def __init__(self, audit_file: str):
        self.audit_file = audit_file
        self.coverage_data = {}
        self.last_update = datetime.now().isoformat()
    
    def collect_coverage(self) -> dict:
        """Run coverage tools and collect metrics."""
        coverage = {}
        
        # Java coverage
        java_modules = [
            "identity", "security", "security-analytics", "runtime",
            "incident-response", "policy-as-code", "plugin", 
            "tool-runtime", "observability"
            # ... add all 28 Java modules
        ]
        
        for module in java_modules:
            result = subprocess.run(
                [
                    "./gradlew",
                    f"platform:java:{module}:jacocoTestReport",
                    "--quiet"
                ],
                capture_output=True,
                text=True
            )
            
            # Parse coverage from Jacoco report
            if result.returncode == 0:
                matches = re.findall(r'CLASS.*?(\d+)%', result.stdout)
                if matches:
                    coverage[module] = int(matches[0])
                else:
                    coverage[module] = 0
        
        # TypeScript coverage
        ts_packages = [
            "design-system", "api", "canvas", "realtime",
            # ... add all 14 TypeScript packages
        ]
        
        for pkg in ts_packages:
            result = subprocess.run(
                ["pnpm", "test:coverage"],
                cwd=f"platform/typescript/{pkg}",
                capture_output=True,
                text=True
            )
            
            if result.returncode == 0:
                matches = re.findall(r'Statements\s+:\s+(\d+)%', result.stdout)
                if matches:
                    coverage[pkg] = int(matches[0])
                else:
                    coverage[pkg] = 0
        
        return coverage
    
    def update_coverage_table(self, audit_content: str, coverage: dict) -> str:
        """Update the main coverage table in audit file."""
        
        # Find the coverage table section
        table_pattern = r'(\| Module \| Line Coverage \|.*?\n\|.*?\n(?:\|.*?\n)+)'
        
        # Build new table rows
        rows = []
        for module, percent in sorted(coverage.items()):
            if percent >= 95:
                status = "✅"
            elif percent >= 80:
                status = "⚠️"
            else:
                status = "❌"
            
            rows.append(f"| **{module}** | {percent}% | {status} |")
        
        new_table = "| Module | Coverage | Status |\n|--------|----------|--------|\n" + "\n".join(rows)
        
        # Replace in content
        updated_content = re.sub(table_pattern, new_table, audit_content, flags=re.MULTILINE)
        
        return updated_content
    
    def update_status_indicators(self, audit_content: str, coverage: dict) -> str:
        """Update status indicators (✅, ⚠️, ❌) throughout document."""
        
        for module, percent in coverage.items():
            if percent >= 95:
                # Update from ⚠️ or ❌ to ✅
                audit_content = re.sub(
                    rf'(\| {module}\s+\|.*?)⚠️|❌',
                    r'\1✅',
                    audit_content
                )
            elif percent >= 80:
                # Update from ❌ to ⚠️
                audit_content = re.sub(
                    rf'(\| {module}\s+\|.*?)❌',
                    r'\1⚠️',
                    audit_content
                )
        
        return audit_content
    
    def update_metadata(self, audit_content: str) -> str:
        """Update audit metadata (date, last updated)."""
        
        audit_content = re.sub(
            r'Last Updated: \d{4}-\d{2}-\d{2}',
            f'Last Updated: {datetime.now().date()}',
            audit_content
        )
        
        return audit_content
    
    def write_update(self, content: str):
        """Write updated content back to file."""
        
        with open(self.audit_file, 'w') as f:
            f.write(content)
        
        print(f"✅ Updated {self.audit_file}")
    
    def run(self):
        """Execute full audit update."""
        
        print("📊 Collecting coverage metrics...")
        coverage = self.collect_coverage()
        
        print("📝 Reading audit file...")
        with open(self.audit_file, 'r') as f:
            audit_content = f.read()
        
        print("🔄 Updating coverage tables...")
        audit_content = self.update_coverage_table(audit_content, coverage)
        
        print("🔄 Updating status indicators...")
        audit_content = self.update_status_indicators(audit_content, coverage)
        
        print("🔄 Updating metadata...")
        audit_content = self.update_metadata(audit_content)
        
        print("💾 Writing updates...")
        self.write_update(audit_content)
        
        # Print summary
        pass_count = sum(1 for p in coverage.values() if p >= 95)
        yellow_count = sum(1 for p in coverage.values() if 80 <= p < 95)
        fail_count = sum(1 for p in coverage.values() if p < 80)
        
        print("")
        print("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        print(f"✅ PASS (≥95%):     {pass_count}/47")
        print(f"⚠️  PARTIAL (80-94%): {yellow_count}/47")
        print(f"❌ FAIL (<80%):     {fail_count}/47")
        print("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

if __name__ == "__main__":
    updater = AuditUpdater("platform/PLATFORM_TEST_AUDIT.md")
    updater.run()
```

### 2.2 Manual Audit Entry Updates

If running automated updates, also update specific sections:

#### Executive Summary

```markdown
## Executive Summary

### Critical Findings

✅ **GAPS CLOSED** (Updated 2026-04-11)
- ❌ → ✅ **identity** module: 95% coverage (57 tests added)
- ❌ → ✅ **security** module: 96% coverage (48 tests added)

🟡 **MODERATE PROGRESS**
- 🟡 → ✅ **core** module: enhanced from 75% to 95% (+15 tests)
- 🟡 → ✅ **database** module: enhanced from 80% to 95% (+30 tests)

### Overall Coverage Progress

| Metric | Previous | Current | Target | Status |
|--------|----------|---------|--------|--------|
| **Java Modules with Tests** | 19/28 (68%) | 21/28 (75%) | 28/28 (100%) | ⏳ |
| **Zero-Test Modules** | 9 modules | 7 modules | 0 modules | ⏳ |
| **Modules ≥95% Coverage** | 8 modules | 10 modules | 47 modules | ⏳ |
| **Behavioral Coverage** | ~30% | ~45% | 100% | ⏳ |
| **E2E Coverage** | ~5% | ~8% | 100% | ⏳ |
```

#### Module Coverage Table

Update section 9.1 with new coverage metrics:

```markdown
### 9.1 Structural Coverage

| Module | Line Coverage | Branch Coverage | Function Coverage | Status |
|--------|---------------|-----------------|-------------------|--------|
| **identity** | **95%** | **93%** | **96%** | ✅ |
| **core** | **95%** | **92%** | **96%** | ✅ |
| **database** | **95%** | **92%** | **94%** | ✅ |
| **workflow** | **92%** | **89%** | **93%** | ⚠️ |
| **agent-core** | **88%** | **85%** | **90%** | ⚠️ |
```

---

## 3. Continuous Validation in CI/CD

### 3.1 GitHub Actions Workflow

```yaml
# .github/workflows/platform-audit.yml

name: Platform Test Audit
on:
  schedule:
    - cron: '0 18 * * 5'  # Every Friday at 6 PM
  workflow_dispatch:

jobs:
  audit:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      
      - name: Set up JDK 21
        uses: actions/setup-java@v3
        with:
          java-version: '21'
      
      - name: Run full test suite
        run: |
          ./gradlew clean build \
            :platform:test \
            :platform:integrationTest \
            :platform:jacocoTestReport
      
      - name: Collect coverage metrics
        run: |
          python3 scripts/audit-updater.py \
            platform/PLATFORM_TEST_AUDIT.md
      
      - name: Check coverage gates
        run: |
          ./scripts/check-coverage-gates.sh
        continue-on-error: true
      
      - name: Generate audit report
        if: always()
        run: |
          ./scripts/generate-audit-report.sh > /tmp/audit-report.md
      
      - name: Comment on PR/Issue
        if: always()
        uses: actions/github-script@v6
        with:
          github-token: ${{ secrets.GITHUB_TOKEN }}
          script: |
            const fs = require('fs');
            const report = fs.readFileSync('/tmp/audit-report.md', 'utf8');
            github.rest.issues.createComment({
              issue_number: context.issue.number,
              owner: context.repo.owner,
              repo: context.repo.repo,
              body: report
            });
      
      - name: Commit audit updates
        if: always()
        run: |
          git config user.name "audit-bot"
          git config user.email "audit@ghatana.dev"
          git add platform/PLATFORM_TEST_AUDIT.md
          git commit -m "chore: update platform test audit [skip ci]" || true
          git push origin HEAD
```

### 3.2 Coverage Gate Enforcement

```bash
#!/bin/bash
# scripts/check-coverage-gates.sh

set -e

echo "🚨 Checking coverage gates..."

FAILED=0

# 1. Minimum overall coverage: 80%
OVERALL=$(./gradlew :platform:jacocoTestReport --quiet 2>/dev/null \
  | grep "TOTAL.*COVERED" | awk '{print $NF}' | sed 's/%//')

if [[ $OVERALL -lt 80 ]]; then
  echo "❌ Overall coverage ${OVERALL}% below minimum 80%"
  FAILED=1
fi

# 2. Zero-test modules must be ≥95%
for module in identity security observability; do
  COVERAGE=$(./gradlew platform:java:${module}:jacocoTestReport --quiet 2>/dev/null \
    | grep "CLASS" | awk '{print $NF}' | sed 's/%//')
  
  if [[ -z $COVERAGE ]]; then
    echo "❌ ${module}: No tests found!"
    FAILED=1
  elif [[ $COVERAGE -lt 95 ]]; then
    echo "❌ ${module}: ${COVERAGE}% (need ≥95%)"
    FAILED=1
  fi
done

# 3. No module can decrease coverage
PREV_COVERAGE=$(cat .coverage-baseline.json 2>/dev/null || echo "{}")

for module in ...; do
  CURRENT=$(get_coverage $module)
  PREVIOUS=$(echo $PREV_COVERAGE | jq -r ".${module} // 0")
  
  if [[ $CURRENT -lt $PREVIOUS ]]; then
    echo "❌ ${module}: Coverage decreased from ${PREVIOUS}% to ${CURRENT}%"
    FAILED=1
  fi
done

echo ""
if [[ $FAILED -eq 0 ]]; then
  echo "✅ All coverage gates passed!"
  exit 0
else
  echo "❌ Coverage gates failed!"
  exit 1
fi
```

---

## 4. Module Completion Checklist

When a module implementation is complete, use this checklist to validate:

```markdown
## Module: identity
**Status**: ✅ COMPLETE (Week 2)

### Pre-Merge Checklist
- [x] All 57 tests written and passing
- [x] Coverage at 95% (Line: 95%, Branch: 93%, Function: 96%)
- [x] Zero lint warnings
- [x] All types specified (no `any` types)
- [x] Vision and README documented
- [x] API contracts documented
- [x] @doc.* tags on all public classes
- [x] Concurrency tests included
- [x] Edge case tests included
- [x] Integration tests with governance
- [x] Observability tests (logging, metrics, tracing)
- [x] Security tests (injection, auth scenarios)
- [x] Performance baseline established
- [x] Build passes clean
- [x] ArchUnit tests pass
- [x] SonarQube quality gate passes

### Post-Merge Updates
- [x] Updated PLATFORM_TEST_AUDIT.md
  - Changed "0 tests" → "57 tests"
  - Changed "❌ NO TESTS" → "✅ 95% COVERAGE"
  - Updated Executive Summary
  - Updated Module Coverage Table (9.1)
  - Updated Behavioral Coverage Table (9.2)
  - Updated Test Distribution (9.3)
  - Updated Structural Coverage (9.1)

- [x] Updated session memory
  - `/memories/session/platform-test-closure-2026-04.md`
  - Marked identity as completed

- [x] Created repo memory
  - `/memories/repo/tier1-identity-implementation-complete.md`
  - Patterns that worked
  - Gotchas discovered

### Validation Confirmation
- [x] Pre-merge validation script ran and passed
- [x] Full platform audit includes module at 95%+
- [x] CI/CD green for module and related tests
- [x] Code review approved
- [x] Ready for next module
```

---

## 5. Template Scripts

All validation scripts should be checked into `/scripts`:

```
scripts/
├── audit-module.sh          # Single module audit
├── audit-full-platform.sh   # Complete audit of all 47 modules
├── audit-updater.py         # Updates PLATFORM_TEST_AUDIT.md
├── check-coverage-gates.sh  # Enforces coverage requirements
├── generate-audit-report.sh # Creates markdown report
└── validate-pre-merge.sh    # Pre-merge validation
```

---

## 6. Success Criteria

### Audit File Will Be Complete When:

1. ✅ **Executive Summary**
   - All 🔴 markers converted to ✅
   - All 🟡 markers converted to ✅ (except where <80% justified)
   - Coverage metrics at or exceeding targets

2. ✅ **Module Inventory (Section 1.1)**
   - All 47 modules have README ✅
   - All modules have vision ✅
   - All modules have architecture docs ✅
   - All modules have API contracts ✅

3. ✅ **Requirements Mapping (Section 1.3)**
   - All 43 requirements marked ✅ Implemented
   - All requirements have tests
   - All requirements traced to test cases

4. ✅ **Edge Cases & Failure Modes (Section 7)**
   - All ✅ markers (tested)
   - No ⚠️ markers (partial)
   - No ❌ markers (missing)

5. ✅ **Coverage Summary (Section 9)**
   - All modules ≥80% line coverage
   - All modules ≥80% branch coverage
   - Zero test gaps documentation

6. ✅ **Missing Coverage Matrix (Section 11)**
   - All items moved to "✅ COMPLETE" section
   - No "Missing" items remaining

---

## 7. When to Update PLATFORM_TEST_AUDIT.md

### Weekly (Every Friday)
- [ ] Run full platform audit
- [ ] Update coverage percentages
- [ ] Update status indicators
- [ ] Run coverage gate checks
- [ ] Commit updates with automated message

### Per Module Completion  
- [ ] Update module entry (✅ mark)
- [ ] Update coverage percentage
- [ ] Update test counts
- [ ] Update section 1.1 (Documentation Inventory)
- [ ] Update section 9.1-9.3 (Coverage Summary)
- [ ] Update section 11 (Missing Coverage)

### Before Major Milestones
- [ ] Full Executive Summary rewrite with progress
- [ ] Updated timeline and phase status
- [ ] Detailed progress metrics
- [ ] Risk assessment and mitigation

---

## Ready to Execute ✅

**Status**: Validation & audit update strategy complete  
**Next**: Begin Week 1 with stakeholder sign-off

All scripts, templates, and validation approaches documented. Ready for automated audit runs and manual review during implementation.
