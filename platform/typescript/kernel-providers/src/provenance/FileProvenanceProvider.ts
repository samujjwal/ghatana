/**
 * FileProvenanceProvider - bootstrap provenance persistence.
 *
 * @doc.type class
 * @doc.purpose File-backed provenance provider for Kernel bootstrap mode
 * @doc.layer kernel-providers
 * @doc.pattern Provider
 */

import * as fs from "node:fs/promises";
import * as path from "node:path";
import type {
  LifecycleProvenanceProvider,
  LifecycleProvenanceRecord,
  LifecycleProviderQuery,
  LifecycleProviderResult,
  LifecycleProviderWriteOptions,
} from "@ghatana/kernel-product-contracts";

export interface FileProvenanceProviderOptions {
  readonly outputDirectory: string;
}

interface StoredProvenanceRecords {
  readonly schemaVersion: "1.0.0";
  readonly records: readonly LifecycleProvenanceRecord[];
}

const PRIVACY_CLASSIFICATIONS = [
  "public",
  "internal",
  "confidential",
  "restricted",
] as const;

export class FileProvenanceProvider implements LifecycleProvenanceProvider {
  readonly providerId = "file-provenance";
  readonly version = "1.0.0";
  readonly capabilities = ["provenance", "bootstrap-mode", "file-backed"];

  private readonly outputDirectory: string;

  constructor(options: FileProvenanceProviderOptions) {
    this.outputDirectory = options.outputDirectory;
  }

  async recordProvenance(
    record: LifecycleProvenanceRecord,
    options: LifecycleProviderWriteOptions
  ): Promise<LifecycleProviderResult> {
    const validation = validateProvenanceRecord(record);
    if (validation.length > 0) {
      return fail(validation.join("; "), options.required);
    }
    if (options.correlationId.trim().length === 0) {
      return fail("provenance write requires correlationId", options.required);
    }

    try {
      const recordWithWriteMetadata = enrichProvenanceRecord(record, options);
      const stored = await this.readStoredRecords();
      const nextRecords = stored.records.filter(
        (storedRecord) =>
          storedRecord.provenanceId !== recordWithWriteMetadata.provenanceId
      );
      const records = [...nextRecords, recordWithWriteMetadata];
      await this.writeStoredRecords({
        schemaVersion: "1.0.0",
        records,
      });
      await this.writeRunScopedRecords(recordWithWriteMetadata, records);
      return { success: true, ref: this.recordsPath };
    } catch (error) {
      return fail(String(error).replace(/^Error: /, ""), options.required);
    }
  }

  async listProvenance(
    query: LifecycleProviderQuery
  ): Promise<readonly LifecycleProvenanceRecord[]> {
    const stored = await this.readStoredRecords();
    return stored.records.filter((record) => {
      if (record.productUnitId !== query.productUnitId) {
        return false;
      }
      if (query.runId !== undefined && record.runId !== query.runId) {
        return false;
      }
      if (
        query.correlationId !== undefined &&
        record.correlationId !== query.correlationId
      ) {
        return false;
      }
      return true;
    });
  }

  private get recordsPath(): string {
    return path.join(this.outputDirectory, "provenance-records.json");
  }

  private async readStoredRecords(): Promise<StoredProvenanceRecords> {
    try {
      const content = await fs.readFile(this.recordsPath, "utf-8");
      const parsed = JSON.parse(content) as Partial<StoredProvenanceRecords>;
      if (parsed.schemaVersion !== "1.0.0" || !Array.isArray(parsed.records)) {
        throw new Error("provenance records file has invalid shape");
      }
      return {
        schemaVersion: "1.0.0",
        records: parsed.records,
      };
    } catch (error) {
      if (isFileNotFound(error)) {
        return { schemaVersion: "1.0.0", records: [] };
      }
      throw error;
    }
  }

