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
  Menu,
  Divider,
  Typography,
  Box,
  ListItemIcon,
  ListItemText,
} from '@ghatana/ui';
import { MenuItem } from '@ghatana/ui';
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
  return (
    <Menu
      open={!!nodeContextMenu}
      onClose={onClose}
      anchorReference="anchorPosition"
      anchorPosition={
        nodeContextMenu
          ? { top: nodeContextMenu.y, left: nodeContextMenu.x }
          : undefined
      }
    >
      <MenuItem
        onClick={() => {
          if (nodeContextMenu?.nodeId)
            canvas.duplicateNode(nodeContextMenu.nodeId);
          onClose();
        }}
      >
        <ListItemIcon>
          <ContentCopy size={16} />
        </ListItemIcon>
        <ListItemText>Duplicate</ListItemText>
        <Typography variant="caption" color="text.secondary" className="ml-4">
          ⌘⇧D
        </Typography>
      </MenuItem>
      <MenuItem
        onClick={() => {
          if (nodeContextMenu?.nodeId)
            canvas.removeNode(nodeContextMenu.nodeId);
          onClose();
        }}
        className="text-red-600"
      >
        <ListItemIcon>
          <Delete size={16} className="text-red-500" />
        </ListItemIcon>
        <ListItemText>Delete</ListItemText>
        <Typography variant="caption" color="text.secondary" className="ml-4">
          Del
        </Typography>
      </MenuItem>

      <Divider />

      {/* Designer Persona Actions */}
      {currentMode === 'design' && (
        <Box>
          <MenuItem
            onClick={() => {
              if (nodeContextMenu?.nodeId)
                canvas.bringForward(nodeContextMenu.nodeId);
              onClose();
            }}
          >
            <ListItemIcon>
              <ArrowUpward size={16} />
            </ListItemIcon>
            <ListItemText>Bring Forward</ListItemText>
            <Typography variant="caption" color="text.secondary" className="ml-4">
              ]
            </Typography>
          </MenuItem>
          <MenuItem
            onClick={() => {
              if (nodeContextMenu?.nodeId)
                canvas.sendBackward(nodeContextMenu.nodeId);
              onClose();
            }}
          >
            <ListItemIcon>
              <ArrowDownward size={16} />
            </ListItemIcon>
            <ListItemText>Send Backward</ListItemText>
            <Typography variant="caption" color="text.secondary" className="ml-4">
              [
            </Typography>
          </MenuItem>
        </Box>
      )}

      {/* Developer Persona Actions */}
      {currentMode === 'code' && (
        <Box>
          <MenuItem
            onClick={() => {
              if (nodeContextMenu?.nodeId)
                canvas.selectNodes([nodeContextMenu.nodeId]);
              if (!codePanelVisible) toggleCodePanel();
              onClose();
            }}
          >
            Edit Code
          </MenuItem>
          <MenuItem onClick={onClose}>Validate Logic</MenuItem>
        </Box>
      )}

      {/* PM Persona Actions */}
      {currentMode === 'plan' && (
        <MenuItem onClick={onClose}>Add Comment</MenuItem>
      )}
    </Menu>
  );
}
