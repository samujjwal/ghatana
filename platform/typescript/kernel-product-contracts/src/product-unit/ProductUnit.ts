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
  PRODUCT_SURFACE_BUILD_SYSTEMS,
  PRODUCT_SURFACE_LANGUAGES,
  PRODUCT_SURFACE_RUNTIMES,
  PRODUCT_UNIT_SURFACE_TYPES,
  isValidLanguageRuntimeBuildSystemCombination,
} from "./ProductUnitSurface.js";
import type { ProductShape } from "./ProductShape.js";
import { PRODUCT_SHAPES } from "./ProductShape.js";
import type { ProductUnitSourceRef } from "./ProductUnitSourceRef.js";
import { ProductUnitSourceRefSchema } from "./ProductUnitSourceRef.js";
import type { ProviderRef } from "../provider/ProviderRef";
import type { ProductEnvironment } from "../environment/ProductEnvironment";
import type { KernelPluginBinding } from "../plugin/KernelPluginBinding";
import type { RequiredGateReference } from "../gate/GateContracts";
import type { ProductArtifact } from "../artifact/ProductArtifact";
import type { ProductDeployment } from "../deployment/ProductDeployment";
import type { AgentLifecycleActionEvidence } from "../agentic/AgentLifecycleActionEvidence";
import type { ProductInteractionDeclaration } from "../product-interaction/ProductInteractionContract.js";
import { ProductInteractionDeclarationSchema } from "../product-interaction/ProductInteractionContract.js";

// Re-export ProductUnitSurface for convenience
export type { ProductUnitSurface };
export type { ProviderRef };
export type { ProductEnvironment };
export type { KernelPluginBinding };
export type { RequiredGateReference };
export type { ProductArtifact };
export type { ProductDeployment };
export type { AgentLifecycleActionEvidence };
export type { ProductInteractionDeclaration };

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

export const LifecycleStatusSchema = z.enum(LIFECYCLE_STATUSES);

/**
 * Provider mode for lifecycle execution.
 */
export type ProviderMode = "bootstrap" | "platform";

const PROVIDER_MODES = [
  "bootstrap",
  "platform",
] as const satisfies readonly ProviderMode[];

export const ProviderModeSchema = z.enum(PROVIDER_MODES);

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
  readonly schemaVersion?: "1.0.0" | undefined;
  readonly id: string;
  readonly name: string;
  readonly kind: ProductUnitKind;
  readonly scope?: ProductUnitScope | undefined;
  readonly owner?: string | undefined;
  readonly surfaces: readonly ProductUnitSurface[];
  readonly productShape?: ProductShape | undefined;
  readonly sourceRefs?: readonly ProductUnitSourceRef[] | undefined;
  readonly lifecycleProfile?: string | undefined;
  readonly semanticArtifactRefs?: readonly string[] | undefined;
  readonly interactions?: ProductInteractionDeclaration | undefined;
  readonly metadata?: Record<string, unknown> | undefined;
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
  readonly exemptions?: readonly string[] | undefined;
}

/**
 * ProductUnit governance configuration.
 */
export interface ProductUnitGovernance {
  /**
   * Required approval gates.
   */
  readonly approvalGates?: readonly string[] | undefined;

  /**
   * Required verification gates.
   */
  readonly verificationGates?: readonly string[] | undefined;

  /**
   * Security requirements.
   */
  readonly securityRequirements?: readonly string[] | undefined;

  /**
   * Privacy requirements.
   */
  readonly privacyRequirements?: readonly string[] | undefined;
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
  readonly scope?: ProductUnitScope | undefined;

  /**
   * Kind of ProductUnit (determines governance and lifecycle treatment).
   */
  readonly kind: ProductUnitKind;

  /**
   * Owner or team responsible for the ProductUnit.
   */
  readonly owner?: string | undefined;

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
   * Product-neutral lifecycle capability shape.
   */
  readonly productShape?: ProductShape | undefined;

  /**
   * Source acquisition references for monorepo/external/archive/generated inputs.
   */
  readonly sourceRefs?: readonly ProductUnitSourceRef[] | undefined;

  /**
   * Lifecycle profile name (e.g., "standard-web-api-product").
   */
  readonly lifecycleProfile?: string | undefined;

