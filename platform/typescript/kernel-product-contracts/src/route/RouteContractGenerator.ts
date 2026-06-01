/**
 * K-002: Kernel route contract generator.
 * Generates TS manifest, backend entitlement data, and route docs from one contract.
 */

import { z } from 'zod';
import type {
  ProductRouteCapability,
  ProductRouteContract,
  RouteAuditRequirement,
  RouteCachePolicy,
  RouteOfflinePolicy,
  RoutePhiSensitivity,
  RouteStability,
} from './ProductRouteContract.js';
import {
  ProductRouteCapabilitySchema,
  RouteAuditRequirementValues,
  RouteCachePolicyValues,
  RouteOfflinePolicyValues,
  RoutePhiSensitivityValues,
  RouteActionSchema,
  RouteCardSchema,
  RouteMetadataSchema,
  RouteStabilityValues,
  parseProductRouteContract,
} from './ProductRouteContract.js';

export interface GeneratedTSManifest {
  routes: {
    path: string;
    label: string;
    description: string;
    minimumRole: string;
    personas?: string[];
    tiers?: string[];
    actions?: Array<string | {
      id: string;
      label: string;
      endpoint: string;
      method: string;
      policyId?: string;
      idempotent?: boolean;
    }>;
    cards?: Array<string | {
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
      apiContractId?: string;
      dtoSchemaId?: string;
      pluginDependencies?: string[];
      auditRequirement?: RouteAuditRequirement;
      phiSensitivity?: RoutePhiSensitivity;
      cachePolicy?: RouteCachePolicy;
      offlinePolicy?: RouteOfflinePolicy;
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
    actions?: (string | { id: string })[];
    cards?: (string | { id: string })[];
    stability: string;
    featureFlag?: boolean;
    apiEndpoint?: string;
    policyId?: string;
    testId?: string;
    apiContractId?: string;
    dtoSchemaId?: string;
    pluginDependencies?: string[];
    auditRequirement?: RouteAuditRequirement;
    phiSensitivity?: RoutePhiSensitivity;
    cachePolicy?: RouteCachePolicy;
    offlinePolicy?: RouteOfflinePolicy;
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
      apiContractId?: string;
      dtoSchemaId?: string;
      pluginDependencies?: string[];
      auditRequirement?: RouteAuditRequirement;
      phiSensitivity?: RoutePhiSensitivity;
      cachePolicy?: RouteCachePolicy;
      offlinePolicy?: RouteOfflinePolicy;
    };
  }[];
}

export interface GeneratedRouteCapabilityContract {
  version: string;
  roleOrder: Record<string, number>;
  capabilities: ProductRouteCapability[];
}

export interface GeneratedJavaRouteConstants {
  packageName: string;
  className: string;
  version: string;
  routes: {
    path: string;
    constantName: string;
    label: string;
    minimumRole: string;
    stability: string;
    apiEndpoint?: string;
    policyId?: string;
    testId?: string;
    apiContractId?: string;
    dtoSchemaId?: string;
    pluginDependencies?: string[];
    auditRequirement?: RouteAuditRequirement;
    phiSensitivity?: RoutePhiSensitivity;
    cachePolicy?: RouteCachePolicy;
    offlinePolicy?: RouteOfflinePolicy;
  }[];
}

const RouteOperationalMetadataSchema = z.object({
  apiContractId: z.string().trim().min(1).optional(),
  dtoSchemaId: z.string().trim().min(1).optional(),
  pluginDependencies: z.array(z.string().trim().min(1)).optional(),
  auditRequirement: z.enum(RouteAuditRequirementValues).optional(),
  phiSensitivity: z.enum(RoutePhiSensitivityValues).optional(),
  cachePolicy: z.enum(RouteCachePolicyValues).optional(),
  offlinePolicy: z.enum(RouteOfflinePolicyValues).optional(),
});

