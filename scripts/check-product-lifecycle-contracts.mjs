#!/usr/bin/env node

import { execFileSync } from 'node:child_process';
import { existsSync, readFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { checkProductInteractionContracts } from './check-product-interaction-contracts.mjs';

const __dirname = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(__dirname, '..');
const registryPath = join(repoRoot, 'config/canonical-product-registry.json');
const lifecycleProfilesPath = join(repoRoot, 'config/product-lifecycle-profiles.json');
const toolchainRegistryPath = join(repoRoot, 'config/toolchain-adapter-registry.json');
const lifecycleExclusionsPath = join(repoRoot, 'config/kernel-lifecycle-exclusions.json');

function loadRegistry() {
  return JSON.parse(readFileSync(registryPath, 'utf8')).registry;
}

function loadLifecycleProfiles() {
  return JSON.parse(readFileSync(lifecycleProfilesPath, 'utf8')).profiles ?? {};
}

function loadToolchainRegistry() {
  return JSON.parse(readFileSync(toolchainRegistryPath, 'utf8')).adapters ?? {};
}

function loadLifecycleExclusions() {
  return JSON.parse(readFileSync(lifecycleExclusionsPath, 'utf8')).excludedProducts ?? {};
}

export async function checkProductLifecycleContracts(registry, lifecycleProfiles, toolchains, excludedProducts, options = {}) {
  const errors = [];
  const warnings = [];
  const parseLifecycleConfig =
    options.parseLifecycleConfig ??
    (async (configPath) => {
      const { parseDocument } = await import('yaml');
      const yamlContent = readFileSync(configPath, 'utf8');
      return parseDocument(yamlContent).toJSON();
    });
  const runPlan =
    options.runPlan ??
    ((productId, planPhase, extraArgs) => {
      execFileSync(
        process.execPath,
        [join(repoRoot, 'scripts', 'kernel-product.mjs'), 'product', productId, 'plan', planPhase, '--json', ...extraArgs],
        { cwd: repoRoot, stdio: 'pipe', encoding: 'utf8' },
      );
    });

  for (const [productId, product] of Object.entries(registry)) {
    const isExcluded = Boolean(excludedProducts[productId]);
    if (isExcluded) {
      if (product.lifecycleStatus === 'enabled') {
        errors.push(`Excluded product ${productId}: lifecycleStatus must not be enabled`);
      }
      if (product.lifecycle?.enabled === true) {
        errors.push(`Excluded product ${productId}: lifecycle.enabled must not be true`);
      }
      if (product.lifecycleConfigPath) {
        errors.push(`Excluded product ${productId}: lifecycleConfigPath must not be declared`);
      }
      continue;
    }

    if (!product.lifecycleProfile) {
      continue;
    }

    if (!lifecycleProfiles[product.lifecycleProfile]) {
      errors.push(`Product ${productId}: lifecycle profile "${product.lifecycleProfile}" not found in registry`);
    }

    const lifecycleStatus = product.lifecycleStatus ?? (product.lifecycle?.enabled ? 'enabled' : 'partial');
    if (!['disabled', 'planned', 'partial', 'enabled'].includes(lifecycleStatus)) {
      errors.push(`Product ${productId}: lifecycleStatus "${lifecycleStatus}" is invalid`);
    }

    if (lifecycleStatus === 'enabled') {
      if (!product.lifecycleConfigPath) {
        errors.push(`Product ${productId}: enabled lifecycle product must declare lifecycleConfigPath`);
        continue;
      }

      const configPath = join(repoRoot, product.lifecycleConfigPath);
      if (!existsSync(configPath)) {
        errors.push(`Product ${productId}: lifecycle config file not found at ${product.lifecycleConfigPath}`);
        continue;
      }

      if (!product.lifecycle?.enabled) {
        errors.push(`Product ${productId}: lifecycleStatus is enabled but lifecycle.enabled is not true`);
      }

      const lifecycleProfile = lifecycleProfiles[product.lifecycleProfile];
      if (lifecycleProfile && lifecycleProfile.safeForDefault !== true) {
        errors.push(`Product ${productId}: enabled lifecycle product must use a safeForDefault lifecycle profile`);
      }

      if (!product.artifacts || Object.keys(product.artifacts).length === 0) {
        errors.push(`Product ${productId}: enabled lifecycle product is missing artifact declarations`);
      }

      if (!product.deployment?.targets?.length) {
        errors.push(`Product ${productId}: enabled lifecycle product is missing deployment targets`);
      }

      const enabledAdapters = new Set([
        ...Object.values(product.lifecycle?.toolchain ?? {}),
        ...Object.values(product.toolchain?.adapters ?? {}),
      ]);

      for (const adapterId of enabledAdapters) {
        if (!toolchains[adapterId]) {
          errors.push(`Product ${productId}: lifecycle references unknown adapter ${adapterId}`);
          continue;
        }
        if (toolchains[adapterId].safeForDefault !== true) {
          errors.push(`Product ${productId}: enabled lifecycle product cannot use unsafe adapter ${adapterId}`);
        }
      }

      let lifecycleConfig = undefined;
      try {
        lifecycleConfig = await parseLifecycleConfig(configPath);
      } catch (error) {
        errors.push(`Product ${productId}: lifecycle config failed to parse: ${error instanceof Error ? error.message : String(error)}`);
      }

      if (lifecycleConfig !== undefined) {
        errors.push(...validateExecutableManifestRequirements(productId, lifecycleConfig));
      }

      for (const planPhase of ['validate', 'test', 'build', 'package', 'deploy', 'verify']) {
        const extraArgs = planPhase === 'deploy' || planPhase === 'verify' ? ['--env', 'local'] : [];
        try {
          runPlan(productId, planPhase, extraArgs);
        } catch (error) {
          errors.push(`Product ${productId}: ${planPhase} plan generation failed: ${error instanceof Error ? error.message : String(error)}`);
        }
      }

      // Validate registry/YAML config drift: lifecycleConfigPath should declare same profile as registry
      if (product.lifecycleConfigPath) {
        const configPath = join(repoRoot, product.lifecycleConfigPath);
        if (existsSync(configPath)) {
          try {
            const yaml = await parseLifecycleConfig(configPath);
            const yamlProfile = yaml?.lifecycleProfile;
            if (yamlProfile && yamlProfile !== product.lifecycleProfile) {
              errors.push(
                `Product ${productId}: registry lifecycleProfile "${product.lifecycleProfile}" does not match YAML lifecycleProfile "${yamlProfile}"`,
              );
            }
          } catch {
            // YAML parsing is best-effort; file may not have lifecycleProfile field
          }
        }
      }
    } else {
      if (product.lifecycle?.enabled) {
        warnings.push(`Product ${productId}: lifecycle.enabled is true while lifecycleStatus is ${lifecycleStatus}`);
      }
      if (product.lifecycleConfigPath) {
        const configPath = join(repoRoot, product.lifecycleConfigPath);
        if (!existsSync(configPath)) {
          warnings.push(`Product ${productId}: lifecycle config file missing at ${product.lifecycleConfigPath}`);
        }
      } else {
        warnings.push(`Product ${productId}: no lifecycleConfigPath declared while status is ${lifecycleStatus}`);
      }
    }
  }

  if (options.validateInteractions !== false) {
    const interactionResult = checkProductInteractionContracts(
      registry,
      options.interactionOptions,
    );
    if (interactionResult.errors.length > 0) {
      errors.push(...interactionResult.errors.map((error) => `Interaction contract: ${error}`));
    }
  }

  return { errors, warnings };
}

function validateExecutableManifestRequirements(productId, lifecycleConfig) {
  const errors = [];
  const requiredManifests = lifecycleConfig?.requiredManifests ?? {};
  const packageConfig = lifecycleConfig?.package ?? {};
  const deploymentConfig = lifecycleConfig?.deployment ?? {};
  const verifyConfig = lifecycleConfig?.verify ?? {};

  if (Object.keys(packageConfig).length > 0) {
    assertManifestIncludes(errors, productId, 'package', requiredManifests.package, ['artifact-manifest', 'lifecycle-health-snapshot']);
  }
  if (Object.keys(deploymentConfig).length > 0) {
    assertManifestIncludes(errors, productId, 'deploy', requiredManifests.deploy, ['deployment-manifest', 'lifecycle-health-snapshot']);
  }
  if (Object.keys(verifyConfig).length > 0) {
    assertManifestIncludes(errors, productId, 'verify', requiredManifests.verify, ['verify-health-report', 'lifecycle-health-snapshot']);
  }

  return errors;
}

function assertManifestIncludes(errors, productId, phase, actual, expected) {
  if (!Array.isArray(actual)) {
    errors.push(`Product ${productId}: enabled lifecycle ${phase} phase must declare requiredManifests.${phase}`);
    return;
  }
  const actualSet = new Set(actual);
  for (const manifestName of expected) {
    if (!actualSet.has(manifestName)) {
      errors.push(`Product ${productId}: requiredManifests.${phase} missing ${manifestName}`);
    }
  }
}

async function main() {
  const registry = loadRegistry();
  const lifecycleProfiles = loadLifecycleProfiles();
  const toolchains = loadToolchainRegistry();
  const excludedProducts = loadLifecycleExclusions();
  const { errors, warnings } = await checkProductLifecycleContracts(registry, lifecycleProfiles, toolchains, excludedProducts);

  if (warnings.length > 0) {
    console.warn('Warnings:');
    for (const warning of warnings) {
      console.warn(`  - ${warning}`);
    }
  }

  if (errors.length > 0) {
    console.error('Errors:');
    for (const error of errors) {
      console.error(`  - ${error}`);
    }
    process.exit(1);
  }

  console.log('All product lifecycle contracts are valid');
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try {
    await main();
  } catch (error) {
    console.error(`Lifecycle contract check failed: ${error instanceof Error ? error.message : String(error)}`);
    process.exit(1);
  }
}
