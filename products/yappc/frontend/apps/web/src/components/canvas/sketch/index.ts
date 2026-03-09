/**
 * Sketch Components - Re-exports from @ghatana/yappc-sketch + App-specific integrations
 *
 * This module provides:
 * - Re-exports from @ghatana/yappc-sketch library for convenience
 * - App-specific EnhancedSketchLayer with Jotai state integration
 * - App-specific hooks that integrate with canvas atoms
 *
 * @example
 * ```tsx
 * // Import from shared library (preferred)
 * import { SketchToolbar, StickyNote } from '@ghatana/yappc-canvas/sketch';
 *
 * // Import app-specific integration
 * import { EnhancedSketchLayer } from './components/canvas/sketch';
 * ```
 */

// Re-export everything from shared library
export * from '@ghatana/yappc-canvas/sketch';

// App-specific components and hooks
export { EnhancedSketchLayer } from './EnhancedSketchLayer';
export { useSketchTools } from './useSketchTools';
export { useSketchKeyboard } from './useSketchKeyboard';
