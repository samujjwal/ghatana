/**
 * ProductUnit - the fundamental abstraction for any governable, deployable artifact in Kernel.
 *
 * A ProductUnit represents a complete product or system that can undergo lifecycle operations.
 * It is product-neutral and can represent monorepo products, external repositories, services,
 * web apps, mobile apps, SDKs, plugins, domain packs, data pipelines, and agent runtimes.
 *
 * @doc.type interface
 * @doc.purpose Core ProductUnit abstraction for lifecycle operations
 * @doc.layer kernel-product-contracts
 * @doc.pattern AggregateRoot
 */

import { z } from "zod";
import type { ProductUnitKind } from "./ProductUnitKind.js";
import { PRODUCT_UNIT_KINDS } from "./ProductUnitKind.js";
import type { ProductUnitSurface } from "./ProductUnitSurface.js";
import {
  IMPLEMENTATION_STATUSES,
  PRODUCT_UNIT_SURFACE_TYPES,
} from "./ProductUnitSurface.js";
import type { ProviderRef } from "../provider/ProviderRef";

// Re-export ProductUnitSurface for convenience
export type { ProductUnitSurface };
export type { ProviderRef };

/**
 * Lifecycle status of a ProductUnit.
 */
export type LifecycleStatus = "disabled" | "planned" | "partial" | "enabled";

const LIFECYCLE_STATUSES = [
  "disabled",
  "planned",
  "partial",
  "enabled",
] as const satisfies readonly LifecycleStatus[];

const SECRET_KEY_PATTERN = /(secret|password|token|api[-_]?key|credential)/i;

const RecordSchema = z.record(z.string(), z.unknown());

/**
 * Tenant/workspace/project scope for ProductUnit truth.
 */
export interface ProductUnitScope {
  readonly tenantId: string;
  readonly workspaceId: string;
  readonly projectId: string;
}

/**
 * Draft ProductUnit shape for ideation and intent flows. Drafts are not executable.
 */
export interface ProductUnitDraft {
  readonly id: string;
  readonly name: string;
  readonly kind: ProductUnitKind;
  readonly scope?: ProductUnitScope;
  readonly owner?: string;
  readonly surfaces: readonly ProductUnitSurface[];
  readonly lifecycleProfile?: string;
  readonly metadata?: Record<string, unknown>;
}

/**
 * ProductUnit conformance requirements.
 */
export interface ProductUnitConformance {
  /**
   * Required conformance checks.
   */
  readonly requiredChecks: readonly string[];

  /**
   * Conformance level (e.g., "basic", "standard", "strict").
   */
  readonly level: string;

  /**
   * Exemptions from specific checks.
   */
  readonly exemptions?: readonly string[];
}

/**
 * ProductUnit governance configuration.
 */
export interface ProductUnitGovernance {
  /**
   * Required approval gates.
   */
  readonly approvalGates?: readonly string[];

  /**
   * Required verification gates.
   */
  readonly verificationGates?: readonly string[];

  /**
   * Security requirements.
   */
  readonly securityRequirements?: readonly string[];

  /**
   * Privacy requirements.
   */
  readonly privacyRequirements?: readonly string[];
}

/**
 * Represents a complete ProductUnit that can undergo lifecycle operations.
 */
export interface ProductUnit {
  /**
   * Schema version for ProductUnit contract compatibility.
   */
  readonly schemaVersion: "1.0.0";

  /**
   * Unique identifier for the ProductUnit.
   */
  readonly id: string;

  /**
   * Human-readable name of the ProductUnit.
   */
  readonly name: string;

  /**
   * Optional tenant/workspace/project scope for multi-tenant lifecycle truth.
   */
  readonly scope?: ProductUnitScope;

  /**
   * Kind of ProductUnit (determines governance and lifecycle treatment).
   */
  readonly kind: ProductUnitKind;

  /**
   * Owner or team responsible for the ProductUnit.
   */
  readonly owner?: string;

  /**
   * Reference to the registry provider for this ProductUnit.
   */
  readonly registryProviderRef: ProviderRef;

  /**
   * Reference to the source provider for this ProductUnit.
   */
  readonly sourceProviderRef: ProviderRef;

  /**
   * Deployable surfaces within this ProductUnit.
   */
  readonly surfaces: readonly ProductUnitSurface[];

  /**
   * Lifecycle profile name (e.g., "standard-web-api-product").
   */
  readonly lifecycleProfile?: string;

  /**
   * Current lifecycle execution status.
   */
  readonly lifecycleStatus?: LifecycleStatus;

  /**
   * Conformance requirements for this ProductUnit.
   */
  readonly conformance?: ProductUnitConformance;

  /**
   * Governance configuration for this ProductUnit.
   */
  readonly governance?: ProductUnitGovernance;

  /**
   * Additional metadata for the ProductUnit.
   */
  readonly metadata?: Record<string, unknown>;
}

export interface ProductUnitValidationResult {
  readonly valid: boolean;
  readonly errors: readonly string[];
}

