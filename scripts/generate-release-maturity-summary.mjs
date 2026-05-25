#!/usr/bin/env node

import { existsSync, mkdirSync, readFileSync, readdirSync, writeFileSync } from 'node:fs';
import { execSync } from 'node:child_process';
import path from 'node:path';
import { pathToFileURL } from 'node:url';

function resolveRepoRoot() {
  const cliRootFlagIndex = process.argv.indexOf('--root');
  const cliRoot = cliRootFlagIndex >= 0 ? process.argv[cliRootFlagIndex + 1] : null;
  const envRoot = process.env.RELEASE_EVIDENCE_ROOT ?? null;
  return path.resolve(cliRoot ?? envRoot ?? process.cwd());
}

const repoRoot = resolveRepoRoot();
const releaseEnv = process.env.RELEASE_ENVIRONMENT ?? 'staging';
const evidenceRoot = path.join(repoRoot, 'release-evidence');
const releaseTargetScore = Number(process.env.RELEASE_TARGET_SCORE ?? '4');

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
const productSloBudgets = readJsonIfExists('.kernel/evidence/product-slo-budgets.json');
const productCostBudgets = readJsonIfExists('.kernel/evidence/product-cost-budgets.json');
const productDomainInvariants = readJsonIfExists('.kernel/evidence/product-domain-invariants.json');
const openApiBreakingChanges = readJsonIfExists('.kernel/evidence/openapi-breaking-changes.json');

function isPassingEvidence(evidence) {
  return Boolean(evidence && evidence.status === 'passed' && (evidence.violations?.length ?? 0) === 0);
}

function readPerProductReleaseScorecards() {
  const evidenceDir = path.join(repoRoot, '.kernel/evidence');
  if (!existsSync(evidenceDir)) {
    return [];
  }

  const affectedProducts = Array.isArray(productReleaseReadiness?.affectedProducts)
    ? productReleaseReadiness.affectedProducts
    : [];
  const scopedProductIds = new Set(affectedProducts);

  return readdirSync(evidenceDir)
    .filter((entry) => /^product-release-readiness\.[^.]+\.json$/i.test(entry))
    .map((entry) => readJsonIfExists(`.kernel/evidence/${entry}`))
    .filter(Boolean)
    .filter((scorecard) => scopedProductIds.size === 0 || scopedProductIds.has(scorecard.productId));
}

const perProductReleaseScorecards = readPerProductReleaseScorecards();
const releaseProductScope =
  Array.isArray(productReleaseReadiness?.affectedProducts) && productReleaseReadiness.affectedProducts.length > 0
    ? productReleaseReadiness.affectedProducts
    : perProductReleaseScorecards.map((scorecard) => scorecard.productId);
const perProductFailedScorecards = perProductReleaseScorecards.filter((scorecard) => {
  const verdictPass = scorecard.releaseVerdict === 'pass';
  const averageScore = Number(scorecard.averageScore ?? 0);
  const hasAllDimensions = Array.isArray(scorecard.dimensions) && scorecard.dimensions.length === 47;
  const allDimensionsScored = hasAllDimensions && scorecard.dimensions.every(d => typeof d?.score === 'number');
  return !verdictPass || averageScore < releaseTargetScore || !hasAllDimensions || !allDimensionsScored;
});

// Validate that all scorecards have 47 dimensions
const scorecardsWithMissingDimensions = perProductReleaseScorecards.filter((scorecard) => {
  return !Array.isArray(scorecard.dimensions) || scorecard.dimensions.length !== 47;
});

const scorecardsWithUnscoredDimensions = perProductReleaseScorecards.filter((scorecard) => {
  return Array.isArray(scorecard.dimensions) && scorecard.dimensions.some(d => typeof d?.score !== 'number');
});
const unresolvedP0P1Blockers = perProductReleaseScorecards
  .flatMap((scorecard) => scorecard.blockingGaps ?? [])
  .filter((gap) => gap.severity === 'P0' || gap.severity === 'P1');
const implementationAverageScore = perProductReleaseScorecards.length > 0
  ? Number((perProductReleaseScorecards.reduce((sum, scorecard) => sum + Number(scorecard.averageScore ?? 0), 0) / perProductReleaseScorecards.length).toFixed(2))
  : null;
const criticalDimensionsBelowThreshold = perProductReleaseScorecards.reduce(
  (sum, scorecard) => sum + (Array.isArray(scorecard.belowTargetDimensions) ? scorecard.belowTargetDimensions.length : 0),
  0,
);
const productSloBudgetsPass = isPassingEvidence(productSloBudgets);
const productCostBudgetsPass = isPassingEvidence(productCostBudgets);
const productDomainInvariantsPass = isPassingEvidence(productDomainInvariants);
const openApiBreakingChangesPass = isPassingEvidence(openApiBreakingChanges);
const thresholdPolicyAvailable = Boolean(
  implementationPlanProgress
  && productSloBudgets
  && productCostBudgets
  && productDomainInvariants
  && openApiBreakingChanges,
);
const thresholdPolicyPassed =
  thresholdPolicyAvailable
  && implementationAverageScore !== null
  && implementationAverageScore >= releaseTargetScore
  && criticalDimensionsBelowThreshold === 0
  && unresolvedP0P1Blockers.length === 0
  && productSloBudgetsPass
  && productCostBudgetsPass
  && productDomainInvariantsPass
  && openApiBreakingChangesPass;

