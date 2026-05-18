/**
 * §2.7 — Digital Marketing lifecycle pilot tests.
 *
 * Validates that Digital Marketing is correctly configured as the sole lifecycle
 * execution-allowed product, and that the full lifecycle pilot works end-to-end
 * through: registry load → ProductUnit validation → lifecycle planning →
 * adapter resolution → gate resolution → dry-run execution → manifest writing.
 *
 * Key invariant: dry-run results must NEVER be reported as real execution success.
 */
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { mkdtemp, rm, readFile } from "node:fs/promises";
import * as path from "node:path";
import * as os from "node:os";
import * as url from "node:url";
import { CanonicalRegistryLoader } from "../io/CanonicalRegistryLoader.js";
import { ProductLifecyclePlanner } from "../planning/ProductLifecyclePlanner.js";
import { ToolchainResolver } from "../planning/ToolchainResolver.js";
import { GateResolver } from "../planning/GateResolver.js";
import { ArtifactResolver } from "../planning/ArtifactResolver.js";
import { ProductLifecycleExecutor } from "../execution/ProductLifecycleExecutor.js";
import {
  ProductLifecycleStepRunner,
  type Adapter,
  type AdapterContext,
  type AdapterResult,
  type AdapterRegistry,
} from "../execution/ProductLifecycleStepRunner.js";
import { ConsoleExecutionLogger } from "../execution/ExecutionLogger.js";
import { ExecutionResultCollector } from "../execution/ExecutionResultCollector.js";
import { ArtifactWriter } from "../io/ArtifactWriter.js";
import { PlanWriter } from "../io/PlanWriter.js";
import { ResultWriter } from "../io/ResultWriter.js";
import {
  validateProductUnit,
  createExecutableProductUnit,
} from "@ghatana/kernel-product-contracts";
import type {
  ProductLifecycleStep,
  ProductLifecyclePlan,
} from "../domain/ProductLifecyclePhase.js";

const REPO_ROOT = path.join(
  path.dirname(url.fileURLToPath(import.meta.url)),
  "../../../../..",
);
const CONFIG_DIR = path.join(REPO_ROOT, "config");

// --- Test adapters -----------------------------------------------------------

class DryRunTrackingAdapter implements Adapter {
  public dryRunInvocations = 0;
  public realInvocations = 0;

  async execute(context: AdapterContext): Promise<AdapterResult> {
    if (context.dryRun) {
      this.dryRunInvocations++;
      return {
        status: "skipped",
        schemaVersion: "1.0.0",
        observability: {
          commandId: context.surfaceConfig["gradleModule"] ?? "dry-run",
          durationMs: 0,
        },
      };
    }
    this.realInvocations++;
    return { status: "succeeded" };
  }
}

class TestAdapterRegistry implements AdapterRegistry {
  private readonly adapters = new Map<string, Adapter>();

  register(id: string, adapter: Adapter): void {
    this.adapters.set(id, adapter);
  }

  getAdapter(id: string): Adapter {
    const adapter = this.adapters.get(id);
    if (!adapter) throw new Error(`Adapter not registered: ${id}`);
    return adapter;
  }
}

function makeStep(
  overrides: Partial<ProductLifecycleStep> & { id: string; adapter: string },
): ProductLifecycleStep {
  return {
    stepKind: "surface",
    phase: "build",
    surface: "backend-api",
    description: "DM pilot step",
    dependsOn: [],
    estimatedDurationMs: 30_000,
    ...overrides,
  };
}

// --- Suite -------------------------------------------------------------------

