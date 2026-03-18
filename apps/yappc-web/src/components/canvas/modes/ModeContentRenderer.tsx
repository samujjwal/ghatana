/**
 * Mode Content Renderer - Unified Mode Switching Component
 * 
 * @doc.type class
 * @doc.purpose Provides unified mode switching for the canvas based on current mode
 * @doc.layer product
 * @doc.pattern FacadePattern
 * 
 * This component acts as a facade that selects the appropriate mode-specific
 * renderer based on the current canvas mode. It simplifies integration into
 * CanvasScene by providing a single component to handle all 7 modes.
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

import React, { useMemo } from 'react';

import { BrainstormModeRenderer } from './BrainstormModeRenderer';
import { CodeModeRenderer } from './CodeModeRenderer';
import { DeployModeRenderer } from './DeployModeRenderer';
import { DesignModeRenderer } from './DesignModeRenderer';
import { DiagramModeRenderer } from './DiagramModeRenderer';
import { ObserveModeRenderer } from './ObserveModeRenderer';
import { TestModeRenderer } from './TestModeRenderer';

import type { ModeContentProps, ModeRendererProps } from './types';

// Mode to Renderer mapping
const MODE_RENDERERS: Record<string, React.ComponentType<ModeRendererProps>> = {
  brainstorm: BrainstormModeRenderer,
  diagram: DiagramModeRenderer,
  design: DesignModeRenderer,
  code: CodeModeRenderer,
  test: TestModeRenderer,
  deploy: DeployModeRenderer,
  observe: ObserveModeRenderer,
};

/**
 * Mode Content Renderer
 * 
 * Automatically selects the appropriate mode-specific renderer based on
 * the current canvas mode. Falls back to DiagramModeRenderer if the mode
 * is not recognized.
 * 
 * @example
 * ```tsx
 * <ModeContentRenderer
 *   mode={currentMode}
 *   level={abstractionLevel}
 *   projectId={projectId}
 *   hasContent={nodes.length > 0}
 *   onAskAI={handleAskAI}
 *   onGetStarted={handleGetStarted}
 * >
 *   <ReactFlowWrapper ... />
 * </ModeContentRenderer>
 * ```
 */
export const ModeContentRenderer: React.FC<ModeContentProps> = ({
  mode,
  level,
  projectId,
  canvasId,
  hasContent,
  onAskAI,
  onGetStarted,
  onDrillDown,
  onZoomOut,
  children,
  readOnly = false,
}) => {
  // Get the appropriate renderer for the current mode
  const ModeRenderer = useMemo(() => {
    return MODE_RENDERERS[mode] ?? DiagramModeRenderer;
  }, [mode]);

  // Render the mode-specific content
  return (
    <ModeRenderer
      level={level}
      projectId={projectId}
      canvasId={canvasId}
      hasContent={hasContent}
      onAskAI={onAskAI}
      onGetStarted={onGetStarted}
      onDrillDown={onDrillDown}
      onZoomOut={onZoomOut}
      readOnly={readOnly}
    >
      {children}
    </ModeRenderer>
  );
};

export default ModeContentRenderer;
