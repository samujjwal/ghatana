#!/usr/bin/env node

import { execFileSync } from 'node:child_process';
import { existsSync, mkdirSync, readFileSync, readdirSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import YAML from 'yaml';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '..');
const PRODUCT_ID = 'phr';
const REQUIRED_HEALTHCARE_GATES = [
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
const REQUIRED_MANIFEST_PHASES = ['build', 'package', 'deploy', 'verify'];
const REQUIRED_EVIDENCE_CATEGORIES = [
  'baseline',
  'plan',
  'validate',
  'test',
  'build',
  'package',
  'deploy',
  'verify',
  'rollback',
  'gate-results',
  'health',
  'approvals',
  'provenance',
  'product-domain-correctness',
];
const REQUIRED_PHASE_EVIDENCE_FIELDS = [
  'runId',
  'correlationId',
  'productUnitId',
  'phase',
  'providerMode',
  'status',
  'startedAt',
  'completedAt',
  'durationMs',
  'evidenceRefs',
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
const ROLLBACK_READY_LOCAL = {
  status: 'ready-local',
  classification: 'ready/local',
  reasonCode: 'phr-rollback-ready-local-production',
};
const ROLLBACK_TARGET_PARTIAL = {
  status: 'target-partial',
  classification: 'target/partial',
  reasonCode: 'phr-rollback-after-stable-deploy-verify',
};
const REQUIRED_MANIFEST_SCHEMA_VERSIONS = [
  'lifecycle-result',
  'artifact-manifest',
  'lifecycle-health-snapshot',
  'deployment-manifest',
  'verify-health-report',
];
const SMOKE_PHASES = [
  { phase: 'validate', args: ['product', 'validate', PRODUCT_ID, '--dry-run', '--json'] },
  { phase: 'test', args: ['product', 'test', PRODUCT_ID, '--dry-run', '--json'] },
  { phase: 'build', args: ['product', 'build', PRODUCT_ID, '--dry-run', '--json'] },
  { phase: 'package', args: ['product', 'package', PRODUCT_ID, '--dry-run', '--json'] },
  { phase: 'deploy', args: ['product', 'deploy', PRODUCT_ID, '--env', 'local', '--dry-run', '--json'] },
  { phase: 'verify', args: ['product', 'verify', PRODUCT_ID, '--env', 'local', '--dry-run', '--json'] },
];
const KERNEL_ROOTS = [
  'platform-kernel',
  'platform/typescript/kernel-product-contracts',
  'platform/typescript/kernel-lifecycle',
  'platform/typescript/kernel-artifacts',
  'platform/typescript/kernel-deployment',
  'platform/typescript/kernel-release',
  'platform/typescript/kernel-providers',
  'platform/typescript/kernel-toolchains',
];

function readJson(root, relativePath) {
  return JSON.parse(readFileSync(path.join(root, relativePath), 'utf8'));
}

function readYaml(root, relativePath) {
  return YAML.parse(readFileSync(path.join(root, relativePath), 'utf8'));
}

function pathExists(root, relativePath) {
  return existsSync(path.join(root, relativePath));
}

function readText(root, relativePath) {
  return readFileSync(path.join(root, relativePath), 'utf8');
}

function listFiles(root, relativeDirectory) {
  const absoluteDirectory = path.join(root, relativeDirectory);
  if (!existsSync(absoluteDirectory)) {
    return [];
  }

  const files = [];
  const visit = (directory) => {
    for (const entry of readdirSync(directory, { withFileTypes: true })) {
      if (['.git', 'node_modules', 'dist', 'build', '.gradle'].includes(entry.name)) {
        continue;
      }
      const absolutePath = path.join(directory, entry.name);
      if (entry.isDirectory()) {
        visit(absolutePath);
      } else if (entry.isFile()) {
        files.push(path.relative(root, absolutePath).replaceAll('\\', '/'));
      }
    }
  };
  visit(absoluteDirectory);
  return files;
}

function asArray(value) {
  return Array.isArray(value) ? value : [];
}

function parseArgs(argv) {
  const normalizedArgv = argv.filter((arg) => arg !== '--');
  return {
    smoke: normalizedArgv.includes('--smoke'),
    evidencePackDir: readFlagValue(normalizedArgv, '--evidence-pack-dir'),
  };
}

function readFlagValue(argv, flagName) {
  const flagIndex = argv.indexOf(flagName);
  if (flagIndex === -1) {
    return undefined;
  }
  const value = argv[flagIndex + 1];
  if (value === undefined || value.startsWith('--')) {
    throw new Error(`Missing value for option ${flagName}`);
  }
  return value;
}

function includesAll(label, actual, expected, errors) {
  const actualSet = new Set(asArray(actual));
  for (const item of expected) {
    if (!actualSet.has(item)) {
      errors.push(`${label} missing required entry: ${item}`);
    }
  }
}

function requireNonEmptyString(label, value, errors) {
  if (typeof value !== 'string' || value.trim().length === 0) {
    errors.push(`${label} must be a non-empty string`);
  }
}

function validateGatePackShape(gatePath, gateId, gatePack, errors) {
  if (gatePack.productId !== PRODUCT_ID || gatePack.gateId !== gateId) {
    errors.push(`${gatePath} must declare productId: phr and gateId: ${gateId}`);
  }
  requireNonEmptyString(`${gatePath}.schemaVersion`, gatePack.schemaVersion, errors);
  requireNonEmptyString(`${gatePath}.owner`, gatePack.owner, errors);
  requireNonEmptyString(`${gatePath}.title`, gatePack.title, errors);
  requireNonEmptyString(`${gatePath}.description`, gatePack.description, errors);
  requireNonEmptyString(`${gatePath}.executionMode`, gatePack.executionMode, errors);
  if (gatePack.executionMode !== 'evidence-backed') {
    errors.push(`${gatePath}.executionMode must be evidence-backed`);
  }
  if (!Array.isArray(gatePack.requiredEvidenceRefs) || gatePack.requiredEvidenceRefs.length === 0) {
    errors.push(`${gatePath} must declare requiredEvidenceRefs`);
  }
  if (!Array.isArray(gatePack.blockingReasonCodes) || gatePack.blockingReasonCodes.length < 2) {
    errors.push(`${gatePath} must declare pass/fail blockingReasonCodes`);
  }
  if (!Array.isArray(gatePack.validationCommands) || gatePack.validationCommands.length === 0) {
    errors.push(`${gatePath} must declare validationCommands`);
  }
  includesAll(`${gatePath} lifecyclePhases`, gatePack.lifecyclePhases, REQUIRED_GATE_PHASES[gateId] ?? [], errors);
}

function validateRollbackReadiness(label, rollbackReadiness, errors) {
  if (!rollbackReadiness || typeof rollbackReadiness !== 'object') {
    errors.push(`${label} must classify PHR rollback readiness while rollback is absent`);
    return;
  }

  const isReadyLocal =
    rollbackReadiness.status === ROLLBACK_READY_LOCAL.status
    && rollbackReadiness.classification === ROLLBACK_READY_LOCAL.classification
    && rollbackReadiness.reasonCode === ROLLBACK_READY_LOCAL.reasonCode;
  const isTargetPartial =
    rollbackReadiness.status === ROLLBACK_TARGET_PARTIAL.status
    && rollbackReadiness.classification === ROLLBACK_TARGET_PARTIAL.classification
    && rollbackReadiness.reasonCode === ROLLBACK_TARGET_PARTIAL.reasonCode;

  if (!isReadyLocal && !isTargetPartial) {
    errors.push(
      `${label} must be either ${ROLLBACK_READY_LOCAL.status}/${ROLLBACK_READY_LOCAL.classification}/${ROLLBACK_READY_LOCAL.reasonCode} or ${ROLLBACK_TARGET_PARTIAL.status}/${ROLLBACK_TARGET_PARTIAL.classification}/${ROLLBACK_TARGET_PARTIAL.reasonCode}`,
    );
  }
  includesAll(
    `${label}.requiredBeforeEnablement`,
    rollbackReadiness.requiredBeforeEnablement,
    REQUIRED_ROLLBACK_ENABLEMENT_ITEMS,
    errors,
  );
  includesAll(`${label}.evidenceRefs`, rollbackReadiness.evidenceRefs, REQUIRED_ROLLBACK_EVIDENCE_REFS, errors);
}

function validateRollbackEvidenceFile(relativePath, expectedRequirementId, loadYaml, exists, errors) {
  if (!exists(relativePath)) {
    errors.push(`PHR rollback evidence file is missing: ${relativePath}`);
    return;
  }

  const evidence = loadYaml(relativePath);
  if (evidence.productId !== PRODUCT_ID) {
    errors.push(`${relativePath} must declare productId: phr`);
  }
  if (evidence.requirementId !== expectedRequirementId) {
    errors.push(`${relativePath} must declare requirementId: ${expectedRequirementId}`);
  }
  requireNonEmptyString(`${relativePath}.schemaVersion`, evidence.schemaVersion, errors);
  requireNonEmptyString(`${relativePath}.title`, evidence.title, errors);
  requireNonEmptyString(`${relativePath}.description`, evidence.description, errors);
  requireNonEmptyString(`${relativePath}.status`, evidence.status, errors);
  for (const evidenceRef of asArray(evidence.evidenceRefs)) {
    if (!exists(evidenceRef)) {
      errors.push(`${relativePath} references missing evidence file ${evidenceRef}`);
    }
  }
}

function validateRollbackEvidence(loadYaml, exists, errors) {
  const readinessPath = 'products/phr/lifecycle/rollback/rollback-readiness-evidence.yaml';
  if (!exists(readinessPath)) {
    errors.push(`PHR rollback readiness evidence index is missing: ${readinessPath}`);
    return;
  }

  const readiness = loadYaml(readinessPath);
  if (readiness.productId !== PRODUCT_ID) {
    errors.push(`${readinessPath} must declare productId: phr`);
  }

  const readinessIsReady = readiness.status === 'ready' && readiness.classification === 'production-ready';
  const readinessIsTargetPartial = readiness.status === 'target-partial' && readiness.classification === 'target/partial';
  if (!readinessIsReady && !readinessIsTargetPartial) {
    errors.push(`${readinessPath} must declare either ready/production-ready or target-partial/target/partial classification`);
  }
  if (readinessIsReady && readiness.promotionBlocked !== false) {
    errors.push(`${readinessPath} must declare promotionBlocked: false when readiness is production-ready`);
  }
  if (readinessIsTargetPartial && readiness.promotionBlocked !== true) {
    errors.push(`${readinessPath} must declare promotionBlocked: true when readiness is target-partial`);
  }
  includesAll(
    `${readinessPath}.evidenceRefs`,
    readiness.evidenceRefs,
    REQUIRED_ROLLBACK_EVIDENCE_REFS.filter((ref) => ref !== readinessPath),
    errors,
  );

  const requirements = new Map(asArray(readiness.requirements).map((requirement) => [requirement.requirementId, requirement]));
  for (const requirementId of REQUIRED_ROLLBACK_ENABLEMENT_ITEMS) {
    const requirement = requirements.get(requirementId);
    if (!requirement?.evidenceRef) {
      errors.push(`${readinessPath} missing requirement evidenceRef for ${requirementId}`);
      continue;
    }
    validateRollbackEvidenceFile(requirement.evidenceRef, requirementId, loadYaml, exists, errors);
  }

  const stablePolicy = loadYaml('products/phr/lifecycle/rollback/stable-deployment-manifest-history-policy.yaml');
  if (readinessIsReady && stablePolicy.status !== 'ready') {
    errors.push('PHR stable deployment manifest history policy must be ready when rollback readiness evidence is production-ready');
  }
  if (readinessIsTargetPartial && stablePolicy.status !== 'blocked') {
    errors.push('PHR stable deployment manifest history policy must remain blocked until real deploy/verify evidence exists');
  }
  includesAll(
    'PHR stable deployment manifest history required manifests',
    stablePolicy.requiredManifestTypes,
    ['artifact-manifest', 'deployment-manifest', 'verify-health-report', 'lifecycle-health-snapshot'],
    errors,
  );

  const previousArtifactPolicy = loadYaml('products/phr/lifecycle/rollback/previous-artifact-selection-policy.yaml');
  if (previousArtifactPolicy.strategy !== 'previous-artifact' || previousArtifactPolicy.failureMode !== 'block-rollback-plan') {
    errors.push('PHR previous artifact policy must use previous-artifact strategy and block invalid rollback plans');
  }
  if (previousArtifactPolicy.requiresStableDeploymentHistory !== true || previousArtifactPolicy.disallowUnverifiedArtifact !== true) {
    errors.push('PHR previous artifact policy must require stable history and disallow unverified artifacts');
  }

  const healthcareGates = loadYaml('products/phr/lifecycle/rollback/healthcare-post-rollback-verification-gates.yaml');
  includesAll('PHR rollback healthcare verification gates', healthcareGates.requiredGates, REQUIRED_HEALTHCARE_GATES, errors);
  for (const gateId of REQUIRED_HEALTHCARE_GATES) {
    if (!healthcareGates.requiredGateEvidenceRefs?.[gateId]) {
      errors.push(`PHR rollback healthcare verification gates missing evidence refs for ${gateId}`);
    }
  }

  const approval = loadYaml('products/phr/lifecycle/rollback/rollback-approval-contract.yaml');
  if (approval.approvalRequired !== true || approval.approvalMode !== 'explicit-recorded-approval') {
    errors.push('PHR rollback approval contract must require explicit recorded approval');
  }
  includesAll('PHR rollback approval fields', approval.requiredApprovalFields, [
    'approvalGateRef',
    'approverId',
    'approverRole',
    'approvedAt',
    'reasonCode',
    'rollbackTargetArtifact',
    'healthcareRiskAcknowledgement',
  ], errors);
}

function checkUnsafeSecretDefaults(envExampleContent) {
  const violations = [];
  for (const line of envExampleContent.split('\n')) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith('#') || !trimmed.includes('=')) {
      continue;
    }

    const [key, ...valueParts] = trimmed.split('=');
    const value = valueParts.join('=').trim();
    if (/SECRET|PASSWORD|TOKEN|API_KEY|KEY/i.test(key) && value && !value.startsWith('your-') && !value.startsWith('<') && !value.startsWith('CHANGE')) {
      violations.push(`${key} has a potentially unsafe default value: "${value}"`);
    }
  }
  return violations;
}

