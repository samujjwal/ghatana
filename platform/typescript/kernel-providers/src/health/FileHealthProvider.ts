/**
 * FileHealthProvider - bootstrap health snapshot persistence.
 *
 * @doc.type class
 * @doc.purpose File-backed health snapshot provider for Kernel bootstrap mode
 * @doc.layer kernel-providers
 * @doc.pattern Provider
 */

import * as fs from "node:fs/promises";
import * as path from "node:path";
import type {
  HealthStatus,
  LifecycleHealthProvider,
  LifecycleHealthSnapshot,
  LifecycleHealthSnapshotRef,
  LifecycleProviderResult,
  LifecycleProviderWriteOptions,
} from "@ghatana/kernel-product-contracts";

export interface FileHealthProviderOptions {
  readonly outputDirectory: string;
}

export interface FileLifecycleHealthSnapshotWriteOptions
  extends LifecycleProviderWriteOptions {
  readonly runId: string;
}

export type OperationalHealthSnapshotKind = "provider" | "plugin" | "toolchain";

export interface OperationalHealthSnapshot {
  readonly kind: OperationalHealthSnapshotKind;
  readonly subjectId: string;
  readonly status: HealthStatus;
  readonly message: string;
  readonly snapshotAt: string;
  readonly evidenceRefs: readonly string[];
}

interface StoredHealthSnapshotRefs {
  readonly schemaVersion: "1.0.0";
  readonly snapshots: readonly LifecycleHealthSnapshotRef[];
}

interface StoredOperationalHealthSnapshots {
  readonly schemaVersion: "1.0.0";
  readonly snapshots: readonly OperationalHealthSnapshot[];
}

interface LatestHealthSnapshotPointer {
  readonly schemaVersion: "1.0.0";
  readonly productUnitId: string;
  readonly runId: string;
  readonly status: string;
  readonly snapshotPath: string;
  readonly updatedAt: string;
}

const HEALTH_STATUSES = [
  "healthy",
  "degraded",
  "blocked",
  "failed",
  "skipped",
  "unknown",
  "requires-approval",
  "requires-verification",
  "obsolete",
  "quarantined",
] as const satisfies readonly HealthStatus[];

const HEALTH_REASON_CODES: Record<string, string> = {
  healthy: "health-healthy",
  degraded: "health-degraded",
  blocked: "health-blocked",
  failed: "health-failed",
  skipped: "health-skipped",
  unknown: "health-unknown",
  "requires-approval": "health-requires-approval",
  "requires-verification": "health-requires-verification",
  obsolete: "health-obsolete",
  quarantined: "health-quarantined",
};

export class FileHealthProvider implements LifecycleHealthProvider {
  readonly providerId = "file-health-snapshots";
  readonly version = "1.0.0";
  readonly backingStore = "file" as const;
  readonly capabilities = ["health-snapshots", "bootstrap-mode", "file-backed"];

  private readonly outputDirectory: string;

  constructor(options: FileHealthProviderOptions) {
    this.outputDirectory = options.outputDirectory;
  }

  async writeLifecycleHealthSnapshot(
    snapshot: LifecycleHealthSnapshot,
    options: FileLifecycleHealthSnapshotWriteOptions
  ): Promise<LifecycleProviderResult> {
    const validation = validateLifecycleHealthSnapshot(snapshot, options.runId);
    if (validation.length > 0) {
      return fail(validation.join("; "), options.required);
    }

    const snapshotPath = this.getRunScopedSnapshotPath(snapshot);
    const ref: LifecycleHealthSnapshotRef = {
      productUnitId: snapshot.productUnitId,
      runId: snapshot.runId,
      status: snapshot.status,
      snapshotPath,
      snapshotAt: snapshot.snapshotAt,
      correlationId: options.correlationId,
      reasonCode: reasonCodeForStatus(snapshot.status),
      ...(options.privacyClassification
        ? { privacyClassification: options.privacyClassification }
        : {}),
      ...(options.retention ? { retention: options.retention } : {}),
    };

    try {
      await this.writeJsonFile(snapshotPath, snapshot);
      const recordResult = await this.recordHealthSnapshot(ref, options);
      if (!recordResult.success) {
        throw new Error(`${recordResult.error}`);
      }
      await this.writeLatestPointer(ref);
      return { success: true, ref: snapshotPath };
    } catch (error) {
      return fail(String(error).replace(/^Error: /, ""), options.required);
    }
  }

  async recordOperationalHealthSnapshot(
    snapshot: OperationalHealthSnapshot,
    options: LifecycleProviderWriteOptions
  ): Promise<LifecycleProviderResult> {
    const validation = validateOperationalHealthSnapshot(snapshot);
    if (validation.length > 0) {
      return fail(validation.join("; "), options.required);
    }
    if (options.correlationId.trim().length === 0) {
      return fail("health snapshot write requires correlationId", options.required);
    }

    try {
      const stored = await this.readOperationalSnapshots();
      await this.writeOperationalSnapshots({
        schemaVersion: "1.0.0",
        snapshots: [...stored.snapshots, snapshot],
      });
      const latestPath = this.getOperationalLatestPath(snapshot);
      await this.writeJsonFile(latestPath, snapshot);
      return { success: true, ref: latestPath };
    } catch (error) {
      return fail(String(error).replace(/^Error: /, ""), options.required);
    }
  }

