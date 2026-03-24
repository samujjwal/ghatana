import { z } from 'zod';

import { CanvasDataSchema } from './canvas-schemas';
import { CommentSchema } from './comment-schemas';
import { EdgeSchema } from './edge-schemas';
import { ExportOptionsSchema } from './export-schemas';
import { NodeSchema } from './node-schemas';
import { PermissionScopeSchema } from './permission-schemas';

// Schema Registry - Central validation utilities
/**
 *
 */
export class SchemaRegistry {
  private static instance: SchemaRegistry;
  private schemas: Map<string, z.ZodSchema> = new Map();

  /**
   *
   */
  private constructor() {
    this.registerDefaultSchemas();
  }

  /**
   *
   */
  public static getInstance(): SchemaRegistry {
    if (!SchemaRegistry.instance) {
      SchemaRegistry.instance = new SchemaRegistry();
    }
    return SchemaRegistry.instance;
  }

  /**
   *
   */
  private registerDefaultSchemas(): void {
    this.schemas.set('node', NodeSchema);
    this.schemas.set('edge', EdgeSchema);
    this.schemas.set('canvas', CanvasDataSchema);
    this.schemas.set('permission', PermissionScopeSchema);
    this.schemas.set('comment', CommentSchema);
    this.schemas.set('export', ExportOptionsSchema);
  }

  /**
   *
   */
  public register<T>(name: string, schema: z.ZodSchema<T>): void {
    this.schemas.set(name, schema);
  }

  /**
   *
   */
  public get<T>(name: string): z.ZodSchema<T> | undefined {
    return this.schemas.get(name) as z.ZodSchema<T> | undefined;
  }

  /**
   *
   */
  public validate<T>(name: string, data: unknown): T {
    const schema = this.get<T>(name);
    if (!schema) {
      throw new Error(`Schema '${name}' not found in registry`);
    }
    return schema.parse(data);
  }

  /**
   *
   */
  public safeParse<T>(name: string, data: unknown): z.SafeParseReturnType<unknown, T> {
    const schema = this.get<T>(name);
    if (!schema) {
      return {
        success: false,
        error: new z.ZodError([
          {
            code: 'custom',
            message: `Schema '${name}' not found in registry`,
            path: [],
          },
        ]),
      } as z.SafeParseReturnType<unknown, T>;
    }
    return schema.safeParse(data) as z.SafeParseReturnType<unknown, T>;
  }

  /**
   *
   */
  public listSchemas(): string[] {
    return Array.from(this.schemas.keys());
  }
}

// Validation utilities
export const validateData = <T>(schemaName: string, data: unknown): T => {
  return SchemaRegistry.getInstance().validate<T>(schemaName, data);
};

export const safeValidateData = <T>(schemaName: string, data: unknown): z.SafeParseReturnType<unknown, T> => {
  return SchemaRegistry.getInstance().safeParse<T>(schemaName, data);
};

export const registerSchema = <T>(
  name: string,
  schema: z.ZodSchema<T>
): void => {
  SchemaRegistry.getInstance().register(name, schema);
};

// Common validation patterns
export const ValidationResult = z.object({
  valid: z.boolean(),
  errors: z.array(z.string()).default([]),
  warnings: z.array(z.string()).default([]),
});

/**
 *
 */
export type ValidationResult = z.infer<typeof ValidationResult>;

export const createValidationResult = (
  valid: boolean,
  errors: string[] = [],
  warnings: string[] = []
): ValidationResult => ({
  valid,
  errors,
  warnings,
});

// Batch validation
export const validateBatch = (
  items: Array<{ schema: string; data: unknown; id?: string }>
): Array<{ id?: string; result: ValidationResult }> => {
  return items.map(({ schema, data, id }) => {
    const parseResult = safeValidateData(schema, data);
    return {
      id,
      result: createValidationResult(
        parseResult.success,
        parseResult.success
          ? []
          : (parseResult.error as unknown).errors.map((e: unknown) => e.message)
      ),
    };
  });
};

// Type guards
export const isValidNode = (data: unknown): boolean => {
  const result = safeValidateData('node', data);
  return result.success;
};

export const isValidEdge = (data: unknown): boolean => {
  const result = safeValidateData('edge', data);
  return result.success;
};

export const isValidCanvas = (data: unknown): boolean => {
  const result = safeValidateData('canvas', data);
  return result.success;
};

export const isValidComment = (data: unknown): boolean => {
  const result = safeValidateData('comment', data);
  return result.success;
};

// Schema transformation utilities
export const transformToSchema = <T, U>(
  fromSchema: string,
  toSchema: string,
  data: unknown,
  transformer: (from: T) => U
): U => {
  const validated = validateData<T>(fromSchema, data);
  const transformed = transformer(validated);
  return validateData<U>(toSchema, transformed);
};

// Default registry instance
export const schemaRegistry = SchemaRegistry.getInstance();
