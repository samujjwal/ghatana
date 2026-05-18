/**
 * Tests for FileLifecycleTruthWriter — verifying all §2.4 truth output requirements.
 *
 * @doc.type test
 * @doc.purpose Verify canonical truth file writes, unregistered filename rejection, and idempotency.
 * @doc.layer kernel-lifecycle
 * @doc.pattern Unit Test
 */
import { describe, it, expect, beforeEach, afterEach } from "vitest";
import { promises as fs } from "node:fs";
import * as path from "node:path";
import * as os from "node:os";
import {
  FileLifecycleTruthWriter,
  CANONICAL_TRUTH_FILE_NAMES,
} from "../LifecycleTruthWriter.js";
import type { ProductLifecycleResult } from "../../domain/ProductLifecyclePhase.js";

function makeResult(
  overrides: Partial<ProductLifecycleResult> = {},
): ProductLifecycleResult {
  return {
    schemaVersion: "1.0.0",
    runId: "run-1",
    correlationId: "corr-abc",
    productId: "digital-marketing",
    phase: "build",
    lifecycleProfile: "standard-web-api-product",
    environment: "staging",
    requestedPhases: ["build"],
    executedPhases: ["build"],
    skippedPhases: [],
    blockedPhases: [],
    status: "succeeded",
    startedAt: "2026-01-01T00:00:00.000Z",
    completedAt: "2026-01-01T00:01:00.000Z",
    steps: [],
    gates: [],
    artifacts: [],
    outputDirectory: "/tmp/out",
    ...overrides,
  };
}

describe("FileLifecycleTruthWriter", () => {
  let tmpDir: string;
  let writer: FileLifecycleTruthWriter;

  beforeEach(async () => {
    tmpDir = await fs.mkdtemp(path.join(os.tmpdir(), "truth-writer-test-"));
    writer = new FileLifecycleTruthWriter();
  });

  afterEach(async () => {
    await fs.rm(tmpDir, { recursive: true, force: true });
  });

  // -------------------------------------------------------------------------
  // writeResult
  // -------------------------------------------------------------------------

  it("writes lifecycle-result.json with all §2.4 required fields", async () => {
    const result = makeResult();
    const writeResult = await writer.writeResult(result, tmpDir);

    expect(writeResult.status).toBe("written");
    expect(writeResult.filePath).toBe(
      path.join(tmpDir, "lifecycle-result.json"),
    );

    const raw = await fs.readFile(writeResult.filePath!, "utf-8");
    const parsed = JSON.parse(raw) as ProductLifecycleResult;

    expect(parsed.schemaVersion).toBe("1.0.0");
    expect(parsed.runId).toBe("run-1");
    expect(parsed.correlationId).toBe("corr-abc");
    expect(parsed.productId).toBe("digital-marketing");
    expect(parsed.lifecycleProfile).toBe("standard-web-api-product");
    expect(parsed.environment).toBe("staging");
    expect(parsed.requestedPhases).toEqual(["build"]);
    expect(parsed.executedPhases).toEqual(["build"]);
    expect(parsed.skippedPhases).toEqual([]);
    expect(parsed.blockedPhases).toEqual([]);
  });

  it("creates the output directory when it does not exist", async () => {
    const nestedDir = path.join(tmpDir, "nested", "deep", "output");
    const result = makeResult({ outputDirectory: nestedDir });

    const writeResult = await writer.writeResult(result, nestedDir);

    expect(writeResult.status).toBe("written");
    const stat = await fs.stat(nestedDir);
    expect(stat.isDirectory()).toBe(true);
  });

  it("is idempotent — writing the same result twice does not error", async () => {
    const result = makeResult();
    const first = await writer.writeResult(result, tmpDir);
    const second = await writer.writeResult(result, tmpDir);

    expect(first.status).toBe("written");
    expect(second.status).toBe("written");
  });

  // -------------------------------------------------------------------------
  // writeEvents
  // -------------------------------------------------------------------------

  it("writes lifecycle-events.json with the provided event array", async () => {
    const events = [
      { eventId: "e-1", eventType: "lifecycle.phase.started", phase: "build" },
      {
        eventId: "e-2",
        eventType: "lifecycle.phase.completed",
        phase: "build",
        status: "succeeded",
      },
    ];

    const writeResult = await writer.writeEvents(events, tmpDir);

    expect(writeResult.status).toBe("written");
    expect(writeResult.filePath).toBe(
      path.join(tmpDir, "lifecycle-events.json"),
    );

    const raw = await fs.readFile(writeResult.filePath!, "utf-8");
    const parsed = JSON.parse(raw) as unknown[];
    expect(parsed).toHaveLength(2);
  });

  // -------------------------------------------------------------------------
  // writeArtefact — canonical file names
  // -------------------------------------------------------------------------

  it.each(CANONICAL_TRUTH_FILE_NAMES.map((name) => [name]))(
    'writes canonical truth file "%s" without error',
    async (canonicalName) => {
      const writeResult = await writer.writeArtefact(
        { runId: "run-1" },
        tmpDir,
        canonicalName as string,
      );
      expect(writeResult.status).toBe("written");
      expect(writeResult.filePath).toBe(
        path.join(tmpDir, canonicalName as string),
      );
    },
  );

  it("rejects non-canonical truth file names with status=failed", async () => {
    const writeResult = await writer.writeArtefact(
      { data: "x" },
      tmpDir,
      "unknown-output.json",
    );

    expect(writeResult.status).toBe("failed");
    expect(writeResult.reasonCode).toBe("write-error");
    expect(writeResult.message).toContain("unknown-output.json");
    expect(writeResult.message).toContain("lifecycle-result.json");
  });

  it("returns serialization-error when content cannot be serialised", async () => {
    const circular: Record<string, unknown> = {};
    circular["self"] = circular;

    const writeResult = await writer.writeArtefact(
      circular,
      tmpDir,
      "lifecycle-result.json",
    );

    expect(writeResult.status).toBe("failed");
    expect(writeResult.reasonCode).toBe("serialization-error");
  });
});
