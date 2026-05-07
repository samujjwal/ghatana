import React from 'react';
import { fireEvent, render } from '@testing-library/react';
import { ReactFlowProvider } from '@xyflow/react';
import { describe, expect, it, vi } from 'vitest';

import { useCanvasKeyboardShortcuts } from '../useCanvasKeyboardShortcuts';

interface TestCanvasNode {
  id: string;
  type?: string;
  position?: { x: number; y: number };
  [key: string]: unknown;
}

function KeyboardHarness({
  canMutateCanvas,
  canvasOverrides = {},
}: {
  canMutateCanvas: boolean;
  canvasOverrides?: Partial<Parameters<typeof useCanvasKeyboardShortcuts>[0]['canvas']>;
}): React.ReactElement {
  const canvas = {
    nodes: [{ id: 'node-1', position: { x: 1, y: 2 } }],
    selectedNodeIds: ['node-1'],
    activeTool: 'select',
    canUndo: true,
    canRedo: true,
    undo: vi.fn(),
    redo: vi.fn(),
    resetZoom: vi.fn(),
    zoomIn: vi.fn(),
    zoomOut: vi.fn(),
    selectNodes: vi.fn(),
    removeNode: vi.fn(),
    duplicateNode: vi.fn((id: string): TestCanvasNode => ({ id: `${id}-copy` })),
    createGroup: vi.fn(),
    ungroup: vi.fn(),
    bringForward: vi.fn(),
    sendBackward: vi.fn(),
    bringToFront: vi.fn(),
    sendToBack: vi.fn(),
    downloadJSON: vi.fn(),
    downloadSVG: vi.fn(),
    setActiveTool: vi.fn(),
    addNode: vi.fn((node: TestCanvasNode): TestCanvasNode => node),
    alignNodes: vi.fn(),
    ...canvasOverrides,
  };
  const showToast = vi.fn();

  useCanvasKeyboardShortcuts({
    canvas,
    projectId: 'project-1',
    calmMode: false,
    setCalmMode: vi.fn(),
    leftRailVisible: false,
    setLeftRailVisible: vi.fn(),
    minimapVisible: false,
    setMinimapVisible: vi.fn(),
    propertiesPanelOpen: false,
    setPropertiesPanelOpen: vi.fn(),
    copiedNodes: [{ id: 'copied-node', position: { x: 10, y: 20 } }],
    setCopiedNodeIds: vi.fn(),
    setCopiedNodes: vi.fn(),
    hasMultipleSelection: false,
    setDrawingTool: vi.fn(),
    setContextMenu: vi.fn(),
    setNodeContextMenu: vi.fn(),
    setAddMenuAnchor: vi.fn(),
    setExportMenuAnchor: vi.fn(),
    setAlignMenuAnchor: vi.fn(),
    setLayerMenuAnchor: vi.fn(),
    setShortcutLegendOpen: vi.fn(),
    showToast,
    addNodeAtPosition: vi.fn(),
    canMutateCanvas,
    readOnlyReason: 'Canvas is locked for review.',
  });

  return <div data-testid="keyboard-harness" data-toast-count={showToast.mock.calls.length} />;
}

describe('useCanvasKeyboardShortcuts access policy', () => {
  it('blocks destructive shortcuts when the legacy canvas is read-only', () => {
    const removeNode = vi.fn();
    const addNode = vi.fn((node: TestCanvasNode): TestCanvasNode => node);
    const showToastProbe = vi.fn();

    function Probe(): null {
      useCanvasKeyboardShortcuts({
        canvas: {
          nodes: [{ id: 'node-1', position: { x: 1, y: 2 } }],
          selectedNodeIds: ['node-1'],
          activeTool: 'select',
          canUndo: true,
          canRedo: true,
          undo: vi.fn(),
          redo: vi.fn(),
          resetZoom: vi.fn(),
          zoomIn: vi.fn(),
          zoomOut: vi.fn(),
          selectNodes: vi.fn(),
          removeNode,
          duplicateNode: vi.fn((id: string): TestCanvasNode => ({ id: `${id}-copy` })),
          createGroup: vi.fn(),
          ungroup: vi.fn(),
          bringForward: vi.fn(),
          sendBackward: vi.fn(),
          bringToFront: vi.fn(),
          sendToBack: vi.fn(),
          downloadJSON: vi.fn(),
          downloadSVG: vi.fn(),
          setActiveTool: vi.fn(),
          addNode,
          alignNodes: vi.fn(),
        },
        projectId: 'project-1',
        calmMode: false,
        setCalmMode: vi.fn(),
        leftRailVisible: false,
        setLeftRailVisible: vi.fn(),
        minimapVisible: false,
        setMinimapVisible: vi.fn(),
        propertiesPanelOpen: false,
        setPropertiesPanelOpen: vi.fn(),
        copiedNodes: [{ id: 'copied-node', position: { x: 10, y: 20 } }],
        setCopiedNodeIds: vi.fn(),
        setCopiedNodes: vi.fn(),
        hasMultipleSelection: false,
        setDrawingTool: vi.fn(),
        setContextMenu: vi.fn(),
        setNodeContextMenu: vi.fn(),
        setAddMenuAnchor: vi.fn(),
        setExportMenuAnchor: vi.fn(),
        setAlignMenuAnchor: vi.fn(),
        setLayerMenuAnchor: vi.fn(),
        setShortcutLegendOpen: vi.fn(),
        showToast: showToastProbe,
        addNodeAtPosition: vi.fn(),
        canMutateCanvas: false,
        readOnlyReason: 'Canvas is locked for review.',
      });

      return null;
    }

    render(
      <ReactFlowProvider>
        <Probe />
      </ReactFlowProvider>
    );

    fireEvent.keyDown(window, { key: 'Delete' });
    fireEvent.keyDown(window, { key: 'v', metaKey: true });

    expect(removeNode).not.toHaveBeenCalled();
    expect(addNode).not.toHaveBeenCalled();
    expect(showToastProbe).toHaveBeenCalledWith('Canvas is locked for review.', 'warning');
  });

  it('keeps read-only navigation shortcuts available', () => {
    const selectNodes = vi.fn();

    render(
      <ReactFlowProvider>
        <KeyboardHarness canMutateCanvas={false} canvasOverrides={{ selectNodes }} />
      </ReactFlowProvider>
    );

    fireEvent.keyDown(window, { key: 'a', metaKey: true });

    expect(selectNodes).toHaveBeenCalledWith(['node-1']);
  });
});
