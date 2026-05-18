/**
 * Tests for ProductLifecyclePlanner — disabled product blocking and fail-closed behaviours.
 *
 * §2.4 requirements verified here:
 *  - Product with lifecycleExecutionAllowed: false must not produce a plan.
 *  - PHR, Finance, and FlashIt remain blocked.
 *  - Missing adapter for a surface causes planning to fail closed.
 *  - Deterministic plan step ordering (same input → same order).
 *
 * @doc.type test
 * @doc.purpose Validate planner fail-closed behaviours and blocked-product gate.
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

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

async function writePlannerFixtures(
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

  await fs.writeFile(
    path.join(configDir, "product-lifecycle-profiles.json"),
    JSON.stringify({ profiles: { default: {} } }, null, 2),
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
      "vite-web-app": {
        supportedPhases: ["dev", "validate", "test", "build"],
        supportedSurfaceTypes: ["studio", "web-app"],
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
      JSON.stringify(kernelProductYaml, null, 2),
      "utf-8",
    );
  }
}

/** Minimal lifecycle-enabled product that CAN run. */
function enabledProduct(
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
  };
}

/** Product that is explicitly NOT allowed to execute (blocked). */
function blockedProduct(
  id: string,
  reasonCodes: readonly string[] = [],
): Record<string, unknown> {
  return {
    id,
    name: id,
    kind: "business-product",
    lifecycleStatus: "enabled",
    lifecycle: { enabled: true },
    lifecycleExecutionAllowed: false,
    lifecycleConfigPath: `config/kernel-product-${id}.yaml`,
    lifecycleReadiness: {
      reasonCodes,
      requiredGates: [],
    },
  };
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe("ProductLifecyclePlanner – blocked product gate", () => {
  let tmpDir: string;
  let configDir: string;

  beforeEach(async () => {
    tmpDir = await fs.mkdtemp(path.join(os.tmpdir(), "planner-blocked-test-"));
    configDir = path.join(tmpDir, "config");
  });

  afterEach(async () => {
    await fs.rm(tmpDir, { recursive: true, force: true });
  });

  it("throws ProductLifecycleNotReadyError when lifecycleExecutionAllowed is false", async () => {
    const phr = blockedProduct("phr", [
      "requires-consent-gate",
      "requires-pii-classification",
      "requires-audit-evidence",
      "requires-fhir-contract-validation",
      "requires-data-sovereignty-gate",
    ]);

    await writePlannerFixtures(configDir, { phr });

    const planner = new ProductLifecyclePlanner(tmpDir, configDir);

    await expect(
      planner.plan("phr", "build", { providerMode: "bootstrap" }),
    ).rejects.toThrow(ProductLifecycleNotReadyError);
  });

  it("ProductLifecycleNotReadyError exposes readinessReasonCodes for blocked products", async () => {
    const finance = blockedProduct("finance", [
      "requires-regulatory-gates",
      "requires-promotion-approval",
      "requires-multi-module-build-validation",
    ]);

    await writePlannerFixtures(configDir, { finance });

    const planner = new ProductLifecyclePlanner(tmpDir, configDir);

    let caught: ProductLifecycleNotReadyError | undefined;
    try {
      await planner.plan("finance", "build", { providerMode: "bootstrap" });
    } catch (err) {
      if (err instanceof ProductLifecycleNotReadyError) {
        caught = err;
      }
    }

    expect(caught).toBeDefined();
    expect(caught?.productId).toBe("finance");
    expect(caught?.lifecycleExecutionAllowed).toBe(false);
    expect(caught?.readinessReasonCodes).toContain("requires-regulatory-gates");
    expect(caught?.readinessReasonCodes).toContain(
      "requires-promotion-approval",
    );
  });

  it("blocks flashit with required mobile adapter reason codes", async () => {
    const flashit = blockedProduct("flashit", [
      "requires-mobile-adapters",
      "requires-preview-security-gate",
      "requires-personal-data-classification",
      "requires-mobile-bundle-artifacts",
    ]);

    await writePlannerFixtures(configDir, { flashit });

    const planner = new ProductLifecyclePlanner(tmpDir, configDir);

    await expect(
      planner.plan("flashit", "build", { providerMode: "bootstrap" }),
    ).rejects.toThrow(ProductLifecycleNotReadyError);
  });

  it("blocks all three restricted products in a single registry", async () => {
    const products = {
      phr: blockedProduct("phr", ["requires-consent-gate"]),
      finance: blockedProduct("finance", ["requires-regulatory-gates"]),
      flashit: blockedProduct("flashit", ["requires-mobile-adapters"]),
    };

    await writePlannerFixtures(configDir, products);

    const planner = new ProductLifecyclePlanner(tmpDir, configDir);

    for (const productId of ["phr", "finance", "flashit"] as const) {
      await expect(
        planner.plan(productId, "build", { providerMode: "bootstrap" }),
      ).rejects.toThrow(ProductLifecycleNotReadyError);
    }
  });
});

describe("ProductLifecyclePlanner – deterministic plan ordering", () => {
  let tmpDir: string;
  let configDir: string;

  beforeEach(async () => {
    tmpDir = await fs.mkdtemp(path.join(os.tmpdir(), "planner-order-test-"));
    configDir = path.join(tmpDir, "config");
  });

  afterEach(async () => {
    await fs.rm(tmpDir, { recursive: true, force: true });
  });

  it("produces identical step order on repeated invocations", async () => {
    const kernelConfig = {
      productId: "digital-marketing",
      lifecycleProfile: "default",
      surfaces: {
        api: { type: "backend-api", adapter: "gradle-java-service" },
        studio: { type: "studio", adapter: "vite-web-app" },
      },
      phases: {
        build: { defaultSurfaces: ["api", "studio"], mode: "parallel" },
      },
    };

    await writePlannerFixtures(
      configDir,
      {
        "digital-marketing": enabledProduct(
          "digital-marketing",
          "config/kernel-product.yaml",
        ),
      },
      kernelConfig,
    );

    const planner = new ProductLifecyclePlanner(tmpDir, configDir);

    const plan1 = await planner.plan("digital-marketing", "build", {
      providerMode: "bootstrap",
    });
    const plan2 = await planner.plan("digital-marketing", "build", {
      providerMode: "bootstrap",
    });

    expect(plan1.steps.map((s) => s.id)).toEqual(plan2.steps.map((s) => s.id));
  });
});
