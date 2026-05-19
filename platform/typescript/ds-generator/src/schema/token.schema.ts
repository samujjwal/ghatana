/**
 * @fileoverview Design system token schema.
 *
 * Provides Zod schema for design system tokens including colors, typography,
 * spacing, shadows, borders, motion, z-index, and other design tokens.
 * Ensures token validation and serialization compatibility.
 *
 * @doc.type module
 * @doc.purpose Design system token schema validation
 * @doc.layer ds-generator
 */

import { z } from 'zod';

// ============================================================================
// Color Token Schema
// ============================================================================

export const ColorTokenSchema = z.object({
  type: z.literal('color'),
  name: z.string(),
  value: z.string().regex(/^#[0-9A-Fa-f]{6}$/),
  description: z.string().optional(),
  category: z.enum(['primary', 'secondary', 'accent', 'neutral', 'semantic', 'custom']),
  variants: z.record(z.string(), z.string()).optional(),
});

export type ColorToken = z.infer<typeof ColorTokenSchema>;

// ============================================================================
// Typography Token Schema
// ============================================================================

export const TypographyTokenSchema = z.object({
  type: z.literal('typography'),
  name: z.string(),
  fontFamily: z.string(),
  fontSize: z.string(),
  fontWeight: z.union([z.number(), z.string()]),
  lineHeight: z.union([z.number(), z.string()]),
  letterSpacing: z.string().optional(),
  textTransform: z.enum(['none', 'uppercase', 'lowercase', 'capitalize']).optional(),
  description: z.string().optional(),
});

export type TypographyToken = z.infer<typeof TypographyTokenSchema>;

// ============================================================================
// Spacing Token Schema
// ============================================================================

export const SpacingTokenSchema = z.object({
  type: z.literal('spacing'),
  name: z.string(),
  value: z.union([z.string(), z.number()]),
  description: z.string().optional(),
  scale: z.array(z.union([z.string(), z.number()])).optional(),
});

export type SpacingToken = z.infer<typeof SpacingTokenSchema>;

// ============================================================================
// Shadow/Elevation Token Schema
// ============================================================================

export const ShadowTokenSchema = z.object({
  type: z.literal('shadow'),
  name: z.string(),
  value: z.string(),
  elevation: z.number().min(0).max(24),
  description: z.string().optional(),
});

export type ShadowToken = z.infer<typeof ShadowTokenSchema>;

// ============================================================================
// Border Token Schema
// ============================================================================

export const BorderTokenSchema = z.object({
  type: z.literal('border'),
  name: z.string(),
  width: z.union([z.string(), z.number()]),
  style: z.enum(['solid', 'dashed', 'dotted', 'double', 'none']),
  color: z.string(),
  radius: z.union([z.string(), z.number()]).optional(),
  description: z.string().optional(),
});

export type BorderToken = z.infer<typeof BorderTokenSchema>;

// ============================================================================
// Motion Token Schema
// ============================================================================

export const MotionTokenSchema = z.object({
  type: z.literal('motion'),
  name: z.string(),
  duration: z.string(),
  easing: z.string(),
  delay: z.string().optional(),
  description: z.string().optional(),
});

export type MotionToken = z.infer<typeof MotionTokenSchema>;

// ============================================================================
// Z-Index Token Schema
// ============================================================================

export const ZIndexTokenSchema = z.object({
  type: z.literal('z-index'),
  name: z.string(),
  value: z.number().int(),
  description: z.string().optional(),
  layer: z.enum(['base', 'dropdown', 'sticky', 'fixed', 'modal', 'popover', 'tooltip']).optional(),
});

export type ZIndexToken = z.infer<typeof ZIndexTokenSchema>;

// ============================================================================
// Token Union Schema
// ============================================================================

export const DesignTokenSchema = z.discriminatedUnion('type', [
  ColorTokenSchema,
  TypographyTokenSchema,
  SpacingTokenSchema,
  ShadowTokenSchema,
  BorderTokenSchema,
  MotionTokenSchema,
  ZIndexTokenSchema,
]);

export type DesignToken = z.infer<typeof DesignTokenSchema>;

// ============================================================================
// Token Collection Schema
// ============================================================================

export const TokenCollectionSchema = z.object({
  schemaVersion: z.string().default('1.0.0'),
  name: z.string(),
  description: z.string().optional(),
  tokens: z.record(DesignTokenSchema),
  metadata: z.object({
    createdAt: z.string(),
    updatedAt: z.string(),
    author: z.string().optional(),
  }),
});

export type TokenCollection = z.infer<typeof TokenCollectionSchema>;

// ============================================================================
// Validation Functions
// ============================================================================

export interface ValidationResult<T> {
  valid: boolean;
  errors: string[];
  data?: T;
}

export function validateToken(token: unknown): ValidationResult<DesignToken> {
  const result = DesignTokenSchema.safeParse(token);
  
  if (!result.success) {
    return {
      valid: false,
      errors: result.error.errors.map(e => `${e.path.join('.')}: ${e.message}`),
    };
  }

  return {
    valid: true,
    errors: [],
    data: result.data,
  };
}

export function validateTokenCollection(collection: unknown): ValidationResult<TokenCollection> {
  const result = TokenCollectionSchema.safeParse(collection);
  
  if (!result.success) {
    return {
      valid: false,
      errors: result.error.errors.map(e => `${e.path.join('.')}: ${e.message}`),
    };
  }

  return {
    valid: true,
    errors: [],
    data: result.data,
  };
}

export function validateTokens(tokens: unknown): ValidationResult<DesignToken[]> {
  const arrayResult = z.array(DesignTokenSchema).safeParse(tokens);
  
  if (!arrayResult.success) {
    return {
      valid: false,
      errors: arrayResult.error.errors.map(e => `${e.path.join('.')}: ${e.message}`),
    };
  }

  return {
    valid: true,
    errors: [],
    data: arrayResult.data,
  };
}
