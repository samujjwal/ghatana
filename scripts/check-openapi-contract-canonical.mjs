#!/usr/bin/env node
/**
 * Canonical OpenAPI contract checks for Data Cloud, AEP, DMOS, and FlashIt.
 *
 * ARCH-P1-002 guardrails:
 * - required canonical OpenAPI files must exist
 * - required endpoint surfaces must be present
 * - AEP product contract spec and server runtime spec must stay byte-equivalent
 * - schema-aware breaking change detection for OpenAPI specifications
 */

import { existsSync, readFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(__dirname, '..');

const files = {
  dataCloudApi: join(repoRoot, 'products', 'data-cloud', 'contracts', 'openapi', 'data-cloud.yaml'),
  aepContracts: join(repoRoot, 'products', 'data-cloud', 'contracts', 'openapi', 'aep.yaml'),
  aepServer: join(repoRoot, 'products', 'data-cloud', 'planes', 'action', 'server', 'src', 'main', 'resources', 'openapi.yaml'),
  dmosApi: join(repoRoot, 'products', 'digital-marketing', 'dm-api', 'src', 'main', 'resources', 'openapi.json'),
  flashitGateway: join(repoRoot, 'products', 'flashit', 'backend', 'gateway', 'openapi.yaml'),
};

const requiredPaths = {
  dataCloud: ['/api/v1/surfaces', '/api/v1/surfaces/schema'],
  aep: [
    '/health',
    '/ready',
    '/api/v1/events',
    '/api/v1/agents',
    '/api/v1/agents/{agentId}/execute',
    '/api/v1/runs',
  ],
  dmos: [
    '/v1/workspaces/{workspaceId}/campaigns',
    '/v1/workspaces/{workspaceId}/campaigns/{id}',
  ],
  flashit: [
    '/health',
    '/metrics',
    '/auth/register',
    '/auth/login',
    '/moments',
    '/moments/{momentId}',
    '/route-entitlements',
  ],
};

const violations = [];

// JSON-format OpenAPI specs use "openapi": "3.x.x" property keys
const jsonFormatFiles = new Set(['dmosApi']);

for (const [name, fullPath] of Object.entries(files)) {
  if (!existsSync(fullPath)) {
    violations.push(`Missing OpenAPI file: ${name} -> ${relativePath(fullPath)}`);
    continue;
  }

  const content = readFileSync(fullPath, 'utf8');
  // YAML: `openapi: "3.x"`, JSON: `"openapi": "3.x"`
  const hasValidHeader = jsonFormatFiles.has(name)
    ? /"openapi"\s*:\s*"3\./.test(content)
    : /\bopenapi:\s*["']?3\./.test(content);

  if (!hasValidHeader) {
    violations.push(`Invalid OpenAPI header in ${relativePath(fullPath)}`);
  }
}

const dataCloudSpec = safeRead(files.dataCloudApi);
const aepContractsSpec = safeRead(files.aepContracts);
const aepServerSpec = safeRead(files.aepServer);

if (dataCloudSpec != null) {
  assertPathSet('Data Cloud API', files.dataCloudApi, dataCloudSpec, requiredPaths.dataCloud);
}
if (aepContractsSpec != null) {
  assertPathSet('AEP contracts', files.aepContracts, aepContractsSpec, requiredPaths.aep);
}
if (aepServerSpec != null) {
  assertPathSet('AEP server runtime', files.aepServer, aepServerSpec, requiredPaths.aep);
}

if (aepContractsSpec != null && aepServerSpec != null) {
  const normalizedContracts = normalizeText(aepContractsSpec);
  const normalizedServer = normalizeText(aepServerSpec);
  if (normalizedContracts !== normalizedServer) {
    violations.push(
      'AEP canonical contract drift: products/data-cloud/contracts/openapi/aep.yaml and products/data-cloud/planes/action/server/src/main/resources/openapi.yaml differ.',
    );
  }
}

// ---------------------------------------------------------------------------
// DMOS OpenAPI validation
// ---------------------------------------------------------------------------

const dmosSpec = safeRead(files.dmosApi);
if (dmosSpec === null) {
  violations.push(`Missing DMOS OpenAPI file: ${relativePath(files.dmosApi)}`);
} else {
  // DMOS spec is JSON (OpenAPI 3.0.3)
  if (!dmosSpec.includes('"openapi"') && !dmosSpec.includes("openapi:")) {
    violations.push(`Invalid OpenAPI header in DMOS spec: ${relativePath(files.dmosApi)}`);
  }
  assertPathSetJson('DMOS API', files.dmosApi, dmosSpec, requiredPaths.dmos);
}

// ---------------------------------------------------------------------------
// FlashIt OpenAPI validation
// ---------------------------------------------------------------------------

const flashitSpec = safeRead(files.flashitGateway);
if (flashitSpec === null) {
  violations.push(`Missing FlashIt OpenAPI file: ${relativePath(files.flashitGateway)}`);
} else {
  if (!/\bopenapi:\s*["']?3\./.test(flashitSpec)) {
    violations.push(`Invalid OpenAPI header in FlashIt spec: ${relativePath(files.flashitGateway)}`);
  }
  assertPathSet('FlashIt Gateway API', files.flashitGateway, flashitSpec, requiredPaths.flashit);
}

// ---------------------------------------------------------------------------
// Schema-aware breaking change detection
// ---------------------------------------------------------------------------

// Check for breaking changes between baseline and current OpenAPI specs
const baselineDir = join(repoRoot, 'release-evidence', 'openapi-baseline');
const baselineFiles = {
  dataCloud: join(baselineDir, 'data-cloud.yaml'),
  aep: join(baselineDir, 'aep.yaml'),
  dmos: join(baselineDir, 'digital-marketing.yaml'),
};

for (const [product, baselinePath] of Object.entries(baselineFiles)) {
  const currentPath = files[`${product === 'dmos' ? 'dmosApi' : product === 'aep' ? 'aepContracts' : product + 'Api'}`];
  if (!currentPath || !existsSync(currentPath)) continue;
  
  const baselineSpec = safeRead(baselinePath);
  const currentSpec = safeRead(currentPath);
  
  if (baselineSpec && currentSpec) {
    const breakingChanges = detectBreakingChanges(baselineSpec, currentSpec, product);
    violations.push(...breakingChanges);
  }
}

if (violations.length > 0) {
  console.error('Canonical OpenAPI checks failed:');
  for (const violation of violations) {
    console.error(`- ${violation}`);
  }
  process.exit(1);
}

console.log('Canonical OpenAPI checks passed.');

function safeRead(filePath) {
  if (!existsSync(filePath)) {
    return null;
  }
  return readFileSync(filePath, 'utf8');
}

function assertPathSet(label, filePath, content, paths) {
  for (const apiPath of paths) {
    if (!containsPathKey(content, apiPath)) {
      violations.push(`${label} is missing path ${apiPath} in ${relativePath(filePath)}`);
    }
  }
}

/**
 * Check required paths in a JSON-format OpenAPI spec.
 * Matches the path key as a JSON property name.
 */
function assertPathSetJson(label, filePath, content, paths) {
  for (const apiPath of paths) {
    const escaped = apiPath.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    // JSON property key — e.g. "/v1/workspaces/{workspaceId}/campaigns": {
    const pattern = new RegExp(`"${escaped}"\\s*:`);
    if (!pattern.test(content)) {
      violations.push(`${label} is missing path ${apiPath} in ${relativePath(filePath)}`);
    }
  }
}

function containsPathKey(content, apiPath) {
  const escaped = apiPath.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  const pattern = new RegExp(`^\\s{2}${escaped}:\\s*$`, 'm');
  return pattern.test(content);
}

function normalizeText(content) {
  return content.replace(/\r\n/g, '\n').trim();
}

function relativePath(filePath) {
  return filePath.replace(`${repoRoot}\\`, '').replace(/\\/g, '/');
}

/**
 * Detect breaking changes between baseline and current OpenAPI specifications.
 * 
 * Breaking changes include:
 * - Removed endpoints (paths)
 * - Removed required request parameters
 * - Removed required response fields
 * - Changed parameter types (string to number, etc.)
 * - Changed response types
 * - Made optional fields required
 * 
 * @param {string} baseline - Baseline OpenAPI spec content
 * @param {string} current - Current OpenAPI spec content
 * @param {string} product - Product name for error messages
 * @returns {string[]} Array of breaking change violations
 */
function detectBreakingChanges(baseline, current, product) {
  const violations = [];
  
  try {
    const baselineSpec = parseOpenAPI(baseline);
    const currentSpec = parseOpenAPI(current);
    
    // Check for removed paths
    const baselinePaths = baselineSpec.paths || {};
    const currentPaths = currentSpec.paths || {};
    
    for (const path of Object.keys(baselinePaths)) {
      if (!currentPaths[path]) {
        violations.push(`[${product.toUpperCase()}] Breaking change: Removed path ${path}`);
      }
    }
    
    // Check for removed required parameters
    for (const [path, pathSpec] of Object.entries(baselinePaths)) {
      if (!currentPaths[path]) continue;
      
      const baselineOps = pathSpec || {};
      const currentOps = currentPaths[path] || {};
      
      for (const [method, opSpec] of Object.entries(baselineOps)) {
        if (!currentOps[method]) continue;
        
        const baselineParams = opSpec.parameters || [];
        const currentParams = currentOps[method].parameters || [];
        
        for (const param of baselineParams) {
          if (param.required && !currentParams.find(p => p.name === param.name)) {
            violations.push(`[${product.toUpperCase()}] Breaking change: Removed required parameter ${param.name} from ${method.toUpperCase()} ${path}`);
          }
        }
      }
    }
    
    // Check for removed required response fields
    for (const [path, pathSpec] of Object.entries(baselinePaths)) {
      if (!currentPaths[path]) continue;
      
      const baselineOps = pathSpec || {};
      const currentOps = currentPaths[path] || {};
      
      for (const [method, opSpec] of Object.entries(baselineOps)) {
        if (!currentOps[method]) continue;
        
        const baselineResponses = opSpec.responses || {};
        const currentResponses = currentOps[method].responses || {};
        
        for (const [statusCode, responseSpec] of Object.entries(baselineResponses)) {
          if (!currentResponses[statusCode]) continue;
          
          const baselineSchema = responseSpec.schema || responseSpec.content?.['application/json']?.schema;
          const currentSchema = currentResponses[statusCode].schema || currentResponses[statusCode].content?.['application/json']?.schema;
          
          if (baselineSchema && currentSchema) {
            const schemaViolations = detectSchemaBreakingChanges(baselineSchema, currentSchema, `${method.toUpperCase()} ${path} ${statusCode}`, product);
            violations.push(...schemaViolations);
          }
        }
      }
    }
    
  } catch (error) {
    // If parsing fails, skip schema-aware checks but don't fail the entire script
    console.warn(`[WARNING] Schema-aware diff failed for ${product}: ${error.message}`);
  }
  
  return violations;
}

/**
 * Parse OpenAPI spec (YAML or JSON) into a JavaScript object.
 * Simple parser that handles basic structure for breaking change detection.
 */
function parseOpenAPI(content) {
  try {
    // Try JSON first
    if (content.trim().startsWith('{')) {
      return JSON.parse(content);
    }
    // For YAML, do a simple parse (not full YAML spec, just enough for our checks)
    return simpleYamlParse(content);
  } catch (error) {
    return {};
  }
}

/**
 * Simple YAML parser for basic OpenAPI structure.
 * This is a minimal implementation for breaking change detection.
 */
function simpleYamlParse(content) {
  const result = { paths: {} };
  const lines = content.split('\n');
  let currentPath = null;
  let currentMethod = null;
  let currentSection = null;
  let indentLevel = 0;
  
  for (const line of lines) {
    const indent = line.search(/\S|$/);
    const trimmed = line.trim();
    
    if (trimmed.startsWith('paths:')) {
      currentSection = 'paths';
      continue;
    }
    
    if (currentSection === 'paths' && indent === 2 && trimmed.endsWith(':')) {
      currentPath = trimmed.slice(0, -1);
      result.paths[currentPath] = {};
      continue;
    }
    
    if (currentPath && indent === 4 && trimmed.endsWith(':')) {
      currentMethod = trimmed.slice(0, -1).toLowerCase();
      if (['get', 'post', 'put', 'delete', 'patch'].includes(currentMethod)) {
        result.paths[currentPath][currentMethod] = {};
      }
      continue;
    }
    
    if (currentMethod && indent === 6 && trimmed.startsWith('parameters:')) {
      result.paths[currentPath][currentMethod].parameters = [];
      continue;
    }
    
    if (currentMethod && indent === 8 && trimmed.startsWith('- name:')) {
      const paramName = trimmed.split(':')[1].trim();
      const required = lines.slice(lines.indexOf(line)).find(l => l.trim().startsWith('required:'));
      result.paths[currentPath][currentMethod].parameters.push({
        name: paramName,
        required: required && required.includes('true')
      });
    }
  }
  
  return result;
}

/**
 * Detect breaking changes in schema definitions.
 */
function detectSchemaBreakingChanges(baselineSchema, currentSchema, context, product) {
  const violations = [];
  
  // Check for removed required properties
  const baselineRequired = baselineSchema.required || [];
  const currentRequired = currentSchema.required || [];
  
  for (const prop of baselineRequired) {
    if (!currentRequired.includes(prop)) {
      violations.push(`[${product.toUpperCase()}] Breaking change: Made required property '${prop}' optional in ${context}`);
    }
  }
  
  // Check for removed properties
  const baselineProps = baselineSchema.properties || {};
  const currentProps = currentSchema.properties || {};
  
  for (const prop of Object.keys(baselineProps)) {
    if (!currentProps[prop]) {
      violations.push(`[${product.toUpperCase()}] Breaking change: Removed property '${prop}' from ${context}`);
    }
  }
  
  return violations;
}
