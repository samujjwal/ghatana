#!/usr/bin/env node
/**
 * Route Manifest Generator
 * 
 * Generates canonical route manifest from Data Cloud backend and produces:
 * 1. Canonical route manifest (JSON)
 * 2. UI RuntimeTruthPosture.generated.ts
 * 3. OpenAPI validation report
 * 
 * DC-P0-03: Single source of truth for route metadata
 * 
 * Usage:
 *   node generate-route-manifest.mjs \
 *     --output-manifest config/route-manifest.json \
 *     --output-ui-truth products/data-cloud/delivery/ui/src/lib/routing/RuntimeTruthPosture.generated.ts \
 *     --manifest-schema config/route-manifest-schema.json \
 *     --java-registry-path products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/RouteSecurityRegistry.java
 */

import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const PROJECT_ROOT = path.resolve(__dirname, '..');

/**
 * Parse RouteSecurityRegistry.java to extract route definitions.
 * 
 * This is a simplified parser that looks for route() method calls
 * and extracts the metadata.
 */
function parseJavaRegistry(registryPath) {
  const content = fs.readFileSync(registryPath, 'utf-8');
  const routes = [];

  const blockPattern = /map\.put\(\s*"([A-Z]+)\s+([^\"]+)"\s*,\s*RouteSecurityMetadata\.builder\(\)([\s\S]*?)\.build\(\)\s*\)\s*;/g;

  let block;
  while ((block = blockPattern.exec(content)) !== null) {
    const method = block[1];
    const routePath = block[2];
    const body = block[3];

    const sensitivityMatch = body.match(/\.sensitivity\(EndpointSensitivity\.([A-Z_]+)\)/);
    const sensitivity = sensitivityMatch ? sensitivityMatch[1] : 'INTERNAL';

    const requiresAuth = body.includes('.requiresAuth(true)');
    const requiresTenant = body.includes('.requiresTenant(true)');
    const requiresPolicy = body.includes('.requiresPolicy(true)');
    const idempotent = body.includes('.idempotent(true)');

    const descriptionMatch = body.match(/\.description\("([\s\S]*?)"\)/);
    const description = descriptionMatch ? descriptionMatch[1].replace(/\\"/g, '"') : `${method} ${routePath}`;

    const surfaceMatch = body.match(/\.runtimeTruthSurface\("([^"]+)"\)/);
    const rawSurface = surfaceMatch ? surfaceMatch[1] : 'none';

    routes.push({
      method,
      path: routePath,
      sensitivity,
      requiresAuth,
      requiresTenant,
      requiresPolicy,
      idempotent,
      description,
      runtimeTruthSurface: mapRuntimeTruthSurface(rawSurface),
      category: categorizeRoute(routePath)
    });
  }
  return routes;
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
  if (routePath.startsWith('/health') || routePath.startsWith('/ready') || routePath.startsWith('/live')) return 'Health';
  return 'Admin';
}

/**
 * Generate canonical route manifest
 */
function generateManifest(routes, generationTime) {
  const versionDate = generationTime.slice(0, 10);
  return {
    version: `generated-${versionDate}`,
    lastUpdated: generationTime,
    generatedFrom: 'RouteSecurityRegistry.java',
    routes: routes.sort((a, b) => {
      // Sort by category, then method, then path
      if (a.category !== b.category) {
        return a.category.localeCompare(b.category);
      }
      if (a.method !== b.method) {
        return a.method.localeCompare(b.method);
      }
      return a.path.localeCompare(b.path);
    })
  };
}

/**
 * Generate UI RuntimeTruthPosture.generated.ts from manifest
 */
function generateUITruth(manifest, generationTime) {
  const timestamp = generationTime;
  
  const routeEntries = manifest.routes
    .map(route => {
      const operationId = `${route.method.toLowerCase()}${route.path
        .split('/')
        .map(p => p.charAt(0).toUpperCase() + p.slice(1))
        .join('')}`;
      
      return `  {
    method: '${route.method}',
    path: '${route.path}',
    operationId: '${operationId}',
    sensitivity: '${route.sensitivity}',
    runtimeTruthSurface: '${route.runtimeTruthSurface}',
    requiresAuth: ${route.requiresAuth},
    requiresTenant: ${route.requiresTenant},
    requiresPolicy: ${route.requiresPolicy},
    idempotent: ${route.idempotent},
    description: '${route.description.replace(/'/g, "\\'")}'
  }`;
    })
    .join(',\n');

  return `/**
 * GENERATED FILE - DO NOT EDIT MANUALLY
 * 
 * Generated from canonical route manifest by generate-route-manifest.mjs
 * To regenerate, run: npm run generate:route-manifest
 * 
 * Generated at: ${timestamp}
 * Source: RouteSecurityRegistry.java
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
  idempotent: boolean;
  description: string;
}

/**
 * Canonical runtime truth for Data Cloud HTTP routes.
 * Used for:
 * - UI feature gating (show/hide actions based on sensitivity)
 * - Client SDK generation (route availability per profile)
 * - Documentation generation (API matrix)
 * - CI/CD validation (drift detection)
 */
export const RUNTIME_TRUTH_POSTURED: RuntimeRoute[] = [
${routeEntries}
];

/**
 * Get route by method and path pattern
 */
export function findRoute(method: string, pathPattern: string): RuntimeRoute | undefined {
  const methodUpper = method.toUpperCase();

  return RUNTIME_TRUTH_POSTURED.find(route => {
    if (route.method !== methodUpper) return false;

    const routePattern = route.path.replace(/{[^}]+}/g, '[^/]+');
    const regex = new RegExp('^' + routePattern + '$');
    return regex.test(pathPattern) || route.path === pathPattern;
  });
}

/**
 * Get all routes for a sensitivity level
 */
export function getRoutesBySensitivity(sensitivity: 'PUBLIC' | 'INTERNAL' | 'SENSITIVE' | 'CRITICAL'): RuntimeRoute[] {
  return RUNTIME_TRUTH_POSTURED.filter(r => r.sensitivity === sensitivity);
}

/**
 * Check if route requires policy evaluation
 */
export function requiresPolicy(method: string, path: string): boolean {
  const route = findRoute(method, path);
  return route?.requiresPolicy ?? false;
}
`;
}

/**
 * Main entry point
 */
function resolvePath(inputPath, fallback) {
  const candidate = inputPath || fallback;
  return path.isAbsolute(candidate) ? candidate : path.join(PROJECT_ROOT, candidate);
}

function validateManifest(manifest) {
  if (!manifest.routes || manifest.routes.length === 0) {
    throw new Error('Manifest generation produced zero routes');
  }
  for (const route of manifest.routes) {
    if (!route.method || !route.path || !route.sensitivity) {
      throw new Error(`Invalid route entry found: ${JSON.stringify(route)}`);
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

  const javaRegistryPath = resolvePath(
    options['java-registry-path'],
    'delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/RouteSecurityRegistry.java'
  );
  const outputManifest = resolvePath(options['output-manifest'], 'config/route-manifest.json');
  const outputUITruth = resolvePath(
    options['output-ui-truth'],
    'delivery/ui/src/lib/routing/RuntimeTruthPosture.generated.ts'
  );
  
  console.log('[Route Manifest Generator]');
  console.log(`  Java registry: ${javaRegistryPath}`);
  console.log(`  Output manifest: ${outputManifest}`);
  console.log(`  Output UI truth: ${outputUITruth}`);
  console.log(`  Mode: ${checkMode ? 'check' : 'write'}`);
  
  // Parse Java registry
  if (!fs.existsSync(javaRegistryPath)) {
    console.error(`✗ Error: Registry not found at ${javaRegistryPath}`);
    process.exit(1);
  }
  
  console.log('  Parsing Java registry...');
  const routes = parseJavaRegistry(javaRegistryPath);
  console.log(`  ✓ Found ${routes.length} routes`);

  const generationTime = new Date(fs.statSync(javaRegistryPath).mtimeMs).toISOString();
  
  // Generate manifest
  console.log('  Generating canonical manifest...');
  const manifest = generateManifest(routes, generationTime);
  validateManifest(manifest);

  const manifestJson = JSON.stringify(manifest, null, 2) + '\n';
  const uiTruth = generateUITruth(manifest, generationTime);

  if (checkMode) {
    const currentManifest = readIfExists(outputManifest);
    const currentUiTruth = readIfExists(outputUITruth);

    const manifestMatches = currentManifest === manifestJson;
    const uiMatches = currentUiTruth === uiTruth;

    if (!manifestMatches || !uiMatches) {
      if (!manifestMatches) {
        console.error(`✗ Drift detected: ${outputManifest} is out of date`);
      }
      if (!uiMatches) {
        console.error(`✗ Drift detected: ${outputUITruth} is out of date`);
      }
      process.exit(1);
    }

    console.log('  ✓ Route manifest artifacts are up to date');
    return;
  }
  
  // Write manifest
  const manifestDir = path.dirname(outputManifest);
  if (!fs.existsSync(manifestDir)) {
    fs.mkdirSync(manifestDir, { recursive: true });
  }
  fs.writeFileSync(outputManifest, manifestJson);
  console.log(`  ✓ Manifest written to ${outputManifest}`);
  
  console.log('  Generating UI runtime truth...');
  
  // Write UI truth
  const uiDir = path.dirname(outputUITruth);
  if (!fs.existsSync(uiDir)) {
    fs.mkdirSync(uiDir, { recursive: true });
  }
  fs.writeFileSync(outputUITruth, uiTruth);
  console.log(`  ✓ UI truth written to ${outputUITruth}`);
  
  console.log('\n✓ Route manifest generation complete');
  console.log(`  Total routes: ${manifest.routes.length}`);
  console.log(`  CRITICAL: ${manifest.routes.filter(r => r.sensitivity === 'CRITICAL').length}`);
  console.log(`  SENSITIVE: ${manifest.routes.filter(r => r.sensitivity === 'SENSITIVE').length}`);
  console.log(`  INTERNAL: ${manifest.routes.filter(r => r.sensitivity === 'INTERNAL').length}`);
  console.log(`  PUBLIC: ${manifest.routes.filter(r => r.sensitivity === 'PUBLIC').length}`);
}

main().catch(error => {
  console.error('✗ Error:', error.message);
  process.exit(1);
});
