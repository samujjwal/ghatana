import {
  createRouteContractGenerator,
  parseProductRouteContract,
  type ProductRouteCapability as KernelRouteCapability,
} from '@ghatana/kernel-product-contracts/route';
import routeContractJson from '../../../config/phr-route-contract.json';
import type { PhrRole, PhrRouteContract } from './phrRouteContracts';
import { PHR_ROLE_ORDER, phrRouteAccess, phrRouteContracts } from './phrRouteContracts';

export interface PhrRoutePlugin {
  readonly product: 'phr';
  readonly roleOrder: Readonly<Record<PhrRole, number>>;
  readonly routes: readonly PhrRouteContract[];
  readonly capabilities: readonly KernelRouteCapability[];
  getRoute(path: string): PhrRouteContract | undefined;
  isBrowserMountable(route: PhrRouteContract): boolean;
  getBrowserRoutes(): readonly PhrRouteContract[];
  isAllowedForRole(route: Pick<PhrRouteContract, 'minimumRole'>, role: PhrRole): boolean;
}

const routeGenerator = createRouteContractGenerator(parseProductRouteContract(routeContractJson));
const capabilityByPath = new Map(
  routeGenerator.generateRouteCapabilities().capabilities.map((capability) => [
    capability.path,
    capability,
  ] as const),
);

const routeByPath = new Map(phrRouteContracts.map((route) => [route.path, route] as const));

function toKernelRouteCapability(route: PhrRouteContract): KernelRouteCapability {
  const capability = capabilityByPath.get(route.path);
  if (!capability) {
    throw new Error(`PHR route plugin is missing Kernel capability projection for ${route.path}`);
  }

  return {
    ...route,
    directLinkAllowed: capability.directLinkAllowed,
    discoverable: capability.discoverable,
    apiEndpoint: capability.apiEndpoint ?? route.apiEndpoint,
    policyId: capability.policyId ?? route.policyId,
    testId: capability.testId ?? route.testId,
  };
}

function isBrowserMountable(route: PhrRouteContract): boolean {
  const capability = capabilityByPath.get(route.path);
  return capability?.directLinkAllowed === true && route.surface?.includes('web') !== false;
}

export const phrRoutePlugin: PhrRoutePlugin = {
  product: 'phr',
  roleOrder: PHR_ROLE_ORDER,
  routes: phrRouteContracts,
  capabilities: phrRouteContracts.map(toKernelRouteCapability),
  getRoute(path: string): PhrRouteContract | undefined {
    return routeByPath.get(path);
  },
  isBrowserMountable,
  getBrowserRoutes(): readonly PhrRouteContract[] {
    return phrRouteContracts.filter(isBrowserMountable);
  },
  isAllowedForRole(route: Pick<PhrRouteContract, 'minimumRole'>, role: PhrRole): boolean {
    return phrRouteAccess.isRouteAllowed(route, role);
  },
};
