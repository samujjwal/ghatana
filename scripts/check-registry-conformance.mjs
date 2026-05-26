#!/usr/bin/env node

/**
 * REG-P1-001, REG-P2-004: Registry conformance validation
 * 
 * Validates that:
 * - Active products with implemented surfaces do not appear production-ready when lifecycle/conformance is blocked
 * - Active products with implemented backend/web surfaces have explicit contract/security/o11y/dataAccess status
 */

import { readFileSync } from 'fs';
import { join } from 'path';

const REGISTRY_PATH = join(process.cwd(), 'config', 'canonical-product-registry.json');

function loadRegistry() {
  try {
    const content = readFileSync(REGISTRY_PATH, 'utf-8');
    return JSON.parse(content);
  } catch (error) {
    console.error(`Failed to load registry: ${error.message}`);
    process.exit(1);
  }
}

function validateProductionReadiness(registry) {
  const errors = [];
  const products = registry.registry;

  for (const [productId, product] of Object.entries(products)) {
    const metadata = product.metadata || {};
    const surfaces = product.surfaces || [];
    const lifecycleExecutionAllowed = product.lifecycleExecutionAllowed;
    const productionReadinessStatus = product.productionReadinessStatus;
    const conformance = product.conformance || {};

    // REG-P1-001: Do not mark implemented surfaces as production-ready when lifecycle/conformance is blocked
    const hasImplementedSurfaces = surfaces.some(s => s.implementationStatus === 'implemented');
    
    if (hasImplementedSurfaces && lifecycleExecutionAllowed === false) {
      if (!productionReadinessStatus || productionReadinessStatus !== 'blocked') {
        errors.push({
          product: productId,
          error: 'REG-P1-001',
          message: 'Product has implemented surfaces but lifecycle execution is blocked without explicit productionReadinessStatus=blocked',
          surfaces: surfaces.filter(s => s.implementationStatus === 'implemented').map(s => s.type)
        });
      }
    }

    // REG-P2-004: Active product with implemented backend/web must have contract/security/o11y/dataAccess status explicit
    if (metadata.status === 'active' && hasImplementedSurfaces) {
      const hasBackend = surfaces.some(s => s.type === 'backend-api' && s.implementationStatus === 'implemented');
      const hasWeb = surfaces.some(s => s.type === 'web' && s.implementationStatus === 'implemented');

      if (hasBackend || hasWeb) {
        const requiredConformance = ['contract', 'security', 'observability', 'dataAccess'];
        const missingConformance = requiredConformance.filter(key => {
          const value = conformance[key];
          return value === undefined || value === null || value === '';
        });

        if (missingConformance.length > 0) {
          errors.push({
            product: productId,
            error: 'REG-P2-004',
            message: 'Active product with implemented backend/web surfaces has missing conformance status',
            missing: missingConformance,
          });
        }
      }
    }
  }

  return errors;
}

function main() {
  const registry = loadRegistry();
  const errors = validateProductionReadiness(registry);

  if (errors.length === 0) {
    console.log('✓ Registry conformance validation passed');
    console.log('  - No implemented surfaces marked as production-ready with blocked lifecycle');
    console.log('  - All active products with implemented surfaces have explicit conformance status');
    process.exit(0);
  } else {
    console.error(`✗ Registry conformance validation failed with ${errors.length} error(s):`);
    errors.forEach(err => {
      console.error(`  [${err.error}] ${err.product}: ${err.message}`);
      if (err.surfaces) console.error(`    Surfaces: ${err.surfaces.join(', ')}`);
      if (err.missing) console.error(`    Missing conformance: ${err.missing.join(', ')}`);
    });
    process.exit(1);
  }
}

main();
