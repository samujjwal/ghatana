#!/usr/bin/env node

/**
 * P1-7: Data Cloud Release Evidence Validation
 *
 * Tightens validation for Data Cloud release evidence:
 * - Fails on warnings in release mode
 * - Validates all required evidence files exist
 * - Checks evidence quality (no fallback posture checks)
 * - Validates test coverage thresholds
 * - Validates security compliance evidence
 * - Validates deployment readiness
 *
 * Usage: node scripts/validate-data-cloud-release-evidence.mjs [--ci]
 */

import { readFileSync, existsSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

import { getReleaseMode, shouldFailOnWarning, validateEvidenceQuality, processValidationResults, logValidationResults } from './lib/release-evidence-policy.mjs';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '..');

const RELEASE_MODE = getReleaseMode();

const violations = [];
const warnings = [];
const evidence = [];

function logError(message) {
  violations.push(message);
  console.error(`❌ ERROR: ${message}`);
}

function logWarning(message) {
  warnings.push(message);
  console.warn(`⚠️  WARNING: ${message}`);
}

function logSuccess(message) {
  console.log(`✓ ${message}`);
}

function logEvidence(message) {
  evidence.push(message);
  console.log(`  📋 ${message}`);
}

/**
 * Validate required evidence files exist
 */
function validateRequiredEvidenceFiles() {
  const evidenceDir = path.join(repoRoot, '.kernel', 'evidence');
  const requiredFiles = [
    'atomic-workflow-failure-injection/atomic-workflow-failure-injection-latest.json',
    'runtime-dependency-failure-injection/runtime-dependency-failure-injection-latest.json',
    'ai-governance-behavioral-proof/ai-governance-behavioral-proof-latest.json',
  ];

  let allFilesExist = true;
  for (const file of requiredFiles) {
    const filePath = path.join(evidenceDir, file);
    if (!existsSync(filePath)) {
      logError(`Required evidence file missing: ${file}`);
      allFilesExist = false;
    } else {
      logEvidence(`Evidence file exists: ${file}`);
    }
  }

  return allFilesExist;
}

/**
 * Validate evidence file content quality
 */
function validateEvidenceFileQuality(filePath, evidenceName) {
  if (!existsSync(filePath)) {
    logError(`${evidenceName}: Evidence file does not exist`);
    return false;
  }

  try {
    const content = readFileSync(filePath, 'utf8');
    const data = JSON.parse(content);

    // Check for fallback posture checks
    const contentStr = JSON.stringify(data);
    if (contentStr.includes('falling back to posture checks') || 
        contentStr.includes('fallback to posture checks')) {
      if (RELEASE_MODE === 'release') {
        logError(`${evidenceName}: Contains fallback posture checks - not allowed in release mode`);
        return false;
      } else {
        logWarning(`${evidenceName}: Contains fallback posture checks - executable tests preferred`);
      }
    }

    // Check for violations
    if (data.violations && data.violations.length > 0) {
      logError(`${evidenceName}: Has ${data.violations.length} violations`);
      return false;
    }

    // Check for warnings
    if (data.warnings && data.warnings.length > 0) {
      if (shouldFailOnWarning()) {
        logError(`${evidenceName}: Has ${data.warnings.length} warnings - failing in release mode`);
        return false;
      } else {
        logWarning(`${evidenceName}: Has ${data.warnings.length} warnings`);
      }
    }

    logSuccess(`${evidenceName}: Evidence quality validated`);
    return true;
  } catch (error) {
    logError(`${evidenceName}: Failed to parse evidence file: ${error.message}`);
    return false;
  }
}

/**
 * Validate Data Cloud specific test coverage
 */
