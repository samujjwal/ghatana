/**
 * Canvas Node Renderer
 *
 * Live React component rendering from canvas node data with theme support.
 *
 * @module canvas/renderer
 */

// Core renderer
export { NodeRenderer, EditModeWrapper, BatchNodeRenderer } from './NodeRenderer';
export type {
  NodeRendererProps,
  EditModeWrapperProps,
  BatchNodeRendererProps,
} from './NodeRenderer';

// Theme system
export { ThemeApplicator } from './ThemeApplicator';
export type {
  ThemeLayer,
  TokenRegistry,
  ResolvedProps,
  ThemeContext,
} from './ThemeApplicator';

// Component registry
export { RendererComponentRegistry, useRegisteredComponent } from './ComponentRegistry';
export type { ComponentType, RegisteredComponent } from './ComponentRegistry';

// Component registration
export { registerAllComponents } from './registerComponents';
