import { Layers as LayersIcon, Eye as VisibilityIcon, EyeOff as VisibilityOffIcon, Lock as LockIcon, LockOpen as LockOpenIcon, Plus as AddIcon, Trash2 as DeleteIcon, Pencil as EditIcon, GripVertical as DragIcon, ChevronDown as ExpandMoreIcon, ChevronUp as ExpandLessIcon, Palette as PaletteIcon, Opacity as OpacityIcon, Users as GroupIcon, Filter as FilterIcon, Sort as SortIcon, MoreVertical as MoreVertIcon } from 'lucide-react';
import {
  Box,
  Typography,
  IconButton,
  ListItem,
  ListItemText,
  ListItemIcon,
  Switch,
  Chip,
  Button,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Fab,
  Tooltip,
  Divider,
  Avatar,
  Badge,
  Menu,
  Card,
  CardContent,
  CardActions,
  Slider,
  FormControlLabel,
  Checkbox,
  Surface as Paper,
  InteractiveList as List,
} from '@ghatana/ui';
import {
  Drawer,
  ListItemSecondaryAction,
  TextField,
  MenuItem,
  Collapse,
} from '@ghatana/ui';
import React, { useState, useCallback, useRef } from 'react';

import type { Node, Edge } from '@xyflow/react';

// Layer configuration interface
/**
 *
 */
export interface CanvasLayer {
  id: string;
  name: string;
  description?: string;
  visible: boolean;
  locked: boolean;
  opacity: number;
  color: string;
  order: number; // z-index equivalent
  nodeIds: string[];
  edgeIds: string[];
  type: 'default' | 'background' | 'foreground' | 'overlay' | 'annotation';
  properties: {
    blendMode?: 'normal' | 'multiply' | 'screen' | 'overlay';
    filters?: string[];
    transform?: {
      x: number;
      y: number;
      scale: number;
      rotation: number;
    };
  };
  metadata: {
    createdAt: Date;
    updatedAt: Date;
    author: string;
    tags: string[];
  };
}

// Layer management hook interface
/**
 *
 */
export interface UseCanvasLayersReturn {
  layers: CanvasLayer[];
  activeLayer: CanvasLayer | null;
  createLayer: (name: string, type?: CanvasLayer['type']) => string;
  deleteLayer: (layerId: string) => void;
  updateLayer: (layerId: string, updates: Partial<CanvasLayer>) => void;
  setActiveLayer: (layerId: string) => void;
  moveLayer: (layerId: string, newOrder: number) => void;
  toggleLayerVisibility: (layerId: string) => void;
  toggleLayerLock: (layerId: string) => void;
  addNodesToLayer: (layerId: string, nodeIds: string[]) => void;
  removeNodesFromLayer: (layerId: string, nodeIds: string[]) => void;
  addEdgesToLayer: (layerId: string, edgeIds: string[]) => void;
  removeEdgesFromLayer: (layerId: string, edgeIds: string[]) => void;
  getLayerNodes: (layerId: string) => Node[];
  getLayerEdges: (layerId: string) => Edge[];
  getVisibleNodes: () => Node[];
  getVisibleEdges: () => Edge[];
  duplicateLayer: (layerId: string) => string;
  mergeLayer: (sourceLayerId: string, targetLayerId: string) => void;
  exportLayer: (layerId: string) => any;
  importLayer: (layerData: unknown) => string;
}

