/**
 * GhatanaFileRegistryProvider - implementation of RegistryProvider backed by the Ghatana file registry.
 *
 * @doc.type class
 * @doc.purpose Ghatana file-backed registry provider implementation
 * @doc.layer kernel-providers
 * @doc.pattern Provider
 */

import * as fs from "node:fs/promises";
import * as path from "node:path";
import type {
  LifecycleStatus,
  ProductUnit,
  ProductUnitConformance,
  ProductUnitIntent,
  ProductUnitSurface,
  ProductUnitSurfaceType,
  RegistryProvider,
} from "@ghatana/kernel-product-contracts";
import {
  isImplementationStatus,
  isProductUnitKind,
  isProductUnitSurfaceType,
  validateProductUnitIntent,
  validateProductUnit,
} from "@ghatana/kernel-product-contracts";

export interface GhatanaFileRegistryProviderOptions {
  readonly registryPath?: string;
  readonly strict?: boolean;
}

export interface RegistryValidationIssue {
  readonly productId: string;
  readonly path: string;
  readonly message: string;
}

export interface RegistryValidationResult {
  readonly valid: boolean;
  readonly errors: readonly RegistryValidationIssue[];
  readonly warnings: readonly RegistryValidationIssue[];
}

export interface ProductUnitIntentTargetValidationResult {
  readonly valid: boolean;
  readonly correlationId: string;
  readonly errors: readonly string[];
  readonly warnings: readonly string[];
}

export interface ProductUnitIntentPreviewResult
  extends ProductUnitIntentTargetValidationResult {
  readonly operation: ProductUnitIntent["intentType"];
  readonly productUnitId: string;
  readonly registryPath: string;
  readonly before: GhatanaRegistryEntry | null;
  readonly after: GhatanaRegistryEntry | null;
  readonly diff: readonly string[];
}

export interface ProductUnitIntentApplyOptions {
  readonly allowWrite: boolean;
}

export interface ProductUnitIntentApplyResult extends ProductUnitIntentPreviewResult {
  readonly applied: boolean;
}

interface GhatanaRegistrySurface {
  readonly type: string;
  readonly path?: string;
  readonly packagePath?: string;
  readonly gradleModule?: string;
  readonly implementationStatus?: string;
}

export interface GhatanaRegistryEntry {
  readonly id: string;
  readonly name: string;
  readonly kind: string;
  readonly owner?: string;
  readonly manifestPath?: string;
  readonly manifestFormat?: string;
  readonly buildFile?: string;
  readonly surfaces?: readonly GhatanaRegistrySurface[];
  readonly lifecycleProfile?: string;
  readonly lifecycleStatus?: string;
  readonly lifecycleConfigPath?: string;
  readonly ci?: unknown;
  readonly deployment?: unknown;
  readonly environments?: unknown;
  readonly conformance?: Record<string, boolean>;
  readonly metadata?: Record<string, unknown>;
}

