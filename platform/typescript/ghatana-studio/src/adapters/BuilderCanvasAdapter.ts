/**
 * @fileoverview Canonical bidirectional adapter between @ghatana/ui-builder (BuilderDocument)
 * and @ghatana/canvas (CanvasNode / CanvasEdge).
 *
 * This is the **canonical adapter** for Builder ↔ Canvas projection. It re-exports all
 * functionality from the consolidated implementation in BuilderCanvasProjectionAdapter.ts.
 *
 * Provides pure functions:
 * - `builderToCanvas`: projects a BuilderDocument into canvas-ready nodes + edges.
 * - `canvasToBuilder`: merges position/size mutations from canvas back into a
 *   BuilderDocument (partial update — only structure/metadata is affected, prop
 *   values are not overwritten).
 * - `filterCanvasSelectionToNodeIds`: validates canvas selection against document.
 * - `reconcileCanvasGeometryDeltas`: converts geometry deltas to builder operations.
 * - `isBuilderCanvasNode`: type guard for validating canvas nodes.
 * - `filterValidBuilderCanvasNodes`: filters and validates canvas nodes against document.
 *
 * Neither function mutates its input.
 *
 * @doc.type module
 * @doc.purpose BuilderDocument ↔ Canvas node/edge projection (canonical adapter)
 * @doc.layer studio
 * @doc.pattern Adapter
 */

// ============================================================================
// Re-export all canonical Builder/Canvas adapter functionality
// ============================================================================

export {
  // Core projection functions
  builderToCanvas,
  canvasToBuilder,
  filterCanvasSelectionToNodeIds,
  reconcileCanvasGeometryDeltas,
  isBuilderCanvasNode,
  filterValidBuilderCanvasNodes,
  
  // Types
  type BuilderNodeData,
  type BuilderEdgeData,
  type BuilderCanvasNode,
  type BuilderCanvasEdge,
  type BuilderToCanvasResult,
  type CanvasToBuilderOptions,
  type CanvasNodeGeometryDelta,
  type BuilderGeometryOperation,
} from './BuilderCanvasProjectionAdapter.js';

// ============================================================================
// Constants
// ============================================================================

/**
 * Grid layout constants for automatic positioning.
 */
export const BUILDER_CANVAS_GRID = {
  /** Number of columns in the auto-layout grid. */
  COLUMNS: 4,
  /** Width of each grid cell in pixels. */
  CELL_WIDTH: 240,
  /** Height of each grid cell in pixels. */
  CELL_HEIGHT: 140,
  /** Horizontal margin in pixels. */
  MARGIN_X: 40,
  /** Vertical margin in pixels. */
  MARGIN_Y: 40,
} as const;

// ============================================================================
// Version
// ============================================================================

/**
 * Adapter version for compatibility checks.
 */
export const BUILDER_CANVAS_ADAPTER_VERSION = '1.0.0' as const;
