#!/usr/bin/env node

/**
 * Phase 0: Evidence freshness and staleness checks
 *
 * Validates that evidence artifacts are recent relative to source changes:
 * - Checks evidence timestamps against source file timestamps
 * - Detects stale evidence (source changed but evidence not regenerated)
 * - Validates evidence freshness thresholds based on evidence type policy
 * - Reports evidence that needs regeneration
 * - Fails critical evidence if stale
 *
 * Usage: node scripts/check-evidence-freshness.mjs [--threshold <hours>]
 */

import { existsSync, readdirSync, readFileSync, statSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '..');

const POLICY_FILE = path.join(repoRoot, 'config', 'evidence-freshness-policy.json');

// Default threshold: evidence older than 24 hours is considered stale
const DEFAULT_THRESHOLD_HOURS = 24;

function loadFreshnessPolicy(repoRoot) {
  try {
    const policyPath = path.join(repoRoot, 'config', 'evidence-freshness-policy.json');
    return JSON.parse(readFileSync(policyPath, 'utf8'));
  } catch (error) {
    console.warn('Warning: Failed to load freshness policy, using defaults:', error.message);
    return {
      defaultThresholdHours: DEFAULT_THRESHOLD_HOURS,
      evidenceFileMappings: {},
      evidenceTypes: {},
      exemptions: { exemptedFiles: [], exemptedPatterns: [] }
    };
  }
}

function getEvidenceType(evidenceFileName, policy) {
  // Check direct mapping first
  const mappedType = policy.evidenceFileMappings?.[evidenceFileName];
  if (mappedType) {
    return mappedType;
  }

  // Check exemptions
  const exemptedFiles = policy.exemptions?.exemptedFiles ?? [];
  const exemptedPatterns = policy.exemptions?.exemptedPatterns ?? [];
  
  if (exemptedFiles.includes(evidenceFileName)) {
    return 'exempted';
  }

  for (const pattern of exemptedPatterns) {
    const regex = new RegExp(pattern.replace(/\*/g, '.*'));
    if (regex.test(evidenceFileName)) {
      return 'exempted';
    }
  }

  // Default to runtime-executed for unknown evidence
  return 'runtime-executed';
}

function getThresholdForType(evidenceType, policy, defaultThreshold) {
  const typeConfig = policy.evidenceTypes?.[evidenceType];
  if (typeConfig && typeConfig.thresholdHours) {
    return typeConfig.thresholdHours;
  }
  return policy.defaultThresholdHours ?? defaultThreshold;
}

function isCriticalEvidence(evidenceType, policy) {
  const typeConfig = policy.evidenceTypes?.[evidenceType];
  return typeConfig?.critical ?? false;
}

function shouldFailOnStale(evidenceType, policy) {
  const typeConfig = policy.evidenceTypes?.[evidenceType];
  return typeConfig?.failOnStale ?? false;
}

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

function isStale(evidenceFile, sourceFile, thresholdHours, evidenceType, policy) {
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
      reason: `evidence is ${Math.round(evidenceAge / (1000 * 60 * 60))} hours old (threshold: ${thresholdHours} hours for type: ${evidenceType})` 
    };
  }

  // Check if source is newer than evidence (only if policy requires source backing)
  if (sourceStats && shouldFailOnStale(evidenceType, policy) && sourceStats.mtimeMs > evidenceStats.mtimeMs) {
    return { 
      stale: true, 
      reason: `source file changed after evidence (source: ${sourceStats.mtime.toISOString()}, evidence: ${evidenceStats.mtime.toISOString()})` 
    };
  }

  return { stale: false };
}

export function checkEvidenceFreshness({ threshold, repoRoot }) {
  const policy = loadFreshnessPolicy(repoRoot);
  const evidenceFiles = getEvidenceFiles(repoRoot);
  const results = [];
  const staleEvidence = [];
  const criticalStaleEvidence = [];

  for (const evidenceFile of evidenceFiles) {
    const evidenceFileName = path.basename(evidenceFile);
    const evidenceType = getEvidenceType(evidenceFileName, policy);
    
    // Skip exempted evidence
    if (evidenceType === 'exempted') {
      results.push({
        evidenceFile: path.relative(repoRoot, evidenceFile),
        evidenceType,
        stale: false,
        reason: 'exempted from freshness checks',
        exempted: true,
      });
      continue;
    }

    const sourceFile = getSourceFileForEvidence(evidenceFile, repoRoot);
    const typeThreshold = getThresholdForType(evidenceType, policy, threshold);
    const staleness = isStale(evidenceFile, sourceFile, typeThreshold, evidenceType, policy);
    
    const result = {
      evidenceFile: path.relative(repoRoot, evidenceFile),
      sourceFile: sourceFile ? path.relative(repoRoot, sourceFile) : null,
      evidenceType,
      thresholdHours: typeThreshold,
      critical: isCriticalEvidence(evidenceType, policy),
      stale: staleness.stale,
      reason: staleness.reason,
    };

    results.push(result);

    if (staleness.stale) {
      staleEvidence.push(result);
      if (result.critical) {
        criticalStaleEvidence.push(result);
      }
    }
  }

  return {
    thresholdHours: threshold,
    totalEvidenceFiles: evidenceFiles.length,
    staleEvidenceCount: staleEvidence.length,
    criticalStaleEvidenceCount: criticalStaleEvidence.length,
    results,
    staleEvidence,
    criticalStaleEvidence,
  };
}

function main() {
  const { threshold, repoRoot } = parseArgs();

  console.log(`Checking evidence freshness (threshold: ${threshold} hours)...\n`);

  const report = checkEvidenceFreshness({ threshold, repoRoot });

  console.log(`Total evidence files: ${report.totalEvidenceFiles}`);
  console.log(`Stale evidence files: ${report.staleEvidenceCount}`);
  console.log(`Critical stale evidence files: ${report.criticalStaleEvidenceCount}`);

  if (report.staleEvidenceCount === 0) {
    console.log('\n✓ All evidence is fresh');
    process.exit(0);
  }

  console.log('\n✗ Stale evidence found:\n');
  for (const stale of report.staleEvidence) {
    const criticalMarker = stale.critical ? '[CRITICAL] ' : '';
    console.log(`  ${criticalMarker}${stale.evidenceFile}:`);
    console.log(`    Type: ${stale.evidenceType}`);
    console.log(`    Threshold: ${stale.thresholdHours} hours`);
    console.log(`    Reason: ${stale.reason}`);
    if (stale.sourceFile) {
      console.log(`    Source: ${stale.sourceFile}`);
    }
    console.log('');
  }

  // Fail if critical evidence is stale
  if (report.criticalStaleEvidenceCount > 0) {
    console.log('✗ CRITICAL: Critical evidence is stale. Regenerate before proceeding.');
    process.exit(1);
  }

  console.log('Regenerate stale evidence by running the corresponding check scripts.');
  process.exit(1);
}

const isDirectRun = process.argv[1] && fileURLToPath(import.meta.url) === path.resolve(process.argv[1]);

if (isDirectRun) {
  main();
}
