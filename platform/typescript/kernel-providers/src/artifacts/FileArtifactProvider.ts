/**
 * FileArtifactProvider - bootstrap artifact manifest persistence.
 *
 * @doc.type class
 * @doc.purpose File-backed artifact manifest provider for Kernel bootstrap mode
 * @doc.layer kernel-providers
 * @doc.pattern Provider
 */

import * as fs from "node:fs/promises";
import * as path from "node:path";
import {
  ArtifactFingerprintCalculator,
  ArtifactManifestGenerator,
  type ArtifactEntry,
  type ArtifactManifest,
} from "@ghatana/kernel-artifacts";
import type {
  LifecycleArtifactManifestRef,
  LifecycleArtifactProvider,
  LifecycleProviderQuery,
  LifecycleProviderResult,
  LifecycleProviderWriteOptions,
} from "@ghatana/kernel-product-contracts";

const NO_ARTIFACT_VALIDATION_ERRORS: readonly string[] = Object.freeze([]);

export interface FileArtifactProviderOptions {
  readonly outputDirectory: string;
  readonly artifactRootDirectory?: string;
}

export interface FileArtifactManifestWriteOptions
  extends LifecycleProviderWriteOptions {
  readonly runId: string;
}

interface StoredArtifactManifestRefs {
  readonly schemaVersion: "1.0.0";
  readonly manifests: readonly LifecycleArtifactManifestRef[];
}

interface LatestArtifactManifestPointer {
  readonly schemaVersion: "1.0.0";
  readonly productUnitId: string;
  readonly runId: string;
  readonly phase: string;
  readonly surface: string;
  readonly manifestPath: string;
  readonly artifactCount: number;
  readonly updatedAt: string;
}

export class FileArtifactProvider implements LifecycleArtifactProvider {
  readonly providerId = "file-artifact-manifests";
  readonly version = "1.0.0";
  readonly backingStore = "file" as const;
  readonly capabilities = ["artifact-manifests", "bootstrap-mode", "file-backed"];

  private readonly outputDirectory: string;
  private readonly artifactRootDirectory: string;
  private readonly generator = new ArtifactManifestGenerator();
  private readonly fingerprintCalculator = new ArtifactFingerprintCalculator();

  constructor(options: FileArtifactProviderOptions) {
    this.outputDirectory = path.resolve(options.outputDirectory);
    this.artifactRootDirectory = path.resolve(options.artifactRootDirectory ?? process.cwd());
  }

  async writeArtifactManifest(
    manifest: ArtifactManifest,
    options: FileArtifactManifestWriteOptions
  ): Promise<LifecycleProviderResult> {
    const validation = await this.validateManifest(manifest);
    if (validation.length > 0) {
      return fail(validation.join("; "), options.required);
    }

    const manifestPath = this.getRunScopedManifestPath(manifest, options.runId);
    const ref: LifecycleArtifactManifestRef = {
      productUnitId: manifest.productId,
      runId: options.runId,
      manifestPath,
      artifactCount: manifest.artifacts.length,
      correlationId: options.correlationId,
      digestStatus: resolveDigestStatus(manifest),
    };

    try {
      await this.writeJsonFile(manifestPath, manifest);
      const recordResult = await this.recordArtifactManifest(ref, options);
      if (!recordResult.success) {
        throw new Error(`${recordResult.error}`);
      }
      await this.writeLatestPointer(manifest, ref);
      return { success: true, ref: manifestPath };
    } catch (error) {
      return fail(String(error).replace(/^Error: /, ""), options.required);
    }
  }

