#!/usr/bin/env node

/**
 * Generate Product Shape Capability Matrix
 *
 * Analyzes the canonical product registry, lifecycle profiles, and toolchain adapters
 * to generate a matrix showing which products can be represented by which lifecycle
 * profiles and what capabilities they require.
 *
 * Outputs:
 * - config/generated/product-shape-capability-matrix.json (machine-readable)
 * - docs/kernel/PRODUCT_SHAPE_CAPABILITY_MATRIX.md (human-readable)
 */

import { readFileSync, mkdirSync, writeFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(__dirname, '..');

function loadRegistry() {
  const registryPath = join(repoRoot, 'config/canonical-product-registry.json');
  return JSON.parse(readFileSync(registryPath, 'utf8'));
}

function loadLifecycleProfiles() {
  const profilesPath = join(repoRoot, 'config/product-lifecycle-profiles.json');
  return JSON.parse(readFileSync(profilesPath, 'utf8')).profiles;
}

function loadToolchainAdapters() {
  const adaptersPath = join(repoRoot, 'config/toolchain-adapter-registry.json');
  return JSON.parse(readFileSync(adaptersPath, 'utf8')).adapters;
}

function analyzeProduct(productId, product, profiles, adapters) {
  const lifecycleProfile = product.lifecycleProfile;
  const profile = profiles[lifecycleProfile];
  const lifecycleReadiness = product.lifecycleReadiness ?? {};
  const lifecycleStatus = product.lifecycleStatus || (product.lifecycle?.enabled ? 'enabled' : 'disabled');
  const surfaces = product.surfaces || [];
  const surfaceTypes = surfaces.map(s => s.type).sort();
  const productKind = product.kind ?? 'business-product';
  const shapeValidationMode = lifecycleStatus === 'enabled'
    ? 'execution'
    : lifecycleStatus === 'planned'
      ? 'shape-only'
      : lifecycleStatus === 'partial'
        ? 'shape-only-with-known-limitations'
        : 'disabled-observed';

  // Determine required capabilities based on profile and surfaces
  const requiredCapabilities = [];
  const requiredAdapters = [];
  const adapterReadiness = [];
  const findings = [];
  const capabilityGaps = [];
  const reasonCodes = [];
  const missingManifests = [];
  const missingProviders = [];
  const blockingGaps = [];
  const gatesNeeded = [
    ...new Set([
      'security',
      'privacy',
      'i18n',
      'a11y',
      ...asStringArray(lifecycleReadiness.requiredGates),
    ]),
  ].sort();
  reasonCodes.push(...asStringArray(lifecycleReadiness.reasonCodes));

  if (!profile && lifecycleStatus !== 'disabled') {
    findings.push(`Lifecycle profile "${lifecycleProfile}" not found in product-lifecycle-profiles.json`);
    capabilityGaps.push('missing-lifecycle-profile');
    reasonCodes.push('missing-lifecycle-profile');
  }

  // Check adapter support for each surface
  for (const surface of surfaces) {
    if (lifecycleStatus === 'disabled' && !profile) {
      continue;
    }
    const surfaceType = surface.type;
    const adapterKey = profile?.defaultAdapters?.[surfaceType];
    const adapter = adapterKey ? adapters[adapterKey] : null;

    if (!adapterKey && lifecycleStatus !== 'disabled') {
      findings.push(`Surface "${surfaceType}" has no default adapter defined in profile "${lifecycleProfile}"`);
      capabilityGaps.push(`missing-adapter:${surfaceType}`);
      reasonCodes.push(`missing-adapter:${surfaceType}`);
    } else if (!adapter) {
      findings.push(`Adapter "${adapterKey}" for surface "${surfaceType}" not found in toolchain-adapter-registry.json`);
      capabilityGaps.push(`unknown-adapter:${adapterKey}`);
      reasonCodes.push(`unknown-adapter:${adapterKey}`);
    } else if (adapter.status !== 'implemented') {
      findings.push(`Adapter "${adapterKey}" for surface "${surfaceType}" has status "${adapter.status}" (not fully implemented)`);
      capabilityGaps.push(`adapter-not-implemented:${adapterKey}`);
      reasonCodes.push(`adapter-not-implemented:${adapterKey}`);
    } else if (adapter.readiness !== 'execution-ready' || adapter.lifecycleEnabled !== true) {
      findings.push(`Adapter "${adapterKey}" for surface "${surfaceType}" is not lifecycle execution-ready`);
      capabilityGaps.push(`adapter-not-execution-ready:${adapterKey}`);
      reasonCodes.push(`adapter-not-execution-ready:${adapterKey}`);
    }

    // Add capability requirements
    if (adapterKey) {
      requiredCapabilities.push(`${surfaceType}:${adapterKey}`);
      requiredAdapters.push(adapterKey);
      adapterReadiness.push({
        adapterId: adapterKey,
        surfaceType,
        status: adapter?.status ?? 'unknown',
        readiness: adapter?.readiness ?? 'unknown',
        lifecycleEnabled: adapter?.lifecycleEnabled === true,
        supportsBootstrapMode: adapter?.supportsBootstrapMode === true,
        supportsPlatformMode: adapter?.supportsPlatformMode === true,
      });
    }
  }

  // Check deployment adapter if lifecycle is enabled
  if (lifecycleStatus === 'enabled' && profile) {
    const deployAdapterKey = profile.defaultAdapters?.['deploy.local'];
    const deployAdapter = deployAdapterKey ? adapters[deployAdapterKey] : null;

    if (!deployAdapterKey) {
      findings.push(`No deployment adapter defined in profile "${lifecycleProfile}"`);
      capabilityGaps.push('missing-deploy-adapter:local');
    } else if (!deployAdapter) {
      findings.push(`Deployment adapter "${deployAdapterKey}" not found in toolchain-adapter-registry.json`);
      capabilityGaps.push(`unknown-deploy-adapter:${deployAdapterKey}`);
      reasonCodes.push(`unknown-deploy-adapter:${deployAdapterKey}`);
    } else if (deployAdapter.status !== 'implemented') {
      findings.push(`Deployment adapter "${deployAdapterKey}" has status "${deployAdapter.status}" (not fully implemented)`);
      capabilityGaps.push(`deploy-adapter-not-implemented:${deployAdapterKey}`);
      reasonCodes.push(`deploy-adapter-not-implemented:${deployAdapterKey}`);
    } else if (deployAdapter.readiness !== 'execution-ready' || deployAdapter.lifecycleEnabled !== true) {
      findings.push(`Deployment adapter "${deployAdapterKey}" is not lifecycle execution-ready`);
      capabilityGaps.push(`deploy-adapter-not-execution-ready:${deployAdapterKey}`);
      reasonCodes.push(`deploy-adapter-not-execution-ready:${deployAdapterKey}`);
    }

    if (deployAdapterKey) {
      requiredCapabilities.push(`deploy:${deployAdapterKey}`);
      requiredAdapters.push(deployAdapterKey);
      adapterReadiness.push({
        adapterId: deployAdapterKey,
        surfaceType: 'deploy.local',
        status: deployAdapter?.status ?? 'unknown',
        readiness: deployAdapter?.readiness ?? 'unknown',
        lifecycleEnabled: deployAdapter?.lifecycleEnabled === true,
        supportsBootstrapMode: deployAdapter?.supportsBootstrapMode === true,
        supportsPlatformMode: deployAdapter?.supportsPlatformMode === true,
      });
    }
  }

  // Determine status based on lifecycle status and findings
  let status;
  let executionReadiness;
  if (lifecycleStatus === 'enabled') {
    status = 'Pilot';
    executionReadiness = 'executable';
    if (findings.some(f => f.includes('not found') || f.includes('not fully implemented'))) {
      status = 'Enabled (with findings)';
      executionReadiness = 'blocked';
    }
  } else if (lifecycleStatus === 'planned') {
    status = 'Shape-only';
    executionReadiness = 'not-enabled';
  } else if (lifecycleStatus === 'disabled') {
    status = 'Disabled observed';
    executionReadiness = 'disabled';
    reasonCodes.push('disabled-observed');
  } else if (lifecycleStatus === 'partial') {
    status = 'Shape-only with limitations';
    executionReadiness = 'not-enabled';
    reasonCodes.push('partial-lifecycle');
  } else {
    status = 'Unknown';
    executionReadiness = 'unknown';
    reasonCodes.push('unknown-lifecycle-status');
  }

  if (lifecycleStatus === 'planned') {
    reasonCodes.push('planned-shape-only');
  }
  if (lifecycleStatus === 'enabled' && capabilityGaps.length === 0) {
    reasonCodes.push('execution-ready');
  }
  if (lifecycleStatus === 'enabled') {
    missingProviders.push(...['platform-events', 'platform-artifacts', 'platform-health'].filter((provider) =>
      product.kind === 'platform-provider' ? false : provider.startsWith('platform-'),
    ));
  }
  if (lifecycleStatus !== 'enabled') {
    missingManifests.push('lifecycle-plan', 'lifecycle-result', 'artifact-manifest', 'deployment-manifest');
  }
  missingManifests.push(...asStringArray(lifecycleReadiness.missingManifests));
  missingProviders.push(...asStringArray(lifecycleReadiness.missingProviders));
  const providerGapsForBlocking = missingProviders.filter((provider) =>
    !(lifecycleStatus === 'enabled' && provider.startsWith('platform-')),
  );
  blockingGaps.push(
    ...capabilityGaps,
    ...missingManifests.map((manifest) => `missing-manifest:${manifest}`),
    ...providerGapsForBlocking.map((provider) => `missing-provider:${provider}`),
  );

  // Determine shape description
  const shapeDescription = getShapeDescription(surfaceTypes, lifecycleProfile, productKind);
  const profileStatus = profile ? (profile.status ?? 'unknown') : lifecycleProfile ? 'missing' : 'not-declared';

  return {
    productId,
    name: product.name || productId,
    productKind,
    shape: shapeDescription,
    lifecycleStatus,
    shapeValidationMode,
    profileStatus,
    requiredAdapters: [...new Set(requiredAdapters)].sort(),
    adapterReadiness,
    missingManifests: [...new Set(missingManifests)].sort(),
    missingProviders: [...new Set(missingProviders)].sort(),
    gatesNeeded,
    readinessDimensions: readinessDimensions({
      lifecycleStatus,
      productKind,
      profile,
      requiredAdapters,
      adapterReadiness,
      missingManifests,
      missingProviders,
      gatesNeeded,
      capabilityGaps,
      lifecycleReadiness,
    }),
    minimumReleaseToEnable: lifecycleReadiness.minimumReleaseToEnable ?? minimumReleaseToEnable(lifecycleStatus),
    blockingGaps: [...new Set(blockingGaps)].sort(),
    nextValidationCommand: lifecycleReadiness.nextValidationCommand ?? nextValidationCommand(productId, lifecycleStatus),
    requiredCapabilities: requiredCapabilities.sort(),
    capabilityGaps: capabilityGaps.sort(),
    reasonCodes: [...new Set(reasonCodes)].sort(),
    nextActions: getNextActions(lifecycleStatus, capabilityGaps, productKind, lifecycleReadiness),
    readinessEvidence: asStringArray(lifecycleReadiness.evidenceRefs),
    executionReadiness,
    status,
    findings: findings.length > 0 ? findings : undefined,
  };
}

function readinessDimensions({
  lifecycleStatus,
  productKind,
  profile,
  requiredAdapters,
  adapterReadiness,
  missingManifests,
  missingProviders,
  gatesNeeded,
  capabilityGaps,
  lifecycleReadiness,
}) {
  const adaptersExecutable = requiredAdapters.length > 0 &&
    adapterReadiness.every((adapter) =>
      adapter.status === 'implemented' &&
      adapter.readiness === 'execution-ready' &&
      adapter.lifecycleEnabled === true &&
      adapter.supportsBootstrapMode === true
    );
  const platformProvidersMissing = missingProviders.filter((provider) => provider.startsWith('platform-'));
  const nonPlatformProvidersMissing = missingProviders.filter((provider) => !provider.startsWith('platform-'));
  const providerStatus = nonPlatformProvidersMissing.length > 0
    ? 'blocked'
    : platformProvidersMissing.length > 0 && lifecycleStatus === 'enabled'
      ? 'bootstrap-ready-platform-planned'
      : lifecycleStatus === 'enabled'
        ? 'ready'
        : 'not-required';
  const manifestStatus = missingManifests.length === 0 ? 'ready' : 'blocked';
  const privacySecurityStatus = gatesNeeded.some((gate) => ['privacy', 'security'].includes(gate))
    ? 'declared'
    : 'blocked';

  return {
    apiReadiness: dimension(profile ? 'profile-backed' : 'blocked', profile ? [] : ['missing-lifecycle-profile']),
    uiReadiness: dimension(
      hasReadinessEvidence(lifecycleReadiness, 'ui') ? 'evidence-backed' : 'unproven',
      hasReadinessEvidence(lifecycleReadiness, 'ui') ? [] : ['ui-evidence-not-recorded'],
      asStringArray(lifecycleReadiness.evidenceRefs).filter((ref) => ref.includes('ui')),
    ),
    providerReadiness: dimension(
      providerStatus,
      [
        ...nonPlatformProvidersMissing.map((provider) => `missing-provider:${provider}`),
        ...platformProvidersMissing.map((provider) => `platform-provider-planned:${provider}`),
      ],
    ),
    runtimeTruthReadiness: dimension(
      lifecycleStatus === 'enabled' ? 'required' : 'not-enabled',
      lifecycleStatus === 'enabled' ? ['runtime-truth-required'] : ['lifecycle-not-enabled'],
    ),
    approvalReadiness: dimension(
      gatesNeeded.includes('approval') || lifecycleStatus === 'enabled' ? 'required' : 'planned',
      gatesNeeded.includes('approval') || lifecycleStatus === 'enabled' ? ['approval-gate-required'] : ['approval-gate-planned'],
    ),
    privacySecurityGateReadiness: dimension(
      privacySecurityStatus,
      privacySecurityStatus === 'declared' ? [] : ['missing-privacy-security-gates'],
    ),
    artifactDeploymentManifestReadiness: dimension(
      manifestStatus,
      missingManifests.map((manifest) => `missing-manifest:${manifest}`),
    ),
    adapterExecutionReadiness: dimension(
      adaptersExecutable ? 'execution-ready' : 'not-executable',
      adaptersExecutable ? [] : ['adapter-execution-not-ready', ...capabilityGaps],
    ),
    platformModeReadiness: dimension(
      productKind === 'platform-provider' ? 'provider-owned' : 'requires-data-cloud-bridge',
      productKind === 'platform-provider' ? ['platform-provider-owned'] : ['data-cloud-bridge-required'],
    ),
    enablementGuardrail: dimension(
      capabilityGaps.length === 0 && adaptersExecutable && nonPlatformProvidersMissing.length === 0
        ? 'can-evaluate'
        : 'blocked',
      [
        ...capabilityGaps,
        ...nonPlatformProvidersMissing.map((provider) => `missing-provider:${provider}`),
      ],
    ),
  };
}

function dimension(status, reasonCodes = [], evidenceRefs = []) {
  return {
    status,
    reasonCodes: [...new Set(reasonCodes)].sort(),
    evidenceRefs: [...new Set(evidenceRefs)].sort(),
  };
}

function hasReadinessEvidence(lifecycleReadiness, token) {
  return asStringArray(lifecycleReadiness.evidenceRefs).some((ref) => ref.includes(token));
}

function minimumReleaseToEnable(lifecycleStatus) {
  return lifecycleStatus === 'enabled' ? 'current-pilot' : 'future-readiness-gate';
}

function nextValidationCommand(productId, lifecycleStatus) {
  if (productId === 'digital-marketing') {
    return 'pnpm check:digital-marketing-lifecycle-pilot --smoke';
  }
  if (lifecycleStatus === 'enabled') {
    return `node scripts/kernel-product.mjs product build ${productId} --dry-run --json`;
  }
  return 'pnpm check:product-shape-capability-matrix';
}

function asStringArray(value) {
  if (!Array.isArray(value)) {
    return [];
  }
  return value.filter(item => typeof item === 'string' && item.length > 0);
}

function getNextActions(lifecycleStatus, capabilityGaps, productKind, lifecycleReadiness = {}) {
  const configuredActions = asStringArray(lifecycleReadiness.nextRequiredWork);
  const defaultActions = [];
  if (lifecycleStatus === 'enabled' && capabilityGaps.length === 0) {
    defaultActions.push('Keep lifecycle smoke passing and preserve manifest evidence');
  } else if (lifecycleStatus === 'planned') {
    defaultActions.push('Keep shape-only until required adapters and manifests are executable');
  } else if (productKind === 'platform-provider') {
    defaultActions.push('Wire platform-mode provider bridge before enabling lifecycle execution');
  } else if (capabilityGaps.length > 0) {
    defaultActions.push('Resolve adapter capability gaps before enabling lifecycle execution');
  } else {
    defaultActions.push('Keep disabled until lifecycle profile and ProductUnit evidence are ready');
  }
  return [...new Set([...configuredActions, ...defaultActions])];
}

function getShapeDescription(surfaceTypes, lifecycleProfile, productKind) {
  const surfaceDesc = surfaceTypes.join(' + ');
  return `${productKind}: ${surfaceDesc} (${lifecycleProfile ?? 'no-profile'})`;
}

function generateMarkdown(matrix) {
  const lines = [
    '# Product Shape Capability Matrix',
    '',
    'This document shows which products can be represented by which lifecycle profiles and what capabilities they require.',
    '',
    'Generated from:',
    '- `config/canonical-product-registry.json`',
    '- `config/product-lifecycle-profiles.json`',
    '- `config/toolchain-adapter-registry.json`',
    '',
    '## Matrix',
    '',
    '| Product | Kind | Mode | Profile | Lifecycle Status | Readiness | Required Adapters | Reason Codes | Status |',
    '|---------|------|------|---------|------------------|-----------|-------------------|--------------|--------|',
  ];

  for (const row of matrix) {
    const capabilities = row.requiredCapabilities.join(', ') || 'None';
    const adapters = row.requiredAdapters.join(', ') || capabilities;
    const reasons = row.reasonCodes.join(', ') || 'None';
    lines.push(`| ${row.productId} | ${row.productKind} | ${row.shapeValidationMode} | ${row.profileStatus} | ${row.lifecycleStatus} | ${row.executionReadiness} | ${adapters} | ${reasons} | ${row.status} |`);
  }

  lines.push('');
  lines.push('## Findings');
  lines.push('');

  let hasFindings = false;
  for (const row of matrix) {
    if (row.findings && row.findings.length > 0) {
      hasFindings = true;
      lines.push(`### ${row.productId}`);
      lines.push('');
      for (const finding of row.findings) {
        lines.push(`- ${finding}`);
      }
      lines.push('');
    }
  }

  if (!hasFindings) {
    lines.push('No findings - all products have required profile and adapter support.');
  }

  return lines.join('\n');
}

async function main() {
  const registry = loadRegistry();
  const profiles = loadLifecycleProfiles();
  const adapters = loadToolchainAdapters();

  const matrix = [];
  for (const [productId, product] of Object.entries(registry.registry)) {
    const analysis = analyzeProduct(productId, product, profiles, adapters);
    matrix.push(analysis);
  }

  // Sort by product ID
  matrix.sort((a, b) => a.productId.localeCompare(b.productId));

  // Write JSON output
  const jsonOutputPath = join(repoRoot, 'config/generated/product-shape-capability-matrix.json');
  mkdirSync(dirname(jsonOutputPath), { recursive: true });
  writeFileSync(jsonOutputPath, JSON.stringify({ version: '1.0.0', generated: new Date().toISOString(), matrix }, null, 2));
  console.log(`Generated ${jsonOutputPath}`);

  // Write Markdown output
  const mdOutputPath = join(repoRoot, 'docs/kernel/PRODUCT_SHAPE_CAPABILITY_MATRIX.md');
  mkdirSync(dirname(mdOutputPath), { recursive: true });
  writeFileSync(mdOutputPath, generateMarkdown(matrix));
  console.log(`Generated ${mdOutputPath}`);

  console.log(`Product shape capability matrix generated for ${matrix.length} products`);
}

try {
  await main();
} catch (error) {
  console.error(`Generation failed: ${error instanceof Error ? error.message : String(error)}`);
  process.exit(1);
}
