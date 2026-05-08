/**
 * CanvasOutlinePanel Component
 *
 * Displays frames as navigation targets and a layer list for node ordering.
 *
 * @doc.type component
 * @doc.purpose Canvas outline panel with frames and layers navigation
 * @doc.layer product
 * @doc.pattern UI Component
 */

import { memo, useMemo } from 'react';
import {
  Box,
  Button,
  Typography,
} from '@ghatana/design-system';
import { CanvasOutlineMap } from '../../../../components/canvas/outline/CanvasOutlineMap';

interface CanvasNodeData {
  title?: string;
  label?: string;
  text?: string;
}

interface CanvasNode {
  id: string;
  type: string;
  position?: { x: number; y: number };
  size?: { width: number; height: number };
  data: CanvasNodeData;
}

export interface CanvasOutlinePanelProps {
  nodes: CanvasNode[];
  selectedNodeIds: string[];
  selectNodes: (ids: string[]) => void;
  addNodeAtPosition: (type: string, position: { x: number; y: number }) => void;
  getViewport: () => { x: number; y: number; zoom: number };
}

export const CanvasOutlinePanel = memo(function CanvasOutlinePanel({
  nodes,
  selectedNodeIds,
  selectNodes,
  addNodeAtPosition,
  getViewport,
}: CanvasOutlinePanelProps) {
  const frames = useMemo(
    () => nodes.filter((n) => n.type === 'frame'),
    [nodes]
  );

  return (
    <Box className="p-4 h-full overflow-auto">
      <Box className="mb-4">
        <CanvasOutlineMap
          nodes={nodes}
          selectedNodeIds={selectedNodeIds}
          onSelectNode={(nodeId: string) => selectNodes([nodeId])}
        />
      </Box>

      <Box className="font-semibold mb-4">📋 Frames ({frames.length})</Box>
      {frames.length === 0 ? (
        <Typography variant="body2" color="text.secondary">
          No frames yet. Press F to create one.
        </Typography>
      ) : (
        frames.map((frame) => (
          <Box
            key={frame.id}
            onClick={() => selectNodes([frame.id])}
            className={`p-2 cursor-pointer rounded mb-2 hover:bg-surface-muted hover:dark:bg-surface ${selectedNodeIds.includes(frame.id) ? 'bg-info-bg dark:bg-info-bg/20' : ''}`}
          >
            {frame.data.title || 'Frame'}
          </Box>
        ))
      )}
      <Button
        size="small"
        fullWidth
        className="mt-4"
        onClick={() => {
          const center = getViewport();
          addNodeAtPosition('frame', {
            x: -center.x / center.zoom,
            y: -center.y / center.zoom,
          });
        }}
      >
        + Frame
      </Button>

      {/* Layers Section */}
      <Box className="font-semibold mb-4 mt-8">
        🗂️ Layers ({nodes.length})
      </Box>
      <Box className="max-h-[300px] overflow-auto">
        {nodes
          .slice()
          .reverse()
          .map((node) => (
            <Box
              key={node.id}
              onClick={() => selectNodes([node.id])}
              className={`mb-2 cursor-pointer rounded p-2 ${selectedNodeIds.includes(node.id) ? 'bg-info-bg dark:bg-info-bg/20' : 'hover:bg-surface-muted dark:hover:bg-surface'}`}
            >
              <Typography variant="body2" className="truncate">
                {node.data.label ||
                  node.data.title ||
                  node.data.text?.substring(0, 20) ||
                  node.type}
              </Typography>
              <Typography variant="caption" color="text.secondary">
                {node.type}
              </Typography>
            </Box>
          ))}
      </Box>
    </Box>
  );
});
