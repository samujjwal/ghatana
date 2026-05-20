/**
 * @fileoverview Canvas command model types.
 *
 * A lightweight command model that layers over the existing snapshot-based
 * undo/redo history. Commands encapsulate a mutation description and a
 * transaction boundary mechanism so composite operations always produce
 * a single, atomic undo/redo step.
 *
 * Design rationale
 * ----------------
 * The controller already operates on Jotai atoms and maintains a snapshot
 * ring-buffer for undo/redo. The command model does not replace that
 * mechanism — it adds:
 *
 *   1. A formal `CanvasCommand` interface for named, describable operations.
 *   2. `CompositeCommand` for grouping multiple atomic commands under one
 *      history entry (preventing multi-step undo for group/duplicate/ungroup).
 *   3. `CommandTransaction` for imperative begin/commit syntax when callers
 *      cannot easily compose `CanvasCommand` objects up front.
 *
 * @doc.type module
 * @doc.purpose Canvas command model types
 * @doc.layer core
 * @doc.pattern Command
 */

import type { HistoryEntry } from '../hybrid/state.js';

// ============================================================================
// Core command interface
// ============================================================================

/**
 * A canvas command represents a single logical mutation to the canvas state.
 * Each command is responsible for declaring what it does (description) and
 * for carrying out the mutation given a mutable state snapshot.
 *
 * Commands are executed by a `CanvasCommandExecutor` which manages the
 * history boundary around the execution.
 */
export interface CanvasCommand {
  /** Human-readable description used as the history entry label. */
  readonly description: string;

  /**
   * Apply the mutation to the canvas.
   *
   * The executor captures a pre-execution snapshot *before* calling
   * `execute`, so commands must mutate the canvas state directly
   * (e.g., via controller methods) without worrying about snapshot timing.
   *
   * @param ctx Execution context providing access to mutation methods.
   */
  execute(ctx: CanvasCommandContext): void;
}

/**
 * Execution context provided to commands.
 * Exposes mutation primitives without coupling commands to the full
 * `HybridCanvasController` type (breaking the circular dependency).
 */
export interface CanvasCommandContext {
  /**
   * Push a raw history entry onto the undo stack.
   * Commands generally do not need to call this — the executor does it.
   * This is exposed for advanced commands that manage history explicitly.
   */
  pushHistory(params: { action: string; snapshot: HistoryEntry['snapshot'] }): void;

  /**
   * Get a fresh snapshot of the mutable arrays tracked by history.
   * Must reflect the state at the moment of the call.
   */
  getSnapshot(): HistoryEntry['snapshot'];

  /**
   * Check if currently inside a transaction context.
   * When true, individual history pushes are suppressed.
   */
  isInTransaction(): boolean;
}

// ============================================================================
// Composite command
// ============================================================================

/**
 * Wraps multiple `CanvasCommand` instances into a single atomic step.
 *
 * On undo, the entire composite is reversed in one step, matching user intent
 * for multi-stage operations such as group, ungroup, and duplicate.
 *
 * Usage:
 * ```ts
 * const cmd = new CompositeCommand('Duplicate selection', [
 *   new AddElementCommand(newElement),
 *   new AddNodeCommand(newNode),
 * ]);
 * executor.execute(cmd);
 * ```
 */
export class CompositeCommand implements CanvasCommand {
  readonly description: string;
  private readonly commands: readonly CanvasCommand[];

  constructor(description: string, commands: CanvasCommand[]) {
    this.description = description;
    this.commands = commands;
  }

  execute(ctx: CanvasCommandContext): void {
    for (const command of this.commands) {
      command.execute(ctx);
    }
  }
}

// ============================================================================
// Transaction
// ============================================================================

/**
 * Imperative transaction boundary for situations where commands cannot be
 * composed up front (e.g., when intermediate results determine subsequent
 * mutations).
 *
 * The controller manages the transaction context (pre-snapshot and suppression).
 * This class provides a convenient API for callers to begin/commit/abort transactions.
 *
 * Usage:
 * ```ts
 * const tx = controller.beginTransaction('Group elements');
 * // ... perform mutations via the controller ...
 * tx.commit();
 * ```
 *
 * Only one undo/redo entry is created per committed transaction, regardless
 * of how many individual mutations occurred inside it.
 *
 * If `commit()` is never called, the transaction context is cleared on abort
 * (or when the transaction object goes out of scope, the controller's context
 * is reset by the next operation).
 */
export class CommandTransaction {
  private readonly controller: any; // HybridCanvasController (avoid circular import)
  private readonly description: string;
  private committed = false;

  constructor(controller: any, description: string) {
    this.controller = controller;
    this.description = description;
  }

  /**
   * Mark the transaction as committed.
   * The controller will push the single history entry using the pre-snapshot
   * captured when beginTransaction was called.
   *
   * Safe to call multiple times — only the first commit pushes history.
   */
  commit(): void {
    if (this.committed) {
      return;
    }
    this.controller.commitTransaction();
    this.committed = true;
  }

  /**
   * Abort the transaction without pushing a history entry.
   * Use this when a transaction is aborted due to validation failure or
   * no effective mutation.
   */
  abort(): void {
    this.controller.abortTransaction();
    this.committed = true;
  }
}
