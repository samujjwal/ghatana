#!/usr/bin/env node

/**
 * Product Registry Artifact Generator
 * 
 * Generates all derived artifacts from the canonical product registry:
 * - product-shape.json (for UI/product shell consumption)
 * - Gradle include files (settings.gradle.kts fragments)
 * - pnpm-workspace.yaml entries
 * - CI matrix configurations
 * - Root package.json scripts
 * 
 * This ensures single source of truth for product registration.
 */

import { execFileSync } from 'node:child_process';
import { readFileSync, writeFileSync, existsSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '..');

// Paths
const REGISTRY_PATH = path.join(repoRoot, 'config/canonical-product-registry.json');
const SCHEMA_PATH = path.join(repoRoot, 'config/canonical-product-registry-schema.json');
const PRODUCT_SHAPE_PATH = path.join(repoRoot, 'config/product-shape.json');
const SETTINGS_OUTPUT_PATH = path.join(repoRoot, 'config/generated/settings-gradle-includes.kts');
const PNPM_OUTPUT_PATH = path.join(repoRoot, 'config/generated/pnpm-workspace-entries.yaml');
const CI_MATRIX_OUTPUT_PATH = path.join(repoRoot, 'config/generated/ci-matrix.json');
const PACKAGE_SCRIPTS_OUTPUT_PATH = path.join(repoRoot, 'config/generated/package-scripts.json');
const PNPM_WORKSPACE_PATH = path.join(repoRoot, 'pnpm-workspace.yaml');
const WORKSPACE_DEPENDENCY_POLICY_PATH = path.join(repoRoot, 'config/workspace-dependency-policy.json');
const PRODUCT_GRADLE_KINDS = new Set(['business-product', 'platform-provider', 'shared-service']);
const PRODUCT_CI_KINDS = new Set(['business-product', 'platform-provider', 'shared-service']);
const PRODUCT_SCRIPT_KINDS = new Set(['business-product', 'platform-provider', 'shared-service']);
const checkMode = process.argv.includes('--check');
const staleArtifacts = [];

function writeGeneratedFile(filePath, content, label) {
  if (checkMode) {
    const existing = existsSync(filePath) ? readFileSync(filePath, 'utf8') : null;
    if (existing !== content) {
      staleArtifacts.push(`${label}: ${path.relative(repoRoot, filePath)}`);
    }
    return;
  }

  writeFileSync(filePath, content);
}

// Load canonical registry
function loadRegistry() {
  if (!existsSync(REGISTRY_PATH)) {
    throw new Error(`Canonical product registry not found: ${REGISTRY_PATH}`);
  }
  const content = readFileSync(REGISTRY_PATH, 'utf8');
  return JSON.parse(content);
}

function loadWorkspaceDependencyPolicy() {
  if (!existsSync(WORKSPACE_DEPENDENCY_POLICY_PATH)) {
    throw new Error(`Workspace dependency policy not found: ${WORKSPACE_DEPENDENCY_POLICY_PATH}`);
  }
  const content = readFileSync(WORKSPACE_DEPENDENCY_POLICY_PATH, 'utf8');
  return JSON.parse(content);
}

// Validate registry against schema (basic validation)
function validateRegistry(registry) {
  if (!registry.version) {
    throw new Error('Registry missing version field');
  }
  if (!registry.registry || typeof registry.registry !== 'object') {
    throw new Error('Registry missing registry object');
  }
  
  const productIds = Object.keys(registry.registry);
  if (productIds.length === 0) {
    throw new Error('Registry contains no products');
  }
  
  for (const [productId, product] of Object.entries(registry.registry)) {
    if (!product.kind) {
      throw new Error(`Registry entry ${productId} must declare kind`);
    }
    if (product.conformance?.manifest === true) {
      if (!product.manifestPath || !product.manifestFormat) {
        throw new Error(`Registry entry ${productId} enables manifest conformance but has no manifestPath/manifestFormat`);
      }
      if (!product.buildFile) {
        throw new Error(`Registry entry ${productId} enables manifest conformance but has no explicit buildFile`);
      }
    }

    for (const surface of product.surfaces || []) {
      if (!surface.implementationStatus) {
        throw new Error(`Registry entry ${productId} surface ${surface.type} must declare implementationStatus`);
      }
      if (!['implemented', 'planned', 'backend-only'].includes(surface.implementationStatus)) {
        throw new Error(`Registry entry ${productId} surface ${surface.type} has invalid implementationStatus: ${surface.implementationStatus}`);
      }
    }
  }

  console.log(`Validated registry with ${productIds.length} entries`);
  return productIds;
}

