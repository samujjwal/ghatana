import { describe, expect, it } from "vitest";
import {
  type KernelLifecycleProviderContext,
  type LifecycleProviderWriteOptions,
  LifecycleArtifactManifestRefSchema,
  LifecycleHealthSnapshotRefSchema,
  LifecycleMemoryRecordSchema,
  LifecycleProvenanceRecordSchema,
  LifecycleRuntimeTruthSnapshotSchema,
  KernelBridgeProviderErrorSchema,
  KernelBridgeProviderHealthResultSchema,
  requireLifecycleProviderSet,
  validateKernelLifecycleProviderContext,
  validateProviderBackingForMode,
  type KernelArtifactProvider,
  type KernelEventProvider,
  type KernelHealthProvider,
  type KernelPolicyEvidenceProvider,
  type KernelProvenanceProvider,
  type KernelRuntimeTruthProvider,
  type KernelTelemetryProvider,
} from "../LifecycleProviders";
import type { KernelProvider } from "../KernelProvider.js";

function provider(
  providerId: string,
  backingStore: "file" | "data-cloud" | "external" = "file",
): KernelProvider {
  return {
    providerId,
    version: "1.0.0",
    capabilities: ["test"],
    backingStore,
  };
}

function bootstrapContext(): KernelLifecycleProviderContext {
  return {
    mode: "bootstrap",
    events: provider("events") as KernelLifecycleProviderContext["events"],
    artifacts: provider(
      "artifacts",
    ) as KernelLifecycleProviderContext["artifacts"],
    health: provider("health") as KernelLifecycleProviderContext["health"],
    approvals: provider(
      "approvals",
    ) as KernelLifecycleProviderContext["approvals"],
    provenance: provider(
      "provenance",
    ) as KernelLifecycleProviderContext["provenance"],
    memory: provider("memory") as KernelLifecycleProviderContext["memory"],
    runtimeTruth: provider(
      "runtimeTruth",
    ) as KernelLifecycleProviderContext["runtimeTruth"],
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
    const { memory: _memory, ...context } = {
      ...bootstrapContext(),
      mode: "platform" as const,
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
        {
          mode: "bootstrap",
          events: provider(
            "events",
          ) as KernelLifecycleProviderContext["events"],
        },
        ["events", "artifacts", "health"],
      ),
    ).toThrow(/artifacts, health/);
  });

  it("exports canonical provider record schemas", () => {
    expect(
      LifecycleArtifactManifestRefSchema.parse({
        productUnitId: "product-unit-1",
        runId: "run-1",
        manifestPath: ".kernel/out/run-1/artifact-manifest.json",
        artifactCount: 3,
        correlationId: "corr-1",
        digestStatus: "complete",
      }),
    ).toMatchObject({
      productUnitId: "product-unit-1",
      artifactCount: 3,
    });

    expect(
      LifecycleHealthSnapshotRefSchema.parse({
        productUnitId: "product-unit-1",
        runId: "run-1",
        status: "healthy",
        snapshotPath: ".kernel/out/run-1/health.json",
        snapshotAt: "2026-01-01T00:00:00.000Z",
        correlationId: "corr-1",
      }),
    ).toMatchObject({
      status: "healthy",
      snapshotPath: ".kernel/out/run-1/health.json",
    });

    expect(
      LifecycleProvenanceRecordSchema.parse({
        provenanceId: "prov-1",
        productUnitId: "product-unit-1",
        runId: "run-1",
        source: "kernel-lifecycle",
        evidenceRefs: ["evidence://run-1"],
        recordedAt: "2026-01-01T00:00:00.000Z",
      }),
    ).toMatchObject({
      provenanceId: "prov-1",
      source: "kernel-lifecycle",
    });

    expect(
      LifecycleMemoryRecordSchema.parse({
        memoryId: "mem-1",
        productUnitId: "product-unit-1",
        runId: "run-1",
        kind: "runtime-truth",
        contentRef: "memory://run-1",
        recordedAt: "2026-01-01T00:00:00.000Z",
      }),
    ).toMatchObject({
      memoryId: "mem-1",
      kind: "runtime-truth",
    });

    expect(
      LifecycleRuntimeTruthSnapshotSchema.parse({
        productUnitId: "product-unit-1",
        runId: "run-1",
        phase: "build",
        status: "healthy",
        observedAt: "2026-01-01T00:00:00.000Z",
        evidenceRefs: ["evidence://run-1"],
        providerMode: "platform",
      }),
    ).toMatchObject({
      phase: "build",
      providerMode: "platform",
    });
  });

  it("defines bridge-facing provider aliases and extension providers", () => {
    const events = provider("events", "data-cloud") as KernelEventProvider;
    const artifacts = provider(
      "artifacts",
      "data-cloud",
    ) as KernelArtifactProvider;
    const health = provider("health", "data-cloud") as KernelHealthProvider;
    const provenance = provider(
      "provenance",
      "data-cloud",
    ) as KernelProvenanceProvider;
    const runtimeTruth = provider(
      "runtimeTruth",
      "data-cloud",
    ) as KernelRuntimeTruthProvider;
    const telemetry = {
      ...provider("telemetry", "data-cloud"),
      recordTelemetry: async () => ({
        success: true,
        ref: "telemetry://event-1",
      }),
    } satisfies KernelTelemetryProvider;
    const policyEvidence = {
      ...provider("policyEvidence", "data-cloud"),
      recordPolicyEvidence: async () => ({
        success: true,
        ref: "policy://evidence-1",
      }),
    } satisfies KernelPolicyEvidenceProvider;

    expect(events.backingStore).toBe("data-cloud");
    expect(artifacts.backingStore).toBe("data-cloud");
    expect(health.backingStore).toBe("data-cloud");
    expect(provenance.backingStore).toBe("data-cloud");
    expect(runtimeTruth.backingStore).toBe("data-cloud");
    expect(telemetry.providerId).toBe("telemetry");
    expect(policyEvidence.providerId).toBe("policyEvidence");
  });

  it("validates provider health and typed bridge errors", () => {
    expect(
      KernelBridgeProviderHealthResultSchema.parse({
        providerId: "data-cloud-events",
        mode: "platform",
        status: "degraded",
        reason: "latency threshold exceeded",
        latencyMs: 1200,
        lastSuccessAt: "2026-01-01T00:00:00.000Z",
        evidenceRefs: ["datacloud://health/events"],
      }),
    ).toMatchObject({
      providerId: "data-cloud-events",
      status: "degraded",
    });

    expect(
      KernelBridgeProviderErrorSchema.parse({
        code: "tenant-isolation",
        providerId: "data-cloud-events",
        message: "Tenant context is missing",
        retryable: false,
        correlationId: "corr-1",
      }),
    ).toMatchObject({
      code: "tenant-isolation",
      retryable: false,
    });

    expect(() =>
      KernelBridgeProviderErrorSchema.parse({
        code: "unknown",
        providerId: "data-cloud-events",
        message: "Bad code",
        retryable: false,
      }),
    ).toThrow();
  });

  describe("validateProviderBackingForMode", () => {
    it("bootstrap mode allows file-backed providers", () => {
      const context = bootstrapContext();
      const result = validateProviderBackingForMode(context);

      expect(result.valid).toBe(true);
      expect(result.invalidBackingStores).toEqual([]);
    });

    it("platform mode rejects file-backed providers", () => {
      const context: KernelLifecycleProviderContext = {
        ...bootstrapContext(),
        mode: "platform",
        events: provider(
          "events",
          "file",
        ) as KernelLifecycleProviderContext["events"],
        artifacts: provider(
          "artifacts",
          "file",
        ) as KernelLifecycleProviderContext["artifacts"],
      };

      const result = validateProviderBackingForMode(context);

      expect(result.valid).toBe(false);
      expect(result.invalidBackingStores.length).toBeGreaterThan(0);
      expect(result.invalidBackingStores[0].providerName).toBe("events");
      expect(result.invalidBackingStores[0].backingStore).toBe("file");
    });

    it("platform mode allows data-cloud-backed providers", () => {
      const context: KernelLifecycleProviderContext = {
        mode: "platform",
        events: provider(
          "events",
          "data-cloud",
        ) as KernelLifecycleProviderContext["events"],
        artifacts: provider(
          "artifacts",
          "data-cloud",
        ) as KernelLifecycleProviderContext["artifacts"],
        health: provider(
          "health",
          "data-cloud",
        ) as KernelLifecycleProviderContext["health"],
        approvals: provider(
          "approvals",
          "data-cloud",
        ) as KernelLifecycleProviderContext["approvals"],
        provenance: provider(
          "provenance",
          "data-cloud",
        ) as KernelLifecycleProviderContext["provenance"],
        memory: provider(
          "memory",
          "data-cloud",
        ) as KernelLifecycleProviderContext["memory"],
        runtimeTruth: provider(
          "runtimeTruth",
          "data-cloud",
        ) as KernelLifecycleProviderContext["runtimeTruth"],
      };

      const result = validateProviderBackingForMode(context);

      expect(result.valid).toBe(true);
      expect(result.invalidBackingStores).toEqual([]);
    });

    it("platform mode allows external-backed providers", () => {
      const context: KernelLifecycleProviderContext = {
        mode: "platform",
        events: provider(
          "events",
          "external",
        ) as KernelLifecycleProviderContext["events"],
        artifacts: provider(
          "artifacts",
          "external",
        ) as KernelLifecycleProviderContext["artifacts"],
        health: provider(
          "health",
          "external",
        ) as KernelLifecycleProviderContext["health"],
        approvals: provider(
          "approvals",
          "external",
        ) as KernelLifecycleProviderContext["approvals"],
        provenance: provider(
          "provenance",
          "external",
        ) as KernelLifecycleProviderContext["provenance"],
        memory: provider(
          "memory",
          "external",
        ) as KernelLifecycleProviderContext["memory"],
        runtimeTruth: provider(
          "runtimeTruth",
          "external",
        ) as KernelLifecycleProviderContext["runtimeTruth"],
      };

      const result = validateProviderBackingForMode(context);

      expect(result.valid).toBe(true);
      expect(result.invalidBackingStores).toEqual([]);
    });

    it("platform mode reports all file-backed providers", () => {
      const context: KernelLifecycleProviderContext = {
        mode: "platform",
        events: provider(
          "events",
          "file",
        ) as KernelLifecycleProviderContext["events"],
        artifacts: provider(
          "artifacts",
          "file",
        ) as KernelLifecycleProviderContext["artifacts"],
        health: provider(
          "health",
          "file",
        ) as KernelLifecycleProviderContext["health"],
        approvals: provider(
          "approvals",
          "file",
        ) as KernelLifecycleProviderContext["approvals"],
        provenance: provider(
          "provenance",
          "file",
        ) as KernelLifecycleProviderContext["provenance"],
        memory: provider(
          "memory",
          "file",
        ) as KernelLifecycleProviderContext["memory"],
        runtimeTruth: provider(
          "runtimeTruth",
          "file",
        ) as KernelLifecycleProviderContext["runtimeTruth"],
      };

      const result = validateProviderBackingForMode(context);

      expect(result.valid).toBe(false);
      expect(result.invalidBackingStores.length).toBe(7);
      expect(result.reasonCodes).toContain("invalid-backing-store");
    });
  });

  describe("validateKernelLifecycleProviderContext with backing store validation", () => {
    it("platform context fails with file-backed providers", () => {
      const context: KernelLifecycleProviderContext = {
        mode: "platform",
        events: provider(
          "events",
          "file",
        ) as KernelLifecycleProviderContext["events"],
        artifacts: provider(
          "artifacts",
          "file",
        ) as KernelLifecycleProviderContext["artifacts"],
        health: provider(
          "health",
          "file",
        ) as KernelLifecycleProviderContext["health"],
        approvals: provider(
          "approvals",
          "file",
        ) as KernelLifecycleProviderContext["approvals"],
        provenance: provider(
          "provenance",
          "file",
        ) as KernelLifecycleProviderContext["provenance"],
        memory: provider(
          "memory",
          "file",
        ) as KernelLifecycleProviderContext["memory"],
        runtimeTruth: provider(
          "runtimeTruth",
          "file",
        ) as KernelLifecycleProviderContext["runtimeTruth"],
      };

      const result = validateKernelLifecycleProviderContext(context);

      expect(result.valid).toBe(false);
      expect(result.invalidBackingStores.length).toBeGreaterThan(0);
      expect(result.reasonCodes).toContain("invalid-backing-store");
    });

    it("platform context passes with data-cloud-backed providers", () => {
      const context: KernelLifecycleProviderContext = {
        mode: "platform",
        events: provider(
          "events",
          "data-cloud",
        ) as KernelLifecycleProviderContext["events"],
        artifacts: provider(
          "artifacts",
          "data-cloud",
        ) as KernelLifecycleProviderContext["artifacts"],
        health: provider(
          "health",
          "data-cloud",
        ) as KernelLifecycleProviderContext["health"],
        approvals: provider(
          "approvals",
          "data-cloud",
        ) as KernelLifecycleProviderContext["approvals"],
        provenance: provider(
          "provenance",
          "data-cloud",
        ) as KernelLifecycleProviderContext["provenance"],
        memory: provider(
          "memory",
          "data-cloud",
        ) as KernelLifecycleProviderContext["memory"],
        runtimeTruth: provider(
          "runtimeTruth",
          "data-cloud",
        ) as KernelLifecycleProviderContext["runtimeTruth"],
      };

      const result = validateKernelLifecycleProviderContext(context);

      expect(result.valid).toBe(true);
      expect(result.invalidBackingStores).toEqual([]);
    });
  });
});
