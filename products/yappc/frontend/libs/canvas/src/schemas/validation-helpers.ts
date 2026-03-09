// Phase 8: Component Schema Registry - Validation helpers and integration utilities
// Integrates with useCanvasApi and import/export workflows

import {
  componentSchemaRegistry,
  validateComponent
} from './component-registry';

import type {
  ValidationResult,
  CanvasComponent,
  CanvasEdge} from './component-registry';

// Import validation utilities
/**
 *
 */
export interface ImportValidationResult {
  success: boolean;
  data?: {
    nodes: CanvasComponent[];
    edges: CanvasEdge[];
  };
  errors: string[];
  warnings: string[];
}

export const validateImportData = (
  importData: unknown
): ImportValidationResult => {
  const errors: string[] = [];
  const warnings: string[] = [];

  // Basic structure validation
  if (!importData || typeof importData !== 'object') {
    return {
      success: false,
      errors: ['Import data must be a valid object'],
      warnings: [],
    };
  }

  const data = importData as unknown;

  if (!Array.isArray(data.nodes)) {
    errors.push('Import data must contain a "nodes" array');
  }

  if (!Array.isArray(data.edges)) {
    errors.push('Import data must contain an "edges" array');
  }

  if (errors.length > 0) {
    return { success: false, errors, warnings };
  }

  const validatedNodes: CanvasComponent[] = [];
  const validatedEdges: CanvasEdge[] = [];

  // Validate nodes
  data.nodes.forEach((node: unknown, index: number) => {
    if (!node.type) {
      errors.push(`Node at index ${index} is missing required "type" field`);
      return;
    }

    const validation = validateComponent(node.type, node);
    if (validation.success && validation.data) {
      validatedNodes.push(validation.data);
    } else {
      errors.push(
        `Node at index ${index} (type: ${node.type}): ${validation.errors.join(', ')}`
      );
    }
  });

  // Validate edges
  data.edges.forEach((edge: unknown, index: number) => {
    try {
      const schema = componentSchemaRegistry.getSchema('edge');
      if (schema) {
        const result = schema.safeParse(edge);
        if (result.success) {
          validatedEdges.push(result.data as CanvasEdge);
        } else {
          errors.push(
            `Edge at index ${index}: ${(result.error as unknown).errors.map((e: unknown) => e.message).join(', ')}`
          );
        }
      } else {
        warnings.push(`No schema registered for edges, skipping validation`);
        validatedEdges.push(edge);
      }
    } catch (error) {
      errors.push(`Edge at index ${index}: ${(error as Error).message}`);
    }
  });

  // Check for orphaned edges
  const nodeIds = new Set(validatedNodes.map((n) => n.id));
  validatedEdges.forEach((edge, index) => {
    if (!nodeIds.has(edge.source)) {
      warnings.push(
        `Edge at index ${index} references non-existent source node: ${edge.source}`
      );
    }
    if (!nodeIds.has(edge.target)) {
      warnings.push(
        `Edge at index ${index} references non-existent target node: ${edge.target}`
      );
    }
  });

  const success = errors.length === 0;
  return {
    success,
    data: success
      ? { nodes: validatedNodes, edges: validatedEdges }
      : undefined,
    errors,
    warnings,
  };
};

// Export validation utilities
/**
 *
 */
export interface ExportValidationOptions {
  validateBeforeExport?: boolean;
  includeMetadata?: boolean;
  minifyOutput?: boolean;
}

