/**
 * @fileoverview Tests for the canvas command model.
 *
 * Validates that:
 * 1. `CanvasCommandExecutor.execute()` captures exactly one history entry.
 * 2. `CompositeCommand` executes all sub-commands under one history entry.
 * 3. `CommandTransaction` batches imperative mutations into one history entry.
 * 4. `HybridCanvasController.execute()` integrates correctly.
 * 5. `HybridCanvasController.beginTransaction()` integrates correctly.
 *
 * @doc.type test
 * @doc.purpose Command model unit tests
 * @doc.layer canvas
 */

import { describe, it, expect, beforeEach } from 'vitest';
import { CanvasCommandExecutor } from '../executor.js';
import { CompositeCommand, CommandTransaction } from '../types.js';
import type { CanvasCommand, CanvasCommandContext, CanvasCommandHost } from '../types.js';
import type { HistoryEntry } from '../../hybrid/state.js';

// ============================================================================
// Test fixtures
// ============================================================================

type Snapshot = HistoryEntry['snapshot'];

function makeSnapshot(tag: string): Snapshot {
  return {
    elements: [{ id: tag } as never],
    nodes: [],
    edges: [],
  };
}

/**
 * A fake `CanvasCommandHost` that records every `pushHistory` call and
 * supports getting a mutable snapshot.
 */
function makeFakeHost(initialTag = 'initial'): {
  host: CanvasCommandHost;
  history: Array<{ action: string; snapshot: Snapshot }>;
  currentTag: { value: string };
} {
  const history: Array<{ action: string; snapshot: Snapshot }> = [];
  const currentTag = { value: initialTag };

  const host: CanvasCommandHost = {
    pushHistory(params) {
      history.push({ action: params.action, snapshot: params.snapshot });
    },
    getSnapshot() {
      return makeSnapshot(currentTag.value);
    },
  };

  return { host, history, currentTag };
}

/**
 * A simple command that mutates the `currentTag` and records when it ran.
 */
function makeTagCommand(description: string, tag: string, ran: { count: number }): CanvasCommand {
  return {
    description,
    execute(ctx) {
      // simulate mutating state by updating the host's tag via closure
      // (ctx.getSnapshot() would reflect the new state in a real scenario)
      ran.count++;
      void ctx; // prevent unused-var lint
    },
  };
}

// ============================================================================
// CanvasCommandExecutor
// ============================================================================

describe('CanvasCommandExecutor', () => {
  it('captures pre-mutation snapshot and pushes one history entry', () => {
    const { host, history, currentTag } = makeFakeHost('before');
    const executor = new CanvasCommandExecutor(host);

    const ran = { count: 0 };
    const cmd = makeTagCommand('Do something', 'after', ran);

    // Simulate mutation happening inside execute
    const originalExecute = cmd.execute.bind(cmd);
    const mutatingCmd: CanvasCommand = {
      description: cmd.description,
      execute(ctx) {
        originalExecute(ctx);
        currentTag.value = 'after'; // mutate after pre-snapshot is taken
      },
    };

    executor.execute(mutatingCmd);

    expect(history).toHaveLength(1);
    expect(history[0]?.action).toBe('Do something');
    // The snapshot should reflect the PRE-mutation state ('before')
    expect((history[0]?.snapshot.elements[0] as { id: string } | undefined)?.id).toBe('before');
  });

  it('pushes one entry even if command is a no-op', () => {
    const { host, history } = makeFakeHost();
    const executor = new CanvasCommandExecutor(host);

    const noOp: CanvasCommand = {
      description: 'No-op',
      execute: () => { /* nothing */ },
    };

    executor.execute(noOp);

    expect(history).toHaveLength(1);
    expect(history[0]?.action).toBe('No-op');
  });
});

// ============================================================================
// CompositeCommand
// ============================================================================

