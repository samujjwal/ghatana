import { seedScenarios } from '@ghatana/yappc-mocks';
import { Provider, createStore, useSetAtom } from 'jotai';
import React, { useEffect } from 'react';

import { Canvas, updateDocumentAtom, type CanvasDocument } from '../../index';

import type { Meta, StoryObj } from '@storybook/react';

/**
 * Transform seed data into the unified CanvasDocument format.
 * Used in stories to avoid the deprecated compatibility setter.
 */
function transformSeedToDocument(seed: unknown): CanvasDocument {
  const nodes = seed?.nodes || [];
  const edges = seed?.edges || [];

  const elements: Record<string, unknown> = {};
  const elementOrder: string[] = [];

  // Map nodes to elements
  (nodes || []).forEach((n: unknown, idx: number) => {
    const id = n.id || `node-${idx}`;
    elements[id] = {
      id,
      type: 'node',
      data: n.data || { label: n.data?.label || `Node ${idx}` },
      bounds: n.bounds || {
        x: n.position?.x ?? 0,
        y: n.position?.y ?? 0,
        width: 160,
        height: 48,
      },
      updatedAt: n.updatedAt ? new Date(n.updatedAt) : new Date(),
    };
    elementOrder.push(id);
  });

  // Map edges to elements
  (edges || []).forEach((e: unknown, idx: number) => {
    const id = e.id || `edge-${idx}`;
    elements[id] = {
      id,
      type: 'edge',
      data: e.data || { label: e.label },
      sourceId: e.source,
      targetId: e.target,
      updatedAt: e.updatedAt ? new Date(e.updatedAt) : new Date(),
    };
    elementOrder.push(id);
  });

  const viewport = seed?.viewport || { x: 0, y: 0, zoom: 1 };

  return {
    id: seed?.canvasId || 'story-canvas',
    version: '1.0.0',
    title: seed?.title || 'Canvas Story',
    viewport: {
      center: { x: viewport.x, y: viewport.y },
      zoom: viewport.zoom || 1,
    },
    elements,
    elementOrder,
    metadata: seed?.metadata || {},
    capabilities: {
      canEdit: true,
      canZoom: true,
      canPan: true,
      canSelect: true,
    },
    createdAt: new Date(),
    updatedAt: new Date(),
  };
}

const meta: Meta<typeof Canvas> = {
  title: 'Canvas/Basic',
  component: Canvas,
  parameters: {
    layout: 'fullscreen',
    docs: {
      description: {
        component:
          'The core Canvas component for rendering and editing diagrams. Supports nodes, edges, groups, and interactions.',
      },
    },
  },
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
  children?: React.ReactNode;
}> = ({ initialDocument, children }) => {
  const store = createStore();

  return (
    <Provider store={store}>
      <CanvasSetup initialDocument={initialDocument} />
      {children}
    </Provider>
  );
};

/**
 * Helper to seed the canvas with initial data.
 */
