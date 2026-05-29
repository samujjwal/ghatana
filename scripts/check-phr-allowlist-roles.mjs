#!/usr/bin/env node

/**
 * G4-T14: Backend static check for direct ALLOWED_ROLES.contains usage
 *
 * This script enforces that role-based authorization decisions use PhrPolicyEvaluator
 * instead of direct ALLOWED_ROLES.contains checks. The only allowed usage is in
 * PhrRouteSupport.requireContext for initial role validation.
 *
 * Usage: node scripts/check-phr-allowlist-roles.mjs
 */

import { readFileSync, readdirSync, statSync } from 'fs';
import { join, relative } from 'path';
import { fileURLToPath } from 'url';
import { dirname } from 'path';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const REPO_ROOT = join(__dirname, '..');

const ALLOWED_FILE = 'products/phr/src/main/java/com/ghatana/phr/api/routes/PhrRouteSupport.java';
const ALLOWED_PATTERN = /ALLOWED_ROLES\.contains\(/g;

const VIOLATIONS = [];

function scanJavaFile(filePath) {
  const content = readFileSync(filePath, 'utf-8');
  const relativePath = relative(REPO_ROOT, filePath);

  // Skip the allowed file
  if (relativePath === ALLOWED_FILE) {
    return;
  }

  const matches = content.matchAll(ALLOWED_PATTERN);
  for (const match of matches) {
    const lineStart = content.substring(0, match.index).split('\n').length;
    VIOLATIONS.push({
      file: relativePath,
      line: lineStart,
      context: extractContext(content, match.index, 50)
    });
  }
}

function extractContext(content, index, contextLength) {
  const start = Math.max(0, index - contextLength);
  const end = Math.min(content.length, index + contextLength);
  return content.substring(start, end).replace(/\s+/g, ' ').trim();
}

function scanDirectory(dir) {
  const entries = readdirSync(dir, { withFileTypes: true });

  for (const entry of entries) {
    const fullPath = join(dir, entry.name);

    if (entry.isDirectory()) {
      // Skip node_modules, .git, build directories
      if (
        entry.name === 'node_modules' ||
        entry.name === '.git' ||
        entry.name === 'build' ||
        entry.name === '.gradle' ||
        entry.name === 'target' ||
        entry.name === 'out'
      ) {
        continue;
      }
      scanDirectory(fullPath);
    } else if (entry.isFile() && entry.name.endsWith('.java')) {
      scanJavaFile(fullPath);
    }
  }
}

function main() {
  console.log('Scanning for direct ALLOWED_ROLES.contains usage...\n');

  scanDirectory(REPO_ROOT);

  if (VIOLATIONS.length === 0) {
    console.log('✓ No violations found. All role checks use PhrPolicyEvaluator.');
    process.exit(0);
  }

  console.error(`Found ${VIOLATIONS.length} violation(s):\n`);
  for (const violation of VIOLATIONS) {
    console.error(`  ${violation.file}:${violation.line}`);
    console.error(`    Context: ${violation.context}\n`);
  }

  console.error('Direct ALLOWED_ROLES.contains usage is prohibited outside PhrRouteSupport.');
  console.error('Use PhrPolicyEvaluator for all PHI access control decisions.');
  process.exit(1);
}

main();