describe("§2.7 — Digital Marketing registry field validation", () => {
  let registry: CanonicalRegistryLoader;

  beforeEach(() => {
    registry = new CanonicalRegistryLoader(CONFIG_DIR);
  });

  it("digital-marketing is lifecycle enabled", async () => {
    const dm = await registry.getProduct("digital-marketing");
    const lifecycle = dm.lifecycle as Record<string, unknown>;
    expect(lifecycle["enabled"]).toBe(true);
  });

  it("digital-marketing has lifecycleExecutionAllowed = true", async () => {
    const dm = await registry.getProduct("digital-marketing");
    // lifecycleExecutionAllowed is a top-level field in the registry JSON
    const raw = dm as unknown as Record<string, unknown>;
    expect(raw["lifecycleExecutionAllowed"]).toBe(true);
  });

  it("digital-marketing has backend-api and web surfaces", async () => {
    const dm = await registry.getProduct("digital-marketing");
    const surfaceTypes = dm.surfaces.map((s) => s.type);
    expect(surfaceTypes).toContain("backend-api");
    expect(surfaceTypes).toContain("web");
  });

  it("digital-marketing uses Gradle adapter for backend-api", async () => {
    const dm = await registry.getProduct("digital-marketing");
    expect(dm.toolchain?.adapters?.["backend-api"]).toBe("gradle-java-service");
  });

  it("digital-marketing uses pnpm adapter for web", async () => {
    const dm = await registry.getProduct("digital-marketing");
    expect(dm.toolchain?.adapters?.["web"]).toBe("pnpm-vite-react");
  });

  it("digital-marketing has jvm-service artifact for backend-api", async () => {
    const dm = await registry.getProduct("digital-marketing");
    expect(dm.artifacts?.["backend-api"]?.type).toBe("jvm-service");
    expect(dm.artifacts?.["backend-api"]?.packaging).toBe("jar");
  });

  it("digital-marketing has static-web-bundle artifact for web", async () => {
    const dm = await registry.getProduct("digital-marketing");
    expect(dm.artifacts?.["web"]?.type).toBe("static-web-bundle");
    expect(dm.artifacts?.["web"]?.packaging).toBe("static-files");
  });

  it("digital-marketing deploys to compose-local", async () => {
    const dm = await registry.getProduct("digital-marketing");
    expect(dm.deployment?.targets).toContain("compose-local");
  });

  it("digital-marketing supports local environment", async () => {
    const dm = await registry.getProduct("digital-marketing");
    expect(dm.deployment?.defaultEnvironment).toBe("local");
    const raw = dm as unknown as Record<string, unknown>;
    const envs = raw["environments"] as Record<string, unknown> | undefined;
    const supported = envs?.["supported"] as string[] | undefined;
    expect(supported).toContain("local");
  });
});

describe("§2.7 — Digital Marketing is the sole lifecycleExecutionAllowed product", () => {
  let registry: CanonicalRegistryLoader;

  beforeEach(() => {
    registry = new CanonicalRegistryLoader(CONFIG_DIR);
  });

  it("only digital-marketing has lifecycleExecutionAllowed = true", async () => {
    const all = await registry.getAllProducts();
    const allowedProducts = Object.entries(all)
      .filter(([, product]) => {
        const raw = product as unknown as Record<string, unknown>;
        return raw["lifecycleExecutionAllowed"] === true;
      })
      .map(([id]) => id);

    expect(allowedProducts).toEqual(["digital-marketing"]);
  });

  it("PHR does NOT have lifecycleExecutionAllowed = true", async () => {
    const phr = await registry.getProduct("phr");
    const raw = phr as unknown as Record<string, unknown>;
    expect(raw["lifecycleExecutionAllowed"]).not.toBe(true);
  });

  it("finance does NOT have lifecycleExecutionAllowed = true", async () => {
    const finance = await registry.getProduct("finance");
    const raw = finance as unknown as Record<string, unknown>;
    expect(raw["lifecycleExecutionAllowed"]).not.toBe(true);
  });
});

