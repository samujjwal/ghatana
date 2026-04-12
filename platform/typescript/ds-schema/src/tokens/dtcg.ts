/**
 * @fileoverview DTCG (Design Tokens Community Group) aligned token schemas.
 *
 * @see https://design-tokens.github.io/community-group/format/
 *
 * Implements the DTCG specification for design token interoperability.
 */

import { z } from 'zod';

// ============================================================================
// DTCG Token Value Types
// ============================================================================

/** DTCG color token value - hex, rgb, rgba, hsl, etc. */
export const ColorTokenValueSchema = z.string().refine(
  (val) => {
    // Basic color format validation
    const hexRegex = /^#([A-Fa-f0-9]{3,4}|[A-Fa-f0-9]{6}|[A-Fa-f0-9]{8})$/;
    const rgbRegex = /^rgb\(\s*\d+\s*,\s*\d+\s*,\s*\d+\s*\)$/;
    const rgbaRegex = /^rgba\(\s*\d+\s*,\s*\d+\s*,\s*\d+\s*,\s*[\d.]+\s*\)$/;
    const hslRegex = /^hsl\(\s*\d+\s*,\s*\d+%?\s*,\s*\d+%?\s*\)$/;
    return hexRegex.test(val) || rgbRegex.test(val) || rgbaRegex.test(val) || hslRegex.test(val);
  },
  { message: 'Invalid color format' }
);

/** DTCG dimension token value - px, rem, em, %, etc. */
export const DimensionTokenValueSchema = z.string().refine(
  (val) => /^-?\d+(\.\d+)?(px|rem|em|%|vh|vw|vmin|vmax|ex|ch|cm|mm|in|pt|pc)$/.test(val),
  { message: 'Invalid dimension format (e.g., 16px, 1rem, 100%)' }
);

/** DTCG font family token value. */
export const FontFamilyTokenValueSchema = z.union([
  z.string(),
  z.array(z.string()),
]);

/** DTCG font weight token value. */
export const FontWeightTokenValueSchema = z.union([
  z.number().int().min(1).max(1000),
  z.enum(['thin', 'extra-light', 'light', 'normal', 'medium', 'semi-bold', 'bold', 'extra-bold', 'black']),
]);

/** DTCG duration token value - milliseconds. */
export const DurationTokenValueSchema = z.string().refine(
  (val) => /^\d+(\.\d+)?ms$/.test(val),
  { message: 'Invalid duration format (e.g., 300ms)' }
);

/** DTCG cubic bezier token value. */
export const CubicBezierTokenValueSchema = z.tuple([
  z.number().min(0).max(1),
  z.number(),
  z.number().min(0).max(1),
  z.number(),
]);

/** DTCG number token value. */
export const NumberTokenValueSchema = z.number();

/** DTCG stroke style token value. */
export const StrokeStyleTokenValueSchema = z.union([
  z.enum(['solid', 'dashed', 'dotted', 'double', 'groove', 'ridge', 'inset', 'outset']),
  z.object({
    lineCap: z.enum(['butt', 'round', 'square']).optional(),
    dashArray: z.array(DimensionTokenValueSchema),
  }),
]);

/** DTCG border token value. */
export const BorderTokenValueSchema = z.object({
  color: ColorTokenValueSchema,
  width: DimensionTokenValueSchema,
  style: StrokeStyleTokenValueSchema,
});

/** DTCG shadow token value. */
export const ShadowTokenValueSchema = z.object({
  color: ColorTokenValueSchema,
  offsetX: DimensionTokenValueSchema,
  offsetY: DimensionTokenValueSchema,
  blur: DimensionTokenValueSchema.optional(),
  spread: DimensionTokenValueSchema.optional(),
});

/** DTCG gradient token value. */
export const GradientStopSchema = z.object({
  color: ColorTokenValueSchema,
  position: z.number().min(0).max(1),
});

export const GradientTokenValueSchema = z.object({
  type: z.enum(['linear', 'radial', 'conic']),
  stops: z.array(GradientStopSchema).min(2),
  angle: z.number().optional(), // degrees for linear
  position: z.string().optional(), // center position for radial
});

/** DTCG easing curve token. */
export const EasingTokenValueSchema = z.union([
  z.enum(['linear', 'ease', 'ease-in', 'ease-out', 'ease-in-out']),
  CubicBezierTokenValueSchema,
]);

// ============================================================================
// Token Value Union
// ============================================================================

export const TokenValueSchema = z.union([
  ColorTokenValueSchema,
  DimensionTokenValueSchema,
  FontFamilyTokenValueSchema,
  FontWeightTokenValueSchema,
  DurationTokenValueSchema,
  CubicBezierTokenValueSchema,
  NumberTokenValueSchema,
  StrokeStyleTokenValueSchema,
  BorderTokenValueSchema,
  ShadowTokenValueSchema,
  GradientTokenValueSchema,
  EasingTokenValueSchema,
  z.string(), // fallback for any custom value
]);

export type TokenValue = z.infer<typeof TokenValueSchema>;

// ============================================================================
// DTCG Token Structure
// ============================================================================

