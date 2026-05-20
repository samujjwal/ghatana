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
import {
  builderToCanvas,
  filterCanvasSelectionToNodeIds,
  type BuilderCanvasNode,
} from '../../adapters/BuilderCanvasProjectionAdapter.js';

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
  // Project BuilderDocument to canvas nodes and edges via canonical adapter.
  const { nodes: canvasNodes, edges: canvasEdges } = useMemo(
    () => builderToCanvas(document),
    [document],
  );

  return (
    <div className="border rounded-lg overflow-hidden bg-white">
      <HybridCanvas
        nodes={[...canvasNodes]}
        edges={[...canvasEdges]}
        mode="hybrid-graph"
        width={width}
        height={height}
        readOnly={readOnly}
        onElementsChange={onElementsChange}
        onNodesChange={onNodesChange}
        onEdgesChange={onEdgesChange}
        onSelectionChange={(selection: SelectionState) => {
          // Validate canvas string IDs against document-known NodeIds.
          // filterCanvasSelectionToNodeIds guards against stale/virtual IDs
          // that do not correspond to real BuilderDocument nodes.
          const nodeIds = filterCanvasSelectionToNodeIds(document, selection.nodeIds);
          onSelectionChange(nodeIds);
        }}
        onViewportChange={() => {}}
        onModeChange={() => {}}
        onConnect={() => {}}
      />
    </div>
  );
}
