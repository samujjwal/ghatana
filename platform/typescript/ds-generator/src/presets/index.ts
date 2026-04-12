/**
 * @fileoverview Design system preset definitions and materialization.
 *
 * A preset is a named, opinionated combination of token values, component
 * defaults, and theme configuration that can be applied as a starting point
 * for a new design system variant.
 */

import { z } from 'zod';

// ============================================================================
// Preset Schema
// ============================================================================

export const PresetColorPaletteSchema = z.object({
  primary: z.string(),
  secondary: z.string(),
  neutral: z.string(),
  success: z.string(),
  warning: z.string(),
  error: z.string(),
  info: z.string(),
}).strict();

export type PresetColorPalette = z.infer<typeof PresetColorPaletteSchema>;

export const PresetTypographySchema = z.object({
  fontFamily: z.string(),
  baseFontSize: z.number().positive(),
  scaleRatio: z.number().min(1).max(2),
}).strict();

export type PresetTypography = z.infer<typeof PresetTypographySchema>;

export const DesignSystemPresetSchema = z.object({
  id: z.string().min(1),
  name: z.string().min(1),
  description: z.string().optional(),
  colors: PresetColorPaletteSchema,
  typography: PresetTypographySchema,
  borderRadius: z.enum(['none', 'subtle', 'rounded', 'pill']),
  density: z.enum(['compact', 'comfortable', 'spacious']),
  elevation: z.enum(['flat', 'subtle', 'raised']),
}).strict();

export type DesignSystemPreset = z.infer<typeof DesignSystemPresetSchema>;

// ============================================================================
// Built-in presets
// ============================================================================

export const PRESET_GHATANA_DEFAULT: DesignSystemPreset = {
  id: 'ghatana-default',
  name: 'Ghatana Default',
  description: 'The canonical Ghatana platform design system baseline.',
  colors: {
    primary: '#3b82f6',
    secondary: '#8b5cf6',
    neutral: '#6b7280',
    success: '#10b981',
    warning: '#f59e0b',
    error: '#ef4444',
    info: '#06b6d4',
  },
  typography: {
    fontFamily: 'Inter, system-ui, sans-serif',
    baseFontSize: 16,
    scaleRatio: 1.25,
  },
  borderRadius: 'rounded',
  density: 'comfortable',
  elevation: 'subtle',
} as const;

export const PRESET_ENTERPRISE_NEUTRAL: DesignSystemPreset = {
  id: 'enterprise-neutral',
  name: 'Enterprise Neutral',
  description: 'A conservative, neutral enterprise palette.',
  colors: {
    primary: '#1d4ed8',
    secondary: '#374151',
    neutral: '#9ca3af',
    success: '#059669',
    warning: '#d97706',
    error: '#dc2626',
    info: '#0284c7',
  },
  typography: {
    fontFamily: 'IBM Plex Sans, system-ui, sans-serif',
    baseFontSize: 14,
    scaleRatio: 1.2,
  },
  borderRadius: 'subtle',
  density: 'compact',
  elevation: 'flat',
} as const;

export const PRESET_CREATIVE_BOLD: DesignSystemPreset = {
  id: 'creative-bold',
  name: 'Creative Bold',
  description: 'High-contrast, bold palette for marketing and creative tools.',
  colors: {
    primary: '#7c3aed',
    secondary: '#ec4899',
    neutral: '#1f2937',
    success: '#16a34a',
    warning: '#ea580c',
    error: '#dc2626',
    info: '#0ea5e9',
  },
  typography: {
    fontFamily: 'Poppins, system-ui, sans-serif',
    baseFontSize: 16,
    scaleRatio: 1.333,
  },
  borderRadius: 'pill',
  density: 'spacious',
  elevation: 'raised',
} as const;

export const ALL_PRESETS: readonly DesignSystemPreset[] = [
  PRESET_GHATANA_DEFAULT,
  PRESET_ENTERPRISE_NEUTRAL,
  PRESET_CREATIVE_BOLD,
] as const;

/** Find a built-in preset by ID. */
export function findPreset(id: string): DesignSystemPreset | undefined {
  return ALL_PRESETS.find((p) => p.id === id);
}

// ============================================================================
// Materialization
// ============================================================================

/** Token value map produced by materializing a preset. */
export interface MaterializedTokens {
  readonly colors: Record<string, string>;
  readonly fontFamily: string;
  readonly fontSizes: Record<string, number>;
  readonly borderRadius: Record<string, string>;
  readonly spacing: Record<string, number>;
}

const BORDER_RADIUS_MAP: Record<DesignSystemPreset['borderRadius'], Record<string, string>> = {
  none: { sm: '0', md: '0', lg: '0', full: '0' },
  subtle: { sm: '2px', md: '4px', lg: '6px', full: '9999px' },
  rounded: { sm: '4px', md: '8px', lg: '12px', full: '9999px' },
  pill: { sm: '8px', md: '16px', lg: '24px', full: '9999px' },
};

const DENSITY_SPACING_MAP: Record<DesignSystemPreset['density'], Record<string, number>> = {
  compact: { xs: 2, sm: 4, md: 8, lg: 12, xl: 16 },
  comfortable: { xs: 4, sm: 8, md: 16, lg: 24, xl: 32 },
  spacious: { xs: 8, sm: 16, md: 24, lg: 40, xl: 56 },
};

/**
 * Materialise a preset into concrete token values.
 * The output can be used to generate CSS custom properties or JSON token files.
 */
export function materializePreset(preset: DesignSystemPreset): MaterializedTokens {
  const base = preset.typography.baseFontSize;
  const ratio = preset.typography.scaleRatio;

  const fontSizes: Record<string, number> = {
    xs: Math.round(base / (ratio * ratio) * 10) / 10,
    sm: Math.round(base / ratio * 10) / 10,
    md: base,
    lg: Math.round(base * ratio * 10) / 10,
    xl: Math.round(base * ratio * ratio * 10) / 10,
    '2xl': Math.round(base * ratio * ratio * ratio * 10) / 10,
  };

  return {
    colors: { ...preset.colors },
    fontFamily: preset.typography.fontFamily,
    fontSizes,
    borderRadius: BORDER_RADIUS_MAP[preset.borderRadius],
    spacing: DENSITY_SPACING_MAP[preset.density],
  };
}

/**
 * Render materialised tokens as CSS custom properties.
 */
export function renderPresetToCss(preset: DesignSystemPreset): string {
  const tokens = materializePreset(preset);
  const lines: string[] = [`:root {`];

  for (const [key, value] of Object.entries(tokens.colors)) {
    lines.push(`  --color-${key}: ${value};`);
  }
  lines.push(`  --font-family-base: ${tokens.fontFamily};`);

  for (const [key, value] of Object.entries(tokens.fontSizes)) {
    lines.push(`  --font-size-${key}: ${value}px;`);
  }
  for (const [key, value] of Object.entries(tokens.borderRadius)) {
    lines.push(`  --radius-${key}: ${value};`);
  }
  for (const [key, value] of Object.entries(tokens.spacing)) {
    lines.push(`  --spacing-${key}: ${value}px;`);
  }

  lines.push(`}`);
  return lines.join('\n');
}
