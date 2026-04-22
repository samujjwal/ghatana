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

// ============================================================================
// Data Classification (mirrors platform-events DataClassification)
// ============================================================================

export const DataClassificationSchema = z.enum([
  'public',
  'internal',
  'confidential',
  'restricted',
  'pii',
  'sensitive',
] as const);

export type DataClassificationValue = z.infer<typeof DataClassificationSchema>;

// ============================================================================
// Telemetry Contract
// ============================================================================

/** Declares telemetry this component emits when used in production. */
export const ComponentTelemetryContractSchema = z.object({
  emittedEvents: z.array(z.object({
    name: z.string().min(1),
    description: z.string().optional(),
    containsPii: z.boolean().default(false),
  })).default([]),
  autoTracksInteractions: z.boolean().default(false),
  requiresConsentForTracking: z.boolean().default(false),
});

export type ComponentTelemetryContract = z.infer<typeof ComponentTelemetryContractSchema>;

// ============================================================================
// Observability Contract
// ============================================================================

export const ComponentObservabilityContractSchema = z.object({
  requiresTraceContext: z.boolean().default(false),
  performanceMarks: z.array(z.string()).default([]),
  reportsRenderErrors: z.boolean().default(false),
});

export type ComponentObservabilityContract = z.infer<typeof ComponentObservabilityContractSchema>;

// ============================================================================
// AI Policy
// ============================================================================

/**
 * Canonical set of AI actions the builder can perform on a component.
 * Use `permittedActions` to declare which of these the AI may apply
 * autonomously (without human review for each change).
 */
export const AIActionTypeSchema = z.enum([
  'set-prop',       // AI autonomously sets a prop value
  'remove-prop',    // AI removes a prop
  'add-node',       // AI inserts a child node into a slot
  'remove-node',    // AI removes this node from the document
  'reorder-node',   // AI reorders children within a slot
  'add-binding',    // AI adds a data binding to this component
  'remove-binding', // AI removes a binding
  'resize',         // AI changes the component's size metadata
  'reposition',     // AI changes the component's position metadata
  'style-update',   // AI updates style/token-referenced props
]);

export type AIActionType = z.infer<typeof AIActionTypeSchema>;

export const ComponentAIPolicySchema = z.object({
  allowAutonomousConfiguration: z.boolean().default(true),
  /**
   * Prop names whose values must be reviewed by a human before the AI
   * change is applied. Applies regardless of `permittedActions`.
   */
  reviewRequiredProps: z.array(z.string()).default([]),
  usageGuidance: z.string().optional(),
  autoApplyConfidenceThreshold: z.number().min(0).max(1).default(0.9),
  /**
   * AI action types the builder is allowed to apply autonomously (without
   * per-change human approval). An empty list means all actions require
   * human review. Defaults to all actions permitted.
   */
  permittedActions: z.array(AIActionTypeSchema).default([]),
});

export type ComponentAIPolicy = z.infer<typeof ComponentAIPolicySchema>;

// ============================================================================
// Builder Accessibility Obligations
// ============================================================================

/**
 * Builder-level accessibility obligations for a component.
 * Complements the per-component `metadata.a11y` field which documents what
 * the component supports; this schema declares what the _builder_ must enforce
 * at design time before the document is considered accessible.
 */
export const BuilderA11yObligationsSchema = z.object({
  /**
   * Prop names (or ARIA attribute names, e.g. `aria-label`) that the builder
   * must prompt the author to set. Used by the builder inspector panel and
   * document validators to warn about missing accessibility declarations.
   */
  requiredA11yProps: z.array(z.string()).default([]),
  /**
   * When true, the builder must verify there is a close affordance (button or
   * keyboard handler) whenever this component traps focus (e.g. modals, drawers).
   */
  trapsFocusRequiresClose: z.boolean().default(false),
  /**
   * When true, the builder warns if the document contains this component
   * without a `prefers-reduced-motion` safeguard declared in a sibling or
   * parent contract.
   */
  motionRequiresReductionSupport: z.boolean().default(false),
  /**
   * The minimum WCAG success criteria level this component is designed to meet.
   * `A` is the least strict, `AAA` the most strict.
   */
  wcagLevel: z.enum(['A', 'AA', 'AAA']).default('AA'),
  /**
   * Free-text guidance shown in the builder accessibility panel.
   */
  a11yGuidance: z.string().optional(),
});

