#!/usr/bin/env node

import { existsSync, readFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import YAML from 'yaml';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '..');

const FINANCE_ID = 'finance';
const REQUIRED_GATES = [
  'regulatory-compliance',
  'risk-controls',
  'promotion-approval',
  'multi-module-build',
];
const REQUIRED_SURFACES = ['backend-api', 'operator', 'portal', 'sdk'];
const REQUIRED_VALIDATION_COMMANDS = [
  './gradlew :products:finance:build',
  './gradlew :products:finance:platform-sdk:build',
  './gradlew :products:finance:operator-workflows:build',
  './gradlew :products:finance:regulator-portal:build',
  './gradlew :products:finance:integration-testing:test',
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

function gradleModuleToBuildFile(modulePath) {
  return `${modulePath.replace(/^:/, '').replaceAll(':', '/')}/build.gradle.kts`;
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

function validateRootDomainScope() {
  const buildText = readFileSync(path.join(repoRoot, 'products/finance/build.gradle.kts'), 'utf8');
  const compileScopedPattern = /^\s*(?:api|implementation)\(project\("(:products:finance:domains:[^"]+)"\)\)/gm;
  const runtimeScopedPattern = /^\s*runtimeOnly\(project\("(:products:finance:domains:[^"]+)"\)\)/gm;
  const compileScopedDomains = [...buildText.matchAll(compileScopedPattern)].map((match) => match[1]);
  const runtimeScopedDomains = [...buildText.matchAll(runtimeScopedPattern)].map((match) => match[1]);

  if (compileScopedDomains.length > 0) {
    fail(`Finance root must not compile-link domain modules: ${compileScopedDomains.join(', ')}`);
  }
  if (runtimeScopedDomains.length === 0) {
    fail('Finance root must compose domain modules with runtimeOnly dependencies');
  }
  return runtimeScopedDomains;
}

function validateGatePack(gateId, gatePath) {
  if (!pathExists(gatePath)) {
    fail(`Finance gate pack ${gateId} is missing at ${gatePath}`);
  }

  const gatePack = readYaml(gatePath);
  if (gatePack.productId !== FINANCE_ID) {
    fail(`${gatePath} must declare productId: finance`);
  }
  if (gatePack.gateId !== gateId) {
    fail(`${gatePath} must declare gateId: ${gateId}`);
  }
  if (gatePack.executionMode !== 'declarative-only') {
    fail(`${gatePath} must stay declarative-only until executable lifecycle gates exist`);
  }
  if ('engineRef' in gatePack || 'genericGateEngine' in gatePack) {
    fail(`${gatePath} must not define a generic gate engine`);
  }
  for (const evidenceRef of asArray(gatePack.requiredEvidenceRefs)) {
    if (!pathExists(evidenceRef)) {
      fail(`${gatePath} references missing evidence file ${evidenceRef}`);
    }
  }
}

function validateReadinessEvidence(readiness, registryProduct, kernelProduct, runtimeOnlyDomainModules) {
  if (readiness.productId !== FINANCE_ID) {
    fail('Finance readiness evidence must declare productId: finance');
  }
  if (readiness.lifecycleExecutionAllowed !== false) {
    fail('Finance readiness evidence must keep lifecycleExecutionAllowed: false');
  }

  const requiredSurfaceModules = asArray(readiness.moduleGraph?.requiredSurfaceModules);
  const surfaces = requiredSurfaceModules.map((surface) => surface.surface);
  assertIncludesAll('Finance readiness surface modules', surfaces, REQUIRED_SURFACES);

  for (const surface of requiredSurfaceModules) {
    if (!registryProduct.gradleModules.includes(surface.gradleModule)) {
      fail(`Finance readiness surface ${surface.surface} references unregistered module ${surface.gradleModule}`);
    }
    if (!pathExists(surface.buildFile)) {
      fail(`Finance readiness surface ${surface.surface} references missing build file ${surface.buildFile}`);
    }
  }

  const sdkSurface = requiredSurfaceModules.find((surface) => surface.surface === 'sdk');
  if (!sdkSurface || sdkSurface.packaging !== 'jar' || sdkSurface.adapter !== 'gradle-java-library') {
    fail('Finance SDK readiness must be recorded as a jar-packaged gradle-java-library surface');
  }

  const declaredRuntimeOnlyDomains = asArray(readiness.moduleGraph?.runtimeOnlyDomainModules);
  const missingRuntimeDomains = declaredRuntimeOnlyDomains.filter((modulePath) => !runtimeOnlyDomainModules.includes(modulePath));
  if (missingRuntimeDomains.length > 0) {
    fail(`Finance readiness evidence lists domains not composed runtimeOnly by root: ${missingRuntimeDomains.join(', ')}`);
  }

  assertIncludesAll('Finance readiness validation commands', readiness.validationCommands, REQUIRED_VALIDATION_COMMANDS);
  assertIncludesAll('Finance kernel build surfaces', kernelProduct.phases?.build?.defaultSurfaces, ['portal', 'sdk']);
}

function main() {
  const registry = readJson('config/canonical-product-registry.json').registry;
  const finance = registry[FINANCE_ID];
  if (!finance) {
    fail('Finance missing from canonical product registry');
  }
  if (finance.lifecycleExecutionAllowed !== false || finance.lifecycle?.enabled === true || finance.lifecycleStatus === 'enabled') {
    fail('Finance lifecycle must remain disabled until required gates and adapters are executable');
  }

  const kernelProduct = readYaml('products/finance/kernel-product.yaml');
  if (kernelProduct.executionEnabled !== false) {
    fail('products/finance/kernel-product.yaml must set executionEnabled: false');
  }
  assertIncludesAll('Finance kernel readiness gates', kernelProduct.readiness?.requiredGates, REQUIRED_GATES);

  const evidenceRefs = asArray(finance.lifecycleReadiness?.evidenceRefs);
  const readinessEvidencePath = 'products/finance/lifecycle/readiness-evidence.yaml';
  const productLocalLifecycleEvidenceRefs = [
    readinessEvidencePath,
    ...REQUIRED_GATES.map((gateId) => `products/finance/lifecycle/gate-packs/${gateId}.yaml`),
  ];
  assertIncludesAll('Finance registry evidence refs', evidenceRefs, productLocalLifecycleEvidenceRefs);
  assertIncludesAll('Finance kernel evidence refs', kernelProduct.readiness?.evidenceRefs, productLocalLifecycleEvidenceRefs);

  const runtimeOnlyDomainModules = validateRootDomainScope();
  const readiness = readYaml(readinessEvidencePath);
  validateReadinessEvidence(readiness, finance, kernelProduct, runtimeOnlyDomainModules);

  const gatePackRefs = new Map(asArray(readiness.gatePacks).map((gatePack) => [gatePack.gateId, gatePack.path]));
  for (const gateId of REQUIRED_GATES) {
    const gatePath = kernelProduct.gates?.gatePacks?.[gateId] ?? gatePackRefs.get(gateId);
    validateGatePack(gateId, gatePath);
  }

  console.log('Finance lifecycle readiness checks passed.');
}

try {
  main();
} catch (error) {
  console.error(error instanceof Error ? error.message : String(error));
  process.exit(1);
}
