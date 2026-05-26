#!/usr/bin/env node
/**
 * OpenAPI Route Manifest Parity Validator
 * 
 * Validates that all routes in the route-manifest.yaml exist in the OpenAPI spec
 * and that metadata (auth, lifecycle, operationId) is consistent.
 * 
 * Usage: node scripts/validate-openapi-parity.js <openapi.yaml> <route-manifest.yaml>
 */

const fs = require('fs');
const path = require('path');
const yaml = require('yaml');

const OPENAPI_FILE = process.argv[2];
const MANIFEST_FILE = process.argv[3];
const stableOnly = process.argv.includes('--stable-only');

if (!OPENAPI_FILE || !MANIFEST_FILE) {
  console.error('Usage: node validate-openapi-parity.js <openapi.yaml> <route-manifest.yaml>');
  process.exit(1);
}

// Load files
const openapiDoc = yaml.parse(fs.readFileSync(OPENAPI_FILE, 'utf8'));
const manifestDoc = yaml.parse(fs.readFileSync(MANIFEST_FILE, 'utf8'));

let errors = [];
let warnings = [];

// Extract OpenAPI paths
const openapiPaths = openapiDoc.paths || {};
const openapiOperations = {};

for (const [path, methods] of Object.entries(openapiPaths)) {
  for (const [method, operation] of Object.entries(methods)) {
    const key = routeKey(method, path);
    openapiOperations[key] = {
      operationId: operation.operationId,
      security: operation.security || [],
      tags: operation.tags || [],
    };
  }
}

function manifestRoutes(doc) {
  if (Array.isArray(doc?.routes)) {
    return doc.routes.map(route => ({ owner: route.servlet || doc.product || 'manifest', route }));
  }

  const routes = [];
  for (const [owner, ownerRoutes] of Object.entries(doc || {})) {
    if (owner.startsWith('#') || owner === '───') continue;
    if (!Array.isArray(ownerRoutes)) continue;
    for (const route of ownerRoutes) {
      routes.push({ owner, route });
    }
  }
  return routes;
}

function normalizePath(routePath) {
  return routePath
    .replace(/:([A-Za-z0-9_]+)/g, '{$1}')
    .replace(/\{[A-Za-z0-9_]+\}/g, '{param}');
}

function routeKey(method, routePath) {
  return `${method.toUpperCase()} ${normalizePath(routePath)}`;
}

// Validate manifest routes
for (const { owner, route } of manifestRoutes(manifestDoc)) {
    if (stableOnly && route.lifecycle && route.lifecycle !== 'stable') {
      continue;
    }
    if (stableOnly && route.servlet === null) {
      continue;
    }
    if (!route.method || !route.path) {
      errors.push(`Invalid route in ${owner}: missing method or path`);
      continue;
    }

    const key = routeKey(route.method, route.path);
    const openapiOp = openapiOperations[key];

    if (!openapiOp) {
      errors.push(`Route ${route.method.toUpperCase()} ${route.path} (owner: ${route.owner || owner}) not found in OpenAPI spec`);
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

    // Validate lifecycle is declared using the manifest schema.
    if (!route.lifecycle) {
      errors.push(`Route ${key}: missing lifecycle field in manifest`);
    } else if (!['stable', 'boundary'].includes(route.lifecycle)) {
      errors.push(`Route ${key}: invalid lifecycle '${route.lifecycle}' in manifest`);
    }
}

// Check for OpenAPI routes not in manifest (optional, can be noisy)
for (const key of Object.keys(openapiOperations)) {
  let found = false;
  for (const { route } of manifestRoutes(manifestDoc)) {
    if (stableOnly && route.lifecycle && route.lifecycle !== 'stable') {
      continue;
    }
    if (stableOnly && route.servlet === null) {
      continue;
    }
    if (route.method && route.path && routeKey(route.method, route.path) === key) {
      found = true;
      break;
    }
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
