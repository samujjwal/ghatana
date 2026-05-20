/**
 * @fileoverview Brand customization — apply brand overrides on top of a preset.
 *
 * Products provide a BrandConfig that selectively overrides preset tokens.
 * The result is a fully-resolved token map ready for CSS or JSON emission.
 */

import { z } from 'zod';
import type { DesignSystemPreset, MaterializedTokens } from '../presets/index.js';
import { materializePreset } from '../presets/index.js';

// ============================================================================
// Brand Config Schema
// ============================================================================

export const BrandConfigSchema = z.object({
  /** Brand name for documentation/provenance. */
  name: z.string().min(1),
  /** Base preset ID to derive from. */
  basePresetId: z.string().min(1),
  /** Selective color overrides (replaces individual semantic colors). */
  colors: z.record(z.string(), z.string()).optional(),
  /** Font family override. */
  fontFamily: z.string().optional(),
  /** Border radius override. */
  borderRadius: z.enum(['none', 'subtle', 'rounded', 'pill']).optional(),
  /** Density override. */
  density: z.enum(['compact', 'comfortable', 'spacious']).optional(),
  /** Additional CSS custom properties to inject verbatim. */
  customProperties: z.record(z.string(), z.string()).optional(),
}).strict();

export type BrandConfig = z.infer<typeof BrandConfigSchema>;

// ============================================================================
// Validation
// ============================================================================

/** Validation error details. */
export interface ValidationError {
  readonly path: string;
  readonly message: string;
}

/** Result of brand validation. */
export interface ValidationResult {
  readonly isValid: boolean;
  readonly errors: readonly ValidationError[];
}

/** Safe CSS custom property name pattern (CSS custom properties must start with -- and contain alphanumeric, hyphens, underscores). */
const CSS_CUSTOM_PROPERTY_PATTERN = /^--[a-zA-Z][a-zA-Z0-9-_]*$/;

