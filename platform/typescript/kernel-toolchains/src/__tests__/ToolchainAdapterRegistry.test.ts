import { describe, it, expect } from "vitest";
import * as path from "node:path";
import * as url from "node:url";
import {
  ToolchainAdapterRegistry,
  ToolchainAdapterRegistryBridge,
  createDefaultToolchainAdapterRegistry,
} from "../ToolchainAdapterRegistry.js";
import { GradleJavaServiceAdapter } from "../adapters/GradleJavaServiceAdapter.js";
import { PnpmViteReactAdapter } from "../adapters/PnpmViteReactAdapter.js";
import { FakeCommandRunner } from "../execution/FakeCommandRunner.js";
import type {
  ToolchainAdapter,
  ToolchainAdapterContext,
  ToolchainExecutionResult,
  ToolchainOutputValidationResult,
  ToolchainPlanStep,
} from "../ToolchainAdapter.js";

// Resolve the repo root relative to this test file
const REPO_ROOT = path.join(
  path.dirname(url.fileURLToPath(import.meta.url)),
  "../../../../..",
);
const REPO_CONFIG_DIR = path.join(REPO_ROOT, "config");

describe("ToolchainAdapterRegistry", () => {
  it("should register and retrieve adapters", () => {
    const registry = new ToolchainAdapterRegistry();
    const gradleAdapter = new GradleJavaServiceAdapter();

    registry.register(gradleAdapter);

    expect(registry.has("gradle-java-service")).toBe(true);
    expect(registry.get("gradle-java-service")).toBe(gradleAdapter);
  });

  it("should load adapter definitions from registry", async () => {
    const registry = new ToolchainAdapterRegistry(REPO_CONFIG_DIR);
    const definitions = await registry.loadRegistry();

    expect(definitions).toHaveProperty("gradle-java-service");
    expect(definitions).toHaveProperty("pnpm-vite-react");
    expect(definitions["gradle-java-service"]).toMatchObject({
      readiness: "execution-ready",
      lifecycleEnabled: true,
      supportsBootstrapMode: true,
      supportsPlatformMode: false,
    });
  });

  it("keeps partial and mobile adapters blocked for lifecycle execution", async () => {
    const registry = new ToolchainAdapterRegistry(REPO_CONFIG_DIR);
    const definitions = await registry.loadRegistry();

    expect(definitions["vitest"]).toMatchObject({
      readiness: "declared-only",
      lifecycleEnabled: false,
    });
    expect(definitions["xcode-ios"]).toMatchObject({
      readiness: "declared-only",
      lifecycleEnabled: false,
      featureFlagRequired: true,
    });
    expect(definitions["gradle-android"]).toMatchObject({
      readiness: "declared-only",
      lifecycleEnabled: false,
      featureFlagRequired: true,
    });
  });

  it("should filter adapters by phase", () => {
    const registry = new ToolchainAdapterRegistry();
    registry.register(new GradleJavaServiceAdapter());
    registry.register(new PnpmViteReactAdapter());

    const buildAdapters = registry.getByPhase("build");

    expect(buildAdapters).toHaveLength(2);
    expect(
      buildAdapters.every((a) => a.supportedPhases.includes("build")),
    ).toBe(true);
  });

  it("should filter adapters by surface type", () => {
    const registry = new ToolchainAdapterRegistry();
    registry.register(new GradleJavaServiceAdapter());
    registry.register(new PnpmViteReactAdapter());

    const webAdapters = registry.getBySurfaceType("web");

    expect(webAdapters).toHaveLength(1);
    expect(webAdapters[0].id).toBe("pnpm-vite-react");
  });
});

describe("createDefaultToolchainAdapterRegistry", () => {
  it("registers all four canonical adapters", () => {
    const { registry } = createDefaultToolchainAdapterRegistry({
      repoRoot: "/repo",
    });

    expect(registry.has("gradle-java-service")).toBe(true);
    expect(registry.has("pnpm-vite-react")).toBe(true);
    expect(registry.has("docker-buildx")).toBe(true);
    expect(registry.has("compose-local")).toBe(true);
    expect(registry.getAll()).toHaveLength(4);
  });

  it("docker-buildx supports only the package phase", () => {
    const { registry } = createDefaultToolchainAdapterRegistry({
      repoRoot: "/repo",
    });
    const packageAdapters = registry.getByPhase("package");
    const ids = packageAdapters.map((a) => a.id);
    expect(ids).toContain("docker-buildx");
  });

  it("compose-local supports deploy and rollback phases", () => {
    const { registry } = createDefaultToolchainAdapterRegistry({
      repoRoot: "/repo",
    });
    const deployAdapters = registry.getByPhase("deploy");
    const rollbackAdapters = registry.getByPhase("rollback");
    expect(deployAdapters.map((a) => a.id)).toContain("compose-local");
    expect(rollbackAdapters.map((a) => a.id)).toContain("compose-local");
  });
});

