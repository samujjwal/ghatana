#!/usr/bin/env node

import { existsSync, mkdirSync, readFileSync, renameSync, rmSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import YAML from 'yaml';

const repoRoot = process.cwd();
const evidenceDir = path.join(repoRoot, '.kernel/evidence');
const evidencePath = path.join(evidenceDir, 'openapi-breaking-changes.json');
const waiversPath = path.join(repoRoot, 'config/openapi-breaking-change-waivers.json');
const RETRYABLE_WRITE_CODES = new Set(['UNKNOWN', 'EACCES', 'EBUSY', 'EPERM']);

function writeEvidenceAtomicWithRetry(filePath, content, maxAttempts = 5) {
  let lastError;
  for (let attempt = 1; attempt <= maxAttempts; attempt += 1) {
    const tempPath = `${filePath}.tmp-${process.pid}-${Date.now()}-${attempt}`;
    try {
      writeFileSync(tempPath, content, 'utf8');
      renameSync(tempPath, filePath);
      return;
    } catch (error) {
      lastError = error;
      try {
        rmSync(tempPath, { force: true });
      } catch {
        // Best-effort cleanup.
      }

      const retryable =
        error
        && typeof error === 'object'
        && 'code' in error
        && RETRYABLE_WRITE_CODES.has(error.code);
      if (!retryable || attempt === maxAttempts) {
        throw error;
      }
    }
  }

  if (lastError) {
    throw lastError;
  }
}

// Breaking change categories
const BREAKING_CHANGE_TYPES = {
  OPERATION_REMOVED: 'operation_removed',
  REQUIRED_FIELD_ADDED: 'required_field_added',
  RESPONSE_FIELD_REMOVED: 'response_field_removed',
  RESPONSE_TYPE_CHANGED: 'response_type_changed',
  ENUM_VALUE_REMOVED: 'enum_value_removed',
  STATUS_CODE_REMOVED: 'status_code_removed',
  SECURITY_REQUIREMENT_CHANGED: 'security_requirement_changed',
  PATH_PARAMETER_RENAMED: 'path_parameter_renamed',
  QUERY_PARAMETER_REQUIRED: 'query_parameter_required',
  CONTENT_TYPE_REMOVED: 'content_type_removed',
  NULLABILITY_CHANGED: 'nullability_changed',
  SCHEMA_REF_CHANGED: 'schema_ref_changed',
};

const specs = [
  {
    id: 'data-cloud',
    current: 'products/data-cloud/contracts/openapi/data-cloud.yaml',
    baseline: 'release-evidence/openapi-baseline/data-cloud.yaml',
  },
  {
    id: 'aep',
    current: 'products/data-cloud/contracts/openapi/aep.yaml',
    baseline: 'release-evidence/openapi-baseline/aep.yaml',
  },
  {
    id: 'digital-marketing',
    current: 'products/digital-marketing/dm-api/src/main/resources/openapi.json',
    baseline: 'release-evidence/openapi-baseline/dmos-openapi.json',
  },
  {
    id: 'flashit',
    current: 'products/flashit/backend/gateway/openapi.yaml',
    baseline: 'release-evidence/openapi-baseline/flashit-openapi.yaml',
  },
];

export function extractPathMethodKeys(source) {
  const normalized = source.replace(/\r\n/g, '\n');
  const keys = new Set();
  const methodNames = new Set(['get', 'post', 'put', 'patch', 'delete', 'head', 'options']);

  const trimmed = normalized.trimStart();
  if (trimmed.startsWith('{')) {
    try {
      const parsed = JSON.parse(normalized);
      const paths = parsed?.paths;
      if (paths && typeof paths === 'object') {
        for (const [routePath, operations] of Object.entries(paths)) {
          if (!operations || typeof operations !== 'object') {
            continue;
          }
          for (const [methodName] of Object.entries(operations)) {
            const loweredMethod = String(methodName).toLowerCase();
            if (methodNames.has(loweredMethod)) {
              keys.add(`${loweredMethod.toUpperCase()} ${routePath}`);
            }
          }
        }
      }
      return keys;
    } catch {
      // Fall through to YAML scanning if JSON parse fails.
    }
  }

  let inPathsBlock = false;
  let currentPath = null;

  for (const line of normalized.split('\n')) {
    if (!inPathsBlock) {
      if (/^paths:\s*$/.test(line.trim())) {
        inPathsBlock = true;
      }
      continue;
    }

    if (/^\S/.test(line) && line.trim() !== 'paths:') {
      inPathsBlock = false;
      currentPath = null;
      continue;
    }

    const pathMatch = line.match(/^\s{2}(\/[^\s]+):\s*$/);
    if (pathMatch) {
      currentPath = pathMatch[1];
      continue;
    }

    if (!currentPath) {
      continue;
    }

    const methodMatch = line.match(/^\s{4}(get|post|put|patch|delete|head|options):\s*$/i);
    if (methodMatch) {
      keys.add(`${methodMatch[1].toUpperCase()} ${currentPath}`);
    }
  }

  return keys;
}

/**
 * Parses OpenAPI spec (YAML or JSON) into a structured object
 */
function parseOpenApiSpec(source) {
  const normalized = source.replace(/\r\n/g, '\n');
  const trimmed = normalized.trimStart();
  
  if (trimmed.startsWith('{')) {
    try {
      return JSON.parse(normalized);
    } catch (error) {
      throw new Error(`Failed to parse OpenAPI JSON: ${error.message}`);
    }
  }
  
  try {
    return YAML.parse(normalized) ?? {};
  } catch {
    return parseYaml(normalized);
  }
}

/**
 * Minimal YAML parser for OpenAPI specs
 * Handles nested objects, arrays, and basic types
 */
function parseYaml(yaml) {
  const lines = yaml.split('\n');
  const result = {};
  const stack = [result];
  const indentStack = [0];
  
  for (const line of lines) {
    const trimmed = line.trimEnd();
    if (!trimmed || trimmed.startsWith('#')) continue;
    
    const indent = line.search(/\S|$/);
    const content = trimmed;
    
    // Pop stack to correct indentation level
    while (indentStack.length > 1 && indent < indentStack[indentStack.length - 1]) {
      stack.pop();
      indentStack.pop();
    }
    
    const current = stack[stack.length - 1];
    
    // Handle key-value pairs
    const colonIndex = content.indexOf(':');
    if (colonIndex > 0) {
      const key = content.substring(0, colonIndex).trim();
      const value = content.substring(colonIndex + 1).trim();
      
      if (value === '' || value === '|' || value === '>') {
        // Multi-line or nested object
        current[key] = {};
        stack.push(current[key]);
        indentStack.push(indent + 2);
      } else if (value === '[]' || value === 'null' || value === '{}') {
        // Empty structures
        if (value === '[]') current[key] = [];
        else if (value === '{}') current[key] = {};
        else current[key] = null;
      } else if (value.startsWith("'") || value.startsWith('"')) {
        // Quoted string
        current[key] = value.slice(1, -1);
      } else if (value === 'true' || value === 'false') {
        current[key] = value === 'true';
      } else if (!isNaN(value)) {
        current[key] = Number(value);
      } else {
        // Try to parse as unquoted string
        current[key] = value;
      }
    } else if (content.startsWith('- ')) {
      // Array item
      const item = content.substring(2).trim();
      if (!Array.isArray(current)) {
        // Convert to array if needed
        const keys = Object.keys(current);
        if (keys.length === 0) {
          Object.setPrototypeOf(current, Array.prototype);
          current.length = 0;
        }
      }
      if (item.startsWith("'") || item.startsWith('"')) {
        current.push(item.slice(1, -1));
      } else if (item === 'true' || item === 'false') {
        current.push(item === 'true');
      } else if (!isNaN(item)) {
        current.push(Number(item));
      } else {
        current.push(item);
      }
    }
  }
  
  return result;
}

/**
 * Detects schema-level breaking changes between baseline and current specs
 */
export function detectSchemaBreakingChanges(baselineSpec, currentSpec, specId) {
  const breakingChanges = [];
  
  const baselinePaths = baselineSpec?.paths || {};
  const currentPaths = currentSpec?.paths || {};
  
  for (const [path, baselineOperations] of Object.entries(baselinePaths)) {
    const currentOperations = currentPaths[path];
    
    if (!currentOperations) {
      // Entire path removed - already caught by operation removal check
      continue;
    }
    
    for (const [method, baselineOp] of Object.entries(baselineOperations)) {
      const currentOp = currentOperations[method];
      
      if (!currentOp) {
        // Operation removed - already caught
        continue;
      }
      
      // Check request body schema changes
      const baselineReqBody = baselineOp.requestBody;
      const currentReqBody = currentOp.requestBody;
      
      if (baselineReqBody && currentReqBody) {
        const reqBodyChanges = detectRequestBodySchemaChanges(
          baselineReqBody,
          currentReqBody,
          `${method.toUpperCase()} ${path}`,
          'request'
        );
        breakingChanges.push(...reqBodyChanges);
      }
      
      // Check response schema changes
      const baselineResponses = baselineOp.responses || {};
      const currentResponses = currentOp.responses || {};
      
      for (const [statusCode, baselineResponse] of Object.entries(baselineResponses)) {
        const currentResponse = currentResponses[statusCode];
        
        if (!currentResponse) {
          breakingChanges.push({
            type: BREAKING_CHANGE_TYPES.STATUS_CODE_REMOVED,
            operation: `${method.toUpperCase()} ${path}`,
            detail: `Response status code ${statusCode} removed`,
            specId,
          });
          continue;
        }
        
        const responseChanges = detectResponseSchemaChanges(
          baselineResponse,
          currentResponse,
          `${method.toUpperCase()} ${path}`,
          statusCode
        );
        breakingChanges.push(...responseChanges);
      }
      
      // Check parameter changes
      const baselineParams = asArray(baselineOp.parameters);
      const currentParams = asArray(currentOp.parameters);
      
      const paramChanges = detectParameterChanges(
        baselineParams,
        currentParams,
        `${method.toUpperCase()} ${path}`,
        specId
      );
      breakingChanges.push(...paramChanges);
      
      // Check security requirement changes
      const baselineSecurity = baselineOp.security || [];
      const currentSecurity = currentOp.security || [];
      
      const securityChanges = detectSecurityChanges(
        baselineSecurity,
        currentSecurity,
        `${method.toUpperCase()} ${path}`,
        specId
      );
      breakingChanges.push(...securityChanges);
    }
  }
  
  return breakingChanges;
}

/**
 * Detects request body schema breaking changes
 */
function detectRequestBodySchemaChanges(baselineReq, currentReq, operation, location) {
  const changes = [];
  
  const baselineContent = baselineReq.content || {};
  const currentContent = currentReq.content || {};
  
  for (const [contentType, baselineMediaType] of Object.entries(baselineContent)) {
    const currentMediaType = currentContent[contentType];
    
    if (!currentMediaType) {
      changes.push({
        type: BREAKING_CHANGE_TYPES.CONTENT_TYPE_REMOVED,
        operation,
        detail: `Request content type ${contentType} removed`,
        location,
      });
      continue;
    }
    
    const baselineSchema = baselineMediaType.schema;
    const currentSchema = currentMediaType.schema;
    
    if (baselineSchema && currentSchema) {
      const schemaChanges = detectSchemaPropertyChanges(
        baselineSchema,
        currentSchema,
        operation,
        location,
        contentType
      );
      changes.push(...schemaChanges);
    }
  }
  
  return changes;
}

/**
 * Detects response schema breaking changes
 */
function detectResponseSchemaChanges(baselineResp, currentResp, operation, statusCode) {
  const changes = [];
  
  const baselineContent = baselineResp.content || {};
  const currentContent = currentResp.content || {};
  
  for (const [contentType, baselineMediaType] of Object.entries(baselineContent)) {
    const currentMediaType = currentContent[contentType];
    
    if (!currentMediaType) {
      changes.push({
        type: BREAKING_CHANGE_TYPES.CONTENT_TYPE_REMOVED,
        operation,
        detail: `Response ${statusCode} content type ${contentType} removed`,
        location: 'response',
      });
      continue;
    }
    
    const baselineSchema = baselineMediaType.schema;
    const currentSchema = currentMediaType.schema;
    
    if (baselineSchema && currentSchema) {
      const schemaChanges = detectSchemaPropertyChanges(
        baselineSchema,
        currentSchema,
        operation,
        'response',
        contentType
      );
      changes.push(...schemaChanges);
    }
  }
  
  return changes;
}

/**
 * Detects schema property-level breaking changes
 */
function detectSchemaPropertyChanges(baselineSchema, currentSchema, operation, location, contentType) {
  const changes = [];
  
  const baselineProps = asObject(baselineSchema.properties);
  const currentProps = asObject(currentSchema.properties);
  const baselineRequired = asArray(baselineSchema.required);
  const currentRequired = asArray(currentSchema.required);
  
  // Check for removed properties
  for (const propName of Object.keys(baselineProps)) {
    if (!currentProps[propName]) {
      changes.push({
        type: BREAKING_CHANGE_TYPES.RESPONSE_FIELD_REMOVED,
        operation,
        detail: `Property '${propName}' removed from ${location} schema (${contentType})`,
        location,
      });
    }
  }
  
  // Check for newly required properties
  for (const propName of currentRequired) {
    if (!baselineRequired.includes(propName) && baselineProps[propName]) {
      changes.push({
        type: BREAKING_CHANGE_TYPES.REQUIRED_FIELD_ADDED,
        operation,
        detail: `Property '${propName}' made required in ${location} schema (${contentType})`,
        location,
      });
    }
  }
  
  // Check for enum value removals
  for (const [propName, baselineProp] of Object.entries(baselineProps)) {
    const currentProp = currentProps[propName];
    if (baselineProp && currentProp) {
      const baselineEnum = asArray(baselineProp.enum);
      const currentEnum = asArray(currentProp.enum);
      if (baselineEnum.length > 0 && currentEnum.length > 0) {
        const removedEnums = baselineEnum.filter(v => !currentEnum.includes(v));
        if (removedEnums.length > 0) {
          changes.push({
            type: BREAKING_CHANGE_TYPES.ENUM_VALUE_REMOVED,
            operation,
            detail: `Enum values ${removedEnums.join(', ')} removed from property '${propName}' in ${location} schema (${contentType})`,
            location,
          });
        }
      }
      
      // Check for type changes
      if (baselineProp.type && currentProp.type && baselineProp.type !== currentProp.type) {
        changes.push({
          type: BREAKING_CHANGE_TYPES.RESPONSE_TYPE_CHANGED,
          operation,
          detail: `Property '${propName}' type changed from ${baselineProp.type} to ${currentProp.type} in ${location} schema (${contentType})`,
          location,
        });
      }
      
      // Check for nullability changes (nullable was removed in OpenAPI 3.1, but check for it)
      if (baselineProp.nullable !== undefined && currentProp.nullable !== undefined) {
        if (baselineProp.nullable === false && currentProp.nullable === true) {
          changes.push({
            type: BREAKING_CHANGE_TYPES.NULLABILITY_CHANGED,
            operation,
            detail: `Property '${propName}' made nullable in ${location} schema (${contentType})`,
            location,
          });
        }
      }
    }
  }
  
  return changes;
}

/**
 * Detects parameter breaking changes
 */
function detectParameterChanges(baselineParams, currentParams, operation, specId) {
  const changes = [];
  
  const baselineParamMap = new Map();
  for (const param of baselineParams) {
    if (param.name && param.in) {
      baselineParamMap.set(`${param.in}:${param.name}`, param);
    }
  }
  
  const currentParamMap = new Map();
  for (const param of currentParams) {
    if (param.name && param.in) {
      currentParamMap.set(`${param.in}:${param.name}`, param);
    }
  }
  
  // Check for removed parameters
  for (const [key, baselineParam] of baselineParamMap) {
    if (!currentParamMap.has(key)) {
      if (baselineParam.required) {
        changes.push({
          type: BREAKING_CHANGE_TYPES.PATH_PARAMETER_RENAMED,
          operation,
          detail: `Required parameter '${baselineParam.name}' (${baselineParam.in}) removed`,
          specId,
        });
      }
    }
  }
  
  // Check for newly required parameters
  for (const [key, currentParam] of currentParamMap) {
    const baselineParam = baselineParamMap.get(key);
    if (baselineParam && !baselineParam.required && currentParam.required) {
      changes.push({
        type: BREAKING_CHANGE_TYPES.QUERY_PARAMETER_REQUIRED,
        operation,
        detail: `Parameter '${currentParam.name}' (${currentParam.in}) made required`,
        specId,
      });
    }
  }
  
  return changes;
}

function asArray(value) {
  return Array.isArray(value) ? value : [];
}

function asObject(value) {
  return value && typeof value === 'object' && !Array.isArray(value) ? value : {};
}

/**
 * Detects security requirement changes
 */
function detectSecurityChanges(baselineSecurity, currentSecurity, operation, specId) {
  const changes = [];
  
  const baselineSecuritySet = new Set();
  for (const req of baselineSecurity) {
    if (typeof req === 'object') {
      baselineSecuritySet.add(JSON.stringify(req));
    }
  }
  
  const currentSecuritySet = new Set();
  for (const req of currentSecurity) {
    if (typeof req === 'object') {
      currentSecuritySet.add(JSON.stringify(req));
    }
  }
  
  // Check for removed security requirements
  for (const req of baselineSecuritySet) {
    if (!currentSecuritySet.has(req)) {
      changes.push({
        type: BREAKING_CHANGE_TYPES.SECURITY_REQUIREMENT_CHANGED,
        operation,
        detail: `Security requirement removed: ${req}`,
        specId,
      });
    }
  }
  
  return changes;
}

export function detectRemovedOperations({ baselineKeys, currentKeys, waivers, specId }) {
  const removed = [...baselineKeys].filter((entry) => !currentKeys.has(entry)).sort();
  const waivedRemoved = removed.filter((entry) => waivers.has(`${specId}:${entry}`));
  const unwaivedRemoved = removed.filter((entry) => !waivers.has(`${specId}:${entry}`));

  return {
    removed,
    waivedRemoved,
    unwaivedRemoved,
  };
}

function loadWaivers() {
  if (!existsSync(waiversPath)) {
    return new Set();
  }
  const waivers = JSON.parse(readFileSync(waiversPath, 'utf8'));
  return new Set((waivers.allowedBreakingChanges ?? []).map((entry) => String(entry)));
}

export function runOpenApiBreakingChangeCheck() {
  const violations = [];
  const details = [];
  const waivers = loadWaivers();

  for (const spec of specs) {
    const currentPath = path.join(repoRoot, spec.current);
    const baselinePath = path.join(repoRoot, spec.baseline);

    if (!existsSync(currentPath)) {
      violations.push(`Missing current OpenAPI spec for ${spec.id}: ${spec.current}`);
      continue;
    }

    if (!existsSync(baselinePath)) {
      violations.push(`Missing OpenAPI baseline for ${spec.id}: ${spec.baseline}`);
      continue;
    }

    const currentSource = readFileSync(currentPath, 'utf8');
    const baselineSource = readFileSync(baselinePath, 'utf8');

    // Original path/method removal check
    const currentKeys = extractPathMethodKeys(currentSource);
    const baselineKeys = extractPathMethodKeys(baselineSource);

    const { removed, waivedRemoved, unwaivedRemoved } = detectRemovedOperations({
      baselineKeys,
      currentKeys,
      waivers,
      specId: spec.id,
    });

    if (unwaivedRemoved.length > 0) {
      violations.push(
        `Breaking OpenAPI removal detected for ${spec.id}: ${unwaivedRemoved.join(', ')}`,
      );
    }

    // Schema-aware breaking change detection
    let schemaBreakingChanges = [];
    try {
      const baselineSpec = parseOpenApiSpec(baselineSource);
      const currentSpec = parseOpenApiSpec(currentSource);
      schemaBreakingChanges = detectSchemaBreakingChanges(baselineSpec, currentSpec, spec.id);
    } catch (error) {
      // If parsing fails, log but don't fail the check
      console.warn(`Warning: Schema-aware diffing failed for ${spec.id}: ${error.message}`);
    }

    // Filter schema changes by waivers
    const unwaivedSchemaChanges = schemaBreakingChanges.filter(change => {
      const waiverKey = `${spec.id}:${change.type}:${change.operation}`;
      return !waivers.has(waiverKey);
    });

    for (const change of unwaivedSchemaChanges) {
      violations.push(
        `Breaking schema change detected for ${spec.id} (${change.operation}): ${change.type} - ${change.detail}`
      );
    }

    details.push({
      id: spec.id,
      baselinePath: spec.baseline,
      currentPath: spec.current,
      removedCount: removed.length,
      waivedRemoved,
      unwaivedRemoved,
      schemaBreakingChanges: schemaBreakingChanges.length,
      unwaivedSchemaChanges: unwaivedSchemaChanges.length,
      schemaChanges: schemaBreakingChanges,
    });
  }

  mkdirSync(evidenceDir, { recursive: true });
  writeEvidenceAtomicWithRetry(
    evidencePath,
    `${JSON.stringify({
      generatedAt: new Date().toISOString(),
      status: violations.length === 0 ? 'passed' : 'failed',
      details,
      violations,
    }, null, 2)}\n`,
  );

  return {
    pass: violations.length === 0,
    violations,
  };
}

function main() {
  const result = runOpenApiBreakingChangeCheck();
  if (!result.pass) {
    console.error('OpenAPI breaking-change check failed:');
    for (const violation of result.violations) {
      console.error(`- ${violation}`);
    }
    process.exit(1);
  }

  console.log('OpenAPI breaking-change check passed.');
}

const isMainModule = process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url);
if (isMainModule) {
  main();
}
