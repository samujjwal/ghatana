/**
 * YAPPC Canvas Module
 * 
 * YAPPC-specific canvas implementation using the generic canvas system.
 * This module contains all YAPPC-specific configurations, actions, and components.
 * 
 * @doc.type yappc
 * @doc.purpose YAPPC canvas module exports
 * @doc.layer application
 */

// Configuration
export * from './yappc-config';

// Actions
export * from './yappc-actions';

// Main initialization
export * from './yappc-canvas';

/**
 * Usage Example:
 * 
 * ```typescript
 * import { initializeYAPPCCanvas, IntegratedCanvasChrome } from '@ghatana/canvas/yappc';
 * 
 * // Initialize YAPPC canvas
 * initializeYAPPCCanvas();
 * 
 * // Use the canvas
 * function App() {
 *   return (
 *     <IntegratedCanvasChrome projectName="My YAPPC Project">
 *       <YourCanvasContent />
 *     </IntegratedCanvasChrome>
 *   );
 * }
 * ```
 */
