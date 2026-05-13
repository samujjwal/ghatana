import { ProductSurface, ProductSurfaceType } from '../domain/ProductLifecyclePhase.js';

/**
 * Product surface validator
 */
export class ProductSurfaceValidator {
  /**
   * Validate surface configuration
   */
  validate(surface: ProductSurface): ValidationError[] {
    const errors: ValidationError[] = [];

    if (!surface.type) {
      errors.push({ path: 'type', message: 'Surface type is required' });
    } else {
      this.validateSurfaceType(surface.type, errors);
    }

    if (!surface.adapter || surface.adapter.trim().length === 0) {
      errors.push({ path: 'adapter', message: 'Surface adapter is required' });
    }

    if (!surface.path || surface.path.trim().length === 0) {
      errors.push({ path: 'path', message: 'Surface path is required' });
    }

    if (!surface.implementationStatus) {
      errors.push({ path: 'implementationStatus', message: 'Implementation status is required' });
    } else {
      this.validateImplementationStatus(surface.implementationStatus, errors);
    }

    return errors;
  }

  /**
   * Validate surface type
   */
  private validateSurfaceType(type: string, errors: ValidationError[]): void {
    const validTypes: ProductSurfaceType[] = [
      'backend-api',
      'web',
      'worker',
      'operator',
      'mobile-ios',
      'mobile-android',
      'sdk',
      'domain-pack',
    ];

    if (!validTypes.includes(type as ProductSurfaceType)) {
      errors.push({
        path: 'type',
        message: `Invalid surface type: ${type}. Must be one of: ${validTypes.join(', ')}`,
      });
    }
  }

  /**
   * Validate implementation status
   */
  private validateImplementationStatus(
    status: string,
    errors: ValidationError[],
  ): void {
    const validStatuses = ['implemented', 'planned', 'backend-only'];

    if (!validStatuses.includes(status)) {
      errors.push({
        path: 'implementationStatus',
        message: `Invalid implementation status: ${status}. Must be one of: ${validStatuses.join(', ')}`,
      });
    }
  }

  /**
   * Validate surface adapter compatibility
   */
  validateAdapterCompatibility(
    surfaceType: ProductSurfaceType,
    adapter: string,
    supportedAdapters: string[],
  ): ValidationError[] {
    const errors: ValidationError[] = [];

    if (!supportedAdapters.includes(adapter)) {
      errors.push({
        path: 'adapter',
        message: `Adapter ${adapter} is not supported for surface type ${surfaceType}`,
      });
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