// Generate product-shape.json
function generateProductShape(registry) {
  const products = {};

  for (const [productId, product] of Object.entries(registry.registry)) {
    const surfaces = product.surfaces || [];
    const uiSurfaces = surfaces.filter(s => s.type === 'web' || s.type === 'mobile');
    const backendOnly = uiSurfaces.length === 0;

    products[productId] = {
      ui: !backendOnly,
      uiMode: backendOnly ? 'backend-only' : (uiSurfaces.length > 1 ? 'multi-surface' : 'web'),
      surfaces: surfaces.map(s => s.type),
      surfaceStatuses: Object.fromEntries(
        surfaces.map(s => [s.type, s.implementationStatus])
      ),
      clientPackages: uiSurfaces
        .filter(s => s.packagePath)
        .map(s => s.packagePath)
    };

    // Add lifecycle metadata if present
    if (product.lifecycleProfile) {
      products[productId].lifecycle = {
        profile: product.lifecycleProfile,
        enabled: product.lifecycle?.enabled || false,
        configPath: product.lifecycleConfigPath || null
      };
    }

    if (backendOnly && product.metadata?.documentation) {
      products[productId].backendOnlyDeclaration = {
        file: product.metadata.documentation,
        mustContain: 'backend-only'
      };
    }
  }

  const output = { products };
  writeGeneratedFile(PRODUCT_SHAPE_PATH, JSON.stringify(output, null, 2) + '\n', 'product shape');
  console.log(`${checkMode ? 'Checked' : 'Generated'} product-shape.json with ${Object.keys(products).length} products`);
}

// Generate Gradle include blocks
function generateGradleIncludes(registry) {
  const lines = [];
  lines.push('// Auto-generated from canonical-product-registry.json');
  lines.push('// DO NOT EDIT MANUALLY - run: node scripts/generate-product-registry-artifacts.mjs');
  lines.push('');
  
  const products = Object.values(registry.registry);
  
  lines.push('// =============================================================================');
  lines.push('// Products (from canonical registry)');
  lines.push('// =============================================================================');
  
  for (const product of products) {
    if (PRODUCT_GRADLE_KINDS.has(product.kind)) {
      lines.push(`// ${product.kind}: ${product.name} (${product.id})`);
      for (const module of product.gradleModules || []) {
        lines.push(`include("${module}")`);
      }
      lines.push('');
    }
  }
  
  writeGeneratedFile(SETTINGS_OUTPUT_PATH, lines.join('\n'), 'Gradle includes');
  console.log(`${checkMode ? 'Checked' : 'Generated'} Gradle includes at ${SETTINGS_OUTPUT_PATH}`);
}