  private async writeStoredRecords(records: StoredProvenanceRecords): Promise<void> {
    await fs.mkdir(this.outputDirectory, { recursive: true });
    const tempPath = `${this.recordsPath}.${process.pid}.${Date.now()}.tmp`;
    await fs.writeFile(tempPath, `${JSON.stringify(records, null, 2)}\n`, "utf-8");
    await fs.rename(tempPath, this.recordsPath);
  }

  private async writeRunScopedRecords(
    record: LifecycleProvenanceRecord,
    records: readonly LifecycleProvenanceRecord[]
  ): Promise<void> {
    const runRecords = records.filter(
      (storedRecord) =>
        storedRecord.productUnitId === record.productUnitId &&
        storedRecord.runId === record.runId
    );
    const runScopedPath = path.join(
      this.outputDirectory,
      "products",
      encodeURIComponent(record.productUnitId),
      "runs",
      encodeURIComponent(record.runId),
      "provenance-records.json"
    );
    await fs.mkdir(path.dirname(runScopedPath), { recursive: true });
    const tempPath = `${runScopedPath}.${process.pid}.${Date.now()}.tmp`;
    await fs.writeFile(
      tempPath,
      `${JSON.stringify({ schemaVersion: "1.0.0", records: runRecords }, null, 2)}\n`,
      "utf-8"
    );
    await fs.rename(tempPath, runScopedPath);
  }
}

function enrichProvenanceRecord(
  record: LifecycleProvenanceRecord,
  options: LifecycleProviderWriteOptions
): LifecycleProvenanceRecord {
  return {
    ...record,
    correlationId: record.correlationId ?? options.correlationId,
    ...(record.privacyClassification ?? options.privacyClassification
      ? {
          privacyClassification:
            record.privacyClassification ?? options.privacyClassification,
        }
      : {}),
    ...(record.retention ?? options.retention
      ? { retention: record.retention ?? options.retention }
      : {}),
  };
}

function validateProvenanceRecord(record: LifecycleProvenanceRecord): string[] {
  const errors: string[] = [];
  if (record.provenanceId.trim().length === 0) {
    errors.push("provenance record requires provenanceId");
  }
  if (record.productUnitId.trim().length === 0) {
    errors.push("provenance record requires productUnitId");
  }
  if (record.runId.trim().length === 0) {
    errors.push("provenance record requires runId");
  }
  if (record.source.trim().length === 0) {
    errors.push("provenance record requires source");
  }
  if (record.evidenceRefs.some((ref) => ref.trim().length === 0)) {
    errors.push("provenance evidence refs must be non-empty");
  }
  if (
    record.privacyClassification !== undefined &&
    !PRIVACY_CLASSIFICATIONS.includes(record.privacyClassification)
  ) {
    errors.push("provenance privacyClassification is invalid");
  }
  if (record.retention !== undefined) {
    if (record.retention.policyId.trim().length === 0) {
      errors.push("provenance retention requires policyId");
    }
    if (!Number.isInteger(record.retention.retentionDays) || record.retention.retentionDays < 0) {
      errors.push("provenance retentionDays must be non-negative");
    }
    if (
      record.retention.expiresAt !== undefined &&
      !isIsoTimestamp(record.retention.expiresAt)
    ) {
      errors.push("provenance retention expiresAt must be ISO timestamp");
    }
  }
  if (!isIsoTimestamp(record.recordedAt)) {
    errors.push("provenance record requires ISO recordedAt");
  }
  return errors;
}

function isIsoTimestamp(value: string): boolean {
  return !Number.isNaN(Date.parse(value));
}

function fail(message: string, required: boolean): LifecycleProviderResult {
  return {
    success: false,
    error: required ? message : `optional provenance write skipped: ${message}`,
  };
}

function isFileNotFound(error: unknown): boolean {
  return (
    typeof error === "object" &&
    error !== null &&
    "code" in error &&
    (error as { readonly code?: unknown }).code === "ENOENT"
  );
}
