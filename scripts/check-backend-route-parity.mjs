#!/usr/bin/env node

/**
 * Validates backend route contract parity
 * BE-001: Every non-hidden apiEndpoint in phr-route-contract.json must be handled by PhrHttpServer
 */

import { readFileSync } from 'fs';
import { fileURLToPath } from 'url';
import { dirname, join } from 'path';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

const routeContractPath = join(__dirname, '..', 'products', 'phr', 'config', 'phr-route-contract.json');
const httpServerPath = join(__dirname, '..', 'products', 'phr', 'src', 'main', 'java', 'com', 'ghatana', 'phr', 'api', 'PhrHttpServer.java');
const mountTablePath = join(__dirname, '..', 'products', 'phr', 'src', 'main', 'java', 'com', 'ghatana', 'phr', 'api', 'PhrRouteContractMountTable.java');

const routeContract = JSON.parse(readFileSync(routeContractPath, 'utf-8'));
const httpServerSource = readFileSync(httpServerPath, 'utf-8');
const mountTableSource = readFileSync(mountTablePath, 'utf-8');

// Extract apiEndpoints from route contract (only stable routes)
const contractEndpoints = new Set();
for (const route of routeContract.routes) {
  if (route.stability === 'stable' && route.apiEndpoint) {
    contractEndpoints.add(route.apiEndpoint);
  }
}

// Extract mounted routes from PhrHttpServer.java
const mountedRoutes = new Set();
const routePattern = /\.with\("([^"]+)",/g;
let match;
while ((match = routePattern.exec(httpServerSource)) !== null) {
  mountedRoutes.add(match[1]);
}
const mountSpecPattern = /new MountSpec\(\s*"([^"]+)"/g;
while ((match = mountSpecPattern.exec(mountTableSource)) !== null) {
  mountedRoutes.add(match[1]);
}

if (!httpServerSource.includes('PhrRouteContractMountTable.loadStableMounts()')) {
  console.error('❌ PhrHttpServer does not load product route mounts from PhrRouteContractMountTable');
  process.exit(1);
}

// Normalize paths for comparison (remove wildcards)
const normalizePath = (path) => {
  return path.replace(/\*$/, '').replace(/\/$/, '');
};

const normalizedContract = new Set([...contractEndpoints].map(normalizePath));
const normalizedMounted = new Set([...mountedRoutes].map(normalizePath));

let errors = 0;
let warnings = 0;

console.log('Validating backend route contract parity...\n');
console.log(`Contract endpoints (stable): ${contractEndpoints.size}`);
console.log(`Mounted routes in PhrHttpServer: ${mountedRoutes.size}\n`);

// Check for missing routes
for (const endpoint of contractEndpoints) {
  const normalized = normalizePath(endpoint);
  const isMounted = [...normalizedMounted].some(mounted => 
    mounted === normalized || 
    endpoint.startsWith(mounted) ||
    mounted.startsWith(normalized)
  );
  
  if (!isMounted) {
    console.error(`❌ Contract endpoint not mounted: ${endpoint}`);
    errors++;
  }
}

// Check for orphaned routes (mounted but not in contract)
for (const route of mountedRoutes) {
  const normalized = normalizePath(route);
  const inContract = [...normalizedContract].some(contract => 
    contract === normalized ||
    contract.startsWith(normalized)
  );
  
  if (!inContract && !route.includes('/fhir') && !route.includes('/health') && !route.includes('/ready') && !route.includes('/auth') && !route.includes('/provider') && !route.includes('/caregiver') && !route.includes('/fchv') && !route.includes('/mobile')) {
    console.warn(`⚠️  Mounted route not in contract: ${route}`);
    warnings++;
  }
}

console.log(`\nErrors: ${errors}`);
console.log(`Warnings: ${warnings}`);

if (errors > 0) {
  process.exit(1);
}

console.log('✅ Backend route contract parity validated');