function runKernelProduct(args) {
  const output = execFileSync(
    process.execPath,
    [path.join(repoRoot, 'scripts', 'kernel-product.mjs'), ...args],
    { cwd: repoRoot, stdio: 'pipe', encoding: 'utf8' },
  );
  return parseKernelJsonOutput(output);
}

function parseKernelJsonOutput(output) {
  const trimmed = output.trim();
  const markerIndex = trimmed.indexOf('{\n  "plan":');
  const jsonStartIndex = markerIndex >= 0 ? markerIndex : trimmed.lastIndexOf('{');
  if (jsonStartIndex === -1) {
    throw new Error(`Kernel output does not contain JSON payload: ${trimmed.slice(0, 200)}`);
  }
  return JSON.parse(trimmed.slice(jsonStartIndex));
}

function runLifecycleSmoke() {
  const errors = [];
  const phases = [];

  for (const smokePhase of SMOKE_PHASES) {
    try {
      const payload = runKernelProduct(smokePhase.args);
      if (payload?.result?.status !== 'skipped') {
        errors.push(`PHR lifecycle smoke phase "${smokePhase.phase}" must dry-run to skipped status, got "${payload?.result?.status}"`);
      }
      if (!payload?.plan?.runId || !payload?.plan?.correlationId) {
        errors.push(`PHR lifecycle smoke phase "${smokePhase.phase}" missing runId or correlationId`);
      }
      if (!payload?.result?.eventsRef || !payload?.result?.healthSnapshotRef) {
        errors.push(`PHR lifecycle smoke phase "${smokePhase.phase}" missing eventsRef or healthSnapshotRef`);
      }
      phases.push(buildSmokePhaseEvidence(smokePhase.phase, payload, 'ok'));
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      errors.push(`PHR lifecycle smoke failed for phase "${smokePhase.phase}": ${message}`);
      phases.push({
        phase: smokePhase.phase,
        status: 'failed',
        error: message,
      });
    }
  }

  return { errors, phases };
}

