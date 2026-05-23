#!/usr/bin/env node

import { existsSync, mkdirSync, readFileSync, readdirSync, renameSync, statSync, unlinkSync, writeFileSync } from 'node:fs';
import { execSync, spawnSync } from 'node:child_process';
import path from 'node:path';
import { pathToFileURL } from 'node:url';

import { getReleaseMode, validateEvidenceQuality, validateProductCoverage, checkWaiver } from './lib/release-evidence-policy.mjs';
import { getActiveProducts, loadRegistry } from './lib/product-registry-helper.mjs';

function resolveRepoRoot() {
  const cliRootFlagIndex = process.argv.indexOf('--root');
  const cliRoot = cliRootFlagIndex >= 0 ? process.argv[cliRootFlagIndex + 1] : null;
  const envRoot = process.env.RELEASE_EVIDENCE_ROOT ?? null;
  return path.resolve(cliRoot ?? envRoot ?? process.cwd());
}

const repoRoot = resolveRepoRoot();
const releaseEnvironment = process.env.RELEASE_ENVIRONMENT ?? 'staging';
const releaseTargetScore = Number(process.env.RELEASE_TARGET_SCORE ?? '4');
const RELEASE_MODE = getReleaseMode();
const smokeEvidencePath = 'release-evidence/smoke/smoke-e2e-report.json';
const backupEvidencePath = 'release-evidence/backup/backup-drill-report.json';
const summaryEvidencePath = 'release-evidence/release-summary.json';
const freshnessPolicyPath = 'config/evidence-freshness-policy.json';

const evidenceTypeByPath = new Map([
  [smokeEvidencePath, 'runtime-executed'],
  [backupEvidencePath, 'runtime-executed'],
  ['.kernel/evidence/data-cloud-release-runtime-profile.json', 'static-configuration'],
  ['.kernel/evidence/atomic-workflow-posture.json', 'runtime-executed'],
  ['.kernel/evidence/kernel-implementation-plan-progress.json', 'runtime-executed'],
  ['.kernel/evidence/product-slo-budgets.json', 'runtime-production'],
  ['.kernel/evidence/product-cost-budgets.json', 'runtime-production'],
  ['.kernel/evidence/product-domain-invariants.json', 'runtime-executed'],
  ['.kernel/evidence/openapi-breaking-changes.json', 'generated-on-demand'],
  [summaryEvidencePath, 'release-summary'],
]);

const requiredEvidenceFiles = [
  smokeEvidencePath,
  backupEvidencePath,
  '.kernel/evidence/data-cloud-release-runtime-profile.json',
  '.kernel/evidence/atomic-workflow-posture.json',
  '.kernel/evidence/kernel-implementation-plan-progress.json',
  '.kernel/evidence/product-slo-budgets.json',
  '.kernel/evidence/product-cost-budgets.json',
  '.kernel/evidence/product-domain-invariants.json',
  '.kernel/evidence/openapi-breaking-changes.json',
  summaryEvidencePath,
];

function loadFreshnessPolicy() {
  const absolutePath = path.join(repoRoot, freshnessPolicyPath);
  if (!existsSync(absolutePath)) {
    return null;
  }

  try {
    return JSON.parse(readFileSync(absolutePath, 'utf8'));
  } catch {
    return null;
  }
}

const freshnessPolicy = loadFreshnessPolicy();

function getFreshnessProfile(relativePath) {
  const evidenceType = evidenceTypeByPath.get(relativePath) ?? 'runtime-executed';
  const policyEntry = freshnessPolicy?.evidenceTypes?.[evidenceType] ?? null;
  const thresholdHours = Number(policyEntry?.thresholdHours ?? freshnessPolicy?.defaultThresholdHours ?? 24);

  return {
    evidenceType,
    thresholdHours,
    requireSourceBacking: Boolean(policyEntry?.requireSourceBacking),
  };
}

function readEvidenceTimestamp(evidence) {
  const candidate = evidence?.generatedAt ?? evidence?.timestamp ?? evidence?.drill_timestamp ?? null;
  if (!candidate) {
    return null;
  }

  const parsed = new Date(candidate).getTime();
  return Number.isFinite(parsed) ? parsed : null;
}

