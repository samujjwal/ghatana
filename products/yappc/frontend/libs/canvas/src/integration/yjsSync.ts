/**
 * Yjs Sync Adapter
 *
 * Implements real-time synchronization using Yjs CRDTs over the unified RealTimeService.
 * Wraps Yjs binary updates in JSON messages for transport compatibility.
 */

import * as Y from 'yjs';
import { SyncAdapter, SyncResult, CanvasChange, SyncConfig } from './types';

export class YjsSyncAdapter implements SyncAdapter {
  readonly type = 'websocket' as const;

  private ws: WebSocket | null = null;
  private doc: Y.Doc;
  private projectId: string;
  private config: SyncConfig;
  private subscribers = new Set<(change: CanvasChange) => void>();
  private updateHandler: (update: Uint8Array, origin: unknown) => void;
  private eventListeners = new Map<string, Set<(arg: unknown) => void>>();

  constructor(config: SyncConfig, projectId: string, doc?: Y.Doc) {
    this.config = config;
    this.projectId = projectId;
    this.doc = doc || new Y.Doc();

    // Listen to local Yjs updates and send to server
    this.updateHandler = (update: Uint8Array, origin: unknown) => {
      if (origin !== this) {
        // Only send local changes
        if (this.ws && this.ws.readyState === WebSocket.OPEN) {
          const base64 = this.uint8ToBase64(update);
          this.ws.send(
            JSON.stringify({
              type: 'crdt-update',
              data: base64,
            })
          );
        }
      }
    };
    this.doc.on('update', this.updateHandler);
  }

  on(event: string, callback: (arg: unknown) => void): void {
    if (!this.eventListeners.has(event)) {
      this.eventListeners.set(event, new Set());
    }
    this.eventListeners.get(event)?.add(callback);
  }

  off(event: string, callback: (arg: unknown) => void): void {
    this.eventListeners.get(event)?.delete(callback);
  }

  private emit(event: string, arg: unknown) {
    this.eventListeners.get(event)?.forEach((cb) => cb(arg));
  }

  async connect(): Promise<void> {
    if (this.isConnected()) return;

    return new Promise((resolve, reject) => {
      // Endpoint format: /ws/canvas/:projectId
      const endpoint = this.config.endpoint.endsWith('/')
        ? this.config.endpoint
        : `${this.config.endpoint}/`;

      const url = `${endpoint}ws/canvas/${this.projectId}`;

      this.ws = new WebSocket(url);

      this.ws.onopen = () => {
        this.emit('status', { status: 'connected' });
        this.emit('sync', true);
        resolve();
      };

      this.ws.onerror = (err) => {
        this.emit('status', { status: 'disconnected' });
        reject(err);
      };

      this.ws.onmessage = (event) => {
        try {
          const msg = JSON.parse(event.data);

          if (msg.type === 'crdt-update') {
            const update = this.base64ToUint8(msg.data);
            Y.applyUpdate(this.doc, update, this); // Pass 'this' as origin

            // Notify subscribers that data changed
            // Note: Subscribers should likely bind to Y.Doc directly,
            // but we fire generic change for compatibility.
            this.notifySubscribers({
              id: 'crdt-update',
              documentId: this.projectId,
              operation: 'update',
              timestamp: Date.now(),
              userId: msg.userId,
              data: {}, // Data is inside Y.Doc
              version: 0,
            });
          }
        } catch (e) {
          console.error('Failed to process message', e);
        }
      };

      this.ws.onclose = () => {
        this.ws = null;
        this.emit('status', { status: 'disconnected' });
        this.emit('sync', false);
      };
    });
  }

  async disconnect(): Promise<void> {
    if (this.ws) {
      this.ws.close();
      this.ws = null;
    }
  }

  isConnected(): boolean {
    return this.ws !== null && this.ws.readyState === WebSocket.OPEN;
  }

  // Yjs is continuous, so pull/push are mostly for initial load or conflict markers
  async pull(_documentId: string): Promise<SyncResult> {
    return { success: true, version: 0, changes: [] };
  }

  async push(
    _documentId: string,
    _changes: CanvasChange[]
  ): Promise<SyncResult> {
    return { success: true, version: 0, changes: [] };
  }

  subscribe(
    documentId: string,
    callback: (change: CanvasChange) => void
  ): () => void {
    this.subscribers.add(callback);
    return () => {
      this.subscribers.delete(callback);
    };
  }

  // Alias for compatibility
  destroy(): void {
    this.disconnect();
  }

  // Public access to the Y.Doc for binding to UI
  public getDoc(): Y.Doc {
    return this.doc;
  }

  private notifySubscribers(change: CanvasChange) {
    this.subscribers.forEach((cb) => cb(change));
  }

  // Helper: Uint8Array -> Base64
  private uint8ToBase64(arr: Uint8Array): string {
    let binary = '';
    const len = arr.byteLength;
    for (let i = 0; i < len; i++) {
      binary += String.fromCharCode(arr[i]);
    }
    return btoa(binary);
  }

  // Helper: Base64 -> Uint8Array
  private base64ToUint8(str: string): Uint8Array {
    const binary = atob(str);
    const len = binary.length;
    const bytes = new Uint8Array(len);
    for (let i = 0; i < len; i++) {
      bytes[i] = binary.charCodeAt(i);
    }
    return bytes;
  }
}
