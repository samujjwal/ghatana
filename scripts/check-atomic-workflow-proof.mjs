#!/usr/bin/env node

import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import path from 'node:path';

const repoRoot = process.cwd();
const registryPath = path.join(
  repoRoot,
  'products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/RouteSecurityRegistry.java',
);
const invariantTestPath = path.join(
  repoRoot,
  'products/data-cloud/delivery/launcher/src/test/java/com/ghatana/datacloud/launcher/http/RouteSecurityRegistryInvariantTest.java',
);
const evidenceDir = path.join(repoRoot, '.kernel/evidence');
const evidencePath = path.join(evidenceDir, 'atomic-workflow-posture.json');

const routePattern = /route\(map,\s*"([A-Z]+)",\s*"([^"]+)",\s*EndpointSensitivity\.([A-Z_]+),\s*(true|false),\s*(true|false),\s*(true|false),\s*(true|false),/g;
const mutatingMethods = new Set(['POST', 'PUT', 'PATCH', 'DELETE']);

const violations = [];

function readRequiredFile(filePath) {
  try {
    return readFileSync(filePath, 'utf8');
  } catch (error) {
    violations.push(`Missing required file: ${path.relative(repoRoot, filePath)}`);
    return '';
  }
}

const registrySource = readRequiredFile(registryPath);
const invariantSource = readRequiredFile(invariantTestPath);

const mutatingRoutes = [];
const criticalMutatingRoutes = [];

for (const match of registrySource.matchAll(routePattern)) {
  const method = match[1];
  const routePath = match[2];
  const sensitivity = match[3];
  const requiresPolicy = match[6] === 'true';
  const requiresBlockingAudit = match[7] === 'true';

  if (!mutatingMethods.has(method)) {
    continue;
  }

  const route = {
    method,
    path: routePath,
    sensitivity,
    requiresPolicy,
    requiresBlockingAudit,
  };

  mutatingRoutes.push(route);

  if (sensitivity === 'CRITICAL') {
    criticalMutatingRoutes.push(route);
    if (!requiresPolicy || !requiresBlockingAudit) {
      violations.push(
        `Critical route ${method} ${routePath} must require policy and blocking audit`,
      );
    }
  }
}

if (mutatingRoutes.length === 0) {
  violations.push('No mutating routes were parsed from RouteSecurityRegistry');
}
if (criticalMutatingRoutes.length === 0) {
  violations.push('No critical mutating routes were parsed from RouteSecurityRegistry');
}

for (const requiredToken of [
  'criticalRoutesRequirePolicyAndBlockingAudit',
  'registryOperationSetExactlyMatchesRouterOperationSet',
  'registryChecksumMatchesRouterRoutes',
]) {
  if (!invariantSource.includes(requiredToken)) {
    violations.push(`RouteSecurityRegistry invariant coverage missing token: ${requiredToken}`);
  }
}

const hasRollbackRoute = mutatingRoutes.some((route) => route.path.includes('/rollback'));
const hasRetryRoute = mutatingRoutes.some((route) => route.path.includes('/retry'));
if (!hasRollbackRoute) {
  violations.push('Expected at least one rollback mutation route in RouteSecurityRegistry');
}
if (!hasRetryRoute) {
  violations.push('Expected at least one retry mutation route in RouteSecurityRegistry');
}

const evidence = {
  generatedAt: new Date().toISOString(),
  summary: {
    mutatingRouteCount: mutatingRoutes.length,
    criticalMutatingRouteCount: criticalMutatingRoutes.length,
    hasRollbackRoute,
    hasRetryRoute,
    violationCount: violations.length,
  },
  criticalMutatingRoutes: criticalMutatingRoutes.map((route) => ({
    operation: `${route.method} ${route.path}`,
    requiresPolicy: route.requiresPolicy,
    requiresBlockingAudit: route.requiresBlockingAudit,
  })),
};

mkdirSync(evidenceDir, { recursive: true });
writeFileSync(evidencePath, `${JSON.stringify(evidence, null, 2)}\n`, 'utf8');

if (violations.length > 0) {
  console.error('Atomic workflow proof check failed:\n');
  for (const violation of violations) {
    console.error(`- ${violation}`);
  }
  console.error(`\nEvidence written to ${path.relative(repoRoot, evidencePath)}`);
  process.exit(1);
}

console.log(`Atomic workflow proof check passed. Evidence: ${path.relative(repoRoot, evidencePath)}`);