  async recordArtifactManifest(
    manifest: LifecycleArtifactManifestRef,
    options: LifecycleProviderWriteOptions
  ): Promise<LifecycleProviderResult> {
    if (options.correlationId.trim().length === 0) {
      return fail("artifact manifest write requires correlationId", options.required);
    }
    if (!this.isInsideOutputDirectory(manifest.manifestPath)) {
      return fail(`artifact manifest path escapes output root: ${manifest.manifestPath}`, options.required);
    }

    try {
      const storedRefs = await this.readStoredRefs();
      const nextRefs = storedRefs.manifests.filter(
        (storedRef) =>
          storedRef.productUnitId !== manifest.productUnitId ||
          storedRef.runId !== manifest.runId ||
          storedRef.manifestPath !== manifest.manifestPath
      );
      await this.writeStoredRefs({
        schemaVersion: "1.0.0",
        manifests: [...nextRefs, manifest],
      });
      return { success: true, ref: this.refsPath };
    } catch (error) {
      return fail(String(error).replace(/^Error: /, ""), options.required);
    }
  }

  async listArtifactManifests(
    query: LifecycleProviderQuery
  ): Promise<readonly LifecycleArtifactManifestRef[]> {
    const storedRefs = await this.readStoredRefs();
    return storedRefs.manifests.filter((manifest) => {
      if (manifest.productUnitId !== query.productUnitId) {
        return false;
      }
      if (query.runId !== undefined && manifest.runId !== query.runId) {
        return false;
      }
      if (
        query.correlationId !== undefined &&
        manifest.correlationId !== query.correlationId
      ) {
        return false;
      }
      return true;
    });
  }

  private get refsPath(): string {
    return path.join(this.outputDirectory, "artifact-manifests.json");
  }

  private getRunScopedManifestPath(
    manifest: ArtifactManifest,
    runId: string
  ): string {
    return path.join(
      this.outputDirectory,
      encodePathSegment(manifest.productId),
      encodePathSegment(runId),
      encodePathSegment(manifest.phase),
      encodePathSegment(this.resolveManifestSurface(manifest)),
      "artifact-manifest.json"
    );
  }

  private getLatestPointerPath(manifest: ArtifactManifest): string {
    return path.join(
      this.outputDirectory,
      encodePathSegment(manifest.productId),
      "latest",
      encodePathSegment(manifest.phase),
      encodePathSegment(this.resolveManifestSurface(manifest)),
      "artifact-manifest.pointer.json"
    );
  }

  private async validateManifest(manifest: ArtifactManifest): Promise<string[]> {
    const errors: string[] = [];
    try {
      this.generator.validateManifest(manifest);
    } catch (error) {
      errors.push(String(error).replace(/^Error: /, ""));
    }

    for (const artifact of manifest.artifacts) {
      errors.push(...(await this.validateArtifactPath(artifact)));
    }

    return errors;
  }

  private async validateArtifactPath(artifact: ArtifactEntry): Promise<string[]> {
    if (artifact.expected && !artifact.found) {
      return [`required artifact ${artifact.id} is marked missing`];
    }

    const artifactPath = this.resolveArtifactPath(artifact.path);
    if (!this.isInsideArtifactRoot(artifactPath)) {
      return [`artifact path escapes artifact root: ${artifact.path}`];
    }
    try {
      await fs.access(artifactPath);
    } catch (error) {
      if (artifact.expected && artifact.found && isFileNotFound(error)) {
        return [`required artifact path does not exist: ${artifact.path}`];
      }
      if (!isFileNotFound(error)) {
        return [
          `cannot access artifact path ${artifact.path}: ${String(error).replace(
            /^Error: /,
            ""
          )}`,
        ];
      }
      return Array.from(NO_ARTIFACT_VALIDATION_ERRORS);
    }

    const actual = await this.fingerprintCalculator.calculateForPath(artifactPath);
    const errors: string[] = [];
    if (actual.fingerprint.algorithm !== artifact.fingerprint.algorithm) {
      errors.push(
        `artifact ${artifact.id} fingerprint algorithm mismatch: expected ${artifact.fingerprint.algorithm}, got ${actual.fingerprint.algorithm}`
      );
    }
    if (actual.fingerprint.hash !== artifact.fingerprint.hash) {
      errors.push(`artifact ${artifact.id} fingerprint hash mismatch`);
    }
    if (actual.sizeBytes !== artifact.metadata.sizeBytes) {
      errors.push(
        `artifact ${artifact.id} size mismatch: expected ${artifact.metadata.sizeBytes}, got ${actual.sizeBytes}`
      );
    }
    return errors;
  }