// Layer management hook
export const useCanvasLayers = (
  nodes: Node[],
  edges: Edge[],
  userId: string = 'default-user'
): UseCanvasLayersReturn => {
  const [layers, setLayers] = useState<CanvasLayer[]>([
    {
      id: 'default',
      name: 'Default Layer',
      description: 'Default canvas layer',
      visible: true,
      locked: false,
      opacity: 1,
      color: '#2196f3',
      order: 0,
      nodeIds: [],
      edgeIds: [],
      type: 'default',
      properties: {
        blendMode: 'normal'
      },
      metadata: {
        createdAt: new Date(),
        updatedAt: new Date(),
        author: userId,
        tags: ['default']
      }
    }
  ]);

  const [activeLayer, setActiveLayerState] = useState<CanvasLayer | null>(layers[0]);

  const createLayer = useCallback((name: string, type: CanvasLayer['type'] = 'default'): string => {
    const newLayer: CanvasLayer = {
      id: `layer-${Date.now()}`,
      name,
      visible: true,
      locked: false,
      opacity: 1,
      color: `#${Math.floor(Math.random() * 16777215).toString(16)}`,
      order: layers.length,
      nodeIds: [],
      edgeIds: [],
      type,
      properties: {
        blendMode: 'normal'
      },
      metadata: {
        createdAt: new Date(),
        updatedAt: new Date(),
        author: userId,
        tags: [type]
      }
    };

    setLayers(prev => [...prev, newLayer]);
    return newLayer.id;
  }, [layers.length, userId]);

  const deleteLayer = useCallback((layerId: string) => {
    setLayers(prev => prev.filter(layer => layer.id !== layerId));
    if (activeLayer?.id === layerId) {
      setActiveLayerState(layers.find(l => l.id !== layerId) || null);
    }
  }, [activeLayer, layers]);

  const updateLayer = useCallback((layerId: string, updates: Partial<CanvasLayer>) => {
    setLayers(prev => prev.map(layer =>
      layer.id === layerId
        ? {
          ...layer,
          ...updates,
          metadata: { ...layer.metadata, updatedAt: new Date() }
        }
        : layer
    ));
  }, []);

  const setActiveLayer = useCallback((layerId: string) => {
    const layer = layers.find(l => l.id === layerId);
    setActiveLayerState(layer || null);
  }, [layers]);

  const moveLayer = useCallback((layerId: string, newOrder: number) => {
    setLayers(prev => {
      const layer = prev.find(l => l.id === layerId);
      if (!layer) return prev;

      const otherLayers = prev.filter(l => l.id !== layerId);
      const updatedLayers = [...otherLayers];

      updatedLayers.splice(newOrder, 0, { ...layer, order: newOrder });

      // Reorder all layers
      return updatedLayers.map((l, index) => ({ ...l, order: index }));
    });
  }, []);

  const toggleLayerVisibility = useCallback((layerId: string) => {
    updateLayer(layerId, { visible: !layers.find(l => l.id === layerId)?.visible });
  }, [layers, updateLayer]);

  const toggleLayerLock = useCallback((layerId: string) => {
    updateLayer(layerId, { locked: !layers.find(l => l.id === layerId)?.locked });
  }, [layers, updateLayer]);

  const addNodesToLayer = useCallback((layerId: string, nodeIds: string[]) => {
    const layer = layers.find(l => l.id === layerId);
    if (layer) {
      const newNodeIds = [...new Set([...layer.nodeIds, ...nodeIds])];
      updateLayer(layerId, { nodeIds: newNodeIds });
    }
  }, [layers, updateLayer]);

  const removeNodesFromLayer = useCallback((layerId: string, nodeIds: string[]) => {
    const layer = layers.find(l => l.id === layerId);
    if (layer) {
      const newNodeIds = layer.nodeIds.filter(id => !nodeIds.includes(id));
      updateLayer(layerId, { nodeIds: newNodeIds });
    }
  }, [layers, updateLayer]);

  const addEdgesToLayer = useCallback((layerId: string, edgeIds: string[]) => {
    const layer = layers.find(l => l.id === layerId);
    if (layer) {
      const newEdgeIds = [...new Set([...layer.edgeIds, ...edgeIds])];
      updateLayer(layerId, { edgeIds: newEdgeIds });
    }
  }, [layers, updateLayer]);

  const removeEdgesFromLayer = useCallback((layerId: string, edgeIds: string[]) => {
    const layer = layers.find(l => l.id === layerId);
    if (layer) {
      const newEdgeIds = layer.edgeIds.filter(id => !edgeIds.includes(id));
      updateLayer(layerId, { edgeIds: newEdgeIds });
    }
  }, [layers, updateLayer]);

  const getLayerNodes = useCallback((layerId: string): Node[] => {
    const layer = layers.find(l => l.id === layerId);
    if (!layer) return [];
    return nodes.filter(node => layer.nodeIds.includes(node.id));
  }, [layers, nodes]);

  const getLayerEdges = useCallback((layerId: string): Edge[] => {
    const layer = layers.find(l => l.id === layerId);
    if (!layer) return [];
    return edges.filter(edge => layer.edgeIds.includes(edge.id));
  }, [layers, edges]);

  const getVisibleNodes = useCallback((): Node[] => {
    const visibleLayers = layers.filter(layer => layer.visible);
    const visibleNodeIds = new Set(visibleLayers.flatMap(layer => layer.nodeIds));
    return nodes.filter(node => visibleNodeIds.has(node.id));
  }, [layers, nodes]);

  const getVisibleEdges = useCallback((): Edge[] => {
    const visibleLayers = layers.filter(layer => layer.visible);
    const visibleEdgeIds = new Set(visibleLayers.flatMap(layer => layer.edgeIds));
    return edges.filter(edge => visibleEdgeIds.has(edge.id));
  }, [layers, edges]);

  const duplicateLayer = useCallback((layerId: string): string => {
    const layer = layers.find(l => l.id === layerId);
    if (!layer) return '';

    const duplicatedLayer: CanvasLayer = {
      ...layer,
      id: `layer-${Date.now()}`,
      name: `${layer.name} Copy`,
      order: layers.length,
      metadata: {
        ...layer.metadata,
        createdAt: new Date(),
        updatedAt: new Date()
      }
    };

    setLayers(prev => [...prev, duplicatedLayer]);
    return duplicatedLayer.id;
  }, [layers]);

  const mergeLayer = useCallback((sourceLayerId: string, targetLayerId: string) => {
    const sourceLayer = layers.find(l => l.id === sourceLayerId);
    const targetLayer = layers.find(l => l.id === targetLayerId);

    if (sourceLayer && targetLayer) {
      const mergedNodeIds = [...new Set([...targetLayer.nodeIds, ...sourceLayer.nodeIds])];
      const mergedEdgeIds = [...new Set([...targetLayer.edgeIds, ...sourceLayer.edgeIds])];

      updateLayer(targetLayerId, {
        nodeIds: mergedNodeIds,
        edgeIds: mergedEdgeIds
      });

      deleteLayer(sourceLayerId);
    }
  }, [layers, updateLayer, deleteLayer]);

  const exportLayer = useCallback((layerId: string) => {
    const layer = layers.find(l => l.id === layerId);
    if (!layer) return null;

    return {
      layer,
      nodes: getLayerNodes(layerId),
      edges: getLayerEdges(layerId)
    };
  }, [layers, getLayerNodes, getLayerEdges]);

  const importLayer = useCallback((layerData: unknown): string => {
    const importedLayer: CanvasLayer = {
      ...layerData.layer,
      id: `layer-${Date.now()}`,
      order: layers.length,
      metadata: {
        ...layerData.layer.metadata,
        createdAt: new Date(),
        updatedAt: new Date(),
        author: userId
      }
    };

    setLayers(prev => [...prev, importedLayer]);
    return importedLayer.id;
  }, [layers.length, userId]);

  return {
    layers,
    activeLayer,
    createLayer,
    deleteLayer,
    updateLayer,
    setActiveLayer,
    moveLayer,
    toggleLayerVisibility,
    toggleLayerLock,
    addNodesToLayer,
    removeNodesFromLayer,
    addEdgesToLayer,
    removeEdgesFromLayer,
    getLayerNodes,
    getLayerEdges,
    getVisibleNodes,
    getVisibleEdges,
    duplicateLayer,
    mergeLayer,
    exportLayer,
    importLayer
  };
};

