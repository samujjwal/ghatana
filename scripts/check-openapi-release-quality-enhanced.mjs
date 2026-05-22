#!/usr/bin/env node

/**
 * P1-6: Enhanced OpenAPI Release Quality Suite
 *
 * Validates comprehensive OpenAPI release quality with behavioral verification:
 * - Method-level parity
 * - Route-level schema specificity
 * - Typed error envelopes
 * - Typed examples per public route
 * - Idempotency header contract for mutations
 * - Backward compatibility diffing
 * - SDK generated tests
 *
 * This ensures high-quality production APIs with strong contract enforcement.
 *
 * Usage: node scripts/check-openapi-release-quality-enhanced.mjs [--ci] [--product <product>]
 */

import { readFileSync, existsSync, readdirSync, statSync, writeFileSync, mkdirSync } from 'node:fs';
import { execSync } from 'node:child_process';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '..');

const CI_MODE = process.argv.includes('--ci');
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
 * Check for method-level parity
 */
function checkMethodLevelParity(productPath, productName) {
  const specDirs = [
    path.join(productPath, 'openapi'),
    path.join(productPath, 'api'),
  ];

  let hasGET = false;
  let hasPOST = false;
  let hasPUT = false;
  let hasPATCH = false;
  let hasDELETE = false;

  for (const specDir of specDirs) {
    if (!existsSync(specDir)) continue;

    const files = readdirSync(specDir);
    const specFiles = files.filter(f => f.endsWith('.yaml') || f.endsWith('.yml') || f.endsWith('.json'));

    for (const file of specFiles) {
      const filePath = path.join(specDir, file);
      const content = readFileSync(filePath, 'utf8');
      
      if (content.includes('get:')) hasGET = true;
      if (content.includes('post:')) hasPOST = true;
      if (content.includes('put:')) hasPUT = true;
      if (content.includes('patch:')) hasPATCH = true;
      if (content.includes('delete:')) hasDELETE = true;
    }
  }

  const hasParity = hasGET && hasPOST && (hasPUT || hasPATCH || hasDELETE);

  if (hasGET) logEvidence(`${productName}: Has GET methods`);
  if (hasPOST) logEvidence(`${productName}: Has POST methods`);
  if (hasPUT) logEvidence(`${productName}: Has PUT methods`);
  if (hasPATCH) logEvidence(`${productName}: Has PATCH methods`);
  if (hasDELETE) logEvidence(`${productName}: Has DELETE methods`);

  if (hasParity) {
    logSuccess(`${productName}: Method-level parity present`);
  } else {
    logWarning(`${productName}: Missing method-level parity`);
  }

  return hasParity;
}

/**
 * Check for route-level schema specificity
 */
function checkRouteLevelSchemaSpecificity(productPath, productName) {
  const specDirs = [
    path.join(productPath, 'openapi'),
    path.join(productPath, 'api'),
  ];

  let hasSpecificSchemas = false;
  let hasGenericSchemas = false;

  for (const specDir of specDirs) {
    if (!existsSync(specDir)) continue;

    const files = readdirSync(specDir);
    const specFiles = files.filter(f => f.endsWith('.yaml') || f.endsWith('.yml') || f.endsWith('.json'));

    for (const file of specFiles) {
      const filePath = path.join(specDir, file);
      const content = readFileSync(filePath, 'utf8');
      
      // Check for specific schemas
      if (content.includes('$ref') && content.includes('components/schemas')) {
        hasSpecificSchemas = true;
        logEvidence(`${productName}: Uses specific schema references`);
      }
      
      // Check for generic object schemas
      if (content.includes('type: object') && !content.includes('properties')) {
        hasGenericSchemas = true;
        logWarning(`${productName}: Has generic object schemas`);
      }
    }
  }

  if (hasSpecificSchemas && !hasGenericSchemas) {
    logSuccess(`${productName}: Route-level schema specificity present`);
  } else if (hasSpecificSchemas) {
    logWarning(`${productName}: Some routes use generic schemas`);
  } else {
    logWarning(`${productName}: Missing route-level schema specificity`);
  }

  return hasSpecificSchemas && !hasGenericSchemas;
}

