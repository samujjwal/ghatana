/**
 * Layers Panel - Real-time canvas layer management
 *
 * Provides tree view of all canvas nodes with visibility/lock controls.
 * Syncs with canvas selection and supports reordering.
 *
 * @doc.type component
 * @doc.purpose Canvas layer management UI
 * @doc.layer components
 */

import { useState, useMemo, useCallback } from 'react';
import {
  Box,
  Typography,
  ListItem,
  ListItemText,
  ListItemIcon,
  IconButton,
  InputAdornment,
  Chip,
  InteractiveList as List,
} from '@ghatana/ui';
import { TextField, Collapse } from '@ghatana/ui';
import { Search } from 'lucide-react';
import type { RailPanelProps, LayerNode } from '../UnifiedLeftRail.types';

/**
 * Layers Panel Component
 */
export function LayersPanel({
  nodes = [],
  selectedNodeIds = [],
  onSelectNode,
  onDeleteNode,
  onToggleVisibility,
  onToggleLock,
}: RailPanelProps) {
  const [expandedGroups, setExpandedGroups] = useState<Set<string>>(new Set());
  const [hoveredNode, setHoveredNode] = useState<string | null>(null);
  const [localSearch, setLocalSearch] = useState('');

  // Build hierarchical layer structure
  const layerTree = useMemo<LayerNode[]>(() => {
    return nodes.map((node) => ({
      id: node.id,
      name:
        node.data?.label ||
        node.data?.title ||
        node.data?.text?.substring(0, 30) ||
        `${node.type} ${node.id.slice(0, 8)}`,
      type: node.type,
      visible: node.hidden !== true,
      locked: node.data?.locked || false,
      nodeData: node,
    }));
  }, [nodes]);

  // Filter by search
  const filteredLayers = useMemo(() => {
    if (!localSearch) return layerTree;
    const query = localSearch.toLowerCase();
    return layerTree.filter(
      (layer) =>
        layer.name.toLowerCase().includes(query) ||
        layer.type.toLowerCase().includes(query)
    );
  }, [layerTree, localSearch]);

  // Group by type for organization
  const groupedLayers = useMemo(() => {
    const groups = new Map<string, LayerNode[]>();
    filteredLayers.forEach((layer) => {
      const type = layer.type;
      if (!groups.has(type)) {
        groups.set(type, []);
      }
      groups.get(type)!.push(layer);
    });
    return groups;
  }, [filteredLayers]);

  const handleToggleExpand = (type: string) => {
    setExpandedGroups((prev) => {
      const next = new Set(prev);
      if (next.has(type)) {
        next.delete(type);
      } else {
        next.add(type);
      }
      return next;
    });
  };

  const handleSelectLayer = (layerId: string) => {
    if (onSelectNode) onSelectNode(layerId);
  };

  const handleToggleVisibility = useCallback(
    (layerId: string) => {
      if (onToggleVisibility) onToggleVisibility(layerId);
    },
    [onToggleVisibility]
  );

  const handleToggleLock = useCallback(
    (layerId: string) => {
      if (onToggleLock) onToggleLock(layerId);
    },
    [onToggleLock]
  );

  const handleDeleteLayer = useCallback(
    (layerId: string) => {
      if (confirm('Delete this layer?')) {
        if (onDeleteNode) onDeleteNode(layerId);
      }
    },
    [onDeleteNode]
  );

  const getTypeIcon = (type: string): string => {
    const iconMap: Record<string, string> = {
      rectangle: '▭',
      ellipse: '○',
      line: '—',
      arrow: '→',
      text: 'T',
      sticky: '📝',
      frame: '⊞',
      connector: '⟷',
      code: '💻',
      image: '🖼️',
      mindmap: '🧠',
    };
    return iconMap[type] || '◆';
  };

  return (
    <Box className="flex flex-col h-full">
      {/* Search & Stats */}
      <Box className="p-4 pb-2">
        <TextField
          fullWidth
          size="small"
          placeholder="Search layers..."
          value={localSearch}
          onChange={(e) => setLocalSearch(e.target.value)}
          InputProps={{
            startAdornment: (
            <InputAdornment position="start">
              <Search size={16} />
            </InputAdornment>
          ),
        }}
      />
        <Box
          className="mt-2 flex justify-between items-center"
        >
          <Typography variant="caption" color="text.secondary">
            {filteredLayers.length} layers
          </Typography>
          <Typography variant="caption" color="text.secondary">
            {selectedNodeIds.length} selected
          </Typography>
        </Box>
      </Box>

      {/* Layer List */}
      <Box className="flex-1 overflow-auto px-2">
        {filteredLayers.length === 0 && (
          <Box className="p-6 text-center">
            <Typography variant="body2" color="text.secondary">
              {localSearch
                ? 'No layers match your search'
                : 'No layers yet. Start adding elements.'}
            </Typography>
          </Box>
        )}

        {Array.from(groupedLayers.entries()).map(([type, layers]) => (
          <Box key={type} className="mb-2">
            {/* Group Header */}
            <Box
              onClick={() => handleToggleExpand(type)}
              className="flex items-center gap-2 p-2 cursor-pointer rounded hover:bg-gray-100 hover:dark:bg-gray-800"
            >
              <Box className="text-[0.8rem]">
                {expandedGroups.has(type) ? '▼' : '▶'}
              </Box>
              <Box className="text-base">{getTypeIcon(type)}</Box>
              <Typography variant="body2" fontWeight={600} className="flex-1">
                {type}
              </Typography>
              <Chip label={layers.length} size="small" className="h-[18px]" />
            </Box>

            {/* Layer Items */}
            <Collapse in={expandedGroups.has(type)}>
              <List dense disablePadding className="pl-4">
                {layers.map((layer) => {
                  const isSelected = selectedNodeIds.includes(layer.id);
                  const isHovered = hoveredNode === layer.id;

                  return (
                    <ListItem
                      key={layer.id}
                      dense
                      onMouseEnter={() => setHoveredNode(layer.id)}
                      onMouseLeave={() => setHoveredNode(null)}
                      className={`rounded mb-1 border border-solid ${isSelected ? 'bg-gray-100 dark:bg-gray-800 border-blue-600' : 'bg-transparent border-transparent'}`}
                    >
                      <ListItemIcon className="min-w-[32px]">
                        <Box className="text-base">
                          {getTypeIcon(layer.type)}
                        </Box>
                      </ListItemIcon>

                      <ListItemText
                        primary={layer.name}
                        primaryTypographyProps={{
                          noWrap: true,
                          variant: 'body2',
                          sx: { opacity: layer.visible ? 1 : 0.5 },
                        }}
                        onClick={() => handleSelectLayer(layer.id)}
                        className="cursor-pointer flex-1"
                      />

                      {/* Controls (show on hover or when selected) */}
                      {(isHovered || isSelected) && (
                        <Box className="flex gap-1">
                          <IconButton
                            size="small"
                            onClick={() => handleToggleVisibility(layer.id)}
                            title={layer.visible ? 'Hide' : 'Show'}
                          >
                            {layer.visible ? '👁️' : '🚫'}
                          </IconButton>
                          <IconButton
                            size="small"
                            onClick={() => handleToggleLock(layer.id)}
                            title={layer.locked ? 'Unlock' : 'Lock'}
                          >
                            {layer.locked ? '🔒' : '🔓'}
                          </IconButton>
                          <IconButton
                            size="small"
                            onClick={() => handleDeleteLayer(layer.id)}
                            title="Delete"
                            color="error"
                          >
                            🗑️
                          </IconButton>
                        </Box>
                      )}
                    </ListItem>
                  );
                })}
              </List>
            </Collapse>
          </Box>
        ))}
      </Box>

      {/* Quick Actions */}
      <Box className="p-4 pt-2 border-gray-200 dark:border-gray-700 border-t" >
        <Box className="flex gap-2">
          <IconButton
            size="small"
            onClick={() => {
              // Select all
              filteredLayers.forEach((layer) => onSelectNode?.(layer.id));
            }}
            title="Select All"
          >
            ☑️
          </IconButton>
          <IconButton
            size="small"
            onClick={() => {
              // Show all
              filteredLayers.forEach((layer) => {
                if (!layer.visible) {
                  handleToggleVisibility(layer.id);
                }
              });
            }}
            title="Show All"
          >
            👁️
          </IconButton>
          <IconButton
            size="small"
            onClick={() => {
              // Lock all
              filteredLayers.forEach((layer) => {
                handleToggleLock(layer.id);
              });
            }}
            title="Lock All"
          >
            🔒
          </IconButton>
        </Box>
      </Box>
    </Box>
  );
}
