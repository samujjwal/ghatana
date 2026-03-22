/**
 * Canvas Command Pattern
 *
 * Provides the Command interface and concrete command implementations for
 * undo/redo operations. Replaces the snapshot-based history system with:
 *
 *   - O(1) memory per command (stores deltas, not full snapshots)
 *   - Mergeable commands (e.g., rapid moves collapse to a single undo step)
 *   - Inverse-operation undo (exact reversal, not state restoration)
 *   - Batch transactions (multiple mutations grouped as one history entry)
 *
 * Architecture:
 *   - CanvasCommand: interface all commands implement
 *   - commandHistoryAtom: stores the command stack + pointer
 *   - executeCommandAtom: executes + pushes to history (the write path)
 *   - undoCommandAtom / redoCommandAtom: walk the command stack
 *
 * @doc.type module
 * @doc.purpose Command pattern undo/redo for canvas operations
 * @doc.layer product
 * @doc.pattern Command
 */

import { atom, type Getter, type Setter } from 'jotai';
import { type Node, type Edge } from '@xyflow/react';
import { nodesAtom, edgesAtom, canvasAnnouncementAtom, MAX_HISTORY_SIZE } from './canvasAtoms';
import type { ArtifactNodeData } from '../nodes/ArtifactNode';
import type { DependencyEdgeData } from '../edges';

// ============================================================================
// Core Interface
// ============================================================================

/**
 * A canvas command that can be executed and reversed.
 *
 * All mutations to canvas state must go through a CanvasCommand so they
 * participate in the undo/redo system.
 */
export interface CanvasCommand {
    /** Human-readable label shown in the history panel */
    readonly label: string;

    /**
     * Apply this command (called on first execution and on redo).
     * Receives Jotai get/set to mutate atoms.
     */
    execute(get: Getter, set: Setter): void;

    /**
     * Reverse this command (called on undo).
     * Must be the exact functional inverse of execute().
     */
    undo(get: Getter, set: Setter): void;

    /**
     * Optionally merge this command with the previous one in the stack.
     * Return a merged command if they should be combined (e.g., rapid moves),
     * or null to keep them separate.
     *
     * Called before pushing to history. `previous` is the command at the
     * current stack pointer — merge into it if the result is the same operation.
     */
    merge?(previous: CanvasCommand): CanvasCommand | null;
}

// ============================================================================
// History Atom
// ============================================================================

export interface CommandHistoryState {
    commands: CanvasCommand[];
    /** Points to the last executed command. -1 = empty. */
    pointer: number;
}

export const commandHistoryAtom = atom<CommandHistoryState>({
    commands: [],
    pointer: -1,
});

export const canUndoCommandAtom = atom(
    (get) => get(commandHistoryAtom).pointer >= 0
);

export const canRedoCommandAtom = atom(
    (get) => {
        const { commands, pointer } = get(commandHistoryAtom);
        return pointer < commands.length - 1;
    }
);

// ============================================================================
// Action Atoms
// ============================================================================

/**
 * Execute a command and push it onto the command history stack.
 *
 * - Clears any redo-future (commands after the current pointer)
 * - Attempts to merge with the previous command via command.merge()
 * - Enforces MAX_HISTORY_SIZE cap
 */
export const executeCommandAtom = atom(
    null,
    (get, set, command: CanvasCommand) => {
        // Execute the command
        command.execute(get, set);

        const { commands, pointer } = get(commandHistoryAtom);

        // Remove redo future
        const trimmed = commands.slice(0, pointer + 1);

        // Attempt merge with last command
        const prev = trimmed[trimmed.length - 1];
        const merged = prev && command.merge ? command.merge(prev) : null;

        let newCommands: CanvasCommand[];
        if (merged) {
            // Replace the last entry with the merged command
            newCommands = [...trimmed.slice(0, -1), merged];
        } else {
            newCommands = [...trimmed, command];
        }

        // Enforce history cap
        if (newCommands.length > MAX_HISTORY_SIZE) {
            newCommands = newCommands.slice(-MAX_HISTORY_SIZE);
        }

        set(commandHistoryAtom, {
            commands: newCommands,
            pointer: newCommands.length - 1,
        });

        set(canvasAnnouncementAtom, `Done: ${command.label}`);
    }
);

/**
 * Undo the command at the current pointer.
 */
export const undoCommandAtom = atom(
    null,
    (get, set) => {
        const { commands, pointer } = get(commandHistoryAtom);
        if (pointer < 0) return;

        const command = commands[pointer];
        command.undo(get, set);

        set(commandHistoryAtom, { commands, pointer: pointer - 1 });
        set(canvasAnnouncementAtom, `Undone: ${command.label}`);
    }
);

/**
 * Redo the command after the current pointer.
 */
