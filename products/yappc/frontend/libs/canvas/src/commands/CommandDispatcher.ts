/**
 * Command Dispatcher
 *
 * Central command execution engine with undo/redo support.
 * All canvas mutations flow through this dispatcher.
 *
 * @doc.type singleton
 * @doc.purpose Command execution and history management
 * @doc.layer core
 * @doc.pattern Command Pattern, Singleton
 */

import { nanoid } from 'nanoid';
import type {
    Command,
    CommandMeta,
    CommandResult,
    CommandListener,
    CommandInterceptor,
    ICommandDispatcher,
    SerializedCommand,
} from './CommandTypes';

// ============================================================================
// Command Dispatcher Implementation
// ============================================================================

/**
 * Configuration for the command dispatcher
 */
export interface CommandDispatcherConfig {
    /** Maximum undo history depth */
    maxHistoryDepth: number;
    /** Enable command merging for consecutive similar commands */
    enableMerging: boolean;
    /** Merge window in milliseconds */
    mergeWindowMs: number;
    /** Enable command validation before execution */
    enableValidation: boolean;
    /** Persist commands to storage */
    enablePersistence: boolean;
    /** Storage key for persistence */
    persistenceKey?: string;
}

/**
 * Default configuration
 */
const DEFAULT_CONFIG: CommandDispatcherConfig = {
    maxHistoryDepth: 100,
    enableMerging: true,
    mergeWindowMs: 300,
    enableValidation: true,
    enablePersistence: false,
};

/**
 * Command Dispatcher - Singleton
 *
 * Manages command execution, undo/redo, and history.
 */
export class CommandDispatcher implements ICommandDispatcher {
    private static instance: CommandDispatcher | null = null;

    private undoStack: Command[] = [];
    private redoStack: Command[] = [];
    private listeners: Set<CommandListener> = new Set();
    private interceptors: Map<string, CommandInterceptor> = new Map();
    private config: CommandDispatcherConfig;
    private lastCommandTime = 0;
    private isExecuting = false;
    private sessionId: string;

    private constructor(config: Partial<CommandDispatcherConfig> = {}) {
        this.config = { ...DEFAULT_CONFIG, ...config };
        this.sessionId = nanoid();
    }

    /**
     * Get singleton instance
     */
    static getInstance(config?: Partial<CommandDispatcherConfig>): CommandDispatcher {
        if (!CommandDispatcher.instance) {
            CommandDispatcher.instance = new CommandDispatcher(config);
        }
        return CommandDispatcher.instance;
    }

    /**
     * Reset singleton (for testing)
     */
    static resetInstance(): void {
        CommandDispatcher.instance = null;
    }

    /**
     * Execute a command
     */
    async execute<T>(command: Command<unknown, T>): Promise<CommandResult<T>> {
        if (this.isExecuting) {
            return {
                success: false,
                error: 'Command execution already in progress',
                errorCode: 'EXECUTION_IN_PROGRESS',
            };
        }

        this.isExecuting = true;

        try {
            // Run through interceptors
            let processedCommand: Command<unknown, T> | null = command;
            for (const interceptor of this.getSortedInterceptors()) {
                if (interceptor.before) {
                    processedCommand = interceptor.before(processedCommand) as Command<
                        unknown,
                        T
                    > | null;
                    if (!processedCommand) {
                        return {
                            success: false,
                            error: 'Command blocked by interceptor',
                            errorCode: 'INTERCEPTOR_BLOCKED',
                        };
                    }
                }
            }

            // Validate if enabled
            if (this.config.enableValidation && !processedCommand.validate()) {
                return {
                    success: false,
                    error: 'Command validation failed',
                    errorCode: 'VALIDATION_FAILED',
                };
            }

            // Try to merge with last command if within merge window
            if (this.config.enableMerging && this.undoStack.length > 0) {
                const now = Date.now();
                const lastCommand = this.undoStack[this.undoStack.length - 1];

                if (
                    now - this.lastCommandTime < this.config.mergeWindowMs &&
                    processedCommand.canMerge(lastCommand)
                ) {
                    // Merge and replace last command
                    const merged = processedCommand.merge(lastCommand) as Command<unknown, T>;
                    this.undoStack[this.undoStack.length - 1] = merged;
                    processedCommand = merged;
                }
            }

            // Execute the command
            const result = await processedCommand.execute();

            if (result.success) {
                // Add to undo stack (if not merged)
                if (
                    this.undoStack.length === 0 ||
                    this.undoStack[this.undoStack.length - 1] !== processedCommand
                ) {
                    this.undoStack.push(processedCommand);
                }

                // Clear redo stack on new command
                this.redoStack = [];

                // Trim history if needed
                while (this.undoStack.length > this.config.maxHistoryDepth) {
                    this.undoStack.shift();
                }

                this.lastCommandTime = Date.now();

                // Persist if enabled
                if (this.config.enablePersistence) {
                    this.persistHistory();
                }
            }

            // Notify listeners
            this.notifyListeners(processedCommand, result as CommandResult<T>);

            // Run after interceptors
            for (const interceptor of this.getSortedInterceptors()) {
                if (interceptor.after) {
                    interceptor.after(processedCommand, result as CommandResult<T>);
                }
            }

            return result as CommandResult<T>;
        } catch (error) {
            const errorMessage =
                error instanceof Error ? error.message : 'Unknown error during execution';

            // Notify error interceptors
            for (const interceptor of this.getSortedInterceptors()) {
                if (interceptor.onError && error instanceof Error) {
                    interceptor.onError(command, error);
                }
            }

            return {
                success: false,
                error: errorMessage,
                errorCode: 'EXECUTION_ERROR',
            };
        } finally {
            this.isExecuting = false;
        }
    }

