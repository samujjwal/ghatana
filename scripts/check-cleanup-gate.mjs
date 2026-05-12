#!/usr/bin/env node

/**
 * Cleanup Gate Check
 * 
 * Validates that the codebase does not contain dead product examples,
 * stale comments, or temporary migration notes that should be cleaned up.
 * 
 * Usage: node scripts/check-cleanup-gate.mjs
 */

import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const repoRoot = join(__dirname, '..');

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
  
  // Stale comments
  /\/\/.*deprecated.*remove/i,
  /\/\*.*deprecated.*remove/i,
  /\/\/.*legacy.*remove/i,
  /\/\*.*legacy.*remove/i,
  
  // Old file patterns
  /\.old\./,
  /_old\./,
  /_backup\./,
  /\.backup\./,
  /\.tmp\./,
  /\.bak\./,
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
  'docs/archive',
  'docs/legacy',
];

function shouldExcludePath(filePath) {
  return EXCLUDED_DIRS.some(dir => filePath.includes(`/${dir}/`) || filePath.startsWith(`${dir}/`));
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

function scanDirectory(dir, extensions = ['.ts', '.tsx', '.js', '.java', '.kt', '.gradle', '.kts', '.md']) {
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

function main() {
  console.log('=== Cleanup Gate Check ===\n');
  
  console.log('Scanning for stale patterns in code...');
  const contentViolations = scanDirectory(repoRoot);
  
  console.log('Scanning for stale file patterns...');
  const fileViolations = checkStaleFilePatterns(repoRoot);
  
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
