#!/usr/bin/env node
/**
 * Route Manifest Generator
 *
 * Generates canonical route manifest from DataCloudRouterBuilder runtime routes reconciled with
 * RouteSecurityRegistry metadata, then emits UI RuntimeTruth posture.
 */

import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const PROJECT_ROOT = path.resolve(__dirname, '..');

function parseJavaRegistry(registryPath) {
  const content = fs.readFileSync(registryPath, 'utf-8');
  const routes = [];
  const pattern = /route\(map,\s*"([A-Z]+)",\s*"([^"]+)",\s*EndpointSensitivity\.([A-Z_]+),\s*(true|false),\s*(true|false),\s*(true|false),\s*(true|false),\s*DataCloudSecurityFilter\.AccessLevel\.([A-Z_]+),\s*(true|false),\s*"([^"]*)",\s*"([^"]*)",\s*"([^"]*)"\);/g;

  let match;
  while ((match = pattern.exec(content)) !== null) {
    const [, method, routePath, sensitivity, requiresAuth, requiresTenant, requiresPolicy,
      requiresBlockingAudit, requiredAccess, idempotent, rawSurface, legacyStatus, description] = match;
    routes.push({
      method,
      path: routePath,
      sensitivity,
      requiresAuth: requiresAuth === 'true',
      requiresTenant: requiresTenant === 'true',
      requiresPolicy: requiresPolicy === 'true',
      requiresBlockingAudit: requiresBlockingAudit === 'true',
      requiredAccess,
      idempotent: idempotent === 'true',
      description: description.replace(/\\"/g, '"'),
      legacyStatus,
      runtimeTruthSurface: mapRuntimeTruthSurface(rawSurface),
      category: categorizeRoute(routePath)
    });
  }
  return routes;
}

function parseRouterRoutes(routerPath) {
  const content = fs.readFileSync(routerPath, 'utf-8');
  const routes = [];
  const pattern = /\.with\(HttpMethod\.([A-Z]+),\s*"([^"]+)"/g;
  let match;
  while ((match = pattern.exec(content)) !== null) {
    routes.push({
      method: match[1],
      path: match[2].replace(/:([A-Za-z_][A-Za-z0-9_]*)/g, '{$1}')
    });
  }
  return Array.from(new Map(routes.map(route => [`${route.method} ${route.path}`, route])).values());
}

function assertRouterRegistryParity(routerRoutes, registryRoutes) {
  const routerKeys = new Set(routerRoutes.map(route => `${route.method} ${route.path}`));
  const registryKeys = new Set(registryRoutes.map(route => `${route.method} ${route.path}`));
  const routerOnly = [...routerKeys].filter(key => !registryKeys.has(key)).sort();
  const registryOnly = registryRoutes
    .filter(route => !routerKeys.has(`${route.method} ${route.path}`) && route.legacyStatus !== 'compatibility-only')
    .map(route => `${route.method} ${route.path}`)
    .sort();

  if (routerOnly.length || registryOnly.length) {
    const details = [
      ...routerOnly.map(route => `router-only: ${route}`),
      ...registryOnly.map(route => `registry-only: ${route}`)
    ].join('\n');
    throw new Error(`Route registry/runtime parity failed:\n${details}`);
  }
}

function mapRuntimeTruthSurface(surface) {
  const normalized = (surface || '').toLowerCase();
  if (normalized === 'none') return 'HIDDEN';
  if (normalized === 'developer' || normalized === 'developer_only') return 'DEVELOPER_ONLY';
  if (normalized === 'experimental') return 'EXPERIMENTAL';
  return 'VISIBLE';
}

function categorizeRoute(routePath) {
  if (routePath.startsWith('/api/v1/action/')) return 'Action Plane';
  if (routePath.startsWith('/api/v1/events')) return 'Event Store';
  if (routePath.startsWith('/api/v1/context')) return 'Context Plane';
  if (routePath.startsWith('/api/v1/connectors') || routePath.startsWith('/data-fabric/')) return 'Integration';
  if (routePath.startsWith('/health') || routePath.startsWith('/ready') || routePath.startsWith('/live') || routePath === '/info' || routePath === '/metrics') return 'Health';
  return 'Admin';
}

