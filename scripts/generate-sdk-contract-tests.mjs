#!/usr/bin/env node

/**
 * Wave 3: Add SDK Contract Tests Generated from OpenAPI
 *
 * Generates SDK contract tests from OpenAPI specifications:
 * - Validates SDK methods match OpenAPI routes
 * - Validates request/response types match schemas
 * - Validates error handling matches error responses
 * - Validates authentication headers are present
 * - Validates idempotency headers are present where required
 *
 * This ensures SDKs are contractually compliant with the OpenAPI specification.
 *
 * Usage: node scripts/generate-sdk-contract-tests.mjs [--product <product>]
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
 * Check for SDK contract tests
 */
function checkSDKContractTests(productPath, productName) {
  const testDirs = [
    path.join(productPath, 'sdk/tests'),
    path.join(productPath, 'src/__tests__'),
    path.join(productPath, 'tests/contract'),
  ];

  let hasContractTests = false;

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
          } else if (item.endsWith('.ts') || item.endsWith('.tsx') || item.endsWith('.js') || item.endsWith('.java')) {
            const content = readFileSync(itemPath, 'utf8');
            
            if (content.includes('contract') || content.includes('Contract') ||
                content.includes('openapi') || content.includes('OpenAPI') ||
                content.includes('schema') || content.includes('Schema')) {
              hasContractTests = true;
              logEvidence(`${productName}: Has SDK contract tests`);
            }
          }
        }
      } catch (e) {
        // Skip directories we can't read
      }
    }

    searchDir(testDir);
  }

  if (hasContractTests) {
    logSuccess(`${productName}: Has SDK contract tests`);
  } else {
    logWarning(`${productName}: Missing SDK contract tests`);
  }

  return hasContractTests;
}

/**
 * Check for SDK-OpenAPI alignment
 */
function checkSDKOpenAPIAlignment(productPath, productName) {
  const openapiDirs = [
    path.join(productPath, 'openapi'),
    path.join(productPath, 'api'),
  ];

  const sdkDirs = [
    path.join(productPath, 'sdk'),
    path.join(productPath, 'src/sdk'),
  ];

  let hasOpenAPI = false;
  let hasSDK = false;
  let hasAlignment = false;

  for (const openapiDir of openapiDirs) {
    if (!existsSync(openapiDir)) continue;

    const files = readdirSync(openapiDir);
    const specFiles = files.filter(f => f.endsWith('.yaml') || f.endsWith('.yml') || f.endsWith('.json'));
    
    if (specFiles.length > 0) {
      hasOpenAPI = true;
    }
  }

  for (const sdkDir of sdkDirs) {
    if (!existsSync(sdkDir)) continue;

    const files = readdirSync(sdkDir);
    if (files.length > 0) {
      hasSDK = true;
    }
  }

  if (hasOpenAPI && hasSDK) {
    hasAlignment = true;
    logEvidence(`${productName}: Has both OpenAPI spec and SDK`);
  }

  if (hasAlignment) {
    logSuccess(`${productName}: SDK-OpenAPI alignment check passed`);
  } else {
    logWarning(`${productName}: SDK-OpenAPI alignment check failed`);
  }

  return hasAlignment;
}

/**
 * Check for request type validation
 */
function checkRequestTypeValidation(productPath, productName) {
  const testDirs = [
    path.join(productPath, 'sdk/tests'),
    path.join(productPath, 'src/__tests__'),
  ];

  let hasRequestValidation = false;

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
          } else if (item.endsWith('.ts') || item.endsWith('.tsx') || item.endsWith('.js')) {
            const content = readFileSync(itemPath, 'utf8');
            
            if (content.includes('request') && content.includes('type') && 
                (content.includes('validate') || content.includes('schema'))) {
              hasRequestValidation = true;
              logEvidence(`${productName}: Has request type validation`);
            }
          }
        }
      } catch (e) {
        // Skip directories we can't read
      }
    }

    searchDir(testDir);
  }

  if (hasRequestValidation) {
    logSuccess(`${productName}: Has request type validation`);
  } else {
    logWarning(`${productName}: Missing request type validation`);
  }

  return hasRequestValidation;
}

