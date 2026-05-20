/**
 * @fileoverview Design-system document model.
 *
 * A DesignSystemDocument is the top-level container that ties together a base
 * preset, optional brand overrides, semantic token aliases, component variant
 * definitions, and the fully-resolved (materialized) token map.  It is the
 * canonical data shape consumed by all DS Generator targets (CSS, JSON,
 * Tailwind, React theme) and by the Studio design-system workflow.
 *
 * @doc.type module
 * @doc.purpose Design-system document model and factory
 * @doc.layer ds-generator
 * @doc.pattern Model
 */

import { z } from 'zod';

// ============================================================================
// Semantic token alias
// ============================================================================

/**
 * A semantic token alias resolves a human-meaningful name (e.g.
 * `color.text.primary`) to a base token key that is already present in the
 * materialized token map produced by the preset/brand pipeline.
 */
export const SemanticTokenAliasSchema = z.object({
  /** Stable semantic name used by components (dot-separated). */
  alias: z.string().min(1),
  /**
   * Key in the resolved token map this alias points to.
   * The value must exist in `MaterializedTokens` at emit time.
   */
  tokenKey: z.string().min(1),
  /** Human description of what this alias represents. */
  description: z.string().optional(),
  /**
   * Category the alias belongs to; used when emitting CSS custom properties
   * into themed scopes.
   */
  category: z
    .enum([
      'color',
      'typography',
      'spacing',
      'shadow',
      'motion',
      'zIndex',
      'border',
      'other',
    ])
    .default('other'),
});

export type SemanticTokenAlias = z.infer<typeof SemanticTokenAliasSchema>;

// ============================================================================
// Component variant / state definition
// ============================================================================

/**
 * Canonical component interaction state names recognised by the DS generator.
 *
 * These match the W3C / ARIA interaction model and the set of CSS
 * pseudo-classes used by design-system component libraries. Using a strict enum
 * here ensures that token override maps and golden snapshots do not silently
 * accept arbitrary strings (e.g. typos like "focussed" or "disbaled").
 *
 * To add a state that is not in this list, open a PR that updates this enum,
 * the golden snapshot tests, and the CSS emit target simultaneously.
 */
export const CANONICAL_COMPONENT_STATES = [
  'default',
  'hover',
  'active',
  'focus',
  'focus-visible',
  'disabled',
  'loading',
  'selected',
  'error',
  'success',
  'warning',
] as const;

export type CanonicalComponentState = typeof CANONICAL_COMPONENT_STATES[number];

export const ComponentStateSchema = z.object({
  /**
   * State name — must be one of the canonical interaction states.
   * Use `z.enum` so the schema rejects unknown strings at runtime.
   */
  state: z.enum(CANONICAL_COMPONENT_STATES),
  /** Token overrides to apply in this state, keyed by CSS custom property name. */
  tokenOverrides: z.record(z.string(), z.string()),
});

export type ComponentState = z.infer<typeof ComponentStateSchema>;

export const ComponentVariantDefinitionSchema = z.object({
  /** Unique component identifier (e.g. "Button", "Card", "Input"). */
  componentId: z.string().min(1),
  /**
   * Named variants (e.g. `primary`, `secondary`, `ghost`).
   * Each variant maps to a record of CSS custom property overrides.
   */
  variants: z.record(z.string(), z.record(z.string(), z.string())),
  /** State-specific token overrides applied after variant resolution. */
  states: z.array(ComponentStateSchema).default([]),
});

export type ComponentVariantDefinition = z.infer<
  typeof ComponentVariantDefinitionSchema
>;

// ============================================================================
// Design-system document
// ============================================================================

export const DS_DOCUMENT_SCHEMA_VERSION = '1.0.0' as const;

