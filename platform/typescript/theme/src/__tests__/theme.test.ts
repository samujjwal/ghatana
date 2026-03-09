import { describe, it, expect } from 'vitest';
import { createTheme, applyThemeLayers, resolveThemeColors, themeToCSSVariables, baseThemeTokens } from '../theme';
import type { ThemeLayer } from '../theme';

describe('createTheme', () => {
  it('should create a light theme by default', () => {
    const theme = createTheme();
    expect(theme.mode).toBe('light');
    expect(theme.computed.mode).toBe('light');
    expect(theme.layers).toEqual([]);
  });

  it('should create a dark theme', () => {
    const theme = createTheme('dark');
    expect(theme.mode).toBe('dark');
    expect(theme.computed.mode).toBe('dark');
  });

  it('should apply layers to theme', () => {
    const layer: ThemeLayer = {
      id: 'test-brand',
      name: 'Test Brand',
      type: 'brand',
    };
    const theme = createTheme('light', [layer]);
    expect(theme.layers).toHaveLength(1);
    expect(theme.layers[0].id).toBe('test-brand');
  });

  it('should not mutate base tokens', () => {
    const spacingBefore = { ...baseThemeTokens.spacing };
    createTheme('dark', [{
      id: 'override',
      name: 'Override',
      type: 'app',
      overrides: { spacing: { xs: '999px' } } as any,
    }]);
    expect(baseThemeTokens.spacing).toEqual(spacingBefore);
  });
});

describe('applyThemeLayers', () => {
  it('should return base tokens when no layers have overrides', () => {
    const tokens = applyThemeLayers([]);
    expect(tokens.spacing).toBeDefined();
    expect(tokens.palette).toBeDefined();
  });

  it('should apply layer overrides in order', () => {
    const layers: ThemeLayer[] = [
      { id: 'app', name: 'App', type: 'app', overrides: { spacing: { xs: '2px' } } as any },
      { id: 'brand', name: 'Brand', type: 'brand', overrides: { spacing: { xs: '4px' } } as any },
    ];
    // Brand (order 1) should be applied before App (order 3)
    const tokens = applyThemeLayers(layers);
    // App layer (order=3) overrides brand (order=1), so xs should be '2px'
    expect(tokens.spacing.xs).toBe('2px');
  });
});

describe('resolveThemeColors', () => {
  it('should resolve light colors for light mode', () => {
    const tokens = applyThemeLayers([]);
    const colors = resolveThemeColors(tokens, 'light');
    expect(colors.background).toBe(tokens.lightColors.background);
    expect(colors.text).toBe(tokens.lightColors.text);
  });

  it('should resolve dark colors for dark mode', () => {
    const tokens = applyThemeLayers([]);
    const colors = resolveThemeColors(tokens, 'dark');
    expect(colors.background).toBe(tokens.darkColors.background);
    expect(colors.text).toBe(tokens.darkColors.text);
  });

  it('should always include palette', () => {
    const tokens = applyThemeLayers([]);
    const colors = resolveThemeColors(tokens, 'light');
    expect(colors.palette).toBeDefined();
  });
});

describe('themeToCSSVariables', () => {
  it('should generate CSS custom properties', () => {
    const theme = createTheme('light');
    const vars = themeToCSSVariables(theme);
    // Should have --gh- prefixed variables
    const keys = Object.keys(vars);
    expect(keys.length).toBeGreaterThan(0);
    keys.forEach(key => {
      expect(key).toMatch(/^--gh-/);
    });
  });

  it('should produce different vars for light vs dark', () => {
    const lightVars = themeToCSSVariables(createTheme('light'));
    const darkVars = themeToCSSVariables(createTheme('dark'));
    // At least some values should differ between light and dark
    const lightValues = Object.values(lightVars).join(',');
    const darkValues = Object.values(darkVars).join(',');
    expect(lightValues).not.toBe(darkValues);
  });
});
