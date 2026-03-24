import { Provider, createStore, useSetAtom } from 'jotai';
import React, { useEffect } from 'react';

import { Canvas, updateDocumentAtom, type CanvasDocument } from '../../index';

import type { Meta, StoryObj } from '@storybook/react';

/**
 * Helper to create a CanvasDocument with complete CanvasCapabilities.
 */
function createStoryDocument(
  id: string,
  title: string,
  elements: Record<string, unknown> = {},
  elementOrder: string[] = []
): CanvasDocument {
  return {
    id,
    version: '1.0.0',
    title,
    description: `${title} story for interaction testing`,
    viewport: {
      center: { x: 0, y: 0 },
      zoom: 1,
    },
    elements,
    elementOrder,
    metadata: {},
    capabilities: {
      canEdit: true,
      canZoom: true,
      canPan: true,
      canSelect: true,
      canUndo: true,
      canRedo: true,
      canExport: false,
      canImport: false,
      canCollaborate: false,
      canPersist: false,
      allowedElementTypes: ['node', 'edge'],
    },
    createdAt: new Date(),
    updatedAt: new Date(),
  };
}

const meta: Meta<typeof Canvas> = {
  title: 'Canvas/Interactions',
  component: Canvas,
  parameters: {
    layout: 'fullscreen',
    docs: {
      description: {
        component: 'Canvas interaction stories: selection, dragging, edges, navigation.',
      },
    },
  },
  decorators: [
    (Story) => (
      <div style={{ width: '100%', height: '100vh' }}>
        <Story />
      </div>
    ),
  ],
  tags: ['autodocs'],
};

export default meta;
/**
 *
 */
type Story = StoryObj<typeof Canvas>;

/**
 * Wrapper to set up Jotai store and seed canvas data in a story.
 */
