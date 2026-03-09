/**
 * DraggableCanvas Component
 *
 * A drag-and-drop canvas system with component library panel.
 * Allows users to drag, drop, and reorder UI components visually.
 *
 * @module ui/components/canvas
 * @doc.type component
 * @doc.purpose Drag-and-drop canvas for visual component arrangement
 * @doc.layer ui
 */

import React, { useState, useCallback } from 'react';
import { Box, Typography, Card, Button, TextField } from '@ghatana/ui';

// ============================================================================
// Types
// ============================================================================

/**
 * Component types available in the canvas
 */
export type ComponentType = 'button' | 'input' | 'card' | 'text' | 'custom';

/**
 * Props for draggable item
 */
export interface DraggableItemProps {
  /** Unique identifier for the item */
  id: string;
  /** Type of component to render */
  type: ComponentType;
  /** Child content */
  children?: React.ReactNode;
  /** Custom styles */
  style?: React.CSSProperties;
  /** Props to pass to the rendered component */
  componentProps?: Record<string, unknown>;
  /** Called when drag starts */
  onDragStart?: (id: string) => void;
  /** Called when item is dropped */
  onDrop?: (id: string) => void;
  /** Called during drag over */
  onDragOver?: (e: React.DragEvent) => void;
}

/**
 * Props for component library panel
 */
export interface ComponentLibraryProps {
  /** Available components */
  components: Array<{ type: ComponentType; label: string; icon?: string }>;
  /** Called when component is selected */
  onComponentSelect: (type: ComponentType) => void;
}

/**
 * Canvas item definition
 */
export interface CanvasItem {
  /** Unique identifier */
  id: string;
  /** Component type */
  type: ComponentType;
  /** Component props */
  props?: Record<string, unknown>;
}

/**
 * Props for draggable canvas
 */
export interface DraggableCanvasProps {
  /** Items currently on the canvas */
  items: CanvasItem[];
  /** Called when items change */
  onItemsChange: (items: CanvasItem[]) => void;
  /** Available components for the library (optional) */
  availableComponents?: Array<{ type: ComponentType; label: string; icon?: string }>;
  /** Whether to show the component library */
  showLibrary?: boolean;
  /** Canvas min height */
  minHeight?: number | string;
}

// ============================================================================
// Component Renderer
// ============================================================================

/**
 * Renders component by type using design system components
 */
const ComponentRenderer: React.FC<{
  type: ComponentType;
  children?: React.ReactNode;
  [key: string]: unknown;
}> = ({ type, children, ...props }) => {
  switch (type) {
    case 'button':
      return (
        <Button variant="solid" {...props}>
          {children || 'Button'}
        </Button>
      );
    case 'input':
      return (
        <TextField
          type="text"
          fullWidth
          size="sm"
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

// ============================================================================
// Draggable Item
// ============================================================================

/**
 * A draggable item that can be reordered within the canvas
 */
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
  const handleDragStart = useCallback((e: React.DragEvent) => {
    e.dataTransfer.setData('text/plain', id);
    e.dataTransfer.effectAllowed = 'move';
    if (onDragStart) onDragStart(id);
  }, [id, onDragStart]);

  const handleDrop = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    if (onDrop) onDrop(id);
  }, [id, onDrop]);

  const handleDragOver = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    e.dataTransfer.dropEffect = 'move';
    if (onDragOver) onDragOver(e);
  }, [onDragOver]);

  return (
    <Box
      draggable
      onDragStart={handleDragStart}
      onDrop={handleDrop}
      onDragOver={handleDragOver}
      className="cursor-grab my-2 p-2 rounded transition-[transform,box-shadow] duration-150 ease-out active:cursor-grabbing active:scale-[1.02] active:shadow-md"
      style={style}
      role="listitem"
      aria-grabbed="false"
    >
      <ComponentRenderer type={type} {...componentProps}>
        {children}
      </ComponentRenderer>
    </Box>
  );
};

// ============================================================================
// Component Library
// ============================================================================

/**
 * Default available components
 */
const DEFAULT_COMPONENTS: Array<{ type: ComponentType; label: string }> = [
  { type: 'button', label: 'Button' },
  { type: 'input', label: 'Input' },
  { type: 'card', label: 'Card' },
  { type: 'text', label: 'Text' },
];

/**
 * Panel displaying available components to add to canvas
 */
export const ComponentLibrary: React.FC<ComponentLibraryProps> = ({
  components,
  onComponentSelect,
}) => {
  return (
    <Box
      className="p-4 border-r border-gray-200 dark:border-gray-700 min-w-[200px] bg-white dark:bg-gray-900"
      role="toolbar"
      aria-label="Component library"
    >
      <Typography as="h6" component="h3" gutterBottom>
        Component Library
      </Typography>
      <Box className="grid gap-2 mt-4">
        {components.map((comp) => (
          <Box
            key={comp.type}
            onClick={() => onComponentSelect(comp.type)}
            onKeyDown={(e) => {
              if (e.key === 'Enter' || e.key === ' ') {
                e.preventDefault();
                onComponentSelect(comp.type);
              }
            }}
            tabIndex={0}
            role="button"
            aria-label={`Add ${comp.label} to canvas`}
            className="p-3 border border-gray-200 dark:border-gray-700 rounded cursor-pointer bg-gray-50 dark:bg-gray-800 transition-all duration-150 ease-out hover:bg-blue-100 dark:hover:bg-blue-900 hover:text-blue-900 dark:hover:text-blue-100 hover:border-blue-600 hover:translate-x-1 focus-visible:outline focus-visible:outline-2 focus-visible:outline-blue-600 focus-visible:outline-offset-2"
          >
            <Typography as="p" className="text-sm" fontWeight={500}>
              {comp.label}
            </Typography>
          </Box>
        ))}
      </Box>
    </Box>
  );
};