export type BuilderA11yObligations = z.infer<typeof BuilderA11yObligationsSchema>;

// ============================================================================
// Preview Restrictions
// ============================================================================

export const ComponentPreviewRestrictionsSchema = z.object({
  minimumTrustLevel: z.enum([
    'trusted-local',
    'trusted-controlled',
    'semi-trusted',
    'untrusted',
  ]).default('semi-trusted'),
  requiresNetwork: z.boolean().default(false),
  requiresStorage: z.boolean().default(false),
  requiresConsent: z.boolean().default(false),
});

export type ComponentPreviewRestrictions = z.infer<typeof ComponentPreviewRestrictionsSchema>;

// ============================================================================
// Configurator Hints
// ============================================================================

export const ComponentConfiguratorHintsSchema = z.object({
  groups: z.array(z.object({
    id: z.string().min(1),
    label: z.string().min(1),
    collapsed: z.boolean().default(false),
    propNames: z.array(z.string()),
  })).default([]),
  showAdvancedSection: z.boolean().default(true),
  showLivePreview: z.boolean().default(true),
  resettableProps: z.array(z.string()).default([]),
});

export type ComponentConfiguratorHints = z.infer<typeof ComponentConfiguratorHintsSchema>;

// ============================================================================
// Layout Semantics
// ============================================================================

export const ComponentLayoutSemanticsSchema = z.object({
  /** Whether this component can contain child components in the builder. */
  isContainer: z.boolean().default(false),
  defaultDisplay: z.enum(['block', 'inline', 'inline-block', 'flex', 'grid', 'none']).default('block'),
  draggable: z.boolean().default(true),
  resizable: z.boolean().default(true),
  aspectRatioLock: z.number().optional(),
  minDimensions: z.object({
    width: z.number().optional(),
    height: z.number().optional(),
  }).optional(),
  /**
   * Whether this component fills all available space in its parent container
   * (e.g. a divider, spacer, or full-width banner that should not be inline).
   */
  fillsParent: z.boolean().default(false),
  /** Components that this component should never be nested inside. */
  forbiddenAncestors: z.array(z.string()).default([]),
  /** Components that may be direct children of this component (whitelist). */
  allowedChildTypes: z.array(z.string()).optional(),
});

export type ComponentLayoutSemantics = z.infer<typeof ComponentLayoutSemanticsSchema>;

// ============================================================================
// Responsive Behavior Metadata
// ============================================================================

/**
 * Describes a breakpoint where this component's behavior or layout changes.
 * Breakpoint names align with common CSS framework conventions.
 */
export const ResponsiveBreakpointBehaviorSchema = z.object({
  /** Named breakpoint (e.g. 'sm', 'md', 'lg', 'xl'). */
  breakpoint: z.enum(['xs', 'sm', 'md', 'lg', 'xl', '2xl']),
  /**
   * Minimum viewport width (px) at which this breakpoint applies.
   * Matches typical Tailwind / Bootstrap breakpoint semantics.
   */
  minWidth: z.number().int().min(0),
  /**
   * Props whose defaults change at this breakpoint.
   * Record of propName → suggested default value at this viewport width.
   */
  propOverrides: z.record(z.string(), z.unknown()).optional(),
  /** Whether this component is hidden by default at this breakpoint. */
  hiddenByDefault: z.boolean().default(false),
  /** Notes for documentation / code generation. */
  notes: z.string().optional(),
});

export type ResponsiveBreakpointBehavior = z.infer<typeof ResponsiveBreakpointBehaviorSchema>;

/**
 * Captures how a component participates in responsive design.
 * This metadata drives the builder's responsive-overrides panel and code
 * generation of responsive utility classes or media-query wrappers.
 */
export const ComponentResponsiveMetadataSchema = z.object({
  /**
   * Whether this component changes its visual appearance or behavior
   * at different viewport widths.
   */
  isResponsive: z.boolean().default(false),
  /**
   * Breakpoint-specific behavior declarations.
   * Ordered from narrowest to widest (mobile-first).
   */
  breakpoints: z.array(ResponsiveBreakpointBehaviorSchema).default([]),
  /**
   * Props that can receive responsive overrides in the builder.
   * Each name must match a prop defined in `ComponentContract.props`.
   */
  responsiveProps: z.array(z.string()).default([]),
  /**
   * Whether the component participates in a container-query-aware
   * layout (as opposed to viewport-width breakpoints).
   */
  supportsContainerQuery: z.boolean().default(false),
  /**
   * The token-based responsive scale this component follows.
   * Matches the theme's spacing/typography scale if applicable.
   */
  responsiveScale: z.enum(['none', 'typography', 'spacing', 'both']).default('none'),
});

