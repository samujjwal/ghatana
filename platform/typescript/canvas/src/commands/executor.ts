/**
 * @fileoverview CanvasCommandExecutor — integrates the command model with the
 * Jotai-backed canvas history.
 *
 * The executor wraps the controller's `pushHistory` and `getSnapshot` methods
 * so that individual `CanvasCommand` implementations remain decoupled from
 * Jotai atoms and the controller class.
 *
 * @doc.type module
 * @doc.purpose Command execution and history integration
 * @doc.layer core
 * @doc.pattern Command
 */

import type { HistoryEntry } from '../hybrid/state.js';
import type { CanvasCommand, CanvasCommandContext } from './types.js';

// ============================================================================
// Executor
// ============================================================================

/**
 * Minimal interface required by the executor.
 *
 * Allows the executor to be tested independently of `HybridCanvasController`.
 */
export interface CanvasCommandHost {
  pushHistory(params: { action: string; snapshot: HistoryEntry['snapshot'] }): void;
  getSnapshot(): HistoryEntry['snapshot'];
}

/**
 * Executes `CanvasCommand` instances against a `CanvasCommandHost`, wrapping
 * each execution in a single pre-mutation snapshot → push-history cycle.
 *
 * This guarantees that every `execute()` call — including composite commands —
 * produces exactly one undo/redo history entry.
 *
 * Usage:
 * ```ts
 * const executor = new CanvasCommandExecutor(controller);
 * executor.execute(new GroupCommand(selection));
 * // Exactly one history entry is pushed, regardless of how many
 * // sub-operations GroupCommand performs internally.
 * ```
 */
export class CanvasCommandExecutor {
  private readonly host: CanvasCommandHost;

  constructor(host: CanvasCommandHost) {
    this.host = host;
  }

  /**
   * Execute a command.
   *
   * Order of operations:
   * 1. Capture pre-mutation snapshot.
   * 2. Call `command.execute(ctx)`.
   * 3. Push exactly one history entry with the pre-mutation snapshot.
   *
   * @param command The command to execute.
   */
  execute(command: CanvasCommand): void {
    const preSnapshot = this.host.getSnapshot();

    const ctx: CanvasCommandContext = {
      pushHistory: (params) => this.host.pushHistory(params),
      getSnapshot: () => this.host.getSnapshot(),
    };

    command.execute(ctx);

    this.host.pushHistory({
      action: command.description,
      snapshot: preSnapshot,
    });
  }
}
