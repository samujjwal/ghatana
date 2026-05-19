#!/usr/bin/env node
// Authoritative Source: config/canonical-product-registry.json
// Task 1.2 — Validate product registry against generated includes

/**
 * Validates that generated artifact files are in sync with the canonical
 * product registry. Checks:
 *   1. Every gradleModule in the registry appears in
 *      config/generated/settings-gradle-includes.kts
 *   2. Every pnpmPackage glob in the registry appears in pnpm-workspace.yaml
 */

import { readFileSync, existsSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '..');

const REGISTRY_PATH = path.join(repoRoot, 'config/canonical-product-registry.json');
const GRADLE_INCLUDES_PATH = path.join(repoRoot, 'config/generated/settings-gradle-includes.kts');
const PNPM_WORKSPACE_PATH = path.join(repoRoot, 'pnpm-workspace.yaml');

function loadRegistry() {
  if (!existsSync(REGISTRY_PATH)) {
    throw new Error(`Canonical product registry not found: ${REGISTRY_PATH}`);
  }
  return JSON.parse(readFileSync(REGISTRY_PATH, 'utf8'));
}

function loadText(filePath, label) {
  if (!existsSync(filePath)) {
    throw new Error(`Required file not found: ${filePath} (${label})`);
  }
  return readFileSync(filePath, 'utf8');
}

function checkGradleIncludes(registry, gradleIncludesContent) {
  const violations = [];
  const gradleKinds = new Set(['business-product', 'platform-provider', 'shared-service']);

  for (const [productId, product] of Object.entries(registry)) {
    if (!gradleKinds.has(product.kind)) continue;
    const modules = product.gradleModules ?? [];
    for (const module of modules) {
      // Modules in registry use colon format: ":products:phr"
      // Generated file uses: include(":products:phr")
      const expected = `include("${module}")`;
      if (!gradleIncludesContent.includes(expected)) {
        violations.push(
          `[${productId}] gradleModule "${module}" missing from config/generated/settings-gradle-includes.kts`
        );
      }
    }
  }
  return violations;
}

function checkPnpmWorkspace(registry, pnpmWorkspaceContent) {
  const violations = [];

  for (const [productId, product] of Object.entries(registry)) {
    const packages = product.pnpmPackages ?? [];
    for (const pkg of packages) {
      // pnpm-workspace.yaml should contain the package glob as a list item
      // Match both quoted and unquoted forms: `  - "products/phr/apps/*"` or `  - products/phr/apps/*`
      const quotedForm = `"${pkg}"`;
      const rawForm = `- ${pkg}`;
      if (
        !pnpmWorkspaceContent.includes(quotedForm) &&
        !pnpmWorkspaceContent.includes(rawForm)
      ) {
        violations.push(
          `[${productId}] pnpmPackage "${pkg}" missing from pnpm-workspace.yaml`
        );
      }
    }
  }
  return violations;
}

function run() {
  console.log('=== check-product-generated-includes ===\n');

  const registryData = loadRegistry();
  const registry = registryData.registry ?? {};
  const productCount = Object.keys(registry).length;
  console.log(`Loaded registry with ${productCount} products`);

  const gradleIncludesContent = loadText(GRADLE_INCLUDES_PATH, 'Gradle includes');
  const pnpmWorkspaceContent = loadText(PNPM_WORKSPACE_PATH, 'pnpm-workspace.yaml');

  const gradleViolations = checkGradleIncludes(registry, gradleIncludesContent);
  const pnpmViolations = checkPnpmWorkspace(registry, pnpmWorkspaceContent);

  const allViolations = [...gradleViolations, ...pnpmViolations];

  if (gradleViolations.length > 0) {
    console.error('\n✗ Gradle include violations:');
    for (const v of gradleViolations) {
      console.error(`  - ${v}`);
    }
  } else {
    console.log('✓ All gradleModules present in settings-gradle-includes.kts');
  }

  if (pnpmViolations.length > 0) {
    console.error('\n✗ pnpm workspace violations:');
    for (const v of pnpmViolations) {
      console.error(`  - ${v}`);
    }
  } else {
    console.log('✓ All pnpmPackages present in pnpm-workspace.yaml');
  }

  if (allViolations.length > 0) {
    console.error(
      `\n✗ ${allViolations.length} violation(s) found. Run:\n` +
        `  node scripts/generate-product-registry-artifacts.mjs\n` +
        `to regenerate the includes, then commit the updated files.`
    );
    process.exit(1);
  }

  console.log('\nOK: product-generated-includes checks passed.');
}

run();
