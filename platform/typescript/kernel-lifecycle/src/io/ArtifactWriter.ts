import { promises as fs } from 'node:fs';
import * as path from 'node:path';
import { ProductArtifact } from '../domain/ProductLifecyclePhase.js';

/**
 * Container image artifact descriptor.
 */
export interface ContainerImageArtifact {
  id: string;
  surface: string;
  producedBy: string;
  image: string;
  tag: string;
  digest?: string;
  localImageId?: string;
  metadata?: Record<string, unknown>;
}

/**
 * Artifact writer
 */
export class ArtifactWriter {
  /**
   * Write artifact manifest to file
   */
  async writeArtifactManifest(
    artifacts: ProductArtifact[],
    outputPath: string,
  ): Promise<void> {
    const manifest: ArtifactManifest = {
      schemaVersion: '1.0.0',
      generatedAt: new Date().toISOString(),
      artifacts,
    };

    const dir = path.dirname(outputPath);
    await fs.mkdir(dir, { recursive: true });

    await fs.writeFile(outputPath, JSON.stringify(manifest, null, 2), 'utf-8');
  }

  /**
   * Write a container image artifact to the manifest.
   *
   * Converts the container image descriptor into a ProductArtifact and writes
   * an artifact-manifest.json to outputDir.
   */
  async writeContainerImageArtifact(
    artifact: ContainerImageArtifact,
    outputDir: string,
  ): Promise<ProductArtifact> {
    const imageRef = `${artifact.image}:${artifact.tag}`;
    const productArtifact: ProductArtifact = {
      id: artifact.id,
      surface: artifact.surface,
      type: 'container-image',
      path: imageRef,
      fingerprint: artifact.digest ?? '',
      producedBy: artifact.producedBy,
      image: artifact.image,
      tag: artifact.tag,
      digest: artifact.digest,
      localImageId: artifact.localImageId,
      metadata: artifact.metadata,
    };

    const manifestPath = path.join(outputDir, 'artifact-manifest.json');
    await this.writeArtifactManifest([productArtifact], manifestPath);

    return productArtifact;
  }

  /**
   * Read artifact manifest from file
   */
  async readArtifactManifest(inputPath: string): Promise<ArtifactManifest> {
    const content = await fs.readFile(inputPath, 'utf-8');
    return JSON.parse(content) as ArtifactManifest;
  }

  /**
   * Copy artifact to output directory
   */
  async copyArtifact(
    sourcePath: string,
    outputPath: string,
  ): Promise<void> {
    const dir = path.dirname(outputPath);
    await fs.mkdir(dir, { recursive: true });
    await fs.copyFile(sourcePath, outputPath);
  }

  /**
   * Calculate artifact fingerprint (SHA-256 of file contents)
   */
  async calculateFingerprint(filePath: string): Promise<string> {
    const crypto = await import('node:crypto');
    const content = await fs.readFile(filePath);
    return crypto.createHash('sha256').update(content).digest('hex');
  }

  /**
   * Verify artifact fingerprint
   */
  async verifyFingerprint(
    filePath: string,
    expectedFingerprint: string,
  ): Promise<boolean> {
    const actualFingerprint = await this.calculateFingerprint(filePath);
    return actualFingerprint === expectedFingerprint;
  }
}

/**
 * Artifact manifest
 */
export interface ArtifactManifest {
  schemaVersion: string;
  generatedAt: string;
  artifacts: ProductArtifact[];
}
