/**
 * Data Integration Types
 * 
 * Type definitions for REST/GraphQL/WebSocket synchronization,
 * offline queue, and conflict resolution.
 */

// Re-export code generation types
export type { GeneratedFile, CodeGenerationResult } from './codeGeneration';
export type { ServiceNodeData, APIEndpointNodeData, DatabaseNodeData } from './node-types';

// Canvas document type (simplified for integration)
/**
 *
 */
export interface CanvasDocument {
  id: string;
  name: string;
  elements: unknown[];
  version: number;
  createdAt: number;
  updatedAt: number;
  createdBy: string;
  updatedBy: string;
}

/**
 * Sync operation types
 */
export type SyncOperation = 'create' | 'update' | 'delete' | 'batch';

/**
 * Sync status
 */
export type SyncStatus = 'pending' | 'syncing' | 'synced' | 'error' | 'conflict';

/**
 * Conflict resolution strategy
 */
export type ConflictStrategy =
  | 'server-wins'    // Server data takes precedence
  | 'client-wins'    // Client data takes precedence
  | 'last-write-wins' // Most recent timestamp wins
  | 'manual'         // Requires manual resolution
  | 'merge';         // Attempt automatic merge

/**
 * Sync adapter interface for REST/GraphQL/WebSocket
 */
export interface SyncAdapter {
  readonly type: 'rest' | 'graphql' | 'websocket';

  /** Initialize connection/authentication */
  connect(): Promise<void>;

  /** Close connection */
  disconnect(): Promise<void>;

  /** Check if connected */
  isConnected(): boolean;

  /** Pull latest data from server */
  pull(documentId: string): Promise<SyncResult>;

  /** Push local changes to server */
  push(documentId: string, changes: CanvasChange[]): Promise<SyncResult>;

  /** Subscribe to real-time updates (WebSocket/GraphQL subscriptions) */
  subscribe?(documentId: string, callback: (change: CanvasChange) => void): () => void;
}

/**
 * Canvas document change
 */
export interface CanvasChange {
  id: string;
  documentId: string;
  operation: SyncOperation;
  timestamp: number;
  userId: string;
  data: Partial<CanvasDocument>;
  version: number;
  clientId?: string;
}

/**
 * Sync result from pull/push operations
 */
export interface SyncResult {
  success: boolean;
  version: number;
  changes: CanvasChange[];
  conflicts?: ConflictInfo[];
  error?: {
    code: string;
    message: string;
    details?: unknown;
  };
}

/**
 * Conflict information
 */
export interface ConflictInfo {
  changeId: string;
  documentId: string;
  localChange: CanvasChange;
  serverChange: CanvasChange;
  strategy: ConflictStrategy;
  resolved: boolean;
  resolution?: CanvasChange;
}

/**
 * Offline queue entry
 */
export interface QueuedOperation {
  id: string;
  documentId: string;
  operation: SyncOperation;
  data: Partial<CanvasDocument>;
  timestamp: number;
  retryCount: number;
  maxRetries: number;
  status: SyncStatus;
  error?: string;
}

/**
 * Sync configuration
 */
export interface SyncConfig {
  /** API endpoint for REST/GraphQL */
  endpoint: string;

  /** Authentication token */
  authToken?: string;

  /** Sync interval in milliseconds (0 = manual only) */
  syncInterval: number;

  /** Enable offline queue */
  enableOfflineQueue: boolean;

  /** Maximum queue size */
  maxQueueSize: number;

  /** Conflict resolution strategy */
  conflictStrategy: ConflictStrategy;

  /** Enable real-time sync (WebSocket/GraphQL subscriptions) */
  enableRealtime: boolean;

  /** Debounce interval for batching changes (ms) */
  debounceInterval: number;

  /** Retry configuration */
  retry: {
    maxRetries: number;
    backoffMultiplier: number;
    initialDelay: number;
  };
}

/**
 * Sync event types
 */
export type SyncEventType =
  | 'sync-start'
  | 'sync-complete'
  | 'sync-error'
  | 'conflict-detected'
  | 'conflict-resolved'
  | 'offline-queued'
  | 'online-resumed'
  | 'connection-lost'
  | 'connection-restored';

/**
 * Sync event
 */
export interface SyncEvent {
  type: SyncEventType;
  documentId: string;
  timestamp: number;
  data?: unknown;
}

/**
 * Sync manager interface
 */
export interface SyncManager {
  /** Configure sync settings */
  configure(config: Partial<SyncConfig>): void;

  /** Start automatic syncing */
  start(): void;

  /** Stop automatic syncing */
  stop(): void;

  /** Manual sync trigger */
  sync(documentId: string): Promise<SyncResult>;

  /** Get sync status */
  getStatus(documentId: string): SyncStatus;

  /** Subscribe to sync events */
  on(event: SyncEventType, callback: (event: SyncEvent) => void): () => void;

  /** Get offline queue */
  getQueue(): QueuedOperation[];

  /** Clear offline queue */
  clearQueue(documentId?: string): void;

  /** Resolve conflict manually */
  resolveConflict(conflictId: string, resolution: CanvasChange): Promise<void>;
}

/**
 * REST API response
 */
export interface RestResponse<T = unknown> {
  data?: T;
  error?: {
    code: string;
    message: string;
    details?: unknown;
  };
  meta?: {
    version: number;
    timestamp: number;
  };
}

/**
 * GraphQL query/mutation response
 */
export interface GraphQLResponse<T = unknown> {
  data?: T;
  errors?: Array<{
    message: string;
    locations?: Array<{ line: number; column: number }>;
    path?: string[];
    extensions?: Record<string, unknown>;
  }>;
}

/**
 * WebSocket message
 */
export interface WebSocketMessage {
  type: 'change' | 'ping' | 'pong' | 'error';
  documentId?: string;
  data?: unknown;
  timestamp: number;
}