export type ComponentResponsiveMetadata = z.infer<typeof ComponentResponsiveMetadataSchema>;

// ============================================================================
// Privacy Metadata
// ============================================================================

/**
 * Privacy metadata specific to this component's role in a builder document.
 * Complements per-prop `dataClassification`; this field captures component-
 * level obligations that apply regardless of which specific prop is set.
 */
export const ComponentPrivacyContractSchema = z.object({
  /**
   * Whether using this component requires the user to explicitly confirm
   * a data-handling consent flow before publishing.
   */
  requiresConsentFlow: z.boolean().default(false),
  /**
   * Whether rendering this component may surface PII on screen even if the
   * individual props are not individually classified.
   */
  mayRenderPii: z.boolean().default(false),
  /**
   * If set, the component must not appear in previews with a trust level
   * below this value (supplements preview restrictions).
   * Valid values mirror `ComponentPreviewRestrictions.minimumTrustLevel`.
   */
  minimumPreviewTrustLevel: z
    .enum(['trusted-local', 'trusted-controlled', 'semi-trusted', 'untrusted'])
    .optional(),
  /** Regulatory frameworks this component's usage must be reviewed under. */
  regulatoryFrameworks: z.array(z.enum(['GDPR', 'CCPA', 'HIPAA', 'PCI-DSS', 'SOC2'])).default([]),
  /** Free-text privacy guidance shown in the builder inspector panel. */
  privacyGuidance: z.string().optional(),
});

export type ComponentPrivacyContract = z.infer<typeof ComponentPrivacyContractSchema>;

// ============================================================================
// Component Prop Schema
// ============================================================================

export const ComponentPropSchema = z.object({
  name: z.string().min(1, 'Prop name must not be empty'),
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

  // Privacy / security metadata for this prop
  dataClassification: DataClassificationSchema.optional(),
  secretBearing: z.boolean().optional(),
  reviewRequired: z.boolean().optional(),
});

export type ComponentProp = z.infer<typeof ComponentPropSchema>;

// ============================================================================
// Component Slot Schema
// ============================================================================

export const ComponentSlotSchema = z.object({
  name: z.string().min(1, 'Slot name must not be empty'),
  description: z.string().optional(),
  allowedComponents: z.array(z.string()).optional(), // Whitelist
  disallowedComponents: z.array(z.string()).optional(), // Blacklist
  maxChildren: z.number().optional(),
  minChildren: z.number().optional(),

  /**
   * Whether this slot is the default (unnamed) slot that receives children
   * passed directly to the component. At most one slot per contract should
   * have `isDefault: true`.
   */
  isDefault: z.boolean().default(false),

  /**
   * Whether children inside this slot can be sorted/reordered in the builder.
   * Useful for list-like containers (e.g. NavigationMenu items).
   */
  allowsReorder: z.boolean().default(true),

  /**
   * Whether this slot accepts only a single child node.
   * Equivalent to `maxChildren: 1` but more expressive in the builder UI.
   */
  isSingleChild: z.boolean().default(false),

  /**
   * The minimum aspect ratio the slot's bounding box should maintain
   * when the builder renders a placeholder. Used to prevent layout collapse
   * when the slot is empty during design time.
   */
  designTimeAspectRatio: z.number().optional(),

  // Builder metadata
  builderMetadata: z.object({
    displayName: z.string().optional(),
    icon: z.string().optional(),
    required: z.boolean().default(false),
    defaultContent: z.unknown().optional(),
    /**
     * Hint to the builder palette about where to display this slot's drop
     * zone — 'inline' keeps the zone within the component's own frame,
     * 'overlay' draws a separate drop zone on top.
     */
    dropZoneMode: z.enum(['inline', 'overlay']).default('inline'),
  }).optional(),
});

export type ComponentSlot = z.infer<typeof ComponentSlotSchema>;

// ============================================================================
// Component Event/Callback Schema
// ============================================================================

