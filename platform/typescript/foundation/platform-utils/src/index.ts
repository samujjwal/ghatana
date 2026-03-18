/**
 * @ghatana/utils
 *
 * Shared utility functions for Ghatana platform
 * Consolidated from DCMAAR and YAPPC
 *
 * @package @ghatana/utils
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

/**
 * Migration notes for products:
 *
 * DCMAAR products should update imports:
 *   - @ghatana/dcmaar-shared-ui-core/utils → @ghatana/utils
 *
 * YAPPC products should update imports:
 *   - @ghatana/yappc-ui/utils → @ghatana/utils
 */
