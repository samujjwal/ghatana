/**
 * @doc.type component
 * @doc.purpose Layers panel for managing z-order and visibility
 * @doc.layer core
 * @doc.pattern Panel Component
 */

import { Eye as VisibleIcon, EyeOff as HiddenIcon, Lock as LockIcon, LockOpen as UnlockIcon, GripVertical as DragIcon, ChevronDown as ExpandIcon, ChevronRight as CollapseIcon, StickyNote as StickyIcon, NotebookText as TextIcon, Square as ShapeIcon, ArrowRight as ConnectorIcon } from 'lucide-react';
import { Box, Typography, InteractiveList as List, ListItem, ListItemButton, ListItemText, IconButton, Collapse, Tooltip } from '@ghatana/ui';
import React, { useMemo, useState } from 'react';

import { CANVAS_TOKENS } from '../tokens/canvas-tokens';

import type { Node } from '@xyflow/react';

/**
 *
 */
export interface LayersPanelProps {
  /** All nodes on canvas */
  nodes: Node[];
  
  /** Selected node IDs */
  selectedNodeIds: string[];
  
  /** Callback when node selection changes */
  onSelectNode: (nodeId: string, addToSelection: boolean) => void;
  
  /** Callback when node visibility changes */
  onToggleVisibility: (nodeId: string, visible: boolean) => void;
  
  /** Callback when node lock state changes */
  onToggleLock: (nodeId: string, locked: boolean) => void;
  
  /** Callback when layer order changes */
  onReorder: (nodeId: string, newIndex: number) => void;
  
  /** Whether the panel is visible */
  visible?: boolean;
}

/**
 *
 */
interface LayerItem {
  id: string;
  type: string;
  label: string;
  visible: boolean;
  locked: boolean;
  zIndex: number;
  phase?: string;
  children?: LayerItem[];
}

/**
 * Get icon for node type
 */
function getNodeIcon(type: string): React.ReactNode {
  switch (type) {
    case 'sticky-note':
      return <StickyIcon size={16} />;
    case 'text':
      return <TextIcon size={16} />;
    case 'connector':
    case 'edge':
      return <ConnectorIcon size={16} />;
    default:
      return <ShapeIcon size={16} />;
  }
}

/**
 * Single layer row component
 */
function LayerRow({
  layer,
  selected,
  indent = 0,
  onSelect,
  onToggleVisibility,
  onToggleLock,
}: {
  layer: LayerItem;
  selected: boolean;
  indent?: number;
  onSelect: (id: string, addToSelection: boolean) => void;
  onToggleVisibility: (id: string, visible: boolean) => void;
  onToggleLock: (id: string, locked: boolean) => void;
}) {
  const [expanded, setExpanded] = useState(true);
  const hasChildren = layer.children && layer.children.length > 0;
  
  return (
    <>
      <ListItem
        disablePadding
        style={{
          paddingLeft: indent * 16,
          backgroundColor: selected ? 'action.selected' : 'transparent',
        }}
        className={selected ? '' : 'hover:bg-gray-100'}
        secondaryAction={
          <Box className="flex gap-1">
            <IconButton
              size="sm"
              onClick={(e) => {
                e.stopPropagation();
                onToggleVisibility(layer.id, !layer.visible);
              }}
            >
              {layer.visible ? (
                <VisibleIcon size={16} />
              ) : (
                <HiddenIcon size={16} />
              )}
            </IconButton>
            <IconButton
              size="sm"
              onClick={(e) => {
                e.stopPropagation();
                onToggleLock(layer.id, !layer.locked);
              }}
            >
              {layer.locked ? (
                <LockIcon size={16} />
              ) : (
                <UnlockIcon size={16} />
              )}
            </IconButton>
            <Box
              className="flex items-center cursor-grab w-[24px]"
            >
              <DragIcon size={16} style={{ color: 'action.disabled' }} />
            </Box>
          </Box>
        }
      >
        <ListItemButton
          onClick={(e) => onSelect(layer.id, e.shiftKey || e.metaKey)}
          className="pl-2 pr-24"
        >
          {hasChildren && (
            <IconButton
              size="sm"
              onClick={(e) => {
                e.stopPropagation();
                setExpanded(!expanded);
              }}
              className="mr-1"
            >
              {expanded ? (
                <ExpandIcon size={16} />
              ) : (
                <CollapseIcon size={16} />
              )}
            </IconButton>
          )}
          <Box className="mr-2 flex items-center">
            {getNodeIcon(layer.type)}
          </Box>
          <ListItemText
            primary={layer.label}
            primaryTypographyProps={{
              variant: 'body2',
              noWrap: true,
            }}
            secondary={layer.phase}
            secondaryTypographyProps={{
              variant: 'caption',
            }}
          />
        </ListItemButton>
      </ListItem>
      
      {hasChildren && (
        <Collapse in={expanded} timeout="auto">
          {layer.children!.map((child) => (
            <LayerRow
              key={child.id}
              layer={child}
              selected={selected}
              indent={indent + 1}
              onSelect={onSelect}
              onToggleVisibility={onToggleVisibility}
              onToggleLock={onToggleLock}
            />
          ))}
        </Collapse>
      )}
    </>
  );
}

