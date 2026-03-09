import type { TokenRegistry } from './registry';
import { tokens } from './registry';

export interface CssVariableOptions {
  /**
   * Prefix for generated CSS variables. Defaults to `gh`.
   */
  prefix?: string;
  /**
   * Selector used to wrap the variables. Defaults to `:root`.
   */
  selector?: string;
  /**
   * Whether to include comments with the token path.
   */
  includeComments?: boolean;
}

interface FlattenContext {
  path: string[];
  result: Record<string, string | number>;
}

function flattenTokens(
  input: unknown,
  ctx: FlattenContext
): Record<string, string | number> {
  if (input === null || input === undefined) {
    return ctx.result;
  }

  if (typeof input === 'string' || typeof input === 'number') {
    ctx.result[ctx.path.join('-')] = input;
    return ctx.result;
  }

  if (Array.isArray(input)) {
    input.forEach((value, index) => {
      flattenTokens(value, {
        path: [...ctx.path, String(index)],
        result: ctx.result,
      });
    });
    return ctx.result;
  }

  if (typeof input === 'object') {
    Object.entries(input as Record<string, unknown>).forEach(([key, value]) => {
      flattenTokens(value, {
        path: [...ctx.path, key],
        result: ctx.result,
      });
    });
  }

  return ctx.result;
}

/**
 * Creates a flat map of CSS variables (without selector wrapper) for the provided token registry.
 */
export function createCssVariableMap(
  registry: TokenRegistry = tokens,
  options: Pick<CssVariableOptions, 'prefix'> = {}
): Record<string, string | number> {
  const prefix = options.prefix ?? 'gh';
  const flattened = flattenTokens(registry, { path: [], result: {} });
  const entries: Record<string, string | number> = {};

  Object.entries(flattened).forEach(([path, value]) => {
    entries[`--${prefix}-${path}`.toLowerCase().replace(/[^a-z0-9-]/g, '-')] = value;
  });

  return entries;
}

/**
 * Generates a CSS string containing custom property declarations for all tokens.
 */
export function generateCssVariables(
  registry: TokenRegistry = tokens,
  options: CssVariableOptions = {}
): string {
  const selector = options.selector ?? ':root';
  const map = createCssVariableMap(registry, options);

  const lines: string[] = [];

  Object.entries(map).forEach(([variable, value]) => {
    if (options.includeComments) {
      lines.push(`  /* ${variable.replace(/^--[a-z]+-/, '').replace(/-/g, '.')} */`);
    }
    lines.push(`  ${variable}: ${value};`);
  });

  return `${selector} {\n${lines.join('\n')}\n}`;
}

/**
 * Convenience helper that returns a CSSStyleSheet-ready string from the default token registry.
 */
export function getCssVariables(options: CssVariableOptions = {}): string {
  return generateCssVariables(tokens, options);
}
