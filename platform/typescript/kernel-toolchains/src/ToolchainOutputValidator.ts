import { promises as fs } from 'node:fs';
import * as path from 'node:path';
import type { ToolchainAdapterContext, ToolchainOutputValidationResult, ValidationError } from './ToolchainAdapter.js';

/**
 * Toolchain output validator
 */
export class ToolchainOutputValidator {
  /**
   * Validate that expected outputs were produced by the adapter
   */
  async validateOutputs(
    context: ToolchainAdapterContext,
    expectedOutputs: string[],
  ): Promise<ToolchainOutputValidationResult> {
    const errors: ValidationError[] = [];
    const missingArtifacts: string[] = [];
    const unexpectedArtifacts: string[] = [];

    if (!context.outputDir) {
      return {
        status: 'invalid',
        errors: [{ path: 'outputDir', message: 'Output directory is required for validation' }],
        missingArtifacts: expectedOutputs,
        unexpectedArtifacts: [],
      };
    }

    // Check if output directory exists
    try {
      await fs.access(context.outputDir);
    } catch {
      return {
        status: 'invalid',
        errors: [{ path: 'outputDir', message: `Output directory does not exist: ${context.outputDir}` }],
        missingArtifacts: expectedOutputs,
        unexpectedArtifacts: [],
      };
    }

    // Get all files in output directory
    const producedFiles = await this.listFilesRecursively(context.outputDir);
    const producedFileSet = new Set(producedFiles);

    // Check for missing expected artifacts
    for (const expected of expectedOutputs) {
      const expectedPath = path.join(context.outputDir, expected);
      if (!producedFileSet.has(expectedPath) && !producedFileSet.has(expected)) {
        missingArtifacts.push(expected);
      }
    }

    // Check for unexpected artifacts (files not in expected list)
    for (const produced of producedFiles) {
      const relativePath = path.relative(context.outputDir, produced);
      if (!expectedOutputs.includes(relativePath)) {
        unexpectedArtifacts.push(relativePath);
      }
    }

    // Determine overall status
    let status: 'valid' | 'invalid' | 'partial' = 'valid';
    if (missingArtifacts.length > 0 && unexpectedArtifacts.length === 0) {
      status = 'invalid';
    } else if (missingArtifacts.length === 0 && unexpectedArtifacts.length > 0) {
      status = 'partial';
    } else if (missingArtifacts.length > 0 && unexpectedArtifacts.length > 0) {
      status = 'invalid';
    }

    if (missingArtifacts.length > 0) {
      errors.push({
        path: 'artifacts',
        message: `Missing expected artifacts: ${missingArtifacts.join(', ')}`,
      });
    }

    return {
      status,
      errors,
      missingArtifacts,
      unexpectedArtifacts,
    };
  }

  /**
   * Validate artifact integrity using checksums
   */
  async validateArtifactChecksums(
    outputDir: string,
    expectedChecksums: Record<string, string>,
  ): Promise<ToolchainOutputValidationResult> {
    const errors: ValidationError[] = [];
    const missingArtifacts: string[] = [];
    const unexpectedArtifacts: string[] = [];

    for (const [relativePath, expectedChecksum] of Object.entries(expectedChecksums)) {
      const artifactPath = path.join(outputDir, relativePath);

      try {
        await fs.access(artifactPath);
        const actualChecksum = await this.calculateChecksum(artifactPath);

        if (actualChecksum !== expectedChecksum) {
          errors.push({
            path: relativePath,
            message: `Checksum mismatch: expected ${expectedChecksum}, got ${actualChecksum}`,
          });
        }
      } catch {
        missingArtifacts.push(relativePath);
      }
    }

    const status = errors.length === 0 && missingArtifacts.length === 0 ? 'valid' : 'invalid';

    return {
      status,
      errors,
      missingArtifacts,
      unexpectedArtifacts,
    };
  }

  /**
   * Validate that artifact files meet size constraints
   */
  async validateArtifactSizes(
    outputDir: string,
    sizeConstraints: Record<string, { min?: number; max?: number }>,
  ): Promise<ToolchainOutputValidationResult> {
    const errors: ValidationError[] = [];
    const missingArtifacts: string[] = [];
    const unexpectedArtifacts: string[] = [];

    for (const [relativePath, constraint] of Object.entries(sizeConstraints)) {
      const artifactPath = path.join(outputDir, relativePath);

      try {
        const stats = await fs.stat(artifactPath);
        const sizeBytes = stats.size;

        if (constraint.min !== undefined && sizeBytes < constraint.min) {
          errors.push({
            path: relativePath,
            message: `Artifact too small: ${sizeBytes} bytes (minimum: ${constraint.min} bytes)`,
          });
        }

        if (constraint.max !== undefined && sizeBytes > constraint.max) {
          errors.push({
            path: relativePath,
            message: `Artifact too large: ${sizeBytes} bytes (maximum: ${constraint.max} bytes)`,
          });
        }
      } catch {
        missingArtifacts.push(relativePath);
      }
    }

    const status = errors.length === 0 && missingArtifacts.length === 0 ? 'valid' : 'invalid';

    return {
      status,
      errors,
      missingArtifacts,
      unexpectedArtifacts,
    };
  }

  /**
   * List all files in a directory recursively
   */
  private async listFilesRecursively(dir: string): Promise<string[]> {
    const files: string[] = [];
    const entries = await fs.readdir(dir, { withFileTypes: true });

    for (const entry of entries) {
      const fullPath = path.join(dir, entry.name);

      if (entry.isDirectory()) {
        const subFiles = await this.listFilesRecursively(fullPath);
        files.push(...subFiles);
      } else if (entry.isFile()) {
        files.push(fullPath);
      }
    }

    return files;
  }

  /**
   * Calculate SHA-256 checksum of a file
   */
  private async calculateChecksum(filePath: string): Promise<string> {
    const crypto = await import('node:crypto');
    const content = await fs.readFile(filePath);
    return crypto.createHash('sha256').update(content).digest('hex');
  }
}
