/**
 * Task 2.2 – Harden lifecycle planning.
 *
 * Verifies:
 *  - Plan output includes all required fields: productUnitId, adapterIds,
 *    healthChecks, warnings, blockingReasons, correlationId, environment, etc.
 *  - Plan validation checks: surfaces exist, adapters exist, default environment
 *    exists for deploy/verify/promote/rollback.
 *  - promote phase is supported and produces a valid plan.
 *  - Finance (lifecycleExecutionAllowed: false) always fails closed.
 *  - Warnings and blockingReasons arrays are present even when empty.
 *
 * @doc.type test
 * @doc.purpose Validate Task 2.2 lifecycle planning hardening.
 * @doc.layer kernel-lifecycle
 * @doc.pattern Unit Test
 */
import { describe, it, expect, beforeEach, afterEach } from "vitest";
import { promises as fs } from "node:fs";
import * as path from "node:path";
import * as os from "node:os";
import {
  ProductLifecyclePlanner,
  ProductLifecycleNotReadyError,
} from "../ProductLifecyclePlanner.js";
import type { ProductLifecyclePlan } from "../../domain/ProductLifecyclePhase.js";

// ---------------------------------------------------------------------------
// Shared fixture helpers
// ---------------------------------------------------------------------------

async function writeMinimalFixtures(
  configDir: string,
  registryProducts: Record<string, unknown>,
  kernelProductYaml?: unknown,
): Promise<void> {
  await fs.mkdir(configDir, { recursive: true });

  const registry = { version: "1.0.0", registry: registryProducts };
  await fs.writeFile(
    path.join(configDir, "canonical-product-registry.json"),
    JSON.stringify(registry, null, 2),
    "utf-8",
  );

  await fs.writeFile(
    path.join(configDir, "kernel-lifecycle-exclusions.json"),
    JSON.stringify({ excludedProducts: {} }, null, 2),
    "utf-8",
  );

  const profiles = {
    profiles: {
      default: {
        requiredGates: {
          validate: ["lint-check"],
          build: ["security-scan"],
        },
      },
      "standard-api": {
        requiredGates: {
          validate: ["lint-check"],
          deploy: ["readiness-gate"],
          promote: ["promotion-readiness"],
        },
      },
    },
  };
  await fs.writeFile(
    path.join(configDir, "product-lifecycle-profiles.json"),
    JSON.stringify(profiles, null, 2),
    "utf-8",
  );

  const toolchains = {
    adapters: {
      "gradle-java-service": {
        supportedPhases: ["dev", "validate", "test", "build"],
        supportedSurfaceTypes: ["backend-api"],
        status: "implemented",
        safeForDefault: true,
        lifecycleEnabled: true,
      },
      "pnpm-vite-react": {
        supportedPhases: ["dev", "validate", "test", "build"],
        supportedSurfaceTypes: ["web"],
        status: "implemented",
        safeForDefault: true,
        lifecycleEnabled: true,
      },
      "compose-local": {
        kind: "deployment-tool",
        supportedPhases: ["deploy", "verify", "rollback", "promote"],
        supportedSurfaceTypes: ["backend-api", "web"],
        status: "implemented",
        safeForDefault: true,
        lifecycleEnabled: true,
      },
    },
  };
  await fs.writeFile(
    path.join(configDir, "toolchain-adapter-registry.json"),
    JSON.stringify(toolchains, null, 2),
    "utf-8",
  );

  if (kernelProductYaml !== undefined) {
    await fs.writeFile(
      path.join(configDir, "kernel-product.yaml"),
      typeof kernelProductYaml === "string"
        ? kernelProductYaml
        : JSON.stringify(kernelProductYaml, null, 2),
      "utf-8",
    );
  }
}

function enabledRegistryProduct(
  id: string,
  configPath: string,
): Record<string, unknown> {
  return {
    id,
    name: id,
    kind: "web-app",
    lifecycleStatus: "enabled",
    lifecycle: { enabled: true },
    lifecycleConfigPath: configPath,
    lifecycleExecutionAllowed: true,
  };
}

function blockedRegistryProduct(
  id: string,
  reasonCodes: readonly string[] = [],
): Record<string, unknown> {
  return {
    id,
    name: id,
    kind: "business-product",
    lifecycleStatus: "planned",
    lifecycle: { enabled: false },
    lifecycleExecutionAllowed: false,
    lifecycleConfigPath: `config/kernel-product-${id}.yaml`,
    lifecycleReadiness: { reasonCodes, requiredGates: [] },
  };
}

