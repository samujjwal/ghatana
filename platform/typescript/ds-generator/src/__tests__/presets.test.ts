import { describe, it, expect } from 'vitest';
import {
  materializePreset,
  renderPresetToCss,
  findPreset,
  PRESET_GHATANA_DEFAULT,
  PRESET_ENTERPRISE_NEUTRAL,
  ALL_PRESETS,
  DesignSystemPresetSchema,
} from '../presets/index.js';
import { applyBrand, renderBrandToCss } from '../brand/index.js';

describe('DesignSystemPresetSchema', () => {
  it('validates the built-in default preset', () => {
    expect(() => DesignSystemPresetSchema.parse(PRESET_GHATANA_DEFAULT)).not.toThrow();
  });

  it('validates all built-in presets', () => {
    for (const preset of ALL_PRESETS) {
      expect(() => DesignSystemPresetSchema.parse(preset)).not.toThrow();
    }
  });

  it('rejects preset with missing required fields', () => {
    const invalid = { id: 'bad', name: 'Bad' };
    expect(() => DesignSystemPresetSchema.parse(invalid)).toThrow();
  });
});

describe('findPreset', () => {
  it('finds ghatana-default by id', () => {
    const p = findPreset('ghatana-default');
    expect(p?.id).toBe('ghatana-default');
  });

  it('returns undefined for unknown id', () => {
    expect(findPreset('does-not-exist')).toBeUndefined();
  });
});

describe('materializePreset', () => {
  it('produces all required token categories', () => {
    const tokens = materializePreset(PRESET_GHATANA_DEFAULT);
    expect(tokens.colors).toBeDefined();
    expect(tokens.fontFamily).toBeDefined();
    expect(tokens.fontSizes).toBeDefined();
    expect(tokens.borderRadius).toBeDefined();
    expect(tokens.spacing).toBeDefined();
  });

  it('includes md font size at base font size', () => {
    const tokens = materializePreset(PRESET_GHATANA_DEFAULT);
    expect(tokens.fontSizes['md']).toBe(PRESET_GHATANA_DEFAULT.typography.baseFontSize);
  });

  it('produces larger sizes with scale ratio', () => {
    const tokens = materializePreset(PRESET_GHATANA_DEFAULT);
    const { md, lg, xl } = tokens.fontSizes as Record<string, number>;
    expect(lg).toBeGreaterThan(md);
    expect(xl).toBeGreaterThan(lg);
  });

  it('produces compact spacing for enterprise preset', () => {
    const tokens = materializePreset(PRESET_ENTERPRISE_NEUTRAL);
    expect(tokens.spacing['md']).toBeLessThan(
      materializePreset(PRESET_GHATANA_DEFAULT).spacing['md'],
    );
  });
});

describe('renderPresetToCss', () => {
  it('produces a :root block', () => {
    const css = renderPresetToCss(PRESET_GHATANA_DEFAULT);
    expect(css).toContain(':root {');
    expect(css).toContain('}');
  });

  it('includes --color-primary', () => {
    const css = renderPresetToCss(PRESET_GHATANA_DEFAULT);
    expect(css).toContain('--color-primary:');
  });

  it('includes --font-family-base', () => {
    const css = renderPresetToCss(PRESET_GHATANA_DEFAULT);
    expect(css).toContain('--font-family-base:');
  });

  it('includes spacing variables', () => {
    const css = renderPresetToCss(PRESET_GHATANA_DEFAULT);
    expect(css).toContain('--spacing-md:');
  });
});

describe('applyBrand', () => {
  it('overrides primary color from brand config', () => {
    const branded = applyBrand(PRESET_GHATANA_DEFAULT, {
      name: 'ACME',
      basePresetId: PRESET_GHATANA_DEFAULT.id,
      colors: { primary: '#ff0000' },
    });
    expect(branded.colors['primary']).toBe('#ff0000');
    expect(branded.brandName).toBe('ACME');
  });

  it('keeps non-overridden colors from preset', () => {
    const branded = applyBrand(PRESET_GHATANA_DEFAULT, {
      name: 'ACME',
      basePresetId: PRESET_GHATANA_DEFAULT.id,
      colors: { primary: '#ff0000' },
    });
    expect(branded.colors['success']).toBe(PRESET_GHATANA_DEFAULT.colors.success);
  });

  it('overrides fontFamily when provided', () => {
    const branded = applyBrand(PRESET_GHATANA_DEFAULT, {
      name: 'Brand',
      basePresetId: PRESET_GHATANA_DEFAULT.id,
      fontFamily: 'Arial, sans-serif',
    });
    expect(branded.fontFamily).toBe('Arial, sans-serif');
  });

  it('includes custom properties in branded tokens', () => {
    const branded = applyBrand(PRESET_GHATANA_DEFAULT, {
      name: 'Brand',
      basePresetId: PRESET_GHATANA_DEFAULT.id,
      customProperties: { '--brand-logo-width': '120px' },
    });
    expect(branded.customProperties['--brand-logo-width']).toBe('120px');
  });

  it('renders brand CSS with custom property', () => {
    const branded = applyBrand(PRESET_GHATANA_DEFAULT, {
      name: 'Brand',
      basePresetId: PRESET_GHATANA_DEFAULT.id,
      customProperties: { '--brand-accent': '#f0f' },
    });
    const css = renderBrandToCss(branded);
    expect(css).toContain('--brand-accent: #f0f');
  });
});
