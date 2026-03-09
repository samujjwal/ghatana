/**
 * Design Token Schema
 *
 * Comprehensive, extensible token schema following W3C Design Token Community Group specifications.
 * Supports multiple token types with strict validation and type safety.
 *
 * Core Principles:
 * - Composability: Tokens can reference other tokens
 * - Extensibility: Easy to add custom token types
 * - Type Safety: Full TypeScript inference from Zod schemas
 * - Validation: Strict runtime validation with clear error messages
 */

import { z } from 'zod';

/**
 * Base token schema
 * All tokens must include these fields
 */
export const baseTokenSchema = z.object({
  $type: z.string(),
  $value: z.any(),
  $description: z.string().optional(),
  $extensions: z.record(z.string(), z.any()).optional(),
});

/**
 * Color token schema
 * Supports: hex, rgb, rgba, hsl, hsla, named colors, and token references
 */
export const colorTokenSchema = baseTokenSchema.extend({
  $type: z.literal('color'),
  $value: z.union([
    z.string().regex(/^#([A-Fa-f0-9]{6}|[A-Fa-f0-9]{3})$/, 'Invalid hex color'),
    z.string().regex(/^rgb\(\d+,\s*\d+,\s*\d+\)$/, 'Invalid RGB color'),
    z.string().regex(/^rgba\(\d+,\s*\d+,\s*\d+,\s*[\d.]+\)$/, 'Invalid RGBA color'),
    z.string().regex(/^hsl\(\d+,\s*\d+%,\s*\d+%\)$/, 'Invalid HSL color'),
    z.string().regex(/^hsla\(\d+,\s*\d+%,\s*\d+%,\s*[\d.]+\)$/, 'Invalid HSLA color'),
    z.string().startsWith('{').endsWith('}'), // Token reference
  ]),
});

/**
 * Dimension token schema
 * For sizes, spacing, borders, etc.
 */
export const dimensionTokenSchema = baseTokenSchema.extend({
  $type: z.literal('dimension'),
  $value: z.union([
    z.string().regex(/^\d+(\.\d+)?(px|rem|em|%|vh|vw|vmin|vmax)$/, 'Invalid dimension value'),
    z.number(),
    z.string().startsWith('{').endsWith('}'), // Token reference
  ]),
});

/**
 * Font family token schema
 */
export const fontFamilyTokenSchema = baseTokenSchema.extend({
  $type: z.literal('fontFamily'),
  $value: z.union([
    z.string(),
    z.array(z.string()),
    z.string().startsWith('{').endsWith('}'),
  ]),
});

/**
 * Font weight token schema
 */
export const fontWeightTokenSchema = baseTokenSchema.extend({
  $type: z.literal('fontWeight'),
  $value: z.union([
    z.enum(['100', '200', '300', '400', '500', '600', '700', '800', '900']),
    z.enum(['thin', 'extralight', 'light', 'normal', 'medium', 'semibold', 'bold', 'extrabold', 'black']),
    z.number().min(100).max(900),
    z.string().startsWith('{').endsWith('}'),
  ]),
});

/**
 * Font size token schema
 */
export const fontSizeTokenSchema = baseTokenSchema.extend({
  $type: z.literal('fontSize'),
  $value: z.union([
    z.string().regex(/^\d+(\.\d+)?(px|rem|em|%)$/, 'Invalid font size'),
    z.number(),
    z.string().startsWith('{').endsWith('}'),
  ]),
});

/**
 * Line height token schema
 */
export const lineHeightTokenSchema = baseTokenSchema.extend({
  $type: z.literal('lineHeight'),
  $value: z.union([
    z.string().regex(/^\d+(\.\d+)?(px|rem|em|%)?$/, 'Invalid line height'),
    z.number(),
    z.string().startsWith('{').endsWith('}'),
  ]),
});

/**
 * Letter spacing token schema
 */
export const letterSpacingTokenSchema = baseTokenSchema.extend({
  $type: z.literal('letterSpacing'),
  $value: z.union([
    z.string().regex(/^-?\d+(\.\d+)?(px|rem|em)$/, 'Invalid letter spacing'),
    z.number(),
    z.string().startsWith('{').endsWith('}'),
  ]),
});

/**
 * Border radius token schema
 */
export const borderRadiusTokenSchema = baseTokenSchema.extend({
  $type: z.literal('borderRadius'),
  $value: z.union([
    z.string().regex(/^\d+(\.\d+)?(px|rem|em|%)$/, 'Invalid border radius'),
    z.number(),
    z.string().startsWith('{').endsWith('}'),
  ]),
});

/**
 * Border width token schema
 */
export const borderWidthTokenSchema = baseTokenSchema.extend({
  $type: z.literal('borderWidth'),
  $value: z.union([
    z.string().regex(/^\d+(\.\d+)?(px|rem|em)$/, 'Invalid border width'),
    z.number(),
    z.string().startsWith('{').endsWith('}'),
  ]),
});

/**
 * Shadow token schema
 */
export const shadowTokenSchema = baseTokenSchema.extend({
  $type: z.literal('shadow'),
  $value: z.union([
    z.object({
      offsetX: z.string(),
      offsetY: z.string(),
      blur: z.string(),
      spread: z.string().optional(),
      color: z.string(),
      inset: z.boolean().optional(),
    }),
    z.string(), // CSS box-shadow string
    z.string().startsWith('{').endsWith('}'),
  ]),
});

/**
 * Duration token schema (for transitions/animations)
 */
export const durationTokenSchema = baseTokenSchema.extend({
  $type: z.literal('duration'),
  $value: z.union([
    z.string().regex(/^\d+(\.\d+)?(ms|s)$/, 'Invalid duration'),
    z.number(),
    z.string().startsWith('{').endsWith('}'),
  ]),
});

/**
 * Cubic bezier token schema (for easing functions)
 */
export const cubicBezierTokenSchema = baseTokenSchema.extend({
  $type: z.literal('cubicBezier'),
  $value: z.union([
    z.tuple([z.number(), z.number(), z.number(), z.number()]),
    z.string(), // CSS timing function string
    z.string().startsWith('{').endsWith('}'),
  ]),
});

/**
 * Z-index token schema
 */
export const zIndexTokenSchema = baseTokenSchema.extend({
  $type: z.literal('zIndex'),
  $value: z.union([
    z.number(),
    z.string().startsWith('{').endsWith('}'),
  ]),
});

/**
 * Opacity token schema
 */
export const opacityTokenSchema = baseTokenSchema.extend({
  $type: z.literal('opacity'),
  $value: z.union([
    z.number().min(0).max(1),
    z.string().regex(/^0(\.\d+)?$|^1(\.0+)?$/, 'Opacity must be between 0 and 1'),
    z.string().startsWith('{').endsWith('}'),
  ]),
});

/**
 * Typography composite token schema
 */
export const typographyTokenSchema = baseTokenSchema.extend({
  $type: z.literal('typography'),
  $value: z.object({
    fontFamily: z.string(),
    fontSize: z.string(),
    fontWeight: z.string(),
    lineHeight: z.string(),
    letterSpacing: z.string().optional(),
  }),
});

/**
 * Union of all token schemas
 */
export const tokenSchema = z.discriminatedUnion('$type', [
  colorTokenSchema,
  dimensionTokenSchema,
  fontFamilyTokenSchema,
  fontWeightTokenSchema,
  fontSizeTokenSchema,
  lineHeightTokenSchema,
  letterSpacingTokenSchema,
  borderRadiusTokenSchema,
  borderWidthTokenSchema,
  shadowTokenSchema,
  durationTokenSchema,
  cubicBezierTokenSchema,
  zIndexTokenSchema,
  opacityTokenSchema,
  typographyTokenSchema,
]);

/**
 * Token collection type definition
 * Represents a group of related tokens (must be defined before schema)
 */
export interface TokenCollection {
  [key: string]: Token | TokenCollection;
}

/**
 * Token collection schema
 * Represents a group of related tokens
 */
export const tokenCollectionSchema: z.ZodType<TokenCollection> = z.record(
  z.string(),
  z.lazy(() => z.union([tokenSchema, tokenCollectionSchema]))
);

/**
 * Root token file schema
 */
export const tokenFileSchema = tokenCollectionSchema;

/**
 * Type exports for TypeScript inference
 */
export type BaseToken = z.infer<typeof baseTokenSchema>;
/**
 *
 */
export type ColorToken = z.infer<typeof colorTokenSchema>;
/**
 *
 */
export type DimensionToken = z.infer<typeof dimensionTokenSchema>;
/**
 *
 */
export type FontFamilyToken = z.infer<typeof fontFamilyTokenSchema>;
/**
 *
 */
export type FontWeightToken = z.infer<typeof fontWeightTokenSchema>;
/**
 *
 */
export type FontSizeToken = z.infer<typeof fontSizeTokenSchema>;
/**
 *
 */
export type LineHeightToken = z.infer<typeof lineHeightTokenSchema>;
/**
 *
 */
export type LetterSpacingToken = z.infer<typeof letterSpacingTokenSchema>;
/**
 *
 */
export type BorderRadiusToken = z.infer<typeof borderRadiusTokenSchema>;
/**
 *
 */
export type BorderWidthToken = z.infer<typeof borderWidthTokenSchema>;
/**
 *
 */
export type ShadowToken = z.infer<typeof shadowTokenSchema>;
/**
 *
 */
export type DurationToken = z.infer<typeof durationTokenSchema>;
/**
 *
 */
export type CubicBezierToken = z.infer<typeof cubicBezierTokenSchema>;
/**
 *
 */
export type ZIndexToken = z.infer<typeof zIndexTokenSchema>;
/**
 *
 */
export type OpacityToken = z.infer<typeof opacityTokenSchema>;
/**
 *
 */
export type TypographyToken = z.infer<typeof typographyTokenSchema>;

/**
 *
 */
export type Token = z.infer<typeof tokenSchema>;
// TokenCollection already defined as interface above
/**
 *
 */
export type TokenFile = z.infer<typeof tokenFileSchema>;

/**
 * Token type enum for convenience
 */
export const TokenType = {
  COLOR: 'color',
  DIMENSION: 'dimension',
  FONT_FAMILY: 'fontFamily',
  FONT_WEIGHT: 'fontWeight',
  FONT_SIZE: 'fontSize',
  LINE_HEIGHT: 'lineHeight',
  LETTER_SPACING: 'letterSpacing',
  BORDER_RADIUS: 'borderRadius',
  BORDER_WIDTH: 'borderWidth',
  SHADOW: 'shadow',
  DURATION: 'duration',
  CUBIC_BEZIER: 'cubicBezier',
  Z_INDEX: 'zIndex',
  OPACITY: 'opacity',
  TYPOGRAPHY: 'typography',
} as const;

/**
 *
 */
export type TokenTypeValue = typeof TokenType[keyof typeof TokenType];
