import {
  Button,
  Card,
  Box,
  Typography,
} from '@ghatana/ui';
import { TextField } from '@ghatana/ui';
import React, { useState } from 'react';

// Types for our component system
/**
 *
 */
export type ComponentType = 'button' | 'input' | 'card' | 'text' | 'custom';

/**
 *
 */
interface DraggableItemProps {
  id: string;
  type: ComponentType;
  children?: React.ReactNode;
  style?: React.CSSProperties;
  componentProps?: Record<string, unknown>;
  onDragStart?: (id: string) => void;
  onDrop?: (id: string) => void;
  onDragOver?: (e: React.DragEvent) => void;
}

/**
 *
 */
interface ComponentLibraryProps {
  components: Array<{ type: ComponentType; label: string; icon?: string }>;
  onComponentSelect: (type: ComponentType) => void;
}

/**
 * Renders component by type using design system components directly
 */
const ComponentRenderer = ({ type, children, ...props }: { type: ComponentType; children?: React.ReactNode } & any) => {
  switch (type) {
    case 'button':
      return (
        <Button variant="contained" {...props}>
          {children || 'Button'}
        </Button>
      );
    case 'input':
      return (
        <TextField
          type="text"
          fullWidth
          size="small"
          {...props}
        />
      );
    case 'card':
      return (
        <Card className="p-4 my-2" {...props}>
          {children}
        </Card>
      );
    case 'text':
      return (
        <Typography className="my-2" {...props}>
          {children || 'Text content goes here'}
        </Typography>
      );
    default:
      return <Box>Component {type} not found</Box>;
  }
};

export const DraggableItem: React.FC<DraggableItemProps> = ({
  id,
  type,
  children,
  style,
  componentProps = {},
  onDragStart,
  onDrop,
  onDragOver,
}) => {
  const handleDragStart = (e: React.DragEvent) => {
    e.dataTransfer.setData('text/plain', id);
    if (onDragStart) onDragStart(id);
  };

  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault();
    if (onDrop) onDrop(id);
  };

  const handleDragOver = (e: React.DragEvent) => {
    e.preventDefault();
    if (onDragOver) onDragOver(e);
  };

  return (
    <Box
      draggable
      onDragStart={handleDragStart}
      onDrop={handleDrop}
      onDragOver={handleDragOver}
      className="cursor-grab my-2 p-2 rounded"
    >
      <ComponentRenderer type={type} {...componentProps}>
        {children}
      </ComponentRenderer>
    </Box>
  );
};

export const ComponentLibrary: React.FC<ComponentLibraryProps> = ({
  components,
  onComponentSelect,
}) => {
  return (
    <Box className="p-4 border-r border-gray-200 dark:border-gray-700">
      <Typography variant="h6" component="h3">Component Library</Typography>
      <Box className="grid gap-2 mt-4">
        {components.map((comp) => (
          <Box
            key={comp.type}
            onClick={() => onComponentSelect(comp.type)}
            className="p-2 border border-gray-200 dark:border-gray-700 rounded cursor-pointer bg-gray-50 dark:bg-gray-800 hover:bg-gray-100 hover:dark:bg-gray-800"
          >
            {comp.label}
          </Box>
        ))}
      </Box>
    </Box>
  );
};

/**
 *
 */
interface DraggableCanvasProps {
  items: Array<{ id: string; type: ComponentType; props?: Record<string, unknown> }>;
  onItemsChange: (items: Array<{ id: string; type: ComponentType; props?: Record<string, unknown> }>) => void;
}

export const DraggableCanvas: React.FC<DraggableCanvasProps> = ({
  items,
  onItemsChange,
}) => {
  const [selectedItem, setSelectedItem] = useState<string | null>(null);
  const [draggedItem, setDraggedItem] = useState<string | null>(null);

  const handleItemSelect = (id: string) => {
    setSelectedItem(id === selectedItem ? null : id);
  };

  const handleDragStart = (id: string) => {
    setDraggedItem(id);
  };

  const handleDrop = (targetId: string) => {
    if (!draggedItem) return;
    
    const draggedIndex = items.findIndex(item => item.id === draggedItem);
    const targetIndex = items.findIndex(item => item.id === targetId);
    
    if (draggedIndex === targetIndex) return;
    
    const newItems = [...items];
    const [movedItem] = newItems.splice(draggedIndex, 1);
    newItems.splice(targetIndex, 0, movedItem);
    
    onItemsChange(newItems);
    setDraggedItem(null);
  };

  const addNewItem = (type: ComponentType) => {
    const newItem = {
      id: `item-${Date.now()}`,
      type,
      props: {},
    };
    onItemsChange([...items, newItem]);
  };

  return (
    <Box className="flex min-h-[500px]">
      <ComponentLibrary
        components={[
          { type: 'button', label: 'Button' },
          { type: 'input', label: 'Input' },
          { type: 'card', label: 'Card' },
          { type: 'text', label: 'Text' },
        ]}
        onComponentSelect={addNewItem}
      />
      <Box
        className="flex-1 p-4 bg-gray-100 dark:bg-gray-800 min-h-full border-[2px] border-gray-200 dark:border-gray-700 border-dashed" >
        <Typography variant="h6" component="h3">Canvas</Typography>
        {items.length === 0 ? (
          <Box className="text-center p-10 text-gray-500 dark:text-gray-400">
            Click on components in the library to add them to the canvas
          </Box>
        ) : (
          items.map((item) => (
            <Box
              key={item.id}
              onClick={() => handleItemSelect(item.id)}
              className="rounded m-1" style={{ outline: selectedItem === item.id ? 2 : 0, outlineColor: 'primary.main', outlineStyle: 'solid' }} >
              <DraggableItem
                id={item.id}
                type={item.type}
                componentProps={item.props || {}}
                onDragStart={handleDragStart}
                onDrop={handleDrop}
              />
            </Box>
          ))
        )}
      </Box>
    </Box>
  );
};

export default DraggableCanvas;
