#!/usr/bin/env node

/**
 * G11-T01 / PHR-BE-006: verify PHR route responses use a correlation-aware
 * response helper overload.
 */

import { readdirSync, readFileSync } from 'fs';
import { dirname, join } from 'path';
import { fileURLToPath } from 'url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const DEFAULT_ROUTES_DIR = join(
  __dirname,
  '..',
  'products',
  'phr',
  'src',
  'main',
  'java',
  'com',
  'ghatana',
  'phr',
  'api',
  'routes',
);

const ROUTES_DIR = process.env.PHR_ROUTE_DIR ?? DEFAULT_ROUTES_DIR;

const REQUIRED_ARG_COUNTS = new Map([
  ['PhrRouteSupport.errorResponse', 4],
  ['PhrRouteSupport.jsonResponse', 3],
  ['PhrRouteSupport.textResponse', 4],
]);

function lineNumber(content, index) {
  return content.slice(0, index).split('\n').length;
}

function parseCall(content, callee, startIndex) {
  const openParen = content.indexOf('(', startIndex + callee.length);
  if (openParen === -1) {
    return null;
  }

  let depth = 0;
  let argStart = openParen + 1;
  let quote = null;
  let escaped = false;
  const args = [];

  for (let index = openParen + 1; index < content.length; index += 1) {
    const char = content[index];
    const next = content[index + 1];

    if (quote !== null) {
      if (escaped) {
        escaped = false;
      } else if (char === '\\') {
        escaped = true;
      } else if (char === quote) {
        quote = null;
      }
      continue;
    }

    if (char === '"' || char === '\'') {
      quote = char;
      continue;
    }

    if (char === '/' && next === '/') {
      const newline = content.indexOf('\n', index + 2);
      index = newline === -1 ? content.length : newline;
      continue;
    }

    if (char === '/' && next === '*') {
      const endComment = content.indexOf('*/', index + 2);
      index = endComment === -1 ? content.length : endComment + 1;
      continue;
    }

    if (char === '(' || char === '[' || char === '{') {
      depth += 1;
      continue;
    }

    if (char === ')' || char === ']' || char === '}') {
      if (depth === 0 && char === ')') {
        const arg = content.slice(argStart, index).trim();
        if (arg.length > 0) {
          args.push(arg);
        }
        return {
          args,
          endIndex: index + 1,
          snippet: content.slice(startIndex, index + 1).replace(/\s+/g, ' ').trim(),
        };
      }
      depth -= 1;
      continue;
    }

    if (char === ',' && depth === 0) {
      args.push(content.slice(argStart, index).trim());
      argStart = index + 1;
    }
  }

  return null;
}

function findCalls(content, callee) {
  const calls = [];
  let index = content.indexOf(callee);

  while (index !== -1) {
    const call = parseCall(content, callee, index);
    if (call !== null) {
      calls.push({ ...call, startIndex: index });
      index = content.indexOf(callee, call.endIndex);
    } else {
      index = content.indexOf(callee, index + callee.length);
    }
  }

  return calls;
}

function checkFile(filePath) {
  const content = readFileSync(filePath, 'utf-8');
  const fileName = filePath.split('/').pop();
  const violations = [];

  for (const [callee, requiredCount] of REQUIRED_ARG_COUNTS.entries()) {
    for (const call of findCalls(content, callee)) {
      if (call.args.length < requiredCount) {
        violations.push({
          file: fileName,
          line: lineNumber(content, call.startIndex),
          type: `${callee.split('.').pop()} without correlationId`,
          snippet: call.snippet,
        });
      }
    }
  }

  return violations;
}

function main() {
  const routeFiles = readdirSync(ROUTES_DIR)
    .filter((fileName) => fileName.endsWith('Routes.java'))
    .map((fileName) => join(ROUTES_DIR, fileName));

  const violations = routeFiles.flatMap(checkFile);

  if (violations.length === 0) {
    console.log('PASS: All PHR route helper responses use correlation-aware overloads');
    return;
  }

  console.error(`FAIL: Found ${violations.length} PHR route response correlation violations:\n`);
  for (const violation of violations) {
    console.error(`  ${violation.file}:${violation.line} - ${violation.type}`);
    console.error(`    ${violation.snippet}\n`);
  }
  process.exit(1);
}

main();