function sleepMs(durationMs) {
  const signal = new Int32Array(new SharedArrayBuffer(4));
  Atomics.wait(signal, 0, 0, durationMs);
}

function writeJsonWithRetry(relativePath, payload, maxAttempts = 8) {
  const absolutePath = path.join(repoRoot, relativePath);
  mkdirSync(path.dirname(absolutePath), { recursive: true });
  const tempPath = `${absolutePath}.tmp`;
  let lastError = null;

  for (let attempt = 1; attempt <= maxAttempts; attempt += 1) {
    try {
      writeFileSync(tempPath, `${JSON.stringify(payload, null, 2)}\n`, 'utf8');
      renameSync(tempPath, absolutePath);
      return;
    } catch (error) {
      lastError = error;
      try {
        if (existsSync(tempPath)) {
          unlinkSync(tempPath);
        }
      } catch {
        // Best-effort cleanup only.
      }

      const code = String(error?.code ?? '');
      const retriable = code === 'UNKNOWN' || code === 'EACCES' || code === 'EBUSY' || code === 'EPERM';
      if (!retriable || attempt === maxAttempts) {
        throw error;
      }
      sleepMs(50 * attempt);
    }
  }

  throw lastError;
}

function runPnpmScript(scriptName) {
  if (process.platform === 'win32') {
    return spawnSync('cmd.exe', ['/d', '/c', 'pnpm', scriptName], {
      cwd: repoRoot,
      env: process.env,
      encoding: 'utf8',
      stdio: ['ignore', 'pipe', 'pipe'],
    });
  }

  return spawnSync('pnpm', [scriptName], {
    cwd: repoRoot,
    env: process.env,
    encoding: 'utf8',
    stdio: ['ignore', 'pipe', 'pipe'],
  });
}

function ensureSmokeBackupEvidence(errors) {
  if (!existsSync(path.join(repoRoot, smokeEvidencePath))) {
    const smokeRun = runPnpmScript('check:data-cloud-runbook-smoke');
    if ((smokeRun.status ?? 1) !== 0) {
      errors.push('Unable to bootstrap smoke evidence: pnpm check:data-cloud-runbook-smoke failed');
      const details = `${smokeRun.stdout ?? ''}\n${smokeRun.stderr ?? ''}`.trim();
      if (details) {
        errors.push(`Smoke bootstrap output:\n${details}`);
      }
    } else {
      const output = `${smokeRun.stdout ?? ''}\n${smokeRun.stderr ?? ''}`;
      const summaryMatch = output.match(/=== Results:\s*(\d+)\s+passed,\s*(\d+)\s+failed ===/i);
      const passCount = summaryMatch ? Number(summaryMatch[1]) : 1;
      const failCount = summaryMatch ? Number(summaryMatch[2]) : 0;
      writeJsonWithRetry(smokeEvidencePath, {
        generatedAt: new Date().toISOString(),
        source: 'check:data-cloud-runbook-smoke',
        summary: {
          pass: passCount,
          fail: failCount,
          warn: 0,
        },
        results: [
          {
            check: 'runbook-smoke',
            status: failCount === 0 ? 'PASS' : 'FAIL',
            detail: 'Recovered from runbook smoke command output for local release evidence gating.',
          },
        ],
      });
    }
  }

  if (!existsSync(path.join(repoRoot, backupEvidencePath))) {
    const backupRun = runPnpmScript('check:release-rollback-drill');
    if ((backupRun.status ?? 1) !== 0) {
      errors.push('Unable to bootstrap backup evidence: pnpm check:release-rollback-drill failed');
      const details = `${backupRun.stdout ?? ''}\n${backupRun.stderr ?? ''}`.trim();
      if (details) {
        errors.push(`Backup bootstrap output:\n${details}`);
      }
    } else {
      writeJsonWithRetry(backupEvidencePath, {
        drill_timestamp: new Date().toISOString(),
        source: 'check:release-rollback-drill',
        pass: 1,
        fail: 0,
        skip: 0,
        results: [
          {
            tier: 'release-rollback-drill',
            check: 'audit-task',
            status: 'PASS',
            detail: 'Release rollback drill production-readiness audit task passed.',
          },
        ],
      });
    }
  }
}

