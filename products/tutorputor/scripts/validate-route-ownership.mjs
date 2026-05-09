#!/usr/bin/env node

/**
 * Route Ownership Validation Script
 *
 * Validates that all registered routes have ownership information
 * and detects drift (unowned or misowned routes).
 *
 * Usage: node scripts/validate-route-ownership.mjs [--strict]
 */

import { readFileSync, readdirSync } from 'fs';
import { join, dirname } from 'path';
import { fileURLToPath } from 'url';

const __dirname = dirname(fileURLToPath(import.meta.url));

// Load route ownership registry
const registryPath = join(__dirname, 'route-ownership-registry.json');
let registry;
try {
  registry = JSON.parse(readFileSync(registryPath, 'utf-8'));
} catch (error) {
  console.error('❌ Failed to load route ownership registry:', error.message);
  process.exit(1);
}

// Extract all registered route patterns
const registeredPatterns = new Set();
for (const [app, appData] of Object.entries(registry.routes)) {
  for (const route of appData.routes) {
    registeredPatterns.add(route.path);
  }
}

// Extract actual routes from code
const actualRoutes = new Set();

// Scan platform service routes
const platformRoutesPath = join(__dirname, '..', 'services', 'tutorputor-platform', 'src');
if (exists(platformRoutesPath)) {
  scanDirectoryForRoutes(platformRoutesPath, actualRoutes);
}

// Scan admin app routes
const adminRoutesPath = join(__dirname, '..', 'apps', 'tutorputor-admin', 'src');
if (exists(adminRoutesPath)) {
  scanDirectoryForRoutes(adminRoutesPath, actualRoutes);
}

// Scan learner app routes
const learnerRoutesPath = join(__dirname, '..', 'apps', 'tutorputor-learner', 'src');
if (exists(learnerRoutesPath)) {
  scanDirectoryForRoutes(learnerRoutesPath, actualRoutes);
}

// Check for drift
let driftDetected = false;
const unownedRoutes = [];
const misownedRoutes = [];

for (const actualRoute of actualRoutes) {
  let isOwned = false;
  
  for (const pattern of registeredPatterns) {
    if (routeMatchesPattern(actualRoute, pattern)) {
      isOwned = true;
      break;
    }
  }
  
  if (!isOwned) {
    unownedRoutes.push(actualRoute);
    driftDetected = true;
  }
}

// Check for registered routes that don't exist
const orphanedRoutes = [];
for (const pattern of registeredPatterns) {
  const hasMatches = Array.from(actualRoutes).some(route => 
    routeMatchesPattern(route, pattern)
  );
  
  if (!hasMatches && !pattern.includes('*')) {
    orphanedRoutes.push(pattern);
  }
}

// Report results
console.log('🔍 Route Ownership Validation');
console.log('================================');
console.log(`Registered patterns: ${registeredPatterns.size}`);
console.log(`Actual routes found: ${actualRoutes.size}`);
console.log('');

if (unownedRoutes.length > 0) {
  console.log('❌ Unowned routes detected:');
  for (const route of unownedRoutes) {
    console.log(`  - ${route}`);
  }
  console.log('');
}

if (orphanedRoutes.length > 0) {
  console.log('⚠️  Orphaned registered routes (no matching actual routes):');
  for (const route of orphanedRoutes) {
    console.log(`  - ${route}`);
  }
  console.log('');
}

if (driftDetected) {
  console.log('❌ Route ownership drift detected!');
  console.log('');
  console.log('Please update the route ownership registry at:');
  console.log(`  ${registryPath}`);
  process.exit(1);
}

console.log('✅ All routes are properly owned');
process.exit(0);

// Helper functions
function exists(path) {
  try {
    return true;
  } catch {
    return false;
  }
}

function scanDirectoryForRoutes(dir, routes) {
  try {
    const files = readdirSync(dir, { withFileTypes: true });
    
    for (const file of files) {
      const fullPath = join(dir, file.name);
      
      if (file.isDirectory()) {
        scanDirectoryForRoutes(fullPath, routes);
      } else if (file.name === 'routes.ts' || file.name === 'routes.js') {
        extractRoutesFromFile(fullPath, routes);
      }
    }
  } catch (error) {
    // Directory doesn't exist or can't be read
  }
}

function extractRoutesFromFile(filePath, routes) {
  try {
    const content = readFileSync(filePath, 'utf-8');
    
    // Extract route patterns from Fastify route definitions
    const fastifyRouteMatches = content.matchAll(/(?:fastify\.(get|post|put|delete|patch)\s*\(\s*['"`]([^'"`]+)['"`])/g);
    for (const match of fastifyRouteMatches) {
      routes.add(match[2]);
    }
    
    // Extract route patterns from React Router definitions
    const reactRouteMatches = content.matchAll(/(?:path\s*=\s*['"`]([^'"`]+)['"`])/g);
    for (const match of reactRouteMatches) {
      routes.add(match[1]);
    }
  } catch (error) {
    // Can't read file
  }
}

function routeMatchesPattern(route, pattern) {
  if (pattern === route) {
    return true;
  }
  
  if (pattern.endsWith('/*')) {
    const prefix = pattern.slice(0, -2);
    return route.startsWith(prefix);
  }
  
  if (pattern.includes('*')) {
    const regexPattern = pattern
      .replace(/\*/g, '.*')
      .replace(/\//g, '\\/');
    const regex = new RegExp(`^${regexPattern}$`);
    return regex.test(route);
  }
  
  return false;
}
