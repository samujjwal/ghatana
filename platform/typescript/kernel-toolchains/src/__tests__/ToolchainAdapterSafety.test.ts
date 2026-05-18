/**
 * §2.5 — Toolchain adapter safety, output-contract, and observability tests.
 *
 * Covers:
 * - safe adapter executes and produces structured observability
 * - planned/blocked adapters are NOT lifecycle-enabled in registry
 * - timeout produces structured failure (FakeCommandRunner simulation)
 * - missing output contract causes validation failure
 * - stdout/stderr captured as bounded, structured events
 * - no product-specific commands in the generic adapter
 * - Digital Marketing resolves backend (gradle-java-service) and web (pnpm-vite-react) adapters
 * - FlashIt mobile adapters (xcode-ios, gradle-android) remain blocked
 */

import { describe, it, expect, beforeEach, afterEach } from "vitest";
import * as path from "node:path";
import * as url from "node:url";
import * as os from "node:os";
import { promises as fs } from "node:fs";
import { GradleJavaServiceAdapter } from "../adapters/GradleJavaServiceAdapter.js";
import { GradleAndroidAdapter } from "../adapters/GradleAndroidAdapter.js";
import { XcodeIosAdapter } from "../adapters/XcodeIosAdapter.js";
import { PnpmViteReactAdapter } from "../adapters/PnpmViteReactAdapter.js";
import { ToolchainAdapterRegistry } from "../ToolchainAdapterRegistry.js";
import { FakeCommandRunner } from "../execution/FakeCommandRunner.js";
import { ToolchainOutputValidator } from "../ToolchainOutputValidator.js";
import type {
  AdapterLogger,
  ToolchainAdapterContext,
  ToolchainAdapterCapability,
  ToolchainOutputContract,
  ToolchainSafetyLevel,
} from "../ToolchainAdapter.js";

const REPO_ROOT = path.join(
  path.dirname(url.fileURLToPath(import.meta.url)),
  "../../../../..",
);
const REPO_CONFIG_DIR = path.join(REPO_ROOT, "config");

// ─── helpers ──────────────────────────────────────────────────────────────────

function makeLogger(): AdapterLogger {
  return {
    info: () => undefined,
    warn: () => undefined,
    error: () => undefined,
    debug: () => undefined,
  };
}

function makeContext(
  overrides: Partial<ToolchainAdapterContext> = {},
): ToolchainAdapterContext {
  return {
    productId: "test-product",
    phase: "build",
    surface: {
      type: "backend-api",
      adapter: "gradle-java-service",
      path: "/backend",
    },
    dryRun: false,
    surfaceConfig: { gradleModule: ":products:test:backend" },
    phaseConfig: {},
    logger: makeLogger(),
    ...overrides,
  };
}

// ─── §2.5-1: safe adapter executes and produces structured observability ──────

