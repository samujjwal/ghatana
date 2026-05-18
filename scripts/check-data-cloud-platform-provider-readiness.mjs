#!/usr/bin/env node

import { existsSync, readFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import YAML from 'yaml';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '..');

const PRODUCT_ID = 'data-cloud';
const READINESS_PATH = 'products/data-cloud/lifecycle/readiness-evidence.yaml';
const REQUIRED_GATES = [
  'bootstrap-build-without-platform-providers',
  'platform-provider-health',
  'runtime-truth-provider',
];
const REQUIRED_RUNTIME_SIGNALS = [
  'lifecycle-events',
  'artifact-refs',
  'health-snapshots',
  'provenance-refs',
  'tenant-scoping',
];
const PROHIBITED_BUSINESS_PRODUCT_ROOTS = [
  'products/digital-marketing',
  'products/finance',
  'products/phr',
  'products/flashit',
];

function fail(message) {
  throw new Error(message);
}

function readJson(relativePath) {
  return JSON.parse(readFileSync(path.join(repoRoot, relativePath), 'utf8'));
}

function readYaml(relativePath) {
  return YAML.parse(readFileSync(path.join(repoRoot, relativePath), 'utf8'));
}

function readText(relativePath) {
  return readFileSync(path.join(repoRoot, relativePath), 'utf8');
}

function pathExists(relativePath) {
  return existsSync(path.join(repoRoot, relativePath));
}

function asArray(value) {
  return Array.isArray(value) ? value : [];
}

function assertIncludesAll(label, actual, expected) {
  const actualSet = new Set(asArray(actual));
  const missing = expected.filter((item) => !actualSet.has(item));
  if (missing.length > 0) {
    fail(`${label} missing required entries: ${missing.join(', ')}`);
  }
}

function assertRefsExist(label, refs) {
  for (const ref of asArray(refs)) {
    if (!pathExists(ref)) {
      fail(`${label} references missing file ${ref}`);
    }
  }
}

function assertDataCloudNeutrality(readiness) {
  const catalogServicePath =
    'products/data-cloud/planes/action/engine/src/main/java/com/ghatana/aep/catalog/AepCentralCatalogService.java';
  const source = readText(catalogServicePath);
  const defaultRootsMatch = source.match(/private static List<Path> defaultRoots[\s\S]*?\n    \}/);
  if (!defaultRootsMatch) {
    fail('AepCentralCatalogService defaultRoots block was not found');
  }
  for (const prohibitedRoot of PROHIBITED_BUSINESS_PRODUCT_ROOTS) {
    if (defaultRootsMatch[0].includes(prohibitedRoot)) {
      fail(`Data Cloud core fallback catalog roots must not hard-code ${prohibitedRoot}`);
    }
  }
  assertIncludesAll(
    'Data Cloud prohibited product roots',
    readiness.productNeutrality?.prohibitedBusinessProductRoots,
    PROHIBITED_BUSINESS_PRODUCT_ROOTS,
  );
}

function main() {
  const registry = readJson('config/canonical-product-registry.json').registry;
  const dataCloud = registry[PRODUCT_ID];
  if (!dataCloud) {
    fail('Data Cloud missing from canonical product registry');
  }
  if (dataCloud.kind !== 'platform-provider') {
    fail('Data Cloud must remain registered as a platform-provider');
  }
  if (dataCloud.lifecycleExecutionAllowed !== false || dataCloud.lifecycle?.enabled === true) {
    fail('Data Cloud must not be enabled as an ordinary Kernel lifecycle product');
  }
  assertIncludesAll('Data Cloud readiness gates', dataCloud.lifecycleReadiness?.requiredGates, REQUIRED_GATES);
  assertIncludesAll('Data Cloud registry evidence refs', dataCloud.lifecycleReadiness?.evidenceRefs, [READINESS_PATH]);

  const readiness = readYaml(READINESS_PATH);
  if (readiness.productId !== PRODUCT_ID || readiness.lifecycleExecutionAllowed !== false) {
    fail('Data Cloud readiness evidence must declare productId: data-cloud and lifecycleExecutionAllowed: false');
  }
  if (readiness.providerMode?.requiresPlatformProviderMode !== true) {
    fail('Data Cloud readiness must require platform-provider mode');
  }
  if (readiness.providerMode?.bootstrapSeparationRequired !== true) {
    fail('Data Cloud readiness must require bootstrap separation');
  }
  if (readiness.providerMode?.runtimeTruthProviderRequired !== true) {
    fail('Data Cloud readiness must require runtime truth providers');
  }

  assertIncludesAll('Data Cloud runtime truth signals', readiness.runtimeTruthProvider?.requiredSignals, REQUIRED_RUNTIME_SIGNALS);
  assertRefsExist('Data Cloud bootstrap separation evidence', readiness.bootstrapSeparation?.evidenceRefs);
  assertRefsExist('Data Cloud runtime truth providers', readiness.runtimeTruthProvider?.providerRefs);
  assertRefsExist('Data Cloud runtime truth tests', readiness.runtimeTruthProvider?.testRefs);
  assertRefsExist('Data Cloud neutrality evidence', readiness.productNeutrality?.evidenceRefs);
  assertRefsExist('Data Cloud action plane evidence', readiness.actionPlane?.evidenceRefs);

  if (readiness.actionPlane?.governedRuntime !== true ||
      readiness.actionPlane?.policyRequired !== true ||
      readiness.actionPlane?.identityRequired !== true ||
      readiness.actionPlane?.securityRequired !== true ||
      readiness.actionPlane?.complianceRequired !== true ||
      readiness.actionPlane?.eventBridgeRequired !== true) {
    fail('Data Cloud Action Plane readiness must require governed runtime, policy, identity, security, compliance, and event bridge');
  }
  if (readiness.conformance?.updateFieldsOnlyWithExecutableProof !== true) {
    fail('Data Cloud conformance fields may only be updated with executable proof');
  }

  assertDataCloudNeutrality(readiness);
  console.log('Data Cloud platform-provider readiness checks passed.');
}

try {
  main();
} catch (error) {
  console.error(error instanceof Error ? error.message : String(error));
  process.exit(1);
}
