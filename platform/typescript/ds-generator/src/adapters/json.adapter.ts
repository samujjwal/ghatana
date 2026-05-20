/**
 * @fileoverview JSON Tokens Target Adapter
 *
 * Generates JSON token export from design system tokens.
 * Provides a production-grade adapter with proper formatting.
 *
 * @doc.type module
 * @doc.purpose JSON token export from design tokens
 * @doc.layer ds-generator
 */

import type { MaterializedTokens } from '../presets/index.js';

export interface JSONAdapterOptions {
  /** Format version (default: '1.0.0') */
  formatVersion?: string;
  /** Whether to include metadata (default: true) */
  includeMetadata?: boolean;
  /** Indentation for JSON (default: 2 spaces) */
  indent?: number;
  /**
   * Injectable clock function for the generatedAt timestamp.
   * Defaults to `() => new Date().toISOString()`.
   * Provide a deterministic value in tests.
   */
  clockFn?: () => string;
}

export function generateJSONTokens(
  tokens: MaterializedTokens,
  options: JSONAdapterOptions = {},
): string {
  const {
    formatVersion = '1.0.0',
    includeMetadata = true,
    indent = 2,
    clockFn = () => new Date().toISOString(),
  } = options;

  const jsonTokens: Record<string, unknown> = {
    formatVersion,
    tokens: {
      colors: tokens.colors,
      typography: {
        fontFamily: tokens.fontFamily,
        fontSizes: tokens.fontSizes,
      },
      borderRadius: tokens.borderRadius,
      spacing: tokens.spacing,
      elevation: tokens.elevation,
    },
  };

  if (includeMetadata) {
    jsonTokens.metadata = {
      generatedAt: clockFn(),
      generator: 'ghatana-ds-generator',
    };
  }

  return JSON.stringify(jsonTokens, null, indent);
}
