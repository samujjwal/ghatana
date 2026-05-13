import { promises as fs } from 'node:fs';
import * as path from 'node:path';
import type { ArtifactEntry, ArtifactFingerprint } from '../domain/ArtifactManifest.js';

/**
 * Product artifact resolver
 */
export class ProductArtifactResolver {
  /**
   * Resolve artifact path by ID
   */
  async resolveArtifactPath(artifactId: string, searchPaths: string[]): Promise<string | null> {
    for (const searchPath of searchPaths) {
      const artifactPath = path.join(searchPath, artifactId);
      try {
        await fs.access(artifactPath);
        return artifactPath;
      } catch {
        continue;
      }
    }
    return null;
  }

  /**
   * Resolve artifact by fingerprint
   */
  async resolveArtifactByFingerprint(
    fingerprint: ArtifactFingerprint,
    searchPaths: string[],
  ): Promise<ArtifactEntry | null> {
    for (const searchPath of searchPaths) {
      const entries = await this.listArtifactsInPath(searchPath);
      
      for (const entry of entries) {
        const entryFingerprint = await this.calculateFingerprint(entry.path);
        
        if (entryFingerprint.hash === fingerprint.hash && entryFingerprint.algorithm === fingerprint.algorithm) {
          return entry;
        }
      }
    }
    return null;
  }

  /**
   * Resolve artifacts for a product surface
   */
  async resolveArtifactsForSurface(
    productId: string,
    surface: string,
    searchPaths: string[],
  ): Promise<ArtifactEntry[]> {
    const artifacts: ArtifactEntry[] = [];
    
    for (const searchPath of searchPaths) {
      const surfacePath = path.join(searchPath, productId, surface);
      
      try {
        await fs.access(surfacePath);
        const entries = await this.listArtifactsInPath(surfacePath);
        artifacts.push(...entries);
      } catch {
        continue;
      }
    }
    
    return artifacts;
  }

  /**
   * Calculate artifact fingerprint
   */
  async calculateFingerprint(filePath: string): Promise<ArtifactFingerprint> {
    const crypto = await import('node:crypto');
    const content = await fs.readFile(filePath);
    const hash = crypto.createHash('sha256').update(content).digest('hex');
    
    return {
      algorithm: 'sha256',
      hash,
    };
  }

  /**
   * List artifacts in a path
   */
  private async listArtifactsInPath(dirPath: string): Promise<ArtifactEntry[]> {
    const artifacts: ArtifactEntry[] = [];
    const entries = await fs.readdir(dirPath, { withFileTypes: true });

    for (const entry of entries) {
      const fullPath = path.join(dirPath, entry.name);

      if (entry.isFile()) {
        const stats = await fs.stat(fullPath);
        const fingerprint = await this.calculateFingerprint(fullPath);
        
        artifacts.push({
          id: entry.name,
          path: fullPath,
          metadata: {
            type: this.inferArtifactType(entry.name),
            version: '1.0.0',
            buildNumber: '0',
            gitCommit: undefined,
            gitBranch: undefined,
            timestamp: new Date().toISOString(),
            sizeBytes: stats.size,
          },
          fingerprint,
          expected: false,
          found: true,
        });
      }
    }

    return artifacts;
  }

  /**
   * Infer artifact type from filename
   */
  private inferArtifactType(filename: string): 'jar' | 'war' | 'static-web-bundle' | 'docker-image' | 'npm-package' | 'test-report' | 'coverage-report' | 'source-map' | 'documentation' {
    if (filename.endsWith('.jar')) return 'jar';
    if (filename.endsWith('.war')) return 'war';
    if (filename.endsWith('.tar.gz') || filename.endsWith('.tgz')) return 'docker-image';
    if (filename.endsWith('.zip')) return 'npm-package';
    if (filename.includes('test-report')) return 'test-report';
    if (filename.includes('coverage')) return 'coverage-report';
    if (filename.endsWith('.map')) return 'source-map';
    return 'documentation';
  }
}
