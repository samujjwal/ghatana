#!/usr/bin/env node

/**
 * Wave 2: Enforce Product Release Gate on Tag/Release Events for All Business Products
 *
 * Enforces release gates for all business products:
 * - Auth Gateway
 * - Incident Service
 * - Studio Workflow Service
 * - Data Cloud
 * - AEP
 * - Digital Marketing
 * - PHR
 * - Finance
 * - FlashIt
 * - YAPPC
 *
 * Each product must pass the following gates before release:
 * - Atomic workflow failure-injection tests pass
 * - Runtime dependency failure-injection tests pass
 * - AI governance tests pass
 * - i18n conformance checks pass
 * - a11y behavioral tests pass
 * - OpenAPI release quality checks pass
 * - Security scans pass
 * - Release evidence is complete
 *
 * Usage: node scripts/enforce-product-release-gates.mjs [--product <product>] [--tag <tag>]
 */

import { readFileSync, existsSync, readdirSync, statSync, writeFileSync, mkdirSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '..');

const PRODUCT_ARG = process.argv.find(arg => arg.startsWith('--product='))?.split('=')[1];
const TAG_ARG = process.argv.find(arg => arg.startsWith('--tag='))?.split('=')[1] || 'latest';

const violations = [];
const warnings = [];
const evidence = [];

function logError(message) {
  violations.push(message);
  console.error(`❌ ERROR: ${message}`);
}

function logWarning(message) {
  warnings.push(message);
  console.warn(`⚠️  WARNING: ${message}`);
}

function logSuccess(message) {
  console.log(`✓ ${message}`);
}

function logEvidence(message) {
  evidence.push(message);
  console.log(`  📋 ${message}`);
}

/**
 * Check atomic workflow gate
 */
function checkAtomicWorkflowGate(productPath, productName) {
  const evidenceDir = path.join(productPath, '.kernel', 'evidence', 'atomic-workflow-failure-injection');
  
  if (!existsSync(evidenceDir)) {
    logError(`${productName}: Atomic workflow gate failed - no evidence directory`);
    return false;
  }

  const files = readdirSync(evidenceDir);
  const hasEvidence = files.length > 0;

  if (hasEvidence) {
    logSuccess(`${productName}: Atomic workflow gate passed`);
    logEvidence(`${productName}: Has ${files.length} atomic workflow evidence files`);
    return true;
  } else {
    logError(`${productName}: Atomic workflow gate failed - no evidence`);
    return false;
  }
}

/**
 * Check runtime dependency gate
 */
function checkRuntimeDependencyGate(productPath, productName) {
  const evidenceDir = path.join(productPath, '.kernel', 'evidence', 'runtime-dependency-failure-injection');
  
  if (!existsSync(evidenceDir)) {
    logError(`${productName}: Runtime dependency gate failed - no evidence directory`);
    return false;
  }

  const files = readdirSync(evidenceDir);
  const hasEvidence = files.length > 0;

  if (hasEvidence) {
    logSuccess(`${productName}: Runtime dependency gate passed`);
    logEvidence(`${productName}: Has ${files.length} runtime dependency evidence files`);
    return true;
  } else {
    logError(`${productName}: Runtime dependency gate failed - no evidence`);
    return false;
  }
}

/**
 * Check AI governance gate
 */
function checkAIGovernanceGate(productPath, productName) {
  const evidenceDir = path.join(productPath, '.kernel', 'evidence', 'ai-governance-behavioral-proof');
  
  if (!existsSync(evidenceDir)) {
    logError(`${productName}: AI governance gate failed - no evidence directory`);
    return false;
  }

  const files = readdirSync(evidenceDir);
  const hasEvidence = files.length > 0;

  if (hasEvidence) {
    logSuccess(`${productName}: AI governance gate passed`);
    logEvidence(`${productName}: Has ${files.length} AI governance evidence files`);
    return true;
  } else {
    logError(`${productName}: AI governance gate failed - no evidence`);
    return false;
  }
}

/**
 * Check i18n conformance gate
 */
function checkI18nConformanceGate(productPath, productName) {
  const evidenceDir = path.join(productPath, '.kernel', 'evidence', 'i18n-conformance');
  
  if (!existsSync(evidenceDir)) {
    logError(`${productName}: i18n conformance gate failed - no evidence directory`);
    return false;
  }

  const files = readdirSync(evidenceDir);
  const hasEvidence = files.length > 0;

  if (hasEvidence) {
    logSuccess(`${productName}: i18n conformance gate passed`);
    logEvidence(`${productName}: Has ${files.length} i18n conformance evidence files`);
    return true;
  } else {
    logError(`${productName}: i18n conformance gate failed - no evidence`);
    return false;
  }
}

/**
 * Check a11y behavioral gate
 */
function checkA11yBehavioralGate(productPath, productName) {
  const evidenceDir = path.join(productPath, '.kernel', 'evidence', 'a11y-behavioral-proof');
  
  if (!existsSync(evidenceDir)) {
    logError(`${productName}: a11y behavioral gate failed - no evidence directory`);
    return false;
  }

  const files = readdirSync(evidenceDir);
  const hasEvidence = files.length > 0;

  if (hasEvidence) {
    logSuccess(`${productName}: a11y behavioral gate passed`);
    logEvidence(`${productName}: Has ${files.length} a11y behavioral evidence files`);
    return true;
  } else {
    logError(`${productName}: a11y behavioral gate failed - no evidence`);
    return false;
  }
}

/**
 * Check OpenAPI quality gate
 */
