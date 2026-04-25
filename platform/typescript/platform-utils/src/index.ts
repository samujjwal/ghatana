/**
 * @ghatana/platform-utils
 *
 * Shared utility functions for Ghatana platform
 * Consolidated from DCMAAR and YAPPC
 *
 * @package @ghatana/platform-utils
 * @version 0.1.0
 */

// Export all formatters
export * from './formatters';

// Export class name utility
export * from './cn';

// Export platform detection
export * from './platform';

// Export responsive utilities
export * from './responsive';

// Export accessibility utilities
export * from './accessibility';

// Export result type for explicit error handling
export * from './result';

/**
 * Migration notes for products:
 *
 * Legacy products should update imports to use canonical package names:
 *   - Old shared UI utils → @ghatana/foundation
 *   - Platform-specific utils → @ghatana/foundation
 *
 * Canonical package name: @ghatana/foundation
 */