function hasProviderId(value: unknown): value is ProviderRef {
  if (typeof value !== "object" || value === null) {
    return false;
  }

  const providerRef = value as Record<string, unknown>;
  return (
    typeof providerRef.providerId === "string" &&
    providerRef.providerId.trim().length > 0
  );
}

function hasSecretLikeField(value: unknown): boolean {
  if (Array.isArray(value)) {
    return value.some((item: unknown) => hasSecretLikeField(item));
  }
  if (typeof value !== "object" || value === null) {
    return false;
  }

  return Object.entries(value as Record<string, unknown>).some(([key, nested]) => {
    if (SECRET_KEY_PATTERN.test(key)) {
      return true;
    }
    return hasSecretLikeField(nested);
  });
}

function hasStringConfigValue(
  providerRef: { readonly config?: Record<string, unknown> | undefined },
  key: string
): boolean {
  const config = providerRef.config;
  if (config === undefined) {
    return false;
  }
  const value = config[key];
  return typeof value === "string" && value.trim().length > 0;
}

export const ProductUnitScopeSchema = z
  .object({
    tenantId: z.string().trim().min(1),
    workspaceId: z.string().trim().min(1),
    projectId: z.string().trim().min(1),
  })
  .strict();

export const ProductUnitSurfaceSchema = z
  .object({
    id: z.string().trim().min(1),
    type: z.enum(PRODUCT_UNIT_SURFACE_TYPES),
    sourceRef: z.string().trim().min(1).optional(),
    implementationStatus: z.enum(IMPLEMENTATION_STATUSES),
    runtime: z.string().trim().min(1).optional(),
    packagePath: z.string().trim().min(1).optional(),
    gradleModule: z.string().trim().min(1).optional(),
    adapterHint: z.string().trim().min(1).optional(),
  })
  .strict();

export const ProviderRefSchema = z
  .object({
    providerId: z.string().trim().min(1),
    config: RecordSchema.optional(),
  })
  .strict();

export const ProductUnitDraftSchema = z
  .object({
    id: z.string().trim().min(1),
    name: z.string().trim().min(1),
    kind: z.enum(PRODUCT_UNIT_KINDS),
    scope: ProductUnitScopeSchema.optional(),
    owner: z.string().trim().min(1).optional(),
    surfaces: z.array(ProductUnitSurfaceSchema),
    lifecycleProfile: z.string().trim().min(1).optional(),
    metadata: RecordSchema.optional(),
  })
  .strict();

export const ProductUnitSchema = z
  .object({
    schemaVersion: z.literal("1.0.0"),
    id: z.string().trim().min(1),
    name: z.string().trim().min(1),
    scope: ProductUnitScopeSchema.optional(),
    kind: z.enum(PRODUCT_UNIT_KINDS),
    owner: z.string().trim().min(1).optional(),
    registryProviderRef: ProviderRefSchema,
    sourceProviderRef: ProviderRefSchema,
    surfaces: z.array(ProductUnitSurfaceSchema).min(1),
    lifecycleProfile: z.string().trim().min(1).optional(),
    lifecycleStatus: z.enum(LIFECYCLE_STATUSES).optional(),
    conformance: z
      .object({
        requiredChecks: z.array(z.string().trim().min(1)),
        level: z.string().trim().min(1),
        exemptions: z.array(z.string().trim().min(1)).optional(),
      })
      .strict()
      .optional(),
    governance: z
      .object({
        approvalGates: z.array(z.string().trim().min(1)).optional(),
        verificationGates: z.array(z.string().trim().min(1)).optional(),
        securityRequirements: z.array(z.string().trim().min(1)).optional(),
        privacyRequirements: z.array(z.string().trim().min(1)).optional(),
      })
      .strict()
      .optional(),
    metadata: RecordSchema.optional(),
  })
  .strict()
  .superRefine((productUnit, context) => {
    if (hasSecretLikeField(productUnit.metadata)) {
      context.addIssue({
        code: "custom",
        path: ["metadata"],
        message: "metadata must not include raw secret-like fields",
      });
    }
    if (
      hasSecretLikeField(productUnit.registryProviderRef.config) ||
      hasSecretLikeField(productUnit.sourceProviderRef.config)
    ) {
      context.addIssue({
        code: "custom",
        path: ["sourceProviderRef", "config"],
        message: "provider config must not include raw secret-like fields",
      });
    }
    if (
      productUnit.lifecycleStatus === "enabled" &&
      productUnit.lifecycleProfile === undefined
    ) {
      context.addIssue({
        code: "custom",
        path: ["lifecycleProfile"],
        message: "enabled lifecycle requires lifecycleProfile",
      });
    }
    if (
      productUnit.lifecycleStatus === "enabled" &&
      productUnit.sourceProviderRef.providerId === "ghatana-file-registry" &&
      !hasStringConfigValue(productUnit.sourceProviderRef, "lifecycleConfigPath")
    ) {
      context.addIssue({
        code: "custom",
        path: ["sourceProviderRef", "config", "lifecycleConfigPath"],
        message:
          "file-backed enabled lifecycle requires sourceProviderRef.config.lifecycleConfigPath",
      });
    }
  });