function buildSmokePhaseEvidence(phase, payload, status) {
  const result = payload?.result ?? {};
  const plan = payload?.plan ?? {};
  const manifests = payload?.manifests ?? {};
  const startedAt = result.startedAt ?? new Date().toISOString();
  const completedAt = result.completedAt ?? startedAt;

  return {
    phase,
    status,
    runId: plan.runId ?? result.runId,
    correlationId: plan.correlationId ?? result.correlationId,
    productUnitId: plan.productUnitId ?? result.productUnitId ?? result.productId ?? PRODUCT_ID,
    providerMode: plan.providerMode ?? result.providerMode,
    startedAt,
    completedAt,
    durationMs: typeof result.durationMs === 'number'
      ? result.durationMs
      : Math.max(0, Date.parse(completedAt) - Date.parse(startedAt)),
    evidenceRefs: collectEvidenceRefs(result, manifests),
    manifests,
  };
}

function collectEvidenceRefs(result, manifests) {
  const refs = new Set();
  for (const value of Object.values(manifests ?? {})) {
    if (typeof value === 'string' && value.trim().length > 0 && value.endsWith('.json')) {
      refs.add(value);
    }
  }
  for (const value of [result?.eventsRef, result?.healthSnapshotRef]) {
    if (typeof value === 'string' && value.trim().length > 0) {
      refs.add(value);
    }
  }
  return [...refs].map(normalizeManifestRefsToRepoRelative).sort();
}

