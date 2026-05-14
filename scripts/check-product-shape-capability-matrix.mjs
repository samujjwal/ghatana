#!/usr/bin/env node

/**
 * Check Product Shape Capability Matrix
 *
 * Validates that:
 * - Matrix is generated and current
 * - Digital Marketing is the only enabled lifecycle proof target unless explicitly changed
 * - PHR/Finance/FlashIt planned status does not fail lifecycle execution checks
 * - YAPPC/Data Cloud platform-provider status is visible
 * - All registered products have shape rows
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

function loadMatrix() {
  const matrixPath = join(repoRoot, 'config/generated/product-shape-capability-matrix.json');
  if (!existsSync(matrixPath)) {
    return null;
  }
  return JSON.parse(readFileSync(matrixPath, 'utf8'));
}

async function main() {
  const errors = [];
  const warnings = [];

  // 1. Check matrix exists
  const matrix = loadMatrix();
  if (!matrix) {
    errors.push('Product shape capability matrix not found. Run: pnpm generate:product-shape-capability-matrix');
    reportAndExit(errors, warnings);
    return;
  }

  // 2. Check matrix is recent (generated within last 7 days)
  const generatedDate = new Date(matrix.generated);
  const now = new Date();
  const daysSinceGeneration = (now - generatedDate) / (1000 * 60 * 60 * 24);
  if (daysSinceGeneration > 7) {
    warnings.push(`Matrix was generated ${Math.floor(daysSinceGeneration)} days ago. Consider regenerating.`);
  }

  // 3. Load registry for comparison
  const registry = loadRegistry();
  const registryProductIds = Object.keys(registry);
  const matrixProductIds = matrix.matrix.map(m => m.productId);

  // 4. Check all registered products have shape rows
  const missingInMatrix = registryProductIds.filter(id => !matrixProductIds.includes(id));
  if (missingInMatrix.length > 0) {
    errors.push(`Products in registry but missing from matrix: ${missingInMatrix.join(', ')}`);
  }

  const extraInMatrix = matrixProductIds.filter(id => !registryProductIds.includes(id));
  if (extraInMatrix.length > 0) {
    warnings.push(`Products in matrix but not in registry: ${extraInMatrix.join(', ')}`);
  }

  for (const row of matrix.matrix) {
    if (!row.productKind) {
      errors.push(`Matrix row "${row.productId}" is missing productKind`);
    }
    if (!row.shapeValidationMode) {
      errors.push(`Matrix row "${row.productId}" is missing shapeValidationMode`);
    }
    if (!row.profileStatus) {
      errors.push(`Matrix row "${row.productId}" is missing profileStatus`);
    }
    if (!row.executionReadiness) {
      errors.push(`Matrix row "${row.productId}" is missing executionReadiness`);
    }
    if (row.lifecycleStatus === 'disabled' && row.findings?.some(f => f.includes('Lifecycle profile "undefined" not found'))) {
      errors.push(`Disabled product "${row.productId}" has a false missing-profile finding`);
    }
  }

  // 5. Check Digital Marketing is the only enabled lifecycle proof target
  const enabledProducts = matrix.matrix.filter(m => m.lifecycleStatus === 'enabled');
  if (enabledProducts.length === 0) {
    warnings.push('No products with lifecycleStatus="enabled" found in matrix');
  } else if (enabledProducts.length > 1) {
    warnings.push(`Multiple products with lifecycleStatus="enabled": ${enabledProducts.map(m => m.productId).join(', ')}. Expected only Digital Marketing as pilot.`);
  } else if (enabledProducts.length === 1 && enabledProducts[0].productId !== 'digital-marketing') {
    warnings.push(`Enabled lifecycle product is "${enabledProducts[0].productId}" but expected "digital-marketing" as pilot.`);
  }

  const digitalMarketing = matrix.matrix.find(m => m.productId === 'digital-marketing');
  if (digitalMarketing?.findings?.some(f => f.includes('deployment adapter'))) {
    errors.push('Digital Marketing has an avoidable deployment adapter finding');
  }

  // 6. Check PHR/Finance/FlashIt have planned status (not enabled)
  const plannedProducts = ['phr', 'finance', 'flashit'];
  for (const productId of plannedProducts) {
    const matrixRow = matrix.matrix.find(m => m.productId === productId);
    if (matrixRow && matrixRow.lifecycleStatus === 'enabled') {
      errors.push(`Product "${productId}" has lifecycleStatus="enabled" but should be "planned" (not lifecycle execution pilot)`);
    }
  }

  // 7. Check YAPPC/Data Cloud platform-provider status is visible
  const platformProviderProducts = ['yappc', 'data-cloud'];
  for (const productId of platformProviderProducts) {
    const matrixRow = matrix.matrix.find(m => m.productId === productId);
    if (!matrixRow) {
      errors.push(`Platform provider product "${productId}" not found in matrix`);
    } else {
      if (matrixRow.productKind !== 'platform-provider') {
        warnings.push(`Platform provider product "${productId}" shape does not indicate platform-provider status: ${matrixRow.shape}`);
      }
    }
  }

  // 8. Check for critical findings that would block lifecycle execution
  const productsWithCriticalFindings = matrix.matrix.filter(m => 
    m.findings && m.findings.some(f => f.includes('not found') || f.includes('not fully implemented'))
  );
  for (const product of productsWithCriticalFindings) {
    if (product.lifecycleStatus === 'enabled') {
      errors.push(`Enabled product "${product.productId}" has critical findings that would block lifecycle execution`);
    }
  }

  reportAndExit(errors, warnings);
}

function reportAndExit(errors, warnings) {
  if (warnings.length > 0) {
    console.warn('Warnings:');
    for (const w of warnings) {
      console.warn(`  - ${w}`);
    }
  }

  if (errors.length > 0) {
    console.error(`\nProduct shape capability matrix check FAILED (${errors.length} error(s)):`);
    for (const e of errors) {
      console.error(`  - ${e}`);
    }
    process.exit(1);
  }

  console.log('Product shape capability matrix check passed');
}

try {
  await main();
} catch (error) {
  console.error(`Check failed: ${error instanceof Error ? error.message : String(error)}`);
  process.exit(1);
}