/** Minimal two-surface kernel-product.yaml config object */
function twoSurfaceConfig(
  overrides: Record<string, unknown> = {},
): Record<string, unknown> {
  return {
    productId: "my-product",
    lifecycleProfile: "default",
    surfaces: {
      api: {
        type: "backend-api",
        adapter: "gradle-java-service",
        health: {
          type: "http",
          livePath: "/health/live",
          readyPath: "/health/ready",
          portVariable: "API_PORT",
          defaultPort: 8080,
        },
      },
      web: {
        type: "web",
        adapter: "pnpm-vite-react",
        health: {
          type: "http",
          livePath: "/",
          readyPath: "/",
          portVariable: "WEB_PORT",
          defaultPort: 5173,
        },
      },
    },
    phases: {
      validate: { defaultSurfaces: ["api", "web"], mode: "parallel" },
      build: { defaultSurfaces: ["api", "web"], mode: "parallel" },
      deploy: {
        defaultEnvironment: "local",
        defaultSurfaces: ["api", "web"],
        mode: "sequential",
      },
      verify: {
        defaultEnvironment: "local",
        mode: "sequential",
      },
      rollback: {
        defaultEnvironment: "local",
        mode: "sequential",
      },
      promote: {
        defaultEnvironment: "local",
        mode: "sequential",
      },
    },
    deployment: {
      local: {
        adapter: "compose-local",
        composeFile: "deploy/local.compose.yaml",
      },
    },
    verify: {
      local: {
        adapter: "compose-local",
      },
    },
    ...overrides,
  };
}

// ---------------------------------------------------------------------------
// Suite: required plan output fields
// ---------------------------------------------------------------------------