describe("ToolchainAdapterRegistryBridge", () => {
  it("throws a descriptive error when adapter is not registered", () => {
    const registry = new ToolchainAdapterRegistry();
    const bridge = new ToolchainAdapterRegistryBridge(registry);

    expect(() => bridge.getAdapter("missing")).toThrow(
      /No toolchain adapter registered for id "missing"/,
    );
  });

  it("includes registered adapter ids when bridge lookup fails", () => {
    const registry = new ToolchainAdapterRegistry();
    registry.register(new GradleJavaServiceAdapter());
    const bridge = new ToolchainAdapterRegistryBridge(registry);

    expect(() => bridge.getAdapter("missing")).toThrow(
      /Registered adapters: \[gradle-java-service\]/,
    );
  });

  it("returns a bridge adapter that calls the underlying adapter in dry-run", async () => {
    const runner = new FakeCommandRunner();
    const { bridge } = createDefaultToolchainAdapterRegistry({
      repoRoot: "/repo",
      commandRunner: runner,
    });

    const bridgedAdapter = bridge.getAdapter("compose-local");
    const result = await bridgedAdapter.execute({
      productId: "test",
      phase: "deploy",
      surface: { type: "backend-api", adapter: "compose-local", path: "/p" },
      dryRun: true,
      surfaceConfig: { composeFile: "/nonexistent/compose.yaml" },
      phaseConfig: {},
      logger: {
        info: () => {},
        warn: () => {},
        error: () => {},
        debug: () => {},
      },
    });

    // dry-run skips execution
    expect(result.status).toBe("skipped");
  });

  it("preserves full toolchain execution evidence through the bridge", async () => {
    class EvidenceAdapter implements ToolchainAdapter {
      readonly id = "evidence-adapter";
      readonly supportedPhases = ["package" as const];
      readonly supportedSurfaceTypes = ["backend-api" as const];

      async plan(
        _context: ToolchainAdapterContext,
      ): Promise<ToolchainPlanStep[]> {
        return [];
      }

      async execute(
        _context: ToolchainAdapterContext,
      ): Promise<ToolchainExecutionResult> {
        return {
          status: "failed",
          steps: [
            {
              stepId: "docker-build",
              status: "failed",
              exitCode: 1,
              stdout: "stdout summary",
              stderr: "stderr summary",
              durationMs: 12,
            },
          ],
          artifacts: ["ghatana/app:local"],
          testResults: { tests: 1, failures: 1, skipped: 0, durationMs: 12 },
          coverageResults: {
            lineCoverage: 50,
            branchCoverage: 40,
            instructionCoverage: 45,
          },
          durationMs: 12,
          failure: {
            stepId: "docker-build",
            message: "build failed",
            cause: "exit 1",
          },
          warnings: ["cache disabled"],
          stdout: "adapter stdout",
          stderr: "adapter stderr",
          manifestRefs: { artifactManifest: "artifact-manifest.json" },
        };
      }

      async validateOutputs(
        _context: ToolchainAdapterContext,
      ): Promise<ToolchainOutputValidationResult> {
        return {
          status: "valid",
          errors: [],
          missingArtifacts: [],
          unexpectedArtifacts: [],
        };
      }
    }

    const registry = new ToolchainAdapterRegistry();
    registry.register(new EvidenceAdapter());
    const bridge = new ToolchainAdapterRegistryBridge(registry);

    const result = await bridge.getAdapter("evidence-adapter").execute({
      productId: "digital-marketing",
      phase: "package",
      surface: {
        type: "backend-api",
        adapter: "evidence-adapter",
        path: "products/api",
      },
      dryRun: false,
      surfaceConfig: {},
      phaseConfig: {},
      logger: {
        info: () => {},
        warn: () => {},
        error: () => {},
        debug: () => {},
      },
    });

    expect(result).toMatchObject({
      status: "failed",
      steps: [{ stepId: "docker-build", stderr: "stderr summary" }],
      artifacts: [
        {
          id: "package-evidence-adapter-artifact-0",
          type: "container-image",
          path: "ghatana/app:local",
          image: "ghatana/app:local",
          producedBy: "evidence-adapter",
        },
      ],
      testResults: { failures: 1 },
      coverageResults: { lineCoverage: 50 },
      durationMs: 12,
      failure: { cause: "exit 1" },
      warnings: ["cache disabled"],
      stdout: "adapter stdout",
      stderr: "adapter stderr",
      manifestRefs: { artifactManifest: "artifact-manifest.json" },
    });
  });

  it("infers bridged artifact types by phase and surface type", async () => {
    const cases = [
      {
        phase: "deploy",
        surfaceType: "backend-api",
        expectedType: "deployment-manifest",
      },
      {
        phase: "verify",
        surfaceType: "backend-api",
        expectedType: "verify-health-report",
      },
      {
        phase: "test",
        surfaceType: "backend-api",
        expectedType: "test-report",
      },
      { phase: "build", surfaceType: "web", expectedType: "static-web-bundle" },
      {
        phase: "build",
        surfaceType: "backend-api",
        expectedType: "jvm-service",
      },
      {
        phase: "package",
        surfaceType: "web",
        expectedType: "static-web-bundle",
        artifactRef: "web/dist",
      },
    ] as const;

    for (const testCase of cases) {
      class ArtifactAdapter implements ToolchainAdapter {
        readonly id = `artifact-${testCase.phase}-${testCase.surfaceType}`;
        readonly supportedPhases = [testCase.phase];
        readonly supportedSurfaceTypes = [testCase.surfaceType];

        async plan(
          _context: ToolchainAdapterContext,
        ): Promise<ToolchainPlanStep[]> {
          return [];
        }

        async execute(
          _context: ToolchainAdapterContext,
        ): Promise<ToolchainExecutionResult> {
          return {
            status: "succeeded",
            steps: [],
            artifacts: [testCase.artifactRef ?? "artifact-ref"],
            durationMs: 1,
          };
        }

        async validateOutputs(
          _context: ToolchainAdapterContext,
        ): Promise<ToolchainOutputValidationResult> {
          return {
            status: "valid",
            errors: [],
            missingArtifacts: [],
            unexpectedArtifacts: [],
          };
        }
      }

      const registry = new ToolchainAdapterRegistry();
      const adapter = new ArtifactAdapter();
      registry.register(adapter);
      const bridge = new ToolchainAdapterRegistryBridge(registry);

      const result = await bridge.getAdapter(adapter.id).execute({
        productId: "digital-marketing",
        phase: testCase.phase,
        surface: {
          type: testCase.surfaceType,
          adapter: adapter.id,
          path: "surface/path",
        },
        dryRun: false,
        surfaceConfig: {},
        phaseConfig: {},
        logger: {
          info: () => {},
          warn: () => {},
          error: () => {},
          debug: () => {},
        },
      });

      expect(result.artifacts?.[0].type).toBe(testCase.expectedType);
    }
  });
});

