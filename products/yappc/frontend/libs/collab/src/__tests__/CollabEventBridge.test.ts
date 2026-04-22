import { describe, it, expect, vi, beforeEach } from 'vitest';
import { CollabEventBridge } from '../CollabEventBridge';
import type { CollabEventBridgeConfig } from '../CollabEventBridge';
import type { CollaborationManager } from '../CollaborationManager';
import { BuilderEvents } from '@ghatana/platform-events';

// Minimal CollaborationManager stub
function makeManager(participantCount = 2): CollaborationManager {
  const handlers: Map<string, Set<(data: unknown) => void>> = new Map();

  return {
    getState: () => ({
      connected: true,
      synced: true,
      users: Array.from({ length: participantCount }, (_, i) => ({
        id: `user-${i}`,
        name: `User ${i}`,
        color: '#fff',
        lastActive: Date.now(),
      })),
    }),
    on(type: string, callback: (data: unknown) => void) {
      if (!handlers.has(type)) handlers.set(type, new Set());
      handlers.get(type)!.add(callback);
      return () => handlers.get(type)?.delete(callback);
    },
    // helper for test: fire an event
    _fire(type: string, data: unknown) {
      handlers.get(type)?.forEach((cb) => cb(data));
    },
  } as unknown as CollaborationManager & { _fire(t: string, d: unknown): void };
}

function makeConfig(
  manager: CollaborationManager,
  emit: CollabEventBridgeConfig['emit'],
): CollabEventBridgeConfig {
  return {
    manager,
    sessionId: 'sess-1',
    documentId: 'doc-1',
    userId: 'user-0',
    emit,
  };
}

describe('CollabEventBridge', () => {
  let emit: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    emit = vi.fn();
  });

  it('emits COLLAB_JOINED on attach()', () => {
    const manager = makeManager();
    const bridge = new CollabEventBridge(makeConfig(manager, emit));
    bridge.attach();
    expect(emit).toHaveBeenCalledWith(
      expect.objectContaining({ name: BuilderEvents.COLLAB_JOINED }),
    );
  });

  it('is idempotent: attach() called twice registers only one listener set', () => {
    const manager = makeManager();
    const bridge = new CollabEventBridge(makeConfig(manager, emit));
    bridge.attach();
    bridge.attach();
    // Should have emitted joined exactly once (second attach is a no-op)
    expect(emit).toHaveBeenCalledTimes(1);
  });

  it('emits COLLAB_LEFT on detach()', () => {
    const manager = makeManager();
    const bridge = new CollabEventBridge(makeConfig(manager, emit));
    bridge.attach();
    emit.mockClear();
    bridge.detach();
    expect(emit).toHaveBeenCalledWith(
      expect.objectContaining({ name: BuilderEvents.COLLAB_LEFT }),
    );
  });

  it('is idempotent: detach() called twice emits left only once', () => {
    const manager = makeManager();
    const bridge = new CollabEventBridge(makeConfig(manager, emit));
    bridge.attach();
    emit.mockClear();
    bridge.detach();
    bridge.detach();
    expect(emit).toHaveBeenCalledTimes(1);
  });

  it('emits COLLAB_JOINED with updated participantCount on awareness-change', () => {
    const manager = makeManager(1) as CollaborationManager & { _fire(t: string, d: unknown): void };
    const bridge = new CollabEventBridge(makeConfig(manager, emit));
    bridge.attach();
    emit.mockClear();
    (manager as unknown as { _fire(t: string, d: unknown): void })._fire('awareness-change', {
      users: [{ id: 'u1' }, { id: 'u2' }, { id: 'u3' }],
    });
    expect(emit).toHaveBeenCalledWith(
      expect.objectContaining({
        name: BuilderEvents.COLLAB_JOINED,
        payload: expect.objectContaining({ participantCount: 3 }),
      }),
    );
  });

  it('emits COLLAB_LEFT on connection-change with connected=false', () => {
    const manager = makeManager() as CollaborationManager & { _fire(t: string, d: unknown): void };
    const bridge = new CollabEventBridge(makeConfig(manager, emit));
    bridge.attach();
    emit.mockClear();
    (manager as unknown as { _fire(t: string, d: unknown): void })._fire('connection-change', { connected: false });
    expect(emit).toHaveBeenCalledWith(
      expect.objectContaining({ name: BuilderEvents.COLLAB_LEFT }),
    );
  });

  it('emits COLLAB_CONFLICT_DETECTED with optional details', () => {
    const manager = makeManager();
    const bridge = new CollabEventBridge(makeConfig(manager, emit));
    bridge.attach();
    emit.mockClear();
    bridge.emitConflictDetected('Concurrent property edits on node-5');
    expect(emit).toHaveBeenCalledWith(
      expect.objectContaining({
        name: BuilderEvents.COLLAB_CONFLICT_DETECTED,
        payload: expect.objectContaining({
          conflictDetails: 'Concurrent property edits on node-5',
        }),
      }),
    );
  });

  it('emits COLLAB_CONFLICT_RESOLVED', () => {
    const manager = makeManager();
    const bridge = new CollabEventBridge(makeConfig(manager, emit));
    bridge.attach();
    emit.mockClear();
    bridge.emitConflictResolved();
    expect(emit).toHaveBeenCalledWith(
      expect.objectContaining({ name: BuilderEvents.COLLAB_CONFLICT_RESOLVED }),
    );
  });

  it('payload includes sessionId, documentId, and userId', () => {
    const manager = makeManager();
    const bridge = new CollabEventBridge(makeConfig(manager, emit));
    bridge.attach();
    const [call] = emit.mock.calls;
    expect(call?.[0].payload).toMatchObject({
      sessionId: 'sess-1',
      documentId: 'doc-1',
      userId: 'user-0',
    });
  });
});
