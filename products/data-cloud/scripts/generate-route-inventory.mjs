#!/usr/bin/env node

/**
 * P2-05: Remove dead or duplicate route definitions.
 * 
 * This script generates route inventory from the router and OpenAPI spec,
 * then compares them to identify dead or duplicate routes.
 *
 * @doc.type script
 * @doc.purpose Route inventory generation and comparison
 * @doc.layer infrastructure
 */

import { readFileSync, readdirSync, statSync } from 'fs';
import { join } from 'path';

function extractRoutesFromRouter(routerPath) {
  const content = readFileSync(routerPath, 'utf8');
  const routes = [];
  
  // Extract route patterns from Java router code
  const routePattern = /route\s*\(\s*["']([^"']+)["']\s*,\s*["']([^"']+)["']/g;
  let match;
  
  while ((match = routePattern.exec(content)) !== null) {
    routes.push({
      method: match[1],
      path: match[2],
      source: 'router',
    });
  }
  
  return routes;
}

function extractRoutesFromOpenAPI(openApiPath) {
  const content = readFileSync(openApiPath, 'utf8');
  const openApi = JSON.parse(content);
  const routes = [];
  
  if (openApi.paths) {
    for (const [path, methods] of Object.entries(openApi.paths)) {
      for (const method of Object.keys(methods)) {
        if (method !== 'parameters' && method !== '$ref') {
          routes.push({
            method: method.toUpperCase(),
            path: path,
            source: 'openapi',
          });
        }
      }
    }
  }
  
  return routes;
}

function compareRoutes(routerRoutes, openApiRoutes) {
  const routerSet = new Set(routerRoutes.map(r => `${r.method}:${r.path}`));
  const openApiSet = new Set(openApiRoutes.map(r => `${r.method}:${r.path}`));
  
  const inRouterNotInOpenApi = routerRoutes.filter(r => 
    !openApiSet.has(`${r.method}:${r.path}`)
  );
  
  const inOpenApiNotInRouter = openApiRoutes.filter(r => 
    !routerSet.has(`${r.method}:${r.path}`)
  );
  
  const duplicates = findDuplicates(routerRoutes);
  
  return {
    inRouterNotInOpenApi,
    inOpenApiNotInRouter,
    duplicates,
  };
}

function findDuplicates(routes) {
  const seen = new Map();
  const duplicates = [];
  
  for (const route of routes) {
    const key = `${route.method}:${route.path}`;
    if (seen.has(key)) {
      duplicates.push({
        method: route.method,
        path: route.path,
        count: seen.get(key) + 1,
      });
    }
    seen.set(key, (seen.get(key) || 0) + 1);
  }
  
  return duplicates;
}

function main() {
  const repoRoot = process.argv[2] || process.cwd();
  
  console.log('Generating route inventory...');
  
  const routerPath = join(repoRoot, 'products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/DataCloudRouterBuilder.java');
  const openApiPath = join(repoRoot, 'products/data-cloud/contracts/openapi/data-cloud.yaml');
  
  try {
    const routerRoutes = extractRoutesFromRouter(routerPath);
    console.log(`Found ${routerRoutes.length} routes in router`);
    
    const openApiRoutes = extractRoutesFromOpenAPI(openApiPath);
    console.log(`Found ${openApiRoutes.length} routes in OpenAPI`);
    
    const comparison = compareRoutes(routerRoutes, openApiRoutes);
    
    console.log('\nRoutes in router but not in OpenAPI:');
    if (comparison.inRouterNotInOpenApi.length === 0) {
      console.log('  ✅ None');
    } else {
      for (const route of comparison.inRouterNotInOpenApi) {
        console.log(`  ⚠️  ${route.method} ${route.path}`);
      }
    }
    
    console.log('\nRoutes in OpenAPI but not in router:');
    if (comparison.inOpenApiNotInRouter.length === 0) {
      console.log('  ✅ None');
    } else {
      for (const route of comparison.inOpenApiNotInRouter) {
        console.log(`  ⚠️  ${route.method} ${route.path}`);
      }
    }
    
    console.log('\nDuplicate routes in router:');
    if (comparison.duplicates.length === 0) {
      console.log('  ✅ None');
    } else {
      for (const route of comparison.duplicates) {
        console.log(`  ❌ ${route.method} ${route.path} (count: ${route.count})`);
      }
    }
    
    const hasIssues = 
      comparison.inRouterNotInOpenApi.length > 0 ||
      comparison.inOpenApiNotInRouter.length > 0 ||
      comparison.duplicates.length > 0;
    
    if (hasIssues) {
      console.error('\n❌ Route inventory check FAILED');
      process.exit(1);
    } else {
      console.log('\n✅ Route inventory check PASSED');
      process.exit(0);
    }
  } catch (error) {
    console.error(`Error: ${error.message}`);
    process.exit(1);
  }
}

main();
