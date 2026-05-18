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
 * - config/product-shape-capability-matrix.json (Phase 5 public matrix)
 * - config/product-shape-capability-matrix.schema.json (Phase 5 public schema)
 * - docs/kernel/PRODUCT_SHAPE_CAPABILITY_MATRIX.md (human-readable)
 * - docs/architecture/PRODUCT_SHAPE_CAPABILITY_MATRIX.md (Phase 5 public documentation)
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
  const contractStatus = profile ? 'profile-backed' : 'blocked';
  const e2eStatus = lifecycleStatus === 'enabled' && capabilityGaps.length === 0 && adaptersExecutable
    ? 'ready'
    : lifecycleStatus === 'enabled'
      ? 'blocked'
      : 'not-enabled';

  return {
    contractReadiness: dimension(
      contractStatus,
      profile ? [] : ['missing-lifecycle-profile'],
    ),
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
    studioUxReadiness: dimension(
      hasReadinessEvidence(lifecycleReadiness, 'studio') ? 'evidence-backed' : 'unproven',
      hasReadinessEvidence(lifecycleReadiness, 'studio') ? [] : ['studio-ux-evidence-not-recorded'],
      asStringArray(lifecycleReadiness.evidenceRefs).filter((ref) => ref.includes('studio')),
    ),
    e2eReadiness: dimension(
      e2eStatus,
      e2eStatus === 'ready' ? [] : ['e2e-not-ready', ...capabilityGaps],
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

function generatePhase5Matrix(registry, matrix) {
  return matrix.map((row) => {
    const product = registry.registry[row.productId];
    const lifecycleReadiness = product?.lifecycleReadiness ?? {};
    const surfaces = (product?.surfaces ?? []).map((surface) => surface.type).sort();
    const requiredArtifactManifests = [
      ...new Set([
        ...Object.values(product?.artifacts ?? {}).map((artifact) => artifact.type ?? artifact.packaging).filter(Boolean),
        'lifecycle-result',
        'artifact-manifest',
        'deployment-manifest',
      ]),
    ].sort();
    const missingEvidenceRefs = [
      ...row.missingManifests.map((manifest) => `manifest:${manifest}`),
      ...row.missingProviders.map((provider) => `provider:${provider}`),
      ...row.capabilityGaps.map((gap) => `capability:${gap}`),
    ].sort();
    const blockers = normalizeBlockers([
      ...row.blockingGaps,
      ...asStringArray(lifecycleReadiness.blockers),
    ]);

    return {
      productId: row.productId,
      kind: row.productKind,
      surfaces,
      lifecycleStatus: row.lifecycleStatus,
      lifecycleExecutionAllowed: product?.lifecycleExecutionAllowed === true,
      requiredKernelCapabilities: [
        ...new Set([
          ...row.requiredCapabilities,
          ...row.gatesNeeded.map((gate) => `gate:${gate}`),
        ]),
      ].sort(),
      requiredToolchainAdapters: row.requiredAdapters,
      requiredArtifactManifests,
      requiredDeploymentTargets: asStringArray(product?.deployment?.targets),
      requiredSecurityPrivacyGates: row.gatesNeeded.filter((gate) =>
        ['security', 'privacy', 'a11y', 'i18n', 'consent', 'pii-classification', 'audit-evidence', 'data-sovereignty', 'tenant-data-sovereignty'].includes(gate),
      ),
      requiredDataCloudSupport: requiredDataCloudSupport(row),
      requiredYappcVisibility: requiredYappcVisibility(row),
      requiredAgentRuntimeSupport: requiredAgentRuntimeSupport(row),
      currentEvidenceRefs: row.readinessEvidence,
      missingEvidenceRefs,
      blockers,
      nextActions: row.nextActions,
      validationCommands: [row.nextValidationCommand],
    };
  });
}

function normalizeBlockers(blockers) {
  return [...new Set(blockers.map((blocker) => {
    if (
      blocker.startsWith('missing-manifest:') ||
      blocker.startsWith('missing-provider:') ||
      blocker.startsWith('missing-adapter:') ||
      blocker.startsWith('unknown-adapter:') ||
      blocker.startsWith('adapter-not-') ||
      blocker.startsWith('deploy-adapter-')
    ) {
      return blocker;
    }
    if (blocker.includes('gate')) {
      return `gate:${blocker}`;
    }
    return `product-owner:${blocker}`;
  }))].sort();
}

function requiredDataCloudSupport(row) {
  if (row.productId === 'data-cloud') {
    return ['provider-bridge', 'runtime-truth', 'event-store', 'artifact-reference-store', 'health-snapshots'];
  }
  if (row.lifecycleStatus === 'enabled') {
    return ['runtime-truth', 'lifecycle-events', 'artifact-references', 'health-snapshots'];
  }
  if (row.productKind === 'platform-provider') {
    return ['provider-bridge-validation'];
  }
  return row.missingProviders.length > 0 ? row.missingProviders : [];
}

function requiredYappcVisibility(row) {
  if (row.productId === 'yappc') {
    return ['product-unit-intent-export', 'artifact-intelligence-contracts', 'builder-document-compatibility'];
  }
  if (row.lifecycleStatus === 'enabled' || row.lifecycleStatus === 'planned') {
    return ['product-unit-summary-visible'];
  }
  return [];
}

function requiredAgentRuntimeSupport(row) {
  if (row.productId === 'data-cloud') {
    return ['agent-action-evidence', 'trace-ledger', 'policy-denial-observability'];
  }
  if (row.lifecycleStatus === 'enabled' || row.lifecycleStatus === 'planned') {
    return ['approval-aware-agent-actions', 'verification-proof-evidence'];
  }
  return [];
}

function generatePhase5Schema() {
  return {
    $schema: 'http://json-schema.org/draft-07/schema#',
    $id: 'https://ghatana.io/schemas/product-shape-capability-matrix.json',
    title: 'Product Shape Capability Matrix',
    type: 'object',
    required: ['version', 'generated', 'source', 'matrix'],
    additionalProperties: false,
    properties: {
      version: { type: 'string', pattern: '^\\d+\\.\\d+\\.\\d+$' },
      generated: { type: 'string', format: 'date-time' },
      source: { type: 'string' },
      matrix: {
        type: 'array',
        items: { $ref: '#/definitions/MatrixRow' },
      },
    },
    definitions: {
      MatrixRow: {
        type: 'object',
        additionalProperties: false,
        required: [
          'productId',
          'kind',
          'surfaces',
          'lifecycleStatus',
          'lifecycleExecutionAllowed',
          'requiredKernelCapabilities',
          'requiredToolchainAdapters',
          'requiredArtifactManifests',
          'requiredDeploymentTargets',
          'requiredSecurityPrivacyGates',
          'requiredDataCloudSupport',
          'requiredYappcVisibility',
          'requiredAgentRuntimeSupport',
          'currentEvidenceRefs',
          'missingEvidenceRefs',
          'blockers',
          'nextActions',
          'validationCommands',
        ],
        properties: Object.fromEntries([
          ['productId', { type: 'string', pattern: '^[a-z0-9]+(-[a-z0-9]+)*$' }],
          ['kind', { type: 'string' }],
          ['lifecycleStatus', { type: 'string', enum: ['disabled', 'planned', 'partial', 'enabled'] }],
          ['lifecycleExecutionAllowed', { type: 'boolean' }],
          ...[
            'surfaces',
            'requiredKernelCapabilities',
            'requiredToolchainAdapters',
            'requiredArtifactManifests',
            'requiredDeploymentTargets',
            'requiredSecurityPrivacyGates',
            'requiredDataCloudSupport',
            'requiredYappcVisibility',
            'requiredAgentRuntimeSupport',
            'currentEvidenceRefs',
            'missingEvidenceRefs',
            'blockers',
            'nextActions',
            'validationCommands',
          ].map((key) => [key, {
            type: 'array',
            items: { type: 'string', minLength: 1 },
          }]),
        ]),
      },
    },
  };
}

function generatePhase5Markdown(matrix) {
  const lines = [
    '# Product Shape Capability Matrix',
    '',
    'This matrix records shape validation evidence without enabling non-pilot products prematurely.',
    '',
    '| Product | Kind | Surfaces | Lifecycle | Execution allowed | Kernel capabilities | Toolchains | Blockers | Next actions |',
    '|---------|------|----------|-----------|-------------------|---------------------|------------|----------|--------------|',
  ];

  for (const row of matrix) {
    lines.push(`| ${row.productId} | ${row.kind} | ${row.surfaces.join(', ') || 'none'} | ${row.lifecycleStatus} | ${String(row.lifecycleExecutionAllowed)} | ${row.requiredKernelCapabilities.join(', ') || 'none'} | ${row.requiredToolchainAdapters.join(', ') || 'none'} | ${row.blockers.join(', ') || 'none'} | ${row.nextActions.join('<br>') || 'none'} |`);
  }

  lines.push('');
  lines.push('## Required Rows Covered');
  lines.push('');
  for (const productId of ['digital-marketing', 'finance', 'phr', 'flashit', 'yappc', 'data-cloud', 'audio-video', 'tutorputor', 'dcmaar', 'security-gateway']) {
    const row = matrix.find((candidate) => candidate.productId === productId);
    lines.push(`- ${productId}: ${row ? 'present' : 'missing'}`);
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
  const generatedAt = new Date().toISOString();
  const jsonOutputPath = join(repoRoot, 'config/generated/product-shape-capability-matrix.json');
  mkdirSync(dirname(jsonOutputPath), { recursive: true });
  writeFileSync(jsonOutputPath, JSON.stringify({ version: '1.0.0', generated: generatedAt, source: 'config/canonical-product-registry.json', matrix }, null, 2));
  console.log(`Generated ${jsonOutputPath}`);

  const phase5Matrix = generatePhase5Matrix(registry, matrix);
  const phase5JsonOutputPath = join(repoRoot, 'config/product-shape-capability-matrix.json');
  writeFileSync(phase5JsonOutputPath, JSON.stringify({ version: '1.0.0', generated: generatedAt, source: 'config/canonical-product-registry.json', matrix: phase5Matrix }, null, 2));
  console.log(`Generated ${phase5JsonOutputPath}`);

  const phase5SchemaPath = join(repoRoot, 'config/product-shape-capability-matrix.schema.json');
  writeFileSync(phase5SchemaPath, JSON.stringify(generatePhase5Schema(), null, 2));
  console.log(`Generated ${phase5SchemaPath}`);

  // Write Markdown output
  const mdOutputPath = join(repoRoot, 'docs/kernel/PRODUCT_SHAPE_CAPABILITY_MATRIX.md');
  mkdirSync(dirname(mdOutputPath), { recursive: true });
  writeFileSync(mdOutputPath, generateMarkdown(matrix));
  console.log(`Generated ${mdOutputPath}`);

  const architectureMdOutputPath = join(repoRoot, 'docs/architecture/PRODUCT_SHAPE_CAPABILITY_MATRIX.md');
  mkdirSync(dirname(architectureMdOutputPath), { recursive: true });
  writeFileSync(architectureMdOutputPath, generatePhase5Markdown(phase5Matrix));
  console.log(`Generated ${architectureMdOutputPath}`);

  console.log(`Product shape capability matrix generated for ${matrix.length} products`);
}

try {
  await main();
} catch (error) {
  console.error(`Generation failed: ${error instanceof Error ? error.message : String(error)}`);
  process.exit(1);
}
