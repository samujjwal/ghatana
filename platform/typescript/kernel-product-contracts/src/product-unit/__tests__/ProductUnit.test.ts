/**
 * Tests for ProductUnit interface.
 */

import { describe, it, expect } from "vitest";
import {
  type ProductUnit,
  isProductUnit,
  createMinimalProductUnit,
  type ProductUnitSurface,
} from "../ProductUnit";
import type { ProductUnitKind } from "../ProductUnitKind";

describe("ProductUnit", () => {
  describe("isProductUnit", () => {
    it("returns true for valid ProductUnit objects", () => {
      const validProductUnit: ProductUnit = {
        schemaVersion: "1.0.0",
        id: "digital-marketing",
        name: "Digital Marketing",
        kind: "business-product",
        registryProviderRef: { providerId: "ghatana-file-registry" },
        sourceProviderRef: { providerId: "ghatana-file-registry" },
        surfaces: [],
      };

      expect(isProductUnit(validProductUnit)).toBe(true);
    });

    it("returns false for invalid objects", () => {
      expect(isProductUnit(null)).toBe(false);
      expect(isProductUnit(undefined)).toBe(false);
      expect(isProductUnit({})).toBe(false);
      expect(isProductUnit({ id: "test" })).toBe(false);
      expect(isProductUnit("string")).toBe(false);
      expect(isProductUnit(123)).toBe(false);
    });

    it("returns false when required fields are missing", () => {
      const missingSchemaVersion = {
        id: "test",
        name: "Test",
        kind: "business-product",
        registryProviderRef: { providerId: "test" },
        sourceProviderRef: { providerId: "test" },
        surfaces: [],
      };
      expect(isProductUnit(missingSchemaVersion)).toBe(false);

      const missingId = {
        schemaVersion: "1.0.0",
        name: "Test",
        kind: "business-product",
        registryProviderRef: { providerId: "test" },
        sourceProviderRef: { providerId: "test" },
        surfaces: [],
      };
      expect(isProductUnit(missingId)).toBe(false);
    });
  });

  describe("createMinimalProductUnit", () => {
    it("creates a minimal valid ProductUnit", () => {
      const productUnit = createMinimalProductUnit(
        "test-product",
        "Test Product",
        "business-product"
      );

      expect(productUnit.schemaVersion).toBe("1.0.0");
      expect(productUnit.id).toBe("test-product");
      expect(productUnit.name).toBe("Test Product");
      expect(productUnit.kind).toBe("business-product");
      expect(productUnit.registryProviderRef.providerId).toBe(
        "ghatana-file-registry"
      );
      expect(productUnit.sourceProviderRef.providerId).toBe(
        "ghatana-file-registry"
      );
      expect(productUnit.surfaces).toEqual([]);
    });
  });

  describe("fixtures", () => {
    it("creates a valid Digital Marketing fixture", () => {
      const surfaces: ProductUnitSurface[] = [
        {
          id: "digital-marketing-api",
          type: "backend-api",
          implementationStatus: "implemented",
          runtime: "java",
          gradleModule: ":products:digital-marketing:api",
          adapterHint: "gradle-java-service",
        },
        {
          id: "digital-marketing-ui",
          type: "web",
          implementationStatus: "implemented",
          runtime: "nodejs",
          packagePath: "products/digital-marketing/ui",
          adapterHint: "pnpm-vite-react",
        },
      ];

      const digitalMarketing: ProductUnit = {
        schemaVersion: "1.0.0",
        id: "digital-marketing",
        name: "Digital Marketing",
        kind: "business-product",
        owner: "digital-marketing-team",
        registryProviderRef: { providerId: "ghatana-file-registry" },
        sourceProviderRef: { providerId: "ghatana-file-registry" },
        surfaces,
        lifecycleProfile: "standard-web-api-product",
        lifecycleStatus: "enabled",
      };

      expect(isProductUnit(digitalMarketing)).toBe(true);
      expect(digitalMarketing.surfaces).toHaveLength(2);
      expect(digitalMarketing.surfaces[0].type).toBe("backend-api");
      expect(digitalMarketing.surfaces[1].type).toBe("web");
    });

    it("creates a valid Finance fixture with backend/operator/sdk", () => {
      const surfaces: ProductUnitSurface[] = [
        {
          id: "finance-api",
          type: "backend-api",
          implementationStatus: "implemented",
          runtime: "java",
          gradleModule: ":products:finance:api",
          adapterHint: "gradle-java-service",
        },
        {
          id: "finance-operator",
          type: "operator",
          implementationStatus: "implemented",
          runtime: "java",
          gradleModule: ":products:finance:operator",
          adapterHint: "gradle-java-operator",
        },
        {
          id: "finance-sdk",
          type: "sdk",
          implementationStatus: "planned",
          runtime: "java",
          gradleModule: ":products:finance:sdk",
          adapterHint: "gradle-java-library",
        },
      ];

      const finance: ProductUnit = {
        schemaVersion: "1.0.0",
        id: "finance",
        name: "Finance",
        kind: "business-product",
        owner: "finance-team",
        registryProviderRef: { providerId: "ghatana-file-registry" },
        sourceProviderRef: { providerId: "ghatana-file-registry" },
        surfaces,
        lifecycleProfile: "backend-only-java-service",
        lifecycleStatus: "planned",
      };

      expect(isProductUnit(finance)).toBe(true);
      expect(finance.surfaces).toHaveLength(3);
      expect(finance.surfaces[0].type).toBe("backend-api");
      expect(finance.surfaces[1].type).toBe("operator");
      expect(finance.surfaces[2].type).toBe("sdk");
    });

    it("creates a valid FlashIt fixture with web/mobile/backend", () => {
      const surfaces: ProductUnitSurface[] = [
        {
          id: "flashit-api",
          type: "backend-api",
          implementationStatus: "implemented",
          runtime: "java",
          gradleModule: ":products:flashit:api",
          adapterHint: "gradle-java-service",
        },
        {
          id: "flashit-web",
          type: "web",
          implementationStatus: "implemented",
          runtime: "nodejs",
          packagePath: "products/flashit/web",
          adapterHint: "pnpm-vite-react",
        },
        {
          id: "flashit-ios",
          type: "mobile-ios",
          implementationStatus: "planned",
          runtime: "swift",
          packagePath: "products/flashit/ios",
          adapterHint: "xcode-ios-app",
        },
        {
          id: "flashit-android",
          type: "mobile-android",
          implementationStatus: "planned",
          runtime: "kotlin",
          packagePath: "products/flashit/android",
          adapterHint: "gradle-android-app",
        },
      ];

      const flashIt: ProductUnit = {
        schemaVersion: "1.0.0",
        id: "flashit",
        name: "FlashIt",
        kind: "business-product",
        owner: "flashit-team",
        registryProviderRef: { providerId: "ghatana-file-registry" },
        sourceProviderRef: { providerId: "ghatana-file-registry" },
        surfaces,
        lifecycleProfile: "mobile-plus-api-product",
        lifecycleStatus: "planned",
      };

      expect(isProductUnit(flashIt)).toBe(true);
      expect(flashIt.surfaces).toHaveLength(4);
      expect(flashIt.surfaces[0].type).toBe("backend-api");
      expect(flashIt.surfaces[1].type).toBe("web");
      expect(flashIt.surfaces[2].type).toBe("mobile-ios");
      expect(flashIt.surfaces[3].type).toBe("mobile-android");
    });

    it("creates a valid external repo fixture", () => {
      const surfaces: ProductUnitSurface[] = [
        {
          id: "external-api",
          type: "backend-api",
          implementationStatus: "planned",
          runtime: "nodejs",
          sourceRef: "https://github.com/example/external-repo.git",
          adapterHint: "pnpm-typescript-service",
        },
        {
          id: "external-web",
          type: "web",
          implementationStatus: "planned",
          runtime: "nodejs",
          sourceRef: "https://github.com/example/external-repo.git",
          adapterHint: "pnpm-vite-react",
        },
      ];

      const externalRepo: ProductUnit = {
        schemaVersion: "1.0.0",
        id: "external-example",
        name: "External Example",
        kind: "external-application",
        owner: "external-team",
        registryProviderRef: {
          providerId: "github",
          config: { repo: "example/external-repo" },
        },
        sourceProviderRef: {
          providerId: "github",
          config: { repo: "example/external-repo" },
        },
        surfaces,
        lifecycleProfile: "standard-web-api-product",
        lifecycleStatus: "planned",
      };

      expect(isProductUnit(externalRepo)).toBe(true);
      expect(externalRepo.kind).toBe("external-application");
      expect(externalRepo.registryProviderRef.providerId).toBe("github");
      expect(externalRepo.surfaces).toHaveLength(2);
    });
  });
});