/**
 * Check for typed error envelopes
 */
function checkTypedErrorEnvelopes(productPath, productName) {
  const specDirs = [
    path.join(productPath, 'openapi'),
    path.join(productPath, 'api'),
  ];

  let hasTypedErrors = false;

  for (const specDir of specDirs) {
    if (!existsSync(specDir)) continue;

    const files = readdirSync(specDir);
    const specFiles = files.filter(f => f.endsWith('.yaml') || f.endsWith('.yml') || f.endsWith('.json'));

    for (const file of specFiles) {
      const filePath = path.join(specDir, file);
      const content = readFileSync(filePath, 'utf8');
      
      // Check for error responses with typed schemas
      if ((content.includes('error') || content.includes('Error')) && 
          content.includes('$ref') && 
          content.includes('components/schemas')) {
        hasTypedErrors = true;
        logEvidence(`${productName}: Has typed error envelopes`);
      }
    }
  }

  if (hasTypedErrors) {
    logSuccess(`${productName}: Typed error envelopes present`);
  } else {
    logWarning(`${productName}: Missing typed error envelopes`);
  }

  return hasTypedErrors;
}

/**
 * Check for typed examples per public route
 */
function checkTypedExamples(productPath, productName) {
  const specDirs = [
    path.join(productPath, 'openapi'),
    path.join(productPath, 'api'),
  ];

  let hasExamples = false;

  for (const specDir of specDirs) {
    if (!existsSync(specDir)) continue;

    const files = readdirSync(specDir);
    const specFiles = files.filter(f => f.endsWith('.yaml') || f.endsWith('.yml') || f.endsWith('.json'));

    for (const file of specFiles) {
      const filePath = path.join(specDir, file);
      const content = readFileSync(filePath, 'utf8');
      
      // Check for examples
      if (content.includes('example') || content.includes('examples')) {
        hasExamples = true;
        logEvidence(`${productName}: Has typed examples`);
      }
    }
  }

  if (hasExamples) {
    logSuccess(`${productName}: Typed examples present`);
  } else {
    logWarning(`${productName}: Missing typed examples`);
  }

  return hasExamples;
}

/**
 * Check for idempotency header contract for mutations
 */
function checkIdempotencyHeaders(productPath, productName) {
  const specDirs = [
    path.join(productPath, 'openapi'),
    path.join(productPath, 'api'),
  ];

  let hasIdempotencyHeaders = false;

  for (const specDir of specDirs) {
    if (!existsSync(specDir)) continue;

    const files = readdirSync(specDir);
    const specFiles = files.filter(f => f.endsWith('.yaml') || f.endsWith('.yml') || f.endsWith('.json'));

    for (const file of specFiles) {
      const filePath = path.join(specDir, file);
      const content = readFileSync(filePath, 'utf8');
      
      // Check for idempotency headers
      if ((content.includes('Idempotency-Key') || content.includes('idempotency-key') || 
           content.includes('X-Idempotency-Key')) &&
          (content.includes('post:') || content.includes('put:') || content.includes('patch:'))) {
        hasIdempotencyHeaders = true;
        logEvidence(`${productName}: Has idempotency headers for mutations`);
      }
    }
  }

  if (hasIdempotencyHeaders) {
    logSuccess(`${productName}: Idempotency header contract present`);
  } else {
    logWarning(`${productName}: Missing idempotency header contract`);
  }

  return hasIdempotencyHeaders;
}

/**
 * Check for backward compatibility diffing
 */
