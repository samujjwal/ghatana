/**
 * FileRuntimeTruthProvider - bootstrap runtime truth persistence.
 *
 * @doc.type class
 * @doc.purpose File-backed runtime truth provider for Kernel bootstrap mode
 * @doc.layer kernel-providers
 * @doc.pattern Provider
 */

import * as fs from "node:fs/promises";
import * as path from "node:path";
import type {
  LifecycleProviderResult,
  LifecycleProviderWriteOptions,
  LifecycleRuntimeTruthProvider,
  LifecycleRuntimeTruthSnapshot,
} from "@ghatana/kernel-product-contracts";

export interface FileRuntimeTruthProviderOptions {
  readonly outputDirectory: string;
}

interface StoredRuntimeTruthSnapshots {
  readonly schemaVersion: "1.0.0";
  readonly snapshots: readonly LifecycleRuntimeTruthSnapshot[];
}

export class FileRuntimeTruthProvider implements LifecycleRuntimeTruthProvider {
  readonly providerId = "file-runtime-truth";
  readonly version = "1.0.0";
  readonly capabilities = ["runtime-truth", "bootstrap-mode", "file-backed"];

  private readonly outputDirectory: string;

  constructor(options: FileRuntimeTruthProviderOptions) {
    this.outputDirectory = options.outputDirectory;
  }

  async recordRuntimeTruth(
    snapshot: LifecycleRuntimeTruthSnapshot,
    options: LifecycleProviderWriteOptions
  ): Promise<LifecycleProviderResult> {
    const validation = validateRuntimeTruthSnapshot(snapshot);
    if (validation.length > 0) {
      return fail(validation.join("; "), options.required);
    }
    if (options.correlationId.trim().length === 0) {
      return fail("runtime truth write requires correlationId", options.required);
    }

    try {
      const stored = await this.readStoredSnapshots();
      const nextSnapshots = stored.snapshots.filter(
        (storedSnapshot) =>
          storedSnapshot.productUnitId !== snapshot.productUnitId ||
          storedSnapshot.runId !== snapshot.runId ||
          storedSnapshot.phase !== snapshot.phase
      );
      await this.writeStoredSnapshots({
        schemaVersion: "1.0.0",
        snapshots: [...nextSnapshots, snapshot],
      });
      await this.writeLatestSnapshot(snapshot);
      return { success: true, ref: this.snapshotsPath };
    } catch (error) {
      return fail(String(error).replace(/^Error: /, ""), options.required);
    }
  }

  async getRuntimeTruth(
    productUnitId: string
  ): Promise<LifecycleRuntimeTruthSnapshot | null> {
    const stored = await this.readStoredSnapshots();
    const matches = stored.snapshots.filter(
      (snapshot) => snapshot.productUnitId === productUnitId
    );
    return matches.at(-1) ?? null;
  }

  private get snapshotsPath(): string {
    return path.join(this.outputDirectory, "runtime-truth-snapshots.json");
  }

  private getLatestSnapshotPath(productUnitId: string): string {
    return path.join(
      this.outputDirectory,
      productUnitId,
      "latest",
      "runtime-truth-snapshot.json"
    );
  }

  private async readStoredSnapshots(): Promise<StoredRuntimeTruthSnapshots> {
    try {
      const content = await fs.readFile(this.snapshotsPath, "utf-8");
      const parsed = JSON.parse(content) as Partial<StoredRuntimeTruthSnapshots>;
      if (parsed.schemaVersion !== "1.0.0" || !Array.isArray(parsed.snapshots)) {
        throw new Error("runtime truth snapshots file has invalid shape");
      }
      return {
        schemaVersion: "1.0.0",
        snapshots: parsed.snapshots,
      };
    } catch (error) {
      if (isFileNotFound(error)) {
        return { schemaVersion: "1.0.0", snapshots: [] };
      }
      throw error;
    }
  }

  private async writeStoredSnapshots(
    snapshots: StoredRuntimeTruthSnapshots
  ): Promise<void> {
    await this.writeJsonFile(this.snapshotsPath, snapshots);
  }

  private async writeLatestSnapshot(
    snapshot: LifecycleRuntimeTruthSnapshot
  ): Promise<void> {
    await this.writeJsonFile(
      this.getLatestSnapshotPath(snapshot.productUnitId),
      snapshot
    );
  }

  private async writeJsonFile(filePath: string, content: unknown): Promise<void> {
    await fs.mkdir(path.dirname(filePath), { recursive: true });
    const tempPath = `${filePath}.${process.pid}.${Date.now()}.tmp`;
    await fs.writeFile(tempPath, `${JSON.stringify(content, null, 2)}\n`, "utf-8");
    await fs.rename(tempPath, filePath);
  }
}

function validateRuntimeTruthSnapshot(
  snapshot: LifecycleRuntimeTruthSnapshot
): string[] {
  const errors: string[] = [];
  if (snapshot.productUnitId.trim().length === 0) {
    errors.push("runtime truth snapshot requires productUnitId");
  }
  if (snapshot.runId.trim().length === 0) {
    errors.push("runtime truth snapshot requires runId");
  }
  if (snapshot.phase.trim().length === 0) {
    errors.push("runtime truth snapshot requires phase");
  }
  if (snapshot.status.trim().length === 0) {
    errors.push("runtime truth snapshot requires status");
  }
  if (!isIsoTimestamp(snapshot.observedAt)) {
    errors.push("runtime truth snapshot requires ISO observedAt");
  }
  if (snapshot.evidenceRefs.some((ref) => ref.trim().length === 0)) {
    errors.push("runtime truth evidence refs must be non-empty");
  }
  return errors;
}

function isIsoTimestamp(value: string): boolean {
  return !Number.isNaN(Date.parse(value));
}

function fail(message: string, required: boolean): LifecycleProviderResult {
  return {
    success: false,
    error: required ? message : `optional runtime truth write skipped: ${message}`,
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
