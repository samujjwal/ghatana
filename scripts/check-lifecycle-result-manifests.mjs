#!/usr/bin/env node
/**
 * check-lifecycle-result-manifests.mjs
 *
 * Validates that a lifecycle run output directory contains all required
 * manifest files for the phase that was executed, and that the
 * lifecycle-result.json has required fields (correlationId, sourceRef, status).
 *
 * Usage:
 *   node scripts/check-lifecycle-result-manifests.mjs <output-dir>
 *
 * Example:
 *   node scripts/check-lifecycle-result-manifests.mjs .kernel-runs/digital-marketing/validate
 */

import { existsSync, readFileSync, readdirSync, statSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const repoRoot = path.resolve(path.dirname(__filename), '..');

/**
 * Recursively list all files under a directory.
 * @param {string} dir
 * @returns {string[]}
 */
function listFiles(dir) {
  if (!existsSync(dir)) return [];
  const results = [];
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const fullPath = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      results.push(...listFiles(fullPath));
    } else {
      results.push(fullPath);
    }
  }
  return results;
}

/**
 * Find the most recently modified file with the given name under a directory.
 * @param {string} dir
 * @param {string} filename
 * @returns {string | undefined}
 */
function findLatestFile(dir, filename) {
  const matches = listFiles(dir).filter((f) => path.basename(f) === filename);
  if (matches.length === 0) return undefined;
  return matches.sort((a, b) => {
    const aStat = statSync(a);
    const bStat = statSync(b);
    return bStat.mtimeMs - aStat.mtimeMs;
  })[0];
}

/**
 * Required manifest files that must always be present in the run output,
 * regardless of phase. These are at root of the output directory.
 */
const ROOT_REQUIRED_FILES = [
  'lifecycle-plan.json',
  'lifecycle-result.json',
];

/**
 * Required manifest files that must be present in the per-run phase folder.
 * Always required for any phase.
 */
const PER_RUN_ALWAYS_REQUIRED = [
  'lifecycle-result.json',
  'gate-result-manifest.json',
  'lifecycle-health-snapshot.json',
  'lifecycle-events.json',
];

/**
 * Phase-specific required manifests. These are only required when the lifecycle
 * result indicates the corresponding phase ran.
 */
const PHASE_SPECIFIC_REQUIRED = {
  deploy: ['deployment-manifest.json'],
  verify: ['verify-health-report.json'],
  rollback: ['rollback-manifest.json'],
};

/**
 * Required fields in lifecycle-result.json (execution hardening rules).
 */
const REQUIRED_RESULT_FIELDS = ['runId', 'correlationId', 'sourceRef', 'status', 'productId', 'phase'];

/**
 * Validate the lifecycle result JSON.
 * @param {Record<string, unknown>} result
 * @returns {string[]} errors
 */
function validateResultFields(result) {
  const errors = [];
  for (const field of REQUIRED_RESULT_FIELDS) {
    const value = result[field];
    if (value === undefined || value === null || value === '') {
      errors.push(`lifecycle-result.json missing required field: ${field}`);
    }
  }
  const validStatuses = ['succeeded', 'failed', 'skipped'];
  if (!validStatuses.includes(String(result['status'] ?? ''))) {
    errors.push(`lifecycle-result.json has invalid status: ${String(result['status'])}`);
  }
  return errors;
}

/**
 * Validate lifecycle run output directory.
 * @param {string} outputDir - Absolute or repo-relative path to the output directory.
 * @returns {{ errors: string[], warnings: string[], phase: string | undefined }}
 */