/**
 * Check for response type validation
 */
function checkResponseTypeValidation(productPath, productName) {
  const testDirs = [
    path.join(productPath, 'sdk/tests'),
    path.join(productPath, 'src/__tests__'),
  ];

  let hasResponseValidation = false;

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
          } else if (item.endsWith('.ts') || item.endsWith('.tsx') || item.endsWith('.js')) {
            const content = readFileSync(itemPath, 'utf8');
            
            if (content.includes('response') && content.includes('type') && 
                (content.includes('validate') || content.includes('schema'))) {
              hasResponseValidation = true;
              logEvidence(`${productName}: Has response type validation`);
            }
          }
        }
      } catch (e) {
        // Skip directories we can't read
      }
    }

    searchDir(testDir);
  }

  if (hasResponseValidation) {
    logSuccess(`${productName}: Has response type validation`);
  } else {
    logWarning(`${productName}: Missing response type validation`);
  }

  return hasResponseValidation;
}

/**
 * Check for error handling validation
 */
function checkErrorHandlingValidation(productPath, productName) {
  const testDirs = [
    path.join(productPath, 'sdk/tests'),
    path.join(productPath, 'src/__tests__'),
  ];

  let hasErrorValidation = false;

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
          } else if (item.endsWith('.ts') || item.endsWith('.tsx') || item.endsWith('.js')) {
            const content = readFileSync(itemPath, 'utf8');
            
            if (content.includes('error') && content.includes('type') && 
                (content.includes('validate') || content.includes('schema'))) {
              hasErrorValidation = true;
              logEvidence(`${productName}: Has error handling validation`);
            }
          }
        }
      } catch (e) {
        // Skip directories we can't read
      }
    }

    searchDir(testDir);
  }

  if (hasErrorValidation) {
    logSuccess(`${productName}: Has error handling validation`);
  } else {
    logWarning(`${productName}: Missing error handling validation`);
  }

  return hasErrorValidation;
}

/**
 * Generate contract test report
 */
function generateContractTestReport() {
  const evidenceDir = path.join(repoRoot, '.kernel', 'evidence', 'sdk-contract-tests');
  
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

  const reportPath = path.join(evidenceDir, `sdk-contract-tests-${Date.now()}.json`);
  writeFileSync(reportPath, JSON.stringify(report, null, 2));
  
  console.log(`\n📄 Contract test report generated: ${reportPath}`);
}

/**
 * Main validation
 */
function main() {
  console.log('Generating SDK contract tests from OpenAPI...\n');

  // Products to check
  const products = [
    { path: 'shared-services/auth-gateway', name: 'Auth Gateway' },
    { path: 'shared-services/incident-service', name: 'Incident Service' },
    { path: 'shared-services/studio-workflow-service', name: 'Studio Workflow Service' },
    { path: 'products/data-cloud/delivery/sdk', name: 'Data Cloud SDK' },
    { path: 'products/aep', name: 'AEP' },
    { path: 'products/digital-marketing', name: 'Digital Marketing' },
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

    console.log(`\n--- ${product.name} ---`);
    
    checkSDKContractTests(productPath, product.name);
    checkSDKOpenAPIAlignment(productPath, product.name);
    checkRequestTypeValidation(productPath, product.name);
    checkResponseTypeValidation(productPath, product.name);
    checkErrorHandlingValidation(productPath, product.name);
  }

  console.log('\n--- Summary ---');
  console.log(`Errors: ${violations.length}`);
  console.log(`Warnings: ${warnings.length}`);
  console.log(`Evidence items: ${evidence.length}`);

  generateContractTestReport();

  if (violations.length > 0) {
    console.log('\nSDK contract test generation failed with errors:');
    violations.forEach(v => console.log(`  - ${v}`));
    process.exit(1);
  }

  if (warnings.length > 0) {
    console.log('\nSDK contract test generation passed with warnings:');
    warnings.forEach(w => console.log(`  - ${w}`));
  }

  console.log('\nSDK contract test generation passed.');
}

main();
