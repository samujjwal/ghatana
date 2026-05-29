#!/usr/bin/env node

/**
 * PHR Route Contract Validator
 * 
 * Validates the PHR route contract JSON against schema and ensures:
 * - JSON schema compliance
 * - Stable routes have required apiEndpoint, policyId, testId
 * - Route paths exist in routeElements
 * - All imported pages are reachable from route contract
 * 
 * Usage:
 *   node scripts/check-phr-route-contract.mjs
 *   node scripts/check-phr-route-contract.mjs --check-backend-mounts
 *   node scripts/check-phr-route-contract.mjs --check-hidden
 */

import { readFileSync, existsSync } from 'fs';
import { join, dirname } from 'path';
import { fileURLToPath } from 'url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const REPO_ROOT = join(__dirname, '..');

// ANSI color codes for output
const colors = {
  reset: '\x1b[0m',
  red: '\x1b[31m',
  green: '\x1b[32m',
  yellow: '\x1b[33m',
  blue: '\x1b[34m',
};

function log(message, color = 'reset') {
  console.log(`${colors[color]}${message}${colors.reset}`);
}

function error(message) {
  log(`❌ ${message}`, 'red');
}

function success(message) {
  log(`✓ ${message}`, 'green');
}

function warn(message) {
  log(`⚠ ${message}`, 'yellow');
}

function info(message) {
  log(`ℹ ${message}`, 'blue');
}

// Load and parse JSON file
function loadJson(filePath) {
  try {
    const content = readFileSync(filePath, 'utf-8');
    return JSON.parse(content);
  } catch (err) {
    error(`Failed to load or parse ${filePath}: ${err.message}`);
    process.exit(1);
  }
}

// Simple JSON schema validator (subset of JSON Schema Draft 7)
function validateSchema(data, schema, path = '') {
  const errors = [];

  function validate(value, schemaNode, currentPath) {
    // Type validation
    if (schemaNode.type) {
      const typeErrors = validateType(value, schemaNode.type, currentPath);
      errors.push(...typeErrors);
    }

    // Enum validation
    if (schemaNode.enum) {
      if (!schemaNode.enum.includes(value)) {
        errors.push(`${currentPath}: value "${value}" not in enum [${schemaNode.enum.join(', ')}]`);
      }
    }

    // Pattern validation
    if (schemaNode.pattern && typeof value === 'string') {
      const regex = new RegExp(schemaNode.pattern);
      if (!regex.test(value)) {
        errors.push(`${currentPath}: value "${value}" does not match pattern ${schemaNode.pattern}`);
      }
    }

    // Const validation
    if (schemaNode.const !== undefined && value !== schemaNode.const) {
      errors.push(`${currentPath}: value "${value}" must be "${schemaNode.const}"`);
    }

    // Required properties for objects
    if (schemaNode.type === 'object' && schemaNode.required && typeof value === 'object' && value !== null) {
      for (const required of schemaNode.required) {
        if (!(required in value)) {
          errors.push(`${currentPath}: missing required property "${required}"`);
        }
      }
    }

    // Array item validation
    if (schemaNode.type === 'array' && schemaNode.items && Array.isArray(value)) {
      value.forEach((item, index) => {
        validate(item, schemaNode.items, `${currentPath}[${index}]`);
      });
    }

    // Object property validation
    if (schemaNode.type === 'object' && schemaNode.properties && typeof value === 'object' && value !== null) {
      for (const [prop, propSchema] of Object.entries(schemaNode.properties)) {
        if (prop in value) {
          validate(value[prop], propSchema, `${currentPath}.${prop}`);
        }
      }

      // Additional properties check
      if (schemaNode.additionalProperties === false) {
        const allowedProps = new Set(Object.keys(schemaNode.properties));
        for (const prop of Object.keys(value)) {
          if (!allowedProps.has(prop)) {
            errors.push(`${currentPath}: additional property "${prop}" not allowed`);
          }
        }
      }
    }

    // allOf validation
    if (schemaNode.allOf) {
      for (const subSchema of schemaNode.allOf) {
        // Handle conditional validation
        if (subSchema.if && subSchema.then) {
          const ifCondition = subSchema.if.properties?.stability?.const;
          if (ifCondition && value.stability === ifCondition) {
            for (const required of subSchema.then.required || []) {
              if (!(required in value)) {
                errors.push(`${currentPath}: stability "${ifCondition}" requires property "${required}"`);
              }
            }
          }
        }
      }
    }
  }

  function validateType(value, type, currentPath) {
    const typeErrors = [];
    const actualType = Array.isArray(value) ? 'array' : value === null ? 'null' : typeof value;

    if (type === 'array' && actualType !== 'array') {
      typeErrors.push(`${currentPath}: expected array, got ${actualType}`);
    } else if (type === 'object' && actualType !== 'object') {
      typeErrors.push(`${currentPath}: expected object, got ${actualType}`);
    } else if (type === 'string' && actualType !== 'string') {
      typeErrors.push(`${currentPath}: expected string, got ${actualType}`);
    } else if (type === 'number' && actualType !== 'number') {
      typeErrors.push(`${currentPath}: expected number, got ${actualType}`);
    } else if (type === 'integer' && actualType !== 'number') {
      typeErrors.push(`${currentPath}: expected integer, got ${actualType}`);
    } else if (type === 'boolean' && actualType !== 'boolean') {
      typeErrors.push(`${currentPath}: expected boolean, got ${actualType}`);
    }

    return typeErrors;
  }

  validate(data, schema, path);
  return errors;
}