const GeneratedTSManifestRouteSchema = z
  .object({
    path: z.string().trim().min(1),
    label: z.string().trim().min(1),
    description: z.string().trim().min(1),
    minimumRole: z.string().trim().min(1),
    personas: z.array(z.string().trim().min(1)).optional(),
    tiers: z.array(z.string().trim().min(1)).optional(),
    actions: z.array(RouteActionSchema).optional(),
    cards: z.array(RouteCardSchema).optional(),
    stability: z.enum(RouteStabilityValues),
    featureFlag: z.boolean().optional(),
    metadata: RouteMetadataSchema.merge(RouteOperationalMetadataSchema).optional(),
  })
  .strict();

export const GeneratedTSManifestSchema = z
  .object({
    routes: z.array(GeneratedTSManifestRouteSchema),
  })
  .strict();

const GeneratedBackendEntitlementRouteSchema = z
  .object({
    path: z.string().trim().min(1),
    minimumRole: z.string().trim().min(1),
    personas: z.array(z.string().trim().min(1)).optional(),
    tiers: z.array(z.string().trim().min(1)).optional(),
    actions: z.array(z.string().trim().min(1)).optional(),
    cards: z.array(z.string().trim().min(1)).optional(),
    stability: z.enum(RouteStabilityValues),
    featureFlag: z.boolean().optional(),
    apiEndpoint: z.string().trim().min(1).optional(),
    policyId: z.string().trim().min(1).optional(),
    testId: z.string().trim().min(1).optional(),
    apiContractId: z.string().trim().min(1).optional(),
    dtoSchemaId: z.string().trim().min(1).optional(),
    pluginDependencies: z.array(z.string().trim().min(1)).optional(),
    auditRequirement: z.enum(RouteAuditRequirementValues).optional(),
    phiSensitivity: z.enum(RoutePhiSensitivityValues).optional(),
    cachePolicy: z.enum(RouteCachePolicyValues).optional(),
    offlinePolicy: z.enum(RouteOfflinePolicyValues).optional(),
  })
  .strict();

export const GeneratedBackendEntitlementSchema = z
  .object({
    version: z.string().trim().min(1),
    roleOrder: z.record(z.string(), z.number().int().nonnegative()),
    routes: z.array(GeneratedBackendEntitlementRouteSchema),
  })
  .strict();

const GeneratedRouteDocsRouteSchema = z
  .object({
    path: z.string().trim().min(1),
    label: z.string().trim().min(1),
    description: z.string().trim().min(1),
    group: z.string().trim().min(1),
    stability: z.enum(RouteStabilityValues),
    metadata: RouteMetadataSchema.pick({
      apiEndpoint: true,
      policyId: true,
      testId: true,
    }).merge(RouteOperationalMetadataSchema).optional(),
  })
  .strict();

export const GeneratedRouteDocsSchema = z
  .object({
    version: z.string().trim().min(1),
    routes: z.array(GeneratedRouteDocsRouteSchema),
  })
  .strict();

export const GeneratedRouteCapabilityContractSchema = z
  .object({
    version: z.string().trim().min(1),
    roleOrder: z.record(z.string(), z.number().int().nonnegative()),
    capabilities: z.array(ProductRouteCapabilitySchema),
  })
  .strict();

export class RouteContractGenerator {
  private readonly contract: ProductRouteContract;
  private readonly packageName: string;
  private readonly className: string;

  constructor(contract: ProductRouteContract, packageName = 'com.ghatana.product.routes', className = 'ProductRoutes') {
    this.contract = parseProductRouteContract(contract);
    this.packageName = packageName;
    this.className = className;
  }