const summary = {
  generatedAt: new Date().toISOString(),
  sourceCommit: process.env.GITHUB_SHA ?? gitValue('git rev-parse HEAD'),
  sourceBranch: process.env.GITHUB_REF_NAME ?? gitValue('git branch --show-current'),
  productScope: releaseProductScope,
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
    productSloBudgets: {
      available: Boolean(productSloBudgets),
      pass: productSloBudgetsPass,
      violationCount: productSloBudgets?.violations?.length ?? 0,
    },
    productCostBudgets: {
      available: Boolean(productCostBudgets),
      pass: productCostBudgetsPass,
      violationCount: productCostBudgets?.violations?.length ?? 0,
    },
    productDomainInvariants: {
      available: Boolean(productDomainInvariants),
      pass: productDomainInvariantsPass,
      violationCount: productDomainInvariants?.violations?.length ?? 0,
    },
    openApiBreakingChanges: {
      available: Boolean(openApiBreakingChanges),
      pass: openApiBreakingChangesPass,
      violationCount: openApiBreakingChanges?.violations?.length ?? 0,
    },
    releaseScoreThresholdPolicy: {
      available: thresholdPolicyAvailable,
      pass: thresholdPolicyPassed,
      implementationAverageScore,
      releaseTargetScore,
      criticalDimensionsBelowThreshold,
      unresolvedP0P1Blockers: unresolvedP0P1Blockers.length,
      productSloBudgetsPass,
      productCostBudgetsPass,
      productDomainInvariantsPass,
      openApiBreakingChangesPass,
      affectedProductCount: releaseProductScope.length,
    },
    perProductReleaseReadiness: {
      available: perProductReleaseScorecards.length > 0,
      pass: perProductReleaseScorecards.length > 0 && perProductFailedScorecards.length === 0 && scorecardsWithMissingDimensions.length === 0 && scorecardsWithUnscoredDimensions.length === 0,
      productCount: perProductReleaseScorecards.length,
      failedProductCount: perProductFailedScorecards.length,
      releaseTargetScore,
      dimensionValidation: {
        requiredDimensionCount: 47,
        productsWithMissingDimensions: scorecardsWithMissingDimensions.length,
        productsWithUnscoredDimensions: scorecardsWithUnscoredDimensions.length,
        allProductsHaveAllDimensions: scorecardsWithMissingDimensions.length === 0,
        allDimensionsScored: scorecardsWithUnscoredDimensions.length === 0,
      },
      failedProducts: perProductFailedScorecards.map((scorecard) => ({
        productId: scorecard.productId,
        releaseVerdict: scorecard.releaseVerdict,
        averageScore: scorecard.averageScore,
        dimensionCount: scorecard.dimensions?.length ?? 0,
      })),
    },
  },
};

const requiredGateKeys = [
  'smoke',
  'backup',
  'runtimeProfile',
  'atomicWorkflow',
  'implementationPlanCoverage',
  'releaseScoreThresholdPolicy',
  'perProductReleaseReadiness',
];

const requiredGateResults = requiredGateKeys.map((gateKey) => {
  const gate = summary.releaseGate?.[gateKey];
  return {
    gateKey,
    available: gate?.available === true,
    pass: gate?.pass === true,
  };
});

summary.releaseGate.requiredGates = requiredGateResults;
summary.pass = requiredGateResults.every((entry) => entry.available === true && entry.pass === true);

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
  `| Product SLO budgets | ${summary.releaseGate.productSloBudgets.available} | ${summary.releaseGate.productSloBudgets.pass} | violations=${summary.releaseGate.productSloBudgets.violationCount} |`,
  `| Product cost budgets | ${summary.releaseGate.productCostBudgets.available} | ${summary.releaseGate.productCostBudgets.pass} | violations=${summary.releaseGate.productCostBudgets.violationCount} |`,
  `| Product domain invariants | ${summary.releaseGate.productDomainInvariants.available} | ${summary.releaseGate.productDomainInvariants.pass} | violations=${summary.releaseGate.productDomainInvariants.violationCount} |`,
  `| OpenAPI breaking changes | ${summary.releaseGate.openApiBreakingChanges.available} | ${summary.releaseGate.openApiBreakingChanges.pass} | violations=${summary.releaseGate.openApiBreakingChanges.violationCount} |`,
  `| Release score threshold policy | ${summary.releaseGate.releaseScoreThresholdPolicy.available} | ${summary.releaseGate.releaseScoreThresholdPolicy.pass} | avg=${summary.releaseGate.releaseScoreThresholdPolicy.implementationAverageScore ?? 'n/a'}, critical<4=${summary.releaseGate.releaseScoreThresholdPolicy.criticalDimensionsBelowThreshold ?? 'n/a'}, blockers=${summary.releaseGate.releaseScoreThresholdPolicy.unresolvedP0P1Blockers}, slo=${summary.releaseGate.releaseScoreThresholdPolicy.productSloBudgetsPass}, cost=${summary.releaseGate.releaseScoreThresholdPolicy.productCostBudgetsPass}, domain=${summary.releaseGate.releaseScoreThresholdPolicy.productDomainInvariantsPass}, openapi=${summary.releaseGate.releaseScoreThresholdPolicy.openApiBreakingChangesPass} |`,
  `| Per-product release readiness | ${summary.releaseGate.perProductReleaseReadiness.available} | ${summary.releaseGate.perProductReleaseReadiness.pass} | products=${summary.releaseGate.perProductReleaseReadiness.productCount}, failed=${summary.releaseGate.perProductReleaseReadiness.failedProductCount}, targetScore=${summary.releaseGate.perProductReleaseReadiness.releaseTargetScore} |`,
  '',
];

writeFileSync(summaryMarkdownPath, `${markdownLines.join('\n')}\n`, 'utf8');
console.log(`Generated release summary: ${path.relative(repoRoot, summaryJsonPath)}`);

if (import.meta.url !== pathToFileURL(process.argv[1]).href) {
  // no-op when imported by tests
}
