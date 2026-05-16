import * as fs from "node:fs/promises";
import * as os from "node:os";
import * as path from "node:path";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import type { LifecycleRuntimeTruthSnapshot } from "@ghatana/kernel-product-contracts";
import { FileRuntimeTruthProvider } from "../FileRuntimeTruthProvider";

describe("FileRuntimeTruthProvider", () => {
  let tempDir: string;
  let provider: FileRuntimeTruthProvider;

  beforeEach(async () => {
    tempDir = await fs.mkdtemp(path.join(os.tmpdir(), "ghatana-runtime-truth-"));
    provider = new FileRuntimeTruthProvider({ outputDirectory: tempDir });
  });

  afterEach(async () => {
    await fs.rm(tempDir, { recursive: true, force: true });
  });

  it("records runtime truth, replaces product-run-phase snapshots, and exposes latest", async () => {
    const first = buildSnapshot("run-1", "build", "healthy");
    const replacement = { ...first, status: "degraded" };
    const second = buildSnapshot("run-2", "verify", "healthy");
    const otherProduct = { ...buildSnapshot("run-1", "build", "healthy"), productUnitId: "finance" };

    await provider.recordRuntimeTruth(first, { required: true, correlationId: "corr-1" });
    await provider.recordRuntimeTruth(replacement, {
      required: true,
      correlationId: "corr-1",
    });
    await provider.recordRuntimeTruth(second, { required: true, correlationId: "corr-1" });
    await provider.recordRuntimeTruth(otherProduct, {
      required: true,
      correlationId: "corr-1",
    });

    await expect(provider.getRuntimeTruth("digital-marketing")).resolves.toEqual(
      expect.objectContaining({ ...second, correlationId: "corr-1" })
    );
    await expect(provider.getRuntimeTruth("finance")).resolves.toEqual(
      expect.objectContaining({ ...otherProduct, correlationId: "corr-1" })
    );
    await expect(provider.getRuntimeTruth("missing")).resolves.toBeNull();
    await expect(
      readJson(
        path.join(
          tempDir,
          "digital-marketing",
          "latest",
          "runtime-truth-snapshot.json"
        )
      )
    ).resolves.toMatchObject({
      runId: "run-2",
      phase: "verify",
      status: "healthy",
    });
  });

  it("uses observedAt to select latest runtime truth and preserves write metadata", async () => {
    const newer = {
      ...buildSnapshot("run-2", "verify", "blocked"),
      observedAt: "2026-05-14T00:00:05.000Z",
    };
    const olderWrittenLater = {
      ...buildSnapshot("run-1", "build", "healthy"),
      observedAt: "2026-05-14T00:00:01.000Z",
    };

    await provider.recordRuntimeTruth(newer, {
      required: true,
      correlationId: "corr-newer",
      privacyClassification: "restricted",
      retention: { policyId: "runtime-truth", retentionDays: 90 },
    });
    await provider.recordRuntimeTruth(olderWrittenLater, {
      required: true,
      correlationId: "corr-older",
    });

    await expect(provider.getRuntimeTruth("digital-marketing")).resolves.toEqual(
      expect.objectContaining({
        runId: "run-2",
        status: "blocked",
        correlationId: "corr-newer",
        privacyClassification: "restricted",
        retention: { policyId: "runtime-truth", retentionDays: 90 },
      })
    );
    await expect(
      readJson(
        path.join(
          tempDir,
          "digital-marketing",
          "latest",
          "runtime-truth-snapshot.json"
        )
      )
    ).resolves.toMatchObject({
      runId: "run-2",
      status: "blocked",
      correlationId: "corr-newer",
    });
  });

  it("rejects invalid runtime truth snapshots before writing", async () => {
    const result = await provider.recordRuntimeTruth(
      {
        productUnitId: "",
        runId: "",
        phase: "",
        status: "",
        observedAt: "not-a-date",
        evidenceRefs: [" "],
      } as LifecycleRuntimeTruthSnapshot,
      { required: true, correlationId: "corr-1" }
    );

    expect(result.success).toBe(false);
    expect(result.error).toContain("requires productUnitId");
    expect(result.error).toContain("requires runId");
    expect(result.error).toContain("requires phase");
    expect(result.error).toContain("requires status");
    expect(result.error).toContain("requires ISO observedAt");
    expect(result.error).toContain("evidence refs must be non-empty");
    await expect(
      fs.access(path.join(tempDir, "runtime-truth-snapshots.json"))
    ).rejects.toMatchObject({ code: "ENOENT" });
  });

  it("accepts lifecycle execution statuses used by KernelLifecycleService", async () => {
    const result = await provider.recordRuntimeTruth(
      buildSnapshot("run-1", "build", "approval-required"),
      { required: true, correlationId: "corr-1" }
    );

    expect(result.success).toBe(true);
    await expect(provider.getRuntimeTruth("digital-marketing")).resolves.toEqual(
      expect.objectContaining({ status: "approval-required" })
    );
  });

  it("rejects unsupported runtime truth statuses", async () => {
    const result = await provider.recordRuntimeTruth(
      buildSnapshot("run-1", "build", "definitely-invalid-status"),
      { required: true, correlationId: "corr-1" }
    );

    expect(result).toEqual({
      success: false,
      error: "unsupported runtime truth status definitely-invalid-status",
    });
  });

  it("returns optional failure when correlation id is missing", async () => {
    const result = await provider.recordRuntimeTruth(
      buildSnapshot("run-1", "build", "healthy"),
      { required: false, correlationId: " " }
    );

    expect(result).toEqual({
      success: false,
      error: "optional runtime truth write skipped: runtime truth write requires correlationId",
    });
  });

  it("fails closed when stored runtime truth is malformed", async () => {
    await fs.writeFile(
      path.join(tempDir, "runtime-truth-snapshots.json"),
      JSON.stringify({ schemaVersion: "1.0.0", snapshots: {} }),
      "utf-8"
    );

    const result = await provider.recordRuntimeTruth(
      buildSnapshot("run-1", "build", "healthy"),
      { required: true, correlationId: "corr-1" }
    );

    expect(result).toEqual({
      success: false,
      error: "runtime truth snapshots file has invalid shape",
    });
  });
});

function buildSnapshot(
  runId: string,
  phase: LifecycleRuntimeTruthSnapshot["phase"],
  status: string
): LifecycleRuntimeTruthSnapshot {
  return {
    productUnitId: "digital-marketing",
    runId,
    phase,
    status,
    observedAt: "2026-05-14T00:00:00.000Z",
    evidenceRefs: ["runtime://truth"],
  };
}

async function readJson(filePath: string): Promise<unknown> {
  return JSON.parse(await fs.readFile(filePath, "utf-8"));
}
