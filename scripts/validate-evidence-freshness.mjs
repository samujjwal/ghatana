#!/usr/bin/env node
/**
 * Evidence Freshness Validation
 * Validates that evidence files are fresh (within acceptable time limits)
 */

import { readFileSync, existsSync, readdirSync } from 'fs';
import { resolve, join } from 'path';

const EVIDENCE_DIR = resolve('.kernel/evidence');
const MAX_AGE_MS = 24 * 60 * 60 * 1000; // 24 hours

function validateEvidenceFile(filePath) {
  if (!existsSync(filePath)) {
    return { valid: false, error: 'File not found' };
  }

  const content = readFileSync(filePath, 'utf-8');
  let evidence;

  try {
    evidence = JSON.parse(content);
  } catch (e) {
    return { valid: false, error: 'Invalid JSON' };
  }

  if (!evidence.generatedAt) {
    return { valid: false, error: 'Missing generatedAt field' };
  }

  const generatedAt = new Date(evidence.generatedAt);
  const now = new Date();
  const age = now - generatedAt;

  if (age > MAX_AGE_MS) {
    const ageHours = Math.floor(age / (60 * 60 * 1000));
    return { valid: false, error: `Evidence is too old (${ageHours} hours old, max 24 hours)` };
  }

  return { valid: true, age };
}

function scanEvidenceDirectory(dir) {
  if (!existsSync(dir)) {
    console.error('❌ Evidence directory not found:', dir);
    process.exit(1);
  }

  const entries = readdirSync(dir, { withFileTypes: true });
  let totalFiles = 0;
  let validFiles = 0;
  let invalidFiles = 0;
  let staleFiles = 0;

  for (const entry of entries) {
    const fullPath = join(dir, entry.name);

    if (entry.isDirectory()) {
      const subResult = scanEvidenceDirectory(fullPath);
      totalFiles += subResult.totalFiles;
      validFiles += subResult.validFiles;
      invalidFiles += subResult.invalidFiles;
      staleFiles += subResult.staleFiles;
    } else if (entry.name.endsWith('.json')) {
      totalFiles++;
      const result = validateEvidenceFile(fullPath);
      if (result.valid) {
        validFiles++;
      } else {
        invalidFiles++;
        if (result.error.includes('too old')) {
          staleFiles++;
        }
        console.error(`❌ Invalid evidence: ${fullPath} - ${result.error}`);
      }
    }
  }

  return { totalFiles, validFiles, invalidFiles, staleFiles };
}

function main() {
  console.log('Validating evidence freshness...');
  const result = scanEvidenceDirectory(EVIDENCE_DIR);

  console.log(`\nSummary:`);
  console.log(`  Total files: ${result.totalFiles}`);
  console.log(`  Valid: ${result.validFiles}`);
  console.log(`  Invalid: ${result.invalidFiles}`);
  console.log(`  Stale (>24h): ${result.staleFiles}`);

  if (result.invalidFiles > 0) {
    console.error('\n❌ Evidence freshness validation failed');
    process.exit(1);
  }

  console.log('\n✅ All evidence files are fresh');
}

main();