    /**
     * Undo last command
     */
    async undo(): Promise<CommandResult> {
        if (!this.canUndo()) {
            return {
                success: false,
                error: 'Nothing to undo',
                errorCode: 'EMPTY_UNDO_STACK',
            };
        }

        const command = this.undoStack.pop()!;

        try {
            const result = await command.undo();

            if (result.success) {
                this.redoStack.push(command);

                // Persist if enabled
                if (this.config.enablePersistence) {
                    this.persistHistory();
                }
            } else {
                // Put command back on failure
                this.undoStack.push(command);
            }

            this.notifyListeners(command, result);
            return result;
        } catch (error) {
            // Put command back on error
            this.undoStack.push(command);

            return {
                success: false,
                error: error instanceof Error ? error.message : 'Unknown undo error',
                errorCode: 'UNDO_ERROR',
            };
        }
    }

    /**
     * Redo last undone command
     */
    async redo(): Promise<CommandResult> {
        if (!this.canRedo()) {
            return {
                success: false,
                error: 'Nothing to redo',
                errorCode: 'EMPTY_REDO_STACK',
            };
        }

        const command = this.redoStack.pop()!;

        try {
            const result = await command.execute();

            if (result.success) {
                this.undoStack.push(command);

                // Persist if enabled
                if (this.config.enablePersistence) {
                    this.persistHistory();
                }
            } else {
                // Put command back on failure
                this.redoStack.push(command);
            }

            this.notifyListeners(command, result);
            return result;
        } catch (error) {
            // Put command back on error
            this.redoStack.push(command);

            return {
                success: false,
                error: error instanceof Error ? error.message : 'Unknown redo error',
                errorCode: 'REDO_ERROR',
            };
        }
    }

    /**
     * Check if undo is available
     */
    canUndo(): boolean {
        return this.undoStack.length > 0;
    }

    /**
     * Check if redo is available
     */
    canRedo(): boolean {
        return this.redoStack.length > 0;
    }

    /**
     * Get undo stack depth
     */
    getUndoDepth(): number {
        return this.undoStack.length;
    }

    /**
     * Get redo stack depth
     */
    getRedoDepth(): number {
        return this.redoStack.length;
    }

    /**
     * Subscribe to command events
     */
    subscribe(listener: CommandListener): () => void {
        this.listeners.add(listener);
        return () => this.listeners.delete(listener);
    }

    /**
     * Add an interceptor
     */
    addInterceptor(interceptor: CommandInterceptor): void {
        this.interceptors.set(interceptor.id, interceptor);
    }

    /**
     * Remove an interceptor
     */
    removeInterceptor(id: string): void {
        this.interceptors.delete(id);
    }

    /**
     * Clear history
     */
    clearHistory(): void {
        this.undoStack = [];
        this.redoStack = [];

        if (this.config.enablePersistence) {
            this.clearPersistedHistory();
        }
    }

