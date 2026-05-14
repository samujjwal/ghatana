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
  ProductUnitSurface,
  ProductUnitSurfaceType,
  RegistryProvider,
} from "@ghatana/kernel-product-contracts";
import {
  isImplementationStatus,
  isProductUnitKind,
  isProductUnitSurfaceType,
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

interface GhatanaRegistrySurface {
  readonly type: string;
  readonly path?: string;
  readonly packagePath?: string;
  readonly gradleModule?: string;
  readonly implementationStatus?: string;
}

interface GhatanaRegistryEntry {
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
    if (status === undefined) {
      return undefined;
    }
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
      sourceProviderRef: { providerId: this.providerId },
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
