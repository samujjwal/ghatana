#!/usr/bin/env node

import { existsSync, readFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const repoRoot = process.cwd();
const waiverPath = path.join(repoRoot, 'config/openapi-generic-schema-waivers.json');
const specConfigs = [
  {
    id: 'data-cloud',
    relativePath: 'products/data-cloud/contracts/openapi/data-cloud.yaml',
    requireExamples: true,
    strictOperationChecks: false,
    enforceGenericSchemas: false,
  },
  {
    id: 'action-plane',
    relativePath: 'products/data-cloud/contracts/openapi/action-plane.yaml',
    requireExamples: false,
    strictOperationChecks: true,
    enforceGenericSchemas: true,
  },
];

function normalizeSource(source) {
  return source.replace(/\r\n/g, '\n');
}

function leadingSpaces(line) {
  const match = line.match(/^(\s*)/);
  return match ? match[1].length : 0;
}

export function extractOperationsFromYaml(source) {
  const lines = normalizeSource(source).split('\n');
  const operations = [];

  let inPaths = false;
  let currentPath = null;
  let currentMethod = null;
  let currentMethodIndent = 0;
  let currentBuffer = [];

  const flushCurrentMethod = () => {
    if (!currentPath || !currentMethod) {
      return;
    }
    operations.push({
      path: currentPath,
      method: currentMethod.toUpperCase(),
      block: currentBuffer.join('\n'),
    });
    currentMethod = null;
    currentBuffer = [];
  };

  for (const line of lines) {
    const trimmed = line.trim();
    const indent = leadingSpaces(line);

    if (!inPaths) {
      if (trimmed === 'paths:') {
        inPaths = true;
      }
      continue;
    }

    if (indent === 0 && trimmed.length > 0 && !trimmed.startsWith('#') && /^[a-zA-Z0-9_-]+:\s*$/.test(trimmed)) {
      flushCurrentMethod();
      break;
    }

    const pathMatch = line.match(/^\s{2}(\/[^\s]+):\s*$/);
    if (pathMatch) {
      flushCurrentMethod();
      currentPath = pathMatch[1];
      continue;
    }

    const methodMatch = line.match(/^\s{4}(get|post|put|patch|delete|head|options):\s*$/i);
    if (methodMatch && currentPath) {
      flushCurrentMethod();
      currentMethod = methodMatch[1];
      currentMethodIndent = indent;
      currentBuffer = [line];
      continue;
    }

    if (currentMethod) {
      if (indent > currentMethodIndent) {
        currentBuffer.push(line);
      } else if (trimmed.length > 0) {
        flushCurrentMethod();
        currentMethod = null;
        currentBuffer = [];
      }
    }
  }

  flushCurrentMethod();
  return operations;
}

function hasTyped2xxResponse(operationBlock) {
  const statusPattern = /^\s{6,}['"]?(2\d\d)['"]?:\s*$/gm;
  const statusMatches = [...operationBlock.matchAll(statusPattern)];
  if (statusMatches.length === 0) {
    return false;
  }

  for (let index = 0; index < statusMatches.length; index += 1) {
    const code = statusMatches[index][1];
    const start = statusMatches[index].index + statusMatches[index][0].length;
    const end = index + 1 < statusMatches.length
      ? statusMatches[index + 1].index
      : operationBlock.length;
    const responseSection = operationBlock.slice(start, end);

    if (code === '204') {
      continue;
    }

    if (responseSection.includes('$ref:') || responseSection.includes('schema:')) {
      return true;
    }
  }

  return false;
}

function hasAny2xxResponse(operationBlock) {
  return /^\s{6,}['"]?(2\d\d)['"]?:\s*$/m.test(operationBlock);
}

function hasErrorEnvelopeContract(operationBlock) {
  if (/^\s{6,}['"]?(4\d\d|5\d\d)['"]?:\s*$/m.test(operationBlock)) {
    return true;
  }
  return /components\/responses\/(BadRequest|Unauthorized|Forbidden|NotFound|Conflict|Internal|ServiceUnavailable)/.test(operationBlock);
}

function hasRequiredMetadata(operationBlock) {
  return operationBlock.includes('x-ghatana-sensitivity:')
    && operationBlock.includes('x-ghatana-required-access:');
}

function requiresIdempotency(operation) {
  return operation.method === 'POST'
    || operation.method === 'PUT'
    || operation.method === 'PATCH'
    || operation.method === 'DELETE';
}

function requiresStrictGovernedChecks(operation) {
  return operation.path.startsWith('/api/v1/action/')
    || operation.block.includes('x-ghatana-sensitivity:')
    || operation.block.includes('x-ghatana-required-access:');
}

function requiresStrictIdempotency(operation) {
  return operation.block.includes('x-ghatana-sensitivity: CRITICAL')
    || operation.block.includes('x-ghatana-requires-policy: true');
}

function hasIdempotencyHeader(operationBlock) {
  return operationBlock.includes('Idempotency-Key')
    || operationBlock.includes('IdempotencyKeyHeader')
    || operationBlock.includes('X-Idempotency-Key');
}

export function collectGenericSchemas(source) {
  const normalized = normalizeSource(source);
  const genericSchemas = new Set();

  const lines = normalized.split('\n');
  let inComponents = false;
  let inSchemas = false;
  let currentSchemaName = null;
  let currentSchemaBody = [];

  const flushSchema = () => {
    if (!currentSchemaName) {
      return;
    }
    const schemaBody = currentSchemaBody.join('\n');
    const isObject = /(^|\n)\s{6}type:\s*object\s*$/m.test(schemaBody);
    const hasAdditionalProperties = /(^|\n)\s{6}additionalProperties:\s*true\s*$/m.test(schemaBody);
    const hasNamedProperties = /(^|\n)\s{6}properties:\s*$/m.test(schemaBody);
    if (isObject && hasAdditionalProperties && !hasNamedProperties) {
      genericSchemas.add(currentSchemaName);
    }
    currentSchemaName = null;
    currentSchemaBody = [];
  };

  for (const line of lines) {
    const trimmed = line.trim();
    const indent = leadingSpaces(line);

    if (!inComponents) {
      if (trimmed === 'components:') {
        inComponents = true;
      }
      continue;
    }

    if (!inSchemas) {
      if (trimmed === 'schemas:' && indent === 2) {
        inSchemas = true;
      } else if (indent === 0 && trimmed.length > 0 && !trimmed.startsWith('#')) {
        break;
      }
      continue;
    }

    if (indent === 2 && trimmed.length > 0 && /^[a-zA-Z0-9_-]+:\s*$/.test(trimmed)) {
      flushSchema();
      break;
    }

    const schemaMatch = line.match(/^ {4}([A-Z][A-Za-z0-9_]+):\s*$/);
    if (schemaMatch) {
      flushSchema();
      currentSchemaName = schemaMatch[1];
      currentSchemaBody = [];
      continue;
    }

    if (currentSchemaName && (indent >= 6 || trimmed.length === 0)) {
      currentSchemaBody.push(line);
      continue;
    }
  }

  flushSchema();

  return genericSchemas;
}

export function validateSpecQuality({
  source,
  specId,
  allowedGenericSchemas,
  requireExamples,
  strictOperationChecks = true,
  enforceGenericSchemas = true,
}) {
  const violations = [];
  const normalized = normalizeSource(source);
  const operations = extractOperationsFromYaml(normalized);

  if (operations.length === 0) {
    violations.push(`${specId}: no OpenAPI path operations were detected`);
  }

  if (strictOperationChecks) {
    for (const operation of operations) {
      const label = `${specId}: ${operation.method} ${operation.path}`;
      const strictGovernedChecks = requiresStrictGovernedChecks(operation);

      if (strictGovernedChecks) {
        if (!hasTyped2xxResponse(operation.block)) {
          violations.push(`${label} must declare a typed 2xx response schema (or 204)`);
        }
        if (!hasErrorEnvelopeContract(operation.block)) {
          violations.push(`${label} must declare at least one 4xx/5xx error response contract`);
        }
        if (!hasRequiredMetadata(operation.block)) {
          violations.push(`${label} must include x-ghatana sensitivity and access metadata`);
        }
        if (requiresIdempotency(operation) && requiresStrictIdempotency(operation) && !hasIdempotencyHeader(operation.block)) {
          violations.push(`${label} must document an idempotency header contract for critical/policy mutating operations`);
        }
      } else if (!hasAny2xxResponse(operation.block)) {
        violations.push(`${label} must declare at least one 2xx response`);
      }
    }
  }

  const genericSchemas = collectGenericSchemas(normalized);
  if (enforceGenericSchemas) {
    for (const schemaName of genericSchemas) {
      if (!allowedGenericSchemas.has(schemaName)) {
        violations.push(`${specId}: generic schema ${schemaName} must be explicitly waived`);
      }
    }

    for (const schemaName of allowedGenericSchemas) {
      if (!genericSchemas.has(schemaName)) {
        violations.push(`${specId}: waiver entry ${schemaName} is stale (schema is no longer generic)`);
      }
    }
  }

  if (requireExamples && !normalized.includes('example:') && !normalized.includes('examples:')) {
    violations.push(`${specId}: must include at least one example/examples block`);
  }

  if (!normalized.includes('x-ghatana-')) {
    violations.push(`${specId}: must contain x-ghatana-* metadata`);
  }

  return {
    violations,
    operationCount: operations.length,
    genericSchemaCount: genericSchemas.size,
  };
}

function readGenericSchemaWaivers() {
  if (!existsSync(waiverPath)) {
    throw new Error('OpenAPI release quality check failed: missing config/openapi-generic-schema-waivers.json');
  }

  const waivers = JSON.parse(readFileSync(waiverPath, 'utf8'));
  return new Set((waivers.allowedGenericSchemas ?? []).map((entry) => String(entry)));
}

export function runOpenApiReleaseQualityCheck() {
  const violations = [];
  const summary = [];
  const baseWaivers = readGenericSchemaWaivers();

  for (const specConfig of specConfigs) {
    const specPath = path.join(repoRoot, specConfig.relativePath);
    if (!existsSync(specPath)) {
      violations.push(`Missing OpenAPI contract: ${specConfig.relativePath}`);
      continue;
    }

    const source = readFileSync(specPath, 'utf8');
    const result = validateSpecQuality({
      source,
      specId: specConfig.id,
      allowedGenericSchemas: baseWaivers,
      requireExamples: specConfig.requireExamples,
      strictOperationChecks: specConfig.strictOperationChecks,
      enforceGenericSchemas: specConfig.enforceGenericSchemas,
    });

    summary.push({
      specId: specConfig.id,
      operationCount: result.operationCount,
      genericSchemaCount: result.genericSchemaCount,
      violationCount: result.violations.length,
    });

    violations.push(...result.violations);
  }

  return {
    pass: violations.length === 0,
    violations,
    summary,
  };
}

function main() {
  const result = runOpenApiReleaseQualityCheck();

  if (!result.pass) {
    console.error('OpenAPI release quality check failed:\n');
    for (const violation of result.violations) {
      console.error(`- ${violation}`);
    }
    process.exit(1);
  }

  console.log('OpenAPI release quality check passed.');
}

const isMainModule = process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url);
if (isMainModule) {
  main();
}