    /**
     * Get command history
     */
    getHistory(): readonly SerializedCommand[] {
        return this.undoStack.map((cmd) => cmd.serialize());
    }

    /**
     * Get session ID
     */
    getSessionId(): string {
        return this.sessionId;
    }

    /**
     * Create command metadata
     */
    createMeta(
        partial: Partial<CommandMeta> = {},
        source: CommandMeta['source'] = 'user'
    ): CommandMeta {
        return {
            commandId: partial.commandId ?? nanoid(),
            userId: partial.userId ?? 'anonymous',
            timestamp: partial.timestamp ?? Date.now(),
            source: partial.source ?? source,
            sessionId: partial.sessionId ?? this.sessionId,
            description: partial.description,
            causedBy: partial.causedBy,
        };
    }

    /**
     * Execute a batch of commands atomically
     */
    async executeBatch(
        commands: Command[],
        atomic = true
    ): Promise<CommandResult<CommandResult[]>> {
        const results: CommandResult[] = [];

        for (const command of commands) {
            const result = await this.execute(command);
            results.push(result);

            if (!result.success && atomic) {
                // Rollback all previous commands
                for (let i = results.length - 2; i >= 0; i--) {
                    await this.undo();
                }

                return {
                    success: false,
                    error: `Batch failed at command ${results.length}: ${result.error}`,
                    errorCode: 'BATCH_FAILED',
                    data: results,
                };
            }
        }

        return {
            success: true,
            data: results,
        };
    }

    // ============================================================================
    // Private Methods
    // ============================================================================

    /**
     * Get interceptors sorted by priority
     */
    private getSortedInterceptors(): CommandInterceptor[] {
        return Array.from(this.interceptors.values()).sort(
            (a, b) => a.priority - b.priority
        );
    }

    /**
     * Notify all listeners
     */
    private notifyListeners(command: Command, result: CommandResult): void {
        for (const listener of this.listeners) {
            try {
                listener(command, result);
            } catch (error) {
                console.error('Command listener error:', error);
            }
        }
    }

    /**
     * Persist history to storage
     */
    private persistHistory(): void {
        if (!this.config.persistenceKey) return;

        try {
            const serialized = {
                undoStack: this.undoStack.map((cmd) => cmd.serialize()),
                redoStack: this.redoStack.map((cmd) => cmd.serialize()),
                sessionId: this.sessionId,
                timestamp: Date.now(),
            };

            localStorage.setItem(this.config.persistenceKey, JSON.stringify(serialized));
        } catch (error) {
            console.warn('Failed to persist command history:', error);
        }
    }

    /**
     * Clear persisted history
     */
    private clearPersistedHistory(): void {
        if (!this.config.persistenceKey) return;

        try {
            localStorage.removeItem(this.config.persistenceKey);
        } catch (error) {
            console.warn('Failed to clear persisted history:', error);
        }
    }
}

// ============================================================================
// Convenience Functions
// ============================================================================

/**
 * Get the global command dispatcher instance
 */
export function getCommandDispatcher(
    config?: Partial<CommandDispatcherConfig>
): CommandDispatcher {
    return CommandDispatcher.getInstance(config);
}

/**
 * Execute a command using the global dispatcher
 */
export async function executeCommand<T>(
    command: Command<unknown, T>
): Promise<CommandResult<T>> {
    return getCommandDispatcher().execute(command);
}

/**
 * Undo using the global dispatcher
 */
export async function undoCommand(): Promise<CommandResult> {
    return getCommandDispatcher().undo();
}

/**
 * Redo using the global dispatcher
 */
export async function redoCommand(): Promise<CommandResult> {
    return getCommandDispatcher().redo();
}

// ============================================================================
// React Hook (for UI integration)
// ============================================================================

/**
 * React hook return type for command dispatcher
 */
export interface UseCommandDispatcherReturn {
    execute: <T>(command: Command<unknown, T>) => Promise<CommandResult<T>>;
    undo: () => Promise<CommandResult>;
    redo: () => Promise<CommandResult>;
    canUndo: boolean;
    canRedo: boolean;
    undoDepth: number;
    redoDepth: number;
}

// Note: The actual React hook implementation would be in a separate hooks file
// to keep this file framework-agnostic