export const ComponentEventSchema = z.object({
  name: z.string().min(1, 'Event name must not be empty'),
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
  // Identification — non-empty name enforced
  name: z.string().min(1, 'Component name must not be empty'),
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
    
    // Accessibility — richer coverage
    a11y: z.object({
      role: z.string().optional(),
      ariaRequired: z.boolean().default(false),
      ariaSupported: z.boolean().default(true),
      keyboardNavigation: z.boolean().default(false),
      screenReader: z.enum(['supported', 'partial', 'not-tested', 'not-applicable']).default('not-tested'),
      trapsFocus: z.boolean().default(false),
      wcagCriteria: z.array(z.string()).default([]),
      notes: z.string().optional(),
    }).optional(),

    dataClassification: DataClassificationSchema.optional(),
    reviewRequired: z.boolean().optional(),
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
      /**
       * The HTML custom-element tag name used by the web/SSR renderer.
       * Defaults to `ghatana-{contractName.toLowerCase()}` if not provided.
       * Must be a valid custom element name (contain a hyphen).
       */
      htmlTagName: z.string().regex(/^[a-z][a-z0-9]*(?:-[a-z0-9]+)+$/).optional(),
    }).optional(),

    /**
     * Palette identity — how this component appears in the builder's
     * component picker and drag palette.
     */
    palette: z.object({
      /**
       * Group name for the component picker panel (e.g. 'Form Controls',
       * 'Data Display', 'Layout').
       */
      group: z.string().optional(),
      /**
       * Sub-group within the palette group for finer-grained organisation
       * (e.g. 'Text Inputs' under 'Form Controls').
       */
      subGroup: z.string().optional(),
      /** Display name shown in the palette (defaults to contract.name). */
      displayName: z.string().optional(),
      /**
       * Short description shown in the palette tooltip (defaults to
       * contract.description).
       */
      tooltip: z.string().optional(),
      /**
       * Rank within the palette group — lower numbers appear first.
       * Components with no rank are placed after ranked ones.
       */
      rank: z.number().int().optional(),
      /** Keywords for palette search (in addition to name and description). */
      searchKeywords: z.array(z.string()).default([]),
      /** Whether this component is pinned to the "Favourites" section. */
      featured: z.boolean().default(false),
    }).optional(),
  }).optional(),
  
  // Examples
  examples: z.array(z.object({
    name: z.string(),
    description: z.string().optional(),
    props: z.record(z.string(), z.unknown()),
    slots: z.record(z.string(), z.unknown()).optional(),
  })).optional(),

  // ════════════════════════════════════════════════════════════════
  // First-class platform extensions
  // ════════════════════════════════════════════════════════════════
  telemetry: ComponentTelemetryContractSchema.optional(),
  observability: ComponentObservabilityContractSchema.optional(),
  aiPolicy: ComponentAIPolicySchema.optional(),
  preview: ComponentPreviewRestrictionsSchema.optional(),
  configurator: ComponentConfiguratorHintsSchema.optional(),
  layout: ComponentLayoutSemanticsSchema.optional(),
  /** Responsive behavior metadata for builder responsive-editing features. */
  responsive: ComponentResponsiveMetadataSchema.optional(),
  /** Component-level privacy obligations for builder and preview flows. */
  privacy: ComponentPrivacyContractSchema.optional(),
  /** Builder-level accessibility obligations for this component. */
  builderA11y: BuilderA11yObligationsSchema.optional(),
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
 * Compute a deterministic hash/fingerprint of the contract for change detection.
 * Only structural fields are included — not documentation or policy fields.
 */
export function computeContractHash(contract: ComponentContract): string {
  const canonical = JSON.stringify({
    name: contract.name,
    version: contract.version,
    props: contract.props
      .map((p) => ({ name: p.name, type: p.type, required: p.required }))
      .sort((a, b) => a.name.localeCompare(b.name)),
    slots: contract.slots.map((s) => s.name).sort(),
    events: contract.events.map((e) => e.name).sort(),
  });

  // DJB2 hash — deterministic, fast
  let hash = 5381;
  for (let i = 0; i < canonical.length; i++) {
    hash = ((hash << 5) + hash) + canonical.charCodeAt(i);
  }
  return (hash >>> 0).toString(16).padStart(8, '0');
}
