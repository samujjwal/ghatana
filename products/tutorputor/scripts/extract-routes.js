#!/usr/bin/env node
/**
 * Extract all routes from the platform and generate API_ROUTE_OWNERS.json
 */

const fs = require('fs');
const path = require('path');

const PLATFORM_SRC = path.join(__dirname, '..', 'services', 'tutorputor-platform', 'src');
const OUTPUT_FILE = path.join(__dirname, '..', 'docs', 'architecture', 'API_ROUTE_OWNERS.json');

// Known module configurations
const MODULES = [
  { name: 'auth', prefix: '/api/v1/auth', routeFile: 'modules/auth/index.ts' },
  { name: 'user', prefix: '/api/v1/users', routeFile: 'modules/user/index.ts' },
  { name: 'learning', prefix: '/api/v1/learning', routeFile: 'modules/learning/routes.ts' },
  { name: 'ai', prefix: '/api/v1/ai', routeFile: 'modules/ai/routes.ts' },
  { name: 'collaboration', prefix: '/api/v1/collaboration', routeFile: 'modules/collaboration/routes.ts' },
  { name: 'integration', prefix: '/api/v1/integration', routeFile: 'modules/integration/index.ts' },
  // Integration sub-modules
  { name: 'integration-marketplace', prefix: '/api/v1/integration/marketplace', routeFile: 'modules/integration/marketplace/routes.ts' },
  { name: 'integration-billing', prefix: '/api/v1/integration/billing', routeFile: 'modules/integration/billing/routes.ts' },
  { name: 'integration-lti', prefix: '/api/v1/integration/lti', routeFile: 'modules/integration/lti/routes.ts' },
  { name: 'engagement', prefix: '/api/v1/engagement', routeFile: 'modules/engagement/index.ts' },
  // Engagement sub-modules
  { name: 'engagement-gamification', prefix: '/api/v1/engagement/gamification', routeFile: 'modules/engagement/gamification/routes.ts' },
  { name: 'engagement-social', prefix: '/api/v1/engagement/social', routeFile: 'modules/engagement/social/routes.ts' },
  { name: 'engagement-credentials', prefix: '/api/v1/engagement/credentials', routeFile: 'modules/engagement/credentials/routes.ts' },
  { name: 'credentials', prefix: '/api/v1/credentials', routeFile: 'modules/credentials/index.ts' },
  { name: 'content', prefix: '/api', routeFile: 'modules/content/routes.ts' },
  // Content sub-modules
  { name: 'content-studio', prefix: '/api/v1/content/studio', routeFile: 'modules/content/studio/routes.ts' },
  { name: 'content-cms', prefix: '/api/v1/content/cms', routeFile: 'modules/content/cms/routes.ts' },
  { name: 'content-generation', prefix: '/api/v1/content/generation', routeFile: 'modules/content/generation/routes.ts' },
  { name: 'content-recommendation', prefix: '/api/v1/content/recommendation', routeFile: 'modules/content/recommendation/routes.ts' },
  { name: 'content-evaluation', prefix: '/api/v1/content/evaluation', routeFile: 'modules/content/evaluation/routes.ts' },
  { name: 'content-candidates', prefix: '/api/v1/content/candidates', routeFile: 'modules/content/candidates/routes.ts' },
  { name: 'content-experiments', prefix: '/api/v1/content/experiments', routeFile: 'modules/content/experiments/ab-testing/routes.ts' },
  { name: 'content-telemetry', prefix: '/api/v1/content/telemetry', routeFile: 'modules/content/telemetry/routes.ts' },
  { name: 'simulation', prefix: '/api/v1/simulations', routeFile: 'modules/simulation/index.ts' },
  { name: 'search', prefix: '/api/v1/search', routeFile: 'modules/search/index.ts' },
  { name: 'kernel-registry', prefix: '/api/v1/kernel-registry', routeFile: 'modules/kernel-registry/fastify-routes.ts' },
  { name: 'content-needs', prefix: '/api/v1/content-needs', routeFile: 'modules/content-needs/routes.ts' },
  { name: 'auto-revision', prefix: '/api/v1/auto-revision', routeFile: 'modules/auto-revision/routes.ts' },
  { name: 'knowledge-base', prefix: '/api/v1/knowledge-base', routeFile: 'modules/knowledge-base/routes.ts' },
  { name: 'payments', prefix: '/api/v1/payments', routeFile: 'modules/payments/routes.ts' },
];

function extractRoutePatterns(content, module) {
  const routes = [];
  // Match both fastify.<method> and app.<method> patterns
  const methodPattern = /(fastify|app)\.(get|post|patch|put|delete)\s*\(\s*['"`]([^'"`]+)['"`]/g;
  let match;
  
  while ((match = methodPattern.exec(content)) !== null) {
    const [, , method, routePath] = match;
    // If the route already starts with /api, use it as-is
    // Otherwise, prepend the module prefix
    const fullPath = routePath.startsWith('/api') ? routePath : module.prefix + routePath;
    routes.push({
      path: fullPath,
      method: method.toUpperCase(),
      module: module.name,
      backendOwner: `services/tutorputor-platform/src/${module.routeFile}`,
    });
  }
  
  return routes;
}

function scanModuleRoutes(module) {
  const routeFilePath = path.join(PLATFORM_SRC, module.routeFile);
  
  try {
    const content = fs.readFileSync(routeFilePath, 'utf-8');
    return extractRoutePatterns(content, module);
  } catch (error) {
    console.warn(`Could not read route file for module ${module.name}: ${routeFilePath}`);
    return [];
  }
}

function main() {
  console.log('Extracting routes from platform...');
  
  const allRoutes = [];
  
  for (const module of MODULES) {
    console.log(`  Scanning module: ${module.name}`);
    const routes = scanModuleRoutes(module);
    allRoutes.push(...routes);
  }
  
  // Build API_ROUTE_OWNERS.json structure
  const routeOwners = {
    runtimeModel: 'primary-platform-with-worker-runtimes',
    canonicalBackend: 'services/tutorputor-platform',
    routes: {},
  };
  
  for (const route of allRoutes) {
    const key = `${route.method} ${route.path}`;
    
    if (!routeOwners.routes[key]) {
      routeOwners.routes[key] = {
        backendOwner: route.backendOwner,
        apiContract: 'api/tutorputor-api.openapi.yaml',
        typedClient: 'apps/tutorputor-web/src/api/tutorputorClient.ts',
        method: route.method,
        module: route.module,
      };
    }
  }
  
  // Sort routes by path
  const sortedRoutes = {};
  Object.keys(routeOwners.routes)
    .sort()
    .forEach(key => {
      sortedRoutes[key] = routeOwners.routes[key];
    });
  
  routeOwners.routes = sortedRoutes;
  
  // Write output
  fs.writeFileSync(OUTPUT_FILE, JSON.stringify(routeOwners, null, 2));
  
  console.log(`\nFound ${Object.keys(routeOwners.routes).length} routes`);
  console.log(`Output written to: ${OUTPUT_FILE}`);
  
  // Print sample
  console.log('\nSample routes:');
  const sampleKeys = Object.keys(routeOwners.routes).slice(0, 20);
  for (const key of sampleKeys) {
    console.log(`  ${key}`);
  }
}

main();
