#!/usr/bin/env node
/**
 * Canonical OpenAPI contract checks for Data Cloud and AEP.
 *
 * ARCH-P1-002 guardrails:
 * - required canonical OpenAPI files must exist
 * - required endpoint surfaces must be present
 * - AEP product contract spec and server runtime spec must stay byte-equivalent
 */

import { existsSync, readFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(__dirname, '..');

const files = {
  dataCloudApi: join(repoRoot, 'products', 'data-cloud', 'api', 'openapi.yaml'),
  dataCloudDocsGenerated: join(
    repoRoot,
    'products',
    'data-cloud',
    'docs-generated',
    '05-usage-manuals-and-api-docs',
    'openapi.yaml',
  ),
  aepContracts: join(repoRoot, 'products', 'aep', 'contracts', 'openapi.yaml'),
  aepServer: join(repoRoot, 'products', 'aep', 'server', 'src', 'main', 'resources', 'openapi.yaml'),
};

const requiredPaths = {
  dataCloud: ['/api/v1/capabilities', '/api/v1/capabilities/schema'],
  aep: [
    '/health',
    '/ready',
    '/api/v1/events',
    '/api/v1/agents',
    '/api/v1/agents/{agentId}/execute',
    '/api/v1/runs',
  ],
};

const violations = [];

for (const [name, fullPath] of Object.entries(files)) {
  if (!existsSync(fullPath)) {
    violations.push(`Missing OpenAPI file: ${name} -> ${relativePath(fullPath)}`);
    continue;
  }

  const content = readFileSync(fullPath, 'utf8');
  if (!/\bopenapi:\s*["']?3\./.test(content)) {
    violations.push(`Invalid OpenAPI header in ${relativePath(fullPath)}`);
  }
}

const dataCloudSpec = safeRead(files.dataCloudApi);
const dataCloudDocsSpec = safeRead(files.dataCloudDocsGenerated);
const aepContractsSpec = safeRead(files.aepContracts);
const aepServerSpec = safeRead(files.aepServer);

if (dataCloudSpec != null) {
  assertPathSet('Data Cloud API', files.dataCloudApi, dataCloudSpec, requiredPaths.dataCloud);
}
if (dataCloudDocsSpec != null) {
  assertPathSet(
    'Data Cloud generated docs',
    files.dataCloudDocsGenerated,
    dataCloudDocsSpec,
    requiredPaths.dataCloud,
  );
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