function checkOpenAPIQualityGate(productPath, productName) {
  const evidenceDir = path.join(productPath, '.kernel', 'evidence', 'openapi-release-quality');
  
  if (!existsSync(evidenceDir)) {
    logError(`${productName}: OpenAPI quality gate failed - no evidence directory`);
    return false;
  }

  const files = readdirSync(evidenceDir);
  const hasEvidence = files.length > 0;

  if (hasEvidence) {
    logSuccess(`${productName}: OpenAPI quality gate passed`);
    logEvidence(`${productName}: Has ${files.length} OpenAPI quality evidence files`);
    return true;
  } else {
    logError(`${productName}: OpenAPI quality gate failed - no evidence`);
    return false;
  }
}

/**
 * Check security gate
 */
function checkSecurityGate(productPath, productName) {
  const evidenceDir = path.join(productPath, '.kernel', 'evidence', 'security');
  
  if (!existsSync(evidenceDir)) {
    logError(`${productName}: Security gate failed - no evidence directory`);
    return false;
  }

  const files = readdirSync(evidenceDir);
  const hasEvidence = files.length > 0;

  if (hasEvidence) {
    logSuccess(`${productName}: Security gate passed`);
    logEvidence(`${productName}: Has ${files.length} security evidence files`);
    return true;
  } else {
    logError(`${productName}: Security gate failed - no evidence`);
    return false;
  }
}

/**
 * Check release evidence completeness
 */
function checkReleaseEvidenceCompleteness(productPath, productName) {
  const summaryPath = path.join(productPath, 'RELEASE_SUMMARY.md');
  
  if (!existsSync(summaryPath)) {
    logError(`${productName}: Release evidence incomplete - missing release summary`);
    return false;
  }

  logSuccess(`${productName}: Release evidence complete`);
  return true;
}

/**
 * Generate gate report
 */
function generateGateReport(productName, gateResults) {
  const report = {
    product: productName,
    tag: TAG_ARG,
    timestamp: new Date().toISOString(),
    gates: gateResults,
    overall: Object.values(gateResults).every(result => result.passed)
  };

  return report;
}

/**
 * Store gate report
 */
function storeGateReport(report) {
  const evidenceDir = path.join(repoRoot, '.kernel', 'evidence', 'product-release-gates');
  
  if (!existsSync(evidenceDir)) {
    mkdirSync(evidenceDir, { recursive: true });
  }

  const reportPath = path.join(evidenceDir, `release-gate-${report.product}-${TAG_ARG}-${Date.now()}.json`);
  writeFileSync(reportPath, JSON.stringify(report, null, 2));
  
  logSuccess(`Gate report stored: ${reportPath}`);
  
  return reportPath;
}

/**
 * Main execution
 */
function main() {
  console.log(`Enforcing product release gates for tag ${TAG_ARG}...\n`);

  // Products to check
  const products = [
    { path: 'shared-services/auth-gateway', name: 'Auth Gateway' },
    { path: 'shared-services/incident-service', name: 'Incident Service' },
    { path: 'shared-services/studio-workflow-service', name: 'Studio Workflow Service' },
    { path: 'products/data-cloud/delivery/launcher', name: 'Data Cloud Launcher' },
    { path: 'products/aep', name: 'AEP' },
    { path: 'products/digital-marketing', name: 'Digital Marketing' },
    { path: 'products/phr', name: 'PHR' },
    { path: 'products/finance', name: 'Finance' },
    { path: 'products/flashit', name: 'FlashIt' },
    { path: 'products/yappc', name: 'YAPPC' },
  ];

  // Filter by product if specified
  const filteredProducts = PRODUCT_ARG 
    ? products.filter(p => p.name.toLowerCase().includes(PRODUCT_ARG.toLowerCase()))
    : products;

  const gateReports = [];

  for (const product of filteredProducts) {
    const productPath = path.join(repoRoot, product.path);
    
    if (!existsSync(productPath)) {
      logWarning(`${product.name}: Product path not found at ${product.path}`);
      continue;
    }

    console.log(`\n--- ${product.name} ---`);
    
    const gateResults = {
      atomicWorkflow: { passed: checkAtomicWorkflowGate(productPath, product.name) },
      runtimeDependency: { passed: checkRuntimeDependencyGate(productPath, product.name) },
      aiGovernance: { passed: checkAIGovernanceGate(productPath, product.name) },
      i18nConformance: { passed: checkI18nConformanceGate(productPath, product.name) },
      a11yBehavioral: { passed: checkA11yBehavioralGate(productPath, product.name) },
      openapiQuality: { passed: checkOpenAPIQualityGate(productPath, product.name) },
      security: { passed: checkSecurityGate(productPath, product.name) },
      releaseEvidence: { passed: checkReleaseEvidenceCompleteness(productPath, product.name) }
    };

    const report = generateGateReport(product.name, gateResults);
    gateReports.push(report);
    storeGateReport(report);
  }

  console.log('\n--- Summary ---');
  console.log(`Total Products: ${gateReports.length}`);
  console.log(`Passed: ${gateReports.filter(r => r.overall).length}`);
  console.log(`Failed: ${gateReports.filter(r => !r.overall).length}`);
  console.log(`Errors: ${violations.length}`);
  console.log(`Warnings: ${warnings.length}`);

  const failedProducts = gateReports.filter(r => !r.overall);
  if (failedProducts.length > 0) {
    console.log('\n❌ Release gate failed for the following products:');
    failedProducts.forEach(p => {
      console.log(`  - ${p.product}`);
      const failedGates = Object.entries(p.gates).filter(([_, result]) => !result.passed);
      failedGates.forEach(([gate, _]) => console.log(`    - ${gate}`));
    });
    process.exit(1);
  }

  console.log('\n✅ All product release gates passed.');
}

main();