export const redoCommandAtom = atom(
    null,
    (get, set) => {
        const { commands, pointer } = get(commandHistoryAtom);
        if (pointer >= commands.length - 1) return;

        const command = commands[pointer + 1];
        command.execute(get, set);

        set(commandHistoryAtom, { commands, pointer: pointer + 1 });
        set(canvasAnnouncementAtom, `Redone: ${command.label}`);
    }
);

/**
 * Execute multiple commands as a single atomic transaction.
 * The entire batch appears as one undo step.
 */
export const executeBatchAtom = atom(
    null,
    (get, set, commands: CanvasCommand[]) => {
        if (commands.length === 0) return;
        const batch: CanvasCommand = {
            label: commands.map((c) => c.label).join(', '),
            execute: (_g, s) => commands.forEach((c) => c.execute(_g, s)),
            undo: (_g, s) => [...commands].reverse().forEach((c) => c.undo(_g, s)),
        };
        set(executeCommandAtom, batch);
    }
);

// ============================================================================
// Concrete Command Implementations
// ============================================================================

/**
 * Add a node to the canvas.
 */
export class AddNodeCommand implements CanvasCommand {
    readonly label: string;
    constructor(private readonly node: Node<ArtifactNodeData>) {
        this.label = `Add ${node.data?.title ?? node.id}`;
    }
    execute(_: Getter, set: Setter) {
        set(nodesAtom, (prev) => {
            if (prev.some((n) => n.id === this.node.id)) return prev;
            return [...prev, this.node];
        });
    }
    undo(_: Getter, set: Setter) {
        set(nodesAtom, (prev) => prev.filter((n) => n.id !== this.node.id));
        set(edgesAtom, (prev) =>
            prev.filter((e) => e.source !== this.node.id && e.target !== this.node.id)
        );
    }
}

/**
 * Remove one or more nodes (and their connected edges) from the canvas.
 *
 * Memory: O(deleted_count) — stores only the removed nodes/edges, NOT a full
 * canvas snapshot.  Previous implementation stored the entire node array per
 * delete operation (O(n × MAX_HISTORY) worst-case memory cliff at 2k nodes).
 */
export class RemoveNodesCommand implements CanvasCommand {
    readonly label: string;
    private readonly ids: Set<string>;

    constructor(
        /** Only the nodes being deleted — not the full canvas. */
        private readonly deletedNodes: Node<ArtifactNodeData>[],
        /** Only the edges connected to deleted nodes — not all edges. */
        private readonly deletedEdges: Edge<DependencyEdgeData>[],
    ) {
        this.ids = new Set(deletedNodes.map((n) => n.id));
        this.label = `Delete ${deletedNodes.length} node${deletedNodes.length > 1 ? 's' : ''}`;
    }

    execute(_: Getter, set: Setter) {
        set(nodesAtom, (prev) => prev.filter((n) => !this.ids.has(n.id)));
        set(edgesAtom, (prev) =>
            prev.filter((e) => !this.ids.has(e.source) && !this.ids.has(e.target))
        );
    }

    undo(_: Getter, set: Setter) {
        set(nodesAtom, (prev) => [...prev, ...this.deletedNodes]);
        set(edgesAtom, (prev) => [...prev, ...this.deletedEdges]);
    }
}

/**
 * Move one or more nodes.
 * Merges with consecutive move commands for the same set of nodes so that
 * rapid dragging collapses to a single undo step.
 */
export class MoveNodesCommand implements CanvasCommand {
    readonly label: string;

    constructor(
        private readonly nodeIds: string[],
        private readonly from: Record<string, { x: number; y: number }>,
        private readonly to: Record<string, { x: number; y: number }>,
    ) {
        this.label = `Move ${nodeIds.length} node${nodeIds.length > 1 ? 's' : ''}`;
    }

    /** Snap to 0.5 px to prevent float accumulation at high zoom levels */
    private snap(v: number): number {
        return Math.round(v * 2) / 2;
    }

    execute(_: Getter, set: Setter) {
        set(nodesAtom, (prev) =>
            prev.map((n) =>
                this.nodeIds.includes(n.id) && this.to[n.id]
                    ? { ...n, position: { x: this.snap(this.to[n.id].x), y: this.snap(this.to[n.id].y) } }
                    : n
            )
        );
    }

    undo(_: Getter, set: Setter) {
        set(nodesAtom, (prev) =>
            prev.map((n) =>
                this.nodeIds.includes(n.id) && this.from[n.id]
                    ? { ...n, position: { x: this.snap(this.from[n.id].x), y: this.snap(this.from[n.id].y) } }
                    : n
            )
        );
    }

    /** Merge consecutive moves of the same nodes into one undo step */
    merge(previous: CanvasCommand): CanvasCommand | null {
        if (
            previous instanceof MoveNodesCommand &&
            previous.nodeIds.length === this.nodeIds.length &&
            previous.nodeIds.every((id) => this.nodeIds.includes(id))
        ) {
            // Keep the original `from` of the previous command, our `to`
            return new MoveNodesCommand(this.nodeIds, previous.from, this.to);
        }
        return null;
    }
}