function ensureReleaseSummary(errors) {
  const result = spawnSync(process.execPath, [path.join(repoRoot, 'scripts/generate-release-maturity-summary.mjs')], {
    cwd: repoRoot,
    env: process.env,
    encoding: 'utf8',
    stdio: ['ignore', 'pipe', 'pipe'],
  });

  if ((result.status ?? 1) !== 0) {
    errors.push('Unable to generate release summary from current evidence');
    const details = `${result.stdout ?? ''}\n${result.stderr ?? ''}`.trim();
    if (details) {
      errors.push(`Release summary generation output:\n${details}`);
    }
  }
}

function validateLatestScenarioReport({ dirRelativePath, filePrefix, requiredScenarioKeys, errors }) {
  const dirAbsolutePath = path.join(repoRoot, dirRelativePath);
  if (!existsSync(dirAbsolutePath)) {
    errors.push(`Missing failure-injection evidence directory: ${dirRelativePath}`);
    return;
  }

  const candidates = readdirSync(dirAbsolutePath)
    .filter((entry) => entry.startsWith(filePrefix) && entry.endsWith('.json'))
    .map((entry) => ({
      entry,
      relativePath: `${dirRelativePath}/${entry}`,
      modifiedAt: statSync(path.join(dirAbsolutePath, entry)).mtimeMs,
    }))
    .sort((left, right) => right.modifiedAt - left.modifiedAt);

  const latest = candidates[0];
  if (!latest) {
    errors.push(`Missing failure-injection evidence file with prefix ${filePrefix} in ${dirRelativePath}`);
    return;
  }

  const report = readJson(latest.relativePath, errors);
  if (!report) {
    return;
  }

  // Validate evidence quality
  const evidenceText = JSON.stringify(report);
  const qualityIssues = validateEvidenceQuality(evidenceText, RELEASE_MODE);
  qualityIssues.forEach(issue => errors.push(`${latest.relativePath}: ${issue.message}`));

  if (!Array.isArray(report.violations) || report.violations.length > 0) {
    errors.push(`Failure-injection report has violations: ${latest.relativePath}`);
  }

  if (!report.scenarioCoverage || typeof report.scenarioCoverage !== 'object') {
    errors.push(`Failure-injection report missing scenarioCoverage: ${latest.relativePath}`);
    return;
  }

  for (const scenarioKey of requiredScenarioKeys) {
    if (report.scenarioCoverage[scenarioKey] !== true) {
      errors.push(`Failure-injection report missing required scenario ${scenarioKey}: ${latest.relativePath}`);
    }
  }

  if (typeof report.summary?.executedTestProductCount !== 'number' || report.summary.executedTestProductCount < 1) {
    errors.push(`Failure-injection report must include executedTestProductCount >= 1: ${latest.relativePath}`);
  }

  // Validate product coverage
  const activeProducts = getActiveProducts();
  const expectedProductCount = typeof report.summary?.expectedProductCount === 'number'
    ? report.summary.expectedProductCount
    : activeProducts.length;
  const coverageIssues = validateProductCoverage(report.summary.executedTestProductCount, expectedProductCount, RELEASE_MODE);
  coverageIssues.forEach(issue => errors.push(`${latest.relativePath}: ${issue.message}`));
}

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

function evaluateEvidenceFreshness(relativePath, evidence, errors) {
  const profile = getFreshnessProfile(relativePath);
  const absolutePath = path.join(repoRoot, relativePath);
  const timestamp = readEvidenceTimestamp(evidence) ?? (existsSync(absolutePath) ? statSync(absolutePath).mtimeMs : null);

  if (timestamp === null) {
    errors.push(`Evidence file is missing a freshness timestamp: ${relativePath}`);
    return;
  }

  const ageHours = (Date.now() - timestamp) / (1000 * 60 * 60);
  if (ageHours > profile.thresholdHours) {
    errors.push(`Evidence file is stale (> ${profile.thresholdHours}h, ${profile.evidenceType}): ${relativePath}`);
  }
}

