/**
 * Canvas History Manager
 * 
 * Implements undo/redo functionality for canvas operations.
 * Uses Command pattern with history stack management.
 * 
 * @doc.type utility
 * @doc.purpose Canvas undo/redo history
 * @doc.layer product
 * @doc.pattern Command + Memento
 */

export interface CanvasCommand<T = unknown> {
    execute: (state: T) => T;
    undo: (state: T) => T;
    description: string;
}

export interface HistoryState<T> {
    past: T[];
    present: T;
    future: T[];
}

export interface HistoryOptions {
    maxHistorySize?: number;
    enableGrouping?: boolean;
    groupingDelay?: number; // milliseconds
}

const DEFAULT_OPTIONS: Required<HistoryOptions> = {
    maxHistorySize: 50,
    enableGrouping: true,
    groupingDelay: 1000,
};

/**
 * Canvas History Manager
 */
export class CanvasHistoryManager<T> {
    private past: T[] = [];
    private present: T;
    private future: T[] = [];
    private options: Required<HistoryOptions>;
    private lastCommandTime: number = 0;
    private groupedCommands: CanvasCommand<T>[] = [];

    constructor(initialState: T, options: HistoryOptions = {}) {
        this.present = initialState;
        this.options = { ...DEFAULT_OPTIONS, ...options };
    }

    /**
     * Execute a command and add to history
     */
    execute(command: CanvasCommand<T>): T {
        const newState = command.execute(this.present);

        // Check if we should group this command with previous ones
        const now = Date.now();
        const shouldGroup =
            this.options.enableGrouping &&
            now - this.lastCommandTime < this.options.groupingDelay &&
            this.groupedCommands.length > 0;

        if (shouldGroup) {
            this.groupedCommands.push(command);
        } else {
            // Flush any grouped commands first
            if (this.groupedCommands.length > 0) {
                this.flushGroupedCommands();
            }

            // Start new group
            this.groupedCommands = [command];
            this.lastCommandTime = now;
        }

        // Add current state to past
        this.past.push(this.present);

        // Limit history size
        if (this.past.length > this.options.maxHistorySize) {
            this.past.shift();
        }

        // Clear future (can't redo after new action)
        this.future = [];

        // Update present
        this.present = newState;

        return this.present;
    }

    /**
     * Undo last command
     */
    undo(): T | null {
        this.flushGroupedCommands();

        if (this.past.length === 0) {
            return null;
        }

        const previous = this.past.pop()!;
        this.future.push(this.present);
        this.present = previous;

        return this.present;
    }

    /**
     * Redo last undone command
     */
    redo(): T | null {
        if (this.future.length === 0) {
            return null;
        }

        const next = this.future.pop()!;
        this.past.push(this.present);
        this.present = next;

        return this.present;
    }

    /**
     * Get current state
     */
    getState(): T {
        return this.present;
    }

    /**
     * Get full history state
     */
    getHistoryState(): HistoryState<T> {
        return {
            past: [...this.past],
            present: this.present,
            future: [...this.future],
        };
    }

    /**
     * Check if can undo
     */
    canUndo(): boolean {
        return this.past.length > 0;
    }

    /**
     * Check if can redo
     */
    canRedo(): boolean {
        return this.future.length > 0;
    }

    /**
     * Clear all history
     */
    clear(): void {
        this.past = [];
        this.future = [];
        this.groupedCommands = [];
    }

    /**
     * Reset to specific state
     */
    reset(state: T): void {
        this.clear();
        this.present = state;
    }

    /**
     * Flush grouped commands
     */
    private flushGroupedCommands(): void {
        // No-op, grouping is handled in execute
        this.groupedCommands = [];
    }
}

/**
 * React hook for canvas history
 */
export function useCanvasHistory<T>(
    initialState: T,
    options: HistoryOptions = {}
) {
    const manager = new CanvasHistoryManager<T>(initialState, options);

    return {
        execute: (command: CanvasCommand<T>) => manager.execute(command),
        undo: () => manager.undo(),
        redo: () => manager.redo(),
        getState: () => manager.getState(),
        canUndo: () => manager.canUndo(),
        canRedo: () => manager.canRedo(),
        clear: () => manager.clear(),
        reset: (state: T) => manager.reset(state),
    };
}

/**
 * Helper to create simple commands
 */
export function createCommand<T>(
    description: string,
    execute: (state: T) => T,
    undo: (state: T) => T
): CanvasCommand<T> {
    return { description, execute, undo };
}