  async recordHealthSnapshot(
    snapshot: LifecycleHealthSnapshotRef,
    options: LifecycleProviderWriteOptions
  ): Promise<LifecycleProviderResult> {
    const validation = validateHealthSnapshotRef(snapshot);
    if (validation.length > 0) {
      return fail(validation.join("; "), options.required);
    }
    if (options.correlationId.trim().length === 0) {
      return fail("health snapshot write requires correlationId", options.required);
    }

    try {
      const snapshotWithWriteMetadata = enrichHealthSnapshotRef(snapshot, options);
      const stored = await this.readStoredRefs();
      const nextRefs = stored.snapshots.filter(
        (storedRef) =>
          storedRef.productUnitId !== snapshotWithWriteMetadata.productUnitId ||
          storedRef.runId !== snapshotWithWriteMetadata.runId ||
          storedRef.snapshotPath !== snapshotWithWriteMetadata.snapshotPath
      );
      await this.writeStoredRefs({
        schemaVersion: "1.0.0",
        snapshots: [...nextRefs, snapshotWithWriteMetadata],
      });
      return { success: true, ref: this.refsPath };
    } catch (error) {
      return fail(String(error).replace(/^Error: /, ""), options.required);
    }
  }

  async getLatestHealthSnapshot(
    productUnitId: string
  ): Promise<LifecycleHealthSnapshotRef | null> {
    const stored = await this.readStoredRefs();
    const matches = stored.snapshots.filter(
      (snapshot) => snapshot.productUnitId === productUnitId
    );
    return selectLatestHealthSnapshot(matches);
  }

  private get refsPath(): string {
    return path.join(this.outputDirectory, "lifecycle-health-snapshots.json");
  }

  private get operationalPath(): string {
    return path.join(this.outputDirectory, "operational-health-snapshots.json");
  }

  private getRunScopedSnapshotPath(snapshot: LifecycleHealthSnapshot): string {
    return path.join(
      this.outputDirectory,
      snapshot.productUnitId,
      snapshot.runId,
      "lifecycle-health-snapshot.json"
    );
  }

  private getLatestPointerPath(productUnitId: string): string {
    return path.join(
      this.outputDirectory,
      productUnitId,
      "latest",
      "lifecycle-health-snapshot.pointer.json"
    );
  }

  private getOperationalLatestPath(snapshot: OperationalHealthSnapshot): string {
    return path.join(
      this.outputDirectory,
      "operational-health",
      snapshot.kind,
      snapshot.subjectId,
      "latest-health-snapshot.json"
    );
  }

  private async writeLatestPointer(ref: LifecycleHealthSnapshotRef): Promise<void> {
    const pointer: LatestHealthSnapshotPointer = {
      schemaVersion: "1.0.0",
      productUnitId: ref.productUnitId,
      runId: ref.runId,
      status: ref.status,
      snapshotPath: ref.snapshotPath,
      updatedAt: new Date().toISOString(),
    };
    await this.writeJsonFile(this.getLatestPointerPath(ref.productUnitId), pointer);
  }

