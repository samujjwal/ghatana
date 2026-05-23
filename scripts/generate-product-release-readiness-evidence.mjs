#!/usr/bin/env node

/**
 * P1-6: Product-Specific Release Readiness Evidence Generator
 *
 * Generates release readiness evidence for each product based on:
 * - Lifecycle profile from canonical product registry
 * - CI gate requirements
 * - Conformance requirements
 * - Deployment readiness
 * - Test coverage evidence
 * - Security compliance
 * - Performance metrics
 *
 * Usage: node scripts/generate-product-release-readiness-evidence.mjs [--product <product>]
 */

import { readFileSync, existsSync, writeFileSync, mkdirSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

import { loadRegistry, getActiveProducts, resolveProductForProof } from './lib/product-registry-helper.mjs';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '..');

const PRODUCT_ARG = process.argv.find(arg => arg.startsWith('--product='))?.split('=')[1];

function logSuccess(message) {
  console.log(`✓ ${message}`);
}

function logWarning(message) {
  console.warn(`⚠️  WARNING: ${message}`);
}

function logError(message) {
  console.error(`❌ ERROR: ${message}`);
}

/**
 * Check if product has required test coverage
 */
function checkTestCoverage(productPath, productName) {
  const testDirs = [
    path.join(productPath, 'src/test/java'),
    path.join(productPath, 'src/__tests__'),
    path.join(productPath, 'tests'),
  ];

  let hasTests = false;
  for (const testDir of testDirs) {
    if (existsSync(testDir)) {
      hasTests = true;
      break;
    }
  }

  return hasTests;
}

/**
 * Check if product has security compliance evidence
 */
function checkSecurityCompliance(productPath, productName) {
  const securityFiles = [
    path.join(productPath, '.github/workflows/security.yml'),
    path.join(productPath, '.github/workflows/codeql.yml'),
    path.join(productPath, '.github/workflows/dependency-review.yml'),
    path.join(productPath, 'SECURITY.md'),
  ];

  let hasSecurity = false;
  for (const file of securityFiles) {
    if (existsSync(file)) {
      hasSecurity = true;
      break;
    }
  }

  return hasSecurity;
}

/**
 * Check if product has performance monitoring
 */
function checkPerformanceMonitoring(productPath, productName) {
  const perfFiles = [
    path.join(productPath, 'config/monitoring'),
    path.join(productPath, 'monitoring'),
  ];

  let hasPerf = false;
  for (const dir of perfFiles) {
    if (existsSync(dir)) {
      hasPerf = true;
      break;
    }
  }

  return hasPerf;
}

/**
 * Check if product has deployment configuration
 */
function checkDeploymentConfig(productPath, productName) {
  const deployFiles = [
    path.join(productPath, 'Dockerfile'),
    path.join(productPath, 'docker-compose.yml'),
    path.join(productPath, '.k8s'),
    path.join(productPath, 'kubernetes'),
  ];

  let hasDeploy = false;
  for (const file of deployFiles) {
    if (existsSync(file)) {
      hasDeploy = true;
      break;
    }
  }

  return hasDeploy;
}

/**
 * Generate release readiness evidence for a single product
 */