export function validateEvidencePackShape(productId, evidence) {
  const errors = [];

  if (evidence?.schemaVersion !== '1.0.0') {
    errors.push(`${productId} evidence pack schemaVersion must be 1.0.0`);
  }
  includesAll(`${productId} evidence categories`, evidence?.evidenceCategories, REQUIRED_EVIDENCE_CATEGORIES, errors);

  for (const [index, phaseEvidence] of asArray(evidence?.checks?.smokePhases).entries()) {
    for (const field of REQUIRED_PHASE_EVIDENCE_FIELDS) {
      if (phaseEvidence?.[field] === undefined || phaseEvidence?.[field] === null || phaseEvidence?.[field] === '') {
        errors.push(`${productId} smokePhases[${index}] missing ${field}`);
      }
    }
    if (!Array.isArray(phaseEvidence?.evidenceRefs) || phaseEvidence.evidenceRefs.length === 0) {
      errors.push(`${productId} smokePhases[${index}].evidenceRefs must include generated manifest refs`);
    }
    for (const [refIndex, ref] of asArray(phaseEvidence?.evidenceRefs).entries()) {
      if (typeof ref === 'string' && isAbsoluteRepoPath(ref)) {
        errors.push(`${productId} smokePhases[${index}].evidenceRefs[${refIndex}] must be repo-relative`);
      }
    }
  }

  return errors;
}

