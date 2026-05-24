#!/usr/bin/env node

import { execFileSync } from 'node:child_process';
import { existsSync, mkdirSync, readFileSync, renameSync, unlinkSync, writeFileSync } from 'node:fs';
import path from 'node:path';

import { loadCanonicalRegistry, resolveAffectedProducts } from './resolve-affected-products.mjs';

const repoRoot = process.cwd();
const packageJsonPath = path.join(repoRoot, 'package.json');
const evidenceDir = path.join(repoRoot, '.kernel/evidence');
const evidencePath = path.join(evidenceDir, 'affected-product-release-profile.json');
const foundationUsageProfilesPath = path.join(repoRoot, 'config/product-foundation-usage-profiles.json');
const productReleaseWorkflowPath = path.join(repoRoot, '.github/workflows/product-release.yml');
const affectedWorkflowPath = path.join(repoRoot, '.github/workflows/affected-products.yml');
const strictTargetProducts = ['data-cloud', 'digital-marketing', 'finance', 'flashit', 'phr', 'yappc'];

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

function parseArg(flag) {
  const index = process.argv.indexOf(flag);
  if (index >= 0 && process.argv[index + 1]) {
    return process.argv[index + 1];
  }
  return undefined;
}

function normalizeProducts(raw) {
  return raw
    .split(',')
    .map((entry) => entry.trim())
    .filter(Boolean)
    .sort();
}

function discoverChangedFiles(baseRef, headRef) {
  const args = ['diff', '--name-only'];
  if (baseRef && headRef) {
    args.push(`${baseRef}...${headRef}`);
  } else {
    args.push('HEAD~1...HEAD');
  }
  const output = execFileSync('git', args, {
    cwd: repoRoot,
    encoding: 'utf8',
    stdio: ['ignore', 'pipe', 'pipe'],
  });
  return output.split(/\r?\n/).filter(Boolean);
}

const providedProducts = parseArg('--products') || process.env.AFFECTED_PRODUCTS || '';
const baseRef = parseArg('--base');
const headRef = parseArg('--head');

const registry = loadCanonicalRegistry(repoRoot);
let affectedProducts;

if (providedProducts) {
  affectedProducts = normalizeProducts(providedProducts);
} else {
  const changedFiles = discoverChangedFiles(baseRef, headRef);
  affectedProducts = resolveAffectedProducts(changedFiles, registry, {
    businessProductsOnly: true,
  }).affectedProducts;
}

const packageJson = JSON.parse(readFileSync(packageJsonPath, 'utf8'));
const scripts = packageJson.scripts ?? {};
const violations = [];
const workflowViolations = [];

const strictWorkflowTokens = [
  'Resolve Affected Products',
  'Run strict product release readiness checks',
  'Enforce strict affected-product release profile',
  'Dry-run release mode',
  'Upload release readiness evidence',
];

const readinessTruthStatuses = new Set(['implemented', 'ready', 'enabled']);

function asArray(value) {
  return Array.isArray(value) ? value : [];
}

function normalizeDeploymentTargets(product) {
  return asArray(product?.deployment?.targets)
    .map((target) => String(target).trim().toLowerCase())
    .filter(Boolean);
}

function hasReleaseReadySurface(product) {
  return asArray(product?.surfaces).some((surface) =>
    readinessTruthStatuses.has(String(surface?.implementationStatus ?? '').toLowerCase()),
  );
}

function hasReleaseReadyLifecycle(product) {
  return readinessTruthStatuses.has(String(product?.lifecycleStatus ?? '').toLowerCase());
}

function hasNonLocalTarget(targets) {
  return targets.some((target) => !target.includes('local') && target !== 'compose-local');
}

function loadFoundationUsageProfiles() {
  if (!existsSync(foundationUsageProfilesPath)) {
    return null;
  }

  const source = JSON.parse(readFileSync(foundationUsageProfilesPath, 'utf8'));
  return source.profiles ?? null;
}

const perProductCoverage = [];
const strictTargetCoverage = [];
const foundationUsageProfiles = loadFoundationUsageProfiles();
const affectedProductSet = new Set(affectedProducts);

if (!foundationUsageProfiles) {
  violations.push('Missing config/product-foundation-usage-profiles.json or invalid profiles payload');
}

for (const productId of strictTargetProducts) {
  const product = registry[productId];
  if (!product) {
    violations.push(`Strict target product ${productId} is missing from canonical-product-registry.json`);
    continue;
  }

  const lifecycleStatus = product.lifecycleStatus ?? 'unknown';
  const isActive = product.metadata?.status === 'active';
  const ciEnabled = product.ci?.enabled === true;

  if (!isActive) {
    violations.push(`Strict target product ${productId} must be active in canonical product registry`);
  }

  if (['enabled', 'planned', 'partial'].includes(lifecycleStatus) && !ciEnabled) {
    violations.push(
      `Strict target product ${productId} must have ci.enabled=true when lifecycleStatus is ${lifecycleStatus}`,
    );
  }

  strictTargetCoverage.push({
    productId,
    kind: product.kind,
    lifecycleStatus,
    active: isActive,
    ciEnabled,
  });

  const deploymentTargets = normalizeDeploymentTargets(product);
  const lifecycleEnabled = product.lifecycleExecutionAllowed === true || hasReleaseReadyLifecycle(product);
  const releaseCandidateLike = lifecycleEnabled && hasReleaseReadySurface(product);
  if (affectedProductSet.has(productId) && releaseCandidateLike && !hasNonLocalTarget(deploymentTargets)) {
    violations.push(
      `Strict target product ${productId} declares release-ready status but deployment.targets are local-only (${deploymentTargets.join(', ') || 'none'})`,
    );
  }
}