describe("Task 2.2 – plan output must include all required fields", () => {
  let tmpDir: string;
  let configDir: string;

  beforeEach(async () => {
    tmpDir = await fs.mkdtemp(path.join(os.tmpdir(), "task22-fields-"));
    configDir = path.join(tmpDir, "config");
  });

  afterEach(async () => {
    await fs.rm(tmpDir, { recursive: true, force: true });
  });

  it("plan includes productUnitId equal to productId", async () => {
    await writeMinimalFixtures(
      configDir,
      { "my-product": enabledRegistryProduct("my-product", "config/kernel-product.yaml") },
      twoSurfaceConfig(),
    );

    const planner = new ProductLifecyclePlanner(tmpDir, configDir);
    const plan = await planner.plan("my-product", "validate", { providerMode: "bootstrap" });

    expect(plan.productUnitId).toBe("my-product");
    expect(plan.productUnitId).toBe(plan.productId);
  });

  it("plan includes correlationId", async () => {
    await writeMinimalFixtures(
      configDir,
      { "my-product": enabledRegistryProduct("my-product", "config/kernel-product.yaml") },
      twoSurfaceConfig(),
    );

    const planner = new ProductLifecyclePlanner(tmpDir, configDir);
    const plan = await planner.plan("my-product", "validate", {
      providerMode: "bootstrap",
      correlationId: "test-corr-001",
    });

    expect(plan.correlationId).toBe("test-corr-001");
  });

  it("plan includes phase field matching the requested phase", async () => {
    await writeMinimalFixtures(
      configDir,
      { "my-product": enabledRegistryProduct("my-product", "config/kernel-product.yaml") },
      twoSurfaceConfig(),
    );

    const planner = new ProductLifecyclePlanner(tmpDir, configDir);
    const plan = await planner.plan("my-product", "build", { providerMode: "bootstrap" });

    expect(plan.phase).toBe("build");
  });

  it("plan includes surfaces array with correct surface info", async () => {
    await writeMinimalFixtures(
      configDir,
      { "my-product": enabledRegistryProduct("my-product", "config/kernel-product.yaml") },
      twoSurfaceConfig(),
    );

    const planner = new ProductLifecyclePlanner(tmpDir, configDir);
    const plan = await planner.plan("my-product", "validate", { providerMode: "bootstrap" });

    expect(plan.surfaces.length).toBeGreaterThan(0);
    expect(plan.surfaces.every((s) => typeof s.surface === "string")).toBe(true);
    expect(plan.surfaces.every((s) => typeof s.adapter === "string")).toBe(true);
  });

  it("plan includes adapterIds top-level array", async () => {
    await writeMinimalFixtures(
      configDir,
      { "my-product": enabledRegistryProduct("my-product", "config/kernel-product.yaml") },
      twoSurfaceConfig(),
    );

    const planner = new ProductLifecyclePlanner(tmpDir, configDir);
    const plan = await planner.plan("my-product", "validate", { providerMode: "bootstrap" });

    expect(Array.isArray(plan.adapterIds)).toBe(true);
    expect(plan.adapterIds.length).toBeGreaterThan(0);
    expect(plan.adapterIds).toContain("gradle-java-service");
    expect(plan.adapterIds).toContain("pnpm-vite-react");
  });

  it("adapterIds has no duplicates", async () => {
    await writeMinimalFixtures(
      configDir,
      { "my-product": enabledRegistryProduct("my-product", "config/kernel-product.yaml") },
      twoSurfaceConfig(),
    );

    const planner = new ProductLifecyclePlanner(tmpDir, configDir);
    const plan = await planner.plan("my-product", "validate", { providerMode: "bootstrap" });

    const unique = [...new Set(plan.adapterIds)];
    expect(plan.adapterIds).toEqual(unique);
  });

  it("plan includes warnings array (empty for healthy plan)", async () => {
    await writeMinimalFixtures(
      configDir,
      { "my-product": enabledRegistryProduct("my-product", "config/kernel-product.yaml") },
      twoSurfaceConfig(),
    );

    const planner = new ProductLifecyclePlanner(tmpDir, configDir);
    const plan = await planner.plan("my-product", "validate", { providerMode: "bootstrap" });

    expect(Array.isArray(plan.warnings)).toBe(true);
  });

  it("plan includes blockingReasons array (empty for valid plan)", async () => {
    await writeMinimalFixtures(
      configDir,
      { "my-product": enabledRegistryProduct("my-product", "config/kernel-product.yaml") },
      twoSurfaceConfig(),
    );

    const planner = new ProductLifecyclePlanner(tmpDir, configDir);
    const plan = await planner.plan("my-product", "validate", { providerMode: "bootstrap" });

    expect(Array.isArray(plan.blockingReasons)).toBe(true);
    expect(plan.blockingReasons.length).toBe(0);
  });

  it("plan includes healthChecks derived from surface health config", async () => {
    await writeMinimalFixtures(
      configDir,
      { "my-product": enabledRegistryProduct("my-product", "config/kernel-product.yaml") },
      twoSurfaceConfig(),
    );

    const planner = new ProductLifecyclePlanner(tmpDir, configDir);
    const plan = await planner.plan("my-product", "validate", { providerMode: "bootstrap" });

    expect(Array.isArray(plan.healthChecks)).toBe(true);
    expect(plan.healthChecks.length).toBeGreaterThan(0);

    const apiHealth = plan.healthChecks.find((h) => h.surface === "api");
    expect(apiHealth).toBeDefined();
    expect(apiHealth?.type).toBe("http");
    expect(apiHealth?.livePath).toBe("/health/live");
    expect(apiHealth?.readyPath).toBe("/health/ready");
    expect(apiHealth?.portVariable).toBe("API_PORT");
    expect(apiHealth?.defaultPort).toBe(8080);
  });

  it("plan includes gates from profile and config", async () => {
    await writeMinimalFixtures(
      configDir,
      { "my-product": enabledRegistryProduct("my-product", "config/kernel-product.yaml") },
      twoSurfaceConfig(),
    );

    const planner = new ProductLifecyclePlanner(tmpDir, configDir);
    const plan = await planner.plan("my-product", "validate", { providerMode: "bootstrap" });

    expect(Array.isArray(plan.gates)).toBe(true);
    // "lint-check" comes from profile.requiredGates.validate
    expect(plan.gates.some((g) => g.gateId === "lint-check")).toBe(true);
  });

  it("plan includes ordered steps with adapter references", async () => {
    await writeMinimalFixtures(
      configDir,
      { "my-product": enabledRegistryProduct("my-product", "config/kernel-product.yaml") },
      twoSurfaceConfig(),
    );

    const planner = new ProductLifecyclePlanner(tmpDir, configDir);
    const plan = await planner.plan("my-product", "validate", { providerMode: "bootstrap" });

    expect(Array.isArray(plan.steps)).toBe(true);
    expect(plan.steps.length).toBeGreaterThan(0);
    expect(plan.steps.every((s) => typeof s.adapter === "string")).toBe(true);
    expect(plan.steps.every((s) => typeof s.id === "string")).toBe(true);
  });

  it("plan includes requiredManifests array", async () => {
    await writeMinimalFixtures(
      configDir,
      { "my-product": enabledRegistryProduct("my-product", "config/kernel-product.yaml") },
      twoSurfaceConfig(),
    );

    const planner = new ProductLifecyclePlanner(tmpDir, configDir);
    const plan = await planner.plan("my-product", "validate", { providerMode: "bootstrap" });

    expect(Array.isArray(plan.requiredManifests)).toBe(true);
  });

  it("plan includes expectedArtifacts array", async () => {
    await writeMinimalFixtures(
      configDir,
      { "my-product": enabledRegistryProduct("my-product", "config/kernel-product.yaml") },
      twoSurfaceConfig(),
    );

    const planner = new ProductLifecyclePlanner(tmpDir, configDir);
    const plan = await planner.plan("my-product", "validate", { providerMode: "bootstrap" });

    expect(Array.isArray(plan.expectedArtifacts)).toBe(true);
  });

  it("plan includes approvalRequirements array", async () => {
    await writeMinimalFixtures(
      configDir,
      { "my-product": enabledRegistryProduct("my-product", "config/kernel-product.yaml") },
      twoSurfaceConfig(),
    );

    const planner = new ProductLifecyclePlanner(tmpDir, configDir);
    const plan = await planner.plan("my-product", "validate", { providerMode: "bootstrap" });

    expect(Array.isArray(plan.approvalRequirements)).toBe(true);
  });

  it("deploy plan includes environment from options", async () => {
    await writeMinimalFixtures(
      configDir,
      { "my-product": enabledRegistryProduct("my-product", "config/kernel-product.yaml") },
      twoSurfaceConfig(),
    );

    const planner = new ProductLifecyclePlanner(tmpDir, configDir);
    const plan = await planner.plan("my-product", "deploy", {
      providerMode: "bootstrap",
      environment: "local",
    });

    expect(plan.environment).toBe("local");
    expect(plan.phase).toBe("deploy");
  });
});

