/**
 * @fileoverview React theme object target for DesignSystemDocument.
 *
 * Emits a typed TypeScript/JavaScript module that exports a React-compatible
 * theme object from the document's resolved tokens.  The generated file is
 * compatible with the `@ghatana/theme` package's `Theme` shape.
 *
 * @doc.type module
 * @doc.purpose DesignSystemDocument → React theme object emission
 * @doc.layer ds-generator
 * @doc.pattern Adapter
 */

import type { DesignSystemDocument } from '../model/design-system-document.js';

export interface ReactThemeTargetOptions {
  /** Exported variable name. Default: "theme". */
  variableName?: string;
  /** Whether to include TypeScript type annotation. Default: true. */
  includeTypes?: boolean;
}

/**
 * Emit a `DesignSystemDocument` as a TypeScript React theme module string.
 */
export function emitReactTheme(
  doc: DesignSystemDocument,
  options: ReactThemeTargetOptions = {},
): string {
  const { variableName = 'theme', includeTypes = true } = options;

  const tokens = doc.resolvedTokens;
  const colors = (tokens['colors'] ?? {}) as Record<string, string>;
  const fontSizes = (tokens['fontSizes'] ?? {}) as Record<string, number>;
  const fontFamily = (tokens['fontFamily'] as string | undefined) ?? '';
  const borderRadius = (tokens['borderRadius'] ?? {}) as Record<string, string>;
  const spacing = (tokens['spacing'] ?? {}) as Record<string, number>;
  const shadow = (tokens['shadow'] ?? tokens['elevation'] ?? {}) as Record<string, string>;
  const motion = (tokens['motion'] ?? {}) as Record<string, string>;
  const zIndex = (tokens['zIndex'] ?? {}) as Record<string, number>;

  const lines: string[] = [];

  if (includeTypes) {
    lines.push("import type { Theme } from '@ghatana/theme';");
    lines.push('');
  }

  lines.push(`/** Design system: ${doc.name} (preset: ${doc.basePresetId}) */`);
  const typeAnnotation = includeTypes ? ': Theme' : '';
  lines.push(`export const ${variableName}${typeAnnotation} = {`);

  // Colors
  lines.push('  colors: {');
  for (const [k, v] of Object.entries(colors).sort(([a], [b]) => a.localeCompare(b))) {
    lines.push(`    '${k}': '${v}',`);
  }
  lines.push('  },');

  // Typography
  lines.push('  typography: {');
  if (fontFamily) lines.push(`    fontFamily: '${fontFamily}',`);
  if (Object.keys(fontSizes).length > 0) {
    lines.push('    fontSizes: {');
    for (const [k, v] of Object.entries(fontSizes).sort(([a], [b]) => a.localeCompare(b))) {
      lines.push(`      '${k}': ${String(v)},`);
    }
    lines.push('    },');
  }
  lines.push('  },');

  // Border radius
  if (Object.keys(borderRadius).length > 0) {
    lines.push('  borderRadius: {');
    for (const [k, v] of Object.entries(borderRadius).sort(([a], [b]) => a.localeCompare(b))) {
      lines.push(`    '${k}': '${v}',`);
    }
    lines.push('  },');
  }

  // Spacing
  if (Object.keys(spacing).length > 0) {
    lines.push('  spacing: {');
    for (const [k, v] of Object.entries(spacing).sort(([a], [b]) => a.localeCompare(b))) {
      lines.push(`    '${k}': ${String(v)},`);
    }
    lines.push('  },');
  }

  // Shadow / elevation
  if (Object.keys(shadow).length > 0) {
    lines.push('  shadow: {');
    for (const [k, v] of Object.entries(shadow).sort(([a], [b]) => a.localeCompare(b))) {
      lines.push(`    '${k}': '${v}',`);
    }
    lines.push('  },');
  }

  // Motion
  if (Object.keys(motion).length > 0) {
    lines.push('  motion: {');
    for (const [k, v] of Object.entries(motion).sort(([a], [b]) => a.localeCompare(b))) {
      lines.push(`    '${k}': '${v}',`);
    }
    lines.push('  },');
  }

  // Z-index
  if (Object.keys(zIndex).length > 0) {
    lines.push('  zIndex: {');
    for (const [k, v] of Object.entries(zIndex).sort(([a], [b]) => a.localeCompare(b))) {
      lines.push(`    '${k}': ${String(v)},`);
    }
    lines.push('  },');
  }

  lines.push('} as const;');

  return lines.join('\n');
}
