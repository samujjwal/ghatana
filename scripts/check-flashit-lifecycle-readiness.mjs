#!/usr/bin/env node

import { existsSync, readFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import YAML from 'yaml';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '..');

const PRODUCT_ID = 'flashit';
const REQUIRED_GATES = ['privacy', 'preview-security', 'personal-data-classification', 'mobile-bundle-contract'];
const REQUIRED_VALIDATION_COMMANDS = [
  'pnpm --filter ./products/flashit type-check',
  'pnpm --filter ./products/flashit test',
  'pnpm --filter ./products/flashit/client/web type-check',
  'pnpm --filter ./products/flashit/client/web test',
  'pnpm --filter ./products/flashit/client/web build',
  'pnpm --filter ./products/flashit/backend/gateway type-check',
  'pnpm --filter ./products/flashit/backend/gateway test',
];

function readJson(relativePath) {
  return JSON.parse(readFileSync(path.join(repoRoot, relativePath), 'utf8'));
}

function readYaml(relativePath) {
  return YAML.parse(readFileSync(path.join(repoRoot, relativePath), 'utf8'));
}

function pathExists(relativePath) {
  return existsSync(path.join(repoRoot, relativePath));
}

function asArray(value) {
  return Array.isArray(value) ? value : [];
}

function fail(message) {
  throw new Error(message);
}

function assertIncludesAll(label, actual, expected) {
  const actualSet = new Set(asArray(actual));
  const missing = expected.filter((item) => !actualSet.has(item));
  if (missing.length > 0) {
    fail(`${label} missing required entries: ${missing.join(', ')}`);
  }
}

function validatePackageScripts(packagePath, requiredScripts) {
  const packageJson = readJson(packagePath);
  assertIncludesAll(`${packagePath} scripts`, Object.keys(packageJson.scripts ?? {}), requiredScripts);
}

function validateMobileAdapter(adapterId, adapters) {
  const adapter = adapters[adapterId];
  if (!adapter) {
    fail(`Missing toolchain adapter ${adapterId}`);
  }
  if (adapter.status !== 'planned' || adapter.readiness !== 'declared-only') {
    fail(`${adapterId} must remain declared-only until real commands, outputs, and tests exist`);
  }
  if (adapter.safeForDefault !== false || adapter.lifecycleEnabled !== false) {
    fail(`${adapterId} must not be safe-by-default or lifecycle-enabled`);
  }
  if (adapter.executionImplemented !== false || adapter.outputValidationImplemented !== false) {
    fail(`${adapterId} must not claim execution/output validation before implementation`);
  }
}

function validateGatePack(gateId, gatePath) {
  if (!pathExists(gatePath)) {
    fail(`FlashIt gate pack ${gateId} is missing at ${gatePath}`);
  }

  const gatePack = readYaml(gatePath);
  if (gatePack.productId !== PRODUCT_ID) {
    fail(`${gatePath} must declare productId: flashit`);
  }
  if (gatePack.gateId !== gateId) {
    fail(`${gatePath} must declare gateId: ${gateId}`);
  }
  if (gatePack.executionMode !== 'declarative-only') {
    fail(`${gatePath} must stay declarative-only until mobile gates are executable`);
  }
  if ('engineRef' in gatePack || 'genericGateEngine' in gatePack) {
    fail(`${gatePath} must not define a generic lifecycle gate engine`);
  }
  for (const evidenceRef of asArray(gatePack.requiredEvidenceRefs)) {
    if (!pathExists(evidenceRef)) {
      fail(`${gatePath} references missing evidence file ${evidenceRef}`);
    }
  }
}

function validateArtifactManifest(relativePath, expectedPackaging, expectedAdapter) {
  if (!pathExists(relativePath)) {
    fail(`FlashIt mobile artifact manifest missing at ${relativePath}`);
  }
  const manifest = readYaml(relativePath);
  if (manifest.productId !== PRODUCT_ID || manifest.packaging !== expectedPackaging || manifest.adapter !== expectedAdapter) {
    fail(`${relativePath} must declare FlashIt ${expectedPackaging} artifact contract for ${expectedAdapter}`);
  }
  if (manifest.status !== 'contract-only') {
    fail(`${relativePath} must remain contract-only until mobile adapters are executable`);
  }
  for (const evidenceRef of asArray(manifest.evidenceRefs)) {
    if (!pathExists(evidenceRef)) {
      fail(`${relativePath} references missing evidence file ${evidenceRef}`);
    }
  }
}