export const DesignSystemDocumentSchema = z.object({
  /** Stable unique identifier for this document. */
  documentId: z.string().min(1),
  /** Human-readable display name. */
  name: z.string().min(1),
  /** Schema version for forward-compatibility detection. */
  schemaVersion: z.literal(DS_DOCUMENT_SCHEMA_VERSION),
  /**
   * ID of the base preset used to derive the initial token values.
   * Must be one of the built-in preset IDs or a custom preset registered via
   * the extension mechanism.
   */
  basePresetId: z.string().min(1),
  /**
   * Optional brand config name that was applied on top of the preset.
   * `undefined` means the document uses the raw preset without brand overrides.
   */
  brandName: z.string().optional(),
  /** Semantic token aliases that map component-level names to base tokens. */
  semanticAliases: z.array(SemanticTokenAliasSchema).default([]),
  /** Per-component variant and state definitions. */
  componentVariants: z.array(ComponentVariantDefinitionSchema).default([]),
  /**
   * The fully-resolved token map produced by combining preset + brand +
   * any custom property injections.  Stored here to make the document
   * self-contained and to enable round-trip validation without re-running
   * the full materialization pipeline.
   */
  resolvedTokens: z.record(z.string(), z.unknown()),
  /** ISO-8601 timestamp when this document was last generated. */
  generatedAt: z.string(),
  /** Free-form metadata (product, team, version notes, etc.). */
  metadata: z.record(z.string(), z.unknown()).optional(),
});

export type DesignSystemDocument = z.infer<typeof DesignSystemDocumentSchema>;

// ============================================================================
// Generation context (for deterministic testing)
// ============================================================================

/**
 * Context passed to factory functions that produce time-stamped or ID-stamped
 * documents. Injecting these functions makes the output deterministic in tests.
 *
 * Production callers do not need to pass this — the defaults use the real clock
 * and `crypto.randomUUID()`.
 */
export interface GenerationContext {
  /**
   * Returns the current ISO-8601 timestamp string.
   * Default (production): `() => new Date().toISOString()`
   * Test: `() => "2024-01-01T00:00:00.000Z"`
   */
  readonly clockFn: () => string;
  /**
   * Returns a new unique ID string.
   * Default (production): `() => crypto.randomUUID()`
   * Test: deterministic counter or fixed value.
   */
  readonly idFn: () => string;
}

const DEFAULT_GENERATION_CONTEXT: GenerationContext = {
  clockFn: () => new Date().toISOString(),
  idFn: () => crypto.randomUUID(),
};

// ============================================================================
// Factory
// ============================================================================

/**
 * Create a new, empty DesignSystemDocument anchored to a preset.
 *
 * The caller is responsible for populating `resolvedTokens` after running the
 * preset/brand materialization pipeline.
 *
 * @param documentId - Stable unique ID for this document.
 * @param name - Human-readable display name.
 * @param basePresetId - ID of the base DS preset.
 * @param resolvedTokens - Fully-resolved token map from the materialization pipeline.
 * @param overrides - Optional partial field overrides.
 * @param context - Optional {@link GenerationContext} for deterministic testing.
 *   In production, omit this parameter to use the real clock and UUID generator.
 */
export function createDesignSystemDocument(
  documentId: string,
  name: string,
  basePresetId: string,
  resolvedTokens: Record<string, unknown>,
  overrides?: Partial<
    Omit<
      DesignSystemDocument,
      'documentId' | 'name' | 'schemaVersion' | 'basePresetId' | 'resolvedTokens' | 'generatedAt'
    >
  >,
  context: GenerationContext = DEFAULT_GENERATION_CONTEXT,
): DesignSystemDocument {
  return {
    documentId,
    name,
    schemaVersion: DS_DOCUMENT_SCHEMA_VERSION,
    basePresetId,
    resolvedTokens,
    generatedAt: context.clockFn(),
    semanticAliases: overrides?.semanticAliases ?? [],
    componentVariants: overrides?.componentVariants ?? [],
    brandName: overrides?.brandName,
    metadata: overrides?.metadata,
  };
}
