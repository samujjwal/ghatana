/**
 * Tests for enhanced ExecutionFailureHandler with LifecycleFailureClassifier integration.
 *
 * @doc.type test
 * @doc.purpose Conformance tests for enhanced failure classification in execution handler
 * @doc.layer kernel-lifecycle
 * @doc.pattern ConformanceTest
 */

import { describe, expect, it } from "vitest";
import { ExecutionFailureHandler } from "../ExecutionFailureHandler";
import type { ProductFailurePolicy } from "../../domain/ProductLifecyclePhase";
import { ConsoleExecutionLogger } from "../ExecutionLogger";

describe("ExecutionFailureHandler with LifecycleFailureClassifier", () => {
  let failureHandler: ExecutionFailureHandler;
  let logger: ConsoleExecutionLogger;

  beforeEach(() => {
    const failurePolicy: ProductFailurePolicy = {
      strategy: "fail-closed",
      retryConfig: {
        maxRetries: 3,
        backoffMs: 1000,
      },
      notifyOnFailure: true,
    };
    failureHandler = new ExecutionFailureHandler(failurePolicy);
    logger = new ConsoleExecutionLogger();
  });

  it("classifies config errors correctly", async () => {
    const error = new Error("Config validation failed: missing required field");
    const result = await failureHandler.handleFailure("step-1", error, logger);

    expect(result.action).toBe("stop");
    expect(result.classifier?.category).toBe("config");
    expect(result.classifier?.severity).toBe("high");
    expect(result.classifier?.retryable).toBe(false);
    expect(result.classifier?.requiresHumanIntervention).toBe(false);
    expect(result.classifier?.remediationSteps).toContain("Review configuration files for syntax errors");
  });

  it("classifies adapter errors correctly", async () => {
    const error = new Error("Adapter failed: gradle-java-service not found");
    const result = await failureHandler.handleFailure("adapter-build", error, logger);

    expect(result.classifier?.category).toBe("adapter");
    expect(result.classifier?.severity).toBe("high");
    expect(result.classifier?.retryable).toBe(false);
    expect(result.classifier?.component).toBe("adapter");
  });

  it("classifies command errors correctly", async () => {
    const error = new Error("Command failed: exit code 1");
    const result = await failureHandler.handleFailure("step-build", error, logger);

    expect(result.classifier?.category).toBe("command");
    expect(result.classifier?.severity).toBe("high");
    expect(result.classifier?.retryable).toBe(false);
  });

  it("classifies gate errors correctly", async () => {
    const error = new Error("Gate evaluation failed: quality gate check blocked");
    const result = await failureHandler.handleFailure("gate-approval", error, logger);

    expect(result.classifier?.category).toBe("gate");
    expect(result.classifier?.severity).toBe("high");
    expect(result.classifier?.retryable).toBe(false);
    expect(result.classifier?.requiresHumanIntervention).toBe(true);
  });

  it("classifies artifact errors correctly", async () => {
    const error = new Error("Artifact not found: /path/to/artifact.jar");
    const result = await failureHandler.handleFailure("artifact-check", error, logger);

    expect(result.classifier?.category).toBe("artifact");
    expect(result.classifier?.severity).toBe("low");
    expect(result.classifier?.retryable).toBe(false);
  });

  it("classifies dependency errors correctly", async () => {
    const error = new Error("Module not found: @ghatana/kernel-core");
    const result = await failureHandler.handleFailure("step-install", error, logger);

    expect(result.classifier?.category).toBe("dependency");
    expect(result.classifier?.severity).toBe("medium");
    expect(result.classifier?.retryable).toBe(false);
    expect(result.classifier?.knownWorkaround).toBeDefined();
    expect(result.classifier?.knownWorkaround?.description).toBe("Missing dependency");
  });

  it("classifies environment errors correctly", async () => {
    const error = new Error("Docker daemon unavailable");
    const result = await failureHandler.handleFailure("docker-build", error, logger);

    expect(result.classifier?.category).toBe("environment");
    expect(result.classifier?.severity).toBe("medium");
    expect(result.classifier?.retryable).toBe(true);
    expect(result.classifier?.knownWorkaround).toBeDefined();
    expect(result.classifier?.knownWorkaround?.description).toBe("Docker daemon unavailable");
  });

  it("classifies approval errors correctly", async () => {
    const error = new Error("Approval denied: insufficient permissions");
    const result = await failureHandler.handleFailure("approval-gate", error, logger);

    expect(result.classifier?.category).toBe("approval");
    expect(result.classifier?.severity).toBe("high");
    expect(result.classifier?.retryable).toBe(false);
    expect(result.classifier?.requiresHumanIntervention).toBe(true);
  });

  it("classifies policy errors correctly", async () => {
    const error = new Error("Policy violation: compliance check failed");
    const result = await failureHandler.handleFailure("policy-check", error, logger);

    expect(result.classifier?.category).toBe("policy");
    expect(result.classifier?.severity).toBe("critical");
    expect(result.classifier?.retryable).toBe(false);
    expect(result.classifier?.requiresHumanIntervention).toBe(true);
  });

  it("classifies security errors correctly", async () => {
    const error = new Error("Security credential expired: authentication token invalid");
    const result = await failureHandler.handleFailure("auth-check", error, logger);

    expect(result.classifier?.category).toBe("security");
    expect(result.classifier?.severity).toBe("critical");
    expect(result.classifier?.retryable).toBe(false);
    expect(result.classifier?.requiresHumanIntervention).toBe(true);
  });

  it("classifies provider errors correctly", async () => {
    const error = new Error("Provider unavailable: service not responding");
    const result = await failureHandler.handleFailure("provider-check", error, logger);

    expect(result.classifier?.category).toBe("provider");
    expect(result.classifier?.severity).toBe("medium");
    expect(result.classifier?.retryable).toBe(true);
  });

  it("classifies infrastructure errors correctly", async () => {
    const error = new Error("Infrastructure error: disk space exhausted");
    const result = await failureHandler.handleFailure("disk-check", error, logger);

    expect(result.classifier?.category).toBe("infrastructure");
    expect(result.classifier?.severity).toBe("high");
    expect(result.classifier?.retryable).toBe(true);
  });

  it("classifies unknown errors as unknown category", async () => {
    const error = new Error("Unknown issue occurred during execution");
    const result = await failureHandler.handleFailure("step-unknown", error, logger);

    expect(result.classifier?.category).toBe("unknown");
    expect(result.classifier?.severity).toBe("medium");
  });

  it("returns retry action for retryable failures in fail-closed mode", async () => {
    const error = new Error("Provider unavailable: service timeout");
    const result = await failureHandler.handleFailure("provider-check", error, logger);

    expect(result.action).toBe("retry");
    expect(result.reason).toContain("Retryable failure");
    expect(result.classifier?.retryable).toBe(true);
  });

  it("returns stop action for non-retryable failures in fail-closed mode", async () => {
    const error = new Error("Config validation failed");
    const result = await failureHandler.handleFailure("config-check", error, logger);

    expect(result.action).toBe("stop");
    expect(result.reason).toContain("Fail-closed policy");
  });

  it("returns continue action in fail-open mode", async () => {
    const failOpenHandler = new ExecutionFailureHandler({
      strategy: "fail-open",
      retryConfig: {
        maxRetries: 3,
        backoffMs: 1000,
      },
      notifyOnFailure: false,
    });

    const error = new Error("Command failed");
    const result = await failOpenHandler.handleFailure("step-1", error, logger);

    expect(result.action).toBe("continue");
    expect(result.reason).toContain("Fail-open policy");
  });

  it("returns continue action in continue-on-error mode", async () => {
    const continueErrorHandler = new ExecutionFailureHandler({
      strategy: "continue-on-error",
      retryConfig: {
        maxRetries: 3,
        backoffMs: 1000,
      },
      notifyOnFailure: false,
    });

    const error = new Error("Command failed");
    const result = await continueErrorHandler.handleFailure("step-1", error, logger);

    expect(result.action).toBe("continue");
    expect(result.reason).toContain("Continue-on-error policy");
  });

  it("provides related failure codes for grouping", async () => {
    const error = new Error("Adapter failed");
    const result = await failureHandler.handleFailure("adapter-step", error, logger);

    expect(result.classifier?.relatedFailureCodes).toContain("adapter-failed");
    expect(result.classifier?.relatedFailureCodes).toContain("adapter-missing");
  });

  it("extracts component from stepId", async () => {
    const error = new Error("Build failed");
    const result = await failureHandler.handleFailure("gradle-build-step", error, logger);

    expect(result.classifier?.component).toBe("gradle");
  });

  it("provides remediation steps for each category", async () => {
    const error = new Error("Config validation failed");
    const result = await failureHandler.handleFailure("config-check", error, logger);

    expect(result.classifier?.remediationSteps).toHaveLength(3);
    expect(result.classifier?.remediationSteps[0]).toBe("Review configuration files for syntax errors");
  });

  it("calculates retry delay with exponential backoff", () => {
    expect(failureHandler.getRetryDelay(0)).toBe(1000);
    expect(failureHandler.getRetryDelay(1)).toBe(2000);
    expect(failureHandler.getRetryDelay(2)).toBe(3000);
    expect(failureHandler.getRetryDelay(3)).toBe(3000);
  });

  it("respects max retries in delay calculation", () => {
    expect(failureHandler.getRetryDelay(10)).toBe(3000); // Capped at backoffMs * maxRetries
  });

  it("returns false for canRetry when no retry config", () => {
    const noRetryHandler = new ExecutionFailureHandler({
      strategy: "fail-closed",
      notifyOnFailure: false,
    });

    expect(noRetryHandler.canRetry(0)).toBe(false);
  });

  it("returns true for canRetry when within max retries", () => {
    expect(failureHandler.canRetry(0)).toBe(true);
    expect(failureHandler.canRetry(1)).toBe(true);
    expect(failureHandler.canRetry(2)).toBe(true);
  });

  it("returns false for canRetry when exceeding max retries", () => {
    expect(failureHandler.canRetry(3)).toBe(false);
    expect(failureHandler.canRetry(4)).toBe(false);
  });

  it("shouldNotify returns true when configured", () => {
    expect(failureHandler.shouldNotify()).toBe(true);
  });

  it("shouldNotify returns false when not configured", () => {
    const noNotifyHandler = new ExecutionFailureHandler({
      strategy: "fail-closed",
      notifyOnFailure: false,
    });

    expect(noNotifyHandler.shouldNotify()).toBe(false);
  });

  it("classifiesFailure can be called independently", () => {
    const error = new Error("Docker daemon unavailable");
    const classifier = failureHandler.classifyFailure(error, "docker-build");

    expect(classifier.category).toBe("environment");
    expect(classifier.severity).toBe("medium");
    expect(classifier.retryable).toBe(true);
    expect(classifier.requiresHumanIntervention).toBe(false);
    expect(classifier.component).toBe("docker");
    expect(classifier.remediationSteps).toBeDefined();
    expect(classifier.relatedFailureCodes).toBeDefined();
    expect(classifier.knownWorkaround).toBeDefined();
  });
});
