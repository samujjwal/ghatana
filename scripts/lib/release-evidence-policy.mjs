#!/usr/bin/env node

/**
 * Release Evidence Policy Module
 *
 * Centralized policy enforcement for release evidence validation:
 * - Warning vs error classification based on mode (local/CI/release)
 * - Waiver management for not-applicable gates
 * - Product coverage validation
 * - Evidence quality checks (fallback posture checks, generated-on-demand)
 *
 * Usage:
 *   import { getReleaseMode, shouldFailOnWarning, checkWaiver, validateEvidenceQuality } from './scripts/lib/release-evidence-policy.mjs';
 */

import { readFileSync, existsSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '../..');

/**
 * Release mode enumeration
 */
export const ReleaseMode = {
  LOCAL: 'local',
  CI: 'ci',
  RELEASE: 'release',
};

/**
 * Determine current release mode from environment
 */
export function getReleaseMode() {
  if (process.env.RELEASE_MODE === 'release') {
    return ReleaseMode.RELEASE;
  }
  if (process.env.CI === 'true' || process.env.GITHUB_ACTIONS === 'true') {
    return ReleaseMode.RELEASE; // CI mode is treated as release mode for strictness
  }
  return ReleaseMode.LOCAL;
}

/**
 * Check if warnings should fail based on release mode
 */
export function shouldFailOnWarning(mode = getReleaseMode()) {
  return mode === ReleaseMode.RELEASE;
}

/**
 * Check if warnings should be reported (not silent)
 */
export function shouldReportWarning(mode = getReleaseMode()) {
  return mode !== ReleaseMode.LOCAL;
}

/**
 * Load waiver registry
 */
export function loadWaiverRegistry() {
  const waiverPath = path.join(repoRoot, 'config/release-proof-waivers.json');
  
  if (!existsSync(waiverPath)) {
    return { waivers: [] };
  }
  
  try {
    const content = readFileSync(waiverPath, 'utf8');
    return JSON.parse(content);
  } catch (error) {
    console.warn(`Failed to load waiver registry: ${error.message}`);
    return { waivers: [] };
  }
}

/**
 * Check if a waiver exists for a product/dimension/gate combination
 */
export function checkWaiver(productId, dimension, gate) {
  const registry = loadWaiverRegistry();
  const now = new Date();
  
  for (const waiver of registry.waivers) {
    if (waiver.productId !== productId) continue;
    if (waiver.dimension !== dimension) continue;
    if (waiver.gate !== gate) continue;
    
    // Check expiry
    if (waiver.expiryDate) {
      const expiry = new Date(waiver.expiryDate);
      if (expiry < now) {
        console.warn(`Waiver expired for ${productId}/${dimension}/${gate} on ${waiver.expiryDate}`);
        continue;
      }
    }
    
    return waiver;
  }
  
  return null;
}

/**
 * Validate evidence quality flags
 */
export function validateEvidenceQuality(evidence, mode = getReleaseMode()) {
  const issues = [];
  
  // Check for fallback posture checks
  if (evidence.includes('falling back to posture checks') || 
      evidence.includes('fallback to posture checks')) {
    if (mode === ReleaseMode.RELEASE) {
      issues.push({
        severity: 'error',
        message: 'Evidence contains fallback posture checks - executable tests required in release mode',
      });
    } else {
      issues.push({
        severity: 'warning',
        message: 'Evidence contains fallback posture checks - executable tests preferred',
      });
    }
  }
  
  // Check for generated-on-demand without commit/release identity
  if (evidence.includes('generated-on-demand') && !evidence.includes('commit') && !evidence.includes('release')) {
    if (mode === ReleaseMode.RELEASE) {
      issues.push({
        severity: 'error',
        message: 'Evidence is generated-on-demand without commit/release identity',
      });
    } else {
      issues.push({
        severity: 'warning',
        message: 'Evidence is generated-on-demand without commit/release identity',
      });
    }
  }
  
  // Check for product path not found
  if (evidence.includes('Product path not found') || evidence.includes('product path not found')) {
    issues.push({
      severity: 'error',
      message: 'Evidence contains "Product path not found" - product path resolution required',
    });
  }
  
  return issues;
}

/**
 * Validate product coverage
 */
export function validateProductCoverage(executedTestProductCount, expectedProductCount, mode = getReleaseMode()) {
  const issues = [];
  
  if (executedTestProductCount < expectedProductCount) {
    const missing = expectedProductCount - executedTestProductCount;
    if (mode === ReleaseMode.RELEASE) {
      issues.push({
        severity: 'error',
        message: `Product coverage gap: ${executedTestProductCount}/${expectedProductCount} products executed tests (${missing} missing)`,
      });
    } else {
      issues.push({
        severity: 'warning',
        message: `Product coverage gap: ${executedTestProductCount}/${expectedProductCount} products executed tests (${missing} missing)`,
      });
    }
  }
  
  return issues;
}

/**
 * Process validation results and determine if should fail
 */
export function processValidationResults(violations, warnings, evidence, mode = getReleaseMode()) {
  const issues = [];
  
  // Always fail on violations
  if (violations.length > 0) {
    violations.forEach(v => {
      issues.push({ severity: 'error', message: v });
    });
  }
  
  // Check warning policy
  if (warnings.length > 0 && shouldFailOnWarning(mode)) {
    warnings.forEach(w => {
      issues.push({ severity: 'error', message: w });
    });
  } else if (warnings.length > 0 && shouldReportWarning(mode)) {
    warnings.forEach(w => {
      issues.push({ severity: 'warning', message: w });
    });
  }
  
  // Validate evidence quality
  const qualityIssues = validateEvidenceQuality(evidence.join(' '), mode);
  issues.push(...qualityIssues);
  
  return {
    issues,
    shouldFail: issues.some(i => i.severity === 'error'),
  };
}

/**
 * Log validation results appropriately
 */
export function logValidationResults(results, context = '') {
  if (context) {
    console.log(`\n--- ${context} ---`);
  }
  
  const errors = results.issues.filter(i => i.severity === 'error');
  const warnings = results.issues.filter(i => i.severity === 'warning');
  
  if (errors.length > 0) {
    console.error(`\n❌ Errors (${errors.length}):`);
    errors.forEach(e => console.error(`  - ${e.message}`));
  }
  
  if (warnings.length > 0) {
    console.warn(`\n⚠️  Warnings (${warnings.length}):`);
    warnings.forEach(w => console.warn(`  - ${w.message}`));
  }
  
  if (errors.length === 0 && warnings.length === 0) {
    console.log('\n✓ All validation checks passed');
  }
}
