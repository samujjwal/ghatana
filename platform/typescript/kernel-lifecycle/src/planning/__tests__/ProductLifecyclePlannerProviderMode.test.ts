/**
 * Tests for ProductLifecyclePlanner provider mode fail-closed behavior.
 *
 * @doc.type test
 * @doc.purpose Validate provider mode validation with allowBootstrapFallback option
 * @doc.layer kernel-lifecycle
 * @doc.pattern Unit Test
 */

import { describe, it, expect, beforeEach, afterEach, vi } from "vitest";
import * as fs from "node:fs/promises";
import * as path from "node:path";
import * as os from "node:os";
import { ProductLifecyclePlanner, type ProductLifecyclePlanOptions } from "../ProductLifecyclePlanner.js";
import type {
  KernelLifecycleProviderContext,
  RegistryProvider,
  SourceProvider,
  LifecycleEventProvider,
  LifecycleArtifactProvider,
  LifecycleHealthProvider,
  LifecycleApprovalProvider,
  LifecycleProvenanceProvider,
  LifecycleRuntimeTruthProvider,
} from "@ghatana/kernel-product-contracts";

describe("ProductLifecyclePlanner Provider Mode Fail-Closed", () => {
  let tempDir: string;
  let registryPath: string;
  let exclusionsPath: string;
  let profilesPath: string;
  let toolchainPath: string;
  let planner: ProductLifecyclePlanner;

  beforeEach(async () => {
    tempDir = await fs.mkdtemp(path.join(os.tmpdir(), "planner-test-"));
    registryPath = path.join(tempDir, "registry.json");
    exclusionsPath = path.join(tempDir, "exclusions.json");
    profilesPath = path.join(tempDir, "profiles.json");
    toolchainPath = path.join(tempDir, "toolchain.json");

    // Create minimal registry
    const registry = {
      version: "1.0.0",
      registry: {
        "test-product": {
          id: "test-product",
          name: "Test Product",
          kind: "web-app",
          lifecycleStatus: "enabled",
          lifecycle: { enabled: true },
          lifecycleConfigPath: "config/kernel-product.yaml",
        },
      },
    };
    await fs.writeFile(registryPath, JSON.stringify(registry, null, 2), "utf-8");

    // Create minimal exclusions
    const exclusions = { excluded: [] };
    await fs.writeFile(exclusionsPath, JSON.stringify(exclusions, null, 2), "utf-8");

    // Create minimal profiles
    const profiles = { profiles: { default: {} } };
    await fs.writeFile(profilesPath, JSON.stringify(profiles, null, 2), "utf-8");

    // Create minimal toolchain registry
    const toolchains = {
      adapters: {
        "gradle-java-service": {
          supportedPhases: ["dev", "validate", "test", "build"],
          supportedSurfaceTypes: ["backend-api"],
          status: "implemented",
          safeForDefault: true,
        },
      },
    };
    await fs.writeFile(toolchainPath, JSON.stringify(toolchains, null, 2), "utf-8");

    // Create minimal kernel-product.yaml
    const configDir = path.join(tempDir, "config");
    await fs.mkdir(configDir, { recursive: true });
    const kernelProductConfig = {
      productId: "test-product",
      lifecycleProfile: "default",
      surfaces: {
        api: {
          type: "backend-api",
          adapter: "gradle-java-service",
        },
      },
      phases: {
        dev: {
          defaultSurfaces: ["api"],
          mode: "parallel",
        },
      },
    };
    await fs.writeFile(
      path.join(configDir, "kernel-product.yaml"),
      kernelProductConfig as unknown as string,
      "utf-8"
    );

    planner = new ProductLifecyclePlanner(tempDir, configDir);
  });

  afterEach(async () => {
    await fs.rm(tempDir, { recursive: true, force: true });
  });

  describe("bootstrap mode", () => {
    it("should allow bootstrap mode without provider context", async () => {
      const options: ProductLifecyclePlanOptions = {
        providerMode: "bootstrap",
      };

      const plan = await planner.plan("test-product", "dev", options);

      expect(plan.providerMode).toBe("bootstrap");
    });

    it("should allow bootstrap mode with missing providers", async () => {
      const partialContext: Partial<KernelLifecycleProviderContext> = {
        mode: "bootstrap",
      };

      const plannerWithContext = new ProductLifecyclePlanner(tempDir, tempDir, partialContext as KernelLifecycleProviderContext);
      const options: ProductLifecyclePlanOptions = {
        providerMode: "bootstrap",
      };

      const plan = await plannerWithContext.plan("test-product", "dev", options);

      expect(plan.providerMode).toBe("bootstrap");
    });
  });

  describe("platform mode fail-closed", () => {
    it("should reject platform mode without provider context", async () => {
      const options: ProductLifecyclePlanOptions = {
        providerMode: "platform",
        allowBootstrapFallback: false,
      };

      await expect(planner.plan("test-product", "dev", options)).rejects.toThrow(
        "Kernel platform provider mode requires a provider context"
      );
    });

    it("should reject platform mode with wrong context mode", async () => {
      const wrongContext: KernelLifecycleProviderContext = {
        mode: "bootstrap",
      };

      const plannerWithContext = new ProductLifecyclePlanner(tempDir, tempDir, wrongContext);
      const options: ProductLifecyclePlanOptions = {
        providerMode: "platform",
        allowBootstrapFallback: false,
      };

      await expect(plannerWithContext.plan("test-product", "dev", options)).rejects.toThrow(
        "Kernel platform provider mode requires a platform provider context"
      );
    });

    it("should reject platform mode with missing required providers", async () => {
      const incompleteContext: KernelLifecycleProviderContext = {
        mode: "platform",
        registryProvider: {} as RegistryProvider,
        // Missing: sourceProvider, artifacts, events, health, approvals, provenance, runtimeTruth
      };

      const plannerWithContext = new ProductLifecyclePlanner(tempDir, tempDir, incompleteContext);
      const options: ProductLifecyclePlanOptions = {
        providerMode: "platform",
        allowBootstrapFallback: false,
      };

      await expect(plannerWithContext.plan("test-product", "dev", options)).rejects.toThrow(
        "Kernel platform provider mode requires all providers to be available"
      );
    });

    it("should accept platform mode with all required providers", async () => {
      const completeContext: KernelLifecycleProviderContext = {
        mode: "platform",
        registryProvider: {} as RegistryProvider,
        sourceProvider: {} as SourceProvider,
        artifacts: {} as LifecycleArtifactProvider,
        events: {} as LifecycleEventProvider,
        health: {} as LifecycleHealthProvider,
        approvals: {} as LifecycleApprovalProvider,
        provenance: {} as LifecycleProvenanceProvider,
        runtimeTruth: {} as LifecycleRuntimeTruthProvider,
      };

      const plannerWithContext = new ProductLifecyclePlanner(tempDir, tempDir, completeContext);
      const options: ProductLifecyclePlanOptions = {
        providerMode: "platform",
        allowBootstrapFallback: false,
      };

      const plan = await plannerWithContext.plan("test-product", "dev", options);

      expect(plan.providerMode).toBe("platform");
    });
  });

  describe("allowBootstrapFallback", () => {
    it("should fall back to bootstrap when allowBootstrapFallback: true and context missing", async () => {
      const options: ProductLifecyclePlanOptions = {
        providerMode: "platform",
        allowBootstrapFallback: true,
        correlationId: "test-corr-123",
      };

      const warnSpy = vi.spyOn(console, "warn").mockImplementation(() => {});

      const plan = await planner.plan("test-product", "dev", options);

      expect(plan.providerMode).toBe("bootstrap");
      expect(warnSpy).toHaveBeenCalledWith(
        expect.stringContaining("Platform mode requested but provider context not available")
      );
      expect(warnSpy).toHaveBeenCalledWith(
        expect.stringContaining("allowBootstrapFallback: true")
      );

      warnSpy.mockRestore();
    });

    it("should fall back to bootstrap when allowBootstrapFallback: true and context mode wrong", async () => {
      const wrongContext: KernelLifecycleProviderContext = {
        mode: "bootstrap",
      };

      const plannerWithContext = new ProductLifecyclePlanner(tempDir, tempDir, wrongContext);
      const options: ProductLifecyclePlanOptions = {
        providerMode: "platform",
        allowBootstrapFallback: true,
        correlationId: "test-corr-123",
      };

      const warnSpy = vi.spyOn(console, "warn").mockImplementation(() => {});

      const plan = await plannerWithContext.plan("test-product", "dev", options);

      expect(plan.providerMode).toBe("bootstrap");
      expect(warnSpy).toHaveBeenCalledWith(
        expect.stringContaining("provider context mode is bootstrap")
      );
      expect(warnSpy).toHaveBeenCalledWith(
        expect.stringContaining("allowBootstrapFallback: true")
      );

      warnSpy.mockRestore();
    });

    it("should fall back to bootstrap when allowBootstrapFallback: true and providers missing", async () => {
      const incompleteContext: KernelLifecycleProviderContext = {
        mode: "platform",
        registryProvider: {} as RegistryProvider,
        // Missing: sourceProvider, artifacts, events, health, approvals, provenance, runtimeTruth
      };

      const plannerWithContext = new ProductLifecyclePlanner(tempDir, tempDir, incompleteContext);
      const options: ProductLifecyclePlanOptions = {
        providerMode: "platform",
        allowBootstrapFallback: true,
        correlationId: "test-corr-123",
      };

      const warnSpy = vi.spyOn(console, "warn").mockImplementation(() => {});

      const plan = await plannerWithContext.plan("test-product", "dev", options);

      expect(plan.providerMode).toBe("bootstrap");
      expect(warnSpy).toHaveBeenCalledWith(
        expect.stringContaining("required providers are missing")
      );
      expect(warnSpy).toHaveBeenCalledWith(
        expect.stringContaining("allowBootstrapFallback: true")
      );

      warnSpy.mockRestore();
    });

    it("should not fall back when allowBootstrapFallback: false and context missing", async () => {
      const options: ProductLifecyclePlanOptions = {
        providerMode: "platform",
        allowBootstrapFallback: false,
      };

      const warnSpy = vi.spyOn(console, "warn").mockImplementation(() => {});

      await expect(planner.plan("test-product", "dev", options)).rejects.toThrow(
        "Kernel platform provider mode requires a provider context"
      );

      expect(warnSpy).not.toHaveBeenCalled();
      warnSpy.mockRestore();
    });

    it("should not fall back when allowBootstrapFallback: false and providers missing", async () => {
      const incompleteContext: KernelLifecycleProviderContext = {
        mode: "platform",
        registryProvider: {} as RegistryProvider,
      };

      const plannerWithContext = new ProductLifecyclePlanner(tempDir, tempDir, incompleteContext);
      const options: ProductLifecyclePlanOptions = {
        providerMode: "platform",
        allowBootstrapFallback: false,
      };

      const warnSpy = vi.spyOn(console, "warn").mockImplementation(() => {});

      await expect(plannerWithContext.plan("test-product", "dev", options)).rejects.toThrow(
        "Kernel platform provider mode requires all providers to be available"
      );

      expect(warnSpy).not.toHaveBeenCalled();
      warnSpy.mockRestore();
    });
  });

  describe("default behavior", () => {
    it("should default to bootstrap when no mode specified", async () => {
      const options: ProductLifecyclePlanOptions = {};

      const plan = await planner.plan("test-product", "dev", options);

      expect(plan.providerMode).toBe("bootstrap");
    });

    it("should default to bootstrap when providerContext has no mode", async () => {
      const contextWithoutMode = {} as KernelLifecycleProviderContext;
      const plannerWithContext = new ProductLifecyclePlanner(tempDir, tempDir, contextWithoutMode);
      const options: ProductLifecyclePlanOptions = {};

      const plan = await plannerWithContext.plan("test-product", "dev", options);

      expect(plan.providerMode).toBe("bootstrap");
    });

    it("should use providerContext.mode when available", async () => {
      const contextWithMode: KernelLifecycleProviderContext = {
        mode: "bootstrap",
        registryProvider: {} as RegistryProvider,
        sourceProvider: {} as SourceProvider,
        artifacts: {} as LifecycleArtifactProvider,
        events: {} as LifecycleEventProvider,
        health: {} as LifecycleHealthProvider,
        approvals: {} as LifecycleApprovalProvider,
        provenance: {} as LifecycleProvenanceProvider,
        runtimeTruth: {} as LifecycleRuntimeTruthProvider,
      };

      const plannerWithContext = new ProductLifecyclePlanner(tempDir, tempDir, contextWithMode);
      const options: ProductLifecyclePlanOptions = {};

      const plan = await plannerWithContext.plan("test-product", "dev", options);

      expect(plan.providerMode).toBe("bootstrap");
    });
  });
});
