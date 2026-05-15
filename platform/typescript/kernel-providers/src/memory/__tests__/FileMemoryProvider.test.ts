import * as fs from "node:fs/promises";
import * as os from "node:os";
import * as path from "node:path";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import type { LifecycleMemoryRecord } from "@ghatana/kernel-product-contracts";
import { FileMemoryProvider } from "../FileMemoryProvider.js";

describe("FileMemoryProvider", () => {
  let outputDirectory: string;

  beforeEach(async () => {
    outputDirectory = await fs.mkdtemp(path.join(os.tmpdir(), "ghatana-file-memory-"));
  });

  afterEach(async () => {
    await fs.rm(outputDirectory, { recursive: true, force: true });
  });

  it("records and lists lifecycle memory records by product and run", async () => {
    const provider = new FileMemoryProvider({ outputDirectory });
    const record = createMemoryRecord();

    await expect(
      provider.recordMemory(record, {
        required: true,
        correlationId: "corr-1",
        privacyClassification: "internal",
        retention: { policyId: "kernel-memory", retentionDays: 30 },
      })
    ).resolves.toEqual({
      success: true,
      ref: path.join(outputDirectory, "memory-records.json"),
    });

    await expect(provider.listMemory({ productUnitId: "digital-marketing" })).resolves.toEqual([
      record,
    ]);
    await expect(
      provider.listMemory({ productUnitId: "digital-marketing", runId: "run-1" })
    ).resolves.toEqual([record]);
    await expect(
      provider.listMemory({ productUnitId: "digital-marketing", runId: "run-2" })
    ).resolves.toEqual([]);
  });

  it("writes latest memory record per ProductUnit", async () => {
    const provider = new FileMemoryProvider({ outputDirectory });
    await provider.recordMemory(createMemoryRecord(), {
      required: true,
      correlationId: "corr-1",
    });

    await expect(
      fs.readFile(
        path.join(outputDirectory, "digital-marketing", "latest", "memory-record.json"),
        "utf-8"
      )
    ).resolves.toContain('"memoryId": "memory-1"');
  });

  it("fails required writes on invalid records", async () => {
    const provider = new FileMemoryProvider({ outputDirectory });

    await expect(
      provider.recordMemory(
        { ...createMemoryRecord(), contentRef: "" },
        { required: true, correlationId: "corr-1" }
      )
    ).resolves.toMatchObject({
      success: false,
      error: "memory record requires contentRef",
    });
  });

  it("preserves privacy and retention metadata on stored records", async () => {
    const provider = new FileMemoryProvider({ outputDirectory });
    const record: LifecycleMemoryRecord = {
      ...createMemoryRecord(),
      privacyClassification: "confidential",
      retention: {
        policyId: "product-lifecycle-memory",
        retentionDays: 90,
        expiresAt: "2026-08-12T00:00:00.000Z",
      },
    };

    await provider.recordMemory(record, { required: true, correlationId: "corr-1" });

    await expect(provider.listMemory({ productUnitId: "digital-marketing" })).resolves.toEqual([
      record,
    ]);
  });
});

function createMemoryRecord(): LifecycleMemoryRecord {
  return {
    memoryId: "memory-1",
    productUnitId: "digital-marketing",
    runId: "run-1",
    kind: "lifecycle-run-summary",
    contentRef: "lifecycle-result:run-1",
    recordedAt: "2026-05-14T00:00:00.000Z",
  };
}
