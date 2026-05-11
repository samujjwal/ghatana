#!/usr/bin/env node
/**
 * DMOS operations documentation guard.
 *
 * @doc.type tooling
 * @doc.purpose Ensure required production runbooks and environment documentation exist
 * @doc.layer infrastructure
 */

import { existsSync, readFileSync } from 'node:fs';
import { join, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const repoRoot = resolve(fileURLToPath(new URL('..', import.meta.url)));
const docsRoot = join(repoRoot, 'products/digital-marketing/docs');

const requiredRunbooks = [
  'launch-failure.md',
  'connector-outage.md',
  'kill-switch.md',
  'data-freshness-incident.md',
  'privacy-incident.md',
  'dsar-request.md',
  'rollback.md',
  'release-gate-failure.md',
];

const requiredEnvVars = [
  'DMOS_ENV',
  'DMOS_OPA_URL',
  'OTEL_EXPORTER_OTLP_ENDPOINT',
  'DMOS_PII_HMAC_KEY',
  'DMOS_CONTACT_ENCRYPTION_KEY',
  'DMOS_KERNEL_AGENT_ENDPOINT',
  'DMOS_GOVERNED_AI_ENABLED',
  'GOOGLE_ADS_CLIENT_ID',
  'GOOGLE_ADS_CLIENT_SECRET',
  'GOOGLE_ADS_DEVELOPER_TOKEN',
  'VITE_API_BASE_URL',
];

const violations = [];

for (const runbook of requiredRunbooks) {
  const path = join(docsRoot, 'runbooks', runbook);
  if (!existsSync(path)) {
    violations.push(`missing runbook ${relative(repoRoot, path).replace(/\\/g, '/')}`);
    continue;
  }
  const content = readFileSync(path, 'utf8');
  for (const heading of ['## Trigger', '## Immediate Containment', '## Verification', '## Recovery']) {
    if (!content.includes(heading)) {
      violations.push(`${relative(repoRoot, path).replace(/\\/g, '/')}: missing ${heading}`);
    }
  }
}

const operations = readFileSync(join(docsRoot, 'canonical/05-OPERATIONS.md'), 'utf8');
for (const runbook of requiredRunbooks) {
  if (!operations.includes(`docs/runbooks/${runbook}`)) {
    violations.push(`canonical operations doc does not link docs/runbooks/${runbook}`);
  }
}

const envDoc = readFileSync(join(docsRoot, 'ENVIRONMENT_VARIABLES.md'), 'utf8');
const deployDoc = readFileSync(join(docsRoot, 'DEPLOYMENT.md'), 'utf8');
for (const envVar of requiredEnvVars) {
  if (!envDoc.includes(envVar)) {
    violations.push(`ENVIRONMENT_VARIABLES.md missing ${envVar}`);
  }
  if (!deployDoc.includes(envVar)) {
    violations.push(`DEPLOYMENT.md missing ${envVar}`);
  }
}

if (violations.length > 0) {
  console.error('DMOS operations documentation guard failed:');
  for (const violation of violations) {
    console.error(`- ${violation}`);
  }
  process.exit(1);
}

console.log('DMOS operations documentation guard passed.');