const CanvasWithProvider: React.FC<{
  initialDocument?: CanvasDocument;
}> = ({ initialDocument }) => {
  const store = createStore();
  const CanvasInner = () => {
    const setDocument = useSetAtom(updateDocumentAtom, { store });
    useEffect(() => {
      if (initialDocument) {
        setDocument(initialDocument);
      }
      // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [initialDocument]);
    return <Canvas />;
  };
  return (
    <Provider store={store}>
      <CanvasInner />
    </Provider>
  );
};

// ============================================
// Story 1: Select Single Node
// ============================================
export const SelectSingleNode: Story = {
  render: () => {
    const elements: Record<string, unknown> = {
      'node-1': {
        id: 'node-1',
        type: 'node',
        data: { label: 'Node 1' },
        bounds: { x: 250, y: 100, width: 120, height: 60 },
        updatedAt: new Date(),
      },
      'node-2': {
        id: 'node-2',
        type: 'node',
        data: { label: 'Node 2' },
        bounds: { x: 500, y: 200, width: 120, height: 60 },
        updatedAt: new Date(),
      },
    };
    const doc = createStoryDocument('select-single', 'Select Single Node', elements, ['node-1', 'node-2']);
    return <CanvasWithProvider initialDocument={doc} />;
  },
  parameters: {
    docs: {
      description: {
        story: 'Click a node to select it. Selection indicator shows which node is selected.',
      },
    },
  },
};

// ============================================
// Story 2: Select Multiple Nodes
// ============================================
export const SelectMultipleNodes: Story = {
  render: () => {
    const elements: Record<string, unknown> = {
      'node-1': {
        id: 'node-1',
        type: 'node',
        data: { label: 'Node 1' },
        bounds: { x: 200, y: 100, width: 120, height: 60 },
        updatedAt: new Date(),
      },
      'node-2': {
        id: 'node-2',
        type: 'node',
        data: { label: 'Node 2' },
        bounds: { x: 450, y: 100, width: 120, height: 60 },
        updatedAt: new Date(),
      },
      'node-3': {
        id: 'node-3',
        type: 'node',
        data: { label: 'Node 3' },
        bounds: { x: 325, y: 250, width: 120, height: 60 },
        updatedAt: new Date(),
      },
    };
    const doc = createStoryDocument('select-multiple', 'Select Multiple Nodes', elements, ['node-1', 'node-2', 'node-3']);
    return <CanvasWithProvider initialDocument={doc} />;
  },
  parameters: {
    docs: {
      description: {
        story: 'Ctrl+Click to select multiple nodes. All selected nodes are highlighted.',
      },
    },
  },
};

// ============================================
// Story 3: Drag Node with Grid Snap
// ============================================
export const DragNodeWithGridSnap: Story = {
  render: () => {
    const elements: Record<string, unknown> = {
      'node-1': {
        id: 'node-1',
        type: 'node',
        data: { label: 'Draggable Node' },
        bounds: { x: 250, y: 100, width: 120, height: 60 },
        updatedAt: new Date(),
      },
    };
    const doc = createStoryDocument('drag-snap', 'Drag Node with Grid Snap', elements, ['node-1']);
    return <CanvasWithProvider initialDocument={doc} />;
  },
  parameters: {
    docs: {
      description: {
        story: 'Drag the node around. It will snap to a 20px grid. Grid lines are shown in the background.',
      },
    },
  },
};

// ============================================
// Story 4: Drag Node Free Form
// ============================================
export const DragNodeFreeForm: Story = {
  render: () => {
    const elements: Record<string, unknown> = {
      'node-1': {
        id: 'node-1',
        type: 'node',
        data: { label: 'Draggable Node' },
        bounds: { x: 250, y: 100, width: 120, height: 60 },
        updatedAt: new Date(),
      },
    };
    const doc = createStoryDocument('drag-freeform', 'Drag Node Free Form', elements, ['node-1']);
    return <CanvasWithProvider initialDocument={doc} />;
  },
  parameters: {
    docs: {
      description: {
        story: 'Drag node with smooth continuous movement, no grid snapping.',
      },
    },
  },
};

// ============================================
// Story 5: Drag Multiple Nodes
// ============================================
export const DragMultipleNodes: Story = {
  render: () => {
    const elements: Record<string, unknown> = {
      'node-1': {
        id: 'node-1',
        type: 'node',
        data: { label: 'Node 1' },
        bounds: { x: 200, y: 100, width: 120, height: 60 },
        updatedAt: new Date(),
      },
      'node-2': {
        id: 'node-2',
        type: 'node',
        data: { label: 'Node 2' },
        bounds: { x: 450, y: 100, width: 120, height: 60 },
        updatedAt: new Date(),
      },
      'node-3': {
        id: 'node-3',
        type: 'node',
        data: { label: 'Node 3' },
        bounds: { x: 325, y: 250, width: 120, height: 60 },
        updatedAt: new Date(),
      },
    };
    const doc = createStoryDocument('drag-multiple', 'Drag Multiple Nodes', elements, ['node-1', 'node-2', 'node-3']);
    return <CanvasWithProvider initialDocument={doc} />;
  },
  parameters: {
    docs: {
      description: {
        story: 'Select multiple nodes and drag all together. Relative positions are preserved.',
      },
    },
  },
};

// ============================================
// Story 6: Edge Preview
// ============================================
export const EdgePreview: Story = {
  render: () => {
    const elements: Record<string, unknown> = {
      'node-1': {
        id: 'node-1',
        type: 'node',
        data: { label: 'Source' },
        bounds: { x: 200, y: 150, width: 120, height: 60 },
        updatedAt: new Date(),
      },
      'node-2': {
        id: 'node-2',
        type: 'node',
        data: { label: 'Target' },
        bounds: { x: 500, y: 150, width: 120, height: 60 },
        updatedAt: new Date(),
      },
    };
    const doc = createStoryDocument('edge-preview', 'Edge Preview', elements, ['node-1', 'node-2']);
    return <CanvasWithProvider initialDocument={doc} />;
  },
  parameters: {
    docs: {
      description: {
        story: 'Drag from output handle on source node. Preview line follows cursor.',
      },
    },
  },
};

// ============================================
// Story 7: Edge Completion
// ============================================
export const EdgeCompletion: Story = {
  render: () => {
    const elements: Record<string, unknown> = {
      'node-1': {
        id: 'node-1',
        type: 'node',
        data: { label: 'Source' },
        bounds: { x: 200, y: 150, width: 120, height: 60 },
        updatedAt: new Date(),
      },
      'node-2': {
        id: 'node-2',
        type: 'node',
        data: { label: 'Target' },
        bounds: { x: 500, y: 150, width: 120, height: 60 },
        updatedAt: new Date(),
      },
      'edge-1': {
        id: 'edge-1',
        type: 'edge',
        data: { label: 'connected' },
        sourceId: 'node-1',
        targetId: 'node-2',
        updatedAt: new Date(),
      },
    };
    const doc = createStoryDocument('edge-completion', 'Edge Completion', elements, ['node-1', 'node-2', 'edge-1']);
    return <CanvasWithProvider initialDocument={doc} />;
  },
  parameters: {
    docs: {
      description: {
        story: 'Edge created between nodes. Shows connection and labels.',
      },
    },
  },
};

// ============================================
// Story 8: Validate Edge Connection
// ============================================
export const ValidateEdgeConnection: Story = {
  render: () => {
    const elements: Record<string, unknown> = {
      'node-1': {
        id: 'node-1',
        type: 'node',
        data: { label: 'Source' },
        bounds: { x: 200, y: 150, width: 120, height: 60 },
        updatedAt: new Date(),
      },
      'node-2': {
        id: 'node-2',
        type: 'node',
        data: { label: 'Invalid Target' },
        bounds: { x: 500, y: 150, width: 120, height: 60 },
        updatedAt: new Date(),
      },
    };
    const doc = createStoryDocument('edge-validate', 'Validate Edge Connection', elements, ['node-1', 'node-2']);
    return <CanvasWithProvider initialDocument={doc} />;
  },
  parameters: {
    docs: {
      description: {
        story: 'Invalid drop zone highlighted in red. Connection validation prevents invalid edges.',
      },
    },
  },
};

// ============================================
// Story 9: Pan Canvas
// ============================================
export const PanCanvas: Story = {
  render: () => {
    const elements: Record<string, unknown> = {
      'node-1': {
        id: 'node-1',
        type: 'node',
        data: { label: 'Node 1' },
        bounds: { x: 250, y: 100, width: 120, height: 60 },
        updatedAt: new Date(),
      },
      'node-2': {
        id: 'node-2',
        type: 'node',
        data: { label: 'Node 2' },
        bounds: { x: 500, y: 300, width: 120, height: 60 },
        updatedAt: new Date(),
      },
      'node-3': {
        id: 'node-3',
        type: 'node',
        data: { label: 'Node 3' },
        bounds: { x: 100, y: 400, width: 120, height: 60 },
        updatedAt: new Date(),
      },
    };
    const doc = createStoryDocument('pan-canvas', 'Pan Canvas', elements, ['node-1', 'node-2', 'node-3']);
    return <CanvasWithProvider initialDocument={doc} />;
  },
  parameters: {
    docs: {
      description: {
        story: 'Click and drag on canvas background to pan the view.',
      },
    },
  },
};

// ============================================
// Story 10: Zoom with Mouse Wheel
// ============================================
export const ZoomWithMouseWheel: Story = {
  render: () => {
    const elements: Record<string, unknown> = {
      'node-1': {
        id: 'node-1',
        type: 'node',
        data: { label: 'Zoom this!' },
        bounds: { x: 250, y: 150, width: 120, height: 60 },
        updatedAt: new Date(),
      },
    };
    const doc = createStoryDocument('zoom-wheel', 'Zoom with Mouse Wheel', elements, ['node-1']);
    return <CanvasWithProvider initialDocument={doc} />;
  },
  parameters: {
    docs: {
      description: {
        story: 'Scroll mouse wheel up to zoom in, down to zoom out. Zoom limits are 0.5x to 3x.',
      },
    },
  },
};

// ============================================
// Story 11: Zoom with Controls
// ============================================
export const ZoomWithControls: Story = {
  render: () => {
    const elements: Record<string, unknown> = {
      'node-1': {
        id: 'node-1',
        type: 'node',
        data: { label: 'Use Toolbar' },
        bounds: { x: 250, y: 150, width: 120, height: 60 },
        updatedAt: new Date(),
      },
    };
    const doc = createStoryDocument('zoom-controls', 'Zoom with Controls', elements, ['node-1']);
    return <CanvasWithProvider initialDocument={doc} />;
  },
  parameters: {
    docs: {
      description: {
        story: 'Click zoom buttons in toolbar to zoom in/out.',
      },
    },
  },
};

// ============================================
// Story 12: Fit View Button
// ============================================
export const FitViewButton: Story = {
  render: () => {
    const elements: Record<string, unknown> = {
      'node-1': {
        id: 'node-1',
        type: 'node',
        data: { label: 'Far Left' },
        bounds: { x: 0, y: 0, width: 120, height: 60 },
        updatedAt: new Date(),
      },
      'node-2': {
        id: 'node-2',
        type: 'node',
        data: { label: 'Far Right' },
        bounds: { x: 800, y: 600, width: 120, height: 60 },
        updatedAt: new Date(),
      },
    };
    const doc = createStoryDocument('fit-view', 'Fit View Button', elements, ['node-1', 'node-2']);
    return <CanvasWithProvider initialDocument={doc} />;
  },
  parameters: {
    docs: {
      description: {
        story: 'Click fit-view button to zoom and pan to show all nodes.',
      },
    },
  },
};

// ============================================
// Story 13: Deselect by Clicking Background
// ============================================
export const DeselectByClickingBackground: Story = {
  render: () => {
    const elements: Record<string, unknown> = {
      'node-1': {
        id: 'node-1',
        type: 'node',
        data: { label: 'Selected Node' },
        bounds: { x: 250, y: 150, width: 120, height: 60 },
        updatedAt: new Date(),
      },
    };
    const doc = createStoryDocument('deselect-bg', 'Deselect by Clicking Background', elements, ['node-1']);
    return <CanvasWithProvider initialDocument={doc} />;
  },
  parameters: {
    docs: {
      description: {
        story: 'Click on empty canvas area to deselect. Selection indicator disappears.',
      },
    },
  },
};

// ============================================
// Story 14: Marquee Selection
// ============================================
export const MarqueeSelection: Story = {
  render: () => {
    const elements: Record<string, unknown> = {
      'node-1': {
        id: 'node-1',
        type: 'node',
        data: { label: 'Node 1' },
        bounds: { x: 150, y: 100, width: 120, height: 60 },
        updatedAt: new Date(),
      },
      'node-2': {
        id: 'node-2',
        type: 'node',
        data: { label: 'Node 2' },
        bounds: { x: 350, y: 100, width: 120, height: 60 },
        updatedAt: new Date(),
      },
      'node-3': {
        id: 'node-3',
        type: 'node',
        data: { label: 'Node 3' },
        bounds: { x: 250, y: 300, width: 120, height: 60 },
        updatedAt: new Date(),
      },
    };
    const doc = createStoryDocument('marquee-select', 'Marquee Selection', elements, ['node-1', 'node-2', 'node-3']);
    return <CanvasWithProvider initialDocument={doc} />;
  },
  parameters: {
    docs: {
      description: {
        story: 'Drag selection box around multiple nodes to select them all.',
      },
    },
  },
};

// ============================================
// Story 15: Right Click Context Menu
// ============================================
export const RightClickContextMenu: Story = {
  render: () => {
    const elements: Record<string, unknown> = {
      'node-1': {
        id: 'node-1',
        type: 'node',
        data: { label: 'Right Click Me' },
        bounds: { x: 250, y: 150, width: 120, height: 60 },
        updatedAt: new Date(),
      },
    };
    const doc = createStoryDocument('context-menu', 'Right Click Context Menu', elements, ['node-1']);
    return <CanvasWithProvider initialDocument={doc} />;
  },
  parameters: {
    docs: {
      description: {
        story: 'Right-click on node to show context menu with actions.',
      },
    },
  },
};

// ============================================
// Story 16: Keyboard Navigation
// ============================================
export const KeyboardNavigation: Story = {
  render: () => {
    const elements: Record<string, unknown> = {
      'node-1': {
        id: 'node-1',
        type: 'node',
        data: { label: 'Node 1' },
        bounds: { x: 200, y: 150, width: 120, height: 60 },
        updatedAt: new Date(),
      },
      'node-2': {
        id: 'node-2',
        type: 'node',
        data: { label: 'Node 2' },
        bounds: { x: 400, y: 150, width: 120, height: 60 },
        updatedAt: new Date(),
      },
    };
    const doc = createStoryDocument('keyboard-nav', 'Keyboard Navigation', elements, ['node-1', 'node-2']);
    return <CanvasWithProvider initialDocument={doc} />;
  },
  parameters: {
    docs: {
      description: {
        story: 'Tab: Navigate between nodes | Arrow keys: Move selected node | Delete: Remove node',
      },
    },
  },
};

// ============================================
// Story 17: Snap to Alignment Guides
// ============================================
export const SnapToAlignmentGuides: Story = {
  render: () => {
    const elements: Record<string, unknown> = {
      'node-1': {
        id: 'node-1',
        type: 'node',
        data: { label: 'Node 1' },
        bounds: { x: 200, y: 150, width: 120, height: 60 },
        updatedAt: new Date(),
      },
      'node-2': {
        id: 'node-2',
        type: 'node',
        data: { label: 'Node 2' },
        bounds: { x: 400, y: 200, width: 120, height: 60 },
        updatedAt: new Date(),
      },
    };
    const doc = createStoryDocument('alignment-guides', 'Snap to Alignment Guides', elements, ['node-1', 'node-2']);
    return <CanvasWithProvider initialDocument={doc} />;
  },
  parameters: {
    docs: {
      description: {
        story: 'Drag nodes near guides to snap for alignment. Guides show when nodes are aligned.',
      },
    },
  },
};

// ============================================
// Story 18: Mini Map / Overview
// ============================================
export const MiniMapOverview: Story = {
  render: () => {
    const elements: Record<string, unknown> = {
      'node-1': {
        id: 'node-1',
        type: 'node',
        data: { label: 'Node 1' },
        bounds: { x: 250, y: 100, width: 120, height: 60 },
        updatedAt: new Date(),
      },
      'node-2': {
        id: 'node-2',
        type: 'node',
        data: { label: 'Node 2' },
        bounds: { x: 500, y: 300, width: 120, height: 60 },
        updatedAt: new Date(),
      },
      'node-3': {
        id: 'node-3',
        type: 'node',
        data: { label: 'Node 3' },
        bounds: { x: 100, y: 400, width: 120, height: 60 },
        updatedAt: new Date(),
      },
    };
    const doc = createStoryDocument('minimap', 'Mini Map / Overview', elements, ['node-1', 'node-2', 'node-3']);
    return <CanvasWithProvider initialDocument={doc} />;
  },
  parameters: {
    docs: {
      description: {
        story: 'Mini map shown in corner displays all nodes and current viewport.',
      },
    },
  },
};

// ============================================
// Story 19: Undo Redo History
// ============================================
export const UndoRedoHistory: Story = {
  render: () => {
    const elements: Record<string, unknown> = {
      'node-1': {
        id: 'node-1',
        type: 'node',
        data: { label: 'Modified Node' },
        bounds: { x: 250, y: 150, width: 120, height: 60 },
        updatedAt: new Date(),
      },
    };
    const doc = createStoryDocument('undo-redo', 'Undo Redo History', elements, ['node-1']);
    return <CanvasWithProvider initialDocument={doc} />;
  },
  parameters: {
    docs: {
      description: {
        story: 'Undo/Redo buttons control command history. Previous actions can be reversed.',
      },
    },
  },
};

// ============================================
// Story 20: Copy Paste
// ============================================
export const CopyPaste: Story = {
  render: () => {
    const elements: Record<string, unknown> = {
      'node-1': {
        id: 'node-1',
        type: 'node',
        data: { label: 'Original Node' },
        bounds: { x: 250, y: 150, width: 120, height: 60 },
        updatedAt: new Date(),
      },
    };
    const doc = createStoryDocument('copy-paste', 'Copy Paste', elements, ['node-1']);
    return <CanvasWithProvider initialDocument={doc} />;
  },
  parameters: {
    docs: {
      description: {
        story: 'Select node and use Ctrl+C/Ctrl+V to copy and paste. Duplicated nodes get new IDs.',
      },
    },
  },
};