// Layer Panel Component
/**
 *
 */
interface LayersPanelProps {
  open: boolean;
  onClose: () => void;
  layers: CanvasLayer[];
  activeLayer: CanvasLayer | null;
  onCreateLayer: (name: string, type?: CanvasLayer['type']) => void;
  onDeleteLayer: (layerId: string) => void;
  onUpdateLayer: (layerId: string, updates: Partial<CanvasLayer>) => void;
  onSetActiveLayer: (layerId: string) => void;
  onMoveLayer: (layerId: string, newOrder: number) => void;
  onToggleVisibility: (layerId: string) => void;
  onToggleLock: (layerId: string) => void;
  onDuplicateLayer: (layerId: string) => void;
  onMergeLayer: (sourceId: string, targetId: string) => void;
  totalNodes: number;
  totalEdges: number;
}

export const LayersPanel: React.FC<LayersPanelProps> = ({
  open,
  onClose,
  layers,
  activeLayer,
  onCreateLayer,
  onDeleteLayer,
  onUpdateLayer,
  onSetActiveLayer,
  onMoveLayer,
  onToggleVisibility,
  onToggleLock,
  onDuplicateLayer,
  onMergeLayer,
  totalNodes,
  totalEdges
}) => {
  const [newLayerDialogOpen, setNewLayerDialogOpen] = useState(false);
  const [newLayerName, setNewLayerName] = useState('');
  const [newLayerType, setNewLayerType] = useState<CanvasLayer['type']>('default');
  const [expandedLayers, setExpandedLayers] = useState<Set<string>>(new Set());
  const [contextMenu, setContextMenu] = useState<{
    mouseX: number;
    mouseY: number;
    layerId: string;
  } | null>(null);

  const handleCreateLayer = () => {
    if (newLayerName.trim()) {
      onCreateLayer(newLayerName.trim(), newLayerType);
      setNewLayerName('');
      setNewLayerDialogOpen(false);
    }
  };

  const handleContextMenu = (event: React.MouseEvent, layerId: string) => {
    event.preventDefault();
    setContextMenu({
      mouseX: event.clientX + 2,
      mouseY: event.clientY - 6,
      layerId
    });
  };

  const handleContextMenuClose = () => {
    setContextMenu(null);
  };

  const toggleLayerExpanded = (layerId: string) => {
    setExpandedLayers(prev => {
      const newSet = new Set(prev);
      if (newSet.has(layerId)) {
        newSet.delete(layerId);
      } else {
        newSet.add(layerId);
      }
      return newSet;
    });
  };

  const sortedLayers = [...layers].sort((a, b) => b.order - a.order);

  return (
    <>
      <Drawer
        anchor="right"
        open={open}
        onClose={onClose}
        PaperProps={{ style: { width: 350, backgroundColor: '#fafafa' } }}
      >
        <Box className="p-4">
          <Box display="flex" alignItems="center" justifyContent="space-between" mb={2}>
            <Typography variant="h6" display="flex" alignItems="center" gap={1}>
              <LayersIcon />
              Layers ({layers.length})
            </Typography>
            <Tooltip title="Add Layer">
              <Fab
                size="small"
                color="primary"
                onClick={() => setNewLayerDialogOpen(true)}
              >
                <AddIcon />
              </Fab>
            </Tooltip>
          </Box>

          {/* Layer Statistics */}
          <Paper className="p-4 mb-4 bg-gray-100 dark:bg-gray-800">
            <Typography variant="subtitle2" gutterBottom>
              Canvas Statistics
            </Typography>
            <Box display="flex" justifyContent="space-between">
              <Typography variant="body2" color="text.secondary">
                Nodes: {totalNodes}
              </Typography>
              <Typography variant="body2" color="text.secondary">
                Edges: {totalEdges}
              </Typography>
              <Typography variant="body2" color="text.secondary">
                Visible: {layers.filter(l => l.visible).length}
              </Typography>
            </Box>
          </Paper>

          {/* Layers List */}
          <List>
            {sortedLayers.map((layer, index) => (
              <Paper
                key={layer.id}
                className="mb-2" style={{ border: activeLayer?.id === layer.id ? 2 : 1, borderColor: activeLayer?.id === layer.id ? 'primary.main' : 'divider', backgroundColor: 'layer.color' }}
              >
                <ListItem
                  button
                  onClick={() => onSetActiveLayer(layer.id)}
                  onContextMenu={(e) => handleContextMenu(e, layer.id)}
                  className="py-2"
                >
                  <ListItemIcon>
                    <Box display="flex" alignItems="center" gap={0.5}>
                      <DragIcon className="cursor-grab text-base" />
                      <Avatar
                        className="text-xs w-[20px] h-[20px]" >
                        {layer.order}
                      </Avatar>
                    </Box>
                  </ListItemIcon>

                  <ListItemText
                    primary={
                      <Box display="flex" alignItems="center" gap={1}>
                        <Typography variant="subtitle2" noWrap>
                          {layer.name}
                        </Typography>
                        <Chip
                          label={layer.type}
                          size="small"
                          variant="outlined"
                          className="h-[18px] text-[0.65rem]"
                        />
                      </Box>
                    }
                    secondary={
                      <Box display="flex" alignItems="center" gap={1} mt={0.5}>
                        <Badge badgeContent={layer.nodeIds.length} color="primary">
                          <GroupIcon className="text-sm" />
                        </Badge>
                        <Typography variant="caption" color="text.secondary">
                          {layer.nodeIds.length} nodes, {layer.edgeIds.length} edges
                        </Typography>
                      </Box>
                    }
                  />

                  <ListItemSecondaryAction>
                    <Box display="flex" alignItems="center" gap={0.5}>
                      {layer.opacity < 1 && (
                        <OpacityIcon className="text-gray-500 dark:text-gray-400 text-base" />
                      )}
                      <IconButton
                        size="small"
                        onClick={(e) => {
                          e.stopPropagation();
                          onToggleVisibility(layer.id);
                        }}
                      >
                        {layer.visible ? <VisibilityIcon /> : <VisibilityOffIcon />}
                      </IconButton>
                      <IconButton
                        size="small"
                        onClick={(e) => {
                          e.stopPropagation();
                          onToggleLock(layer.id);
                        }}
                      >
                        {layer.locked ? <LockIcon /> : <LockOpenIcon />}
                      </IconButton>
                      <IconButton
                        size="small"
                        onClick={() => toggleLayerExpanded(layer.id)}
                      >
                        {expandedLayers.has(layer.id) ? <ExpandLessIcon /> : <ExpandMoreIcon />}
                      </IconButton>
                    </Box>
                  </ListItemSecondaryAction>
                </ListItem>

                {/* Expanded Layer Details */}
                <Collapse in={expandedLayers.has(layer.id)}>
                  <Box className="px-4 pb-4">
                    <Divider className="my-2" />

                    {/* Opacity Slider */}
                    <Box mb={2}>
                      <Typography variant="caption" gutterBottom>
                        Opacity: {Math.round(layer.opacity * 100)}%
                      </Typography>
                      <Slider
                        value={layer.opacity}
                        onChange={(_, value) =>
                          onUpdateLayer(layer.id, { opacity: value as number })
                        }
                        min={0}
                        max={1}
                        step={0.1}
                        size="small"
                      />
                    </Box>

                    {/* Layer Properties */}
                    <Box display="flex" flexWrap="wrap" gap={1}>
                      <Chip
                        icon={<PaletteIcon />}
                        label="Color"
                        size="small"
                        onClick={() => {
                          // Color picker would open here
                        }}
                        className="text-white" style={{ backgroundColor: layer.color }}
                      />
                      {layer.properties.blendMode !== 'normal' && (
                        <Chip
                          label={layer.properties.blendMode}
                          size="small"
                          variant="outlined"
                        />
                      )}
                      {layer.metadata.tags.map((tag, tagIndex) => (
                        <Chip
                          key={tagIndex}
                          label={tag}
                          size="small"
                          variant="outlined"
                        />
                      ))}
                    </Box>

                    {/* Layer Info */}
                    <Typography variant="caption" color="text.secondary" className="mt-2 block">
                      Created: {layer.metadata.createdAt.toLocaleDateString()}
                    </Typography>
                    <Typography variant="caption" color="text.secondary">
                      Author: {layer.metadata.author}
                    </Typography>
                  </Box>
                </Collapse>
              </Paper>
            ))}
          </List>
        </Box>
      </Drawer>

      {/* Context Menu */}
      <Menu
        open={contextMenu !== null}
        onClose={handleContextMenuClose}
        anchorReference="anchorPosition"
        anchorPosition={
          contextMenu !== null
            ? { top: contextMenu.mouseY, left: contextMenu.mouseX }
            : undefined
        }
      >
        <MenuItem onClick={() => {
          if (contextMenu) onDuplicateLayer(contextMenu.layerId);
          handleContextMenuClose();
        }}>
          <ListItemIcon><GroupIcon size={16} /></ListItemIcon>
          <ListItemText>Duplicate Layer</ListItemText>
        </MenuItem>
        <MenuItem onClick={() => {
          if (contextMenu) onDeleteLayer(contextMenu.layerId);
          handleContextMenuClose();
        }}>
          <ListItemIcon><DeleteIcon size={16} /></ListItemIcon>
          <ListItemText>Delete Layer</ListItemText>
        </MenuItem>
      </Menu>

      {/* New Layer Dialog */}
      <Dialog open={newLayerDialogOpen} onClose={() => setNewLayerDialogOpen(false)}>
        <DialogTitle>Create New Layer</DialogTitle>
        <DialogContent>
          <Box display="flex" flexDirection="column" gap={2} pt={1}>
            <TextField
              label="Layer Name"
              value={newLayerName}
              onChange={(e) => setNewLayerName(e.target.value)}
              fullWidth
              autoFocus
            />
            <TextField
              label="Layer Type"
              value={newLayerType}
              onChange={(e) => setNewLayerType(e.target.value as CanvasLayer['type'])}
              fullWidth
              select
            >
              <MenuItem value="default">Default</MenuItem>
              <MenuItem value="background">Background</MenuItem>
              <MenuItem value="foreground">Foreground</MenuItem>
              <MenuItem value="overlay">Overlay</MenuItem>
              <MenuItem value="annotation">Annotation</MenuItem>
            </TextField>
          </Box>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setNewLayerDialogOpen(false)}>Cancel</Button>
          <Button onClick={handleCreateLayer} variant="contained">Create</Button>
        </DialogActions>
      </Dialog>
    </>
  );
};