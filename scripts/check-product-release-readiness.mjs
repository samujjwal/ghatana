#!/usr/bin/env node

import { spawnSync } from 'node:child_process';
import { existsSync, mkdirSync, readFileSync, renameSync, unlinkSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

import { loadCanonicalRegistry, resolveAffectedProducts } from './resolve-affected-products.mjs';

const repoRoot = process.cwd();
const evidenceDir = path.join(repoRoot, '.kernel/evidence');
const evidencePath = path.join(evidenceDir, 'product-release-readiness.json');
const affectedProductProfilePath = path.join(evidenceDir, 'affected-product-release-profile.json');
const implementationPlanEvidencePath = path.join(evidenceDir, 'kernel-implementation-plan-progress.json');
const wave2ScorecardPath = path.join(evidenceDir, 'wave2-product-quality-scorecard.json');
const defaultReleaseTargetScore = Number(process.env.RELEASE_TARGET_SCORE ?? '4');
const platformLifecycleBuildScript = 'pnpm:build:kernel-lifecycle-platform';
const targetEnvironment = process.env.RELEASE_ENVIRONMENT ?? 'staging';
const evidenceValidityHours = Number(process.env.RELEASE_EVIDENCE_VALIDITY_HOURS ?? '48');

function currentGitSha() {
  const result = spawnSync('git', ['rev-parse', 'HEAD'], {
    cwd: repoRoot,
    encoding: 'utf8',
    stdio: ['ignore', 'pipe', 'ignore'],
  });
  return result.status === 0 ? result.stdout.trim() : 'unknown';
}

function evidenceRunMetadata() {
  const commit = currentGitSha();
  const targetCommitSha = process.env.TARGET_COMMIT_SHA ?? process.env.AUDIT_TARGET_COMMIT ?? commit;

  return {
    generatedBy: 'scripts/check-product-release-readiness.mjs',
    command: 'pnpm check:product-release-readiness',
    source: 'scripts/check-product-release-readiness.mjs',
    commit,
    sourceCommitSha: commit,
    targetCommitSha,
    targetEnvironment,
  };
}

function evidenceLifecycleMetadata(isValid) {
  const now = new Date();
  const sourceCommitSha = currentGitSha();
  const targetCommitSha = process.env.TARGET_COMMIT_SHA ?? process.env.AUDIT_TARGET_COMMIT ?? sourceCommitSha;

  return {
    sourceCommitSha,
    targetCommitSha,
    targetEnvironment,
    validationStatus: isValid ? 'validated' : 'failed',
    reviewDueAt: new Date(now.getTime() + 24 * 60 * 60 * 1000).toISOString(),
    expiresAt: new Date(now.getTime() + evidenceValidityHours * 60 * 60 * 1000).toISOString(),
  };
}

function hasUnresolvedLifecycleBlockers(productMetadata) {
  const matrix = productMetadata?.lifecycleReadiness?.blockerGateAdapterMatrix;
  if (!matrix || typeof matrix !== 'object') {
    return false;
  }

  const blockers = Array.isArray(matrix.blockers) ? matrix.blockers : [];
  const unresolvedBlocker = blockers.some((blocker) => {
    const normalized = String(blocker).trim().toLowerCase();
    return normalized.startsWith('missing-') || normalized.includes('blocked') || normalized.includes('unvalidated') || normalized.includes('pending');
  });

  const adapters = Array.isArray(matrix.adapters) ? matrix.adapters : [];
  const unresolvedAdapter = adapters.some((adapter) => {
    const readiness = String(adapter?.readiness ?? '').trim().toLowerCase();
    return readiness.length > 0 && readiness !== 'ready' && readiness !== 'validated' && readiness !== 'pass';
  });

  return unresolvedBlocker || unresolvedAdapter;
}

function validateRegistryAndEvidenceConsistency(productId, productMetadata) {
  const gaps = [];
  const deploymentTargets = productMetadata?.deployment?.targets ?? [];
  const environments = productMetadata?.environments?.supported ?? [];
  const requiresStagingOrProd =
    deploymentTargets.includes('staging') ||
    deploymentTargets.includes('prod') ||
    environments.includes('staging') ||
    environments.includes('prod');

  const rollbackStatus = String(productMetadata?.rollbackReadiness?.status ?? '').toLowerCase();
  if (requiresStagingOrProd && rollbackStatus === 'ready-local') {
    gaps.push({
      severity: 'P0',
      gate: 'rollback-readiness',
      reason: 'ready-local-with-staging-prod-targets',
      message: `Product ${productId} cannot declare rollback status ready-local while staging/prod targets are enabled`,
    });
  }

  if (hasUnresolvedLifecycleBlockers(productMetadata)) {
    gaps.push({
      severity: 'P0',
      gate: 'lifecycle-readiness-blockers',
      reason: 'unresolved-blocker-matrix',
      message: `Product ${productId} has unresolved blocker matrix entries while lifecycle readiness is enabled`,
    });
  }

  return gaps;
}

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

const productScopedScriptRefs = new Set([
  './scripts/check-affected-product-strict-release-profile.mjs',
  './scripts/check-product-release-evidence-packs.mjs',
  './scripts/check-product-a11y-route-matrix.mjs',
  './scripts/check-product-slo-budgets.mjs',
  './scripts/check-product-cost-budgets.mjs',
  './scripts/check-product-domain-invariants.mjs',
  'pnpm:check:affected-product-strict-release-profile',
]);

function runCheck(checkRef, options = {}) {
  const products = options.products ?? [];
  const productArgs = productScopedScriptRefs.has(checkRef) && products.length > 0
    ? ['--products', products.join(',')]
    : [];

  if (checkRef.startsWith('pnpm:')) {
    const scriptName = checkRef.slice('pnpm:'.length);
    const pnpmArgs = productArgs.length > 0 ? [scriptName, '--', ...productArgs] : [scriptName];
    if (process.platform === 'win32') {
      return spawnSync('cmd.exe', ['/d', '/c', 'pnpm', ...pnpmArgs], {
        cwd: repoRoot,
        stdio: 'inherit',
        env: process.env,
      });
    }
    return spawnSync('pnpm', pnpmArgs, {
      cwd: repoRoot,
      stdio: 'inherit',
      env: process.env,
    });
  }

  return spawnSync(process.execPath, [checkRef, ...productArgs], {
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

export function filterReleaseEligibleProducts(productIds, registry) {
  return productIds.filter((productId) => {
    const metadata = registry[productId];
    if (!metadata || metadata.kind !== 'business-product') {
      return false;
    }

    if (metadata.metadata?.status !== 'active') {
      return false;
    }

    return metadata.lifecycleExecutionAllowed === true || metadata.lifecycleStatus === 'enabled';
  });
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
          './scripts/check-production-stubs.mjs',
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
          './scripts/check-release-profile-local-targets.mjs',
          './scripts/check-openapi-release-quality.mjs',
          './scripts/check-openapi-breaking-changes.mjs',
          './scripts/check-product-release-evidence-packs.mjs',
          './scripts/generate-wave2-product-quality-scorecard.mjs',
          './scripts/check-product-ci-matrices.mjs',
          './scripts/check-affected-product-strict-release-profile.mjs',
          './scripts/check-product-family-asset-promotion-policy.mjs',
          './scripts/check-yappc-product-family-control-plane.mjs',
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
      './scripts/check-product-family-asset-registry.mjs',
      './scripts/check-product-family-asset-promotion-policy.mjs',
      './scripts/check-product-release-evidence-packs.mjs',
      './scripts/check-release-profile-local-targets.mjs',
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
      './scripts/check-production-stubs.mjs',
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

const scopedCheckGroups = {
  globalPrerequisiteChecks: [
    'pnpm:check:aggregate-gate-integrity',
    './scripts/check-release-profile-local-targets.mjs',
    './scripts/check-product-family-asset-registry.mjs',
    './scripts/check-affected-product-strict-release-profile.mjs',
  ],
  platformChecks: [
    './scripts/check-kernel-platform-lifecycle.mjs',
    './scripts/check-kernel-lifecycle-truth.mjs',
    './scripts/check-toolchain-adapter-contracts.mjs',
    './scripts/check-product-artifact-contracts.mjs',
    './scripts/check-product-deployment-contracts.mjs',
    './scripts/check-product-environment-contracts.mjs',
  ],
  productChecks: [
    './scripts/check-product-shape-capability-matrix.mjs',
    './scripts/check-product-registry-drift.mjs',
    './scripts/check-product-release-evidence-packs.mjs',
    './scripts/check-product-ci-matrices.mjs',
    './scripts/check-product-slo-budgets.mjs',
    './scripts/check-product-cost-budgets.mjs',
    './scripts/check-product-domain-invariants.mjs',
    './scripts/check-product-a11y-route-matrix.mjs',
    './scripts/check-openapi-release-quality.mjs',
    './scripts/check-openapi-breaking-changes.mjs',
  ],
  dataCloudChecks: [
    'pnpm:check:data-cloud-release-gate',
    './scripts/check-data-cloud-release-runtime-profile.mjs',
    './scripts/check-data-cloud-ui-a11y.mjs',
    './scripts/check-data-cloud-platform-providers.mjs',
    './scripts/check-data-cloud-platform-provider-readiness.mjs',
    './scripts/check-action-plane-route-lifecycle.mjs',
    './scripts/check-data-cloud-maturity-proof.mjs',
  ],
  dmosChecks: [
    './scripts/check-digital-marketing-lifecycle-pilot.mjs',
    './scripts/check-dmos-boundary-workflow-coverage.mjs',
    './scripts/check-dmos-no-fake-data.mjs',
    './scripts/check-dmos-docs-matrix.mjs',
    './scripts/check-dmos-operations-docs.mjs',
    './scripts/check-dmos-duplicate-runtimes.mjs',
    './scripts/check-dmos-production-wiring.mjs',
    './scripts/check-dmos-runtime-module.mjs',
    './scripts/check-dmos-persistence.mjs',
    './scripts/check-dmos-kernel-context.mjs',
    './scripts/check-dmos-data-cloud-rls.mjs',
    './scripts/check-dmos-ai-action-log.mjs',
    './scripts/check-dmos-google-ads-connector.mjs',
  ],
  phrChecks: [
    './scripts/check-phr-lifecycle-readiness.mjs',
    './scripts/check-phr-lifecycle-pilot.mjs',
    './scripts/check-phr-kernel-context.mjs',
    './scripts/check-phr-data-cloud-rls.mjs',
    './scripts/check-phr-emergency-access-audit.mjs',
    './scripts/check-phr-fhir-validation.mjs',
    './scripts/check-phr-hipaa-compliance.mjs',
    './scripts/check-phr-consent-cache.mjs',
  ],
  yappcChecks: [
    './scripts/check-yappc-platform-provider-readiness.mjs',
    './scripts/check-yappc-product-family-control-plane.mjs',
    './scripts/check-yappc-product-unit-intent-handoff.mjs',
    './scripts/check-yappc-artifact-intelligence-boundary.mjs',
  ],
  flashitChecks: [
    './scripts/check-flashit-lifecycle-readiness.mjs',
    './scripts/check-flashit-client-conformance.mjs',
    './scripts/check-flashit-doc-content.mjs',
    './scripts/check-flashit-package-manager.mjs',
  ],
  artifactAuthoringChecks: artifactAuthoringReadinessScripts,
};

const productScopedGroupById = {
  'data-cloud': 'dataCloudChecks',
  'digital-marketing': 'dmosChecks',
  phr: 'phrChecks',
  yappc: 'yappcChecks',
  flashit: 'flashitChecks',
};

function uniqueExistingChecks(checkRefs) {
  const seen = new Set();
  return checkRefs.filter((checkRef) => {
    if (seen.has(checkRef)) {
      return false;
    }
    seen.add(checkRef);
    if (checkRef.startsWith('pnpm:')) {
      return true;
    }
    return existsSync(path.join(repoRoot, checkRef));
  });
}

function artifactAuthoringRelevant(paths) {
  return paths.some((changedPath) =>
    /^(platform\/typescript\/(?:ghatana-studio|kernel-artifacts|artifact|canvas|ui-builder)|products\/yappc\/|config\/artifact|\.kernel\/evidence\/artifact)/.test(changedPath),
  );
}

export function buildScopedExecutionOrder(affectedProducts, options = {}) {
  if (options.full) {
    return uniqueExistingChecks(executionOrder);
  }

  if (options.explicitProductScope) {
    const strictScopedPlan = [
      './scripts/check-affected-product-strict-release-profile.mjs',
    ];

    for (const productId of affectedProducts) {
      const groupName = productScopedGroupById[productId];
      if (groupName) {
        strictScopedPlan.push(...scopedCheckGroups[groupName]);
      }
    }

    return uniqueExistingChecks(strictScopedPlan);
  }

  const plan = [
    ...scopedCheckGroups.globalPrerequisiteChecks,
    ...scopedCheckGroups.platformChecks,
    ...scopedCheckGroups.productChecks,
  ];

  for (const productId of affectedProducts) {
    const groupName = productScopedGroupById[productId];
    if (groupName) {
      plan.push(...scopedCheckGroups[groupName]);
    }
  }

  if (options.releaseRisk || artifactAuthoringRelevant(options.paths ?? [])) {
    plan.push(...scopedCheckGroups.artifactAuthoringChecks);
  }

  return uniqueExistingChecks(plan);
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

function hasArg(flag) {
  return process.argv.includes(flag);
}

function splitCsv(value) {
  return String(value ?? '')
    .split(',')
    .map((entry) => entry.trim())
    .filter(Boolean);
}

function readChangedFilesFromGit(baseRef, headRef) {
  if (!baseRef && !headRef) {
    return [];
  }
  const range = baseRef && headRef ? `${baseRef}...${headRef}` : baseRef || headRef;
  const result = spawnSync('git', ['diff', '--name-only', range], {
    cwd: repoRoot,
    encoding: 'utf8',
  });
  if (result.status !== 0) {
    throw new Error(`Could not resolve release readiness diff ${range}`);
  }
  return result.stdout.split(/\r?\n/).filter(Boolean);
}

function releaseReadinessOptions() {
  const explicitPaths = splitCsv(parseArg('--paths'));
  const baseRef = parseArg('--base');
  const headRef = parseArg('--head');
  const explicitProducts = parseArg('--products') || parseArg('--product') || process.env.AFFECTED_PRODUCTS || '';
  return {
    full: hasArg('--full'),
    releaseRisk: hasArg('--release-risk') || process.env.RELEASE_RISK === 'true',
    explicitProductScope: explicitProducts.trim().length > 0,
    paths: explicitPaths.length > 0 ? explicitPaths : readChangedFilesFromGit(baseRef, headRef),
  };
}

export function discoverAffectedProducts() {
  const providedProducts = parseArg('--products') || parseArg('--product') || process.env.AFFECTED_PRODUCTS || '';
  const registry = loadCanonicalRegistry(repoRoot);

  if (providedProducts.trim().length > 0) {
    return filterReleaseEligibleProducts(
      providedProducts
      .split(',')
      .map((entry) => entry.trim())
      .filter(Boolean)
      .sort(),
      registry,
    );
  }

  const options = releaseReadinessOptions();
  if (options.paths.length > 0) {
    const result = resolveAffectedProducts(options.paths, registry, {
      businessProductsOnly: true,
      includeDemo: false,
    });
    return filterReleaseEligibleProducts(result.affectedProducts, registry);
  }

  const affectedProfile = readJsonIfExists(affectedProductProfilePath);
  if (Array.isArray(affectedProfile?.affectedProducts) && affectedProfile.affectedProducts.length > 0) {
    return filterReleaseEligibleProducts([...affectedProfile.affectedProducts].sort(), registry);
  }

  return [];
}

function validateProductionEvidence(productId, productMetadata) {
  const gaps = [];
  
  // Check deployment targets from registry
  const deploymentTargets = productMetadata?.deployment?.targets || [];
  const environments = productMetadata?.environments?.supported || [];
  
  // Determine if product targets staging or prod
  const targetsStaging = deploymentTargets.includes('staging') || environments.includes('staging');
  const targetsProd = deploymentTargets.includes('prod') || environments.includes('prod');
  
  if (!targetsStaging && !targetsProd) {
    return gaps; // No staging/prod target, no validation needed
  }

  // Helper to validate bootstrap evidence for a specific environment
  function validateBootstrapEvidence(environment) {
    const bootstrapEvidencePath = path.join(evidenceDir, productId, `${environment}-bootstrap-evidence.json`);
    const bootstrapAltPath = path.join(evidenceDir, productId, 'bootstrap-evidence.json');
    const bootstrapGenericPath = path.join(evidenceDir, productId, 'phr-release-readiness.json');
    
    const bootstrapEvidenceExists = existsSync(bootstrapEvidencePath) || 
                                   existsSync(bootstrapAltPath) || 
                                   existsSync(bootstrapGenericPath);
    
    if (!bootstrapEvidenceExists) {
      gaps.push({
        severity: 'P0',
        gate: `${environment}-bootstrap-evidence`,
        reason: 'missing-bootstrap-evidence',
        message: `Product ${productId} targets ${environment} but lacks bootstrap evidence file`,
        environment,
      });
      return false;
    }
    
    // Validate bootstrap evidence content
    const evidencePath = existsSync(bootstrapEvidencePath) ? bootstrapEvidencePath :
                        existsSync(bootstrapAltPath) ? bootstrapAltPath : bootstrapGenericPath;
    const evidence = readJsonIfExists(evidencePath);
    
    if (!evidence || !evidence.bootstrap || !evidence.bootstrap.validated) {
      gaps.push({
        severity: 'P0',
        gate: `${environment}-bootstrap-evidence`,
        reason: 'invalid-bootstrap-evidence',
        message: `Product ${productId} ${environment} bootstrap evidence exists but is not validated`,
        environment,
      });
      return false;
    }
    
    // Check for required bootstrap fields
    const requiredFields = ['postgres', 'migrations', 'secrets', 'storage', 'distributedCache'];
    const missingFields = requiredFields.filter(field => !evidence.bootstrap[field]);
    
    if (missingFields.length > 0) {
      gaps.push({
        severity: 'P0',
        gate: `${environment}-bootstrap-evidence`,
        reason: 'incomplete-bootstrap-evidence',
        message: `Product ${productId} ${environment} bootstrap evidence missing required fields: ${missingFields.join(', ')}`,
        environment,
        missingFields,
      });
      return false;
    }
    
    return true;
  }

  // Helper to validate rollback evidence for a specific environment
  function validateRollbackEvidence(environment) {
    const rollbackEvidencePath = path.join(evidenceDir, productId, `${environment}-rollback-evidence.json`);
    const rollbackAltPath = path.join(evidenceDir, productId, 'rollback-evidence.json');
    const rollbackPolicyPath = path.join(evidenceDir, productId, 'stable-deployment-manifest-history-policy.yaml');
    
    const rollbackEvidenceExists = existsSync(rollbackEvidencePath) || 
                                  existsSync(rollbackAltPath) || 
                                  existsSync(rollbackPolicyPath);
    
    if (!rollbackEvidenceExists) {
      gaps.push({
        severity: 'P0',
        gate: `${environment}-rollback-evidence`,
        reason: 'missing-rollback-evidence',
        message: `Product ${productId} targets ${environment} but lacks rollback evidence file`,
        environment,
      });
      return false;
    }
    
    // Validate rollback evidence content
    const evidencePath = existsSync(rollbackEvidencePath) ? rollbackEvidencePath :
                        existsSync(rollbackAltPath) ? rollbackAltPath : rollbackPolicyPath;
    const evidence = readJsonIfExists(evidencePath);
    
    if (!evidence || (!evidence.rollback && !evidence.deploymentManifestHistory)) {
      gaps.push({
        severity: 'P0',
        gate: `${environment}-rollback-evidence`,
        reason: 'invalid-rollback-evidence',
        message: `Product ${productId} ${environment} rollback evidence exists but is not valid`,
        environment,
      });
      return false;
    }
    
    // Check for required rollback fields
    const requiredFields = ['deploymentManifestHistory', 'artifactSelectionPolicy', 'approvalContract'];
    const missingFields = requiredFields.filter(field => !evidence[field]);
    
    if (missingFields.length > 0) {
      gaps.push({
        severity: 'P0',
        gate: `${environment}-rollback-evidence`,
        reason: 'incomplete-rollback-evidence',
        message: `Product ${productId} ${environment} rollback evidence missing required fields: ${missingFields.join(', ')}`,
        environment,
        missingFields,
      });
      return false;
    }
    
    return true;
  }

  // Validate staging evidence if staging is a target
  if (targetsStaging) {
    validateBootstrapEvidence('staging');
    validateRollbackEvidence('staging');
  }

  // Validate prod evidence if prod is a target
  if (targetsProd) {
    validateBootstrapEvidence('prod');
    validateRollbackEvidence('prod');
  }

  return gaps;
}

export function buildProductScorecard(productId, runs, releaseProfiles, registry, options = {}) {
  const { wave2Rows, releaseTargetScore } = options;
  const runPassRatio = Number((runs.filter((run) => run.status === 0).length / Math.max(1, runs.length)).toFixed(2));
  const wave2Row = wave2Rows.get(productId);
  const productMetadata = registry[productId] ?? null;
  const defaultScore = Number(Math.max(0, Math.min(5, 2 + (runPassRatio * 3))).toFixed(2));
  const dimensions = releaseScorecardDimensionNames.map((dimensionName) => {
    const baseline = defaultScore;
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

  if (!productMetadata) {
    blockingGaps.push({
      severity: 'P0',
      gate: 'product-metadata',
      reason: 'missing-product-metadata',
    });
  }

  // FND-002: Validate production evidence for products targeting production
  const productionEvidenceGaps = validateProductionEvidence(productId, productMetadata);
  blockingGaps.push(...productionEvidenceGaps);

  const consistencyGaps = validateRegistryAndEvidenceConsistency(productId, productMetadata);
  blockingGaps.push(...consistencyGaps);

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

  const releaseVerdict = blockingGaps.length === 0 ? 'pass' : 'fail';

  return {
    generatedAt: new Date().toISOString(),
    evidenceRun: evidenceRunMetadata(),
    ...evidenceLifecycleMetadata(releaseVerdict === 'pass'),
    productId,
    releaseVerdict,
    releaseProfiles,
    productMetadata,
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
  const wave2Rows = loadWave2ScoreRows();
  const paths = [];

  for (const productId of affectedProducts) {
    const scorecard = buildProductScorecard(productId, runs, releaseProfiles, registry, {
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
  const options = releaseReadinessOptions();
  const affectedProducts = discoverAffectedProducts();
  const plannedExecutionOrder = buildScopedExecutionOrder(affectedProducts, options);

  function failWithEvidence(exitCode, failedScript, reason) {
    const releaseProfiles = releaseProfileIds();
    mkdirSync(evidenceDir, { recursive: true });
    writeJsonWithRetry(evidencePath, {
      generatedAt: new Date().toISOString(),
      evidenceRun: evidenceRunMetadata(),
      ...evidenceLifecycleMetadata(false),
      pass: false,
      reason,
      failedScript,
      runs,
      journeyMatrix,
      releaseAreas,
      scopedCheckGroups,
      artifactAuthoringGateScripts: artifactAuthoringReadinessScripts,
      releaseProfiles,
      affectedProducts,
      scopedExecution: !options.full,
      plannedExecutionOrder,
    });
    writePerProductScorecards(affectedProducts, runs, releaseProfiles);
    if (failedScript) {
      console.error(`Product release readiness failed at ${failedScript}`);
    }
    process.exit(exitCode);
  }

  if (!options.full && affectedProducts.length === 0) {
    failWithEvidence(
      1,
      null,
      'No release-eligible products were provided or resolved. Pass --products <id[,id]> or --base/--head/--paths, or use --full for the platform-wide release gate.',
    );
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

  const buildStartedAt = Date.now();
  const buildResult = runCheck(platformLifecycleBuildScript);
  const buildStatus = buildResult.status ?? 1;
  const buildRunRecord = {
    script: platformLifecycleBuildScript,
    status: buildStatus,
    durationMs: Date.now() - buildStartedAt,
  };
  runByScript.set(platformLifecycleBuildScript, buildRunRecord);
  runs.push(buildRunRecord);
  if (buildStatus !== 0) {
    failWithEvidence(buildStatus, platformLifecycleBuildScript, 'platform-lifecycle-build-failure');
  }

  for (const scriptPath of plannedExecutionOrder) {
    const startedAt = Date.now();
    const result = runCheck(scriptPath, { products: affectedProducts });

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
    evidenceRun: evidenceRunMetadata(),
    ...evidenceLifecycleMetadata(true),
    pass: true,
    journeyResults,
    releaseAreaResults,
    runs,
    scopedCheckGroups,
    artifactAuthoringGateScripts: artifactAuthoringReadinessScripts,
    releaseProfiles: releaseProfileIds(),
    affectedProducts,
    scopedExecution: !options.full,
    plannedExecutionOrder,
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