// ---------------------------------------------------------------------------
// Suite: plan validation — surfaces, adapters, gates, environment
// ---------------------------------------------------------------------------

describe("Task 2.2 – plan validation fail-closed checks", () => {
  let tmpDir: string;
  let configDir: string;

  beforeEach(async () => {
    tmpDir = await fs.mkdtemp(path.join(os.tmpdir(), "task22-validation-"));
    configDir = path.join(tmpDir, "config");
  });

  afterEach(async () => {
    await fs.rm(tmpDir, { recursive: true, force: true });
  });

  it("fails when surface declared in phase does not exist in surfaces config", async () => {
    const badConfig = {
      productId: "my-product",
      lifecycleProfile: "default",
      surfaces: {
        api: { type: "backend-api", adapter: "gradle-java-service" },
      },
      phases: {
        validate: { defaultSurfaces: ["api", "missing-surface"], mode: "parallel" },
      },
    };

    await writeMinimalFixtures(
      configDir,
      { "my-product": enabledRegistryProduct("my-product", "config/kernel-product.yaml") },
      badConfig,
    );

    const planner = new ProductLifecyclePlanner(tmpDir, configDir);

    await expect(
      planner.plan("my-product", "validate", { providerMode: "bootstrap" }),
    ).rejects.toThrow(/missing-surface/);
  });

  it("fails when adapter for surface is not in toolchain registry", async () => {
    const badConfig = {
      productId: "my-product",
      lifecycleProfile: "default",
      surfaces: {
        api: { type: "backend-api", adapter: "nonexistent-adapter" },
      },
      phases: {
        validate: { defaultSurfaces: ["api"], mode: "parallel" },
      },
    };

    await writeMinimalFixtures(
      configDir,
      { "my-product": enabledRegistryProduct("my-product", "config/kernel-product.yaml") },
      badConfig,
    );

    const planner = new ProductLifecyclePlanner(tmpDir, configDir);

    await expect(
      planner.plan("my-product", "validate", { providerMode: "bootstrap" }),
    ).rejects.toThrow(/nonexistent-adapter|adapter/i);
  });

  it("fails when deploy target environment has no deployment config", async () => {
    const noDeployConfig = {
      productId: "my-product",
      lifecycleProfile: "default",
      surfaces: {
        api: { type: "backend-api", adapter: "gradle-java-service" },
      },
      phases: {
        deploy: {
          defaultEnvironment: "staging",
          defaultSurfaces: ["api"],
          mode: "sequential",
        },
      },
      deployment: {
        // "staging" is not defined
        local: {
          adapter: "compose-local",
          composeFile: "deploy/local.compose.yaml",
        },
      },
    };

    await writeMinimalFixtures(
      configDir,
      { "my-product": enabledRegistryProduct("my-product", "config/kernel-product.yaml") },
      noDeployConfig,
    );

    const planner = new ProductLifecyclePlanner(tmpDir, configDir);

    await expect(
      planner.plan("my-product", "deploy", { providerMode: "bootstrap" }),
    ).rejects.toThrow(/staging|deployment config/i);
  });

  it("fails when product is not in the registry", async () => {
    await writeMinimalFixtures(configDir, {});

    const planner = new ProductLifecyclePlanner(tmpDir, configDir);

    await expect(
      planner.plan("ghost-product", "validate", { providerMode: "bootstrap" }),
    ).rejects.toThrow(/ghost-product/);
  });

  it("fails closed for finance (lifecycleExecutionAllowed: false)", async () => {
    const finance = blockedRegistryProduct("finance", ["requires-regulatory-gates"]);

    await writeMinimalFixtures(configDir, { finance });

    const planner = new ProductLifecyclePlanner(tmpDir, configDir);

    await expect(
      planner.plan("finance", "validate", { providerMode: "bootstrap" }),
    ).rejects.toThrow(ProductLifecycleNotReadyError);
  });

  it("fails closed for phr (lifecycleExecutionAllowed: false)", async () => {
    const phr = blockedRegistryProduct("phr", ["requires-consent-gate"]);

    await writeMinimalFixtures(configDir, { phr });

    const planner = new ProductLifecyclePlanner(tmpDir, configDir);

    await expect(
      planner.plan("phr", "validate", { providerMode: "bootstrap" }),
    ).rejects.toThrow(ProductLifecycleNotReadyError);
  });
});

