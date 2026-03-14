/**
 * @ghatana/ui-integration
 *
 * UI Integration layer for the Ghatana platform.
 * Provides AI features, collaboration, and page builder capabilities.
 *
 * @package @ghatana/ui-integration
 */

// AI Features - use namespace export to avoid conflicts
export * as AI from './integration/aiFeatures';

// Collaboration
export * from './collaboration';

// Page Builder
export * as PageBuilder from './integration/pageBuilder';