// Generate pnpm workspace entries
function generatePnpmEntries(registry, dependencyPolicy) {
  const lines = [];
  lines.push('# Auto-generated from canonical-product-registry.json');
  lines.push('# DO NOT EDIT MANUALLY - run: node scripts/generate-product-registry-artifacts.mjs');
  lines.push('packages:');
  
  // Add platform typescript packages (always included)
  lines.push('  # Platform TypeScript libraries');
  lines.push('  - "platform/typescript/*"');
  lines.push('  - "platform/typescript/foundation/*"');
  lines.push('  - "platform/typescript/canvas/*"');
  lines.push('  - "platform/typescript/ghatana-studio"');
  lines.push('');
  
  // Add product packages
  lines.push('  # Product packages (from canonical registry)');
  for (const product of Object.values(registry.registry)) {
    if (product.pnpmPackages && product.pnpmPackages.length > 0) {
      lines.push(`  # ${product.name} (${product.id})`);
      for (const pkg of product.pnpmPackages) {
        lines.push(`  - "${pkg}"`);
      }
      lines.push('');
    }
  }
  
  // Add shared services
  lines.push('  # Shared services');
  lines.push('  - "shared-services/*/ui"');

  const catalogEntries = Object.entries(dependencyPolicy.catalog ?? {}).sort(([left], [right]) => left.localeCompare(right));
  if (catalogEntries.length > 0) {
    lines.push('');
    lines.push('catalog:');
    for (const [dependencyName, version] of catalogEntries) {
      lines.push(`  ${JSON.stringify(dependencyName)}: ${JSON.stringify(version)}`);
    }
  }
  
  const output = lines.join('\n') + '\n';
  writeGeneratedFile(PNPM_OUTPUT_PATH, output, 'pnpm workspace entries');
  writeGeneratedFile(PNPM_WORKSPACE_PATH, output, 'root pnpm workspace');
  console.log(`${checkMode ? 'Checked' : 'Generated'} pnpm workspace entries at ${PNPM_OUTPUT_PATH}`);
  console.log(`${checkMode ? 'Checked' : 'Updated'} root pnpm workspace at ${PNPM_WORKSPACE_PATH}`);
}

// Generate CI matrix configuration
function generateCIMatrix(registry) {
  const matrix = {
    products: [],
    productsWithUI: [],
    productsWithTests: [],
    productsWithIntegrationTests: [],
    productsWithLifecycle: [],
    lifecycleProfiles: []
  };

  for (const [productId, product] of Object.entries(registry.registry)) {
    if (product.ci?.enabled && PRODUCT_CI_KINDS.has(product.kind)) {
      matrix.products.push(productId);

      const hasUI = product.surfaces?.some(s => s.type === 'web' || s.type === 'mobile');
      if (hasUI) {
        matrix.productsWithUI.push(productId);
      }

      const gates = product.ci?.gates || [];
      if (gates.includes('test') || gates.includes('integration-test')) {
        matrix.productsWithTests.push(productId);
      }

      if (gates.includes('integration-test')) {
        matrix.productsWithIntegrationTests.push(productId);
      }

      // Add lifecycle metadata
      if (product.lifecycleProfile && product.lifecycle?.enabled) {
        matrix.productsWithLifecycle.push(productId);
        if (!matrix.lifecycleProfiles.includes(product.lifecycleProfile)) {
          matrix.lifecycleProfiles.push(product.lifecycleProfile);
        }
      }
    }
  }

  writeGeneratedFile(CI_MATRIX_OUTPUT_PATH, JSON.stringify(matrix, null, 2) + '\n', 'CI matrix');
  console.log(`${checkMode ? 'Checked' : 'Generated'} CI matrix with ${matrix.products.length} products (${matrix.productsWithLifecycle.length} with lifecycle)`);
}