/** Safe CSS value pattern (rejects potentially dangerous values like javascript:, data:, url(), etc.). */
const UNSAFE_CSS_VALUE_PATTERNS = [
  /javascript:/i,
  /data:/i,
  /vbscript:/i,
  /expression\(/i,
  /url\(javascript:/i,
  /@import/i,
  /<script/i,
];

/**
 * Validate a CSS custom property name for safety.
 * @param name - The property name to validate
 * @returns True if the name is safe
 */
export function isValidCssPropertyName(name: string): boolean {
  return CSS_CUSTOM_PROPERTY_PATTERN.test(name);
}

/**
 * Validate a CSS value for safety.
 * @param value - The CSS value to validate
 * @returns True if the value is safe
 */
export function isValidCssValue(value: string): boolean {
  return !UNSAFE_CSS_VALUE_PATTERNS.some((pattern) => pattern.test(value));
}

/**
 * Validate brand configuration for safety and correctness.
 * @param brand - The brand configuration to validate
 * @param preset - The preset that the brand references
 * @returns Validation result with any errors
 */
export function validateBrand(
  brand: BrandConfig,
  preset: DesignSystemPreset,
): ValidationResult {
  const errors: ValidationError[] = [];

  // Validate basePresetId matches the preset
  if (brand.basePresetId !== preset.id) {
    errors.push({
      path: 'basePresetId',
      message: `basePresetId '${brand.basePresetId}' does not match preset.id '${preset.id}'`,
    });
  }

  // Validate custom property names and values
  if (brand.customProperties) {
    for (const [name, value] of Object.entries(brand.customProperties)) {
      if (!isValidCssPropertyName(name)) {
        errors.push({
          path: `customProperties.${name}`,
          message: `Invalid CSS custom property name: '${name}'. Must start with '--' and contain only alphanumeric characters, hyphens, and underscores.`,
        });
      }
      if (!isValidCssValue(value)) {
        errors.push({
          path: `customProperties.${name}`,
          message: `Potentially unsafe CSS value for '${name}': '${value}'. Values containing javascript:, data:, or other dangerous patterns are rejected.`,
        });
      }
    }
  }

  // Validate color overrides are valid hex colors
  if (brand.colors) {
    for (const [key, value] of Object.entries(brand.colors)) {
      if (!/^#[0-9a-fA-F]{3}$/.test(value) && !/^#[0-9a-fA-F]{6}$/.test(value)) {
        errors.push({
          path: `colors.${key}`,
          message: `Invalid hex color value: '${value}'. Must be a valid 3-digit or 6-digit hex color.`,
        });
      }
    }
  }

  return {
    isValid: errors.length === 0,
    errors,
  };
}

// ============================================================================
// Brand Application
// ============================================================================

/** Result of applying a brand to a preset. */
export interface BrandedTokens extends MaterializedTokens {
  readonly brandName: string;
  readonly basePresetId: string;
  readonly customProperties: Record<string, string>;
}

/**
 * Apply brand overrides on top of a materialised preset.
 * Validates the brand configuration before applying.
 * @throws Error if validation fails
 */
export function applyBrand(
  preset: DesignSystemPreset,
  brand: BrandConfig,
): BrandedTokens {
  // Validate brand configuration
  const validation = validateBrand(brand, preset);
  if (!validation.isValid) {
    const errorMessages = validation.errors.map((e) => `${e.path}: ${e.message}`).join('; ');
    throw new Error(`Brand validation failed: ${errorMessages}`);
  }

  const base = materializePreset(preset);

  const colors = { ...base.colors, ...(brand.colors ?? {}) };
  const fontFamily = brand.fontFamily ?? base.fontFamily;

  // Re-compute border radius if overridden
  const RADIUS_MAP: Record<string, Record<string, string>> = {
    none: { sm: '0', md: '0', lg: '0', full: '0' },
    subtle: { sm: '2px', md: '4px', lg: '6px', full: '9999px' },
    rounded: { sm: '4px', md: '8px', lg: '12px', full: '9999px' },
    pill: { sm: '8px', md: '16px', lg: '24px', full: '9999px' },
  };
  const borderRadius = brand.borderRadius
    ? RADIUS_MAP[brand.borderRadius] ?? base.borderRadius
    : base.borderRadius;

  const DENSITY_MAP: Record<string, Record<string, number>> = {
    compact: { xs: 2, sm: 4, md: 8, lg: 12, xl: 16 },
    comfortable: { xs: 4, sm: 8, md: 16, lg: 24, xl: 32 },
    spacious: { xs: 8, sm: 16, md: 24, lg: 40, xl: 56 },
  };
  const spacing = brand.density
    ? DENSITY_MAP[brand.density] ?? base.spacing
    : base.spacing;

  return {
    brandName: brand.name,
    basePresetId: brand.basePresetId,
    colors,
    fontFamily,
    fontSizes: base.fontSizes,
    borderRadius,
    spacing,
    elevation: base.elevation,
    shadow: base.shadow,
    motion: base.motion,
    zIndex: base.zIndex,
    customProperties: brand.customProperties ?? {},
  };
}

/**
 * Render a BrandedTokens map to CSS custom properties.
 * Emits all token categories — colors, typography, border-radius, spacing,
 * elevation, shadow, motion, z-index, and any additional custom properties.
 */
export function renderBrandToCss(branded: BrandedTokens): string {
  const lines: string[] = [`:root {`];

  for (const [key, value] of Object.entries(branded.colors)) {
    lines.push(`  --color-${key}: ${value};`);
  }
  lines.push(`  --font-family-base: ${branded.fontFamily};`);
  for (const [key, value] of Object.entries(branded.fontSizes)) {
    lines.push(`  --font-size-${key}: ${value}px;`);
  }
  for (const [key, value] of Object.entries(branded.borderRadius)) {
    lines.push(`  --radius-${key}: ${value};`);
  }
  for (const [key, value] of Object.entries(branded.spacing)) {
    lines.push(`  --spacing-${key}: ${value}px;`);
  }
  for (const [key, value] of Object.entries(branded.elevation)) {
    lines.push(`  --elevation-${key}: ${value};`);
  }
  if (branded.shadow) {
    for (const [key, value] of Object.entries(branded.shadow)) {
      lines.push(`  --shadow-${key}: ${value};`);
    }
  }
  if (branded.motion) {
    for (const [key, value] of Object.entries(branded.motion)) {
      lines.push(`  --motion-${key}: ${value};`);
    }
  }
  if (branded.zIndex) {
    for (const [key, value] of Object.entries(branded.zIndex)) {
      lines.push(`  --z-index-${key}: ${value};`);
    }
  }
  for (const [key, value] of Object.entries(branded.customProperties)) {
    lines.push(`  ${key}: ${value};`);
  }

  lines.push(`}`);
  return lines.join('\n');
}

/**
 * Validate a BrandConfig object. Returns parsed config or throws.
 */
export function parseBrandConfig(input: unknown): BrandConfig {
  return BrandConfigSchema.parse(input);
}
