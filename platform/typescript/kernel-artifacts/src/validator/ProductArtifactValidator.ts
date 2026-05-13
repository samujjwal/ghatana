import type { ArtifactEntry, ArtifactFingerprint } from '../domain/ArtifactManifest.js';

/**
 * Product artifact validator
 */
export class ProductArtifactValidator {
  /**
   * Validate artifact entry
   */
  validateArtifact(artifact: ArtifactEntry): ValidationError[] {
    const errors: ValidationError[] = [];

    if (!artifact.id || artifact.id.trim().length === 0) {
      errors.push({ path: 'id', message: 'Artifact ID is required' });
    }

    if (!artifact.path || artifact.path.trim().length === 0) {
      errors.push({ path: 'path', message: 'Artifact path is required' });
    }

    if (!artifact.metadata.type) {
      errors.push({ path: 'metadata.type', message: 'Artifact type is required' });
    }

    if (!artifact.metadata.version) {
      errors.push({ path: 'metadata.version', message: 'Artifact version is required' });
    }

    if (!artifact.fingerprint.hash) {
      errors.push({ path: 'fingerprint.hash', message: 'Fingerprint hash is required' });
    }

    this.validateFingerprint(artifact.fingerprint, errors);

    if (artifact.metadata.sizeBytes < 0) {
      errors.push({ path: 'metadata.sizeBytes', message: 'Size bytes must be non-negative' });
    }

    return errors;
  }

  /**
   * Validate fingerprint
   */
  validateFingerprint(fingerprint: ArtifactFingerprint, errors: ValidationError[]): void {
    const validAlgorithms = ['sha256', 'sha512', 'md5'];

    if (!validAlgorithms.includes(fingerprint.algorithm)) {
      errors.push({
        path: 'fingerprint.algorithm',
        message: `Invalid algorithm: ${fingerprint.algorithm}. Must be one of: ${validAlgorithms.join(', ')}`,
      });
    }

    if (fingerprint.hash.length === 0) {
      errors.push({ path: 'fingerprint.hash', message: 'Fingerprint hash is required' });
    }

    const expectedLengths: Record<string, number> = {
      sha256: 64,
      sha512: 128,
      md5: 32,
    };

    const expectedLength = expectedLengths[fingerprint.algorithm];
    if (fingerprint.hash.length !== expectedLength) {
      errors.push({
        path: 'fingerprint.hash',
        message: `Fingerprint hash must be ${expectedLength} characters for ${fingerprint.algorithm}`,
      });
    }

    if (!/^[a-f0-9]+$/i.test(fingerprint.hash)) {
      errors.push({ path: 'fingerprint.hash', message: 'Fingerprint hash must be a valid hexadecimal string' });
    }
  }

  /**
   * Validate artifact type
   */
  validateArtifactType(type: string): ValidationError[] {
    const errors: ValidationError[] = [];
    const validTypes = ['jar', 'war', 'static-web-bundle', 'docker-image', 'npm-package', 'test-report', 'coverage-report', 'source-map', 'documentation'];

    if (!validTypes.includes(type)) {
      errors.push({
        path: 'type',
        message: `Invalid artifact type: ${type}. Must be one of: ${validTypes.join(', ')}`,
      });
    }

    return errors;
  }

  /**
   * Validate artifact path exists
   */
  async validateArtifactPathExists(path: string): Promise<ValidationError[]> {
    const errors: ValidationError[] = [];
    const { promises: fs } = await import('node:fs');

    try {
      await fs.access(path);
    } catch {
      errors.push({ path: 'path', message: `Artifact path does not exist: ${path}` });
    }

    return errors;
  }

  /**
   * Validate artifact size matches expected
   */
  async validateArtifactSize(path: string, expectedSize: number): Promise<ValidationError[]> {
    const errors: ValidationError[] = [];
    const { promises: fs } = await import('node:fs');

    try {
      const stats = await fs.stat(path);
      const actualSize = stats.size;

      if (actualSize !== expectedSize) {
        errors.push({
          path: 'size',
          message: `Artifact size mismatch: expected ${expectedSize} bytes, got ${actualSize} bytes`,
        });
      }
    } catch {
      errors.push({ path: 'path', message: `Cannot read artifact size: ${path}` });
    }

    return errors;
  }
}

/**
 * Validation error
 */
export interface ValidationError {
  path: string;
  message: string;
}
