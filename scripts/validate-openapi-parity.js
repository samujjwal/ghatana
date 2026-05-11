#!/usr/bin/env node
/**
 * OpenAPI Route Manifest Parity Validator
 * 
 * Validates that all routes in the route-manifest.yaml exist in the OpenAPI spec
 * and that metadata (auth, scopes, operationId) is consistent.
 * 
 * Usage: node scripts/validate-openapi-parity.js <openapi.yaml> <route-manifest.yaml>
 */

const fs = require('fs');
const path = require('path');
const yaml = require('js-yaml');

const OPENAPI_FILE = process.argv[2];
const MANIFEST_FILE = process.argv[3];

if (!OPENAPI_FILE || !MANIFEST_FILE) {
  console.error('Usage: node validate-openapi-parity.js <openapi.yaml> <route-manifest.yaml>');
  process.exit(1);
}

// Load files
const openapiDoc = yaml.load(fs.readFileSync(OPENAPI_FILE, 'utf8'));
const manifestDoc = yaml.load(fs.readFileSync(MANIFEST_FILE, 'utf8'));

let errors = [];
let warnings = [];

// Extract OpenAPI paths
const openapiPaths = openapiDoc.paths || {};
const openapiOperations = {};

for (const [path, methods] of Object.entries(openapiPaths)) {
  for (const [method, operation] of Object.entries(methods)) {
    const key = `${method.toUpperCase()} ${path}`;
    openapiOperations[key] = {
      operationId: operation.operationId,
      security: operation.security || [],
      tags: operation.tags || [],
    };
  }
}

// Validate manifest routes
for (const [owner, routes] of Object.entries(manifestDoc)) {
  if (owner.startsWith('#') || owner === '───') continue; // Skip comments and section headers
  
  for (const route of routes) {
    if (!route.method || !route.path) {
      errors.push(`Invalid route in ${owner}: missing method or path`);
      continue;
    }

    const key = `${route.method.toUpperCase()} ${route.path}`;
    const openapiOp = openapiOperations[key];

    if (!openapiOp) {
      errors.push(`Route ${key} (owner: ${route.owner}) not found in OpenAPI spec`);
      continue;
    }

    // Validate operationId matches
    if (route.operationId && openapiOp.operationId !== route.operationId) {
      errors.push(
        `Route ${key}: operationId mismatch - manifest: ${route.operationId}, OpenAPI: ${openapiOp.operationId}`
      );
    }

    // Validate auth level
    const hasSecurity = openapiOp.security && openapiOp.security.length > 0;
    const manifestAuth = route.auth;
    
    if (manifestAuth === 'public' && hasSecurity) {
      warnings.push(
        `Route ${key}: marked as public in manifest but has security in OpenAPI`
      );
    } else if (manifestAuth === 'required' && !hasSecurity) {
      warnings.push(
        `Route ${key}: marked as required auth in manifest but no security in OpenAPI`
      );
    }

    // Validate boundary is declared
    if (!route.boundary) {
      warnings.push(`Route ${key}: missing boundary field in manifest`);
    }
  }
}

// Check for OpenAPI routes not in manifest (optional, can be noisy)
for (const key of Object.keys(openapiOperations)) {
  let found = false;
  for (const [owner, routes] of Object.entries(manifestDoc)) {
    if (owner.startsWith('#') || owner === '───') continue;
    for (const route of routes) {
      if (`${route.method.toUpperCase()} ${route.path}` === key) {
        found = true;
        break;
      }
    }
    if (found) break;
  }
  if (!found) {
    warnings.push(`OpenAPI route ${key} not found in route manifest`);
  }
}

// Report results
console.log(`\n🔍 OpenAPI Parity Validation`);
console.log(`   OpenAPI: ${OPENAPI_FILE}`);
console.log(`   Manifest: ${MANIFEST_FILE}\n`);

if (errors.length > 0) {
  console.error(`❌ ${errors.length} error(s) found:\n`);
  errors.forEach(err => console.error(`   - ${err}`));
}

if (warnings.length > 0) {
  console.warn(`⚠️  ${warnings.length} warning(s) found:\n`);
  warnings.forEach(warn => console.warn(`   - ${warn}`));
}

if (errors.length === 0 && warnings.length === 0) {
  console.log(`✅ All checks passed!`);
  process.exit(0);
} else if (errors.length === 0) {
  console.log(`⚠️  Passed with warnings`);
  process.exit(0);
} else {
  console.error(`\n❌ Validation failed`);
  process.exit(1);
}
