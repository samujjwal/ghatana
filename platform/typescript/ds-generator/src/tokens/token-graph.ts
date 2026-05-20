/**
 * @fileoverview Token alias resolution graph.
 *
 * Provides a directed acyclic graph (DAG) that resolves chains of semantic
 * token aliases to their ultimate primitive token values.  The graph is built
 * from a flat list of {@link SemanticTokenAlias} entries and the resolved base
 * token map produced by the preset/brand pipeline.
 *
 * Resolution rules:
 * 1. If `alias.tokenKey` is a key in `baseTokens`, the value is returned
 *    directly (primitive resolution).
 * 2. If `alias.tokenKey` is another alias name, the graph walks the chain
 *    until it reaches a primitive or detects a cycle.
 * 3. Cycles are surfaced as `TokenGraphError` entries and the offending alias
 *    is left unresolved.
 *
 * @doc.type module
 * @doc.purpose Token alias resolution graph with cycle detection
 * @doc.layer ds-generator
 * @doc.pattern Graph
 */

import type { SemanticTokenAlias } from '../model/design-system-document.js';

// ============================================================================
// Public types
// ============================================================================

/** An error discovered during graph construction or resolution. */
export interface TokenGraphError {
  readonly kind: 'cycle' | 'missing-token' | 'missing-alias';
  readonly alias: string;
  readonly message: string;
}

/** A fully-resolved entry in the alias graph. */
export interface ResolvedAlias {
  /** The semantic alias name. */
  readonly alias: string;
  /** The concrete value resolved from the base token map. */
  readonly value: unknown;
  /**
   * Chain of intermediate alias names traversed to reach the primitive.
   * Empty for direct (one-hop) resolutions.
   */
  readonly resolutionPath: readonly string[];
}

/** Result of building and resolving the token graph. */
export interface TokenGraphResult {
  /**
   * Successfully resolved aliases.  Only entries that reached a primitive
   * token value are included.
   */
  readonly resolved: ReadonlyMap<string, ResolvedAlias>;
  /** Any errors encountered during resolution. */
  readonly errors: readonly TokenGraphError[];
  /** Whether all aliases were resolved without errors. */
  readonly isComplete: boolean;
}

// ============================================================================
// Internal helpers
// ============================================================================

/**
 * Build a lookup map from alias name → SemanticTokenAlias for fast access.
 */
function buildAliasLookup(
  aliases: readonly SemanticTokenAlias[],
): ReadonlyMap<string, SemanticTokenAlias> {
  const map = new Map<string, SemanticTokenAlias>();
  for (const a of aliases) {
    map.set(a.alias, a);
  }
  return map;
}

/**
 * Walk the alias chain from `startAlias` until we reach a primitive token
 * value or detect a cycle/missing reference.
 */
function resolveChain(
  startAlias: string,
  aliasLookup: ReadonlyMap<string, SemanticTokenAlias>,
  baseTokens: ReadonlyMap<string, unknown>,
  errors: TokenGraphError[],
): ResolvedAlias | null {
  const visited = new Set<string>();
  const path: string[] = [];
  let current = startAlias;

  while (true) {
    if (visited.has(current)) {
      errors.push({
        kind: 'cycle',
        alias: startAlias,
        message: `Cycle detected resolving alias "${startAlias}": path was ${[...path, current].join(' → ')}`,
      });
      return null;
    }
    visited.add(current);
    path.push(current);

    // Check if current token key exists directly in base tokens (primitive)
    if (baseTokens.has(current)) {
      return {
        alias: startAlias,
        value: baseTokens.get(current),
        resolutionPath: path.slice(1), // exclude the startAlias itself
      };
    }

    // Check if current is another alias
    const nextAlias = aliasLookup.get(current);
    if (!nextAlias) {
      // Neither a base token nor a known alias
      errors.push({
        kind: path.length <= 2 ? 'missing-token' : 'missing-alias',
        alias: startAlias,
        message:
          path.length === 1
            ? `Alias "${startAlias}" references unknown token key "${current}"`
            : `Alias "${startAlias}" chain reaches unknown alias or token "${current}" after path: ${path.join(' → ')}`,
      });
      return null;
    }

    // Follow the chain
    current = nextAlias.tokenKey;
  }
}

// ============================================================================
// Public API
// ============================================================================

/**
 * Build and resolve a token alias graph.
 *
 * @param aliases - Semantic token aliases to resolve.
 * @param baseTokens - The flat resolved token map produced by the
 *   preset/brand pipeline.  Keys are bare token names (e.g. `"primary"`,
 *   `"spacing-4"`) and values are the corresponding resolved values.
 */
export function buildTokenGraph(
  aliases: readonly SemanticTokenAlias[],
  baseTokens: ReadonlyMap<string, unknown>,
): TokenGraphResult {
  const aliasLookup = buildAliasLookup(aliases);
  const resolved = new Map<string, ResolvedAlias>();
  const errors: TokenGraphError[] = [];

  for (const alias of aliases) {
    const result = resolveChain(alias.alias, aliasLookup, baseTokens, errors);
    if (result !== null) {
      resolved.set(alias.alias, result);
    }
  }

  return {
    resolved,
    errors,
    isComplete: errors.length === 0,
  };
}

/**
 * Convert a plain `Record<string, unknown>` token map to a `ReadonlyMap` for
 * use with {@link buildTokenGraph}.
 *
 * Nested objects are flattened using dot-notation keys so that both `"colors"`
 * (a sub-object) and `"colors.primary"` (a dotted key) are addressable.
 */
export function flattenTokenRecord(
  record: Record<string, unknown>,
  prefix = '',
): ReadonlyMap<string, unknown> {
  const flat = new Map<string, unknown>();

  for (const [key, value] of Object.entries(record)) {
    const fullKey = prefix ? `${prefix}.${key}` : key;

    if (value !== null && typeof value === 'object' && !Array.isArray(value)) {
      // Recurse into nested objects
      const nested = flattenTokenRecord(
        value as Record<string, unknown>,
        fullKey,
      );
      // Also store the top-level sub-object itself for alias references that
      // point to the group name
      flat.set(fullKey, value);
      for (const [nestedKey, nestedValue] of nested) {
        flat.set(nestedKey, nestedValue);
      }
    } else {
      flat.set(fullKey, value);
    }
  }

  return flat;
}

/**
 * Emit a resolved token graph as a flat `Record<string, string>` suitable for
 * use in CSS custom property generation or JSON targets.
 *
 * Values that are not strings are serialized with `String()`.
 */
export function graphToRecord(
  result: TokenGraphResult,
): Record<string, string> {
  const output: Record<string, string> = {};
  for (const [alias, resolved] of result.resolved) {
    output[alias] = String(resolved.value);
  }
  return output;
}
