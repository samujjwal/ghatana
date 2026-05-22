/**
 * Surface hash computation for change detection.
 *
 * Computes a hash of a surface's source code and configuration to detect when
 * the surface has changed and needs to be re-executed in lifecycle phases.
 *
 * @doc.type class
 * @doc.purpose Compute surface hashes for change detection and affected surface analysis
 * @doc.layer kernel-lifecycle
 * @doc.pattern ValueObject
 */

import crypto from "crypto";
import { promises as fs } from "fs";
import path from "path";

export interface SurfaceHashResult {
  readonly surfaceId: string;
  readonly hash: string;
  readonly computedAt: Date;
  readonly sourceFiles: readonly string[];
  readonly configFiles: readonly string[];
}

/**
 * Computes cryptographic hashes for product surfaces to detect changes.
 */
export class SurfaceHashComputer {
  private readonly algorithm = "sha256";

  /**
   * Computes a hash for a surface based on its source files and configuration.
   *
   * @param surfacePath the path to the surface
   * @param surfaceId the surface identifier
   * @returns the hash result
   */
  async computeHash(surfacePath: string, surfaceId: string): Promise<SurfaceHashResult> {
    const hash = crypto.createHash(this.algorithm);
    const sourceFiles: string[] = [];
    const configFiles: string[] = [];

    // Hash source files
    const srcDir = path.join(surfacePath, "src");
    if (await this.directoryExists(srcDir)) {
      const files = await this.getAllFiles(srcDir);
      for (const file of files) {
        const content = await fs.readFile(file, "utf-8");
        hash.update(content);
        sourceFiles.push(file);
      }
    }

    // Hash configuration files
    const configFilesToHash = [
      "package.json",
      "tsconfig.json",
      "Cargo.toml",
      "pyproject.toml",
      "build.gradle.kts",
      "build.gradle",
      "pom.xml",
    ];

    for (const configFile of configFilesToHash) {
      const configPath = path.join(surfacePath, configFile);
      if (await this.fileExists(configPath)) {
        const content = await fs.readFile(configPath, "utf-8");
        hash.update(content);
        configFiles.push(configPath);
      }
    }

    const digest = hash.digest("hex");

    return {
      surfaceId,
      hash: digest,
      computedAt: new Date(),
      sourceFiles: [...sourceFiles],
      configFiles: [...configFiles],
    };
  }

  /**
   * Computes hashes for multiple surfaces in parallel.
   *
   * @param surfaces the surfaces to hash
   * @returns the hash results
   */
  async computeHashes(
    surfaces: readonly { readonly surfaceId: string; readonly surfacePath: string }[]
  ): Promise<readonly SurfaceHashResult[]> {
    return Promise.all(
      surfaces.map((s) => this.computeHash(s.surfacePath, s.surfaceId))
    );
  }

  /**
   * Compares two hash results to determine if a surface has changed.
   *
   * @param previous the previous hash result
   * @param current the current hash result
   * @returns true if the surface has changed
   */
  hasChanged(previous: SurfaceHashResult, current: SurfaceHashResult): boolean {
    return previous.hash !== current.hash;
  }

  private async directoryExists(dir: string): Promise<boolean> {
    try {
      const stat = await fs.stat(dir);
      return stat.isDirectory();
    } catch {
      return false;
    }
  }

  private async fileExists(file: string): Promise<boolean> {
    try {
      const stat = await fs.stat(file);
      return stat.isFile();
    } catch {
      return false;
    }
  }

  private async getAllFiles(dir: string): Promise<string[]> {
    const files: string[] = [];
    const entries = await fs.readdir(dir, { withFileTypes: true });

    for (const entry of entries) {
      const fullPath = path.join(dir, entry.name);
      if (entry.isDirectory()) {
        const subFiles = await this.getAllFiles(fullPath);
        files.push(...subFiles);
      } else if (entry.isFile()) {
        files.push(fullPath);
      }
    }

    return files;
  }
}
