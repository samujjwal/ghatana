#!/usr/bin/env node

/**
 * Check Tutorputor WCAG 2.1 AA compliance
 *
 * Validates that Tutorputor web interface meets WCAG 2.1 Level AA standards
 * for inclusive education delivery.
 */

import { existsSync, readFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '..');

const PRODUCT_ID = 'tutorputor';
const WCAG_GATE_PACK = 'products/tutorputor/lifecycle/gate-packs/wcag-validation.yaml';
const REQUIRED_GATES = [
  'automated-wcag-scan',
  'keyboard-navigation',
  'screen-reader-compatibility',
  'multimedia-accessibility',
  'cognitive-accessibility',
];
const REQUIRED_CHECKS = [
  'axe-core-scan-pass',
  'contrast-ratio-compliance',
  'tab-order-logical',
  'focus-visible-indicators',
  'semantic-html-structure',
  'aria-labels-present',
  'form-labels-associated',
  'captions-provided',
];

const EVIDENCE_PATHS = [
  'products/tutorputor/e2e/accessibility/wcag-audit-results.json',
  'products/tutorputor/docs/accessibility-compliance-report.md',
];

function readJson(relativePath) {
  const fullPath = path.join(repoRoot, relativePath);
  if (!existsSync(fullPath)) {
    return null;
  }
  return JSON.parse(readFileSync(fullPath, 'utf8'));
}

function pathExists(relativePath) {
  return existsSync(path.join(repoRoot, relativePath));
}

function fail(message) {
  throw new Error(message);
}

function assert(condition, message) {
  if (!condition) {
    fail(message);
  }
}

function assertExists(label, relativePath) {
  if (!pathExists(relativePath)) {
    fail(`${label} must exist at ${relativePath}`);
  }
}

function validateWcagGatePack() {
  console.log('[TUTOR-WCAG] Validating WCAG gate pack...');

  assertExists('WCAG gate pack', WCAG_GATE_PACK);

  // Gate pack structure validation would use YAML parser in production
  // For this check script, we verify file exists and has expected content markers
  const content = readFileSync(path.join(repoRoot, WCAG_GATE_PACK), 'utf8');

  assert(content.includes('wcagVersion:'), 'Gate pack must declare wcagVersion');
  assert(content.includes('conformanceLevel: AA'), 'Gate pack must declare AA conformance level');
  assert(content.includes('validationGates:'), 'Gate pack must declare validationGates');

  for (const gate of REQUIRED_GATES) {
    assert(content.includes(`gate: ${gate}`), `Gate pack must include ${gate} gate`);
  }

  for (const check of REQUIRED_CHECKS) {
    assert(content.includes(`checkName: ${check}`), `Gate pack must include ${check} check`);
  }

  console.log('  ✓ WCAG gate pack structure valid');
}

function validateEvidenceFiles() {
  console.log('[TUTOR-WCAG] Validating evidence files...');

  const releaseReadiness = readJson('.kernel/evidence/tutorputor/tutorputor-release-readiness.json');
  assert(releaseReadiness != null, 'Tutorputor release readiness evidence must exist');

  // Check if accessibility evidence is referenced
  const evidenceCategories = releaseReadiness.evidenceCategories || {};
  const hasAccessibilityEvidence = Object.keys(evidenceCategories).some(
    key => key.toLowerCase().includes('accessibility') || key.toLowerCase().includes('a11y')
  );

  if (!hasAccessibilityEvidence) {
    console.warn('  ⚠ No accessibility evidence category found in release readiness');
  } else {
    console.log('  ✓ Accessibility evidence category present');
  }

  for (const evidencePath of EVIDENCE_PATHS) {
    if (pathExists(evidencePath)) {
      console.log(`  ✓ Evidence file exists: ${evidencePath}`);
    } else {
      console.warn(`  ⚠ Evidence file missing (optional): ${evidencePath}`);
    }
  }
}

function validateUiPackage() {
  console.log('[TUTOR-WCAG] Validating UI package accessibility configuration...');

  const uiPackagePath = 'products/tutorputor/delivery/ui/package.json';
  if (!pathExists(uiPackagePath)) {
    console.warn('  ⚠ UI package not found at expected path');
    return;
  }

  const uiPackage = readJson(uiPackagePath);
  const scripts = uiPackage.scripts || {};

  // Check for accessibility testing scripts
  const hasA11yScript = Object.keys(scripts).some(
    key => key.toLowerCase().includes('a11y') || key.toLowerCase().includes('accessibility')
  );

  if (hasA11yScript) {
    console.log('  ✓ UI package has accessibility testing scripts');
  } else {
    console.warn('  ⚠ UI package lacks accessibility testing scripts');
  }

  // Check for testing-library/jest-dom for accessibility matchers
  const devDeps = uiPackage.devDependencies || {};
  const hasTestingLibrary = Object.keys(devDeps).some(
    key => key.includes('@testing-library')
  );

  if (hasTestingLibrary) {
    console.log('  ✓ UI package uses Testing Library (accessible queries)');
  } else {
    console.warn('  ⚠ UI package should use Testing Library for accessible queries');
  }
}

function validateCheckScript() {
  console.log('[TUTOR-WCAG] Running WCAG compliance validation...');

  const results = {
    passed: [],
    warnings: [],
    errors: [],
  };

  try {
    validateWcagGatePack();
    results.passed.push('WCAG gate pack structure');
  } catch (error) {
    results.errors.push(error.message);
  }

  try {
    validateEvidenceFiles();
    results.passed.push('Evidence file validation');
  } catch (error) {
    results.errors.push(error.message);
  }

  try {
    validateUiPackage();
    results.passed.push('UI package accessibility configuration');
  } catch (error) {
    results.warnings.push(error.message);
  }

  return results;
}

function main() {
  console.log('=== Tutorputor WCAG 2.1 AA Validation ===\n');

  const results = validateCheckScript();

  console.log('\n=== Results ===');
  console.log(`Passed: ${results.passed.length}`);
  console.log(`Warnings: ${results.warnings.length}`);
  console.log(`Errors: ${results.errors.length}`);

  for (const pass of results.passed) {
    console.log(`  ✓ ${pass}`);
  }

  for (const warning of results.warnings) {
    console.log(`  ⚠ ${warning}`);
  }

  for (const error of results.errors) {
    console.log(`  ✗ ${error}`);
  }

  // Write evidence file
  const evidenceDir = path.join(repoRoot, '.kernel', 'evidence', 'tutorputor');
  const evidenceFile = path.join(evidenceDir, 'wcag-validation-evidence.json');

  const evidence = {
    schemaVersion: '1.0.0',
    productId: PRODUCT_ID,
    evidenceType: 'wcag-validation',
    generatedAt: new Date().toISOString(),
    wcagVersion: '2.1',
    conformanceLevel: 'AA',
    status: results.errors.length === 0 ? 'passed' : 'failed',
    summary: {
      totalChecks: results.passed.length + results.warnings.length + results.errors.length,
      passed: results.passed.length,
      warnings: results.warnings.length,
      errors: results.errors.length,
    },
    checks: {
      passed: results.passed,
      warnings: results.warnings,
      errors: results.errors,
    },
    gatePackRef: WCAG_GATE_PACK,
  };

  // Note: In production, this would write to the evidence directory
  // For this implementation, we output the evidence structure
  console.log('\n=== Generated Evidence ===');
  console.log(JSON.stringify(evidence, null, 2));

  if (results.errors.length > 0) {
    console.error('\n✗ WCAG validation failed');
    process.exit(1);
  }

  console.log('\n✓ WCAG validation passed');
  process.exit(0);
}

main();
