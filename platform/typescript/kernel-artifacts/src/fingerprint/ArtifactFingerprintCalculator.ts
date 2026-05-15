import { createHash } from 'node:crypto';
import { promises as fs } from 'node:fs';
import * as path from 'node:path';
import type { ArtifactFingerprint } from '../domain/ArtifactManifest.js';

export interface ArtifactFingerprintResult {
  readonly fingerprint: ArtifactFingerprint;
  readonly sizeBytes: number;
}

export interface ArtifactFingerprintCalculatorOptions {
  readonly ignorePatterns?: readonly string[];
}

export class ArtifactFingerprintCalculator {
  private readonly ignorePatterns: readonly string[];

  constructor(options: ArtifactFingerprintCalculatorOptions = {}) {
    this.ignorePatterns = options.ignorePatterns ?? [
      '.DS_Store',
      'Thumbs.db',
      '*.tmp',
      '*.temp',
      '*.log',
      '.vite/**',
      '.cache/**',
      'coverage/**',
      'node_modules/**',
    ];
  }

  async calculateForPath(targetPath: string): Promise<ArtifactFingerprintResult> {
    const stats = await fs.stat(targetPath);
    if (stats.isDirectory()) {
      return this.calculateDirectory(targetPath);
    }

    return this.calculateFile(targetPath);
  }

  async calculateFile(filePath: string): Promise<ArtifactFingerprintResult> {
    const content = await fs.readFile(filePath);
    return {
      fingerprint: {
        algorithm: 'sha256',
        hash: createHash('sha256').update(content).digest('hex'),
      },
      sizeBytes: content.byteLength,
    };
  }

  async calculateDirectory(directoryPath: string): Promise<ArtifactFingerprintResult> {
    const files = await this.listFiles(directoryPath);
    const hash = createHash('sha256');
    let sizeBytes = 0;

    for (const filePath of files) {
      const relativePath = path.relative(directoryPath, filePath).replace(/\\/g, '/');
      const content = await fs.readFile(filePath);
      hash.update(relativePath);
      hash.update(content);
      sizeBytes += content.byteLength;
    }

    return {
      fingerprint: {
        algorithm: 'sha256',
        hash: hash.digest('hex'),
      },
      sizeBytes,
    };
  }

  private async listFiles(directoryPath: string): Promise<string[]> {
    const entries = await fs.readdir(directoryPath, { withFileTypes: true });
    const files = await Promise.all(
      entries.map(async (entry) => {
        const entryPath = path.join(directoryPath, entry.name);
        const relativePath = path.relative(directoryPath, entryPath).replace(/\\/g, '/');
        if (this.shouldIgnore(relativePath)) {
          return [];
        }

        if (entry.isDirectory()) {
          return this.listFilesRecursive(directoryPath, entryPath);
        }

        if (entry.isSymbolicLink()) {
          return [];
        }

        if (entry.isFile()) {
          return [entryPath];
        }

        return [];
      }),
    );

    return files.flat().sort((left, right) => left.localeCompare(right));
  }

  private async listFilesRecursive(rootPath: string, directoryPath: string): Promise<string[]> {
    const entries = await fs.readdir(directoryPath, { withFileTypes: true });
    const files = await Promise.all(
      entries.map(async (entry) => {
        const entryPath = path.join(directoryPath, entry.name);
        const relativePath = path.relative(rootPath, entryPath).replace(/\\/g, '/');
        if (this.shouldIgnore(relativePath)) {
          return [];
        }

        if (entry.isDirectory()) {
          return this.listFilesRecursive(rootPath, entryPath);
        }

        if (entry.isSymbolicLink()) {
          return [];
        }

        if (entry.isFile()) {
          return [entryPath];
        }

        return [];
      }),
    );

    return files.flat();
  }

  private shouldIgnore(relativePath: string): boolean {
    return this.ignorePatterns.some((pattern) => this.matchesPattern(relativePath, pattern));
  }

  private matchesPattern(relativePath: string, pattern: string): boolean {
    if (pattern.endsWith('/**')) {
      const directory = pattern.slice(0, -3);
      return relativePath === directory ||
        relativePath.startsWith(`${directory}/`) ||
        relativePath.includes(`/${directory}/`) ||
        relativePath.endsWith(`/${directory}`);
    }

    if (pattern.startsWith('*.')) {
      return relativePath.endsWith(pattern.slice(1));
    }

    return relativePath === pattern || relativePath.endsWith(`/${pattern}`);
  }
}