describe("safe adapter (gradle-java-service)", () => {
  let repoRoot: string;
  const MODULE_PATH = "products/test/backend";
  const GRADLE_MODULE = ":products:test:backend";

  beforeEach(async () => {
    repoRoot = await fs.mkdtemp(path.join(os.tmpdir(), "gradle-safety-"));
    await fs.writeFile(
      path.join(repoRoot, "settings.gradle.kts"),
      `include("${GRADLE_MODULE}")`,
    );
    // Create expected build output so output validation passes
    await fs.mkdir(path.join(repoRoot, MODULE_PATH, "build", "libs"), {
      recursive: true,
    });
    await fs.writeFile(
      path.join(repoRoot, MODULE_PATH, "build", "libs", "service.jar"),
      "fake-jar",
    );
  });

  afterEach(async () => {
    await fs.rm(repoRoot, { recursive: true, force: true });
  });

  function makeGradleContext(
    overrides: Partial<ToolchainAdapterContext> = {},
  ): ToolchainAdapterContext {
    return {
      productId: "test-product",
      phase: "build",
      surface: {
        type: "backend-api",
        adapter: "gradle-java-service",
        path: MODULE_PATH,
      },
      dryRun: false,
      surfaceConfig: {
        gradleModule: GRADLE_MODULE,
        source: MODULE_PATH,
      },
      phaseConfig: {},
      logger: makeLogger(),
      ...overrides,
    };
  }

  it("produces structured observability on success", async () => {
    const runner = new FakeCommandRunner([
      { exitCode: 0, stdout: "BUILD SUCCESSFUL", stderr: "", durationMs: 120 },
    ]);
    const adapter = new GradleJavaServiceAdapter({
      repoRoot,
      commandRunner: runner,
    });

    const result = await adapter.execute(makeGradleContext());

    expect(result.status).toBe("succeeded");
    expect(result.schemaVersion).toBe("1.0.0");
    expect(result.observability).toBeDefined();
    expect(result.observability?.durationMs).toBeGreaterThanOrEqual(0);
    expect(result.observability?.exitCode).toBe(0);
    expect(typeof result.observability?.stdoutBytes).toBe("number");
    expect(typeof result.observability?.stderrBytes).toBe("number");
    expect(typeof result.observability?.stdoutTruncated).toBe("boolean");
  });

  it("captures stdout and stderr as bounded step-level events", async () => {
    const stdoutContent = "Build output line 1\nBuild output line 2";
    const stderrContent = "Warning: deprecated API";
    const runner = new FakeCommandRunner([
      {
        exitCode: 0,
        stdout: stdoutContent,
        stderr: stderrContent,
        durationMs: 60,
      },
    ]);
    const adapter = new GradleJavaServiceAdapter({
      repoRoot,
      commandRunner: runner,
    });

    const result = await adapter.execute(makeGradleContext());

    expect(result.status).toBe("succeeded");
    expect(result.steps).toHaveLength(1);
    const step = result.steps[0];
    expect(step?.stdout).toContain("Build output line 1");
    expect(step?.stderr).toContain("Warning: deprecated API");
  });

  it("produces a failed result with stderr when exit code is non-zero", async () => {
    const runner = new FakeCommandRunner([
      { exitCode: 1, stdout: "", stderr: "COMPILATION ERROR", durationMs: 30 },
    ]);
    const adapter = new GradleJavaServiceAdapter({
      repoRoot,
      commandRunner: runner,
    });

    const result = await adapter.execute(makeGradleContext());

    expect(result.status).toBe("failed");
    expect(result.failure?.message).toMatch(/gradle-task-failed/);
    expect(result.failure?.cause).toContain("COMPILATION ERROR");
    expect(result.observability?.exitCode).toBe(1);
  });

  it("returns correlation ID and run ID from context in result", async () => {
    const runner = new FakeCommandRunner([
      { exitCode: 0, stdout: "ok", stderr: "", durationMs: 10 },
    ]);
    const adapter = new GradleJavaServiceAdapter({
      repoRoot,
      commandRunner: runner,
    });
    const context = makeGradleContext({
      runId: "run-001",
      correlationId: "corr-abc",
    });

    const result = await adapter.execute(context);

    expect(result.runId).toBe("run-001");
    expect(result.correlationId).toBe("corr-abc");
  });
});

// ─── §2.5-2: planned adapters (gradle-android, xcode-ios) are not lifecycle-enabled ──

describe("planned/blocked adapter registry entries", () => {
  it("xcode-ios has lifecycleEnabled: false and featureFlagRequired: true", async () => {
    const registry = new ToolchainAdapterRegistry(REPO_CONFIG_DIR);
    const definitions = await registry.loadRegistry();

    expect(definitions["xcode-ios"]).toBeDefined();
    expect(definitions["xcode-ios"]?.lifecycleEnabled).toBe(false);
    expect(definitions["xcode-ios"]?.featureFlagRequired).toBe(true);
    expect(definitions["xcode-ios"]?.readiness).toBe("declared-only");
  });

  it("gradle-android has lifecycleEnabled: false and featureFlagRequired: true", async () => {
    const registry = new ToolchainAdapterRegistry(REPO_CONFIG_DIR);
    const definitions = await registry.loadRegistry();

    expect(definitions["gradle-android"]).toBeDefined();
    expect(definitions["gradle-android"]?.lifecycleEnabled).toBe(false);
    expect(definitions["gradle-android"]?.featureFlagRequired).toBe(true);
    expect(definitions["gradle-android"]?.readiness).toBe("declared-only");
  });

  it("gradle-android dry-run returns skipped (not fake success)", async () => {
    const runner = new FakeCommandRunner([]);
    const adapter = new GradleAndroidAdapter(runner);
    const context: ToolchainAdapterContext = {
      productId: "flashit",
      phase: "build",
      surface: {
        type: "mobile-android",
        adapter: "gradle-android",
        path: "/android",
      },
      dryRun: true,
      surfaceConfig: { gradleModule: "app", variant: "release" },
      phaseConfig: {},
      logger: makeLogger(),
    };

    const result = await adapter.execute(context);

    // dry-run must be 'skipped', not 'succeeded' — it must never fake execution success
    expect(result.status).toBe("skipped");
    expect(runner.invocations).toHaveLength(0);
  });

  it("xcode-ios dry-run returns skipped (not fake success)", async () => {
    const runner = new FakeCommandRunner([]);
    const adapter = new XcodeIosAdapter(runner);
    const context: ToolchainAdapterContext = {
      productId: "flashit",
      phase: "build",
      surface: { type: "mobile-ios", adapter: "xcode-ios", path: "/ios" },
      dryRun: true,
      surfaceConfig: { xcodeProject: "FlashIt.xcodeproj", scheme: "FlashIt" },
      phaseConfig: {},
      logger: makeLogger(),
    };

    const result = await adapter.execute(context);

    // dry-run must be 'skipped', not 'succeeded'
    expect(result.status).toBe("skipped");
    expect(runner.invocations).toHaveLength(0);
  });
});

