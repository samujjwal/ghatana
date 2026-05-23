#!/usr/bin/env node

/**
 * @doc.type script
 * @doc.purpose Static gate to prevent bare gradlew usage in npm scripts
 * @doc.layer governance
 * @doc.pattern Validation
 *
 * P0-07: Governance check for gradle wrapper script usage in package.json
 * Ensures that all npm scripts use cross-platform gradlew invocation:
 * - Shell scripts: ./gradlew (not bare gradlew)
 * - Windows batch: gradlew.bat (not bare gradlew)
 * - Node scripts: platform-specific logic or ./gradlew
 *
 * Bare gradlew usage is rejected as it's not portable across platforms.
 */

import { readFileSync, readdirSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { cwd } from 'node:process';

const __dirname = dirname(fileURLToPath(import.meta.url));
const repoRoot = process.argv.includes('--test-dir') ? cwd() : join(__dirname, '..');

/**
 * Files that are allowed to reference gradlew without ./ prefix
 */
const ALLOWLIST = new Set([
  'check-gradlew-portability.mjs',
  'check-gradle-wrapper-script-usage.mjs',
  'run-gradle-wrapper.mjs',
  'check-data-access-contract.mjs',
  'check-current-state-claims.mjs',
  'check-bridge-compliance.mjs',
]);

/**
 * Check a single package.json file for bare gradlew usage
 */
function checkPackageJson(filePath) {
  const content = readFileSync(filePath, 'utf8');
  const pkg = JSON.parse(content);
  const issues = [];

  if (pkg.scripts) {
    for (const [scriptName, scriptCommand] of Object.entries(pkg.scripts)) {
      if (typeof scriptCommand !== 'string') continue;

      // Check for bare gradlew as a command invocation
      // Only flag when gradlew is the actual command being executed
      // Pattern: command starts with "gradlew" followed by space/argument
      // Or: shell operator (&&, ||, ;, |) followed by "gradlew" and space/argument
      // But not: ./gradlew, gradlew.bat, or in script names/paths
      const commandPattern = /(?:^|&&|\|\||;|\||\n)\s*gradlew(?!\.bat)(?=\s|$)/;
      const hasBareGradlew = commandPattern.test(scriptCommand);
      
      // Exclude valid patterns - these are NOT bare gradlew invocations
      const hasValidPattern = 
        scriptCommand.includes('./gradlew') || 
        scriptCommand.includes('gradlew.bat') ||
        scriptCommand.includes('check-gradlew') ||  // Script names
        scriptCommand.includes('gradlew-portability') ||  // Script names
        scriptCommand.includes('gradle-wrapper');  // Script names
      
      if (hasBareGradlew && !hasValidPattern) {
        issues.push({
          script: scriptName,
          line: 1,
          command: scriptCommand,
          message: `Bare 'gradlew' usage found in script '${scriptName}'. Use './gradlew' for Unix or platform-specific logic.`,
        });
      }
    }
  }

  return issues;
}

/**
 * Recursively find all package.json files
 */
function findPackageJsonFiles(dir, files = []) {
  const entries = readdirSync(dir, { withFileTypes: true });

  for (const entry of entries) {
    const fullPath = join(dir, entry.name);
    if (entry.isDirectory()) {
      // Skip node_modules, .git, and other common exclusions
      if (entry.name !== 'node_modules' && entry.name !== '.git' && entry.name !== 'dist' && entry.name !== 'build' && entry.name !== '.tmp') {
        findPackageJsonFiles(fullPath, files);
      }
    } else if (entry.name === 'package.json') {
      files.push(fullPath);
    }
  }

  return files;
}

function main() {
  console.log('Checking gradle wrapper script usage in package.json files...\n');

  const packageJsonFiles = findPackageJsonFiles(repoRoot);
  const filesWithIssues = [];

  for (const filePath of packageJsonFiles) {
    const relativePath = filePath.replace(repoRoot, '').replace(/\\/g, '/');
    const issues = checkPackageJson(filePath);

    if (issues.length > 0) {
      filesWithIssues.push({ path: relativePath, issues });
    }
  }

  if (filesWithIssues.length === 0) {
    console.log('✓ All package.json scripts use portable gradlew invocation');
    console.log('✓ Shell scripts use ./gradlew');
    console.log('✓ Batch files use gradlew.bat');
    console.log('✓ Node scripts use platform-specific logic or ./gradlew');
    console.log('\nNo issues found.');
    process.exit(0);
  }

  console.log(`✗ Found ${filesWithIssues.length} package.json file(s) with non-portable gradlew usage:\n`);

  for (const { path, issues } of filesWithIssues) {
    console.log(`  ${path}:`);
    for (const issue of issues) {
      console.log(`    - Script: ${issue.script}`);
      console.log(`      Line ${issue.line}: ${issue.command}`);
      console.log(`      Issue: ${issue.message}`);
    }
    console.log('');
  }

  console.log('Fix guidelines:');
  console.log('  - For Unix/Linux/macOS: Use ./gradlew instead of gradlew');
  console.log('  - For Windows: Use gradlew.bat instead of gradlew');
  console.log('  - For cross-platform scripts: Use platform-specific logic');
  console.log('    Example: const gradleCommand = process.platform === "win32" ? "gradlew.bat" : "./gradlew"');

  process.exit(1);
}

try {
  main();
} catch (error) {
  console.error(`Check failed: ${error.message}`);
  process.exit(1);
}