function generateManifest(routes, generationTime) {
  const versionDate = generationTime.slice(0, 10);
  return {
    version: `generated-${versionDate}`,
    lastUpdated: generationTime,
    generatedFrom: 'DataCloudRouterBuilder.java + RouteSecurityRegistry.java',
    routes: routes.sort((a, b) => a.category.localeCompare(b.category)
      || a.method.localeCompare(b.method)
      || a.path.localeCompare(b.path))
  };
}

function operationId(route) {
  return `${route.method.toLowerCase()}${route.path.split('/').map(p => p.charAt(0).toUpperCase() + p.slice(1).replace(/[{}-]/g, '')).join('')}`;
}

function generateUITruth(manifest, generationTime) {
  const routeEntries = manifest.routes.map(route => `  {
    method: '${route.method}',
    path: '${route.path}',
    operationId: '${operationId(route)}',
    sensitivity: '${route.sensitivity}',
    runtimeTruthSurface: '${route.runtimeTruthSurface}',
    requiresAuth: ${route.requiresAuth},
    requiresTenant: ${route.requiresTenant},
    requiresPolicy: ${route.requiresPolicy},
    requiresBlockingAudit: ${route.requiresBlockingAudit},
    requiredAccess: '${route.requiredAccess}',
    legacyStatus: '${route.legacyStatus}',
    idempotent: ${route.idempotent},
    description: '${route.description.replace(/'/g, "\\'")}'
  }`).join(',\n');

  return `/**
 * GENERATED FILE - DO NOT EDIT MANUALLY
 *
 * Generated from canonical route manifest by generate-route-manifest.mjs
 * To regenerate, run: npm run generate:route-manifest
 *
 * Generated at: ${generationTime}
 * Source: DataCloudRouterBuilder.java + RouteSecurityRegistry.java
 *
 * DC-P0-03: Runtime truth for UI feature gating and route visibility
 */

export interface RuntimeRoute {
  method: string;
  path: string;
  operationId: string;
  sensitivity: 'PUBLIC' | 'INTERNAL' | 'SENSITIVE' | 'CRITICAL';
  runtimeTruthSurface: 'VISIBLE' | 'HIDDEN' | 'DEVELOPER_ONLY' | 'EXPERIMENTAL';
  requiresAuth: boolean;
  requiresTenant: boolean;
  requiresPolicy: boolean;
  requiresBlockingAudit: boolean;
  requiredAccess: 'NONE' | 'VIEWER' | 'AUDITOR' | 'OPERATOR' | 'ADMIN';
  legacyStatus: 'active' | 'deprecated' | 'compatibility-only';
  idempotent: boolean;
  description: string;
}

export const RUNTIME_TRUTH_POSTURED: RuntimeRoute[] = [
${routeEntries}
];

export function findRoute(method: string, pathPattern: string): RuntimeRoute | undefined {
  const methodUpper = method.toUpperCase();
  return RUNTIME_TRUTH_POSTURED.find(route => {
    if (route.method !== methodUpper) return false;
    const routePattern = route.path.replace(/{[^}]+}/g, '[^/]+');
    const regex = new RegExp('^' + routePattern + '$');
    return regex.test(pathPattern) || route.path === pathPattern;
  });
}

export function getRoutesBySensitivity(sensitivity: 'PUBLIC' | 'INTERNAL' | 'SENSITIVE' | 'CRITICAL'): RuntimeRoute[] {
  return RUNTIME_TRUTH_POSTURED.filter(r => r.sensitivity === sensitivity);
}

export function requiresPolicy(method: string, path: string): boolean {
  const route = findRoute(method, path);
  return route?.requiresPolicy ?? false;
}
`;
}

