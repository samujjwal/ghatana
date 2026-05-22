/**
 * Tests for LifecycleRecoveryGuidance
 *
 * @doc.type test
 * @doc.purpose Test recovery guidance functionality
 * @doc.layer kernel-lifecycle
 */

import { describe, it, expect } from "vitest";
import {
  getRecoveryGuidance,
  inferFailureCategory,
  formatRecoveryGuidance,
  type LifecycleFailureCategory,
} from "../LifecycleRecoveryGuidance";

describe("LifecycleRecoveryGuidance", () => {
  describe("getRecoveryGuidance", () => {
    it("returns guidance for known failure categories", () => {
      const guidance = getRecoveryGuidance("adapter-toolchain-missing");
      expect(guidance.failureCategory).toBe("adapter-toolchain-missing");
      expect(guidance.title).toBe("Required toolchain is not installed");
      expect(guidance.actions.length).toBeGreaterThan(0);
    });

    it("returns unknown guidance for unknown category", () => {
      const guidance = getRecoveryGuidance("unknown" as LifecycleFailureCategory);
      expect(guidance.failureCategory).toBe("unknown");
      expect(guidance.title).toBe("Unknown failure type");
      expect(guidance.requiresIntervention).toBe(true);
    });
  });

  describe("inferFailureCategory", () => {
    it("infers toolchain-missing from error message", () => {
      const category = inferFailureCategory("cargo: command not found");
      expect(category).toBe("adapter-toolchain-missing");
    });

    it("infers toolchain-version-mismatch from error message", () {
      const category = inferFailureCategory("Java version 17 required but 21 is installed");
      expect(category).toBe("adapter-toolchain-version-mismatch");
    });

    it("infers dependency-error from error message", () => {
      const category = inferFailureCategory("npm install failed: dependency not found");
      expect(category).toBe("adapter-dependency-error");
    });

    it("infers configuration-error from error message", () => {
      const category = inferFailureCategory("Invalid configuration in kernel-product.yaml");
      expect(category).toBe("adapter-configuration-error");
    });

    it("infers timeout from error message", () => {
      const category = inferFailureCategory("Operation timed out after 30000ms");
      expect(category).toBe("adapter-timeout");
    });

    it("infers authentication-failed from error message", () => {
      const category = inferFailureCategory("Authentication failed: invalid token");
      expect(category).toBe("policy-authentication-failed");
    });

    it("infers authentication-failed from 401 status code", () => {
      const category = inferFailureCategory("Unauthorized", 401);
      expect(category).toBe("policy-authentication-failed");
    });

    it("infers tenant-isolation-violation from error message", () => {
      const category = inferFailureCategory("Tenant isolation violation: cross-tenant access denied");
      expect(category).toBe("policy-tenant-isolation-violation");
    });

    it("infers purpose-of-use-violation from error message", () {
      const category = inferFailureCategory("Purpose of use not allowed for this operation");
      expect(category).toBe("policy-purpose-of-use-violation");
    });

    it("infers consent-verification-failed from error message", () => {
      const category = inferFailureCategory("Consent verification failed: consent revoked");
      expect(category).toBe("policy-consent-verification-failed");
    });

    it("infers handler-not-found from error message", () => {
      const category = inferFailureCategory("Handler not found for contract: kernel.consent-status.v1");
      expect(category).toBe("interaction-handler-not-found");
    });

    it("infers provider-unavailable from error message", () => {
      const category = inferFailureCategory("Data Cloud provider unavailable");
      expect(category).toBe("interaction-provider-unavailable");
    });

    it("infers environment-blocked from error message", () => {
      const category = inferFailureCategory("Docker environment blocked: daemon not running");
      expect(category).toBe("environment-blocked");
    });

    it("infers test-failure from error message", () => {
      const category = inferFailureCategory("Tests failed: 3 of 100 tests failed");
      expect(category).toBe("test-failure");
    });

    it("infers gate-failure from error message", () => {
      const category = inferFailureCategory("Coverage gate failed: 45% coverage below 80% threshold");
      expect(category).toBe("gate-failure");
    });

    it("infers artifact-validation-failure from error message", () => {
      const category = inferFailureCategory("Artifact validation failed: checksum mismatch");
      expect(category).toBe("artifact-validation-failure");
    });

    it("returns unknown for unrecognized error messages", () => {
      const category = inferFailureCategory("Some unknown error occurred");
      expect(category).toBe("unknown");
    });
  });

  describe("formatRecoveryGuidance", () => {
    it("formats guidance as human-readable summary", () => {
      const guidance = getRecoveryGuidance("adapter-toolchain-missing");
      const formatted = formatRecoveryGuidance(guidance);

      expect(formatted).toContain("## Required toolchain is not installed");
      expect(formatted).toContain("### Recovery Actions:");
      expect(formatted).toContain("**Estimated Complexity:**");
    });

    it("includes intervention warning when required", () => {
      const guidance = getRecoveryGuidance("policy-tenant-isolation-violation");
      const formatted = formatRecoveryGuidance(guidance);

      expect(formatted).toContain("**⚠️ Requires manual intervention or approval**");
    });

    it("does not include intervention warning when not required", () => {
      const guidance = getRecoveryGuidance("adapter-toolchain-missing");
      const formatted = formatRecoveryGuidance(guidance);

      expect(formatted).not.toContain("**⚠️ Requires manual intervention or approval**");
    });
  });
});
