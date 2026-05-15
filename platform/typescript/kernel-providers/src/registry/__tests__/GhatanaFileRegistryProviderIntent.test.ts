/**
 * Tests for GhatanaFileRegistryProvider ProductUnitIntent application.
 *
 * @doc.type test
 * @doc.purpose Validate ProductUnitIntent application with atomic writes and conflict detection
 * @doc.layer kernel-providers
 * @doc.pattern Unit Test
 */

import { describe, it, expect, beforeEach, afterEach } from "vitest";
import * as fs from "node:fs/promises";
import * as path from "node:path";
import * as os from "node:os";
import { GhatanaFileRegistryProvider } from "../GhatanaFileRegistryProvider.js";
import {
  type ProductUnitIntent,
} from "@ghatana/kernel-product-contracts";

interface GhatanaRegistry {
  readonly version: string;
  readonly registry: Record<string, unknown>;
}

describe("GhatanaFileRegistryProvider ProductUnitIntent Application", () => {
  let tempDir: string;
  let registryPath: string;
  let provider: GhatanaFileRegistryProvider;

  beforeEach(async () => {
    tempDir = await fs.mkdtemp(path.join(os.tmpdir(), "ghatana-registry-test-"));
    registryPath = path.join(tempDir, "registry.json");

    const initialRegistry: GhatanaRegistry = {
      version: "1.0.0",
      registry: {},
    };

    await fs.writeFile(registryPath, JSON.stringify(initialRegistry, null, 2), "utf-8");
    provider = new GhatanaFileRegistryProvider({ registryPath, strict: true });
  });

  afterEach(async () => {
    await fs.rm(tempDir, { recursive: true, force: true });
  });

  describe("previewApplyProductUnitIntent", () => {
    it("should preview a valid create intent", async () => {
      const intent: ProductUnitIntent = {
        schemaVersion: "1.0.0",
        intentId: "intent-001",
        intentType: "create",
        scope: {
          tenantId: "tenant-1",
          workspaceId: "workspace-1",
          projectId: "project-1",
        },
        producer: {
          id: "yappc",
          type: "yappc",
          correlationId: "corr-001",
        },
        target: {
          registryProvider: "ghatana-file-registry",
          sourceProvider: "ghatana-file-registry",
        },
        productUnit: {
          schemaVersion: "1.0.0",
          id: "product-001",
          name: "Test Product",
          kind: "web-app",
          surfaces: [
            {
              id: "surface-1",
              type: "frontend",
              implementationStatus: "implemented",
            },
          ],
        },
      };

      const result = await provider.previewApplyProductUnitIntent(intent);

      expect(result.valid).toBe(true);
      expect(result.productUnitId).toBe("product-001");
      expect(result.operation).toBe("create");
      expect(result.before).toBeNull();
      expect(result.after).not.toBeNull();
      expect(result.diff).toContain("+ registry.product-001");
    });

    it("should preview an update intent for existing product", async () => {
      // First create a product
      const createIntent: ProductUnitIntent = {
        schemaVersion: "1.0.0",
        intentId: "intent-001",
        intentType: "create",
        scope: {
          tenantId: "tenant-1",
          workspaceId: "workspace-1",
          projectId: "project-1",
        },
        producer: {
          id: "yappc",
          type: "yappc",
          correlationId: "corr-001",
        },
        target: {
          registryProvider: "ghatana-file-registry",
          sourceProvider: "ghatana-file-registry",
        },
        productUnit: {
          schemaVersion: "1.0.0",
          id: "product-001",
          name: "Test Product",
          kind: "web-app",
          surfaces: [
            {
              id: "surface-1",
              type: "frontend",
              implementationStatus: "implemented",
            },
          ],
        },
      };

      await provider.applyProductUnitIntent(createIntent, { allowWrite: true });

      // Now preview an update
      const updateIntent: ProductUnitIntent = {
        ...createIntent,
        intentId: "intent-002",
        intentType: "update",
        productUnit: {
          ...createIntent.productUnit,
          name: "Updated Test Product",
        },
      };

      const result = await provider.previewApplyProductUnitIntent(updateIntent);

      expect(result.valid).toBe(true);
      expect(result.before).not.toBeNull();
      expect(result.after).not.toBeNull();
      expect(result.diff).toContain("~ registry.product-001.name");
    });

    it("should reject intent with invalid provider target", async () => {
      const intent: ProductUnitIntent = {
        schemaVersion: "1.0.0",
        intentId: "intent-001",
        intentType: "create",
        scope: {
          tenantId: "tenant-1",
          workspaceId: "workspace-1",
          projectId: "project-1",
        },
        producer: {
          id: "yappc",
          type: "yappc",
          correlationId: "corr-001",
        },
        target: {
          registryProvider: "wrong-provider",
          sourceProvider: "ghatana-file-registry",
        },
        productUnit: {
          schemaVersion: "1.0.0",
          id: "product-001",
          name: "Test Product",
          kind: "web-app",
          surfaces: [
            {
              id: "surface-1",
              type: "frontend",
              implementationStatus: "implemented",
            },
          ],
        },
      };

      const result = await provider.previewApplyProductUnitIntent(intent);

      expect(result.valid).toBe(false);
      expect(result.errors).toContain(
        "Intent targets registry provider wrong-provider, expected ghatana-file-registry"
      );
    });

    it("should reject create intent for existing product", async () => {
      // First create a product
      const createIntent: ProductUnitIntent = {
        schemaVersion: "1.0.0",
        intentId: "intent-001",
        intentType: "create",
        scope: {
          tenantId: "tenant-1",
          workspaceId: "workspace-1",
          projectId: "project-1",
        },
        producer: {
          id: "yappc",
          type: "yappc",
          correlationId: "corr-001",
        },
        target: {
          registryProvider: "ghatana-file-registry",
          sourceProvider: "ghatana-file-registry",
        },
        productUnit: {
          schemaVersion: "1.0.0",
          id: "product-001",
          name: "Test Product",
          kind: "web-app",
          surfaces: [
            {
              id: "surface-1",
              type: "frontend",
              implementationStatus: "implemented",
            },
          ],
        },
      };

      await provider.applyProductUnitIntent(createIntent, { allowWrite: true });

      // Try to create again
      const duplicateIntent: ProductUnitIntent = {
        ...createIntent,
        intentId: "intent-002",
      };

      const result = await provider.previewApplyProductUnitIntent(duplicateIntent);

      expect(result.valid).toBe(false);
      expect(result.errors).toContain("ProductUnit already exists: product-001");
    });
  });

  describe("applyProductUnitIntent", () => {
    it("should apply a valid create intent with allowWrite: true", async () => {
      const intent: ProductUnitIntent = {
        schemaVersion: "1.0.0",
        intentId: "intent-001",
        intentType: "create",
        scope: {
          tenantId: "tenant-1",
          workspaceId: "workspace-1",
          projectId: "project-1",
        },
        producer: {
          id: "yappc",
          type: "yappc",
          correlationId: "corr-001",
        },
        target: {
          registryProvider: "ghatana-file-registry",
          sourceProvider: "ghatana-file-registry",
        },
        productUnit: {
          schemaVersion: "1.0.0",
          id: "product-001",
          name: "Test Product",
          kind: "web-app",
          surfaces: [
            {
              id: "surface-1",
              type: "frontend",
              implementationStatus: "implemented",
            },
          ],
        },
      };

      const result = await provider.applyProductUnitIntent(intent, {
        allowWrite: true,
      });

      expect(result.valid).toBe(true);
      expect(result.applied).toBe(true);

      // Verify the product was actually created
      const productUnit = await provider.getProductUnit("product-001");
      expect(productUnit).not.toBeNull();
      expect(productUnit?.name).toBe("Test Product");
    });

    it("should not apply intent without allowWrite: true", async () => {
      const intent: ProductUnitIntent = {
        schemaVersion: "1.0.0",
        intentId: "intent-001",
        intentType: "create",
        scope: {
          tenantId: "tenant-1",
          workspaceId: "workspace-1",
          projectId: "project-1",
        },
        producer: {
          id: "yappc",
          type: "yappc",
          correlationId: "corr-001",
        },
        target: {
          registryProvider: "ghatana-file-registry",
          sourceProvider: "ghatana-file-registry",
        },
        productUnit: {
          schemaVersion: "1.0.0",
          id: "product-001",
          name: "Test Product",
          kind: "web-app",
          surfaces: [
            {
              id: "surface-1",
              type: "frontend",
              implementationStatus: "implemented",
            },
          ],
        },
      };

      const result = await provider.applyProductUnitIntent(intent, {
        allowWrite: false,
      });

      expect(result.valid).toBe(false);
      expect(result.applied).toBe(false);
      expect(result.errors).toContain(
        "File registry intent apply requires allowWrite: true"
      );
    });

    it("should not apply invalid intent", async () => {
      const intent: ProductUnitIntent = {
        schemaVersion: "1.0.0",
        intentId: "intent-001",
        intentType: "create",
        scope: {
          tenantId: "tenant-1",
          workspaceId: "workspace-1",
          projectId: "project-1",
        },
        producer: {
          id: "yappc",
          type: "yappc",
          correlationId: "corr-001",
        },
        target: {
          registryProvider: "wrong-provider",
          sourceProvider: "ghatana-file-registry",
        },
        productUnit: {
          schemaVersion: "1.0.0",
          id: "product-001",
          name: "Test Product",
          kind: "web-app",
          surfaces: [
            {
              id: "surface-1",
              type: "frontend",
              implementationStatus: "implemented",
            },
          ],
        },
      };

      const result = await provider.applyProductUnitIntent(intent, {
        allowWrite: true,
      });

      expect(result.valid).toBe(false);
      expect(result.applied).toBe(false);
    });

    it("should preserve registry formatting and schema version", async () => {
      const intent: ProductUnitIntent = {
        schemaVersion: "1.0.0",
        intentId: "intent-001",
        intentType: "create",
        scope: {
          tenantId: "tenant-1",
          workspaceId: "workspace-1",
          projectId: "project-1",
        },
        producer: {
          id: "yappc",
          type: "yappc",
          correlationId: "corr-001",
        },
        target: {
          registryProvider: "ghatana-file-registry",
          sourceProvider: "ghatana-file-registry",
        },
        productUnit: {
          schemaVersion: "1.0.0",
          id: "product-001",
          name: "Test Product",
          kind: "web-app",
          surfaces: [
            {
              id: "surface-1",
              type: "frontend",
              implementationStatus: "implemented",
            },
          ],
        },
      };

      await provider.applyProductUnitIntent(intent, { allowWrite: true });

      const registryContent = await fs.readFile(registryPath, "utf-8");
      const registry: GhatanaRegistry = JSON.parse(registryContent);

      expect(registry.version).toBe("1.0.0");
      expect(registryContent).toContain("\n"); // Should have newline at end
    });

    it("should detect concurrent modifications (conflict detection)", async () => {
      const intent: ProductUnitIntent = {
        schemaVersion: "1.0.0",
        intentId: "intent-001",
        intentType: "create",
        scope: {
          tenantId: "tenant-1",
          workspaceId: "workspace-1",
          projectId: "project-1",
        },
        producer: {
          id: "yappc",
          type: "yappc",
          correlationId: "corr-001",
        },
        target: {
          registryProvider: "ghatana-file-registry",
          sourceProvider: "ghatana-file-registry",
        },
        productUnit: {
          schemaVersion: "1.0.0",
          id: "product-001",
          name: "Test Product",
          kind: "web-app",
          surfaces: [
            {
              id: "surface-1",
              type: "frontend",
              implementationStatus: "implemented",
            },
          ],
        },
      };

      // Preview first
      const preview = await provider.previewApplyProductUnitIntent(intent);

      // Simulate concurrent modification by directly modifying the file
      const registryContent = await fs.readFile(registryPath, "utf-8");
      const registry: GhatanaRegistry = JSON.parse(registryContent);
      registry.registry["product-001"] = {
        id: "product-001",
        name: "Concurrently Modified",
        kind: "web-app",
      };
      await fs.writeFile(registryPath, JSON.stringify(registry, null, 2), "utf-8");

      // Clear cache to force reload
      provider.clearCache();

      // Try to apply - should detect conflict
      const result = await provider.applyProductUnitIntent(intent, {
        allowWrite: true,
      });

      expect(result.valid).toBe(false);
      expect(result.applied).toBe(false);
      expect(result.errors).toContain(
        "Registry entry was modified concurrently, please retry"
      );
    });
  });

  describe("provider capabilities", () => {
    it("should declare product-unit-intent-apply capability", () => {
      expect(provider.capabilities).toContain("product-unit-intent-apply");
    });

    it("should implement ProductUnitIntentCapableRegistryProvider interface", () => {
      expect(typeof provider.previewApplyProductUnitIntent).toBe("function");
      expect(typeof provider.applyProductUnitIntent).toBe("function");
    });
  });
});
