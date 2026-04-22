/**
 * @fileoverview Builder collab event bridge for YAPPC.
 *
 * Bridges CollaborationManager lifecycle events into the canonical
 * `@ghatana/platform-events` builder collab event taxonomy so the rest of
 * the platform (observability, analytics, UI state) can react to collab
 * transitions without coupling directly to the Yjs provider.
 *
 * Usage:
 * ```ts
 * const bridge = new CollabEventBridge({
 *   manager,
 *   sessionId: 'session-abc',
 *   documentId: 'doc-xyz',
 *   userId: 'user-123',
 *   emit: (event) => platformEventBus.emit(event),
 * });
 * bridge.attach();
 * // ... later:
 * bridge.detach();
 * ```
 *
 * @doc.type class
 * @doc.purpose Bridges CollaborationManager events to platform builder collab events
 * @doc.layer product
 * @doc.pattern Adapter
 */

import { BuilderEvents } from '@ghatana/platform-events';
import type { CollabPayload } from '@ghatana/platform-events';
import type { CollaborationManager } from './CollaborationManager';

// ============================================================================
// Types
// ============================================================================

/** A minimal typed builder collab event as emitted by the bridge. */
export interface BuilderCollabEvent<K extends keyof typeof BuilderEvents> {
  readonly name: (typeof BuilderEvents)[K];
  readonly payload: CollabPayload;
}

/** Emit function shape — accepts any builder collab event. */
export type CollabEventEmit = (event: {
  readonly name: string;
  readonly payload: CollabPayload;
}) => void;

export interface CollabEventBridgeConfig {
  /** The CollaborationManager instance to observe. */
  readonly manager: CollaborationManager;
  /** Platform-level session identifier for this collaboration session. */
  readonly sessionId: string;
  /** Builder document identifier being collaborated on. */
  readonly documentId: string;
  /** Local user's identifier. */
  readonly userId: string;
  /**
   * Callback that receives each typed platform event.
   * Wire this to `@ghatana/realtime` or your product-level event bus.
   */
  readonly emit: CollabEventEmit;
}

// ============================================================================
// Bridge
// ============================================================================

/**
 * Bridges `CollaborationManager` lifecycle events into canonical
 * `@ghatana/platform-events` builder collab events.
 *
 * Attach after `manager.connect()` resolves; detach before `manager.disconnect()`.
 */
export class CollabEventBridge {
  private readonly config: CollabEventBridgeConfig;
  private attached = false;

  // Unsubscribe functions returned by CollaborationManager.on()
  private unsubAwareness: (() => void) | null = null;
  private unsubConnection: (() => void) | null = null;

  constructor(config: CollabEventBridgeConfig) {
    this.config = config;
  }

  /** Attach the bridge to the CollaborationManager. Idempotent. */
  attach(): void {
    if (this.attached) return;

    this.unsubAwareness = this.config.manager.on('awareness-change', ({ users }) => {
      this.emitParticipantCount(users.length);
    });

    this.unsubConnection = this.config.manager.on('connection-change', ({ connected }) => {
      if (connected) {
        this.emit(BuilderEvents.COLLAB_JOINED, this.makePayload());
      } else {
        this.emit(BuilderEvents.COLLAB_LEFT, this.makePayload());
      }
    });

    // Emit an initial joined event
    this.emit(BuilderEvents.COLLAB_JOINED, this.makePayload());
    this.attached = true;
  }

  /** Detach the bridge. Idempotent. */
  detach(): void {
    if (!this.attached) return;
    this.unsubAwareness?.();
    this.unsubConnection?.();
    this.unsubAwareness = null;
    this.unsubConnection = null;
    this.emit(BuilderEvents.COLLAB_LEFT, this.makePayload());
    this.attached = false;
  }

  /**
   * Emit a conflict-detected event. Call this from the conflict resolution
   * layer (e.g., CRDT divergence handler).
   */
  emitConflictDetected(
    conflictDetails?: {
      readonly conflictingRegions: readonly string[];
      readonly resolutionStrategy: 'accept-remote' | 'accept-local' | 'merge' | 'manual';
    },
  ): void {
    this.emit(BuilderEvents.COLLAB_CONFLICT_DETECTED, {
      ...this.makePayload(),
      conflictDetails,
    });
  }

  /**
   * Emit a conflict-resolved event. Call this after the conflict has been
   * reconciled (e.g., user chose a winner or auto-merge succeeded).
   */
  emitConflictResolved(
    conflictDetails?: {
      readonly conflictingRegions: readonly string[];
      readonly resolutionStrategy: 'accept-remote' | 'accept-local' | 'merge' | 'manual';
    },
  ): void {
    this.emit(BuilderEvents.COLLAB_CONFLICT_RESOLVED, {
      ...this.makePayload(),
      conflictDetails,
    });
  }

  // --------------------------------------------------------------------------
  // Private helpers
  // --------------------------------------------------------------------------

  private makePayload(overrides: Partial<CollabPayload> = {}): CollabPayload {
    return {
      sessionId: this.config.sessionId,
      documentId: this.config.documentId,
      userId: this.config.userId,
      participantCount: this.config.manager.getState().users.length,
      ...overrides,
    };
  }

  private emitParticipantCount(participantCount: number): void {
    // Emit as a joined update with the latest participant count.
    this.emit(BuilderEvents.COLLAB_JOINED, {
      ...this.makePayload(),
      participantCount,
    });
  }

  private emit(eventKey: string, payload: CollabPayload): void {
    this.config.emit({ name: eventKey, payload });
  }
}
