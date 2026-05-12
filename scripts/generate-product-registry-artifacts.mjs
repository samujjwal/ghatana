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

// Load canonical registry
function loadRegistry() {
  if (!existsSync(REGISTRY_PATH)) {
    throw new Error(`Canonical product registry not found: ${REGISTRY_PATH}`);
  }
  const content = readFileSync(REGISTRY_PATH, 'utf8');
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
      clientPackages: uiSurfaces
        .filter(s => s.packagePath)
        .map(s => s.packagePath)
    };
    
    if (backendOnly && product.metadata?.documentation) {
      products[productId].backendOnlyDeclaration = {
        file: product.metadata.documentation,
        mustContain: 'backend-only'
      };
    }
  }
  
  const output = { products };
  writeFileSync(PRODUCT_SHAPE_PATH, JSON.stringify(output, null, 2) + '\n');
  console.log(`Generated product-shape.json with ${Object.keys(products).length} products`);
}

// Generate Gradle include blocks
function generateGradleIncludes(registry) {
  const lines = [];
  lines.push('// Auto-generated from canonical-product-registry.json');
  lines.push('// DO NOT EDIT MANUALLY - run: node scripts/generate-product-registry-artifacts.mjs');
  lines.push('');
  
  // Group by type
  const products = Object.values(registry.registry);
  
  lines.push('// =============================================================================');
  lines.push('// Products (from canonical registry)');
  lines.push('// =============================================================================');
  
  for (const product of products) {
    if (product.type === 'product') {
      lines.push(`// Product: ${product.name} (${product.id})`);
      for (const module of product.gradleModules || []) {
        lines.push(`include("${module}")`);
      }
      lines.push('');
    }
  }
  
  writeFileSync(SETTINGS_OUTPUT_PATH, lines.join('\n'));
  console.log(`Generated Gradle includes at ${SETTINGS_OUTPUT_PATH}`);
}

// Generate pnpm workspace entries
function generatePnpmEntries(registry) {
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
  
  const output = lines.join('\n') + '\n';
  writeFileSync(PNPM_OUTPUT_PATH, output);
  writeFileSync(PNPM_WORKSPACE_PATH, output);
  console.log(`Generated pnpm workspace entries at ${PNPM_OUTPUT_PATH}`);
  console.log(`Updated root pnpm workspace at ${PNPM_WORKSPACE_PATH}`);
}

// Generate CI matrix configuration
function generateCIMatrix(registry) {
  const matrix = {
    products: [],
    productsWithUI: [],
    productsWithTests: [],
    productsWithIntegrationTests: []
  };
  
  for (const [productId, product] of Object.entries(registry.registry)) {
    if (product.ci?.enabled) {
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
    }
  }
  
  writeFileSync(CI_MATRIX_OUTPUT_PATH, JSON.stringify(matrix, null, 2) + '\n');
  console.log(`Generated CI matrix with ${matrix.products.length} products`);
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
  
  // Generate product-specific scripts from registry
  for (const [productId, product] of Object.entries(registry.registry)) {
    const surfaces = product.surfaces || [];
    
    for (const surface of surfaces) {
      const surfaceName = surface.type === 'backend-api' ? 'gateway' : surface.type;
      scripts[`build:${productId}-${surfaceName}`] = `pnpm product ${productId} build ${surfaceName}`;
      scripts[`test:${productId}-${surfaceName}`] = `pnpm product ${productId} test ${surfaceName}`;
      if (surface.packagePath) {
        scripts[`dev:${productId}-${surfaceName}`] = `pnpm product ${productId} dev ${surfaceName}`;
      }
    }
    
    scripts[`build:${productId}`] = `pnpm product ${productId} build`;
    scripts[`test:${productId}`] = `pnpm product ${productId} test`;
  }
  
  writeFileSync(PACKAGE_SCRIPTS_OUTPUT_PATH, JSON.stringify(scripts, null, 2) + '\n');
  console.log(`Generated package scripts with ${Object.keys(scripts).length} entries`);
}

// Main execution
function main() {
  console.log('=== Product Registry Artifact Generator ===\n');
  
  try {
    const registry = loadRegistry();
    const productIds = validateRegistry(registry);
    
    console.log(`Processing ${productIds.length} products...\n`);
    
    // Generate all artifacts
    generateProductShape(registry);
    generateGradleIncludes(registry);
    generatePnpmEntries(registry);
    generateCIMatrix(registry);
    generatePackageScripts(registry);
    
    // Merge generated package scripts into package.json
    console.log('\nMerging generated package scripts into package.json...');
    execFileSync(process.execPath, ['scripts/merge-generated-package-scripts.mjs'], { cwd: repoRoot, stdio: 'inherit' });
    
    console.log('\n=== All artifacts generated successfully ===');
    console.log('\nGenerated files:');
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