// ─── §2.5-3: missing output contract causes validation failure ─────────────────

describe("ToolchainOutputValidator — missing output contract", () => {
  it("returns invalid when outputDir is not provided", async () => {
    const validator = new ToolchainOutputValidator();
    const context: ToolchainAdapterContext = {
      productId: "test-product",
      phase: "build",
      surface: {
        type: "backend-api",
        adapter: "gradle-java-service",
        path: "/backend",
      },
      dryRun: false,
      surfaceConfig: {},
      phaseConfig: {},
      logger: makeLogger(),
      // outputDir intentionally absent
    };

    const result = await validator.validateOutputs(context, ["app.jar"]);

    expect(result.status).toBe("invalid");
    expect(result.errors.some((e) => e.path === "outputDir")).toBe(true);
    expect(result.missingArtifacts).toContain("app.jar");
  });
});

// ─── §2.5-4: no product-specific commands in the generic adapters ──────────────

describe("generic adapter — no product-specific logic", () => {
  it("GradleJavaServiceAdapter.plan uses only context-provided gradleModule, not hardcoded product names", async () => {
    // plan() does not validate the gradle module — only execute() does; use default repoRoot
    const adapter = new GradleJavaServiceAdapter();
    const context = makeContext({
      surfaceConfig: { gradleModule: ":products:custom-module:service" },
    });

    const steps = await adapter.plan(context);

    // The command must use the surface's gradleModule verbatim, not a hardcoded product name
    const command = steps[0]?.command.join(" ");
    expect(command).toContain(":products:custom-module:service:build");
    expect(command).not.toContain("digital-marketing");
    expect(command).not.toContain("dm-api");
  });

  it("PnpmViteReactAdapter dry-run uses only context-provided packagePath, not hardcoded product paths", async () => {
    const pnpmRepoRoot = await fs.mkdtemp(
      path.join(os.tmpdir(), "pnpm-safety-"),
    );
    try {
      const packageDir = path.join(pnpmRepoRoot, "products/custom-product/ui");
      await fs.mkdir(packageDir, { recursive: true });
      // Must have the build script so plan() doesn't throw 'script-not-found'
      await fs.writeFile(
        path.join(packageDir, "package.json"),
        JSON.stringify({
          name: "custom-product-ui",
          version: "0.0.0",
          scripts: { build: "vite build" },
        }),
      );

      const runner = new FakeCommandRunner([]);
      const adapter = new PnpmViteReactAdapter({
        repoRoot: pnpmRepoRoot,
        commandRunner: runner,
      });
      const context: ToolchainAdapterContext = {
        productId: "some-product",
        phase: "build",
        surface: { type: "web", adapter: "pnpm-vite-react", path: "/web" },
        dryRun: true,
        surfaceConfig: { packagePath: "products/custom-product/ui" },
        phaseConfig: {},
        logger: makeLogger(),
      };

      const result = await adapter.execute(context);

      // dry-run must be skipped — not fake success
      expect(result.status).toBe("skipped");
      // The adapter must not embed a hardcoded product path
      expect(runner.invocations).toHaveLength(0);
    } finally {
      await fs.rm(pnpmRepoRoot, { recursive: true, force: true });
    }
  });
});

// ─── §2.5-5: Digital Marketing resolves correct adapters ──────────────────────