// Generate root package.json scripts
function generatePackageScripts(registry) {
  const scripts = {};
  
  // Generic platform scripts
  scripts['build'] = 'pnpm -r --filter \'./platform/typescript/**\' --filter \'./products/*/ui\' build';
  scripts['build:platform'] = 'pnpm -r --filter \'./platform/typescript/**\' build';
  scripts['dev'] = 'pnpm -r --parallel --filter \'./products/*/ui\' dev';
  scripts['test'] = 'pnpm -r test';
  scripts['test:ui'] = 'pnpm -r --filter \'./platform/typescript/**\' --filter \'./products/*/ui\' test';
  scripts['lint'] = 'pnpm -r lint';
  scripts['format'] = 'prettier --write "**/*.{ts,tsx,js,jsx,json,md}"';
  scripts['clean'] = 'pnpm -r --parallel exec rm -rf node_modules dist build .turbo';
  scripts['typecheck'] = 'pnpm -r --parallel --filter \'./platform/typescript/**\' --filter \'./products/*/ui\' exec tsc --noEmit';
  scripts['product'] = 'node ./scripts/run-product-task.mjs';
  scripts['kernel'] = 'node ./scripts/kernel-product.mjs';
  scripts['build:kernel-lifecycle-platform'] = 'pnpm --dir platform/typescript/kernel-artifacts build && pnpm --dir platform/typescript/kernel-lifecycle build && pnpm --dir platform/typescript/kernel-toolchains build && pnpm --dir platform/typescript/kernel-deployment build && pnpm --dir platform/typescript/kernel-release build';
  scripts['check:affected-products'] = 'node ./scripts/resolve-affected-products.test.mjs';
  scripts['check:product-registry-artifacts'] = 'node ./scripts/generate-product-registry-artifacts.mjs --check';
  scripts['check:product-kind-classification'] = 'node ./scripts/check-product-kind-classification.mjs';
  scripts['check:kernel-platform-lifecycle'] = 'pnpm build:kernel-lifecycle-platform && node ./scripts/check-kernel-platform-lifecycle.mjs';
  
  // Generate product-specific scripts from registry
  for (const [productId, product] of Object.entries(registry.registry)) {
    if (!PRODUCT_SCRIPT_KINDS.has(product.kind)) {
      continue;
    }

    // Use kernel lifecycle CLI for products with lifecycle enabled
    const useLifecycle = product.lifecycleProfile && product.lifecycle?.enabled;

    const surfaces = product.surfaces || [];
    
    for (const surface of surfaces) {
      const surfaceName = surface.type === 'backend-api' ? 'gateway' : surface.type;
      const surfaceFlag = surface.type === 'backend-api' ? 'backend-api' : surface.type;
      
      if (useLifecycle) {
        scripts[`build:${productId}-${surfaceName}`] = `node scripts/kernel-product.mjs product build ${productId} --surface ${surfaceFlag}`;
        scripts[`test:${productId}-${surfaceName}`] = `node scripts/kernel-product.mjs product test ${productId} --surface ${surfaceFlag}`;
        scripts[`plan:build:${productId}-${surfaceName}`] = `node scripts/kernel-product.mjs product plan ${productId} build --surface ${surfaceFlag}`;
        scripts[`plan:test:${productId}-${surfaceName}`] = `node scripts/kernel-product.mjs product plan ${productId} test --surface ${surfaceFlag}`;
        if (surface.packagePath) {
          scripts[`dev:${productId}-${surfaceName}`] = `node scripts/kernel-product.mjs product dev ${productId} --surface ${surfaceFlag}`;
          scripts[`plan:dev:${productId}-${surfaceName}`] = `node scripts/kernel-product.mjs product plan ${productId} dev --surface ${surfaceFlag}`;
        }
      } else {
        scripts[`build:${productId}-${surfaceName}`] = `pnpm product ${productId} build ${surfaceName}`;
        scripts[`test:${productId}-${surfaceName}`] = `pnpm product ${productId} test ${surfaceName}`;
        if (surface.packagePath) {
          scripts[`dev:${productId}-${surfaceName}`] = `pnpm product ${productId} dev ${surfaceName}`;
        }
      }
    }
    
    if (useLifecycle) {
      scripts[`build:${productId}`] = `node scripts/kernel-product.mjs product build ${productId}`;
      scripts[`test:${productId}`] = `node scripts/kernel-product.mjs product test ${productId}`;
      scripts[`dev:${productId}`] = `node scripts/kernel-product.mjs product dev ${productId}`;
      scripts[`validate:${productId}`] = `node scripts/kernel-product.mjs product validate ${productId}`;
      scripts[`package:${productId}`] = `node scripts/kernel-product.mjs product package ${productId}`;
      scripts[`deploy:local:${productId}`] = `node scripts/kernel-product.mjs product deploy ${productId} --env local`;
      scripts[`verify:local:${productId}`] = `node scripts/kernel-product.mjs product verify ${productId} --env local`;
      scripts[`plan:build:${productId}`] = `node scripts/kernel-product.mjs product plan ${productId} build`;
      scripts[`plan:test:${productId}`] = `node scripts/kernel-product.mjs product plan ${productId} test`;
      scripts[`plan:dev:${productId}`] = `node scripts/kernel-product.mjs product plan ${productId} dev`;
      scripts[`plan:validate:${productId}`] = `node scripts/kernel-product.mjs product plan ${productId} validate`;
      scripts[`plan:package:${productId}`] = `node scripts/kernel-product.mjs product plan ${productId} package`;
      scripts[`plan:deploy:local:${productId}`] = `node scripts/kernel-product.mjs product plan ${productId} deploy --env local`;
      scripts[`plan:verify:local:${productId}`] = `node scripts/kernel-product.mjs product plan ${productId} verify --env local`;
      scripts[`release:${productId}`] = `node scripts/kernel-product.mjs release ${productId}`;
      scripts[`promote:${productId}`] = `node scripts/kernel-product.mjs promote ${productId} --from staging --to prod`;
      scripts[`rollback:${productId}`] = `node scripts/kernel-product.mjs rollback ${productId} --env prod`;
    } else {
      scripts[`build:${productId}`] = `pnpm product ${productId} build`;
      scripts[`test:${productId}`] = `pnpm product ${productId} test`;
    }
  }
  
  writeGeneratedFile(PACKAGE_SCRIPTS_OUTPUT_PATH, JSON.stringify(scripts, null, 2) + '\n', 'package scripts');
  console.log(`${checkMode ? 'Checked' : 'Generated'} package scripts with ${Object.keys(scripts).length} entries`);
}

