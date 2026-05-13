import { ProductLifecyclePhase, ProductSurfaceType } from '../domain/ProductLifecyclePhase.js';

/**
 * Artifact resolver for lifecycle planning
 */
export class ArtifactResolver {
  /**
   * Resolve expected artifacts for a phase and surface
   */
  resolve(
    phase: ProductLifecyclePhase,
    surfaceType: ProductSurfaceType,
  ): ExpectedArtifact[] {
    const artifacts: ExpectedArtifact[] = [];

    switch (phase) {
      case 'build':
        artifacts.push(...this.getBuildArtifacts(surfaceType));
        break;
      case 'package':
        artifacts.push(...this.getPackageArtifacts(surfaceType));
        break;
      case 'test':
        artifacts.push(...this.getTestArtifacts(surfaceType));
        break;
      default:
        // Other phases may not produce artifacts
        break;
    }

    return artifacts;
  }

  /**
   * Get build artifacts for surface type
   */
  private getBuildArtifacts(surfaceType: ProductSurfaceType): ExpectedArtifact[] {
    switch (surfaceType) {
      case 'backend-api':
      case 'worker':
        return [
          { type: 'jvm-classes', required: true },
          { type: 'test-report', required: true },
          { type: 'coverage-report', required: false },
        ];
      case 'web':
        return [
          { type: 'static-web-bundle', required: true },
          { type: 'test-report', required: true },
          { type: 'typecheck-report', required: true },
        ];
      case 'mobile-ios':
        return [
          { type: 'ios-app', required: true },
          { type: 'test-report', required: true },
        ];
      case 'mobile-android':
        return [
          { type: 'android-apk', required: true },
          { type: 'android-aab', required: false },
          { type: 'test-report', required: true },
        ];
      case 'sdk':
        return [
          { type: 'jar', required: true },
          { type: 'test-report', required: true },
          { type: 'coverage-report', required: false },
        ];
      default:
        return [];
    }
  }

  /**
   * Get package artifacts for surface type
   */
  private getPackageArtifacts(surfaceType: ProductSurfaceType): ExpectedArtifact[] {
    switch (surfaceType) {
      case 'backend-api':
      case 'worker':
        return [{ type: 'container-image', required: true }];
      case 'web':
        return [{ type: 'static-web-image', required: true }];
      case 'mobile-ios':
        return [{ type: 'ios-ipa', required: true }];
      case 'mobile-android':
        return [{ type: 'android-apk', required: true }];
      case 'sdk':
        return [{ type: 'maven-artifact', required: true }];
      default:
        return [];
    }
  }

  /**
   * Get test artifacts for surface type
   */
  private getTestArtifacts(_surfaceType: ProductSurfaceType): ExpectedArtifact[] {
    return [
      { type: 'test-report', required: true },
      { type: 'coverage-report', required: false },
    ];
  }

  /**
   * Validate artifact configuration
   */
  validate(artifact: ExpectedArtifact): ValidationError[] {
    const errors: ValidationError[] = [];

    if (!artifact.type || artifact.type.trim().length === 0) {
      errors.push({ path: 'type', message: 'Artifact type is required' });
    }

    return errors;
  }
}

/**
 * Expected artifact
 */
export interface ExpectedArtifact {
  type: string;
  required: boolean;
}

/**
 * Validation error
 */
export interface ValidationError {
  path: string;
  message: string;
}
