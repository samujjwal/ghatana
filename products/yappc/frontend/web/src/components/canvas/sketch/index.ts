/**
 * Sketch Components - Integrated canvas sketch module
 *
 * This module provides:
 * - App-specific EnhancedSketchLayer with Jotai state integration
 * - App-specific hooks and types used by canvas sketch mode
 *
 * @example
 * ```tsx
 * // Import app-specific integration
 * import { EnhancedSketchLayer } from './components/canvas/sketch';
 * ```
 */

export * from './types';
export * from './smoothStroke';

// App-specific components and hooks
export { EnhancedSketchLayer } from './EnhancedSketchLayer';
export { useSketchTools } from './useSketchTools';
export { useSketchKeyboard } from './useSketchKeyboard';
