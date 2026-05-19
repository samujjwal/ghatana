#!/usr/bin/env node

/**
 * Check digital-marketing lifecycle pilot readiness
 *
 * Validates that:
 * - kernel-product.yaml parses correctly
 * - Registry says lifecycle is enabled for digital-marketing
 * - Build/test/package/deploy/verify plans can be generated
 * - Package phase uses Docker adapter (not Gradle/pnpm surface adapters)
 * - Deploy phase uses Compose adapter
 * - Health paths match /health/ready and /health/live
 * - Env example has no unsafe secret defaults
 * - Compose file exists and has Kernel labels
 */

import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { execFileSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(__dirname, '..');
const PRODUCT_ID = 'digital-marketing';
const KERNEL_LIFECYCLE_SERVICE_PATH = 'platform/typescript/kernel-lifecycle/src/service/KernelLifecycleService.ts';
const STUDIO_KERNEL_CLIENT_PATH = 'platform/typescript/ghatana-studio/src/api/kernelLifecycleClient.ts';
const GENERATED_MANIFEST_KEYS = [
  'lifecyclePlan',
  'lifecycleResult',
  'gateResultManifest',
  'artifactManifest',
  'deploymentManifest',
  'verifyHealthReport',
  'lifecycleHealthSnapshot',
  'lifecycleEvents',
  'rollbackManifest',
];
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

function parseArgs(argv) {
  return {
    smoke: argv.includes('--smoke'),
    composeProof: argv.includes('--compose-proof'),
    evidencePackDir: readFlagValue(argv, '--evidence-pack-dir'),
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

function loadRegistry() {
  const registryPath = join(repoRoot, 'config/canonical-product-registry.json');
  return JSON.parse(readFileSync(registryPath, 'utf8')).registry;
}

function loadYaml(filePath) {
  // Parse YAML manually for simple key: value patterns — avoid adding yaml dep if not present
  // Use dynamic import if yaml package is available
  return filePath;
}

async function parseYaml(content) {
  try {
    const { parse } = await import('yaml');
    return parse(content);
  } catch {
    throw new Error('yaml package is required: pnpm add -w yaml');
  }
}

async function loadManifestValidators() {
  const [{ ArtifactManifestSchema }, { DeploymentManifestSchema }] = await Promise.all([
    import('../platform/typescript/kernel-artifacts/dist/index.js'),
    import('../platform/typescript/kernel-deployment/dist/index.js'),
  ]);
  return { ArtifactManifestSchema, DeploymentManifestSchema };
}

function readJson(filePath) {
  return JSON.parse(readFileSync(filePath, 'utf8'));
}

function readRelative(relativePath) {
  const absolutePath = join(repoRoot, relativePath);
  if (!existsSync(absolutePath)) {
    return null;
  }
  return readFileSync(absolutePath, 'utf8');
}

function asNonEmptyString(value) {
  return typeof value === 'string' && value.trim().length > 0;
}

function asArray(value) {
  return Array.isArray(value) ? value : [];
}

function includesAll(label, actual, expected, errors) {
  const actualSet = new Set(asArray(actual));
  for (const item of expected) {
    if (!actualSet.has(item)) {
      errors.push(`${label} missing required entry: ${item}`);
    }
  }
}

function runKernelProduct(args) {
  const output = execFileSync(
    process.execPath,
    [join(repoRoot, 'scripts', 'kernel-product.mjs'), ...args],
    { cwd: repoRoot, stdio: 'pipe', encoding: 'utf8' },
  );
  return JSON.parse(output);
}

function checkUnsafeSecretDefaults(envExampleContent) {
  const unsafePatterns = [
    /^[A-Z_]*SECRET[A-Z_]*=.+/m,
    /^[A-Z_]*PASSWORD[A-Z_]*=.+/m,
    /^[A-Z_]*TOKEN[A-Z_]*=.+/m,
    /^[A-Z_]*KEY[A-Z_]*=.+/m,
    /^[A-Z_]*API_KEY[A-Z_]*=.+/m,
  ];
  const lines = envExampleContent.split('\n');
  const violations = [];

  for (const line of lines) {
    const trimmed = line.trim();
    if (trimmed.startsWith('#') || !trimmed.includes('=')) continue;

    const [key, ...valueParts] = trimmed.split('=');
    const value = valueParts.join('=').trim();

    // Unsafe: key looks like a secret but has a non-empty, non-placeholder value
    if (/SECRET|PASSWORD|TOKEN|API_KEY/i.test(key)) {
      if (value && !value.startsWith('your-') && !value.startsWith('<') && !value.startsWith('CHANGE') && value !== '' && value !== 'changeme') {
        violations.push(`${key} has a potentially unsafe default value: "${value}"`);
      }
    }
  }

  return violations;
}

async function main() {
  const options = parseArgs(process.argv.slice(2));
  const errors = [];
  const warnings = [];
  const evidence = {
    schemaVersion: '1.0.0',
    productId: PRODUCT_ID,
    checkedAt: new Date().toISOString(),
    smokeEnabled: options.smoke,
    evidenceCategories: REQUIRED_EVIDENCE_CATEGORIES,
    checks: {
      plannedPhases: [],
      smokePhases: [],
      failureScenarios: [],
      composeProof: [],
    },
  };

  // 1. Check registry lifecycle status
  const registry = loadRegistry();
  const product = registry[PRODUCT_ID];
  if (!product) {
    errors.push(`Product "${PRODUCT_ID}" not found in canonical-product-registry.json`);
    reportAndExit(errors, warnings);
    return;
  }

  if (product.lifecycleStatus !== 'enabled') {
    errors.push(`Product "${PRODUCT_ID}": lifecycleStatus must be "enabled", got "${product.lifecycleStatus}"`);
  }

  if (!product.lifecycle?.enabled) {
    errors.push(`Product "${PRODUCT_ID}": lifecycle.enabled must be true`);
  }

  if (product.metadata?.pilot !== true) {
    errors.push(`Product "${PRODUCT_ID}": metadata.pilot must be true for the validated lifecycle pilot`);
  }

  if (product.lifecycleMigration?.readinessReasonCode !== 'validated-digital-marketing-lifecycle-pilot') {
    errors.push(`Product "${PRODUCT_ID}": lifecycleMigration.readinessReasonCode must be "validated-digital-marketing-lifecycle-pilot"`);
  }

  // 2. Validate kernel-product.yaml exists and parses
  const yamlPath = product.lifecycleConfigPath
    ? join(repoRoot, product.lifecycleConfigPath)
    : join(repoRoot, 'products/digital-marketing/kernel-product.yaml');

  if (!existsSync(yamlPath)) {
    errors.push(`kernel-product.yaml not found at ${yamlPath}`);
    reportAndExit(errors, warnings);
    return;
  }

  let config;
  try {
    const content = readFileSync(yamlPath, 'utf8');
    config = await parseYaml(content);
  } catch (err) {
    errors.push(`Failed to parse kernel-product.yaml: ${err instanceof Error ? err.message : String(err)}`);
    reportAndExit(errors, warnings);
    return;
  }

  if (!config) {
    errors.push('kernel-product.yaml parsed as empty/null');
    reportAndExit(errors, warnings);
    return;
  }

  const manifestSchemaVersions = config?.manifestSchemaVersions ?? {};
  const requiredManifestNames = new Set(Object.values(config?.requiredManifests ?? {}).flat());
  for (const manifestName of requiredManifestNames) {
    if (!manifestSchemaVersions[manifestName]) {
      errors.push(`required manifest "${manifestName}" missing manifestSchemaVersions entry`);
    }
  }

  if (config?.approval !== undefined) {
    errors.push('kernel-product.yaml must use canonical approvals, not legacy approval');
  }
  for (const phase of ['deploy', 'promote', 'rollback']) {
    const approvals = config?.approvals?.[phase];
    if (!Array.isArray(approvals) || approvals.length === 0) {
      errors.push(`approvals.${phase} must include at least one approval requirement`);
      continue;
    }
    for (const [index, approval] of approvals.entries()) {
      if (!approval?.required) {
        errors.push(`approvals.${phase}[${index}].required must be true`);
      }
      if (typeof approval?.action !== 'string' || approval.action.length === 0) {
        errors.push(`approvals.${phase}[${index}].action must be declared`);
      }
      if (!Array.isArray(approval?.requiredApprovers) || approval.requiredApprovers.length === 0) {
        errors.push(`approvals.${phase}[${index}].requiredApprovers must include at least one role`);
      }
    }
  }

  for (const policyGroup of ['security', 'privacy']) {
    if (!Array.isArray(config?.policyPacks?.[policyGroup]) || config.policyPacks[policyGroup].length === 0) {
      errors.push(`policyPacks.${policyGroup} must include at least one policy pack reference`);
    }
  }

  for (const [pluginName, pluginConfig] of Object.entries(config?.plugins ?? {})) {
    if (!pluginConfig?.bindingId) {
      errors.push(`plugins.${pluginName}.bindingId must match a Kernel plugin registry binding id`);
    }
  }

  // 3. Validate health paths for backend-api surface
  const backendHealth = config?.surfaces?.['backend-api']?.health;
  if (!backendHealth) {
    errors.push('backend-api surface missing health configuration');
  } else {
    if (backendHealth.livePath !== '/health/live') {
      errors.push(`backend-api health.livePath must be "/health/live", got "${backendHealth.livePath}"`);
    }
    if (backendHealth.readyPath !== '/health/ready') {
      errors.push(`backend-api health.readyPath must be "/health/ready", got "${backendHealth.readyPath}"`);
    }
  }

  // 4. Validate package config has required fields
  const packageConfig = config?.package ?? {};
  for (const [surface, surfacePkg] of Object.entries(packageConfig)) {
    const adapter = surfacePkg?.adapter;
    if (adapter && adapter !== 'docker-buildx') {
      errors.push(`Package phase surface "${surface}" uses adapter "${adapter}"; must use docker-buildx`);
    }
    if (adapter === 'pnpm-vite-react' && surfacePkg?.image) {
      errors.push(`Package phase surface "${surface}" attempts to produce a container image with pnpm-vite-react; use docker-buildx`);
    }
    if (!adapter) {
      errors.push(`Package phase surface "${surface}" has no adapter declared`);
    }
    // Validate required package config fields
    const requiredPkgFields = ['image', 'tag', 'dockerfile', 'context'];
    for (const field of requiredPkgFields) {
      if (!surfacePkg?.[field]) {
        errors.push(`Package phase surface "${surface}" missing required field: ${field}`);
      }
    }
  }

  // 5. Validate deploy uses compose-local adapter and has required fields
  const deployLocal = config?.deployment?.local;
  if (!deployLocal) {
    errors.push('deployment.local configuration is missing');
  } else {
    if (deployLocal.adapter !== 'compose-local') {
      errors.push(`deployment.local.adapter must be "compose-local", got "${deployLocal.adapter}"`);
    }
    // Validate required deployment config fields
    const requiredDeployFields = ['composeFile', 'envExampleFile', 'healthChecks'];
    for (const field of requiredDeployFields) {
      if (!deployLocal?.[field]) {
        errors.push(`deployment.local missing required field: ${field}`);
      }
    }
    // Validate requireEnvFile is false for local
    if (deployLocal.requireEnvFile !== false) {
      errors.push(`deployment.local.requireEnvFile must be false for local environment, got "${deployLocal.requireEnvFile}"`);
    }
  }

  // 6. Validate compose file exists and has required Kernel labels
  if (deployLocal?.composeFile) {
    const composePath = join(repoRoot, deployLocal.composeFile);
    if (!existsSync(composePath)) {
      errors.push(`Compose file not found: ${deployLocal.composeFile}`);
    } else {
      // Check for required Kernel labels in compose file
      const composeContent = readFileSync(composePath, 'utf8');
      const requiredLabels = [
        'ghatana.kernel.productUnit',
        'ghatana.kernel.surface',
        'ghatana.kernel.lifecycle',
        'ghatana.kernel.environment',
        'ghatana.kernel.artifactRef',
        'ghatana.kernel.health.livePath',
        'ghatana.kernel.health.readyPath',
      ];
      for (const label of requiredLabels) {
        if (!composeContent.includes(label)) {
          errors.push(`Compose file ${deployLocal.composeFile} missing required label: ${label}`);
        }
      }
    }
  } else {
    errors.push('deployment.local.composeFile is not declared');
  }

  // 7. Validate verify uses compose-local adapter
  const verifyLocal = config?.verify?.local;
  if (!verifyLocal) {
    errors.push('verify.local configuration is missing');
  } else if (verifyLocal.adapter !== 'compose-local') {
    errors.push(`verify.local.adapter must be "compose-local", got "${verifyLocal.adapter}"`);
  }

  const expectedVerifyFields = verifyLocal?.expectedReportFields ?? [];
  for (const field of ['schemaVersion', 'productUnitId', 'runId', 'correlationId', 'environment', 'status', 'checkedAt', 'checks', 'evidenceRefs']) {
    if (!expectedVerifyFields.includes(field)) {
      errors.push(`verify.local.expectedReportFields missing required field: ${field}`);
    }
  }

  // 8. Validate health URLs in verify match /health/ready and /health/live patterns
  const verifyHealthChecks = verifyLocal?.healthChecks ?? {};
  const backendVerifyCheck = verifyHealthChecks['backend-api'];
  if (!backendVerifyCheck) {
    errors.push('verify.local.healthChecks missing backend-api entry');
  } else {
    const url = backendVerifyCheck.url ?? '';
    if (!url.includes('/health/ready') && !url.includes('/health/live')) {
      errors.push(`verify.local.healthChecks.backend-api.url must reference /health/ready or /health/live, got: "${url}"`);
    }
  }

  // 9. Validate env example has no unsafe secret defaults
  if (deployLocal?.envExampleFile) {
    const envExamplePath = join(repoRoot, deployLocal.envExampleFile);
    if (existsSync(envExamplePath)) {
      const envContent = readFileSync(envExamplePath, 'utf8');
      const unsafeDefaults = checkUnsafeSecretDefaults(envContent);
      for (const violation of unsafeDefaults) {
        errors.push(`Env example ${deployLocal.envExampleFile}: ${violation}`);
      }
      if (envContent.includes('DMOS_PERSISTENCE_TYPE=in-memory')) {
        errors.push(`Env example ${deployLocal.envExampleFile} must not claim in-memory persistence for compose deploy`);
      }
      if (!envContent.includes('DMOS_PERSISTENCE_TYPE=postgres')) {
        errors.push(`Env example ${deployLocal.envExampleFile} must set DMOS_PERSISTENCE_TYPE=postgres`);
      }
      if (!envContent.includes('DATABASE_URL=')) {
        errors.push(`Env example ${deployLocal.envExampleFile} must include DATABASE_URL for compose deploy`);
      }
    } else {
      warnings.push(`Env example file not found: ${deployLocal.envExampleFile}`);
    }
  }

  // 10. Validate plan generation for key phases
  const planPhases = ['validate', 'build', 'test', 'package'];
  for (const phase of planPhases) {
    try {
      const planPayload = runKernelProduct(['product', 'plan', PRODUCT_ID, phase, '--json']);
      evidence.checks.plannedPhases.push({
        phase,
        status: 'ok',
        runId: planPayload?.plan?.runId,
        correlationId: planPayload?.plan?.correlationId,
        providerMode: planPayload?.plan?.providerMode,
      });
    } catch (err) {
      errors.push(`Plan generation failed for phase "${phase}": ${err instanceof Error ? err.message : String(err)}`);
      evidence.checks.plannedPhases.push({
        phase,
        status: 'failed',
        error: err instanceof Error ? err.message : String(err),
      });
    }
  }

  // 11. Validate plan generation for deploy, promote, and verify with --env local
        for (const phase of ['deploy', 'verify']) {
    try {
      const payload = runKernelProduct(['product', 'plan', PRODUCT_ID, phase, '--env', 'local', '--json']);
      evidence.checks.plannedPhases.push({
        phase,
        status: 'ok',
        runId: payload?.plan?.runId,
        correlationId: payload?.plan?.correlationId,
        providerMode: payload?.plan?.providerMode,
      });
            if (phase === 'deploy' && !hasApprovalRequirements(payload)) {
        errors.push(`${phase} plan must emit approvalRequirements from canonical approvals config`);
      }
    } catch (err) {
      errors.push(`Plan generation failed for phase "${phase} --env local": ${err instanceof Error ? err.message : String(err)}`);
      evidence.checks.plannedPhases.push({
        phase,
        status: 'failed',
        error: err instanceof Error ? err.message : String(err),
      });
    }
  }
  for (const phase of ['rollback']) {
    try {
      const payload = runKernelProduct(['product', 'plan', PRODUCT_ID, phase, '--env', 'local', '--json']);
      evidence.checks.plannedPhases.push({
        phase,
        status: 'ok',
        runId: payload?.plan?.runId,
        correlationId: payload?.plan?.correlationId,
        providerMode: payload?.plan?.providerMode,
      });
      if (!hasApprovalRequirements(payload)) {
        errors.push(`${phase} plan must emit approvalRequirements from canonical approvals config`);
      }
    } catch (err) {
      errors.push(`Plan generation failed for phase "${phase} --env local": ${err instanceof Error ? err.message : String(err)}`);
      evidence.checks.plannedPhases.push({
        phase,
        status: 'failed',
        error: err instanceof Error ? err.message : String(err),
      });
    }
  }

  // 12. Validate .gitignore includes local env files
  const deployDir = join(repoRoot, 'products/digital-marketing/deploy');
  const gitignorePath = join(deployDir, '.gitignore');
  if (existsSync(gitignorePath)) {
    const gitignoreContent = readFileSync(gitignorePath, 'utf8');
    if (!gitignoreContent.includes('local.env') && !gitignoreContent.includes('*.local.env')) {
      errors.push('deploy/.gitignore must include local.env or *.local.env to prevent committing secrets');
    }
  } else {
    errors.push('deploy/.gitignore not found - must exist to prevent committing local.env files');
  }

  // 13. Validate no product lifecycle runner script exists in Digital Marketing
  const productDir = join(repoRoot, 'products/digital-marketing');
  const forbiddenScripts = ['lifecycle-runner.js', 'lifecycle-runner.mjs', 'run-lifecycle.sh', 'run-lifecycle.js'];
  for (const script of forbiddenScripts) {
    const scriptPath = join(productDir, script);
    if (existsSync(scriptPath)) {
      errors.push(`Product lifecycle runner script found at ${scriptPath} - lifecycle execution must use Kernel, not product-specific scripts`);
    }
  }

  errors.push(...validateProvenanceAndStudioContracts());

  if (options.smoke) {
    const smokeResult = await runLifecycleSmoke();
    const smokeErrors = smokeResult.errors;
    evidence.checks.smokePhases = smokeResult.phases;
    errors.push(...smokeErrors);
  }

  const failureScenariosResult = runFailureScenarios();
  evidence.checks.failureScenarios = failureScenariosResult.scenarios;
  errors.push(...failureScenariosResult.errors);

  if (options.composeProof) {
    const composeProofResult = runComposeProof(config);
    evidence.checks.composeProof = composeProofResult.steps;
    errors.push(...composeProofResult.errors);
    warnings.push(...composeProofResult.warnings);
  }

  errors.push(...validateEvidencePackShape(PRODUCT_ID, evidence));

  if (options.evidencePackDir !== undefined) {
    writeEvidencePack(options.evidencePackDir, {
      ...evidence,
      summary: {
        status: errors.length === 0 ? 'passed' : 'failed',
        errorCount: errors.length,
        warningCount: warnings.length,
      },
      errors,
      warnings,
    });
  }

  reportAndExit(errors, warnings);
}

function runComposeProof(config) {
  const errors = [];
  const warnings = [];
  const steps = [];

  if (!isDockerComposeReady()) {
    warnings.push(
      'Compose-backed proof skipped: docker compose is unavailable or daemon is not running in current environment.',
    );
    steps.push({
      step: 'environment-check',
      status: 'blocked',
      reason: 'docker-compose-unavailable',
    });
    return { errors, warnings, steps };
  }

  const deployConfig = config?.deployment?.local;
  const envFilePath = deployConfig?.envFile;
  if (!asNonEmptyString(envFilePath)) {
    warnings.push('Compose-backed proof skipped: deployment.local.envFile is not configured in kernel-product.yaml.');
    steps.push({
      step: 'environment-check',
      status: 'blocked',
      reason: 'missing-env-file-config',
    });
    return { errors, warnings, steps };
  }

  const envFileAbsolutePath = join(repoRoot, envFilePath);
  if (!existsSync(envFileAbsolutePath)) {
    warnings.push(`Compose-backed proof skipped: required env file does not exist (${envFilePath}).`);
    steps.push({
      step: 'environment-check',
      status: 'blocked',
      reason: 'missing-env-file',
      envFile: envFilePath,
    });
    return { errors, warnings, steps };
  }

  const proofSteps = [
    {
      step: 'deploy',
      args: ['product', 'deploy', PRODUCT_ID, '--env', 'local', '--json'],
      timeoutMs: 240_000,
    },
    {
      step: 'verify',
      args: ['product', 'verify', PRODUCT_ID, '--env', 'local', '--json'],
      timeoutMs: 240_000,
    },
  ];

  for (const proofStep of proofSteps) {
    const execution = runKernelProductSafely(proofStep.args, proofStep.timeoutMs);
    if (!execution.ok) {
      errors.push(`Compose proof ${proofStep.step} failed: ${execution.error}`);
      steps.push({
        step: proofStep.step,
        status: 'failed',
        error: execution.error,
      });
      continue;
    }

    steps.push({
      step: proofStep.step,
      status: 'ok',
      runId: execution.payload?.plan?.runId,
      correlationId: execution.payload?.plan?.correlationId,
      resultStatus: execution.payload?.result?.status,
      manifests: execution.payload?.manifests ?? {},
    });
  }

  return { errors, warnings, steps };
}

function isDockerComposeReady() {
  try {
    execFileSync('docker', ['compose', 'version'], {
      cwd: repoRoot,
      stdio: 'ignore',
      timeout: 20_000,
    });
    execFileSync('docker', ['info'], {
      cwd: repoRoot,
      stdio: 'ignore',
      timeout: 20_000,
    });
    return true;
  } catch {
    return false;
  }
}

function runKernelProductSafely(args, timeoutMs) {
  try {
    const output = execFileSync(
      process.execPath,
      [join(repoRoot, 'scripts', 'kernel-product.mjs'), ...args],
      { cwd: repoRoot, stdio: 'pipe', encoding: 'utf8', timeout: timeoutMs },
    );
    return { ok: true, payload: parseKernelJsonOutput(output) };
  } catch (error) {
    const message =
      error instanceof Error
        ? error.message
        : typeof error === 'object' && error !== null && 'stderr' in error
          ? String(error.stderr)
          : String(error);
    return { ok: false, error: message };
  }
}

function parseKernelJsonOutput(output) {
  const trimmed = output.trim();
  const payloadMarker = '{\n  "plan":';
  const markerIndex = trimmed.indexOf(payloadMarker);
  const jsonStartIndex = markerIndex >= 0 ? markerIndex : trimmed.lastIndexOf('{');
  if (jsonStartIndex === -1) {
    throw new Error(`Kernel output does not contain JSON payload: ${trimmed.slice(0, 200)}`);
  }
  const candidate = trimmed.slice(jsonStartIndex);
  return JSON.parse(candidate);
}

function validateProvenanceAndStudioContracts() {
  const errors = [];

  const kernelLifecycleServiceSource = readRelative(KERNEL_LIFECYCLE_SERVICE_PATH);
  if (kernelLifecycleServiceSource === null) {
    errors.push(`Missing required Kernel lifecycle service source: ${KERNEL_LIFECYCLE_SERVICE_PATH}`);
    return errors;
  }

  const studioKernelClientSource = readRelative(STUDIO_KERNEL_CLIENT_PATH);
  if (studioKernelClientSource === null) {
    errors.push(`Missing required Studio kernel client source: ${STUDIO_KERNEL_CLIENT_PATH}`);
    return errors;
  }

  const requiredKernelLifecycleMarkers = [
    'private async recordProvenance(',
    'this.providerContext.provenance.recordProvenance(',
    'private async recordRuntimeTruth(',
    'this.providerContext.runtimeTruth.recordRuntimeTruth(',
    'private async loadRunSummary(',
    'eventsRef',
    'healthSnapshotRef',
    'productUnitId: result.productId',
  ];

  for (const marker of requiredKernelLifecycleMarkers) {
    if (!kernelLifecycleServiceSource.includes(marker)) {
      errors.push(`Kernel lifecycle service must include marker for provenance/studio summary contract: ${marker}`);
    }
  }

  const requiredStudioClientMarkers = [
    'export const LifecycleRunSchema = z',
    'productUnitId: z.string().trim().min(1)',
    'eventsRef: z.string().trim().min(1).optional()',
    'healthSnapshotRef: z.string().trim().min(1).optional()',
    'getLifecycleRun(productUnitId: string, runId: string)',
    'listLifecycleRuns(productUnitId: string)',
  ];

  for (const marker of requiredStudioClientMarkers) {
    if (!studioKernelClientSource.includes(marker)) {
      errors.push(`Studio kernel client must include lifecycle run visibility marker: ${marker}`);
    }
  }

  return errors;
}

async function runLifecycleSmoke() {
  const errors = [];
  const phases = [];
  const { ArtifactManifestSchema, DeploymentManifestSchema } = await loadManifestValidators();
  const smokeRuns = [
    { phase: 'validate', args: ['product', 'validate', PRODUCT_ID, '--dry-run', '--json'] },
    { phase: 'test', args: ['product', 'test', PRODUCT_ID, '--dry-run', '--json'] },
    { phase: 'build', args: ['product', 'build', PRODUCT_ID, '--dry-run', '--json'] },
    { phase: 'package', args: ['product', 'package', PRODUCT_ID, '--dry-run', '--json'] },
    { phase: 'deploy', args: ['product', 'deploy', PRODUCT_ID, '--env', 'local', '--dry-run', '--json'] },
    { phase: 'verify', args: ['product', 'verify', PRODUCT_ID, '--env', 'local', '--dry-run', '--json'] },
    {
      phase: 'rollback',
      args: [
        'product',
        'rollback',
        PRODUCT_ID,
        '--env',
        'local',
        '--dry-run',
        '--require-approval',
        '--approval-id',
        'rollback-rollback:local',
        '--json',
      ],
    },
  ];

  for (const smokeRun of smokeRuns) {
    let payload;
    try {
      payload = runKernelProduct(smokeRun.args);
    } catch (err) {
      const failureMessage = String(err instanceof Error ? err.message : err);
      if (
        (smokeRun.phase === 'deploy' || smokeRun.phase === 'rollback') &&
        (failureMessage.includes('approval required') || failureMessage.includes('approval request failed'))
      ) {
        continue;
      }
      errors.push(`Lifecycle smoke failed for phase "${smokeRun.phase}": ${err instanceof Error ? err.message : String(err)}`);
      phases.push({
        phase: smokeRun.phase,
        status: 'failed',
        error: err instanceof Error ? err.message : String(err),
      });
      continue;
    }

    if (payload?.result?.status !== 'skipped') {
      errors.push(`Lifecycle smoke phase "${smokeRun.phase}" must dry-run to skipped status, got "${payload?.result?.status}"`);
    }
    errors.push(...validateLifecycleEvidenceRefs(smokeRun.phase, payload));
    errors.push(...validateLatestPointerConsistency(smokeRun.phase, payload));
    errors.push(...validateGeneratedManifests(smokeRun.phase, payload, {
      ArtifactManifestSchema,
      DeploymentManifestSchema,
    }));
    errors.push(...validateGateEvidence(smokeRun.phase, payload));

    phases.push(buildSmokePhaseEvidence(smokeRun.phase, payload, 'ok'));
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
  return [...refs].sort();
}

function validateEvidencePackShape(productId, evidence) {
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
  }

  return errors;
}

function runFailureScenarios() {
  const errors = [];
  const scenarios = [];

  const cases = [
    {
      id: 'deploy-requires-approval-id',
      args: ['product', 'deploy', PRODUCT_ID, '--env', 'local', '--dry-run', '--require-approval', '--json'],
      expected: '--require-approval requires --approval-id',
    },
    {
      id: 'platform-mode-provider-bridge-unavailable',
      args: ['product', 'plan', PRODUCT_ID, 'deploy', '--mode', 'platform', '--env', 'local', '--json'],
      expected: 'Kernel platform mode requires the Data Cloud-backed provider bridge',
    },
  ];

  for (const scenario of cases) {
    try {
      runKernelProduct(scenario.args);
      errors.push(`Failure scenario "${scenario.id}" unexpectedly succeeded`);
      scenarios.push({
        id: scenario.id,
        status: 'failed',
        reason: 'unexpected-success',
      });
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      if (!message.includes(scenario.expected)) {
        errors.push(
          `Failure scenario "${scenario.id}" returned unexpected error. Expected to include "${scenario.expected}", got: ${message}`,
        );
        scenarios.push({
          id: scenario.id,
          status: 'failed',
          reason: 'unexpected-error-shape',
          message,
        });
        continue;
      }
      scenarios.push({
        id: scenario.id,
        status: 'ok',
        expectedError: scenario.expected,
      });
    }
  }

  return { errors, scenarios };
}

function toRepoRelativePath(absolutePath) {
  if (!absolutePath || typeof absolutePath !== 'string') {
    return absolutePath;
  }
  // Convert absolute path to repo-relative path
  const normalizedRepoRoot = repoRoot.replace(/\\/g, '/');
  const normalizedPath = absolutePath.replace(/\\/g, '/');
  if (normalizedPath.startsWith(normalizedRepoRoot)) {
    return normalizedPath.slice(normalizedRepoRoot.length + 1); // Remove repo root and leading slash
  }
  return absolutePath; // Return as-is if not under repo root
}

function normalizeManifestRefsToRepoRelative(obj) {
  if (!obj || typeof obj !== 'object') {
    return obj;
  }
  if (Array.isArray(obj)) {
    return obj.map(normalizeManifestRefsToRepoRelative);
  }
  const result = {};
  for (const [key, value] of Object.entries(obj)) {
    // Convert manifest paths to repo-relative
    if (key === 'manifests' && typeof value === 'object' && value !== null) {
      result[key] = {};
      for (const [manifestKey, manifestPath] of Object.entries(value)) {
        result[key][manifestKey] = toRepoRelativePath(manifestPath);
      }
    } else if (typeof value === 'object' && value !== null) {
      result[key] = normalizeManifestRefsToRepoRelative(value);
    } else if (typeof value === 'string' && (value.endsWith('.json') || value.includes('/.kernel/'))) {
      result[key] = toRepoRelativePath(value);
    } else {
      result[key] = value;
    }
  }
  return result;
}

function writeEvidencePack(relativeDir, reportPayload) {
  const outputDir = join(repoRoot, relativeDir);
  mkdirSync(outputDir, { recursive: true });
  const reportPath = join(outputDir, 'digital-marketing-lifecycle-evidence-pack.json');
  
  // Normalize all manifest refs to repo-relative paths
  const normalizedPayload = normalizeManifestRefsToRepoRelative(reportPayload);
  
  writeFileSync(reportPath, `${JSON.stringify(normalizedPayload, null, 2)}\n`, 'utf8');
}

function validateLifecycleEvidenceRefs(phase, payload) {
  const errors = [];
  const result = payload?.result;
  const manifests = payload?.manifests ?? {};

  if (!result || typeof result !== 'object') {
    errors.push(`Lifecycle smoke phase "${phase}" missing execution result payload`);
    return errors;
  }

  if (!asNonEmptyString(result.eventsRef)) {
    errors.push(`Lifecycle smoke phase "${phase}" must provide result.eventsRef for lifecycle event traceability`);
  }
  if (!asNonEmptyString(result.healthSnapshotRef)) {
    errors.push(`Lifecycle smoke phase "${phase}" must provide result.healthSnapshotRef for runtime truth linkage`);
  }
  if (!asNonEmptyString(result.runId) || !asNonEmptyString(result.correlationId) || !asNonEmptyString(result.productId)) {
    errors.push(
      `Lifecycle smoke phase "${phase}" must include runId/correlationId/productId to keep Studio run summaries resolvable`,
    );
  }

  const lifecycleEventsManifest = manifests.lifecycleEvents;
  if (!asNonEmptyString(lifecycleEventsManifest)) {
    errors.push(`Lifecycle smoke phase "${phase}" missing lifecycleEvents manifest pointer`);
  } else if (result.eventsRef !== lifecycleEventsManifest) {
    errors.push(
      `Lifecycle smoke phase "${phase}" has mismatched event refs: result.eventsRef (${result.eventsRef}) != manifests.lifecycleEvents (${lifecycleEventsManifest})`,
    );
  }

  const lifecycleHealthSnapshotManifest = manifests.lifecycleHealthSnapshot;
  if (!asNonEmptyString(lifecycleHealthSnapshotManifest)) {
    errors.push(`Lifecycle smoke phase "${phase}" missing lifecycleHealthSnapshot manifest pointer`);
  } else if (result.healthSnapshotRef !== lifecycleHealthSnapshotManifest) {
    errors.push(
      `Lifecycle smoke phase "${phase}" has mismatched health refs: result.healthSnapshotRef (${result.healthSnapshotRef}) != manifests.lifecycleHealthSnapshot (${lifecycleHealthSnapshotManifest})`,
    );
  }

  return errors;
}

function validateLatestPointerConsistency(phase, payload) {
  const errors = [];
  const plan = payload?.plan;
  const manifests = payload?.manifests ?? {};

  if (!plan || !asNonEmptyString(plan.runId) || !asNonEmptyString(plan.correlationId)) {
    errors.push(`Lifecycle smoke phase "${phase}" missing plan/run metadata needed for latest pointer validation`);
    return errors;
  }

  const latestPointerPath = join(
    repoRoot,
    '.kernel',
    'out',
    'products',
    PRODUCT_ID,
    phase,
    'latest',
    'manifest-pointers.json',
  );

  if (!existsSync(latestPointerPath)) {
    errors.push(`Lifecycle smoke phase "${phase}" missing latest manifest pointer file: ${latestPointerPath}`);
    return errors;
  }

  let latestPointers;
  try {
    latestPointers = readJson(latestPointerPath);
  } catch (error) {
    errors.push(
      `Lifecycle smoke phase "${phase}" failed to parse latest manifest pointers: ${error instanceof Error ? error.message : String(error)}`,
    );
    return errors;
  }

  if (latestPointers.runId !== plan.runId) {
    errors.push(`Lifecycle smoke phase "${phase}" latest pointer runId drift: ${latestPointers.runId} != ${plan.runId}`);
  }
  if (latestPointers.correlationId !== plan.correlationId) {
    errors.push(
      `Lifecycle smoke phase "${phase}" latest pointer correlationId drift: ${latestPointers.correlationId} != ${plan.correlationId}`,
    );
  }
  if (latestPointers.providerMode !== plan.providerMode) {
    errors.push(`Lifecycle smoke phase "${phase}" latest pointer providerMode drift: ${latestPointers.providerMode} != ${plan.providerMode}`);
  }

  for (const key of ['lifecycleResult', 'lifecycleEvents', 'lifecycleHealthSnapshot']) {
    if (asNonEmptyString(manifests[key]) && latestPointers[key] !== manifests[key]) {
      errors.push(
        `Lifecycle smoke phase "${phase}" latest pointer ${key} drift: ${latestPointers[key]} != ${manifests[key]}`,
      );
    }
  }

  return errors;
}

function validateGateEvidence(phase, payload) {
  const errors = [];
  const gateResultManifestPath = payload?.manifests?.gateResultManifest;

  if (!gateResultManifestPath) {
    // gateResultManifest is expected for all phases except dev
    if (phase !== 'dev') {
      errors.push(`Lifecycle smoke phase "${phase}" missing gateResultManifest`);
    }
    return errors;
  }

  if (!existsSync(gateResultManifestPath)) {
    errors.push(`Lifecycle smoke phase "${phase}" gateResultManifest does not exist: ${gateResultManifestPath}`);
    return errors;
  }

  try {
    const gateResults = readJson(gateResultManifestPath);
    const gateResultsArray = Array.isArray(gateResults)
      ? gateResults
      : Array.isArray(gateResults?.results)
        ? gateResults.results
        : gateResults?.gates;

    if (!Array.isArray(gateResultsArray)) {
      errors.push(`Lifecycle smoke phase "${phase}" gateResultManifest has invalid format: expected array of results`);
      return errors;
    }

    for (const gateResult of gateResultsArray) {
      const gateId = gateResult?.gateId;
      const passed = gateResult?.passed === true || gateResult?.status === 'passed';
      const reason = gateResult?.reason ?? gateResult?.details;
      const evidence = gateResult?.evidence ?? gateResult?.evidenceRefs;

      if (passed === true) {
        // Check for synthetic bootstrap gate success without real evidence
        if (reason?.includes('synthetic') || reason?.includes('replace with concrete provider')) {
          errors.push(
            `Gate "${gateId}" in phase "${phase}" returned synthetic success without real evidence: ${reason}`
          );
        }
        // Check for bootstrap-gate evidence pattern (indicates synthetic pass)
        if (Array.isArray(evidence) && evidence.some((e) => e?.startsWith('bootstrap-gate:'))) {
          errors.push(
            `Gate "${gateId}" in phase "${phase}" has synthetic bootstrap-gate evidence without real implementation`
          );
        }
        // Check for empty evidence when gate passed
        if (!Array.isArray(evidence) || evidence.length === 0) {
          errors.push(
            `Gate "${gateId}" in phase "${phase}" passed but has no evidence`
          );
        }
      }
    }
  } catch (err) {
    errors.push(
      `Lifecycle smoke phase "${phase}" failed to parse gateResultManifest: ${err instanceof Error ? err.message : String(err)}`
    );
  }

  return errors;
}

function validateGeneratedManifests(phase, payload, validators) {
  const errors = [];
  const manifests = payload?.manifests ?? {};
  for (const key of GENERATED_MANIFEST_KEYS) {
    const manifestPath = manifests[key];
    if (manifestPath === undefined) {
      if (isExpectedForPhase(key, phase)) {
        errors.push(`Lifecycle smoke phase "${phase}" missing generated manifest pointer: ${key}`);
      }
      continue;
    }
    if (!existsSync(manifestPath)) {
      errors.push(`Lifecycle smoke phase "${phase}" manifest pointer does not exist for ${key}: ${manifestPath}`);
      continue;
    }
    if (key === 'artifactManifest') {
      const parsed = validators.ArtifactManifestSchema.safeParse(readJson(manifestPath));
      if (!parsed.success) {
        errors.push(`Lifecycle smoke phase "${phase}" artifact manifest schema validation failed: ${parsed.error.message}`);
      }
    }
    if (key === 'deploymentManifest') {
      const parsed = validators.DeploymentManifestSchema.safeParse(readJson(manifestPath));
      if (!parsed.success) {
        errors.push(`Lifecycle smoke phase "${phase}" deployment manifest schema validation failed: ${parsed.error.message}`);
      }
    }
  }
  return errors;
}

function isExpectedForPhase(key, phase) {
  if (['lifecyclePlan', 'lifecycleResult', 'gateResultManifest', 'lifecycleHealthSnapshot', 'lifecycleEvents'].includes(key)) {
    return true;
  }
  if (key === 'artifactManifest') {
    return ['build', 'package'].includes(phase);
  }
  if (key === 'deploymentManifest') {
    return phase === 'deploy';
  }
  if (key === 'rollbackManifest') {
    return phase === 'rollback';
  }
  if (key === 'verifyHealthReport') {
    return phase === 'verify';
  }
  return false;
}

function hasApprovalRequirements(payload) {
  const approvalRequirements = payload?.plan?.approvalRequirements ?? payload?.approvalRequirements;
  return Array.isArray(approvalRequirements) && approvalRequirements.some((approval) =>
    approval?.required === true &&
    typeof approval?.action === 'string' &&
    approval.action.length > 0,
  );
}

function reportAndExit(errors, warnings) {
  if (warnings.length > 0) {
    console.warn('Warnings:');
    for (const w of warnings) {
      console.warn(`  - ${w}`);
    }
  }

  if (errors.length > 0) {
    console.error(`\nDigital Marketing lifecycle pilot check FAILED (${errors.length} error(s)):`);
    for (const e of errors) {
      console.error(`  - ${e}`);
    }
    process.exit(1);
  }

  console.log('Digital Marketing lifecycle pilot check passed');
}

try {
  await main();
} catch (error) {
  console.error(`Check failed: ${error instanceof Error ? error.message : String(error)}`);
  process.exit(1);
}
