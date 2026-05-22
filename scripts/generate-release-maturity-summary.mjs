#!/usr/bin/env node

import { existsSync, mkdirSync, readFileSync, readdirSync, writeFileSync } from 'node:fs';
import { execSync } from 'node:child_process';
import path from 'node:path';

const repoRoot = process.cwd();
const releaseEnv = process.env.RELEASE_ENVIRONMENT ?? 'staging';
const evidenceRoot = path.join(repoRoot, 'release-evidence');

function gitValue(command) {
  try {
    return execSync(command, { cwd: repoRoot, encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] }).trim();
  } catch {
    return null;
  }
}

function readJsonIfExists(relativePath) {
  const absolutePath = path.join(repoRoot, relativePath);
  if (!existsSync(absolutePath)) {
    return null;
  }
  return JSON.parse(readFileSync(absolutePath, 'utf8'));
}

const smoke = readJsonIfExists('release-evidence/smoke/smoke-e2e-report.json');
const backup = readJsonIfExists('release-evidence/backup/backup-drill-report.json');
const runtimeProfile = readJsonIfExists('.kernel/evidence/data-cloud-release-runtime-profile.json');
const atomicPosture = readJsonIfExists('.kernel/evidence/atomic-workflow-posture.json');
const implementationPlanProgress = readJsonIfExists('.kernel/evidence/kernel-implementation-plan-progress.json');
const wave2Scorecard = readJsonIfExists('.kernel/evidence/wave2-product-quality-scorecard.json');
const productReleaseReadiness = readJsonIfExists('.kernel/evidence/product-release-readiness.json');

function readPerProductReleaseScorecards() {
  const evidenceDir = path.join(repoRoot, '.kernel/evidence');
  if (!existsSync(evidenceDir)) {
    return [];
  }

  return readdirSync(evidenceDir)
    .filter((entry) => /^product-release-readiness\.[^.]+\.json$/i.test(entry))
    .map((entry) => readJsonIfExists(`.kernel/evidence/${entry}`))
    .filter(Boolean);
}

const perProductReleaseScorecards = readPerProductReleaseScorecards();
const unresolvedP0P1Blockers = perProductReleaseScorecards
  .flatMap((scorecard) => scorecard.blockingGaps ?? [])
  .filter((gap) => gap.severity === 'P0' || gap.severity === 'P1');
const implementationAverageScore = implementationPlanProgress?.summary?.averageMaturityScore ?? null;
const criticalDimensionsBelowThreshold = implementationPlanProgress?.summary?.criticalDimensionsBelowThresholdCount ?? null;
const thresholdPolicyPassed =
  implementationAverageScore !== null
  && implementationAverageScore >= 4.0
  && criticalDimensionsBelowThreshold === 0
  && unresolvedP0P1Blockers.length === 0;

const summary = {
  generatedAt: new Date().toISOString(),
  sourceCommit: process.env.GITHUB_SHA ?? gitValue('git rev-parse HEAD'),
  sourceBranch: process.env.GITHUB_REF_NAME ?? gitValue('git branch --show-current'),
  productScope: productReleaseReadiness?.affectedProducts ?? perProductReleaseScorecards.map((scorecard) => scorecard.productId),
  releaseEnvironment: releaseEnv,
  releaseGate: {
    smoke: {
      available: Boolean(smoke),
      fail: smoke?.summary?.fail ?? null,
      warn: smoke?.summary?.warn ?? null,
      pass: Boolean(smoke && smoke.summary && smoke.summary.fail === 0 && smoke.summary.warn === 0),
    },
    backup: {
      available: Boolean(backup),
      fail: backup?.fail ?? null,
      pass: Boolean(backup && backup.fail === 0),
    },
    runtimeProfile: {
      available: Boolean(runtimeProfile),
      pass: Boolean(runtimeProfile?.pass === true),
      checkCount: runtimeProfile?.checks?.length ?? 0,
    },
    atomicWorkflow: {
      available: Boolean(atomicPosture),
      pass: Boolean(atomicPosture && atomicPosture.summary && atomicPosture.summary.violationCount === 0),
      criticalMutatingRouteCount: atomicPosture?.summary?.criticalMutatingRouteCount ?? 0,
    },
    implementationPlanCoverage: {
      available: Boolean(implementationPlanProgress),
      pass: Boolean(
        implementationPlanProgress
        && implementationPlanProgress.summary
        && implementationPlanProgress.summary.violationCount === 0,
      ),
      coveredDimensions: implementationPlanProgress?.summary?.coveredDimensions ?? null,
      uncoveredDimensions: implementationPlanProgress?.summary?.uncoveredDimensions ?? null,
      wave1Complete: implementationPlanProgress?.summary?.wave1Complete ?? null,
    },
    wave2ProductQuality: {
      available: Boolean(wave2Scorecard),
      productCount: wave2Scorecard?.productCount ?? 0,
      avgScoreRatio: wave2Scorecard
        ? Number(
          (
            wave2Scorecard.scoreRows.reduce((sum, row) => sum + (row.score?.ratio ?? 0), 0)
            / Math.max(1, wave2Scorecard.scoreRows.length)
          ).toFixed(2),
        )
        : null,
    },
    releaseScoreThresholdPolicy: {
      available: Boolean(implementationPlanProgress),
      pass: thresholdPolicyPassed,
      implementationAverageScore,
      criticalDimensionsBelowThreshold,
      unresolvedP0P1Blockers: unresolvedP0P1Blockers.length,
      affectedProductCount: productReleaseReadiness?.affectedProducts?.length ?? perProductReleaseScorecards.length,
    },
  },
};

