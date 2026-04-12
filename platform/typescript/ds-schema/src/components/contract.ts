/**
 * @fileoverview Component contract schemas - define the interface between
 * design system components and the builder/platform.
 */

import { z } from 'zod';

// ============================================================================
// Prop Type Schemas
// ============================================================================

export const PropTypeSchema = z.enum([
  'string',
  'number',
  'boolean',
  'array',
  'object',
  'function',
  'node', // ReactNode
  'element', // ReactElement
  'enum',
  'union',
  'literal',
  'token-ref', // Reference to a design token
  'component-ref', // Reference to another component
]);

export type PropType = z.infer<typeof PropTypeSchema>;

// ============================================================================
// Component Prop Schema
// ============================================================================

export const ComponentPropSchema = z.object({
  name: z.string(),
  type: PropTypeSchema,
  typeDetails: z.unknown().optional(), // For complex types (enum values, object shape, etc.)
  description: z.string().optional(),
  required: z.boolean().default(false),
  defaultValue: z.unknown().optional(),
  
  // Builder-specific metadata
  builderMetadata: z.object({
    // Control type for builder UI
    control: z.enum([
      'text',
      'number',
      'toggle',
      'select',
      'multiselect',
      'color',
      'token-select',
      'json',
      'code',
      'slot',
      'hidden', // Computed/internal
    ]).optional(),
    
    // Category for grouping in builder UI
    category: z.string().optional(),
    
    // Order within category
    order: z.number().optional(),
    
    // Whether this prop can be bound to data
    bindable: z.boolean().default(false),
    
    // Valid token types for token-ref props
    tokenTypes: z.array(z.string()).optional(),
    
    // Valid component types for component-ref props
    componentTypes: z.array(z.string()).optional(),
  }).optional(),
  
  // Validation rules
  validation: z.object({
    min: z.number().optional(),
    max: z.number().optional(),
    minLength: z.number().optional(),
    maxLength: z.number().optional(),
    pattern: z.string().optional(), // regex
    enum: z.array(z.unknown()).optional(),
  }).optional(),
});

export type ComponentProp = z.infer<typeof ComponentPropSchema>;

// ============================================================================
// Component Slot Schema
// ============================================================================

export const ComponentSlotSchema = z.object({
  name: z.string(),
  description: z.string().optional(),
  allowedComponents: z.array(z.string()).optional(), // Whitelist
  disallowedComponents: z.array(z.string()).optional(), // Blacklist
  maxChildren: z.number().optional(),
  minChildren: z.number().optional(),
  
  // Builder metadata
  builderMetadata: z.object({
    displayName: z.string().optional(),
    icon: z.string().optional(),
    required: z.boolean().default(false),
    defaultContent: z.unknown().optional(),
  }).optional(),
});

export type ComponentSlot = z.infer<typeof ComponentSlotSchema>;

// ============================================================================
// Component Event/Callback Schema
// ============================================================================

export const ComponentEventSchema = z.object({
  name: z.string(),
  description: z.string().optional(),
  payloadType: z.string().optional(), // TypeScript type string
  
  builderMetadata: z.object({
    // Can be wired to actions in builder
    actionable: z.boolean().default(true),
    category: z.string().optional(),
  }).optional(),
});

export type ComponentEvent = z.infer<typeof ComponentEventSchema>;

// ============================================================================
// Component Style Schema
// ============================================================================

export const ComponentStyleSchema = z.object({
  // CSS properties this component accepts
  styleProps: z.array(z.string()).optional(),
  
  // Token categories that can be applied
  tokenCategories: z.array(z.enum([
    'color',
    'spacing',
    'typography',
    'elevation',
    'border',
    'motion',
    'opacity',
  ])).optional(),
  
  // Style variants
  variants: z.record(z.string(), z.object({
    description: z.string().optional(),
    styleOverrides: z.record(z.string(), z.unknown()),
  })).optional(),
  
  // Size variants
  sizes: z.record(z.string(), z.object({
    description: z.string().optional(),
    styleOverrides: z.record(z.string(), z.unknown()),
  })).optional(),
});

