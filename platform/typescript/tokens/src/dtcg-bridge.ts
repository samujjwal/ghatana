/**
 * @fileoverview DTCG schema bridge for @ghatana/tokens.
 *
 * Connects the canonical Ghatana token registry to the DTCG-aligned schemas
 * defined in @ghatana/ds-schema. Provides Zod-backed validation that can be
 * used at build time, in CLI tooling, and in tests.
 *
 * This module is explicitly NOT imported in the main bundle entry to avoid
 * SSR payload bloat — import from `@ghatana/tokens/validation` in scripts only.
 */

import { z } from 'zod';

// ============================================================================
// Colour palette schema
// ============================================================================

const ColorScaleSchema = z.object({
  50: z.string(),
  100: z.string(),
  200: z.string(),
  300: z.string(),
  400: z.string(),
  500: z.string(),
  600: z.string(),
  700: z.string(),
  800: z.string(),
  900: z.string(),
}).passthrough();

const PaletteSchema = z.object({
  primary: ColorScaleSchema,
  secondary: ColorScaleSchema,
  neutral: ColorScaleSchema,
  success: ColorScaleSchema,
  warning: ColorScaleSchema,
  error: ColorScaleSchema,
  info: ColorScaleSchema,
}).passthrough();

const SemanticColorsSchema = z.record(z.string(), z.string()).or(z.record(z.string(), z.unknown()));

const ColorsSchema = z.object({
  palette: PaletteSchema,
  light: SemanticColorsSchema,
  dark: SemanticColorsSchema,
  white: z.string(),
  black: z.string(),
}).passthrough();

// ============================================================================
// Spacing schema
// ============================================================================

const SpacingScaleSchema = z.record(z.string().or(z.number()), z.number().or(z.string()));

const SpacingSchema = z.object({
  scale: SpacingScaleSchema,
  semantic: z.record(z.string(), z.number().or(z.string())),
  density: z.record(z.string(), z.record(z.string(), z.number().or(z.string()))),
}).passthrough();

// ============================================================================
// Typography schema
// ============================================================================

const TypographySchema = z.object({
  fontFamily: z.record(z.string(), z.string().or(z.array(z.string()))),
  fontSize: z.record(z.string(), z.number().or(z.string())),
  fontWeight: z.record(z.string(), z.number()),
  lineHeight: z.record(z.string(), z.number().or(z.string())),
  letterSpacing: z.record(z.string(), z.number().or(z.string())),
  typography: z.record(z.string(), z.unknown()),
}).passthrough();

// ============================================================================
// Shadows schema
// ============================================================================

const ShadowsSchema = z.object({
  light: z.union([z.record(z.string(), z.string()), z.array(z.string())]),
  dark: z.union([z.record(z.string(), z.string()), z.array(z.string())]),
  elevationLevels: z.array(z.number().or(z.string())).or(z.record(z.string(), z.unknown())),
}).passthrough();

// ============================================================================
// Transitions schema
// ============================================================================

const TransitionsSchema = z.object({
  transitions: z.record(z.string(), z.string().or(z.unknown())),
  animations: z.record(z.string(), z.string().or(z.unknown())),
  reducedMotion: z.record(z.string(), z.string().or(z.unknown())),
  durations: z.record(z.string(), z.number().or(z.string())),
  easings: z.record(z.string(), z.string().or(z.array(z.number()))),
  properties: z.record(z.string(), z.string().or(z.array(z.string()))),
}).passthrough();

// ============================================================================
// Top-level token registry schema (matches TokenRegistry from registry.ts)
// ============================================================================

export const tokenRegistrySchema = z.object({
  colors: ColorsSchema,
  spacing: SpacingSchema,
  typography: TypographySchema,
  shadows: ShadowsSchema,
  borderRadius: z.record(z.string(), z.string().or(z.number())),
  borderWidth: z.record(z.string().or(z.number()), z.string().or(z.number())),
  componentRadius: z.record(z.string(), z.string().or(z.number())),
  shapeVariants: z.record(z.string(), z.unknown()),
  transitions: TransitionsSchema,
  zIndex: z.record(z.string(), z.number()),
  semanticZIndex: z.record(z.string(), z.number()),
  componentZIndex: z.record(z.string(), z.number()),
  breakpoints: z.object({
    breakpoints: z.record(z.string(), z.number().or(z.string())),
    semanticBreakpoints: z.record(z.string(), z.number().or(z.string())),
    mediaQueries: z.record(z.string(), z.string().or(z.function())),
    containerMaxWidths: z.record(z.string(), z.number().or(z.string())),
    touchTargets: z.record(z.string(), z.number().or(z.string())),
  }).passthrough(),
}).passthrough();

export type TokenRegistrySchema = z.infer<typeof tokenRegistrySchema>;

function isSerializableTokenValue(value: unknown): value is string | number | boolean | readonly unknown[] | Record<string, unknown> {
  return (
    typeof value === 'string' ||
    typeof value === 'number' ||
    typeof value === 'boolean' ||
    Array.isArray(value) ||
    (typeof value === 'object' && value !== null)
  );
}

function toDTCGGroup(input: Record<string, unknown>): Record<string, unknown> {
  const group: Record<string, unknown> = {};

  for (const [key, value] of Object.entries(input)) {
    if (typeof value === 'function' || value === undefined || value === null) {
      continue;
    }

    if (typeof value === 'object' && !Array.isArray(value)) {
      group[key] = toDTCGGroup(value as Record<string, unknown>);
      continue;
    }

    if (isSerializableTokenValue(value)) {
      group[key] = { $value: value };
    }
  }

  return group;
}

export function toDTCGTokenFile(
  registry: Record<string, unknown>,
): Record<string, unknown> {
  return {
    $schema: 'https://design-tokens.org/schema',
    $version: '1.0.0',
    ...toDTCGGroup(registry),
  };
}

// ============================================================================
// DTCG-aware validation result
// ============================================================================

export interface DTCGValidationResult {
  readonly success: boolean;
  readonly errors?: readonly string[];
  readonly warnings?: readonly string[];
}

/**
 * Validates the Ghatana token registry against the DTCG-aligned Zod schema.
 * Produces structured, path-annotated error messages.
 */
export function validateTokenRegistryDTCG(
  registry: Record<string, unknown>,
): DTCGValidationResult {
  const result = tokenRegistrySchema.safeParse(registry);
  if (result.success) {
    return { success: true };
  }

  const errors = result.error.issues.map((issue: z.ZodIssue) => {
    const path = issue.path.join('.');
    return path ? `${path}: ${issue.message}` : issue.message;
  });

  return { success: false, errors };
}

/**
 * Assert the token registry conforms to the DTCG schema. Throws with detail.
 */
export function assertTokenRegistryDTCG(registry: Record<string, unknown>): void {
  const result = validateTokenRegistryDTCG(registry);
  if (!result.success) {
    throw new Error(
      `DTCG token registry validation failed:\n${result.errors?.join('\n') ?? 'Unknown error'}`,
    );
  }
}