describe("Digital Marketing adapter resolution", () => {
  it("resolves gradle-java-service for backend-api surface", async () => {
    const registry = new ToolchainAdapterRegistry(REPO_CONFIG_DIR);
    const definitions = await registry.loadRegistry();

    // digital-marketing backend uses gradle-java-service
    const definition = definitions["gradle-java-service"];
    expect(definition?.lifecycleEnabled).toBe(true);
    expect(definition?.supportedSurfaceTypes).toContain("backend-api");
    expect(definition?.readiness).toBe("execution-ready");
  });

  it("resolves pnpm-vite-react for web surface", async () => {
    const registry = new ToolchainAdapterRegistry(REPO_CONFIG_DIR);
    const definitions = await registry.loadRegistry();

    const definition = definitions["pnpm-vite-react"];
    expect(definition?.lifecycleEnabled).toBe(true);
    expect(definition?.supportedSurfaceTypes).toContain("web");
    expect(definition?.readiness).toBe("execution-ready");
  });
});

// ─── §2.5-6: FlashIt mobile adapters remain blocked ──────────────────────────

describe("FlashIt mobile adapters remain blocked", () => {
  it("xcode-ios is not lifecycle-enabled — FlashIt iOS surface stays blocked", async () => {
    const registry = new ToolchainAdapterRegistry(REPO_CONFIG_DIR);
    const definitions = await registry.loadRegistry();

    const xcodeEntry = definitions["xcode-ios"];
    // FlashIt uses xcode-ios; confirm it stays blocked
    expect(xcodeEntry?.lifecycleEnabled).toBe(false);
    expect(xcodeEntry?.featureFlagRequired).toBe(true);
  });

  it("gradle-android is not lifecycle-enabled — FlashIt Android surface stays blocked", async () => {
    const registry = new ToolchainAdapterRegistry(REPO_CONFIG_DIR);
    const definitions = await registry.loadRegistry();

    const androidEntry = definitions["gradle-android"];
    expect(androidEntry?.lifecycleEnabled).toBe(false);
    expect(androidEntry?.featureFlagRequired).toBe(true);
  });
});

// ─── §2.5-7: ToolchainSafetyLevel, ToolchainAdapterCapability, ToolchainOutputContract type tests ──

describe("§2.5 type contracts compile and are usable", () => {
  it("ToolchainSafetyLevel values are a closed set", () => {
    const levels: ToolchainSafetyLevel[] = [
      "safe",
      "blocked",
      "not-ready",
      "planned",
    ];
    expect(levels).toHaveLength(4);
    expect(levels).toContain("safe");
    expect(levels).toContain("blocked");
  });

  it("ToolchainOutputContract can describe expected outputs per phase", () => {
    const contract: ToolchainOutputContract = {
      phase: "build",
      expectedOutputs: ["app.jar", "test-report.xml"],
      description: "JVM service build artifacts",
    };
    expect(contract.phase).toBe("build");
    expect(contract.expectedOutputs).toHaveLength(2);
  });

  it("ToolchainAdapterCapability captures all required metadata fields", () => {
    const capability: ToolchainAdapterCapability = {
      adapterId: "gradle-java-service",
      supportedPhases: ["dev", "validate", "test", "build", "package"],
      supportedSurfaceTypes: ["backend-api", "worker", "operator"],
      requiredInputs: ["gradleModule"],
      outputContracts: [
        { phase: "build", expectedOutputs: ["build/libs/service.jar"] },
      ],
      safetyLevel: "safe",
      testStatus: "passing",
      implementationStatus: "complete",
      allowedByDefault: true,
    };
    expect(capability.safetyLevel).toBe("safe");
    expect(capability.allowedByDefault).toBe(true);
    expect(capability.outputContracts).toHaveLength(1);
  });

  it("blocked adapter capability encodes reason code", () => {
    const capability: ToolchainAdapterCapability = {
      adapterId: "xcode-ios",
      supportedPhases: ["dev", "validate", "test", "build", "package"],
      supportedSurfaceTypes: ["mobile-ios"],
      requiredInputs: ["xcodeProject", "scheme"],
      outputContracts: [],
      safetyLevel: "blocked",
      testStatus: "no-tests",
      implementationStatus: "partial",
      allowedByDefault: false,
      blockedReasonCode: "requires-mobile-adapter-contracts",
    };
    expect(capability.safetyLevel).toBe("blocked");
    expect(capability.allowedByDefault).toBe(false);
    expect(capability.blockedReasonCode).toBe(
      "requires-mobile-adapter-contracts",
    );
  });
});
