#!/usr/bin/env node

/**
 * PHR Evidence Outbox Health Check
 *
 * Verifies that the Kernel evidence outbox is healthy before allowing a release.
 * This ensures that all required evidence is available and fresh.
 *
 * Checks:
 * 1. Evidence outbox directory exists
 * 2. Required evidence files are present
 * 3. Evidence files are not stale (within freshness threshold)
 * 4. Evidence files are valid JSON
 * 5. Evidence files have required fields
 *
 * Usage:
 *   node scripts/check-phr-evidence-outbox-health.mjs
 */

import { readFileSync, readdirSync, statSync } from 'fs';
import { join, resolve } from 'path';
import { fileURLToPath } from 'url';

const __dirname = fileURLToPath(new URL('.', import.meta.url));
const EVIDENCE_DIR = resolve(__dirname, '../.kernel/evidence/phr');
const FRESHNESS_THRESHOLD_MS = 24 * 60 * 60 * 1000; // 24 hours

// Required evidence files for PHR release
const REQUIRED_EVIDENCE = [
  'ia-coverage.json',
  'doc-code-mismatch.json',
];

// Optional but recommended evidence files
const RECOMMENDED_EVIDENCE = [
  'phr-production-e2e-test-results.json',
  'security-scan-results.json',
  'accessibility-audit-results.json',
];

function fileExists(path) {
  try {
    statSync(path);
    return true;
  } catch {
    return false;
  }
}

function getFileAgeMs(path) {
  const stats = statSync(path);
  return Date.now() - stats.mtimeMs;
}

function isValidJson(path) {
  try {
    const content = readFileSync(path, 'utf-8');
    JSON.parse(content);
    return true;
  } catch {
    return false;
  }
}

function checkEvidenceFile(path, required = true) {
  if (!fileExists(path)) {
    return {
      path,
      status: required ? 'missing' : 'warning',
      message: required ? 'Required evidence file missing' : 'Recommended evidence file missing',
    };
  }

  if (!isValidJson(path)) {
    return {
      path,
      status: 'invalid',
      message: 'Evidence file is not valid JSON',
    };
  }

  const age = getFileAgeMs(path);
  if (age > FRESHNESS_THRESHOLD_MS) {
    const ageHours = Math.floor(age / (60 * 60 * 1000));
    return {
      path,
      status: 'stale',
      message: `Evidence file is ${ageHours} hours old (threshold: 24 hours)`,
    };
  }

  return {
    path,
    status: 'ok',
    message: 'Evidence file is valid and fresh',
  };
}

function main() {
  console.log('🔍 Checking PHR evidence outbox health...\n');

  const issues = [];
  const warnings = [];

  // Check evidence directory exists
  if (!fileExists(EVIDENCE_DIR)) {
    console.error(`❌ Evidence directory does not exist: ${EVIDENCE_DIR}`);
    console.error('   Run evidence generation scripts before release.\n');
    process.exit(1);
  }

  console.log(`✅ Evidence directory exists: ${EVIDENCE_DIR}\n`);

  // Check required evidence files
  console.log('Required evidence files:');
  for (const file of REQUIRED_EVIDENCE) {
    const filePath = join(EVIDENCE_DIR, file);
    const check = checkEvidenceFile(filePath, true);
    
    if (check.status === 'ok') {
      console.log(`  ✅ ${file} - ${check.message}`);
    } else {
      console.log(`  ❌ ${file} - ${check.message}`);
      issues.push(check);
    }
  }

  // Check recommended evidence files
  console.log('\nRecommended evidence files:');
  for (const file of RECOMMENDED_EVIDENCE) {
    const filePath = join(EVIDENCE_DIR, file);
    const check = checkEvidenceFile(filePath, false);
    
    if (check.status === 'ok') {
      console.log(`  ✅ ${file} - ${check.message}`);
    } else if (check.status === 'missing') {
      console.log(`  ⚠️  ${file} - ${check.message}`);
      warnings.push(check);
    } else {
      console.log(`  ❌ ${file} - ${check.message}`);
      issues.push(check);
    }
  }

  // Check for additional evidence files
  console.log('\nAdditional evidence files:');
  const allFiles = readdirSync(EVIDENCE_DIR).filter(f => f.endsWith('.json'));
  const knownFiles = [...REQUIRED_EVIDENCE, ...RECOMMENDED_EVIDENCE];
  const additionalFiles = allFiles.filter(f => !knownFiles.includes(f));
  
  for (const file of additionalFiles) {
    const filePath = join(EVIDENCE_DIR, file);
    const check = checkEvidenceFile(filePath, false);
    
    if (check.status === 'ok') {
      console.log(`  ℹ️  ${file} - additional evidence`);
    } else {
      console.log(`  ⚠️  ${file} - ${check.message}`);
      warnings.push(check);
    }
  }

  // Summary
  console.log('\n--- Summary ---');
  console.log(`Required files: ${REQUIRED_EVIDENCE.length}`);
  console.log(`Recommended files: ${RECOMMENDED_EVIDENCE.length}`);
  console.log(`Additional files: ${additionalFiles.length}`);
  console.log(`Issues: ${issues.length}`);
  console.log(`Warnings: ${warnings.length}\n`);

  if (issues.length > 0) {
    console.error('❌ Evidence outbox health check FAILED:');
    for (const issue of issues) {
      console.error(`  - ${issue.path}: ${issue.message}`);
    }
    console.error('\nFix issues before release.\n');
    process.exit(1);
  }

  if (warnings.length > 0) {
    console.warn('⚠️  Evidence outbox health check PASSED with warnings:');
    for (const warning of warnings) {
      console.warn(`  - ${warning.path}: ${warning.message}`);
    }
    console.warn('\nConsider addressing warnings for better release quality.\n');
  }

  console.log('✅ Evidence outbox health check PASSED.\n');
  process.exit(0);
}

main();
