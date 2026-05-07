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

import { Box } from '@ghatana/design-system';
import {
  SaveSyncStatusBadge,
} from '../../../../components/status/SaveSyncStatusBadge';
import type { DrawingTool } from './types';
import type { SaveSyncStatusContract } from '@/contracts/workspace-project';
import { CANVAS_SYNC_STATUS_LABELS } from '@/services/canvas/canvasSyncStatus';

interface CanvasStatusBarProps {
  calmMode: boolean;
  activeTool: string;
  drawingTool: DrawingTool;
  drawingColor: string;
  hasSelection: boolean;
  selectedCount: number;
  currentPhase: string;
  zoom: number;
  syncStatus: SaveSyncStatusContract;
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
  syncStatus,
}: CanvasStatusBarProps) {
  return (
    <Box
      className="flex justify-between items-center px-4 bg-white dark:bg-surface border-t border-solid text-xs text-fg-muted dark:text-fg-muted overflow-hidden transition-all duration-300" style={{ paddingTop: calmMode ? 0 : 4, paddingBottom: calmMode ? 0 : 4, borderColor: calmMode ? 'transparent' : 'rgba(0,0,0,0.12)', maxHeight: calmMode ? 0 : 40, opacity: calmMode ? 0 : 1, pointerEvents: calmMode ? 'none' : 'auto' }}
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
        <SaveSyncStatusBadge
          className="border-none bg-transparent px-0 py-0 text-xs"
          data-testid="canvas-sync-status"
          labels={CANVAS_SYNC_STATUS_LABELS}
          status={syncStatus}
        />
        <span>Zoom: {Math.round(zoom * 100)}%</span>
      </Box>
    </Box>
  );
}
