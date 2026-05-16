import * as os from "node:os";
import * as path from "node:path";
import { describe, expect, it } from "vitest";
import {
  FileApprovalProvider,
  FileArtifactProvider,
  FileBootstrapGateProvider,
  FileHealthProvider,
  FileLifecycleEventProvider,
  FileMemoryProvider,
  FileProvenanceProvider,
  FileRuntimeTruthProvider,
  GhatanaFileRegistryProvider,
  createBootstrapKernelProviders,
} from "../..";

describe("createBootstrapKernelProviders", () => {
  it("creates a typed bootstrap lifecycle provider context under .kernel/out", () => {
    const repoRoot = path.join(os.tmpdir(), "ghatana-repo");

    const providers = createBootstrapKernelProviders({ repoRoot });

    expect(providers.repoRoot).toBe(path.resolve(repoRoot));
    expect(providers.outputRoot).toBe(path.join(path.resolve(repoRoot), ".kernel", "out"));
    expect(providers.events).toBeInstanceOf(FileLifecycleEventProvider);
    expect(providers.artifacts).toBeInstanceOf(FileArtifactProvider);
    expect(providers.health).toBeInstanceOf(FileHealthProvider);
    expect(providers.approvals).toBeInstanceOf(FileApprovalProvider);
    expect(providers.gates["registry-validation"]).toBeInstanceOf(FileBootstrapGateProvider);
    expect(providers.provenance).toBeInstanceOf(FileProvenanceProvider);
    expect(providers.memory).toBeInstanceOf(FileMemoryProvider);
    expect(providers.runtimeTruth).toBeInstanceOf(FileRuntimeTruthProvider);
    expect(providers.context).toMatchObject({
      mode: "bootstrap",
      events: providers.events,
      artifacts: providers.artifacts,
      health: providers.health,
      approvals: providers.approvals,
      gates: providers.gates,
      provenance: providers.provenance,
      memory: providers.memory,
      runtimeTruth: providers.runtimeTruth,
    });
  });

  it("allows custom output roots inside repo .kernel/out", () => {
    const repoRoot = path.join(os.tmpdir(), "ghatana-repo");

    const providers = createBootstrapKernelProviders({
      repoRoot,
      outputRoot: path.join(".kernel", "out", "runs", "run-1"),
    });

    expect(providers.outputRoot).toBe(
      path.join(path.resolve(repoRoot), ".kernel", "out", "runs", "run-1")
    );
  });

  it("passes bootstrap scope to scoped providers", async () => {
    const repoRoot = path.join(os.tmpdir(), "ghatana-repo");
    const providers = createBootstrapKernelProviders({
      repoRoot,
      scope: {
        tenantId: "tenant-a",
        workspaceId: "workspace-a",
        projectId: "project-a",
      },
    });

    const result = await providers.events.appendEvent(
      {
        metadata: {
          eventId: "event-1",
          schemaVersion: "1.0.0",
          eventType: "lifecycle.manifest.written",
          productUnitId: "digital-marketing",
          runId: "run-1",
          phase: "build",
          timestamp: "2026-05-14T00:00:00.000Z",
          source: "kernel",
          correlationId: "corr-1",
        },
        payload: {
          manifestType: "lifecycle-events",
          path: ".kernel/out/lifecycle-events.json",
          required: true,
          status: "written",
        },
      },
      { required: true, correlationId: "corr-1" }
    );

    expect(result).toEqual({
      success: true,
      ref: `${path.join(
        "tenant-a",
        "workspace-a",
        "project-a",
        "events",
        "lifecycle-events.json"
      )}?correlationId=corr-1&providerId=file-lifecycle-events`,
    });
  });

  it("creates file registry provider when requested", () => {
    const repoRoot = path.join(os.tmpdir(), "ghatana-repo");

    const providers = createBootstrapKernelProviders({
      repoRoot,
      includeRegistryProvider: true,
    });

    expect(providers.registryProvider).toBeInstanceOf(GhatanaFileRegistryProvider);
    expect(providers.context.registryProvider).toBe(providers.registryProvider);
  });

  it("rejects custom output roots outside repo .kernel/out by default", () => {
    const repoRoot = path.join(os.tmpdir(), "ghatana-repo");

    expect(() =>
      createBootstrapKernelProviders({
        repoRoot,
        outputRoot: path.join("tmp", "kernel-output"),
      })
    ).toThrow("Bootstrap Kernel outputRoot must be inside");
  });

  it("allows custom output roots outside repo .kernel/out only when explicitly overridden", () => {
    const repoRoot = path.join(os.tmpdir(), "ghatana-repo");
    const outputRoot = path.join(os.tmpdir(), "external-kernel-output");

    const providers = createBootstrapKernelProviders({
      repoRoot,
      outputRoot,
      allowOutputOutsideKernelOut: true,
    });

    expect(providers.outputRoot).toBe(path.resolve(repoRoot, outputRoot));
  });

  it("rejects sibling paths that merely share the .kernel/out prefix", () => {
    const repoRoot = path.join(os.tmpdir(), "ghatana-repo");

    expect(() =>
      createBootstrapKernelProviders({
        repoRoot,
        outputRoot: path.join(".kernel", "out-sibling"),
      })
    ).toThrow("Bootstrap Kernel outputRoot must be inside");
  });
});
