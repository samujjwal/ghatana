/**
 * @fileoverview IndexedDB Sink - OOB (Out-of-Box) Implementation
 *
 * Persists events to IndexedDB for local storage, offline operation, and debugging.
 * This is the primary sink for standalone extension operation without external dependencies.
 *
 * **Features**:
 * - Persistent local storage using IndexedDB
 * - Batching for performance optimization
 * - Automatic cleanup of old events
 * - Storage quota management
 * - Query API for analysis and debugging
 * - Export/import capabilities
 * - Compression support
 * - No external dependencies
 *
 * **Usage**:
 * ```typescript
 * const sink = new IndexedDBSink({
 *   id: 'indexeddb-main',
 *   dbName: 'dcmaar-events',
 *   storeName: 'events',
 *   batchSize: 50,
 *   flushIntervalMs: 5000,
 *   maxEvents: 10000,
 *   retentionDays: 7
 * });
 *
 * await sink.initialize();
 *
 * await sink.process({
 *   id: 'evt-1',
 *   type: 'tab.created',
 *   timestamp: Date.now(),
 *   payload: { tabId: 123 }
 * });
 *
 * const events = await sink.query({ type: 'tab.created' });
 * ```
 *
 * @module connectors/sinks/IndexedDBSink
 */

import { openDB, type IDBPDatabase } from 'idb';
import type { Event } from '@ghatana/dcmaar-connectors';
import { getCrypto, type EncryptedData, type EncryptionConfig } from '../../utils/encryption';

/**
 * IndexedDB sink configuration
 */
export interface IndexedDBSinkConfig {
  /** Unique sink identifier */
  id: string;

  /** Database name */
  dbName: string;

  /** Object store name */
  storeName?: string;

  /** Batch size for writes */
  batchSize?: number;

  /** Flush interval in milliseconds */
  flushIntervalMs?: number;

  /** Maximum number of events to store */
  maxEvents?: number;

  /** Retention period in days */
  retentionDays?: number;

  /** Enable compression */
  compression?: boolean;

  /** Enable auto-cleanup */
  autoCleanup?: boolean;

  /** Cleanup interval in milliseconds */
  cleanupIntervalMs?: number;

  /** Enable encryption for sensitive data */
  encryption?: boolean;

  /** Encryption configuration */
  encryptionConfig?: EncryptionConfig;

  /** Fields to encrypt (if empty, encrypts entire payload) */
  encryptFields?: string[];
}

/**
 * Event filter for queries
 */
export interface EventFilter {
  /** Filter by event type */
  type?: string | string[];

  /** Filter by timestamp range */
  timestampRange?: {
    start: number;
    end: number;
  };

  /** Filter by metadata fields */
  metadata?: Record<string, any>;

  /** Limit number of results */
  limit?: number;

  /** Offset for pagination */
  offset?: number;

  /** Sort order */
  sort?: 'asc' | 'desc';
}

/**
 * Sink statistics
 */
export interface SinkStats {
  /** Total events stored */
  totalEvents: number;

  /** Storage size in bytes */
  storageSize: number;

  /** Events by type */
  eventsByType: Record<string, number>;

  /** Oldest event timestamp */
  oldestEvent: number | null;

  /** Newest event timestamp */
  newestEvent: number | null;

  /** Database version */
  dbVersion: number;
}

/**
 * Sink state
 */
export type SinkState = 'idle' | 'ready' | 'processing' | 'error';

/**
 * IndexedDB Sink
 *
 * Persists events to IndexedDB with batching, compression, and automatic cleanup.
 * This is an OOB (out-of-box) sink that requires no external dependencies.
 */
export class IndexedDBSink {
  readonly id: string;
  readonly type = 'indexeddb';
  state: SinkState = 'idle';

  private config: Required<Omit<IndexedDBSinkConfig, 'encryptionConfig'>> & { encryptionConfig?: EncryptionConfig };
  private db: IDBPDatabase | null = null;
  private batchBuffer: Event[] = [];
  private flushTimer: NodeJS.Timeout | null = null;
  private cleanupTimer: NodeJS.Timeout | null = null;
  private processingCount = 0;

  // ✅ Memory management constants
  private readonly MAX_BATCH_BUFFER_SIZE = 500; // Max events in batch buffer
  private readonly BUFFER_CLEANUP_PERCENTAGE = 0.3; // Remove oldest 30% on overflow

