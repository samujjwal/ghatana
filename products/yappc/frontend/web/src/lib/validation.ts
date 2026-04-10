/**
 * Validation Library
 *
 * Runtime validation utilities with Zod schemas and type guards.
 * Provides validation middleware for API clients and type-safe data parsing.
 *
 * @doc.type library
 * @doc.purpose Runtime validation utilities
 * @doc.layer product
 * @doc.pattern Library
 */

import { z } from 'zod';
import type { ApiResponse, ApiError } from '../types/validation';

// ============================================================================
// Validation Result Types
// ============================================================================

/**
 * Validation result type
 */
export interface ValidationResult<T> {
  success: boolean;
  data?: T;
  error?: z.ZodError;
}

/**
 * Type guard for API response
 */
export function isApiResponse(value: unknown): value is ApiResponse {
  const schema = z.object({
    success: z.boolean(),
    data: z.unknown().optional(),
    error: z.object({
      code: z.string(),
      message: z.string(),
    }).optional(),
    timestamp: z.string(),
  });

  try {
    schema.parse(value);
    return true;
  } catch {
    return false;
  }
}

/**
 * Type guard for API error
 */
export function isApiError(value: unknown): value is ApiError {
  const schema = z.object({
    code: z.string(),
    message: z.string(),
    details: z.record(z.string(), z.unknown()).optional(),
  });

  try {
    schema.parse(value);
    return true;
  } catch {
    return false;
  }
}

// ============================================================================
// Validation Functions
// ============================================================================

/**
 * Validate data against a Zod schema
 *
 * @param schema The Zod schema to validate against
 * @param data The data to validate
 * @returns Validation result with data or error
 */
export function validate<T extends z.ZodTypeAny>(
  schema: T,
  data: unknown
): ValidationResult<z.infer<T>> {
  const result = schema.safeParse(data);

  if (result.success) {
    return {
      success: true,
      data: result.data,
    };
  }

  return {
    success: false,
    error: result.error,
  };
}

/**
 * Validate API response
 *
 * @param response The API response to validate
 * @returns Validation result with typed response or error
 */
export function validateApiResponse<T>(
  response: unknown,
  dataSchema?: z.ZodType<T>
): ValidationResult<ApiResponse<T>> {
  const baseSchema = z.object({
    success: z.boolean(),
    data: z.unknown().optional(),
    error: z.object({
      code: z.string(),
      message: z.string(),
      details: z.record(z.string(), z.unknown()).optional(),
    }).optional(),
    timestamp: z.string(),
  });

  const result = baseSchema.safeParse(response);

  if (!result.success) {
    return {
      success: false,
      error: result.error,
    };
  }

  // If data schema is provided, validate the data field
  if (dataSchema && result.data.data !== undefined) {
    const dataResult = dataSchema.safeParse(result.data.data);

    if (!dataResult.success) {
      return {
        success: false,
        error: dataResult.error,
      };
    }

    return {
      success: true,
      data: {
        ...result.data,
        data: dataResult.data,
      } as ApiResponse<T>,
    };
  }

  return {
    success: true,
    data: result.data as ApiResponse<T>,
  };
}

/**
 * Parse and validate unknown data with a schema
 *
 * @param schema The Zod schema to validate against
 * @param data The unknown data to parse
 * @returns Parsed and validated data
 * @throws z.ZodError if validation fails
 */
export function parseSafe<T extends z.ZodTypeAny>(
  schema: T,
  data: unknown
): z.infer<T> {
  return schema.parse(data);
}

/**
 * Create a type guard from a Zod schema
 *
 * @param schema The Zod schema to use for type guarding
 * @returns Type guard function
 */
export function createTypeGuard<T extends z.ZodTypeAny>(
  schema: T
): (value: unknown) => value is z.infer<T> {
  return (value: unknown): value is z.infer<T> => {
    return schema.safeParse(value).success;
  };
}

// ============================================================================
// Validation Middleware
// ============================================================================

/**
 * Validation middleware for API clients
 *
 * Wraps API responses with validation logic to ensure type safety.
 */
export class ValidationMiddleware {
  /**
   * Wrap an API response with validation
   *
   * @param response The raw API response
   * @param dataSchema Optional schema for the data field
   * @returns Validated API response or throws error
   */
  static wrapResponse<T>(
    response: unknown,
    dataSchema?: z.ZodType<T>
  ): ApiResponse<T> {
    const result = validateApiResponse(response, dataSchema);

    if (!result.success) {
      throw new Error(`Validation failed: ${result.error?.message}`);
    }

    if (!result.data) {
      throw new Error('Validation failed: No data returned');
    }

    return result.data;
  }

  /**
   * Wrap an API error with validation
   *
   * @param error The raw error object
   * @returns Validated API error
   */
  static wrapError(error: unknown): ApiError {
    if (isApiError(error)) {
      return error;
    }

    // Handle standard Error objects
    if (error instanceof Error) {
      return {
        code: 'UNKNOWN_ERROR',
        message: error.message,
      };
    }

    // Handle string errors
    if (typeof error === 'string') {
      return {
        code: 'UNKNOWN_ERROR',
        message: error,
      };
    }

    // Handle unknown errors
    return {
      code: 'UNKNOWN_ERROR',
      message: 'An unknown error occurred',
    };
  }

  /**
   * Validate request data before sending
   *
   * @param schema The Zod schema for request validation
   * @param data The request data to validate
   * @returns Validated request data
   * @throws z.ZodError if validation fails
   */
  static validateRequest<T extends z.ZodTypeAny>(
    schema: T,
    data: unknown
  ): z.infer<T> {
    return schema.parse(data);
  }
}

// ============================================================================
// Error Formatting
// ============================================================================

/**
 * Format Zod validation errors into user-friendly messages
 *
 * @param error The Zod error to format
 * @returns Formatted error message
 */
export function formatValidationError(error: z.ZodError): string {
  const issues = error.issues.map((issue) => {
    const path = issue.path.length > 0 ? issue.path.join('.') : 'root';
    return `${path}: ${issue.message}`;
  });

  return issues.join(', ');
}

/**
 * Get the first error message from a Zod error
 *
 * @param error The Zod error
 * @returns First error message or default message
 */
export function getFirstErrorMessage(error: z.ZodError, defaultMessage = 'Validation failed'): string {
  return error.issues[0]?.message || defaultMessage;
}

// ============================================================================
// Schema Builders
// ============================================================================

/**
 * Create a paginated response schema
 *
 * @param itemSchema Schema for the items in the paginated response
 * @returns Paginated response schema
 */
export function createPaginatedSchema<T extends z.ZodTypeAny>(itemSchema: T) {
  return z.object({
    items: z.array(itemSchema),
    totalItems: z.number().int().nonnegative(),
    page: z.number().int().positive(),
    pageSize: z.number().int().positive(),
    totalPages: z.number().int().nonnegative(),
    hasNext: z.boolean(),
    hasPrevious: z.boolean(),
  });
}

/**
 * Create an API response schema
 *
 * @param dataSchema Optional schema for the data field
 * @returns API response schema
 */
export function createApiResponseSchema<T extends z.ZodTypeAny>(dataSchema?: T) {
  return z.object({
    success: z.boolean(),
    data: dataSchema?.optional(),
    error: z.object({
      code: z.string(),
      message: z.string(),
      details: z.record(z.string(), z.unknown()).optional(),
    }).optional(),
    timestamp: z.string(),
  });
}
