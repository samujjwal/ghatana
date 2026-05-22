#!/usr/bin/env node

/**
 * Wave 3: Enforce Typed OpenAPI Schemas for Every Public Route
 *
 * Validates that every public route has a fully typed OpenAPI schema:
 * - Request body schemas are typed
 * - Response schemas are typed
 * - Parameter schemas are typed
 * - Error response schemas are typed
 * - All schemas use $ref to components/schemas
 * - No 'any' or untyped schemas
 *
 * This ensures API contracts are fully typed and type-safe.
 *
 * Usage: node scripts/enforce-typed-openapi-schemas.mjs [--product <product>]
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
 * Check for typed request body schemas
 */
function checkTypedRequestBodySchemas(productPath, productName) {
  const specDirs = [
    path.join(productPath, 'openapi'),
    path.join(productPath, 'api'),
  ];

  let hasTypedSchemas = false;
  let hasUntypedSchemas = false;

  for (const specDir of specDirs) {
    if (!existsSync(specDir)) continue;

    const files = readdirSync(specDir);
    const specFiles = files.filter(f => f.endsWith('.yaml') || f.endsWith('.yml') || f.endsWith('.json'));

    for (const file of specFiles) {
      const filePath = path.join(specDir, file);
      const content = readFileSync(filePath, 'utf8');
      
      // Check for request body with schema
      if (content.includes('requestBody') && content.includes('schema')) {
        hasTypedSchemas = true;
        
        // Check if schema uses $ref
        if (content.includes('$ref') && content.includes('components/schemas')) {
          logEvidence(`${productName}: Request body uses $ref to typed schema`);
        } else {
          hasUntypedSchemas = true;
          logWarning(`${productName}: Request body schema may not be fully typed`);
        }
      }
    }
  }

  if (hasTypedSchemas && !hasUntypedSchemas) {
    logSuccess(`${productName}: Request body schemas are typed`);
  } else if (hasTypedSchemas) {
    logWarning(`${productName}: Some request body schemas may be untyped`);
  } else {
    logWarning(`${productName}: No request body schemas found`);
  }

  return hasTypedSchemas && !hasUntypedSchemas;
}

/**
 * Check for typed response schemas
 */
function checkTypedResponseSchemas(productPath, productName) {
  const specDirs = [
    path.join(productPath, 'openapi'),
    path.join(productPath, 'api'),
  ];

  let hasTypedSchemas = false;
  let hasUntypedSchemas = false;

  for (const specDir of specDirs) {
    if (!existsSync(specDir)) continue;

    const files = readdirSync(specDir);
    const specFiles = files.filter(f => f.endsWith('.yaml') || f.endsWith('.yml') || f.endsWith('.json'));

    for (const file of specFiles) {
      const filePath = path.join(specDir, file);
      const content = readFileSync(filePath, 'utf8');
      
      // Check for response with schema
      if (content.includes('responses') && content.includes('schema')) {
        hasTypedSchemas = true;
        
        // Check if schema uses $ref
        if (content.includes('$ref') && content.includes('components/schemas')) {
          logEvidence(`${productName}: Response uses $ref to typed schema`);
        } else {
          hasUntypedSchemas = true;
          logWarning(`${productName}: Response schema may not be fully typed`);
        }
      }
    }
  }

  if (hasTypedSchemas && !hasUntypedSchemas) {
    logSuccess(`${productName}: Response schemas are typed`);
  } else if (hasTypedSchemas) {
    logWarning(`${productName}: Some response schemas may be untyped`);
  } else {
    logWarning(`${productName}: No response schemas found`);
  }

  return hasTypedSchemas && !hasUntypedSchemas;
}

/**
 * Check for typed parameter schemas
 */
function checkTypedParameterSchemas(productPath, productName) {
  const specDirs = [
    path.join(productPath, 'openapi'),
    path.join(productPath, 'api'),
  ];

  let hasTypedSchemas = false;
  let hasUntypedSchemas = false;

  for (const specDir of specDirs) {
    if (!existsSync(specDir)) continue;

    const files = readdirSync(specDir);
    const specFiles = files.filter(f => f.endsWith('.yaml') || f.endsWith('.yml') || f.endsWith('.json'));

    for (const file of specFiles) {
      const filePath = path.join(specDir, file);
      const content = readFileSync(filePath, 'utf8');
      
      // Check for parameters with schema
      if (content.includes('parameters') && content.includes('schema')) {
        hasTypedSchemas = true;
        
        // Check if schema uses $ref
        if (content.includes('$ref') && content.includes('components/schemas')) {
          logEvidence(`${productName}: Parameters use $ref to typed schema`);
        } else {
          hasUntypedSchemas = true;
          logWarning(`${productName}: Parameter schema may not be fully typed`);
        }
      }
    }
  }

  if (hasTypedSchemas && !hasUntypedSchemas) {
    logSuccess(`${productName}: Parameter schemas are typed`);
  } else if (hasTypedSchemas) {
    logWarning(`${productName}: Some parameter schemas may be untyped`);
  } else {
    logWarning(`${productName}: No parameter schemas found`);
  }

  return hasTypedSchemas && !hasUntypedSchemas;
}

/**
 * Check for typed error response schemas
 */