export type ComponentStyle = z.infer<typeof ComponentStyleSchema>;

// ============================================================================
// Component Contract Schema
// ============================================================================

export const ComponentContractSchema = z.object({
  // Identification
  name: z.string(),
  version: z.string().default('1.0.0'),
  description: z.string().optional(),
  
  // Component metadata
  metadata: z.object({
    category: z.string(), // e.g., 'input', 'display', 'layout', 'feedback'
    subcategory: z.string().optional(),
    tags: z.array(z.string()).optional(),
    
    // Documentation
    docsUrl: z.string().optional(),
    storybookUrl: z.string().optional(),
    
    // Status
    status: z.enum(['draft', 'experimental', 'stable', 'deprecated']).default('draft'),
    
    // Platform support
    platforms: z.array(z.enum(['web', 'ios', 'android', 'figma'])).default(['web']),
    
    // Accessibility
    a11y: z.object({
      role: z.string().optional(),
      ariaSupported: z.boolean().default(true),
      keyboardNavigation: z.boolean().default(false),
      screenReader: z.enum(['supported', 'partial', 'not-tested', 'not-applicable']).default('not-tested'),
    }).optional(),
  }),
  
  // Interface definition
  props: z.array(ComponentPropSchema).default([]),
  slots: z.array(ComponentSlotSchema).default([]),
  events: z.array(ComponentEventSchema).default([]),
  styles: ComponentStyleSchema.optional(),
  
  // Dependencies
  dependencies: z.object({
    // Other components this component requires
    components: z.array(z.string()).optional(),
    
    // Tokens this component uses
    tokens: z.array(z.string()).optional(),
    
    // External libraries
    packages: z.array(z.object({
      name: z.string(),
      version: z.string(),
      optional: z.boolean().default(false),
    })).optional(),
  }).optional(),
  
  // Builder integration
  builder: z.object({
    // Icon for component picker
    icon: z.string().optional(),
    
    // Default props when dragging onto canvas
    defaultProps: z.record(z.string(), z.unknown()).optional(),
    
    // Canvas behavior
    canvas: z.object({
      resizable: z.boolean().default(true),
      draggable: z.boolean().default(true),
      selectable: z.boolean().default(true),
      container: z.boolean().default(false), // Can contain other components
    }).optional(),
    
    // Code generation
    codegen: z.object({
      importPath: z.string(),
      componentName: z.string(),
      namedExport: z.boolean().default(true),
    }).optional(),
  }).optional(),
  
  // Examples
  examples: z.array(z.object({
    name: z.string(),
    description: z.string().optional(),
    props: z.record(z.string(), z.unknown()),
    slots: z.record(z.string(), z.unknown()).optional(),
  })).optional(),
});

export type ComponentContract = z.infer<typeof ComponentContractSchema>;

// ============================================================================
// Validation
// ============================================================================

export function validateComponentContract(data: unknown): { success: true; data: ComponentContract } | { success: false; errors: z.ZodError } {
  const result = ComponentContractSchema.safeParse(data);
  if (result.success) {
    return { success: true, data: result.data };
  }
  return { success: false, errors: result.error };
}

/**
 * Compute a hash/fingerprint of the contract for change detection.
 */
export function computeContractHash(contract: ComponentContract): string {
  // Simple hash based on JSON serialization (in production, use proper hashing)
  const canonical = JSON.stringify({
    name: contract.name,
    version: contract.version,
    props: contract.props.map(p => ({ name: p.name, type: p.type, required: p.required })).sort((a, b) => a.name.localeCompare(b.name)),
    slots: contract.slots.map(s => s.name).sort(),
    events: contract.events.map(e => e.name).sort(),
  });
  
  // Simple DJB2 hash
  let hash = 5381;
  for (let i = 0; i < canonical.length; i++) {
    hash = ((hash << 5) + hash) + canonical.charCodeAt(i);
  }
  return (hash >>> 0).toString(16).padStart(8, '0');
}