  /**
   * Current lifecycle execution status.
   */
  readonly lifecycleStatus?: LifecycleStatus | undefined;

  /**
   * Semantic artifact evidence references shared by contract with Studio/YAPPC.
   */
  readonly semanticArtifactRefs?: readonly string[] | undefined;

  /**
   * Provider mode for lifecycle execution (bootstrap or platform).
   */
  readonly providerMode?: ProviderMode | undefined;

  /**
   * Environment configurations for this ProductUnit.
   */
  readonly environments?: readonly ProductEnvironment[] | undefined;

  /**
   * Plugin bindings for this ProductUnit.
   */
  readonly pluginBindings?: readonly KernelPluginBinding[] | undefined;

  /**
   * Required gate references for this ProductUnit.
   */
  readonly gateBindings?: readonly RequiredGateReference[] | undefined;

  /**
   * Policy pack references for this ProductUnit.
   */
  readonly policyPacks?: readonly string[] | undefined;

  /**
   * Artifact configurations for this ProductUnit.
   */
  readonly artifacts?: readonly ProductArtifact[] | undefined;

  /**
   * Deployment configurations for this ProductUnit.
   */
  readonly deployments?: readonly ProductDeployment[] | undefined;

  /**
   * Release configuration for this ProductUnit.
   */
  readonly releaseConfig?: Record<string, unknown> | undefined;

  /**
   * Health configuration for this ProductUnit.
   */
  readonly healthConfig?: Record<string, unknown> | undefined;

  /**
   * Agentic action evidence references for this ProductUnit.
   */
  readonly agenticActionEvidence?: readonly AgentLifecycleActionEvidence[] | undefined;

  /**
   * Declarative cross-product interaction contracts.
   */
  readonly interactions?: ProductInteractionDeclaration | undefined;

  /**
   * Conformance requirements for this ProductUnit.
   */
  readonly conformance?: ProductUnitConformance | undefined;

  /**
   * Governance configuration for this ProductUnit.
   */
  readonly governance?: ProductUnitGovernance | undefined;

  /**
   * Additional metadata for the ProductUnit.
   */
  readonly metadata?: Record<string, unknown> | undefined;
}

export interface ProductUnitValidationResult {
  readonly valid: boolean;
  readonly errors: readonly string[];
}

export type ProductUnitValidationReasonCode =
  | "missing-scope"
  | "missing-lifecycle-config-path"
  | "missing-lifecycle-profile"
  | "missing-executable-surfaces"
  | "invalid-provider-ref"
  | "secret-like-config-or-metadata"
  | "schema-invalid";

export type ProductUnitValidationSeverity = "error" | "warning";

export interface ProductUnitValidationIssue {
  readonly path: string;
  readonly reasonCode: ProductUnitValidationReasonCode;
  readonly message: string;
  readonly severity: ProductUnitValidationSeverity;
}

export interface ProductUnitDetailedValidationResult extends ProductUnitValidationResult {
  readonly issues: readonly ProductUnitValidationIssue[];
}

