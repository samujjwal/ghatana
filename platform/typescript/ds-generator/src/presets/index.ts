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
  shadow: z.enum(['none', 'light', 'medium', 'heavy']),
  motion: z.enum(['instant', 'fast', 'normal', 'slow']),
}).strict();

export type DesignSystemPreset = z.infer<typeof DesignSystemPresetSchema>;

// ============================================================================
// Materialized Tokens Schema
// ============================================================================

/** Zod schema for MaterializedTokens - token value map produced by materializing a preset. */
export const MaterializedTokensSchema = z.object({
  colors: z.record(z.string(), z.string()),
  fontFamily: z.string(),
  fontSizes: z.record(z.string(), z.number()),
  borderRadius: z.record(z.string(), z.string()),
  spacing: z.record(z.string(), z.number()),
  elevation: z.record(z.string(), z.string()),
  shadow: z.record(z.string(), z.string()),
  motion: z.record(z.string(), z.string()),
  zIndex: z.record(z.string(), z.number()),
});

export type MaterializedTokens = z.infer<typeof MaterializedTokensSchema>;

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
  shadow: 'medium',
  motion: 'normal',
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
  shadow: 'light',
  motion: 'fast',
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
  shadow: 'heavy',
  motion: 'slow',
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

const ELEVATION_MAP: Record<DesignSystemPreset['elevation'], Record<string, string>> = {
  flat: {
    xs: 'none',
    sm: 'none',
    md: 'none',
    lg: 'none',
    xl: 'none',
  },
  subtle: {
    xs: '0 1px 2px rgba(0, 0, 0, 0.05)',
    sm: '0 1px 3px rgba(0, 0, 0, 0.1)',
    md: '0 4px 6px rgba(0, 0, 0, 0.1)',
    lg: '0 10px 15px rgba(0, 0, 0, 0.1)',
    xl: '0 20px 25px rgba(0, 0, 0, 0.1)',
  },
  raised: {
    xs: '0 2px 4px rgba(0, 0, 0, 0.1)',
    sm: '0 4px 6px rgba(0, 0, 0, 0.15)',
    md: '0 10px 15px rgba(0, 0, 0, 0.15)',
    lg: '0 20px 25px rgba(0, 0, 0, 0.15)',
    xl: '0 25px 50px rgba(0, 0, 0, 0.25)',
  },
};

const SHADOW_MAP: Record<DesignSystemPreset['shadow'], Record<string, string>> = {
  none: {
    sm: 'none',
    md: 'none',
    lg: 'none',
    xl: 'none',
  },
  light: {
    sm: '0 1px 2px rgba(0, 0, 0, 0.05)',
    md: '0 2px 4px rgba(0, 0, 0, 0.05)',
    lg: '0 4px 8px rgba(0, 0, 0, 0.05)',
    xl: '0 8px 16px rgba(0, 0, 0, 0.05)',
  },
  medium: {
    sm: '0 1px 3px rgba(0, 0, 0, 0.12)',
    md: '0 4px 6px rgba(0, 0, 0, 0.12)',
    lg: '0 10px 15px rgba(0, 0, 0, 0.12)',
    xl: '0 20px 25px rgba(0, 0, 0, 0.12)',
  },
  heavy: {
    sm: '0 2px 4px rgba(0, 0, 0, 0.15)',
    md: '0 8px 12px rgba(0, 0, 0, 0.15)',
    lg: '0 16px 24px rgba(0, 0, 0, 0.15)',
    xl: '0 32px 48px rgba(0, 0, 0, 0.15)',
  },
};

const MOTION_MAP: Record<DesignSystemPreset['motion'], Record<string, string>> = {
  instant: {
    fast: '150ms',
    normal: '200ms',
    slow: '300ms',
  },
  fast: {
    fast: '100ms',
    normal: '150ms',
    slow: '200ms',
  },
  normal: {
    fast: '200ms',
    normal: '300ms',
    slow: '500ms',
  },
  slow: {
    fast: '300ms',
    normal: '500ms',
    slow: '700ms',
  },
};

const Z_INDEX_MAP = {
  base: {
    dropdown: 1000,
    sticky: 1020,
    fixed: 1030,
    modal: 1040,
    popover: 1050,
    tooltip: 1060,
  },
} as const;

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
    elevation: ELEVATION_MAP[preset.elevation],
    shadow: SHADOW_MAP[preset.shadow],
    motion: MOTION_MAP[preset.motion],
    zIndex: Z_INDEX_MAP['base'],
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
  for (const [key, value] of Object.entries(tokens.elevation)) {
    lines.push(`  --elevation-${key}: ${value};`);
  }
  for (const [key, value] of Object.entries(tokens.shadow)) {
    lines.push(`  --shadow-${key}: ${value};`);
  }
  for (const [key, value] of Object.entries(tokens.motion)) {
    lines.push(`  --motion-${key}: ${value};`);
  }
  for (const [key, value] of Object.entries(tokens.zIndex)) {
    lines.push(`  --z-index-${key}: ${value};`);
  }

  lines.push(`}`);
  return lines.join('\n');
}
