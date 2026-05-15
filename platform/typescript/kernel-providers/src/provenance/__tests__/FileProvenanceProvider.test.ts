import * as fs from "node:fs/promises";
import * as os from "node:os";
import * as path from "node:path";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import type { LifecycleProvenanceRecord } from "@ghatana/kernel-product-contracts";
import { FileProvenanceProvider } from "../FileProvenanceProvider";

describe("FileProvenanceProvider", () => {
  let tempDir: string;
  let provider: FileProvenanceProvider;

  beforeEach(async () => {
    tempDir = await fs.mkdtemp(path.join(os.tmpdir(), "ghatana-provenance-"));
    provider = new FileProvenanceProvider({ outputDirectory: tempDir });
  });

  afterEach(async () => {
    await fs.rm(tempDir, { recursive: true, force: true });
  });

  it("records, deduplicates, and lists provenance by product and run", async () => {
    const first = buildRecord("prov-1", "run-1");
    const replacement = { ...first, source: "replacement" };
    const second = buildRecord("prov-2", "run-2");
    const third = { ...buildRecord("prov-3", "run-1"), productUnitId: "finance" };

    await provider.recordProvenance(first, { required: true, correlationId: "corr-1" });
    await provider.recordProvenance(replacement, {
      required: true,
      correlationId: "corr-1",
    });
    await provider.recordProvenance(second, { required: true, correlationId: "corr-1" });
    await provider.recordProvenance(third, { required: true, correlationId: "corr-1" });

    await expect(
      provider.listProvenance({ productUnitId: "digital-marketing", runId: "run-1" })
    ).resolves.toEqual([expect.objectContaining({ ...replacement, correlationId: "corr-1" })]);
    expect(replacement.privacyClassification).toBe("confidential");
    expect(replacement.retention?.policyId).toBe("marketing-evidence-365");
    await expect(
      provider.listProvenance({ productUnitId: "digital-marketing", runId: "run-x" })
    ).resolves.toEqual([]);
    await expect(
      provider.listProvenance({ productUnitId: "finance" })
    ).resolves.toEqual([expect.objectContaining({ ...third, correlationId: "corr-1" })]);
    await expect(
      readJson(
        path.join(
          tempDir,
          "products",
          "digital-marketing",
          "runs",
          "run-1",
          "provenance-records.json"
        )
      )
    ).resolves.toMatchObject({
      records: [{ provenanceId: "prov-1", source: "replacement" }],
    });
  });

  it("lists provenance by correlation id and applies write metadata", async () => {
    await provider.recordProvenance(
      buildRecordWithoutMetadata("prov-no-metadata", "run-1"),
      {
        required: true,
        correlationId: "corr-target",
        privacyClassification: "restricted",
        retention: { policyId: "runtime-evidence", retentionDays: 90 },
      }
    );
    await provider.recordProvenance(buildRecord("prov-other", "run-1"), {
      required: true,
      correlationId: "corr-other",
    });

    await expect(
      provider.listProvenance({
        productUnitId: "digital-marketing",
        runId: "run-1",
        correlationId: "corr-target",
      })
    ).resolves.toEqual([
      expect.objectContaining({
        provenanceId: "prov-no-metadata",
        correlationId: "corr-target",
        privacyClassification: "restricted",
        retention: { policyId: "runtime-evidence", retentionDays: 90 },
      }),
    ]);
  });

  it("rejects invalid privacy and retention metadata before writing", async () => {
    const result = await provider.recordProvenance(
      {
        ...buildRecord("prov-privacy", "run-1"),
        privacyClassification: "private",
        retention: {
          policyId: "",
          retentionDays: -1,
          expiresAt: "not-a-date",
        },
      } as LifecycleProvenanceRecord,
      { required: true, correlationId: "corr-1" }
    );

    expect(result.success).toBe(false);
    expect(result.error).toContain("privacyClassification is invalid");
    expect(result.error).toContain("retention requires policyId");
    expect(result.error).toContain("retentionDays must be non-negative");
    expect(result.error).toContain("retention expiresAt must be ISO timestamp");
  });

  it("rejects invalid provenance records before writing", async () => {
    const result = await provider.recordProvenance(
      {
        provenanceId: "",
        productUnitId: "",
        runId: "",
        source: "",
        evidenceRefs: [" "],
        recordedAt: "not-a-date",
      },
      { required: true, correlationId: "corr-1" }
    );

    expect(result.success).toBe(false);
    expect(result.error).toContain("requires provenanceId");
    expect(result.error).toContain("requires productUnitId");
    expect(result.error).toContain("requires runId");
    expect(result.error).toContain("requires source");
    expect(result.error).toContain("evidence refs must be non-empty");
    expect(result.error).toContain("requires ISO recordedAt");
    await expect(fs.access(path.join(tempDir, "provenance-records.json"))).rejects.toMatchObject({
      code: "ENOENT",
    });
  });

  it("returns optional failure when correlation id is missing", async () => {
    const result = await provider.recordProvenance(buildRecord("prov-1", "run-1"), {
      required: false,
      correlationId: " ",
    });

    expect(result).toEqual({
      success: false,
      error: "optional provenance write skipped: provenance write requires correlationId",
    });
  });

  it("fails closed when stored provenance is malformed", async () => {
    await fs.writeFile(
      path.join(tempDir, "provenance-records.json"),
      JSON.stringify({ schemaVersion: "1.0.0", records: {} }),
      "utf-8"
    );

    const result = await provider.recordProvenance(buildRecord("prov-1", "run-1"), {
      required: true,
      correlationId: "corr-1",
    });

    expect(result).toEqual({
      success: false,
      error: "provenance records file has invalid shape",
    });
  });
});

async function readJson(filePath: string): Promise<unknown> {
  return JSON.parse(await fs.readFile(filePath, "utf-8"));
}

function buildRecordWithoutMetadata(
  provenanceId: string,
  runId: string
): LifecycleProvenanceRecord {
  return {
    provenanceId,
    productUnitId: "digital-marketing",
    runId,
    source: "test",
    evidenceRefs: ["evidence://blueprint"],
    recordedAt: "2026-05-14T00:00:00.000Z",
  };
}

function buildRecord(
  provenanceId: string,
  runId: string
): LifecycleProvenanceRecord {
  return {
    provenanceId,
    productUnitId: "digital-marketing",
    runId,
    source: "test",
    evidenceRefs: ["evidence://blueprint"],
    privacyClassification: "confidential",
    retention: {
      policyId: "marketing-evidence-365",
      retentionDays: 365,
      expiresAt: "2027-05-14T00:00:00.000Z",
      legalHold: false,
    },
    recordedAt: "2026-05-14T00:00:00.000Z",
  };
}