/**
 * LayersPanel - Z-order and visibility management
 * 
 * Provides a hierarchical view of all canvas objects, allowing users
 * to manage visibility, lock state, and layer ordering. Essential for
 * complex canvases with many overlapping objects.
 * 
 * @example
 * ```tsx
 * <LayersPanel
 *   nodes={nodes}
 *   selectedNodeIds={selectedNodeIds}
 *   onSelectNode={(id, add) => {
 *     if (add) addToSelection(id);
 *     else setSelection([id]);
 *   }}
 *   onToggleVisibility={(id, visible) => {
 *     updateNode(id, { hidden: !visible });
 *   }}
 *   onToggleLock={(id, locked) => {
 *     updateNode(id, { locked });
 *   }}
 * />
 * ```
 */
export function LayersPanel({
  nodes,
  selectedNodeIds,
  onSelectNode,
  onToggleVisibility,
  onToggleLock,
  onReorder,
  visible = true,
}: LayersPanelProps) {
  // Convert nodes to layer items, grouped by lifecycle phase
  const layers = useMemo(() => {
    const layerMap: Record<string, LayerItem[]> = {};
    
    nodes.forEach((node) => {
      const nodeData = node.data as unknown;
      const phase = nodeData?.phase || 'ungrouped';
      
      if (!layerMap[phase]) {
        layerMap[phase] = [];
      }
      
      layerMap[phase].push({
        id: node.id,
        type: node.type || 'default',
        label: nodeData?.label || nodeData?.text || `${node.type} ${node.id.slice(0, 8)}`,
        visible: !node.hidden,
        locked: !node.draggable,
        zIndex: node.zIndex || 0,
        phase,
      });
    });
    
    // Sort by z-index (highest first)
    Object.values(layerMap).forEach((layers) => {
      layers.sort((a, b) => b.zIndex - a.zIndex);
    });
    
    // Create hierarchy
    const result: LayerItem[] = [];
    
    // Group by phase if phases exist
    if (layerMap.ungrouped && Object.keys(layerMap).length === 1) {
      // No phases, flat list
      return layerMap.ungrouped;
    }
    
    // Group by phase
    CANVAS_TOKENS.LIFECYCLE_PHASES.forEach((phase) => {
      const phaseLayers = layerMap[phase.id];
      if (phaseLayers && phaseLayers.length > 0) {
        result.push({
          id: `phase-${phase.id}`,
          type: 'group',
          label: phase.title,
          visible: true,
          locked: false,
          zIndex: 0,
          phase: phase.id,
          children: phaseLayers,
        });
      }
    });
    
    // Add ungrouped at end
    if (layerMap.ungrouped) {
      result.push({
        id: 'phase-ungrouped',
        type: 'group',
        label: 'Ungrouped',
        visible: true,
        locked: false,
        zIndex: 0,
        children: layerMap.ungrouped,
      });
    }
    
    return result;
  }, [nodes]);
  
  if (!visible) {
    return null;
  }
  
  return (
    <Box
      className="h-full flex flex-col"
    >
      {/* Header */}
      <Box
        className="border-gray-200 dark:border-gray-700 border-b border-solid" style={{ padding: CANVAS_TOKENS.SPACING.MD }} >
        <Typography as="h6">Layers</Typography>
        <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
          {nodes.length} {nodes.length === 1 ? 'object' : 'objects'}
        </Typography>
      </Box>
      
      {/* Layer list */}
      <Box className="flex-1 overflow-y-auto">
        {layers.length === 0 ? (
          <Box
            className="p-6 text-center text-gray-500 dark:text-gray-400"
          >
            <Typography as="p" className="text-sm">No objects on canvas</Typography>
          </Box>
        ) : (
          <List dense disablePadding>
            {layers.map((layer) => (
              <LayerRow
                key={layer.id}
                layer={layer}
                selected={
                  selectedNodeIds.includes(layer.id) ||
                  (layer.children?.some((c) => selectedNodeIds.includes(c.id)) ?? false)
                }
                onSelect={onSelectNode}
                onToggleVisibility={onToggleVisibility}
                onToggleLock={onToggleLock}
              />
            ))}
          </List>
        )}
      </Box>
      
      {/* Footer with actions */}
      <Box
        className="p-2 flex gap-2 border-t border-solid border-gray-200 dark:border-gray-700"
      >
        <Tooltip title="Bring to Front">
          <IconButton
            size="sm"
            disabled={selectedNodeIds.length === 0}
            onClick={() => {
              selectedNodeIds.forEach((id) => onReorder(id, 999));
            }}
          >
            <Box
              className="flex flex-col gap-0.5 w-[16px] h-[16px]"
            >
              <Box className="w-full h-[4px] bg-blue-600" />
              <Box className="w-full h-[4px] bg-gray-400" />
            </Box>
          </IconButton>
        </Tooltip>
        
        <Tooltip title="Send to Back">
          <IconButton
            size="sm"
            disabled={selectedNodeIds.length === 0}
            onClick={() => {
              selectedNodeIds.forEach((id) => onReorder(id, 0));
            }}
          >
            <Box
              className="flex flex-col gap-0.5 w-[16px] h-[16px]"
            >
              <Box className="w-full h-[4px] bg-gray-400" />
              <Box className="w-full h-[4px] bg-blue-600" />
            </Box>
          </IconButton>
        </Tooltip>
      </Box>
    </Box>
  );
}