// ---------------------------------------------------------------------------
// Suite: promote phase support
// ---------------------------------------------------------------------------

describe("Task 2.2 – promote phase support", () => {
  let tmpDir: string;
  let configDir: string;

  beforeEach(async () => {
    tmpDir = await fs.mkdtemp(path.join(os.tmpdir(), "task22-promote-"));
    configDir = path.join(tmpDir, "config");
  });

  afterEach(async () => {
    await fs.rm(tmpDir, { recursive: true, force: true });
  });

  it("can plan promote phase without error", async () => {
    await writeMinimalFixtures(
      configDir,
      { "my-product": enabledRegistryProduct("my-product", "config/kernel-product.yaml") },
      twoSurfaceConfig(),
    );

    const planner = new ProductLifecyclePlanner(tmpDir, configDir);
    const plan = await planner.plan("my-product", "promote", {
      providerMode: "bootstrap",
      environment: "local",
    });

    expect(plan.phase).toBe("promote");
    expect(plan.productUnitId).toBe("my-product");
  });

  it("promote plan includes adapterIds, warnings, and blockingReasons", async () => {
    await writeMinimalFixtures(
      configDir,
      { "my-product": enabledRegistryProduct("my-product", "config/kernel-product.yaml") },
      twoSurfaceConfig(),
    );

    const planner = new ProductLifecyclePlanner(tmpDir, configDir);
    const plan = await planner.plan("my-product", "promote", {
      providerMode: "bootstrap",
      environment: "local",
    });

    expect(Array.isArray(plan.adapterIds)).toBe(true);
    expect(plan.adapterIds).toContain("compose-local");
    expect(Array.isArray(plan.warnings)).toBe(true);
    expect(Array.isArray(plan.blockingReasons)).toBe(true);
  });

  it("promote step has stepKind=promotion", async () => {
    await writeMinimalFixtures(
      configDir,
      { "my-product": enabledRegistryProduct("my-product", "config/kernel-product.yaml") },
      twoSurfaceConfig(),
    );

    const planner = new ProductLifecyclePlanner(tmpDir, configDir);
    const plan = await planner.plan("my-product", "promote", {
      providerMode: "bootstrap",
      environment: "local",
    });

    const promoteStep = plan.steps[0];
    expect(promoteStep).toBeDefined();
    expect(promoteStep.stepKind).toBe("promotion");
  });

  it("promote fails when deploy deployment config does not exist for environment", async () => {
    const noPromoteConfig = {
      productId: "my-product",
      lifecycleProfile: "default",
      surfaces: {
        api: { type: "backend-api", adapter: "gradle-java-service" },
      },
      phases: {
        deploy: {
          defaultEnvironment: "prod",
          mode: "sequential",
        },
        promote: {
          defaultEnvironment: "prod",
          mode: "sequential",
        },
      },
      deployment: {
        local: {
          adapter: "compose-local",
          composeFile: "deploy/local.compose.yaml",
        },
        // "prod" deliberately missing
      },
    };

    await writeMinimalFixtures(
      configDir,
      { "my-product": enabledRegistryProduct("my-product", "config/kernel-product.yaml") },
      noPromoteConfig,
    );

    const planner = new ProductLifecyclePlanner(tmpDir, configDir);

    await expect(
      planner.plan("my-product", "promote", {
        providerMode: "bootstrap",
        environment: "prod",
      }),
    ).rejects.toThrow(/prod|deployment config/i);
  });
});

