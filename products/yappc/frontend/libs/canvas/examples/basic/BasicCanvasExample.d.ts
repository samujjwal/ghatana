/**
 * Basic Canvas Example
 *
 * Demonstrates fundamental Canvas library usage including:
 * - Component setup and initialization
 * - Basic element management
 * - State management with Canvas atoms
 * - Theme integration
 * - Event handling
 *
 * This example is intended for development and documentation purposes only.
 * Do not import in production builds.
 *
 * @example
 * ```tsx
 * // Development-only usage
 * if (process.env.NODE_ENV === 'development') {
 *   const { BasicCanvasExample } = await import('@ghatana/yappc-canvas/examples/basic');
 *   <BasicCanvasExample onElementAdd={handleAdd} />
 * }
 * ```
 */
import React from 'react';
import { type CanvasNode, type CanvasEdge, type CanvasTheme } from '../../src';
export interface BasicCanvasExampleProps {
    /** Called when an element is added */
    onElementAdd?: (element: CanvasNode | CanvasEdge) => void;
    /** Called when an element is updated */
    onElementUpdate?: (elementId: string, updates: unknown) => void;
    /** Optional theme override */
    theme?: Partial<CanvasTheme>;
    /** Width of the canvas */
    width?: number;
    /** Height of the canvas */
    height?: number;
}
/**
 * Basic Canvas Example Component
 *
 * Provides a minimal working Canvas with example elements
 * and basic interaction capabilities.
 */
export declare const BasicCanvasExample: React.FC<BasicCanvasExampleProps>;
export default BasicCanvasExample;
//# sourceMappingURL=BasicCanvasExample.d.ts.map