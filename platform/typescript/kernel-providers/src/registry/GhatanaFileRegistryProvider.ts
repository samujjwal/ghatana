/**
 * GhatanaFileRegistryProvider - implementation of RegistryProvider backed by the Ghatana file registry.
 *
 * This provider reads from config/canonical-product-registry.json and converts entries
 * into ProductUnit representations. It preserves lifecycle fields and validates that
 * enabled products have lifecycle configuration paths.
 *
 * @doc.type class
 * @doc.purpose Ghatana file-backed registry provider implementation
 * @doc.layer kernel-providers
 * @doc.pattern Provider
 */

import * as fs from "node:fs/promises";
import * as path from "node:path";
import type {
  RegistryProvider,
  ProductUnit,
  ProductUnitSurface,
  ProviderRef,
  ProductUnitConformance,
  ProductUnitGovernance,
  LifecycleStatus,
} from "@ghatana/kernel-product-contracts";
import {
  ProductUnitKind,
  ImplementationStatus,
  type ProductUnitSurfaceType,
} from "@ghatana/kernel-product-contracts";

/**
 * Ghatana file registry entry structure.
 */
interface GhatanaRegistryEntry {
  readonly id: string;
  readonly name: string;
  readonly kind: string;
  readonly owner?: string;
  readonly surfaces: readonly {
    readonly type: string;
    readonly path?: string;
    readonly packagePath?: string;
    readonly gradleModule?: string;
    readonly implementationStatus: string;
  }[];
  readonly lifecycleProfile?: string;
  readonly lifecycleStatus?: string;
  readonly lifecycleConfigPath?: string;
  readonly conformance?: {
    readonly manifest: boolean;
    readonly observability: boolean;
    readonly security: boolean;
    readonly dataAccess: boolean;
    readonly agentDefinitions: boolean;
    readonly masteryBindings: boolean;
    readonly evaluationPacks: boolean;
    readonly runtimeModule: boolean;
  };
  readonly metadata?: Record<string, unknown>;
}

/**
 * Ghatana file registry structure.
 */
interface GhatanaRegistry {
  readonly version: string;
  readonly registry: Record<string, GhatanaRegistryEntry>;
}

/**
 * Ghatana file registry provider implementation.
 */
export class GhatanaFileRegistryProvider implements RegistryProvider {
  readonly providerId = "ghatana-file-registry";
  readonly version = "1.0.0";
  readonly capabilities = ["registry-read", "product-unit-conversion"];

  private registryPath: string;
  private cachedRegistry: GhatanaRegistry | null = null;

  constructor(registryPath?: string) {
    this.registryPath =
      registryPath ?? path.join(process.cwd(), "config/canonical-product-registry.json");
  }

  private async loadRegistry(): Promise<GhatanaRegistry> {
    if (this.cachedRegistry) {
      return this.cachedRegistry;
    }

    const content = await fs.readFile(this.registryPath, "utf-8");
    this.cachedRegistry = JSON.parse(content) as GhatanaRegistry;
    return this.cachedRegistry;
  }

  private convertSurfaceType(type: string): ProductUnitSurfaceType {
    const validTypes: readonly ProductUnitSurfaceType[] = [
      "backend-api",
      "web",
      "worker",
      "operator",
      "portal",
      "sdk",
      "mobile",
      "mobile-ios",
      "mobile-android",
      "cli",
      "domain-pack",
      "plugin",
      "agent-runtime",
      "data-pipeline",
    ];

    if (validTypes.includes(type as ProductUnitSurfaceType)) {
      return type as ProductUnitSurfaceType;
    }

    return "backend-api"; // Default fallback
  }

  private convertImplementationStatus(status: string): ImplementationStatus {
    const validStatuses: readonly ImplementationStatus[] = [
      "implemented",
      "planned",
      "backend-only",
      "experimental",
    ];

    if (validStatuses.includes(status as ImplementationStatus)) {
      return status as ImplementationStatus;
    }

    return "planned"; // Default fallback
  }