summary.pass = Object.values(summary.releaseGate).every((entry) => entry.pass === true || entry.available === false);

mkdirSync(evidenceRoot, { recursive: true });

const summaryJsonPath = path.join(evidenceRoot, 'release-summary.json');
const summaryMarkdownPath = path.join(evidenceRoot, 'release-summary.md');

writeFileSync(summaryJsonPath, `${JSON.stringify(summary, null, 2)}\n`, 'utf8');

const markdownLines = [
  '# Data Cloud Release Maturity Summary',
  '',
  `- Generated at: ${summary.generatedAt}`,
  `- Environment: ${summary.releaseEnvironment}`,
  `- Overall pass: ${summary.pass ? 'yes' : 'no'}`,
  '',
  '| Gate | Available | Pass | Notes |',
  '| --- | --- | --- | --- |',
  `| Smoke E2E | ${summary.releaseGate.smoke.available} | ${summary.releaseGate.smoke.pass} | fail=${summary.releaseGate.smoke.fail ?? 'n/a'}, warn=${summary.releaseGate.smoke.warn ?? 'n/a'} |`,
  `| Backup drill | ${summary.releaseGate.backup.available} | ${summary.releaseGate.backup.pass} | fail=${summary.releaseGate.backup.fail ?? 'n/a'} |`,
  `| Runtime profile | ${summary.releaseGate.runtimeProfile.available} | ${summary.releaseGate.runtimeProfile.pass} | checks=${summary.releaseGate.runtimeProfile.checkCount} |`,
  `| Atomic workflow posture | ${summary.releaseGate.atomicWorkflow.available} | ${summary.releaseGate.atomicWorkflow.pass} | critical routes=${summary.releaseGate.atomicWorkflow.criticalMutatingRouteCount} |`,
  `| Implementation plan coverage | ${summary.releaseGate.implementationPlanCoverage.available} | ${summary.releaseGate.implementationPlanCoverage.pass} | covered=${summary.releaseGate.implementationPlanCoverage.coveredDimensions ?? 'n/a'}, uncovered=${summary.releaseGate.implementationPlanCoverage.uncoveredDimensions ?? 'n/a'}, wave1=${summary.releaseGate.implementationPlanCoverage.wave1Complete ?? 'n/a'} |`,
  `| Wave 2 product quality scorecard | ${summary.releaseGate.wave2ProductQuality.available} | n/a | products=${summary.releaseGate.wave2ProductQuality.productCount}, avgScore=${summary.releaseGate.wave2ProductQuality.avgScoreRatio ?? 'n/a'} |`,
  `| Release score threshold policy | ${summary.releaseGate.releaseScoreThresholdPolicy.available} | ${summary.releaseGate.releaseScoreThresholdPolicy.pass} | avg=${summary.releaseGate.releaseScoreThresholdPolicy.implementationAverageScore ?? 'n/a'}, critical<4=${summary.releaseGate.releaseScoreThresholdPolicy.criticalDimensionsBelowThreshold ?? 'n/a'}, blockers=${summary.releaseGate.releaseScoreThresholdPolicy.unresolvedP0P1Blockers} |`,
  '',
];

writeFileSync(summaryMarkdownPath, `${markdownLines.join('\n')}\n`, 'utf8');
console.log(`Generated release summary: ${path.relative(repoRoot, summaryJsonPath)}`);
