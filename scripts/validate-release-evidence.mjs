#!/usr/bin/env node

import { existsSync, readFileSync, statSync } from 'node:fs';
import { execSync } from 'node:child_process';
import path from 'node:path';

const repoRoot = process.cwd();
const releaseEnvironment = process.env.RELEASE_ENVIRONMENT ?? 'staging';

const requiredEvidenceFiles = [
  'release-evidence/smoke/smoke-e2e-report.json',
  'release-evidence/backup/backup-drill-report.json',
  '.kernel/evidence/data-cloud-release-runtime-profile.json',
  '.kernel/evidence/atomic-workflow-posture.json',
  '.kernel/evidence/kernel-implementation-plan-progress.json',
  'release-evidence/release-summary.json',
];

function readJson(relativePath, errors) {
  const absolutePath = path.join(repoRoot, relativePath);
  if (!existsSync(absolutePath)) {
    errors.push(`Missing evidence file: ${relativePath}`);
    return null;
  }

  const stats = statSync(absolutePath);
  if (stats.size === 0) {
    errors.push(`Evidence file is empty: ${relativePath}`);
    return null;
  }

  try {
    return JSON.parse(readFileSync(absolutePath, 'utf8'));
  } catch (error) {
    errors.push(`Evidence file is invalid JSON: ${relativePath} (${error.message})`);
    return null;
  }
}

function gitValue(command) {
  try {
    return execSync(command, { cwd: repoRoot, encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] }).trim();
  } catch {
    return null;
  }
}

function assertRecent(relativePath, maxHours, errors) {
  const absolutePath = path.join(repoRoot, relativePath);
  if (!existsSync(absolutePath)) {
    return;
  }
  const modifiedAt = statSync(absolutePath).mtimeMs;
  const ageHours = (Date.now() - modifiedAt) / (1000 * 60 * 60);
  if (ageHours > maxHours) {
    errors.push(`Evidence file is stale (> ${maxHours}h): ${relativePath}`);
  }
}

function validateRequiredContent(evidenceByPath, errors) {
  const smoke = evidenceByPath.get('release-evidence/smoke/smoke-e2e-report.json');
  if (smoke) {
    if (!Array.isArray(smoke.results) || smoke.results.length === 0) {
      errors.push('Smoke report must contain non-empty results');
    }
    if (!smoke.summary || typeof smoke.summary.fail !== 'number') {
      errors.push('Smoke report must contain summary.fail');
    }
  }

  const backup = evidenceByPath.get('release-evidence/backup/backup-drill-report.json');
  if (backup) {
    if (!Array.isArray(backup.results) || backup.results.length === 0) {
      errors.push('Backup report must contain non-empty results');
    }
    if (typeof backup.fail !== 'number') {
      errors.push('Backup report must contain fail count');
    }
  }

  const runtimeProfile = evidenceByPath.get('.kernel/evidence/data-cloud-release-runtime-profile.json');
  if (runtimeProfile) {
    if (runtimeProfile.pass !== true) {
      errors.push('Runtime profile evidence must pass');
    }
    if (!Array.isArray(runtimeProfile.checks) || runtimeProfile.checks.length === 0) {
      errors.push('Runtime profile evidence must include executed checks');
    }
  }

  const atomic = evidenceByPath.get('.kernel/evidence/atomic-workflow-posture.json');
  if (atomic) {
    const violations = atomic.summary?.violationCount;
    if (typeof violations !== 'number') {
      errors.push('Atomic workflow evidence must include summary.violationCount');
    }
  }

  const planProgress = evidenceByPath.get('.kernel/evidence/kernel-implementation-plan-progress.json');
  if (planProgress) {
    if (typeof planProgress.summary?.averageMaturityScore !== 'number') {
      errors.push('Implementation plan evidence must include summary.averageMaturityScore');
    }
    if (typeof planProgress.summary?.criticalDimensionsBelowThresholdCount !== 'number') {
      errors.push('Implementation plan evidence must include summary.criticalDimensionsBelowThresholdCount');
    }
  }

  const summary = evidenceByPath.get('release-evidence/release-summary.json');
  if (summary) {
    if (summary.releaseEnvironment !== releaseEnvironment) {
      errors.push(`Release summary environment mismatch: expected ${releaseEnvironment}, got ${summary.releaseEnvironment}`);
    }

    const currentCommit = process.env.GITHUB_SHA ?? gitValue('git rev-parse HEAD');
    if (currentCommit && summary.sourceCommit && summary.sourceCommit !== currentCommit) {
      errors.push(`Release summary commit mismatch: expected ${currentCommit}, got ${summary.sourceCommit}`);
    }

    const currentBranch = process.env.GITHUB_REF_NAME ?? gitValue('git branch --show-current');
    if (currentBranch && summary.sourceBranch && summary.sourceBranch !== currentBranch) {
      errors.push(`Release summary branch mismatch: expected ${currentBranch}, got ${summary.sourceBranch}`);
    }

    const threshold = summary.releaseGate?.releaseScoreThresholdPolicy;
    if (!threshold || threshold.pass !== true) {
      errors.push('Release score threshold policy failed: critical dimensions below threshold or unresolved P0/P1 blockers exist');
    }
  }
}

function main() {
  const errors = [];
  const evidenceByPath = new Map();

  for (const relativePath of requiredEvidenceFiles) {
    const content = readJson(relativePath, errors);
    if (content) {
      evidenceByPath.set(relativePath, content);
    }
    assertRecent(relativePath, 72, errors);
  }

  validateRequiredContent(evidenceByPath, errors);

  if (errors.length > 0) {
    console.error('Release evidence validation failed:');
    for (const error of errors) {
      console.error(`- ${error}`);
    }
    process.exit(1);
  }

  console.log('Release evidence validation passed.');
}

main();
