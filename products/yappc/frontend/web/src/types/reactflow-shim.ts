// Minimal React Flow types used in tests and lightweight components
/**
 *
 */
export type RFViewport = { x: number; y: number; zoom: number };
/**
 *
 */
export type RFNode = { id: string; type?: string } & Record<string, unknown>;

/**
 *
 */
export type RFInstanceLike = {
  getNodes?: () => unknown[];
  getEdges?: () => unknown[];
  project?: (pos: { x: number; y: number }) => { x: number; y: number };
  setViewport?: (v: Partial<RFViewport>) => void;
  toObject?: () => { nodes: unknown[]; edges: unknown[] };
  __applyNodeChanges?: (changes: unknown[]) => unknown[];
  __applyEdgeChanges?: (changes: unknown[]) => unknown[];
  __viewport?: () => RFViewport;
};