function resolvePath(inputPath, fallback) {
  const candidate = inputPath || fallback;
  return path.isAbsolute(candidate) ? candidate : path.join(PROJECT_ROOT, candidate);
}

function validateManifest(manifest) {
  if (!manifest.routes || manifest.routes.length === 0) throw new Error('Manifest generation produced zero routes');
  for (const route of manifest.routes) {
    for (const field of ['method', 'path', 'sensitivity', 'requiredAccess', 'legacyStatus']) {
      if (!route[field]) throw new Error(`Invalid route entry found: ${JSON.stringify(route)}`);
    }
  }
}

function readIfExists(filePath) {
  return fs.existsSync(filePath) ? fs.readFileSync(filePath, 'utf-8') : '';
}

async function main() {
  const args = process.argv.slice(2);
  const options = {};
  const checkMode = args.includes('--check');
  for (let i = 0; i < args.length; i += 1) {
    if (!args[i].startsWith('--') || args[i] === '--check') continue;
    const key = args[i].replace(/^--/, '');
    const value = args[i + 1] && !args[i + 1].startsWith('--') ? args[i + 1] : '';
    options[key] = value;
  }

  const javaRegistryPath = resolvePath(options['java-registry-path'], 'delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/RouteSecurityRegistry.java');
  const routerPath = resolvePath(options['router-path'], 'delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/DataCloudRouterBuilder.java');
  const outputManifest = resolvePath(options['output-manifest'], 'config/route-manifest.json');
  const outputUITruth = resolvePath(options['output-ui-truth'], 'delivery/ui/src/lib/routing/RuntimeTruthPosture.generated.ts');

  console.log('[Route Manifest Generator]');
  console.log(`  Java registry: ${javaRegistryPath}`);
  console.log(`  Router source: ${routerPath}`);
  console.log(`  Output manifest: ${outputManifest}`);
  console.log(`  Output UI truth: ${outputUITruth}`);
  console.log(`  Mode: ${checkMode ? 'check' : 'write'}`);

  for (const file of [javaRegistryPath, routerPath]) {
    if (!fs.existsSync(file)) {
      console.error(`✗ Error: file not found at ${file}`);
      process.exit(1);
    }
  }

  const routes = parseJavaRegistry(javaRegistryPath);
  const routerRoutes = parseRouterRoutes(routerPath);
  console.log(`  ✓ Found ${routes.length} registered routes`);
  assertRouterRegistryParity(routerRoutes, routes);
  console.log(`  ✓ Runtime/router parity verified (${routerRoutes.length} routes)`);

  const generationTime = new Date(Math.max(fs.statSync(javaRegistryPath).mtimeMs, fs.statSync(routerPath).mtimeMs)).toISOString();
  const manifest = generateManifest(routes, generationTime);
  validateManifest(manifest);

  const manifestJson = JSON.stringify(manifest, null, 2) + '\n';
  const uiTruth = generateUITruth(manifest, generationTime);

  if (checkMode) {
    const manifestMatches = readIfExists(outputManifest) === manifestJson;
    const uiMatches = readIfExists(outputUITruth) === uiTruth;
    if (!manifestMatches || !uiMatches) {
      if (!manifestMatches) console.error(`✗ Drift detected: ${outputManifest} is out of date`);
      if (!uiMatches) console.error(`✗ Drift detected: ${outputUITruth} is out of date`);
      process.exit(1);
    }
    console.log('  ✓ Route manifest artifacts are up to date');
    return;
  }

  fs.mkdirSync(path.dirname(outputManifest), { recursive: true });
  fs.writeFileSync(outputManifest, manifestJson);
  fs.mkdirSync(path.dirname(outputUITruth), { recursive: true });
  fs.writeFileSync(outputUITruth, uiTruth);
  console.log('\n✓ Route manifest generation complete');
}

main().catch(error => {
  console.error('✗ Error:', error.message);
  process.exit(1);
});
