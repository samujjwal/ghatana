/**
 * Schema-based conformance validation.
 * Uses Zod schemas to validate product manifests and configuration files.
 */

import { z } from 'zod';

/**
 * Product manifest schema for conformance validation.
 */
export const ProductManifestSchema = z.object({
  id: z.string().min(1),
  productName: z.string().min(1),
  productCode: z.string().min(1),
  version: z.string().regex(/^\d+\.\d+\.\d+$/),
  mvpScope: z.string().min(1),
  boundaryPolicyStoreClass: z.string().optional(),
  pluginBindingsClass: z.string().optional(),
  complianceRulePackClass: z.string().optional(),
  kernelCapabilitiesConsumed: z.array(z.string().min(1)).min(1),
  policyActions: z.array(z.string().min(1)).min(1),
  policyResources: z.array(z.string().min(1)).min(1),
  pluginsConsumed: z.array(z.string().min(1)),
  bridgesConsumed: z.array(z.string().min(1)),
  domainPacksProvided: z.array(z.string().min(1)),
  uiSurfaces: z.array(z.enum(['web', 'mobile', 'cli'])).min(1),
  runtimeServices: z.array(z.string().min(1)).min(1),
  dataSensitivity: z.string().min(1),
  complianceRuleSets: z.array(z.string().min(1)),
  pluginBindings: z.record(z.string(), z.enum(['enabled', 'disabled'])),
  referenceConsumerPolicy: z.object({
    phrFinanceStatus: z.enum(['reference-only', 'full-access', 'no-access']),
    forbiddenRulePrefixes: z.array(z.string().min(1)),
  }).optional(),
});

export type ProductManifest = z.infer<typeof ProductManifestSchema>;

/**
 * Observability flow manifest schema for conformance validation.
 */
export const ObservabilityFlowSchema = z.object({
  requiredFacets: z.array(z.string().min(1)).min(1),
  flows: z.array(z.object({
    product: z.string().min(1),
    flow: z.string().min(1),
    kind: z.enum(['api', 'bridge', 'worker', 'scheduler']),
    facets: z.array(z.string().min(1)).min(1),
    evidence: z.array(z.object({
      file: z.string().min(1),
      tokens: z.array(z.string().min(1)).min(1),
    })).min(1),
  })).min(1),
});

export type ObservabilityFlow = z.infer<typeof ObservabilityFlowSchema>;

/**
 * Validates a product manifest against the schema.
 *
 * @throws {z.ZodError} if validation fails
 */
export function validateProductManifest(data: unknown): ProductManifest {
  return ProductManifestSchema.parse(data);
}

/**
 * Validates an observability flow manifest against the schema.
 *
 * @throws {z.ZodError} if validation fails
 */
export function validateObservabilityFlow(data: unknown): ObservabilityFlow {
  return ObservabilityFlowSchema.parse(data);
}

/**
 * Checks if a value conforms to the product manifest schema without throwing.
 */
export function checkProductManifest(data: unknown): data is ProductManifest {
  return ProductManifestSchema.safeParse(data).success;
}

/**
 * Checks if a value conforms to the observability flow schema without throwing.
 */
export function checkObservabilityFlow(data: unknown): data is ObservabilityFlow {
  return ObservabilityFlowSchema.safeParse(data).success;
}