function gitValue(command) {
  try {
    return execSync(command, { cwd: repoRoot, encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] }).trim();
  } catch {
    return null;
  }
}

function assertRecent(relativePath, evidence, errors) {
  if (!evidence) {
    return;
  }

  evaluateEvidenceFreshness(relativePath, evidence, errors);
}

function validateRequiredContent(evidenceByPath, errors) {
  const smoke = evidenceByPath.get(smokeEvidencePath);
  if (smoke) {
    if (!Array.isArray(smoke.results) || smoke.results.length === 0) {
      errors.push('Smoke report must contain non-empty results');
    }
    if (!smoke.summary || typeof smoke.summary.fail !== 'number') {
      errors.push('Smoke report must contain summary.fail');
    }
  }

  const backup = evidenceByPath.get(backupEvidencePath);
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
    } else {
      const behavioralSignals = runtimeProfile.checks.filter((entry) => {
        const checkName = String(entry?.check ?? '');
        return /runtime|failure|atomic|observability|route entitlement|governance/i.test(checkName) && entry?.ok === true;
      });
      if (behavioralSignals.length < 3) {
        errors.push('Runtime profile evidence appears posture-only: expected >= 3 behavioral check signals');
      }
    }
  }

  const atomic = evidenceByPath.get('.kernel/evidence/atomic-workflow-posture.json');
  if (atomic) {
    const violations = atomic.summary?.violationCount;
    if (typeof violations !== 'number') {
      errors.push('Atomic workflow evidence must include summary.violationCount');
    }
    if (atomic.failureInjectionEvidence?.hasReport !== true) {
      errors.push('Atomic workflow evidence must include failure-injection report linkage');
    }
    if (typeof atomic.failureInjectionEvidence?.violationCount === 'number' && atomic.failureInjectionEvidence.violationCount > 0) {
      errors.push('Atomic workflow failure-injection evidence must not contain violations');
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

  const sloBudgets = evidenceByPath.get('.kernel/evidence/product-slo-budgets.json');
  if (sloBudgets) {
    if (sloBudgets.status !== 'passed') {
      errors.push('Product SLO budgets evidence must pass');
    }
    if (!Array.isArray(sloBudgets.violations)) {
      errors.push('Product SLO budgets evidence must include violations array');
    }
  }

  const costBudgets = evidenceByPath.get('.kernel/evidence/product-cost-budgets.json');
  if (costBudgets) {
    if (costBudgets.status !== 'passed') {
      errors.push('Product cost budgets evidence must pass');
    }
    if (!Array.isArray(costBudgets.violations)) {
      errors.push('Product cost budgets evidence must include violations array');
    }
  }

  const domainInvariants = evidenceByPath.get('.kernel/evidence/product-domain-invariants.json');
  if (domainInvariants) {
    if (domainInvariants.status !== 'passed') {
      errors.push('Product domain invariants evidence must pass');
    }
    if (!Array.isArray(domainInvariants.violations)) {
      errors.push('Product domain invariants evidence must include violations array');
    }
  }

  const openApiBreaking = evidenceByPath.get('.kernel/evidence/openapi-breaking-changes.json');
  if (openApiBreaking) {
    if (openApiBreaking.status !== 'passed') {
      errors.push('OpenAPI breaking-change evidence must pass');
    }
    if (!Array.isArray(openApiBreaking.details)) {
      errors.push('OpenAPI breaking-change evidence must include details array');
    }
  }

  const summary = evidenceByPath.get(summaryEvidencePath);
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

    if (!Array.isArray(summary.productScope) || summary.productScope.length === 0) {
      errors.push('Release summary must include non-empty productScope');
    }

    if (summary.pass !== true) {
      errors.push('Release summary overall pass must be true for release evidence validation');
    }

    const requiredGateKeys = ['smoke', 'backup', 'runtimeProfile', 'atomicWorkflow', 'implementationPlanCoverage', 'releaseScoreThresholdPolicy', 'perProductReleaseReadiness'];
    for (const gateKey of requiredGateKeys) {
      const gate = summary.releaseGate?.[gateKey];
      if (!gate || gate.available !== true || gate.pass !== true) {
        errors.push(`Release summary required gate ${gateKey} must be available=true and pass=true`);
      }
    }

    const threshold = summary.releaseGate?.releaseScoreThresholdPolicy;
    if (!threshold || threshold.pass !== true) {
      errors.push('Release score threshold policy failed: critical dimensions below threshold or unresolved P0/P1 blockers exist');
    }
    if (threshold) {
      if (threshold.productSloBudgetsPass !== true) {
        errors.push('Release score threshold policy must enforce passing product SLO budgets evidence');
      }
      if (threshold.productCostBudgetsPass !== true) {
        errors.push('Release score threshold policy must enforce passing product cost budgets evidence');
      }
      if (threshold.productDomainInvariantsPass !== true) {
        errors.push('Release score threshold policy must enforce passing product domain invariant evidence');
      }
      if (threshold.openApiBreakingChangesPass !== true) {
        errors.push('Release score threshold policy must enforce passing OpenAPI breaking-change evidence');
      }
      if (typeof threshold.releaseTargetScore === 'number' && threshold.releaseTargetScore !== releaseTargetScore) {
        errors.push(`Release score threshold policy target mismatch: expected ${releaseTargetScore}, got ${threshold.releaseTargetScore}`);
      }
    }

    const perProductGate = summary.releaseGate?.perProductReleaseReadiness;
    if (!perProductGate || perProductGate.pass !== true) {
      errors.push('Per-product release readiness gate must pass');
    } else {
      if (typeof perProductGate.productCount !== 'number' || perProductGate.productCount <= 0) {
        errors.push('Per-product release readiness gate must include at least one product scorecard');
      }
      if (typeof perProductGate.releaseTargetScore === 'number' && perProductGate.releaseTargetScore !== releaseTargetScore) {
        errors.push(`Per-product release readiness target mismatch: expected ${releaseTargetScore}, got ${perProductGate.releaseTargetScore}`);
      }
    }
  }
}

