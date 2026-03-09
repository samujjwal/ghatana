import { atom } from 'jotai';

/**
 * Workflow state interface.
 */
export interface WorkflowState {
    selectedWorkflowId: string | null;
    selectedNodeId: string | null;
    playbackSpeed: number;
    flowFilter: string | null;
    inspectorEventId: string | null;
    isPlayingTimeline: boolean;
}

/**
 * Workflow Jotai store - manages workflow explorer UI state.
 *
 * <p><b>Purpose</b><br>
 * Centralizes workflow feature state (selection, playback, filtering, inspector).
 *
 * <p><b>Atoms</b><br>
 * - selectedWorkflowIdAtom: Currently selected workflow
 * - selectedNodeIdAtom: Currently selected workflow node
 * - playbackSpeedAtom: Timeline playback speed (0.25x - 2x)
 * - flowFilterAtom: Filter nodes by type
 * - inspectorEventIdAtom: Event ID being inspected in detail panel
 * - isPlayingTimelineAtom: Timeline is playing/paused
 * - canvasTransformAtom: Canvas pan/zoom transform
 * - canvasHoveredNodeAtom: Currently hovered node (for highlight)
 * - canvasSelectedEdgeAtom: Selected edge for inspection
 *
 * <p><b>Usage</b><br>
 * ```typescript
 * import { useAtom } from 'jotai';
 * import { selectedWorkflowIdAtom, playbackSpeedAtom } from '@/features/workflows/stores/workflows.store';
 *
 * function WorkflowsPage() {
 *   const [workflow, setWorkflow] = useAtom(selectedWorkflowIdAtom);
 *   const [speed, setSpeed] = useAtom(playbackSpeedAtom);
 *   // Page logic
 * }
 * ```
 *
 * @doc.type configuration
 * @doc.purpose Workflow state management
 * @doc.layer product
 * @doc.pattern State Store
 */

/**
 * Selected workflow ID atom
 */
export const selectedWorkflowIdAtom = atom<string | null>(null);

/**
 * Selected node ID atom - currently selected node in the workflow DAG
 */
export const selectedNodeIdAtom = atom<string | null>(null);

/**
 * Playback speed atom - timeline playback speed multiplier (0.25, 0.5, 1, 1.5, 2)
 */
export const playbackSpeedAtom = atom<number>(1);

/**
 * Flow filter atom - filter workflow nodes by type (trigger, action, decision, task, end)
 */
export const flowFilterAtom = atom<string | null>(null);

/**
 * Inspector event ID atom - event ID currently being inspected in detail panel
 */
export const inspectorEventIdAtom = atom<string | null>(null);

/**
 * Timeline playback state atom - is timeline currently playing
 */
export const isPlayingTimelineAtom = atom<boolean>(false);

/**
 * Canvas transform atom - canvas pan/zoom transform [scale, translateX, translateY]
 */
export const canvasTransformAtom = atom<[number, number, number]>([1, 0, 0]);

/**
 * Canvas hovered node atom - currently hovered node ID for highlight
 */
export const canvasHoveredNodeAtom = atom<string | null>(null);

/**
 * Canvas selected edge atom - currently selected edge for inspection
 */
export const canvasSelectedEdgeAtom = atom<{ from: string; to: string } | null>(null);

export default {
    selectedWorkflowIdAtom,
    selectedNodeIdAtom,
    playbackSpeedAtom,
    flowFilterAtom,
    inspectorEventIdAtom,
    isPlayingTimelineAtom,
    canvasTransformAtom,
    canvasHoveredNodeAtom,
    canvasSelectedEdgeAtom,
};
