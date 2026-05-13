import { promises as fs } from 'node:fs';
import * as path from 'node:path';
import type { ArtifactEntry } from '../domain/ArtifactManifest.js';

/**
 * Product artifact registry
 */
export class ProductArtifactRegistry {
  private registryPath: string;

  constructor(configDir: string = '/Users/samujjwal/Development/ghatana/config') {
    this.registryPath = path.join(configDir, 'artifact-registry.json');
  }

  /**
   * Register an artifact
   */
  async registerArtifact(artifact: ArtifactEntry): Promise<void> {
    const registry = await this.loadRegistry();
    
    if (!registry.artifacts[artifact.id]) {
      registry.artifacts[artifact.id] = artifact;
      await this.saveRegistry(registry);
    }
  }

  /**
   * Get an artifact by ID
   */
  async getArtifact(artifactId: string): Promise<ArtifactEntry | undefined> {
    const registry = await this.loadRegistry();
    return registry.artifacts[artifactId];
  }

  /**
   * Get artifacts by product
   */
  async getArtifactsByProduct(productId: string): Promise<ArtifactEntry[]> {
    const registry = await this.loadRegistry();
    return Object.values(registry.artifacts).filter((a) => a.id.startsWith(productId));
  }

  /**
   * Get artifacts by surface
   */
  async getArtifactsBySurface(surface: string): Promise<ArtifactEntry[]> {
    const registry = await this.loadRegistry();
    return Object.values(registry.artifacts).filter((a) => a.path.includes(surface));
  }

  /**
   * Update artifact status
   */
  async updateArtifactStatus(artifactId: string, found: boolean): Promise<void> {
    const registry = await this.loadRegistry();
    
    if (registry.artifacts[artifactId]) {
      registry.artifacts[artifactId].found = found;
      await this.saveRegistry(registry);
    }
  }

  /**
   * Load registry
   */
  private async loadRegistry(): Promise<ArtifactRegistry> {
    try {
      const content = await fs.readFile(this.registryPath, 'utf-8');
      return JSON.parse(content) as ArtifactRegistry;
    } catch {
      return { version: '1.0.0', artifacts: {} };
    }
  }

  /**
   * Save registry
   */
  private async saveRegistry(registry: ArtifactRegistry): Promise<void> {
    const dir = path.dirname(this.registryPath);
    await fs.mkdir(dir, { recursive: true });
    await fs.writeFile(this.registryPath, JSON.stringify(registry, null, 2), 'utf-8');
  }
}

/**
 * Artifact registry
 */
export interface ArtifactRegistry {
  version: string;
  artifacts: Record<string, ArtifactEntry>;
}
