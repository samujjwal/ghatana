/**
 * @ghatana/yappc-config — Consolidated Configuration Library
 *
 * Configuration utilities consolidated from:
 * - libs/config (loaders, patterns, feature flags, results)
 * - libs/storage (persistence, caching)
 *
 * @doc.type library
 * @doc.purpose Application configuration and storage utilities
 * @doc.layer platform
 */

// Configuration loading
export * from './tasks/configLoader';

// Async patterns
export * from './patterns/async-patterns';

// Feature flags
export * from './features/feature-flags.tsx';

// Result types
export * from './results/result';

// ============================================================================
// REMOVED: @ghatana/yappc-storage - package doesn't exist
// ============================================================================

// ============================================================================
// DEPRECATION WARNING
// ============================================================================
 
console.warn(
  '[DEPRECATED] @ghatana/yappc-config is deprecated. Use @yappc/core/config instead. ' +
    'See: docs/NAMING_CONVENTIONS.md'
);
