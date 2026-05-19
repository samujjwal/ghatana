/**
 * @fileoverview Golden tests for target adapters.
 *
 * Ensures adapter outputs remain consistent across versions by comparing
 * generated output against expected golden snapshots.
 *
 * @doc.type test
 * @doc.purpose Target adapter golden test validation
 * @doc.layer ds-generator
 */

import { describe, it, expect } from 'vitest';
import {
  generateCSSVariables,
  generateTailwindConfig,
  generateReactTheme,
  generateJSONTokens,
} from '../index.js';
import type { MaterializedTokens } from '../../presets/index.js';

const mockTokens: MaterializedTokens = {
  colors: {
    primary: '#0066CC',
    secondary: '#6B7280',
    accent: '#FF6B6B',
    neutral: '#F3F4F6',
  },
  fontFamily: 'Inter, sans-serif',
  fontSizes: {
    xs: 12,
    sm: 14,
    md: 16,
    lg: 18,
    xl: 24,
  },
  borderRadius: {
    sm: '4px',
    md: '8px',
    lg: '12px',
    full: '9999px',
  },
  spacing: {
    xs: 4,
    sm: 8,
    md: 16,
    lg: 24,
    xl: 32,
  },
  elevation: {
    sm: '0 1px 2px rgba(0,0,0,0.05)',
    md: '0 4px 6px rgba(0,0,0,0.1)',
    lg: '0 10px 15px rgba(0,0,0,0.15)',
  },
};

describe('CSS Variables Adapter Golden Tests', () => {
  it('generates CSS variables matching golden snapshot', () => {
    const output = generateCSSVariables(mockTokens, {
      prefix: '',
      includeComments: true,
      indent: '  ',
    });

    expect(output).toMatchSnapshot('css-variables');
  });

  it('generates CSS variables with custom prefix', () => {
    const output = generateCSSVariables(mockTokens, {
      prefix: 'ghatana-',
      includeComments: false,
    });

    expect(output).toContain('--ghatana-color-primary');
    expect(output).toContain('--ghatana-font-family-base');
  });
});

describe('Tailwind Config Adapter Golden Tests', () => {
  it('generates Tailwind config matching golden snapshot', () => {
    const output = generateTailwindConfig(mockTokens, {
      extend: true,
    });

    expect(output).toMatchSnapshot('tailwind-config');
  });

  it('generates Tailwind config without extend', () => {
    const output = generateTailwindConfig(mockTokens, {
      extend: false,
    });

    expect(output).not.toContain('extend:');
    expect(output).toContain('theme: {');
  });
});

describe('React Theme Adapter Golden Tests', () => {
  it('generates React theme matching golden snapshot', () => {
    const output = generateReactTheme(mockTokens, {
      themeName: 'default',
      includeTypes: true,
    });

    expect(output).toMatchSnapshot('react-theme');
  });

  it('generates React theme without TypeScript types', () => {
    const output = generateReactTheme(mockTokens, {
      themeName: 'custom',
      includeTypes: false,
    });

    expect(output).not.toContain('import type');
    expect(output).toContain('customTheme:');
  });
});

describe('JSON Tokens Adapter Golden Tests', () => {
  it('generates JSON tokens matching golden snapshot', () => {
    const output = generateJSONTokens(mockTokens, {
      formatVersion: '1.0.0',
      includeMetadata: true,
      indent: 2,
    });

    expect(output).toMatchSnapshot('json-tokens');
  });

  it('generates JSON tokens without metadata', () => {
    const output = generateJSONTokens(mockTokens, {
      includeMetadata: false,
    });

    const parsed = JSON.parse(output);
    expect(parsed.metadata).toBeUndefined();
    expect(parsed.formatVersion).toBe('1.0.0');
  });

  it('generates valid JSON', () => {
    const output = generateJSONTokens(mockTokens);
    
    expect(() => JSON.parse(output)).not.toThrow();
    const parsed = JSON.parse(output);
    expect(parsed.tokens).toBeDefined();
    expect(parsed.tokens.colors).toEqual(mockTokens.colors);
  });
});
