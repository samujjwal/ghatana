#!/usr/bin/env node

/**
 * Release Proof Script Base Module
 *
 * Provides a standardized structure and common utilities for all release proof scripts.
 * This ensures consistency across behavioral proof, failure-injection, and validation scripts.
 *
 * Usage: Import and extend this module in release proof scripts
 */

import { readFileSync, existsSync, writeFileSync, mkdirSync } from 'node:fs';
import path from 'node:path';

import { getReleaseMode, processValidationResults, logValidationResults } from './release-evidence-policy.mjs';
import { getActiveProducts, resolveProductForProof } from './product-registry-helper.mjs';

/**
 * Base class for release proof scripts
 */
export class ReleaseProofScript {
  constructor(scriptName, repoRoot) {
    this.scriptName = scriptName;
    this.repoRoot = repoRoot || path.resolve(process.cwd(), '..');
    this.RELEASE_MODE = getReleaseMode();
    this.violations = [];
    this.warnings = [];
    this.evidence = [];
    this.stableGeneratedAt = process.env.GITHUB_SHA ? `commit:${process.env.GITHUB_SHA}` : 'generated-on-demand';
  }

  /**
   * Log an error
   */
  logError(message) {
    this.violations.push(message);
    console.error(`❌ ERROR: ${message}`);
  }

  /**
   * Log a warning
   */
  logWarning(message) {
    this.warnings.push(message);
    console.warn(`⚠️  WARNING: ${message}`);
  }

  /**
   * Log a success message
   */
  logSuccess(message) {
    console.log(`✓ ${message}`);
  }

  /**
   * Log evidence
   */
  logEvidence(message) {
    this.evidence.push(message);
    console.log(`  📋 ${message}`);
  }

  /**
   * Generate evidence report
   */
  generateEvidenceReport(subdirectory) {
    const evidenceDir = path.join(this.repoRoot, '.kernel', 'evidence', subdirectory);
    
    if (!existsSync(evidenceDir)) {
      mkdirSync(evidenceDir, { recursive: true });
    }

    const report = {
      timestamp: this.stableGeneratedAt,
      violations: this.violations,
      warnings: this.warnings,
      evidence: this.evidence,
      summary: {
        totalViolations: this.violations.length,
        totalWarnings: this.warnings.length,
        totalEvidence: this.evidence.length,
      }
    };

    const reportPath = path.join(evidenceDir, `${subdirectory}-latest.json`);
    writeFileSync(reportPath, JSON.stringify(report, null, 2));
    
    console.log(`\n📄 Evidence report generated: ${reportPath}`);
  }

  /**
   * Process validation results and exit appropriately
   */
  finalize() {
    console.log('\n--- Summary ---');
    console.log(`Errors: ${this.violations.length}`);
    console.log(`Warnings: ${this.warnings.length}`);
    console.log(`Evidence items: ${this.evidence.length}`);

    const validationResults = processValidationResults(this.violations, this.warnings, this.evidence, this.RELEASE_MODE);
    logValidationResults(validationResults, `${this.scriptName} Validation`);

    if (validationResults.shouldFail) {
      process.exit(1);
    }

    console.log(`\n${this.scriptName} check passed.`);
  }

  /**
   * Get products to validate
   */
  getProducts(productArg) {
    const registryProducts = getActiveProducts();
    const products = registryProducts
      .map(({ productId }) => resolveProductForProof(productId))
      .filter(p => p !== null);

    return productArg
      ? products.filter(p => p.name.toLowerCase().includes(productArg.toLowerCase()))
      : products;
  }

  /**
   * Check if a file exists
   */
  fileExists(relativePath) {
    return existsSync(path.join(this.repoRoot, relativePath));
  }

  /**
   * Read a JSON file
   */
  readJson(relativePath) {
    const absolutePath = path.join(this.repoRoot, relativePath);
    if (!existsSync(absolutePath)) {
      return null;
    }

    try {
      return JSON.parse(readFileSync(absolutePath, 'utf8'));
    } catch (error) {
      this.logError(`Failed to parse JSON file: ${relativePath} (${error.message})`);
      return null;
    }
  }

  /**
   * Write a JSON file
   */
  writeJson(relativePath, data) {
    const absolutePath = path.join(this.repoRoot, relativePath);
    mkdirSync(path.dirname(absolutePath), { recursive: true });
    writeFileSync(absolutePath, JSON.stringify(data, null, 2));
  }
}

/**
 * Standard script entry point
 */
export function runReleaseProofScript(scriptName, validateFn, options = {}) {
  const { repoRoot, productArg } = options;
  const script = new ReleaseProofScript(scriptName, repoRoot);

  console.log(`Checking ${scriptName} across products...\n`);

  try {
    validateFn(script, productArg);
  } catch (error) {
    script.logError(`Script execution failed: ${error.message}`);
    console.error(error);
    process.exit(1);
  }

  script.finalize();
}
