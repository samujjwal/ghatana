#!/usr/bin/env node

/**
 * Kernel Scope Governance Check
 * 
 * Enforces Kernel promotion and de-promotion through executable gates.
 * This script validates that:
 * - Kernel modules are not promoted to product scope without proper governance
 * - Product modules do not depend on Kernel internals directly
 * - Kernel boundary contracts are respected
 * 
 * Usage: node scripts/check-kernel-scope-governance.mjs
 */

import { readFileSync, existsSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '..');

// Configuration
const KERNEL_MODULE_PREFIX = ':platform:';
const PRODUCT_MODULE_PREFIX = ':products:';
const KERNEL_INTERNALS = [
  'platform:java:kernel-core',
  'platform:java:kernel-persistence',
  'platform-kernel',
];

const violations = [];

function addViolation(module, message) {
  violations.push({ module, message });
}

function loadSettings() {
  const settingsPath = path.join(repoRoot, 'settings.gradle.kts');
  if (!existsSync(settingsPath)) {
    throw new Error('settings.gradle.kts not found');
  }
  return readFileSync(settingsPath, 'utf8');
}

function extractModules(settingsContent) {
  const includePattern = /include\("([^"]+)"\)/g;
  const modules = [];
  for (const match of settingsContent.matchAll(includePattern)) {
    modules.push(match[1]);
  }
  return modules;
}

function validateKernelModulePromotion(modules) {
  const kernelModules = modules.filter(m => m.startsWith(KERNEL_MODULE_PREFIX));
  const productModules = modules.filter(m => m.startsWith(PRODUCT_MODULE_PREFIX));
  
  // Check for Kernel modules in product scope
  for (const productModule of productModules) {
    const buildPath = path.join(repoRoot, productModule.replace(':', '/'), 'build.gradle.kts');
    if (existsSync(buildPath)) {
      const buildContent = readFileSync(buildPath, 'utf8');
      
      // Check for direct dependencies on Kernel internals
      for (const internal of KERNEL_INTERNALS) {
        const pattern = new RegExp(`(?:api|implementation)\\(project\\("${internal.replace(':', ':')}"\\)\\)`);
        if (pattern.test(buildContent)) {
          addViolation(
            productModule,
            `Product module depends directly on Kernel internal: ${internal}. Use Kernel-provided contracts instead.`
          );
        }
      }
    }
  }
  
  // Check for product modules in Kernel scope
  for (const kernelModule of kernelModules) {
    const buildPath = path.join(repoRoot, kernelModule.replace(':', '/'), 'build.gradle.kts');
    if (existsSync(buildPath)) {
      const buildContent = readFileSync(buildPath, 'utf8');
      
      // Check for dependencies on product modules
      for (const productModule of productModules) {
        const pattern = new RegExp(`(?:api|implementation)\\(project\\("${productModule.replace(':', ':')}"\\)\\)`);
        if (pattern.test(buildContent)) {
          addViolation(
            kernelModule,
            `Kernel module depends on product module: ${productModule}. This violates Kernel isolation.`
          );
        }
      }
    }
  }
}

function validateKernelBoundaryContracts(modules) {
  const kernelModules = modules.filter(m => m.startsWith(KERNEL_MODULE_PREFIX));
  
  for (const kernelModule of kernelModules) {
    const buildPath = path.join(repoRoot, kernelModule.replace(':', '/'), 'build.gradle.kts');
    if (existsSync(buildPath)) {
      const buildContent = readFileSync(buildPath, 'utf8');
      
      // Kernel modules should only depend on other Kernel modules or shared platform modules
      const dependencyPattern = /(?:api|implementation)\(project\("([^"]+)"\)\)/g;
      for (const match of buildContent.matchAll(dependencyPattern)) {
        const dep = match[1];
        
        if (dep.startsWith(PRODUCT_MODULE_PREFIX)) {
          addViolation(
            kernelModule,
            `Kernel module depends on product module: ${dep}. Kernel must remain product-agnostic.`
          );
        }
      }
    }
  }
}

function validateCanonicalRegistry() {
  const registryPath = path.join(repoRoot, 'config/canonical-product-registry.json');
  if (!existsSync(registryPath)) {
    console.warn('Canonical product registry not found, skipping registry validation');
    return;
  }
  
  const registry = JSON.parse(readFileSync(registryPath, 'utf8'));
  
  // Ensure all products in registry are in settings.gradle.kts
  const settingsContent = loadSettings();
  const settingsModules = extractModules(settingsContent);
  
  for (const [productId, product] of Object.entries(registry.registry)) {
    if (product.gradleModules && product.gradleModules.length > 0) {
      for (const gradleModule of product.gradleModules) {
        if (!settingsModules.includes(gradleModule)) {
          addViolation(
            `canonical-registry:${productId}`,
            `Product registry declares Gradle module '${gradleModule}' but it's not included in settings.gradle.kts. Run: node scripts/generate-product-registry-artifacts.mjs`
          );
        }
      }
    }
  }
}

function main() {
  console.log('=== Kernel Scope Governance Check ===\n');
  
  try {
    const settingsContent = loadSettings();
    const modules = extractModules(settingsContent);
    
    console.log(`Validating ${modules.length} modules...\n`);
    
    validateKernelModulePromotion(modules);
    validateKernelBoundaryContracts(modules);
    validateCanonicalRegistry();
    
    if (violations.length === 0) {
      console.log('✓ Kernel scope governance check passed');
      console.log('  - No Kernel module promotion violations detected');
      console.log('  - Kernel boundary contracts respected');
      console.log('  - Canonical registry aligned with settings.gradle.kts');
      process.exit(0);
    } else {
      console.error(`✗ Kernel scope governance check failed with ${violations.length} violation(s):\n`);
      for (const { module, message } of violations) {
        console.error(`- ${module}: ${message}`);
      }
      process.exit(1);
    }
  } catch (error) {
    console.error('✗ Kernel scope governance check failed with error:');
    console.error(error.message);
    process.exit(1);
  }
}

main();
