/**
 * Canvas Renderer Types
 * 
 * Shared type definitions for canvas rendering components.
 * Extracted to prevent circular dependencies between renderer modules.
 * 
 * @doc.type types
 * @doc.purpose Renderer type definitions
 * @doc.layer canvas/renderer
 */

import type { UniversalNode, UniqueId, ArtifactContract } from '../model/contracts';
import type { ReactNode, MouseEvent } from 'react';

/**
 * Selection state for canvas nodes
 */
export interface SelectionState {
  /** Selected node IDs */
  selectedIds: Set<UniqueId>;
  /** Primary selected node (for multi-select) */
  primaryId: UniqueId | null;
  /** Hovered node ID */
  hoveredId: UniqueId | null;
  /** Editing node ID */
  editingId: UniqueId | null;
}

/**
 * Node render state - determines visual appearance
 */
export interface NodeRenderState {
  selected: boolean;
  hovered: boolean;
  editing: boolean;
  primary: boolean;
}

/**
 * Canvas surface props
 */
export interface CanvasSurfaceProps {
  /** Nodes to render (flat map) */
  nodes: Map<UniqueId, UniversalNode>;
  /** Root node IDs (top-level) */
  rootIds: UniqueId[];
  /** Contract lookup function */
  getContract: (kind: string) => ArtifactContract | undefined;
  /** Selection state */
  selection: SelectionState;
  /** Current zoom level */
  zoom: number;
  /** Node click handler */
  onNodeClick?: (nodeId: UniqueId, event: MouseEvent) => void;
  /** Node double-click handler */
  onNodeDoubleClick?: (nodeId: UniqueId, event: MouseEvent) => void;
  /** Node context menu handler */
  onNodeContextMenu?: (nodeId: UniqueId, event: MouseEvent) => void;
  /** Node hover handler */
  onNodeHover?: (nodeId: UniqueId | null) => void;
  /** Node drag start handler */
  onNodeDragStart?: (nodeId: UniqueId, event: MouseEvent) => void;
  /** Background click handler */
  onBackgroundClick?: (event: MouseEvent) => void;
  /** Custom node renderer */
  renderNode?: (
    node: UniversalNode,
    contract: ArtifactContract | undefined,
    state: NodeRenderState
  ) => ReactNode;
  /** Show guides for alignment */
  showGuides?: boolean;
  /** Grid size for snapping */
  gridSize?: number;
  /** Show selection bounds */
  showSelectionBounds?: boolean;
}
