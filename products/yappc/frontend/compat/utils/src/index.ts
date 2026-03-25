/**
 * @ghatana/yappc-utils — Consolidated Utility Library
 *
 * Core utilities consolidated from:
 * - libs/utils (feature flags, performance)
 * - libs/infrastructure (adapters, platform integration)
 * - libs/platform-tools (dev tooling, diagnostics)
 * - libs/performance (runtime perf monitoring)
 *
 * @doc.type library
 * @doc.purpose Shared utility functions and helpers
 * @doc.layer platform
 */

// Core utilities
export * from './featureFlags';
export * from './performance';

// ============================================================================
// CONSOLIDATED LIBRARIES (REMOVED - packages don't exist)
// ============================================================================

// REMOVED: @ghatana/yappc-infrastructure - package doesn't exist
// REMOVED: @ghatana/yappc-platform-tools - package doesn't exist
// REMOVED: @ghatana/yappc-performance - package doesn't exist

// ============================================================================
// DEPRECATION WARNING
// ============================================================================
// eslint-disable-next-line no-console
console.warn(
  '[DEPRECATED] @ghatana/yappc-utils is deprecated. Use @yappc/core/utils instead. ' +
    'See: docs/NAMING_CONVENTIONS.md'
);