// Main execution
function main() {
  console.log(`=== Product Registry Artifact ${checkMode ? 'Checker' : 'Generator'} ===\n`);
  
  try {
    const registry = loadRegistry();
    const dependencyPolicy = loadWorkspaceDependencyPolicy();
    const productIds = validateRegistry(registry);
    
    console.log(`Processing ${productIds.length} products...\n`);
    
    // Generate all artifacts
    generateProductShape(registry);
    generateGradleIncludes(registry);
    generatePnpmEntries(registry, dependencyPolicy);
    generateCIMatrix(registry);
    generatePackageScripts(registry);
    
    // Merge generated package scripts into package.json
    console.log(`\n${checkMode ? 'Checking' : 'Merging'} generated package scripts ${checkMode ? 'against' : 'into'} package.json...`);
    execFileSync(
      process.execPath,
      ['scripts/merge-generated-package-scripts.mjs', ...(checkMode ? ['--check'] : [])],
      { cwd: repoRoot, stdio: 'inherit' },
    );

    if (checkMode && staleArtifacts.length > 0) {
      console.error('\n=== Generated product registry artifacts are stale ===');
      for (const artifact of staleArtifacts) {
        console.error(`- ${artifact}`);
      }
      console.error('\nRun: node scripts/generate-product-registry-artifacts.mjs');
      process.exit(1);
    }
    
    console.log(`\n=== All artifacts ${checkMode ? 'are current' : 'generated successfully'} ===`);
    console.log(`\n${checkMode ? 'Checked' : 'Generated'} files:`);
    console.log(`  - ${PRODUCT_SHAPE_PATH}`);
    console.log(`  - ${SETTINGS_OUTPUT_PATH}`);
    console.log(`  - ${PNPM_OUTPUT_PATH}`);
    console.log(`  - ${CI_MATRIX_OUTPUT_PATH}`);
    console.log(`  - ${PACKAGE_SCRIPTS_OUTPUT_PATH}`);
    console.log(`  - package.json (merged with generated scripts)`);
    
  } catch (error) {
    console.error('\n=== Generation failed ===');
    console.error(error.message);
    process.exit(1);
  }
}

// Run if executed directly
main();

export { main as generateArtifacts };