  private resolveArtifactPath(artifactPath: string): string {
    return path.resolve(path.isAbsolute(artifactPath)
      ? artifactPath
      : path.join(this.artifactRootDirectory, artifactPath));
  }

  private async readStoredRefs(): Promise<StoredArtifactManifestRefs> {
    try {
      const content = await fs.readFile(this.refsPath, "utf-8");
      const parsed = JSON.parse(content) as Partial<StoredArtifactManifestRefs>;
      if (parsed.schemaVersion !== "1.0.0" || !Array.isArray(parsed.manifests)) {
        throw new Error("artifact manifest refs file has invalid shape");
      }
      return {
        schemaVersion: "1.0.0",
        manifests: parsed.manifests,
      };
    } catch (error) {
      if (isFileNotFound(error)) {
        return { schemaVersion: "1.0.0", manifests: [] };
      }
      throw error;
    }
  }

  private async writeStoredRefs(refs: StoredArtifactManifestRefs): Promise<void> {
    await this.writeJsonFile(this.refsPath, refs);
  }

  private async writeLatestPointer(
    manifest: ArtifactManifest,
    ref: LifecycleArtifactManifestRef
  ): Promise<void> {
    const pointer: LatestArtifactManifestPointer = {
      schemaVersion: "1.0.0",
      productUnitId: ref.productUnitId,
      runId: ref.runId,
      phase: manifest.phase,
      surface: this.resolveManifestSurface(manifest),
      manifestPath: ref.manifestPath,
      artifactCount: ref.artifactCount,
      updatedAt: new Date().toISOString(),
    };
    await this.writeJsonFile(this.getLatestPointerPath(manifest), pointer);
  }

  private resolveManifestSurface(manifest: ArtifactManifest): string {
    return manifest.surface ?? "aggregate";
  }

  private async writeJsonFile(filePath: string, content: unknown): Promise<void> {
    if (!this.isInsideOutputDirectory(filePath)) {
      throw new Error(`artifact provider write path escapes output root: ${filePath}`);
    }
    await fs.mkdir(path.dirname(filePath), { recursive: true });
    const tempPath = `${filePath}.${process.pid}.${Date.now()}.tmp`;
    await fs.writeFile(tempPath, `${JSON.stringify(content, null, 2)}\n`, "utf-8");
    await fs.rename(tempPath, filePath);
  }

  private isInsideOutputDirectory(candidatePath: string): boolean {
    return isSamePathOrChild(path.resolve(candidatePath), this.outputDirectory);
  }

  private isInsideArtifactRoot(candidatePath: string): boolean {
    return isSamePathOrChild(path.resolve(candidatePath), this.artifactRootDirectory);
  }
}

function fail(message: string, required: boolean): LifecycleProviderResult {
  return {
    success: false,
    error: required ? message : `optional artifact manifest write skipped: ${message}`,
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

function isSamePathOrChild(candidate: string, root: string): boolean {
  const relative = path.relative(root, candidate);
  return relative === "" || (!relative.startsWith("..") && !path.isAbsolute(relative));
}

function encodePathSegment(segment: string): string {
  return encodeURIComponent(segment.trim()).replace(/\./g, "%2E");
}

function resolveDigestStatus(manifest: ArtifactManifest): "complete" | "partial" | "missing" {
  if (manifest.artifacts.length === 0) {
    return "missing";
  }
  const fingerprinted = manifest.artifacts.filter((artifact) =>
    artifact.fingerprint.hash.trim().length > 0 &&
    artifact.fingerprint.algorithm.trim().length > 0
  ).length;
  if (fingerprinted === manifest.artifacts.length) {
    return "complete";
  }
  return fingerprinted === 0 ? "missing" : "partial";
}