  constructor(config: IndexedDBSinkConfig) {
    this.id = config.id;
    this.config = {
      id: config.id,
      dbName: config.dbName,
      storeName: config.storeName || 'events',
      batchSize: config.batchSize || 50,
      flushIntervalMs: config.flushIntervalMs || 5000,
      maxEvents: config.maxEvents || 10000,
      retentionDays: config.retentionDays || 7,
      compression: config.compression || false,
      autoCleanup: config.autoCleanup !== false,
      cleanupIntervalMs: config.cleanupIntervalMs || 3600000, // 1 hour
      encryption: config.encryption || false,
      encryptionConfig: config.encryptionConfig || undefined,
      encryptFields: config.encryptFields || [],
    };
  }

  /**
   * Initialize the sink and open database
   */
  async initialize(): Promise<void> {
    if (this.state === 'ready') {
      return;
    }

    try {
      this.db = await this.openDatabase();
      this.startAutoFlush();

      if (this.config.autoCleanup) {
        this.startAutoCleanup();
      }

      this.state = 'ready';
    } catch (error) {
      this.state = 'error';
      throw error;
    }
  }

  /**
   * Open IndexedDB database
   */
  private async openDatabase(): Promise<IDBPDatabase> {
    return openDB(this.config.dbName, 1, {
      upgrade(db) {
        // Create events store
        if (!db.objectStoreNames.contains('events')) {
          const store = db.createObjectStore('events', {
            keyPath: 'id',
            autoIncrement: false,
          });

          // Create indexes for efficient queries
          store.createIndex('type', 'type', { unique: false });
          store.createIndex('timestamp', 'timestamp', { unique: false });
          store.createIndex('type_timestamp', ['type', 'timestamp'], { unique: false });
        }

        // Create metadata store for stats
        if (!db.objectStoreNames.contains('metadata')) {
          db.createObjectStore('metadata', { keyPath: 'key' });
        }
      },
    });
  }

  /**
   * Process a single event
   */
  async process(event: Event): Promise<void> {
    // Allow buffering during 'ready' or 'processing' states
    // Only prevent if sink is in 'idle' (not initialized) or 'error' state
    if (this.state === 'idle' || this.state === 'error') {
      throw new Error(`Sink not ready: ${this.state}`);
    }

    // ✅ Check buffer size before adding to prevent memory issues
    if (this.batchBuffer.length >= this.MAX_BATCH_BUFFER_SIZE) {
      console.warn(
        `[IndexedDBSink] Batch buffer at max capacity (${this.MAX_BATCH_BUFFER_SIZE}), force flushing to prevent memory overflow`
      );
      // Force flush before adding new event
      await this.flush();

      // If buffer still full after flush (flush failed), drop oldest events
      if (this.batchBuffer.length >= this.MAX_BATCH_BUFFER_SIZE) {
        const dropCount = Math.floor(this.MAX_BATCH_BUFFER_SIZE * this.BUFFER_CLEANUP_PERCENTAGE);
        console.error(
          `[IndexedDBSink] Flush failed, dropping ${dropCount} oldest events to prevent memory leak`
        );
        this.batchBuffer.splice(0, dropCount);
      }
    }

    this.batchBuffer.push(event);

    // Flush if buffer reaches normal batch size
    if (this.batchBuffer.length >= this.config.batchSize) {
      await this.flush();
    }
  }

  /**
   * Process a batch of events
   */
  async processBatch(events: Event[]): Promise<void> {
    // Allow processing during 'ready' or 'processing' states
    // Only prevent if sink is in 'idle' (not initialized) or 'error' state
    if (this.state === 'idle' || this.state === 'error') {
      throw new Error(`Sink not ready: ${this.state}`);
    }

    if (!this.db) {
      throw new Error('Database not initialized');
    }

    this.state = 'processing';
    this.processingCount++;

    try {
      const tx = this.db.transaction(this.config.storeName, 'readwrite');
      const store = tx.objectStore(this.config.storeName);

      // Add all events (with optional encryption)
      for (const event of events) {
        const processedEvent = await this.encryptEventIfNeeded(event);
        await store.add({
          ...processedEvent,
          _indexed: Date.now(),
        });
      }

      await tx.done;

      // Check if we need to cleanup old events
      const count = await this.count();
      if (count > this.config.maxEvents) {
        await this.cleanupOldEvents();
      }
    } finally {
      this.processingCount--;
      if (this.processingCount === 0) {
        this.state = 'ready';
      }
    }
  }

