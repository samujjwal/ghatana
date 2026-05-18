/**
 * PHR lifecycle pilot tests.
 *
 * Validates that PHR is correctly configured as the second lifecycle
 * execution-allowed product (alongside Digital Marketing), and that the
 * full lifecycle pilot works end-to-end through:
 * registry load → ProductUnit validation → lifecycle planning → adapter resolution.
 *
 * PHR is a healthcare product with FHIR R4 support and Nepal Directive 2081.
 * Healthcare-specific gates (consent, pii-classification, audit-evidence,
 * fhir-contract-validation, tenant-data-sovereignty) are active at execution time.
 */
import { beforeEach, describe, expect, it } from "vitest";
import * as path from "node:path";
import * as url from "node:url";
import { CanonicalRegistryLoader } from "../io/CanonicalRegistryLoader.js";
import { ProductLifecyclePlanner } from "../planning/ProductLifecyclePlanner.js";
import { ToolchainResolver } from "../planning/ToolchainResolver.js";
import {
  validateProductUnit,
  createExecutableProductUnit,
} from "@ghatana/kernel-product-contracts";

const REPO_ROOT = path.join(
  path.dirname(url.fileURLToPath(import.meta.url)),
  "../../../../..",
);
const CONFIG_DIR = path.join(REPO_ROOT, "config");

// --- Registry field validation -----------------------------------------------

describe("§PHR — PHR registry field validation", () => {
  let registry: CanonicalRegistryLoader;

  beforeEach(() => {
    registry = new CanonicalRegistryLoader(CONFIG_DIR);
  });

  it("phr is lifecycle enabled", async () => {
    const phr = await registry.getProduct("phr");
    const lifecycle = phr.lifecycle as Record<string, unknown>;
    expect(lifecycle["enabled"]).toBe(true);
  });

  it("phr has lifecycleExecutionAllowed = true", async () => {
    const phr = await registry.getProduct("phr");
    const raw = phr as unknown as Record<string, unknown>;
    expect(raw["lifecycleExecutionAllowed"]).toBe(true);
  });

  it("phr has lifecycleStatus = enabled", async () => {
    const phr = await registry.getProduct("phr");
    const raw = phr as unknown as Record<string, unknown>;
    expect(raw["lifecycleStatus"]).toBe("enabled");
  });

  it("phr has backend-api and web surfaces", async () => {
    const phr = await registry.getProduct("phr");
    const surfaceTypes = phr.surfaces.map((s) => s.type);
    expect(surfaceTypes).toContain("backend-api");
    expect(surfaceTypes).toContain("web");
  });

  it("phr uses Gradle adapter for backend-api", async () => {
    const phr = await registry.getProduct("phr");
    expect(phr.toolchain?.adapters?.["backend-api"]).toBe(
      "gradle-java-service",
    );
  });

  it("phr uses pnpm adapter for web", async () => {
    const phr = await registry.getProduct("phr");
    expect(phr.toolchain?.adapters?.["web"]).toBe("pnpm-vite-react");
  });

  it("phr has jvm-service artifact for backend-api", async () => {
    const phr = await registry.getProduct("phr");
    expect(phr.artifacts?.["backend-api"]?.type).toBe("jvm-service");
    expect(phr.artifacts?.["backend-api"]?.packaging).toBe("jar");
  });

  it("phr has static-web-bundle artifact for web", async () => {
    const phr = await registry.getProduct("phr");
    expect(phr.artifacts?.["web"]?.type).toBe("static-web-bundle");
    expect(phr.artifacts?.["web"]?.packaging).toBe("static-files");
  });

  it("phr deploys to compose-local", async () => {
    const phr = await registry.getProduct("phr");
    expect(phr.deployment?.targets).toContain("compose-local");
  });

  it("phr is classified as a business-product (not platform-provider)", async () => {
    const phr = await registry.getProduct("phr");
    expect(phr.kind).toBe("business-product");
  });
});

// --- Healthcare readiness codes -----------------------------------------------

describe("§PHR — PHR healthcare readiness validation", () => {
  let registry: CanonicalRegistryLoader;

  beforeEach(() => {
    registry = new CanonicalRegistryLoader(CONFIG_DIR);
  });

  it("phr lifecycleReadiness.status is enabled", async () => {
    const phr = await registry.getProduct("phr");
    const raw = phr as unknown as Record<string, unknown>;
    const readiness = raw["lifecycleReadiness"] as Record<string, unknown>;
    expect(readiness["status"]).toBe("enabled");
  });

  it("phr has healthcare-pilot-enabled reason code", async () => {
    const phr = await registry.getProduct("phr");
    const raw = phr as unknown as Record<string, unknown>;
    const readiness = raw["lifecycleReadiness"] as Record<string, unknown>;
    const reasonCodes = readiness["reasonCodes"] as string[];
    expect(reasonCodes).toContain("healthcare-pilot-enabled");
  });

  it("phr has required healthcare gates declared", async () => {
    const phr = await registry.getProduct("phr");
    const raw = phr as unknown as Record<string, unknown>;
    const readiness = raw["lifecycleReadiness"] as Record<string, unknown>;
    const gates = readiness["requiredGates"] as string[];
    expect(gates).toContain("consent");
    expect(gates).toContain("pii-classification");
    expect(gates).toContain("audit-evidence");
    expect(gates).toContain("fhir-contract-validation");
    expect(gates).toContain("tenant-data-sovereignty");
  });
});

