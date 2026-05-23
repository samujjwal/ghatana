#!/usr/bin/env node

import { spawnSync } from 'node:child_process';
import { existsSync, mkdirSync, readFileSync, renameSync, unlinkSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

import { loadCanonicalRegistry, resolveAffectedProducts } from './resolve-affected-products.mjs';

const repoRoot = process.cwd();
const evidenceDir = path.join(repoRoot, '.kernel/evidence');
const evidencePath = path.join(evidenceDir, 'product-release-readiness.json');
const implementationPlanEvidencePath = path.join(evidenceDir, 'kernel-implementation-plan-progress.json');
const wave2ScorecardPath = path.join(evidenceDir, 'wave2-product-quality-scorecard.json');
const defaultReleaseTargetScore = Number(process.env.RELEASE_TARGET_SCORE ?? '4');

function sleepMs(durationMs) {
  const signal = new Int32Array(new SharedArrayBuffer(4));
  Atomics.wait(signal, 0, 0, durationMs);
}

function writeJsonWithRetry(targetPath, payload, maxAttempts = 8) {
  let lastError = null;
  const tempPath = `${targetPath}.tmp`;

  for (let attempt = 1; attempt <= maxAttempts; attempt += 1) {
    try {
      writeFileSync(tempPath, `${JSON.stringify(payload, null, 2)}\n`, 'utf8');
      renameSync(tempPath, targetPath);
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

const releaseScorecardDimensionNames = [
  'Vision alignment',
  'Product coherence',
  'Feature completeness',
  'End-to-end workflow completeness',
  'Runtime correctness',
  'Domain correctness',
  'Data model correctness',
  'Contract correctness',
  'Route/API correctness',
  'UI/API/runtime coherence',
  'Runtime Truth maturity',
  'Security',
  'Privacy',
  'Tenant isolation',
  'Authorization/RBAC/ABAC/scope',
  'Governance/policy/compliance',
  'Audit durability/evidence quality',
  'Event correctness',
  'Action Plane / automation correctness',
  'Implicit AI/ML maturity',
  'HITL/override control',
  'Observability',
  'Reliability/resilience',
  'Error/degraded mode',
  'Idempotency/retry/replay/rollback',
  'Performance',
  'Scalability',
  'Extensibility/plugin model',
  'Shared-library reuse',
  'Dependency hygiene',
  'Architecture boundaries',
  'Simplicity/maintainability',
  'UI/UX simplicity/consistency',
  'Accessibility',
  'i18n/l10n readiness',
  'Testing depth',
  'Test quality/no-theater',
  'CI gate strength',
  'Release readiness',
  'Deployment/ops readiness',
  'Backup/restore/DR',
  'Config/secrets management',
  'Documentation truthfulness',
  'Migration/deprecation hygiene',
  'Cost/operational efficiency',
  'Overall production readiness',
  'Overall world-class maturity',
];

const artifactAuthoringReadinessScripts = [
  'pnpm:check:kernel-authoring-pipeline',
  'pnpm:check:artifact-roundtrip',
  'pnpm:check:generated-artifact-validation-pipeline',
  'pnpm:check:studio-production-profile:strict',
  'pnpm:check:studio-source-acquisition-worker',
  'pnpm:check:studio-workflow-persistence-contracts',
];

function runCheck(checkRef) {
  if (checkRef.startsWith('pnpm:')) {
    const scriptName = checkRef.slice('pnpm:'.length);
    if (process.platform === 'win32') {
      return spawnSync('cmd.exe', ['/d', '/c', 'pnpm', scriptName], {
        cwd: repoRoot,
        stdio: 'inherit',
        env: process.env,
      });
    }
    return spawnSync('pnpm', [scriptName], {
      cwd: repoRoot,
      stdio: 'inherit',
      env: process.env,
    });
  }

  return spawnSync(process.execPath, [checkRef], {
    cwd: repoRoot,
    stdio: 'inherit',
    env: process.env,
  });
}

function readJsonIfExists(filePath) {
  if (!existsSync(filePath)) {
    return null;
  }

  return JSON.parse(readFileSync(filePath, 'utf8'));
}

function normalizeDimensionName(dimensionName) {
  return String(dimensionName)
    .toLowerCase()
    .replace(/\s+/g, ' ')
    .replace(/[()]/g, '')
    .replace(/\s*\/\s*/g, '/')
    .trim();
}

function loadDimensionScoreMap() {
  const evidence = readJsonIfExists(implementationPlanEvidencePath);
  const map = new Map();

  for (const entry of evidence?.dimensionResults ?? []) {
    const key = normalizeDimensionName(entry.name);
    if (typeof entry.maturityScore === 'number') {
      map.set(key, Number(entry.maturityScore));
    }
  }

  return map;
}

function loadWave2ScoreRows() {
  const scorecard = readJsonIfExists(wave2ScorecardPath);
  const rows = new Map();

  for (const row of scorecard?.scoreRows ?? []) {
    if (typeof row.productId === 'string') {
      rows.set(row.productId, row);
    }
  }

  return rows;
}

function scoreBoostFromWave2(dimensionName, wave2Row) {
  if (!wave2Row?.area) {
    return 0;
  }

  const normalized = normalizeDimensionName(dimensionName);

  if (normalized === 'accessibility') {
    return wave2Row.area.a11y === true ? 0.5 : -1;
  }
  if (normalized === 'internationalization and localization') {
    return wave2Row.area.i18n === true ? 0.5 : -1;
  }
  if (normalized === 'implicit ai/ml maturity') {
    return wave2Row.area.aiGovernance === true ? 0.5 : -1;
  }
  if (normalized === 'performance') {
    return wave2Row.area.performanceSlo === true ? 0.5 : -0.5;
  }
  if (normalized === 'runtime truth maturity') {
    return wave2Row.area.runtimeTruth === true ? 0.5 : -0.5;
  }

  return 0;
}

const journeyMatrix = [
  {
    journey: 'vision-and-coherence',
    areas: [
      {
        area: 'product-truth-and-ownership',
        scripts: [
          './scripts/check-product-shape-capability-matrix.mjs',
          './scripts/check-product-registry-drift.mjs',
          'pnpm:check:product-registry',
          './scripts/check-platform-product-boundaries.mjs',
          './scripts/check-cross-product-interaction-boundaries.mjs',
        ],
      },
    ],
  },
  {
    journey: 'workflow-and-runtime-proof',
    areas: [
      {
        area: 'e2e-runtime-and-failure-proof',
        scripts: [
          './scripts/check-product-ui-contracts.mjs',
          './scripts/check-audited-e2e-workflow.mjs',
          'pnpm:check:cross-product-interaction-flows',
          './scripts/check-interaction-runtime-truth.mjs',
          './scripts/check-data-cloud-release-runtime-profile.mjs',
          './scripts/check-runtime-dependency-failure-injection.mjs',
          './scripts/check-atomic-workflow-failure-injection.mjs',
          './scripts/check-runtime-failure-injection.mjs',
          './scripts/check-atomic-workflow-proof.mjs',
        ],
      },
    ],
  },
  {
    journey: 'security-privacy-governance',
    areas: [
      {
        area: 'secure-routes-observability-and-doc-truth',
        scripts: [
          './scripts/check-secret-default-credentials.mjs',
          './scripts/check-route-entitlement-contracts.mjs',
          './scripts/check-observability-conformance.mjs',
          './scripts/check-doc-claims-evidence.mjs',
          './scripts/check-current-state-claims.mjs',
          './scripts/check-doc-truth.mjs',
        ],
      },
    ],
  },
  {
    journey: 'quality-experience-and-release',
    areas: [
      {
        area: 'a11y-i18n-ai-performance',
        scripts: [
          './scripts/check-data-cloud-ui-a11y.mjs',
          './scripts/check-product-a11y-route-matrix.mjs',
          './scripts/check-i18n-conformance.mjs',
          './scripts/check-ai-governance-conformance.mjs',
          './scripts/check-audited-performance-workflows.mjs',
          './scripts/check-product-slo-budgets.mjs',
          './scripts/check-product-cost-budgets.mjs',
          './scripts/check-product-domain-invariants.mjs',
        ],
      },
      {
        area: 'strict-release-and-coverage',
        scripts: [
          './scripts/check-openapi-release-quality.mjs',
          './scripts/check-openapi-breaking-changes.mjs',
          './scripts/generate-wave2-product-quality-scorecard.mjs',
          './scripts/check-product-ci-matrices.mjs',
          './scripts/check-affected-product-strict-release-profile.mjs',
          './scripts/check-kernel-implementation-task-matrix.mjs',
          './scripts/check-kernel-implementation-plan-coverage.mjs',
        ],
      },
      {
        area: 'artifact-authoring-platform-readiness',
        scripts: artifactAuthoringReadinessScripts,
      },
    ],
  },
];

const releaseAreas = [
  {
    area: 'strict-release-gate-evidence',
    scripts: [
      './scripts/check-data-cloud-release-runtime-profile.mjs',
      './scripts/check-affected-product-strict-release-profile.mjs',
      './scripts/check-kernel-implementation-plan-coverage.mjs',
    ],
  },
  {
    area: 'quality-and-experience-gates',
    scripts: [
      './scripts/check-data-cloud-ui-a11y.mjs',
      './scripts/check-product-a11y-route-matrix.mjs',
      './scripts/check-i18n-conformance.mjs',
      './scripts/check-ai-governance-conformance.mjs',
      './scripts/check-audited-performance-workflows.mjs',
      './scripts/check-product-slo-budgets.mjs',
      './scripts/check-product-cost-budgets.mjs',
      './scripts/check-product-domain-invariants.mjs',
      './scripts/check-openapi-release-quality.mjs',
      './scripts/check-openapi-breaking-changes.mjs',
    ],
  },
  {
    area: 'security-and-doc-truth-gates',
    scripts: [
      './scripts/check-secret-default-credentials.mjs',
      './scripts/check-route-entitlement-contracts.mjs',
      './scripts/check-doc-claims-evidence.mjs',
      './scripts/check-current-state-claims.mjs',
      './scripts/check-doc-truth.mjs',
    ],
  },
  {
    area: 'artifact-authoring-release-gates',
    scripts: artifactAuthoringReadinessScripts,
  },
];

const executionOrder = [];
for (const journey of journeyMatrix) {
  for (const area of journey.areas) {
    for (const scriptPath of area.scripts) {
      if (!executionOrder.includes(scriptPath)) {
        executionOrder.push(scriptPath);
      }
    }
  }
}

for (const releaseArea of releaseAreas) {
  for (const scriptPath of releaseArea.scripts) {
    if (!executionOrder.includes(scriptPath)) {
      executionOrder.push(scriptPath);
    }
  }
}

function releaseProfileIds() {
  const profilesPath = path.join(repoRoot, 'config/product-release-profiles.json');
  if (!existsSync(profilesPath)) {
    return [];
  }
  const profiles = JSON.parse(readFileSync(profilesPath, 'utf8'));
  return Object.keys(profiles.profiles ?? profiles);
}

function parseArg(flag) {
  const index = process.argv.indexOf(flag);
  if (index >= 0 && process.argv[index + 1]) {
    return process.argv[index + 1];
  }
  return undefined;
}

function discoverAffectedProducts() {
  const providedProducts = parseArg('--products') || process.env.AFFECTED_PRODUCTS || '';
  if (providedProducts.trim().length > 0) {
    return providedProducts
      .split(',')
      .map((entry) => entry.trim())
      .filter(Boolean)
      .sort();
  }

  const registry = loadCanonicalRegistry(repoRoot);
  const result = resolveAffectedProducts(['config/canonical-product-registry.json'], registry, {
    businessProductsOnly: true,
  });
  return result.affectedProducts;
}

function buildProductScorecard(productId, runs, releaseProfiles, registry, options = {}) {
  const { dimensionScoreMap, wave2Rows, releaseTargetScore } = options;
  const runPassRatio = Number((runs.filter((run) => run.status === 0).length / Math.max(1, runs.length)).toFixed(2));
  const wave2Row = wave2Rows.get(productId);
  const dimensions = releaseScorecardDimensionNames.map((dimensionName) => {
    const normalized = normalizeDimensionName(dimensionName);
    const baseline = dimensionScoreMap.get(normalized) ?? Number((Math.max(0, Math.min(5, 2 + (runPassRatio * 3)))).toFixed(2));
    const wave2Delta = scoreBoostFromWave2(dimensionName, wave2Row);
    const score = Number(Math.max(0, Math.min(5, baseline + wave2Delta)).toFixed(2));
    return {
      dimensionName,
      score,
      sources: {
        baseline: Number(baseline.toFixed(2)),
        wave2Delta,
      },
    };
  });

  const blockingGaps = runs
    .filter((run) => run.status !== 0)
    .map((run) => ({
      severity: 'P0',
      gate: run.script,
      reason: 'script-failure',
    }));

  if (wave2Row?.area?.i18n === false) {
    blockingGaps.push({
      severity: 'P1',
      gate: 'check:i18n-conformance',
      reason: 'wave2-i18n-gap',
    });
  }

  const averageScore = Number((dimensions.reduce((sum, dimension) => sum + dimension.score, 0) / dimensions.length).toFixed(2));
  const belowTargetDimensions = dimensions
    .filter((dimension) => dimension.score < releaseTargetScore)
    .map((dimension) => ({
      dimensionName: dimension.dimensionName,
      score: dimension.score,
    }));

  if (belowTargetDimensions.length > 0) {
    blockingGaps.push({
      severity: 'P1',
      gate: 'score-threshold',
      reason: `dimensions-below-${releaseTargetScore}`,
      count: belowTargetDimensions.length,
    });
  }

  return {
    generatedAt: new Date().toISOString(),
    productId,
    releaseVerdict: blockingGaps.length === 0 ? 'pass' : 'fail',
    releaseProfiles,
    productMetadata: registry[productId] ?? null,
    dimensions,
    averageScore,
    releaseTargetScore,
    belowTargetDimensions,
    blockingGaps,
    skippedGates: runs.filter((run) => run.skipped === true).map((run) => run.script),
    evidencePaths: [
      '.kernel/evidence/product-release-readiness.json',
      '.kernel/evidence/kernel-implementation-plan-progress.json',
      '.kernel/evidence/wave2-product-quality-scorecard.json',
    ],
    artifactAuthoringGateScripts: artifactAuthoringReadinessScripts,
  };
}

function writePerProductScorecards(affectedProducts, runs, releaseProfiles) {
  const registry = loadCanonicalRegistry(repoRoot);
  const dimensionScoreMap = loadDimensionScoreMap();
  const wave2Rows = loadWave2ScoreRows();
  const paths = [];

  for (const productId of affectedProducts) {
    const scorecard = buildProductScorecard(productId, runs, releaseProfiles, registry, {
      dimensionScoreMap,
      wave2Rows,
      releaseTargetScore: defaultReleaseTargetScore,
    });
    const productEvidencePath = path.join(evidenceDir, `product-release-readiness.${productId}.json`);
    writeJsonWithRetry(productEvidencePath, scorecard);
    paths.push(path.relative(repoRoot, productEvidencePath));
  }

  return paths;
}

export function runProductReleaseReadinessCheck({ writeEvidence = true } = {}) {
  if (!writeEvidence) {
    return {
      status: 'passed',
      journeys: journeyMatrix,
      releaseAreas,
      artifactAuthoringGateScripts: artifactAuthoringReadinessScripts,
      releaseProfiles: releaseProfileIds(),
    };
  }

  return runProductReleaseReadinessCli();
}

function runProductReleaseReadinessCli() {
  const runs = [];
  const runByScript = new Map();
  const affectedProducts = discoverAffectedProducts();

  function failWithEvidence(exitCode, failedScript, reason) {
    const releaseProfiles = releaseProfileIds();
    mkdirSync(evidenceDir, { recursive: true });
    writeJsonWithRetry(evidencePath, {
      generatedAt: new Date().toISOString(),
      pass: false,
      reason,
      failedScript,
      runs,
      journeyMatrix,
      releaseAreas,
      artifactAuthoringGateScripts: artifactAuthoringReadinessScripts,
      releaseProfiles,
      affectedProducts,
    });
    writePerProductScorecards(affectedProducts, runs, releaseProfiles);
    if (failedScript) {
      console.error(`Product release readiness failed at ${failedScript}`);
    }
    process.exit(exitCode);
  }

  for (const journey of journeyMatrix) {
    if (!journey.areas || journey.areas.length === 0) {
      failWithEvidence(1, null, `Journey ${journey.journey} must define at least one area`);
    }
    for (const area of journey.areas) {
      if (!area.scripts || area.scripts.length === 0) {
        failWithEvidence(1, null, `Area ${journey.journey}/${area.area} must define at least one script`);
      }
    }
  }

  for (const scriptPath of executionOrder) {
    const startedAt = Date.now();
    const result = runCheck(scriptPath);

    const status = result.status ?? 1;
    const durationMs = Date.now() - startedAt;
    const runRecord = { script: scriptPath, status, durationMs };
    runByScript.set(scriptPath, runRecord);
    runs.push(runRecord);

    if (status !== 0) {
      failWithEvidence(status, scriptPath, 'script-failure');
    }
  }

  const journeyResults = journeyMatrix.map((journey) => {
    const areaResults = journey.areas.map((area) => {
      const areaRuns = area.scripts
        .map((scriptPath) => runByScript.get(scriptPath))
        .filter(Boolean);
      return {
        area: area.area,
        scripts: area.scripts,
        pass: areaRuns.every((run) => run.status === 0),
      };
    });

    return {
      journey: journey.journey,
      pass: areaResults.every((area) => area.pass),
      areas: areaResults,
    };
  });

  const releaseAreaResults = releaseAreas.map((releaseArea) => {
    const releaseRuns = releaseArea.scripts
      .map((scriptPath) => runByScript.get(scriptPath))
      .filter(Boolean);
    return {
      area: releaseArea.area,
      pass: releaseRuns.every((run) => run.status === 0),
      scripts: releaseArea.scripts,
    };
  });

  const evidence = {
    generatedAt: new Date().toISOString(),
    pass: true,
    journeyResults,
    releaseAreaResults,
    runs,
    artifactAuthoringGateScripts: artifactAuthoringReadinessScripts,
    releaseProfiles: releaseProfileIds(),
    affectedProducts,
  };

  mkdirSync(evidenceDir, { recursive: true });
  writeJsonWithRetry(evidencePath, evidence);
  const perProductEvidencePaths = writePerProductScorecards(affectedProducts, runs, evidence.releaseProfiles);

  console.log(`Product release readiness check passed. Evidence: ${path.relative(repoRoot, evidencePath)}`);
  return {
    status: 'passed',
    journeys: journeyMatrix,
    perProductEvidencePaths,
    ...evidence,
  };
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  runProductReleaseReadinessCli();
}
