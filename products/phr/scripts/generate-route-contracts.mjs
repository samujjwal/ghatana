#!/usr/bin/env node

/**
 * Generates the web route contract projection from the canonical JSON contract.
 * The generated TypeScript intentionally imports JSON instead of copying route
 * literals, so the web app cannot drift from products/phr/config/phr-route-contract.json.
 */

import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const JSON_CONTRACT_PATH = path.resolve(__dirname, '../config/phr-route-contract.json');
const TS_OUTPUT_PATH = path.resolve(__dirname, '../apps/web/src/phrRouteContracts.ts');

const TS_CONTENT = `import { createRouteAccessEvaluator, type ProductRouteCapability } from '@ghatana/product-shell';
import routeContractJson from '../../../config/phr-route-contract.json';

export type PhrRole = 'patient' | 'caregiver' | 'fchv' | 'clinician' | 'admin';
export type PhrRouteStability = 'stable' | 'preview' | 'blocked' | 'hidden';

export interface PhrRouteContract extends ProductRouteCapability {
  readonly path: string;
  readonly label: string;
  readonly description: string;
  readonly group: string;
  readonly minimumRole: PhrRole;
  readonly personas: readonly PhrRole[];
  readonly tiers: readonly string[];
  readonly actions: readonly string[];
  readonly cards: readonly string[];
  readonly stability: PhrRouteStability;
  readonly emergencyAction?: boolean;
  readonly featureFlag?: boolean;
  readonly hidden?: boolean;
  readonly blocked?: boolean;
  readonly apiEndpoint?: string;
  readonly policyId?: string;
  readonly testId?: string;
}

interface PhrRouteContractSource {
  readonly roleOrder: Readonly<Record<PhrRole, number>>;
  readonly routes: readonly PhrRouteContract[];
}

const canonicalRouteContract = routeContractJson as unknown as PhrRouteContractSource;

export const PHR_ROLE_ORDER: Readonly<Record<PhrRole, number>> = canonicalRouteContract.roleOrder;

export const phrRouteAccess = createRouteAccessEvaluator(PHR_ROLE_ORDER);

export function isRouteAllowedForRole(route: Pick<PhrRouteContract, 'minimumRole'>, role: PhrRole): boolean {
  return phrRouteAccess.isRouteAllowed(route, role);
}

export const phrRouteContracts = canonicalRouteContract.routes;

export type PhrRoutePath = (typeof phrRouteContracts)[number]['path'];
`;

function main() {
  const jsonContent = fs.readFileSync(JSON_CONTRACT_PATH, 'utf-8');
  JSON.parse(jsonContent);

  fs.writeFileSync(TS_OUTPUT_PATH, TS_CONTENT, 'utf-8');

  console.log(`Generated web route projection from ${JSON_CONTRACT_PATH}`);
  console.log(`Output: ${TS_OUTPUT_PATH}`);
}

main();
