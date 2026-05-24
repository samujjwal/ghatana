#!/usr/bin/env node
/**
 * Environment-Specific Evidence Validation
 * Validates that all evidence files include environment-specific fields
 */

import { readFileSync, existsSync, readdirSync } from 'fs';
import { resolve, join } from 'path';

const EVIDENCE_DIR = resolve('.kernel/evidence');

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

  // Check for required environment-specific fields
  const requiredFields = ['commitSha', 'generatedAt'];
  const missingFields = requiredFields.filter(f => !evidence[f]);

  if (missingFields.length > 0) {
    return { valid: false, error: `Missing fields: ${missingFields.join(', ')}` };
  }

  // For bootstrap/rollback evidence, check for environment field
  if (filePath.includes('bootstrap') || filePath.includes('rollback')) {
    if (!evidence.environment && !evidence.targetEnv) {
      return { valid: false, error: 'Missing environment or targetEnv field' };
    }
  }

  return { valid: true };
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

  for (const entry of entries) {
    const fullPath = join(dir, entry.name);

    if (entry.isDirectory()) {
      const subResult = scanEvidenceDirectory(fullPath);
      totalFiles += subResult.totalFiles;
      validFiles += subResult.validFiles;
      invalidFiles += subResult.invalidFiles;
    } else if (entry.name.endsWith('.json')) {
      totalFiles++;
      const result = validateEvidenceFile(fullPath);
      if (result.valid) {
        validFiles++;
      } else {
        invalidFiles++;
        console.error(`❌ Invalid evidence: ${fullPath} - ${result.error}`);
      }
    }
  }

  return { totalFiles, validFiles, invalidFiles };
}

function main() {
  console.log('Validating environment-specific evidence...');
  const result = scanEvidenceDirectory(EVIDENCE_DIR);

  console.log(`\nSummary:`);
  console.log(`  Total files: ${result.totalFiles}`);
  console.log(`  Valid: ${result.validFiles}`);
  console.log(`  Invalid: ${result.invalidFiles}`);

  if (result.invalidFiles > 0) {
    console.error('\n❌ Environment-specific evidence validation failed');
    process.exit(1);
  }

  console.log('\n✅ All evidence files are environment-specific');
}

main();
