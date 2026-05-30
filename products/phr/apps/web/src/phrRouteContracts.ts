import { createRouteAccessEvaluator, type ProductRouteCapability } from '@ghatana/product-shell';
import { 
  type ProductRouteContract, 
  type RouteStability,
  parseProductRouteContract 
} from '@ghatana/kernel-product-contracts';
import routeContractJson from '../../../config/phr-route-contract.json';

export type PhrRole = 'patient' | 'caregiver' | 'fchv' | 'clinician' | 'admin';
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

// Validate route contract against kernel schema
const _validatedContract = parseProductRouteContract(routeContractJson);
const canonicalRouteContract = routeContractJson as unknown as PhrRouteContractSource;

export const PHR_ROLE_ORDER: Readonly<Record<PhrRole, number>> = canonicalRouteContract.roleOrder;

export const phrRouteAccess = createRouteAccessEvaluator(PHR_ROLE_ORDER);

export function isRouteAllowedForRole(route: Pick<PhrRouteContract, 'minimumRole'>, role: PhrRole): boolean {
  return phrRouteAccess.isRouteAllowed(route, role);
}

export const phrRouteContracts = canonicalRouteContract.routes;

export type PhrRoutePath = (typeof phrRouteContracts)[number]['path'];

/**
 * Get the i18n key for a route's label, falling back to the raw label if no i18n key exists.
 * This ensures UI rendering uses localized strings instead of raw English.
 */
export function getRouteLabelI18nKey(route: PhrRouteContract): string {
  return route.i18nKey ?? route.label;
}

/**
 * Get the i18n key for a route's description, falling back to the raw description if no i18n key exists.
 */
export function getRouteDescriptionI18nKey(route: PhrRouteContract): string {
  return route.descriptionI18nKey ?? route.description;
}
