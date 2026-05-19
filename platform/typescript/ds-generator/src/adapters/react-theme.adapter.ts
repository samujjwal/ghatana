/**
 * @fileoverview React Theme Target Adapter
 *
 * Generates React theme object from design system tokens.
 * Provides a production-grade adapter with proper TypeScript types.
 *
 * @doc.type module
 * @doc.purpose React theme generation from design tokens
 * @doc.layer ds-generator
 */

import type { MaterializedTokens } from '../presets/index.js';

export interface ReactThemeAdapterOptions {
  /** Theme name (default: 'default') */
  themeName?: string;
  /** Whether to include TypeScript types (default: true) */
  includeTypes?: boolean;
}

export function generateReactTheme(
  tokens: MaterializedTokens,
  options: ReactThemeAdapterOptions = {},
): string {
  const { themeName = 'default', includeTypes = true } = options;

  const lines: string[] = [];

  if (includeTypes) {
    lines.push('import type { Theme } from \'@ghatana/theme\';');
    lines.push('');
  }

  lines.push(`export const ${themeName}Theme: Theme = {`);
  lines.push('  colors: {');
  for (const [key, value] of Object.entries(tokens.colors)) {
    lines.push(`    '${key}': '${value}',`);
  }
  lines.push('  },');
  lines.push('');
  lines.push('  typography: {');
  lines.push(`    fontFamily: '${tokens.fontFamily}',`);
  lines.push('    fontSizes: {');
  for (const [key, value] of Object.entries(tokens.fontSizes)) {
    lines.push(`      '${key}': ${value},`);
  }
  lines.push('    },');
  lines.push('  },');
  lines.push('');
  lines.push('  borderRadius: {');
  for (const [key, value] of Object.entries(tokens.borderRadius)) {
    lines.push(`    '${key}': '${value}',`);
  }
  lines.push('  },');
  lines.push('');
  lines.push('  spacing: {');
  for (const [key, value] of Object.entries(tokens.spacing)) {
    lines.push(`    '${key}': ${value},`);
  }
  lines.push('  },');
  lines.push('');
  lines.push('  shadows: {');
  for (const [key, value] of Object.entries(tokens.elevation)) {
    lines.push(`    '${key}': '${value}',`);
  }
  lines.push('  },');
  lines.push('};');

  return lines.join('\n');
}