  private async readStoredRefs(): Promise<StoredHealthSnapshotRefs> {
    try {
      const content = await fs.readFile(this.refsPath, "utf-8");
      const parsed = JSON.parse(content) as Partial<StoredHealthSnapshotRefs>;
      if (parsed.schemaVersion !== "1.0.0" || !Array.isArray(parsed.snapshots)) {
        throw new Error("health snapshot refs file has invalid shape");
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

  private async writeStoredRefs(refs: StoredHealthSnapshotRefs): Promise<void> {
    await this.writeJsonFile(this.refsPath, refs);
  }

  private async readOperationalSnapshots(): Promise<StoredOperationalHealthSnapshots> {
    try {
      const content = await fs.readFile(this.operationalPath, "utf-8");
      const parsed = JSON.parse(content) as Partial<StoredOperationalHealthSnapshots>;
      if (parsed.schemaVersion !== "1.0.0" || !Array.isArray(parsed.snapshots)) {
        throw new Error("operational health snapshots file has invalid shape");
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

  private async writeOperationalSnapshots(
    snapshots: StoredOperationalHealthSnapshots
  ): Promise<void> {
    await this.writeJsonFile(this.operationalPath, snapshots);
  }

  private async writeJsonFile(filePath: string, content: unknown): Promise<void> {
    await fs.mkdir(path.dirname(filePath), { recursive: true });
    const tempPath = `${filePath}.${process.pid}.${Date.now()}.tmp`;
    await fs.writeFile(tempPath, `${JSON.stringify(content, null, 2)}\n`, "utf-8");
    await fs.rename(tempPath, filePath);
  }
}

function enrichHealthSnapshotRef(
  snapshot: LifecycleHealthSnapshotRef,
  options: LifecycleProviderWriteOptions
): LifecycleHealthSnapshotRef {
  return {
    ...snapshot,
    correlationId: snapshot.correlationId ?? options.correlationId,
    reasonCode: snapshot.reasonCode ?? reasonCodeForStatus(snapshot.status),
    ...(snapshot.privacyClassification ?? options.privacyClassification
      ? {
          privacyClassification:
            snapshot.privacyClassification ?? options.privacyClassification,
        }
      : {}),
    ...(snapshot.retention ?? options.retention
      ? { retention: snapshot.retention ?? options.retention }
      : {}),
  };
}

function selectLatestHealthSnapshot(
  snapshots: readonly LifecycleHealthSnapshotRef[]
): LifecycleHealthSnapshotRef | null {
  if (snapshots.length === 0) {
    return null;
  }

  return snapshots.reduce((latest, candidate) => {
    const latestTime = timestampOrZero(latest.snapshotAt);
    const candidateTime = timestampOrZero(candidate.snapshotAt);
    if (candidateTime > latestTime) {
      return candidate;
    }
    if (candidateTime === latestTime) {
      return candidate;
    }
    return latest;
  });
}

function timestampOrZero(value: string | undefined): number {
  if (!value) {
    return 0;
  }
  const parsed = Date.parse(value);
  return Number.isNaN(parsed) ? 0 : parsed;
}

function reasonCodeForStatus(status: string): string {
  return HEALTH_REASON_CODES[status] ?? "health-unknown";
}

function validateLifecycleHealthSnapshot(
  snapshot: LifecycleHealthSnapshot,
  runId: string
): string[] {
  const errors = validateHealthSnapshotRef({
    productUnitId: snapshot.productUnitId,
    runId: snapshot.runId,
    status: snapshot.status,
    snapshotPath: "pending",
  });
  if (snapshot.runId !== runId) {
    errors.push(
      `lifecycle health snapshot runId ${snapshot.runId} does not match write runId ${runId}`
    );
  }
  if (!isIsoTimestamp(snapshot.snapshotAt)) {
    errors.push("lifecycle health snapshot requires ISO snapshotAt");
  }
  if (snapshot.totalDuration < 0) {
    errors.push("lifecycle health snapshot totalDuration must be non-negative");
  }
  for (const phase of snapshot.phases) {
    if (!isHealthStatus(phase.status)) {
      errors.push(`phase ${phase.phase} uses unsupported health status ${phase.status}`);
    }
    if (!isIsoTimestamp(phase.completedAt)) {
      errors.push(`phase ${phase.phase} requires ISO completedAt`);
    }
    if (phase.duration < 0) {
      errors.push(`phase ${phase.phase} duration must be non-negative`);
    }
  }
  return errors;
}

function validateOperationalHealthSnapshot(
  snapshot: OperationalHealthSnapshot
): string[] {
  const errors: string[] = [];
  if (!["provider", "plugin", "toolchain"].includes(snapshot.kind)) {
    errors.push(`unsupported operational health kind ${snapshot.kind}`);
  }
  if (snapshot.subjectId.trim().length === 0) {
    errors.push("operational health snapshot requires subjectId");
  }
  if (!isHealthStatus(snapshot.status)) {
    errors.push(`unsupported health status ${snapshot.status}`);
  }
  if (snapshot.message.trim().length === 0) {
    errors.push("operational health snapshot requires message");
  }
  if (!isIsoTimestamp(snapshot.snapshotAt)) {
    errors.push("operational health snapshot requires ISO snapshotAt");
  }
  return errors;
}

function validateHealthSnapshotRef(snapshot: LifecycleHealthSnapshotRef): string[] {
  const errors: string[] = [];
  if (snapshot.productUnitId.trim().length === 0) {
    errors.push("health snapshot requires productUnitId");
  }
  if (snapshot.runId.trim().length === 0) {
    errors.push("health snapshot requires runId");
  }
  if (!isHealthStatus(snapshot.status)) {
    errors.push(`unsupported health status ${snapshot.status}`);
  }
  if (snapshot.snapshotPath.trim().length === 0) {
    errors.push("health snapshot requires snapshotPath");
  }
  return errors;
}

function isHealthStatus(value: unknown): value is HealthStatus {
  return (
    typeof value === "string" && HEALTH_STATUSES.includes(value as HealthStatus)
  );
}

function isIsoTimestamp(value: string): boolean {
  return !Number.isNaN(Date.parse(value));
}

function fail(message: string, required: boolean): LifecycleProviderResult {
  return {
    success: false,
    error: required ? message : `optional health snapshot write skipped: ${message}`,
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