  generateTSManifest(): GeneratedTSManifest {
    return {
      routes: this.contract.routes.map(route => {
        const metadata = this.routeMetadata(route);
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
          result.actions = route.actions.map(action => {
            if (typeof action === 'string') {
              return action as string | { id: string; label: string; endpoint: string; method: string; policyId?: string; idempotent?: boolean };
            }
            return {
              id: action.id,
              label: action.label,
              endpoint: action.endpoint,
              method: action.method,
              ...(action.policyId !== undefined && { policyId: action.policyId }),
              ...(action.idempotent !== undefined && { idempotent: action.idempotent }),
            } as { id: string; label: string; endpoint: string; method: string; policyId?: string; idempotent?: boolean };
          }) as Array<string | { id: string; label: string; endpoint: string; method: string; policyId?: string; idempotent?: boolean }>;
        }
        if (route.cards) {
          result.cards = route.cards.map(card => {
            if (typeof card === 'string') {
              return card as string | { id: string; title: string; description: string };
            }
            return {
              id: card.id,
              title: card.title,
              description: card.description,
            } as { id: string; title: string; description: string };
          }) as Array<string | { id: string; title: string; description: string }>;
        }
        if (route.featureFlag !== undefined) result.featureFlag = route.featureFlag;
        if (Object.keys(metadata).length > 0) {
          result.metadata = {};
          if (metadata.apiEndpoint) result.metadata.apiEndpoint = metadata.apiEndpoint;
          if (metadata.policyId) result.metadata.policyId = metadata.policyId;
          if (metadata.testId) result.metadata.testId = metadata.testId;
          if (metadata.featureFlag) result.metadata.featureFlag = metadata.featureFlag;
          if (metadata.apiContractId) result.metadata.apiContractId = metadata.apiContractId;
          if (metadata.dtoSchemaId) result.metadata.dtoSchemaId = metadata.dtoSchemaId;
          if (metadata.pluginDependencies) result.metadata.pluginDependencies = metadata.pluginDependencies;
          if (metadata.auditRequirement) result.metadata.auditRequirement = metadata.auditRequirement;
          if (metadata.phiSensitivity) result.metadata.phiSensitivity = metadata.phiSensitivity;
          if (metadata.cachePolicy) result.metadata.cachePolicy = metadata.cachePolicy;
          if (metadata.offlinePolicy) result.metadata.offlinePolicy = metadata.offlinePolicy;
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
        const metadata = this.routeMetadata(route);
        const result: GeneratedBackendEntitlement['routes'][0] = {
          path: route.path,
          minimumRole: route.minimumRole,
          stability: route.stability,
        };
        
        if (route.personas) result.personas = route.personas;
        if (route.tiers) result.tiers = route.tiers;
        if (route.actions) result.actions = route.actions.map(a => typeof a === 'string' ? a : a.id);
        if (route.cards) result.cards = route.cards.map(c => typeof c === 'string' ? c : c.id);
        if (route.featureFlag !== undefined) result.featureFlag = route.featureFlag;
        if (metadata.apiEndpoint) result.apiEndpoint = metadata.apiEndpoint;
        if (metadata.policyId) result.policyId = metadata.policyId;
        if (metadata.testId) result.testId = metadata.testId;
        if (metadata.apiContractId) result.apiContractId = metadata.apiContractId;
        if (metadata.dtoSchemaId) result.dtoSchemaId = metadata.dtoSchemaId;
        if (metadata.pluginDependencies) result.pluginDependencies = metadata.pluginDependencies;
        if (metadata.auditRequirement) result.auditRequirement = metadata.auditRequirement;
        if (metadata.phiSensitivity) result.phiSensitivity = metadata.phiSensitivity;
        if (metadata.cachePolicy) result.cachePolicy = metadata.cachePolicy;
        if (metadata.offlinePolicy) result.offlinePolicy = metadata.offlinePolicy;
        
        return result;
      }),
    };
  }

