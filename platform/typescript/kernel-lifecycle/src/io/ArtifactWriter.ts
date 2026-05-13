import { promises as fs } from 'node:fs';
import { ProductArtifact } from '../domain/ProductLifecyclePhase.js';

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
    const manifest = {
      schemaVersion: '1.0.0',
      generatedAt: new Date().toISOString(),
      artifacts,
    };

    const dir = outputPath.substring(0, outputPath.lastIndexOf('/'));
    await fs.mkdir(dir, { recursive: true });

    const content = JSON.stringify(manifest, null, 2);
    await fs.writeFile(outputPath, content, 'utf-8');
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
    const dir = outputPath.substring(0, outputPath.lastIndexOf('/'));
    await fs.mkdir(dir, { recursive: true });
    await fs.copyFile(sourcePath, outputPath);
  }

  /**
   * Calculate artifact fingerprint
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
