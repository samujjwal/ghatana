/**
 * Tests for ProviderModeEnforcer.
 */

import { describe, it, expect } from "vitest";
import {
  ProviderModeEnforcer,
  DEFAULT_PROVIDER_MODE_ENFORCEMENT_CONFIG,
  type ProviderModeEnforcementConfig,
} from "../ProviderModeEnforcer";
import type { KernelProviderHealthMatrix } from "@ghatana/kernel-product-contracts";

describe("ProviderModeEnforcer", () => {
  describe("default configuration", () => {
    it("should use default configuration", () => {
      const enforcer = new ProviderModeEnforcer();
      expect(enforcer).toBeDefined();
    });

    it("should allow custom configuration", () => {
      const customConfig: Partial<ProviderModeEnforcementConfig> = {
        minimumHealthThreshold: 0.8,
        allowDegradedInPlatform: true,
      };
      const enforcer = new ProviderModeEnforcer(customConfig);
      expect(enforcer).toBeDefined();
    });
  });

  describe("enforceProviderMode", () => {
    it("should always allow bootstrap mode", () => {
      const enforcer = new ProviderModeEnforcer();
      const healthMatrix: KernelProviderHealthMatrix = {
        schemaVersion: "1.0.0",
        generatedAt: "2024-01-01T00:00:00.000Z",
        overallStatus: "unhealthy",
        totalProviders: 10,
        healthyProviders: 0,
        degradedProviders: 0,
        unhealthyProviders: 10,
        unknownProviders: 0,
        providers: [],
        providerMode: "bootstrap",
        missingCapabilities: ["registry-read", "registry-write"],
      };

      const result = enforcer.enforceProviderMode(healthMatrix, "bootstrap");
      expect(result.allowed).toBe(true);
      expect(result.recommendedMode).toBe("bootstrap");
      expect(result.blockingIssues).toHaveLength(0);
    });

    it("should allow platform mode when all providers are healthy", () => {
      const enforcer = new ProviderModeEnforcer();
      const healthMatrix: KernelProviderHealthMatrix = {
        schemaVersion: "1.0.0",
        generatedAt: "2024-01-01T00:00:00.000Z",
        overallStatus: "healthy",
        totalProviders: 10,
        healthyProviders: 10,
        degradedProviders: 0,
        unhealthyProviders: 0,
        unknownProviders: 0,
        providers: [],
        providerMode: "platform",
        missingCapabilities: [],
      };

      const result = enforcer.enforceProviderMode(healthMatrix, "platform");
      expect(result.allowed).toBe(true);
      expect(result.recommendedMode).toBe("platform");
      expect(result.blockingIssues).toHaveLength(0);
    });

    it("should block platform mode when health ratio is below threshold", () => {
      const enforcer = new ProviderModeEnforcer({ minimumHealthThreshold: 0.9 });
      const healthMatrix: KernelProviderHealthMatrix = {
        schemaVersion: "1.0.0",
        generatedAt: "2024-01-01T00:00:00.000Z",
        overallStatus: "degraded",
        totalProviders: 10,
        healthyProviders: 8,
        degradedProviders: 2,
        unhealthyProviders: 0,
        unknownProviders: 0,
        providers: [],
        providerMode: "platform",
        missingCapabilities: [],
      };

      const result = enforcer.enforceProviderMode(healthMatrix, "platform");
      expect(result.allowed).toBe(false);
      expect(result.recommendedMode).toBe("bootstrap");
      expect(result.blockingIssues.length).toBeGreaterThan(0);
      expect(result.blockingIssues[0]).toContain("Health ratio");
    });

    it("should block platform mode when there are unhealthy providers", () => {
      const enforcer = new ProviderModeEnforcer();
      const healthMatrix: KernelProviderHealthMatrix = {
        schemaVersion: "1.0.0",
        generatedAt: "2024-01-01T00:00:00.000Z",
        overallStatus: "unhealthy",
        totalProviders: 10,
        healthyProviders: 8,
        degradedProviders: 0,
        unhealthyProviders: 2,
        unknownProviders: 0,
        providers: [],
        providerMode: "platform",
        missingCapabilities: [],
      };

      const result = enforcer.enforceProviderMode(healthMatrix, "platform");
      expect(result.allowed).toBe(false);
      expect(result.recommendedMode).toBe("bootstrap");
      expect(result.blockingIssues.length).toBeGreaterThan(0);
      expect(result.blockingIssues.some((issue) => issue.includes("unhealthy providers"))).toBe(true);
    });

    it("should block platform mode when degraded providers are not allowed", () => {
      const enforcer = new ProviderModeEnforcer({ allowDegradedInPlatform: false });
      const healthMatrix: KernelProviderHealthMatrix = {
        schemaVersion: "1.0.0",
        generatedAt: "2024-01-01T00:00:00.000Z",
        overallStatus: "degraded",
        totalProviders: 10,
        healthyProviders: 9,
        degradedProviders: 1,
        unhealthyProviders: 0,
        unknownProviders: 0,
        providers: [],
        providerMode: "platform",
        missingCapabilities: [],
      };

      const result = enforcer.enforceProviderMode(healthMatrix, "platform");
      expect(result.allowed).toBe(false);
      expect(result.recommendedMode).toBe("bootstrap");
      expect(result.blockingIssues.length).toBeGreaterThan(0);
      expect(result.blockingIssues[0]).toContain("degraded providers");
    });

    it("should allow platform mode when degraded providers are allowed", () => {
      const enforcer = new ProviderModeEnforcer({ allowDegradedInPlatform: true });
      const healthMatrix: KernelProviderHealthMatrix = {
        schemaVersion: "1.0.0",
        generatedAt: "2024-01-01T00:00:00.000Z",
        overallStatus: "degraded",
        totalProviders: 10,
        healthyProviders: 9,
        degradedProviders: 1,
        unhealthyProviders: 0,
        unknownProviders: 0,
        providers: [],
        providerMode: "platform",
        missingCapabilities: [],
      };

      const result = enforcer.enforceProviderMode(healthMatrix, "platform");
      expect(result.allowed).toBe(true);
      expect(result.recommendedMode).toBe("platform");
      expect(result.blockingIssues).toHaveLength(0);
    });

    it("should block platform mode when required capabilities are missing", () => {
      const enforcer = new ProviderModeEnforcer();
      const healthMatrix: KernelProviderHealthMatrix = {
        schemaVersion: "1.0.0",
        generatedAt: "2024-01-01T00:00:00.000Z",
        overallStatus: "healthy",
        totalProviders: 10,
        healthyProviders: 10,
        degradedProviders: 0,
        unhealthyProviders: 0,
        unknownProviders: 0,
        providers: [],
        providerMode: "platform",
        missingCapabilities: ["registry-read", "registry-write"],
      };

      const result = enforcer.enforceProviderMode(healthMatrix, "platform");
      expect(result.allowed).toBe(false);
      expect(result.recommendedMode).toBe("bootstrap");
      expect(result.blockingIssues.length).toBeGreaterThan(0);
      expect(result.blockingIssues[0]).toContain("Missing required capabilities");
    });
  });

  describe("validateProviderConformance", () => {
    it("should validate healthy matrix", () => {
      const enforcer = new ProviderModeEnforcer();
      const healthMatrix: KernelProviderHealthMatrix = {
        schemaVersion: "1.0.0",
        generatedAt: "2024-01-01T00:00:00.000Z",
        overallStatus: "healthy",
        totalProviders: 10,
        healthyProviders: 10,
        degradedProviders: 0,
        unhealthyProviders: 0,
        unknownProviders: 0,
        providers: [],
        providerMode: "platform",
        missingCapabilities: [],
      };

      const result = enforcer.validateProviderConformance(healthMatrix);
      expect(result.valid).toBe(true);
      expect(result.errors).toHaveLength(0);
    });

    it("should fail validation for unhealthy matrix", () => {
      const enforcer = new ProviderModeEnforcer();
      const healthMatrix: KernelProviderHealthMatrix = {
        schemaVersion: "1.0.0",
        generatedAt: "2024-01-01T00:00:00.000Z",
        overallStatus: "unhealthy",
        totalProviders: 10,
        healthyProviders: 0,
        degradedProviders: 0,
        unhealthyProviders: 10,
        unknownProviders: 0,
        providers: [],
        providerMode: "platform",
        missingCapabilities: [],
      };

      const result = enforcer.validateProviderConformance(healthMatrix);
      expect(result.valid).toBe(false);
      expect(result.errors.length).toBeGreaterThan(0);
      expect(result.errors[0]).toContain("unhealthy");
    });

    it("should warn for unknown status", () => {
      const enforcer = new ProviderModeEnforcer();
      const healthMatrix: KernelProviderHealthMatrix = {
        schemaVersion: "1.0.0",
        generatedAt: "2024-01-01T00:00:00.000Z",
        overallStatus: "unknown",
        totalProviders: 10,
        healthyProviders: 0,
        degradedProviders: 0,
        unhealthyProviders: 0,
        unknownProviders: 10,
        providers: [],
        providerMode: "platform",
        missingCapabilities: [],
      };

      const result = enforcer.validateProviderConformance(healthMatrix);
      expect(result.valid).toBe(true);
      expect(result.warnings.length).toBeGreaterThan(0);
      expect(result.warnings[0]).toContain("unknown");
    });

    it("should error on missing capabilities when fail-closed is enabled", () => {
      const enforcer = new ProviderModeEnforcer({ failClosedOnMissingCapabilities: true });
      const healthMatrix: KernelProviderHealthMatrix = {
        schemaVersion: "1.0.0",
        generatedAt: "2024-01-01T00:00:00.000Z",
        overallStatus: "healthy",
        totalProviders: 10,
        healthyProviders: 10,
        degradedProviders: 0,
        unhealthyProviders: 0,
        unknownProviders: 0,
        providers: [],
        providerMode: "platform",
        missingCapabilities: ["registry-read"],
      };

      const result = enforcer.validateProviderConformance(healthMatrix);
      expect(result.valid).toBe(false);
      expect(result.errors.length).toBeGreaterThan(0);
      expect(result.errors[0]).toContain("Missing required capabilities");
    });

    it("should warn on missing capabilities when fail-closed is disabled", () => {
      const enforcer = new ProviderModeEnforcer({ failClosedOnMissingCapabilities: false });
      const healthMatrix: KernelProviderHealthMatrix = {
        schemaVersion: "1.0.0",
        generatedAt: "2024-01-01T00:00:00.000Z",
        overallStatus: "healthy",
        totalProviders: 10,
        healthyProviders: 10,
        degradedProviders: 0,
        unhealthyProviders: 0,
        unknownProviders: 0,
        providers: [],
        providerMode: "platform",
        missingCapabilities: ["registry-read"],
      };

      const result = enforcer.validateProviderConformance(healthMatrix);
      expect(result.valid).toBe(true);
      expect(result.warnings.length).toBeGreaterThan(0);
      expect(result.warnings[0]).toContain("Missing optional capabilities");
    });
  });

  describe("getRecommendedMode", () => {
    it("should recommend platform mode for healthy matrix", () => {
      const enforcer = new ProviderModeEnforcer();
      const healthMatrix: KernelProviderHealthMatrix = {
        schemaVersion: "1.0.0",
        generatedAt: "2024-01-01T00:00:00.000Z",
        overallStatus: "healthy",
        totalProviders: 10,
        healthyProviders: 10,
        degradedProviders: 0,
        unhealthyProviders: 0,
        unknownProviders: 0,
        providers: [],
        providerMode: "platform",
        missingCapabilities: [],
      };

      const recommended = enforcer.getRecommendedMode(healthMatrix);
      expect(recommended).toBe("platform");
    });

    it("should recommend bootstrap mode for unhealthy matrix", () => {
      const enforcer = new ProviderModeEnforcer();
      const healthMatrix: KernelProviderHealthMatrix = {
        schemaVersion: "1.0.0",
        generatedAt: "2024-01-01T00:00:00.000Z",
        overallStatus: "unhealthy",
        totalProviders: 10,
        healthyProviders: 0,
        degradedProviders: 0,
        unhealthyProviders: 10,
        unknownProviders: 0,
        providers: [],
        providerMode: "bootstrap",
        missingCapabilities: [],
      };

      const recommended = enforcer.getRecommendedMode(healthMatrix);
      expect(recommended).toBe("bootstrap");
    });
  });
});