export const validateExportData = (
  nodes: CanvasComponent[],
  edges: CanvasEdge[],
  options: ExportValidationOptions = {}
): ImportValidationResult => {
  const { validateBeforeExport = true } = options;
  const errors: string[] = [];
  const warnings: string[] = [];

  if (!validateBeforeExport) {
    return {
      success: true,
      data: { nodes, edges },
      errors: [],
      warnings: [],
    };
  }

  const validatedNodes: CanvasComponent[] = [];
  const validatedEdges: CanvasEdge[] = [];

  // Validate nodes
  nodes.forEach((node, index) => {
    const validation = validateComponent(node.type, node);
    if (validation.success && validation.data) {
      validatedNodes.push(validation.data);
    } else {
      errors.push(
        `Node at index ${index} (id: ${node.id}): ${validation.errors.join(', ')}`
      );
    }
  });

  // Validate edges
  edges.forEach((edge, index) => {
    const schema = componentSchemaRegistry.getSchema('edge');
    if (schema) {
      const result = schema.safeParse(edge);
      if (result.success) {
        validatedEdges.push(result.data as CanvasEdge);
      } else {
        errors.push(
          `Edge at index ${index} (id: ${edge.id}): ${(result.error as unknown).errors.map((e: unknown) => e.message).join(', ')}`
        );
      }
    } else {
      warnings.push('No schema registered for edges, skipping validation');
      validatedEdges.push(edge);
    }
  });

  const success = errors.length === 0;
  return {
    success,
    data: success
      ? { nodes: validatedNodes, edges: validatedEdges }
      : undefined,
    errors,
    warnings,
  };
};

// Runtime validation hooks for canvas operations
/**
 *
 */
export interface RuntimeValidationOptions {
  strict?: boolean; // Throw errors on validation failure
  logWarnings?: boolean; // Log warnings to console
}

export const validateNodeOperation = (
  operation: 'create' | 'update' | 'delete',
  nodeType: string,
  nodeData: unknown,
  options: RuntimeValidationOptions = {}
): ValidationResult<CanvasComponent> => {
  const { strict = false, logWarnings = true } = options;

  if (operation === 'delete') {
    // No validation needed for delete operations
    return {
      success: true,
      errors: [],
    };
  }

  // For updates we allow partial data (only changed fields).
  // Merge the partial update with the default component data so nested required
  // fields (for example data.question on a decision node) are present for
  // validation. This avoids requiring callers to provide the full object on update.
  const schema = componentSchemaRegistry.getSchema(nodeType);
  let validation: ValidationResult<CanvasComponent>;

  const mergeDeep = (base: unknown, patch: unknown): unknown => {
    if (base == null) return patch;
    if (patch == null) return base;
    if (
      typeof base !== 'object' ||
      typeof patch !== 'object' ||
      Array.isArray(base) ||
      Array.isArray(patch)
    ) {
      return patch;
    }
    const out: Record<string, unknown> = { ...(base as Record<string, unknown>) };
    for (const key of Object.keys(patch as Record<string, unknown>)) {
      out[key] = mergeDeep((base as Record<string, unknown>)[key], (patch as Record<string, unknown>)[key]);
    }
    return out;
  };

  try {
    if (operation === 'update' && schema) {
      const defaults = componentSchemaRegistry.getDefaultData(nodeType) || {};
      // Provide a minimal skeleton for required fields so partial updates
      // don't fail due to missing structural properties like id/position/size.
      const skeleton = {
        id: '__validation_placeholder__',
        type: nodeType,
        version: '1.0.0',
        position: { x: 0, y: 0 },
        size: { width: 1, height: 1 },
        rotation: 0,
        visible: true,
        locked: false,
        metadata: {},
        ...(defaults as unknown),
      };

      const merged = mergeDeep(skeleton, nodeData as unknown);
      const result = (schema as unknown).safeParse(merged);
      if (result.success) {
        validation = {
          success: true,
          data: result.data as CanvasComponent,
          errors: [],
        };
      } else {
        validation = {
          success: false,
          error: result.error as unknown,
          errors: result.error.errors.map(
            (e: unknown) => `${e.path.join('.')}: ${e.message}`
          ),
        };
      }
    } else {
      validation = validateComponent(nodeType, nodeData);
    }
  } catch (err) {
    validation = {
      success: false,
      error: err as Error,
      errors: [(err as Error).message],
    };
  }

  if (!validation.success && strict) {
    throw new Error(`Node validation failed: ${validation.errors.join(', ')}`);
  }

  if (!validation.success && logWarnings) {
    console.warn(
      `Node validation warnings for ${operation}:`,
      validation.errors
    );
  }

  return validation;
};

