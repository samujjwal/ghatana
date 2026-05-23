import { ProductSurfaceSchema, getCombinationRecoveryGuidance } from '@ghatana/kernel-product-contracts';
import type { ProductSurfaceType } from '../domain/ProductLifecyclePhase.js';

/**
 * Product surface validator using strict Zod schema
 */
export class ProductSurfaceValidator {
  /**
   * Validate surface configuration using Zod schema
   */
  validate(surface: unknown): ValidationError[] {
    const errors: ValidationError[] = [];

    const result = ProductSurfaceSchema.safeParse(surface);

    if (!result.success) {
      for (const issue of result.error.issues) {
        const path = issue.path.join('.');
        let message = issue.message;

        // Add recovery guidance for combination errors
        if (issue.code === 'custom' && issue.message.includes('Invalid language/runtime/buildSystem combination')) {
          const data = surface as any;
          if (data.language && data.runtime && data.buildSystem) {
            message = getCombinationRecoveryGuidance(data.language, data.runtime, data.buildSystem);
          }
        }

        errors.push({ path, message });
      }
    }

    return errors;
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
