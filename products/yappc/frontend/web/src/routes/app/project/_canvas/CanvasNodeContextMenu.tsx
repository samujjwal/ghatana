/**
 * CanvasNodeContextMenu Component
 *
 * Context menu shown on right-clicking a canvas node.
 * Provides duplicate, delete, layer, code, and comment actions
 * based on the current persona mode.
 *
 * @doc.type component
 * @doc.purpose Node right-click context menu
 * @doc.layer product
 * @doc.pattern UI Component
 */

import { useCallback } from 'react';
import {
  Button,
  Divider,
  Typography,
  Box,
} from '@ghatana/design-system';
import {
  Copy as ContentCopy,
  Trash2 as Delete,
  ArrowUp as ArrowUpward,
  ArrowDown as ArrowDownward,
} from 'lucide-react';
import type { NodeContextMenuState } from './types';

interface CanvasNodeContextMenuProps {
  nodeContextMenu: NodeContextMenuState | null;
  onClose: () => void;
  canvas: {
    duplicateNode: (id: string) => void;
    removeNode: (id: string) => void;
    bringForward: (id: string) => void;
    sendBackward: (id: string) => void;
    selectNodes: (ids: string[]) => void;
  };
  currentMode: string;
  codePanelVisible: boolean;
  toggleCodePanel: () => void;
}

export function CanvasNodeContextMenu({
  nodeContextMenu,
  onClose,
  canvas,
  currentMode,
  codePanelVisible,
  toggleCodePanel,
}: CanvasNodeContextMenuProps) {
  const withNode = useCallback(
    (action: (nodeId: string) => void) => {
      if (nodeContextMenu?.nodeId) {
        action(nodeContextMenu.nodeId);
      }
      onClose();
    },
    [nodeContextMenu, onClose]
  );

  if (!nodeContextMenu) return null;

  return (
    <Box
      className="absolute z-50 min-w-[220px] rounded-md border border-gray-200 bg-white p-1 shadow-lg dark:border-gray-700 dark:bg-gray-900"
      style={{ top: nodeContextMenu.y, left: nodeContextMenu.x }}
    >
      <Button
        variant="ghost"
        className="flex w-full items-center justify-between"
        onClick={() => withNode((id) => canvas.duplicateNode(id))}
      >
        <Box className="flex items-center gap-2">
          <ContentCopy size={16} />
          <span>Duplicate</span>
        </Box>
        <Typography variant="caption" color="text.secondary">
          ⌘⇧D
        </Typography>
      </Button>
      <Button
        variant="ghost"
        className="flex w-full items-center justify-between text-red-600"
        onClick={() => withNode((id) => canvas.removeNode(id))}
      >
        <Box className="flex items-center gap-2">
          <Delete size={16} className="text-red-500" />
          <span>Delete</span>
        </Box>
        <Typography variant="caption" color="text.secondary">
          Del
        </Typography>
      </Button>

      <Divider />

      {/* Designer Persona Actions */}
      {currentMode === 'design' && (
        <Box>
          <Button
            variant="ghost"
            className="flex w-full items-center justify-between"
            onClick={() => withNode((id) => canvas.bringForward(id))}
          >
            <Box className="flex items-center gap-2">
              <ArrowUpward size={16} />
              <span>Bring Forward</span>
            </Box>
            <Typography variant="caption" color="text.secondary">
              ]
            </Typography>
          </Button>
          <Button
            variant="ghost"
            className="flex w-full items-center justify-between"
            onClick={() => withNode((id) => canvas.sendBackward(id))}
          >
            <Box className="flex items-center gap-2">
              <ArrowDownward size={16} />
              <span>Send Backward</span>
            </Box>
            <Typography variant="caption" color="text.secondary">
              [
            </Typography>
          </Button>
        </Box>
      )}

      {/* Developer Persona Actions */}
      {currentMode === 'code' && (
        <Box>
          <Button
            variant="ghost"
            className="w-full justify-start"
            onClick={() => {
              if (nodeContextMenu?.nodeId)
                canvas.selectNodes([nodeContextMenu.nodeId]);
              if (!codePanelVisible) toggleCodePanel();
              onClose();
            }}
          >
            Edit Code
          </Button>
          <Button variant="ghost" className="w-full justify-start" onClick={onClose}>
            Validate Logic
          </Button>
        </Box>
      )}

      {/* PM Persona Actions */}
      {currentMode === 'plan' && (
        <Button variant="ghost" className="w-full justify-start" onClick={onClose}>
          Add Comment
        </Button>
      )}
    </Box>
  );
}
