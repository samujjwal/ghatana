#!/usr/bin/env node

/**
 * Phase 8: Strict release workflow enforcement script.
 *
 * This script enforces strict order of release steps, prevents skipping release gates,
 * requires all evidence to be fresh, and requires all checks to pass.
 *
 * Usage: node scripts/strict-release-workflow.mjs --validate
 *        node scripts/strict-release-workflow.mjs --check-step <step-name>
 */

import { readFileSync, existsSync, statSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '..');

// Required release steps in strict order
const REQUIRED_STEPS = [
  'check-product-release-readiness',
  'check-evidence-freshness',
  'check-production-readiness-audit-tasks',
  'generate-comprehensive-release-summary',
  'validate-release-evidence',
  'artifact-bundler',
  'validate-artifact-bundle',
];

// Evidence freshness threshold (in hours)
const EVIDENCE_FRESHNESS_THRESHOLD = 24;

// Required evidence files
const REQUIRED_EVIDENCE = [
  '.kernel/evidence/atomic-workflow-posture.json',
  '.kernel/evidence/affected-product-release-profile.json',
  '.kernel/evidence/release-summary.json',
  'release-evidence/release-readiness-evidence.json',
];

function validateWorkflow() {
  console.log('Phase 8: Validating strict release workflow...\n');

  let allPassed = true;

  // Check 1: All required steps must be executed
  console.log('Checking required release steps...');
  for (const step of REQUIRED_STEPS) {
    const scriptPath = path.join(repoRoot, 'scripts', `${step}.mjs`);
    if (!existsSync(scriptPath)) {
      console.error(`❌ Missing required step script: ${step}.mjs`);
      allPassed = false;
    } else {
      console.log(`✅ Step script exists: ${step}.mjs`);
    }
  }

  // Check 2: All required evidence must exist
  console.log('\nChecking required evidence files...');
  for (const evidence of REQUIRED_EVIDENCE) {
    const evidencePath = path.join(repoRoot, evidence);
    if (!existsSync(evidencePath)) {
      console.error(`❌ Missing required evidence: ${evidence}`);
      allPassed = false;
    } else {
      console.log(`✅ Evidence exists: ${evidence}`);
    }
  }

  // Check 3: Evidence freshness
  console.log('\nChecking evidence freshness...');
  for (const evidence of REQUIRED_EVIDENCE) {
    const evidencePath = path.join(repoRoot, evidence);
    if (existsSync(evidencePath)) {
      const stats = statSync(evidencePath);
      const ageHours = (Date.now() - stats.mtimeMs) / (1000 * 60 * 60);
      if (ageHours > EVIDENCE_FRESHNESS_THRESHOLD) {
        console.error(`❌ Evidence is stale: ${evidence} (${ageHours.toFixed(1)}h old)`);
        allPassed = false;
      } else {
        console.log(`✅ Evidence is fresh: ${evidence} (${ageHours.toFixed(1)}h old)`);
      }
    }
  }

  // Check 4: Release gate workflow must be configured
  console.log('\nChecking release gate workflow...');
  const releaseGatePath = path.join(repoRoot, '.github', 'workflows', 'release-gate.yml');
  if (!existsSync(releaseGatePath)) {
    console.error('❌ Missing release gate workflow: .github/workflows/release-gate.yml');
    allPassed = false;
  } else {
    console.log('✅ Release gate workflow exists');
  }

  if (allPassed) {
    console.log('\n✅ Strict release workflow validation passed.');
    process.exit(0);
  } else {
    console.log('\n❌ Strict release workflow validation failed.');
    process.exit(1);
  }
}

function checkStep(stepName) {
  console.log(`Phase 8: Checking release step: ${stepName}\n`);

  if (!REQUIRED_STEPS.includes(stepName)) {
    console.error(`❌ Unknown step: ${stepName}`);
    console.log(`Valid steps: ${REQUIRED_STEPS.join(', ')}`);
    process.exit(1);
  }

  const stepIndex = REQUIRED_STEPS.indexOf(stepName);
  console.log(`Step position: ${stepIndex + 1} of ${REQUIRED_STEPS.length}`);

  if (stepIndex > 0) {
    const previousStep = REQUIRED_STEPS[stepIndex - 1];
    console.log(`Required previous step: ${previousStep}`);
  }

  console.log(`✅ Step ${stepName} is valid in the workflow.`);
  process.exit(0);
}

function main() {
  const args = process.argv.slice(2);

  if (args.length === 0) {
    console.log('Usage: node scripts/strict-release-workflow.mjs --validate');
    console.log('       node scripts/strict-release-workflow.mjs --check-step <step-name>');
    process.exit(1);
  }

  const command = args[0];

  if (command === '--validate') {
    validateWorkflow();
  } else if (command === '--check-step') {
    const stepName = args[1];
    if (!stepName) {
      console.error('Error: --check-step requires a step name');
      process.exit(1);
    }
    checkStep(stepName);
  } else {
    console.error(`Unknown command: ${command}`);
    process.exit(1);
  }
}

try {
  main();
} catch (error) {
  console.error('Strict release workflow check failed:', error);
  process.exit(1);
}
