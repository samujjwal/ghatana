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
const REQUIRED_GATE_PHASES = {
  consent: ['validate', 'build', 'deploy'],
  'pii-classification': ['validate', 'build'],
  'audit-evidence': ['validate', 'build'],
  'fhir-contract-validation': ['validate', 'build'],
  'tenant-data-sovereignty': ['validate', 'build', 'deploy'],
};
const REQUIRED_VALIDATION_COMMANDS = [
  './gradlew :products:phr:build',
  './gradlew :products:phr:launcher:build',
  './gradlew :products:phr:domains:healthcare:build',
  'pnpm --filter ./products/phr/apps/web type-check',
  'pnpm --filter ./products/phr/apps/web test',
  'pnpm --filter ./products/phr/apps/web build',
];
const REQUIRED_ROLLBACK_ENABLEMENT_ITEMS = [
  'stable-deployment-manifest-history',
  'previous-artifact-selection-policy',
  'healthcare-post-rollback-verification-gates',
  'rollback-approval-contract',
];
const REQUIRED_ROLLBACK_EVIDENCE_REFS = [
  'products/phr/lifecycle/rollback/rollback-readiness-evidence.yaml',
  'products/phr/lifecycle/rollback/stable-deployment-manifest-history-policy.yaml',
  'products/phr/lifecycle/rollback/previous-artifact-selection-policy.yaml',
  'products/phr/lifecycle/rollback/healthcare-post-rollback-verification-gates.yaml',
  'products/phr/lifecycle/rollback/rollback-approval-contract.yaml',
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
  if (!gatePack.schemaVersion) {
    fail(`${gatePath} must declare schemaVersion`);
  }
  if (!gatePack.owner) {
    fail(`${gatePath} must declare owner`);
  }
  if (!gatePack.title || !gatePack.description) {
    fail(`${gatePath} must declare title and description`);
  }
  if (!['active', 'ready'].includes(gatePack.status)) {
    fail(`${gatePath} must declare status: active or ready`);
  }
  if (!Array.isArray(gatePack.requiredEvidenceRefs) || gatePack.requiredEvidenceRefs.length === 0) {
    fail(`${gatePath} must declare requiredEvidenceRefs`);
  }
  if (!Array.isArray(gatePack.blockingReasonCodes) || gatePack.blockingReasonCodes.length < 2) {
    fail(`${gatePath} must declare blockingReasonCodes for fail-closed evidence reporting`);
  }
  if (!Array.isArray(gatePack.validationCommands) || gatePack.validationCommands.length === 0) {
    fail(`${gatePath} must declare validationCommands`);
  }
  assertIncludesAll(`${gatePath} lifecycle phases`, gatePack.lifecyclePhases, REQUIRED_GATE_PHASES[gateId] ?? []);
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

function validateRollbackReadiness(label, rollbackReadiness) {
  if (!rollbackReadiness || typeof rollbackReadiness !== 'object') {
    fail(`${label} must classify PHR rollback readiness while rollback is absent`);
  }
  if (rollbackReadiness.status !== 'target-partial') {
    fail(`${label}.status must be target-partial`);
  }
  if (rollbackReadiness.classification !== 'target/partial') {
    fail(`${label}.classification must be target/partial`);
  }
  if (rollbackReadiness.reasonCode !== 'phr-rollback-after-stable-deploy-verify') {
    fail(`${label}.reasonCode must be phr-rollback-after-stable-deploy-verify`);
  }
  assertIncludesAll(
    `${label}.requiredBeforeEnablement`,
    rollbackReadiness.requiredBeforeEnablement,
    REQUIRED_ROLLBACK_ENABLEMENT_ITEMS,
  );
  assertIncludesAll(`${label}.evidenceRefs`, rollbackReadiness.evidenceRefs, REQUIRED_ROLLBACK_EVIDENCE_REFS);
}

function validateRollbackEvidenceFile(relativePath, expectedRequirementId) {
  if (!pathExists(relativePath)) {
    fail(`PHR rollback evidence file is missing: ${relativePath}`);
  }

  const evidence = readYaml(relativePath);
  if (evidence.productId !== PRODUCT_ID) {
    fail(`${relativePath} must declare productId: phr`);
  }
  if (evidence.requirementId !== expectedRequirementId) {
    fail(`${relativePath} must declare requirementId: ${expectedRequirementId}`);
  }
  if (!evidence.schemaVersion) {
    fail(`${relativePath} must declare schemaVersion`);
  }
  if (!evidence.title || !evidence.description) {
    fail(`${relativePath} must declare title and description`);
  }
  if (!evidence.status) {
    fail(`${relativePath} must declare status`);
  }
  for (const evidenceRef of asArray(evidence.evidenceRefs)) {
    if (!pathExists(evidenceRef)) {
      fail(`${relativePath} references missing evidence file ${evidenceRef}`);
    }
  }
}

function validateRollbackEvidence() {
  const readinessPath = 'products/phr/lifecycle/rollback/rollback-readiness-evidence.yaml';
  if (!pathExists(readinessPath)) {
    fail(`PHR rollback readiness evidence index is missing: ${readinessPath}`);
  }

  const readiness = readYaml(readinessPath);
  if (readiness.productId !== PRODUCT_ID) {
    fail(`${readinessPath} must declare productId: phr`);
  }
  if (readiness.status !== 'target-partial' || readiness.classification !== 'target/partial') {
    fail(`${readinessPath} must preserve target-partial classification until stable deploy history exists`);
  }
  if (readiness.promotionBlocked !== true) {
    fail(`${readinessPath} must declare promotionBlocked: true`);
  }
  assertIncludesAll(`${readinessPath}.evidenceRefs`, readiness.evidenceRefs, REQUIRED_ROLLBACK_EVIDENCE_REFS.filter((ref) => ref !== readinessPath));

  const requirements = new Map(asArray(readiness.requirements).map((requirement) => [requirement.requirementId, requirement]));
  for (const requirementId of REQUIRED_ROLLBACK_ENABLEMENT_ITEMS) {
    const requirement = requirements.get(requirementId);
    if (!requirement?.evidenceRef) {
      fail(`${readinessPath} missing requirement evidenceRef for ${requirementId}`);
    }
    validateRollbackEvidenceFile(requirement.evidenceRef, requirementId);
  }

  const stablePolicy = readYaml('products/phr/lifecycle/rollback/stable-deployment-manifest-history-policy.yaml');
  if (stablePolicy.status !== 'blocked') {
    fail('PHR stable deployment manifest history policy must remain blocked until real deploy/verify evidence exists');
  }
  assertIncludesAll(
    'PHR stable deployment manifest history required manifests',
    stablePolicy.requiredManifestTypes,
    ['artifact-manifest', 'deployment-manifest', 'verify-health-report', 'lifecycle-health-snapshot'],
  );

  const previousArtifactPolicy = readYaml('products/phr/lifecycle/rollback/previous-artifact-selection-policy.yaml');
  if (previousArtifactPolicy.strategy !== 'previous-artifact' || previousArtifactPolicy.failureMode !== 'block-rollback-plan') {
    fail('PHR previous artifact policy must use previous-artifact strategy and block invalid rollback plans');
  }
  if (previousArtifactPolicy.requiresStableDeploymentHistory !== true || previousArtifactPolicy.disallowUnverifiedArtifact !== true) {
    fail('PHR previous artifact policy must require stable history and disallow unverified artifacts');
  }

  const healthcareGates = readYaml('products/phr/lifecycle/rollback/healthcare-post-rollback-verification-gates.yaml');
  assertIncludesAll('PHR rollback healthcare verification gates', healthcareGates.requiredGates, REQUIRED_GATES);
  for (const gateId of REQUIRED_GATES) {
    if (!healthcareGates.requiredGateEvidenceRefs?.[gateId]) {
      fail(`PHR rollback healthcare verification gates missing evidence refs for ${gateId}`);
    }
  }

  const approval = readYaml('products/phr/lifecycle/rollback/rollback-approval-contract.yaml');
  if (approval.approvalRequired !== true || approval.approvalMode !== 'explicit-recorded-approval') {
    fail('PHR rollback approval contract must require explicit recorded approval');
  }
  assertIncludesAll('PHR rollback approval fields', approval.requiredApprovalFields, [
    'approvalGateRef',
    'approverId',
    'approverRole',
    'approvedAt',
    'reasonCode',
    'rollbackTargetArtifact',
    'healthcareRiskAcknowledgement',
  ]);
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
  validateRollbackReadiness('PHR kernel rollbackReadiness', kernelProduct.rollbackReadiness);
  validateRollbackReadiness('PHR registry rollbackReadiness', phr.rollbackReadiness);
  validateRollbackEvidence();
  if (kernelProduct.phases?.rollback || kernelProduct.requiredManifests?.rollback) {
    fail('PHR rollback phase/manifests must remain absent until rollbackReadiness is promoted from target-partial');
  }

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