describe('CompositeCommand', () => {
  it('executes all sub-commands in order', () => {
    const executionOrder: number[] = [];

    const cmd1: CanvasCommand = {
      description: 'Step 1',
      execute: () => { executionOrder.push(1); },
    };
    const cmd2: CanvasCommand = {
      description: 'Step 2',
      execute: () => { executionOrder.push(2); },
    };
    const cmd3: CanvasCommand = {
      description: 'Step 3',
      execute: () => { executionOrder.push(3); },
    };

    const composite = new CompositeCommand('Composite', [cmd1, cmd2, cmd3]);

    // Use a dummy context
    const ctx: CanvasCommandContext = {
      pushHistory: () => { /* no-op */ },
      getSnapshot: () => ({ elements: [], nodes: [], edges: [] }),
      isInTransaction: () => false,
    };

    composite.execute(ctx);

    expect(executionOrder).toEqual([1, 2, 3]);
  });

  it('produces exactly ONE history entry when executed via the executor', () => {
    const { host, history } = makeFakeHost();
    const executor = new CanvasCommandExecutor(host);

    const ran = { count: 0 };
    const composite = new CompositeCommand('Grouped operation', [
      makeTagCommand('Part A', 'a', ran),
      makeTagCommand('Part B', 'b', ran),
      makeTagCommand('Part C', 'c', ran),
    ]);

    executor.execute(composite);

    expect(ran.count).toBe(3); // all sub-commands ran
    expect(history).toHaveLength(1); // exactly one history entry
    expect(history[0]?.action).toBe('Grouped operation');
  });
});

// ============================================================================
// CommandTransaction
// ============================================================================

describe('CommandTransaction', () => {
  function makeFakeTransactionController() {
    return {
      commitCount: 0,
      abortCount: 0,
      commitTransaction() {
        this.commitCount++;
      },
      abortTransaction() {
        this.abortCount++;
      },
    };
  }

  it('delegates commit to the controller transaction context', () => {
    const controller = makeFakeTransactionController();
    const tx = new CommandTransaction(controller, 'Transaction');

    tx.commit();

    expect(controller.commitCount).toBe(1);
    expect(controller.abortCount).toBe(0);
  });

  it('lets the controller decide whether a commit has an active transaction', () => {
    const controller = makeFakeTransactionController();
    const tx = new CommandTransaction(controller, 'Never started');

    tx.commit();

    expect(controller.commitCount).toBe(1);
  });

  it('delegates abort to the controller and prevents later commit', () => {
    const controller = makeFakeTransactionController();
    const tx = new CommandTransaction(controller, 'Aborted transaction');

    tx.abort();
    tx.commit(); // commit after abort should be a no-op

    expect(controller.abortCount).toBe(1);
    expect(controller.commitCount).toBe(0);
  });

  it('only commits once for multiple commit() calls', () => {
    const controller = makeFakeTransactionController();
    const tx = new CommandTransaction(controller, 'Double commit');

    tx.commit();
    tx.commit(); // second call should not push again

    expect(controller.commitCount).toBe(1);
  });
});

// ============================================================================
// HybridCanvasController integration
// ============================================================================

describe('HybridCanvasController.execute()', () => {
  it('exposes execute() and beginTransaction() that use the command model', async () => {
    // Dynamic import to avoid JSdom requirement for the controller's React deps
    const { HybridCanvasController } = await import('../../hybrid/hybrid-canvas-controller.js');

    const ctrl = new HybridCanvasController();
    expect(typeof ctrl.execute).toBe('function');
    expect(typeof ctrl.beginTransaction).toBe('function');
    expect(typeof ctrl.getSnapshot).toBe('function');
  });

  it('execute() adds one undo entry', async () => {
    const { HybridCanvasController } = await import('../../hybrid/hybrid-canvas-controller.js');
    const ctrl = new HybridCanvasController();

    expect(ctrl.canUndo()).toBe(false);

    const cmd: CanvasCommand = {
      description: 'Test command',
      execute: () => { /* no-op mutation */ },
    };

    ctrl.execute(cmd);
    expect(ctrl.canUndo()).toBe(true);
  });

  it('beginTransaction commits produce one undo entry', async () => {
    const { HybridCanvasController } = await import('../../hybrid/hybrid-canvas-controller.js');
    const ctrl = new HybridCanvasController();

    expect(ctrl.canUndo()).toBe(false);

    const tx = ctrl.beginTransaction('Batch update');
    // ... multiple mutations could happen here ...
    tx.commit();

    expect(ctrl.canUndo()).toBe(true);
  });
});
