import { promises as fs } from 'node:fs';
import * as path from 'node:path';
import type { ArtifactEntry, ArtifactFingerprint, ArtifactPackaging, ArtifactType } from '../domain/ArtifactManifest.js';
import { ArtifactFingerprintCalculator } from '../fingerprint/ArtifactFingerprintCalculator.js';

/**
 * Product artifact resolver
 */
export class ProductArtifactResolver {
  private readonly fingerprintCalculator: ArtifactFingerprintCalculator;

  constructor(fingerprintCalculator: ArtifactFingerprintCalculator = new ArtifactFingerprintCalculator()) {
    this.fingerprintCalculator = fingerprintCalculator;
  }

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
    const result = await this.fingerprintCalculator.calculateForPath(filePath);
    return result.fingerprint;
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
        const fingerprintResult = await this.fingerprintCalculator.calculateForPath(fullPath);
        
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
            sizeBytes: stats.isDirectory() ? fingerprintResult.sizeBytes : stats.size,
          },
          fingerprint: fingerprintResult.fingerprint,
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
  private inferArtifactType(filename: string): ArtifactType {
    if (filename.endsWith('.jar')) return 'jvm-service';
    if (filename.endsWith('.tar.gz') || filename.endsWith('.tgz')) return 'container-image';
    if (filename.endsWith('.apk') || filename.endsWith('.aab') || filename.endsWith('.ipa')) return 'mobile-bundle';
    if (filename.endsWith('.zip')) return 'sdk-package';
    if (filename.includes('test-report')) return 'test-report';
    if (filename.includes('coverage')) return 'coverage-report';
    if (filename.endsWith('.map')) return 'source-map';
    return 'documentation';
  }

  private inferArtifactPackaging(filename: string): ArtifactPackaging {
    if (filename.endsWith('.jar')) return 'jar';
    if (filename.endsWith('.tar.gz') || filename.endsWith('.tgz')) return 'container';
    if (filename.endsWith('.apk')) return 'apk';
    if (filename.endsWith('.aab')) return 'aab';
    if (filename.endsWith('.ipa')) return 'ipa';
    if (filename.endsWith('.zip')) return 'distribution';
    if (filename.endsWith('.json')) return 'json';
    if (filename.endsWith('.xml')) return 'xml';
    return 'static-files';
  }
}