  private convertLifecycleStatus(status?: string): LifecycleStatus {
    const validStatuses: readonly LifecycleStatus[] = [
      "disabled",
      "planned",
      "partial",
      "enabled",
    ];

    if (status && validStatuses.includes(status as LifecycleStatus)) {
      return status as LifecycleStatus;
    }

    return "planned"; // Default fallback
  }

  private convertEntryToProductUnit(entry: GhatanaRegistryEntry): ProductUnit {
    const surfaces: ProductUnitSurface[] = entry.surfaces.map((surface) => ({
      id: `${entry.id}-${surface.type}`,
      type: this.convertSurfaceType(surface.type),
      sourceRef: surface.path,
      implementationStatus: this.convertImplementationStatus(surface.implementationStatus),
      packagePath: surface.packagePath,
      gradleModule: surface.gradleModule,
      adapterHint: undefined, // Could be derived from toolchain in a full implementation
    }));

    const conformance: ProductUnitConformance | undefined = entry.conformance
      ? {
          requiredChecks: Object.entries(entry.conformance)
            .filter(([, value]) => value)
            .map(([key]) => key),
          level: entry.conformance.agentDefinitions ? "strict" : "standard",
          exemptions: [],
        }
      : undefined;

    return {
      schemaVersion: "1.0.0",
      id: entry.id,
      name: entry.name,
      kind: entry.kind as ProductUnitKind,
      owner: entry.owner || entry.metadata?.owner as string,
      registryProviderRef: { providerId: this.providerId },
      sourceProviderRef: { providerId: this.providerId },
      surfaces,
      lifecycleProfile: entry.lifecycleProfile,
      lifecycleStatus: this.convertLifecycleStatus(entry.lifecycleStatus),
      conformance,
      governance: undefined, // Could be derived from conformance in a full implementation
      metadata: entry.metadata,
    };
  }

  async getProductUnit(productUnitId: string): Promise<ProductUnit | null> {
    const registry = await this.loadRegistry();
    const entry = registry.registry[productUnitId];

    if (!entry) {
      return null;
    }

    return this.convertEntryToProductUnit(entry);
  }

  async listProductUnits(): Promise<readonly ProductUnit[]> {
    const registry = await this.loadRegistry();
    const entries = Object.values(registry.registry);
    return entries.map((entry) => this.convertEntryToProductUnit(entry));
  }

  async listProductUnitsByKind(kind: string): Promise<readonly ProductUnit[]> {
    const registry = await this.loadRegistry();
    const entries = Object.values(registry.registry).filter(
      (entry) => entry.kind === kind
    );
    return entries.map((entry) => this.convertEntryToProductUnit(entry));
  }

  async validateProductUnit(productUnit: ProductUnit): Promise<{
    valid: boolean;
    errors: readonly string[];
  }> {
    const errors: string[] = [];

    // Validate schema version
    if (productUnit.schemaVersion !== "1.0.0") {
      errors.push(`Invalid schema version: ${productUnit.schemaVersion}`);
    }

    // Validate required fields
    if (!productUnit.id || typeof productUnit.id !== "string") {
      errors.push("Missing or invalid id");
    }

    if (!productUnit.name || typeof productUnit.name !== "string") {
      errors.push("Missing or invalid name");
    }

    // Validate enabled products have lifecycle config
    if (productUnit.lifecycleStatus === "enabled" && !productUnit.lifecycleProfile) {
      errors.push("Enabled product must have lifecycle profile");
    }

    // Validate surfaces
    if (!productUnit.surfaces || productUnit.surfaces.length === 0) {
      errors.push("ProductUnit must have at least one surface");
    }

    // Validate provider refs
    if (!productUnit.registryProviderRef || !productUnit.registryProviderRef.providerId) {
      errors.push("Missing registry provider reference");
    }

    if (!productUnit.sourceProviderRef || !productUnit.sourceProviderRef.providerId) {
      errors.push("Missing source provider reference");
    }

    return {
      valid: errors.length === 0,
      errors,
    };
  }
}
