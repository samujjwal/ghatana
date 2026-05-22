#!/usr/bin/env node

/**
 * Wave 2: Extend Strict Release Evidence from Data Cloud to Every Affected Product
 *
 * Extends the strict release evidence framework from Data Cloud to all affected products:
 * - Auth Gateway
 * - Incident Service
 * - Studio Workflow Service
 * - AEP
 * - Digital Marketing
 * - PHR
 * - Finance
 * - FlashIt
 * - YAPPC
 *
 * This ensures consistent release evidence standards across all business products.
 *
 * Usage: node scripts/extend-product-family-evidence.mjs [--product <product>]
 */

import { readFileSync, existsSync, readdirSync, statSync, writeFileSync, mkdirSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '..');

const PRODUCT_ARG = process.argv.find(arg => arg.startsWith('--product='))?.split('=')[1];

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
 * Check for atomic workflow failure-injection tests
 */
function checkAtomicWorkflowTests(productPath, productName) {
  const testDirs = [
    path.join(productPath, 'src/test/java'),
    path.join(productPath, 'src/__tests__'),
  ];

  let hasAtomicTests = false;

  for (const testDir of testDirs) {
    if (!existsSync(testDir)) continue;

    function searchDir(dir) {
      try {
        const items = readdirSync(dir);
        
        for (const item of items) {
          const itemPath = path.join(dir, item);
          const stat = statSync(itemPath);
          
          if (stat.isDirectory() && !item.includes('node_modules') && !item.includes('.git')) {
            searchDir(itemPath);
          } else if (item.endsWith('.java') || item.endsWith('.ts') || item.endsWith('.tsx')) {
            const content = readFileSync(itemPath, 'utf8');
            
            if (content.includes('AtomicWorkflow') || content.includes('atomic-workflow') ||
                content.includes('FailureInjection') || content.includes('failure-injection')) {
              hasAtomicTests = true;
              logEvidence(`${productName}: Has atomic workflow failure-injection tests`);
            }
          }
        }
      } catch (e) {
        // Skip directories we can't read
      }
    }

    searchDir(testDir);
  }

  if (hasAtomicTests) {
    logSuccess(`${productName}: Has atomic workflow failure-injection tests`);
  } else {
    logWarning(`${productName}: Missing atomic workflow failure-injection tests`);
  }

  return hasAtomicTests;
}

/**
 * Check for runtime dependency failure-injection tests
 */
function checkRuntimeDependencyTests(productPath, productName) {
  const testDirs = [
    path.join(productPath, 'src/test/java'),
    path.join(productPath, 'src/__tests__'),
  ];

  let hasRuntimeTests = false;

  for (const testDir of testDirs) {
    if (!existsSync(testDir)) continue;

    function searchDir(dir) {
      try {
        const items = readdirSync(dir);
        
        for (const item of items) {
          const itemPath = path.join(dir, item);
          const stat = statSync(itemPath);
          
          if (stat.isDirectory() && !item.includes('node_modules') && !item.includes('.git')) {
            searchDir(itemPath);
          } else if (item.endsWith('.java') || item.endsWith('.ts') || item.endsWith('.tsx')) {
            const content = readFileSync(itemPath, 'utf8');
            
            if (content.includes('RuntimeDependency') || content.includes('runtime-dependency') ||
                content.includes('DependencyResilience') || content.includes('dependency-resilience')) {
              hasRuntimeTests = true;
              logEvidence(`${productName}: Has runtime dependency failure-injection tests`);
            }
          }
        }
      } catch (e) {
        // Skip directories we can't read
      }
    }

    searchDir(testDir);
  }

  if (hasRuntimeTests) {
    logSuccess(`${productName}: Has runtime dependency failure-injection tests`);
  } else {
    logWarning(`${productName}: Missing runtime dependency failure-injection tests`);
  }

  return hasRuntimeTests;
}

/**
 * Check for AI governance tests
 */
