#!/usr/bin/env node

import { existsSync, readFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import YAML from 'yaml';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '..');

const PRODUCT_ID = 'yappc';
const REQUIRED_GATES = [
  'product-unit-intent-export',
  'artifact-intelligence-boundary',
  'creator-kernel-lifecycle-separation',
];
const REQUIRED_EVIDENCE_TYPES = [
  'ArtifactGraphSummary',
  'ProductShapeEvidence',
  'DependencyGraphEvidence',
  'ResidualIslandReport',
  'RiskHotspotReport',
  'GeneratedChangeSetSummary',
];

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

function assertRefsExist(label, refs) {
  for (const ref of refs) {
    if (!pathExists(ref)) {
      fail(`${label} references missing file ${ref}`);
    }
  }
}

function validateNoCanonicalMutation() {
  const scannedFiles = [
    'products/yappc/scripts/generate-product-shape-capability-matrix.mjs',
    'products/yappc/scripts/check-lifecycle-registry-config-drift.mjs',
    'products/yappc/frontend/web/src/services/canvas/commands/ProductUnitIntentExportService.ts',
  ];
  for (const file of scannedFiles) {
    const source = readText(file);
    if (/writeFileSync\([^)]*canonical-product-registry|writeFileSync\([^)]*settings-gradle-includes|fs\.writeFile\([^)]*canonical-product-registry/s.test(source)) {
      fail(`${file} must not mutate canonical registry or generated Gradle includes`);
    }
  }
}

function main() {
  const registry = readJson('config/canonical-product-registry.json').registry;
  const yappc = registry[PRODUCT_ID];
  if (!yappc) {
    fail('YAPPC missing from canonical product registry');
  }
  if (yappc.kind !== 'platform-provider') {
    fail('YAPPC must remain registered as a platform-provider');
  }
  if (yappc.lifecycleExecutionAllowed !== false || yappc.lifecycle?.enabled === true) {
    fail('YAPPC must not be enabled as an ordinary Kernel lifecycle product');
  }
  assertIncludesAll('YAPPC readiness gates', yappc.lifecycleReadiness?.requiredGates, REQUIRED_GATES);

  const readinessPath = 'products/yappc/lifecycle/readiness-evidence.yaml';
  assertIncludesAll('YAPPC registry evidence refs', yappc.lifecycleReadiness?.evidenceRefs, [
    readinessPath,
    'platform/typescript/kernel-product-contracts/src/artifact-intelligence',
  ]);

  const readiness = readYaml(readinessPath);
  if (readiness.productId !== PRODUCT_ID || readiness.lifecycleExecutionAllowed !== false) {
    fail('YAPPC readiness evidence must declare productId: yappc and lifecycleExecutionAllowed: false');
  }
  if (readiness.providerMode?.creatorLifecycleDistinctFromKernelLifecycle !== true) {
    fail('YAPPC readiness must explicitly separate Creator Lifecycle from Kernel Product Lifecycle');
  }
  if (readiness.providerMode?.kernelConsumesReferencesOnly !== true) {
    fail('YAPPC readiness must keep Kernel consumption limited to references/evidence');
  }

  assertIncludesAll(
    'YAPPC artifact intelligence evidence types',
    readiness.contracts?.artifactIntelligence?.requiredEvidenceTypes,
    REQUIRED_EVIDENCE_TYPES,
  );
  assertRefsExist('YAPPC ProductUnitIntent exporter', readiness.contracts?.productUnitIntentExporter?.implementationRefs);
  assertRefsExist('YAPPC artifact intelligence providers', readiness.contracts?.artifactIntelligence?.providerRefs);
  assertRefsExist('YAPPC Data Cloud publication evidence', readiness.contracts?.dataCloudPublication?.evidenceRefs);
  assertRefsExist('YAPPC public visualization evidence', readiness.contracts?.visualization?.evidenceRefs);

  const artifactContract = readText('platform/typescript/kernel-product-contracts/src/artifact-intelligence/ArtifactIntelligence.ts');
  for (const evidenceType of REQUIRED_EVIDENCE_TYPES) {
    if (!artifactContract.includes(`${evidenceType}Schema`)) {
      fail(`Artifact intelligence contract missing ${evidenceType}Schema`);
    }
  }

  validateNoCanonicalMutation();
  console.log('YAPPC platform-provider readiness checks passed.');
}

try {
  main();
} catch (error) {
  console.error(error instanceof Error ? error.message : String(error));
  process.exit(1);
}
