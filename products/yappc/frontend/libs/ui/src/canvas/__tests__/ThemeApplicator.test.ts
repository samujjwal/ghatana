/**
 * Tests for ThemeApplicator
 */

import { ThemeApplicator } from '../renderer/ThemeApplicator';

import type { ThemeContext, TokenRegistry } from '../renderer/ThemeApplicator';

describe.skip('ThemeApplicator', () => {
  // Test data
  const mockTokens: TokenRegistry = {
    base: {
      color: {
        primary: { 500: '#1976d2', 600: '#1565c0' },
        neutral: { 100: '#f5f5f5', 900: '#212121' },
      },
      spacing: {
        sm: 8,
        md: 16,
        lg: 24,
      },
      typography: {
        fontSize: { md: 16, lg: 20 },
      },
    },
    brand: {
      color: {
        primary: { 500: '#ff5722' },
      },
    },
    workspace: {
      color: {
        primary: { 500: '#4caf50' },
      },
    },
    app: {},
  };

  const mockContext: ThemeContext = {
    activeLayer: 'base',
    tokens: mockTokens,
    fallbackValues: {},
  };

  describe('applyTheme', () => {
    it('should apply tokens to props', () => {
      const props = { label: 'Click me', variant: 'primary' };
      const tokens = {
        color: '$color.primary.500',
        backgroundColor: '$color.neutral.100',
      };

      const result = ThemeApplicator.applyTheme(props, tokens, mockContext);

      expect(result.props.color).toBe('#1976d2');
      expect(result.props.backgroundColor).toBe('#f5f5f5');
      expect(result.unresolvedTokens).toHaveLength(0);
    });

    it('should handle props without tokens', () => {
      const props = { label: 'Click me', variant: 'primary' };

      const result = ThemeApplicator.applyTheme(props, undefined, mockContext);

      expect(result.props).toEqual(props);
      expect(result.styles).toEqual({});
      expect(result.unresolvedTokens).toHaveLength(0);
    });

    it('should collect unresolved tokens', () => {
      const props = { label: 'Click me' };
      const tokens = {
        color: '$color.nonexistent.500',
      };

      const result = ThemeApplicator.applyTheme(props, tokens, mockContext);

      expect(result.unresolvedTokens).toContain('$color.nonexistent.500');
    });

    it('should use fallback values for unresolved tokens', () => {
      const props = { label: 'Click me' };
      const tokens = {
        color: '$color.nonexistent.500',
      };
      const contextWithFallback: ThemeContext = {
        ...mockContext,
        fallbackValues: { color: '#000000' },
      };

      const result = ThemeApplicator.applyTheme(props, tokens, contextWithFallback);

      expect(result.props.color).toBe('#000000');
    });
  });

  describe('resolveToken', () => {
    it('should resolve token from base layer', () => {
      const value = ThemeApplicator.resolveToken('$color.primary.500', mockContext);

      expect(value).toBe('#1976d2');
    });

    it('should resolve token without $ prefix', () => {
      const value = ThemeApplicator.resolveToken('color.primary.500', mockContext);

      expect(value).toBe('#1976d2');
    });

    it('should return undefined for non-existent token', () => {
      const value = ThemeApplicator.resolveToken('$color.nonexistent', mockContext);

      expect(value).toBeUndefined();
    });

    it('should cascade through layers (brand overrides base)', () => {
      const brandContext: ThemeContext = {
        ...mockContext,
        activeLayer: 'brand',
      };

      const value = ThemeApplicator.resolveToken('$color.primary.500', brandContext);

      expect(value).toBe('#ff5722'); // Brand color, not base
    });

    it('should cascade through layers (workspace overrides brand)', () => {
      const workspaceContext: ThemeContext = {
        ...mockContext,
        activeLayer: 'workspace',
      };

      const value = ThemeApplicator.resolveToken('$color.primary.500', workspaceContext);

      expect(value).toBe('#4caf50'); // Workspace color
    });

    it('should fall back to base layer if not in higher layer', () => {
      const brandContext: ThemeContext = {
        ...mockContext,
        activeLayer: 'brand',
      };

      const value = ThemeApplicator.resolveToken('$color.primary.600', brandContext);

      expect(value).toBe('#1565c0'); // Falls back to base
    });
  });

  describe('createCSSVariables', () => {
    it('should create CSS custom properties', () => {
      const cssVars = ThemeApplicator.createCSSVariables(mockTokens, 'base');

      expect(cssVars['--color-primary-500']).toBe('#1976d2');
      expect(cssVars['--color-primary-600']).toBe('#1565c0');
      expect(cssVars['--spacing-md']).toBe('16');
    });
  });

  describe('validateTokenPaths', () => {
    it('should validate valid token paths', () => {
      const tokens = {
        color: '$color.primary.500',
        backgroundColor: '$color.neutral.100',
      };

      const result = ThemeApplicator.validateTokenPaths(tokens, mockContext);

      expect(result.valid).toBe(true);
      expect(result.errors).toHaveLength(0);
    });

    it('should detect invalid token paths', () => {
      const tokens = {
        color: '$color.nonexistent',
        backgroundColor: '$color.invalid.path',
      };

      const result = ThemeApplicator.validateTokenPaths(tokens, mockContext);

      expect(result.valid).toBe(false);
      expect(result.errors.length).toBeGreaterThan(0);
    });
  });

  describe('getAvailableTokens', () => {
    it('should return all available token paths', () => {
      const paths = ThemeApplicator.getAvailableTokens(mockContext);

      expect(paths).toContain('color.primary.500');
      expect(paths).toContain('color.primary.600');
      expect(paths).toContain('spacing.md');
      expect(paths.length).toBeGreaterThan(0);
    });
  });

  describe('mergeContexts', () => {
    it('should merge parent and child contexts', () => {
      const parentContext: ThemeContext = {
        activeLayer: 'base',
        tokens: mockTokens,
        fallbackValues: { color: '#000' },
      };

      const childOverrides = {
        activeLayer: 'brand' as const,
        fallbackValues: { backgroundColor: '#fff' },
      };

      const merged = ThemeApplicator.mergeContexts(parentContext, childOverrides);

      expect(merged.activeLayer).toBe('brand');
      expect(merged.tokens).toBe(mockTokens);
      expect(merged.fallbackValues).toEqual({
        color: '#000',
        backgroundColor: '#fff',
      });
    });
  });

  describe('createDefaultContext', () => {
    it('should create a valid default context', () => {
      const context = ThemeApplicator.createDefaultContext();

      expect(context.activeLayer).toBe('base');
      expect(context.tokens.base).toBeDefined();
      expect(context.tokens.brand).toBeDefined();
      expect(context.tokens.workspace).toBeDefined();
      expect(context.tokens.app).toBeDefined();
      expect(context.fallbackValues).toEqual({});
    });
  });
});
