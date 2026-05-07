/**
 * Pipeline builder Jotai store.
 *
 * Manages pipeline definition state, selected elements,
 * validation results, and undo/redo history.
 *
 * @doc.type store
 * @doc.purpose Pipeline builder state management
 * @doc.layer frontend
 */
import { atom } from 'jotai';
import type { Node, Edge } from '@xyflow/react';
import type {
  PipelineSpec,
  PipelineStatus,
  PipelineValidationResult,
  StageNodeData,
  ConnectorNodeData,
} from '@/types/pipeline.types';

// ─── Primitive Atoms ─────────────────────────────────────────────────

/** Currently loaded pipeline definition. */
export const pipelineAtom = atom<PipelineSpec>({
  name: 'Untitled Pipeline',
  stages: [],
  status: 'DRAFT',
  version: 1,
});

/** ReactFlow nodes (stages + connectors). */
export const nodesAtom = atom<Node<StageNodeData | ConnectorNodeData>[]>([]);

/** ReactFlow edges between stages. */
export const edgesAtom = atom<Edge[]>([]);

/** Currently selected node ID (null = none). */
export const selectedNodeIdAtom = atom<string | null>(null);

/** Currently selected edge ID. */
export const selectedEdgeIdAtom = atom<string | null>(null);

/** Latest validation result. */
export const validationAtom = atom<PipelineValidationResult | null>(null);

/** Dirty flag — unsaved changes exist. */
export const isDirtyAtom = atom(false);

/** Pipeline status (mirrors PipelineSpec.status for toolbar badge). */
export const pipelineStatusAtom = atom<PipelineStatus>('DRAFT');

// ─── Derived Atoms ───────────────────────────────────────────────────

/** Selected node object (derived from nodesAtom + selectedNodeIdAtom). */
export const selectedNodeAtom = atom((get) => {
  const id = get(selectedNodeIdAtom);
  if (!id) return null;
  return get(nodesAtom).find((n) => n.id === id) ?? null;
});

/** Number of stages in the pipeline. */
export const stageCountAtom = atom((get) => {
  return get(nodesAtom).filter((n) => n.type === 'stage').length;
});

/** Total agent count across all stages. */
export const totalAgentCountAtom = atom((get) => {
  return get(nodesAtom)
    .filter((n) => n.type === 'stage')
    .reduce((sum, n) => sum + ((n.data as StageNodeData).agentCount ?? 0), 0);
});

/** Whether the pipeline is valid (no errors). */
export const isValidAtom = atom((get) => {
  const v = get(validationAtom);
  return v?.isValid ?? false;
});

// ─── Undo/Redo History ───────────────────────────────────────────────

interface HistorySnapshot {
  nodes: Node<StageNodeData | ConnectorNodeData>[];
  edges: Edge[];
}

export const historyAtom = atom<HistorySnapshot[]>([]);
export const historyIndexAtom = atom(0);

/** Whether undo is available. */
export const canUndoAtom = atom((get) => get(historyIndexAtom) > 0);

/** Whether redo is available. */
export const canRedoAtom = atom((get) => {
  const idx = get(historyIndexAtom);
  const len = get(historyAtom).length;
  return idx < len - 1;
});
