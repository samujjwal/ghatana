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
// CONSOLIDATED LIBRARIES
// ============================================================================

// REMOVED: deprecated @ghatana/yappc-infrastructure
// REMOVED: deprecated @ghatana/yappc-infrastructure
// // // Infrastructure adapters (from @ghatana/yappc-infrastructure)
export * from '../../infrastructure/src';

// REMOVED: deprecated @ghatana/yappc-platform-tools
// REMOVED: deprecated @ghatana/yappc-platform-tools
// // // Platform tools (from @ghatana/yappc-platform-tools)
export * from '../../platform-tools/src';

// REMOVED: deprecated @ghatana/yappc-performance
// REMOVED: deprecated @ghatana/yappc-performance
// // // Performance monitoring (from @ghatana/yappc-performance)
export * from '../../performance/src';