function isAbsoluteRepoPath(value) {
  const normalized = value.replaceAll('\\', '/');
  return normalized.startsWith('/') || /^[A-Za-z]:\//.test(normalized);
}

function normalizeManifestRefsToRepoRelative(value) {
  if (typeof value === 'string') {
    const normalizedValue = value.replaceAll('\\', '/');
    const normalizedRepoRoot = repoRoot.replaceAll('\\', '/');
    return normalizedValue.startsWith(normalizedRepoRoot)
      ? normalizedValue.slice(normalizedRepoRoot.length + 1)
      : value;
  }
  if (!value || typeof value !== 'object') {
    return value;
  }
  if (Array.isArray(value)) {
    return value.map(normalizeManifestRefsToRepoRelative);
  }

  const normalized = {};
  for (const [key, entry] of Object.entries(value)) {
    normalized[key] = normalizeManifestRefsToRepoRelative(entry);
  }
  return normalized;
}

function writeEvidencePack(relativeDir, reportPayload) {
  const outputDir = path.join(repoRoot, relativeDir);
  mkdirSync(outputDir, { recursive: true });
  writeFileSync(
    path.join(outputDir, 'phr-lifecycle-evidence-pack.json'),
    `${JSON.stringify(normalizeManifestRefsToRepoRelative(reportPayload), null, 2)}\n`,
    'utf8',
  );
}

