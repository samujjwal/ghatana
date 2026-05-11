#!/usr/bin/env node
/**
 * Structural YAML AST validator for product CI matrix and workflow files.
 *
 * Replaces flat string-token checks with proper structural validation that:
 *  - Parses matrix.include entries into JS objects
 *  - Validates required products, taskPrefixes, commands, and reportPaths
 *  - Checks path-filter coverage for every required product
 *  - Detects forbidden patterns (|| true) in run steps
 *  - Validates Node version meets minimum requirement
 *  - Validates pnpm version matches root policy
 *
 * Uses only Node.js built-ins; no external YAML parser dependency.
 */

import { readFileSync } from 'node:fs';
import path from 'node:path';

const repoRoot = process.cwd();
const violations = [];
const productShape = JSON.parse(
  readFileSync(path.join(repoRoot, 'config/product-shape.json'), 'utf8'),
);

const productDisplayNames = {
  finance: 'Finance',
  phr: 'PHR',
  'digital-marketing': 'Digital Marketing',
  flashit: 'FlashIt',
};

function productIdsFromShape() {
  return Object.keys(productShape.products).sort();
}

function productDisplayName(productId) {
  return productDisplayNames[productId] ?? productId;
}

function productPathFilter(productId) {
  return `products/${productId}/**`;
}

function productTaskPrefix(productId) {
  if (productId === 'digital-marketing') {
    return ':products:digital-marketing';
  }
  return `:products:${productId}`;
}

// ---------------------------------------------------------------------------
// Minimal structural YAML parser – GitHub Actions workflow subset
// ---------------------------------------------------------------------------

/**
 * Parses a sequence of `- key: value` blocks from YAML text, starting at the
 * first line that begins with listIndent spaces followed by `- `.
 * Returns an array of plain JS objects, one per list item.
 *
 * Only handles scalar leaf values (string / number / boolean).  Nested blocks
 * and multi-line strings are flattened into a single concatenated string.
 */
