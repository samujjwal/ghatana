#!/usr/bin/env node

/**
 * Generate Standardized Product Test Scripts
 * 
 * Generates standardized test script configurations for product UI packages
 * based on the canonical product registry. This ensures consistency across
 * all product UI packages for test commands and coverage gates.
 * 
 * Usage: node scripts/generate-product-test-scripts.mjs [--check]
 */

import { readFileSync, existsSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '..');

// Parse command line arguments
const args = process.argv.slice(2);
const checkMode = args.includes('--check');

function readJson(relativePath) {
  return JSON.parse(readFileSync(path.join(repoRoot, relativePath), 'utf8'));
}

function loadCanonicalRegistry() {
  const registryPath = path.join(repoRoot, 'config/canonical-product-registry.json');
  if (!existsSync(registryPath)) {
    throw new Error('Canonical product registry not found');
  }
  return JSON.parse(readFileSync(registryPath, 'utf8'));
}

function generateTestScriptConfig(registry) {
  const config = {
    version: '1.0.0',
    generated: new Date().toISOString(),
    standardScripts: {
      // Standard test scripts that all UI products should have
      test: 'vitest run',
      'test:unit': 'vitest run',
      'test:integration': 'vitest run --config vitest.integration.config.ts',
      'test:e2e': 'playwright test',
      'test:e2e:headed': 'playwright test --headed',
      'test:e2e:ui': 'playwright test --ui',
      'test:e2e:debug': 'playwright test --debug',
      'test:coverage': 'vitest run --coverage',
      'test:ui': 'vitest --ui',
      'test:watch': 'vitest',
      lint: 'eslint src --max-warnings=0',
      'type-check': 'tsc --noEmit',
    },
    productOverrides: {}
  };
  
  // Generate product-specific overrides based on their capabilities
  for (const [productId, product] of Object.entries(registry.registry)) {
    const surfaces = product.surfaces || [];
    const uiSurfaces = surfaces.filter(s => s.type === 'web' || s.type === 'mobile');
    
    if (uiSurfaces.length === 0) {
      continue; // Skip products without UI surfaces
    }
    
    const override = {
      scripts: {},
      coverageThreshold: {
        statements: 80,
        branches: 80,
        functions: 80,
        lines: 80
      }
    };
    
    // Add product-specific test scripts based on data sensitivity
    const dataSensitivity = product.conformance?.dataAccess === true ? 'regulated' : 'standard';
    
    if (dataSensitivity === 'regulated') {
      // Regulated products require additional compliance tests
      override.scripts['test:compliance'] = 'vitest run --config vitest.compliance.config.ts';
      override.scripts['test:audit'] = 'vitest run --config vitest.audit.config.ts';
    }
    
    config.productOverrides[productId] = override;
  }
  
  return config;
}

function main() {
  console.log('=== Product Test Script Generator ===\n');
  
  try {
    const registry = loadCanonicalRegistry();
    console.log(`Loaded ${Object.keys(registry.registry).length} products from registry\n`);
    
    const config = generateTestScriptConfig(registry);
    
    const outputPath = path.join(repoRoot, 'config/generated/product-test-scripts.json');
    
    if (checkMode) {
      const existingConfig = existsSync(outputPath)
        ? readJson('config/generated/product-test-scripts.json')
        : null;
      
      if (!existingConfig) {
        console.error('✗ No existing product test scripts configuration found');
        process.exit(1);
      }
      
      // Compare configurations
      const currentProductCount = Object.keys(config.productOverrides).length;
      const existingProductCount = Object.keys(existingConfig.productOverrides).length;
      
      if (currentProductCount !== existingProductCount) {
        console.error(`✗ Product count mismatch: current=${currentProductCount}, existing=${existingProductCount}`);
        console.error('Run: node scripts/generate-product-test-scripts.mjs');
        process.exit(1);
      }
      
      console.log('✓ Product test scripts configuration is up to date');
    } else {
      writeFileSync(outputPath, JSON.stringify(config, null, 2) + '\n');
      console.log(`Generated product test scripts configuration at ${outputPath}`);
      console.log(`  - ${Object.keys(config.productOverrides).length} products with UI surfaces`);
      console.log(`  - ${Object.keys(config.standardScripts).length} standard test scripts`);
    }
    
    process.exit(0);
  } catch (error) {
    console.error('✗ Product test script generation failed:');
    console.error(error.message);
    process.exit(1);
  }
}

main();
