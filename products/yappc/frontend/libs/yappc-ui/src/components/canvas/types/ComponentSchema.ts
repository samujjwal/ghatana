/**
 * Component Schema to Canvas Node mapping types
 */

/**
 * Transformation context
 */
export interface TransformContext {
  /**
   * Current theme layer
   */
  theme: 'base' | 'brand' | 'workspace' | 'app';

  /**
   * Parent node ID (for nested components)
   */
  parentId?: string;

  /**
   * Position offset for nested components
   */
  offset?: { x: number; y: number };

  /**
   * Available design tokens
   */
  tokens?: Record<string, unknown>;

  /**
   * Component registry reference
   */
  componentRegistry?: unknown;
}

/**
 * Transformation options
 */
export interface TransformOptions {
  /**
   * Generate unique IDs
   */
  generateIds?: boolean;

  /**
   * Validate transformed data
   */
  validate?: boolean;

  /**
   * Include metadata in output
   */
  includeMetadata?: boolean;

  /**
   * Preserve original IDs
   */
  preserveIds?: boolean;
}

/**
 * Transformation result
 */
export interface TransformResult<T> {
  data: T;
  warnings: string[];
  errors: string[];
  metadata?: {
    transformedAt: number;
    context?: TransformContext;
  };
}

/**
 * Validation error
 */
export interface ValidationError {
  field: string;
  message: string;
  severity: 'error' | 'warning' | 'info';
  code?: string;
}

/**
 * Validation result
 */
export interface ValidationResult {
  valid: boolean;
  errors: ValidationError[];
}
