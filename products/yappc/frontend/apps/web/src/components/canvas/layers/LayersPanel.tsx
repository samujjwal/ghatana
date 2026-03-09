import { Eye as VisibilityIcon, EyeOff as VisibilityOffIcon, Lock as LockIcon, LockOpen as LockOpenIcon, Plus as AddIcon, Trash2 as DeleteIcon, GripVertical as DragIcon } from 'lucide-react';
import {
  Box,
  Typography,
  ListItem,
  ListItemText,
  IconButton,
  Button,
  Stack,
  Surface as Paper,
  InteractiveList as List,
} from '@ghatana/ui';
import { TextField } from '@ghatana/ui';
import React, { useState } from 'react';

import type { Layer } from '../../../services/export/types';

/**
 *
 */
interface LayersPanelProps {
  layers: Layer[];
  onCreateLayer: (name: string) => void;
  onDeleteLayer: (id: string) => void;
  onToggleVisibility: (id: string) => void;
  onToggleLock: (id: string) => void;
  onReorder: (layerIds: string[]) => void;
}

export const LayersPanel: React.FC<LayersPanelProps> = ({
  layers,
  onCreateLayer,
  onDeleteLayer,
  onToggleVisibility,
  onToggleLock,
  onReorder,
}) => {
  const [newLayerName, setNewLayerName] = useState('');
  const [isAdding, setIsAdding] = useState(false);

  const handleAddLayer = () => {
    if (newLayerName.trim()) {
      onCreateLayer(newLayerName.trim());
      setNewLayerName('');
      setIsAdding(false);
    }
  };

  return (
    <Paper
      elevation={2}
      className="h-full flex flex-col overflow-hidden w-[250px]"
    >
      {/* Header */}
      <Box
        className="p-4 flex justify-between items-center border-gray-200 dark:border-gray-700 border-b" >
        <Typography variant="h6" className="text-base font-semibold">
          Layers
        </Typography>
        <IconButton
          size="small"
          onClick={() => setIsAdding(true)}
          title="Add Layer"
        >
          <AddIcon size={16} />
        </IconButton>
      </Box>

      {/* Add Layer Form */}
      {isAdding && (
        <Box className="p-4 border-gray-200 dark:border-gray-700 border-b" >
          <Stack spacing={1}>
            <TextField
              size="small"
              placeholder="Layer name"
              value={newLayerName}
              onChange={(e) => setNewLayerName(e.target.value)}
              onKeyPress={(e) => {
                if (e.key === 'Enter') {
                  handleAddLayer();
                }
              }}
              autoFocus
            />
            <Stack direction="row" spacing={1}>
              <Button
                size="small"
                variant="contained"
                onClick={handleAddLayer}
                fullWidth
              >
                Add
              </Button>
              <Button
                size="small"
                variant="outlined"
                onClick={() => {
                  setIsAdding(false);
                  setNewLayerName('');
                }}
                fullWidth
              >
                Cancel
              </Button>
            </Stack>
          </Stack>
        </Box>
      )}

      {/* Layers List */}
      <Box className="flex-1 overflow-y-auto">
        {layers.length === 0 ? (
          <Box
            className="p-6 text-center text-gray-500 dark:text-gray-400"
          >
            <Typography variant="body2">No layers yet</Typography>
            <Typography variant="caption">
              Click + to create a layer
            </Typography>
          </Box>
        ) : (
          <List className="py-0">
            {layers.map((layer) => (
              <ListItem
                key={layer.id}
                className="border-gray-200 dark:border-gray-700 border-b" >
                <IconButton size="small" className="cursor-grab mr-2">
                  <DragIcon size={16} />
                </IconButton>

                <ListItemText
                  primary={layer.name}
                  secondary={`${layer.elementIds.length} elements`}
                  primaryTypographyProps={{
                    fontSize: '0.9rem',
                    fontWeight: 500,
                  }}
                  secondaryTypographyProps={{
                    fontSize: '0.75rem',
                  }}
                />

                <IconButton
                  size="small"
                  onClick={() => onToggleVisibility(layer.id)}
                  title={layer.visible ? 'Hide layer' : 'Show layer'}
                >
                  {layer.visible ? (
                    <VisibilityIcon size={16} />
                  ) : (
                    <VisibilityOffIcon size={16} />
                  )}
                </IconButton>

                <IconButton
                  size="small"
                  onClick={() => onToggleLock(layer.id)}
                  title={layer.locked ? 'Unlock layer' : 'Lock layer'}
                >
                  {layer.locked ? (
                    <LockIcon size={16} />
                  ) : (
                    <LockOpenIcon size={16} />
                  )}
                </IconButton>

                <IconButton
                  size="small"
                  onClick={() => onDeleteLayer(layer.id)}
                  title="Delete layer"
                  color="error"
                >
                  <DeleteIcon size={16} />
                </IconButton>
              </ListItem>
            ))}
          </List>
        )}
      </Box>
    </Paper>
  );
};
