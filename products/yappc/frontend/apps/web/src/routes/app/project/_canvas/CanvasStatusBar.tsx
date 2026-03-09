/**
 * CanvasStatusBar Component
 *
 * Bottom status bar showing current tool, selection count, phase, and zoom level.
 * Automatically hides in calm mode.
 *
 * @doc.type component
 * @doc.purpose Canvas status bar with contextual info
 * @doc.layer product
 * @doc.pattern UI Component
 */

import { Box } from '@ghatana/ui';
import type { DrawingTool } from './types';

interface CanvasStatusBarProps {
  calmMode: boolean;
  activeTool: string;
  drawingTool: DrawingTool;
  drawingColor: string;
  hasSelection: boolean;
  selectedCount: number;
  currentPhase: string;
  zoom: number;
}

export function CanvasStatusBar({
  calmMode,
  activeTool,
  drawingTool,
  drawingColor,
  hasSelection,
  selectedCount,
  currentPhase,
  zoom,
}: CanvasStatusBarProps) {
  return (
    <Box
      className="flex justify-between items-center px-4 bg-white dark:bg-gray-900 border-t border-solid text-xs text-gray-500 dark:text-gray-400 overflow-hidden transition-all duration-300" style={{ paddingTop: calmMode ? 0 : 4, paddingBottom: calmMode ? 0 : 4, borderColor: calmMode ? 'transparent' : 'rgba(0,0,0,0.12)', maxHeight: calmMode ? 0 : 40, opacity: calmMode ? 0 : 1, pointerEvents: calmMode ? 'none' : 'auto' }}
    >
      <Box className="flex gap-4">
        {activeTool === 'draw' ? (
          <>
            <span>Drawing: {drawingTool}</span>
            <span>Color: {drawingColor}</span>
          </>
        ) : hasSelection ? (
          <span>{selectedCount} selected</span>
        ) : (
          <span>{currentPhase} Phase</span>
        )}
      </Box>
      <Box className="flex gap-4">
        <span>Zoom: {Math.round(zoom * 100)}%</span>
      </Box>
    </Box>
  );
}