  /**
   * Encrypt event payload if encryption is enabled
   */
  private async encryptEventIfNeeded(event: Event): Promise<Event> {
    if (!this.config.encryption) {
      return event;
    }

    const crypto = getCrypto(this.config.encryptionConfig);

    // If specific fields are specified, encrypt only those fields
    if (this.config.encryptFields && this.config.encryptFields.length > 0) {
      const encryptedPayload = { ...event.payload };

      for (const field of this.config.encryptFields) {
        if (field in encryptedPayload && encryptedPayload[field] !== undefined) {
          const fieldValue = JSON.stringify(encryptedPayload[field]);
          encryptedPayload[field] = await crypto.encrypt(fieldValue);
        }
      }

      return {
        ...event,
        payload: encryptedPayload,
        metadata: {
          ...event.metadata,
          _encrypted: true,
          _encryptedFields: this.config.encryptFields,
        },
      };
    }

    // Otherwise, encrypt entire payload
    const encryptedPayload = await crypto.encryptJSON(event.payload);

    return {
      ...event,
      payload: encryptedPayload as any,
      metadata: {
        ...event.metadata,
        _encrypted: true,
        _fullPayloadEncrypted: true,
      },
    };
  }

  /**
   * Decrypt event payload if needed
   */
  private async decryptEventIfNeeded(event: Event): Promise<Event> {
    if (!event.metadata?._encrypted) {
      return event;
    }

    const crypto = getCrypto(this.config.encryptionConfig);

    // Check if full payload was encrypted
    if (event.metadata._fullPayloadEncrypted) {
      const decryptedPayload = await crypto.decryptJSON(event.payload as any as EncryptedData);
      return {
        ...event,
        payload: decryptedPayload,
      };
    }

    // Otherwise, decrypt specific fields
    if (event.metadata._encryptedFields && Array.isArray(event.metadata._encryptedFields)) {
      const decryptedPayload = { ...event.payload };

      for (const field of event.metadata._encryptedFields) {
        if (field in decryptedPayload && typeof decryptedPayload[field] === 'object') {
          const decrypted = await crypto.decrypt(decryptedPayload[field] as any);
          decryptedPayload[field] = JSON.parse(decrypted);
        }
      }

      return {
        ...event,
        payload: decryptedPayload,
      };
    }

    return event;
  }

  /**
   * Flush buffered events
   */
  async flush(): Promise<void> {
    if (this.batchBuffer.length === 0) {
      return;
    }

    const batch = [...this.batchBuffer];
    this.batchBuffer = [];

    await this.processBatch(batch);
  }

  /**
   * Query events with filters
   */
  async query(filter: EventFilter = {}): Promise<Event[]> {
    if (!this.db) {
      throw new Error('Database not initialized');
    }

    const tx = this.db.transaction(this.config.storeName, 'readonly');
    const store = tx.objectStore(this.config.storeName);

    let results: Event[] = [];

    // Use appropriate index based on filter
    if (filter.type && filter.timestampRange) {
      // Use compound index
      const index = store.index('type_timestamp');
      const range = IDBKeyRange.bound(
        [filter.type, filter.timestampRange.start],
        [filter.type, filter.timestampRange.end]
      );
      results = await index.getAll(range);
    } else if (filter.type) {
      // Use type index
      const index = store.index('type');
      const types = Array.isArray(filter.type) ? filter.type : [filter.type];
      
      for (const type of types) {
        const events = await index.getAll(type);
        results.push(...events);
      }
    } else if (filter.timestampRange) {
      // Use timestamp index
      const index = store.index('timestamp');
      const range = IDBKeyRange.bound(
        filter.timestampRange.start,
        filter.timestampRange.end
      );
      results = await index.getAll(range);
    } else {
      // Get all events
      results = await store.getAll();
    }

    // Apply metadata filter if specified
    if (filter.metadata) {
      results = results.filter((event) => {
        if (!event.metadata) return false;
        
        return Object.entries(filter.metadata!).every(([key, value]) => {
          return event.metadata![key] === value;
        });
      });
    }

    // Decrypt results if needed
    if (this.config.encryption) {
      results = await Promise.all(results.map((event) => this.decryptEventIfNeeded(event)));
    }

    // Sort results
    if (filter.sort === 'desc') {
      results.sort((a, b) => b.timestamp - a.timestamp);
    } else {
      results.sort((a, b) => a.timestamp - b.timestamp);
    }

    // Apply pagination
    const offset = filter.offset || 0;
    const limit = filter.limit || results.length;
    results = results.slice(offset, offset + limit);

    return results;
  }

