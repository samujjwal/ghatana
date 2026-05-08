/**
 * Canvas outline minimap.
 *
 * @doc.type component
 * @doc.purpose Compact accessible overview for outline, layer, and preview sync surfaces
 * @doc.layer components
 */

import { useMemo } from 'react';
import { Box, Typography } from '@ghatana/design-system';

import {
  createCanvasOutlineMinimapSummary,
  type CanvasOutlineNodeInput,
} from './canvasOutlineMinimap';

export interface CanvasOutlineMapProps {
  readonly nodes: readonly CanvasOutlineNodeInput[];
  readonly selectedNodeIds: readonly string[];
  readonly hoveredNodeId?: string | null;
  readonly onSelectNode?: (nodeId: string) => void;
}

export function CanvasOutlineMap({
  nodes,
  selectedNodeIds,
  hoveredNodeId = null,
  onSelectNode,
}: CanvasOutlineMapProps) {
  const summary = useMemo(
    () => createCanvasOutlineMinimapSummary(nodes, selectedNodeIds, hoveredNodeId),
    [hoveredNodeId, nodes, selectedNodeIds],
  );

  return (
    <Box
      className="rounded-lg border border-border bg-surface p-3"
      data-testid="canvas-outline-minimap"
    >
      <Box className="mb-2 flex items-center justify-between gap-2">
        <Typography variant="caption" className="font-semibold text-fg">
          Outline map
        </Typography>
        <Typography variant="caption" color="text.secondary">
          {summary.nodeCount} nodes
        </Typography>
      </Box>

      <Box
        role="img"
        aria-label={`Canvas outline map with ${summary.nodeCount} nodes, ${summary.selectedCount} selected, ${summary.hiddenCount} hidden, and ${summary.lockedCount} locked.`}
        className="relative h-28 overflow-hidden rounded-md border border-border bg-surface-muted"
      >
        {summary.markers.length === 0 ? (
          <Box className="flex h-full items-center justify-center px-3 text-center">
            <Typography variant="caption" color="text.secondary">
              Add positioned nodes to populate the outline map.
            </Typography>
          </Box>
        ) : (
          summary.markers.map((marker) => (
            <Box
              key={marker.id}
              role={onSelectNode ? 'button' : 'presentation'}
              tabIndex={onSelectNode ? 0 : undefined}
              aria-label={`${marker.label} ${marker.type}${marker.selected ? ', selected' : ''}${marker.hovered ? ', preview hovered' : ''}${marker.hidden ? ', hidden' : ''}${marker.locked ? ', locked' : ''}`}
              title={`${marker.label} (${marker.type})`}
              data-testid={`canvas-outline-minimap-marker-${marker.id}`}
              data-selected={marker.selected ? 'true' : undefined}
              data-preview-hovered={marker.hovered ? 'true' : undefined}
              className={[
                'absolute rounded-sm border transition',
                marker.selected ? 'border-info-border bg-info-bg shadow-sm' : 'border-border bg-white',
                marker.hovered ? 'ring-2 ring-info-border' : '',
                marker.hidden ? 'opacity-40' : '',
                marker.locked ? 'border-warning-border' : '',
                onSelectNode ? 'cursor-pointer focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-info-border' : '',
              ].filter(Boolean).join(' ')}
              style={{
                left: `${marker.leftPercent}%`,
                top: `${marker.topPercent}%`,
                width: `${marker.widthPercent}%`,
                height: `${marker.heightPercent}%`,
              }}
              onClick={() => onSelectNode?.(marker.id)}
              onKeyDown={(event) => {
                if (!onSelectNode) {
                  return;
                }

                if (event.key === 'Enter' || event.key === ' ') {
                  event.preventDefault();
                  onSelectNode(marker.id);
                }
              }}
            />
          ))
        )}
      </Box>

      <Box className="mt-2 flex flex-wrap gap-2">
        <Typography variant="caption" color="text.secondary">
          Selected {summary.selectedCount}
        </Typography>
        <Typography variant="caption" color="text.secondary">
          Hidden {summary.hiddenCount}
        </Typography>
        <Typography variant="caption" color="text.secondary">
          Locked {summary.lockedCount}
        </Typography>
      </Box>
    </Box>
  );
}
