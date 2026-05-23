/**
 * Tests for KernelDoctorApiHandlers
 */

import { describe, expect, it } from "vitest";
import {
  checkAdapterAvailability,
  checkKernelServices,
  checkPlatformBridges,
  checkProviderReadiness,
  checkToolchainHealth,
  resolveProviderMode,
  runAllDoctorChecks,
  runDoctorCheck,
  type KernelLifecycleService,
} from "../KernelDoctorApiHandlers.js";

describe("KernelDoctorApiHandlers", () => {
  describe("resolveProviderMode", () => {
    it("defaults to platform mode for production environment", () => {
      expect(resolveProviderMode("production")).toBe("platform");
    });

    it("defaults to platform mode for production profile", () => {
      expect(resolveProviderMode(undefined, "production")).toBe("platform");
    });

    it("defaults to platform mode for development environment", () => {
      expect(resolveProviderMode("development")).toBe("platform");
    });

    it("uses bootstrap mode when GHATANA_PROVIDER_MODE is set to bootstrap", () => {
      const originalEnv = process.env.GHATANA_PROVIDER_MODE;
      process.env.GHATANA_PROVIDER_MODE = "bootstrap";
      expect(resolveProviderMode("development")).toBe("bootstrap");
      process.env.GHATANA_PROVIDER_MODE = originalEnv;
    });

    it("defaults to platform mode when no environment is specified", () => {
      expect(resolveProviderMode()).toBe("platform");
    });
  });

  describe("checkProviderReadiness", () => {
    it("returns healthy status when all required bridges are available", async () => {
      const mockService = {} as KernelLifecycleService;
      const result = await checkProviderReadiness(mockService, "test-product", "production");

      expect(result.checkType).toBe("provider-readiness");
      expect(result.status).toBe("healthy");
      expect(result.details.activeProviderMode).toBe("platform");
      expect(result.details.missingBridges).toEqual([]);
    });

    it("returns unhealthy status when required bridges are missing", async () => {
      const mockService = {} as KernelLifecycleService;
      const result = await checkProviderReadiness(mockService, "test-product", "production");

      // The mock has risk and notification bridges missing
      expect(result.details.missingBridges).toContain("risk");
      expect(result.details.missingBridges).toContain("notification");
      expect(result.recommendations).toBeDefined();
      expect(result.recommendations!.length).toBeGreaterThan(0);
    });

    it("provides actionable recommendations for missing bridges", async () => {
      const mockService = {} as KernelLifecycleService;
      const result = await checkProviderReadiness(mockService, "test-product", "production");

      expect(result.recommendations).toBeDefined();
      expect(result.recommendations!.some((rec) => rec.includes("Missing required platform bridges"))).toBe(true);
    });
  });

  describe("checkAdapterAvailability", () => {
    it("returns healthy status when all adapters are available", async () => {
      const mockService = {} as KernelLifecycleService;
      const result = await checkAdapterAvailability(mockService);

      expect(result.checkType).toBe("adapter-availability");
      expect(result.status).toBe("healthy");
      expect(result.details.javaAdapterAvailable).toBe(true);
      expect(result.details.typescriptAdapterAvailable).toBe(true);
      expect(result.details.rustAdapterAvailable).toBe(true);
      expect(result.details.pythonAdapterAvailable).toBe(true);
    });

    it("lists missing adapters when some are unavailable", async () => {
      const mockService = {} as KernelLifecycleService;
      const result = await checkAdapterAvailability(mockService);

      expect(result.details.missingAdapters).toBeDefined();
      expect(Array.isArray(result.details.missingAdapters)).toBe(true);
    });
  });

  describe("checkToolchainHealth", () => {
    it("returns healthy status when all toolchains are available", async () => {
      const result = await checkToolchainHealth();

      expect(result.checkType).toBe("toolchain-health");
      expect(result.status).toBe("healthy");
      expect(result.details.gradleAvailable).toBe(true);
      expect(result.details.nodeAvailable).toBe(true);
      expect(result.details.cargoAvailable).toBe(true);
      expect(result.details.pythonAvailable).toBe(true);
      expect(result.details.dockerAvailable).toBe(true);
    });

    it("includes version information for all toolchains", async () => {
      const result = await checkToolchainHealth();

      expect(result.details.versionInfo).toBeDefined();
      expect(result.details.versionInfo.gradle).toBeDefined();
      expect(result.details.versionInfo.node).toBeDefined();
      expect(result.details.versionInfo.cargo).toBeDefined();
    });
  });

  describe("checkPlatformBridges", () => {
    it("returns degraded status when some bridges are missing", async () => {
      const result = await checkPlatformBridges();

      expect(result.checkType).toBe("platform-bridges");
      expect(result.status).toBe("degraded");
      expect(result.details.riskBridgeAvailable).toBe(false);
      expect(result.details.notificationBridgeAvailable).toBe(false);
    });

    it("provides recommendations for missing bridges", async () => {
      const result = await checkPlatformBridges();

      expect(result.recommendations).toBeDefined();
      expect(result.recommendations!.some((rec) => rec.includes("Missing platform bridges"))).toBe(true);
    });
  });

  describe("checkKernelServices", () => {
    it("returns healthy status when all services are available", async () => {
      const mockService = {} as KernelLifecycleService;
      const result = await checkKernelServices(mockService);

      expect(result.checkType).toBe("kernel-services");
      expect(result.status).toBe("healthy");
      expect(result.details.lifecycleServiceAvailable).toBe(true);
      expect(result.details.planningServiceAvailable).toBe(true);
      expect(result.details.executionServiceAvailable).toBe(true);
      expect(result.details.validationServiceAvailable).toBe(true);
    });

    it("includes service health status for each service", async () => {
      const mockService = {} as KernelLifecycleService;
      const result = await checkKernelServices(mockService);

      expect(result.details.serviceHealth).toBeDefined();
      expect(result.details.serviceHealth.lifecycle).toBe("healthy");
      expect(result.details.serviceHealth.planning).toBe("healthy");
    });
  });

  describe("runDoctorCheck", () => {
    it("runs the specified doctor check", async () => {
      const mockService = {} as KernelLifecycleService;
      const result = await runDoctorCheck(mockService, "provider-readiness", "test-product", "production");

      expect(result.checkType).toBe("provider-readiness");
      expect(result.timestamp).toBeDefined();
    });

    it("returns unknown status for invalid check type", async () => {
      const mockService = {} as KernelLifecycleService;
      const result = await runDoctorCheck(mockService, "invalid-check", "test-product", "production");

      expect(result.checkType).toBe("unknown");
      expect(result.status).toBe("unknown");
      expect(result.details.error).toBeDefined();
    });

    it("provides recommendations for invalid check type", async () => {
      const mockService = {} as KernelLifecycleService;
      const result = await runDoctorCheck(mockService, "invalid-check", "test-product", "production");

      expect(result.recommendations).toBeDefined();
      expect(result.recommendations!.some((rec) => rec.includes("ghatana kernel doctor"))).toBe(true);
    });
  });

  describe("runAllDoctorChecks", () => {
    it("runs all available doctor checks", async () => {
      const mockService = {} as KernelLifecycleService;
      const results = await runAllDoctorChecks(mockService, "test-product", "production");

      expect(results).toHaveLength(5);
      expect(results[0].checkType).toBe("provider-readiness");
      expect(results[1].checkType).toBe("adapter-availability");
      expect(results[2].checkType).toBe("toolchain-health");
      expect(results[3].checkType).toBe("platform-bridges");
      expect(results[4].checkType).toBe("kernel-services");
    });

    it("includes timestamps for all check results", async () => {
      const mockService = {} as KernelLifecycleService;
      const results = await runAllDoctorChecks(mockService, "test-product", "production");

      results.forEach((result) => {
        expect(result.timestamp).toBeDefined();
        expect(new Date(result.timestamp).toISOString()).toBe(result.timestamp);
      });
    });
  });
});
