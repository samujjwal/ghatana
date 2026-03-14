/**
 * FlowControls — zoom, fit-view, and minimap controls for FlowCanvas.
 *
 * A composable control panel that wraps @xyflow/react Panel + Controls
 * with Ghatana platform styling and keyboard shortcuts.
 *
 * @doc.type component
 * @doc.purpose Zoom/pan/fit-view controls for FlowCanvas
 * @doc.layer shared
 * @doc.pattern PresentationalComponent
 */
import React, { memo } from 'react';
import { Controls, MiniMap, Panel, useReactFlow } from '@xyflow/react';

export interface FlowControlsProps {
  /** Show the minimap panel. Default: true. */
  showMiniMap?: boolean;
  /** Show the zoom/fit controls. Default: true. */
  showControls?: boolean;
  /** Position of the controls panel. Default: 'bottom-left'. */
  controlsPosition?: 'top-left' | 'top-right' | 'bottom-left' | 'bottom-right';
}

/**
 * Pre-configured controls block for FlowCanvas.
 *
 * Includes zoom in/out/fit-view buttons and an optional minimap.
 */
const FlowControls = memo(
  ({
    showMiniMap = true,
    showControls = true,
    controlsPosition = 'bottom-left',
  }: FlowControlsProps) => {
    const { fitView } = useReactFlow();

    return (
      <>
        {showControls && (
          <Controls
            position={controlsPosition}
            showFitView
            showZoom
            onFitView={() => fitView({ padding: 0.1 })}
            aria-label="Canvas controls"
          />
        )}
        {showMiniMap && (
          <MiniMap
            position="bottom-right"
            zoomable
            pannable
            aria-label="Canvas minimap"
            nodeColor={(node) => {
              switch (node.type) {
                case 'hotTier':     return '#ef4444';
                case 'warmTier':    return '#f59e0b';
                case 'coldTier':    return '#3b82f6';
                case 'archiveTier': return '#64748b';
                case 'agent':       return '#6366f1';
                default:            return '#94a3b8';
              }
            }}
          />
        )}
      </>
    );
  },
);

FlowControls.displayName = 'FlowControls';
export default FlowControls;
