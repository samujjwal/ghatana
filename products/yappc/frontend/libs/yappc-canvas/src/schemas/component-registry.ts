// Phase 8: Component Schema Registry - Comprehensive component validation system
// Uniform validation using Zod for all canvas components with migration support

import { z } from 'zod';

// Base component schema - all components must extend this
export const BaseComponentSchema = z.object({
  id: z.string().min(1, 'Component ID is required'),
  type: z.string().min(1, 'Component type is required'),
  version: z.string().default('1.0.0'),
  position: z.object({
    x: z.number(),
    y: z.number(),
  }),
  size: z.object({
    width: z.number().min(0),
    height: z.number().min(0),
  }),
  rotation: z.number().default(0),
  visible: z.boolean().default(true),
  locked: z.boolean().default(false),
  metadata: z.record(z.string(), z.any()).default({}),
  createdAt: z.string().datetime().optional(),
  updatedAt: z.string().datetime().optional(),
});

// Process node schema
export const ProcessNodeSchema = BaseComponentSchema.extend({
  type: z.literal('process'),
  data: z.object({
    label: z.string().min(1, 'Process label is required'),
    description: z.string().optional(),
    category: z.enum(['manual', 'automated', 'decision']).default('manual'),
    icon: z.string().optional(),
    color: z
      .string()
      .regex(/^#[0-9A-Fa-f]{6}$/)
      .default('#2196f3'),
    tags: z.array(z.string()).default([]),
  }),
});

// Decision node schema
export const DecisionNodeSchema = BaseComponentSchema.extend({
  type: z.literal('decision'),
  data: z.object({
    label: z.string().min(1, 'Decision label is required'),
    question: z.string().min(1, 'Decision question is required'),
    trueLabel: z.string().default('Yes'),
    falseLabel: z.string().default('No'),
    color: z
      .string()
      .regex(/^#[0-9A-Fa-f]{6}$/)
      .default('#ff9800'),
    tags: z.array(z.string()).default([]),
  }),
});

// Database node schema
export const DatabaseNodeSchema = BaseComponentSchema.extend({
  type: z.literal('database'),
  data: z.object({
    label: z.string().min(1, 'Database label is required'),
    dbType: z.enum(['sql', 'nosql', 'cache', 'graph']).default('sql'),
    technology: z.string().optional(),
    description: z.string().optional(),
    color: z
      .string()
      .regex(/^#[0-9A-Fa-f]{6}$/)
      .default('#4caf50'),
    tags: z.array(z.string()).default([]),
  }),
});

// Group/Container schema
export const GroupNodeSchema = BaseComponentSchema.extend({
  type: z.literal('group'),
  data: z.object({
    label: z.string().min(1, 'Group label is required'),
    description: z.string().optional(),
    childIds: z.array(z.string()).default([]),
    backgroundColor: z
      .string()
      .regex(/^#[0-9A-Fa-f]{6}$/)
      .default('#f5f5f5'),
    borderColor: z
      .string()
      .regex(/^#[0-9A-Fa-f]{6}$/)
      .default('#cccccc'),
    borderWidth: z.number().min(0).default(1),
    collapsed: z.boolean().default(false),
    tags: z.array(z.string()).default([]),
  }),
});

// Service node schema
export const ServiceNodeSchema = BaseComponentSchema.extend({
  type: z.literal('service'),
  data: z.object({
    label: z.string().min(1, 'Service label is required'),
    serviceType: z
      .enum(['api', 'microservice', 'function', 'external'])
      .default('api'),
    protocol: z.enum(['http', 'grpc', 'websocket', 'tcp']).default('http'),
    endpoint: z.string().url().optional(),
    description: z.string().optional(),
    color: z
      .string()
      .regex(/^#[0-9A-Fa-f]{6}$/)
      .default('#9c27b0'),
    ports: z
      .array(
        z.object({
          name: z.string(),
          port: z.number().min(1).max(65535),
          protocol: z.enum(['tcp', 'udp']).default('tcp'),
        })
      )
      .default([]),
    tags: z.array(z.string()).default([]),
  }),
});

// UI Component schemas for page designer
export const UIButtonSchema = BaseComponentSchema.extend({
  type: z.literal('ui-button'),
  data: z.object({
    text: z.string().default('Button'),
    variant: z.enum(['contained', 'outlined', 'text']).default('contained'),
    color: z
      .enum(['primary', 'secondary', 'success', 'error', 'info', 'warning'])
      .default('primary'),
    size: z.enum(['small', 'medium', 'large']).default('medium'),
    disabled: z.boolean().default(false),
    fullWidth: z.boolean().default(false),
    startIcon: z.string().optional(),
    endIcon: z.string().optional(),
    onClick: z.string().optional(), // Function name or code
  }),
});

export const UICardSchema = BaseComponentSchema.extend({
  type: z.literal('ui-card'),
  data: z.object({
    title: z.string().optional(),
    subtitle: z.string().optional(),
    content: z.string().optional(),
    elevation: z.number().min(0).max(24).default(2),
    showActions: z.boolean().default(false),
    actions: z
      .array(
        z.object({
          label: z.string(),
          action: z.string(),
        })
      )
      .default([]),
  }),
});

export const UITextFieldSchema = BaseComponentSchema.extend({
  type: z.literal('ui-textfield'),
  data: z.object({
    label: z.string().default(''),
    placeholder: z.string().optional(),
    variant: z.enum(['outlined', 'filled', 'standard']).default('outlined'),
    size: z.enum(['small', 'medium']).default('medium'),
    required: z.boolean().default(false),
    disabled: z.boolean().default(false),
    multiline: z.boolean().default(false),
    rows: z.number().min(1).default(1),
    type: z
      .enum(['text', 'email', 'password', 'number', 'tel', 'url'])
      .default('text'),
    helperText: z.string().optional(),
    error: z.boolean().default(false),
  }),
});

// Edge/Connection schema
export const EdgeSchema = z.object({
  id: z.string().min(1, 'Edge ID is required'),
  source: z.string().min(1, 'Source node ID is required'),
  target: z.string().min(1, 'Target node ID is required'),
  type: z
    .enum(['straight', 'smoothstep', 'step', 'bezier'])
    .default('smoothstep'),
  animated: z.boolean().default(false),
  label: z.string().optional(),
  style: z
    .object({
      stroke: z.string().default('#b1b1b7'),
      strokeWidth: z.number().min(1).default(2),
      strokeDasharray: z.string().optional(),
    })
    .optional(),
  markerEnd: z
    .object({
      type: z.enum(['arrow', 'arrowclosed']).default('arrowclosed'),
      color: z.string().default('#b1b1b7'),
    })
    .optional(),
  data: z
    .object({
      label: z.string().optional(),
      condition: z.string().optional(), // For decision nodes
      weight: z.number().optional(),
      metadata: z.record(z.string(), z.any()).default(() => ({}) as unknown),
    })
    .default(() => ({}) as unknown),
});

// Union of all component schemas
export const ComponentSchema = z.discriminatedUnion('type', [
  ProcessNodeSchema,
  DecisionNodeSchema,
  DatabaseNodeSchema,
  GroupNodeSchema,
  ServiceNodeSchema,
  UIButtonSchema,
  UICardSchema,
  UITextFieldSchema,
]);

// Type exports
/**
 *
 */
export type BaseComponent = z.infer<typeof BaseComponentSchema>;
/**
 *
 */
export type ProcessNode = z.infer<typeof ProcessNodeSchema>;
/**
 *
 */
export type DecisionNode = z.infer<typeof DecisionNodeSchema>;
/**
 *
 */
export type DatabaseNode = z.infer<typeof DatabaseNodeSchema>;
/**
 *
 */
export type GroupNode = z.infer<typeof GroupNodeSchema>;
/**
 *
 */
export type ServiceNode = z.infer<typeof ServiceNodeSchema>;
/**
 *
 */
export type UIButton = z.infer<typeof UIButtonSchema>;
/**
 *
 */
export type UICard = z.infer<typeof UICardSchema>;
/**
 *
 */
export type UITextField = z.infer<typeof UITextFieldSchema>;
/**
 *
 */
export type CanvasComponent = z.infer<typeof ComponentSchema>;
/**
 *
 */
export type CanvasEdge = z.infer<typeof EdgeSchema>;

// Schema registry for components
/**
 *
 */
export class ComponentSchemaRegistry {
  private static instance: ComponentSchemaRegistry;
  private schemas = new Map<string, z.ZodSchema>();
  private migrations = new Map<string, Array<MigrationFunction>>();
  private defaultData = new Map<string, () => Partial<CanvasComponent>>();

  /**
   *
   */
  private constructor() {
    this.registerDefaultSchemas();
    this.registerDefaultData();
  }

  /**
   *
   */
  public static getInstance(): ComponentSchemaRegistry {
    if (!ComponentSchemaRegistry.instance) {
      ComponentSchemaRegistry.instance = new ComponentSchemaRegistry();
    }
    return ComponentSchemaRegistry.instance;
  }

  /**
   *
   */
  private registerDefaultSchemas(): void {
    this.schemas.set('process', ProcessNodeSchema);
    this.schemas.set('decision', DecisionNodeSchema);
    this.schemas.set('database', DatabaseNodeSchema);
    this.schemas.set('group', GroupNodeSchema);
    this.schemas.set('service', ServiceNodeSchema);
    this.schemas.set('ui-button', UIButtonSchema);
    this.schemas.set('ui-card', UICardSchema);
    this.schemas.set('ui-textfield', UITextFieldSchema);
    this.schemas.set('edge', EdgeSchema);
  }

  /**
   *
   */
  private registerDefaultData(): void {
    this.defaultData.set('process', () => ({
      type: 'process',
      data: {
        label: 'New Process',
        category: 'manual',
        color: '#2196f3',
        tags: [],
      },
    }));

    this.defaultData.set('decision', () => ({
      type: 'decision',
      data: {
        label: 'Decision Point',
        question: 'Condition met?',
        trueLabel: 'Yes',
        falseLabel: 'No',
        color: '#ff9800',
        tags: [],
      },
    }));

    this.defaultData.set('database', () => ({
      type: 'database',
      data: {
        label: 'Database',
        dbType: 'sql',
        color: '#4caf50',
        tags: [],
      },
    }));

    this.defaultData.set('group', () => ({
      type: 'group',
      data: {
        label: 'Group',
        childIds: [],
        backgroundColor: '#f5f5f5',
        borderColor: '#cccccc',
        borderWidth: 1,
        collapsed: false,
        tags: [],
      },
    }));

    this.defaultData.set('service', () => ({
      type: 'service',
      data: {
        label: 'Service',
        serviceType: 'api',
        protocol: 'http',
        color: '#9c27b0',
        ports: [],
        tags: [],
      },
    }));

    this.defaultData.set('ui-button', () => ({
      type: 'ui-button',
      data: {
        text: 'Button',
        variant: 'contained',
        color: 'primary',
        size: 'medium',
        disabled: false,
        fullWidth: false,
      },
    }));

    this.defaultData.set('ui-card', () => ({
      type: 'ui-card',
      data: {
        elevation: 2,
        showActions: false,
        actions: [],
      },
    }));

    this.defaultData.set('ui-textfield', () => ({
      type: 'ui-textfield',
      data: {
        label: 'Text Field',
        variant: 'outlined',
        size: 'medium',
        required: false,
        disabled: false,
        multiline: false,
        rows: 1,
        type: 'text',
        error: false,
      },
    }));
  }

  // Register a custom schema
  /**
   *
   */
  public registerSchema(type: string, schema: z.ZodSchema): void {
    this.schemas.set(type, schema);
  }

  // Get schema for a component type
  /**
   *
   */
  public getSchema(type: string): z.ZodSchema | undefined {
    return this.schemas.get(type);
  }

  // Validate component data
  /**
   *
   */
  public validate(
    type: string,
    data: unknown
  ): ValidationResult<CanvasComponent> {
    const schema = this.getSchema(type);
    if (!schema) {
      return {
        success: false,
        error: new Error(`No schema registered for type: ${type}`),
        errors: [`No schema registered for type: ${type}`],
      };
    }

    try {
      const validated = schema.parse(data);
      return {
        success: true,
        data: validated as CanvasComponent,
        errors: [],
      };
    } catch (error) {
      if (error instanceof z.ZodError) {
        return {
          success: false,
          error,
          errors: (error as unknown).errors.map(
            (e: unknown) => `${e.path.join('.')}: ${e.message}`
          ),
        };
      }
      return {
        success: false,
        error: error as Error,
        errors: ['Unknown validation error'],
      };
    }
  }

  // Safe parse component data
  /**
   *
   */
  public safeParse(type: string, data: unknown): z.SafeParseReturnType<unknown, unknown> {
    const schema = this.getSchema(type);
    if (!schema) {
      return {
        success: false,
        error: new z.ZodError([
          {
            code: 'custom',
            message: `No schema registered for type: ${type}`,
            path: ['type'],
          },
        ]),
      };
    }

    return schema.safeParse(data);
  }

  // Get default data for a component type
  /**
   *
   */
  public getDefaultData(type: string): Partial<CanvasComponent> | undefined {
    const factory = this.defaultData.get(type);
    return factory ? factory() : undefined;
  }

  // Register default data factory
  /**
   *
   */
  public setDefaultData(
    type: string,
    factory: () => Partial<CanvasComponent>
  ): void {
    this.defaultData.set(type, factory);
  }

  // List all registered schemas
  /**
   *
   */
  public listSchemas(): string[] {
    return Array.from(this.schemas.keys());
  }

  // Register migration function
  /**
   *
   */
  public registerMigration(type: string, migration: MigrationFunction): void {
    if (!this.migrations.has(type)) {
      this.migrations.set(type, []);
    }
    this.migrations.get(type)!.push(migration);
  }

  // Migrate component data
  /**
   *
   */
  public migrate(
    type: string,
    data: unknown,
    fromVersion: string,
    toVersion: string
  ): ValidationResult<CanvasComponent> {
    const migrations = this.migrations.get(type) || [];
    let current = data;

    // Apply migrations sequentially
    for (const migration of migrations) {
      if (
        migration.fromVersion === fromVersion &&
        migration.toVersion === toVersion
      ) {
        try {
          current = migration.migrate(current);
          break;
        } catch (error) {
          return {
            success: false,
            error: error as Error,
            errors: [`Migration failed: ${(error as Error).message}`],
          };
        }
      }
    }

    // Validate after migration
    return this.validate(type, current);
  }
}

// Migration function interface
/**
 *
 */
export interface MigrationFunction {
  fromVersion: string;
  toVersion: string;
  migrate: (data: unknown) => unknown;
}

// Validation result interface
/**
 *
 */
export interface ValidationResult<T> {
  success: boolean;
  data?: T;
  error?: Error;
  errors: string[];
}

// Utility functions
export const componentSchemaRegistry = ComponentSchemaRegistry.getInstance();

export const validateComponent = (
  type: string,
  data: unknown
): ValidationResult<CanvasComponent> => {
  return componentSchemaRegistry.validate(type, data);
};

export const safeParseComponent = (type: string, data: unknown): z.SafeParseReturnType<unknown, unknown> => {
  return componentSchemaRegistry.safeParse(type, data);
};

export const getComponentDefault = (
  type: string
): Partial<CanvasComponent> | undefined => {
  return componentSchemaRegistry.getDefaultData(type);
};

export const createComponent = (
  type: string,
  overrides: Partial<CanvasComponent> = {}
): CanvasComponent => {
  const defaults = getComponentDefault(type);
  if (!defaults) {
    throw new Error(`No default data registered for type: ${type}`);
  }

  const component = {
    id: `${type}-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
    version: '1.0.0',
    position: { x: 0, y: 0 },
    size: { width: 200, height: 100 },
    rotation: 0,
    visible: true,
    locked: false,
    metadata: {},
    ...defaults,
    ...overrides,
  };

  const validation = validateComponent(type, component);
  if (!validation.success) {
    throw new Error(
      `Failed to create component: ${validation.errors.join(', ')}`
    );
  }

  return validation.data!;
};

// Batch validation utilities
export const validateBatch = (
  components: Array<{ type: string; data: unknown }>
): Array<ValidationResult<CanvasComponent>> => {
  return components.map(({ type, data }) => validateComponent(type, data));
};

export const validateCanvas = (canvas: {
  nodes: unknown[];
  edges: unknown[];
}): {
  nodes: Array<ValidationResult<CanvasComponent>>;
  edges: Array<ValidationResult<CanvasEdge>>;
} => {
  return {
    nodes: canvas.nodes.map((node: unknown) => validateComponent(node.type, node)),
    edges: canvas.edges.map((edge: unknown) => {
      const result = EdgeSchema.safeParse(edge);
      return {
        success: result.success,
        data: result.success ? result.data : undefined,
        error: result.success ? undefined : result.error,
        errors: result.success
          ? []
          : (result.error as unknown).errors.map((e: unknown) => e.message),
      };
    }),
  };
};

// Export/Import helpers with validation
export const exportComponentWithValidation = (
  component: CanvasComponent
): string => {
  const validation = validateComponent(component.type, component);
  if (!validation.success) {
    throw new Error(
      `Component validation failed: ${validation.errors.join(', ')}`
    );
  }
  return JSON.stringify(component, null, 2);
};

export const importComponentWithValidation = (
  json: string
): CanvasComponent => {
  try {
    const data = JSON.parse(json);
    const validation = validateComponent(data.type, data);
    if (!validation.success) {
      throw new Error(
        `Component validation failed: ${validation.errors.join(', ')}`
      );
    }
    return validation.data!;
  } catch (error) {
    if (error instanceof SyntaxError) {
      throw new Error(`Invalid JSON: ${error.message}`);
    }
    throw error;
  }
};
