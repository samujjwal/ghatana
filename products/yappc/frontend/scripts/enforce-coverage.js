#!/usr/bin/env node

/**
 * Coverage Enforcement Script for Feature 6.3: Unit Test Coverage
 * 
 * This script enforces coverage thresholds for critical paths in the canvas application.
 * It reads coverage reports and fails the build if thresholds are not met.
 * 
 * Critical paths requiring 90%+ coverage:
 * - libs/canvas/src/state/* (normalization logic)
 * - libs/canvas/src/elements/* (change logic)
 * - libs/canvas/src/viewport/* (viewport management)
 * - libs/canvas/src/layout/* (layout algorithms)
 * - libs/store/src/* (state management)
 * 
 * Usage:
 *   node scripts/enforce-coverage.js [--strict] [--verbose]
 * 
 * Options:
 *   --strict    Enforce 90% thresholds (default: 70%)
 *   --verbose   Show detailed coverage breakdown
 *   --help      Show this help message
 */

const fs = require('fs');
const path = require('path');

// Parse command line arguments
const args = process.argv.slice(2);
const isStrict = args.includes('--strict');
const isVerbose = args.includes('--verbose');
const showHelp = args.includes('--help');

if (showHelp) {
  console.log(`
Coverage Enforcement Script

Usage: node scripts/enforce-coverage.js [options]

Options:
  --strict    Enforce 90% thresholds for critical paths (default: 70%)
  --verbose   Show detailed coverage breakdown
  --help      Show this help message

Critical Paths (90%+ coverage required with --strict):
  - libs/canvas/src/state/*       (normalization logic)
  - libs/canvas/src/elements/*    (change logic)  
  - libs/canvas/src/viewport/*    (viewport management)
  - libs/canvas/src/layout/*      (layout algorithms)
  - libs/store/src/*              (state management)

Exit Codes:
  0 - All coverage thresholds met
  1 - Coverage thresholds not met
  2 - Coverage report not found
  `);
  process.exit(0);
}

// Configuration
const COVERAGE_DIR = path.join(process.cwd(), 'coverage');
const COVERAGE_SUMMARY_FILE = path.join(COVERAGE_DIR, 'coverage-summary.json');

// Thresholds
const GLOBAL_THRESHOLD = 70;
const STRICT_THRESHOLD = 90;
const threshold = isStrict ? STRICT_THRESHOLD : GLOBAL_THRESHOLD;

// Critical paths requiring strict coverage
const CRITICAL_PATHS = [
  'libs/canvas/src/state',
  'libs/canvas/src/elements',
  'libs/canvas/src/viewport',
  'libs/canvas/src/layout',
  'libs/store/src',
];

/**
 * Load coverage summary JSON
 */
function loadCoverageSummary() {
  if (!fs.existsSync(COVERAGE_SUMMARY_FILE)) {
    console.error('❌ Coverage summary not found. Run tests with --coverage first.');
    console.error(`   Expected: ${COVERAGE_SUMMARY_FILE}`);
    process.exit(2);
  }

  try {
    const content = fs.readFileSync(COVERAGE_SUMMARY_FILE, 'utf-8');
    return JSON.parse(content);
  } catch (error) {
    console.error('❌ Failed to parse coverage summary:', error.message);
    process.exit(2);
  }
}

/**
 * Calculate average coverage percentage
 */
function calculateCoverage(metrics) {
  const { lines, statements, functions, branches } = metrics;
  return {
    lines: lines.pct,
    statements: statements.pct,
    functions: functions.pct,
    branches: branches.pct,
    average: (lines.pct + statements.pct + functions.pct + branches.pct) / 4,
  };
}

/**
 * Check if file path matches critical paths
 */
