#!/usr/bin/env node

import { existsSync, readFileSync } from 'node:fs';
import path from 'node:path';

const repoRoot = process.cwd();

const sources = [
  'products/flashit/.env.example',
  'products/flashit/docker-compose.local.yml',
  'products/phr/.env.example',
  'products/phr/docker-compose.local.yml',
  'products/finance/.env.example',
  'products/finance/docker-compose.local.yml',
  'products/digital-marketing/.env.example',
  'products/digital-marketing/docker-compose.local.yml',
];

const ignoreVars = new Set([
  'API_PORT',
  'WEB_PORT',
  'MOBILE_PORT',
  'WS_PORT',
  'SMTP_PORT',
  'REDIS_PORT',
  'METRICS_PORT',
  'MINIO_PORT',
]);

const findingsByPort = new Map();

function addFinding(port, finding) {
  const existing = findingsByPort.get(port) ?? [];
  existing.push(finding);
  findingsByPort.set(port, existing);
}

function extractProduct(relativePath) {
  const match = relativePath.match(/^products\/([^/]+)\//);
  return match?.[1] ?? 'unknown';
}

for (const relativePath of sources) {
  const absolutePath = path.join(repoRoot, relativePath);
  if (!existsSync(absolutePath)) {
    continue;
  }

  const product = extractProduct(relativePath);
  const source = readFileSync(absolutePath, 'utf8');

  const envMatches = source.matchAll(/^([A-Z0-9_]*PORT[A-Z0-9_]*)=(\d+)$/gm);
  for (const [, variable, port] of envMatches) {
    if (ignoreVars.has(variable)) {
      continue;
    }
    addFinding(port, { product, variable, file: relativePath, kind: 'env' });
  }

  const composeMatches = source.matchAll(/\$\{([A-Z0-9_]*PORT[A-Z0-9_]*):-(\d+)\}/g);
  for (const [, variable, port] of composeMatches) {
    if (ignoreVars.has(variable)) {
      continue;
    }
    addFinding(port, { product, variable, file: relativePath, kind: 'compose' });
  }
}

const violations = [];

for (const [port, findings] of findingsByPort.entries()) {
  const productSet = new Set(findings.map((finding) => finding.product));
  if (productSet.size <= 1) {
    continue;
  }

  const rendered = findings
    .map((finding) => `${finding.product}:${finding.variable} (${finding.file})`)
    .join(', ');
  violations.push(`port ${port} is assigned by multiple products: ${rendered}`);
}

if (violations.length > 0) {
  console.error('Local development port allocation check failed:\n');
  for (const violation of violations) {
    console.error(`- ${violation}`);
  }
  process.exit(1);
}

console.log('Local development port allocation check passed.');
