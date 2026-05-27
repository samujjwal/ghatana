#!/usr/bin/env node

/**
 * Generates TypeScript route contracts from the canonical JSON route contract.
 * This ensures the web app and backend use the same source of truth for routes.
 */

import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const JSON_CONTRACT_PATH = path.resolve(__dirname, '../config/phr-route-contract.json');
const TS_OUTPUT_PATH = path.resolve(__dirname, '../apps/web/src/phrRouteContracts.ts');

function toCamelCase(str) {
  return str
    .replace(/[-_](.)/g, (_, c) => c.toUpperCase())
    .replace(/^(.)/, (_, c) => c.toLowerCase());
}

function generateTSContract(json) {
  const { roleOrder, routes } = json;
  
  // Generate role order object
  const roleOrderEntries = Object.entries(roleOrder)
    .map(([role, order]) => `  ${role}: ${order},`)
    .join('\n');
  
  // Generate route contracts array
  const routeEntries = routes.map(route => {
    const path = route.path;
    const label = route.label;
    const description = route.description;
    const group = route.group;
    const minimumRole = route.minimumRole;
    const personas = route.personas || [];
    const tiers = route.tiers || [];
    const actions = route.actions || [];
    const cards = route.cards || [];
    const stability = route.stability || 'stable';
    const apiEndpoint = route.apiEndpoint || undefined;
    const policyId = route.policyId || undefined;
    const testId = route.testId || undefined;
    const emergencyAction = route.emergencyAction || false;
    
    // Build the route object
    let routeObj = `  {
    path: '${path}',
    label: t('route.${toCamelCase(path.replace(/^\//, '').replace(/:/g, ''))}.label'),
    description: t('route.${toCamelCase(path.replace(/^\//, '').replace(/:/g, ''))}.description'),
    group: t('route.group.${toCamelCase(group)}'),
    minimumRole: '${minimumRole}',
    personas: ${JSON.stringify(personas)},
    tiers: ${JSON.stringify(tiers)},
    actions: ${JSON.stringify(actions)},
    cards: ${JSON.stringify(cards)},
    stability: '${stability}'`;
    
    if (emergencyAction) {
      routeObj += `,
    emergencyAction: true`;
    }
    
    if (apiEndpoint) {
      routeObj += `,
    apiEndpoint: '${apiEndpoint}'`;
    }
    
    if (policyId) {
      routeObj += `,
    policyId: '${policyId}'`;
    }
    
    if (testId) {
      routeObj += `,
    testId: '${testId}'`;
    }
    
    routeObj += '\n  }';
    
    return routeObj;
  }).join(',\n');
  
  return `import { createRouteAccessEvaluator, type ProductRouteCapability } from '@ghatana/product-shell';
import type { PhrRole } from './auth/PhrAccessContext';
import { t } from './i18n/phrI18n';

export interface PhrRouteContract extends ProductRouteCapability {
  readonly personas?: readonly string[];
  readonly tiers?: readonly string[];
  readonly emergencyAction?: boolean;
  /**
   * When present and set to \`true\`, this route is behind a feature flag and
   * is not yet production-ready. The router renders a \`FeatureFlagPage\`
   * placeholder instead of the real page component.
   */
  readonly featureFlag?: boolean;
  /**
   * Route stability status - only stable, preview, blocked, or hidden allowed.
   * No deprecated or removed states per fix-forward policy.
   */
  readonly stability?: 'stable' | 'preview' | 'blocked' | 'hidden';
  /**
   * Backend API endpoint for this route.
   */
  readonly apiEndpoint?: string;
  /**
   * Policy ID for access control.
   */
  readonly policyId?: string;
  /**
   * Test ID for route verification.
   */
  readonly testId?: string;
}

export type PhrRole = 'patient' | 'caregiver' | 'clinician' | 'admin' | 'fchv';

export const PHR_ROLE_ORDER: Readonly<Record<PhrRole, number>> = {
${roleOrderEntries}
};

export const phrRouteAccess = createRouteAccessEvaluator(PHR_ROLE_ORDER);

export function isRouteAllowedForRole(route: Pick<PhrRouteContract, 'minimumRole'>, role: PhrRole): boolean {
  return phrRouteAccess.isRouteAllowed(route, role);
}

export const phrRouteContracts = [
${routeEntries}
] as const satisfies readonly PhrRouteContract[];

export type PhrRoutePath = (typeof phrRouteContracts)[number]['path'];
`;
}

function main() {
  try {
    const jsonContent = fs.readFileSync(JSON_CONTRACT_PATH, 'utf-8');
    const json = JSON.parse(jsonContent);
    
    const tsContent = generateTSContract(json);
    
    fs.writeFileSync(TS_OUTPUT_PATH, tsContent, 'utf-8');
    
    console.log(`Generated TypeScript route contracts from ${JSON_CONTRACT_PATH}`);
    console.log(`Output: ${TS_OUTPUT_PATH}`);
    console.log(`Routes generated: ${json.routes.length}`);
  } catch (error) {
    console.error('Error generating route contracts:', error);
    process.exit(1);
  }
}

main();
