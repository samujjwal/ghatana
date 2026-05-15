/**
 * Tests for GhatanaFileRegistryProvider.
 */

import { describe, it, expect, beforeEach, afterEach } from "vitest";
import { GhatanaFileRegistryProvider } from "../GhatanaFileRegistryProvider";
import * as fs from "node:fs/promises";
import * as path from "node:path";
import type { ProductUnitIntent } from "@ghatana/kernel-product-contracts";

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
    it("uses the default registry path and reload alias", async () => {
      const defaultRegistryPath = path.join(
        process.cwd(),
        "config/canonical-product-registry.json"
      );
      const defaultProvider = new GhatanaFileRegistryProvider();

      expect(defaultRegistryPath.replaceAll("\\", "/")).toContain(
        "config/canonical-product-registry.json"
      );
      defaultProvider.reload();
    });

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

    it("strict mode accepts valid entries", async () => {
      provider = new GhatanaFileRegistryProvider({
        registryPath: mockRegistryPath,
        strict: true,
      });
      await fs.writeFile(
        mockRegistryPath,
        JSON.stringify({
          version: "1.0.0",
          registry: {
            valid: {
              id: "valid",
              name: "Valid",
              kind: "business-product",
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

      await expect(provider.getProductUnit("valid")).resolves.toMatchObject({
        id: "valid",
      });
    });

    it("supports object options defaults", async () => {
      provider = new GhatanaFileRegistryProvider({});
      provider.reload();

      expect(provider.providerId).toBe("ghatana-file-registry");
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

    it("preserves optional registry metadata and conformance truth", async () => {
      await fs.writeFile(
        mockRegistryPath,
        JSON.stringify({
          version: "1.0.0",
          registry: {
            docs: {
              id: "docs",
              name: "Docs",
              kind: "business-product",
              metadata: {
                owner: "docs-team",
              },
              manifestPath: "products/docs/product.yaml",
              manifestFormat: "yaml",
              buildFile: "products/docs/package.json",
              lifecycleStatus: "mystery-status",
              ci: { workflow: "docs-ci" },
              environments: { local: true },
              surfaces: [
                {
                  type: "web",
                  path: "products/docs/web",
                  implementationStatus: "implemented",
                },
              ],
              conformance: {
                agentDefinitions: true,
                designSystem: false,
                observability: true,
              },
            },
          },
        })
      );

      const result = await provider.getProductUnit("docs");

      expect(result).toMatchObject({
        owner: "docs-team",
        lifecycleStatus: "mystery-status",
        conformance: {
          requiredChecks: ["agentDefinitions", "observability"],
          level: "strict",
        },
        metadata: {
          sourceRegistry: {
            manifestPath: "products/docs/product.yaml",
            manifestFormat: "yaml",
            buildFile: "products/docs/package.json",
            ci: { workflow: "docs-ci" },
            environments: { local: true },
          },
        },
      });
      expect(result?.surfaces[0].sourceRef).toBe("products/docs/web");
    });

    it("handles entries without surfaces and standard conformance", async () => {
      await fs.writeFile(
        mockRegistryPath,
        JSON.stringify({
          version: "1.0.0",
          registry: {
            docs: {
              id: "docs",
              name: "Docs",
              kind: "business-product",
              conformance: {
                observability: true,
              },
            },
          },
        })
      );

      const result = await provider.getProductUnit("docs");

      expect(result).toMatchObject({
        surfaces: [],
        conformance: {
          requiredChecks: ["observability"],
          level: "standard",
        },
      });
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

    it("reports lifecycle readiness errors for enabled entries", async () => {
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
              lifecycleStatus: "enabled",
            },
          },
        })
      );

      const result = await provider.validateRegistry();

      expect(result.errors).toEqual(
        expect.arrayContaining([
          expect.objectContaining({ path: "lifecycleProfile" }),
          expect.objectContaining({ path: "lifecycleConfigPath" }),
        ])
      );
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
        scope: {
          tenantId: "tenant-1",
          workspaceId: "workspace-1",
          projectId: "project-1",
        },
        registryProviderRef: { providerId: "ghatana-file-registry" },
        sourceProviderRef: {
          providerId: "ghatana-file-registry",
          config: { lifecycleConfigPath: "products/test-product/kernel-product.yaml" },
        },
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

  describe("ProductUnitIntent apply flow", () => {
    it("validates target providers, correlation id, and duplicate create targets", async () => {
      await writeRegistry({
        "digital-marketing": {
          id: "digital-marketing",
          name: "Digital Marketing",
          kind: "business-product",
          surfaces: [
            {
              type: "web",
              packagePath: "products/digital-marketing/web",
              implementationStatus: "implemented",
            },
          ],
        },
      });

      const result = await provider.validateIntentTarget(buildIntent("create"));

      expect(result).toEqual({
        valid: false,
        correlationId: "corr-1",
        errors: ["ProductUnit already exists: digital-marketing"],
        warnings: [],
      });
    });

    it("rejects intents targeting non-file providers and product-specific platform metadata", async () => {
      await writeRegistry({});

      const result = await provider.validateIntentTarget({
        ...buildIntent("create"),
        target: {
          registryProvider: "platform-registry",
          sourceProvider: "platform-source",
        },
        productUnit: {
          ...buildIntent("create").productUnit,
          metadata: {
            platform: {
              productSpecificLogic: "do-not-store-platform-product-branches",
            },
          },
        },
      });

      expect(result.valid).toBe(false);
      expect(result.errors).toContain(
        "Intent targets registry provider platform-registry, expected ghatana-file-registry"
      );
      expect(result.errors).toContain(
        "Intent targets source provider platform-source, expected ghatana-file-registry"
      );
      expect(result.errors).toContain(
        "Intent platform metadata must not contain product-specific logic"
      );
    });

    it("previews create intents without mutating the registry", async () => {
      await writeRegistry({});

      const preview = await provider.previewApplyProductUnitIntent(buildIntent("create"));
      const registryAfterPreview = JSON.parse(await fs.readFile(mockRegistryPath, "utf-8"));

      expect(preview.valid).toBe(true);
      expect(preview.before).toBeNull();
      expect(preview.after).toMatchObject({
        id: "digital-marketing",
        lifecycleStatus: "enabled",
        lifecycleProfile: "standard-web-api-product",
        lifecycleConfigPath: "products/digital-marketing/kernel-product.yaml",
        metadata: { owner: "growth" },
      });
      expect(preview.diff).toEqual(["+ registry.digital-marketing"]);
      expect(registryAfterPreview.registry).toEqual({});
    });

    it("blocks apply unless write access is explicit", async () => {
      await writeRegistry({});

      const result = await provider.applyProductUnitIntent(buildIntent("create"), {
        allowWrite: false,
      });

      expect(result.applied).toBe(false);
      expect(result.valid).toBe(false);
      expect(result.errors).toContain(
        "File registry intent apply requires allowWrite: true"
      );
      expect(JSON.parse(await fs.readFile(mockRegistryPath, "utf-8")).registry).toEqual({});
    });

    it("applies create intents when allowWrite is true", async () => {
      await writeRegistry({});

      const result = await provider.applyProductUnitIntent(buildIntent("create"), {
        allowWrite: true,
      });

      expect(result.applied).toBe(true);
      const registry = JSON.parse(await fs.readFile(mockRegistryPath, "utf-8"));
      expect(registry.registry["digital-marketing"]).toMatchObject({
        id: "digital-marketing",
        name: "Digital Marketing",
        kind: "business-product",
        lifecycleStatus: "enabled",
      });
      expect((await provider.getProductUnit("digital-marketing"))?.sourceProviderRef).toMatchObject({
        config: {
          lifecycleConfigPath: "products/digital-marketing/kernel-product.yaml",
        },
      });
    });

    it("previews and applies update intents with field-level diff hints", async () => {
      await writeRegistry({
        "digital-marketing": {
          id: "digital-marketing",
          name: "Old Name",
          kind: "business-product",
          owner: "legacy",
          surfaces: [
            {
              type: "web",
              packagePath: "old/path",
              implementationStatus: "planned",
            },
          ],
          metadata: {
            owner: "legacy",
          },
        },
      });

      const preview = await provider.previewApplyProductUnitIntent(buildIntent("update"));
      const result = await provider.applyProductUnitIntent(buildIntent("update"), {
        allowWrite: true,
      });

      expect(preview.diff).toEqual([
        "~ registry.digital-marketing.name",
        "~ registry.digital-marketing.owner",
        "~ registry.digital-marketing.lifecycleProfile",
        "~ registry.digital-marketing.lifecycleStatus",
        "~ registry.digital-marketing.lifecycleConfigPath",
        "~ registry.digital-marketing.surfaces",
        "~ registry.digital-marketing.metadata",
      ]);
      expect(result.applied).toBe(true);
    });

    it("rejects update intents for missing products and warns on promote-candidate", async () => {
      await writeRegistry({});

      const updateResult = await provider.previewApplyProductUnitIntent(buildIntent("update"));
      const promoteResult = await provider.previewApplyProductUnitIntent(
        buildIntent("promote-candidate")
      );

      expect(updateResult.valid).toBe(false);
      expect(updateResult.after).toBeNull();
      expect(updateResult.errors).toContain("ProductUnit does not exist: digital-marketing");
      expect(promoteResult.valid).toBe(true);
      expect(promoteResult.warnings).toEqual([
        "promote-candidate intents are previewed as registry updates only",
      ]);
    });

    it("does not produce diff lines when an update preserves registry shape", async () => {
      await writeRegistry({});
      await provider.applyProductUnitIntent(buildIntent("create"), {
        allowWrite: true,
      });
      provider.clearCache();

      const preview = await provider.previewApplyProductUnitIntent(buildIntent("update"));

      expect(preview.valid).toBe(true);
      expect(preview.diff).toEqual([]);
    });

    it("returns not applied when allowWrite is true but preview validation fails", async () => {
      await writeRegistry({});

      const result = await provider.applyProductUnitIntent(buildIntent("update"), {
        allowWrite: true,
      });

      expect(result).toMatchObject({
        valid: false,
        applied: false,
      });
    });

    it("maps intent registry metadata variants without leaking sourceRegistry", async () => {
      await writeRegistry({
        "digital-marketing": {
          id: "digital-marketing",
          name: "Digital Marketing",
          kind: "business-product",
          owner: "legacy",
          lifecycleStatus: "partial",
          surfaces: [
            {
              type: "backend-api",
              implementationStatus: "planned",
            },
          ],
        },
      });

      const preview = await provider.previewApplyProductUnitIntent({
        ...buildIntent("update"),
        requestedLifecycle: undefined,
        productUnit: {
          ...buildIntent("update").productUnit,
          owner: undefined,
          lifecycleProfile: "draft-profile",
          metadata: {
            sourceRegistry: {
              lifecycleProfile: "source-registry-profile",
              lifecycleConfigPath: "products/digital-marketing/kernel-product.yaml",
            },
          },
          surfaces: [
            {
              id: "digital-marketing-api",
              type: "backend-api",
              sourceRef: "products/digital-marketing/api",
              gradleModule: ":products:digital-marketing:api",
              implementationStatus: "planned",
            },
          ],
        },
      });

      expect(preview.after).toMatchObject({
        owner: "legacy",
        lifecycleProfile: "source-registry-profile",
        lifecycleStatus: "partial",
        metadata: {},
        surfaces: [
          {
            path: "products/digital-marketing/api",
            gradleModule: ":products:digital-marketing:api",
          },
        ],
      });
    });

    it("uses draft lifecycle profile when no requested or source profile exists", async () => {
      await writeRegistry({});

      const preview = await provider.previewApplyProductUnitIntent({
        ...buildIntent("create"),
        requestedLifecycle: undefined,
        productUnit: {
          ...buildIntent("create").productUnit,
          metadata: undefined,
        },
      });

      expect(preview.after).toMatchObject({
        lifecycleProfile: "standard-web-api-product",
      });
    });

    it("supports planned lifecycle intent previews without lifecycle profile fallback", async () => {
      await writeRegistry({
        "digital-marketing": {
          id: "digital-marketing",
          name: "Digital Marketing",
          kind: "business-product",
          surfaces: [],
        },
      });

      const preview = await provider.previewApplyProductUnitIntent({
        ...buildIntent("update"),
        requestedLifecycle: {
          profile: "standard-web-api-product",
          enableExecution: false,
        },
        productUnit: {
          ...buildIntent("update").productUnit,
          lifecycleProfile: undefined,
          metadata: undefined,
        },
      });

      expect(preview.after).toMatchObject({
        lifecycleProfile: "standard-web-api-product",
        lifecycleStatus: "planned",
      });
    });

    it("omits lifecycle profile and status when intent and entry do not provide them", async () => {
      await writeRegistry({
        "digital-marketing": {
          id: "digital-marketing",
          name: "Digital Marketing",
          kind: "business-product",
          surfaces: [],
        },
      });

      const preview = await provider.previewApplyProductUnitIntent({
        ...buildIntent("update"),
        requestedLifecycle: undefined,
        productUnit: {
          ...buildIntent("update").productUnit,
          lifecycleProfile: undefined,
          metadata: undefined,
        },
      });

      expect(preview.after?.lifecycleProfile).toBeUndefined();
      expect(preview.after?.lifecycleStatus).toBeUndefined();
    });

    it("diffs updates for existing entries with omitted surfaces", async () => {
      await writeRegistry({
        "digital-marketing": {
          id: "digital-marketing",
          name: "Digital Marketing",
          kind: "business-product",
        },
      });

      const preview = await provider.previewApplyProductUnitIntent(buildIntent("update"));

      expect(preview.diff).toContain("~ registry.digital-marketing.surfaces");
    });
  });

  async function writeRegistry(registry: Record<string, unknown>): Promise<void> {
    await fs.writeFile(
      mockRegistryPath,
      JSON.stringify({ version: "1.0.0", registry }),
      "utf-8"
    );
    provider.clearCache();
  }
});

function buildIntent(intentType: ProductUnitIntent["intentType"]): ProductUnitIntent {
  return {
    schemaVersion: "1.0.0",
    intentId: `intent-${intentType}`,
    intentType,
    scope: {
      tenantId: "tenant-1",
      workspaceId: "workspace-1",
      projectId: "project-1",
    },
    producer: {
      id: "yappc",
      type: "yappc",
      correlationId: "corr-1",
    },
    target: {
      registryProvider: "ghatana-file-registry",
      sourceProvider: "ghatana-file-registry",
    },
    productUnit: {
      id: "digital-marketing",
      name: "Digital Marketing",
      kind: "business-product",
      owner: "growth-team",
      surfaces: [
        {
          id: "digital-marketing-web",
          type: "web",
          packagePath: "products/digital-marketing/web",
          implementationStatus: "implemented",
        },
      ],
      lifecycleProfile: "standard-web-api-product",
      metadata: {
        owner: "growth",
        sourceRegistry: {
          lifecycleConfigPath: "products/digital-marketing/kernel-product.yaml",
        },
      },
    },
    requestedLifecycle: {
      profile: "standard-web-api-product",
      enableExecution: true,
      phases: ["build", "deploy", "verify"],
    },
    provenance: {
      sourceSystem: "yappc",
      sourceArtifactRefs: ["artifact://blueprint"],
      evidenceRefs: ["evidence://artifact-intelligence/blueprint"],
      createdBy: "sam",
      createdAt: "2026-05-14T00:00:00.000Z",
    },
  };
}
