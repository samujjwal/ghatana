import { ProductSurfaceType, ProductSurface } from '../domain/ProductLifecyclePhase.js';

/**
 * Surface selector for lifecycle planning
 */
export class SurfaceSelector {
  /**
   * Select surfaces based on configuration and filters
   */
  select(
    availableSurfaces: Record<string, ProductSurface>,
    filters?: {
      surfaceTypes?: ProductSurfaceType[];
      surfaceIds?: string[];
      implementationStatus?: ('implemented' | 'planned' | 'backend-only')[];
    },
  ): ProductSurface[] {
    let surfaces = Object.values(availableSurfaces);

    if (filters?.surfaceTypes && filters.surfaceTypes.length > 0) {
      surfaces = surfaces.filter((s) => filters.surfaceTypes!.includes(s.type));
    }

    if (filters?.surfaceIds && filters.surfaceIds.length > 0) {
      surfaces = surfaces.filter((s) => filters.surfaceIds!.includes(s.type));
    }

    if (filters?.implementationStatus && filters.implementationStatus.length > 0) {
      surfaces = surfaces.filter((s) =>
        filters.implementationStatus!.includes(s.implementationStatus),
      );
    }

    return surfaces;
  }

  /**
   * Validate surface configuration
   */
  validate(surface: ProductSurface): ValidationError[] {
    const errors: ValidationError[] = [];

    if (!surface.type) {
      errors.push({ path: 'type', message: 'Surface type is required' });
    }

    if (!surface.adapter) {
      errors.push({ path: 'adapter', message: 'Surface adapter is required' });
    }

    if (!surface.path) {
      errors.push({ path: 'path', message: 'Surface path is required' });
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