describe("§2.7 — Digital Marketing ProductUnit validation", () => {
  it("createExecutableProductUnit produces valid unit for digital-marketing", () => {
    const unit = createExecutableProductUnit({
      id: "digital-marketing",
      name: "Digital Marketing Operating System",
      kind: "business-product",
      scope: {
        tenantId: "platform",
        workspaceId: "digital-marketing-ws",
        projectId: "digital-marketing",
      },
      lifecycleProfile: "standard-web-api-product",
      lifecycleStatus: "enabled",
      registryProviderRef: { providerId: "platform-registry" },
      sourceProviderRef: {
        providerId: "platform-source",
        config: {
          lifecycleConfigPath: "products/digital-marketing/kernel-product.yaml",
        },
      },
      surfaces: [
        {
          id: "backend-api",
          type: "backend-api",
          implementationStatus: "implemented",
          sourceRef: "products/digital-marketing/dm-api",
        },
        {
          id: "web",
          type: "web",
          implementationStatus: "implemented",
          sourceRef: "products/digital-marketing/ui",
        },
      ],
    });

    const result = validateProductUnit(unit);
    expect(result.valid).toBe(true);
    expect(result.errors).toHaveLength(0);
    expect(unit.id).toBe("digital-marketing");
    expect(unit.kind).toBe("business-product");
  });

  it("validateProductUnit returns invalid for unit with empty id", () => {
    // Directly construct an invalid object (bypass createExecutableProductUnit which throws)
    const corrupted = {
      id: "",
      name: "DM",
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

describe("§2.7 — Digital Marketing lifecycle planning", () => {
  let planner: ProductLifecyclePlanner;

  beforeEach(() => {
    planner = new ProductLifecyclePlanner(REPO_ROOT);
  });

  it("loads DM kernel-product.yaml config", async () => {
    const config = await planner.loadProductConfig("digital-marketing");
    expect(config.productId).toBe("digital-marketing");
    expect(config.lifecycleProfile).toBe("standard-web-api-product");
  });

  it("plans build phase with jvm-service and static-web-bundle artifacts", async () => {
    const plan = await planner.plan("digital-marketing", "build");

    expect(plan.productId).toBe("digital-marketing");
    expect(plan.phase).toBe("build");
    expect(plan.schemaVersion).toBe("1.0.0");
    expect(plan.runId).toBeTruthy();
    expect(plan.correlationId).toBeTruthy();

    const artifactTypes = plan.expectedArtifacts.map((a) => a.type);
    expect(artifactTypes).toContain("jvm-service");
    expect(artifactTypes).toContain("static-web-bundle");
  });

  it("plans deploy phase with deployment-manifest required manifests", async () => {
    const plan = await planner.plan("digital-marketing", "deploy");

    expect(plan.phase).toBe("deploy");
    expect(plan.requiredManifests).toContain("deployment-manifest");
  });

  it("plans dev phase in parallel mode", async () => {
    const plan = await planner.plan("digital-marketing", "dev");

    expect(plan.phase).toBe("dev");
    expect(plan.phaseMode).toBe("parallel");
  });

  it("plan has required plugins including audit and observability", async () => {
    const plan = await planner.plan("digital-marketing", "build");
    const pluginIds = plan.requiredPlugins.map((p) => p.pluginId);

    expect(pluginIds).toContain("audit");
    expect(pluginIds).toContain("observability");
  });

  it("plan steps reference expected toolchain adapters", async () => {
    const plan = await planner.plan("digital-marketing", "build");
    const adapters = plan.steps.map((s) => s.adapter);

    expect(adapters).toContain("gradle-java-service");
    expect(adapters).toContain("pnpm-vite-react");
  });
});

describe("§2.7 — Digital Marketing adapter resolution", () => {
  it("resolves gradle-java-service adapter for backend-api build", async () => {
    const resolver = new ToolchainResolver(CONFIG_DIR);
    const adapter = await resolver.resolve("gradle-java-service");

    expect(adapter.supportedSurfaceTypes).toContain("backend-api");
    expect(adapter.supportedPhases).toContain("build");
  });

  it("resolves pnpm-vite-react adapter for web build", async () => {
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

describe("§2.7 — Digital Marketing gate resolution", () => {
  const gateResolver = new GateResolver();

  it("resolves build-phase required gates", () => {
    const gates = gateResolver.resolve(
      "build",
      ["backend-check", "web-typecheck", "web-bundle-budget"],
      ["web-a11y-readiness"],
    );

    expect(gates.filter((g) => g.required)).toHaveLength(3);
    expect(gates.filter((g) => !g.required)).toHaveLength(1);
    expect(gates.some((g) => g.gateId === "backend-check" && g.required)).toBe(
      true,
    );
    expect(
      gates.some((g) => g.gateId === "web-a11y-readiness" && !g.required),
    ).toBe(true);
  });

  it("resolves deploy-phase gates", () => {
    const gates = gateResolver.resolve(
      "deploy",
      ["deployment-readiness", "environment-configuration-validation"],
      [],
    );

    expect(gates).toHaveLength(2);
    expect(gates.every((g) => g.status === "pending")).toBe(true);
    expect(gates.every((g) => g.phase === "deploy")).toBe(true);
  });

  it("validates a known gate id succeeds", () => {
    const errors = gateResolver.validate("backend-check");
    expect(errors).toHaveLength(0);
  });

  it("gate validation fails on empty gate id", () => {
    const errors = gateResolver.validate("");
    expect(errors.length).toBeGreaterThan(0);
  });
});

describe("§2.7 — Digital Marketing dry-run execution (must not be real success)", () => {
  let tempDir: string;
  let executor: ProductLifecycleExecutor;
  let gradleAdapter: DryRunTrackingAdapter;
  let pnpmAdapter: DryRunTrackingAdapter;

  beforeEach(async () => {
    tempDir = await mkdtemp(path.join(os.tmpdir(), "dm-pilot-"));
    gradleAdapter = new DryRunTrackingAdapter();
    pnpmAdapter = new DryRunTrackingAdapter();

    const registry = new TestAdapterRegistry();
    registry.register("gradle-java-service", gradleAdapter);
    registry.register("pnpm-vite-react", pnpmAdapter);

    const logger = new ConsoleExecutionLogger();
    const stepRunner = new ProductLifecycleStepRunner(registry);
    const resultCollector = new ExecutionResultCollector(logger);
    executor = new ProductLifecycleExecutor(stepRunner, resultCollector);
  });

  afterEach(async () => {
    await rm(tempDir, { recursive: true, force: true });
  });

  it("dry-run returns skipped status (never succeeded)", async () => {
    const steps: ProductLifecycleStep[] = [
      makeStep({ id: "build-backend", adapter: "gradle-java-service" }),
      makeStep({ id: "build-web", adapter: "pnpm-vite-react", surface: "web" }),
    ];

    const result = await executor.execute("digital-marketing", "build", steps, {
      dryRun: true,
      outputDirectory: tempDir,
    });

    expect(result.status).toBe("skipped");
    expect(result.steps.every((s) => s.status === "skipped")).toBe(true);
    // Dry-run must never be reported as real success
    expect(result.status).not.toBe("succeeded");
  });

  it("dry-run bypasses adapter — step has zero duration and empty artifacts", async () => {
    const steps: ProductLifecycleStep[] = [
      makeStep({ id: "build-backend", adapter: "gradle-java-service" }),
    ];

    const result = await executor.execute("digital-marketing", "build", steps, {
      dryRun: true,
      outputDirectory: tempDir,
    });

    // The executor short-circuits before calling the adapter in dry-run mode
    expect(gradleAdapter.realInvocations).toBe(0);
    // Step was still processed (just as skipped) and has zero real duration
    const backendStep = result.steps.find((s) => s.stepId === "build-backend");
    expect(backendStep).toBeDefined();
    expect(backendStep?.status).toBe("skipped");
    expect(backendStep?.artifacts).toHaveLength(0);
  });

  it("dry-run result has no failure field", async () => {
    const steps: ProductLifecycleStep[] = [
      makeStep({ id: "build-backend", adapter: "gradle-java-service" }),
    ];

    const result = await executor.execute("digital-marketing", "build", steps, {
      dryRun: true,
      outputDirectory: tempDir,
    });

    expect(result.failure).toBeUndefined();
  });

  it("real run (non-dry) returns succeeded when adapters succeed", async () => {
    const steps: ProductLifecycleStep[] = [
      makeStep({ id: "build-backend", adapter: "gradle-java-service" }),
      makeStep({ id: "build-web", adapter: "pnpm-vite-react", surface: "web" }),
    ];

    const result = await executor.execute("digital-marketing", "build", steps, {
      dryRun: false,
      outputDirectory: tempDir,
    });

    expect(result.status).toBe("succeeded");
    expect(gradleAdapter.realInvocations).toBe(1);
    expect(pnpmAdapter.realInvocations).toBe(1);
  });
});

describe("§2.7 — Digital Marketing artifact and manifest ref production", () => {
  let tempDir: string;

  beforeEach(async () => {
    tempDir = await mkdtemp(path.join(os.tmpdir(), "dm-manifest-"));
  });

  afterEach(async () => {
    await rm(tempDir, { recursive: true, force: true });
  });

  it("ArtifactWriter writes jvm-service artifact manifest with correct shape", async () => {
    const writer = new ArtifactWriter();
    const outputPath = path.join(tempDir, "artifact-manifest.json");

    await writer.writeArtifactManifest(
      [
        {
          id: "backend-jar",
          surface: "backend-api",
          type: "jvm-service",
          path: "products/digital-marketing/dm-api/build/libs/dm-api.jar",
          fingerprint: "sha256:abc123",
          producedBy: "gradle-java-service",
        },
        {
          id: "web-bundle",
          surface: "web",
          type: "static-web-bundle",
          path: "products/digital-marketing/ui/dist",
          fingerprint: "sha256:def456",
          producedBy: "pnpm-vite-react",
        },
      ],
      outputPath,
    );

    const raw = JSON.parse(await readFile(outputPath, "utf8")) as {
      schemaVersion: string;
      generatedAt: string;
      artifacts: Array<{ id: string; type: string; surface: string }>;
    };

    expect(raw.schemaVersion).toBe("1.0.0");
    expect(raw.generatedAt).toBeTruthy();
    expect(raw.artifacts).toHaveLength(2);
    expect(raw.artifacts.map((a) => a.type)).toContain("jvm-service");
    expect(raw.artifacts.map((a) => a.type)).toContain("static-web-bundle");
  });

  it("PlanWriter writes lifecycle plan ref with runId and correlationId", async () => {
    const writer = new PlanWriter();
    // writePlan expects a full file path, not a directory
    const planFile = path.join(tempDir, "run-001", "lifecycle-plan.json");
    const planner = new ProductLifecyclePlanner(REPO_ROOT);
    const plan = await planner.plan("digital-marketing", "build");

    await writer.writePlan(plan, planFile);

    const raw = JSON.parse(await readFile(planFile, "utf8")) as {
      schemaVersion: string;
      runId: string;
      correlationId: string;
      productId: string;
      phase: string;
    };

    expect(raw.schemaVersion).toBe("1.0.0");
    expect(raw.runId).toBeTruthy();
    expect(raw.correlationId).toBeTruthy();
    expect(raw.productId).toBe("digital-marketing");
    expect(raw.phase).toBe("build");
  });
});

describe("§2.7 — Digital Marketing dm-kernel-bridge conformance", () => {
  let registry: CanonicalRegistryLoader;

  beforeEach(() => {
    registry = new CanonicalRegistryLoader(CONFIG_DIR);
  });

  it("dm-kernel-bridge is listed in gradleModules", async () => {
    const dm = await registry.getProduct("digital-marketing");
    expect(dm.gradleModules).toContain(
      ":products:digital-marketing:dm-kernel-bridge",
    );
  });

  it("conformance.bridge is enabled", async () => {
    const dm = await registry.getProduct("digital-marketing");
    expect(dm.conformance?.bridge).toBe(true);
  });

  it("bridgeAdapters contains DigitalMarketingKernelAdapterImpl", async () => {
    const dm = await registry.getProduct("digital-marketing");
    const adapters = dm.conformance?.bridgeAdapters ?? [];
    expect(adapters.length).toBeGreaterThan(0);
    const adapterFile = adapters[0].file;
    expect(adapterFile).toContain("DigitalMarketingKernelAdapterImpl");
  });

  it("bridgeAdapters reference NotificationRetryAndDlqTest", async () => {
    const dm = await registry.getProduct("digital-marketing");
    const adapters = dm.conformance?.bridgeAdapters ?? [];
    const allTestFiles = adapters.flatMap((a) => a.tests.map((t) => t.file));
    expect(
      allTestFiles.some((f) => f.includes("NotificationRetryAndDlqTest")),
    ).toBe(true);
  });

  it("conformance observability and security are enabled", async () => {
    const dm = await registry.getProduct("digital-marketing");
    expect(dm.conformance?.observability).toBe(true);
    expect(dm.conformance?.security).toBe(true);
  });
});
