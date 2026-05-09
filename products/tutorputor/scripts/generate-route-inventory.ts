/**
 * Route Inventory Generator
 *
 * Generates a route inventory from actual Fastify route registrations.
 * This script should be run to regenerate API_ROUTE_OWNERS.json
 * whenever routes are added, modified, or removed.
 *
 * @doc.type script
 * @doc.purpose Generate route inventory from Fastify routes
 * @doc.layer platform
 * @doc.pattern Script
 */

import Fastify from "fastify";
import { setupPlatform } from "../services/tutorputor-platform/src/setup.js";
import { readFileSync, writeFileSync } from "fs";
import { resolve } from "path";

interface RouteInfo {
  method: string;
  path: string;
  module: string;
  prefix?: string;
}

interface RouteInventory {
  runtimeModel: string;
  canonicalBackend: string;
  routes: Record<string, {
    backendOwner: string;
    apiContract: string;
    typedClient: string;
    method: string;
    module: string;
  }>;
}

/**
 * Extract routes from a Fastify instance
 */
function extractRoutes(app: Fastify.FastifyInstance): RouteInfo[] {
  const routes: RouteInfo[] = [];
  
  // Fastify stores routes in app.routes
  for (const route of (app as any).routes || []) {
    routes.push({
      method: route.method,
      path: route.path,
      module: route.schema?.['x-module'] || 'unknown',
      prefix: route.schema?.['x-prefix'],
    });
  }
  
  return routes;
}

/**
 * Normalize route path by removing duplicate segments and placeholders
 */
function normalizeRoutePath(path: string): string {
  // Remove ${prefix} placeholders
  let normalized = path.replace(/\$\{prefix\}/g, '');
  
  // Remove duplicate consecutive segments
  const segments = normalized.split('/').filter(Boolean);
  const normalizedSegments: string[] = [];
  
  for (let i = 0; i < segments.length; i++) {
    const seg = segments[i];
    // Skip if same as previous segment
    if (i > 0 && seg === segments[i - 1]) {
      continue;
    }
    normalizedSegments.push(seg);
  }
  
  return '/' + normalizedSegments.join('/');
}

/**
 * Generate route inventory
 */
async function generateRouteInventory(): Promise<void> {
  console.log('Starting route inventory generation...');
  
  // Create Fastify instance with all plugins
  const app = Fastify({ logger: false });
  await setupPlatform(app);
  
  // Wait for all routes to be registered
  await app.ready();
  
  // Extract routes
  const routes = extractRoutes(app);
  
  console.log(`Found ${routes.length} routes`);
  
  // Build inventory
  const inventory: RouteInventory = {
    runtimeModel: 'primary-platform-with-worker-runtimes',
    canonicalBackend: 'services/tutorputor-platform',
    routes: {},
  };
  
  for (const route of routes) {
    const normalizedPath = normalizeRoutePath(route.path);
    const key = `${route.method} ${normalizedPath}`;
    
    // Determine module from path if not specified
    let module = route.module;
    if (module === 'unknown') {
      module = inferModuleFromPath(normalizedPath);
    }
    
    // Determine backend owner from module
    const backendOwner = inferBackendOwner(module, normalizedPath);
    
    inventory.routes[key] = {
      backendOwner,
      apiContract: 'api/tutorputor-api.openapi.yaml',
      typedClient: 'apps/tutorputor-web/src/api/tutorputorClient.ts',
      method: route.method,
      module,
    };
  }
  
  // Write inventory to file
  const outputPath = resolve(process.cwd(), 'docs/architecture/API_ROUTE_OWNERS.json');
  writeFileSync(outputPath, JSON.stringify(inventory, null, 2));
  
  console.log(`Route inventory written to ${outputPath}`);
  
  // Validate for common issues
  validateInventory(inventory);
  
  await app.close();
}

/**
 * Infer module from route path
 */
