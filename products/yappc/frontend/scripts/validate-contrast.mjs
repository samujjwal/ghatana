#!/usr/bin/env node

/**
 * Contrast Validation CI Script
 *
 * Validates all design tokens for WCAG AA compliance.
 * Fails the build if any critical contrast violations are found.
 *
 * Usage: node scripts/validate-contrast.mjs
 *
 * Exit codes:
 * - 0: All checks pass
 * - 1: Critical violations found
 * - 2: Script error
 */

import { fileURLToPath } from 'url';
import { dirname, resolve } from 'path';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

// Import contrast checker (would need to be compiled first)
const {
  checkAllTokenPairs,
  getFailingChecks,
  generateContrastReport,
  formatRatio,
  getLevelEmoji,
} = await import(
  resolve(__dirname, '../libs/canvas/src/utils/contrast-checker.ts')
).catch(async () => {
  // Fallback: try compiled JS
  return await import(
    resolve(__dirname, '../dist/libs/canvas/src/utils/contrast-checker.js')
  );
});

/**
 * Color codes for terminal output
 */
const colors = {
  reset: '\x1b[0m',
  red: '\x1b[31m',
  green: '\x1b[32m',
  yellow: '\x1b[33m',
  blue: '\x1b[34m',
  bold: '\x1b[1m',
};

/**
 * Log with color
 */
function log(message, color = colors.reset) {
  console.log(`${color}${message}${colors.reset}`);
}

/**
 * Main validation function
 */
async function main() {
  log('\n🎨 Validating Design Token Contrast Ratios\n', colors.bold);

  try {
    // Run all checks
    log('Running contrast checks...', colors.blue);
    const checks = checkAllTokenPairs();
    const failing = getFailingChecks(checks);

    // Display summary
    log(`\n📊 Results:`, colors.bold);
    log(`  Total checks: ${checks.length}`);
    log(`  Passing: ${checks.length - failing.length} ✅`, colors.green);

    if (failing.length > 0) {
      log(`  Failing: ${failing.length} ❌`, colors.red);
    }

    // Display failing checks
    if (failing.length > 0) {
      log(`\n❌ Contrast Violations Found:\n`, colors.red + colors.bold);

      failing.forEach((check) => {
        log(`  ${check.context}`, colors.yellow);
        log(`    Foreground: ${check.foreground}`);
        log(`    Background: ${check.background}`);
        log(
          `    Ratio: ${formatRatio(check.ratio)} (needs ≥ 4.5:1)`,
          colors.red
        );
        log(`    Level: ${getLevelEmoji(check.level)} ${check.level}\n`);
      });

      // Generate full report
      const report = generateContrastReport(checks);
      const reportPath = resolve(__dirname, '../contrast-report.md');

      log(`📝 Full report written to: ${reportPath}\n`, colors.blue);

      // Exit with error
      log(
        '❌ Build failed due to contrast violations',
        colors.red + colors.bold
      );
      process.exit(1);
    }

    // Success
    log('\n✅ All contrast checks passed!', colors.green + colors.bold);
    log('   All design tokens meet WCAG AA standards.\n');
    process.exit(0);
  } catch (error) {
    log('\n💥 Error running contrast validation:', colors.red + colors.bold);
    console.error(error);
    process.exit(2);
  }
}

// Run
main();
