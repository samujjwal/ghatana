import { promises as fs } from 'node:fs';
import * as path from 'node:path';
import type { ArtifactEntry, ArtifactPackaging, ArtifactType } from '../domain/ArtifactManifest.js';

/**
 * Artifact storage
 */
export class ArtifactStorage {
  private baseStoragePath: string;

  constructor(baseStoragePath: string = '/tmp/artifacts') {
    this.baseStoragePath = baseStoragePath;
  }

  /**
   * Store an artifact
   */
  async storeArtifact(
    artifactId: string,
    sourcePath: string,
    productId: string,
    surface: string,
  ): Promise<string> {
    const storagePath = this.getArtifactStoragePath(productId, surface, artifactId);
    const dir = path.dirname(storagePath);
    
    await fs.mkdir(dir, { recursive: true });
    await fs.copyFile(sourcePath, storagePath);
    
    return storagePath;
  }

  /**
   * Retrieve an artifact
   */
  async retrieveArtifact(artifactId: string, productId: string, surface: string): Promise<Buffer> {
    const storagePath = this.getArtifactStoragePath(productId, surface, artifactId);
    return fs.readFile(storagePath);
  }

  /**
   * Delete an artifact
   */
  async deleteArtifact(artifactId: string, productId: string, surface: string): Promise<void> {
    const storagePath = this.getArtifactStoragePath(productId, surface, artifactId);
    await fs.unlink(storagePath);
  }

  /**
   * List artifacts for a product
   */
  async listArtifacts(productId: string, surface?: string): Promise<ArtifactEntry[]> {
    const artifacts: ArtifactEntry[] = [];
    const productPath = surface
      ? path.join(this.baseStoragePath, productId, surface)
      : path.join(this.baseStoragePath, productId);

    try {
      await fs.access(productPath);
      await this.listArtifactsRecursive(productPath, artifacts, productId, surface || '');
    } catch {
      return [];
    }

    return artifacts;
  }

  /**
   * Get artifact storage path
   */
  private getArtifactStoragePath(productId: string, surface: string, artifactId: string): string {
    return path.join(this.baseStoragePath, productId, surface, artifactId);
  }

  /**
   * List artifacts recursively
   */
  private async listArtifactsRecursive(
    dirPath: string,
    artifacts: ArtifactEntry[],
    productId: string,
    surface: string,
  ): Promise<void> {
    const entries = await fs.readdir(dirPath, { withFileTypes: true });

    for (const entry of entries) {
      const fullPath = path.join(dirPath, entry.name);

      if (entry.isDirectory()) {
        await this.listArtifactsRecursive(fullPath, artifacts, productId, surface);
      } else if (entry.isFile()) {
        const stats = await fs.stat(fullPath);
        const crypto = await import('node:crypto');
        const content = await fs.readFile(fullPath);
        const hash = crypto.createHash('sha256').update(content).digest('hex');

        artifacts.push({
          id: entry.name,
          path: fullPath,
          metadata: {
            type: this.inferArtifactType(entry.name),
            packaging: this.inferArtifactPackaging(entry.name),
            version: '1.0.0',
            buildNumber: '0',
            gitCommit: undefined,
            gitBranch: undefined,
            timestamp: new Date().toISOString(),
            sizeBytes: stats.size,
          },
          fingerprint: {
            algorithm: 'sha256',
            hash,
          },
          expected: false,
          found: true,
        });
      }
    }
  }

  /**
   * Infer artifact type from filename
   */
  private inferArtifactType(filename: string): ArtifactType {
    if (filename.endsWith('.jar')) return 'jvm-service';
    if (filename.endsWith('.tar.gz') || filename.endsWith('.tgz')) return 'container-image';
    if (filename.endsWith('.zip')) return 'sdk-package';
    if (filename.includes('test-report')) return 'test-report';
    if (filename.includes('coverage')) return 'coverage-report';
    if (filename.endsWith('.map')) return 'source-map';
    return 'documentation';
  }

  private inferArtifactPackaging(filename: string): ArtifactPackaging {
    if (filename.endsWith('.jar')) return 'jar';
    if (filename.endsWith('.tar.gz') || filename.endsWith('.tgz')) return 'container';
    if (filename.endsWith('.zip')) return 'distribution';
    if (filename.endsWith('.json')) return 'json';
    if (filename.endsWith('.xml')) return 'xml';
    return 'static-files';
  }
}
