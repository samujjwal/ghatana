#!/usr/bin/env node

/**
 * Generic Product Conformance Runner
 * 
 * Executes product conformance checks based on rules defined in the canonical product registry.
 * This replaces one-off product-specific conformance scripts with a unified, configurable runner.
 * 
 * Usage: node scripts/check-product-conformance.mjs [--product <productId>]
 */

import { readFileSync, existsSync, readdirSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '..');

// Parse command line arguments
const args = process.argv.slice(2);
const productFilter = args.find(arg => arg.startsWith('--product='))?.split('=')[1];

function readJson(relativePath) {
  return JSON.parse(readFileSync(path.join(repoRoot, relativePath), 'utf8'));
}

function readText(relativePath) {
  return readFileSync(path.join(repoRoot, relativePath), 'utf8');
}

function loadCanonicalRegistry() {
  const registryPath = path.join(repoRoot, 'config/canonical-product-registry.json');
  if (!existsSync(registryPath)) {
    throw new Error('Canonical product registry not found');
  }
  return JSON.parse(readFileSync(registryPath, 'utf8'));
}

function loadProductShape() {
  const shapePath = path.join(repoRoot, 'config/product-shape.json');
  if (!existsSync(shapePath)) {
    throw new Error('Product shape file not found');
  }
  return JSON.parse(readFileSync(shapePath, 'utf8'));
}

// Conformance check implementations
const checks = {
  clientPackages: (product, productConfig, productShape) => {
    const violations = [];
    const clientPackages = productConfig.surfaces?.filter(s => s.type === 'web' || s.type === 'mobile') || [];
    
    if (clientPackages.length === 0) {
      return violations; // No client surfaces to check
    }
    
    const declaredPackages = productShape.products[product]?.clientPackages || [];
    
    for (const surface of clientPackages) {
      const surfacePath = surface.path;
      if (!existsSync(path.join(repoRoot, surfacePath))) {
        violations.push(`${surfacePath} declared in registry but does not exist`);
        continue;
      }
      
      const actualPackages = collectClientPackages(surfacePath);
      const expectedPackages = declaredPackages.filter(p => p.startsWith(surfacePath));
      
      if (JSON.stringify(actualPackages.sort()) !== JSON.stringify(expectedPackages.sort())) {
        violations.push(
          `${product} client packages in ${surfacePath} must match config/product-shape.json. ` +
          `Actual=${JSON.stringify(actualPackages)} Expected=${JSON.stringify(expectedPackages)}`
        );
      }
    }
    
    return violations;
  },
  
  requiredDependencies: (product, productConfig) => {
    const violations = [];
    const clientSurfaces = productConfig.surfaces?.filter(s => s.type === 'web' || s.type === 'mobile') || [];
    
    for (const surface of clientSurfaces) {
      const packageJsonPath = path.join(surface.path, 'package.json');
      if (!existsSync(path.join(repoRoot, packageJsonPath))) {
        continue;
      }
      
      const pkg = readJson(packageJsonPath);
      const allDeps = { ...(pkg.dependencies ?? {}), ...(pkg.devDependencies ?? {}) };
      
      // Required platform dependencies
      const requiredDeps = [
        '@ghatana/design-system',
        '@ghatana/product-shell',
        '@ghatana/tokens'
      ];
      
      for (const dep of requiredDeps) {
        if (!allDeps[dep]) {
          violations.push(`${packageJsonPath} must declare dependency ${dep}`);
        }
      }
    }
    
    return violations;
  },
  
  documentationSections: (product, productConfig) => {
    const violations = [];
    const docPath = path.join('products', product.replace('-', '/'), 'docs');
    
    if (!existsSync(path.join(repoRoot, docPath))) {
      return violations; // No docs directory
    }
    
    const requiredSections = [
      '## Domain Boundary',
      '## Kernel Dependencies',
      '## Platform Capabilities Consumed',
      '## Product-Only Business Logic',
    ];
    
    const visionPath = path.join(docPath, '00-VISION.md');
    if (existsSync(path.join(repoRoot, visionPath))) {
      const content = readText(visionPath);
      for (const section of requiredSections) {
        if (!content.includes(section)) {
          violations.push(`${visionPath} is missing section "${section}"`);
        }
      }
    }
    
    return violations;
  },
  
  manifestExists: (product, productConfig) => {
    const violations = [];
    
    if (productConfig.manifestPath) {
      if (!existsSync(path.join(repoRoot, productConfig.manifestPath))) {
        violations.push(`Manifest file declared in registry but does not exist: ${productConfig.manifestPath}`);
      }
    }
    
    return violations;
  },
  
  buildFileExists: (product, productConfig) => {
    const violations = [];
    
    if (productConfig.buildFile) {
      if (!existsSync(path.join(repoRoot, productConfig.buildFile))) {
        violations.push(`Build file declared in registry but does not exist: ${productConfig.buildFile}`);
      }
    }
    
    return violations;
  }
};

function collectClientPackages(relativeDir) {
  const absoluteDir = path.join(repoRoot, relativeDir);
  const entries = readdirSync(absoluteDir, { withFileTypes: true });
  const packagePaths = [];
  
  for (const entry of entries) {
    if (!entry.isDirectory()) {
      continue;
    }
    
    const packageJsonPath = path.join(relativeDir, entry.name, 'package.json');
    try {
      readFileSync(path.join(repoRoot, packageJsonPath), 'utf8');
      packagePaths.push(packageJsonPath);
    } catch {
      // Ignore non-package subdirectories.
    }
  }
  
  return packagePaths.sort();
}

function runProductChecks(productId, productConfig, productShape) {
  const violations = [];
  
  // Get conformance configuration from registry
  const conformance = productConfig.conformance || {};
  
  // Run enabled checks
  if (conformance.manifest !== false) {
    violations.push(...checks.manifestExists(productId, productConfig));
  }
  
  if (conformance.buildFile !== false) {
    violations.push(...checks.buildFileExists(productId, productConfig));
  }
  
  if (conformance.clientPackages !== false) {
    violations.push(...checks.clientPackages(productId, productConfig, productShape));
  }
  
  if (conformance.requiredDependencies !== false) {
    violations.push(...checks.requiredDependencies(productId, productConfig));
  }
  
  if (conformance.documentation !== false) {
    violations.push(...checks.documentationSections(productId, productConfig));
  }
  
  return violations;
}

function main() {
  console.log('=== Generic Product Conformance Runner ===\n');
  
  try {
    const registry = loadCanonicalRegistry();
    const productShape = loadProductShape();
    
    let totalViolations = 0;
    let checkedProducts = 0;
    
    for (const [productId, productConfig] of Object.entries(registry.registry)) {
      // Skip if product filter is specified and doesn't match
      if (productFilter && productId !== productFilter) {
        continue;
      }
      
      checkedProducts++;
      console.log(`Checking ${productId}...`);
      
      const violations = runProductChecks(productId, productConfig, productShape);
      
      if (violations.length > 0) {
        console.error(`  ✗ ${violations.length} violation(s):`);
        for (const violation of violations) {
          console.error(`    - ${violation}`);
        }
        totalViolations += violations.length;
      } else {
        console.log(`  ✓ Passed`);
      }
    }
    
    console.log(`\nChecked ${checkedProducts} product(s)`);
    
    if (totalViolations > 0) {
      console.error(`\n✗ Product conformance check failed with ${totalViolations} total violation(s)`);
      process.exit(1);
    }
    
    console.log('✓ Product conformance check passed');
    process.exit(0);
  } catch (error) {
    console.error('✗ Product conformance check failed with error:');
    console.error(error.message);
    process.exit(1);
  }
}

main();
