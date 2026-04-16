/**
 * Mode-specific Canvas Renderers
 * 
 * @doc.type module
 * @doc.purpose Export mode-specific canvas renderers for the 7 canvas modes
 * @doc.layer product
 * @doc.pattern ModuleExports
 * 
 * Each mode renderer is designed to provide specialized content and tools
 * based on the current abstraction level (system → component → file → code).
 * 
 * Mode×Level Matrix (28 states):
 * - brainstorm × {system, component, file, code}
 * - diagram × {system, component, file, code}
 * - design × {system, component, file, code}
 * - code × {system, component, file, code}
 * - test × {system, component, file, code}
 * - deploy × {system, component, file, code}
 * - observe × {system, component, file, code}
 */

// Individual Mode Renderers
export { BrainstormModeRenderer } from './BrainstormModeRenderer';
export { DiagramModeRenderer } from './DiagramModeRenderer';
export { DesignModeRenderer } from './DesignModeRenderer';
export { CodeModeRenderer } from './CodeModeRenderer';
export { TestModeRenderer } from './TestModeRenderer';
export { DeployModeRenderer } from './DeployModeRenderer';
export { ObserveModeRenderer } from './ObserveModeRenderer';

// Mode Selector (dynamic renderer based on current mode)
export { ModeContentRenderer } from './ModeContentRenderer';

// Types
export type { ModeRendererProps, ModeContentProps } from './types';
