import {
  createRouteAccessEvaluator,
  type ProductRouteCapability,
} from "@ghatana/product-shell";
import {
  type ProductRouteContract,
  type RouteStability,
  parseProductRouteContract,
} from "@ghatana/kernel-product-contracts/route";
import routeContractJson from "../../../config/phr-route-contract.json";
import type { PhrMessageKey } from "./i18n/phrI18n";

export type PhrRole = "patient" | "caregiver" | "fchv" | "clinician" | "admin";
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
  readonly surface?: readonly ("web" | "mobile" | "backend" | "hidden")[];
  readonly i18nKey: string;
  readonly descriptionI18nKey: string;
  readonly routeType?: "page" | "detail" | "action" | "system";
  readonly visibilityReason?: string;
  readonly apiContractId?: string;
  readonly dtoSchemaId?: string;
  readonly pluginDependencies?: readonly string[];
  readonly auditRequirement?: "none" | "standard" | "phi-access" | "phi-write" | "emergency-break-glass" | "admin-review";
  readonly phiSensitivity?: "none" | "pii" | "phi" | "restricted-phi" | "emergency-phi";
  readonly cachePolicy?: "no-store" | "private-session" | "short-lived" | "offline-encrypted";
  readonly offlinePolicy?: "online-only" | "metadata-only" | "encrypted-ttl" | "emergency-unavailable";
}

interface PhrRouteContractSource {
  readonly roleOrder: Readonly<Record<PhrRole, number>>;
  readonly routes: readonly PhrRouteContract[];
}

// Validate route contract against kernel schema
const validatedContract = parseProductRouteContract(routeContractJson);

// Use validated contract directly - Kernel provides typed contract
const canonicalRouteContract: PhrRouteContractSource = {
  roleOrder: validatedContract.roleOrder as Readonly<Record<PhrRole, number>>,
  routes: validatedContract.routes as readonly PhrRouteContract[],
};

export const PHR_ROLE_ORDER: Readonly<Record<PhrRole, number>> =
  canonicalRouteContract.roleOrder;

export const phrRouteAccess = createRouteAccessEvaluator(PHR_ROLE_ORDER);

export function isRouteAllowedForRole(
  route: Pick<PhrRouteContract, "minimumRole">,
  role: PhrRole,
): boolean {
  return phrRouteAccess.isRouteAllowed(route, role);
}

export const phrRouteContracts = canonicalRouteContract.routes;

export type PhrRoutePath = (typeof phrRouteContracts)[number]["path"];

/**
 * Get the i18n key for a route's label.
 */
export function getRouteLabelI18nKey(route: PhrRouteContract): PhrMessageKey {
  if (!route.i18nKey) {
    throw new Error(`PHR route ${route.path} is missing i18nKey`);
  }
  return route.i18nKey as PhrMessageKey;
}

/**
 * Get the i18n key for a route's description.
 */
export function getRouteDescriptionI18nKey(route: PhrRouteContract): PhrMessageKey {
  if (!route.descriptionI18nKey) {
    throw new Error(`PHR route ${route.path} is missing descriptionI18nKey`);
  }
  return route.descriptionI18nKey as PhrMessageKey;
}