function parseYamlListBlock(lines, listIndent) {
  const items = [];
  let current = null;
  const itemPrefix = new RegExp(`^\\s{${listIndent}}-\\s+(\\S.*)$`);
  const keyPrefix = new RegExp(`^\\s{${listIndent + 2}}([\\w-]+):\\s*(.*)$`);
  const runLine = new RegExp(`^\\s{${listIndent + 4}}(.+)$`);

  for (const line of lines) {
    const itemMatch = itemPrefix.exec(line);
    if (itemMatch) {
      if (current) items.push(current);
      current = {};
      // First key may appear inline: `- product: Finance`
      const inlineKey = /^(\w[\w-]*):\s*(.*)$/.exec(itemMatch[1].trim());
      if (inlineKey) {
        current[inlineKey[1]] = inlineKey[2].trim().replace(/^['"]|['"]$/g, '');
      }
      continue;
    }
    if (!current) continue;
    const keyMatch = keyPrefix.exec(line);
    if (keyMatch) {
      const [, key, rawVal] = keyMatch;
      const val = rawVal.trim().replace(/^['"]|['"]$/g, '');
      if (val === '|' || val === '>') {
        current[key] = current[key] ?? '';
        continue;
      }
      current[key] = (current[key] ?? '') + val;
      continue;
    }
    // Continuation lines inside `run: |` blocks
    if (current.command !== undefined || current.run !== undefined) {
      const runMatch = runLine.exec(line);
      if (runMatch) {
        const activeKey = current.run !== undefined ? 'run' : 'command';
        current[activeKey] = (current[activeKey] ?? '') + ' ' + runMatch[1].trim();
      }
    }
  }
  if (current) items.push(current);
  return items;
}

/**
 * Locates the `include:` list under `strategy > matrix` in workflow YAML and
 * returns an array of objects, one per matrix entry.
 */
function extractMatrixIncludes(source) {
  const lines = source.split(/\r?\n/);
  let matrixIndent = -1;
  let includeIndent = -1;
  let listIndent = -1;
  const includeLines = [];

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];
    const matrixMatch = /^(\s*)matrix:\s*$/.exec(line);
    if (matrixMatch) {
      matrixIndent = matrixMatch[1].length;
      includeIndent = -1;
      listIndent = -1;
      includeLines.length = 0;
      continue;
    }
    if (matrixIndent === -1) continue;

    const indent = /^(\s*)/.exec(line)[1].length;
    const trimmed = line.trim();

    // At or below matrix level — stop if non-empty non-child
    if (!trimmed) continue;
    if (indent <= matrixIndent && trimmed !== '') {
      matrixIndent = -1;
      continue;
    }

    if (includeIndent === -1) {
      if (trimmed === 'include:') {
        includeIndent = indent;
      }
      continue;
    }

    // Stop at sibling of include (same indent as include, not a list item)
    if (indent <= includeIndent && !trimmed.startsWith('-')) {
      break;
    }

    if (listIndent === -1 && trimmed.startsWith('-')) {
      listIndent = indent;
    }
    includeLines.push(line);
  }
  if (listIndent === -1) return [];
  return parseYamlListBlock(includeLines, listIndent);
}

/**
 * Returns all path filter strings under `on.pull_request.paths`.
 */
function extractPathFilters(source) {
  const lines = source.split(/\r?\n/);
  const pathValues = [];
  let inOn = false;
  let inPR = false;
  let pathsIndent = -1;

  for (const line of lines) {
    const trimmed = line.trim();
    if (!trimmed) continue;
    const indent = /^(\s*)/.exec(line)[1].length;

    if (/^on:/.test(line)) { inOn = true; inPR = false; pathsIndent = -1; continue; }
    if (!inOn) continue;

    if (trimmed === 'pull_request:') { inPR = true; pathsIndent = -1; continue; }
    if (!inPR) {
      if (indent === 0 && /^\w/.test(trimmed)) { inOn = false; }
      continue;
    }

    if (trimmed === 'paths:') { pathsIndent = indent; continue; }
    if (pathsIndent === -1) continue;

    if (indent > pathsIndent && trimmed.startsWith('-')) {
      pathValues.push(trimmed.replace(/^-\s+/, '').replace(/^['"]|['"]$/g, ''));
      continue;
    }
    if (indent <= pathsIndent && !trimmed.startsWith('-')) break;
  }
  return pathValues;
}

/**
 * Returns all `run:` block contents in the workflow.
 */
function extractRunBlocks(source) {
  const blocks = [];
  const lines = source.split(/\r?\n/);
  let inRun = false;
  let currentRun = '';

  for (const line of lines) {
    if (/^\s+run:\s*\|?\s*$/.test(line)) {
      if (currentRun) blocks.push(currentRun);
      currentRun = '';
      inRun = true;
      continue;
    }
    if (/^\s+run:\s+(.+)$/.test(line)) {
      if (currentRun) blocks.push(currentRun);
      currentRun = /^\s+run:\s+(.+)$/.exec(line)[1];
      inRun = false;
      blocks.push(currentRun);
      currentRun = '';
      continue;
    }
    if (inRun) {
      if (/^\s{10,}/.test(line)) {
        currentRun += '\n' + line;
        continue;
      }
      if (currentRun) blocks.push(currentRun);
      currentRun = '';
      inRun = false;
    }
  }
  if (currentRun) blocks.push(currentRun);
  return blocks;
}

/**
 * Extracts all `node-version:` values in the workflow.
 */
function extractNodeVersions(source) {
  const versions = [];
  for (const line of source.split(/\r?\n/)) {
    const m = /node-version:\s*['"]?(\d+)/.exec(line);
    if (m) versions.push(parseInt(m[1], 10));
  }
  return versions;
}

/**
 * Extracts all pnpm version strings (from `pnpm@X.Y.Z` or `pnpm-version:`).
 */
function extractPnpmVersions(source) {
  const versions = [];
  for (const line of source.split(/\r?\n/)) {
    const m = /pnpm@([\d.]+)/.exec(line);
    if (m) versions.push(m[1]);
  }
  return versions;
}

// ---------------------------------------------------------------------------
// Validators
// ---------------------------------------------------------------------------

function assertMatrixProducts(file, label, source, requiredProducts) {
  const includes = extractMatrixIncludes(source);
  const found = new Set(includes.map((e) => e.product));
  for (const p of requiredProducts) {
    if (!found.has(p)) {
      violations.push(`${file}: ${label} matrix is missing required product "${p}" (found: ${[...found].join(', ')})`);
    }
  }
  return includes;
}

function assertPathFilters(file, label, source, requiredProductPaths) {
  const filters = extractPathFilters(source);
  for (const expected of requiredProductPaths) {
    if (!filters.some((f) => f === expected || f.startsWith(expected.replace(/\*\*$/, '')))) {
      violations.push(`${file}: ${label} pull_request.paths is missing filter "${expected}"`);
    }
  }
}

function assertNoSwallowedFailures(file, source) {
  const runBlocks = extractRunBlocks(source);
  for (const block of runBlocks) {
    if (block.includes('|| true')) {
      violations.push(`${file}: run block contains forbidden "|| true" pattern: ${block.trim().slice(0, 80)}`);
    }
  }
}

function assertMinNodeVersion(file, source, minVersion) {
  const versions = extractNodeVersions(source);
  for (const v of versions) {
    if (v < minVersion) {
      violations.push(`${file}: node-version ${v} is below minimum required ${minVersion}`);
    }
  }
}

function assertPnpmVersion(file, source, requiredVersion) {
  const versions = extractPnpmVersions(source);
  for (const v of versions) {
    if (v !== requiredVersion) {
      violations.push(`${file}: pnpm version ${v} does not match required ${requiredVersion}`);
    }
  }
}

function assertMatrixEntryFields(file, label, includes, requiredFields) {
  for (const entry of includes) {
    const product = entry.product ?? '(unknown)';
    for (const field of requiredFields) {
      if (!entry[field] || String(entry[field]).trim() === '') {
        violations.push(`${file}: ${label} matrix entry "${product}" is missing required field "${field}"`);
      }
    }
  }
}

function assertMatrixCommands(file, label, includes, expectedCommands) {
  for (const { product, command } of expectedCommands) {
    const entry = includes.find((e) => e.product === product);
    if (!entry) continue; // missing product already reported
    const actual = (entry.command ?? '').trim();
    if (!actual.includes(command)) {
      violations.push(
        `${file}: ${label} matrix entry "${product}" command does not contain expected "${command}"`,
      );
    }
  }
}

// ---------------------------------------------------------------------------
// Check 1: product-coverage-gates.yml
// ---------------------------------------------------------------------------

const coverageGatesFile = '.github/workflows/product-coverage-gates.yml';
const coverageGatesSource = readFileSync(path.join(repoRoot, coverageGatesFile), 'utf8');

const productIds = productIdsFromShape();
const coverageProducts = productIds.map(productDisplayName);
const coverageIncludes = assertMatrixProducts(coverageGatesFile, 'product coverage gates', coverageGatesSource, coverageProducts);

assertMatrixEntryFields(coverageGatesFile, 'product coverage gates', coverageIncludes, [
  'product',
  'taskPrefix',
  'reportPath',
]);

for (const productId of productIds) {
  const product = productDisplayName(productId);
  const prefix = productTaskPrefix(productId);
  const entry = coverageIncludes.find((e) => e.product === product);
  if (entry && !String(entry.taskPrefix ?? '').includes(prefix)) {
    violations.push(
      `${coverageGatesFile}: product coverage gates matrix entry "${product}" taskPrefix "${entry.taskPrefix}" does not contain "${prefix}"`,
    );
  }
}

assertPathFilters(coverageGatesFile, 'product coverage gates', coverageGatesSource, productIds.map(productPathFilter));

assertNoSwallowedFailures(coverageGatesFile, coverageGatesSource);

// ---------------------------------------------------------------------------
// Check 2: api-contract-conformance.yml
// ---------------------------------------------------------------------------

const apiConformanceFile = '.github/workflows/api-contract-conformance.yml';
const apiConformanceSource = readFileSync(path.join(repoRoot, apiConformanceFile), 'utf8');

const conformanceProducts = productIds.map(productDisplayName);
const conformanceIncludes = assertMatrixProducts(apiConformanceFile, 'API contract conformance', apiConformanceSource, conformanceProducts);

assertMatrixEntryFields(apiConformanceFile, 'API contract conformance', conformanceIncludes, [
  'product',
  'command',
  'reportPath',
]);

const expectedConformanceCommands = productIds.map((productId) => {
  if (productId === 'digital-marketing') {
    return { product: productDisplayName(productId), command: ':products:digital-marketing:dm-api:test' };
  }
  if (productId === 'flashit') {
    return { product: productDisplayName(productId), command: 'flashit-tests' };
  }
  return {
    product: productDisplayName(productId),
    command: `${productTaskPrefix(productId)}:checkApiContractConformance`,
  };
});
assertMatrixCommands(apiConformanceFile, 'API contract conformance', conformanceIncludes, expectedConformanceCommands);

assertPathFilters(apiConformanceFile, 'API contract conformance', apiConformanceSource, productIds.map(productPathFilter));

assertMinNodeVersion(apiConformanceFile, apiConformanceSource, 22);
assertPnpmVersion(apiConformanceFile, apiConformanceSource, '10.33.0');
assertNoSwallowedFailures(apiConformanceFile, apiConformanceSource);

// ---------------------------------------------------------------------------
// Report
// ---------------------------------------------------------------------------

if (violations.length > 0) {
  console.error('Product CI matrix structural check failed:\n');
  for (const violation of violations) {
    console.error(`- ${violation}`);
  }
  process.exit(1);
}

console.log('Product CI matrix structural check passed.');
