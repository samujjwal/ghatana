import { describe, expect, it } from "vitest";
import {
  type KernelLifecycleProviderContext,
  type KernelProvider,
  type LifecycleProviderWriteOptions,
  requireLifecycleProviderSet,
  validateKernelLifecycleProviderContext,
} from "../LifecycleProviders";

function provider(providerId: string): KernelProvider {
  return {
    providerId,
    version: "1.0.0",
    capabilities: ["test"],
  };
}

function bootstrapContext(): KernelLifecycleProviderContext {
  return {
    mode: "bootstrap",
    events: provider("events") as KernelLifecycleProviderContext["events"],
    artifacts: provider("artifacts") as KernelLifecycleProviderContext["artifacts"],
    health: provider("health") as KernelLifecycleProviderContext["health"],
    approvals: provider("approvals") as KernelLifecycleProviderContext["approvals"],
    provenance: provider("provenance") as KernelLifecycleProviderContext["provenance"],
    runtimeTruth: provider("runtimeTruth") as KernelLifecycleProviderContext["runtimeTruth"],
  };
}

describe("LifecycleProviders", () => {
  it("bootstrap context passes with file-backed minimum providers", () => {
    const result = validateKernelLifecycleProviderContext(bootstrapContext());

    expect(result.valid).toBe(true);
    expect(result.missingProviders).toEqual([]);
    expect(result.mode).toBe("bootstrap");
  });

  it("platform context fails when memory provider is missing", () => {
    const context: KernelLifecycleProviderContext = {
      ...bootstrapContext(),
      mode: "platform",
    };

    const result = validateKernelLifecycleProviderContext(context);

    expect(result.valid).toBe(false);
    expect(result.missingProviders).toContain("memory");
  });

  it("platform context fails when runtimeTruth provider is missing", () => {
    const { runtimeTruth: _runtimeTruth, ...context } = {
      ...bootstrapContext(),
      mode: "platform" as const,
      memory: provider("memory") as KernelLifecycleProviderContext["memory"],
    };

    const result = validateKernelLifecycleProviderContext(context);

    expect(result.valid).toBe(false);
    expect(result.missingProviders).toContain("runtimeTruth");
  });

  it("write options preserve correlation privacy and retention metadata", () => {
    const options: LifecycleProviderWriteOptions = {
      required: true,
      correlationId: "corr-1",
      privacyClassification: "confidential",
      retention: {
        policyId: "retention-365",
        retentionDays: 365,
      },
    };

    expect(options.privacyClassification).toBe("confidential");
    expect(options.retention?.policyId).toBe("retention-365");
  });

  it("requireLifecycleProviderSet reports exact missing providers", () => {
    expect(() =>
      requireLifecycleProviderSet(
        { mode: "bootstrap", events: provider("events") as KernelLifecycleProviderContext["events"] },
        ["events", "artifacts", "health"]
      )
    ).toThrow(/artifacts, health/);
  });
});
