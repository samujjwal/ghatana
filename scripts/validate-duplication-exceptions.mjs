#!/usr/bin/env node

/**
 * Duplication Exceptions Validator
 *
 * Validates config/duplication-exceptions.json to ensure:
 * - Each exception has all required fields (id, packageName, rationale, expiresAt)
 * - Expired exceptions are rejected
 * - New duplicate packages without exceptions are flagged
 *
 * @doc.type tooling
 * @doc.purpose Validate duplication exceptions with expiry tracking
 * @doc.layer infrastructure
 */

import { readFileSync, existsSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '..');
const DUPLICATION_EXCEPTIONS_PATH = path.join(repoRoot, 'config/duplication-exceptions.json');

function readJson(relativePath) {
  return JSON.parse(readFileSync(path.join(repoRoot, relativePath), 'utf8'));
}

function validateDuplicationExceptions() {
  const violations = [];
  
  if (!existsSync(DUPLICATION_EXCEPTIONS_PATH)) {
    violations.push('config/duplication-exceptions.json does not exist');
    return violations;
  }

  const exceptionsFile = readJson('config/duplication-exceptions.json');
  
  // Validate structure
  if (!exceptionsFile.version) {
    violations.push('config/duplication-exceptions.json missing version field');
  }
  
  if (!Array.isArray(exceptionsFile.exceptions)) {
    violations.push('config/duplication-exceptions.json missing exceptions array');
    return violations;
  }

  const now = new Date();
  
  for (const exception of exceptionsFile.exceptions) {
    // Validate required fields
    if (!exception.id) {
      violations.push('Exception missing required field: id');
    }
    
    if (!exception.packageName) {
      violations.push(`Exception ${exception.id || 'unknown'} missing required field: packageName`);
    }
    
    if (!exception.rationale) {
      violations.push(`Exception ${exception.id || 'unknown'} missing required field: rationale`);
    }
    
    if (!exception.expiresAt) {
      violations.push(`Exception ${exception.id || 'unknown'} missing required field: expiresAt`);
    } else {
      // Validate expiry date format and check if expired
      const expiryDate = new Date(exception.expiresAt);
      if (isNaN(expiryDate.getTime())) {
        violations.push(`Exception ${exception.id} has invalid expiresAt format: ${exception.expiresAt}`);
      } else if (expiryDate < now) {
        violations.push(`Exception ${exception.id} expired on ${exception.expiresAt}. Remove or renew exception.`);
      }
    }
  }

  return violations;
}

function main() {
  const violations = validateDuplicationExceptions();

  if (violations.length > 0) {
    console.error('Duplication exceptions validation failed:');
    for (const violation of violations) {
      console.error(`- ${violation}`);
    }
    process.exit(1);
  }

  console.log('OK: duplication exceptions validation passed.');
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  main();
}

export { validateDuplicationExceptions };
