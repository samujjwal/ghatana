#!/usr/bin/env node

/**
 * Generate Risk-Based Scanning Configuration
 * 
 * Generates risk-based scanning configuration based on product manifests.
 * This replaces the production-stub allowlist with a declarative, risk-aware approach.
 * 
 * Usage: node scripts/generate-risk-based-scanning-config.mjs [--check]
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

function generateRiskBasedScanningConfig(registry) {
  const config = {
    version: '1.0.0',
    generated: new Date().toISOString(),
    riskLevels: {
      critical: {
        description: 'Production systems handling regulated data (healthcare, finance)',
        scanning: {
          sast: true,
          sca: true,
          secrets: true,
          dependencyAudit: true,
          licenseCompliance: true,
          securityTests: true,
          penetrationTests: true
        },
        products: []
      },
      high: {
        description: 'Production systems with sensitive data',
        scanning: {
          sast: true,
          sca: true,
          secrets: true,
          dependencyAudit: true,
          licenseCompliance: true,
          securityTests: true,
          penetrationTests: false
        },
        products: []
      },
      medium: {
        description: 'Production systems with standard data',
        scanning: {
          sast: true,
          sca: true,
          secrets: true,
          dependencyAudit: true,
          licenseCompliance: false,
          securityTests: false,
          penetrationTests: false
        },
        products: []
      },
      low: {
        description: 'Development and test environments',
        scanning: {
          sast: false,
          sca: false,
          secrets: false,
          dependencyAudit: true,
          licenseCompliance: false,
          securityTests: false,
          penetrationTests: false
        },
        products: []
      }
    },
    scopedExclusions: []
  };
  
  // Classify products based on data sensitivity and conformance requirements
  for (const [productId, product] of Object.entries(registry.registry)) {
    const surfaces = product.surfaces || [];
    const hasProductionSurface = surfaces.some(s => s.type === 'backend-api' || s.type === 'web' || s.type === 'mobile');
    
    if (!hasProductionSurface) {
      config.riskLevels.low.products.push(productId);
      continue;
    }
    
    // Check for regulated data handling
    const conformance = product.conformance || {};
    const dataAccess = conformance.dataAccess === true;
    const security = conformance.security === true;
    const bridge = conformance.bridge === true;
    
    if (dataAccess && security && bridge) {
      config.riskLevels.critical.products.push(productId);
    } else if (dataAccess || security) {
      config.riskLevels.high.products.push(productId);
    } else {
      config.riskLevels.medium.products.push(productId);
    }
  }
  
  return config;
}

function main() {
  console.log('=== Risk-Based Scanning Configuration Generator ===\n');
  
  try {
    const registry = loadCanonicalRegistry();
    console.log(`Loaded ${Object.keys(registry.registry).length} products from registry\n`);
    
    const config = generateRiskBasedScanningConfig(registry);
    
    const outputPath = path.join(repoRoot, 'config/generated/risk-based-scanning.json');
    
    if (checkMode) {
      const existingConfig = existsSync(outputPath)
        ? readJson('config/generated/risk-based-scanning.json')
        : null;
      
      if (!existingConfig) {
        console.error('✗ No existing risk-based scanning configuration found');
        process.exit(1);
      }
      
      // Compare configurations
      const criticalCount = config.riskLevels.critical.products.length;
      const existingCriticalCount = existingConfig.riskLevels.critical.products.length;
      
      if (criticalCount !== existingCriticalCount) {
        console.error(`✗ Critical product count mismatch: current=${criticalCount}, existing=${existingCriticalCount}`);
        console.error('Run: node scripts/generate-risk-based-scanning-config.mjs');
        process.exit(1);
      }
      
      console.log('✓ Risk-based scanning configuration is up to date');
    } else {
      writeFileSync(outputPath, JSON.stringify(config, null, 2) + '\n');
      console.log(`Generated risk-based scanning configuration at ${outputPath}`);
      console.log(`  - Critical: ${config.riskLevels.critical.products.length} products`);
      console.log(`  - High: ${config.riskLevels.high.products.length} products`);
      console.log(`  - Medium: ${config.riskLevels.medium.products.length} products`);
      console.log(`  - Low: ${config.riskLevels.low.products.length} products`);
    }
    
    process.exit(0);
  } catch (error) {
    console.error('✗ Risk-based scanning configuration generation failed:');
    console.error(error.message);
    process.exit(1);
  }
}

main();