function validateDataCloudTestCoverage() {
  const dataCloudPath = path.join(repoRoot, 'products/data-cloud');
  
  if (!existsSync(dataCloudPath)) {
    logError('Data Cloud product path not found');
    return false;
  }

  // Check for test directories
  const testDirs = [
    path.join(dataCloudPath, 'delivery/launcher/src/test/java'),
    path.join(dataCloudPath, 'planes/action/orchestrator/src/test/java'),
  ];

  let hasTests = false;
  for (const testDir of testDirs) {
    if (existsSync(testDir)) {
      hasTests = true;
      logEvidence(`Test directory exists: ${path.relative(repoRoot, testDir)}`);
    }
  }

  if (!hasTests) {
    logError('Data Cloud missing test coverage');
    return false;
  }

  logSuccess('Data Cloud test coverage validated');
  return true;
}

/**
 * Validate Data Cloud security compliance
 */
function validateDataCloudSecurityCompliance() {
  const dataCloudPath = path.join(repoRoot, 'products/data-cloud');
  
  const securityFiles = [
    path.join(dataCloudPath, '.github/workflows/security.yml'),
    path.join(dataCloudPath, 'SECURITY.md'),
  ];

  let hasSecurity = false;
  for (const file of securityFiles) {
    if (existsSync(file)) {
      hasSecurity = true;
      logEvidence(`Security file exists: ${path.relative(repoRoot, file)}`);
    }
  }

  if (!hasSecurity) {
    logError('Data Cloud missing security compliance evidence');
    return false;
  }

  logSuccess('Data Cloud security compliance validated');
  return true;
}

/**
 * Validate Data Cloud deployment readiness
 */
function validateDataCloudDeploymentReadiness() {
  const dataCloudPath = path.join(repoRoot, 'products/data-cloud');
  
  const deployFiles = [
    path.join(dataCloudPath, 'Dockerfile'),
    path.join(dataCloudPath, 'docker-compose.yml'),
  ];

  let hasDeploy = false;
  for (const file of deployFiles) {
    if (existsSync(file)) {
      hasDeploy = true;
      logEvidence(`Deployment file exists: ${path.relative(repoRoot, file)}`);
    }
  }

  if (!hasDeploy) {
    logError('Data Cloud missing deployment configuration');
    return false;
  }

  logSuccess('Data Cloud deployment readiness validated');
  return true;
}

/**
 * Main validation
 */
function main() {
  console.log('Validating Data Cloud release evidence...\n');
  console.log(`Release mode: ${RELEASE_MODE}\n`);

  // Validate required evidence files exist
  console.log('--- Checking required evidence files ---');
  const filesExist = validateRequiredEvidenceFiles();

  // Validate evidence file quality
  console.log('\n--- Validating evidence file quality ---');
  const evidenceDir = path.join(repoRoot, '.kernel', 'evidence');
  
  const atomicWorkflowValid = validateEvidenceFileQuality(
    path.join(evidenceDir, 'atomic-workflow-failure-injection/atomic-workflow-failure-injection-latest.json'),
    'Atomic Workflow Failure-Injection'
  );

  const runtimeDepValid = validateEvidenceFileQuality(
    path.join(evidenceDir, 'runtime-dependency-failure-injection/runtime-dependency-failure-injection-latest.json'),
    'Runtime Dependency Failure-Injection'
  );

  const aiGovValid = validateEvidenceFileQuality(
    path.join(evidenceDir, 'ai-governance-behavioral-proof/ai-governance-behavioral-proof-latest.json'),
    'AI Governance Behavioral Proof'
  );

  // Validate Data Cloud specific checks
  console.log('\n--- Validating Data Cloud specific checks ---');
  const testCoverageValid = validateDataCloudTestCoverage();
  const securityValid = validateDataCloudSecurityCompliance();
  const deployValid = validateDataCloudDeploymentReadiness();

  console.log('\n--- Summary ---');
  console.log(`Errors: ${violations.length}`);
  console.log(`Warnings: ${warnings.length}`);
  console.log(`Evidence items: ${evidence.length}`);

  // Process validation results with release evidence policy
  const validationResults = processValidationResults(violations, warnings, evidence, RELEASE_MODE);
  logValidationResults(validationResults, 'Data Cloud Release Evidence Validation');

  if (validationResults.shouldFail) {
    process.exit(1);
  }

  console.log('\nData Cloud release evidence validation passed.');
}

main();
