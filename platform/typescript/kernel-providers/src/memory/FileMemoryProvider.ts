/**
 * FileMemoryProvider - bootstrap lifecycle memory persistence.
 *
 * @doc.type class
 * @doc.purpose File-backed lifecycle memory provider for Kernel bootstrap mode
 * @doc.layer kernel-providers
 * @doc.pattern Provider
 */

import * as fs from "node:fs/promises";
import * as path from "node:path";
import type {
  LifecycleMemoryProvider,
  LifecycleMemoryRecord,
  LifecycleProviderQuery,
  LifecycleProviderResult,
  LifecycleProviderWriteOptions,
} from "@ghatana/kernel-product-contracts";

export interface FileMemoryProviderOptions {
  readonly outputDirectory: string;
}

interface StoredMemoryRecords {
  readonly schemaVersion: "1.0.0";
  readonly records: readonly LifecycleMemoryRecord[];
}

const PRIVACY_CLASSIFICATIONS = [
  "public",
  "internal",
  "confidential",
  "restricted",
] as const;

export class FileMemoryProvider implements LifecycleMemoryProvider {
  readonly providerId = "file-memory";
  readonly version = "1.0.0";
  readonly capabilities = ["memory", "bootstrap-mode", "file-backed"];

  private readonly outputDirectory: string;

  constructor(options: FileMemoryProviderOptions) {
    this.outputDirectory = options.outputDirectory;
  }

  async recordMemory(
    record: LifecycleMemoryRecord,
    options: LifecycleProviderWriteOptions
  ): Promise<LifecycleProviderResult> {
    const validation = validateMemoryRecord(record);
    if (validation.length > 0) {
      return fail(validation.join("; "), options.required);
    }
    if (options.correlationId.trim().length === 0) {
      return fail("memory write requires correlationId", options.required);
    }

    try {
      const stored = await this.readStoredRecords();
      const nextRecords = stored.records.filter(
        (storedRecord) => storedRecord.memoryId !== record.memoryId
      );
      await this.writeStoredRecords({
        schemaVersion: "1.0.0",
        records: [...nextRecords, record],
      });
      await this.writeLatestRecord(record);
      return { success: true, ref: this.recordsPath };
    } catch (error) {
      return fail(String(error).replace(/^Error: /, ""), options.required);
    }
  }

  async listMemory(
    query: LifecycleProviderQuery
  ): Promise<readonly LifecycleMemoryRecord[]> {
    const stored = await this.readStoredRecords();
    return stored.records.filter((record) => {
      if (record.productUnitId !== query.productUnitId) {
        return false;
      }
      if (query.runId !== undefined && record.runId !== query.runId) {
        return false;
      }
      return true;
    });
  }

  private get recordsPath(): string {
    return path.join(this.outputDirectory, "memory-records.json");
  }

  private getLatestRecordPath(productUnitId: string): string {
    return path.join(
      this.outputDirectory,
      productUnitId,
      "latest",
      "memory-record.json"
    );
  }

  private async readStoredRecords(): Promise<StoredMemoryRecords> {
    try {
      const content = await fs.readFile(this.recordsPath, "utf-8");
      const parsed = JSON.parse(content) as Partial<StoredMemoryRecords>;
      if (parsed.schemaVersion !== "1.0.0" || !Array.isArray(parsed.records)) {
        throw new Error("memory records file has invalid shape");
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

  private async writeStoredRecords(records: StoredMemoryRecords): Promise<void> {
    await this.writeJsonFile(this.recordsPath, records);
  }

  private async writeLatestRecord(record: LifecycleMemoryRecord): Promise<void> {
    await this.writeJsonFile(this.getLatestRecordPath(record.productUnitId), record);
  }

  private async writeJsonFile(filePath: string, content: unknown): Promise<void> {
    await fs.mkdir(path.dirname(filePath), { recursive: true });
    const tempPath = `${filePath}.${process.pid}.${Date.now()}.tmp`;
    await fs.writeFile(tempPath, `${JSON.stringify(content, null, 2)}\n`, "utf-8");
    await fs.rename(tempPath, filePath);
  }
}

function validateMemoryRecord(record: LifecycleMemoryRecord): string[] {
  const errors: string[] = [];
  if (record.memoryId.trim().length === 0) {
    errors.push("memory record requires memoryId");
  }
  if (record.productUnitId.trim().length === 0) {
    errors.push("memory record requires productUnitId");
  }
  if (record.runId.trim().length === 0) {
    errors.push("memory record requires runId");
  }
  if (record.kind.trim().length === 0) {
    errors.push("memory record requires kind");
  }
  if (record.contentRef.trim().length === 0) {
    errors.push("memory record requires contentRef");
  }
  if (
    record.privacyClassification !== undefined &&
    !PRIVACY_CLASSIFICATIONS.includes(record.privacyClassification)
  ) {
    errors.push("memory privacyClassification is invalid");
  }
  if (record.retention !== undefined) {
    if (record.retention.policyId.trim().length === 0) {
      errors.push("memory retention requires policyId");
    }
    if (!Number.isInteger(record.retention.retentionDays) || record.retention.retentionDays < 0) {
      errors.push("memory retentionDays must be non-negative");
    }
    if (
      record.retention.expiresAt !== undefined &&
      !isIsoTimestamp(record.retention.expiresAt)
    ) {
      errors.push("memory retention expiresAt must be ISO timestamp");
    }
  }
  if (!isIsoTimestamp(record.recordedAt)) {
    errors.push("memory record requires ISO recordedAt");
  }
  return errors;
}

function isIsoTimestamp(value: string): boolean {
  return !Number.isNaN(Date.parse(value));
}

function fail(message: string, required: boolean): LifecycleProviderResult {
  return {
    success: false,
    error: required ? message : `optional memory write skipped: ${message}`,
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
