/**
 * Unit tests for ProductUnit projection function.
 *
 * These tests validate the projectProductUnit function with mock data.
 * Golden registry tests that load actual registry data are integration tests
 * and should be run from the repo root.
 */

import { describe, it, expect } from "vitest";
import {
  projectProductUnit,
  type ProductUnit,
  type ProductUnitProjectionInput,
  type ProductUnitSurface,
  validateProductUnit,
} from "../ProductUnit";
import type { ProductUnitKind } from "../ProductUnitKind";
import type { ProductShape } from "../ProductShape";

describe("ProductUnit Projection", () => {
  describe("projectProductUnit function", () => {
    it("should project a valid ProductUnit with minimal fields", () => {
      const input: ProductUnitProjectionInput = {
        productId: "test-product",
        productName: "Test Product",
        productKind: "business-product" as ProductUnitKind,
        lifecycleStatus: "enabled",
        lifecycleExecutionAllowed: true,
        lifecycleConfigPath: "products/test/kernel-product.yaml",
        lifecycleProfile: "standard-web-api-product",
        scope: {
          tenantId: "tenant-1",
          workspaceId: "workspace-1",
          projectId: "test-product",
        },
        surfaces: [
          {
            id: "test-api",
            type: "backend-api",
            implementationStatus: "implemented",            language: "java", runtime: "java-jre", buildSystem: "gradle",
          },
        ],
      };

      const productUnit = projectProductUnit(input);

      expect(productUnit.schemaVersion).toBe("1.0.0");
      expect(productUnit.id).toBe("test-product");
      expect(productUnit.name).toBe("Test Product");
      expect(productUnit.kind).toBe("business-product");
      expect(productUnit.lifecycleStatus).toBe("enabled");
      expect(productUnit.surfaces).toHaveLength(1);
      expect(productUnit.surfaces[0].id).toBe("test-api");
    });

    it("should project a ProductUnit with all optional fields", () => {
      const input: ProductUnitProjectionInput = {
        productId: "full-product",
        productName: "Full Product",
        productKind: "platform-provider" as ProductUnitKind,
        lifecycleStatus: "enabled",
        lifecycleExecutionAllowed: true,
        lifecycleConfigPath: "products/full/kernel-product.yaml",
        surfaces: [
          {
            id: "full-api",
            type: "backend-api",
            implementationStatus: "implemented",
            language: "java",
            runtime: "java-jre",
            buildSystem: "gradle",
            packagePath: "products/full/api",
          },
          {
            id: "full-web",
            type: "web",
            implementationStatus: "implemented",
            language: "typescript",
            runtime: "nodejs",
            buildSystem: "pnpm",
            packagePath: "products/full/web",
          },
        ],
        productShape: "platform-provider" as ProductShape,
        lifecycleProfile: "standard-web-api-product",
        owner: "platform-team",
        providerMode: "platform",
        scope: {
          tenantId: "tenant-1",
          workspaceId: "workspace-1",
          projectId: "full-product",
        },
        environments: [
          {
            name: "local",
            target: "compose-local",
            variables: {
              ENV: "local",
            },
          },
        ],
        policyPacks: ["security-policy", "compliance-policy"],
        semanticArtifactRefs: ["artifact-evidence://full/graph-summary"],
        metadata: {
          version: "1.0.0",
        },
      };

      const productUnit = projectProductUnit(input);

      expect(productUnit.id).toBe("full-product");
      expect(productUnit.kind).toBe("platform-provider");
      expect(productUnit.productShape).toBe("platform-provider");
      expect(productUnit.lifecycleProfile).toBe("standard-web-api-product");
      expect(productUnit.owner).toBe("platform-team");
      expect(productUnit.providerMode).toBe("platform");
      expect(productUnit.scope).toBeDefined();
      expect(productUnit.scope?.tenantId).toBe("tenant-1");
      expect(productUnit.environments).toBeDefined();
      expect(productUnit.environments?.length).toBe(1);
      expect(productUnit.policyPacks).toBeDefined();
      expect(productUnit.policyPacks?.length).toBe(2);
      expect(productUnit.semanticArtifactRefs).toBeDefined();
      expect(productUnit.semanticArtifactRefs?.length).toBe(1);
      expect(productUnit.metadata).toBeDefined();
      expect(productUnit.metadata?.version).toBe("1.0.0");
    });

    it("should validate the projected ProductUnit", () => {
      const input: ProductUnitProjectionInput = {
        productId: "valid-product",
        productName: "Valid Product",
        productKind: "business-product" as ProductUnitKind,
        lifecycleStatus: "enabled",
        lifecycleExecutionAllowed: true,
        lifecycleConfigPath: "products/valid/kernel-product.yaml",
        lifecycleProfile: "standard-web-api-product",
        scope: {
          tenantId: "tenant-1",
          workspaceId: "workspace-1",
          projectId: "valid-product",
        },
        surfaces: [
          {
            id: "valid-api",
            type: "backend-api",
            implementationStatus: "implemented",
            language: "java",
            runtime: "java-jre",
            buildSystem: "gradle",
          },
        ],
      };

      const productUnit = projectProductUnit(input);
      const result = validateProductUnit(productUnit);

      expect(result.valid).toBe(true);
      expect(result.errors).toHaveLength(0);
    });

    it("should throw error for invalid ProductUnit projection", () => {
      const input: ProductUnitProjectionInput = {
        productId: "", // Invalid: empty id
        productName: "Invalid Product",
        productKind: "business-product" as ProductUnitKind,
        lifecycleStatus: "enabled",
        lifecycleExecutionAllowed: true,
        lifecycleConfigPath: "products/invalid/kernel-product.yaml",
        surfaces: [], // Invalid: no surfaces
      };

      expect(() => projectProductUnit(input)).toThrow();
    });

    it("should include all provider refs in projection", () => {
      const input: ProductUnitProjectionInput = {
        productId: "provider-product",
        productName: "Provider Product",
        productKind: "shared-service" as ProductUnitKind,
        lifecycleStatus: "enabled",
        lifecycleExecutionAllowed: true,
        lifecycleConfigPath: "products/provider/kernel-product.yaml",
        lifecycleProfile: "standard-web-api-product",
        scope: {
          tenantId: "tenant-1",
          workspaceId: "workspace-1",
          projectId: "provider-product",
        },
        surfaces: [
          {
            id: "provider-api",
            type: "backend-api",
            implementationStatus: "implemented",
            language: "java",
            runtime: "java-jre",
            buildSystem: "gradle",
          },
        ],
      };

      const productUnit = projectProductUnit(input);

      expect(productUnit.registryProviderRef).toBeDefined();
      expect(productUnit.registryProviderRef.providerId).toBe("ghatana-file-registry");
      expect(productUnit.sourceProviderRef).toBeDefined();
      expect(productUnit.sourceProviderRef.providerId).toBe("ghatana-file-registry");
      expect(productUnit.sourceProviderRef.config).toBeDefined();
      expect(productUnit.sourceProviderRef.config?.lifecycleConfigPath).toBe("products/provider/kernel-product.yaml");
    });

    it("should support different ProductUnit kinds", () => {
      const kinds: ProductUnitKind[] = [
        "business-product",
        "platform-provider",
        "shared-service",
        "demo-example",
        "domain-pack",
        "sdk",
        "plugin",
        "data-pipeline",
        "agent-runtime",
        "external-application",
      ];

      for (const kind of kinds) {
        const input: ProductUnitProjectionInput = {
          productId: `${kind}-product`,
          productName: `${kind} Product`,
          productKind: kind,
          lifecycleStatus: "enabled",
          lifecycleExecutionAllowed: true,
          lifecycleConfigPath: `products/${kind}/kernel-product.yaml`,
          lifecycleProfile: "standard-web-api-product",
          scope: {
            tenantId: "tenant-1",
            workspaceId: "workspace-1",
            projectId: `${kind}-product`,
          },
          surfaces: [
            {
              id: `${kind}-surface`,
              type: "backend-api",
              implementationStatus: "implemented",
              language: "java",
              runtime: "java-jre",
              buildSystem: "gradle",
            },
          ],
        };

        const productUnit = projectProductUnit(input);
        expect(productUnit.kind).toBe(kind);
      }
    });

    it("should support different lifecycle statuses", () => {
      const statuses = ["disabled", "planned", "partial", "enabled"] as const;

      for (const status of statuses) {
        const input: ProductUnitProjectionInput = {
          productId: `status-${status}`,
          productName: `Status ${status}`,
          productKind: "business-product" as ProductUnitKind,
          lifecycleStatus: status,
          lifecycleExecutionAllowed: status === "enabled",
          lifecycleConfigPath: "products/status/kernel-product.yaml",
          ...(status === "enabled" ? {
            lifecycleProfile: "standard-web-api-product",
            scope: {
              tenantId: "tenant-1",
              workspaceId: "workspace-1",
              projectId: `status-${status}`,
            },
          } : {}),
          surfaces: [
            {
              id: "status-surface",
              type: "backend-api",
              implementationStatus: "implemented",
              language: "java",
              runtime: "java-jre",
              buildSystem: "gradle",
            },
          ],
        };

        const productUnit = projectProductUnit(input);
        expect(productUnit.lifecycleStatus).toBe(status);
      }
    });
  });
});



