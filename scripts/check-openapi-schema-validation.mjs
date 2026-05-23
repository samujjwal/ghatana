#!/usr/bin/env node

/**
 * OpenAPI Schema Validation Script
 * 
 * Validates OpenAPI specifications for schema correctness, completeness, and consistency.
 * This complements the existing parity validation by ensuring schemas are well-formed.
 * 
 * Usage: node scripts/check-openapi-schema-validation.mjs <openapi-file>
 */

import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = fileURLToPath(new URL('.', import.meta.url));
const repoRoot = resolve(__dirname, '..');

function parseOpenAPI(content) {
  try {
    // Try JSON first
    if (content.trim().startsWith('{')) {
      return JSON.parse(content);
    }
    // Try YAML (simple parser for basic YAML)
    return parseSimpleYAML(content);
  } catch (error) {
    return null;
  }
}

function parseSimpleYAML(content) {
  // Very basic YAML parser for OpenAPI specs
  const lines = content.split('\n');
  const result = {};
  let currentPath = [];
  let currentSection = result;
  
  for (const line of lines) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith('#')) continue;
    
    const indent = line.search(/\S/);
    const parts = trimmed.split(':');
    if (parts.length < 2) continue;
    
    const key = parts[0].trim();
    const value = parts.slice(1).join(':').trim();
    
    // Handle nested structure based on indentation
    // This is a simplified approach - for production, use a proper YAML parser
  }
  
  return result;
}

function validateOpenAPISpec(spec, filePath) {
  const violations = [];
  
  if (!spec) {
    violations.push(`Failed to parse OpenAPI spec: ${filePath}`);
    return violations;
  }
  
  // Check OpenAPI version
  const openapiVersion = spec.openapi || spec.swagger;
  if (!openapiVersion) {
    violations.push(`Missing OpenAPI version in ${filePath}`);
  } else if (!openapiVersion.startsWith('3.')) {
    violations.push(`Unsupported OpenAPI version ${openapiVersion} in ${filePath} (expected 3.x)`);
  }
  
  // Check required fields
  if (!spec.info) {
    violations.push(`Missing 'info' section in ${filePath}`);
  } else {
    if (!spec.info.title) {
      violations.push(`Missing 'info.title' in ${filePath}`);
    }
    if (!spec.info.version) {
      violations.push(`Missing 'info.version' in ${filePath}`);
    }
  }
  
  if (!spec.paths) {
    violations.push(`Missing 'paths' section in ${filePath}`);
  } else {
    // Validate each path
    for (const [path, pathItem] of Object.entries(spec.paths)) {
      if (!path.startsWith('/')) {
        violations.push(`Invalid path '${path}' in ${filePath} (must start with '/')`);
      }
      
      // Validate operations
      const methods = ['get', 'post', 'put', 'delete', 'patch', 'options', 'head'];
      for (const method of methods) {
        if (pathItem[method]) {
          const operation = pathItem[method];
          
          // Check for operationId
          if (!operation.operationId) {
            violations.push(`Missing operationId for ${method.toUpperCase()} ${path} in ${filePath}`);
          }
          
          // Check for responses
          if (!operation.responses) {
            violations.push(`Missing responses for ${method.toUpperCase()} ${path} in ${filePath}`);
          } else if (!operation.responses['200'] && !operation.responses['201'] && !operation.responses['204']) {
            violations.push(`Missing success response (2xx) for ${method.toUpperCase()} ${path} in ${filePath}`);
          }
        }
      }
    }
  }
  
  // Validate components/schemas if present
  if (spec.components && spec.components.schemas) {
    for (const [schemaName, schema] of Object.entries(spec.components.schemas)) {
      if (!schema.type) {
        violations.push(`Schema '${schemaName}' missing 'type' in ${filePath}`);
      }
    }
  }
  
  return violations;
}

function validatePHROpenAPI() {
  const phrOpenAPIPath = resolve(repoRoot, 'products/phr/docs/openapi.yaml');
  const violations = [];
  
  try {
    const content = readFileSync(phrOpenAPIPath, 'utf8');
    const spec = parseOpenAPI(content);
    const schemaViolations = validateOpenAPISpec(spec, phrOpenAPIPath);
    violations.push(...schemaViolations);
    
    // PHR-specific validations
    if (spec.paths) {
      // Check for required healthcare endpoints
      const requiredEndpoints = [
        '/api/v1/patients',
        '/api/v1/consent',
        '/api/v1/fhir',
      ];
      
      for (const endpoint of requiredEndpoints) {
        const found = Object.keys(spec.paths).some(path => path.startsWith(endpoint));
        if (!found) {
          violations.push(`Missing required healthcare endpoint: ${endpoint}`);
        }
      }
      
      // Check for PII classification in security schemes
      if (!spec.components || !spec.components.securitySchemes) {
        violations.push('Missing security schemes in OpenAPI spec');
      }
    }
  } catch (error) {
    violations.push(`Failed to read PHR OpenAPI spec: ${error.message}`);
  }
  
  return violations;
}

export function checkOpenAPISchemaValidation({ productId = 'phr' } = {}) {
  const violations = [];
  
  if (productId === 'phr') {
    violations.push(...validatePHROpenAPI());
  }
  
  return {
    status: violations.length === 0 ? 'passed' : 'failed',
    violations,
  };
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const productId = process.argv[2] || 'phr';
  const result = checkOpenAPISchemaValidation({ productId });
  
  console.log(`\n🔍 OpenAPI Schema Validation for ${productId}`);
  console.log(`   Status: ${result.status}\n`);
  
  if (result.violations.length > 0) {
    console.error(`❌ ${result.violations.length} violation(s) found:\n`);
    result.violations.forEach(v => console.error(`   - ${v}`));
    process.exit(1);
  }
  
  console.log(`✅ All schema validations passed!`);
  process.exit(0);
}