// Schema migration utilities
/**
 *
 */
export interface MigrationOptions {
  dryRun?: boolean;
  logMigrations?: boolean;
}

export const migrateCanvas = (
  canvas: { nodes: unknown[]; edges: unknown[] },
  options: MigrationOptions = {}
): ImportValidationResult => {
  const { dryRun = false, logMigrations = true } = options;
  const errors: string[] = [];
  const warnings: string[] = [];
  const migratedNodes: CanvasComponent[] = [];
  const migratedEdges: CanvasEdge[] = [];

  // Guard against malformed canvas input
  if (
    !canvas ||
    typeof canvas !== 'object' ||
    !Array.isArray((canvas as unknown).nodes) ||
    !Array.isArray((canvas as unknown).edges)
  ) {
    return {
      success: false,
      errors: ['Canvas must be an object with nodes and edges arrays'],
      warnings: [],
    };
  }

  // Migrate nodes
  (canvas.nodes as unknown[]).forEach((node: unknown, index) => {
    if (!node.type) {
      errors.push(`Node at index ${index} is missing type field`);
      return;
    }

    // Check if node has version info for migration
    const currentVersion = node.version || '1.0.0';
    const targetVersion = '1.0.0'; // Could be configurable

    if (currentVersion !== targetVersion) {
      if (logMigrations) {
        console.log(
          `Migrating node ${node.id} from ${currentVersion} to ${targetVersion}`
        );
      }

      if (!dryRun) {
        try {
          const migrationResult = componentSchemaRegistry.migrate(
            node.type,
            node,
            currentVersion,
            targetVersion
          );
          if (migrationResult.success && migrationResult.data) {
            migratedNodes.push(migrationResult.data);
          } else {
            errors.push(
              `Migration failed for node ${node.id}: ${migrationResult.errors.join(', ')}`
            );
          }
        } catch (error) {
          errors.push(
            `Migration error for node ${node.id}: ${(error as Error).message}`
          );
        }
      }
    } else {
      // No migration needed, just validate
      const validation = validateComponent(node.type, node);
      if (validation.success && validation.data) {
        migratedNodes.push(validation.data);
      } else {
        errors.push(
          `Validation failed for node ${node.id}: ${validation.errors.join(', ')}`
        );
      }
    }
  });

  // For edges, assume no migration needed for now
  canvas.edges.forEach((edge: unknown) => {
    const schema = componentSchemaRegistry.getSchema('edge');
    if (schema) {
      const result = schema.safeParse(edge);
      if (result.success) {
        migratedEdges.push(result.data as CanvasEdge);
      } else {
        errors.push(
          `Edge validation failed for ${edge.id}: ${(result.error as unknown).errors.map((e: unknown) => e.message).join(', ')}`
        );
      }
    } else {
      warnings.push('No schema registered for edges');
      migratedEdges.push(edge);
    }
  });

  const success = errors.length === 0;
  return {
    success,
    data: success ? { nodes: migratedNodes, edges: migratedEdges } : undefined,
    errors,
    warnings,
  };
};

// Canvas API integration helpers
/**
 *
 */
export interface CanvasAPIValidationConfig {
  validateOnCreate: boolean;
  validateOnUpdate: boolean;
  validateOnImport: boolean;
  strictMode: boolean;
  logValidationErrors: boolean;
}

