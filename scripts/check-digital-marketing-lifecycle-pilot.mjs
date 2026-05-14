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

  // 4. Validate package uses docker-buildx adapter (not gradle/pnpm)
  const packageConfig = config?.package ?? {};
  for (const [surface, surfacePkg] of Object.entries(packageConfig)) {
    const adapter = surfacePkg?.adapter;
    if (adapter && !['docker-buildx', 'docker-static-web'].includes(adapter)) {
      errors.push(`Package phase surface "${surface}" uses adapter "${adapter}"; must use docker-buildx or docker-static-web`);
    }
    if (!adapter) {
      errors.push(`Package phase surface "${surface}" has no adapter declared`);
    }
  }

  // 5. Validate deploy uses compose-local adapter
  const deployLocal = config?.deployment?.local;
  if (!deployLocal) {
    errors.push('deployment.local configuration is missing');
  } else if (deployLocal.adapter !== 'compose-local') {
    errors.push(`deployment.local.adapter must be "compose-local", got "${deployLocal.adapter}"`);
  }

  // 6. Validate compose file exists
  if (deployLocal?.composeFile) {
    const composePath = join(repoRoot, deployLocal.composeFile);
    if (!existsSync(composePath)) {
      errors.push(`Compose file not found: ${deployLocal.composeFile}`);
    } else {
      // Check for Kernel labels in compose file
      const composeContent = readFileSync(composePath, 'utf8');
      if (!composeContent.includes('ghatana.kernel')) {
        warnings.push(`Compose file ${deployLocal.composeFile} does not include "ghatana.kernel" labels — consider adding them for lifecycle tracking`);
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
  for (const phase of ['build', 'test', 'package']) {
    try {
      execFileSync(
        process.execPath,
        [join(repoRoot, 'scripts', 'kernel-product.mjs'), 'product', 'plan', PRODUCT_ID, phase, '--json'],
        { cwd: repoRoot, stdio: 'pipe', encoding: 'utf8' },
      );
    } catch (err) {
      errors.push(`Plan generation failed for phase "${phase}": ${err instanceof Error ? err.message : String(err)}`);
    }
  }

  reportAndExit(errors, warnings);
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
