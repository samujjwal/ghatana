/**
 * @fileoverview Tailwind CSS Config Target Adapter
 *
 * Generates Tailwind CSS theme configuration from design system tokens.
 * Provides a production-grade adapter with proper TypeScript types.
 *
 * @doc.type module
 * @doc.purpose Tailwind CSS theme generation from design tokens
 * @doc.layer ds-generator
 */

import type { MaterializedTokens } from '../presets/index.js';

export interface TailwindAdapterOptions {
  /** Whether to extend existing Tailwind config (default: true) */
  extend?: boolean;
  /** Custom Tailwind theme keys to include */
  customKeys?: Record<string, unknown>;
}

export function generateTailwindConfig(
  tokens: MaterializedTokens,
  options: TailwindAdapterOptions = {},
): string {
  const { extend = true, customKeys = {} } = options;

  const lines: string[] = [];
  lines.push('/** @type {import("tailwindcss").Config} */');
  lines.push('export default {');
  lines.push('  theme: {');
  
  if (extend) {
    lines.push('    extend: {');
  }

  // Colors
  lines.push('      colors: {');
  for (const [key, value] of Object.entries(tokens.colors)) {
    lines.push(`        '${key}': '${value}',`);
  }
  lines.push('      },');

  // Font Family
  lines.push('      fontFamily: {');
  lines.push(`        sans: ['${tokens.fontFamily}', 'sans-serif'],`);
  lines.push('      },');

  // Font Size
  lines.push('      fontSize: {');
  for (const [key, value] of Object.entries(tokens.fontSizes)) {
    lines.push(`        '${key}': '${value}px',`);
  }
  lines.push('      },');

  // Border Radius
  lines.push('      borderRadius: {');
  for (const [key, value] of Object.entries(tokens.borderRadius)) {
    lines.push(`        '${key}': '${value}',`);
  }
  lines.push('      },');

  // Spacing
  lines.push('      spacing: {');
  for (const [key, value] of Object.entries(tokens.spacing)) {
    lines.push(`        '${key}': '${value}px',`);
  }
  lines.push('      },');

  // Box Shadow (elevation)
  lines.push('      boxShadow: {');
  for (const [key, value] of Object.entries(tokens.elevation)) {
    lines.push(`        '${key}': '${value}',`);
  }
  lines.push('      },');

  // Custom keys
  if (Object.keys(customKeys).length > 0) {
    for (const [key, value] of Object.entries(customKeys)) {
      lines.push(`      ${key}: ${JSON.stringify(value)},`);
    }
  }

  if (extend) {
    lines.push('    },');
  }

  lines.push('  },');
  lines.push('};');

  return lines.join('\n');
}
