import * as crypto from 'node:crypto';
import * as fs from 'node:fs/promises';
import * as path from 'node:path';
import type { ProductUnitSurface } from '@ghatana/kernel-product-contracts';
import type { ProductLifecycleStep } from '../domain/ProductLifecyclePhase.js';

/**
 * Surface hash calculator for change detection and skip-safe execution.
 *
 * Computes deterministic hashes for surfaces based on source files, dependencies,
 * and configuration. Enables skip-safe execution by detecting when surfaces
 * have not changed since the last successful run.
 *
 * @doc.type class
 * @doc.purpose Calculate surface hashes for change detection and skip-safe execution
 * @doc.layer kernel
 * @doc.pattern Hash
 */
export class SurfaceHashCalculator {
  private readonly repoRoot: string;
  private readonly cache: Map<string, string> = new Map();

  constructor(repoRoot: string) {
    this.repoRoot = repoRoot;
  }

  /**
   * Computes a hash for a surface based on its source files, dependencies, and configuration.
   *
   * @param surface the surface to hash
   * @param step the lifecycle step (provides additional context)
   * @returns the computed hash
   */
  async computeSurfaceHash(surface: ProductUnitSurface, step: ProductLifecycleStep): Promise<string> {
    const cacheKey = `${surface.id}:${step.phase}`;
    if (this.cache.has(cacheKey)) {
      return this.cache.get(cacheKey)!;
    }

    const hashInputs: string[] = [];

    // Add surface source files
    if (surface.sourceRef) {
      const sourcePath = path.isAbsolute(surface.sourceRef)
        ? surface.sourceRef
        : path.join(this.repoRoot, surface.sourceRef);
      try {
        const sourceHash = await this.hashDirectory(sourcePath);
        hashInputs.push(sourceHash);
      } catch {
        // Source path may not exist or be inaccessible
        hashInputs.push('source:unavailable');
      }
    }

    // Add package path if available
    if (surface.packagePath) {
      const packagePath = path.isAbsolute(surface.packagePath)
        ? surface.packagePath
        : path.join(this.repoRoot, surface.packagePath);
      try {
        const packageHash = await this.hashFile(packagePath);
        hashInputs.push(packageHash);
      } catch {
        hashInputs.push('package:unavailable');
      }
    }

    // Add gradle module if available
    if (surface.gradleModule) {
      const buildPath = path.join(this.repoRoot, surface.gradleModule, 'build.gradle.kts');
      try {
        const buildHash = await this.hashFile(buildPath);
        hashInputs.push(buildHash);
      } catch {
        hashInputs.push('gradle:unavailable');
      }
    }

    // Add Cargo.toml if available
    if (surface.cratePath) {
      const cargoPath = path.join(this.repoRoot, surface.cratePath, 'Cargo.toml');
      try {
        const cargoHash = await this.hashFile(cargoPath);
        hashInputs.push(cargoHash);
      } catch {
        hashInputs.push('cargo:unavailable');
      }
    }

    // Add pyproject.toml if available
    if (surface.pyprojectPath) {
      const pyprojectPath = path.isAbsolute(surface.pyprojectPath)
        ? surface.pyprojectPath
        : path.join(this.repoRoot, surface.pyprojectPath);
      try {
        const pyprojectHash = await this.hashFile(pyprojectPath);
        hashInputs.push(pyprojectHash);
      } catch {
        hashInputs.push('pyproject:unavailable');
      }
    }

    // Add surface configuration
    hashInputs.push(JSON.stringify({
      type: surface.type,
      implementationStatus: surface.implementationStatus,
      language: surface.language,
      runtime: surface.runtime,
      buildSystem: surface.buildSystem,
      adapterHint: surface.adapterHint,
    }));

    // Add step context
    hashInputs.push(JSON.stringify({
      phase: step.phase,
      adapter: step.adapter,
      stepKind: step.stepKind,
    }));

    const combined = hashInputs.join('|');
    const hash = crypto.createHash('sha256').update(combined).digest('hex');
    this.cache.set(cacheKey, hash);
    return hash;
  }

  /**
   * Computes a hash for a directory by hashing all files recursively.
   */
  private async hashDirectory(dirPath: string): Promise<string> {
    const hashes: string[] = [];
    
    async function collectHashes(currentPath: string, relativePath: string): Promise<void> {
      const entries = await fs.readdir(currentPath, { withFileTypes: true });
      
      for (const entry of entries) {
        const entryPath = path.join(currentPath, entry.name);
        const entryRelative = path.join(relativePath, entry.name);
        
        if (entry.isDirectory()) {
          // Skip node_modules, target, dist, build directories
          if (['node_modules', 'target', 'dist', 'build', '.git'].includes(entry.name)) {
            continue;
          }
          await collectHashes(entryPath, entryRelative);
        } else if (entry.isFile()) {
          try {
            const content = await fs.readFile(entryPath);
            const fileHash = crypto.createHash('sha256').update(content).digest('hex');
            hashes.push(`${entryRelative}:${fileHash}`);
          } catch {
            // Skip files that can't be read
          }
        }
      }
    }

    await collectHashes(dirPath, '');
    hashes.sort();
    return crypto.createHash('sha256').update(hashes.join('|')).digest('hex');
  }

  /**
   * Computes a hash for a single file.
   */
  private async hashFile(filePath: string): Promise<string> {
    const content = await fs.readFile(filePath, 'utf-8');
    return crypto.createHash('sha256').update(content).digest('hex');
  }

  /**
   * Clears the hash cache.
   */
  clearCache(): void {
    this.cache.clear();
  }
}
