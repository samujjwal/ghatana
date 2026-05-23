#!/usr/bin/env node

/**
 * Phase 0: Evidence freshness and staleness checks
 *
 * Validates that evidence artifacts are recent relative to source changes:
 * - Checks evidence timestamps against source file timestamps
 * - Detects stale evidence (source changed but evidence not regenerated)
 * - Validates evidence freshness thresholds
 * - Reports evidence that needs regeneration
 *
 * Usage: node scripts/check-evidence-freshness.mjs [--threshold <hours>]
 */

import { existsSync, readdirSync, readFileSync, statSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

// Default threshold: evidence older than 24 hours is considered stale
const DEFAULT_THRESHOLD_HOURS = 24;

function parseArgs() {
  const args = process.argv.slice(2);
  const threshold = args.includes('--threshold')
    ? parseInt(args[args.indexOf('--threshold') + 1], 10)
    : DEFAULT_THRESHOLD_HOURS;
  const repoRoot = args.includes('--root')
    ? path.resolve(args[args.indexOf('--root') + 1])
    : process.env.EVIDENCE_REPO_ROOT
      ? path.resolve(process.env.EVIDENCE_REPO_ROOT)
      : process.cwd();
  return { threshold, repoRoot };
}

function getEvidenceFiles(repoRoot) {
  const evidenceDir = path.join(repoRoot, '.kernel', 'evidence');

  if (!existsSync(evidenceDir)) {
    return [];
  }

  const files = [];
  const entries = readdirSync(evidenceDir, { withFileTypes: true });

  for (const entry of entries) {
    if (entry.isFile() && entry.name.endsWith('.json')) {
      files.push(path.join(evidenceDir, entry.name));
    }
  }

  return files;
}

export function getSourceFileForEvidence(evidenceFile, repoRoot = process.cwd()) {
  const evidenceName = path.basename(evidenceFile, '.json');
  
  // Map evidence files to their source files
  const sourceMap = {
    'kernel-implementation-plan-progress': 'scripts/check-kernel-implementation-plan-coverage.mjs',
    'product-release-readiness': 'scripts/check-product-release-readiness.mjs',
    'product-release-readiness.data-cloud': 'scripts/check-product-release-readiness.mjs',
    'product-release-readiness.digital-marketing': 'scripts/check-product-release-readiness.mjs',
    'product-release-readiness.finance': 'scripts/check-product-release-readiness.mjs',
    'product-release-readiness.flashit': 'scripts/check-product-release-readiness.mjs',
    'product-release-readiness.phr': 'scripts/check-product-release-readiness.mjs',
    'product-release-readiness.tutorputor': 'scripts/check-product-release-readiness.mjs',
    'atomic-workflow-posture': 'scripts/check-atomic-workflow-failure-injection.mjs',
    'data-cloud-release-runtime-profile': '.github/workflows/data-cloud-release.yml',
    'affected-product-release-profile': 'scripts/check-affected-product-strict-release-profile.mjs',
    'release-summary': 'scripts/generate-release-maturity-summary.mjs',
  };

  const sourcePath = sourceMap[evidenceName];
  if (sourcePath) {
    const fullPath = path.join(repoRoot, sourcePath);
    return existsSync(fullPath) ? fullPath : null;
  }

  return null;
}

function getFileStats(filePath) {
  try {
    const stats = statSync(filePath);
    return {
      mtime: stats.mtime,
      mtimeMs: stats.mtimeMs,
    };
  } catch (error) {
    return null;
  }
}

function isStale(evidenceFile, sourceFile, thresholdHours) {
  const evidenceStats = getFileStats(evidenceFile);
  const sourceStats = sourceFile ? getFileStats(sourceFile) : null;

  if (!evidenceStats) {
    return { stale: true, reason: 'evidence file not accessible' };
  }

  const now = Date.now();
  const evidenceAge = now - evidenceStats.mtimeMs;
  const thresholdMs = thresholdHours * 60 * 60 * 1000;

  // Check if evidence is older than threshold
  if (evidenceAge > thresholdMs) {
    return { 
      stale: true, 
      reason: `evidence is ${Math.round(evidenceAge / (1000 * 60 * 60))} hours old (threshold: ${thresholdHours} hours)` 
    };
  }

  // Check if source is newer than evidence
  if (sourceStats && sourceStats.mtimeMs > evidenceStats.mtimeMs) {
    return { 
      stale: true, 
      reason: `source file changed after evidence (source: ${sourceStats.mtime.toISOString()}, evidence: ${evidenceStats.mtime.toISOString()})` 
    };
  }

  return { stale: false };
}

export function checkEvidenceFreshness({ threshold, repoRoot }) {
  const evidenceFiles = getEvidenceFiles(repoRoot);
  const results = [];
  const staleEvidence = [];

  for (const evidenceFile of evidenceFiles) {
    const sourceFile = getSourceFileForEvidence(evidenceFile, repoRoot);
    const staleness = isStale(evidenceFile, sourceFile, threshold);
    
    const result = {
      evidenceFile: path.relative(repoRoot, evidenceFile),
      sourceFile: sourceFile ? path.relative(repoRoot, sourceFile) : null,
      stale: staleness.stale,
      reason: staleness.reason,
    };

    results.push(result);

    if (staleness.stale) {
      staleEvidence.push(result);
    }
  }

  return {
    thresholdHours: threshold,
    totalEvidenceFiles: evidenceFiles.length,
    staleEvidenceCount: staleEvidence.length,
    results,
    staleEvidence,
  };
}

function main() {
  const { threshold, repoRoot } = parseArgs();

  console.log(`Checking evidence freshness (threshold: ${threshold} hours)...\n`);

  const report = checkEvidenceFreshness({ threshold, repoRoot });

  console.log(`Total evidence files: ${report.totalEvidenceFiles}`);
  console.log(`Stale evidence files: ${report.staleEvidenceCount}`);

  if (report.staleEvidenceCount === 0) {
    console.log('\n✓ All evidence is fresh');
    process.exit(0);
  }

  console.log('\n✗ Stale evidence found:\n');
  for (const stale of report.staleEvidence) {
    console.log(`  ${stale.evidenceFile}:`);
    console.log(`    Reason: ${stale.reason}`);
    if (stale.sourceFile) {
      console.log(`    Source: ${stale.sourceFile}`);
    }
    console.log('');
  }

  console.log('Regenerate stale evidence by running the corresponding check scripts.');
  process.exit(1);
}

const isDirectRun = process.argv[1] && fileURLToPath(import.meta.url) === path.resolve(process.argv[1]);

if (isDirectRun) {
  main();
}
