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

const RUNTIME_TRUTH_STATUSES = [
  "planned",
  "execution-started",
  "execution-succeeded",
  "execution-failed",
  "approval-required",
  "running",
  "pending approval",
  "approval-pending",
  "requires verification",
  "healthy",
  "degraded",
  "failed",
  "blocked",
  "agent-action-received",
  "policy-denied",
  "mastery-denied",
  "lifecycle-plan-created",
  "lifecycle-executed",
  "verification-failed",
] as const;

export class FileRuntimeTruthProvider implements LifecycleRuntimeTruthProvider {
  readonly providerId = "file-runtime-truth";
  readonly version = "1.0.0";
  readonly backingStore = "file" as const;
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
      const snapshotWithWriteMetadata = enrichRuntimeTruthSnapshot(snapshot, options);
      const stored = await this.readStoredSnapshots();
      const nextSnapshots = stored.snapshots.filter(
        (storedSnapshot) =>
          storedSnapshot.productUnitId !== snapshotWithWriteMetadata.productUnitId ||
          storedSnapshot.runId !== snapshotWithWriteMetadata.runId ||
          storedSnapshot.phase !== snapshotWithWriteMetadata.phase
      );
      const nextStoredSnapshots = [...nextSnapshots, snapshotWithWriteMetadata];
      await this.writeStoredSnapshots({
        schemaVersion: "1.0.0",
        snapshots: nextStoredSnapshots,
      });
      const latest = selectLatestRuntimeTruthSnapshot(
        nextStoredSnapshots.filter(
          (storedSnapshot) =>
            storedSnapshot.productUnitId === snapshotWithWriteMetadata.productUnitId
        )
      );
      if (latest) {
        await this.writeLatestSnapshot(latest);
      }
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
    return selectLatestRuntimeTruthSnapshot(matches);
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
    await renameWithRetry(tempPath, filePath);
  }
}

async function renameWithRetry(sourcePath: string, targetPath: string): Promise<void> {
  const retryableCodes = new Set(["EPERM", "EBUSY", "EACCES"]);
  let lastError: unknown;

  for (let attempt = 0; attempt < 8; attempt += 1) {
    try {
      await fs.rename(sourcePath, targetPath);
      return;
    } catch (error) {
      lastError = error;
      if (!isNodeError(error) || !retryableCodes.has(error.code)) {
        throw error;
      }
      await delay(25 * (attempt + 1));
    }
  }

  throw lastError;
}

function delay(milliseconds: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, milliseconds));
}

function enrichRuntimeTruthSnapshot(
  snapshot: LifecycleRuntimeTruthSnapshot,
  options: LifecycleProviderWriteOptions
): LifecycleRuntimeTruthSnapshot {
  return {
    ...snapshot,
    correlationId: snapshot.correlationId ?? options.correlationId,
    ...(snapshot.privacyClassification ?? options.privacyClassification
      ? {
          privacyClassification:
            snapshot.privacyClassification ?? options.privacyClassification,
        }
      : {}),
    ...(snapshot.retention ?? options.retention
      ? { retention: snapshot.retention ?? options.retention }
      : {}),
    ...(snapshot.providerMode ? { providerMode: snapshot.providerMode } : {}),
  };
}

function selectLatestRuntimeTruthSnapshot(
  snapshots: readonly LifecycleRuntimeTruthSnapshot[]
): LifecycleRuntimeTruthSnapshot | null {
  if (snapshots.length === 0) {
    return null;
  }

  return snapshots.reduce((latest, candidate) => {
    const latestTime = Date.parse(latest.observedAt);
    const candidateTime = Date.parse(candidate.observedAt);
    if (!Number.isNaN(candidateTime) && candidateTime > latestTime) {
      return candidate;
    }
    if (candidateTime === latestTime) {
      return candidate;
    }
    return latest;
  });
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
  } else if (!isRuntimeTruthStatus(snapshot.status)) {
    errors.push(`unsupported runtime truth status ${snapshot.status}`);
  }
  if (!isIsoTimestamp(snapshot.observedAt)) {
    errors.push("runtime truth snapshot requires ISO observedAt");
  }
  if (snapshot.evidenceRefs.some((ref) => ref.trim().length === 0)) {
    errors.push("runtime truth evidence refs must be non-empty");
  }
  return errors;
}

function isRuntimeTruthStatus(value: string): boolean {
  return RUNTIME_TRUTH_STATUSES.includes(
    value as (typeof RUNTIME_TRUTH_STATUSES)[number]
  );
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

function isNodeError(error: unknown): error is NodeJS.ErrnoException & { readonly code: string } {
  return (
    typeof error === "object" &&
    error !== null &&
    "code" in error &&
    typeof (error as { readonly code?: unknown }).code === "string"
  );
}