function getString(value: unknown): string | undefined {
  return typeof value === "string" && value.trim().length > 0 ? value : undefined;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function getSourceRegistryMetadata(
  metadata: Record<string, unknown> | undefined
): Record<string, unknown> {
  if (!isRecord(metadata?.sourceRegistry)) {
    return {};
  }
  return metadata.sourceRegistry;
}

function stripSourceRegistryMetadata(
  metadata: Record<string, unknown>
): Record<string, unknown> {
  const { sourceRegistry, ...remaining } = metadata;
  return remaining;
}

function containsProductSpecificPlatformMetadata(
  metadata: Record<string, unknown> | undefined
): boolean {
  if (!isRecord(metadata?.platform)) {
    return false;
  }
  return Object.keys(metadata.platform).some((key) =>
    /(productSpecific|hardcodedProduct|productId|productUnitId)/i.test(key)
  );
}

function buildPreviewDiff(
  before: GhatanaRegistryEntry | null,
  after: GhatanaRegistryEntry | null
): readonly string[] {
  if (after === null) {
    return [];
  }
  if (before === null) {
    return [`+ registry.${after.id}`];
  }

  const diff: string[] = [];
  const fields: readonly (keyof GhatanaRegistryEntry)[] = [
    "name",
    "kind",
    "owner",
    "lifecycleProfile",
    "lifecycleStatus",
    "lifecycleConfigPath",
  ];
  for (const field of fields) {
    if (before[field] !== after[field]) {
      diff.push(`~ registry.${after.id}.${field}`);
    }
  }
  if (JSON.stringify(before.surfaces ?? []) !== JSON.stringify(after.surfaces)) {
    diff.push(`~ registry.${after.id}.surfaces`);
  }
  if (JSON.stringify(before.metadata ?? {}) !== JSON.stringify(after.metadata)) {
    diff.push(`~ registry.${after.id}.metadata`);
  }
  return diff;
}

interface GhatanaRegistry {
  readonly version: string;
  readonly registry: Record<string, GhatanaRegistryEntry>;
}

const LIFECYCLE_STATUSES: readonly LifecycleStatus[] = [
  "disabled",
  "planned",
  "partial",
  "enabled",
];

export class GhatanaFileRegistryProvider implements RegistryProvider {
  readonly providerId = "ghatana-file-registry";
  readonly version = "1.0.0";
  readonly capabilities = ["registry-read", "product-unit-conversion"];

  private readonly registryPath: string;
  private readonly strict: boolean;
  private cachedRegistry: GhatanaRegistry | null = null;

  constructor(options?: string | GhatanaFileRegistryProviderOptions) {
    if (typeof options === "string" || options === undefined) {
      this.registryPath =
        options ?? path.join(process.cwd(), "config/canonical-product-registry.json");
      this.strict = false;
      return;
    }

    this.registryPath =
      options.registryPath ??
      path.join(process.cwd(), "config/canonical-product-registry.json");
    this.strict = options.strict ?? false;
  }

  clearCache(): void {
    this.cachedRegistry = null;
  }

  reload(): void {
    this.clearCache();
  }

  private async loadRegistry(): Promise<GhatanaRegistry> {
    if (this.cachedRegistry) {
      return this.cachedRegistry;
    }

    const content = await fs.readFile(this.registryPath, "utf-8");
    this.cachedRegistry = JSON.parse(content) as GhatanaRegistry;
    return this.cachedRegistry;
  }

  private issue(
    productId: string,
    issuePath: string,
    message: string
  ): RegistryValidationIssue {
    return { productId, path: issuePath, message };
  }

  private validateEntry(
    entry: GhatanaRegistryEntry,
    productId: string
  ): RegistryValidationResult {
    const errors: RegistryValidationIssue[] = [];
    const warnings: RegistryValidationIssue[] = [];

    if (!isProductUnitKind(entry.kind)) {
      errors.push(this.issue(productId, "kind", `Unknown product kind "${entry.kind}"`));
    }

    const surfaces = entry.surfaces ?? [];
    surfaces.forEach((surface, index) => {
      if (!isProductUnitSurfaceType(surface.type)) {
        errors.push(
          this.issue(
            productId,
            `surfaces[${index}].type`,
            `Unknown surface type "${surface.type}"`
          )
        );
      }
      if (!isImplementationStatus(surface.implementationStatus)) {
        errors.push(
          this.issue(
            productId,
            `surfaces[${index}].implementationStatus`,
            `Unknown implementation status "${String(surface.implementationStatus)}"`
          )
        );
      }
    });

    if (
      entry.lifecycleStatus !== undefined &&
      !LIFECYCLE_STATUSES.includes(entry.lifecycleStatus as LifecycleStatus)
    ) {
      errors.push(
        this.issue(
          productId,
          "lifecycleStatus",
          `Unknown lifecycle status "${entry.lifecycleStatus}"`
        )
      );
    }

    if (entry.lifecycleStatus === "enabled" && !entry.lifecycleProfile) {
      errors.push(
        this.issue(
          productId,
          "lifecycleProfile",
          "Enabled product must declare lifecycleProfile"
        )
      );
    }

    if (entry.lifecycleStatus === "enabled" && !entry.lifecycleConfigPath) {
      errors.push(
        this.issue(
          productId,
          "lifecycleConfigPath",
          "Enabled product must declare lifecycleConfigPath"
        )
      );
    }

    if (!entry.surfaces || entry.surfaces.length === 0) {
      warnings.push(
        this.issue(productId, "surfaces", "Product has no registered surfaces")
      );
    }

    return { valid: errors.length === 0, errors, warnings };
  }

  async validateRegistry(): Promise<RegistryValidationResult> {
    const registry = await this.loadRegistry();
    const errors: RegistryValidationIssue[] = [];
    const warnings: RegistryValidationIssue[] = [];

    for (const [productId, entry] of Object.entries(registry.registry)) {
      const result = this.validateEntry(entry, productId);
      errors.push(...result.errors);
      warnings.push(...result.warnings);
    }

    return { valid: errors.length === 0, errors, warnings };
  }

  async validateIntentTarget(
    intent: ProductUnitIntent
  ): Promise<ProductUnitIntentTargetValidationResult> {
    const intentValidation = validateProductUnitIntent(intent);
    const errors = [...intentValidation.errors];
    const warnings: string[] = [];

    if (intent.target.registryProvider !== this.providerId) {
      errors.push(
        `Intent targets registry provider ${intent.target.registryProvider}, expected ${this.providerId}`
      );
    }
    if (intent.target.sourceProvider !== this.providerId) {
      errors.push(
        `Intent targets source provider ${intent.target.sourceProvider}, expected ${this.providerId}`
      );
    }
    if (containsProductSpecificPlatformMetadata(intent.productUnit.metadata)) {
      errors.push("Intent platform metadata must not contain product-specific logic");
    }

    const registry = await this.loadRegistry();
    const existingEntry = registry.registry[intent.productUnit.id];
    if (intent.intentType === "create" && existingEntry !== undefined) {
      errors.push(`ProductUnit already exists: ${intent.productUnit.id}`);
    }
    if (intent.intentType === "update" && existingEntry === undefined) {
      errors.push(`ProductUnit does not exist: ${intent.productUnit.id}`);
    }
    if (intent.intentType === "promote-candidate") {
      warnings.push(
        "promote-candidate intents are previewed as registry updates only"
      );
    }

    return {
      valid: errors.length === 0,
      correlationId: intent.producer.correlationId,
      errors,
      warnings,
    };
  }

  async previewApplyProductUnitIntent(
    intent: ProductUnitIntent
  ): Promise<ProductUnitIntentPreviewResult> {
    const targetValidation = await this.validateIntentTarget(intent);
    const registry = await this.loadRegistry();
    const before = registry.registry[intent.productUnit.id] ?? null;
    const after = targetValidation.valid
      ? this.convertIntentToRegistryEntry(intent, before)
      : null;

    return {
      ...targetValidation,
      operation: intent.intentType,
      productUnitId: intent.productUnit.id,
      registryPath: this.registryPath,
      before,
      after,
      diff: buildPreviewDiff(before, after),
    };
  }

  async applyProductUnitIntent(
    intent: ProductUnitIntent,
    options: ProductUnitIntentApplyOptions
  ): Promise<ProductUnitIntentApplyResult> {
    const preview = await this.previewApplyProductUnitIntent(intent);
    if (!options.allowWrite) {
      return {
        ...preview,
        valid: false,
        applied: false,
        errors: [...preview.errors, "File registry intent apply requires allowWrite: true"],
      };
    }
    if (!preview.valid || preview.after === null) {
      return { ...preview, applied: false };
    }

    const registry = await this.loadRegistry();
    const nextRegistry: GhatanaRegistry = {
      ...registry,
      registry: {
        ...registry.registry,
        [preview.productUnitId]: preview.after,
      },
    };
    await this.writeRegistry(nextRegistry);
    this.cachedRegistry = nextRegistry;
    return { ...preview, applied: true };
  }

  private assertEntryValid(entry: GhatanaRegistryEntry, productId: string): void {
    if (!this.strict) {
      return;
    }

    const result = this.validateEntry(entry, productId);
    if (!result.valid) {
      const details = result.errors
        .map((error) => `${error.path}: ${error.message}`)
        .join("; ");
      throw new Error(`Invalid registry entry "${productId}": ${details}`);
    }
  }

  private convertLifecycleStatus(status?: string): LifecycleStatus | undefined {
    if (LIFECYCLE_STATUSES.includes(status as LifecycleStatus)) {
      return status as LifecycleStatus;
    }
    return status as LifecycleStatus;
  }

  private convertSurface(
    entry: GhatanaRegistryEntry,
    surface: GhatanaRegistrySurface
  ): ProductUnitSurface {
    const converted: ProductUnitSurface = {
      id: `${entry.id}-${surface.type}`,
      type: surface.type as ProductUnitSurfaceType,
      implementationStatus: String(surface.implementationStatus) as ProductUnitSurface["implementationStatus"],
      ...(surface.path !== undefined ? { sourceRef: surface.path } : {}),
      ...(surface.packagePath !== undefined ? { packagePath: surface.packagePath } : {}),
      ...(surface.gradleModule !== undefined ? { gradleModule: surface.gradleModule } : {}),
    };

    return converted;
  }

  private buildSourceMetadata(
    entry: GhatanaRegistryEntry,
    validation: RegistryValidationResult
  ): Record<string, unknown> {
    return {
      ...(entry.metadata ?? {}),
      sourceRegistry: {
        ...(entry.manifestPath !== undefined ? { manifestPath: entry.manifestPath } : {}),
        ...(entry.manifestFormat !== undefined
          ? { manifestFormat: entry.manifestFormat }
          : {}),
        ...(entry.buildFile !== undefined ? { buildFile: entry.buildFile } : {}),
        ...(entry.lifecycleConfigPath !== undefined
          ? { lifecycleConfigPath: entry.lifecycleConfigPath }
          : {}),
        ...(entry.ci !== undefined ? { ci: entry.ci } : {}),
        ...(entry.deployment !== undefined ? { deployment: entry.deployment } : {}),
        ...(entry.environments !== undefined ? { environments: entry.environments } : {}),
      },
      validation: {
        warnings: validation.warnings,
        errors: validation.errors,
      },
    };
  }

  private convertEntryToProductUnit(
    entry: GhatanaRegistryEntry,
    productId: string
  ): ProductUnit {
    this.assertEntryValid(entry, productId);
    const validation = this.validateEntry(entry, productId);
    const surfaces: ProductUnitSurface[] = (entry.surfaces ?? []).map((surface) =>
      this.convertSurface(entry, surface)
    );

    const conformance: ProductUnitConformance | undefined = entry.conformance
      ? {
          requiredChecks: Object.entries(entry.conformance)
            .filter(([, value]) => value)
            .map(([key]) => key),
          level: entry.conformance.agentDefinitions ? "strict" : "standard",
          exemptions: [],
        }
      : undefined;

    const metadataOwner =
      typeof entry.metadata?.owner === "string" ? entry.metadata.owner : undefined;

    const productUnit: ProductUnit = {
      schemaVersion: "1.0.0",
      id: entry.id,
      name: entry.name,
      kind: entry.kind as ProductUnit["kind"],
      ...(entry.owner ?? metadataOwner
        ? { owner: entry.owner ?? metadataOwner }
        : {}),
      registryProviderRef: { providerId: this.providerId },
      sourceProviderRef: {
        providerId: this.providerId,
        ...(entry.lifecycleConfigPath !== undefined
          ? { config: { lifecycleConfigPath: entry.lifecycleConfigPath } }
          : {}),
      },
      surfaces,
      ...(entry.lifecycleProfile !== undefined
        ? { lifecycleProfile: entry.lifecycleProfile }
        : {}),
      ...(entry.lifecycleStatus !== undefined
        ? { lifecycleStatus: this.convertLifecycleStatus(entry.lifecycleStatus) }
        : {}),
      ...(conformance !== undefined ? { conformance } : {}),
      metadata: this.buildSourceMetadata(entry, validation),
    };

    return productUnit;
  }

  private convertIntentToRegistryEntry(
    intent: ProductUnitIntent,
    existingEntry: GhatanaRegistryEntry | null
  ): GhatanaRegistryEntry {
    const sourceRegistry = getSourceRegistryMetadata(intent.productUnit.metadata);
    return {
      ...(existingEntry ?? {}),
      id: intent.productUnit.id,
      name: intent.productUnit.name,
      kind: intent.productUnit.kind,
      ...(intent.productUnit.owner !== undefined
        ? { owner: intent.productUnit.owner }
        : {}),
      ...(getString(sourceRegistry.lifecycleConfigPath) !== undefined
        ? { lifecycleConfigPath: getString(sourceRegistry.lifecycleConfigPath) }
        : {}),
      ...(getString(sourceRegistry.lifecycleProfile) !== undefined
        ? { lifecycleProfile: getString(sourceRegistry.lifecycleProfile) }
        : intent.requestedLifecycle !== undefined
          ? { lifecycleProfile: intent.requestedLifecycle.profile }
          : intent.productUnit.lifecycleProfile !== undefined
            ? { lifecycleProfile: intent.productUnit.lifecycleProfile }
            : {}),
      ...(intent.requestedLifecycle !== undefined
        ? {
            lifecycleStatus: intent.requestedLifecycle.enableExecution
              ? "enabled"
              : "planned",
          }
        : existingEntry?.lifecycleStatus !== undefined
          ? { lifecycleStatus: existingEntry.lifecycleStatus }
          : {}),
      surfaces: intent.productUnit.surfaces.map((surface) => ({
        type: surface.type,
        ...(surface.sourceRef !== undefined ? { path: surface.sourceRef } : {}),
        ...(surface.packagePath !== undefined
          ? { packagePath: surface.packagePath }
          : {}),
        ...(surface.gradleModule !== undefined
          ? { gradleModule: surface.gradleModule }
          : {}),
        implementationStatus: surface.implementationStatus,
      })),
      ...(intent.productUnit.metadata !== undefined
        ? { metadata: stripSourceRegistryMetadata(intent.productUnit.metadata) }
        : {}),
    };
  }

  private async writeRegistry(registry: GhatanaRegistry): Promise<void> {
    await fs.mkdir(path.dirname(this.registryPath), { recursive: true });
    const tempPath = `${this.registryPath}.${process.pid}.${Date.now()}.tmp`;
    await fs.writeFile(tempPath, `${JSON.stringify(registry, null, 2)}\n`, "utf-8");
    await fs.rename(tempPath, this.registryPath);
  }

  async getProductUnit(productUnitId: string): Promise<ProductUnit | null> {
    const registry = await this.loadRegistry();
    const entry = registry.registry[productUnitId];

    if (!entry) {
      return null;
    }

    return this.convertEntryToProductUnit(entry, productUnitId);
  }

  async listProductUnits(): Promise<readonly ProductUnit[]> {
    const registry = await this.loadRegistry();
    return Object.entries(registry.registry).map(([productId, entry]) =>
      this.convertEntryToProductUnit(entry, productId)
    );
  }

  async listProductUnitsByKind(kind: string): Promise<readonly ProductUnit[]> {
    const registry = await this.loadRegistry();
    return Object.entries(registry.registry)
      .filter(([, entry]) => entry.kind === kind)
      .map(([productId, entry]) => this.convertEntryToProductUnit(entry, productId));
  }

  async validateProductUnit(productUnit: ProductUnit): Promise<{
    valid: boolean;
    errors: readonly string[];
  }> {
    const result = validateProductUnit(productUnit);
    const errors = [...result.errors];

    if (productUnit.lifecycleStatus === "enabled" && !productUnit.lifecycleProfile) {
      errors.push("Enabled product must have lifecycle profile");
    }
    if (productUnit.surfaces.length === 0) {
      errors.push("ProductUnit must have at least one surface");
    }

    return {
      valid: errors.length === 0,
      errors,
    };
  }
}
