#!/usr/bin/env node

/**
 * Check lifecycle registry/YAML config drift
 *
 * For each lifecycle-enabled product, compares:
 * - Registry fields (lifecycleProfile, lifecycleStatus, lifecycle.enabled)
 * - kernel-product.yaml fields (lifecycleProfile, surfaces, deployment adapter, etc.)
 *
 * Reports drift between the two sources of truth.
 */

import { existsSync, readFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(__dirname, '..');

function loadRegistry() {
  const registryPath = join(repoRoot, 'config/canonical-product-registry.json');
  return JSON.parse(readFileSync(registryPath, 'utf8')).registry;
}

async function parseYaml(content) {
  const { parse } = await import('yaml');
  return parse(content);
}

async function checkDrift(productId, product) {
  const driftErrors = [];
  const warnings = [];

  if (!product.lifecycleConfigPath) {
    warnings.push(`Product ${productId}: no lifecycleConfigPath declared — skipping YAML drift check`);
    return { driftErrors, warnings };
  }

  const yamlPath = join(repoRoot, product.lifecycleConfigPath);
  if (!existsSync(yamlPath)) {
    driftErrors.push(`Product ${productId}: kernel-product.yaml declared at "${product.lifecycleConfigPath}" does not exist`);
    return { driftErrors, warnings };
  }

  let config;
  try {
    const content = readFileSync(yamlPath, 'utf8');
    config = await parseYaml(content);
  } catch (err) {
    driftErrors.push(`Product ${productId}: failed to parse kernel-product.yaml: ${err instanceof Error ? err.message : String(err)}`);
    return { driftErrors, warnings };
  }

  // Check productId field matches
  if (config?.productId && config.productId !== productId) {
    driftErrors.push(
      `Product ${productId}: registry ID "${productId}" does not match YAML productId "${config.productId}"`,
    );
  }

  // Check lifecycleProfile field matches
  if (config?.lifecycleProfile && config.lifecycleProfile !== product.lifecycleProfile) {
    driftErrors.push(
      `Product ${productId}: registry lifecycleProfile "${product.lifecycleProfile}" does not match YAML lifecycleProfile "${config.lifecycleProfile}"`,
    );
  }

  // Check that registry surfaces match YAML surfaces
  const registrySurfaces = (product.surfaces ?? []).map((s) => s.type).sort();
  const yamlSurfaces = Object.keys(config?.surfaces ?? {}).sort();

  if (registrySurfaces.length > 0 && yamlSurfaces.length > 0) {
    const missingInYaml = registrySurfaces.filter((s) => !yamlSurfaces.includes(s));
    const missingInRegistry = yamlSurfaces.filter((s) => !registrySurfaces.includes(s));

    for (const surface of missingInYaml) {
      warnings.push(`Product ${productId}: surface "${surface}" is in registry but not in kernel-product.yaml`);
    }
    for (const surface of missingInRegistry) {
      warnings.push(`Product ${productId}: surface "${surface}" is in kernel-product.yaml but not in registry`);
    }
  }

  // Check deployment adapter declared in registry matches YAML
  const registryDeploymentTargets = product.deployment?.targets ?? [];
  const yamlDeploymentLocal = config?.deployment?.local;

  if (yamlDeploymentLocal?.adapter) {
    const registryHasComposeLocal = registryDeploymentTargets.some(
      (t) => t.type === 'compose-local' || t === 'compose-local',
    );
    if (yamlDeploymentLocal.adapter === 'compose-local' && !registryHasComposeLocal && registryDeploymentTargets.length > 0) {
      warnings.push(
        `Product ${productId}: YAML declares compose-local deployment but registry deployment.targets does not include compose-local`,
      );
    }
  }

  // Check that registry deployment environments match YAML deployment environments
  const registryEnvironments = Object.keys(product.environments ?? {}).sort();
  const yamlDeploymentEnvs = Object.keys(config?.deployment ?? {}).sort();
  const yamlVerifyEnvs = Object.keys(config?.verify ?? {}).sort();
  const yamlEnvs = [...new Set([...yamlDeploymentEnvs, ...yamlVerifyEnvs])].sort();

  if (registryEnvironments.length > 0 && yamlEnvs.length > 0) {
    for (const env of registryEnvironments) {
      if (!yamlEnvs.includes(env)) {
        warnings.push(`Product ${productId}: environment "${env}" is in registry but not in kernel-product.yaml deployment/verify`);
      }
    }
  }

  return { driftErrors, warnings };
}

async function main() {
  const errors = [];
  const warnings = [];

  const registry = loadRegistry();
  const enabledProducts = Object.entries(registry).filter(
    ([, product]) => product.lifecycleStatus === 'enabled' || product.lifecycle?.enabled === true,
  );

  if (enabledProducts.length === 0) {
    console.log('No lifecycle-enabled products found — nothing to check');
    return;
  }

  for (const [productId, product] of enabledProducts) {
    const { driftErrors, warnings: productWarnings } = await checkDrift(productId, product);
    errors.push(...driftErrors);
    warnings.push(...productWarnings);
  }

  if (warnings.length > 0) {
    console.warn('Warnings:');
    for (const w of warnings) {
      console.warn(`  - ${w}`);
    }
  }

  if (errors.length > 0) {
    console.error(`\nLifecycle registry/config drift check FAILED (${errors.length} error(s)):`);
    for (const e of errors) {
      console.error(`  - ${e}`);
    }
    process.exit(1);
  }

  console.log(`Lifecycle registry/config drift check passed (${enabledProducts.length} enabled product(s) checked)`);
}

try {
  await main();
} catch (error) {
  console.error(`Check failed: ${error instanceof Error ? error.message : String(error)}`);
  process.exit(1);
}