  generateRouteDocs(): GeneratedRouteDocs {
    return {
      version: this.contract.version,
      routes: this.contract.routes.map(route => {
        const metadata = this.routeMetadata(route);
        const result: GeneratedRouteDocs['routes'][0] = {
          path: route.path,
          label: route.label,
          description: route.description,
          group: route.group,
          stability: route.stability,
        };
        
        if (metadata.apiEndpoint || metadata.policyId || metadata.testId) {
          result.metadata = {};
          if (metadata.apiEndpoint) result.metadata.apiEndpoint = metadata.apiEndpoint;
          if (metadata.policyId) result.metadata.policyId = metadata.policyId;
          if (metadata.testId) result.metadata.testId = metadata.testId;
          if (metadata.apiContractId) result.metadata.apiContractId = metadata.apiContractId;
          if (metadata.dtoSchemaId) result.metadata.dtoSchemaId = metadata.dtoSchemaId;
          if (metadata.pluginDependencies) result.metadata.pluginDependencies = metadata.pluginDependencies;
          if (metadata.auditRequirement) result.metadata.auditRequirement = metadata.auditRequirement;
          if (metadata.phiSensitivity) result.metadata.phiSensitivity = metadata.phiSensitivity;
          if (metadata.cachePolicy) result.metadata.cachePolicy = metadata.cachePolicy;
          if (metadata.offlinePolicy) result.metadata.offlinePolicy = metadata.offlinePolicy;
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
        const metadata = this.routeMetadata(route);
        const suppressed = route.stability === 'hidden'
          || route.stability === 'blocked'
          || route.stability === 'deferred'
          || route.stability === 'removed';
        const result: ProductRouteCapability = {
          path: route.path,
          stability: route.stability,
          directLinkAllowed: !suppressed,
          discoverable: !suppressed && route.stability !== 'preview',
          minimumRole: route.minimumRole,
        };

        if (route.featureFlag !== undefined) result.featureFlag = route.featureFlag;
        if (metadata.apiEndpoint) result.apiEndpoint = metadata.apiEndpoint;
        if (metadata.policyId) result.policyId = metadata.policyId;
        if (metadata.testId) result.testId = metadata.testId;
        if (metadata.apiContractId) result.apiContractId = metadata.apiContractId;
        if (metadata.dtoSchemaId) result.dtoSchemaId = metadata.dtoSchemaId;
        if (metadata.pluginDependencies) result.pluginDependencies = metadata.pluginDependencies;
        if (metadata.auditRequirement) result.auditRequirement = metadata.auditRequirement;
        if (metadata.phiSensitivity) result.phiSensitivity = metadata.phiSensitivity;
        if (metadata.cachePolicy) result.cachePolicy = metadata.cachePolicy;
        if (metadata.offlinePolicy) result.offlinePolicy = metadata.offlinePolicy;

        return result;
      }),
    };
  }

  generateJavaRouteConstants(): GeneratedJavaRouteConstants {
    return {
      packageName: this.packageName,
      className: this.className,
      version: this.contract.version,
      routes: this.contract.routes.map(route => {
        const metadata = this.routeMetadata(route);
        const constantName = this.pathToConstantName(route.path);
        const result: GeneratedJavaRouteConstants['routes'][0] = {
          path: route.path,
          constantName,
          label: route.label,
          minimumRole: route.minimumRole,
          stability: route.stability,
        };

        if (metadata.apiEndpoint) result.apiEndpoint = metadata.apiEndpoint;
        if (metadata.policyId) result.policyId = metadata.policyId;
        if (metadata.testId) result.testId = metadata.testId;
        if (metadata.apiContractId) result.apiContractId = metadata.apiContractId;
        if (metadata.dtoSchemaId) result.dtoSchemaId = metadata.dtoSchemaId;
        if (metadata.pluginDependencies) result.pluginDependencies = metadata.pluginDependencies;
        if (metadata.auditRequirement) result.auditRequirement = metadata.auditRequirement;
        if (metadata.phiSensitivity) result.phiSensitivity = metadata.phiSensitivity;
        if (metadata.cachePolicy) result.cachePolicy = metadata.cachePolicy;
        if (metadata.offlinePolicy) result.offlinePolicy = metadata.offlinePolicy;

        return result;
      }),
    };
  }

  private pathToConstantName(path: string): string {
    return path
      .replace(/[^a-zA-Z0-9/_-]/g, '_')
      .split('/')
      .filter(segment => segment.length > 0)
      .map(segment => segment.toUpperCase().replace(/[^A-Z0-9]/g, '_'))
      .join('_');
  }

  private routeMetadata(route: ProductRouteContract['routes'][number]): {
    apiEndpoint?: string;
    policyId?: string;
    testId?: string;
    featureFlag?: string;
    apiContractId?: string;
    dtoSchemaId?: string;
    pluginDependencies?: string[];
    auditRequirement?: RouteAuditRequirement;
    phiSensitivity?: RoutePhiSensitivity;
    cachePolicy?: RouteCachePolicy;
    offlinePolicy?: RouteOfflinePolicy;
  } {
    return {
      ...(route.metadata?.apiEndpoint !== undefined && { apiEndpoint: route.metadata.apiEndpoint }),
      ...(route.apiEndpoint !== undefined && { apiEndpoint: route.apiEndpoint }),
      ...(route.metadata?.policyId !== undefined && { policyId: route.metadata.policyId }),
      ...(route.policyId !== undefined && { policyId: route.policyId }),
      ...(route.metadata?.testId !== undefined && { testId: route.metadata.testId }),
      ...(route.testId !== undefined && { testId: route.testId }),
      ...(route.metadata?.featureFlag !== undefined && { featureFlag: route.metadata.featureFlag }),
      ...(route.apiContractId !== undefined && { apiContractId: route.apiContractId }),
      ...(route.dtoSchemaId !== undefined && { dtoSchemaId: route.dtoSchemaId }),
      ...(route.pluginDependencies !== undefined && { pluginDependencies: route.pluginDependencies }),
      ...(route.auditRequirement !== undefined && { auditRequirement: route.auditRequirement }),
      ...(route.phiSensitivity !== undefined && { phiSensitivity: route.phiSensitivity }),
      ...(route.cachePolicy !== undefined && { cachePolicy: route.cachePolicy }),
      ...(route.offlinePolicy !== undefined && { offlinePolicy: route.offlinePolicy }),
    };
  }

  generateAll(): {
    tsManifest: GeneratedTSManifest;
    backendEntitlement: GeneratedBackendEntitlement;
    routeDocs: GeneratedRouteDocs;
    routeCapabilities: GeneratedRouteCapabilityContract;
    javaRouteConstants: GeneratedJavaRouteConstants;
  } {
    return {
      tsManifest: this.generateTSManifest(),
      backendEntitlement: this.generateBackendEntitlement(),
      routeDocs: this.generateRouteDocs(),
      routeCapabilities: this.generateRouteCapabilities(),
      javaRouteConstants: this.generateJavaRouteConstants(),
    };
  }

  generateJavaSourceCode(): string {
    const constants = this.generateJavaRouteConstants();
    let code = `package ${constants.packageName};\n\n`;
    code += `/**\n`;
    code += ` * Auto-generated route constants for ${constants.className}.\n`;
    code += ` * Version: ${constants.version}\n`;
    code += ` * Generated from route contract. Do not edit manually.\n`;
    code += ` */\n`;
    code += `public final class ${constants.className} {\n\n`;
    code += `    private ${constants.className}() {\n`;
    code += `        // Utility class - prevent instantiation\n`;
    code += `    }\n\n`;

    for (const route of constants.routes) {
      code += `    /**\n`;
      code += `     * Route: ${route.path}\n`;
      code += `     * Label: ${route.label}\n`;
      code += `     * Minimum Role: ${route.minimumRole}\n`;
      code += `     * Stability: ${route.stability}\n`;
      if (route.apiEndpoint) code += `     * API Endpoint: ${route.apiEndpoint}\n`;
      if (route.policyId) code += `     * Policy ID: ${route.policyId}\n`;
      if (route.testId) code += `     * Test ID: ${route.testId}\n`;
      code += `     */\n`;
      code += `    public static final String ${route.constantName} = "${route.path}";\n\n`;
    }

    code += `    public static final String VERSION = "${constants.version}";\n`;
    code += `}\n`;

    return code;
  }
}

export function createRouteContractGenerator(contract: ProductRouteContract): RouteContractGenerator {
  return new RouteContractGenerator(contract);
}

export function validateGeneratedTSManifest(
  value: unknown
): value is GeneratedTSManifest {
  return GeneratedTSManifestSchema.safeParse(value).success;
}

export function validateGeneratedBackendEntitlement(
  value: unknown
): value is GeneratedBackendEntitlement {
  return GeneratedBackendEntitlementSchema.safeParse(value).success;
}

export function validateGeneratedRouteDocs(
  value: unknown
): value is GeneratedRouteDocs {
  return GeneratedRouteDocsSchema.safeParse(value).success;
}

export function validateGeneratedRouteCapabilityContract(
  value: unknown
): value is GeneratedRouteCapabilityContract {
  return GeneratedRouteCapabilityContractSchema.safeParse(value).success;
}