  /**
   * Count total events
   */
  async count(filter?: EventFilter): Promise<number> {
    if (!this.db) {
      throw new Error('Database not initialized');
    }

    if (!filter) {
      const tx = this.db.transaction(this.config.storeName, 'readonly');
      const store = tx.objectStore(this.config.storeName);
      return store.count();
    }

    // If filter specified, query and count
    const events = await this.query(filter);
    return events.length;
  }

  /**
   * Delete events matching filter
   */
  async delete(filter: EventFilter): Promise<number> {
    if (!this.db) {
      throw new Error('Database not initialized');
    }

    const events = await this.query(filter);
    const tx = this.db.transaction(this.config.storeName, 'readwrite');
    const store = tx.objectStore(this.config.storeName);

    for (const event of events) {
      await store.delete(event.id);
    }

    await tx.done;

    return events.length;
  }

  /**
   * Clear all events
   */
  async clear(): Promise<void> {
    if (!this.db) {
      throw new Error('Database not initialized');
    }

    const tx = this.db.transaction(this.config.storeName, 'readwrite');
    const store = tx.objectStore(this.config.storeName);
    await store.clear();
    await tx.done;
  }

  /**
   * Get sink statistics
   */
  async getStats(): Promise<SinkStats> {
    if (!this.db) {
      throw new Error('Database not initialized');
    }

    const tx = this.db.transaction(this.config.storeName, 'readonly');
    const store = tx.objectStore(this.config.storeName);
    const typeIndex = store.index('type');
    const timestampIndex = store.index('timestamp');

    // Get total count
    const totalEvents = await store.count();

    // Get events by type
    const eventsByType: Record<string, number> = {};
    let cursor = await typeIndex.openCursor();
    
    while (cursor) {
      const type = cursor.key as string;
      eventsByType[type] = (eventsByType[type] || 0) + 1;
      cursor = await cursor.continue();
    }

    // Get oldest and newest timestamps
    const timestamps = await timestampIndex.getAllKeys();
    const oldestEvent = timestamps.length > 0 ? Math.min(...(timestamps as number[])) : null;
    const newestEvent = timestamps.length > 0 ? Math.max(...(timestamps as number[])) : null;

    // Estimate storage size (rough approximation)
    const allEvents = await store.getAll();
    const storageSize = JSON.stringify(allEvents).length;

    return {
      totalEvents,
      storageSize,
      eventsByType,
      oldestEvent,
      newestEvent,
      dbVersion: this.db.version,
    };
  }

  /**
   * Export events to JSON
   */
  async export(filter?: EventFilter): Promise<string> {
    const events = await this.query(filter || {});
    return JSON.stringify(events, null, 2);
  }

  /**
   * Import events from JSON
   */
  async import(json: string): Promise<number> {
    const events = JSON.parse(json) as Event[];
    await this.processBatch(events);
    return events.length;
  }

  /**
   * Cleanup old events based on retention policy
   */
  private async cleanupOldEvents(): Promise<void> {
    if (!this.db) return;

    const cutoffTime = Date.now() - this.config.retentionDays * 24 * 60 * 60 * 1000;

    const deleted = await this.delete({
      timestampRange: {
        start: 0,
        end: cutoffTime,
      },
    });

    console.log(`[IndexedDBSink] Cleaned up ${deleted} old events`);
  }

  /**
   * Start auto-flush timer
   */
  private startAutoFlush(): void {
    this.flushTimer = setInterval(() => {
      void this.flush();
    }, this.config.flushIntervalMs);
  }

  /**
   * Start auto-cleanup timer
   */
  private startAutoCleanup(): void {
    this.cleanupTimer = setInterval(() => {
      void this.cleanupOldEvents();
    }, this.config.cleanupIntervalMs);
  }

  /**
   * Stop auto-flush timer
   */
  private stopAutoFlush(): void {
    if (this.flushTimer) {
      clearInterval(this.flushTimer);
      this.flushTimer = null;
    }
  }

  /**
   * Stop auto-cleanup timer
   */
  private stopAutoCleanup(): void {
    if (this.cleanupTimer) {
      clearInterval(this.cleanupTimer);
      this.cleanupTimer = null;
    }
  }

  /**
   * Destroy the sink and cleanup resources
   */
  async destroy(): Promise<void> {
    // Flush remaining events
    await this.flush();

    // Stop timers
    this.stopAutoFlush();
    this.stopAutoCleanup();

    // Close database
    if (this.db) {
      this.db.close();
      this.db = null;
    }

    this.state = 'idle';
  }
}
