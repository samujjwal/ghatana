#!/usr/bin/env node
/**
 * DMOS production wiring guard.
 *
 * @doc.type tooling
 * @doc.purpose Fail CI when production startup wiring permits dev/test adapters or silent fallbacks
 * @doc.layer infrastructure
 */

import { readFileSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const repoRoot = resolve(fileURLToPath(new URL('..', import.meta.url)));
const apiServerPath = join(
  repoRoot,
  'products/digital-marketing/dm-api/src/main/java/com/ghatana/digitalmarketing/api/DmosApiServer.java',
);
const source = readFileSync(apiServerPath, 'utf8');

const requiredSnippets = [
  {
    id: 'REQUIRE_OPA',
    snippet: '"DMOS_OPA_URL"',
    description: 'production startup must require OPA policy endpoint',
  },
  {
    id: 'FORBID_ALLOW_ALL_PROD',
    snippet: 'allow-all authorization is not permitted',
    description: 'production authorization must fail instead of using allowAll',
  },
  {
    id: 'FORBID_NOOP_AUDIT_PROD',
    snippet: 'no-op audit is not permitted',
    description: 'production audit must fail instead of using noOp emitter',
  },
  {
    id: 'REQUIRE_GOVERNED_AI_ENDPOINT',
    snippet: 'DMOS_KERNEL_AGENT_ENDPOINT is required for production governed AI',
    description: 'production governed AI must require a kernel endpoint',
  },
  {
    id: 'FORBID_GOVERNED_AI_DISABLE',
    snippet: 'DMOS_GOVERNED_AI_ENABLED=false is not allowed in production',
    description: 'production governed AI cannot be disabled',
  },
  {
    id: 'REQUIRE_OTEL',
    snippet: 'OTEL_EXPORTER_OTLP_ENDPOINT or OTEL_COLLECTOR_ENDPOINT is required for production telemetry',
    description: 'production telemetry endpoint must be explicit',
  },
];

const forbiddenPatterns = [
  {
    id: 'PROD_ALLOW_ALL_RETURN',
    pattern: /if\s*\(environment\.equals\(PRODUCTION\)\)\s*\{[^}]*BridgeAuthorizationService\.allowAll\(\)/,
    description: 'production branch returns allowAll authorization',
  },
  {
    id: 'PROD_NOOP_AUDIT_RETURN',
    pattern: /if\s*\(environment\.equals\(PRODUCTION\)\)\s*\{[^}]*BridgeAuditEmitter\.noOp\(\)/,
    description: 'production branch returns no-op audit emitter',
  },
  {
    id: 'PROD_LOCAL_GOVERNED_AI_ENDPOINT',
    pattern: /environment\.equals\(PRODUCTION\)[\s\S]{0,1200}?kernelEndpoint\s*=\s*"http:\/\/localhost:8080"/,
    description: 'production governed AI defaults to localhost',
  },
  {
    id: 'PROD_GOVERNED_AI_WARN_ONLY',
    pattern: /\[PRODUCTION\][^\n]+DMOS_GOVERNED_AI_ENABLED=false[^\n]+deterministic fallback/,
    description: 'production governed AI disablement is warning-only',
  },
];

const violations = [];

for (const requirement of requiredSnippets) {
  if (!source.includes(requirement.snippet)) {
    violations.push(`${requirement.id}: missing ${requirement.description}`);
  }
}

for (const rule of forbiddenPatterns) {
  if (rule.pattern.test(source)) {
    violations.push(`${rule.id}: ${rule.description}`);
  }
}

if (violations.length > 0) {
  console.error('DMOS production wiring guard failed:');
  for (const violation of violations) {
    console.error(`- ${violation}`);
  }
  process.exit(1);
}

console.log('DMOS production wiring guard passed.');
