/**
 * Schema-based conformance validation.
 * Uses Zod schemas to validate product manifests and configuration files.
 */

import { z } from 'zod';

/**
 * Product manifest schema for conformance validation.
 */
export const ProductManifestSchema = z.object({
  schemaVersion: z.string().min(1),
  product: z.string().min(1),
  kind: z.string().min(1),
  capabilities: z.unknown(),
  policies: z.unknown(),
  surfaces: z.unknown(),
  runtimeServices: z.unknown(),
});

export type ProductManifest = z.infer<typeof ProductManifestSchema>;

/**
 * Observability flow manifest schema for conformance validation.
 */
export const ObservabilityFlowSchema = z.object({
  schemaVersion: z.string().min(1).optional(),
  requiredFacets: z.array(z.string().min(1)).min(1),
  flows: z.array(z.object({
    product: z.string().min(1),
    flow: z.string().min(1),
    kind: z.enum(['api', 'bridge', 'background', 'frontend', 'job']),
    facets: z.array(z.string().min(1)).min(1),
    evidence: z.array(z.object({
      type: z.literal('behavior'),
      file: z.string().min(1),
      requiredFacets: z.array(z.string().min(1)).min(1),
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