export type ExecutableProductUnit = ProductUnit & {
  readonly scope: ProductUnitScope;
  readonly lifecycleProfile: string;
  readonly lifecycleStatus: "enabled";
  readonly surfaces: readonly [ProductUnitSurface, ...ProductUnitSurface[]];
  readonly sourceProviderRef: ProviderRef & {
    readonly config: Record<string, unknown> & {
      readonly lifecycleConfigPath: string;
    };
  };
};

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
    language: z.enum(PRODUCT_SURFACE_LANGUAGES).optional(),
    languageVersion: z.string().trim().min(1).optional(),
    runtime: z.enum(PRODUCT_SURFACE_RUNTIMES).optional(),
    runtimeVersion: z.string().trim().min(1).optional(),
    buildSystem: z.enum(PRODUCT_SURFACE_BUILD_SYSTEMS).optional(),
    buildSystemVersion: z.string().trim().min(1).optional(),
    packagePath: z.string().trim().min(1).optional(),
    gradleModule: z.string().trim().min(1).optional(),
    cratePath: z.string().trim().min(1).optional(),
    cargoToml: z.string().trim().min(1).optional(),
    pyprojectPath: z.string().trim().min(1).optional(),
    adapterHint: z.string().trim().min(1).optional(),
  })
  .strict()
  .superRefine((surface, context) => {
    // Require language/runtime/buildSystem for implemented surfaces
    if (surface.implementationStatus === "implemented") {
      if (surface.language === undefined) {
        context.addIssue({
          code: "custom",
          path: ["language"],
          message: "language is required for implemented surfaces",
        });
      }
      if (surface.runtime === undefined) {
        context.addIssue({
          code: "custom",
          path: ["runtime"],
          message: "runtime is required for implemented surfaces",
        });
      }
      if (surface.buildSystem === undefined) {
        context.addIssue({
          code: "custom",
          path: ["buildSystem"],
          message: "buildSystem is required for implemented surfaces",
        });
      }
    }

    // Validate language/runtime/buildSystem combination when all are present
    if (
      surface.language !== undefined &&
      surface.runtime !== undefined &&
      surface.buildSystem !== undefined
    ) {
      if (!isValidLanguageRuntimeBuildSystemCombination(surface.language, surface.runtime, surface.buildSystem)) {
        context.addIssue({
          code: "custom",
          path: ["language"],
          message: `Invalid combination: language="${surface.language}", runtime="${surface.runtime}", buildSystem="${surface.buildSystem}". This combination is not supported by Kernel lifecycle adapters.`,
        });
      }
    }
  });

export const ProviderRefSchema = z
  .object({
    providerId: z.string().trim().min(1),
    config: RecordSchema.optional(),
  })
  .strict();

export const ProductUnitConformanceSchema = z
  .object({
    requiredChecks: z.array(z.string().trim().min(1)),
    level: z.string().trim().min(1),
    exemptions: z.array(z.string().trim().min(1)).optional(),
  })
  .strict();

export const ProductUnitGovernanceSchema = z
  .object({
    approvalGates: z.array(z.string().trim().min(1)).optional(),
    verificationGates: z.array(z.string().trim().min(1)).optional(),
    securityRequirements: z.array(z.string().trim().min(1)).optional(),
    privacyRequirements: z.array(z.string().trim().min(1)).optional(),
  })
  .strict();

export const ProductUnitValidationResultSchema = z
  .object({
    valid: z.boolean(),
    errors: z.array(z.string()),
  })
  .strict();

export const ProductUnitValidationReasonCodeSchema = z.enum([
  "missing-scope",
  "missing-lifecycle-config-path",
  "missing-lifecycle-profile",
  "missing-executable-surfaces",
  "invalid-provider-ref",
  "secret-like-config-or-metadata",
  "schema-invalid",
]);

export const ProductUnitValidationSeveritySchema = z.enum(["error", "warning"]);

export const ProductUnitValidationIssueSchema = z
  .object({
    path: z.string(),
    reasonCode: ProductUnitValidationReasonCodeSchema,
    message: z.string(),
    severity: ProductUnitValidationSeveritySchema,
  })
  .strict();

export const ProductUnitDetailedValidationResultSchema =
  ProductUnitValidationResultSchema.extend({
    issues: z.array(ProductUnitValidationIssueSchema),
  });