function generateProductEvidence(product) {
  const productPath = path.join(repoRoot, product.path);
  
  if (!existsSync(productPath)) {
    return {
      productId: product.productId,
      name: product.name,
      error: `Product path not found at ${product.path}`,
      ready: false,
    };
  }

  const registry = loadRegistry();
  const productConfig = registry[product.productId];

  const evidence = {
    productId: product.productId,
    name: product.name,
    kind: product.kind,
    status: product.status,
    timestamp: new Date().toISOString(),
    lifecycle: {
      enabled: product.lifecycleEnabled,
      profile: productConfig?.lifecycleProfile || null,
    },
    ci: {
      enabled: productConfig?.ci?.enabled || false,
      gates: productConfig?.ci?.gates || [],
    },
    conformance: {
      agentDefinitions: productConfig?.conformance?.agentDefinitions || false,
      masteryBindings: productConfig?.conformance?.masteryBindings || false,
      evaluationPacks: productConfig?.conformance?.evaluationPacks || false,
    },
    surfaces: product.surfaces.map(s => ({
      type: s.type,
      implementationStatus: s.implementationStatus,
    })),
    readinessChecks: {
      testCoverage: checkTestCoverage(productPath, product.name),
      securityCompliance: checkSecurityCompliance(productPath, product.name),
      performanceMonitoring: checkPerformanceMonitoring(productPath, product.name),
      deploymentConfig: checkDeploymentConfig(productPath, product.name),
    },
    ready: false,
    blockers: [],
  };

  // Determine readiness based on lifecycle profile and checks
  if (product.status !== 'active') {
    evidence.blockers.push(`Product status is ${product.status}, not active`);
  }

  if (!evidence.lifecycle.enabled) {
    evidence.blockers.push('Lifecycle not enabled for this product');
  }

  if (!evidence.readinessChecks.testCoverage) {
    evidence.blockers.push('Missing test coverage');
  }

  if (!evidence.readinessChecks.securityCompliance) {
    evidence.blockers.push('Missing security compliance evidence');
  }

  if (!evidence.readinessChecks.deploymentConfig) {
    evidence.blockers.push('Missing deployment configuration');
  }

  // Check CI gates
  if (evidence.ci.enabled && evidence.ci.gates.length === 0) {
    evidence.blockers.push('CI enabled but no gates defined');
  }

  evidence.ready = evidence.blockers.length === 0;

  return evidence;
}

/**
 * Main generation
 */
function main() {
  console.log('Generating product-specific release readiness evidence...\n');

  // Resolve active products from canonical product registry
  const registryProducts = getActiveProducts();
  
  // Resolve product information for evidence generation
  const products = registryProducts
    .map(({ productId }) => resolveProductForProof(productId))
    .filter(p => p !== null);

  // Filter by product if specified
  const filteredProducts = PRODUCT_ARG 
    ? products.filter(p => p.name.toLowerCase().includes(PRODUCT_ARG.toLowerCase()))
    : products;

  const evidenceDir = path.join(repoRoot, '.kernel', 'evidence', 'product-release-readiness');
  
  if (!existsSync(evidenceDir)) {
    mkdirSync(evidenceDir, { recursive: true });
  }

  const allEvidence = [];

  for (const product of filteredProducts) {
    console.log(`\n--- Generating evidence for ${product.name} ---`);
    
    const evidence = generateProductEvidence(product);
    allEvidence.push(evidence);

    if (evidence.error) {
      logError(evidence.error);
    } else {
      logSuccess(`Generated evidence for ${product.name}`);
      console.log(`  Ready: ${evidence.ready}`);
      if (evidence.blockers.length > 0) {
        console.log(`  Blockers: ${evidence.blockers.join(', ')}`);
      }
    }

    // Write individual product evidence file
    const productEvidencePath = path.join(evidenceDir, `${product.productId}-release-readiness.json`);
    writeFileSync(productEvidencePath, JSON.stringify(evidence, null, 2));
    console.log(`  Written to: ${productEvidencePath}`);
  }

  // Write combined evidence file
  const combinedEvidencePath = path.join(evidenceDir, 'all-products-release-readiness.json');
  const combinedReport = {
    timestamp: new Date().toISOString(),
    totalProducts: allEvidence.length,
    readyProducts: allEvidence.filter(e => e.ready).length,
    notReadyProducts: allEvidence.filter(e => !e.ready).length,
    products: allEvidence,
  };
  writeFileSync(combinedEvidencePath, JSON.stringify(combinedReport, null, 2));

  console.log('\n--- Summary ---');
  console.log(`Total products: ${allEvidence.length}`);
  console.log(`Ready for release: ${combinedReport.readyProducts}`);
  console.log(`Not ready: ${combinedReport.notReadyProducts}`);
  console.log(`\nCombined evidence written to: ${combinedEvidencePath}`);
}

main();
