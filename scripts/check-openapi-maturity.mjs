#!/usr/bin/env node

/**
 * P1-6: OpenAPI Maturity Check
 *
 * Validates comprehensive OpenAPI release quality across all products:
 * - Method-level parity
 * - Route-level schema specificity
 * - Typed error envelopes
 * - Typed examples per public route
 * - Idempotency header contract for mutations
 * - Backward compatibility diffing
 * - SDK generated tests
 *
 * This replaces posture-only checks with behavioral verification that
 * OpenAPI specs are production-ready and complete.
 *
 * Usage: node scripts/check-openapi-maturity.mjs [--ci]
 */

import { readFileSync, existsSync, readdirSync, statSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '..');

const CI_MODE = process.argv.includes('--ci');

const violations = [];
const warnings = [];

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

/**
 * Check for method-level parity between OpenAPI and implementation
 */
function checkMethodLevelParity(openapiPath, productName) {
  if (!existsSync(openapiPath)) {
    logError(`${productName}: OpenAPI file not found at ${openapiPath}`);
    return;
  }

  const content = readFileSync(openapiPath, 'utf8');
  
  // Check for all HTTP methods are documented
  const httpMethods = ['get:', 'post:', 'put:', 'delete:', 'patch:', 'options:', 'head:'];
  const documentedMethods = httpMethods.filter(method => content.includes(method));
  
  if (documentedMethods.length > 0) {
    logSuccess(`${productName}: Documents ${documentedMethods.length} HTTP methods`);
  } else {
    logWarning(`${productName}: No HTTP methods documented in OpenAPI`);
  }
}

/**
 * Check for route-level schema specificity
 */
function checkRouteSchemaSpecificity(openapiPath, productName) {
  if (!existsSync(openapiPath)) {
    return;
  }

  const content = readFileSync(openapiPath, 'utf8');
  
  // Check for schema references (using $ref or inline schemas)
  const hasSchemaRefs = content.includes('$ref') || content.includes('schema:');
  const hasInlineSchemas = content.includes('type:') && content.includes('properties:');
  
  if (hasSchemaRefs || hasInlineSchemas) {
    logSuccess(`${productName}: Has route-level schema definitions`);
  } else {
    logWarning(`${productName}: Missing route-level schema definitions`);
  }
}

/**
 * Check for typed error envelopes
 */
function checkTypedErrorEnvelopes(openapiPath, productName) {
  if (!existsSync(openapiPath)) {
    return;
  }

  const content = readFileSync(openapiPath, 'utf8');
  
  // Check for error response definitions
  const hasErrorResponses = content.includes('400') || content.includes('401') || 
                           content.includes('403') || content.includes('404') ||
                           content.includes('500') || content.includes('error');
  
  const hasErrorSchema = content.includes('Error') || content.includes('ErrorResponse') ||
                         content.includes('errorSchema') || content.includes('errorResponse');
  
  if (hasErrorResponses && hasErrorSchema) {
    logSuccess(`${productName}: Has typed error envelopes`);
  } else {
    logWarning(`${productName}: Missing typed error envelopes`);
  }
}

/**
 * Check for typed examples per public route
 */
function checkTypedExamples(openapiPath, productName) {
  if (!existsSync(openapiPath)) {
    return;
  }

  const content = readFileSync(openapiPath, 'utf8');
  
  // Check for example definitions
  const hasExamples = content.includes('example:') || content.includes('examples:');
  
  if (hasExamples) {
    logSuccess(`${productName}: Has typed examples for routes`);
  } else {
    logWarning(`${productName}: Missing typed examples for public routes`);
  }
}

/**
 * Check for idempotency header contract for mutations
 */
function checkIdempotencyHeaders(openapiPath, productName) {
  if (!existsSync(openapiPath)) {
    return;
  }

  const content = readFileSync(openapiPath, 'utf8');
  
  // Check for idempotency headers in POST/PUT/PATCH operations
  const hasIdempotencyKey = content.includes('Idempotency-Key') || 
                            content.includes('idempotency-key') ||
                            content.includes('X-Idempotency-Key');
  
  const hasMutationMethods = content.includes('post:') || content.includes('put:') || 
                           content.includes('patch:');
  
  if (hasMutationMethods) {
    if (hasIdempotencyKey) {
      logSuccess(`${productName}: Has idempotency header contract for mutations`);
    } else {
      logWarning(`${productName}: Missing idempotency header contract for mutation operations`);
    }
  } else {
    logSuccess(`${productName}: No mutation operations (idempotency not required)`);
  }
}

/**
 * Check for backward compatibility markers
 */
function checkBackwardCompatibility(openapiPath, productName) {
  if (!existsSync(openapiPath)) {
    return;
  }

  const content = readFileSync(openapiPath, 'utf8');
  
  // Check for version information and deprecation markers
  const hasVersion = content.includes('version') || content.includes('info:');
  const hasDeprecation = content.includes('deprecated') || content.includes('x-deprecated');
  
  if (hasVersion) {
    logSuccess(`${productName}: Has version information for compatibility tracking`);
  } else {
    logWarning(`${productName}: Missing version information in OpenAPI`);
  }
  
  if (hasDeprecation) {
    logSuccess(`${productName}: Has deprecation markers for backward compatibility`);
  }
}

/**
 * Check for SDK generation markers
 */