function formatProductUnitIssue(issue: z.ZodIssue): string {
  const path = issue.path.join(".");
  if (path === "schemaVersion") {
    return 'schemaVersion must be "1.0.0"';
  }
  if (path === "id") {
    return "id must be a non-empty string";
  }
  if (path === "name") {
    return "name must be a non-empty string";
  }
  if (path === "kind") {
    return "kind is not a known ProductUnit kind";
  }
  if (path === "registryProviderRef.providerId") {
    return "registryProviderRef.providerId must be a non-empty string";
  }
  if (path === "sourceProviderRef.providerId") {
    return "sourceProviderRef.providerId must be a non-empty string";
  }
  if (path === "surfaces") {
    return "surfaces must contain at least one surface";
  }
  if (/^surfaces\.\d+\.type$/.test(path)) {
    const index = path.split(".")[1];
    return `surfaces[${index}].type is not a known ProductUnit surface type`;
  }
  if (/^surfaces\.\d+\.implementationStatus$/.test(path)) {
    const index = path.split(".")[1];
    return `surfaces[${index}].implementationStatus is not a known implementation status`;
  }
  return issue.message;
}

export function validateProductUnit(value: unknown): ProductUnitValidationResult {
  if (typeof value !== "object" || value === null) {
    return { valid: false, errors: ["ProductUnit must be an object"] };
  }

  const pu = value as { readonly registryProviderRef?: unknown; readonly sourceProviderRef?: unknown };
  const providerErrors: string[] = [];
  if (!hasProviderId(pu.registryProviderRef)) {
    providerErrors.push("registryProviderRef.providerId must be a non-empty string");
  }
  if (!hasProviderId(pu.sourceProviderRef)) {
    providerErrors.push("sourceProviderRef.providerId must be a non-empty string");
  }
  const parsed = ProductUnitSchema.safeParse(value);
  const errors = parsed.success
    ? []
    : parsed.error.issues.map((issue: z.ZodIssue) => formatProductUnitIssue(issue));

  return { valid: providerErrors.length === 0 && errors.length === 0, errors: [...providerErrors, ...errors] };
}

/**
 * Type guard to check if an object is a valid ProductUnit.
 */
export function isProductUnit(value: unknown): value is ProductUnit {
  return validateProductUnit(value).valid;
}

/**
 * Creates a draft-only ProductUnit skeleton for callers that need to seed ideation flows.
 * Draft skeletons are intentionally not accepted as executable ProductUnits.
 */
export function createMinimalProductUnit(
  id: string,
  name: string,
  kind: ProductUnitKind
): ProductUnitDraft {
  return createProductUnitDraftSkeleton(id, name, kind);
}

export function createProductUnitDraftSkeleton(
  id: string,
  name: string,
  kind: ProductUnitKind
): ProductUnitDraft {
  return {
    id,
    name,
    kind,
    surfaces: [],
  };
}

export interface CreateExecutableProductUnitInput {
  readonly id: string;
  readonly name: string;
  readonly kind: ProductUnitKind;
  readonly surfaces: readonly ProductUnitSurface[];
  readonly scope?: ProductUnitScope;
  readonly owner?: string;
  readonly registryProviderRef?: ProviderRef;
  readonly sourceProviderRef?: ProviderRef;
  readonly lifecycleProfile?: string;
  readonly lifecycleStatus?: LifecycleStatus;
  readonly conformance?: ProductUnitConformance;
  readonly governance?: ProductUnitGovernance;
  readonly metadata?: Record<string, unknown>;
}

export function createExecutableProductUnit(
  input: CreateExecutableProductUnitInput
): ProductUnit {
  const productUnit: ProductUnit = {
    schemaVersion: "1.0.0",
    id: input.id,
    name: input.name,
    kind: input.kind,
    registryProviderRef: input.registryProviderRef ?? {
      providerId: "ghatana-file-registry",
    },
    sourceProviderRef: input.sourceProviderRef ?? {
      providerId: "ghatana-file-registry",
      config: { lifecycleConfigPath: "kernel-product.yaml" },
    },
    surfaces: input.surfaces,
    ...(input.scope === undefined ? {} : { scope: input.scope }),
    ...(input.owner === undefined ? {} : { owner: input.owner }),
    ...(input.lifecycleProfile === undefined
      ? {}
      : { lifecycleProfile: input.lifecycleProfile }),
    ...(input.lifecycleStatus === undefined
      ? {}
      : { lifecycleStatus: input.lifecycleStatus }),
    ...(input.conformance === undefined ? {} : { conformance: input.conformance }),
    ...(input.governance === undefined ? {} : { governance: input.governance }),
    ...(input.metadata === undefined ? {} : { metadata: input.metadata }),
  };
  const result = validateProductUnit(productUnit);
  if (!result.valid) {
    throw new Error(`Invalid executable ProductUnit: ${result.errors.join("; ")}`);
  }
  return productUnit;
}