function checkAIGovernanceTests(productPath, productName) {
  const testDirs = [
    path.join(productPath, 'src/test/java'),
    path.join(productPath, 'src/__tests__'),
  ];

  let hasAIGovernanceTests = false;

  for (const testDir of testDirs) {
    if (!existsSync(testDir)) continue;

    function searchDir(dir) {
      try {
        const items = readdirSync(dir);
        
        for (const item of items) {
          const itemPath = path.join(dir, item);
          const stat = statSync(itemPath);
          
          if (stat.isDirectory() && !item.includes('node_modules') && !item.includes('.git')) {
            searchDir(itemPath);
          } else if (item.endsWith('.java') || item.endsWith('.ts') || item.endsWith('.tsx')) {
            const content = readFileSync(itemPath, 'utf8');
            
            if (content.includes('AIGovernance') || content.includes('ai-governance') ||
                content.includes('ModelAvailability') || content.includes('model-availability')) {
              hasAIGovernanceTests = true;
              logEvidence(`${productName}: Has AI governance tests`);
            }
          }
        }
      } catch (e) {
        // Skip directories we can't read
      }
    }

    searchDir(testDir);
  }

  if (hasAIGovernanceTests) {
    logSuccess(`${productName}: Has AI governance tests`);
  } else {
    logWarning(`${productName}: Missing AI governance tests`);
  }

  return hasAIGovernanceTests;
}

/**
 * Check for release evidence directory
 */
function checkReleaseEvidenceDirectory(productPath, productName) {
  const evidenceDir = path.join(productPath, '.kernel', 'evidence');
  
  if (existsSync(evidenceDir)) {
    logSuccess(`${productName}: Has release evidence directory`);
    return true;
  } else {
    logWarning(`${productName}: Missing release evidence directory`);
    return false;
  }
}

/**
 * Check for release summary
 */
function checkReleaseSummary(productPath, productName) {
  const summaryPath = path.join(productPath, 'RELEASE_SUMMARY.md');
  
  if (existsSync(summaryPath)) {
    logSuccess(`${productName}: Has release summary`);
    return true;
  } else {
    logWarning(`${productName}: Missing release summary`);
    return false;
  }
}

/**
 * Generate evidence report
 */
function generateEvidenceReport() {
  const evidenceDir = path.join(repoRoot, '.kernel', 'evidence', 'product-family-evidence');
  
  if (!existsSync(evidenceDir)) {
    mkdirSync(evidenceDir, { recursive: true });
  }

  const report = {
    timestamp: new Date().toISOString(),
    violations,
    warnings,
    evidence,
    summary: {
      totalViolations: violations.length,
      totalWarnings: warnings.length,
      totalEvidence: evidence.length,
    }
  };

  const reportPath = path.join(evidenceDir, `product-family-evidence-${Date.now()}.json`);
  writeFileSync(reportPath, JSON.stringify(report, null, 2));
  
  console.log(`\n📄 Evidence report generated: ${reportPath}`);
}

/**
 * Main validation
 */
function main() {
  console.log('Extending strict release evidence from Data Cloud to all affected products...\n');

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

  for (const product of filteredProducts) {
    const productPath = path.join(repoRoot, product.path);
    
    if (!existsSync(productPath)) {
      logWarning(`${product.name}: Product path not found at ${product.path}`);
      continue;
    }

    console.log(`\n--- Checking ${product.name} ---`);
    
    checkAtomicWorkflowTests(productPath, product.name);
    checkRuntimeDependencyTests(productPath, product.name);
    checkAIGovernanceTests(productPath, product.name);
    checkReleaseEvidenceDirectory(productPath, product.name);
    checkReleaseSummary(productPath, product.name);
  }

  console.log('\n--- Summary ---');
  console.log(`Errors: ${violations.length}`);
  console.log(`Warnings: ${warnings.length}`);
  console.log(`Evidence items: ${evidence.length}`);

  generateEvidenceReport();

  if (violations.length > 0) {
    console.log('\nProduct family evidence extension failed with errors:');
    violations.forEach(v => console.log(`  - ${v}`));
    process.exit(1);
  }

  if (warnings.length > 0) {
    console.log('\nProduct family evidence extension passed with warnings:');
    warnings.forEach(w => console.log(`  - ${w}`));
  }

  console.log('\nProduct family evidence extension passed.');
}

main();
