import { Plus as AddIcon, Trash2 as DeleteIcon, Pencil as EditIcon, X as CloseIcon } from 'lucide-react';
import {
  Box,
  Stack,
  Typography,
  IconButton,
  Divider,
  Button,
  Surface as Paper,
} from '@ghatana/ui';
import { Drawer } from '@ghatana/ui';
import React, { useState, useCallback } from 'react';

import { ComponentRenderer } from './ComponentRenderer';
import { PropertyForm } from './PropertyForm';
import { getDefaultComponentData } from './schemas';

import type { ComponentData} from './schemas';

/**
 *
 */
interface PageDesignerProps {
  initialComponents?: ComponentData[];
  onComponentsChange?: (components: ComponentData[]) => void;
}

const AVAILABLE_COMPONENTS = [
  { type: 'button', label: 'Button', icon: '🔘' },
  { type: 'card', label: 'Card', icon: '🃏' },
  { type: 'textfield', label: 'Text Field', icon: '📝' },
  { type: 'typography', label: 'Typography', icon: '📄' },
  { type: 'box', label: 'Container', icon: '📦' },
];

export const PageDesigner: React.FC<PageDesignerProps> = ({
  initialComponents = [],
  onComponentsChange,
}) => {
  const [components, setComponents] = useState<ComponentData[]>(initialComponents);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [drawerOpen, setDrawerOpen] = useState(false);

  const selectedComponent = components.find((c) => c.id === selectedId);
  const editingComponent = components.find((c) => c.id === editingId);

  const handleAddComponent = useCallback(
    (type: string) => {
      const newComponent = getDefaultComponentData(type) as ComponentData;
      const updated = [...components, newComponent];
      setComponents(updated);
      onComponentsChange?.(updated);
      setSelectedId(newComponent.id);
    },
    [components, onComponentsChange],
  );

  const handlePaletteDragStart = useCallback((event: React.DragEvent<HTMLButtonElement>, componentType: string) => {
    event.dataTransfer.setData('application/x-page-component', componentType);
    event.dataTransfer.effectAllowed = 'copy';
  }, []);

  const handleDesignAreaDrop = useCallback(
    (event: React.DragEvent<HTMLDivElement>) => {
      event.preventDefault();
      const type = event.dataTransfer.getData('application/x-page-component');
      if (type) {
        handleAddComponent(type);
      }
    },
    [handleAddComponent],
  );

  const handleSelectComponent = useCallback((id: string) => {
    setSelectedId(id);
  }, []);

  const handleDeleteComponent = useCallback(() => {
    if (!selectedId) return;

    const updated = components.filter((c) => c.id !== selectedId);
    setComponents(updated);
    onComponentsChange?.(updated);
    setSelectedId(null);
  }, [selectedId, components, onComponentsChange]);

  const handleEditComponent = useCallback(() => {
    if (!selectedId) return;
    setEditingId(selectedId);
    setDrawerOpen(true);
  }, [selectedId]);

  const handleUpdateComponent = useCallback(
    (updatedData: ComponentData) => {
      const updated = components.map((c) =>
        c.id === updatedData.id ? updatedData : c,
      );
      setComponents(updated);
      onComponentsChange?.(updated);
      setDrawerOpen(false);
      setEditingId(null);
    },
    [components, onComponentsChange],
  );

  const handleCloseDrawer = useCallback(() => {
    setDrawerOpen(false);
    setEditingId(null);
  }, []);

  return (
    <Box className="flex h-full relative" data-testid="page-designer">
      {/* Component Palette */}
      <Paper
        elevation={2}
        className="p-4 overflow-y-auto w-[200px] rounded-none"
      >
        <Typography variant="h6" gutterBottom>
          Components
        </Typography>
        <Stack spacing={1}>
          {AVAILABLE_COMPONENTS.map((comp) => (
            <Button
              key={comp.type}
              variant="outlined"
              startIcon={<span>{comp.icon}</span>}
              onClick={() => handleAddComponent(comp.type)}
              fullWidth
              className="justify-start"
              data-testid={`page-component-${comp.type}`}
              draggable
              onDragStart={(event) => handlePaletteDragStart(event, comp.type)}
            >
              {comp.label}
            </Button>
          ))}
        </Stack>
      </Paper>

      {/* Canvas Area */}
      <Box
        className="flex-1 p-6 overflow-y-auto relative" style={{ backgroundColor: 'var(--bg-surface)' }} data-testid="page-design-area"
        onDragOver={(event) => event.preventDefault()}
        onDrop={handleDesignAreaDrop}
      >
        {/* Toolbar */}
        {selectedComponent && (
          <Paper
            elevation={3}
            className="absolute p-2 flex gap-2 top-[16px] right-[16px] z-10"
          >
            <IconButton
              size="small"
              color="primary"
              onClick={handleEditComponent}
              title="Edit Properties"
            >
              <EditIcon size={16} />
            </IconButton>
            <IconButton
              size="small"
              color="error"
              onClick={handleDeleteComponent}
              title="Delete"
            >
              <DeleteIcon size={16} />
            </IconButton>
          </Paper>
        )}

        {/* Page Frame */}
        <Paper
          elevation={1}
          className="p-8 max-w-[800px] mx-auto min-h-[600px] bg-white"
        >
          {components.length === 0 ? (
            <Box
              className="flex items-center justify-center rounded-lg h-[400px]" style={{ border: '2px dashed #ccc' }} >
              <Stack alignItems="center" spacing={2}>
                <AddIcon className="text-gray-500 dark:text-gray-400 text-5xl" />
                <Typography variant="h6" color="text.secondary">
                  Add components to get started
                </Typography>
              </Stack>
            </Box>
          ) : (
            <Stack spacing={2}>
              {components.map((component) => (
                <ComponentRenderer
                  key={component.id}
                  data={component}
                  isSelected={component.id === selectedId}
                  onClick={() => handleSelectComponent(component.id)}
                />
              ))}
            </Stack>
          )}
        </Paper>
      </Box>

      {/* Property Editor Drawer */}
      <Drawer
        anchor="right"
        open={drawerOpen}
        onClose={handleCloseDrawer}
        PaperProps={{
          style: { width: 350 },
        }}
      >
        <Box className="p-4 flex justify-between items-center">
          <Typography variant="h6">Properties</Typography>
          <IconButton onClick={handleCloseDrawer} size="small">
            <CloseIcon />
          </IconButton>
        </Box>
        <Divider />
        {editingComponent && (
          <PropertyForm
            componentData={editingComponent}
            onUpdate={handleUpdateComponent}
            onCancel={handleCloseDrawer}
          />
        )}
      </Drawer>
    </Box>
  );
};
