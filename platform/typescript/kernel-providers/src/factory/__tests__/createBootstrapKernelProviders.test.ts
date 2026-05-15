import * as os from "node:os";
import * as path from "node:path";
import { describe, expect, it } from "vitest";
import {
  FileApprovalProvider,
  FileArtifactProvider,
  FileHealthProvider,
  FileLifecycleEventProvider,
  FileProvenanceProvider,
  FileRuntimeTruthProvider,
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
    expect(providers.provenance).toBeInstanceOf(FileProvenanceProvider);
    expect(providers.runtimeTruth).toBeInstanceOf(FileRuntimeTruthProvider);
    expect(providers.context).toMatchObject({
      mode: "bootstrap",
      events: providers.events,
      artifacts: providers.artifacts,
      health: providers.health,
      approvals: providers.approvals,
      provenance: providers.provenance,
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
