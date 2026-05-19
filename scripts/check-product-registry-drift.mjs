#!/usr/bin/env node

import { existsSync, readFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '..');

function readJson(relativePath) {
  return JSON.parse(readFileSync(path.join(repoRoot, relativePath), 'utf8'));
}

function fileExists(relativePath) {
  return existsSync(path.join(repoRoot, relativePath));
}

const LIFECYCLE_ENABLED_KINDS = new Set(['business-product', 'shared-service']);
const PLATFORM_PROVIDER_KINDS = new Set(['platform-provider']);

function readReleasePlan(root) {
  const releasePlanPath = path.join(root, 'config/platform-release-plan.json');
  if (!existsSync(releasePlanPath)) {
    return {
      openingLifecyclePilots: ['digital-marketing'],
      disabledUntilReady: ['phr', 'finance', 'flashit'],
      platformProviderValidators: ['data-cloud', 'yappc'],
    };
  }
  return JSON.parse(readFileSync(releasePlanPath, 'utf8'));
}

export function checkProductRegistryDrift(options = {}) {
  const registryPath = options.registryPath ?? 'config/canonical-product-registry.json';
  const root = options.repoRoot ?? repoRoot;
  const pathExists = options.pathExists ?? ((relativePath) => existsSync(path.join(root, relativePath)));

  let registry;
  try {
    registry = JSON.parse(readFileSync(path.join(root, registryPath), 'utf8'));
  } catch (error) {
    return [`FATAL: Failed to load product registry at ${registryPath}: ${error.message}`];
  }

  const violations = [];
  const releasePlan = options.releasePlan ?? readReleasePlan(root);
  const openingLifecyclePilots = new Set(releasePlan.openingLifecyclePilots ?? ['digital-marketing']);
  const disabledUntilReady = releasePlan.disabledUntilReady ?? ['finance', 'flashit'];

  if (!registry.registry || typeof registry.registry !== 'object') {
    violations.push('Product registry missing "registry" object.');
    return violations;
  }

  // Load pnpm-workspace.yaml for package mapping checks
  let pnpmWorkspace = '';
  try {
    pnpmWorkspace = readFileSync(path.join(root, 'pnpm-workspace.yaml'), 'utf8');
  } catch {
    violations.push('Warning: pnpm-workspace.yaml not found, skipping pnpm package mapping checks.');
  }

  // Load generated Gradle includes for module mapping checks
  let gradleIncludes = '';
  const gradleIncludesPath = path.join(root, 'config/generated/settings-gradle-includes.kts');
  try {
    gradleIncludes = readFileSync(gradleIncludesPath, 'utf8');
  } catch {
    // Generated file may not exist yet; skip gradle checks
  }

  // Load settings.gradle.kts for root includes
  let settingsGradle = '';
  try {
    settingsGradle = readFileSync(path.join(root, 'settings.gradle.kts'), 'utf8');
  } catch {
    // Skip if not found
  }

  for (const [productId, product] of Object.entries(registry.registry)) {
    const lifecycleEnabled = product.lifecycle?.enabled === true;
    const lifecycleStatus = product.lifecycleStatus;
    const lifecycleExecutionAllowed = product.lifecycleExecutionAllowed === true;
    const isDemoExample = product.kind === 'demo/example';
    const requiresWorkspaceWiring = !isDemoExample || lifecycleEnabled || lifecycleExecutionAllowed;

    // Validate pnpmPackages exist in pnpm-workspace.yaml
    if (pnpmWorkspace && Array.isArray(product.pnpmPackages)) {
      for (const pkg of product.pnpmPackages) {
        // pnpmPackages use glob patterns; check if the base pattern appears in workspace
        const basePattern = pkg.replace(/\/\*$/, '');
        if (!pnpmWorkspace.includes(basePattern) && !pnpmWorkspace.includes(pkg)) {
          violations.push(
            `${productId}: pnpmPackages entry '${pkg}' not found in pnpm-workspace.yaml. ` +
              'Run: node scripts/generate-product-registry-artifacts.mjs',
          );
        }
      }
    }

    // Validate Gradle modules exist in generated includes or root settings
    if (Array.isArray(product.gradleModules) && requiresWorkspaceWiring) {
      for (const module of product.gradleModules) {
        const includeStatement = `include("${module}")`;
        if (!gradleIncludes.includes(includeStatement) && !settingsGradle.includes(includeStatement)) {
          violations.push(
            `${productId}: Gradle module '${module}' not found in generated includes or root settings. ` +
              'Run: node scripts/generate-product-registry-artifacts.mjs',
          );
        }
      }
    }

    if (Array.isArray(product.gradleModules) && !requiresWorkspaceWiring) {
      if (
        !product.lifecycleReadiness?.reasonCodes ||
        product.lifecycleReadiness.reasonCodes.length === 0
      ) {
        violations.push(
          `${productId}: demo/example product is not workspace-wired; add lifecycleReadiness.reasonCodes to document explicit drift exemption.`,
        );
      }
    }

    // Validate lifecycle-enabled products have required fields
    if (lifecycleEnabled) {
      if (lifecycleStatus !== 'enabled') {
        violations.push(
          `${productId}: lifecycle.enabled is true but lifecycleStatus is '${lifecycleStatus}', expected 'enabled'.`,
        );
      }

      if (!product.lifecycleConfigPath) {
        violations.push(
          `${productId}: lifecycle.enabled but missing lifecycleConfigPath.`,
        );
      }

      if (!Array.isArray(product.surfaces) || product.surfaces.length === 0) {
        violations.push(
          `${productId}: lifecycle.enabled but has no surfaces declared.`,
        );
      }

      // Validate toolchain mapping for every default surface
      const toolchainAdapters = product.toolchain?.adapters ?? product.lifecycle?.toolchain ?? {};
      if (Array.isArray(product.surfaces)) {
        for (const surface of product.surfaces) {
          if (!toolchainAdapters[surface.type]) {
            violations.push(
              `${productId}: lifecycle.enabled but surface '${surface.type}' has no toolchain mapping.`,
            );
          }
        }
      }

      // Validate artifact declaration for required build surfaces
      if (Array.isArray(product.surfaces)) {
        for (const surface of product.surfaces) {
          const artifactDecl = product.artifacts?.[surface.type];
          if (!artifactDecl) {
            violations.push(
              `${productId}: lifecycle.enabled but surface '${surface.type}' has no artifact declaration.`,
            );
          }
        }
      }

      // Validate deployment target
      if (!product.deployment || !Array.isArray(product.deployment.targets) || product.deployment.targets.length === 0) {
        violations.push(
          `${productId}: lifecycle.enabled but no deployment targets declared.`,
        );
      }

      // Validate health checks
      if (!product.deployment?.healthChecks || product.deployment.healthChecks.length === 0) {
        violations.push(
          `${productId}: lifecycle.enabled but no health checks declared.`,
        );
      }

      // Validate lifecycle migration status
      if (product.lifecycleMigration?.status !== 'ready') {
        violations.push(
          `${productId}: lifecycle.enabled but lifecycleMigration.status is '${product.lifecycleMigration?.status}', expected 'ready'.`,
        );
      }
    }

    // Validate lifecycle-disabled products have reason codes
    if (lifecycleStatus && lifecycleStatus !== 'enabled') {
      if (
        !product.lifecycleReadiness?.reasonCodes ||
        product.lifecycleReadiness.reasonCodes.length === 0
      ) {
        violations.push(
          `${productId}: lifecycleStatus is '${lifecycleStatus}' but no lifecycleReadiness.reasonCodes provided. ` +
            'Add explicit reason codes for why lifecycle is not enabled.',
        );
      }
    }

    // Validate platform-provider products cannot be treated as ordinary lifecycle targets
    if (PLATFORM_PROVIDER_KINDS.has(product.kind)) {
      if (lifecycleEnabled && product.manifestPath === null) {
        violations.push(
          `${productId}: kind is '${product.kind}' with null manifest. ` +
            'Platform-provider products cannot be treated as ordinary business-product lifecycle targets.',
        );
      }
    }
  }

  // Validate release-plan lifecycle pilots remain executable.
  for (const productId of openingLifecyclePilots) {
    const product = registry.registry[productId];
    if (!product) {
      violations.push(`${productId}: opening lifecycle pilot is missing from canonical registry.`);
      continue;
    }
    if (product.lifecycleStatus !== 'enabled') {
      violations.push(
        `${productId}: must remain lifecycleStatus "enabled" as an opening lifecycle pilot. ` +
          `Current status: '${product.lifecycleStatus}'.`,
      );
    }
    if (product.lifecycle?.enabled !== true || product.lifecycleExecutionAllowed !== true) {
      violations.push(
        `${productId}: must keep lifecycle.enabled and lifecycleExecutionAllowed true as an opening lifecycle pilot.`,
      );
    }
  }

  // Validate non-pilot products remain disabled/planned until their readiness gates change.
  for (const productId of disabledUntilReady) {
    const product = registry.registry[productId];
    if (product && (product.lifecycle?.enabled === true || product.lifecycleExecutionAllowed === true)) {
      violations.push(
        `${productId}: lifecycle must remain disabled until release-plan readiness changes. ` +
          'Current lifecycle execution is enabled.',
      );
    }
  }

  return violations;
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const violations = checkProductRegistryDrift();

  if (violations.length === 0) {
    console.log('OK: product registry drift checks passed.');
    process.exit(0);
  }

  console.error('FAIL: product registry drift checks found violations:');
  for (const violation of violations) {
    console.error(` - ${violation}`);
  }
  process.exit(1);
}