function main() {
  const registry = readJson('config/canonical-product-registry.json').registry;
  const flashit = registry[PRODUCT_ID];
  if (!flashit) {
    fail('FlashIt missing from canonical product registry');
  }
  if (flashit.lifecycleExecutionAllowed !== false || flashit.lifecycle?.enabled === true || flashit.lifecycleStatus === 'enabled') {
    fail('FlashIt lifecycle must remain disabled until mobile manifests/gates are executable');
  }

  const adapters = readJson('config/toolchain-adapter-registry.json').adapters;
  validateMobileAdapter('xcode-ios', adapters);
  validateMobileAdapter('gradle-android', adapters);

  const kernelProduct = readYaml('products/flashit/kernel-product.yaml');
  if (kernelProduct.executionEnabled !== false) {
    fail('products/flashit/kernel-product.yaml must set executionEnabled: false');
  }
  assertIncludesAll('FlashIt kernel readiness gates', kernelProduct.readiness?.requiredGates, REQUIRED_GATES);
  assertIncludesAll('FlashIt registry readiness gates', flashit.lifecycleReadiness?.requiredGates, REQUIRED_GATES);
  if (kernelProduct.surfaces?.['backend-api']?.adapter !== 'pnpm-node-api') {
    fail('FlashIt backend-api lifecycle surface must use pnpm-node-api');
  }

  const readinessEvidencePath = 'products/flashit/lifecycle/readiness-evidence.yaml';
  const productLocalEvidenceRefs = [
    readinessEvidencePath,
    'products/flashit/lifecycle/artifact-manifests/mobile-ipa-artifact-manifest.yaml',
    'products/flashit/lifecycle/artifact-manifests/mobile-aab-artifact-manifest.yaml',
    ...REQUIRED_GATES.map((gateId) => `products/flashit/lifecycle/gate-packs/${gateId}.yaml`),
  ];
  assertIncludesAll('FlashIt registry evidence refs', flashit.lifecycleReadiness?.evidenceRefs, productLocalEvidenceRefs);
  assertIncludesAll('FlashIt kernel evidence refs', kernelProduct.readiness?.evidenceRefs, productLocalEvidenceRefs);

  const readiness = readYaml(readinessEvidencePath);
  if (readiness.productId !== PRODUCT_ID || readiness.lifecycleExecutionAllowed !== false) {
    fail('FlashIt readiness evidence must declare productId: flashit and lifecycleExecutionAllowed: false');
  }
  assertIncludesAll('FlashIt readiness validation commands', readiness.validationCommands, REQUIRED_VALIDATION_COMMANDS);
  for (const packageEntry of asArray(readiness.workspaceGraph?.requiredPackages)) {
    validatePackageScripts(packageEntry.packagePath, asArray(packageEntry.requiredScripts));
  }

  validateArtifactManifest('products/flashit/lifecycle/artifact-manifests/mobile-ipa-artifact-manifest.yaml', 'ipa', 'xcode-ios');
  validateArtifactManifest('products/flashit/lifecycle/artifact-manifests/mobile-aab-artifact-manifest.yaml', 'aab', 'gradle-android');

  const gatePackRefs = new Map(asArray(readiness.gatePacks).map((gatePack) => [gatePack.gateId, gatePack.path]));
  for (const gateId of REQUIRED_GATES) {
    const gatePath = kernelProduct.gates?.gatePacks?.[gateId] ?? gatePackRefs.get(gateId);
    validateGatePack(gateId, gatePath);
  }

  console.log('FlashIt lifecycle readiness checks passed.');
}

try {
  main();
} catch (error) {
  console.error(error instanceof Error ? error.message : String(error));
  process.exit(1);
}
