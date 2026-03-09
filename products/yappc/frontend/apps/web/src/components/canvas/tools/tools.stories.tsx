import { Box } from '@ghatana/ui';

import { AccessibilityTool } from './AccessibilityTool';
import { CommandPalette } from './CommandPalette';
import { LayoutTool } from './LayoutTool';

import type { CommandAction } from './CommandPalette';
import type { User } from '../../../services/collaboration/types';
import type { Meta, StoryObj } from '@storybook/react';

// Mock canvas context for stories
const mockContext = {
  getCanvasState: () => ({
    elements: [
      {
        id: 'node-1',
        kind: 'node',
        type: 'api',
        position: { x: 100, y: 100 },
        size: { width: 150, height: 80 },
        data: { label: 'API Gateway' },
      },
      {
        id: 'node-2',
        kind: 'node',
        type: 'data',
        position: { x: 300, y: 200 },
        size: { width: 150, height: 80 },
        data: { label: 'Database' },
      },
    ],
    connections: [],
    viewport: { x: 0, y: 0, zoom: 1 },
  }),
  updateCanvasState: () => {},
  addElement: () => 'new-id',
  updateElement: () => {},
  deleteElement: () => {},
  duplicateElement: () => 'dup-id',
  getSelection: () => [],
  setSelection: () => {},
  addToSelection: () => {},
  clearSelection: () => {},
  addConnection: () => 'conn-id',
  updateConnection: () => {},
  deleteConnection: () => {},
  getViewport: () => ({ x: 0, y: 0, zoom: 1 }),
  setViewport: () => {},
  fitView: () => {},
  createLayer: () => 'layer-id',
  deleteLayer: () => {},
  moveToLayer: () => {},
  exportSnapshot: () => '{}',
  importSnapshot: () => {},
  undo: () => {},
  redo: () => {},
  on: () => () => {},
  emit: () => {},
};

const mockUser: User = {
  id: 'user-1',
  name: 'John Doe',
  email: 'john@example.com',
  color: '#2196f3',
};

// Accessibility Tool Stories
const AccessibilityMeta: Meta<typeof AccessibilityTool> = {
  title: 'Canvas/Tools/AccessibilityTool',
  parameters: {
    docs: {
      description: {
        component: 'Tool for checking canvas accessibility issues and providing recommendations.',
      },
    },
  },
};

export const AccessibilityPanel: StoryObj = {
  render: () => {
    const tool = new AccessibilityTool();
    tool.initialize(mockContext);
    tool.onActivate?.(mockContext);
    
    return (
      <Box className="w-[400px] h-[600px]">
        {tool.renderPanel?.(mockContext)}
      </Box>
    );
  },
  parameters: {
    docs: {
      description: {
        story: 'The accessibility tool panel showing detected issues and recommendations.',
      },
    },
  },
};

// Layout Tool Stories
const LayoutMeta: Meta<typeof LayoutTool> = {
  title: 'Canvas/Tools/LayoutTool',
  parameters: {
    docs: {
      description: {
        component: 'Tool for automatically arranging canvas elements using different layout algorithms.',
      },
    },
  },
};

export const LayoutPanel: StoryObj = {
  render: () => {
    const tool = new LayoutTool();
    tool.initialize(mockContext);
    
    return (
      <Box className="w-[300px]">
        {tool.renderPanel?.(mockContext)}
      </Box>
    );
  },
  parameters: {
    docs: {
      description: {
        story: 'The layout tool panel with options for grid and hierarchical layouts.',
      },
    },
  },
};

// Command Palette Stories
const CommandPaletteMeta: Meta<typeof CommandPalette> = {
  title: 'Canvas/Tools/CommandPalette',
  component: CommandPalette,
  parameters: {
    docs: {
      description: {
        component: 'Command palette for quick access to canvas tools and actions.',
      },
    },
  },
};

const sampleActions: CommandAction[] = [
  {
    id: 'add-node',
    label: 'Add Node',
    description: 'Add a new node to the canvas',
    category: 'Create',
    keywords: ['node', 'add', 'create'],
    shortcut: 'Ctrl+N',
    action: () => console.log('Add node'),
  },
  {
    id: 'accessibility-check',
    label: 'Run Accessibility Check',
    description: 'Check canvas for accessibility issues',
    category: 'Tools',
    keywords: ['accessibility', 'a11y', 'check'],
    shortcut: 'Ctrl+Shift+A',
    action: () => console.log('Accessibility check'),
  },
  {
    id: 'auto-layout',
    label: 'Auto Layout',
    description: 'Automatically arrange elements',
    category: 'Tools',
    keywords: ['layout', 'arrange', 'organize'],
    shortcut: 'Ctrl+L',
    action: () => console.log('Auto layout'),
  },
  {
    id: 'export-json',
    label: 'Export as JSON',
    description: 'Export canvas to JSON format',
    category: 'Export',
    keywords: ['export', 'json', 'save'],
    shortcut: 'Ctrl+E',
    action: () => console.log('Export JSON'),
  },
  {
    id: 'fit-view',
    label: 'Fit to View',
    description: 'Fit all elements in the viewport',
    category: 'View',
    keywords: ['fit', 'view', 'zoom'],
    shortcut: 'Ctrl+0',
    action: () => console.log('Fit view'),
  },
];

export const CommandPaletteOpen: StoryObj<typeof CommandPalette> = {
  args: {
    actions: sampleActions,
    open: true,
    onClose: () => console.log('Close palette'),
  },
  parameters: {
    docs: {
      description: {
        story: 'The command palette in open state with sample actions grouped by category.',
      },
    },
  },
};

export const CommandPaletteWithSearch: StoryObj<typeof CommandPalette> = {
  args: {
    actions: sampleActions,
    open: true,
    onClose: () => console.log('Close palette'),
  },
  parameters: {
    docs: {
      description: {
        story: 'Command palette with search functionality. Try typing "layout" or "export".',
      },
    },
  },
};

export default AccessibilityMeta;