export function validateLifecycleResultManifests(outputDir) {
  const absoluteDir = path.isAbsolute(outputDir)
    ? outputDir
    : path.resolve(repoRoot, outputDir);

  const errors = [];
  const warnings = [];

  if (!existsSync(absoluteDir)) {
    return { errors: [`Output directory not found: ${absoluteDir}`], warnings, phase: undefined };
  }

  // 1. Check root-level required files
  for (const filename of ROOT_REQUIRED_FILES) {
    const filePath = path.join(absoluteDir, filename);
    if (!existsSync(filePath)) {
      errors.push(`Missing root manifest: ${filename}`);
    }
  }

  // 2. Load lifecycle-result.json from root (written by KernelLifecycleService)
  const rootResultPath = path.join(absoluteDir, 'lifecycle-result.json');
  let rootResult = undefined;
  let phase = undefined;

  if (existsSync(rootResultPath)) {
    try {
      rootResult = JSON.parse(readFileSync(rootResultPath, 'utf8'));
      phase = rootResult['phase'];
      errors.push(...validateResultFields(rootResult));
    } catch {
      errors.push('lifecycle-result.json is not valid JSON');
    }
  }

  // 3. Find the most recent per-run lifecycle-result.json (under product/runId/phase/)
  const perRunResult = findLatestFile(absoluteDir, 'lifecycle-result.json');
  const perRunDir = perRunResult ? path.dirname(perRunResult) : undefined;
  const effectivePhase = phase ?? rootResult?.['phase'];

  // 4. Check per-run always-required manifests
  if (perRunDir) {
    for (const filename of PER_RUN_ALWAYS_REQUIRED) {
      const filePath = path.join(perRunDir, filename);
      if (!existsSync(filePath)) {
        // If it's not in the per-run dir, try anywhere in the output
        const anywhereMatch = findLatestFile(absoluteDir, filename);
        if (anywhereMatch === undefined) {
          errors.push(`Missing required manifest: ${filename}`);
        }
      }
    }

    // 5. Validate per-run lifecycle-result.json fields
    try {
      const perRunResultData = JSON.parse(readFileSync(perRunResult, 'utf8'));
      const perRunErrors = validateResultFields(perRunResultData);
      // Only report per-run errors that weren't already reported from root
      if (rootResult === undefined) {
        errors.push(...perRunErrors);
      }
    } catch {
      errors.push('Per-run lifecycle-result.json is not valid JSON');
    }
  } else if (existsSync(rootResultPath)) {
    // No per-run dir found — check root for always-required manifests as fallback
    for (const filename of PER_RUN_ALWAYS_REQUIRED) {
      if (filename === 'lifecycle-result.json') continue;
      const anywhereMatch = findLatestFile(absoluteDir, filename);
      if (anywhereMatch === undefined) {
        warnings.push(`Phase-specific manifest not found (may be expected for dry-run): ${filename}`);
      }
    }
  }

  // 6. Check phase-specific required manifests
  if (effectivePhase && effectivePhase in PHASE_SPECIFIC_REQUIRED) {
    const phaseFiles = PHASE_SPECIFIC_REQUIRED[effectivePhase];
    for (const filename of phaseFiles) {
      const match = findLatestFile(absoluteDir, filename);
      if (match === undefined) {
        errors.push(`Missing phase-specific manifest for ${effectivePhase}: ${filename}`);
      }
    }
  }

  return { errors, warnings, phase: effectivePhase };
}

// Run as CLI
if (process.argv[1] && path.resolve(process.argv[1]) === __filename) {
  const outputDir = process.argv[2];
  if (!outputDir) {
    console.error('Usage: node scripts/check-lifecycle-result-manifests.mjs <output-dir>');
    process.exit(1);
  }

  const { errors, warnings, phase } = validateLifecycleResultManifests(outputDir);

  if (warnings.length > 0) {
    for (const w of warnings) {
      console.warn(`  WARNING: ${w}`);
    }
  }

  if (errors.length === 0) {
    console.log(`Lifecycle result manifests check passed (phase: ${phase ?? 'unknown'}).`);
    process.exit(0);
  }

  console.error(`Lifecycle result manifests check FAILED (phase: ${phase ?? 'unknown'}):`);
  for (const err of errors) {
    console.error(`  - ${err}`);
  }
  process.exit(1);
}