// ============================================================================
// Draggable Canvas
// ============================================================================

/**
 * A canvas that accepts draggable items and allows reordering
 *
 * @example
 * ```tsx
 * const [items, setItems] = useState<CanvasItem[]>([]);
 *
 * <DraggableCanvas
 *   items={items}
 *   onItemsChange={setItems}
 *   showLibrary={true}
 * />
 * ```
 */
export const DraggableCanvas: React.FC<DraggableCanvasProps> = ({
  items,
  onItemsChange,
  availableComponents = DEFAULT_COMPONENTS,
  showLibrary = true,
  minHeight = 500,
}) => {
  const [selectedItem, setSelectedItem] = useState<string | null>(null);
  const [draggedItem, setDraggedItem] = useState<string | null>(null);

  const handleItemSelect = useCallback((id: string) => {
    setSelectedItem(prev => prev === id ? null : id);
  }, []);

  const handleDragStart = useCallback((id: string) => {
    setDraggedItem(id);
  }, []);

  const handleDrop = useCallback((targetId: string) => {
    if (!draggedItem) return;

    const draggedIndex = items.findIndex(item => item.id === draggedItem);
    const targetIndex = items.findIndex(item => item.id === targetId);

    if (draggedIndex === targetIndex) return;

    const newItems = [...items];
    const [movedItem] = newItems.splice(draggedIndex, 1);
    newItems.splice(targetIndex, 0, movedItem);

    onItemsChange(newItems);
    setDraggedItem(null);
  }, [draggedItem, items, onItemsChange]);

  const addNewItem = useCallback((type: ComponentType) => {
    const newItem: CanvasItem = {
      id: `item-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
      type,
      props: {},
    };
    onItemsChange([...items, newItem]);
  }, [items, onItemsChange]);

  const removeItem = useCallback((id: string) => {
    onItemsChange(items.filter(item => item.id !== id));
    if (selectedItem === id) {
      setSelectedItem(null);
    }
  }, [items, onItemsChange, selectedItem]);

  return (
    <Box className="flex">
      {showLibrary && (
        <ComponentLibrary
          components={availableComponents}
          onComponentSelect={addNewItem}
        />
      )}
      <Box
        className="flex-1 p-4 rounded overflow-auto bg-gray-100 dark:bg-gray-800 min-h-full border-[2px] border-gray-200 dark:border-gray-700 border-dashed" role="list"
        aria-label="Canvas area"
      >
        <Box className="flex justify-between items-center mb-4">
          <Typography as="h6" component="h3">
            Canvas
          </Typography>
          {items.length > 0 && (
            <Typography as="p" className="text-sm" color="text.secondary">
              {items.length} item{items.length !== 1 ? 's' : ''}
            </Typography>
          )}
        </Box>

        {items.length === 0 ? (
          <Box
            className="text-center p-10 rounded-lg text-gray-500 dark:text-gray-400 border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 border-dashed" >
            <Typography as="p">
              Click on components in the library to add them to the canvas
            </Typography>
            <Typography as="p" className="text-sm" className="mt-2">
              Drag items to reorder them
            </Typography>
          </Box>
        ) : (
          items.map((item) => (
            <Box
              key={item.id}
              onClick={() => handleItemSelect(item.id)}
              onKeyDown={(e) => {
                if (e.key === 'Delete' || e.key === 'Backspace') {
                  removeItem(item.id);
                }
              }}
              tabIndex={0}
              className={`rounded m-1 bg-white dark:bg-gray-900 relative group ${
                selectedItem === item.id ? 'outline outline-2 outline-blue-600' : ''
              }`}
            >
              <DraggableItem
                id={item.id}
                type={item.type}
                componentProps={item.props || {}}
                onDragStart={handleDragStart}
                onDrop={handleDrop}
              />
              <Box
                className="delete-button absolute top-1 right-1 w-5 h-5 rounded-full bg-red-300 dark:bg-red-400 text-white flex items-center justify-center text-xs cursor-pointer opacity-0 transition-opacity duration-150 hover:bg-red-600 group-hover:opacity-100"
                onClick={(e) => {
                  e.stopPropagation();
                  removeItem(item.id);
                }}
                role="button"
                aria-label={`Remove item ${item.id}`}
              >
                ×
              </Box>
            </Box>
          ))
        )}
      </Box>
    </Box>
  );
};

export default DraggableCanvas;
