/**
 * Tests for GhatanaFileRegistryProvider ProductUnitIntent application.
 *
 * @doc.type test
 * @doc.purpose Validate ProductUnitIntent application with atomic writes and conflict detection
 * @doc.layer kernel-providers
 * @doc.pattern Unit Test
 */

import { afterEach, beforeEach, describe, expect, it } from "vitest";
import * as fs from "node:fs/promises";
import * as os from "node:os";
import * as path from "node:path";
import { GhatanaFileRegistryProvider } from "../GhatanaFileRegistryProvider.js";
import type { ProductUnitIntent } from "@ghatana/kernel-product-contracts";

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
    await writeRegistry(registryPath, {});
    provider = new GhatanaFileRegistryProvider({ registryPath, strict: true });
  });

  afterEach(async () => {
    await fs.rm(tempDir, { recursive: true, force: true });
  });

  describe("previewApplyProductUnitIntent", () => {
    it("should preview a valid create intent", async () => {
      const result = await provider.previewApplyProductUnitIntent(buildIntent("create"));

      expect(result.valid).toBe(true);
      expect(result.productUnitId).toBe("product-001");
      expect(result.operation).toBe("create");
      expect(result.before).toBeNull();
      expect(result.after).toMatchObject({
        id: "product-001",
        name: "Test Product",
        kind: "business-product",
      });
      expect(result.diff).toContain("+ registry.product-001");
    });

    it("should preview an update intent for existing product", async () => {
      await provider.applyProductUnitIntent(buildIntent("create"), { allowWrite: true });

      const result = await provider.previewApplyProductUnitIntent(
        buildIntent("update", {
          intentId: "intent-002",
          productUnit: {
            name: "Updated Test Product",
          },
        })
      );

      expect(result.valid).toBe(true);
      expect(result.before).not.toBeNull();
      expect(result.after).not.toBeNull();
      expect(result.diff).toContain("~ registry.product-001.name");
    });

    it("should reject intent with invalid provider target", async () => {
      const result = await provider.previewApplyProductUnitIntent(
        buildIntent("create", {
          target: {
            registryProvider: "wrong-provider",
            sourceProvider: "ghatana-file-registry",
          },
        })
      );

      expect(result.valid).toBe(false);
      expect(result.errors).toContain(
        "Intent targets registry provider wrong-provider, expected ghatana-file-registry"
      );
    });

    it("should reject create intent for existing product", async () => {
      await provider.applyProductUnitIntent(buildIntent("create"), { allowWrite: true });

      const result = await provider.previewApplyProductUnitIntent(
        buildIntent("create", { intentId: "intent-002" })
      );

      expect(result.valid).toBe(false);
      expect(result.errors).toContain("ProductUnit already exists: product-001");
    });
  });

  describe("applyProductUnitIntent", () => {
    it("should apply a valid create intent with allowWrite: true", async () => {
      const result = await provider.applyProductUnitIntent(buildIntent("create"), {
        allowWrite: true,
      });

      expect(result.valid).toBe(true);
      expect(result.applied).toBe(true);

      const productUnit = await provider.getProductUnit("product-001");
      expect(productUnit).not.toBeNull();
      expect(productUnit?.name).toBe("Test Product");
    });

    it("should not apply intent without allowWrite: true", async () => {
      const result = await provider.applyProductUnitIntent(buildIntent("create"), {
        allowWrite: false,
      });

      expect(result.valid).toBe(false);
      expect(result.applied).toBe(false);
      expect(result.errors).toContain(
        "File registry intent apply requires allowWrite: true"
      );
    });

    it("should not apply invalid intent", async () => {
      const result = await provider.applyProductUnitIntent(
        buildIntent("create", {
          target: {
            registryProvider: "wrong-provider",
            sourceProvider: "ghatana-file-registry",
          },
        }),
        { allowWrite: true }
      );

      expect(result.valid).toBe(false);
      expect(result.applied).toBe(false);
    });

    it("should preserve registry formatting and schema version", async () => {
      await provider.applyProductUnitIntent(buildIntent("create"), { allowWrite: true });

      const registryContent = await fs.readFile(registryPath, "utf-8");
      const registry: GhatanaRegistry = JSON.parse(registryContent);

      expect(registry.version).toBe("1.0.0");
      expect(registryContent).toContain("\n");
    });

    it("should revalidate against the latest registry state before apply", async () => {
      await provider.applyProductUnitIntent(buildIntent("create"), { allowWrite: true });

      const registryContent = await fs.readFile(registryPath, "utf-8");
      const registry: GhatanaRegistry = JSON.parse(registryContent);
      registry.registry["product-001"] = {
        id: "product-001",
        name: "Concurrently Modified",
        kind: "business-product",
      };
      await fs.writeFile(registryPath, JSON.stringify(registry, null, 2), "utf-8");

      provider.clearCache();

      const result = await provider.applyProductUnitIntent(
        buildIntent("create", { intentId: "intent-create-apply" }),
        {
        allowWrite: true,
        }
      );

      expect(result.valid).toBe(false);
      expect(result.applied).toBe(false);
      expect(result.errors).toContain("ProductUnit already exists: product-001");
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

type IntentOverrides = {
  readonly intentId?: string;
  readonly target?: ProductUnitIntent["target"];
  readonly requestedLifecycle?: ProductUnitIntent["requestedLifecycle"];
  readonly productUnit?: {
    readonly name?: string;
    readonly metadata?: ProductUnitIntent["productUnit"]["metadata"];
  };
};

function buildIntent(
  intentType: ProductUnitIntent["intentType"],
  overrides: IntentOverrides = {}
): ProductUnitIntent {
  return {
    schemaVersion: "1.0.0",
    intentId: overrides.intentId ?? `intent-${intentType}`,
    intentType,
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
    target: overrides.target ?? {
      registryProvider: "ghatana-file-registry",
      sourceProvider: "ghatana-file-registry",
    },
    productUnit: {
      schemaVersion: "1.0.0",
      id: "product-001",
      name: overrides.productUnit?.name ?? "Test Product",
      kind: "business-product",
      surfaces: [
        {
          id: "surface-1",
          type: "web",
          implementationStatus: "implemented",
        },
      ],
      ...(overrides.productUnit?.metadata !== undefined
        ? { metadata: overrides.productUnit.metadata }
        : {}),
    },
    ...(overrides.requestedLifecycle !== undefined
      ? { requestedLifecycle: overrides.requestedLifecycle }
      : {}),
  };
}

async function writeRegistry(
  registryPath: string,
  registry: Record<string, unknown>
): Promise<void> {
  const initialRegistry: GhatanaRegistry = {
    version: "1.0.0",
    registry,
  };

  await fs.writeFile(registryPath, JSON.stringify(initialRegistry, null, 2), "utf-8");
}
