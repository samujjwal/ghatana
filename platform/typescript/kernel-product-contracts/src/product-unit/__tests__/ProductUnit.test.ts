/**
 * Tests for ProductUnit interface.
 */

import { describe, it, expect } from "vitest";
import {
  type ProductUnit,
  isProductUnit,
  validateProductUnit,
  validateProductUnitDetailed,
  createMinimalProductUnit,
  createExecutableProductUnit,
  type ProductUnitSurface,
} from "../ProductUnit";
import { isProductShape } from "../ProductShape";
import { isProductUnitSourceRef } from "../ProductUnitSourceRef";

describe("ProductUnit", () => {
  describe("isProductUnit", () => {
    it("returns true for valid ProductUnit objects", () => {
      const validProductUnit: ProductUnit = {
        schemaVersion: "1.0.0",
        id: "digital-marketing",
        name: "Digital Marketing",
        kind: "business-product",
        scope: {
          tenantId: "tenant-1",
          workspaceId: "workspace-1",
          projectId: "digital-marketing",
        },
        registryProviderRef: { providerId: "ghatana-file-registry" },
        sourceProviderRef: {
          providerId: "ghatana-file-registry",
          config: { lifecycleConfigPath: "products/digital-marketing/kernel-product.yaml" },
        },
        surfaces: [
          {
            id: "digital-marketing-api",
            type: "backend-api",
            implementationStatus: "implemented",            language: "java", runtime: "java-jre", buildSystem: "gradle",
          },
          {
            id: "digital-marketing-web",
            type: "web",
            implementationStatus: "implemented",            language: "typescript", runtime: "nodejs", buildSystem: "pnpm",
          },
        ],
        productShape: "marketing-ops",
        sourceRefs: [
          {
            kind: "monorepo-path",
            ref: "products/digital-marketing",
            displayName: "Digital Marketing source",
          },
        ],
        semanticArtifactRefs: ["artifact-evidence://dm/graph-summary"],
        lifecycleProfile: "standard-web-api-product",
        lifecycleStatus: "enabled",
      };

      expect(isProductUnit(validProductUnit)).toBe(true);
    });

    it("accepts planned and provider product shapes without executable lifecycle claims", () => {
      const platformProvider: ProductUnit = {
        schemaVersion: "1.0.0",
        id: "data-cloud",
        name: "Data Cloud",
        kind: "platform-provider",
        registryProviderRef: { providerId: "ghatana-file-registry" },
        sourceProviderRef: { providerId: "ghatana-file-registry" },
        surfaces: [
          {
            id: "data-cloud-api",
            type: "backend-api",
            implementationStatus: "implemented",            language: "java", runtime: "java-jre", buildSystem: "gradle",
          },
        ],
        productShape: "platform-provider",
        sourceRefs: [{ kind: "monorepo-path", ref: "products/data-cloud" }],
        lifecycleStatus: "planned",
      };

      expect(isProductUnit(platformProvider)).toBe(true);
      expect(isProductShape(platformProvider.productShape)).toBe(true);
      expect(isProductUnitSourceRef(platformProvider.sourceRefs?.[0])).toBe(true);
    });

    it("rejects external source provider refs without providerId", () => {
      const productUnit = {
        schemaVersion: "1.0.0",
        id: "external-app",
        name: "External App",
        kind: "external-application",
        registryProviderRef: { providerId: "registry" },
        sourceProviderRef: { providerId: "source" },
        surfaces: [
          {
            id: "external-web",
            type: "web",
            implementationStatus: "planned",
          },
        ],
        productShape: "external-repo",
        sourceRefs: [{ kind: "external-source-provider", ref: "repo://app" }],
      };

      expect(isProductUnit(productUnit)).toBe(false);
      expect(validateProductUnit(productUnit).errors).toContain(
        "external-source-provider source refs require providerId"
      );
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
        surfaces: [
          {
            id: "test-web",
            type: "web",
            implementationStatus: "planned",
          },
        ],
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

    it("rejects missing provider ids", () => {
      const productUnit: ProductUnit = {
        schemaVersion: "1.0.0",
        id: "test",
        name: "Test",
        kind: "business-product",
        registryProviderRef: { providerId: "" },
        sourceProviderRef: { providerId: "source" },
        surfaces: [
          {
            id: "test-web",
            type: "web",
            implementationStatus: "planned",
          },
        ],
      };

      expect(isProductUnit(productUnit)).toBe(false);
      expect(validateProductUnit(productUnit).errors).toContain(
        "registryProviderRef.providerId must be a non-empty string"
      );
    });

    it("rejects unknown kind values", () => {
      const productUnit = {
        schemaVersion: "1.0.0",
        id: "test",
        name: "Test",
        kind: "unknown-kind",
        registryProviderRef: { providerId: "registry" },
        sourceProviderRef: { providerId: "source" },
        surfaces: [],
      };

      expect(isProductUnit(productUnit)).toBe(false);
      expect(validateProductUnit(productUnit).errors).toContain(
        "kind is not a known ProductUnit kind"
      );
    });

    it("rejects invalid surface type", () => {
      const productUnit = {
        schemaVersion: "1.0.0",
        id: "test",
        name: "Test",
        kind: "business-product",
        registryProviderRef: { providerId: "registry" },
        sourceProviderRef: { providerId: "source" },
        surfaces: [
          {
            id: "test-api",
            type: "surprise",
            implementationStatus: "implemented",
          },
        ],
      };

      expect(isProductUnit(productUnit)).toBe(false);
      expect(validateProductUnit(productUnit).errors).toContain(
        "surfaces[0].type is not a known ProductUnit surface type"
      );
    });

    it("rejects invalid implementation status", () => {
      const productUnit = {
        schemaVersion: "1.0.0",
        id: "test",
        name: "Test",
        kind: "business-product",
        registryProviderRef: { providerId: "registry" },
        sourceProviderRef: { providerId: "source" },
        surfaces: [
          {
            id: "test-api",
            type: "backend-api",
            implementationStatus: "half-built",
          },
        ],
      };

      expect(isProductUnit(productUnit)).toBe(false);
      expect(validateProductUnit(productUnit).errors).toContain(
        "surfaces[0].implementationStatus is not a known implementation status"
      );
    });

    it("accepts language and build-system aware polyglot surfaces", () => {
      const productUnit = {
        schemaVersion: "1.0.0",
        id: "polyglot-service",
        name: "Polyglot Service",
        kind: "business-product",
        registryProviderRef: { providerId: "registry" },
        sourceProviderRef: { providerId: "source" },
        surfaces: [
          {
            id: "rust-api",
            type: "backend-api",
            implementationStatus: "implemented",
            language: "rust",
            runtime: "rust-native",
            buildSystem: "cargo",
            cratePath: "products/polyglot/rust-api",
            cargoToml: "products/polyglot/rust-api/Cargo.toml",
            adapterHint: "cargo-rust",
          },
          {
            id: "python-worker",
            type: "worker",
            implementationStatus: "implemented",
            language: "python",
            runtime: "python",
            buildSystem: "pyproject",
            pyprojectPath: "products/polyglot/python-worker/pyproject.toml",
            adapterHint: "python-pyproject",
          },
          {
            id: "node-api",
            type: "backend-api",
            implementationStatus: "implemented",
            language: "typescript",
            runtime: "nodejs",
            buildSystem: "pnpm",
            packagePath: "products/polyglot/node-api/package.json",
            adapterHint: "pnpm-node-api",
          },
        ],
      };

      expect(validateProductUnit(productUnit).valid).toBe(true);
    });

    it("rejects unsupported language and build-system values", () => {
      const productUnit = {
        schemaVersion: "1.0.0",
        id: "test",
        name: "Test",
        kind: "business-product",
        registryProviderRef: { providerId: "registry" },
        sourceProviderRef: { providerId: "source" },
        surfaces: [
          {
            id: "test-api",
            type: "backend-api",
            implementationStatus: "implemented",
            language: "ruby",
            buildSystem: "rake",
          },
        ],
      };

      expect(validateProductUnit(productUnit).errors).toEqual(
        expect.arrayContaining([
          "surfaces[0].language is not a known surface language",
          "surfaces[0].buildSystem is not a known surface build system",
        ])
      );
    });

    it("rejects enabled lifecycle without lifecycle profile", () => {
      const productUnit = {
        schemaVersion: "1.0.0",
        id: "test",
        name: "Test",
        kind: "business-product",
        registryProviderRef: { providerId: "registry" },
        sourceProviderRef: { providerId: "source" },
        surfaces: [
          {
            id: "test-web",
            type: "web",
            implementationStatus: "planned",
          },
        ],
        lifecycleStatus: "enabled",
      };

      expect(isProductUnit(productUnit)).toBe(false);
      expect(validateProductUnit(productUnit).errors).toContain(
        "enabled lifecycle requires lifecycleProfile"
      );
    });

    it("returns detailed reason codes for Studio/API diagnostics", () => {
      const productUnit = {
        schemaVersion: "1.0.0",
        id: "test",
        name: "Test",
        kind: "business-product",
        registryProviderRef: { providerId: "registry" },
        sourceProviderRef: { providerId: "ghatana-file-registry" },
        surfaces: [],
        lifecycleStatus: "enabled",
        metadata: {
          password: "raw-password",
        },
      };

      const result = validateProductUnitDetailed(productUnit);

      expect(result.valid).toBe(false);
      expect(result.issues.map((issue) => issue.reasonCode)).toEqual(
        expect.arrayContaining([
          "missing-scope",
          "missing-lifecycle-profile",
          "missing-lifecycle-config-path",
          "missing-executable-surfaces",
          "secret-like-config-or-metadata",
        ])
      );
    });

    it("rejects executable ProductUnits without surfaces", () => {
      const productUnit = {
        schemaVersion: "1.0.0",
        id: "test",
        name: "Test",
        kind: "business-product",
        scope: {
          tenantId: "tenant-1",
          workspaceId: "workspace-1",
          projectId: "test",
        },
        registryProviderRef: { providerId: "registry" },
        sourceProviderRef: { providerId: "source" },
        surfaces: [],
      };

      expect(isProductUnit(productUnit)).toBe(false);
      expect(validateProductUnit(productUnit).errors).toContain(
        "surfaces must contain at least one surface"
      );
    });

    it("rejects secret-like metadata", () => {
      const productUnit = {
        schemaVersion: "1.0.0",
        id: "test",
        name: "Test",
        kind: "business-product",
        registryProviderRef: { providerId: "registry" },
        sourceProviderRef: { providerId: "source" },
        surfaces: [
          {
            id: "test-web",
            type: "web",
            implementationStatus: "planned",
          },
        ],
        metadata: {
          apiKey: "raw-key",
        },
      };

      expect(isProductUnit(productUnit)).toBe(false);
      expect(validateProductUnit(productUnit).errors).toContain(
        "metadata must not include raw secret-like fields"
      );
    });

    it("rejects nested secret-like metadata inside arrays", () => {
      const productUnit = {
        schemaVersion: "1.0.0",
        id: "test",
        name: "Test",
        kind: "business-product",
        registryProviderRef: { providerId: "registry" },
        sourceProviderRef: { providerId: "source" },
        surfaces: [
          {
            id: "test-web",
            type: "web",
            implementationStatus: "planned",
          },
        ],
        metadata: {
          integrations: [{ credential: "raw-credential" }],
        },
      };

      expect(validateProductUnit(productUnit).errors).toContain(
        "metadata must not include raw secret-like fields"
      );
    });

    it("rejects secret-like provider config", () => {
      const productUnit = {
        schemaVersion: "1.0.0",
        id: "test",
        name: "Test",
        kind: "business-product",
        registryProviderRef: {
          providerId: "registry",
          config: { token: "raw-token" },
        },
        sourceProviderRef: { providerId: "source" },
        surfaces: [
          {
            id: "test-web",
            type: "web",
            implementationStatus: "planned",
          },
        ],
      };

      expect(validateProductUnit(productUnit).errors).toContain(
        "provider config must not include raw secret-like fields"
      );
    });

    it("rejects missing source provider ids", () => {
      const productUnit = {
        schemaVersion: "1.0.0",
        id: "test",
        name: "Test",
        kind: "business-product",
        registryProviderRef: { providerId: "registry" },
        sourceProviderRef: { providerId: "" },
        surfaces: [
          {
            id: "test-web",
            type: "web",
            implementationStatus: "planned",
          },
        ],
      };

      expect(validateProductUnit(productUnit).errors).toContain(
        "sourceProviderRef.providerId must be a non-empty string"
      );
    });

    it("rejects file-backed enabled lifecycle without lifecycle config path", () => {
      const productUnit = {
        schemaVersion: "1.0.0",
        id: "test",
        name: "Test",
        kind: "business-product",
        registryProviderRef: { providerId: "registry" },
        sourceProviderRef: { providerId: "ghatana-file-registry" },
        surfaces: [
          {
            id: "test-web",
            type: "web",
            implementationStatus: "implemented",            language: "typescript", runtime: "nodejs", buildSystem: "pnpm",
          },
        ],
        lifecycleProfile: "standard-web-api-product",
        lifecycleStatus: "enabled",
      };

      expect(isProductUnit(productUnit)).toBe(false);
      expect(validateProductUnit(productUnit).errors).toContain(
        "file-backed enabled lifecycle requires sourceProviderRef.config.lifecycleConfigPath"
      );
    });
  });

  describe("createMinimalProductUnit", () => {
    it("creates a draft-only ProductUnit skeleton", () => {
      const productUnit = createMinimalProductUnit(
        "test-product",
        "Test Product",
        "business-product"
      );

      expect(productUnit.id).toBe("test-product");
      expect(productUnit.name).toBe("Test Product");
      expect(productUnit.kind).toBe("business-product");
      expect(productUnit.surfaces).toEqual([]);
      expect(isProductUnit(productUnit)).toBe(false);
    });
  });

  describe("createExecutableProductUnit", () => {
    it("creates a valid executable ProductUnit when surfaces are supplied", () => {
      const productUnit = createExecutableProductUnit({
        id: "test-product",
        name: "Test Product",
        kind: "business-product",
        scope: {
          tenantId: "tenant-1",
          workspaceId: "workspace-1",
          projectId: "test-product",
        },
        surfaces: [
          {
            id: "test-web",
            type: "web",
            implementationStatus: "implemented",            language: "typescript", runtime: "nodejs", buildSystem: "pnpm",
          },
        ],
        lifecycleProfile: "standard-web-api-product",
        lifecycleStatus: "enabled",
      });

      expect(isProductUnit(productUnit)).toBe(true);
      expect(productUnit.sourceProviderRef.config).toEqual({
        lifecycleConfigPath: "kernel-product.yaml",
      });
    });

    it("preserves optional executable ProductUnit fields", () => {
      const productUnit = createExecutableProductUnit({
        id: "test-product",
        name: "Test Product",
        kind: "business-product",
        scope: {
          tenantId: "tenant-1",
          workspaceId: "workspace-1",
          projectId: "project-1",
        },
        owner: "platform-team",
        registryProviderRef: { providerId: "registry" },
        sourceProviderRef: { providerId: "github" },
        surfaces: [
          {
            id: "test-web",
            type: "web",
            implementationStatus: "planned",
          },
        ],
        conformance: {
          requiredChecks: ["typecheck"],
          level: "strict",
        },
        governance: {
          approvalGates: ["release-manager"],
        },
        metadata: {
          productLine: "studio",
        },
      });

      expect(productUnit.scope?.tenantId).toBe("tenant-1");
      expect(productUnit.owner).toBe("platform-team");
      expect(productUnit.conformance?.level).toBe("strict");
      expect(productUnit.governance?.approvalGates).toEqual(["release-manager"]);
      expect(productUnit.metadata).toEqual({ productLine: "studio" });
    });

    it("throws when executable ProductUnit input is not lifecycle valid", () => {
      expect(() =>
        createExecutableProductUnit({
          id: "test-product",
          name: "Test Product",
          kind: "business-product",
          surfaces: [],
        })
      ).toThrow(/Invalid executable ProductUnit/);
    });
  });

  describe("fixtures", () => {
    it("creates a valid Digital Marketing fixture", () => {
      const surfaces: ProductUnitSurface[] = [
        {
          id: "digital-marketing-api",
          type: "backend-api",
          implementationStatus: "implemented",            language: "java", runtime: "java-jre", buildSystem: "gradle",
          runtime: "java-jre",
          language: "java",
          buildSystem: "gradle",
          gradleModule: ":products:digital-marketing:api",
          adapterHint: "gradle-java-service",
        },
        {
          id: "digital-marketing-ui",
          type: "web",
          implementationStatus: "implemented",
          runtime: "nodejs",
          language: "typescript",
          buildSystem: "pnpm",
          packagePath: "products/digital-marketing/ui",
          adapterHint: "pnpm-vite-react",
        },
      ];

      const digitalMarketing: ProductUnit = {
        schemaVersion: "1.0.0",
        id: "digital-marketing",
        name: "Digital Marketing",
        kind: "business-product",
        scope: {
          tenantId: "tenant-1",
          workspaceId: "workspace-1",
          projectId: "digital-marketing",
        },
        owner: "digital-marketing-team",
        registryProviderRef: { providerId: "ghatana-file-registry" },
        sourceProviderRef: {
          providerId: "ghatana-file-registry",
          config: { lifecycleConfigPath: "products/digital-marketing/kernel-product.yaml" },
        },
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
          runtime: "java-jre",
          language: "java",
          buildSystem: "gradle",
          gradleModule: ":products:finance:api",
          adapterHint: "gradle-java-service",
        },
        {
          id: "finance-operator",
          type: "operator",
          implementationStatus: "implemented",
          runtime: "java-jre",
          language: "java",
          buildSystem: "gradle",
          gradleModule: ":products:finance:operator",
          adapterHint: "gradle-java-operator",
        },
        {
          id: "finance-sdk",
          type: "sdk",
          implementationStatus: "planned",
          runtime: "java-jre",
          language: "java",
          buildSystem: "gradle",
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
          runtime: "java-jre",
          language: "java",
          buildSystem: "gradle",
          gradleModule: ":products:flashit:api",
          adapterHint: "gradle-java-service",
        },
        {
          id: "flashit-web",
          type: "web",
          implementationStatus: "implemented",
          runtime: "nodejs",
          language: "typescript",
          buildSystem: "pnpm",
          packagePath: "products/flashit/web",
          adapterHint: "pnpm-vite-react",
        },
        {
          id: "flashit-ios",
          type: "mobile-ios",
          implementationStatus: "planned",
          runtime: "mobile-ios",
          language: "swift",
          buildSystem: "xcode",
          packagePath: "products/flashit/ios",
          adapterHint: "xcode-ios-app",
        },
        {
          id: "flashit-android",
          type: "mobile-android",
          implementationStatus: "planned",
          runtime: "kotlin-jvm",
          language: "kotlin",
          buildSystem: "gradle",
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
          language: "typescript",
          buildSystem: "pnpm",
          sourceRef: "https://github.com/example/external-repo.git",
          adapterHint: "pnpm-typescript-service",
        },
        {
          id: "external-web",
          type: "web",
          implementationStatus: "planned",
          runtime: "nodejs",
          language: "typescript",
          buildSystem: "pnpm",
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


































