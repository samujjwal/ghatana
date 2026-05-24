#!/usr/bin/env node

/**
 * Product Registry Helper Module
 *
 * Centralized product registry resolution for release proof scripts:
 * - Load canonical product registry
 * - Resolve product surfaces and paths
 * - Filter products by criteria (active, lifecycle-enabled, AI-enabled, etc.)
 * - Resolve Gradle tasks and test commands
 *
 * Usage:
 *   import { loadRegistry, getActiveProducts, getProductSurface, getProductGradleTask } from './scripts/lib/product-registry-helper.mjs';
 */

import { readFileSync, existsSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '../..');

/**
 * Load canonical product registry
 */
export function loadRegistry() {
  const registryPath = path.join(repoRoot, 'config/canonical-product-registry.json');
  
  if (!existsSync(registryPath)) {
    throw new Error(`Product registry not found at ${registryPath}`);
  }
  
  try {
    const content = readFileSync(registryPath, 'utf8');
    const data = JSON.parse(content);
    return data.registry;
  } catch (error) {
    throw new Error(`Failed to load product registry: ${error.message}`);
  }
}

/**
 * Get all active products
 */
export function getActiveProducts() {
  const registry = loadRegistry();
  return Object.entries(registry)
    .filter(([, product]) => product.metadata?.status === 'active')
    .filter(([, product]) => product.ci?.enabled === true)
    .map(([productId, product]) => ({ productId, product }));
}

/**
 * Get products with lifecycle enabled
 */
export function getLifecycleEnabledProducts() {
  const registry = loadRegistry();
  return Object.entries(registry)
    .filter(([, product]) => product.lifecycle?.enabled === true)
    .filter(([, product]) => product.metadata?.status === 'active')
    .map(([productId, product]) => ({ productId, product }));
}

/**
 * Get products with Gradle surfaces (backend products)
 */
export function getGradleProducts() {
  const registry = loadRegistry();
  return Object.entries(registry)
    .filter(([, product]) => product.gradleModules && product.gradleModules.length > 0)
    .filter(([, product]) => product.metadata?.status === 'active')
    .map(([productId, product]) => ({ productId, product }));
}

/**
 * Get products with pnpm surfaces (web products)
 */
export function getPnpmProducts() {
  const registry = loadRegistry();
  return Object.entries(registry)
    .filter(([, product]) => product.pnpmPackages && product.pnpmPackages.length > 0)
    .filter(([, product]) => product.metadata?.status === 'active')
    .map(([productId, product]) => ({ productId, product }));
}

/**
 * Get AI-enabled products
 */
export function getAIEnabledProducts() {
  const registry = loadRegistry();
  return Object.entries(registry)
    .filter(([, product]) => product.conformance?.agentDefinitions === true)
    .filter(([, product]) => product.metadata?.status === 'active')
    .map(([productId, product]) => ({ productId, product }));
}

/**
 * Get products with critical mutations (backend-api surfaces)
 */
export function getCriticalMutationProducts() {
  const registry = loadRegistry();
  return Object.entries(registry)
    .filter(([, product]) => {
      const surfaces = product.surfaces || [];
      return surfaces.some(s => s.type === 'backend-api' && s.implementationStatus === 'implemented');
    })
    .filter(([, product]) => product.metadata?.status === 'active')
    .map(([productId, product]) => ({ productId, product }));
}

/**
 * Get product surface path by type
 */
export function getProductSurface(productId, surfaceType) {
  const registry = loadRegistry();
  const product = registry[productId];
  
  if (!product) {
    return null;
  }
  
  const surfaces = product.surfaces || [];
  const surface = surfaces.find(s => s.type === surfaceType);
  
  return surface ? surface.path : null;
}

/**
 * Get product Gradle task for a specific module
 */
export function getProductGradleTask(productId, modulePath) {
  const registry = loadRegistry();
  const product = registry[productId];
  
  if (!product) {
    return null;
  }
  
  const gradleModules = product.gradleModules || [];
  
  // Find the module that matches the path
  const module = gradleModules.find(m => {
    const normalizedModule = m.replace(/^:/, '').replace(/:/g, '/');
    return normalizedModule.includes(modulePath) || modulePath.includes(normalizedModule);
  });
  
  return module || null;
}

/**
 * Get product lifecycle test command
 */
export function getProductLifecycleTestCommand(productId) {
  const registry = loadRegistry();
  const product = registry[productId];
  
  if (!product) {
    return null;
  }
  
  // For Gradle products, return the test task
  if (product.gradleModules && product.gradleModules.length > 0) {
    // Use the first module as the primary test task
    const primaryModule = product.gradleModules[0];
    return `${primaryModule}:test`;
  }
  
  // For pnpm products, return the test script
  if (product.pnpmPackages && product.pnpmPackages.length > 0) {
    return 'pnpm test';
  }
  
  return null;
}

/**
 * Get product release profile
 */
