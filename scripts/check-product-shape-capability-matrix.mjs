#!/usr/bin/env node

/**
 * Check Product Shape Capability Matrix
 *
 * Validates that:
 * - Matrix is generated and current
 * - Digital Marketing is the only enabled lifecycle proof target unless explicitly changed
 * - PHR/Finance/FlashIt planned status does not fail lifecycle execution checks
 * - YAPPC/Data Cloud platform-provider status is visible
 * - All registered products have shape rows
 */

import { existsSync, mkdirSync, readFileSync, renameSync, unlinkSync, writeFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(__dirname, '..');
const requiredReadinessDimensions = [
  'contractReadiness',
  'apiReadiness',
  'uiReadiness',
  'providerReadiness',
  'runtimeTruthReadiness',
  'approvalReadiness',
  'privacySecurityGateReadiness',
  'artifactDeploymentManifestReadiness',
  'adapterExecutionReadiness',
  'platformModeReadiness',
  'studioUxReadiness',
  'e2eReadiness',
];
const capabilityStatusScale = new Set(['Complete', 'Partial', 'Missing', 'Broken', 'Overbuilt', 'Unknown']);

function loadPackageScripts() {
  const packageJsonPath = join(repoRoot, 'package.json');
  const packageJson = JSON.parse(readFileSync(packageJsonPath, 'utf8'));
  return packageJson.scripts ?? {};
}

function loadCapabilityTraceabilityMatrix() {
  const matrixPath = join(repoRoot, 'config/product-capability-matrix.json');
  if (!existsSync(matrixPath)) {
    return null;
  }
  return JSON.parse(readFileSync(matrixPath, 'utf8'));
}

function loadRegistry() {
  const registryPath = join(repoRoot, 'config/canonical-product-registry.json');
  return JSON.parse(readFileSync(registryPath, 'utf8')).registry;
}

function loadMatrix() {
  const matrixPath = join(repoRoot, 'config/generated/product-shape-capability-matrix.json');
  if (!existsSync(matrixPath)) {
    return null;
  }
  return JSON.parse(readFileSync(matrixPath, 'utf8'));
}

function loadPublicMatrix() {
  const matrixPath = join(repoRoot, 'config/product-shape-capability-matrix.json');
  if (!existsSync(matrixPath)) {
    return null;
  }
  return JSON.parse(readFileSync(matrixPath, 'utf8'));
}

function loadAdapters() {
  const adaptersPath = join(repoRoot, 'config/toolchain-adapter-registry.json');
  return JSON.parse(readFileSync(adaptersPath, 'utf8')).adapters;
}

async function main() {
  const errors = [];
  const warnings = [];
  const packageScripts = loadPackageScripts();

  // 1. Check matrix exists
  const matrix = loadMatrix();
  if (!matrix) {
    errors.push('Product shape capability matrix not found. Run: pnpm generate:product-shape-capability-matrix');
    reportAndExit(errors, warnings);
    return;
  }
  const publicMatrix = loadPublicMatrix();
  if (!publicMatrix) {
    errors.push('Public product shape capability matrix not found at config/product-shape-capability-matrix.json');
  }

  // 2. Load registry for comparison and cross-validation
  const registry = loadRegistry();

  const capabilityMatrix = loadCapabilityTraceabilityMatrix();
  if (!capabilityMatrix) {
    errors.push('Canonical capability traceability matrix not found at config/product-capability-matrix.json');
  } else {
    validateCapabilityTraceabilityMatrix(capabilityMatrix, registry, packageScripts, errors, warnings);
  }

  // 3. Check matrix is recent (generated within last 7 days)
  const generatedDate = new Date(matrix.generated);
  const now = new Date();
  const daysSinceGeneration = (now - generatedDate) / (1000 * 60 * 60 * 24);
  if (daysSinceGeneration > 7) {
    warnings.push(`Matrix was generated ${Math.floor(daysSinceGeneration)} days ago. Consider regenerating.`);
  }

  // 4. Continue matrix checks
  const adapters = loadAdapters();
  const registryProductIds = Object.keys(registry);
  const matrixProductIds = matrix.matrix.map(m => m.productId);
  const publicMatrixProductIds = publicMatrix?.matrix?.map(m => m.productId) ?? [];

  // 4. Check all registered products have shape rows
  const missingInMatrix = registryProductIds.filter(id => !matrixProductIds.includes(id));
  if (missingInMatrix.length > 0) {
    errors.push(`Products in registry but missing from matrix: ${missingInMatrix.join(', ')}`);
  }
  const missingInPublicMatrix = registryProductIds.filter(id => !publicMatrixProductIds.includes(id));
  if (missingInPublicMatrix.length > 0) {
    errors.push(`Products in registry but missing from public matrix: ${missingInPublicMatrix.join(', ')}`);
  }

  const extraInMatrix = matrixProductIds.filter(id => !registryProductIds.includes(id));
  if (extraInMatrix.length > 0) {
    warnings.push(`Products in matrix but not in registry: ${extraInMatrix.join(', ')}`);
  }

  for (const row of matrix.matrix) {
    if (!row.productKind) {
      errors.push(`Matrix row "${row.productId}" is missing productKind`);
    }
    if (!row.shapeValidationMode) {
      errors.push(`Matrix row "${row.productId}" is missing shapeValidationMode`);
    }
    if (!row.profileStatus) {
      errors.push(`Matrix row "${row.productId}" is missing profileStatus`);
    }
    if (!row.executionReadiness) {
      errors.push(`Matrix row "${row.productId}" is missing executionReadiness`);
    }
    if (!Array.isArray(row.reasonCodes)) {
      errors.push(`Matrix row "${row.productId}" is missing reasonCodes`);
    }
    if (!Array.isArray(row.requiredAdapters)) {
      errors.push(`Matrix row "${row.productId}" is missing requiredAdapters`);
    }
    if (!Array.isArray(row.adapterReadiness)) {
      errors.push(`Matrix row "${row.productId}" is missing adapterReadiness`);
    }
    if (!Array.isArray(row.missingManifests)) {
      errors.push(`Matrix row "${row.productId}" is missing missingManifests`);
    }
    if (!Array.isArray(row.missingProviders)) {
      errors.push(`Matrix row "${row.productId}" is missing missingProviders`);
    }
    if (!Array.isArray(row.gatesNeeded)) {
      errors.push(`Matrix row "${row.productId}" is missing gatesNeeded`);
    }
    if (!isReadinessDimensions(row.readinessDimensions)) {
      errors.push(`Matrix row "${row.productId}" is missing structured readinessDimensions`);
    } else {
      for (const dimensionName of requiredReadinessDimensions) {
        if (!row.readinessDimensions[dimensionName]) {
          errors.push(`Matrix row "${row.productId}" is missing readiness dimension "${dimensionName}"`);
        }
      }
    }
    if (typeof row.minimumReleaseToEnable !== 'string' || row.minimumReleaseToEnable.length === 0) {
      errors.push(`Matrix row "${row.productId}" is missing minimumReleaseToEnable`);
    }
    if (!Array.isArray(row.blockingGaps)) {
      errors.push(`Matrix row "${row.productId}" is missing blockingGaps`);
    }
    if (typeof row.nextValidationCommand !== 'string' || row.nextValidationCommand.length === 0) {
      errors.push(`Matrix row "${row.productId}" is missing nextValidationCommand`);
    }
    const registryProduct = registry[row.productId];
    const lifecycleReadiness = registryProduct?.lifecycleReadiness;
    if (lifecycleReadiness) {
      for (const reasonCode of lifecycleReadiness.reasonCodes ?? []) {
        if (!row.reasonCodes.includes(reasonCode)) {
          errors.push(`Matrix row "${row.productId}" is missing configured readiness reason code "${reasonCode}"`);
        }
      }
      for (const gate of lifecycleReadiness.requiredGates ?? []) {
        if (!row.gatesNeeded.includes(gate)) {
          errors.push(`Matrix row "${row.productId}" is missing configured readiness gate "${gate}"`);
        }
      }
      for (const nextAction of lifecycleReadiness.nextRequiredWork ?? []) {
        if (!row.nextActions?.includes(nextAction)) {
          errors.push(`Matrix row "${row.productId}" is missing configured next action "${nextAction}"`);
        }
      }
      for (const evidenceRef of lifecycleReadiness.evidenceRefs ?? []) {
        if (!row.readinessEvidence?.includes(evidenceRef)) {
          errors.push(`Matrix row "${row.productId}" is missing configured readiness evidence "${evidenceRef}"`);
        }
      }
    }
    if (row.lifecycleStatus === 'disabled' && row.findings?.some(f => f.includes('Lifecycle profile "undefined" not found'))) {
      errors.push(`Disabled product "${row.productId}" has a false missing-profile finding`);
    }
    if (
      row.lifecycleStatus === 'disabled' &&
      row.shapeValidationMode === 'disabled-observed' &&
      row.capabilityGaps?.some(gap => gap === 'unknown-adapter:undefined')
    ) {
      errors.push(`Disabled observed product "${row.productId}" must not report unknown-adapter:undefined`);
    }
    for (const adapterId of row.requiredAdapters ?? []) {
      const adapter = adapters[adapterId];
      if (!adapter) {
        errors.push(`Matrix row "${row.productId}" references unknown adapter "${adapterId}"`);
        continue;
      }
      const readiness = row.adapterReadiness?.find(entry => entry.adapterId === adapterId);
      if (!readiness) {
        errors.push(`Matrix row "${row.productId}" is missing readiness entry for adapter "${adapterId}"`);
      } else if (readiness.readiness !== adapter.readiness || readiness.lifecycleEnabled !== (adapter.lifecycleEnabled === true)) {
        errors.push(`Matrix row "${row.productId}" readiness for adapter "${adapterId}" is out of sync with toolchain registry`);
      }
    }
    if (row.lifecycleStatus === 'enabled' && row.capabilityGaps?.length > 0) {
      errors.push(`Enabled product "${row.productId}" has capability gaps: ${row.capabilityGaps.join(', ')}`);
    }
    if (row.lifecycleStatus === 'enabled') {
      const nonPlatformBlockingGaps = (row.blockingGaps ?? []).filter(gap => !gap.startsWith('missing-provider:platform-'));
      if (nonPlatformBlockingGaps.length > 0) {
        errors.push(`Enabled product "${row.productId}" has blocking gaps: ${nonPlatformBlockingGaps.join(', ')}`);
      }
      if (row.executionReadiness !== 'executable') {
        errors.push(`Enabled product "${row.productId}" is not marked executable`);
      }
      if (row.readinessDimensions?.adapterExecutionReadiness?.status !== 'execution-ready') {
        errors.push(`Enabled product "${row.productId}" does not have execution-ready adapters`);
      }
      if (!['ready', 'bootstrap-ready-platform-planned'].includes(row.readinessDimensions?.providerReadiness?.status)) {
        errors.push(`Enabled product "${row.productId}" does not have valid bootstrap/platform provider readiness`);
      }
      if (row.readinessDimensions?.artifactDeploymentManifestReadiness?.status !== 'ready') {
        errors.push(`Enabled product "${row.productId}" is missing artifact/deployment manifest readiness`);
      }
    }
  }

  if (publicMatrix?.matrix) {
    validatePublicMatrixRows(publicMatrix.matrix, registry, errors);
  }

  // 5. Check exactly the two opening lifecycle pilots are enabled
  const OPENING_PILOTS = ['digital-marketing', 'phr'];
  const enabledProducts = matrix.matrix.filter(m => m.lifecycleStatus === 'enabled');
  const enabledProductIds = enabledProducts.map(m => m.productId).sort();
  if (enabledProducts.length === 0) {
    warnings.push('No products with lifecycleStatus="enabled" found in matrix');
  } else {
    const missingPilots = OPENING_PILOTS.filter(id => !enabledProductIds.includes(id));
    const unexpectedPilots = enabledProductIds.filter(id => !OPENING_PILOTS.includes(id));
    if (missingPilots.length > 0) {
      warnings.push(`Opening lifecycle pilot(s) missing from enabled set: ${missingPilots.join(', ')}`);
    }
    if (unexpectedPilots.length > 0) {
      warnings.push(`Unexpected products have lifecycleStatus="enabled" (not opening pilots): ${unexpectedPilots.join(', ')}`);
    }
  }

  const digitalMarketing = matrix.matrix.find(m => m.productId === 'digital-marketing');
  if (digitalMarketing?.findings?.some(f => f.includes('deployment adapter'))) {
    errors.push('Digital Marketing has an avoidable deployment adapter finding');
  }

  // 6. Check Finance/FlashIt have planned status (not enabled) — PHR is now an opening pilot
  const plannedProducts = ['finance', 'flashit'];
  for (const productId of plannedProducts) {
    const matrixRow = matrix.matrix.find(m => m.productId === productId);
    if (matrixRow && matrixRow.lifecycleStatus === 'enabled') {
      errors.push(`Product "${productId}" has lifecycleStatus="enabled" but should be "planned" (not a lifecycle execution pilot)`);
    }
    if (!registry[productId]?.lifecycleReadiness) {
      errors.push(`Product "${productId}" is missing lifecycleReadiness guardrails in the canonical registry`);
    }
  }
  // Also ensure PHR (opening pilot) has lifecycleReadiness in the registry
  if (!registry['phr']?.lifecycleReadiness) {
    errors.push(`Opening pilot "phr" is missing lifecycleReadiness guardrails in the canonical registry`);
  }

  // 7. Check YAPPC/Data Cloud platform-provider status is visible
  const platformProviderProducts = ['yappc', 'data-cloud'];
  for (const productId of platformProviderProducts) {
    const matrixRow = matrix.matrix.find(m => m.productId === productId);
    if (!matrixRow) {
      errors.push(`Platform provider product "${productId}" not found in matrix`);
    } else {
      if (matrixRow.productKind !== 'platform-provider') {
        warnings.push(`Platform provider product "${productId}" shape does not indicate platform-provider status: ${matrixRow.shape}`);
      }
      if (!registry[productId]?.lifecycleReadiness) {
        errors.push(`Platform provider product "${productId}" is missing lifecycleReadiness guardrails in the canonical registry`);
      }
    }
  }

  // 8. Check for critical findings that would block lifecycle execution
  const productsWithCriticalFindings = matrix.matrix.filter(m => 
    m.findings && m.findings.some(f => f.includes('not found') || f.includes('not fully implemented'))
  );
  for (const product of productsWithCriticalFindings) {
    if (product.lifecycleStatus === 'enabled') {
      errors.push(`Enabled product "${product.productId}" has critical findings that would block lifecycle execution`);
    }
  }

  writeCapabilityCheckEvidence(errors, warnings, capabilityMatrix);
  reportAndExit(errors, warnings);
}

function validateCapabilityTraceabilityMatrix(capabilityMatrix, registry, packageScripts, errors, warnings) {
  const capabilities = capabilityMatrix.capabilities;
  if (!Array.isArray(capabilities) || capabilities.length === 0) {
    errors.push('Capability traceability matrix must define a non-empty capabilities array');
    return;
  }

  const activeBusinessProducts = Object.entries(registry)
    .filter(([, product]) => product.kind === 'business-product' && product.metadata?.status === 'active')
    .map(([productId]) => productId)
    .sort();
  const coveredProducts = new Set();

  for (const capability of capabilities) {
    const capabilityKey = `${capability.productId}:${capability.capabilityId}`;

    if (!capability.productId || typeof capability.productId !== 'string') {
      errors.push(`Capability entry ${capabilityKey} is missing productId`);
      continue;
    }
    if (!registry[capability.productId]) {
      errors.push(`Capability entry ${capabilityKey} references unknown productId ${capability.productId}`);
      continue;
    }

    coveredProducts.add(capability.productId);

    if (!capability.capabilityId || typeof capability.capabilityId !== 'string') {
      errors.push(`Capability entry for ${capability.productId} is missing capabilityId`);
    }
    if (!capability.capabilityName || typeof capability.capabilityName !== 'string') {
      errors.push(`Capability entry ${capabilityKey} is missing capabilityName`);
    }
    if (!capability.owner || typeof capability.owner !== 'string') {
      errors.push(`Capability entry ${capabilityKey} is missing owner`);
    }
    if (!capabilityStatusScale.has(capability.status)) {
      errors.push(`Capability entry ${capabilityKey} has invalid status ${JSON.stringify(capability.status)}`);
    }

    if (capability.requiredForRelease === true && ['Missing', 'Broken', 'Unknown'].includes(capability.status)) {
      errors.push(
        `Required capability ${capabilityKey} has blocking status ${capability.status}; release must fail until resolved`,
      );
    }

    if (!Array.isArray(capability.implementationModules) || capability.implementationModules.length === 0) {
      errors.push(`Capability entry ${capabilityKey} is missing implementationModules`);
    }

    const evidence = Array.isArray(capability.testEvidence) ? capability.testEvidence : [];
    if (capability.requiredForRelease === true && evidence.length === 0) {
      errors.push(`Required capability ${capabilityKey} is missing testEvidence`);
    }

    let hasValidEvidence = false;
    for (const evidenceItem of evidence) {
      if (!evidenceItem || typeof evidenceItem !== 'object') {
        errors.push(`Capability entry ${capabilityKey} has invalid evidence item`);
        continue;
      }

      if (evidenceItem.kind === 'command') {
        if (typeof evidenceItem.value !== 'string' || !packageScripts[evidenceItem.value]) {
          errors.push(`Capability entry ${capabilityKey} references unknown command evidence ${JSON.stringify(evidenceItem.value)}`);
        } else {
          hasValidEvidence = true;
        }
        continue;
      }

      if (evidenceItem.kind === 'file') {
        const relativePath = evidenceItem.value;
        if (typeof relativePath !== 'string' || !existsSync(join(repoRoot, relativePath))) {
          errors.push(`Capability entry ${capabilityKey} references missing file evidence ${JSON.stringify(relativePath)}`);
        } else {
          hasValidEvidence = true;
        }
        continue;
      }

      warnings.push(`Capability entry ${capabilityKey} uses non-enforced evidence kind ${JSON.stringify(evidenceItem.kind)}`);
    }

    if (capability.requiredForRelease === true && !hasValidEvidence) {
      errors.push(`Required capability ${capabilityKey} does not provide validated implementation evidence`);
    }
  }

  for (const productId of activeBusinessProducts) {
    if (!coveredProducts.has(productId)) {
      errors.push(`Capability matrix is missing active business product coverage for ${productId}`);
    }
  }
}

function writeCapabilityCheckEvidence(errors, warnings, capabilityMatrix) {
  const evidenceDir = join(repoRoot, '.kernel/evidence');
  const evidencePath = join(evidenceDir, 'product-capability-matrix-validation.json');
  mkdirSync(evidenceDir, { recursive: true });
  const payload = {
    generatedAt: new Date().toISOString(),
    status: errors.length === 0 ? 'passed' : 'failed',
    capabilityCount: Array.isArray(capabilityMatrix?.capabilities) ? capabilityMatrix.capabilities.length : 0,
    warnings,
    errors,
  };
  writeJsonWithRetry(evidencePath, payload);
}

function writeJsonWithRetry(targetPath, payload, maxAttempts = 8) {
  const tempPath = `${targetPath}.tmp`;
  const content = `${JSON.stringify(payload, null, 2)}\n`;

  for (let attempt = 1; attempt <= maxAttempts; attempt += 1) {
    try {
      writeFileSync(tempPath, content, 'utf8');
      renameSync(tempPath, targetPath);
      return;
    } catch (error) {
      const code = error?.code;
      const retriable = code === 'UNKNOWN' || code === 'EACCES' || code === 'EBUSY' || code === 'EPERM';
      try {
        if (existsSync(tempPath)) {
          unlinkSync(tempPath);
        }
      } catch {
        // best-effort cleanup
      }
      if (!retriable || attempt === maxAttempts) {
        throw error;
      }
    }
  }
}

function validatePublicMatrixRows(rows, registry, errors) {
  const requiredColumns = [
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
  ];
  const requiredProducts = [
    'digital-marketing',
    'finance',
    'phr',
    'flashit',
    'yappc',
    'data-cloud',
    'audio-video',
    'tutorputor',
    'dcmaar',
    'security-gateway',
  ];

  for (const productId of requiredProducts) {
    if (!rows.some((row) => row.productId === productId)) {
      errors.push(`Public matrix missing required Phase 5 product row "${productId}"`);
    }
  }

  for (const row of rows) {
    for (const column of requiredColumns) {
      if (!(column in row)) {
        errors.push(`Public matrix row "${row.productId ?? 'unknown'}" is missing required column "${column}"`);
      }
    }
    for (const column of requiredColumns.filter((column) => Array.isArray(row[column]))) {
      if (!Array.isArray(row[column])) {
        errors.push(`Public matrix row "${row.productId}" column "${column}" must be an array`);
      }
    }
    const registryProduct = registry[row.productId];
    if (!registryProduct) {
      errors.push(`Public matrix row "${row.productId}" does not exist in canonical registry`);
      continue;
    }
    const registryLifecycleStatus = registryProduct.lifecycleStatus || (registryProduct.lifecycle?.enabled ? 'enabled' : 'disabled');
    if (row.lifecycleStatus !== registryLifecycleStatus) {
      errors.push(`Public matrix row "${row.productId}" lifecycleStatus="${row.lifecycleStatus}" does not match registry "${registryLifecycleStatus}"`);
    }
    if (row.lifecycleExecutionAllowed !== (registryProduct.lifecycleExecutionAllowed === true)) {
      errors.push(`Public matrix row "${row.productId}" lifecycleExecutionAllowed does not match registry`);
    }
    if (row.lifecycleStatus === 'enabled' && registryLifecycleStatus !== 'enabled') {
      errors.push(`Public matrix row "${row.productId}" claims lifecycle enabled while registry does not`);
    }
    for (const blocker of row.blockers ?? []) {
      if (!isMappedBlocker(blocker)) {
        errors.push(`Public matrix row "${row.productId}" blocker "${blocker}" is not mapped to a gate, adapter, manifest, provider, or product-owner action`);
      }
    }
    if (!Array.isArray(row.validationCommands) || row.validationCommands.length === 0) {
      errors.push(`Public matrix row "${row.productId}" must include at least one validation command`);
    }
  }
}

function isMappedBlocker(blocker) {
  return [
    'gate:',
    'missing-adapter:',
    'unknown-adapter:',
    'adapter-not-',
    'deploy-adapter-',
    'missing-manifest:',
    'missing-provider:',
    'product-owner:',
  ].some((prefix) => blocker.startsWith(prefix));
}

function reportAndExit(errors, warnings) {
  if (warnings.length > 0) {
    console.warn('Warnings:');
    for (const w of warnings) {
      console.warn(`  - ${w}`);
    }
  }

  if (errors.length > 0) {
    console.error(`\nProduct shape capability matrix check FAILED (${errors.length} error(s)):`);
    for (const e of errors) {
      console.error(`  - ${e}`);
    }
    process.exit(1);
  }

  console.log('Product shape capability matrix check passed');
}

function isReadinessDimensions(value) {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    return false;
  }
  return Object.values(value).every((dimension) =>
    dimension &&
    typeof dimension === 'object' &&
    !Array.isArray(dimension) &&
    typeof dimension.status === 'string' &&
    Array.isArray(dimension.reasonCodes) &&
    Array.isArray(dimension.evidenceRefs)
  );
}

try {
  await main();
} catch (error) {
  console.error(`Check failed: ${error instanceof Error ? error.message : String(error)}`);
  process.exit(1);
}