// --- Lifecycle planning -------------------------------------------------------

describe("§PHR — PHR lifecycle planning", () => {
  let planner: ProductLifecyclePlanner;

  beforeEach(() => {
    planner = new ProductLifecyclePlanner(REPO_ROOT);
  });

  it("loads PHR kernel-product.yaml config", async () => {
    const config = await planner.loadProductConfig("phr");
    expect(config.productId).toBe("phr");
    expect(config.lifecycleProfile).toBe("standard-web-api-product");
  });

  it("plans build phase with jvm-service and static-web-bundle artifacts", async () => {
    const plan = await planner.plan("phr", "build");

    expect(plan.productId).toBe("phr");
    expect(plan.phase).toBe("build");
    expect(plan.schemaVersion).toBe("1.0.0");
    expect(plan.runId).toBeTruthy();
    expect(plan.correlationId).toBeTruthy();

    const artifactTypes = plan.expectedArtifacts.map((a) => a.type);
    expect(artifactTypes).toContain("jvm-service");
    expect(artifactTypes).toContain("static-web-bundle");
  });

  it("plans deploy phase with deployment-manifest required manifests", async () => {
    const plan = await planner.plan("phr", "deploy");

    expect(plan.phase).toBe("deploy");
    expect(plan.productId).toBe("phr");
    expect(plan.requiredManifests).toContain("deployment-manifest");
  });

  it("phr build plan has backend-api and web steps", async () => {
    const plan = await planner.plan("phr", "build");

    const stepSurfaces = plan.steps.map((s) => s.surface);
    expect(stepSurfaces).toContain("backend-api");
    expect(stepSurfaces).toContain("web");
  });

  it("phr build plan steps use the correct adapters", async () => {
    const plan = await planner.plan("phr", "build");

    const backendStep = plan.steps.find((s) => s.surface === "backend-api");
    const webStep = plan.steps.find((s) => s.surface === "web");

    expect(backendStep?.adapter).toBe("gradle-java-service");
    expect(webStep?.adapter).toBe("pnpm-vite-react");
  });
});

// --- Toolchain adapter validation ---------------------------------------------

describe("§PHR — PHR toolchain adapter validation", () => {
  it("resolves gradle-java-service adapter", async () => {
    const resolver = new ToolchainResolver(CONFIG_DIR);
    const adapter = await resolver.resolve("gradle-java-service");

    expect(adapter.supportedSurfaceTypes).toContain("backend-api");
    expect(adapter.supportedPhases).toContain("build");
  });

  it("resolves pnpm-vite-react adapter", async () => {
    const resolver = new ToolchainResolver(CONFIG_DIR);
    const adapter = await resolver.resolve("pnpm-vite-react");

    expect(adapter.supportedSurfaceTypes).toContain("web");
    expect(adapter.supportedPhases).toContain("build");
  });

  it("validates gradle-java-service supports backend-api build phase", async () => {
    const resolver = new ToolchainResolver(CONFIG_DIR);
    const errors = await resolver.validateAdapter(
      "gradle-java-service",
      "build",
      "backend-api",
    );

    expect(errors).toHaveLength(0);
  });

  it("validates pnpm-vite-react supports web build phase", async () => {
    const resolver = new ToolchainResolver(CONFIG_DIR);
    const errors = await resolver.validateAdapter(
      "pnpm-vite-react",
      "build",
      "web",
    );

    expect(errors).toHaveLength(0);
  });
});

// --- ProductUnit validation ---------------------------------------------------

describe("§PHR — PHR ProductUnit validation", () => {
  it("createExecutableProductUnit produces valid unit for phr", () => {
    const unit = createExecutableProductUnit({
      id: "phr",
      name: "Personal Health Records",
      kind: "business-product",
      scope: {
        tenantId: "platform",
        workspaceId: "phr-ws",
        projectId: "phr",
      },
      lifecycleProfile: "standard-web-api-product",
      lifecycleStatus: "enabled",
      registryProviderRef: { providerId: "registry" },
      sourceProviderRef: { providerId: "source", config: {} },
      surfaces: [
        {
          id: "backend-api",
          type: "backend-api",
          implementationStatus: "implemented",
          sourceRef: "products/phr",
        },
        {
          id: "web",
          type: "web",
          implementationStatus: "implemented",
          sourceRef: "products/phr/apps/web",
        },
      ],
    });

    const result = validateProductUnit(unit);
    expect(result.valid).toBe(true);
    expect(result.errors).toHaveLength(0);
    expect(unit.id).toBe("phr");
    expect(unit.kind).toBe("business-product");
  });

  it("validateProductUnit returns invalid for unit with empty id", () => {
    const corrupted = {
      id: "",
      name: "PHR",
      kind: "business-product",
      scope: { tenantId: "p", workspaceId: "ws", projectId: "p" },
      lifecycleProfile: "standard-web-api-product",
      lifecycleStatus: "enabled",
      registryProviderRef: { providerId: "registry" },
      sourceProviderRef: { providerId: "source", config: {} },
      surfaces: [],
    };
    const result = validateProductUnit(corrupted);
    expect(result.valid).toBe(false);
    expect(result.errors.length).toBeGreaterThan(0);
  });
});
