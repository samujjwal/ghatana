import { describe, expect, it } from "vitest";
import {
  isKernelProviderMode,
  requireLifecycleProvider,
  type GateProvider,
  type KernelLifecycleProviderContext,
  type LifecycleEventProvider,
} from "../LifecycleProviders";

const eventProvider: LifecycleEventProvider = {
  providerId: "file-events",
  version: "1.0.0",
  capabilities: ["lifecycle-events"],
  appendEvent: async () => ({ success: true, ref: "lifecycle-events.json" }),
  listEvents: async () => [],
};

const gateProvider: GateProvider = {
  providerId: "policy-gates",
  version: "1.0.0",
  capabilities: ["gates"],
  evaluateGate: async () => ({
    gateId: "security",
    passed: true,
    reason: "passed",
    evidence: ["policy:security"],
    evaluatedAt: "2026-05-14T00:00:00.000Z",
    duration: 1,
  }),
  getGateConfig: async () => null,
  listGates: async () => ["security"],
};

describe("LifecycleProviders", () => {
  it("recognizes provider modes", () => {
    expect(isKernelProviderMode("bootstrap")).toBe(true);
    expect(isKernelProviderMode("platform")).toBe(true);
    expect(isKernelProviderMode("local")).toBe(false);
  });

  it("returns a required provider when present", () => {
    const context: KernelLifecycleProviderContext = {
      mode: "bootstrap",
      events: eventProvider,
    };

    expect(requireLifecycleProvider<LifecycleEventProvider>(context, "events")).toBe(eventProvider);
  });

  it("carries gate provider registry entries", () => {
    const context: KernelLifecycleProviderContext = {
      mode: "bootstrap",
      gates: {
        security: gateProvider,
      },
    };

    expect(context.gates?.security).toBe(gateProvider);
  });

  it("fails closed when a required lifecycle provider is missing", () => {
    const context: KernelLifecycleProviderContext = {
      mode: "platform",
    };

    expect(() => requireLifecycleProvider(context, "runtimeTruth")).toThrow(
      "Kernel platform mode requires lifecycle provider: runtimeTruth"
    );
  });
});
