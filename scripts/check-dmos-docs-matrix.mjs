#!/usr/bin/env node
/**
 * DMOS canonical documentation matrix guard.
 *
 * @doc.type tooling
 * @doc.purpose Ensure DMOS product truth lives in canonical docs and stale audit output stays archived
 * @doc.layer infrastructure
 */

import { existsSync, readFileSync, readdirSync, statSync } from 'node:fs';
import { join, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const repoRoot = resolve(fileURLToPath(new URL('..', import.meta.url)));
const docsRoot = join(repoRoot, 'products/digital-marketing/docs');
const canonicalRoot = join(docsRoot, 'canonical');

const requiredCanonicalDocs = [
  '00-VISION.md',
  '01-ARCHITECTURE.md',
  '02-API_CONTRACTS.md',
  '03-UX_WORKFLOWS.md',
  '04-TESTING.md',
  '05-OPERATIONS.md',
  '06-IMPLEMENTATION_PLAN.md',
  '06-PRODUCTION_MVP_CONTRACT.md',
  '07-MARKET_AND_POSITIONING.md',
  '08-PRODUCT_REQUIREMENTS.md',
  '09-FEATURE_CATALOG.md',
  '10-DESIGN.md',
  '11-DATA_MODEL.md',
];

const violations = [];

for (const docName of requiredCanonicalDocs) {
  const path = join(canonicalRoot, docName);
  if (!existsSync(path)) {
    violations.push(`missing canonical doc products/digital-marketing/docs/canonical/${docName}`);
    continue;
  }
  const content = readFileSync(path, 'utf8').trim();
  if (content.length < 200) {
    violations.push(`canonical doc is too thin to own product truth: docs/canonical/${docName}`);
  }
}

function walk(dir) {
  if (!existsSync(dir)) {
    return [];
  }
  const files = [];
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry);
    const stat = statSync(full);
    if (stat.isDirectory()) {
      files.push(...walk(full));
    } else if (entry.endsWith('.md')) {
      files.push(full);
    }
  }
  return files;
}

for (const file of walk(join(docsRoot, 'audits'))) {
  const rel = relative(repoRoot, file).replace(/\\/g, '/');
  if (!rel.includes('/docs/audits/archived/')) {
    violations.push(`${rel}: audit output must be under docs/audits/archived`);
  }
}

const activeDocTruthPattern =
  /\b(single source of truth|canonical source of truth|authoritative definition|production-ready|ready for production)\b/i;

for (const file of walk(docsRoot)) {
  const rel = relative(repoRoot, file).replace(/\\/g, '/');
  const isCanonical = rel.includes('/docs/canonical/');
  const isArchived = rel.includes('/docs/audits/archived/') || rel.includes('/docs/archive/');
  const isOpenApi = rel.endsWith('/docs/api-contract.yaml');
  const isRunbook = rel.includes('/docs/runbooks/');
  const isAdr = rel.includes('/docs/adr/');

  if (isCanonical || isArchived || isOpenApi || isRunbook || isAdr) {
    continue;
  }

  const content = readFileSync(file, 'utf8');
  if (activeDocTruthPattern.test(content)) {
    violations.push(`${rel}: non-canonical active doc claims product truth/readiness`);
  }
}

if (violations.length > 0) {
  console.error('DMOS documentation matrix guard failed:');
  for (const violation of violations) {
    console.error(`- ${violation}`);
  }
  process.exit(1);
}

console.log('DMOS documentation matrix guard passed.');
