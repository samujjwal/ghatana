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

import { useMemo } from 'react';
import {
  Box,
  Button,
  Typography,
  ListItem,
  ListItemText,
  InteractiveList as List,
} from '@ghatana/ui';

interface CanvasOutlinePanelProps {
  nodes: unknown[];
  selectedNodeIds: string[];
  selectNodes: (ids: string[]) => void;
  addNodeAtPosition: (type: string, position: { x: number; y: number }) => void;
  getViewport: () => { x: number; y: number; zoom: number };
}

export function CanvasOutlinePanel({
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
            className={`p-2 cursor-pointer rounded mb-2 hover:bg-gray-100 hover:dark:bg-gray-800 ${selectedNodeIds.includes(frame.id) ? 'bg-blue-50 dark:bg-blue-900/20' : ''}`}
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
      <List dense className="overflow-auto max-h-[300px]">
        {nodes
          .slice()
          .reverse()
          .map((node) => (
            <ListItem
              key={node.id}
              dense
              button
              selected={selectedNodeIds.includes(node.id)}
              onClick={() => selectNodes([node.id])}
              className="rounded"
            >
              <ListItemText
                primary={
                  node.data.label ||
                  node.data.title ||
                  node.data.text?.substring(0, 20) ||
                  node.type
                }
                secondary={node.type}
                primaryTypographyProps={{ noWrap: true, variant: 'body2' }}
                secondaryTypographyProps={{ variant: 'caption' }}
              />
            </ListItem>
          ))}
      </List>
    </Box>
  );
}
