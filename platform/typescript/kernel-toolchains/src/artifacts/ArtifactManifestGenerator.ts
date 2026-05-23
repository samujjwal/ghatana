import { createHash } from 'node:crypto';
import { promises as fs } from 'node:fs';
import * as path from 'node:path';
import type { ToolchainAdapterContext } from '../ToolchainAdapter.js';

/**
 * Artifact type mapping for manifest
 */
export type ArtifactType =
  | 'jar'
  | 'war'
  | 'static-web-bundle'
  | 'docker-image'
  | 'npm-package'
  | 'test-report'
  | 'coverage-report'
  | 'source-map'
  | 'documentation';

/**
 * Artifact entry in the manifest
 */
export interface ArtifactEntry {
  id: string;
  path: string;
  metadata: {
    type: ArtifactType;
    version: string;
    buildNumber?: string;
    gitCommit?: string;
    gitBranch?: string;
    timestamp: string;
    sizeBytes: number;
    // P1-03: Enhanced metadata for production-grade fingerprinting
    buildCommand?: string;
    runtime?: string;
    target?: string;
    language?: string;
  };
  fingerprint: {
    algorithm: 'sha256' | 'sha512' | 'md5';
    hash: string;
  };
  expected: boolean;
  found: boolean;
}

/**
 * Product artifact manifest (matches schema in config/product-artifact-manifest.schema.json)
 */
export interface ProductArtifactManifest {
  schemaVersion: string;
  productId: string;
  phase: string;
  surface: string;
  timestamp: string;
  artifacts: ArtifactEntry[];
}

/**
 * Utility for generating product artifact manifests
 */
export class ArtifactManifestGenerator {
  /**
   * Generate a manifest for artifacts
   */
  static async generateManifest(
    context: ToolchainAdapterContext,
    artifacts: Array<{
      path: string;
      type: ArtifactType;
      id?: string;
    }>
  ): Promise<ProductArtifactManifest> {
    const timestamp = new Date().toISOString();
    const manifestEntries: ArtifactEntry[] = [];

    for (const artifact of artifacts) {
      const absolutePath = path.isAbsolute(artifact.path) ? artifact.path : path.join(context.outputDir || process.cwd(), artifact.path);

      const found = await this.exists(absolutePath);
      const sizeBytes = found ? await this.getFileSize(absolutePath) : 0;
      const hash = found ? await this.calculateSHA256(absolutePath) : '';

      const artifactId = artifact.id || `${path.basename(artifact.path)}-${context.phase}`;

      // Build metadata with proper optional field handling
      const metadata: ArtifactEntry['metadata'] = {
        type: artifact.type,
        version: context.metadata?.version || '0.0.0',
        timestamp,
        sizeBytes,
      };

      // Add optional fields only if they have values
      if (context.metadata?.buildNumber) {
        metadata.buildNumber = context.metadata.buildNumber;
      }
      if (context.metadata?.gitCommit) {
        metadata.gitCommit = context.metadata.gitCommit;
      }
      if (context.metadata?.gitBranch) {
        metadata.gitBranch = context.metadata.gitBranch;
      }

      // P1-03: Add enhanced metadata from surfaceConfig
      if (typeof context.surfaceConfig.buildCommand === 'string') {
        metadata.buildCommand = context.surfaceConfig.buildCommand;
      }
      if (typeof context.surfaceConfig.runtime === 'string') {
        metadata.runtime = context.surfaceConfig.runtime;
      }
      if (typeof context.surfaceConfig.target === 'string') {
        metadata.target = context.surfaceConfig.target;
      }
      if (typeof context.surfaceConfig.language === 'string') {
        metadata.language = context.surfaceConfig.language;
      }

      manifestEntries.push({
        id: artifactId,
        path: artifact.path,
        metadata,
        fingerprint: {
          algorithm: 'sha256',
          hash,
        },
        expected: true,
        found,
      });
    }

    // Determine surface ID - prefer explicit id or use type
    let surfaceId: string;
    if (context.surface && typeof (context.surface as any).id === 'string' && (context.surface as any).id) {
      surfaceId = (context.surface as any).id;
    } else if (context.surface && context.surface.type) {
      surfaceId = context.surface.type;
    } else {
      surfaceId = 'unknown';
    }

    return {
      schemaVersion: '1.0.0',
      productId: context.productId || 'unknown',
      phase: context.phase,
      surface: surfaceId,
      timestamp,
      artifacts: manifestEntries,
    };
  }

  /**
   * Write manifest to file
   */
  static async writeManifest(manifest: ProductArtifactManifest, outputPath: string): Promise<void> {
    const directory = path.dirname(outputPath);
    await fs.mkdir(directory, { recursive: true });
    await fs.writeFile(outputPath, JSON.stringify(manifest, null, 2), 'utf8');
  }

  /**
   * Calculate SHA256 hash of a file
   */
  private static async calculateSHA256(filePath: string): Promise<string> {
    const hash = createHash('sha256');
    const fileContent = await fs.readFile(filePath);
    hash.update(fileContent);
    return hash.digest('hex');
  }

  /**
   * Get file size in bytes
   */
  private static async getFileSize(filePath: string): Promise<number> {
    try {
      const stats = await fs.stat(filePath);
      return stats.size;
    } catch {
      return 0;
    }
  }

  /**
   * Check if file/directory exists
   */
  private static async exists(targetPath: string): Promise<boolean> {
    try {
      await fs.access(targetPath);
      return true;
    } catch {
      return false;
    }
  }
}
