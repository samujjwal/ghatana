/**
 * LifecycleTruthWriter — contracts and file-system implementation for persisting
 * authoritative lifecycle truth outputs.
 *
 * Truth file canonical names (§2.4):
 *   lifecycle-plan.json
 *   lifecycle-result.json
 *   lifecycle-events.json
 *   gate-result-manifest.json
 *   artifact-manifest.json
 *   deployment-manifest.json
 *   verify-health-report.json
 *   rollback-manifest.json
 *   lifecycle-health-snapshot.json
 *
 * @doc.type class
 * @doc.purpose Persists authoritative lifecycle results and event logs to the truth file store.
 * @doc.layer platform
 * @doc.pattern Service
 */
import { promises as fs } from "node:fs";
import * as path from "node:path";
import type { ProductLifecycleResult } from "../domain/ProductLifecyclePhase.js";

// ---------------------------------------------------------------------------
// Result type
// ---------------------------------------------------------------------------

export type LifecycleTruthWriteStatus = "written" | "skipped" | "failed";

export interface LifecycleTruthWriteResult {
  readonly status: LifecycleTruthWriteStatus;
  /** Absolute path to the written file, or undefined when skipped. */
  readonly filePath?: string;
  readonly reasonCode?:
    | "write-error"
    | "directory-create-error"
    | "serialization-error";
  readonly message?: string;
  readonly cause?: string;
}

// ---------------------------------------------------------------------------
// Interface
// ---------------------------------------------------------------------------

/**
 * Port: writes lifecycle truth outputs to a durable store.
 *
 * Implementations must be idempotent — writing the same run twice with the same
 * content must not produce an error.
 */
export interface LifecycleTruthWriter {
  /**
   * Persist the lifecycle result to `lifecycle-result.json` under the given
   * output directory.
   */
  writeResult(
    result: ProductLifecycleResult,
    outputDirectory: string,
  ): Promise<LifecycleTruthWriteResult>;

  /**
   * Persist a lifecycle event log (any JSON-serialisable array) to
   * `lifecycle-events.json` under the given output directory.
   */
  writeEvents(
    events: readonly unknown[],
    outputDirectory: string,
  ): Promise<LifecycleTruthWriteResult>;

  /**
   * Persist an arbitrary truth artefact under the given output directory using
   * the canonical filename provided by the caller.
   *
   * The `canonicalFileName` must be one of the registered truth file names:
   *   - lifecycle-plan.json
   *   - lifecycle-result.json
   *   - lifecycle-events.json
   *   - gate-result-manifest.json
   *   - artifact-manifest.json
   *   - deployment-manifest.json
   *   - verify-health-report.json
   *   - rollback-manifest.json
   *   - lifecycle-health-snapshot.json
   */
  writeArtefact(
    content: unknown,
    outputDirectory: string,
    canonicalFileName: string,
  ): Promise<LifecycleTruthWriteResult>;
}

// ---------------------------------------------------------------------------
// Canonical truth file names
// ---------------------------------------------------------------------------

export const CANONICAL_TRUTH_FILE_NAMES = [
  "lifecycle-plan.json",
  "lifecycle-result.json",
  "lifecycle-events.json",
  "gate-result-manifest.json",
  "artifact-manifest.json",
  "deployment-manifest.json",
  "verify-health-report.json",
  "rollback-manifest.json",
  "lifecycle-health-snapshot.json",
] as const;

export type CanonicalTruthFileName =
  (typeof CANONICAL_TRUTH_FILE_NAMES)[number];

function isCanonicalTruthFileName(
  name: string,
): name is CanonicalTruthFileName {
  return (CANONICAL_TRUTH_FILE_NAMES as readonly string[]).includes(name);
}

// ---------------------------------------------------------------------------
// File-system implementation
// ---------------------------------------------------------------------------

/**
 * Writes lifecycle truth outputs to the local file system.
 *
 * Directories are created on demand. Writes are atomic at the file level
 * (write-to-temp then rename is not used here; for prototype-grade atomicity
 * this impl writes directly — production flows should use atomic replace).
 */
export class FileLifecycleTruthWriter implements LifecycleTruthWriter {
  async writeResult(
    result: ProductLifecycleResult,
    outputDirectory: string,
  ): Promise<LifecycleTruthWriteResult> {
    return this.writeArtefact(result, outputDirectory, "lifecycle-result.json");
  }

  async writeEvents(
    events: readonly unknown[],
    outputDirectory: string,
  ): Promise<LifecycleTruthWriteResult> {
    return this.writeArtefact(events, outputDirectory, "lifecycle-events.json");
  }

  async writeArtefact(
    content: unknown,
    outputDirectory: string,
    canonicalFileName: string,
  ): Promise<LifecycleTruthWriteResult> {
    if (!isCanonicalTruthFileName(canonicalFileName)) {
      return {
        status: "failed",
        reasonCode: "write-error",
        message:
          `'${canonicalFileName}' is not a registered canonical truth file name. ` +
          `Allowed: ${CANONICAL_TRUTH_FILE_NAMES.join(", ")}.`,
      };
    }

    const targetPath = path.join(outputDirectory, canonicalFileName);

    let serialized: string;
    try {
      serialized = JSON.stringify(content, null, 2);
    } catch (err) {
      return {
        status: "failed",
        reasonCode: "serialization-error",
        message: `Failed to serialise content for '${canonicalFileName}'.`,
        cause: err instanceof Error ? err.message : String(err),
      };
    }

    try {
      await fs.mkdir(outputDirectory, { recursive: true });
    } catch (err) {
      return {
        status: "failed",
        reasonCode: "directory-create-error",
        message: `Failed to create output directory '${outputDirectory}'.`,
        cause: err instanceof Error ? err.message : String(err),
      };
    }

    try {
      await fs.writeFile(targetPath, serialized, "utf-8");
    } catch (err) {
      return {
        status: "failed",
        reasonCode: "write-error",
        message: `Failed to write '${targetPath}'.`,
        cause: err instanceof Error ? err.message : String(err),
      };
    }

    return { status: "written", filePath: targetPath };
  }
}
