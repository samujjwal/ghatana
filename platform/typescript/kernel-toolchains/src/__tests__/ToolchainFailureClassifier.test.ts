/**
 * Tests for ToolchainFailureClassifier.
 */

import { describe, it, expect } from "vitest";
import {
  ToolchainFailureClassifier,
  DEFAULT_FAILURE_CLASSIFIER_CONFIG,
} from "../ToolchainFailureClassifier";
import type { ToolchainAdapterContext } from "../ToolchainAdapter";
import type { ProductLifecyclePhase } from "@ghatana/kernel-product-contracts";

describe("ToolchainFailureClassifier", () => {
  describe("default configuration", () => {
    it("should use default configuration", () => {
      const classifier = new ToolchainFailureClassifier();
      expect(classifier).toBeDefined();
    });

    it("should allow custom configuration", () => {
      const customConfig = {
        defaultTimeoutMs: 60000,
      };
      const classifier = new ToolchainFailureClassifier(customConfig);
      expect(classifier).toBeDefined();
    });
  });

  describe("classify", () => {
    const createTestContext = (phase: ProductLifecyclePhase): ToolchainAdapterContext => ({
      productId: "test-product",
      phase,
      surface: { 
        id: "test-surface", 
        type: "backend-api",
        adapter: "gradle-java",
        path: "/path/to/project",
      },
      dryRun: false,
      surfaceConfig: {},
      phaseConfig: {},
      logger: {
        info: () => {},
        error: () => {},
        warn: () => {},
        debug: () => {},
      },
    });

    it("should classify network errors as environment category", async () => {
      const classifier = new ToolchainFailureClassifier();
      const error = new Error("ECONNREFUSED: Connection refused");
      const context = createTestContext("build" as ProductLifecyclePhase);

      const result = await classifier.classify(error, context);
      expect(result.category).toBe("environment");
      expect(result.retryable).toBe(true);
      expect(result.severity).toBe("medium");
    });

    it("should classify permission errors as environment category", async () => {
      const classifier = new ToolchainFailureClassifier();
      const error = new Error("EACCES: Permission denied");
      const context = createTestContext("build" as ProductLifecyclePhase);

      const result = await classifier.classify(error, context);
      expect(result.category).toBe("environment");
      expect(result.retryable).toBe(true);
    });

    it("should classify build errors as adapter category", async () => {
      const classifier = new ToolchainFailureClassifier();
      const error = new Error("Build failed: compilation error");
      const context = createTestContext("build" as ProductLifecyclePhase);

      const result = await classifier.classify(error, context);
      expect(result.category).toBe("adapter");
      expect(result.retryable).toBe(false);
      expect(result.severity).toBe("critical");
    });

    it("should classify test failures as adapter category", async () => {
      const classifier = new ToolchainFailureClassifier();
      const error = new Error("Test failed: assertion failed");
      const context = createTestContext("test" as ProductLifecyclePhase);

      const result = await classifier.classify(error, context);
      expect(result.category).toBe("adapter");
      expect(result.retryable).toBe(false);
      expect(result.severity).toBe("critical");
    });

    it("should classify dependency errors as dependency category", async () => {
      const classifier = new ToolchainFailureClassifier();
      const error = new Error("Cannot find module 'missing-package'");
      const context = createTestContext("build" as ProductLifecyclePhase);

      const result = await classifier.classify(error, context);
      expect(result.category).toBe("dependency");
      expect(result.retryable).toBe(false);
      expect(result.severity).toBe("high");
    });

    it("should classify config errors as config category", async () => {
      const classifier = new ToolchainFailureClassifier();
      const error = new Error("Configuration error: invalid config");
      const context = createTestContext("build" as ProductLifecyclePhase);

      const result = await classifier.classify(error, context);
      expect(result.category).toBe("config");
      expect(result.retryable).toBe(false);
      expect(result.severity).toBe("high");
    });

    it("should classify timeout errors as environment category", async () => {
      const classifier = new ToolchainFailureClassifier();
      const error = new Error("Operation timed out");
      const context = createTestContext("build" as ProductLifecyclePhase);

      const result = await classifier.classify(error, context);
      expect(result.category).toBe("environment");
      expect(result.retryable).toBe(true);
      expect(result.severity).toBe("medium");
    });

    it("should classify infrastructure errors as infrastructure category", async () => {
      const classifier = new ToolchainFailureClassifier();
      const error = new Error("ENOMEM: Out of memory");
      const context = createTestContext("build" as ProductLifecyclePhase);

      const result = await classifier.classify(error, context);
      expect(result.category).toBe("infrastructure");
      expect(result.retryable).toBe(true);
      expect(result.severity).toBe("medium");
    });

    it("should classify artifact errors as artifact category", async () => {
      const classifier = new ToolchainFailureClassifier();
      const error = new Error("Artifact not found");
      const context = createTestContext("package" as ProductLifecyclePhase);

      const result = await classifier.classify(error, context);
      expect(result.category).toBe("artifact");
      expect(result.retryable).toBe(false);
      expect(result.severity).toBe("medium");
    });

    it("should set requiresHumanIntervention for critical failures", async () => {
      const classifier = new ToolchainFailureClassifier();
      const error = new Error("Build failed: compilation error");
      const context = createTestContext("build" as ProductLifecyclePhase);

      const result = await classifier.classify(error, context);
      expect(result.requiresHumanIntervention).toBe(true);
    });

    it("should set requiresHumanIntervention for non-retryable failures", async () => {
      const classifier = new ToolchainFailureClassifier();
      const error = new Error("Configuration error: invalid config");
      const context = createTestContext("build" as ProductLifecyclePhase);

      const result = await classifier.classify(error, context);
      expect(result.requiresHumanIntervention).toBe(true);
    });

    it("should not set requiresHumanIntervention for retryable failures", async () => {
      const classifier = new ToolchainFailureClassifier();
      const error = new Error("Operation timed out");
      const context = createTestContext("build" as ProductLifecyclePhase);

      const result = await classifier.classify(error, context);
      expect(result.requiresHumanIntervention).toBe(false);
    });

    it("should provide remediation steps", async () => {
      const classifier = new ToolchainFailureClassifier();
      const error = new Error("Build failed: compilation error");
      const context = createTestContext("build" as ProductLifecyclePhase);

      const result = await classifier.classify(error, context);
      expect(result.remediationSteps?.length).toBeGreaterThan(0);
    });

    it("should extract error codes from error message", async () => {
      const classifier = new ToolchainFailureClassifier();
      const error = new Error("ECONNREFUSED: Connection refused");
      const context = createTestContext("build" as ProductLifecyclePhase);

      const result = await classifier.classify(error, context);
      expect(result.relatedFailureCodes).toContain("ECONNREFUSED");
    });

    it("should classify unknown errors as environment category (fallback)", async () => {
      const classifier = new ToolchainFailureClassifier();
      const error = new Error("Some unknown error occurred");
      const context = createTestContext("build" as ProductLifecyclePhase);

      const result = await classifier.classify(error, context);
      expect(result.category).toBe("environment");
      expect(result.retryable).toBe(true);
      expect(result.severity).toBe("medium");
    });

    it("should set critical severity for artifact errors during deploy", async () => {
      const classifier = new ToolchainFailureClassifier();
      const error = new Error("Artifact not found");
      const context = createTestContext("deploy" as ProductLifecyclePhase);

      const result = await classifier.classify(error, context);
      expect(result.category).toBe("artifact");
      expect(result.severity).toBe("critical");
    });
  });
});