describe("ToolchainAdapterRegistry", () => {
  it("should register and retrieve adapters", () => {
    const registry = new ToolchainAdapterRegistry();
    const gradleAdapter = new GradleJavaServiceAdapter();

    registry.register(gradleAdapter);

    expect(registry.has("gradle-java-service")).toBe(true);
    expect(registry.get("gradle-java-service")).toBe(gradleAdapter);
  });

  it("should load adapter definitions from registry", async () => {
    const registry = new ToolchainAdapterRegistry(REPO_CONFIG_DIR);
    const definitions = await registry.loadRegistry();

    expect(definitions).toHaveProperty("gradle-java-service");
    expect(definitions).toHaveProperty("pnpm-vite-react");
  });

  it("should filter adapters by phase", () => {
    const registry = new ToolchainAdapterRegistry();
    registry.register(new GradleJavaServiceAdapter());
    registry.register(new PnpmViteReactAdapter());

    const buildAdapters = registry.getByPhase("build");

    expect(buildAdapters).toHaveLength(2);
    expect(
      buildAdapters.every((a) => a.supportedPhases.includes("build")),
    ).toBe(true);
  });

  it("should filter adapters by surface type", () => {
    const registry = new ToolchainAdapterRegistry();
    registry.register(new GradleJavaServiceAdapter());
    registry.register(new PnpmViteReactAdapter());

    const webAdapters = registry.getBySurfaceType("web");

    expect(webAdapters).toHaveLength(1);
    expect(webAdapters[0].id).toBe("pnpm-vite-react");
  });
});
