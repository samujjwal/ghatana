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
 */
export function applyBrand(
  preset: DesignSystemPreset,
  brand: BrandConfig,
): BrandedTokens {
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
    customProperties: brand.customProperties ?? {},
  };
}

/**
 * Render a BrandedTokens map to CSS custom properties.
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
