import { createHash } from "node:crypto";
import * as fs from "node:fs/promises";
import * as os from "node:os";
import * as path from "node:path";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import type { ArtifactManifest } from "@ghatana/kernel-artifacts";
import { FileArtifactProvider } from "../FileArtifactProvider";

describe("FileArtifactProvider", () => {
  let tempDir: string;
  let outputDir: string;
  let artifactRootDir: string;
  let provider: FileArtifactProvider;

  beforeEach(async () => {
    tempDir = await fs.mkdtemp(path.join(os.tmpdir(), "ghatana-artifacts-"));
    outputDir = path.join(tempDir, "out");
    artifactRootDir = path.join(tempDir, "workspace");
    provider = new FileArtifactProvider({
      outputDirectory: outputDir,
      artifactRootDirectory: artifactRootDir,
    });
  });

  afterEach(async () => {
    await fs.rm(tempDir, { recursive: true, force: true });
  });

  it("writes run-scoped artifact manifests and latest pointers", async () => {
    const artifactPath = "dist/index.html";
    await writeArtifactFile(artifactRootDir, artifactPath, "<html></html>");
    const manifest = buildManifest({
      artifactPath,
      hash: hash("<html></html>"),
      sizeBytes: Buffer.byteLength("<html></html>"),
    });

    const result = await provider.writeArtifactManifest(manifest, {
      required: true,
      correlationId: "corr-1",
      runId: "run-1",
    });

    const expectedManifestPath = path.join(
      outputDir,
      "digital-marketing",
      "run-1",
      "build",
      "web",
      "artifact-manifest.json"
    );
    expect(result).toEqual({ success: true, ref: expectedManifestPath });
    await expect(readJson(expectedManifestPath)).resolves.toMatchObject({
      productId: "digital-marketing",
      artifacts: [{ id: "web-dist" }],
    });
    await expect(
      readJson(
        path.join(
          outputDir,
          "digital-marketing",
          "latest",
          "build",
          "web",
          "artifact-manifest.pointer.json"
        )
      )
    ).resolves.toMatchObject({
      runId: "run-1",
      manifestPath: expectedManifestPath,
      artifactCount: 1,
    });
    await expect(
      provider.listArtifactManifests({
        productUnitId: "digital-marketing",
        runId: "run-1",
      })
    ).resolves.toEqual([
      {
        productUnitId: "digital-marketing",
        runId: "run-1",
        manifestPath: expectedManifestPath,
        artifactCount: 1,
        correlationId: "corr-1",
        digestStatus: "complete",
      },
    ]);
    await expect(
      provider.listArtifactManifests({ productUnitId: "finance", runId: "run-1" })
    ).resolves.toEqual([]);
    await expect(
      provider.listArtifactManifests({
        productUnitId: "digital-marketing",
        runId: "run-2",
      })
    ).resolves.toEqual([]);
  });

  it("uses the process working directory as the default artifact root", async () => {
    provider = new FileArtifactProvider({ outputDirectory: outputDir });

    const result = await provider.writeArtifactManifest(
      buildManifest({
        artifactPath: "dist/not-in-cwd.html",
        hash: "a".repeat(64),
      }),
      { required: true, correlationId: "corr-1", runId: "run-1" }
    );

    expect(result).toEqual({
      success: false,
      error: "required artifact path does not exist: dist/not-in-cwd.html",
    });
  });

  it("rejects absolute artifact paths outside the artifact root", async () => {
    const absoluteArtifactPath = path.join(tempDir, "absolute", "index.html");
    await fs.mkdir(path.dirname(absoluteArtifactPath), { recursive: true });
    await fs.writeFile(absoluteArtifactPath, "<html></html>", "utf-8");

    const result = await provider.writeArtifactManifest(
      buildManifest({
        artifactPath: absoluteArtifactPath,
        hash: hash("<html></html>"),
        sizeBytes: Buffer.byteLength("<html></html>"),
      }),
      { required: true, correlationId: "corr-1", runId: "run-1" }
    );

    expect(result).toEqual({
      success: false,
      error: `artifact path escapes artifact root: ${absoluteArtifactPath}`,
    });
  });

  it("deduplicates recorded manifest refs by product, run, and path", async () => {
    const ref = {
      productUnitId: "digital-marketing",
      runId: "run-1",
      manifestPath: path.join(outputDir, "manifest.json"),
      artifactCount: 1,
      correlationId: "corr-1",
      digestStatus: "complete" as const,
    };

    await provider.recordArtifactManifest(ref, {
      required: true,
      correlationId: "corr-1",
    });
    await provider.recordArtifactManifest({ ...ref, artifactCount: 2 }, {
      required: true,
      correlationId: "corr-1",
    });

    await expect(
      provider.listArtifactManifests({ productUnitId: "digital-marketing" })
    ).resolves.toEqual([{ ...ref, artifactCount: 2 }]);
    await expect(
      provider.listArtifactManifests({
        productUnitId: "digital-marketing",
        correlationId: "corr-missing",
      })
    ).resolves.toEqual([]);
  });

  it("rejects artifact manifest refs outside the output root", async () => {
    const result = await provider.recordArtifactManifest(
      {
        productUnitId: "digital-marketing",
        runId: "run-1",
        manifestPath: path.join(tempDir, "outside.json"),
        artifactCount: 1,
      },
      { required: true, correlationId: "corr-1" }
    );

    expect(result).toEqual({
      success: false,
      error: `artifact manifest path escapes output root: ${path.join(tempDir, "outside.json")}`,
    });
  });

  it("rejects artifact paths outside the artifact root", async () => {
    const outsideArtifactPath = path.join(tempDir, "outside", "index.html");
    await fs.mkdir(path.dirname(outsideArtifactPath), { recursive: true });
    await fs.writeFile(outsideArtifactPath, "<html></html>", "utf-8");

    const result = await provider.writeArtifactManifest(
      buildManifest({
        artifactPath: outsideArtifactPath,
        hash: hash("<html></html>"),
        sizeBytes: Buffer.byteLength("<html></html>"),
      }),
      { required: true, correlationId: "corr-1", runId: "run-1" }
    );

    expect(result).toEqual({
      success: false,
      error: `artifact path escapes artifact root: ${outsideArtifactPath}`,
    });
  });

  it("rejects invalid manifest schema before writing", async () => {
    const result = await provider.writeArtifactManifest(
      { ...buildManifest({ hash: "a".repeat(64) }), productId: "" },
      { required: true, correlationId: "corr-1", runId: "run-1" }
    );

    expect(result.success).toBe(false);
    expect(result.error).toContain("productId");
    await expect(fs.access(outputDir)).rejects.toMatchObject({ code: "ENOENT" });
  });

  it("fails closed when a required artifact path is missing", async () => {
    const result = await provider.writeArtifactManifest(
      buildManifest({ artifactPath: "dist/missing.html", hash: "a".repeat(64) }),
      { required: true, correlationId: "corr-1", runId: "run-1" }
    );

    expect(result).toEqual({
      success: false,
      error: "required artifact path does not exist: dist/missing.html",
    });
  });

  it("allows optional missing artifacts that are not expected", async () => {
    const manifest = buildManifest({
      artifactPath: "dist/optional.html",
      expected: false,
      found: false,
      hash: "a".repeat(64),
    });

    const result = await provider.writeArtifactManifest(manifest, {
      required: true,
      correlationId: "corr-1",
      runId: "run-1",
    });

    expect(result.success).toBe(true);
  });

  it("rejects required artifacts explicitly marked missing", async () => {
    const result = await provider.writeArtifactManifest(
      buildManifest({ found: false, hash: "a".repeat(64) }),
      { required: true, correlationId: "corr-1", runId: "run-1" }
    );

    expect(result).toEqual({
      success: false,
      error: "required artifact web-dist is marked missing",
    });
  });

  it("validates fingerprints and sizes when paths exist", async () => {
    const artifactPath = "dist/index.html";
    await writeArtifactFile(artifactRootDir, artifactPath, "<html></html>");

    const result = await provider.writeArtifactManifest(
      buildManifest({
        artifactPath,
        algorithm: "sha512",
        hash: "b".repeat(128),
        sizeBytes: 999,
      }),
      { required: true, correlationId: "corr-1", runId: "run-1" }
    );

    expect(result.success).toBe(false);
    expect(result.error).toContain("fingerprint algorithm mismatch");
    expect(result.error).toContain("fingerprint hash mismatch");
    expect(result.error).toContain("size mismatch");
  });

  it("fails closed when an artifact path cannot be inspected", async () => {
    const fileRoot = path.join(tempDir, "workspace-file");
    await fs.writeFile(fileRoot, "not a directory", "utf-8");
    provider = new FileArtifactProvider({
      outputDirectory: outputDir,
      artifactRootDirectory: fileRoot,
    });

    const result = await provider.writeArtifactManifest(
      buildManifest({ artifactPath: "dist/index.html", hash: "a".repeat(64) }),
      { required: true, correlationId: "corr-1", runId: "run-1" }
    );

    expect(result.success).toBe(false);
    expect(result.error).toContain("cannot access artifact path dist/index.html");
  });

  it("fails manifest writes when ref indexing fails", async () => {
    const artifactPath = "dist/index.html";
    await writeArtifactFile(artifactRootDir, artifactPath, "<html></html>");
    await fs.mkdir(outputDir, { recursive: true });
    await fs.writeFile(
      path.join(outputDir, "artifact-manifests.json"),
      JSON.stringify({ schemaVersion: "1.0.0", manifests: {} }),
      "utf-8"
    );

    const result = await provider.writeArtifactManifest(
      buildManifest({
        artifactPath,
        hash: hash("<html></html>"),
        sizeBytes: Buffer.byteLength("<html></html>"),
      }),
      { required: true, correlationId: "corr-1", runId: "run-1" }
    );

    expect(result).toEqual({
      success: false,
      error: "artifact manifest refs file has invalid shape",
    });
  });

  it("returns an explicit optional failure when correlation id is missing", async () => {
    const result = await provider.recordArtifactManifest(
      {
        productUnitId: "digital-marketing",
        runId: "run-1",
        manifestPath: path.join(outputDir, "artifact-manifest.json"),
        artifactCount: 1,
      },
      { required: false, correlationId: " " }
    );

    expect(result).toEqual({
      success: false,
      error:
        "optional artifact manifest write skipped: artifact manifest write requires correlationId",
    });
  });

  it("fails closed when stored manifest refs are malformed", async () => {
    await fs.mkdir(outputDir, { recursive: true });
    await fs.writeFile(
      path.join(outputDir, "artifact-manifests.json"),
      JSON.stringify({ schemaVersion: "1.0.0", manifests: {} }),
      "utf-8"
    );

    const result = await provider.recordArtifactManifest(
      {
        productUnitId: "digital-marketing",
        runId: "run-1",
        manifestPath: path.join(outputDir, "artifact-manifest.json"),
        artifactCount: 1,
      },
      { required: true, correlationId: "corr-1" }
    );

    expect(result).toEqual({
      success: false,
      error: "artifact manifest refs file has invalid shape",
    });
  });
});

