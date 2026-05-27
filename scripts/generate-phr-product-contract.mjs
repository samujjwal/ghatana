#!/usr/bin/env node
/**
 * PHR Product Contract Generator
 * -------------------------------
 * Generates PHR web route manifest and backend entitlement payload from Kernel ProductContract.
 *
 * This script reads the PHR route contract JSON and generates:
 * 1. TypeScript route manifest for web (phrRouteContracts.ts)
 * 2. Backend entitlement JSON for PhrEntitlementRoutes
 *
 * Usage:
 *   node scripts/generate-phr-product-contract.mjs
 */

import { readFileSync, writeFileSync } from 'fs';
import { join, dirname } from 'path';
import { fileURLToPath } from 'url';

const __dirname = fileURLToPath(new URL('.', import.meta.url));
const REPO_ROOT = join(__dirname, '..');
const PHR_CONFIG = join(REPO_ROOT, 'products/phr/config');
const WEB_SRC = join(REPO_ROOT, 'products/phr/apps/web/src');

// Input: PHR route contract
const ROUTE_CONTRACT_PATH = join(PHR_CONFIG, 'phr-route-contract.json');

// Outputs
const WEB_MANIFEST_PATH = join(WEB_SRC, 'phrRouteContracts.ts');
const BACKEND_ENTITLEMENT_PATH = join(PHR_CONFIG, 'phr-backend-entitlement.json');

function readRouteContract() {
  const content = readFileSync(ROUTE_CONTRACT_PATH, 'utf-8');
  return JSON.parse(content);
}

function generateWebManifest(contract) {
  const routes = contract.routes || [];
  const roleOrder = contract.roleOrder || ['patient', 'caregiver', 'clinician', 'admin', 'fchv'];
  
  const routeEntries = routes.map(route => {
    const { id, path, label, minimumRole, personas, tier, actions, cards } = route;
    return `  {
    id: '${id}',
    path: '${path}',
    label: '${label}',
    minimumRole: '${minimumRole}',
    personas: ${JSON.stringify(personas)},
    tier: '${tier}',
    actions: ${JSON.stringify(actions)},
    cards: ${JSON.stringify(cards)},
  }`;
  }).join(',\n');

  return `/**
 * PHR Route Contracts
 * ------------------
 * Auto-generated from phr-route-contract.json by generate-phr-product-contract.mjs
 * DO NOT EDIT MANUALLY - regenerate with: node scripts/generate-phr-product-contract.mjs
 */

import type { PhrRouteContract } from './types';

export const PHR_ROLE_ORDER = ${JSON.stringify(roleOrder)} as const;

export const phrRouteContracts: PhrRouteContract[] = [
${routeEntries}
];

export type PhrRouteId = typeof phrRouteContracts[number]['id'];
`;
}

function generateBackendEntitlement(contract) {
  const routes = contract.routes || [];
  const roleOrder = contract.roleOrder || ['patient', 'caregiver', 'clinician', 'admin', 'fchv'];
  
  const entitlements = roleOrder.map(role => {
    const roleRoutes = routes.filter(r => 
      r.minimumRole === role || 
      (r.minimumRole === 'patient' && role === 'caregiver') ||
      r.minimumRole === 'admin'
    );
    
    return {
      role,
      routes: roleRoutes.map(r => ({
        id: r.id,
        path: r.path,
        method: r.method || 'GET',
        minimumRole: r.minimumRole,
        personas: r.personas || [],
        tier: r.tier || 'default',
      }))
    };
  });

  return JSON.stringify({
    version: contract.version || '1.0.0',
    generatedAt: new Date().toISOString(),
    roleOrder,
    entitlements
  }, null, 2);
}

function main() {
  console.log('🔧 Generating PHR product contract artifacts...\n');
  
  const contract = readRouteContract();
  
  // Generate web manifest
  const webManifest = generateWebManifest(contract);
  writeFileSync(WEB_MANIFEST_PATH, webManifest, 'utf-8');
  console.log('✅ Generated web manifest:', WEB_MANIFEST_PATH);
  
  // Generate backend entitlement
  const backendEntitlement = generateBackendEntitlement(contract);
  writeFileSync(BACKEND_ENTITLEMENT_PATH, backendEntitlement, 'utf-8');
  console.log('✅ Generated backend entitlement:', BACKEND_ENTITLEMENT_PATH);
  
  console.log('\n🎉 PHR product contract generation complete.');
}

main();