export function validatePhrLifecyclePilot(options = {}) {
  const root = options.repoRoot ?? repoRoot;
  const registry = options.registry ?? readJson(root, 'config/canonical-product-registry.json').registry;
  const exists = options.pathExists ?? ((relativePath) => pathExists(root, relativePath));
  const loadYaml = options.readYaml ?? ((relativePath) => readYaml(root, relativePath));
  const loadText = options.readText ?? ((relativePath) => readText(root, relativePath));
  const findFiles = options.listFiles ?? ((relativeDirectory) => listFiles(root, relativeDirectory));
  const product = registry[PRODUCT_ID];
  const errors = [];

  if (!product) {
    return ['PHR missing from canonical product registry'];
  }

  if (product.lifecycleStatus !== 'enabled') {
    errors.push(`PHR lifecycleStatus must be enabled, got ${product.lifecycleStatus}`);
  }
  if (product.lifecycleExecutionAllowed !== true) {
    errors.push('PHR lifecycleExecutionAllowed must be true');
  }
  if (product.lifecycle?.enabled !== true) {
    errors.push('PHR lifecycle.enabled must be true');
  }
  if (product.metadata?.pilot !== true) {
    errors.push('PHR metadata.pilot must be true for the validated lifecycle pilot');
  }
  if (product.lifecycleMigration?.readinessReasonCode !== 'validated-phr-lifecycle-pilot') {
    errors.push('PHR lifecycleMigration.readinessReasonCode must be validated-phr-lifecycle-pilot');
  }
  for (const staleReasonCode of [
    'disabled-observed',
    'requires-product-owner-executable-surface-definition',
    'requires-lifecycle-profile',
    'demo-product-not-execution-ready',
  ]) {
    if (asArray(product.metadata?.lifecycleReadiness?.reasonCodes).includes(staleReasonCode)) {
      errors.push(`PHR metadata.lifecycleReadiness.reasonCodes must not include stale disabled/demo code: ${staleReasonCode}`);
    }
  }
  includesAll(
    'PHR metadata.lifecycleReadiness.requiredGates',
    product.metadata?.lifecycleReadiness?.requiredGates,
    REQUIRED_HEALTHCARE_GATES,
    errors,
  );
  validateRollbackReadiness('PHR registry rollbackReadiness', product.rollbackReadiness, errors);
  if (!exists(product.lifecycleConfigPath ?? '')) {
    errors.push(`PHR lifecycleConfigPath does not exist: ${product.lifecycleConfigPath}`);
    return errors;
  }

  let config;
  try {
    config = options.kernelProduct ?? loadYaml(product.lifecycleConfigPath);
  } catch (error) {
    errors.push(`PHR kernel-product.yaml failed to parse: ${error instanceof Error ? error.message : String(error)}`);
    return errors;
  }

  if (config.productId !== PRODUCT_ID) {
    errors.push('PHR kernel-product.yaml must declare productId: phr');
  }
  if (config.lifecycleProfile !== 'standard-web-api-product') {
    errors.push('PHR lifecycleProfile must be standard-web-api-product');
  }
  if (config.status !== 'enabled') {
    errors.push('PHR kernel-product.yaml status must be enabled');
  }
  if (config.executionEnabled !== true) {
    errors.push('PHR kernel-product.yaml executionEnabled must be true');
  }
  validateRollbackReadiness('PHR kernel rollbackReadiness', config.rollbackReadiness, errors);
  validateRollbackEvidence(loadYaml, exists, errors);
  if (config.phases?.rollback || config.requiredManifests?.rollback) {
    errors.push('PHR rollback phase/manifests must remain absent until rollbackReadiness is enabled for non-local environments');
  }

  if (config.surfaces?.['backend-api']?.adapter !== 'gradle-java-service') {
    errors.push('PHR backend-api surface adapter must be gradle-java-service');
  }
  if (config.surfaces?.web?.adapter !== 'pnpm-vite-react') {
    errors.push('PHR web surface adapter must be pnpm-vite-react');
  }

  includesAll('PHR dev defaultSurfaces', config.phases?.dev?.defaultSurfaces, ['backend-api', 'web'], errors);
  includesAll('PHR build defaultSurfaces', config.phases?.build?.defaultSurfaces, ['backend-api', 'web'], errors);
  includesAll('PHR validate gates', config.gates?.validate, REQUIRED_HEALTHCARE_GATES, errors);
  includesAll('PHR build gates', config.gates?.build, REQUIRED_HEALTHCARE_GATES, errors);
  includesAll('PHR deploy gates', config.gates?.deploy, ['consent', 'tenant-data-sovereignty'], errors);

  for (const gateId of REQUIRED_HEALTHCARE_GATES) {
    const gatePath = `products/phr/lifecycle/gate-packs/${gateId}.yaml`;
    if (!exists(gatePath)) {
      errors.push(`PHR gate pack missing: ${gatePath}`);
      continue;
    }
    const gatePack = loadYaml(gatePath);
    validateGatePackShape(gatePath, gateId, gatePack, errors);
  }

  for (const evidencePath of [
    'products/phr/lifecycle/readiness-evidence.yaml',
    'products/phr/schema-packs/schema-registry.yaml',
    'products/phr/deploy/local.compose.yaml',
    'products/phr/deploy/local.env.example',
  ]) {
    if (!exists(evidencePath)) {
      errors.push(`PHR required lifecycle evidence file missing: ${evidencePath}`);
    }
  }

  for (const phase of REQUIRED_MANIFEST_PHASES) {
    if (!Array.isArray(config.requiredManifests?.[phase]) || config.requiredManifests[phase].length === 0) {
      errors.push(`PHR requiredManifests.${phase} must be declared`);
    }
  }
  for (const manifestName of REQUIRED_MANIFEST_SCHEMA_VERSIONS) {
    if (!config.manifestSchemaVersions?.[manifestName]) {
      errors.push(`PHR manifestSchemaVersions missing ${manifestName}`);
    }
  }

  for (const [surface, packageConfig] of Object.entries(config.package ?? {})) {
    if (packageConfig?.adapter !== 'docker-buildx') {
      errors.push(`PHR package.${surface}.adapter must be docker-buildx`);
    }
    for (const field of ['image', 'tag', 'dockerfile', 'context']) {
      if (!packageConfig?.[field]) {
        errors.push(`PHR package.${surface} missing ${field}`);
      }
    }
    if (packageConfig?.dockerfile && !exists(packageConfig.dockerfile)) {
      errors.push(`PHR package.${surface}.dockerfile does not exist: ${packageConfig.dockerfile}`);
    }
  }

  const deployLocal = config.deployment?.local;
  if (deployLocal?.adapter !== 'compose-local') {
    errors.push('PHR deployment.local.adapter must be compose-local');
  }
  includesAll('PHR deployment.local.expectedServices', deployLocal?.expectedServices, ['phr-api', 'phr-web'], errors);
  if (deployLocal?.requireEnvFile !== false) {
    errors.push('PHR deployment.local.requireEnvFile must be false for bootstrap local deploy');
  }
  if (!deployLocal?.healthChecks?.['backend-api'] || !deployLocal?.healthChecks?.web) {
    errors.push('PHR deployment.local health checks must include backend-api and web');
  }
  if (!config.verify?.local?.healthChecks?.['backend-api'] || !config.verify?.local?.healthChecks?.web) {
    errors.push('PHR verify.local health checks must include backend-api and web');
  }

  if (deployLocal?.envExampleFile && exists(deployLocal.envExampleFile)) {
    for (const violation of checkUnsafeSecretDefaults(loadText(deployLocal.envExampleFile))) {
      errors.push(`PHR env example unsafe default: ${violation}`);
    }
  }

  const kernelFiles = KERNEL_ROOTS.flatMap((directory) => findFiles(directory));
  for (const file of kernelFiles) {
    if (file.includes('/src/test/') || file.includes('/__tests__/')) {
      continue;
    }
    if (!/\.(java|ts|tsx|js|mjs)$/.test(file)) {
      continue;
    }
    const content = loadText(file);
    if (content.includes('products/phr') || content.includes('com.ghatana.phr')) {
      errors.push(`Kernel file imports or references PHR implementation code: ${file}`);
    }
  }

  return errors;
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const options = parseArgs(process.argv.slice(2));
  const errors = validatePhrLifecyclePilot();
  const evidence = {
    schemaVersion: '1.0.0',
    productId: PRODUCT_ID,
    checkedAt: new Date().toISOString(),
    smokeEnabled: options.smoke,
    evidenceCategories: REQUIRED_EVIDENCE_CATEGORIES,
    checks: {
      smokePhases: [],
    },
  };

  if (options.smoke) {
    const smokeResult = runLifecycleSmoke();
    evidence.checks.smokePhases = smokeResult.phases;
    errors.push(...smokeResult.errors);
  }

  errors.push(...validateEvidencePackShape(PRODUCT_ID, evidence));

  if (options.evidencePackDir !== undefined) {
    writeEvidencePack(options.evidencePackDir, {
      ...evidence,
      summary: {
        status: errors.length === 0 ? 'passed' : 'failed',
        errorCount: errors.length,
      },
      errors,
    });
  }

  if (errors.length === 0) {
    console.log('PHR lifecycle pilot check passed');
    process.exit(0);
  }

  console.error(`PHR lifecycle pilot check FAILED (${errors.length} error(s)):`);
  for (const error of errors) {
    console.error(`  - ${error}`);
  }
  process.exit(1);
}
