/**
 * @fileoverview Tailwind CSS config target for DesignSystemDocument.
 *
 * Emits a `tailwind.config.js`-compatible theme object derived from the
 * document's resolved tokens and semantic aliases.
 *
 * @doc.type module
 * @doc.purpose DesignSystemDocument → Tailwind CSS theme emission
 * @doc.layer ds-generator
 * @doc.pattern Adapter
 */

import type { DesignSystemDocument } from '../model/design-system-document.js';
import {
  buildTokenGraph,
  flattenTokenRecord,
  graphToRecord,
} from '../tokens/token-graph.js';

export interface TailwindTargetOptions {
  /** Whether to wrap in `extend`. Default: true. */
  extend?: boolean;
}

/**
 * Emit a `DesignSystemDocument` as a Tailwind CSS theme config string.
 */
export function emitTailwind(
  doc: DesignSystemDocument,
  options: TailwindTargetOptions = {},
): string {
  const { extend = true } = options;

  // Resolve semantic aliases
  const flatBase = flattenTokenRecord(doc.resolvedTokens);
  const graphResult = buildTokenGraph(doc.semanticAliases, flatBase);
  const aliasRecord = graphToRecord(graphResult);

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
  lines.push('/** @type {import("tailwindcss").Config} */');
  lines.push('export default {');
  lines.push('  theme: {');
  const themeIndent = extend ? '      ' : '    ';
  if (extend) lines.push('    extend: {');

  // Colors (merge resolved tokens + semantic aliases)
  const mergedColors: Record<string, string> = { ...colors };
  for (const [alias, val] of Object.entries(aliasRecord)) {
    mergedColors[alias.replace(/\./g, '-')] = val;
  }
  lines.push(`${themeIndent}colors: {`);
  for (const [k, v] of Object.entries(mergedColors).sort(([a], [b]) => a.localeCompare(b))) {
    lines.push(`${themeIndent}  '${k}': '${v}',`);
  }
  lines.push(`${themeIndent}},`);

  // Font family
  if (fontFamily) {
    lines.push(`${themeIndent}fontFamily: {`);
    lines.push(`${themeIndent}  sans: ['${fontFamily}', 'sans-serif'],`);
    lines.push(`${themeIndent}},`);
  }

  // Font sizes
  if (Object.keys(fontSizes).length > 0) {
    lines.push(`${themeIndent}fontSize: {`);
    for (const [k, v] of Object.entries(fontSizes).sort(([a], [b]) => a.localeCompare(b))) {
      lines.push(`${themeIndent}  '${k}': '${v}px',`);
    }
    lines.push(`${themeIndent}},`);
  }

  // Border radius
  if (Object.keys(borderRadius).length > 0) {
    lines.push(`${themeIndent}borderRadius: {`);
    for (const [k, v] of Object.entries(borderRadius).sort(([a], [b]) => a.localeCompare(b))) {
      lines.push(`${themeIndent}  '${k}': '${v}',`);
    }
    lines.push(`${themeIndent}},`);
  }

  // Spacing
  if (Object.keys(spacing).length > 0) {
    lines.push(`${themeIndent}spacing: {`);
    for (const [k, v] of Object.entries(spacing).sort(([a], [b]) => a.localeCompare(b))) {
      lines.push(`${themeIndent}  '${k}': '${v}px',`);
    }
    lines.push(`${themeIndent}},`);
  }

  // Box shadow
  if (Object.keys(shadow).length > 0) {
    lines.push(`${themeIndent}boxShadow: {`);
    for (const [k, v] of Object.entries(shadow).sort(([a], [b]) => a.localeCompare(b))) {
      lines.push(`${themeIndent}  '${k}': '${v}',`);
    }
    lines.push(`${themeIndent}},`);
  }

  // Transition duration (from motion tokens)
  if (Object.keys(motion).length > 0) {
    lines.push(`${themeIndent}transitionDuration: {`);
    for (const [k, v] of Object.entries(motion).sort(([a], [b]) => a.localeCompare(b))) {
      lines.push(`${themeIndent}  '${k}': '${v}',`);
    }
    lines.push(`${themeIndent}},`);
  }

  // Z-index
  if (Object.keys(zIndex).length > 0) {
    lines.push(`${themeIndent}zIndex: {`);
    for (const [k, v] of Object.entries(zIndex).sort(([a], [b]) => a.localeCompare(b))) {
      lines.push(`${themeIndent}  '${k}': ${String(v)},`);
    }
    lines.push(`${themeIndent}},`);
  }

  if (extend) lines.push('    },');
  lines.push('  },');
  lines.push('};');

  return lines.join('\n');
}
