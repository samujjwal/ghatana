import { createRouteAccessEvaluator, type ProductRouteCapability } from '@ghatana/product-shell';
import type { RouteStability } from '@ghatana/kernel-product-contracts';
import routeContractJson from '../../../config/phr-route-contract.json';

export type PhrRole = 'patient' | 'caregiver' | 'fchv' | 'clinician' | 'admin';
// Use platform contract stability values instead of duplicating
export type PhrRouteStability = RouteStability;

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
  readonly hidden?: boolean;
  readonly blocked?: boolean;
  readonly apiEndpoint?: string;
  readonly policyId?: string;
  readonly testId?: string;
  readonly surface?: readonly ('web' | 'mobile' | 'backend' | 'hidden')[];
  readonly i18nKey?: string;
  readonly descriptionI18nKey?: string;
  readonly routeType?: 'page' | 'detail' | 'action' | 'system';
  readonly visibilityReason?: string;
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