// ---------------------------------------------------------------------------
// Suite: plan type contracts
// ---------------------------------------------------------------------------

describe("Task 2.2 – plan type contracts", () => {
  let tmpDir: string;
  let configDir: string;

  beforeEach(async () => {
    tmpDir = await fs.mkdtemp(path.join(os.tmpdir(), "task22-types-"));
    configDir = path.join(tmpDir, "config");
  });

  afterEach(async () => {
    await fs.rm(tmpDir, { recursive: true, force: true });
  });

  it("plan satisfies ProductLifecyclePlan interface shape", async () => {
    await writeMinimalFixtures(
      configDir,
      { "my-product": enabledRegistryProduct("my-product", "config/kernel-product.yaml") },
      twoSurfaceConfig(),
    );

    const planner = new ProductLifecyclePlanner(tmpDir, configDir);
    const plan: ProductLifecyclePlan = await planner.plan("my-product", "validate", {
      providerMode: "bootstrap",
    });

    // Type-level assertions — these compile only if the interface matches
    const _schemaVersion: "1.0.0" = plan.schemaVersion;
    const _productId: string = plan.productId;
    const _productUnitId: string = plan.productUnitId;
    const _phase: string = plan.phase;
    const _warnings: readonly string[] = plan.warnings;
    const _blockingReasons: readonly string[] = plan.blockingReasons;
    const _adapterIds: readonly string[] = plan.adapterIds;
    const _healthChecks = plan.healthChecks;
    const _correlationId: string = plan.correlationId;

    expect(_schemaVersion).toBe("1.0.0");
    expect(typeof _productId).toBe("string");
    expect(typeof _productUnitId).toBe("string");
    expect(typeof _phase).toBe("string");
    expect(Array.isArray(_warnings)).toBe(true);
    expect(Array.isArray(_blockingReasons)).toBe(true);
    expect(Array.isArray(_adapterIds)).toBe(true);
    expect(Array.isArray(_healthChecks)).toBe(true);
    expect(typeof _correlationId).toBe("string");
  });

  it("plan for rollback includes phase=rollback and deployment adapter", async () => {
    await writeMinimalFixtures(
      configDir,
      { "my-product": enabledRegistryProduct("my-product", "config/kernel-product.yaml") },
      twoSurfaceConfig(),
    );

    const planner = new ProductLifecyclePlanner(tmpDir, configDir);
    const plan = await planner.plan("my-product", "rollback", {
      providerMode: "bootstrap",
      environment: "local",
    });

    expect(plan.phase).toBe("rollback");
    expect(plan.productUnitId).toBe("my-product");
    expect(plan.adapterIds).toContain("compose-local");
    expect(Array.isArray(plan.warnings)).toBe(true);
    expect(Array.isArray(plan.blockingReasons)).toBe(true);
  });

  it("plan for verify includes phase=verify", async () => {
    await writeMinimalFixtures(
      configDir,
      { "my-product": enabledRegistryProduct("my-product", "config/kernel-product.yaml") },
      twoSurfaceConfig(),
    );

    const planner = new ProductLifecyclePlanner(tmpDir, configDir);
    const plan = await planner.plan("my-product", "verify", {
      providerMode: "bootstrap",
      environment: "local",
    });

    expect(plan.phase).toBe("verify");
    expect(plan.productUnitId).toBe("my-product");
    expect(Array.isArray(plan.healthChecks)).toBe(true);
  });
});
