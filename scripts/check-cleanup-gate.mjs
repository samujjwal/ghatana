#!/usr/bin/env node

/**
 * Cleanup Gate Check
 * 
 * Validates that production-facing source does not contain dead product examples,
 * stale comments, or temporary migration notes that should be cleaned up.
 *
 * Usage: node scripts/check-cleanup-gate.mjs
 */

import { existsSync, readFileSync, readdirSync, statSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const repoRoot = join(__dirname, '..');
const criticalScopesPath = join(repoRoot, 'config', 'production-critical-scopes.config.json');

// Patterns that indicate cleanup needed
const STALE_PATTERNS = [
  // Temporary migration notes
  /TODO.*migrat(e|ion)/i,
  /FIXME.*migrat(e|ion)/i,
  /HACK.*migrat(e|ion)/i,
  /TEMPORARY.*migrat(e|ion)/i,
  
  // Dead product examples
  /example.*product.*do not use/i,
  /sample.*product.*remove/i,
  /placeholder.*delete.*this/i,
  /MOVED:\s*Implementation lives/i,
  
  // Stale comments
  /\/\/.*deprecated.*\bremove\b/i,
  /\/\*.*deprecated.*\bremove\b/i,
  /\/\/.*legacy.*\bremove\b/i,
  /\/\*.*legacy.*\bremove\b/i,
  
];

const EXCLUDED_DIRS = [
  'node_modules',
  '.git',
  'build',
  'dist',
  'target',
  '.gradle',
  'out',
  'coverage',
  '.turbo',
  '.next',
  '.expo',
  'generated',
  '__generated__',
  '__tests__',
  'test',
  'tests',
  'fixtures',
  'test-results',
  'docs',
  'code-audits',
  'docs/archive',
  'docs/legacy',
  'scripts',
  'templates',
];

const EXCLUDED_FILES = [
  'End-to-End-Product-Prompt.md',
  'libraries-review-prompt.md',
  'product-review-prompt.md',
];

function shouldExcludePath(filePath) {
  const normalized = filePath.replace(/\\/g, '/');
  const baseName = normalized.slice(normalized.lastIndexOf('/') + 1);
  return EXCLUDED_FILES.includes(baseName)
    || EXCLUDED_DIRS.some(dir => normalized.includes(`/${dir}/`) || normalized.endsWith(`/${dir}`));
}

function checkFile(filePath) {
  const content = readFileSync(filePath, 'utf8');
  const violations = [];
  
  for (const pattern of STALE_PATTERNS) {
    const matches = content.match(pattern);
    if (matches) {
      violations.push({
        pattern: pattern.toString(),
        matches: matches.slice(0, 3), // Limit to first 3 matches
      });
    }
  }
  
  return violations;
}

function scanDirectory(dir, extensions = ['.ts', '.tsx', '.js', '.java', '.kt', '.gradle', '.kts']) {
  const violations = [];
  
  const entries = readdirSync(dir, { withFileTypes: true });
  
  for (const entry of entries) {
    const fullPath = join(dir, entry.name);
    
    if (shouldExcludePath(fullPath)) {
      continue;
    }
    
    if (entry.isDirectory()) {
      violations.push(...scanDirectory(fullPath, extensions));
    } else if (entry.isFile()) {
      const ext = entry.name.substring(entry.name.lastIndexOf('.'));
      if (extensions.includes(ext)) {
        const fileViolations = checkFile(fullPath);
        if (fileViolations.length > 0) {
          violations.push({ file: fullPath, violations: fileViolations });
        }
      }
    }
  }
  
  return violations;
}

function checkStaleFilePatterns(dir) {
  const violations = [];
  
  const entries = readdirSync(dir, { withFileTypes: true });
  
  for (const entry of entries) {
    const fullPath = join(dir, entry.name);
    
    if (shouldExcludePath(fullPath)) {
      continue;
    }
    
    if (entry.isDirectory()) {
      violations.push(...checkStaleFilePatterns(fullPath));
    } else if (entry.isFile()) {
      // Check for stale file name patterns
      if (/\.old\./.test(entry.name) ||
          /_old\./.test(entry.name) ||
          /_backup\./.test(entry.name) ||
          /\.backup\./.test(entry.name) ||
          /\.tmp\./.test(entry.name) ||
          /\.bak\./.test(entry.name)) {
        violations.push({ file: fullPath, reason: 'Stale file pattern detected' });
      }
    }
  }
  
  return violations;
}

function loadScanRoots() {
  if (!existsSync(criticalScopesPath)) {
    return [repoRoot];
  }

  const config = JSON.parse(readFileSync(criticalScopesPath, 'utf8'));
  return config.scanScopes
    .filter(scope => scope.critical)
    .map(scope => join(repoRoot, scope.path))
    .filter(scopePath => existsSync(scopePath));
}

function main() {
  console.log('=== Cleanup Gate Check ===\n');
  
  console.log('Scanning for stale patterns in code...');
  const scanRoots = loadScanRoots();
  const contentViolations = scanRoots.flatMap(root => scanDirectory(root));
  
  console.log('Scanning for stale file patterns...');
  const fileViolations = scanRoots.flatMap(root => checkStaleFilePatterns(root));
  
  let totalViolations = 0;
  
  if (contentViolations.length > 0) {
    console.log(`\n❌ Found ${contentViolations.length} files with stale patterns:\n`);
    for (const { file, violations: fileVio } of contentViolations) {
      console.log(`  ${file}`);
      for (const { pattern, matches } of fileVio) {
        console.log(`    - Pattern: ${pattern}`);
        console.log(`      Matches: ${matches.join(', ')}`);
      }
      totalViolations++;
    }
  }
  
  if (fileViolations.length > 0) {
    console.log(`\n❌ Found ${fileViolations.length} files with stale naming patterns:\n`);
    for (const { file, reason } of fileViolations) {
      console.log(`  ${file} - ${reason}`);
      totalViolations++;
    }
  }
  
  console.log();
  if (totalViolations === 0) {
    console.log('✓ Cleanup gate check passed - no stale patterns found');
    process.exit(0);
  } else {
    console.log(`✗ Cleanup gate check failed - ${totalViolations} violations found`);
    console.log('\nPlease remove stale patterns, comments, and temporary migration notes.');
    process.exit(1);
  }
}

main();
