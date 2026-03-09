// Core UI components from @ghatana/yappc-ui
import { IconButton, Stack } from '@ghatana/ui';

// MUI components not available in @ghatana/yappc-ui
import { Tooltip } from '@ghatana/ui';

import { Undo2 as Undo, Redo2 as Redo } from 'lucide-react';
import React from 'react';

import { useCanvasHistory } from './hooks/useCanvasHistory';

/**
 * Props for HistoryToolbar component.
 *
 * Controls toolbar appearance and canvas context for undo/redo operations.
 *
 * @doc.type interface
 * @doc.purpose Component props definition
 * @doc.layer presentation
 */
export interface HistoryToolbarProps {
  projectId?: string;
  canvasId?: string;
  size?: 'small' | 'medium' | 'large';
  orientation?: 'horizontal' | 'vertical';
}

/**
 * History toolbar for undo/redo operations.
 *
 * Provides undo and redo buttons with keyboard shortcut support.
 * Integrates with canvas history management to track and replay changes.
 *
 * Features:
 * - Undo/redo button controls
 * - Keyboard shortcut support (Ctrl+Z, Ctrl+Y)
 * - Visual feedback for available actions
 * - Configurable size and orientation
 * - History state tracking
 *
 * @param props - Component properties
 * @returns Rendered history toolbar
 *
 * @doc.type component
 * @doc.purpose Canvas history management UI
 * @doc.layer presentation
 * @doc.pattern Command
 *
 * @example
 * ```tsx
 * <HistoryToolbar
 *   projectId="proj-123"
 *   canvasId="canvas-456"
 *   size="md"
 *   orientation="horizontal"
 * />
 * ```
 */
export const HistoryToolbar: React.FC<HistoryToolbarProps> = ({
  projectId,
  canvasId,
  size = 'medium',
  orientation = 'horizontal',
}) => {
  const { canUndo, canRedo, undo, redo, historySize } = useCanvasHistory(
    projectId,
    canvasId
  );

  const handleUndo = () => {
    undo();
  };

  const handleRedo = () => {
    redo();
  };

  const iconSize = size === 'small' ? 16 : size === 'large' ? 24 : 20;

  return (
    <Stack
      direction={orientation === 'horizontal' ? 'row' : 'column'}
      spacing={0.5}
      className={
        size === 'small'
          ? '[&_.MuiIconButton-root]:w-8 [&_.MuiIconButton-root]:h-8'
          : size === 'large'
          ? '[&_.MuiIconButton-root]:w-12 [&_.MuiIconButton-root]:h-12'
          : '[&_.MuiIconButton-root]:w-10 [&_.MuiIconButton-root]:h-10'
      }
    >
      <Tooltip title={`Undo (${historySize} operations available)`}>
        <span>
          <IconButton
            onClick={handleUndo}
            disabled={!canUndo}
            size={size}
            aria-label="Undo last action"
          >
            <Undo style={{ fontSize: iconSize }} />
          </IconButton>
        </span>
      </Tooltip>

      <Tooltip title="Redo">
        <span>
          <IconButton
            onClick={handleRedo}
            disabled={!canRedo}
            size={size}
            aria-label="Redo last undone action"
          >
            <Redo style={{ fontSize: iconSize }} />
          </IconButton>
        </span>
      </Tooltip>
    </Stack>
  );
};

export default HistoryToolbar;