/**
 * Align one or more nodes along a common axis.
 *
 * Callers compute `before`/`after` position maps then dispatch this command so
 * the alignment participates in undo/redo. Example usage:
 *
 * ```ts
 * const before = Object.fromEntries(selectedIds.map(id => [id, nodes.find(n => n.id === id)!.position]));
 * const after  = computeAlignedPositions(selectedIds, nodes, 'left');
 * executeCommand(new AlignNodesCommand(selectedIds, 'left', before, after));
 * ```
 *
 * @doc.type class
 * @doc.purpose Undo/redo-aware node alignment command
 * @doc.layer product
 * @doc.pattern Command
 */
export class AlignNodesCommand implements CanvasCommand {
    readonly label: string;

    constructor(
        private readonly nodeIds: string[],
        private readonly axis: 'left' | 'right' | 'top' | 'bottom' | 'center-x' | 'center-y',
        private readonly before: Record<string, { x: number; y: number }>,
        private readonly after: Record<string, { x: number; y: number }>,
    ) {
        this.label = `Align ${nodeIds.length} node${nodeIds.length !== 1 ? 's' : ''} (${this.axis})`;
    }

    execute(_: Getter, set: Setter) {
        set(nodesAtom, (prev) =>
            prev.map((n) =>
                this.nodeIds.includes(n.id) && this.after[n.id]
                    ? { ...n, position: this.after[n.id] }
                    : n,
            ),
        );
    }

    undo(_: Getter, set: Setter) {
        set(nodesAtom, (prev) =>
            prev.map((n) =>
                this.nodeIds.includes(n.id) && this.before[n.id]
                    ? { ...n, position: this.before[n.id] }
                    : n,
            ),
        );
    }
}

/**
 * Update node data (title, status, type, etc.).
 */
export class UpdateNodeDataCommand<T extends Record<string, unknown> = Record<string, unknown>> implements CanvasCommand {
    readonly label: string;

    constructor(
        private readonly nodeId: string,
        private readonly from: Partial<T>,
        private readonly to: Partial<T>,
        label?: string,
    ) {
        this.label = label ?? `Update node`;
    }

    execute(_: Getter, set: Setter) {
        set(nodesAtom, (prev) =>
            prev.map((n) =>
                n.id === this.nodeId ? { ...n, data: { ...n.data, ...this.to } } : n
            )
        );
    }

    undo(_: Getter, set: Setter) {
        set(nodesAtom, (prev) =>
            prev.map((n) =>
                n.id === this.nodeId ? { ...n, data: { ...n.data, ...this.from } } : n
            )
        );
    }
}

/**
 * Add an edge.
 */
export class AddEdgeCommand implements CanvasCommand {
    readonly label = 'Add connection';
    constructor(private readonly edge: Edge<DependencyEdgeData>) {}

    execute(_: Getter, set: Setter) {
        set(edgesAtom, (prev) => {
            if (prev.some((e) => e.id === this.edge.id)) return prev;
            return [...prev, this.edge];
        });
    }

    undo(_: Getter, set: Setter) {
        set(edgesAtom, (prev) => prev.filter((e) => e.id !== this.edge.id));
    }
}

/**
 * Remove an edge.
 */
export class RemoveEdgeCommand implements CanvasCommand {
    readonly label = 'Remove connection';
    constructor(private readonly edge: Edge<DependencyEdgeData>) {}

    execute(_: Getter, set: Setter) {
        set(edgesAtom, (prev) => prev.filter((e) => e.id !== this.edge.id));
    }

    undo(_: Getter, set: Setter) {
        set(edgesAtom, (prev) => {
            if (prev.some((e) => e.id === this.edge.id)) return prev;
            return [...prev, this.edge];
        });
    }
}

/**
 * Paste nodes (offset copies of the clipboard).
 */
export class PasteNodesCommand implements CanvasCommand {
    readonly label: string;
    private pastedIds: string[] = [];

    constructor(private readonly nodes: Node<ArtifactNodeData>[]) {
        this.label = `Paste ${nodes.length} node${nodes.length > 1 ? 's' : ''}`;
        this.pastedIds = nodes.map((n) => n.id);
    }

    execute(_: Getter, set: Setter) {
        set(nodesAtom, (prev) => {
            const existing = new Set(prev.map((n) => n.id));
            const toAdd = this.nodes.filter((n) => !existing.has(n.id));
            return [...prev, ...toAdd];
        });
    }

    undo(_: Getter, set: Setter) {
        set(nodesAtom, (prev) => prev.filter((n) => !this.pastedIds.includes(n.id)));
    }
}
