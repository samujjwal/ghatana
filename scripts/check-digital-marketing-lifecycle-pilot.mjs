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

import { existsSync, readFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { execFileSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(__dirname, '..');
const PRODUCT_ID = 'digital-marketing';
const GENERATED_MANIFEST_KEYS = [
  'lifecyclePlan',
  'lifecycleResult',
  'gateResultManifest',
  'artifactManifest',
  'deploymentManifest',
  'verifyHealthReport',
  'lifecycleHealthSnapshot',
  'lifecycleEvents',
];

function parseArgs(argv) {
  return {
    smoke: argv.includes('--smoke'),
  };
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

  const lifecycleEnabledProducts = Object.values(registry)
    .filter((candidate) => candidate.lifecycleStatus === 'enabled' || candidate.lifecycle?.enabled === true)
    .map((candidate) => candidate.id);
  const unexpectedLifecycleProducts = lifecycleEnabledProducts.filter((id) => id !== PRODUCT_ID);
  if (unexpectedLifecycleProducts.length > 0) {
    errors.push(`Only "${PRODUCT_ID}" may be lifecycle-enabled pilot; unexpected enabled products: ${unexpectedLifecycleProducts.join(', ')}`);
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

  for (const phase of ['deploy', 'promote', 'rollback']) {
    const approval = config?.approval?.[phase];
    if (!approval?.required) {
      errors.push(`approval.${phase}.required must be true`);
    }
    if (!Array.isArray(approval?.approverRoles) || approval.approverRoles.length === 0) {
      errors.push(`approval.${phase}.approverRoles must include at least one role`);
    }
    if (!Array.isArray(approval?.evidenceRefs) || approval.evidenceRefs.length === 0) {
      errors.push(`approval.${phase}.evidenceRefs must include required evidence refs`);
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
    } else {
      warnings.push(`Env example file not found: ${deployLocal.envExampleFile}`);
    }
  }

  // 10. Validate plan generation for key phases
  const planPhases = ['validate', 'build', 'test', 'package'];
  for (const phase of planPhases) {
    try {
      runKernelProduct(['product', 'plan', PRODUCT_ID, phase, '--json']);
    } catch (err) {
      errors.push(`Plan generation failed for phase "${phase}": ${err instanceof Error ? err.message : String(err)}`);
    }
  }

  // 11. Validate plan generation for deploy and verify with --env local
  for (const phase of ['deploy', 'verify']) {
    try {
      runKernelProduct(['product', 'plan', PRODUCT_ID, phase, '--env', 'local', '--json']);
    } catch (err) {
      errors.push(`Plan generation failed for phase "${phase} --env local": ${err instanceof Error ? err.message : String(err)}`);
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

  if (options.smoke) {
    const smokeErrors = await runLifecycleSmoke();
    errors.push(...smokeErrors);
  }

  reportAndExit(errors, warnings);
}

async function runLifecycleSmoke() {
  const errors = [];
  const { ArtifactManifestSchema, DeploymentManifestSchema } = await loadManifestValidators();
  const smokeRuns = [
    { phase: 'build', args: ['product', 'build', PRODUCT_ID, '--dry-run', '--json'] },
    { phase: 'package', args: ['product', 'package', PRODUCT_ID, '--dry-run', '--json'] },
    { phase: 'deploy', args: ['product', 'deploy', PRODUCT_ID, '--env', 'local', '--dry-run', '--json'] },
    { phase: 'verify', args: ['product', 'verify', PRODUCT_ID, '--env', 'local', '--dry-run', '--json'] },
  ];

  for (const smokeRun of smokeRuns) {
    let payload;
    try {
      payload = runKernelProduct(smokeRun.args);
    } catch (err) {
      errors.push(`Lifecycle smoke failed for phase "${smokeRun.phase}": ${err instanceof Error ? err.message : String(err)}`);
      continue;
    }

    if (payload?.result?.status !== 'skipped') {
      errors.push(`Lifecycle smoke phase "${smokeRun.phase}" must dry-run to skipped status, got "${payload?.result?.status}"`);
    }
    errors.push(...validateGeneratedManifests(smokeRun.phase, payload, {
      ArtifactManifestSchema,
      DeploymentManifestSchema,
    }));
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
  if (key === 'verifyHealthReport') {
    return phase === 'verify';
  }
  return false;
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