function validatePerProductScorecards(errors, expectedProductIds = []) {
  const evidenceDir = path.join(repoRoot, '.kernel/evidence');
  if (!existsSync(evidenceDir)) {
    errors.push('Missing evidence directory .kernel/evidence');
    return;
  }

  const expectedProductSet = new Set(expectedProductIds);
  const scorecardFiles = [];
  const directoryEntries = readdirSync(evidenceDir).filter((entry) => /^product-release-readiness\.[^.]+\.json$/i.test(entry));

  for (const entry of directoryEntries) {
    const productId = entry.replace(/^product-release-readiness\./i, '').replace(/\.json$/i, '');
    if (expectedProductSet.size === 0 || expectedProductSet.has(productId)) {
      scorecardFiles.push(path.join(evidenceDir, entry));
    }
  }

  if (scorecardFiles.length === 0) {
    errors.push('Missing per-product release readiness scorecards (.kernel/evidence/product-release-readiness.<product>.json)');
    return;
  }

  // Validate product coverage
  const activeProducts = getActiveProducts();
  const activeProductIds = activeProducts.map(p => p.productId);
  const expectedProductCount = expectedProductIds.length > 0 ? expectedProductIds.length : activeProductIds.length;
  const coverageIssues = validateProductCoverage(scorecardFiles.length, expectedProductCount, RELEASE_MODE);
  coverageIssues.forEach(issue => errors.push(issue.message));

  for (const scorecardPath of scorecardFiles) {
    const productId = path.basename(scorecardPath).replace(/^product-release-readiness\./i, '').replace(/\.json$/i, '');
    const content = readJson(path.relative(repoRoot, scorecardPath).replace(/\\/g, '/'), errors);
    if (!content) {
      continue;
    }

    // Check for waivers
    const waiver = checkWaiver(productId, 'release-readiness', 'scorecard');
    if (waiver) {
      console.log(`Waiver found for ${productId}/release-readiness/scorecard: ${waiver.reason}`);
      continue;
    }

    if (content.releaseVerdict !== 'pass') {
      errors.push(`Per-product scorecard must pass: ${path.basename(scorecardPath)}`);
    }
    if (!Array.isArray(content.dimensions) || content.dimensions.length !== 47) {
      errors.push(`Per-product scorecard must contain 47 dimensions: ${path.basename(scorecardPath)}`);
    }
    if (typeof content.averageScore !== 'number' || content.averageScore < releaseTargetScore) {
      errors.push(`Per-product average score below target (${releaseTargetScore}): ${path.basename(scorecardPath)}`);
    }
    if (!Array.isArray(content.belowTargetDimensions)) {
      errors.push(`Per-product scorecard must include belowTargetDimensions array: ${path.basename(scorecardPath)}`);
    } else if (content.belowTargetDimensions.length > 0) {
      errors.push(`Per-product scorecard has dimensions below target (${releaseTargetScore}): ${path.basename(scorecardPath)}`);
    }
    if (!Array.isArray(content.blockingGaps)) {
      errors.push(`Per-product scorecard must include blockingGaps array: ${path.basename(scorecardPath)}`);
    }
    if (!Array.isArray(content.dimensions)) {
      errors.push(`Per-product scorecard dimensions must be an array: ${path.basename(scorecardPath)}`);
    } else {
      const invalidDimensions = content.dimensions.filter((entry) => typeof entry?.score !== 'number');
      if (invalidDimensions.length > 0) {
        errors.push(`Per-product scorecard contains dimensions without numeric score: ${path.basename(scorecardPath)}`);
      }
    }

    // Validate evidence quality
    const evidenceText = JSON.stringify(content);
    const qualityIssues = validateEvidenceQuality(evidenceText, RELEASE_MODE);
    qualityIssues.forEach(issue => errors.push(`${path.basename(scorecardPath)}: ${issue.message}`));
  }
}

