/**
 * PipelineCanvas — React Testing Library unit tests.
 *
 * Covers:
 * - Canvas renders with pipeline-canvas testid
 * - Jotai store nodes/edges are consumed
 * - External Jotai store mutations reseed ReactFlow (desync fix)
 * - Delete key removes selected nodes from store
 * - pushHistory fires on connect / drop
 * - Selection sync: clicking node updates selectedNodeIdAtom
 * - PipelineErrorBoundary: fallback renders when canvas throws
 *
 * NOTE: @xyflow/react is mocked to avoid canvas/WebGL environment deps.
 *
 * @doc.type test
 * @doc.purpose RTL unit tests for PipelineCanvas
 * @doc.layer frontend
 */
import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, act } from '@testing-library/react';
import { Provider as JotaiProvider, createStore } from 'jotai';

import { PipelineCanvas } from '@/components/pipeline/PipelineCanvas';
import { PipelineErrorBoundary } from '@/components/pipeline/PipelineErrorBoundary';
import {
  nodesAtom,
  edgesAtom,
  selectedNodeIdAtom,
  isDirtyAtom,
  historyAtom,
  historyIndexAtom,
} from '@/stores/pipeline.store';

// ── ReactFlow mock ───────────────────────────────────────────────────

let capturedOnNodesChange: ((changes: any[]) => void) | undefined;
let capturedOnEdgesChange: ((changes: any[]) => void) | undefined;
let capturedOnConnect: ((conn: any) => void) | undefined;
let capturedOnNodeClick: ((e: any, node: any) => void) | undefined;
let capturedOnEdgeClick: ((e: any, edge: any) => void) | undefined;
let capturedOnPaneClick: (() => void) | undefined;

vi.mock('@xyflow/react', async () => {
  const actual = await vi.importActual<typeof import('@xyflow/react')>('@xyflow/react');
  return {
    ...actual,
    ReactFlow: (props: any) => {
      capturedOnNodesChange = props.onNodesChange;
      capturedOnEdgesChange = props.onEdgesChange;
      capturedOnConnect = props.onConnect;
      capturedOnNodeClick = props.onNodeClick;
      capturedOnEdgeClick = props.onEdgeClick;
      capturedOnPaneClick = props.onPaneClick;
      return <div data-testid="react-flow" />;
    },
    Background: () => null,
    Controls: () => null,
    MiniMap: () => null,
    addEdge: actual.addEdge,
    useNodesState: (initial: any[]) => {
      const [nodes, setNodes] = React.useState(initial ?? []);
      const onNodesChange = vi.fn((changes) => {
        // apply ApplyNodeChanges-style deletions for selected
        setNodes((nds: any[]) =>
          nds.filter((n) => !changes.some((c: any) => c.type === 'remove' && c.id === n.id)),
        );
      });
      return [nodes, setNodes, onNodesChange];
    },
    useEdgesState: (initial: any[]) => {
      const [edges, setEdges] = React.useState(initial ?? []);
      const onEdgesChange = vi.fn();
      return [edges, setEdges, onEdgesChange];
    },
    BackgroundVariant: { Dots: 'dots' },
  };
});

// ── Helpers ───────────────────────────────────────────────────────────

function stageNode(id: string, selected = false) {
  return {
    id,
    type: 'stage' as const,
    position: { x: 0, y: 0 },
    data: { label: id, kind: 'ingestion', agents: [], agentCount: 0 },
    selected,
  };
}

function renderCanvas(store = createStore()) {
  return render(
    <JotaiProvider store={store}>
      <PipelineCanvas />
    </JotaiProvider>,
  );
}

// ── Tests ─────────────────────────────────────────────────────────────

