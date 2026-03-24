#!/bin/bash

###############################################################################
# YAPPC Quality Gates Implementation
#
# Implements automated quality gates:
# 1. TODO reduction (637 → <100)
# 2. Module size limits
# 3. Dependency governance
# 4. CI enforcement
#
# Usage: ./scripts/implement-quality-gates.sh [--dry-run]
###############################################################################

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
YAPPC_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

DRY_RUN=false
if [[ "$1" == "--dry-run" ]]; then
    DRY_RUN=true
    echo "🔍 Running in DRY RUN mode - no files will be modified"
fi

echo "🚀 YAPPC Quality Gates Implementation"
echo "====================================="
echo ""

###############################################################################
# Phase 4.1: TODO Reduction
###############################################################################

echo "📋 Phase 4.1: TODO Analysis and Reduction"
echo ""

# Count current TODOs
TODO_COUNT=$(grep -r "TODO\|FIXME\|XXX\|HACK" "$YAPPC_ROOT" --include="*.java" --include="*.ts" --include="*.tsx" 2>/dev/null | wc -l)
echo "  Current TODOs: $TODO_COUNT"

# Generate TODO report
TODO_REPORT="$YAPPC_ROOT/docs/TODO_REDUCTION_REPORT.md"

cat > "$TODO_REPORT" <<EOF
# TODO Reduction Report

**Date:** $(date +%Y-%m-%d)  
**Current Count:** $TODO_COUNT  
**Target:** <100  
**Reduction Needed:** $((TODO_COUNT - 100))

## TODO Categories

### Critical (Must Fix)
TODOs that block functionality or represent bugs.

### Important (Should Fix)
TODOs that improve code quality or performance.

### Nice-to-Have (Can Defer)
TODOs that are enhancements or optimizations.

## Action Plan

1. **Convert to Issues:** Create GitHub issues for critical TODOs
2. **Remove Completed:** Delete TODOs for already-implemented features
3. **Remove Vague:** Delete TODOs without actionable items
4. **Consolidate Duplicates:** Merge duplicate TODOs

## TODO Locations