function isCriticalPath(filePath) {
  return CRITICAL_PATHS.some(criticalPath => 
    filePath.includes(criticalPath.replace(/\//g, path.sep))
  );
}

/**
 * Format coverage percentage with color
 */
function formatCoverage(value, threshold) {
  const percentage = value.toFixed(2);
  if (value >= threshold) {
    return `✅ ${percentage}%`;
  } else if (value >= threshold - 10) {
    return `⚠️  ${percentage}%`;
  } else {
    return `❌ ${percentage}%`;
  }
}

/**
 * Main enforcement logic
 */
function enforceCoverage() {
  console.log('\n🔍 Coverage Enforcement Check\n');
  console.log(`Mode: ${isStrict ? 'STRICT (90%)' : 'STANDARD (70%)'}`);
  console.log(`Threshold: ${threshold}%\n`);

  const coverageSummary = loadCoverageSummary();
  
  // Extract total coverage
  const totalCoverage = coverageSummary.total;
  const total = calculateCoverage(totalCoverage);

  // Display global coverage
  console.log('📊 Global Coverage:');
  console.log(`   Lines:      ${formatCoverage(total.lines, GLOBAL_THRESHOLD)}`);
  console.log(`   Statements: ${formatCoverage(total.statements, GLOBAL_THRESHOLD)}`);
  console.log(`   Functions:  ${formatCoverage(total.functions, GLOBAL_THRESHOLD)}`);
  console.log(`   Branches:   ${formatCoverage(total.branches, GLOBAL_THRESHOLD)}`);
  console.log(`   Average:    ${formatCoverage(total.average, GLOBAL_THRESHOLD)}\n`);

  // Check critical paths if in strict mode
  const criticalPathViolations = [];
  
  if (isStrict) {
    console.log('🎯 Critical Path Coverage (90%+ required):\n');
    
    const criticalFiles = Object.entries(coverageSummary)
      .filter(([filePath]) => filePath !== 'total' && isCriticalPath(filePath));

    if (criticalFiles.length === 0) {
      console.log('   ⚠️  No critical path files found in coverage report\n');
    } else {
      // Group by critical path
      const pathGroups = {};
      criticalFiles.forEach(([filePath, metrics]) => {
        const matchedPath = CRITICAL_PATHS.find(cp => 
          filePath.includes(cp.replace(/\//g, path.sep))
        );
        if (matchedPath) {
          if (!pathGroups[matchedPath]) {
            pathGroups[matchedPath] = [];
          }
          pathGroups[matchedPath].push({ filePath, metrics });
        }
      });

      // Calculate and display coverage for each critical path
      Object.entries(pathGroups).forEach(([criticalPath, files]) => {
        // Calculate average coverage for this path
        const pathCoverage = files.reduce((acc, { metrics }) => {
          const cov = calculateCoverage(metrics);
          return {
            lines: acc.lines + cov.lines,
            statements: acc.statements + cov.statements,
            functions: acc.functions + cov.functions,
            branches: acc.branches + cov.branches,
            average: acc.average + cov.average,
          };
        }, { lines: 0, statements: 0, functions: 0, branches: 0, average: 0 });

        const fileCount = files.length;
        Object.keys(pathCoverage).forEach(key => {
          pathCoverage[key] = pathCoverage[key] / fileCount;
        });

        console.log(`   ${criticalPath}:`);
        console.log(`     Files:      ${fileCount}`);
        console.log(`     Lines:      ${formatCoverage(pathCoverage.lines, STRICT_THRESHOLD)}`);
        console.log(`     Statements: ${formatCoverage(pathCoverage.statements, STRICT_THRESHOLD)}`);
        console.log(`     Functions:  ${formatCoverage(pathCoverage.functions, STRICT_THRESHOLD)}`);
        console.log(`     Branches:   ${formatCoverage(pathCoverage.branches, STRICT_THRESHOLD)}`);
        console.log(`     Average:    ${formatCoverage(pathCoverage.average, STRICT_THRESHOLD)}\n`);

        // Check if path meets threshold
        if (pathCoverage.average < STRICT_THRESHOLD) {
          criticalPathViolations.push({
            path: criticalPath,
            coverage: pathCoverage.average,
            threshold: STRICT_THRESHOLD,
          });
        }

        // Show individual files if verbose
        if (isVerbose) {
          files.forEach(({ filePath, metrics }) => {
            const cov = calculateCoverage(metrics);
            const relativePath = path.relative(process.cwd(), filePath);
            console.log(`       ${relativePath}:`);
            console.log(`         Lines:      ${formatCoverage(cov.lines, STRICT_THRESHOLD)}`);
            console.log(`         Statements: ${formatCoverage(cov.statements, STRICT_THRESHOLD)}`);
            console.log(`         Functions:  ${formatCoverage(cov.functions, STRICT_THRESHOLD)}`);
            console.log(`         Branches:   ${formatCoverage(cov.branches, STRICT_THRESHOLD)}`);
            console.log(`         Average:    ${formatCoverage(cov.average, STRICT_THRESHOLD)}\n`);
          });
        }
      });
    }
  }

  // Check for violations
  const globalViolations = [];
  if (total.lines < GLOBAL_THRESHOLD) {
    globalViolations.push(`Lines: ${total.lines.toFixed(2)}% (threshold: ${GLOBAL_THRESHOLD}%)`);
  }
  if (total.statements < GLOBAL_THRESHOLD) {
    globalViolations.push(`Statements: ${total.statements.toFixed(2)}% (threshold: ${GLOBAL_THRESHOLD}%)`);
  }
  if (total.functions < GLOBAL_THRESHOLD) {
    globalViolations.push(`Functions: ${total.functions.toFixed(2)}% (threshold: ${GLOBAL_THRESHOLD}%)`);
  }
  if (total.branches < GLOBAL_THRESHOLD) {
    globalViolations.push(`Branches: ${total.branches.toFixed(2)}% (threshold: ${GLOBAL_THRESHOLD}%)`);
  }

  // Report results
  if (globalViolations.length > 0 || criticalPathViolations.length > 0) {
    console.log('❌ Coverage Enforcement FAILED\n');
    
    if (globalViolations.length > 0) {
      console.log('Global threshold violations:');
      globalViolations.forEach(violation => console.log(`   - ${violation}`));
      console.log('');
    }

    if (criticalPathViolations.length > 0) {
      console.log('Critical path threshold violations (90%+ required):');
      criticalPathViolations.forEach(({ path, coverage, threshold }) => {
        console.log(`   - ${path}: ${coverage.toFixed(2)}% (threshold: ${threshold}%)`);
      });
      console.log('');
    }

    console.log('To fix:');
    console.log('   1. Add tests for uncovered code');
    console.log('   2. Remove unused/dead code');
    console.log('   3. Run: pnpm test --coverage\n');
    
    process.exit(1);
  }

  console.log('✅ Coverage Enforcement PASSED\n');
  console.log(`All files meet the ${isStrict ? 'strict (90%)' : 'standard (70%)'} coverage threshold.\n`);
  process.exit(0);
}

// Run enforcement
try {
  enforceCoverage();
} catch (error) {
  console.error('❌ Unexpected error:', error.message);
  if (isVerbose) {
    console.error(error.stack);
  }
  process.exit(2);
}