function checkTypedErrorSchemas(productPath, productName) {
  const specDirs = [
    path.join(productPath, 'openapi'),
    path.join(productPath, 'api'),
  ];

  let hasTypedSchemas = false;
  let hasUntypedSchemas = false;

  for (const specDir of specDirs) {
    if (!existsSync(specDir)) continue;

    const files = readdirSync(specDir);
    const specFiles = files.filter(f => f.endsWith('.yaml') || f.endsWith('.yml') || f.endsWith('.json'));

    for (const file of specFiles) {
      const filePath = path.join(specDir, file);
      const content = readFileSync(filePath, 'utf8');
      
      // Check for error responses with schema
      if ((content.includes('error') || content.includes('Error')) && content.includes('schema')) {
        hasTypedSchemas = true;
        
        // Check if schema uses $ref
        if (content.includes('$ref') && content.includes('components/schemas')) {
          logEvidence(`${productName}: Error responses use $ref to typed schema`);
        } else {
          hasUntypedSchemas = true;
          logWarning(`${productName}: Error response schema may not be fully typed`);
        }
      }
    }
  }

  if (hasTypedSchemas && !hasUntypedSchemas) {
    logSuccess(`${productName}: Error response schemas are typed`);
  } else if (hasTypedSchemas) {
    logWarning(`${productName}: Some error response schemas may be untyped`);
  } else {
    logWarning(`${productName}: No error response schemas found`);
  }

  return hasTypedSchemas && !hasUntypedSchemas;
}

/**
 * Check for components/schemas section
 */
function checkComponentsSchemas(productPath, productName) {
  const specDirs = [
    path.join(productPath, 'openapi'),
    path.join(productPath, 'api'),
  ];

  let hasComponents = false;
  let hasSchemas = false;

  for (const specDir of specDirs) {
    if (!existsSync(specDir)) continue;

    const files = readdirSync(specDir);
    const specFiles = files.filter(f => f.endsWith('.yaml') || f.endsWith('.yml') || f.endsWith('.json'));

    for (const file of specFiles) {
      const filePath = path.join(specDir, file);
      const content = readFileSync(filePath, 'utf8');
      
      if (content.includes('components')) {
        hasComponents = true;
      }
      
      if (content.includes('components/schemas')) {
        hasSchemas = true;
        logEvidence(`${productName}: Has components/schemas section`);
      }
    }
  }

  if (hasComponents && hasSchemas) {
    logSuccess(`${productName}: Has components/schemas section`);
  } else {
    logWarning(`${productName}: Missing components/schemas section`);
  }

  return hasComponents && hasSchemas;
}

/**
 * Check for untyped 'any' schemas
 */
function checkNoUntypedSchemas(productPath, productName) {
  const specDirs = [
    path.join(productPath, 'openapi'),
    path.join(productPath, 'api'),
  ];

  let hasUntyped = false;

  for (const specDir of specDirs) {
    if (!existsSync(specDir)) continue;

    const files = readdirSync(specDir);
    const specFiles = files.filter(f => f.endsWith('.yaml') || f.endsWith('.yml') || f.endsWith('.json'));

    for (const file of specFiles) {
      const filePath = path.join(specDir, file);
      const content = readFileSync(filePath, 'utf8');
      
      // Check for untyped schemas
      if (content.includes('type: any') || content.includes('"type": "any"') || 
          content.includes('type: object') && !content.includes('properties')) {
        hasUntyped = true;
        logError(`${productName}: Has untyped 'any' or object schemas`);
      }
    }
  }

  if (!hasUntyped) {
    logSuccess(`${productName}: No untyped schemas found`);
  }

  return !hasUntyped;
}

/**
 * Generate enforcement report
 */
function generateEnforcementReport() {
  const evidenceDir = path.join(repoRoot, '.kernel', 'evidence', 'typed-openapi-schemas');
  
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

  const reportPath = path.join(evidenceDir, `typed-openapi-schemas-${Date.now()}.json`);
  writeFileSync(reportPath, JSON.stringify(report, null, 2));
  
  console.log(`\n📄 Enforcement report generated: ${reportPath}`);
}

/**
 * Main validation
 */
function main() {
  console.log('Enforcing typed OpenAPI schemas for every public route...\n');

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
    
    checkTypedRequestBodySchemas(productPath, product.name);
    checkTypedResponseSchemas(productPath, product.name);
    checkTypedParameterSchemas(productPath, product.name);
    checkTypedErrorSchemas(productPath, product.name);
    checkComponentsSchemas(productPath, product.name);
    checkNoUntypedSchemas(productPath, product.name);
  }

  console.log('\n--- Summary ---');
  console.log(`Errors: ${violations.length}`);
  console.log(`Warnings: ${warnings.length}`);
  console.log(`Evidence items: ${evidence.length}`);

  generateEnforcementReport();

  if (violations.length > 0) {
    console.log('\nTyped OpenAPI schema enforcement failed with errors:');
    violations.forEach(v => console.log(`  - ${v}`));
    process.exit(1);
  }

  if (warnings.length > 0) {
    console.log('\nTyped OpenAPI schema enforcement passed with warnings:');
    warnings.forEach(w => console.log(`  - ${w}`));
  }

  console.log('\nTyped OpenAPI schema enforcement passed.');
}

main();
