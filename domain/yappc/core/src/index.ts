/**
 * @yappc/core
 *
 * Core library for YAPPC - types, utilities, API clients, and configuration.
 * Consolidates: @ghatana/yappc-types, @ghatana/yappc-utils, @ghatana/yappc-api, @ghatana/yappc-config
 *
 * @package @yappc/core
 */

// Re-export all from consolidated packages (relative paths to avoid circular refs)
export * from './types';
export * from './utils';
export * from './config';