/** DTCG token $type values per specification. */
export const TokenTypeSchema = z.enum([
  'color',
  'dimension',
  'fontFamily',
  'fontWeight',
  'duration',
  'cubicBezier',
  'number',
  'strokeStyle',
  'border',
  'shadow',
  'gradient',
  'transition',
  'font',
]);

export type TokenType = z.infer<typeof TokenTypeSchema>;

/** DTCG token metadata (description, extensions). */
export const TokenMetaSchema = z.object({
  $description: z.string().optional(),
  $deprecated: z.union([z.boolean(), z.string()]).optional(),
}).catchall(z.unknown()); // allow extensions

/** DTCG base token structure. */
export const BaseTokenSchema = z.object({
  $value: TokenValueSchema,
  $type: TokenTypeSchema.optional(),
  $description: z.string().optional(),
  $deprecated: z.union([z.boolean(), z.string()]).optional(),
});

export type BaseToken = z.infer<typeof BaseTokenSchema>;

/** DTCG token with explicit type. */
export const TypedTokenSchema = <T extends TokenType>(type: T) =>
  z.object({
    $value: TokenValueSchema,
    $type: z.literal(type),
    $description: z.string().optional(),
    $deprecated: z.union([z.boolean(), z.string()]).optional(),
  });

// ============================================================================
// DTCG Token Groups
// ============================================================================

/** A group of tokens (can be nested). */
export const TokenGroupSchema: z.ZodType<TokenGroup> = z.lazy(() =>
  z.record(
    z.string(),
    z.union([
      BaseTokenSchema,
      TokenGroupSchema, // nested groups
    ])
  )
);

export interface TokenGroup {
  [key: string]: BaseToken | TokenGroup;
}

/** Root DTCG token file structure. */
export const DTCGTokenFileSchema = z.object({
  $schema: z.string().optional(),
  $version: z.enum(['1.0.0', '1.1.0']).default('1.0.0'),
}).catchall(z.union([BaseTokenSchema, TokenGroupSchema]));

export type DTCGTokenFile = z.infer<typeof DTCGTokenFileSchema>;

// ============================================================================
// Ghatana Extensions
// ============================================================================

/** Ghatana-specific token extensions. */
export const GhatanaTokenExtensionSchema = z.object({
  $ghatana: z.object({
    category: z.enum(['color', 'spacing', 'typography', 'elevation', 'border', 'motion', 'opacity', 'z-index']),
    usage: z.string().optional(),
    platform: z.array(z.enum(['web', 'ios', 'android', 'figma'])).optional(),
    componentRefs: z.array(z.string()).optional(),
    themeable: z.boolean().default(true),
    source: z.string().optional(), // e.g., 'brand-guidelines-v2'
  }).optional(),
});

/** Extended token with Ghatana metadata. */
export const ExtendedTokenSchema = BaseTokenSchema.merge(GhatanaTokenExtensionSchema);

export type ExtendedToken = z.infer<typeof ExtendedTokenSchema>;

// ============================================================================
// Validation Functions
// ============================================================================

/** Maps token $type to its type-specific value schema. */
const TOKEN_TYPE_VALUE_SCHEMAS: Record<string, z.ZodTypeAny> = {
  color: ColorTokenValueSchema,
  dimension: DimensionTokenValueSchema,
  fontFamily: FontFamilyTokenValueSchema,
  fontWeight: FontWeightTokenValueSchema,
  duration: DurationTokenValueSchema,
  cubicBezier: CubicBezierTokenValueSchema,
  number: NumberTokenValueSchema,
  strokeStyle: StrokeStyleTokenValueSchema,
  border: BorderTokenValueSchema,
  shadow: ShadowTokenValueSchema,
  gradient: GradientTokenValueSchema,
  transition: EasingTokenValueSchema,
};

/**
 * Validates a DTCG token file.
 */
export function validateDTCGTokenFile(data: unknown): { success: true; data: DTCGTokenFile } | { success: false; errors: z.ZodError } {
  const result = DTCGTokenFileSchema.safeParse(data);
  if (result.success) {
    return { success: true, data: result.data };
  }
  return { success: false, errors: result.error };
}

/**
 * Validates a single token. When $type is present, the $value is validated
 * against the type-specific schema to catch semantically invalid values.
 */
export function validateToken(data: unknown): { success: true; data: BaseToken } | { success: false; errors: z.ZodError } {
  const result = BaseTokenSchema.safeParse(data);
  if (!result.success) {
    return { success: false, errors: result.error };
  }
  const token = result.data;
  if (token.$type !== undefined) {
    const typeSchema = TOKEN_TYPE_VALUE_SCHEMAS[token.$type];
    if (typeSchema !== undefined) {
      const valueResult = typeSchema.safeParse(token.$value);
      if (!valueResult.success) {
        return { success: false, errors: valueResult.error };
      }
    }
  }
  return { success: true, data: token };
}

/**
 * Type guard for token values.
 */
export function isValidTokenValue(value: unknown): value is TokenValue {
  return TokenValueSchema.safeParse(value).success;
}