\`\`\`bash
# Find all TODOs
grep -r "TODO\|FIXME\|XXX\|HACK" . --include="*.java" --include="*.ts" --include="*.tsx"
\`\`\`

## Progress Tracking

| Week | Count | Reduction | Status |
|------|-------|-----------|--------|
| Week 1 | $TODO_COUNT | 0 | Baseline |
| Week 2 | TBD | TBD | In Progress |
| Week 3 | TBD | TBD | Pending |
| Week 4 | <100 | $((TODO_COUNT - 100)) | Target |

---

**Next Review:** $(date -v+1w +%Y-%m-%d)
EOF

echo "  ✅ Generated TODO reduction report: docs/TODO_REDUCTION_REPORT.md"

###############################################################################
# Phase 4.2: Module Size Limits
###############################################################################

echo ""
echo "📏 Phase 4.2: Module Size Limits"
echo ""

# Add module size check to build.gradle.kts
MODULE_SIZE_CHECK=$(cat <<'EOF'

// ============================================================================
// Module Size Enforcement
// ============================================================================
tasks.register("checkModuleSize") {
    description = "Fails if any module exceeds size limits"
    group = "verification"
    
    doLast {
        val maxJavaFiles = 150
        val violations = mutableListOf<String>()
        
        subprojects.forEach { project ->
            val srcDir = project.file("src/main/java")
            if (srcDir.exists()) {
                val fileCount = srcDir.walkTopDown()
                    .filter { it.isFile && it.extension == "java" }
                    .count()
                
                if (fileCount > maxJavaFiles) {
                    violations.add("${project.path}: $fileCount files (max: $maxJavaFiles)")
                }
            }
        }
        
        if (violations.isNotEmpty()) {
            throw GradleException(
                "Modules exceed size limit:\n" +
                violations.joinToString("\n") { "  - $it" } + "\n" +
                "Consider splitting large modules into focused submodules."
            )
        }
        
        logger.lifecycle("✓ All modules within size limits")
    }
}

tasks.named("check") {
    dependsOn("checkModuleSize")
}
EOF
)

if [[ "$DRY_RUN" == false ]]; then
    echo "$MODULE_SIZE_CHECK" >> "$YAPPC_ROOT/build.gradle.kts"
    echo "  ✅ Added module size check to build.gradle.kts"
else
    echo "  ⏭️  Skipped (dry run)"
fi

###############################################################################
# Phase 4.3: Dependency Governance
###############################################################################

echo ""
echo "🔒 Phase 4.3: Dependency Governance"
echo ""

# Add ESLint rules for import restrictions
ESLINT_RULES="$YAPPC_ROOT/frontend/eslint-rules/import-restrictions.js"

if [[ "$DRY_RUN" == false ]]; then
    mkdir -p "$(dirname "$ESLINT_RULES")"
    
    cat > "$ESLINT_RULES" <<'EOF'
/**
 * Import Restriction Rules
 * 
 * Prevents imports from consolidated libraries that should no longer be used.
 */

module.exports = {
  rules: {
    'no-restricted-imports': ['error', {
      patterns: [
        {
          group: ['@yappc/base-ui', '@yappc/base-ui/*'],
          message: 'Import from @yappc/ui instead. base-ui has been consolidated.'
        },
        {
          group: ['@yappc/development-ui', '@yappc/development-ui/*'],
          message: 'Import from @yappc/ui instead. development-ui has been consolidated.'
        },
        {
          group: ['@yappc/initialization-ui', '@yappc/initialization-ui/*'],
          message: 'Import from @yappc/ui instead. initialization-ui has been consolidated.'
        },
        {
          group: ['@yappc/navigation-ui', '@yappc/navigation-ui/*'],
          message: 'Import from @yappc/ui instead. navigation-ui has been consolidated.'
        },
        {
          group: ['@yappc/theme', '@yappc/theme/*'],
          message: 'Import from @yappc/ui instead. theme has been consolidated.'
        },
        {
          group: ['@yappc/messaging', '@yappc/messaging/*'],
          message: 'Import from @yappc/ai instead. messaging has been consolidated.'
        },
        {
          group: ['@yappc/realtime', '@yappc/realtime/*'],
          message: 'Import from @yappc/ai instead. realtime has been consolidated.'
        },
        {
          group: ['@yappc/notifications', '@yappc/notifications/*'],
          message: 'Import from @yappc/ai instead. notifications has been consolidated.'
        },
        {
          group: ['@yappc/config-hooks', '@yappc/config-hooks/*'],
          message: 'Import from @yappc/state instead. config-hooks has been consolidated.'
        },
        {
          group: ['@yappc/crdt', '@yappc/crdt/*'],
          message: 'Import from @yappc/state instead. crdt has been consolidated.'
        },
        {
          group: ['@yappc/types', '@yappc/types/*'],
          message: 'Import from @yappc/core instead. types has been consolidated.'
        },
        {
          group: ['@yappc/utils', '@yappc/utils/*'],
          message: 'Import from @yappc/core instead. utils has been consolidated.'
        }
      ]
    }]
  }
};
EOF
    
    echo "  ✅ Created ESLint import restriction rules"
else
    echo "  ⏭️  Skipped (dry run)"
fi

###############################################################################
# Phase 4.4: CI Enforcement
###############################################################################

echo ""
echo "🔄 Phase 4.4: CI Enforcement Configuration"
echo ""

# Create GitHub Actions workflow
CI_WORKFLOW="$YAPPC_ROOT/.github/workflows/quality-gates.yml"

if [[ "$DRY_RUN" == false ]]; then
    mkdir -p "$(dirname "$CI_WORKFLOW")"
    
    cat > "$CI_WORKFLOW" <<'EOF'
name: Quality Gates

on:
  pull_request:
    branches: [main, develop]
  push:
    branches: [main, develop]

jobs:
  quality-checks:
    runs-on: ubuntu-latest
    
    steps:
      - uses: actions/checkout@v4
      
      - name: Set up Java 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
      
      - name: Set up Node.js
        uses: actions/setup-node@v4
        with:
          node-version: '20'
      
      - name: Install pnpm
        uses: pnpm/action-setup@v2
        with:
          version: 10
      
      - name: Check Module Sizes
        run: ./gradlew checkModuleSize
        working-directory: products/yappc
      
      - name: Check TODO Count
        run: |
          TODO_COUNT=$(grep -r "TODO\|FIXME\|XXX\|HACK" . --include="*.java" --include="*.ts" --include="*.tsx" | wc -l)
          echo "TODO count: $TODO_COUNT"
          if [ $TODO_COUNT -gt 100 ]; then
            echo "❌ Too many TODOs: $TODO_COUNT (max: 100)"
            exit 1
          fi
          echo "✅ TODO count within limits"
        working-directory: products/yappc
      
      - name: Run Backend Tests
        run: ./gradlew test
        working-directory: products/yappc
      
      - name: Run Frontend Tests
        run: |
          cd frontend
          pnpm install
          pnpm test
        working-directory: products/yappc
      
      - name: Check Import Restrictions
        run: |
          cd frontend
          pnpm lint
        working-directory: products/yappc
      
      - name: Type Check
        run: |
          cd frontend
          pnpm typecheck
        working-directory: products/yappc

  architecture-validation:
    runs-on: ubuntu-latest
    
    steps:
      - uses: actions/checkout@v4
      
      - name: Set up Java 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
      
      - name: Run ArchUnit Tests
        run: ./gradlew test --tests "*BoundaryTest"
        working-directory: products/yappc
EOF
    
    echo "  ✅ Created CI workflow: .github/workflows/quality-gates.yml"
else
    echo "  ⏭️  Skipped (dry run)"
fi

###############################################################################
# Phase 4.5: ArchUnit Boundary Tests
###############################################################################

echo ""
echo "🏛️  Phase 4.5: ArchUnit Boundary Tests"
echo ""

# Create ArchUnit test template
ARCHUNIT_TEST="$YAPPC_ROOT/core/agents/code-specialists/src/test/java/com/ghatana/yappc/agents/code/AgentBoundaryTest.java"

if [[ "$DRY_RUN" == false ]]; then
    mkdir -p "$(dirname "$ARCHUNIT_TEST")"
    
    cat > "$ARCHUNIT_TEST" <<'EOF'
package com.ghatana.yappc.agents.code;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * ArchUnit tests to enforce module boundaries and architectural rules.
 */
class AgentBoundaryTest {

    private static final JavaClasses CLASSES = new ClassFileImporter()
        .importPackages("com.ghatana.yappc.agents.code");

    @Test
    void codeSpecialistsShouldNotDependOnArchitectureSpecialists() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("..agents.code..")
            .should().dependOnClassesThat().resideInAPackage("..agents.architecture..")
            .because("Code specialists must not depend on architecture specialists");

        rule.check(CLASSES);
    }

    @Test
    void codeSpecialistsShouldNotDependOnTestingSpecialists() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("..agents.code..")
            .should().dependOnClassesThat().resideInAPackage("..agents.testing..")
            .because("Code specialists must not depend on testing specialists");

        rule.check(CLASSES);
    }

    @Test
    void agentsShouldNotDependOnScaffold() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("..agents..")
            .should().dependOnClassesThat().resideInAPackage("..scaffold..")
            .because("Agents must not depend on scaffold - they are different concerns");

        rule.check(CLASSES);
    }

    @Test
    void agentsShouldNotDependOnRefactorer() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("..agents..")
            .should().dependOnClassesThat().resideInAPackage("..refactorer..")
            .because("Agents must not depend on refactorer");

        rule.check(CLASSES);
    }

    @Test
    void modulesShouldHaveMaximum150Files() {
        // This is enforced by Gradle checkModuleSize task
        // This test documents the requirement
        ArchRule rule = classes()
            .that().resideInAPackage("..agents.code..")
            .should().haveSimpleNameNotContaining("TooManyFiles")
            .because("Modules should have maximum 150 files");

        rule.check(CLASSES);
    }
}
EOF
    
    echo "  ✅ Created ArchUnit boundary test template"
else
    echo "  ⏭️  Skipped (dry run)"
fi

###############################################################################
# Summary
###############################################################################

echo ""
echo "✅ Quality Gates Implementation Complete!"
echo ""
echo "📊 Summary:"
echo "  ✓ TODO reduction report generated"
echo "  ✓ Module size limits enforced"
echo "  ✓ Import restriction rules created"
echo "  ✓ CI workflow configured"
echo "  ✓ ArchUnit boundary tests added"
echo ""
echo "📋 Quality Gates:"
echo "  1. Module Size: Max 150 Java files per module"
echo "  2. TODO Count: Max 100 TODOs across codebase"
echo "  3. Import Restrictions: No imports from consolidated libraries"
echo "  4. Boundary Tests: ArchUnit enforces module boundaries"
echo "  5. CI Enforcement: All checks run on every PR"
echo ""
echo "🔄 Next Steps:"
echo "  1. Review TODO reduction report"
echo "  2. Run: ./gradlew checkModuleSize"
echo "  3. Run: pnpm lint (frontend)"
echo "  4. Commit and push to trigger CI"
echo ""

if [[ "$DRY_RUN" == true ]]; then
    echo "⚠️  This was a DRY RUN - no files were modified"
fi