for (const workflowPath of [productReleaseWorkflowPath, affectedWorkflowPath]) {
  if (!existsSync(workflowPath)) {
    workflowViolations.push(`Missing release orchestration workflow: ${path.relative(repoRoot, workflowPath)}`);
  }
}

if (existsSync(productReleaseWorkflowPath)) {
  const source = readFileSync(productReleaseWorkflowPath, 'utf8');
  for (const token of strictWorkflowTokens) {
    if (!source.includes(token)) {
      workflowViolations.push(`product-release.yml is missing strict token ${JSON.stringify(token)}`);
    }
  }
}

for (const productId of affectedProducts) {
  const product = registry[productId];
  if (!product) {
    violations.push(`Affected product ${productId} is not present in canonical-product-registry.json`);
    continue;
  }

  if (product.kind !== 'business-product') {
    violations.push(`Affected product ${productId} must be a business-product for strict release orchestration`);
    continue;
  }

  if (product.metadata?.status !== 'active') {
    violations.push(`Affected product ${productId} must be active in canonical product registry`);
  }

  const deploymentTargets = normalizeDeploymentTargets(product);
  const lifecycleEnabled = product.lifecycleExecutionAllowed === true || hasReleaseReadyLifecycle(product);
  const releaseCandidateLike = lifecycleEnabled && hasReleaseReadySurface(product);
  if (releaseCandidateLike && !hasNonLocalTarget(deploymentTargets)) {
    violations.push(
      `Affected product ${productId} declares release-ready status but deployment.targets are local-only (${deploymentTargets.join(', ') || 'none'})`,
    );
  }

  if (releaseCandidateLike) {
    const readinessEvidenceRefs = asArray(product?.lifecycleReadiness?.evidenceRefs);
    if (readinessEvidenceRefs.length === 0) {
      violations.push(
        `Affected product ${productId} has release-ready claims but no lifecycleReadiness.evidenceRefs; claims must be evidence-backed`,
      );
    }
    if (product.lifecycleExecutionAllowed !== true) {
      violations.push(
        `Affected product ${productId} has release-ready claims but lifecycleExecutionAllowed is not true`,
      );
    }
  }

  const usageProfile = foundationUsageProfiles?.[productId];
  if (releaseCandidateLike) {
    if (!usageProfile) {
      violations.push(`Affected product ${productId} is missing foundation usage profile in config/product-foundation-usage-profiles.json`);
    } else {
      const requiredSlices = asArray(usageProfile.requiredSlices).filter((slice) => slice?.requiredForRelease === true);
      if (requiredSlices.length === 0) {
        violations.push(`Affected product ${productId} foundation usage profile must declare at least one required release slice`);
      }
      const nonReadyRequiredSlices = requiredSlices.filter((slice) => slice?.productionReady !== true);
      if (nonReadyRequiredSlices.length > 0) {
        violations.push(
          `Affected product ${productId} has non-production required slices: ${nonReadyRequiredSlices.map((slice) => slice.id ?? 'unknown').join(', ')}`,
        );
      }
    }
  }

  const releaseJourney = {
    productId,
    journeyChecks: {
      build: `build:${productId}`,
      test: `test:${productId}`,
      validate: `validate:${productId}`,
      package: `package:${productId}`,
    },
    hasWebSurface: (product.surfaces ?? []).some((surface) => surface.type === 'web'),
    hasBackendSurface: (product.surfaces ?? []).some((surface) => surface.type === 'backend-api'),
  };

  const requiredScripts = [releaseJourney.journeyChecks.build, releaseJourney.journeyChecks.test];
  for (const scriptName of requiredScripts) {
    if (!scripts[scriptName]) {
      violations.push(`Missing strict release script ${scriptName} for affected product ${productId}`);
    }
  }

  const optionalCore = [releaseJourney.journeyChecks.validate, releaseJourney.journeyChecks.package];
  const hasAtLeastOneReleaseCore = optionalCore.some((scriptName) => Boolean(scripts[scriptName]));
  if (!hasAtLeastOneReleaseCore) {
    violations.push(
      `Affected product ${productId} must define at least one release core script (${optionalCore.join(' or ')})`,
    );
  }

  if (releaseJourney.hasWebSurface && !scripts[`build:${productId}-web`]) {
    violations.push(`Affected product ${productId} has a web surface but is missing script build:${productId}-web`);
  }

  if (releaseJourney.hasBackendSurface && !scripts[`build:${productId}-gateway`]) {
    violations.push(
      `Affected product ${productId} has a backend-api surface but is missing script build:${productId}-gateway`,
    );
  }

  perProductCoverage.push({
    productId,
    requiredScripts,
    optionalCore,
    hasAtLeastOneReleaseCore,
    hasWebSurface: releaseJourney.hasWebSurface,
    hasBackendSurface: releaseJourney.hasBackendSurface,
    deploymentTargets,
    hasFoundationUsageProfile: Boolean(foundationUsageProfiles?.[productId]),
  });
}

violations.push(...workflowViolations);

mkdirSync(evidenceDir, { recursive: true });
writeJsonWithRetry(evidencePath, {
  generatedAt: new Date().toISOString(),
  affectedProducts,
  strictTargetCoverage,
  perProductCoverage,
  violations,
});

if (violations.length > 0) {
  console.error('Affected product strict release profile failed:\n');
  for (const violation of violations) {
    console.error(`- ${violation}`);
  }
  console.error(`\nEvidence written to ${path.relative(repoRoot, evidencePath)}`);
  process.exit(1);
}

console.log(`Affected product strict release profile passed. Evidence: ${path.relative(repoRoot, evidencePath)}`);
