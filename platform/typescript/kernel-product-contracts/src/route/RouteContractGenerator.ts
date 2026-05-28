/**
 * K-002: Kernel route contract generator.
 * Generates TS manifest, backend entitlement data, and route docs from one contract.
 */

import type {
  ProductRouteCapability,
  ProductRouteContract,
  RouteStability,
} from './ProductRouteContract.js';
import { parseProductRouteContract } from './ProductRouteContract.js';

export interface GeneratedTSManifest {
  routes: {
    path: string;
    label: string;
    description: string;
    minimumRole: string;
    personas?: string[];
    tiers?: string[];
    actions?: Array<{
      id: string;
      label: string;
      endpoint: string;
      method: string;
      policyId?: string;
      idempotent?: boolean;
    }>;
    cards?: Array<{
      id: string;
      title: string;
      description: string;
    }>;
    stability: RouteStability;
    featureFlag?: boolean;
    metadata?: {
      apiEndpoint?: string;
      policyId?: string;
      testId?: string;
      featureFlag?: string;
    };
  }[];
}

export interface GeneratedBackendEntitlement {
  version: string;
  roleOrder: Record<string, number>;
  routes: {
    path: string;
    minimumRole: string;
    personas?: string[];
    tiers?: string[];
    actions?: string[];
    cards?: string[];
    stability: string;
    featureFlag?: boolean;
    apiEndpoint?: string;
    policyId?: string;
    testId?: string;
  }[];
}

export interface GeneratedRouteDocs {
  version: string;
  routes: {
    path: string;
    label: string;
    description: string;
    group: string;
    stability: string;
    metadata?: {
      apiEndpoint?: string;
      policyId?: string;
      testId?: string;
    };
  }[];
}

export interface GeneratedRouteCapabilityContract {
  version: string;
  roleOrder: Record<string, number>;
  capabilities: ProductRouteCapability[];
}

export class RouteContractGenerator {
  private readonly contract: ProductRouteContract;

  constructor(contract: ProductRouteContract) {
    this.contract = parseProductRouteContract(contract);
  }

  generateTSManifest(): GeneratedTSManifest {
    return {
      routes: this.contract.routes.map(route => {
        const result: GeneratedTSManifest['routes'][0] = {
          path: route.path,
          label: route.label,
          description: route.description,
          minimumRole: route.minimumRole,
          stability: route.stability,
        };
        
        if (route.personas) result.personas = route.personas;
        if (route.tiers) result.tiers = route.tiers;
        if (route.actions) {
          result.actions = route.actions.map(action => ({
            id: action.id,
            label: action.label,
            endpoint: action.endpoint,
            method: action.method,
            ...(action.policyId !== undefined && { policyId: action.policyId }),
            ...(action.idempotent !== undefined && { idempotent: action.idempotent }),
          }));
        }
        if (route.cards) {
          result.cards = route.cards.map(card => ({
            id: card.id,
            title: card.title,
            description: card.description,
          }));
        }
        if (route.featureFlag !== undefined) result.featureFlag = route.featureFlag;
        if (route.metadata) {
          result.metadata = {};
          if (route.metadata.apiEndpoint) result.metadata.apiEndpoint = route.metadata.apiEndpoint;
          if (route.metadata.policyId) result.metadata.policyId = route.metadata.policyId;
          if (route.metadata.testId) result.metadata.testId = route.metadata.testId;
          if (route.metadata.featureFlag) result.metadata.featureFlag = route.metadata.featureFlag;
        }
        
        return result;
      }),
    };
  }

  generateBackendEntitlement(): GeneratedBackendEntitlement {
    return {
      version: this.contract.version,
      roleOrder: this.contract.roleOrder,
      routes: this.contract.routes.map(route => {
        const result: GeneratedBackendEntitlement['routes'][0] = {
          path: route.path,
          minimumRole: route.minimumRole,
          stability: route.stability,
        };
        
        if (route.personas) result.personas = route.personas;
        if (route.tiers) result.tiers = route.tiers;
        if (route.actions) result.actions = route.actions.map(a => a.id);
        if (route.cards) result.cards = route.cards.map(c => c.id);
        if (route.featureFlag !== undefined) result.featureFlag = route.featureFlag;
        if (route.metadata?.apiEndpoint) result.apiEndpoint = route.metadata.apiEndpoint;
        if (route.metadata?.policyId) result.policyId = route.metadata.policyId;
        if (route.metadata?.testId) result.testId = route.metadata.testId;
        
        return result;
      }),
    };
  }

  generateRouteDocs(): GeneratedRouteDocs {
    return {
      version: this.contract.version,
      routes: this.contract.routes.map(route => {
        const result: GeneratedRouteDocs['routes'][0] = {
          path: route.path,
          label: route.label,
          description: route.description,
          group: route.group,
          stability: route.stability,
        };
        
        if (route.metadata) {
          result.metadata = {};
          if (route.metadata.apiEndpoint) result.metadata.apiEndpoint = route.metadata.apiEndpoint;
          if (route.metadata.policyId) result.metadata.policyId = route.metadata.policyId;
          if (route.metadata.testId) result.metadata.testId = route.metadata.testId;
        }
        
        return result;
      }),
    };
  }

  generateRouteCapabilities(): GeneratedRouteCapabilityContract {
    return {
      version: this.contract.version,
      roleOrder: this.contract.roleOrder,
      capabilities: this.contract.routes.map(route => {
        const suppressed = route.stability === 'hidden' || route.stability === 'blocked';
        const result: ProductRouteCapability = {
          path: route.path,
          stability: route.stability,
          directLinkAllowed: !suppressed,
          discoverable: !suppressed && route.stability !== 'preview',
          minimumRole: route.minimumRole,
        };

        if (route.featureFlag !== undefined) result.featureFlag = route.featureFlag;
        if (route.metadata?.apiEndpoint) result.apiEndpoint = route.metadata.apiEndpoint;
        if (route.metadata?.policyId) result.policyId = route.metadata.policyId;
        if (route.metadata?.testId) result.testId = route.metadata.testId;

        return result;
      }),
    };
  }

  generateAll(): {
    tsManifest: GeneratedTSManifest;
    backendEntitlement: GeneratedBackendEntitlement;
    routeDocs: GeneratedRouteDocs;
    routeCapabilities: GeneratedRouteCapabilityContract;
  } {
    return {
      tsManifest: this.generateTSManifest(),
      backendEntitlement: this.generateBackendEntitlement(),
      routeDocs: this.generateRouteDocs(),
      routeCapabilities: this.generateRouteCapabilities(),
    };
  }
}

export function createRouteContractGenerator(contract: ProductRouteContract): RouteContractGenerator {
  return new RouteContractGenerator(contract);
}