const CanvasSetup: React.FC<{ initialDocument?: CanvasDocument }> = ({ initialDocument }) => {
  const setDocument = useSetAtom(updateDocumentAtom);

  useEffect(() => {
    if (initialDocument) {
      // Defer seeding to after mount to avoid effect churn
      // Use setDocument only once - don't add it to dependencies to prevent infinite loops
      setTimeout(() => {
        setDocument(initialDocument);
      }, 0);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [initialDocument]);

  return null;
};

// ============================================================================
// BASIC RENDERING STORIES
// ============================================================================

/**
 * Empty canvas with no elements.
 * Verifies: Grid background, zoom controls, empty state.
 */
export const Empty: Story = {
  render: () => (
    <CanvasWithProvider>
      <Canvas
        elements={{}}
        elementOrder={[]}
        onElementsChange={() => {}}
        viewport={{ center: { x: 0, y: 0 }, zoom: 1 }}
      />
    </CanvasWithProvider>
  ),
  parameters: {
    docs: {
      description: {
        story: 'Empty canvas with no elements. Tests grid rendering and zoom controls.',
      },
    },
  },
};

/**
 * Canvas with a single node.
 * Verifies: Node rendering, selection, properties.
 */
export const SingleNode: Story = {
  render: () => {
    const elements: Record<string, unknown> = {
      'node-1': {
        id: 'node-1',
        type: 'node',
        data: { label: 'Start Process' },
        bounds: { x: 100, y: 100, width: 160, height: 48 },
        updatedAt: new Date(),
      },
    };

    return (
      <CanvasWithProvider initialDocument={transformSeedToDocument({ nodes: [{ id: 'node-1', data: { label: 'Start' } }] })}>
        <Canvas
          elements={elements}
          elementOrder={['node-1']}
          onElementsChange={() => {}}
          viewport={{ center: { x: 0, y: 0 }, zoom: 1 }}
        />
      </CanvasWithProvider>
    );
  },
  parameters: {
    docs: {
      description: {
        story: 'Canvas with a single node. Tests node rendering and selection.',
      },
    },
  },
};

/**
 * Canvas with multiple nodes arranged in a grid.
 * Verifies: Multiple node rendering, z-index ordering.
 */
export const MultipleNodes: Story = {
  render: () => {
    const nodes = [
      { id: 'node-1', position: { x: 100, y: 100 }, data: { label: 'Node A' } },
      { id: 'node-2', position: { x: 300, y: 100 }, data: { label: 'Node B' } },
      { id: 'node-3', position: { x: 200, y: 250 }, data: { label: 'Node C' } },
      { id: 'node-4', position: { x: 400, y: 250 }, data: { label: 'Node D' } },
    ];

    return (
      <CanvasWithProvider initialDocument={transformSeedToDocument({ nodes })}>
        <Canvas
          elements={{}}
          elementOrder={[]}
          onElementsChange={() => {}}
          viewport={{ center: { x: 0, y: 0 }, zoom: 1 }}
        />
      </CanvasWithProvider>
    );
  },
  parameters: {
    docs: {
      description: {
        story: 'Canvas with multiple nodes arranged in a grid pattern.',
      },
    },
  },
};

/**
 * Canvas with nodes and edges connecting them.
 * Verifies: Edge rendering, connection validation, edge properties.
 */
export const WithEdges: Story = {
  render: () => {
    const nodes = [
      { id: 'node-1', position: { x: 100, y: 100 }, data: { label: 'Start' } },
      { id: 'node-2', position: { x: 300, y: 100 }, data: { label: 'Process' } },
      { id: 'node-3', position: { x: 500, y: 100 }, data: { label: 'End' } },
    ];

    const edges = [
      { id: 'edge-1', source: 'node-1', target: 'node-2', label: 'flow' },
      { id: 'edge-2', source: 'node-2', target: 'node-3', label: 'complete' },
    ];

    return (
      <CanvasWithProvider initialDocument={transformSeedToDocument({ nodes, edges })}>
        <Canvas
          elements={{}}
          elementOrder={[]}
          onElementsChange={() => {}}
          viewport={{ center: { x: 0, y: 0 }, zoom: 1 }}
        />
      </CanvasWithProvider>
    );
  },
  parameters: {
    docs: {
      description: {
        story: 'Canvas with nodes connected by edges.',
      },
    },
  },
};

/**
 * Canvas with grouped elements.
 * Verifies: Group containers, nested elements, group styling.
 */
export const WithGroups: Story = {
  render: () => {
    const nodes = [
      {
        id: 'group-1',
        position: { x: 100, y: 100 },
        data: { label: 'Group A', isGroup: true },
        bounds: { x: 100, y: 100, width: 300, height: 200 },
      },
      {
        id: 'node-1',
        position: { x: 120, y: 120 },
        data: { label: 'Item 1' },
        parentId: 'group-1',
      },
      {
        id: 'node-2',
        position: { x: 250, y: 120 },
        data: { label: 'Item 2' },
        parentId: 'group-1',
      },
    ];

    return (
      <CanvasWithProvider initialDocument={transformSeedToDocument({ nodes })}>
        <Canvas
          elements={{}}
          elementOrder={[]}
          onElementsChange={() => {}}
          viewport={{ center: { x: 0, y: 0 }, zoom: 1 }}
        />
      </CanvasWithProvider>
    );
  },
  parameters: {
    docs: {
      description: {
        story: 'Canvas with grouped elements.',
      },
    },
  },
};

// ============================================================================
// SEED SCENARIO STORIES (Data-driven)
// ============================================================================

/**
 * Small diagram with seed data.
 * Verifies: Seed loading, viewport adjustment.
 */
export const SmallDiagram: Story = {
  render: () => (
    <CanvasWithProvider initialDocument={transformSeedToDocument(seedScenarios.small())}>
      <Canvas
        elements={{}}
        elementOrder={[]}
        onElementsChange={() => {}}
        viewport={{ center: { x: 0, y: 0 }, zoom: 1 }}
      />
    </CanvasWithProvider>
  ),
  parameters: {
    docs: {
      description: {
        story: 'Small diagram loaded from seed scenarios.',
      },
    },
  },
};

/**
 * Medium diagram with seed data.
 * Verifies: Multiple nodes and edges, layout optimization.
 */
export const MediumDiagram: Story = {
  render: () => (
    <CanvasWithProvider initialDocument={transformSeedToDocument(seedScenarios.medium())}>
      <Canvas
        elements={{}}
        elementOrder={[]}
        onElementsChange={() => {}}
        viewport={{ center: { x: 0, y: 0 }, zoom: 0.8 }}
      />
    </CanvasWithProvider>
  ),
  parameters: {
    docs: {
      description: {
        story: 'Medium diagram with seed data. Tests layout with more complex structures.',
      },
    },
  },
};

/**
 * Large diagram with seed data.
 * Verifies: Performance with many elements, virtualization.
 */
export const LargeDiagram: Story = {
  render: () => (
    <CanvasWithProvider initialDocument={transformSeedToDocument(seedScenarios.large())}>
      <Canvas
        elements={{}}
        elementOrder={[]}
        onElementsChange={() => {}}
        viewport={{ center: { x: 0, y: 0 }, zoom: 0.6 }}
      />
    </CanvasWithProvider>
  ),
  parameters: {
    docs: {
      description: {
        story: 'Large diagram for performance testing. Should maintain smooth interaction.',
      },
    },
  },
};

/**
 * Microservices architecture diagram.
 * Verifies: Complex interconnected system, many edges.
 */
export const MicroservicesArchitecture: Story = {
  render: () => (
    <CanvasWithProvider initialDocument={transformSeedToDocument(seedScenarios.microservices())}>
      <Canvas
        elements={{}}
        elementOrder={[]}
        onElementsChange={() => {}}
        viewport={{ center: { x: 0, y: 0 }, zoom: 0.7 }}
      />
    </CanvasWithProvider>
  ),
  parameters: {
    docs: {
      description: {
        story: 'Microservices architecture diagram showing service interactions.',
      },
    },
  },
};

// ============================================================================
// VIEWPORT & ZOOM STORIES
// ============================================================================

/**
 * Canvas zoomed in (200%).
 * Verifies: Zoom level, element scaling, readable text.
 */
export const ZoomedIn: Story = {
  render: () => {
    const nodes = [
      { id: 'node-1', position: { x: 100, y: 100 }, data: { label: 'Zoomed In' } },
      { id: 'node-2', position: { x: 300, y: 100 }, data: { label: 'View' } },
    ];

    return (
      <CanvasWithProvider initialDocument={transformSeedToDocument({ nodes, viewport: { x: 0, y: 0, zoom: 2 } })}>
        <Canvas
          elements={{}}
          elementOrder={[]}
          onElementsChange={() => {}}
          viewport={{ center: { x: 0, y: 0 }, zoom: 2 }}
        />
      </CanvasWithProvider>
    );
  },
  parameters: {
    docs: {
      description: {
        story: 'Canvas zoomed in to 200%.',
      },
    },
  },
};

/**
 * Canvas zoomed out (50%).
 * Verifies: Zoom out limits, overview rendering.
 */
export const ZoomedOut: Story = {
  render: () => {
    const nodes = [
      { id: 'node-1', position: { x: 100, y: 100 }, data: { label: 'Zoomed Out' } },
      { id: 'node-2', position: { x: 300, y: 100 }, data: { label: 'View' } },
      { id: 'node-3', position: { x: 500, y: 100 }, data: { label: 'Test' } },
    ];

    return (
      <CanvasWithProvider initialDocument={transformSeedToDocument({ nodes, viewport: { x: 0, y: 0, zoom: 0.5 } })}>
        <Canvas
          elements={{}}
          elementOrder={[]}
          onElementsChange={() => {}}
          viewport={{ center: { x: 0, y: 0 }, zoom: 0.5 }}
        />
      </CanvasWithProvider>
    );
  },
  parameters: {
    docs: {
      description: {
        story: 'Canvas zoomed out to 50%.',
      },
    },
  },
};

// ============================================================================
// STATE MANAGEMENT STORIES
// ============================================================================

/**
 * Controlled canvas with external state.
 * Verifies: Props sync, onChange callbacks.
 */
export const Controlled: Story = {
  render: () => {
    const [elements, setElements] = React.useState<Record<string, unknown>>({
      'node-1': {
        id: 'node-1',
        type: 'node',
        data: { label: 'Controlled' },
        bounds: { x: 100, y: 100, width: 160, height: 48 },
        updatedAt: new Date(),
      },
    });

    const handleChange = (newElements: Record<string, unknown>) => {
      setElements(newElements);
    };

    return (
      <CanvasWithProvider>
        <Canvas
          elements={elements}
          elementOrder={Object.keys(elements)}
          onElementsChange={handleChange}
          viewport={{ center: { x: 0, y: 0 }, zoom: 1 }}
        />
      </CanvasWithProvider>
    );
  },
  parameters: {
    docs: {
      description: {
        story: 'Controlled canvas with external state management.',
      },
    },
  },
};

/**
 * Dark theme canvas.
 * Verifies: Theme switching, contrast levels.
 */
export const DarkTheme: Story = {
  render: () => (
    <div style={{ background: '#1a1a1a', color: '#fff', minHeight: '100vh' }}>
      <CanvasWithProvider initialDocument={transformSeedToDocument(seedScenarios.small())}>
        <Canvas
          elements={{}}
          elementOrder={[]}
          onElementsChange={() => {}}
          viewport={{ center: { x: 0, y: 0 }, zoom: 1 }}
        />
      </CanvasWithProvider>
    </div>
  ),
  parameters: {
    docs: {
      description: {
        story: 'Canvas with dark theme styling.',
      },
    },
  },
};
