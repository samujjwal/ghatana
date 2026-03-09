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
export * from './features/feature-flags';

// Result types
export * from './results/result';

// ============================================================================
// REMOVED: deprecated @ghatana/yappc-storage
// REMOVED: deprecated @ghatana/yappc-storage
// // // CONSOLIDATED: Storage (from @ghatana/yappc-storage)
// ============================================================================
export * from '../../storage/src';