export const ProductUnitDraftSchema = z
  .object({
    schemaVersion: z.literal("1.0.0").optional(),
    id: z.string().trim().min(1),
    name: z.string().trim().min(1),
    kind: z.enum(PRODUCT_UNIT_KINDS),
    scope: ProductUnitScopeSchema.optional(),
    owner: z.string().trim().min(1).optional(),
    surfaces: z.array(ProductUnitSurfaceSchema),
    productShape: z.enum(PRODUCT_SHAPES).optional(),
    sourceRefs: z.array(ProductUnitSourceRefSchema).optional(),
    lifecycleProfile: z.string().trim().min(1).optional(),
    semanticArtifactRefs: z.array(z.string().trim().min(1)).optional(),
    interactions: ProductInteractionDeclarationSchema.optional(),
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
    productShape: z.enum(PRODUCT_SHAPES).optional(),
    sourceRefs: z.array(ProductUnitSourceRefSchema).optional(),
    lifecycleProfile: z.string().trim().min(1).optional(),
    lifecycleStatus: LifecycleStatusSchema.optional(),
    semanticArtifactRefs: z.array(z.string().trim().min(1)).optional(),
    providerMode: ProviderModeSchema.optional(),
    environments: z.array(z.object({ name: z.string(), target: z.string().optional(), variables: z.record(z.string(), z.string()).optional() })).optional(),
    pluginBindings: z.array(z.object({ id: z.string(), pluginRef: z.object({ pluginId: z.string() }), productUnitId: z.string(), enabled: z.boolean(), lifecycleHooks: z.array(z.string()), priority: z.number() })).optional(),
    gateBindings: z.array(z.object({ gateId: z.string(), phase: z.string(), required: z.boolean(), providerId: z.string().optional() })).optional(),
    policyPacks: z.array(z.string().trim().min(1)).optional(),
    artifacts: z.array(z.object({ type: z.string(), packaging: z.string().optional(), required: z.boolean().optional(), paths: z.array(z.string()).optional() })).optional(),
    deployments: z.array(z.object({ target: z.string(), environment: z.string() })).optional(),
    releaseConfig: RecordSchema.optional(),
    healthConfig: RecordSchema.optional(),
    agenticActionEvidence: z.array(z.object({ evidenceId: z.string(), kind: z.string(), ref: z.string(), capturedAt: z.string(), redacted: z.boolean(), providedByAgentId: z.string().optional(), description: z.string().optional() })).optional(),
    interactions: ProductInteractionDeclarationSchema.optional(),
    conformance: ProductUnitConformanceSchema.optional(),
    governance: ProductUnitGovernanceSchema.optional(),
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

export const ExecutableProductUnitSchema = ProductUnitSchema.superRefine(
  (productUnit, context) => {
    if (productUnit.lifecycleStatus !== "enabled") {
      context.addIssue({
        code: "custom",
        path: ["lifecycleStatus"],
        message: 'executable ProductUnit requires lifecycleStatus "enabled"',
      });
    }
    if (productUnit.scope === undefined) {
      context.addIssue({
        code: "custom",
        path: ["scope"],
        message: "executable ProductUnit requires scope",
      });
    }
    if (productUnit.lifecycleProfile === undefined) {
      context.addIssue({
        code: "custom",
        path: ["lifecycleProfile"],
        message: "executable ProductUnit requires lifecycleProfile",
      });
    }
    if (productUnit.surfaces.length === 0) {
      context.addIssue({
        code: "custom",
        path: ["surfaces"],
        message: "executable ProductUnit requires at least one surface",
      });
    }
    if (!hasStringConfigValue(productUnit.sourceProviderRef, "lifecycleConfigPath")) {
      context.addIssue({
        code: "custom",
        path: ["sourceProviderRef", "config", "lifecycleConfigPath"],
        message:
          "executable ProductUnit requires sourceProviderRef.config.lifecycleConfigPath",
      });
    }
  }
);

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
  if (/^surfaces\.\d+\.language$/.test(path)) {
    const index = path.split(".")[1];
    return `surfaces[${index}].language is not a known surface language`;
  }
  if (/^surfaces\.\d+\.buildSystem$/.test(path)) {
    const index = path.split(".")[1];
    return `surfaces[${index}].buildSystem is not a known surface build system`;
  }
  return issue.message;
}

function reasonCodeForProductUnitIssue(
  path: string,
  message: string
): ProductUnitValidationReasonCode {
  if (path === "scope") {
    return "missing-scope";
  }
  if (path === "lifecycleProfile") {
    return "missing-lifecycle-profile";
  }
  if (path === "sourceProviderRef.config.lifecycleConfigPath") {
    return "missing-lifecycle-config-path";
  }
  if (path === "surfaces" || path.startsWith("surfaces.")) {
    return "missing-executable-surfaces";
  }
  if (path.includes("ProviderRef") || path.includes("providerRef")) {
    return "invalid-provider-ref";
  }
  if (message.includes("secret-like")) {
    return "secret-like-config-or-metadata";
  }
  return "schema-invalid";
}

function toProductUnitIssue(
  path: string,
  reasonCode: ProductUnitValidationReasonCode,
  message: string
): ProductUnitValidationIssue {
  return {
    path,
    reasonCode,
    message,
    severity: "error",
  };
}

function addExecutableProductUnitIssues(
  value: unknown,
  issues: ProductUnitValidationIssue[]
): void {
  if (typeof value !== "object" || value === null) {
    return;
  }

  const productUnit = value as {
    readonly lifecycleStatus?: unknown;
    readonly lifecycleProfile?: unknown;
    readonly scope?: unknown;
    readonly surfaces?: unknown;
    readonly sourceProviderRef?: unknown;
  };

  if (productUnit.lifecycleStatus !== "enabled") {
    return;
  }

  if (productUnit.scope === undefined) {
    issues.push(
      toProductUnitIssue(
        "scope",
        "missing-scope",
        "enabled lifecycle requires scope"
      )
    );
  }
  if (typeof productUnit.lifecycleProfile !== "string" || productUnit.lifecycleProfile.trim().length === 0) {
    issues.push(
      toProductUnitIssue(
        "lifecycleProfile",
        "missing-lifecycle-profile",
        "enabled lifecycle requires lifecycleProfile"
      )
    );
  }
  if (!Array.isArray(productUnit.surfaces) || productUnit.surfaces.length === 0) {
    issues.push(
      toProductUnitIssue(
        "surfaces",
        "missing-executable-surfaces",
        "enabled lifecycle requires at least one executable surface"
      )
    );
  }

  const sourceProviderRef = productUnit.sourceProviderRef;
  if (
    typeof sourceProviderRef === "object" &&
    sourceProviderRef !== null &&
    (sourceProviderRef as { readonly providerId?: unknown }).providerId === "ghatana-file-registry" &&
    !hasStringConfigValue(
      sourceProviderRef as { readonly config?: Record<string, unknown> },
      "lifecycleConfigPath"
    )
  ) {
    issues.push(
      toProductUnitIssue(
        "sourceProviderRef.config.lifecycleConfigPath",
        "missing-lifecycle-config-path",
        "file-backed enabled lifecycle requires sourceProviderRef.config.lifecycleConfigPath"
      )
    );
  }
}

export function validateProductUnitDetailed(
  value: unknown
): ProductUnitDetailedValidationResult {
  if (typeof value !== "object" || value === null) {
    const issue = toProductUnitIssue(
      "",
      "schema-invalid",
      "ProductUnit must be an object"
    );
    return { valid: false, errors: [issue.message], issues: [issue] };
  }

  const pu = value as { readonly registryProviderRef?: unknown; readonly sourceProviderRef?: unknown };
  const issues: ProductUnitValidationIssue[] = [];
  if (!hasProviderId(pu.registryProviderRef)) {
    issues.push(
      toProductUnitIssue(
        "registryProviderRef.providerId",
        "invalid-provider-ref",
        "registryProviderRef.providerId must be a non-empty string"
      )
    );
  }
  if (!hasProviderId(pu.sourceProviderRef)) {
    issues.push(
      toProductUnitIssue(
        "sourceProviderRef.providerId",
        "invalid-provider-ref",
        "sourceProviderRef.providerId must be a non-empty string"
      )
    );
  }
  const parsed = ProductUnitSchema.safeParse(value);
  if (!parsed.success) {
    for (const issue of parsed.error.issues) {
      const path = issue.path.join(".");
      const message = formatProductUnitIssue(issue);
      issues.push(
        toProductUnitIssue(path, reasonCodeForProductUnitIssue(path, message), message)
      );
    }
  }

  addExecutableProductUnitIssues(value, issues);

  const errors = issues.map((issue) => issue.message);

  return { valid: errors.length === 0, errors, issues };
}

export function validateProductUnit(value: unknown): ProductUnitValidationResult {
  const result = validateProductUnitDetailed(value);
  return { valid: result.valid, errors: result.errors };
}

export function validateLifecycleStatus(value: unknown): value is LifecycleStatus {
  return LifecycleStatusSchema.safeParse(value).success;
}

export function validateProviderMode(value: unknown): value is ProviderMode {
  return ProviderModeSchema.safeParse(value).success;
}

export function validateProductUnitConformance(
  value: unknown
): value is ProductUnitConformance {
  return ProductUnitConformanceSchema.safeParse(value).success;
}

export function validateProductUnitGovernance(
  value: unknown
): value is ProductUnitGovernance {
  return ProductUnitGovernanceSchema.safeParse(value).success;
}

export function validateProductUnitValidationResult(
  value: unknown
): value is ProductUnitValidationResult {
  return ProductUnitValidationResultSchema.safeParse(value).success;
}

export function validateProductUnitValidationReasonCode(
  value: unknown
): value is ProductUnitValidationReasonCode {
  return ProductUnitValidationReasonCodeSchema.safeParse(value).success;
}

export function validateProductUnitValidationSeverity(
  value: unknown
): value is ProductUnitValidationSeverity {
  return ProductUnitValidationSeveritySchema.safeParse(value).success;
}

export function validateProductUnitValidationIssue(
  value: unknown
): value is ProductUnitValidationIssue {
  return ProductUnitValidationIssueSchema.safeParse(value).success;
}

export function validateProductUnitDetailedValidationResult(
  value: unknown
): value is ProductUnitDetailedValidationResult {
  return ProductUnitDetailedValidationResultSchema.safeParse(value).success;
}

export function validateExecutableProductUnit(
  value: unknown
): value is ExecutableProductUnit {
  return ExecutableProductUnitSchema.safeParse(value).success;
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

export const CreateExecutableProductUnitInputSchema = z
  .object({
    id: z.string().trim().min(1),
    name: z.string().trim().min(1),
    kind: z.enum(PRODUCT_UNIT_KINDS),
    surfaces: z.array(ProductUnitSurfaceSchema).min(1),
    scope: ProductUnitScopeSchema.optional(),
    owner: z.string().trim().min(1).optional(),
    registryProviderRef: ProviderRefSchema.optional(),
    sourceProviderRef: ProviderRefSchema.optional(),
    lifecycleProfile: z.string().trim().min(1).optional(),
    lifecycleStatus: LifecycleStatusSchema.optional(),
    conformance: ProductUnitConformanceSchema.optional(),
    governance: ProductUnitGovernanceSchema.optional(),
    metadata: RecordSchema.optional(),
  })
  .strict();

export function validateCreateExecutableProductUnitInput(
  value: unknown
): value is CreateExecutableProductUnitInput {
  return CreateExecutableProductUnitInputSchema.safeParse(value).success;
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

/**
 * Projection input from canonical product registry and kernel-product.yaml.
 */
export interface ProductUnitProjectionInput {
  readonly productId: string;
  readonly productName: string;
  readonly productKind: ProductUnitKind;
  readonly lifecycleStatus: LifecycleStatus;
  readonly lifecycleExecutionAllowed: boolean;
  readonly lifecycleConfigPath: string;
  readonly surfaces: readonly ProductUnitSurface[];
  readonly productShape?: ProductShape;
  readonly sourceRefs?: readonly ProductUnitSourceRef[];
  readonly lifecycleProfile?: string;
  readonly owner?: string;
  readonly scope?: ProductUnitScope;
  readonly providerMode?: ProviderMode;
  readonly environments?: readonly ProductEnvironment[];
  readonly pluginBindings?: readonly KernelPluginBinding[];
  readonly gateBindings?: readonly RequiredGateReference[];
  readonly policyPacks?: readonly string[];
  readonly artifacts?: readonly ProductArtifact[];
  readonly deployments?: readonly ProductDeployment[];
  readonly releaseConfig?: Record<string, unknown>;
  readonly healthConfig?: Record<string, unknown>;
  readonly agenticActionEvidence?: readonly AgentLifecycleActionEvidence[];
  readonly conformance?: ProductUnitConformance;
  readonly governance?: ProductUnitGovernance;
  readonly semanticArtifactRefs?: readonly string[];
  readonly metadata?: Record<string, unknown>;
}

export const ProductUnitProjectionInputSchema = z
  .object({
    productId: z.string().trim().min(1),
    productName: z.string().trim().min(1),
    productKind: z.enum(PRODUCT_UNIT_KINDS),
    lifecycleStatus: LifecycleStatusSchema,
    lifecycleExecutionAllowed: z.boolean(),
    lifecycleConfigPath: z.string().trim().min(1),
    surfaces: z.array(ProductUnitSurfaceSchema).min(1),
    productShape: z.enum(PRODUCT_SHAPES).optional(),
    sourceRefs: z.array(ProductUnitSourceRefSchema).optional(),
    lifecycleProfile: z.string().trim().min(1).optional(),
    owner: z.string().trim().min(1).optional(),
    scope: ProductUnitScopeSchema.optional(),
    providerMode: ProviderModeSchema.optional(),
    environments: z.array(z.unknown()).optional(),
    pluginBindings: z.array(z.unknown()).optional(),
    gateBindings: z.array(z.unknown()).optional(),
    policyPacks: z.array(z.string().trim().min(1)).optional(),
    artifacts: z.array(z.unknown()).optional(),
    deployments: z.array(z.unknown()).optional(),
    releaseConfig: RecordSchema.optional(),
    healthConfig: RecordSchema.optional(),
    agenticActionEvidence: z.array(z.unknown()).optional(),
    conformance: ProductUnitConformanceSchema.optional(),
    governance: ProductUnitGovernanceSchema.optional(),
    semanticArtifactRefs: z.array(z.string().trim().min(1)).optional(),
    metadata: RecordSchema.optional(),
  })
  .strict();

export function validateProductUnitProjectionInput(
  value: unknown
): value is ProductUnitProjectionInput {
  return ProductUnitProjectionInputSchema.safeParse(value).success;
}

/**
 * Projects a ProductUnit from canonical product registry and kernel-product.yaml data.
 * This is the single validated projection that combines all ProductUnit dimensions.
 */
export function projectProductUnit(input: ProductUnitProjectionInput): ProductUnit {
  const productUnit: ProductUnit = {
    schemaVersion: "1.0.0",
    id: input.productId,
    name: input.productName,
    kind: input.productKind,
    lifecycleStatus: input.lifecycleStatus,
    registryProviderRef: {
      providerId: "ghatana-file-registry",
    },
    sourceProviderRef: {
      providerId: "ghatana-file-registry",
      config: {
        lifecycleConfigPath: input.lifecycleConfigPath,
      },
    },
    surfaces: input.surfaces,
    ...(input.scope === undefined ? {} : { scope: input.scope }),
    ...(input.owner === undefined ? {} : { owner: input.owner }),
    ...(input.productShape === undefined ? {} : { productShape: input.productShape }),
    ...(input.sourceRefs === undefined ? {} : { sourceRefs: input.sourceRefs }),
    ...(input.lifecycleProfile === undefined ? {} : { lifecycleProfile: input.lifecycleProfile }),
    ...(input.providerMode === undefined ? {} : { providerMode: input.providerMode }),
    ...(input.environments === undefined ? {} : { environments: input.environments }),
    ...(input.pluginBindings === undefined ? {} : { pluginBindings: input.pluginBindings }),
    ...(input.gateBindings === undefined ? {} : { gateBindings: input.gateBindings }),
    ...(input.policyPacks === undefined ? {} : { policyPacks: input.policyPacks }),
    ...(input.artifacts === undefined ? {} : { artifacts: input.artifacts }),
    ...(input.deployments === undefined ? {} : { deployments: input.deployments }),
    ...(input.releaseConfig === undefined ? {} : { releaseConfig: input.releaseConfig }),
    ...(input.healthConfig === undefined ? {} : { healthConfig: input.healthConfig }),
    ...(input.agenticActionEvidence === undefined ? {} : { agenticActionEvidence: input.agenticActionEvidence }),
    ...(input.conformance === undefined ? {} : { conformance: input.conformance }),
    ...(input.governance === undefined ? {} : { governance: input.governance }),
    ...(input.semanticArtifactRefs === undefined ? {} : { semanticArtifactRefs: input.semanticArtifactRefs }),
    ...(input.metadata === undefined ? {} : { metadata: input.metadata }),
  };

  const result = validateProductUnit(productUnit);
  if (!result.valid) {
    throw new Error(
      `Invalid ProductUnit projection for "${input.productId}": ${result.errors.join("; ")}`
    );
  }

  return productUnit;
}