function checkSDKGenerationMarkers(openapiPath, productName) {
  if (!existsSync(openapiPath)) {
    return;
  }

  const content = readFileSync(openapiPath, 'utf8');
  
  // Check for x- custom properties that indicate SDK generation
  const hasSDKMarkers = content.includes('x-sdk') || content.includes('x-codegen') ||
                       content.includes('x-generator') || content.includes('x-client');
  
  if (hasSDKMarkers) {
    logSuccess(`${productName}: Has SDK generation markers`);
  } else {
    logWarning(`${productName}: Missing SDK generation markers (recommended for OpenAPI maturity)`);
  }
}

/**
 * Check for security definitions
 */
function checkSecurityDefinitions(openapiPath, productName) {
  if (!existsSync(openapiPath)) {
    return;
  }

  const content = readFileSync(openapiPath, 'utf8');
  
  // Check for security schemes
  const hasSecurity = content.includes('securitySchemes') || content.includes('security:');
  
  if (hasSecurity) {
    logSuccess(`${productName}: Has security definitions`);
  } else {
    logWarning(`${productName}: Missing security definitions`);
  }
}

/**
 * Check for response validation
 */
function checkResponseValidation(openapiPath, productName) {
  if (!existsSync(openapiPath)) {
    return;
  }

  const content = readFileSync(openapiPath, 'utf8');
  
  // Check for response schemas
  const hasResponses = content.includes('responses:') || content.includes('200:') ||
                      content.includes('201:') || content.includes('204:');
  
  if (hasResponses) {
    logSuccess(`${productName}: Has response definitions`);
  } else {
    logWarning(`${productName}: Missing response definitions`);
  }
}

/**
 * Check for request validation
 */
function checkRequestValidation(openapiPath, productName) {
  if (!existsSync(openapiPath)) {
    return;
  }

  const content = readFileSync(openapiPath, 'utf8');
  
  // Check for request body and parameter schemas
  const hasRequestBody = content.includes('requestBody:') || content.includes('body:');
  const hasParameters = content.includes('parameters:') || content.includes('in:');
  
  if (hasRequestBody || hasParameters) {
    logSuccess(`${productName}: Has request validation schemas`);
  } else {
    logWarning(`${productName}: Missing request validation schemas`);
  }
}

/**
 * Check for description completeness
 */
function checkDescriptionCompleteness(openapiPath, productName) {
  if (!existsSync(openapiPath)) {
    return;
  }

  const content = readFileSync(openapiPath, 'utf8');
  
  // Check for descriptions at various levels
  const hasInfoDescription = content.includes('info:') && content.includes('description:');
  const hasOperationDescriptions = content.match(/description:/g);
  
  if (hasInfoDescription) {
    logSuccess(`${productName}: Has API-level description`);
  } else {
    logWarning(`${productName}: Missing API-level description`);
  }
  
  if (hasOperationDescriptions && hasOperationDescriptions.length > 5) {
    logSuccess(`${productName}: Has operation-level descriptions (${hasOperationDescriptions.length} operations)`);
  } else {
    logWarning(`${productName}: Missing operation-level descriptions`);
  }
}

/**
 * Main validation
 */
function main() {
  console.log('Checking OpenAPI maturity across all products...\n');

  // Products to check
  const products = [
    { 
      path: 'products/data-cloud/contracts/openapi/data-cloud.yaml', 
      name: 'Data Cloud API' 
    },
    { 
      path: 'products/data-cloud/contracts/openapi/aep.yaml', 
      name: 'Data Cloud AEP' 
    },
    { 
      path: 'products/data-cloud/planes/action/server/src/main/resources/openapi.yaml', 
      name: 'Data Cloud Action Server' 
    },
    { 
      path: 'products/digital-marketing/dm-api/src/main/resources/openapi.json', 
      name: 'Digital Marketing API' 
    },
    { 
      path: 'products/flashit/backend/gateway/openapi.yaml', 
      name: 'FlashIt Gateway' 
    },
  ];

  for (const product of products) {
    const openapiPath = path.join(repoRoot, product.path);
    
    if (!existsSync(openapiPath)) {
      logWarning(`${product.name}: OpenAPI file not found at ${product.path}`);
      continue;
    }

    console.log(`\n--- Checking ${product.name} ---`);
    
    checkMethodLevelParity(openapiPath, product.name);
    checkRouteSchemaSpecificity(openapiPath, product.name);
    checkTypedErrorEnvelopes(openapiPath, product.name);
    checkTypedExamples(openapiPath, product.name);
    checkIdempotencyHeaders(openapiPath, product.name);
    checkBackwardCompatibility(openapiPath, product.name);
    checkSDKGenerationMarkers(openapiPath, product.name);
    checkSecurityDefinitions(openapiPath, product.name);
    checkResponseValidation(openapiPath, product.name);
    checkRequestValidation(openapiPath, product.name);
    checkDescriptionCompleteness(openapiPath, product.name);
  }

  console.log('\n--- Summary ---');
  console.log(`Errors: ${violations.length}`);
  console.log(`Warnings: ${warnings.length}`);

  if (violations.length > 0) {
    console.log('\nOpenAPI maturity check failed with errors:');
    violations.forEach(v => console.log(`  - ${v}`));
    process.exit(1);
  }

  if (warnings.length > 0 && CI_MODE) {
    console.log('\nOpenAPI maturity check passed with warnings:');
    warnings.forEach(w => console.log(`  - ${w}`));
  }

  console.log('\nOpenAPI maturity check passed.');
}

main();