export function getProductReleaseProfile(productId) {
  const registry = loadRegistry();
  const product = registry[productId];
  
  if (!product) {
    return null;
  }
  
  return product.lifecycleProfile || null;
}

/**
 * Get product applicable gates
 */
export function getProductApplicableGates(productId) {
  const registry = loadRegistry();
  const product = registry[productId];
  
  if (!product) {
    return [];
  }
  
  return product.ci?.gates || [];
}

/**
 * Resolve product information for release proof scripts
 */
export function resolveProductForProof(productId) {
  const registry = loadRegistry();
  const product = registry[productId];
  
  if (!product) {
    return null;
  }
  
  // Find the primary backend-api surface
  const backendSurface = (product.surfaces || []).find(s => s.type === 'backend-api');
  let primaryPath = backendSurface ? backendSurface.path : null;
  
  // Special handling for products whose canonical proof surface differs from
  // the first registered Gradle module.
  if (productId === 'finance') {
    // Finance backend-api surface is at products/finance (root of finance product)
    primaryPath = 'products/finance';
  } else if (productId === 'phr') {
    // PHR backend-api surface is at products/phr (root of phr product)
    primaryPath = 'products/phr';
  } else if (productId === 'data-cloud') {
    // Data Cloud runtime release proofs execute against the launcher module.
    primaryPath = 'products/data-cloud/delivery/launcher';
  } else if (productId === 'digital-marketing') {
    // DMOS runtime release proofs execute against the API composition module.
    primaryPath = 'products/digital-marketing/dm-api';
  }
  
  // Find the primary Gradle module
  let primaryGradleModule = product.gradleModules && product.gradleModules.length > 0
    ? product.gradleModules[0]
    : null;
  if (productId === 'data-cloud') {
    primaryGradleModule = ':products:data-cloud:delivery:launcher';
  } else if (productId === 'digital-marketing') {
    primaryGradleModule = ':products:digital-marketing:dm-api';
  }
  
  return {
    productId,
    name: product.name,
    kind: product.kind,
    path: primaryPath,
    gradleTask: primaryGradleModule ? `${primaryGradleModule}:test` : null,
    surfaces: product.surfaces || [],
    gradleModules: product.gradleModules || [],
    pnpmPackages: product.pnpmPackages || [],
    lifecycleEnabled: product.lifecycle?.enabled === true,
    aiEnabled: product.conformance?.agentDefinitions === true,
    status: product.metadata?.status,
    owner: product.metadata?.owner,
  };
}

/**
 * Get all products applicable for atomic workflow failure-injection
 */
export function getAtomicWorkflowProducts() {
  const registry = loadRegistry();
  return Object.entries(registry)
    .filter(([, product]) => product.gradleModules && product.gradleModules.length > 0)
    .filter(([, product]) => product.metadata?.status === 'active')
    .filter(([productId, product]) => {
      if (productId === 'data-cloud') {
        return true;
      }
      if (product.kind !== 'business-product') {
        return false;
      }
      return product.lifecycleExecutionAllowed === true || product.lifecycleStatus === 'enabled';
    })
    .map(([productId, product]) => ({ productId, product }));
}

/**
 * Get all products applicable for runtime dependency failure-injection
 */
export function getRuntimeDependencyProducts() {
  const registry = loadRegistry();
  return Object.entries(registry)
    .filter(([, product]) => product.gradleModules && product.gradleModules.length > 0)
    .filter(([, product]) => product.metadata?.status === 'active')
    .filter(([productId, product]) => {
      if (productId === 'data-cloud') {
        return true;
      }
      if (product.kind !== 'business-product') {
        return false;
      }
      return product.lifecycleExecutionAllowed === true || product.lifecycleStatus === 'enabled';
    })
    .map(([productId, product]) => ({ productId, product }));
}

/**
 * Get all products applicable for AI governance behavioral proof
 */
export function getAIGovernanceProducts() {
  const products = getAIEnabledProducts();
  const hasDataCloud = products.some(({ productId }) => productId === 'data-cloud');
  if (!hasDataCloud) {
    const registry = loadRegistry();
    const dataCloud = registry['data-cloud'];
    if (dataCloud?.metadata?.status === 'active') {
      products.push({ productId: 'data-cloud', product: dataCloud });
    }
  }
  return products;
}

/**
 * Validate product path exists
 */
export function validateProductPath(productPath) {
  const fullPath = path.join(repoRoot, productPath);
  return existsSync(fullPath);
}

/**
 * Get products with valid paths
 */
export function getProductsWithValidPaths(products) {
  return products.filter(({ product }) => {
    const surfaces = product.surfaces || [];
    const backendSurface = surfaces.find(s => s.type === 'backend-api');
    const primaryPath = backendSurface ? backendSurface.path : null;
    
    if (!primaryPath) {
      return false;
    }
    
    return validateProductPath(primaryPath);
  });
}