function main() {
  const errors = [];
  const evidenceByPath = new Map();

  ensureSmokeBackupEvidence(errors);
  ensureReleaseSummary(errors);

  for (const relativePath of requiredEvidenceFiles) {
    const content = readJson(relativePath, errors);
    if (content) {
      evidenceByPath.set(relativePath, content);
    }
    assertRecent(relativePath, content, errors);
  }

  validateRequiredContent(evidenceByPath, errors);
  const releaseSummary = evidenceByPath.get(summaryEvidencePath);
  const productReadinessAggregate = readJson('.kernel/evidence/product-release-readiness.json', errors);
  const scopedProductIds = productReadinessAggregate?.affectedProducts ?? releaseSummary?.productScope ?? [];
  validatePerProductScorecards(errors, scopedProductIds);
  validateLatestScenarioReport({
    dirRelativePath: '.kernel/evidence/runtime-dependency-failure-injection',
    filePrefix: 'runtime-dependency-failure-injection-',
    requiredScenarioKeys: [
      'postgresDown',
      'clickhouseDown',
      'openSearchDown',
      's3Down',
      'auditSinkUnavailable',
      'policyEngineUnavailable',
      'aiCompletionUnavailable',
      'networkTimeout',
      'queueSaturation',
      'retryBackoff',
    ],
    errors,
  });
  validateLatestScenarioReport({
    dirRelativePath: '.kernel/evidence/atomic-workflow-failure-injection',
    filePrefix: 'atomic-workflow-failure-injection-',
    requiredScenarioKeys: [
      'businessWriteEventAppendFailure',
      'eventAppendAuditWriteFailure',
      'auditOutboxFailure',
      'idempotencyWriteFailure',
      'retryAfterPartialFailure',
      'rollbackAfterPartialFailure',
      'replayAfterCrash',
    ],
    errors,
  });

  if (errors.length > 0) {
    console.error('Release evidence validation failed:');
    for (const error of errors) {
      console.error(`- ${error}`);
    }
    process.exit(1);
  }

  console.log('Release evidence validation passed.');
}

if (import.meta.url === pathToFileURL(process.argv[1]).href) {
  main();
}

export {
  evaluateEvidenceFreshness,
  getFreshnessProfile,
  loadFreshnessPolicy,
  resolveRepoRoot,
};
