/**
 * @fileoverview CSS custom properties target for DesignSystemDocument.
 *
 * Wraps the lower-level `css-variables.adapter` with document-aware logic:
 * resolves semantic aliases, emits all token categories, and optionally emits
 * component variant scopes as nested CSS custom-property blocks.
 *
 * @doc.type module
 * @doc.purpose DesignSystemDocument → CSS custom properties emission
 * @doc.layer ds-generator
 * @doc.pattern Adapter
 */

import type { DesignSystemDocument } from '../model/design-system-document.js';
import {
  buildTokenGraph,
  flattenTokenRecord,
  graphToRecord,
} from '../tokens/token-graph.js';

export interface CssTargetOptions {
  /** CSS variable prefix (e.g. "--ds-"). Default: "--". */
  prefix?: string;
  /** Whether to include comment headers. Default: true. */
  includeComments?: boolean;
  /** Indentation string. Default: two spaces. */
  indent?: string;
  /** Whether to emit component variant scopes. Default: true. */
  includeVariants?: boolean;
}

/**
 * Emit a `DesignSystemDocument` as CSS custom properties.
 *
 * The resulting string is deterministic: tokens are emitted in lexicographic
 * key order so that diffs are stable.
 */
export function emitCss(
  doc: DesignSystemDocument,
  options: CssTargetOptions = {},
): string {
  const {
    prefix = '--',
    includeComments = true,
    indent = '  ',
    includeVariants = true,
  } = options;

  const lines: string[] = [];

  if (includeComments) {
    lines.push(`/* Design System: ${doc.name} */`);
    lines.push(`/* Base preset: ${doc.basePresetId} */`);
    lines.push(`/* Generated: ${doc.generatedAt} */`);
    lines.push('');
  }

  // Resolve base tokens
  const flatBase = flattenTokenRecord(doc.resolvedTokens);

  // Resolve semantic aliases (if any)
  const graphResult = buildTokenGraph(doc.semanticAliases, flatBase);
  const aliasRecord = graphToRecord(graphResult);

  // Emit :root block with resolved tokens + aliases
  lines.push(':root {');

  const baseRecord = doc.resolvedTokens;
  for (const category of Object.keys(baseRecord).sort()) {
    const value = baseRecord[category];
    if (value !== null && typeof value === 'object' && !Array.isArray(value)) {
      if (includeComments) lines.push(`${indent}/* ${category} */`);
      for (const [key, val] of Object.entries(value as Record<string, unknown>).sort(([a], [b]) => a.localeCompare(b))) {
        lines.push(`${indent}${prefix}${category}-${key}: ${String(val)};`);
      }
    } else {
      lines.push(`${indent}${prefix}${category}: ${String(value)};`);
    }
  }

  // Semantic alias custom properties
  if (Object.keys(aliasRecord).length > 0) {
    if (includeComments) lines.push(`${indent}/* Semantic aliases */`);
    for (const [alias, val] of Object.entries(aliasRecord).sort(([a], [b]) => a.localeCompare(b))) {
      // Convert dot-notation alias to hyphenated CSS var name
      const varName = alias.replace(/\./g, '-');
      lines.push(`${indent}${prefix}${varName}: ${val};`);
    }
  }

  lines.push('}');

  // Component variant scopes
  if (includeVariants && doc.componentVariants.length > 0) {
    for (const component of doc.componentVariants) {
      for (const [variantName, overrides] of Object.entries(component.variants).sort(([a], [b]) => a.localeCompare(b))) {
        lines.push('');
        if (includeComments) {
          lines.push(`/* Component: ${component.componentId} — variant: ${variantName} */`);
        }
        lines.push(`[data-variant="${component.componentId}-${variantName}"] {`);
        for (const [prop, val] of Object.entries(overrides).sort(([a], [b]) => a.localeCompare(b))) {
          lines.push(`${indent}${prop}: ${val};`);
        }
        lines.push('}');
      }

      for (const stateDef of component.states) {
        lines.push('');
        if (includeComments) {
          lines.push(`/* Component: ${component.componentId} — state: ${stateDef.state} */`);
        }
        lines.push(`[data-component="${component.componentId}"]:${stateDef.state} {`);
        for (const [prop, val] of Object.entries(stateDef.tokenOverrides).sort(([a], [b]) => a.localeCompare(b))) {
          lines.push(`${indent}${prop}: ${val};`);
        }
        lines.push('}');
      }
    }
  }

  return lines.join('\n');
}
