/**
 * @fileoverview Visual canvas for builder workspace.
 *
 * Integrates @ghatana/canvas HybridCanvas for visual editing of builder documents.
 * Supports drag-and-drop, selection, viewport control, and element manipulation.
 *
 * @doc.type component
 * @doc.purpose Visual editing canvas for builder documents
 * @doc.layer platform
 */

import type { ReactElement } from 'react';
import { useMemo } from 'react';
import { HybridCanvas } from '@ghatana/canvas';
import type { SelectionState } from '@ghatana/canvas';
import type { BuilderDocument, NodeId } from '@ghatana/ui-builder';

export interface VisualCanvasProps {
  /** The builder document to render */
  document: BuilderDocument;
  /** Currently selected node IDs */
  selectedNodeIds: readonly NodeId[];
  /** Callback when selection changes */
  onSelectionChange: (nodeIds: readonly NodeId[]) => void;
  /** Callback when elements are modified */
  onElementsChange?: (elements: readonly unknown[]) => void;
  /** Callback when nodes are modified */
  onNodesChange?: (nodes: readonly unknown[]) => void;
  /** Callback when edges are modified */
  onEdgesChange?: (edges: readonly unknown[]) => void;
  /** Canvas width */
  width?: string | number;
  /** Canvas height */
  height?: string | number;
  /** Whether the canvas is read-only */
  readOnly?: boolean;
}

export function VisualCanvas({
  document,
  selectedNodeIds: _selectedNodeIds,
  onSelectionChange,
  onElementsChange,
  onNodesChange,
  onEdgesChange,
  width = '100%',
  height = '600px',
  readOnly = false,
}: VisualCanvasProps): ReactElement {
  // Convert BuilderDocument to canvas nodes and edges
  const { canvasNodes, canvasEdges } = useMemo(() => {
    const nodes = document.nodes;

    const canvasNodes: Array<{
      id: string;
      type: 'node';
      data: Record<string, unknown>;
      position: { x: number; y: number };
    }> = [];

    const canvasEdges: Array<{
      id: string;
      type: 'edge';
      source: string;
      target: string;
      data: Record<string, unknown>;
    }> = [];

    // Convert ComponentInstance nodes to canvas nodes
    for (const [nodeId, instance] of Object.entries(nodes)) {
      const metadata = instance.metadata as { position?: { x: number; y: number } };
      const position = metadata?.position || { x: 0, y: 0 };

      canvasNodes.push({
        id: nodeId,
        type: 'node',
        data: {
          contractName: instance.contractName,
          props: instance.props,
          label: instance.contractName,
        },
        position,
      });
    }

    // Convert slots to edges (parent-child relationships)
    for (const [nodeId, instance] of Object.entries(nodes)) {
      for (const [slotName, childIds] of Object.entries(instance.slots)) {
        for (const childId of childIds) {
          canvasEdges.push({
            id: `${nodeId}-${childId}-${slotName}`,
            type: 'edge',
            source: nodeId,
            target: childId,
            data: {
              slot: slotName,
              type: 'slot',
            },
          });
        }
      }
    }

    return { canvasNodes, canvasEdges };
  }, [document]);

  return (
    <div className="border rounded-lg overflow-hidden bg-white">
      <HybridCanvas
        nodes={canvasNodes}
        edges={canvasEdges}
        mode="hybrid-graph"
        width={width}
        height={height}
        readOnly={readOnly}
        onElementsChange={onElementsChange}
        onNodesChange={onNodesChange}
        onEdgesChange={onEdgesChange}
        onSelectionChange={(selection: SelectionState) => {
          // Map canvas string node IDs to typed NodeId (which is `string & { __brand: 'NodeId' }`)
          // The canvas selection.nodeIds are string[] that correspond 1:1 with builder NodeId keys.
          const nodeIds = selection.nodeIds as NodeId[];
          onSelectionChange(nodeIds);
        }}
        onViewportChange={() => {}}
        onModeChange={() => {}}
        onConnect={() => {}}
      />
    </div>
  );
}
