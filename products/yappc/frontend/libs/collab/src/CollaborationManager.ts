/**
 * Real-time Collaboration Provider
 *
 * @description CRDT-based real-time collaboration using Yjs for
 * document synchronization across multiple users.
 */

import * as Y from 'yjs';
import { WebsocketProvider } from 'y-websocket';
import { IndexeddbPersistence } from 'y-indexeddb';

// =============================================================================
// Types
// =============================================================================

export interface CollaborationConfig {
  roomId: string;
  userId: string;
  userName: string;
  userColor: string;
  serverUrl?: string;
}

export interface CollaborationUser {
  id: string;
  name: string;
  color: string;
  cursor?: {
    x: number;
    y: number;
  };
  selection?: {
    start: number;
    end: number;
  };
  lastActive: number;
}

export interface CollaborationState {
  connected: boolean;
  synced: boolean;
  users: CollaborationUser[];
}

export type CollaborationEventType =
  | 'connection-change'
  | 'sync-change'
  | 'awareness-change'
  | 'document-change';

export interface CollaborationEvent {
  type: CollaborationEventType;
  data: unknown;
}

// =============================================================================
// Collaboration Manager Class
// =============================================================================

export class CollaborationManager {
  private doc: Y.Doc;
  private provider: WebsocketProvider | null = null;
  private persistence: IndexeddbPersistence | null = null;
  private config: CollaborationConfig;
  private listeners: Map<CollaborationEventType, Set<(event: CollaborationEvent) => void>>;
  private state: CollaborationState;

  constructor(config: CollaborationConfig) {
    this.config = config;
    this.doc = new Y.Doc();
    this.listeners = new Map();
    this.state = {
      connected: false,
      synced: false,
      users: [],
    };
  }

  /**
   * Connect to the collaboration server
   */
  connect(): Promise<void> {
    return new Promise((resolve, reject) => {
      try {
        // Initialize IndexedDB persistence for offline support
        this.persistence = new IndexeddbPersistence(
          `yappc-collab-${this.config.roomId}`,
          this.doc
        );

        this.persistence.on('synced', () => {
          console.log('[Collab] Local persistence synced');
        });

        // Initialize WebSocket provider
        const serverUrl = this.config.serverUrl || 'ws://localhost:7003/ws';
        this.provider = new WebsocketProvider(
          serverUrl,
          this.config.roomId,
          this.doc,
          { connect: true }
        );

        // Set up awareness (cursor positions, user presence)
        this.provider.awareness.setLocalStateField('user', {
          id: this.config.userId,
          name: this.config.userName,
          color: this.config.userColor,
          lastActive: Date.now(),
        });

        // Connection status
        this.provider.on('status', (event: { status: string }) => {
          const connected = event.status === 'connected';
          this.state.connected = connected;
          this.emit('connection-change', { connected });
        });

        // Sync status
        this.provider.on('sync', (isSynced: boolean) => {
          this.state.synced = isSynced;
          this.emit('sync-change', { synced: isSynced });
          if (isSynced) {
            resolve();
          }
        });

        // Awareness changes (other users)
        this.provider.awareness.on('change', () => {
          const users = this.getConnectedUsers();
          this.state.users = users;
          this.emit('awareness-change', { users });
        });

        // Document changes
        this.doc.on('update', (update: Uint8Array, origin: unknown) => {
          this.emit('document-change', { update, origin });
        });
      } catch (error) {
        reject(error);
      }
    });
  }

  /**
   * Disconnect from the collaboration server
   */
  disconnect(): void {
    if (this.provider) {
      this.provider.destroy();
      this.provider = null;
    }
    if (this.persistence) {
      this.persistence.destroy();
      this.persistence = null;
    }
    this.state.connected = false;
    this.state.synced = false;
    this.state.users = [];
  }

  /**
   * Get the Yjs document
   */
  getDocument(): Y.Doc {
    return this.doc;
  }

  /**
   * Get a shared map from the document
   */
  getMap<T = unknown>(name: string): Y.Map<T> {
    return this.doc.getMap<T>(name);
  }

  /**
   * Get a shared array from the document
   */
  getArray<T = unknown>(name: string): Y.Array<T> {
    return this.doc.getArray<T>(name);
  }

  /**
   * Get a shared text from the document
   */
  getText(name: string): Y.Text {
    return this.doc.getText(name);
  }

  /**
   * Get all connected users
   */
  getConnectedUsers(): CollaborationUser[] {
    if (!this.provider) return [];

    const users: CollaborationUser[] = [];
    this.provider.awareness.getStates().forEach((state: unknown, clientId: number) => {
      if (state.user && clientId !== this.doc.clientID) {
        users.push({
          ...state.user,
          cursor: state.cursor,
          selection: state.selection,
        });
      }
    });
    return users;
  }

  /**
   * Update local cursor position
   */
  updateCursor(x: number, y: number): void {
    if (this.provider) {
      this.provider.awareness.setLocalStateField('cursor', { x, y });
      this.provider.awareness.setLocalStateField('lastActive', Date.now());
    }
  }

  /**
   * Update local selection
   */
  updateSelection(start: number, end: number): void {
    if (this.provider) {
      this.provider.awareness.setLocalStateField('selection', { start, end });
    }
  }

  /**
   * Clear local selection
   */
  clearSelection(): void {
    if (this.provider) {
      this.provider.awareness.setLocalStateField('selection', null);
    }
  }

  /**
   * Get current state
   */
  getState(): CollaborationState {
    return { ...this.state };
  }

  /**
   * Subscribe to events
   */
  on(
    type: CollaborationEventType,
    callback: (event: CollaborationEvent) => void
  ): () => void {
    if (!this.listeners.has(type)) {
      this.listeners.set(type, new Set());
    }
    this.listeners.get(type)!.add(callback);

    // Return unsubscribe function
    return () => {
      this.listeners.get(type)?.delete(callback);
    };
  }

  /**
   * Emit an event
   */
  private emit(type: CollaborationEventType, data: unknown): void {
    const event: CollaborationEvent = { type, data };
    this.listeners.get(type)?.forEach((callback) => callback(event));
  }

  /**
   * Perform a transactional update
   */
  transact(fn: () => void): void {
    this.doc.transact(fn);
  }

  /**
   * Undo the last operation
   */
  undo(): void {
    // Would need UndoManager integration
  }

  /**
   * Redo the last undone operation
   */
  redo(): void {
    // Would need UndoManager integration
  }
}

// =============================================================================
// Factory Function
// =============================================================================

let collaborationInstance: CollaborationManager | null = null;

export function getCollaboration(config?: CollaborationConfig): CollaborationManager | null {
  if (config) {
    collaborationInstance = new CollaborationManager(config);
  }
  return collaborationInstance;
}

export function destroyCollaboration(): void {
  if (collaborationInstance) {
    collaborationInstance.disconnect();
    collaborationInstance = null;
  }
}

export default CollaborationManager;
