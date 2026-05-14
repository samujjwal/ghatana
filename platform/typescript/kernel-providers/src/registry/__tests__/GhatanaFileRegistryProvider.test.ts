/**
 * Tests for GhatanaFileRegistryProvider.
 */

import { describe, it, expect, beforeEach, afterEach } from "vitest";
import { GhatanaFileRegistryProvider } from "../GhatanaFileRegistryProvider";
import * as fs from "node:fs/promises";
import * as path from "node:path";

describe("GhatanaFileRegistryProvider", () => {
  let provider: GhatanaFileRegistryProvider;
  let mockRegistryPath: string;

  beforeEach(() => {
    // Use a mock registry path for testing
    mockRegistryPath = path.join(process.cwd(), "test-mock-registry.json");
    provider = new GhatanaFileRegistryProvider(mockRegistryPath);
  });

  afterEach(async () => {
    // Clean up mock registry file if it exists
    try {
      await fs.unlink(mockRegistryPath);
    } catch {
      // Ignore if file doesn't exist
    }
  });

  describe("getProductUnit", () => {
    it("returns null for non-existent product unit", async () => {
      const mockRegistry = {
        version: "1.0.0",
        registry: {},
      };
      await fs.writeFile(mockRegistryPath, JSON.stringify(mockRegistry));

      const result = await provider.getProductUnit("non-existent");
      expect(result).toBeNull();
    });

    it("converts Digital Marketing to ProductUnit", async () => {
      const mockRegistry = {
        version: "1.0.0",
        registry: {
          "digital-marketing": {
            id: "digital-marketing",
            name: "Digital Marketing",
            kind: "business-product",
            owner: "digital-marketing-team",
            surfaces: [
              {
                type: "backend-api",
                path: "products/digital-marketing/api",
                implementationStatus: "implemented",
              },
              {
                type: "web",
                packagePath: "products/digital-marketing/ui",
                implementationStatus: "implemented",
              },
            ],
            lifecycleProfile: "standard-web-api-product",
            lifecycleStatus: "enabled",
            lifecycleConfigPath: "products/digital-marketing/kernel-product.yaml",
            deployment: {
              targets: ["compose-local"],
            },
          },
        },
      };
      await fs.writeFile(mockRegistryPath, JSON.stringify(mockRegistry));

      const result = await provider.getProductUnit("digital-marketing");

      expect(result).not.toBeNull();
      expect(result?.id).toBe("digital-marketing");
      expect(result?.name).toBe("Digital Marketing");
      expect(result?.kind).toBe("business-product");
      expect(result?.owner).toBe("digital-marketing-team");
      expect(result?.surfaces).toHaveLength(2);
      expect(result?.surfaces[0].type).toBe("backend-api");
      expect(result?.surfaces[1].type).toBe("web");
      expect(result?.lifecycleProfile).toBe("standard-web-api-product");
      expect(result?.lifecycleStatus).toBe("enabled");
      expect(result?.metadata?.sourceRegistry).toMatchObject({
        lifecycleConfigPath: "products/digital-marketing/kernel-product.yaml",
        deployment: {
          targets: ["compose-local"],
        },
      });
    });

    it("strict mode rejects unknown surface type", async () => {
      provider = new GhatanaFileRegistryProvider({
        registryPath: mockRegistryPath,
        strict: true,
      });
      await fs.writeFile(
        mockRegistryPath,
        JSON.stringify({
          version: "1.0.0",
          registry: {
            broken: {
              id: "broken",
              name: "Broken",
              kind: "business-product",
              surfaces: [
                {
                  type: "desktop",
                  implementationStatus: "implemented",
                },
              ],
              lifecycleStatus: "planned",
            },
          },
        })
      );

      await expect(provider.getProductUnit("broken")).rejects.toThrow(
        'Unknown surface type "desktop"'
      );
    });

    it("strict mode rejects unknown lifecycle status", async () => {
      provider = new GhatanaFileRegistryProvider({
        registryPath: mockRegistryPath,
        strict: true,
      });
      await fs.writeFile(
        mockRegistryPath,
        JSON.stringify({
          version: "1.0.0",
          registry: {
            broken: {
              id: "broken",
              name: "Broken",
              kind: "business-product",
              surfaces: [
                {
                  type: "backend-api",
                  implementationStatus: "implemented",
                },
              ],
              lifecycleStatus: "ready-ish",
            },
          },
        })
      );

      await expect(provider.getProductUnit("broken")).rejects.toThrow(
        'Unknown lifecycle status "ready-ish"'
      );
    });

    it("strict mode rejects unknown kind", async () => {
      provider = new GhatanaFileRegistryProvider({
        registryPath: mockRegistryPath,
        strict: true,
      });
      await fs.writeFile(
        mockRegistryPath,
        JSON.stringify({
          version: "1.0.0",
          registry: {
            broken: {
              id: "broken",
              name: "Broken",
              kind: "mystery",
              surfaces: [
                {
                  type: "backend-api",
                  implementationStatus: "implemented",
                },
              ],
              lifecycleStatus: "planned",
            },
          },
        })
      );

      await expect(provider.getProductUnit("broken")).rejects.toThrow(
        'Unknown product kind "mystery"'
      );
    });

    it("non-strict mode preserves validation warning rather than silently changing meaning", async () => {
      await fs.writeFile(
        mockRegistryPath,
        JSON.stringify({
          version: "1.0.0",
          registry: {
            broken: {
              id: "broken",
              name: "Broken",
              kind: "business-product",
              surfaces: [
                {
                  type: "desktop",
                  implementationStatus: "half-built",
                },
              ],
              lifecycleStatus: "planned",
            },
          },
        })
      );

      const result = await provider.getProductUnit("broken");

      expect(result?.surfaces[0].type).toBe("desktop");
      expect(result?.surfaces[0].implementationStatus).toBe("half-built");
      expect(result?.metadata?.validation).toMatchObject({
        errors: [
          {
            productId: "broken",
            path: "surfaces[0].type",
            message: 'Unknown surface type "desktop"',
          },
          {
            productId: "broken",
            path: "surfaces[0].implementationStatus",
            message: 'Unknown implementation status "half-built"',
          },
        ],
      });
    });

    it("converts Finance backend/operator/sdk shape", async () => {
      const mockRegistry = {
        version: "1.0.0",
        registry: {
          finance: {
            id: "finance",
            name: "Finance",
            kind: "business-product",
            surfaces: [
              {
                type: "backend-api",
                gradleModule: ":products:finance:api",
                implementationStatus: "implemented",
              },
              {
                type: "operator",
                gradleModule: ":products:finance:operator",
                implementationStatus: "implemented",
              },
              {
                type: "sdk",
                gradleModule: ":products:finance:sdk",
                implementationStatus: "planned",
              },
            ],
            lifecycleProfile: "backend-only-java-service",
            lifecycleStatus: "planned",
          },
        },
      };
      await fs.writeFile(mockRegistryPath, JSON.stringify(mockRegistry));

      const result = await provider.getProductUnit("finance");

      expect(result).not.toBeNull();
      expect(result?.id).toBe("finance");
      expect(result?.name).toBe("Finance");
      expect(result?.surfaces).toHaveLength(3);
      expect(result?.surfaces[0].type).toBe("backend-api");
      expect(result?.surfaces[1].type).toBe("operator");
      expect(result?.surfaces[2].type).toBe("sdk");
    });

    it("converts FlashIt web/mobile/backend shape", async () => {
      const mockRegistry = {
        version: "1.0.0",
        registry: {
          flashit: {
            id: "flashit",
            name: "FlashIt",
            kind: "business-product",
            surfaces: [
              {
                type: "backend-api",
                gradleModule: ":products:flashit:api",
                implementationStatus: "implemented",
              },
              {
                type: "web",
                packagePath: "products/flashit/web",
                implementationStatus: "implemented",
              },
              {
                type: "mobile-ios",
                packagePath: "products/flashit/ios",
                implementationStatus: "planned",
              },
              {
                type: "mobile-android",
                packagePath: "products/flashit/android",
                implementationStatus: "planned",
              },
            ],
            lifecycleProfile: "mobile-plus-api-product",
            lifecycleStatus: "planned",
          },
        },
      };
      await fs.writeFile(mockRegistryPath, JSON.stringify(mockRegistry));

      const result = await provider.getProductUnit("flashit");

      expect(result).not.toBeNull();
      expect(result?.surfaces).toHaveLength(4);
      expect(result?.surfaces[0].type).toBe("backend-api");
      expect(result?.surfaces[1].type).toBe("web");
      expect(result?.surfaces[2].type).toBe("mobile-ios");
      expect(result?.surfaces[3].type).toBe("mobile-android");
    });

    it("converts FlashIt mobile shorthand shape", async () => {
      const mockRegistry = {
        version: "1.0.0",
        registry: {
          flashit: {
            id: "flashit",
            name: "FlashIt",
            kind: "business-product",
            surfaces: [
              {
                type: "mobile",
                packagePath: "products/flashit/mobile",
                implementationStatus: "planned",
              },
            ],
            lifecycleStatus: "planned",
          },
        },
      };
      await fs.writeFile(mockRegistryPath, JSON.stringify(mockRegistry));

      const result = await provider.getProductUnit("flashit");

      expect(result?.surfaces[0].type).toBe("mobile");
    });

    it("converts YAPPC platform-provider shape", async () => {
      const mockRegistry = {
        version: "1.0.0",
        registry: {
          yappc: {
            id: "yappc",
            name: "YAPPC",
            kind: "platform-provider",
            surfaces: [
              {
                type: "backend-api",
                gradleModule: ":products:yappc:api",
                implementationStatus: "implemented",
              },
              {
                type: "web",
                packagePath: "products/yappc/web",
                implementationStatus: "implemented",
              },
              {
                type: "operator",
                gradleModule: ":products:yappc:operator",
                implementationStatus: "planned",
              },
            ],
            lifecycleProfile: "platform-provider-product",
            lifecycleStatus: "planned",
          },
        },
      };
      await fs.writeFile(mockRegistryPath, JSON.stringify(mockRegistry));

      const result = await provider.getProductUnit("yappc");

      expect(result).not.toBeNull();
      expect(result?.kind).toBe("platform-provider");
      expect(result?.surfaces).toHaveLength(3);
      expect(result?.surfaces[0].type).toBe("backend-api");
      expect(result?.surfaces[1].type).toBe("web");
      expect(result?.surfaces[2].type).toBe("operator");
    });
  });

  describe("listProductUnits", () => {
    it("returns all product units", async () => {
      const mockRegistry = {
        version: "1.0.0",
        registry: {
          "digital-marketing": {
            id: "digital-marketing",
            name: "Digital Marketing",
            kind: "business-product",
            surfaces: [],
          },
          finance: {
            id: "finance",
            name: "Finance",
            kind: "business-product",
            surfaces: [],
          },
        },
      };
      await fs.writeFile(mockRegistryPath, JSON.stringify(mockRegistry));

      const result = await provider.listProductUnits();

      expect(result).toHaveLength(2);
      expect(result[0].id).toBe("digital-marketing");
      expect(result[1].id).toBe("finance");
    });
  });

  describe("cache", () => {
    it("clearCache reloads an updated registry", async () => {
      await fs.writeFile(
        mockRegistryPath,
        JSON.stringify({
          version: "1.0.0",
          registry: {
            demo: {
              id: "demo",
              name: "Demo One",
              kind: "business-product",
              surfaces: [],
            },
          },
        })
      );

      expect((await provider.getProductUnit("demo"))?.name).toBe("Demo One");

      await fs.writeFile(
        mockRegistryPath,
        JSON.stringify({
          version: "1.0.0",
          registry: {
            demo: {
              id: "demo",
              name: "Demo Two",
              kind: "business-product",
              surfaces: [],
            },
          },
        })
      );

      expect((await provider.getProductUnit("demo"))?.name).toBe("Demo One");
      provider.clearCache();
      expect((await provider.getProductUnit("demo"))?.name).toBe("Demo Two");
    });
  });

  describe("validateRegistry", () => {
    it("returns structured registry errors", async () => {
      await fs.writeFile(
        mockRegistryPath,
        JSON.stringify({
          version: "1.0.0",
          registry: {
            broken: {
              id: "broken",
              name: "Broken",
              kind: "business-product",
              surfaces: [
                {
                  type: "backend-api",
                  implementationStatus: "unknown",
                },
              ],
            },
          },
        })
      );

      const result = await provider.validateRegistry();

      expect(result.valid).toBe(false);
      expect(result.errors[0]).toMatchObject({
        productId: "broken",
        path: "surfaces[0].implementationStatus",
      });
    });
  });

  describe("listProductUnitsByKind", () => {
    it("filters by kind", async () => {
      const mockRegistry = {
        version: "1.0.0",
        registry: {
          "digital-marketing": {
            id: "digital-marketing",
            name: "Digital Marketing",
            kind: "business-product",
            surfaces: [],
          },
          yappc: {
            id: "yappc",
            name: "YAPPC",
            kind: "platform-provider",
            surfaces: [],
          },
        },
      };
      await fs.writeFile(mockRegistryPath, JSON.stringify(mockRegistry));

      const result = await provider.listProductUnitsByKind("business-product");

      expect(result).toHaveLength(1);
      expect(result[0].id).toBe("digital-marketing");
    });
  });

  describe("validateProductUnit", () => {
    it("validates a correct product unit", async () => {
      const validProductUnit = {
        schemaVersion: "1.0.0",
        id: "test-product",
        name: "Test Product",
        kind: "business-product",
        registryProviderRef: { providerId: "ghatana-file-registry" },
        sourceProviderRef: { providerId: "ghatana-file-registry" },
        surfaces: [
          {
            id: "test-api",
            type: "backend-api",
            implementationStatus: "implemented",
          },
        ],
        lifecycleStatus: "enabled",
        lifecycleProfile: "standard-web-api-product",
      };

      const result = await provider.validateProductUnit(validProductUnit);

      expect(result.valid).toBe(true);
      expect(result.errors).toHaveLength(0);
    });

    it("rejects product without lifecycle profile when enabled", async () => {
      const invalidProductUnit = {
        schemaVersion: "1.0.0",
        id: "test-product",
        name: "Test Product",
        kind: "business-product",
        registryProviderRef: { providerId: "ghatana-file-registry" },
        sourceProviderRef: { providerId: "ghatana-file-registry" },
        surfaces: [],
        lifecycleStatus: "enabled",
      };

      const result = await provider.validateProductUnit(invalidProductUnit);

      expect(result.valid).toBe(false);
      expect(result.errors).toContain("Enabled product must have lifecycle profile");
    });

    it("rejects product without surfaces", async () => {
      const invalidProductUnit = {
        schemaVersion: "1.0.0",
        id: "test-product",
        name: "Test Product",
        kind: "business-product",
        registryProviderRef: { providerId: "ghatana-file-registry" },
        sourceProviderRef: { providerId: "ghatana-file-registry" },
        surfaces: [],
      };

      const result = await provider.validateProductUnit(invalidProductUnit);

      expect(result.valid).toBe(false);
      expect(result.errors).toContain("ProductUnit must have at least one surface");
    });
  });
});
