import * as fs from "node:fs/promises";
import * as os from "node:os";
import * as path from "node:path";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import type { LifecycleHealthSnapshot } from "@ghatana/kernel-product-contracts";
import {
  FileHealthProvider,
  type OperationalHealthSnapshot,
} from "../FileHealthProvider";

describe("FileHealthProvider", () => {
  let tempDir: string;
  let provider: FileHealthProvider;

  beforeEach(async () => {
    tempDir = await fs.mkdtemp(path.join(os.tmpdir(), "ghatana-health-"));
    provider = new FileHealthProvider({ outputDirectory: tempDir });
  });

  afterEach(async () => {
    await fs.rm(tempDir, { recursive: true, force: true });
  });

  it("writes lifecycle health snapshots and latest pointers", async () => {
    const snapshot = buildLifecycleSnapshot("run-1", "healthy");

    const result = await provider.writeLifecycleHealthSnapshot(snapshot, {
      required: true,
      correlationId: "corr-1",
      runId: "run-1",
    });

    const expectedPath = path.join(
      tempDir,
      "digital-marketing",
      "run-1",
      "lifecycle-health-snapshot.json"
    );
    expect(result).toEqual({ success: true, ref: expectedPath });
    await expect(readJson(expectedPath)).resolves.toMatchObject({
      productUnitId: "digital-marketing",
      status: "healthy",
    });
    await expect(
      readJson(
        path.join(
          tempDir,
          "digital-marketing",
          "latest",
          "lifecycle-health-snapshot.pointer.json"
        )
      )
    ).resolves.toMatchObject({
      runId: "run-1",
      status: "healthy",
      snapshotPath: expectedPath,
    });
    await expect(provider.getLatestHealthSnapshot("digital-marketing")).resolves.toEqual(
      {
        productUnitId: "digital-marketing",
        runId: "run-1",
        status: "healthy",
        snapshotPath: expectedPath,
      }
    );
  });

  it("deduplicates health snapshot refs and returns the latest product match", async () => {
    const firstRef = {
      productUnitId: "digital-marketing",
      runId: "run-1",
      status: "healthy",
      snapshotPath: "run-1.json",
    };
    const secondRef = {
      productUnitId: "digital-marketing",
      runId: "run-2",
      status: "degraded",
      snapshotPath: "run-2.json",
    };
    await provider.recordHealthSnapshot(firstRef, {
      required: true,
      correlationId: "corr-1",
    });
    await provider.recordHealthSnapshot({ ...firstRef, status: "blocked" }, {
      required: true,
      correlationId: "corr-1",
    });
    await provider.recordHealthSnapshot(secondRef, {
      required: true,
      correlationId: "corr-1",
    });

    await expect(provider.getLatestHealthSnapshot("digital-marketing")).resolves.toEqual(
      secondRef
    );
    await expect(provider.getLatestHealthSnapshot("finance")).resolves.toBeNull();
  });

  it("rejects invalid lifecycle snapshots before writing", async () => {
    const invalidSnapshot = {
      ...buildLifecycleSnapshot("run-1", "healthy"),
      status: "running",
      snapshotAt: "not-a-date",
      totalDuration: -1,
      phases: [
        {
          phase: "build",
          status: "running",
          message: "bad",
          duration: -1,
          completedAt: "not-a-date",
        },
      ],
    } as unknown as LifecycleHealthSnapshot;

    const result = await provider.writeLifecycleHealthSnapshot(invalidSnapshot, {
      required: true,
      correlationId: "corr-1",
      runId: "run-2",
    });

    expect(result.success).toBe(false);
    expect(result.error).toContain("unsupported health status running");
    expect(result.error).toContain(
      "lifecycle health snapshot runId run-1 does not match write runId run-2"
    );
    expect(result.error).toContain("requires ISO snapshotAt");
    expect(result.error).toContain("totalDuration must be non-negative");
    expect(result.error).toContain("phase build uses unsupported health status running");
    expect(result.error).toContain("phase build requires ISO completedAt");
    expect(result.error).toContain("phase build duration must be non-negative");
    await expect(fs.access(path.join(tempDir, "digital-marketing"))).rejects.toMatchObject({
      code: "ENOENT",
    });
  });

  it("rejects invalid health refs and optional missing correlation ids", async () => {
    const invalidRefResult = await provider.recordHealthSnapshot(
      {
        productUnitId: "",
        runId: "",
        status: "running",
        snapshotPath: "",
      },
      { required: true, correlationId: "corr-1" }
    );
    const missingCorrelationResult = await provider.recordHealthSnapshot(
      {
        productUnitId: "digital-marketing",
        runId: "run-1",
        status: "healthy",
        snapshotPath: "run-1.json",
      },
      { required: false, correlationId: " " }
    );

    expect(invalidRefResult.success).toBe(false);
    expect(invalidRefResult.error).toContain("requires productUnitId");
    expect(invalidRefResult.error).toContain("requires runId");
    expect(invalidRefResult.error).toContain("unsupported health status running");
    expect(invalidRefResult.error).toContain("requires snapshotPath");
    expect(missingCorrelationResult).toEqual({
      success: false,
      error: "optional health snapshot write skipped: health snapshot write requires correlationId",
    });
  });

  it("fails lifecycle writes when ref indexing fails", async () => {
    await fs.writeFile(
      path.join(tempDir, "lifecycle-health-snapshots.json"),
      JSON.stringify({ schemaVersion: "1.0.0", snapshots: {} }),
      "utf-8"
    );

    const result = await provider.writeLifecycleHealthSnapshot(
      buildLifecycleSnapshot("run-1", "healthy"),
      { required: true, correlationId: "corr-1", runId: "run-1" }
    );

    expect(result).toEqual({
      success: false,
      error: "health snapshot refs file has invalid shape",
    });
  });

  it("persists operational provider, plugin, and toolchain health snapshots", async () => {
    const providerSnapshot = buildOperationalSnapshot("provider", "registry");
    const pluginSnapshot = buildOperationalSnapshot("plugin", "approval-plugin");
    const toolchainSnapshot = buildOperationalSnapshot("toolchain", "node");

    await expect(
      provider.recordOperationalHealthSnapshot(providerSnapshot, {
        required: true,
        correlationId: "corr-1",
      })
    ).resolves.toMatchObject({
      success: true,
      ref: path.join(
        tempDir,
        "operational-health",
        "provider",
        "registry",
        "latest-health-snapshot.json"
      ),
    });
    await provider.recordOperationalHealthSnapshot(pluginSnapshot, {
      required: true,
      correlationId: "corr-1",
    });
    await provider.recordOperationalHealthSnapshot(toolchainSnapshot, {
      required: true,
      correlationId: "corr-1",
    });

    await expect(readJson(path.join(tempDir, "operational-health-snapshots.json"))).resolves.toMatchObject({
      snapshots: [
        { kind: "provider", subjectId: "registry" },
        { kind: "plugin", subjectId: "approval-plugin" },
        { kind: "toolchain", subjectId: "node" },
      ],
    });
  });

  it("rejects invalid operational health snapshots", async () => {
    const result = await provider.recordOperationalHealthSnapshot(
      {
        kind: "adapter",
        subjectId: "",
        status: "running",
        message: "",
        snapshotAt: "not-a-date",
        evidenceRefs: [],
      } as unknown as OperationalHealthSnapshot,
      { required: true, correlationId: "corr-1" }
    );

    expect(result.success).toBe(false);
    expect(result.error).toContain("unsupported operational health kind adapter");
    expect(result.error).toContain("requires subjectId");
    expect(result.error).toContain("unsupported health status running");
    expect(result.error).toContain("requires message");
    expect(result.error).toContain("requires ISO snapshotAt");
  });

  it("returns optional failure when operational health correlation id is missing", async () => {
    const result = await provider.recordOperationalHealthSnapshot(
      buildOperationalSnapshot("provider", "registry"),
      { required: false, correlationId: " " }
    );

    expect(result).toEqual({
      success: false,
      error: "optional health snapshot write skipped: health snapshot write requires correlationId",
    });
  });

  it("fails closed when operational health storage is malformed", async () => {
    await fs.writeFile(
      path.join(tempDir, "operational-health-snapshots.json"),
      JSON.stringify({ schemaVersion: "1.0.0", snapshots: {} }),
      "utf-8"
    );

    const result = await provider.recordOperationalHealthSnapshot(
      buildOperationalSnapshot("provider", "registry"),
      { required: true, correlationId: "corr-1" }
    );

    expect(result).toEqual({
      success: false,
      error: "operational health snapshots file has invalid shape",
    });
  });
});

function buildLifecycleSnapshot(
  runId: string,
  status: LifecycleHealthSnapshot["status"]
): LifecycleHealthSnapshot {
  return {
    productUnitId: "digital-marketing",
    runId,
    status,
    phases: [
      {
        phase: "build",
        status,
        message: "Build phase completed",
        duration: 12,
        completedAt: "2026-05-14T00:00:00.000Z",
      },
    ],
    currentPhase: "build",
    totalDuration: 12,
    snapshotAt: "2026-05-14T00:00:01.000Z",
  };
}

function buildOperationalSnapshot(
  kind: OperationalHealthSnapshot["kind"],
  subjectId: string
): OperationalHealthSnapshot {
  return {
    kind,
    subjectId,
    status: "healthy",
    message: "Ready",
    snapshotAt: "2026-05-14T00:00:00.000Z",
    evidenceRefs: ["health://ready"],
  };
}

async function readJson(filePath: string): Promise<unknown> {
  return JSON.parse(await fs.readFile(filePath, "utf-8"));
}
