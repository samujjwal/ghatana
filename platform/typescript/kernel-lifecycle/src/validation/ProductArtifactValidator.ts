import { ProductArtifact } from '../domain/ProductLifecyclePhase.js';

/**
 * Product artifact validator
 */
export class ProductArtifactValidator {
  /**
   * Validate artifact
   */
  validate(artifact: ProductArtifact): ValidationError[] {
    const errors: ValidationError[] = [];

    if (!artifact.id || artifact.id.trim().length === 0) {
      errors.push({ path: 'id', message: 'Artifact ID is required' });
    }

    if (!artifact.surface || artifact.surface.trim().length === 0) {
      errors.push({ path: 'surface', message: 'Surface is required' });
    }

    if (!artifact.type || artifact.type.trim().length === 0) {
      errors.push({ path: 'type', message: 'Artifact type is required' });
    }

    if (!artifact.path || artifact.path.trim().length === 0) {
      errors.push({ path: 'path', message: 'Artifact path is required' });
    }

    if (!artifact.fingerprint || artifact.fingerprint.trim().length === 0) {
      errors.push({ path: 'fingerprint', message: 'Artifact fingerprint is required' });
    }

    if (!artifact.producedBy || artifact.producedBy.trim().length === 0) {
      errors.push({ path: 'producedBy', message: 'Produced by is required' });
    }

    if (artifact.sizeBytes !== undefined && artifact.sizeBytes < 0) {
      errors.push({ path: 'sizeBytes', message: 'Size bytes must be non-negative' });
    }

    return errors;
  }

  /**
   * Validate artifact type
   */
  validateArtifactType(type: string): ValidationError[] {
    const errors: ValidationError[] = [];

    const validTypes = [
      'jar',
      'container-image',
      'static-web-bundle',
      'static-web-image',
      'node-bundle',
      'mobile-bundle',
      'ios-app',
      'ios-ipa',
      'android-apk',
      'android-aab',
      'sdk-package',
      'maven-artifact',
      'jvm-classes',
      'test-report',
      'coverage-report',
      'typecheck-report',
    ];

    if (!validTypes.includes(type)) {
      errors.push({
        path: 'type',
        message: `Invalid artifact type: ${type}. Must be one of: ${validTypes.join(', ')}`,
      });
    }

    return errors;
  }

  /**
   * Validate fingerprint format
   */
  validateFingerprint(fingerprint: string): ValidationError[] {
    const errors: ValidationError[] = [];

    // SHA-256 fingerprints should be 64 hex characters
    if (fingerprint.length !== 64) {
      errors.push({ path: 'fingerprint', message: 'Fingerprint must be 64 characters (SHA-256)' });
    }

    if (!/^[a-f0-9]{64}$/i.test(fingerprint)) {
      errors.push({ path: 'fingerprint', message: 'Fingerprint must be a valid hexadecimal string' });
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
