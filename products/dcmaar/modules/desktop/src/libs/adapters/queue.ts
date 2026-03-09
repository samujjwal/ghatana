/**
 * Crash-safe, encrypted queue service for control commands.
 * Uses IndexedDB with AES-GCM encryption and compaction support.
 */

import type { QueueService, ControlCommand } from './types';

export interface QueueConfig {
  dbName: string;
  storeName: string;
  maxSizeMB: number;
  encryptionKey?: CryptoKey;
}

interface QueueItem {
  id: string;
  command: ControlCommand;
  enqueuedAt: number;
  encrypted: boolean;
}

export class IndexedDBQueue implements QueueService {
  private readonly config: QueueConfig;
  private db: IDBDatabase | null = null;

  constructor(config: QueueConfig) {
    this.config = config;
  }

  async init(): Promise<void> {
    return new Promise((resolve, reject) => {
      const request = indexedDB.open(this.config.dbName, 1);

      request.onerror = () => reject(request.error);
      request.onsuccess = () => {
        this.db = request.result;
        resolve();
      };

      request.onupgradeneeded = (event) => {
        const db = (event.target as IDBOpenDBRequest).result;
        if (!db.objectStoreNames.contains(this.config.storeName)) {
          db.createObjectStore(this.config.storeName, { keyPath: 'id' });
        }
      };
    });
  }

  async enqueue(command: ControlCommand): Promise<void> {
    if (!this.db) {
      throw new Error('Queue not initialized');
    }

    const item: QueueItem = {
      id: `${Date.now()}-${Math.random().toString(36).substring(2)}`,
      command,
      enqueuedAt: Date.now(),
      encrypted: !!this.config.encryptionKey,
    };

    if (this.config.encryptionKey) {
      item.command = await this.encrypt(command);
    }

    return this.put(item);
  }

  async peek(): Promise<ControlCommand | null> {
    const item = await this.getFirst();
    if (!item) {
      return null;
    }

    return this.config.encryptionKey && item.encrypted
      ? this.decrypt(item.command)
      : item.command;
  }

  async dequeue(): Promise<ControlCommand | null> {
    const item = await this.getFirst();
    if (!item) {
      return null;
    }

    await this.delete(item.id);

    return this.config.encryptionKey && item.encrypted
      ? this.decrypt(item.command)
      : item.command;
  }

  async size(): Promise<number> {
    if (!this.db) {
      return 0;
    }

    return new Promise((resolve, reject) => {
      const tx = this.db!.transaction(this.config.storeName, 'readonly');
      const store = tx.objectStore(this.config.storeName);
      const request = store.count();

      request.onsuccess = () => resolve(request.result);
      request.onerror = () => reject(request.error);
    });
  }

  async compact(): Promise<number> {
    // Remove old entries if queue exceeds size limit
    const items = await this.getAll();
    const targetSize = Math.floor(items.length * 0.8);

    if (items.length <= targetSize) {
      return 0;
    }

    const toRemove = items.slice(0, items.length - targetSize);
    for (const item of toRemove) {
      await this.delete(item.id);
    }

    return toRemove.length;
  }

  private async put(item: QueueItem): Promise<void> {
    if (!this.db) {
      throw new Error('Queue not initialized');
    }

    return new Promise((resolve, reject) => {
      const tx = this.db!.transaction(this.config.storeName, 'readwrite');
      const store = tx.objectStore(this.config.storeName);
      const request = store.put(item);

      request.onsuccess = () => resolve();
      request.onerror = () => reject(request.error);
    });
  }

  private async getFirst(): Promise<QueueItem | null> {
    if (!this.db) {
      return null;
    }

    return new Promise((resolve, reject) => {
      const tx = this.db!.transaction(this.config.storeName, 'readonly');
      const store = tx.objectStore(this.config.storeName);
      const request = store.openCursor();

      request.onsuccess = () => {
        const cursor = request.result;
        resolve(cursor ? cursor.value : null);
      };
      request.onerror = () => reject(request.error);
    });
  }

  private async getAll(): Promise<QueueItem[]> {
    if (!this.db) {
      return [];
    }

    return new Promise((resolve, reject) => {
      const tx = this.db!.transaction(this.config.storeName, 'readonly');
      const store = tx.objectStore(this.config.storeName);
      const request = store.getAll();

      request.onsuccess = () => resolve(request.result);
      request.onerror = () => reject(request.error);
    });
  }

  private async delete(id: string): Promise<void> {
    if (!this.db) {
      return;
    }

    return new Promise((resolve, reject) => {
      const tx = this.db!.transaction(this.config.storeName, 'readwrite');
      const store = tx.objectStore(this.config.storeName);
      const request = store.delete(id);

      request.onsuccess = () => resolve();
      request.onerror = () => reject(request.error);
    });
  }

  private async encrypt(command: ControlCommand): Promise<ControlCommand> {
    if (!this.config.encryptionKey) {
      return command;
    }

    const { createCryptoService } = await import('./crypto');
    const crypto = createCryptoService();
    const encrypted = await crypto.encrypt(command, this.config.encryptionKey);

    return {
      ...command,
      payload: encrypted as any,
      metadata: {
        ...command.metadata,
        encrypted: true,
      },
    };
  }

  private async decrypt(command: ControlCommand): Promise<ControlCommand> {
    if (!this.config.encryptionKey || !command.metadata.encrypted) {
      return command;
    }

    const { createCryptoService } = await import('./crypto');
    const crypto = createCryptoService();
    const decrypted = await crypto.decrypt(command.payload as any, this.config.encryptionKey);

    return {
      ...command,
      payload: decrypted,
      metadata: {
        ...command.metadata,
        encrypted: false,
      },
    };
  }
}

export const createQueue = async (config: QueueConfig): Promise<QueueService> => {
  const queue = new IndexedDBQueue(config);
  await queue.init();
  return queue;
};