export const createCanvasAPIValidator = (config: CanvasAPIValidationConfig) => {
  return {
    validateCreate: (nodeType: string, nodeData: unknown) => {
      if (!config.validateOnCreate) return { success: true, errors: [] };

      return validateNodeOperation('create', nodeType, nodeData, {
        strict: config.strictMode,
        logWarnings: config.logValidationErrors,
      });
    },

    validateUpdate: (nodeType: string, nodeData: unknown) => {
      if (!config.validateOnUpdate) return { success: true, errors: [] };

      return validateNodeOperation('update', nodeType, nodeData, {
        strict: config.strictMode,
        logWarnings: config.logValidationErrors,
      });
    },

    validateImport: (importData: unknown) => {
      if (!config.validateOnImport)
        return { success: true, errors: [], warnings: [] };

      return validateImportData(importData);
    },

    validateBatch: (
      operations: Array<{
        type: 'create' | 'update';
        nodeType: string;
        data: unknown;
      }>
    ) => {
      const results = operations.map((op, index) => {
        const validation = validateNodeOperation(
          op.type,
          op.nodeType,
          op.data,
          {
            strict: false, // Don't throw in batch mode
            logWarnings: config.logValidationErrors,
          }
        );

        return {
          index,
          operation: op.type,
          nodeType: op.nodeType,
          success: validation.success,
          errors: validation.errors,
        };
      });

      // Consider batch successful if all individual validations succeeded
      const allSuccessful =
        results.length === 0 ? true : results.every((r) => r.success === true);
      const allErrors = results.flatMap((r) => r.errors || []);

      return {
        success: allSuccessful,
        results,
        errors: allErrors,
      };
    },
  };
};

// Default validation config for production
export const PRODUCTION_VALIDATION_CONFIG: CanvasAPIValidationConfig = {
  validateOnCreate: true,
  validateOnUpdate: true,
  validateOnImport: true,
  strictMode: true, // Be strict in production by default
  logValidationErrors: true,
};

// Development validation config
export const DEVELOPMENT_VALIDATION_CONFIG: CanvasAPIValidationConfig = {
  validateOnCreate: true,
  validateOnUpdate: true,
  validateOnImport: true,
  strictMode: false, // Be lenient in development
  logValidationErrors: true,
};

// Utility to get validation statistics
export const getValidationStats = (canvas: {
  nodes: unknown[];
  edges: unknown[];
}) => {
  const nodeValidations = canvas.nodes.map((node: unknown) => ({
    id: node.id,
    type: node.type,
    validation: node.type
      ? validateComponent(node.type, node)
      : { success: false, errors: ['Missing type'] },
  }));

  const edgeValidations = canvas.edges.map((edge: unknown) => {
    const schema = componentSchemaRegistry.getSchema('edge');
    const validation = schema
      ? schema.safeParse(edge)
      : { success: false, error: { errors: [{ message: 'No schema' }] } };
    return {
      id: edge.id,
      validation: {
        success: validation.success,
        errors: validation.success
          ? []
          : (validation as unknown).error.errors.map((e: unknown) => e.message),
      },
    };
  });

  const validNodes = nodeValidations.filter((n) => n.validation.success).length;
  const validEdges = edgeValidations.filter((e) => e.validation.success).length;
  const totalErrors = [
    ...nodeValidations.flatMap((n) => n.validation.errors),
    ...edgeValidations.flatMap((e) => e.validation.errors),
  ];

  return {
    nodes: {
      total: canvas.nodes.length,
      valid: validNodes,
      invalid: canvas.nodes.length - validNodes,
      validationRate:
        canvas.nodes.length > 0
          ? (validNodes / canvas.nodes.length) * 100
          : 100,
    },
    edges: {
      total: canvas.edges.length,
      valid: validEdges,
      invalid: canvas.edges.length - validEdges,
      validationRate:
        canvas.edges.length > 0
          ? (validEdges / canvas.edges.length) * 100
          : 100,
    },
    overall: {
      totalElements: canvas.nodes.length + canvas.edges.length,
      validElements: validNodes + validEdges,
      errors: totalErrors,
      healthy: totalErrors.length === 0,
    },
  };
};