function inferModuleFromPath(path: string): string {
  const segments = path.split('/').filter(Boolean);
  
  // Known module prefixes
  if (segments.includes('auth')) return 'auth';
  if (segments.includes('learning')) return 'learning';
  if (segments.includes('ai')) return 'ai';
  if (segments.includes('content')) {
    if (segments.includes('cms')) return 'content-cms';
    if (segments.includes('generation')) return 'content-generation';
    if (segments.includes('evaluation')) return 'content-evaluation';
    if (segments.includes('studio')) return 'content-studio';
    return 'content';
  }
  if (segments.includes('engagement')) {
    if (segments.includes('social')) return 'engagement-social';
    if (segments.includes('gamification')) return 'engagement-gamification';
    if (segments.includes('credentials')) return 'engagement-credentials';
    return 'engagement';
  }
  if (segments.includes('integration')) {
    if (segments.includes('lti')) return 'integration-lti';
    if (segments.includes('billing')) return 'integration-billing';
    if (segments.includes('marketplace')) return 'integration-marketplace';
    return 'integration';
  }
  if (segments.includes('kernel') || segments.includes('plugins')) return 'kernel-registry';
  if (segments.includes('search')) return 'search';
  if (segments.includes('simulation') || segments.includes('simulations')) return 'simulation';
  if (segments.includes('knowledge-base')) return 'knowledge-base';
  
  return 'unknown';
}

/**
 * Infer backend owner from module and path
 */
function inferBackendOwner(module: string, path: string): string {
  const moduleToOwner: Record<string, string> = {
    'auth': 'services/tutorputor-platform/src/modules/auth/index.ts',
    'learning': 'services/tutorputor-platform/src/modules/learning/routes.ts',
    'ai': 'services/tutorputor-platform/src/modules/ai/routes.ts',
    'content': 'services/tutorputor-platform/src/modules/content/routes.ts',
    'content-cms': 'services/tutorputor-platform/src/modules/content/cms/routes.ts',
    'content-generation': 'services/tutorputor-platform/src/modules/content/generation/routes.ts',
    'content-evaluation': 'services/tutorputor-platform/src/modules/content/evaluation/routes.ts',
    'content-studio': 'services/tutorputor-platform/src/modules/content/studio/routes.ts',
    'engagement': 'services/tutorputor-platform/src/modules/engagement/index.ts',
    'engagement-social': 'services/tutorputor-platform/src/modules/engagement/social/routes.ts',
    'engagement-gamification': 'services/tutorputor-platform/src/modules/engagement/gamification/routes.ts',
    'engagement-credentials': 'services/tutorputor-platform/src/modules/engagement/credentials/routes.ts',
    'integration': 'services/tutorputor-platform/src/modules/integration/index.ts',
    'integration-lti': 'services/tutorputor-platform/src/modules/integration/lti/routes.ts',
    'integration-billing': 'services/tutorputor-platform/src/modules/integration/billing/routes.ts',
    'integration-marketplace': 'services/tutorputor-platform/src/modules/integration/marketplace/routes.ts',
    'kernel-registry': 'services/tutorputor-platform/src/modules/kernel-registry/fastify-routes.ts',
    'search': 'services/tutorputor-platform/src/modules/search/index.ts',
    'simulation': 'services/tutorputor-platform/src/modules/simulation/index.ts',
    'knowledge-base': 'services/tutorputor-platform/src/modules/knowledge-base/routes.ts',
  };
  
  return moduleToOwner[module] || `services/tutorputor-platform/src/modules/${module}/routes.ts`;
}

/**
 * Validate inventory for common issues
 */
function validateInventory(inventory: RouteInventory): void {
  const errors: string[] = [];
  
  for (const [key, value] of Object.entries(inventory.routes)) {
    // Check for ${prefix} placeholders
    if (key.includes('${')) {
      errors.push(`Route ${key} contains placeholder`);
    }
    
    // Check for duplicate segments
    const segments = key.split('/').filter(Boolean);
    for (let i = 1; i < segments.length; i++) {
      if (segments[i] === segments[i - 1]) {
        errors.push(`Route ${key} has duplicate segment: ${segments[i]}`);
      }
    }
    
    // Check for missing required fields
    if (!value.backendOwner) {
      errors.push(`Route ${key} missing backendOwner`);
    }
    if (!value.apiContract) {
      errors.push(`Route ${key} missing apiContract`);
    }
    if (!value.typedClient) {
      errors.push(`Route ${key} missing typedClient`);
    }
    if (!value.module) {
      errors.push(`Route ${key} missing module`);
    }
  }
  
  if (errors.length > 0) {
    console.error('Validation errors:');
    errors.forEach(err => console.error(`  - ${err}`));
    throw new Error(`Route inventory validation failed with ${errors.length} errors`);
  }
  
  console.log('Route inventory validation passed');
}

// Run the generator
generateRouteInventory().catch(err => {
  console.error('Failed to generate route inventory:', err);
  process.exit(1);
});