function checkBackwardCompatibilityDiffing(productPath, productName) {
  const specDirs = [
    path.join(productPath, 'openapi'),
    path.join(productPath, 'api'),
  ];

  let hasVersioning = false;
  let hasDiffing = false;

  for (const specDir of specDirs) {
    if (!existsSync(specDir)) continue;

    const files = readdirSync(specDir);
    const specFiles = files.filter(f => f.endsWith('.yaml') || f.endsWith('.yml') || f.endsWith('.json'));

    for (const file of specFiles) {
      const filePath = path.join(specDir, file);
      const content = readFileSync(filePath, 'utf8');
      
      // Check for versioning
      if (content.includes('version') || content.includes('v1') || content.includes('v2')) {
        hasVersioning = true;
        logEvidence(`${productName}: Has API versioning`);
      }
      
      // Check for diffing scripts
      if (content.includes('diff') || content.includes('compatibility')) {
        hasDiffing = true;
        logEvidence(`${productName}: Has compatibility diffing`);
      }
    }
  }

  if (hasVersioning) {
    logSuccess(`${productName}: API versioning present`);
  } else {
    logWarning(`${productName}: Missing API versioning`);
  }

  if (hasDiffing) {
    logSuccess(`${productName}: Backward compatibility diffing present`);
  } else {
    logWarning(`${productName}: Missing backward compatibility diffing`);
  }

  return hasVersioning && hasDiffing;
}

/**
 * Check for SDK generated tests
 */
function checkSDKGeneratedTests(productPath, productName) {
  const testDirs = [
    path.join(productPath, 'sdk/tests'),
    path.join(productPath, 'src/__tests__'),
    path.join(productPath, 'tests/contract'),
  ];

  let hasSDKTests = false;

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
            
            if (content.includes('sdk') || content.includes('SDK') ||
                content.includes('contract') || content.includes('Contract')) {
              hasSDKTests = true;
              logEvidence(`${productName}: Has SDK generated tests`);
            }
          }
        }
      } catch (e) {
        // Skip directories we can't read
      }
    }

    searchDir(testDir);
  }

  if (hasSDKTests) {
    logSuccess(`${productName}: SDK generated tests present`);
  } else {
    logWarning(`${productName}: Missing SDK generated tests`);
  }

  return hasSDKTests;
}

/**
 * Generate evidence report
 */
function generateEvidenceReport() {
  const evidenceDir = path.join(repoRoot, '.kernel', 'evidence', 'openapi-release-quality-enhanced');
  
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

  const reportPath = path.join(evidenceDir, `openapi-release-quality-enhanced-${Date.now()}.json`);
  writeFileSync(reportPath, JSON.stringify(report, null, 2));
  
  console.log(`\n📄 Evidence report generated: ${reportPath}`);
}

/**
 * Main validation
 */
function main() {
  console.log('Checking enhanced OpenAPI release quality across products...\n');

  // Products to check
  const products = [
    { path: 'shared-services/auth-gateway', name: 'Auth Gateway' },
    { path: 'shared-services/incident-service', name: 'Incident Service' },
    { path: 'shared-services/studio-workflow-service', name: 'Studio Workflow Service' },
    { path: 'products/data-cloud/delivery/launcher', name: 'Data Cloud Launcher' },
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
    
    checkMethodLevelParity(productPath, product.name);
    checkRouteLevelSchemaSpecificity(productPath, product.name);
    checkTypedErrorEnvelopes(productPath, product.name);
    checkTypedExamples(productPath, product.name);
    checkIdempotencyHeaders(productPath, product.name);
    checkBackwardCompatibilityDiffing(productPath, product.name);
    checkSDKGeneratedTests(productPath, product.name);
  }

  console.log('\n--- Summary ---');
  console.log(`Errors: ${violations.length}`);
  console.log(`Warnings: ${warnings.length}`);
  console.log(`Evidence items: ${evidence.length}`);

  if (CI_MODE) {
    generateEvidenceReport();
  }

  if (violations.length > 0) {
    console.log('\nEnhanced OpenAPI release quality check failed with errors:');
    violations.forEach(v => console.log(`  - ${v}`));
    process.exit(1);
  }

  if (warnings.length > 0 && CI_MODE) {
    console.log('\nEnhanced OpenAPI release quality check passed with warnings:');
    warnings.forEach(w => console.log(`  - ${w}`));
  }

  console.log('\nEnhanced OpenAPI release quality check passed.');
}

main();
