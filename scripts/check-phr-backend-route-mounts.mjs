#!/usr/bin/env node

/**
 * PHR backend route mount guard.
 *
 * Verifies that PhrHttpServer does not reintroduce legacy non-versioned API
 * mounts or hidden role-specific route families. The canonical runtime API
 * surface is /api/v1, with explicit exceptions for health checks and FHIR.
 */

import { existsSync, readFileSync } from 'fs';
import { resolve } from 'path';

const ROOT = process.cwd();
const SERVER_PATH = resolve(ROOT, 'products/phr/src/main/java/com/ghatana/phr/api/PhrHttpServer.java');
const MOUNT_TABLE_PATH = resolve(ROOT, 'products/phr/src/main/java/com/ghatana/phr/api/PhrRouteContractMountTable.java');
const CONTRACT_PATH = resolve(ROOT, 'products/phr/config/phr-route-contract.json');
const OPENAPI_PATH = resolve(ROOT, 'products/phr/docs/openapi.yaml');

const SYSTEM_ROUTE_PREFIXES = ['/fhir', '/health', '/ready'];
const LEGACY_ROUTE_PREFIXES = [
  '/dashboard',
  '/records',
  '/patients',
  '/consents',
  '/clinical',
  '/emergency',
  '/release-readiness',
  '/appointments',
  '/admin',
  '/phr/billing',
  '/audit',
  '/auth',
  '/provider',
  '/caregiver',
  '/fchv',
  '/mobile',
  '/notifications',
  '/profile',
];

function readRequired(path, label) {
  if (!existsSync(path)) {
    console.error(`FAIL [phr-backend-route-mounts]: ${label} not found at ${path}`);
    process.exit(1);
  }
  return readFileSync(path, 'utf-8');
}

function normalizeRoute(path) {
  if (path.length > 1 && path.endsWith('/*')) {
    return path.slice(0, -2);
  }
  if (path.length > 1 && path.endsWith('/')) {
    return path.slice(0, -1);
  }
  return path;
}

function startsWithRoutePrefix(path, prefix) {
  return path === prefix || path.startsWith(`${prefix}/`);
}

function hiddenFamilyPrefix(apiEndpoint) {
  const parts = normalizeRoute(apiEndpoint).split('/').filter(Boolean);
  if (parts.length < 3 || parts[0] !== 'api' || parts[1] !== 'v1') {
    return null;
  }
  return `/api/v1/${parts[2]}`;
}

const serverSource = readRequired(SERVER_PATH, 'PhrHttpServer.java');
const mountTableSource = readRequired(MOUNT_TABLE_PATH, 'PhrRouteContractMountTable.java');
const openApiSource = readRequired(OPENAPI_PATH, 'openapi.yaml');

let contract;
try {
  contract = JSON.parse(readRequired(CONTRACT_PATH, 'phr-route-contract.json'));
} catch (error) {
  console.error(`FAIL [phr-backend-route-mounts]: phr-route-contract.json is invalid JSON: ${error.message}`);
  process.exit(1);
}

const infrastructureRoutes = [...serverSource.matchAll(/\.with\(\s*"([^"]+)"/g)]
  .map((match) => normalizeRoute(match[1]));
const contractMountRoutes = [...mountTableSource.matchAll(/new MountSpec\(\s*"([^"]+)"/g)]
  .map((match) => normalizeRoute(match[1]));
const mountedRoutes = [...new Set([...infrastructureRoutes, ...contractMountRoutes])]
  .sort((a, b) => a.localeCompare(b));

const failures = [];

if (contractMountRoutes.length === 0) {
  failures.push('PhrRouteContractMountTable.java has no statically discoverable contract mount specs');
}

if (!serverSource.includes('PhrRouteContractMountTable.loadStableMounts()')) {
  failures.push('PhrHttpServer.java must load stable product routes from PhrRouteContractMountTable');
}

const directProductMountPattern = /\.with\(\s*"\/api\/v1\/(dashboard|records|consents|clinical|emergency|release-readiness|appointments|admin|audit|notifications|profile|hie|route-entitlements)(?:\/|\*|"|\?)/g;
for (const match of serverSource.matchAll(directProductMountPattern)) {
  failures.push(`PhrHttpServer.java must not hardcode product route mount '${match[0]}'`);
}

for (const route of mountedRoutes) {
  const isSystemRoute = SYSTEM_ROUTE_PREFIXES.some((prefix) => startsWithRoutePrefix(route, prefix));
  if (!isSystemRoute && !startsWithRoutePrefix(route, '/api/v1')) {
    failures.push(`${route}: production API mounts must use canonical /api/v1`);
  }

  for (const legacyPrefix of LEGACY_ROUTE_PREFIXES) {
    if (startsWithRoutePrefix(route, legacyPrefix)) {
      failures.push(`${route}: legacy non-versioned PHR route mount is forbidden`);
    }
  }
}

const hiddenApiPrefixes = new Set(
  (contract.routes ?? [])
    .filter((route) => route.stability === 'hidden')
    .map((route) => route.apiEndpoint)
    .filter((apiEndpoint) => typeof apiEndpoint === 'string' && apiEndpoint.trim() !== '')
    .map(hiddenFamilyPrefix)
    .filter((prefix) => prefix !== null)
);

for (const hiddenPrefix of hiddenApiPrefixes) {
  for (const route of mountedRoutes) {
    if (startsWithRoutePrefix(route, hiddenPrefix)) {
      failures.push(`${route}: hidden route family '${hiddenPrefix}' must not be mounted`);
    }
  }

  const openApiPathPrefix = hiddenPrefix.slice('/api/v1'.length);
  const pathDefinition = new RegExp(`^  ${openApiPathPrefix.replaceAll('/', '\\/')}(?:/|:)`, 'm');
  if (pathDefinition.test(openApiSource)) {
    failures.push(`${openApiPathPrefix}: hidden route family must not be documented in OpenAPI`);
  }
}

if (failures.length > 0) {
  console.error(`FAIL: ${failures.length} PHR backend route mount violation(s) found`);
  for (const failure of failures) {
    console.error(` - ${failure}`);
  }
  process.exit(1);
}

console.log(
  `PASS: PHR backend route mounts are canonical (${mountedRoutes.length} mounts, ${hiddenApiPrefixes.size} hidden families guarded)`
);