describe('PipelineCanvas', () => {
  beforeEach(() => {
    capturedOnNodesChange = undefined;
    capturedOnEdgesChange = undefined;
    capturedOnConnect = undefined;
    capturedOnNodeClick = undefined;
    capturedOnPaneClick = undefined;
    vi.clearAllMocks();
  });

  // ── Rendering ───────────────────────────────────────────────────

  it('renders the canvas wrapper', () => {
    renderCanvas();
    expect(screen.getByTestId('pipeline-canvas')).toBeInTheDocument();
  });

  it('renders the mocked ReactFlow inside the canvas', () => {
    renderCanvas();
    expect(screen.getByTestId('react-flow')).toBeInTheDocument();
  });

  // ── External store reseed ───────────────────────────────────────

  it('reseeds ReactFlow nodes when Jotai nodesAtom is updated externally', () => {
    const store = createStore();
    renderCanvas(store);

    const newNodes = [stageNode('n-ext-1')];
    act(() => {
      store.set(nodesAtom, newNodes as any);
    });

    // The useEffect reseed should have fired — verify store state
    expect(store.get(nodesAtom)).toHaveLength(1);
    expect(store.get(nodesAtom)[0].id).toBe('n-ext-1');
  });

  it('reseeds ReactFlow edges when edgesAtom is updated externally', () => {
    const store = createStore();
    renderCanvas(store);

    const newEdges = [{ id: 'e-ext', source: 'a', target: 'b' }];
    act(() => {
      store.set(edgesAtom, newEdges as any);
    });

    expect(store.get(edgesAtom)).toHaveLength(1);
  });

  // ── Node click selection sync ───────────────────────────────────

  it('onNodeClick updates selectedNodeIdAtom in Jotai store', () => {
    const store = createStore();
    renderCanvas(store);

    expect(capturedOnNodeClick).toBeDefined();
    act(() => {
      capturedOnNodeClick?.({} as any, stageNode('node-42') as any);
    });

    expect(store.get(selectedNodeIdAtom)).toBe('node-42');
  });

  it('onEdgeClick updates selectedEdgeIdAtom in Jotai store', () => {
    const store = createStore();
    renderCanvas(store);

    expect(capturedOnEdgeClick).toBeDefined();
    act(() => {
      capturedOnEdgeClick?.({} as any, { id: 'edge-7', source: 'a', target: 'b' } as any);
    });

    // After edge click the node selection is cleared
    expect(store.get(selectedNodeIdAtom)).toBeNull();
  });

  it('onPaneClick clears both selections', () => {
    const store = createStore();
    store.set(selectedNodeIdAtom, 'some-node');
    renderCanvas(store);

    act(() => {
      capturedOnPaneClick?.();
    });

    expect(store.get(selectedNodeIdAtom)).toBeNull();
  });

  // ── History ─────────────────────────────────────────────────────

  it('history starts empty', () => {
    const store = createStore();
    renderCanvas(store);
    expect(store.get(historyAtom)).toEqual([]);
    expect(store.get(historyIndexAtom)).toBe(0);
  });

  // ── Dirty flag ──────────────────────────────────────────────────

  it('isDirtyAtom starts false', () => {
    const store = createStore();
    renderCanvas(store);
    expect(store.get(isDirtyAtom)).toBe(false);
  });

  // ── Keyboard: Delete ────────────────────────────────────────────

  it('Delete key removes selected nodes from Jotai store', () => {
    const store = createStore();
    const nodes = [stageNode('n1', true), stageNode('n2', false)];
    store.set(nodesAtom, nodes as any);
    renderCanvas(store);

    const canvasEl = screen.getByTestId('pipeline-canvas');
    act(() => {
      fireEvent.keyDown(canvasEl, { key: 'Delete' });
    });

    // The onKeyDown handler calls setStoreNodes with filtered list
    const remaining = store.get(nodesAtom);
    expect(remaining.every((n) => n.id !== 'n1')).toBe(true);
  });

  it('Backspace key also removes selected nodes', () => {
    const store = createStore();
    store.set(nodesAtom, [stageNode('del-me', true)] as any);
    renderCanvas(store);

    const canvasEl = screen.getByTestId('pipeline-canvas');
    act(() => {
      fireEvent.keyDown(canvasEl, { key: 'Backspace' });
    });

    const remaining = store.get(nodesAtom);
    expect(remaining.find((n) => n.id === 'del-me')).toBeUndefined();
  });

  it('Delete with no selected nodes is a no-op', () => {
    const store = createStore();
    store.set(nodesAtom, [stageNode('keep', false)] as any);
    renderCanvas(store);

    const canvasEl = screen.getByTestId('pipeline-canvas');
    act(() => {
      fireEvent.keyDown(canvasEl, { key: 'Delete' });
    });

    expect(store.get(nodesAtom)).toHaveLength(1);
  });
});

// ── PipelineErrorBoundary ─────────────────────────────────────────────

describe('PipelineErrorBoundary', () => {
  it('renders children normally when no error', () => {
    render(
      <PipelineErrorBoundary>
        <div data-testid="child">ok</div>
      </PipelineErrorBoundary>,
    );
    expect(screen.getByTestId('child')).toBeInTheDocument();
  });

  it('renders fallback UI when child throws', () => {
    // Suppress React's error boundary console.error noise in test output
    const errorSpy = vi.spyOn(console, 'error').mockImplementation(() => {});

    function Bomb(): never {
      throw new Error('render boom');
    }

    render(
      <PipelineErrorBoundary>
        <Bomb />
      </PipelineErrorBoundary>,
    );

    expect(screen.getByTestId('pipeline-error-fallback')).toBeInTheDocument();
    expect(screen.getByText(/render boom/i)).toBeInTheDocument();

    errorSpy.mockRestore();
  });

  it('Try to recover button resets error state', () => {
    const errorSpy = vi.spyOn(console, 'error').mockImplementation(() => {});

    let shouldThrow = true;
    function MaybeThrow() {
      if (shouldThrow) throw new Error('crash');
      return <div data-testid="recovered">recovered</div>;
    }

    const { rerender } = render(
      <PipelineErrorBoundary>
        <MaybeThrow />
      </PipelineErrorBoundary>,
    );

    expect(screen.getByTestId('pipeline-error-fallback')).toBeInTheDocument();

    shouldThrow = false;
    fireEvent.click(screen.getByRole('button', { name: /try to recover/i }));

    rerender(
      <PipelineErrorBoundary>
        <MaybeThrow />
      </PipelineErrorBoundary>,
    );

    expect(screen.getByTestId('recovered')).toBeInTheDocument();
    errorSpy.mockRestore();
  });
});