// Main validation function
function validateRouteContract(contractPath, options = {}) {
  info(`Validating PHR route contract: ${contractPath}`);

  const contract = loadJson(contractPath);
  const schemaPath = join(dirname(contractPath), 'phr-route-contract.schema.json');
  
  if (!existsSync(schemaPath)) {
    error(`Schema file not found: ${schemaPath}`);
    process.exit(1);
  }

  const schema = loadJson(schemaPath);
  let hasErrors = false;

  // 1. Schema validation
  info('Validating JSON schema...');
  const schemaErrors = validateSchema(contract, schema);
  if (schemaErrors.length > 0) {
    hasErrors = true;
    error('Schema validation failed:');
    schemaErrors.forEach(err => error(`  ${err}`));
  } else {
    success('JSON schema validation passed');
  }

  // 2. Stable route requirements
  info('Validating stable route requirements...');
  const stableRoutes = contract.routes.filter(r => r.stability === 'stable');
  const missingRequiredFields = [];

  for (const route of stableRoutes) {
    if (!route.apiEndpoint) {
      missingRequiredFields.push(`${route.path}: missing apiEndpoint`);
    }
    if (!route.policyId) {
      missingRequiredFields.push(`${route.path}: missing policyId`);
    }
    if (!route.testId) {
      missingRequiredFields.push(`${route.path}: missing testId`);
    }
  }

  if (missingRequiredFields.length > 0) {
    hasErrors = true;
    error('Stable routes missing required fields:');
    missingRequiredFields.forEach(err => error(`  ${err}`));
  } else {
    success(`All ${stableRoutes.length} stable routes have required fields`);
  }

  // 3. Route path to routeElements validation
  if (!options.skipRouteElements) {
    info('Validating route paths against routeElements...');
    const routeElementsPath = join(REPO_ROOT, 'products/phr/apps/web/src/phrRouteElements.tsx');
    
    if (!existsSync(routeElementsPath)) {
      warn(`RouteElements file not found: ${routeElementsPath}`);
    } else {
      const routeElementsContent = readFileSync(routeElementsPath, 'utf-8');
      const routeElements = contract.routes.map(r => r.path);
      const missingInElements = [];

      for (const routePath of routeElements) {
        // Check if the path exists in the routeElements mapping
        const escapedPath = routePath.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
        const regex = new RegExp(`['"]${escapedPath}['"]\\s*:`, 'm');
        if (!regex.test(routeElementsContent)) {
          missingInElements.push(routePath);
        }
      }

      if (missingInElements.length > 0) {
        hasErrors = true;
        error('Route paths missing from routeElements:');
        missingInElements.forEach(path => error(`  ${path}`));
      } else {
        success('All route paths exist in routeElements');
      }
    }
  }

  // 4. Page import validation
  if (!options.skipPageImports) {
    info('Validating page imports against route contract...');
    const routeElementsPath = join(REPO_ROOT, 'products/phr/apps/web/src/phrRouteElements.tsx');
    
    if (!existsSync(routeElementsPath)) {
      warn(`RouteElements file not found: ${routeElementsPath}`);
    } else {
      const routeElementsContent = readFileSync(routeElementsPath, 'utf-8');
      
      // Extract imported page components
      const importRegex = /import\s+{\s*([^}]+)\s*}\s+from\s+['"]\.\/pages\/[^'"]+['"]/g;
      const imports = [];
      let match;
      
      while ((match = importRegex.exec(routeElementsContent)) !== null) {
        const components = match[1].split(',').map(c => c.trim());
        imports.push(...components);
      }

      // Extract route paths from contract
      const contractPaths = new Set(contract.routes.map(r => r.path));
      
      // Build a mapping of component names to their actual route paths from routeElements
      const routeMappingRegex = /['"]([^'"]+)['"]\s*:\s*<(\w+)\s*\/>/g;
      const componentToPath = new Map();
      
      while ((match = routeMappingRegex.exec(routeElementsContent)) !== null) {
        const path = match[1];
        const component = match[2];
        componentToPath.set(component, path);
      }
      
      // Check if each imported page has a corresponding route
      const orphanPages = [];
      for (const imp of imports) {
        // Skip system pages
        if (['ForbiddenPage', 'NotFoundPage'].includes(imp)) {
          continue;
        }
        
        const mappedPath = componentToPath.get(imp);
        if (!mappedPath) {
          orphanPages.push(`${imp} (no mapping found in routeElements)`);
        } else if (!contractPaths.has(mappedPath)) {
          orphanPages.push(`${imp} (mapped to ${mappedPath} which is not in contract)`);
        }
      }

      if (orphanPages.length > 0) {
        hasErrors = true;
        error('Imported pages without corresponding route contract entries:');
        orphanPages.forEach(page => error(`  ${page}`));
      } else {
        success('All imported pages have corresponding route contract entries');
      }
    }
  }

  // 5. Hidden route validation
  if (options.checkHidden) {
    info('Validating hidden routes...');
    const hiddenRoutes = contract.routes.filter(r => r.stability === 'hidden');
    
    for (const route of hiddenRoutes) {
      if (!route.visibilityReason) {
        warn(`${route.path}: hidden route missing visibilityReason`);
      }
    }
    
    success(`Checked ${hiddenRoutes.length} hidden routes`);
  }

  // 6. Backend mount validation
  if (options.checkBackendMounts) {
    info('Validating backend route mounts...');
    const httpServerPath = join(REPO_ROOT, 'products/phr/src/main/java/com/ghatana/phr/api/PhrHttpServer.java');
    
    if (!existsSync(httpServerPath)) {
      warn(`PhrHttpServer.java not found: ${httpServerPath}`);
    } else {
      const httpServerContent = readFileSync(httpServerPath, 'utf-8');
      const missingMounts = [];

      for (const route of stableRoutes) {
        if (route.apiEndpoint) {
          // Extract the base path from apiEndpoint
          const basePath = route.apiEndpoint.split('/').slice(0, 3).join('/');
          const escapedPath = basePath.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
          const regex = new RegExp(`['"]${escapedPath}`, 'm');
          
          if (!regex.test(httpServerContent)) {
            missingMounts.push(`${route.path}: ${route.apiEndpoint}`);
          }
        }
      }

      if (missingMounts.length > 0) {
        hasErrors = true;
        error('Stable route API endpoints not mounted in PhrHttpServer:');
        missingMounts.forEach(mount => error(`  ${mount}`));
      } else {
        success('All stable route API endpoints are mounted in PhrHttpServer');
      }
    }
  }

  if (hasErrors) {
    error('Route contract validation failed');
    process.exit(1);
  }

  success('Route contract validation passed');
}

// Parse command line arguments
const args = process.argv.slice(2);
const options = {
  checkBackendMounts: args.includes('--check-backend-mounts'),
  checkHidden: args.includes('--check-hidden'),
  skipRouteElements: args.includes('--skip-route-elements'),
  skipPageImports: args.includes('--skip-page-imports'),
};

const contractPath = join(REPO_ROOT, 'products/phr/config/phr-route-contract.json');

if (!existsSync(contractPath)) {
  error(`Route contract not found: ${contractPath}`);
  process.exit(1);
}

validateRouteContract(contractPath, options);
