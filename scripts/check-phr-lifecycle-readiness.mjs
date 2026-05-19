#!/usr/bin/env node

import { existsSync, readFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import YAML from 'yaml';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '..');

const PRODUCT_ID = 'phr';
const REQUIRED_GATES = [
  'consent',
  'pii-classification',
  'audit-evidence',
  'fhir-contract-validation',
  'tenant-data-sovereignty',
];
const REQUIRED_VALIDATION_COMMANDS = [
  './gradlew :products:phr:build',
  './gradlew :products:phr:launcher:build',
  './gradlew :products:phr:domains:healthcare:build',
  'pnpm --filter ./products/phr/apps/web type-check',
  'pnpm --filter ./products/phr/apps/web test',
  'pnpm --filter ./products/phr/apps/web build',
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

function validateGatePack(gateId, gatePath) {
  if (!pathExists(gatePath)) {
    fail(`PHR gate pack ${gateId} is missing at ${gatePath}`);
  }

  const gatePack = readYaml(gatePath);
  if (gatePack.productId !== PRODUCT_ID) {
    fail(`${gatePath} must declare productId: phr`);
  }
  if (gatePack.gateId !== gateId) {
    fail(`${gatePath} must declare gateId: ${gateId}`);
  }
  if (gatePack.executionMode !== 'evidence-backed') {
    fail(`${gatePath} must declare executionMode: evidence-backed`);
  }
  if (!['active', 'ready'].includes(gatePack.status)) {
    fail(`${gatePath} must declare status: active or ready`);
  }
  if (!Array.isArray(gatePack.requiredEvidenceRefs) || gatePack.requiredEvidenceRefs.length === 0) {
    fail(`${gatePath} must declare requiredEvidenceRefs`);
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

function validateReadinessEvidence(readiness) {
  if (readiness.productId !== PRODUCT_ID) {
    fail('PHR readiness evidence must declare productId: phr');
  }
  if (readiness.status !== 'enabled') {
    fail('PHR readiness evidence must declare status: enabled');
  }
  if (readiness.lifecycleExecutionAllowed !== true) {
    fail('PHR readiness evidence must declare lifecycleExecutionAllowed: true');
  }

  const modules = asArray(readiness.moduleGraph?.requiredModules);
  assertIncludesAll('PHR readiness modules', modules.map((entry) => entry.module), [
    'backend-api',
    'launcher',
    'healthcare-domain',
    'web',
  ]);

  for (const moduleEntry of modules) {
    if (moduleEntry.buildFile && !pathExists(moduleEntry.buildFile)) {
      fail(`PHR readiness module ${moduleEntry.module} references missing build file ${moduleEntry.buildFile}`);
    }
    if (moduleEntry.packagePath && !pathExists(moduleEntry.packagePath)) {
      fail(`PHR readiness module ${moduleEntry.module} references missing package file ${moduleEntry.packagePath}`);
    }
  }

  const webPackage = readJson('products/phr/apps/web/package.json');
  assertIncludesAll('PHR web package scripts', Object.keys(webPackage.scripts ?? {}), ['type-check', 'test', 'build']);
  assertIncludesAll('PHR readiness validation commands', readiness.validationCommands, REQUIRED_VALIDATION_COMMANDS);
}

function main() {
  const registry = readJson('config/canonical-product-registry.json').registry;
  const phr = registry[PRODUCT_ID];
  if (!phr) {
    fail('PHR missing from canonical product registry');
  }
  if (phr.kind !== 'business-product') {
    fail('PHR must remain a business-product, not a platform-provider');
  }
  if (phr.lifecycleExecutionAllowed !== true || phr.lifecycle?.enabled !== true || phr.lifecycleStatus !== 'enabled') {
    fail('PHR lifecycle must be enabled as a regulated executable pilot');
  }

  const kernelProduct = readYaml('products/phr/kernel-product.yaml');
  if (kernelProduct.executionEnabled !== true || kernelProduct.status !== 'enabled') {
    fail('products/phr/kernel-product.yaml must set status: enabled and executionEnabled: true');
  }
  assertIncludesAll('PHR kernel readiness gates', kernelProduct.readiness?.requiredGates, REQUIRED_GATES);
  assertIncludesAll('PHR registry readiness gates', phr.lifecycleReadiness?.requiredGates, REQUIRED_GATES);

  const readinessEvidencePath = 'products/phr/lifecycle/readiness-evidence.yaml';
  const productLocalEvidenceRefs = [
    readinessEvidencePath,
    ...REQUIRED_GATES.map((gateId) => `products/phr/lifecycle/gate-packs/${gateId}.yaml`),
  ];
  assertIncludesAll('PHR registry evidence refs', phr.lifecycleReadiness?.evidenceRefs, productLocalEvidenceRefs);
  assertIncludesAll('PHR kernel evidence refs', kernelProduct.readiness?.evidenceRefs, productLocalEvidenceRefs);

  const readiness = readYaml(readinessEvidencePath);
  validateReadinessEvidence(readiness);

  const gatePackRefs = new Map(asArray(readiness.gatePacks).map((gatePack) => [gatePack.gateId, gatePack.path]));
  for (const gateId of REQUIRED_GATES) {
    const gatePath = kernelProduct.gates?.gatePacks?.[gateId] ?? gatePackRefs.get(gateId);
    validateGatePack(gateId, gatePath);
    const readinessGatePack = asArray(readiness.gatePacks).find((gatePack) => gatePack.gateId === gateId);
    if (readinessGatePack?.executionMode !== 'evidence-backed') {
      fail(`PHR readiness evidence gate ${gateId} must declare executionMode: evidence-backed`);
    }
  }

  console.log('PHR lifecycle readiness checks passed.');
}

try {
  main();
} catch (error) {
  console.error(error instanceof Error ? error.message : String(error));
  process.exit(1);
}