interface ManifestOverrides {
  readonly artifactPath?: string;
  readonly algorithm?: "sha256" | "sha512";
  readonly hash?: string;
  readonly sizeBytes?: number;
  readonly expected?: boolean;
  readonly found?: boolean;
}

function buildManifest(overrides: ManifestOverrides = {}): ArtifactManifest {
  return {
    schemaVersion: "1.0.0",
    productId: "digital-marketing",
    phase: "build",
    surface: "web",
    timestamp: "2026-05-14T00:00:00.000Z",
    artifacts: [
      {
        id: "web-dist",
        path: overrides.artifactPath ?? "dist/index.html",
        metadata: {
          type: "static-web-bundle",
          packaging: "static-files",
          version: "1.0.0",
          buildNumber: "42",
          gitCommit: "abcdef0",
          gitBranch: "main",
          timestamp: "2026-05-14T00:00:00.000Z",
          sizeBytes: overrides.sizeBytes ?? 12,
        },
        fingerprint: {
          algorithm: overrides.algorithm ?? "sha256",
          hash: overrides.hash ?? hash("<html></html>"),
        },
        expected: overrides.expected ?? true,
        found: overrides.found ?? true,
      },
    ],
  };
}

async function writeArtifactFile(
  rootDirectory: string,
  artifactPath: string,
  content: string
): Promise<void> {
  const fullPath = path.join(rootDirectory, artifactPath);
  await fs.mkdir(path.dirname(fullPath), { recursive: true });
  await fs.writeFile(fullPath, content, "utf-8");
}

async function readJson(filePath: string): Promise<unknown> {
  return JSON.parse(await fs.readFile(filePath, "utf-8"));
}

function hash(content: string): string {
  return createHash("sha256").update(content).digest("hex");
}
