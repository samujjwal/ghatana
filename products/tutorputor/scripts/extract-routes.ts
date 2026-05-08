#!/usr/bin/env tsx
/**
 * Extract all routes from the platform and generate API_ROUTE_OWNERS.json
 *
 * This script scans all route files in the platform and extracts:
 * - Route path
 * - HTTP method
 * - Backend owner file
 * - Module prefix
 */

import { readFileSync, readdirSync, statSync } from 'fs';
import { join, relative } from 'path';
import { fileURLToPath } from 'url';

const __dirname = fileURLToPath(new URL('.', import.meta.url));
const PLATFORM_SRC = join(__dirname, '..', 'services', 'tutorputor-platform', 'src');
const OUTPUT_FILE = join(__dirname, '..', 'docs', 'architecture', 'API_ROUTE_OWNERS.json');

interface RouteInfo {
  path: string;
  method: string;
  module: string;
  backendOwner: string;
}

interface ModuleConfig {
  name: string;
  prefix: string;
  routeFile: string;
}

// Known module configurations from business-modules.ts and content-modules.ts
const MODULES: ModuleConfig[] = [
  { name: 'auth', prefix: '/api/v1/auth', routeFile: 'modules/auth/index.ts' },
  { name: 'user', prefix: '/api/v1/users', routeFile: 'modules/user/index.ts' },
  { name: 'learning', prefix: '/api/v1/learning', routeFile: 'modules/learning/routes.ts' },
  { name: 'ai', prefix: '/api/v1/ai', routeFile: 'modules/ai/routes.ts' },
  { name: 'collaboration', prefix: '/api/v1/collaboration', routeFile: 'modules/collaboration/routes.ts' },
  { name: 'integration', prefix: '/api/v1/integration', routeFile: 'modules/integration/index.ts' },
  { name: 'engagement', prefix: '/api/v1/engagement', routeFile: 'modules/engagement/index.ts' },
  { name: 'credentials', prefix: '/api/v1/credentials', routeFile: 'modules/credentials/index.ts' },
  { name: 'content', prefix: '/api', routeFile: 'modules/content/index.ts' },
  { name: 'simulation', prefix: '/api/v1/simulations', routeFile: 'modules/simulation/index.ts' },
  { name: 'search', prefix: '/api/v1/search', routeFile: 'modules/search/index.ts' },
  { name: 'kernel-registry', prefix: '/api/v1/kernel-registry', routeFile: 'modules/kernel-registry/fastify-routes.ts' },
  { name: 'content-needs', prefix: '/api/v1/content-needs', routeFile: 'modules/content-needs/routes.ts' },
  { name: 'auto-revision', prefix: '/api/v1/auto-revision', routeFile: 'modules/auto-revision/routes.ts' },
  { name: 'knowledge-base', prefix: '/api/v1/knowledge-base', routeFile: 'modules/knowledge-base/routes.ts' },
  { name: 'payments', prefix: '/api/v1/payments', routeFile: 'modules/payments/routes.ts' },
];

function extractRoutePatterns(content: string): RouteInfo[] {
  const routes: RouteInfo[] = [];
  
  // Match fastify.<method>(path, ...) patterns
  const methodPattern = /fastify\.(get|post|patch|put|delete)\s*\(\s*['"`]([^'"`]+)['"`]/g;
  let match;
  
  while ((match = methodPattern.exec(content)) !== null) {
    const [, method, path] = match;
    routes.push({ path, method: method.toUpperCase(), module: '', backendOwner: '' });
  }
  
  return routes;
}

function findRouteFiles(dir: string, baseDir: string = dir): string[] {
  const files: string[] = [];
  
  try {
    const entries = readdirSync(dir);
    
    for (const entry of entries) {
      const fullPath = join(dir, entry);
      const stat = statSync(fullPath);
      
      if (stat.isDirectory()) {
        // Skip __tests__ and __mocks__ directories
        if (!entry.startsWith('__')) {
          files.push(...findRouteFiles(fullPath, baseDir));
        }
      } else if (entry.endsWith('routes.ts') || entry.endsWith('routes.js')) {
        files.push(fullPath);
      }
    }
  } catch (error) {
    // Ignore permission errors
  }
  
  return files;
}

function scanModuleRoutes(module: ModuleConfig): RouteInfo[] {
  const routeFilePath = join(PLATFORM_SRC, module.routeFile);
  
  try {
    const content = readFileSync(routeFilePath, 'utf-8');
    const routePatterns = extractRoutePatterns(content);
    
    return routePatterns.map(route => ({
      ...route,
      module: module.name,
      path: module.prefix + route.path,
      backendOwner: `services/tutorputor-platform/src/${module.routeFile}`,
    }));
  } catch (error) {
    console.warn(`Could not read route file for module ${module.name}: ${routeFilePath}`);
    return [];
  }
}

function main() {
  console.log('Extracting routes from platform...');
  
  const allRoutes: RouteInfo[] = [];
  
  for (const module of MODULES) {
    console.log(`  Scanning module: ${module.name}`);
    const routes = scanModuleRoutes(module);
    allRoutes.push(...routes);
  }
  
  // Also scan for any additional route files not in known modules
  console.log('  Scanning for additional route files...');
  const routeFiles = findRouteFiles(PLATFORM_SRC);
  const knownRouteFiles = new Set(MODULES.map(m => join(PLATFORM_SRC, m.routeFile)));
  
  for (const routeFile of routeFiles) {
    if (!knownRouteFiles.has(routeFile)) {
      console.log(`    Found additional route file: ${relative(PLATFORM_SRC, routeFile)}`);
      try {
        const content = readFileSync(routeFile, 'utf-8');
        const routePatterns = extractRoutePatterns(content);
        
        for (const route of routePatterns) {
          allRoutes.push({
            ...route,
            module: 'unknown',
            path: route.path, // Use as-is since we don't know the prefix
            backendOwner: `services/tutorputor-platform/src/${relative(PLATFORM_SRC, routeFile)}`,
          });
        }
      } catch (error) {
        console.warn(`      Could not read: ${error}`);
      }
    }
  }
  
  // Build API_ROUTE_OWNERS.json structure
  const routeOwners: Record<string, any> = {
    runtimeModel: 'primary-platform-with-worker-runtimes',
    canonicalBackend: 'services/tutorputor-platform',
    routes: {},
  };
  
  for (const route of allRoutes) {
    const key = `${route.method} ${route.path}`;
    
    if (routeOwners.routes[key]) {
      // Duplicate route - skip or merge
      continue;
    }
    
    routeOwners.routes[key] = {
      backendOwner: route.backendOwner,
      apiContract: 'api/tutorputor-api.openapi.yaml',
      typedClient: 'apps/tutorputor-web/src/api/tutorputorClient.ts',
      method: route.method,
      module: route.module,
    };
  }
  
  // Sort routes by path
  const sortedRoutes: Record<string, any> = {};
  Object.keys(routeOwners.routes)
    .sort()
    .forEach(key => {
      sortedRoutes[key] = routeOwners.routes[key];
    });
  
  routeOwners.routes = sortedRoutes;
  
  // Write output
  const output = JSON.stringify(routeOwners, null, 2);
  writeFileSync(OUTPUT_FILE, output, 'utf-8');
  console.log(`\nWrote ${Object.keys(routeOwners.routes).length} routes to: ${OUTPUT_FILE}`);
  
  // Print sample
  console.log('\nSample routes:');
  const sampleKeys = Object.keys(routeOwners.routes).slice(0, 10);
  for (const key of sampleKeys) {
    console.log(`  ${key}`);
  }
  
  return routeOwners;
}

// Run the script
main();
