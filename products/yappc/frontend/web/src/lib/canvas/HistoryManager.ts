/**
 * Canvas History Manager - Undo/Redo System
 * 
 * Command pattern implementation for canvas operations
 * 
 * @doc.type manager
 * @doc.purpose Undo/redo functionality for canvas
 * @doc.layer core
 * @doc.pattern Command
 */

import type { HierarchicalNode } from './HierarchyManager';
import type { Connection } from './NodeManipulation';

export interface CanvasCommand {
    execute(): void;
    undo(): void;
    redo(): void;
    merge?(command: CanvasCommand): boolean;
    description: string;
}

export interface CanvasSnapshot {
    nodes: HierarchicalNode[];
    connections: Connection[];
    selectedNodeIds: string[];
}

/**
 * Add Node Command
 */
export class AddNodeCommand implements CanvasCommand {
    description = 'Add node';

    constructor(
        private node: HierarchicalNode,
        private addFn: (node: HierarchicalNode) => void,
        private removeFn: (nodeId: string) => void
    ) { }

    execute(): void {
        this.addFn(this.node);
    }

    undo(): void {
        this.removeFn(this.node.id);
    }

    redo(): void {
        this.execute();
    }
}

/**
 * Remove Node Command
 */
export class RemoveNodeCommand implements CanvasCommand {
    description = 'Remove node';

    constructor(
        private node: HierarchicalNode,
        private addFn: (node: HierarchicalNode) => void,
        private removeFn: (nodeId: string) => void
    ) { }

    execute(): void {
        this.removeFn(this.node.id);
    }

    undo(): void {
        this.addFn(this.node);
    }

    redo(): void {
        this.execute();
    }
}

/**
 * Move Node Command
 */
export class MoveNodeCommand implements CanvasCommand {
    description = 'Move node';

    constructor(
        private nodeId: string,
        private oldPosition: { x: number; y: number },
        private newPosition: { x: number; y: number },
        private updateFn: (nodeId: string, updates: Partial<HierarchicalNode>) => void
    ) { }

    execute(): void {
        this.updateFn(this.nodeId, { position: this.newPosition });
    }

    undo(): void {
        this.updateFn(this.nodeId, { position: this.oldPosition });
    }

    redo(): void {
        this.execute();
    }

    merge(command: CanvasCommand): boolean {
        if (command instanceof MoveNodeCommand && command.nodeId === this.nodeId) {
            this.newPosition = command.newPosition;
            return true;
        }
        return false;
    }
}

/**
 * Update Node Command
 */
export class UpdateNodeCommand implements CanvasCommand {
    description = 'Update node';

    constructor(
        private nodeId: string,
        private oldData: Partial<HierarchicalNode>,
        private newData: Partial<HierarchicalNode>,
        private updateFn: (nodeId: string, updates: Partial<HierarchicalNode>) => void
    ) { }

    execute(): void {
        this.updateFn(this.nodeId, this.newData);
    }

    undo(): void {
        this.updateFn(this.nodeId, this.oldData);
    }

    redo(): void {
        this.execute();
    }
}

/**
 * Batch Command - Execute multiple commands as one
 */
export class BatchCommand implements CanvasCommand {
    description = 'Batch operation';

    constructor(private commands: CanvasCommand[]) { }

    execute(): void {
        this.commands.forEach(cmd => cmd.execute());
    }

    undo(): void {
        // Undo in reverse order
        for (let i = this.commands.length - 1; i >= 0; i--) {
            this.commands[i].undo();
        }
    }

    redo(): void {
        this.execute();
    }
}

/**
 * History Manager
 */
export class HistoryManager {
    private undoStack: CanvasCommand[] = [];
    private redoStack: CanvasCommand[] = [];
    private maxHistorySize = 100;
    private isExecuting = false;

    constructor(
        private onHistoryChange?: () => void
    ) { }

    /**
     * Execute a command and add to history
     */
    execute(command: CanvasCommand): void {
        if (this.isExecuting) return;

        this.isExecuting = true;
        try {
            command.execute();

            // Try to merge with previous command
            const lastCommand = this.undoStack[this.undoStack.length - 1];
            if (lastCommand?.merge?.(command)) {
                // Merged successfully, don't add new command
                this.onHistoryChange?.();
                return;
            }

            // Add to undo stack
            this.undoStack.push(command);

            // Clear redo stack
            this.redoStack = [];

            // Limit stack size
            if (this.undoStack.length > this.maxHistorySize) {
                this.undoStack.shift();
            }

            this.onHistoryChange?.();
        } finally {
            this.isExecuting = false;
        }
    }

    /**
     * Undo last command
     */
    undo(): boolean {
        const command = this.undoStack.pop();
        if (!command) return false;

        this.isExecuting = true;
        try {
            command.undo();
            this.redoStack.push(command);
            this.onHistoryChange?.();
            return true;
        } finally {
            this.isExecuting = false;
        }
    }

    /**
     * Redo last undone command
     */
    redo(): boolean {
        const command = this.redoStack.pop();
        if (!command) return false;

        this.isExecuting = true;
        try {
            command.redo();
            this.undoStack.push(command);
            this.onHistoryChange?.();
            return true;
        } finally {
            this.isExecuting = false;
        }
    }

    /**
     * Check if can undo
     */
    canUndo(): boolean {
        return this.undoStack.length > 0;
    }

    /**
     * Check if can redo
     */
    canRedo(): boolean {
        return this.redoStack.length > 0;
    }

    /**
     * Clear history
     */
    clear(): void {
        this.undoStack = [];
        this.redoStack = [];
        this.onHistoryChange?.();
    }

    /**
     * Get history info
     */
    getInfo(): { undoCount: number; redoCount: number } {
        return {
            undoCount: this.undoStack.length,
            redoCount: this.redoStack.length
        };
    }
}
