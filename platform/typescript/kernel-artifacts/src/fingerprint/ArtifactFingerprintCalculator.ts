import { createHash } from 'node:crypto';
import { promises as fs } from 'node:fs';
import * as path from 'node:path';
import type { ArtifactFingerprint } from '../domain/ArtifactManifest.js';

export interface ArtifactFingerprintResult {
  readonly fingerprint: ArtifactFingerprint;
  readonly sizeBytes: number;
}

export class ArtifactFingerprintCalculator {
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
        if (entry.isDirectory()) {
          return this.listFiles(entryPath);
        }

        if (entry.isFile()) {
          return [entryPath];
        }

        return [];
      }),
    );

    return files.flat().sort((left, right) => left.localeCompare(right));
  }
}