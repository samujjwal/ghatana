/**
 * IndexedDB Storage Adapter
 * 
 * High-performance storage adapter using IndexedDB for unlimited canvas storage.
 * Supports compression, quota management, and efficient querying.
 * 
 * @doc.type service
 * @doc.purpose IndexedDB-based persistent storage
 * @doc.layer product
 * @doc.pattern Adapter Pattern
 */

import type { CanvasSnapshot } from '../CanvasPersistence';
import { CompressionService } from './CompressionService';

export interface QuotaInfo {
    used: number;
    available: number;
    percentUsed: number;
}

export interface StorageAdapter {
    init(): Promise<void>;
    saveSnapshot(snapshot: CanvasSnapshot): Promise<void>;
    loadSnapshot(id: string): Promise<CanvasSnapshot | null>;
    listSnapshots(projectId: string, canvasId: string): Promise<CanvasSnapshot[]>;
    deleteSnapshot(id: string): Promise<void>;
    clear(): Promise<void>;
    getQuota(): Promise<QuotaInfo>;
}

export class IndexedDBAdapter implements StorageAdapter {
    private db: IDBDatabase | null = null;
    private readonly DB_NAME = 'YappcCanvas';
    private readonly DB_VERSION = 1;
    private readonly SNAPSHOT_STORE = 'snapshots';
    private readonly METADATA_STORE = 'metadata';
    private compressionService: CompressionService;
    private enableCompression: boolean;

    constructor(options: { enableCompression?: boolean } = {}) {
        this.enableCompression = options.enableCompression ?? true;
        this.compressionService = new CompressionService();
    }
    saveSnapshot(snapshot: CanvasSnapshot): Promise<void> {
        throw new Error('Method not implemented.');
    }
    loadSnapshot(id: string): Promise<CanvasSnapshot | null> {
        throw new Error('Method not implemented.');
    }
    listSnapshots(projectId: string, canvasId: string): Promise<CanvasSnapshot[]> {
        throw new Error('Method not implemented.');
    }

    /**
     * Initialize database connection
     */
    public async init(): Promise<void> {
        if (this.db) return; // Already initialized

        return new Promise((resolve, reject) => {
            const request = indexedDB.open(this.DB_NAME, this.DB_VERSION);

            request.onerror = () => reject(new Error(`Failed to open IndexedDB: ${request.error}`));

            request.onsuccess = () => {
                this.db = request.result;
                resolve();
            };

            request.onupgradeneeded = (event) => {
                const db = (event.target as IDBOpenDBRequest).result;

                // Create snapshots store
                if (!db.objectStoreNames.contains(this.SNAPSHOT_STORE)) {
                    const snapshotStore = db.createObjectStore(this.SNAPSHOT_STORE, {
                        keyPath: 'id'
                    });

                    // Create indexes for efficient querying
                    snapshotStore.createIndex('projectId', 'projectId', { unique: false });
                    snapshotStore.createIndex('canvasId', 'canvasId', { unique: false });
                    snapshotStore.createIndex('timestamp', 'timestamp', { unique: false });
                    snapshotStore.createIndex('project_canvas', ['projectId', 'canvasId'], { unique: false });
                }

                // Create metadata store
                if (!db.objectStoreNames.contains(this.METADATA_STORE)) {
                    db.createObjectStore(this.METADATA_STORE, { keyPath: 'key' });
                }
            };
        });
    }

    /**
     * S// Compress snapshot data before saving
        const dataToSave = this.enableCompression
            ? await this.compressSnapshot(snapshot)
            : snapshot;
        
        return new Promise((resolve, reject) => {
            const transaction = this.db!.transaction([this.SNAPSHOT_STORE], 'readwrite');
            const store = transaction.objectStore(this.SNAPSHOT_STORE);
            const request = store.put(dataToSavetion([this.SNAPSHOT_STORE], 'readwrite');
            const store = transaction.objectStore(this.SNAPSHOT_STORE);
            
            // Compress snapshot data before saving
            const compressed = this.compressSnapshot(snapshot);
            const request = store.put(compressed);
            
            request.onsuccess = () => resolve();
            request.onerror = () => reject(new Error(`Failed to save snapshot: ${request.error}`));
        });
    }
    async (resolve, reject) => {
            const transaction = this.db!.transaction([this.SNAPSHOT_STORE], 'readonly');
            const store = transaction.objectStore(this.SNAPSHOT_STORE);
            const request = store.get(id);
            
            request.onsuccess = async () => {
                if (request.result) {
                    try {
                        const decompressed = await this.decompressSnapshot(request.result);
                        resolve(decompressed);
                    } catch (error) {
                        reject(new Error(`Failed to decompress snapshot: ${error}`));
                    }ransaction([this.SNAPSHOT_STORE], 'readonly');
            const store = transaction.objectStore(this.SNAPSHOT_STORE);
            const request = store.get(id);
            
            request.onsuccess = () => {
                if (request.result) {
                    const decompressed = this.decompressSnapshot(request.result);
                    resolve(decompressed);
                } else {
                    resolve(null);
                }
            };
            
            request.onerror = () => reject(new Error(`Failed to load snapshot: ${request.error}`));
        });
    }
    
    /**async (resolve, reject) => {
            const transaction = this.db!.transaction([this.SNAPSHOT_STORE], 'readonly');
            const store = transaction.objectStore(this.SNAPSHOT_STORE);
            const index = store.index('project_canvas');
            const request = index.getAll([projectId, canvasId]);
            
            request.onsuccess = async () => {
                try {
                    const decompressPromises = request.result.map(s => this.decompressSnapshot(s));
                    const snapshots = await Promise.all(decompressPromises);
                    // Sort by timestamp descending (newest first)
                    snapshots.sort((a, b) => b.timestamp - a.timestamp);
                    resolve(snapshots);
                } catch (error) {
                    reject(new Error(`Failed to decompress snapshots: ${error}`));
                }etAll([projectId, canvasId]);
            
            request.onsuccess = () => {
                const snapshots = request.result.map(s => this.decompressSnapshot(s));
                // Sort by timestamp descending (newest first)
                snapshots.sort((a, b) => b.timestamp - a.timestamp);
                resolve(snapshots);
            };
            
            request.onerror = () => reject(new Error(`Failed to list snapshots: ${request.error}`));
        });
    }
    
    /**
     * Delete a snapshot
     */
    public async deleteSnapshot(id: string): Promise<void> {
        await this.ensureInitialized();

        return new Promise((resolve, reject) => {
            const transaction = this.db!.transaction([this.SNAPSHOT_STORE], 'readwrite');
            const store = transaction.objectStore(this.SNAPSHOT_STORE);
            const request = store.delete(id);

            request.onsuccess = () => resolve();
            request.onerror = () => reject(new Error(`Failed to delete snapshot: ${request.error}`));
        });
    }

    /**
     * Clear all data (use with caution!)
     */
    public async clear(): Promise<void> {
        await this.ensureInitialized();

        return new Promise((resolve, reject) => {
            const transaction = this.db!.transaction([this.SNAPSHOT_STORE, this.METADATA_STORE], 'readwrite');

            const clearSnapshots = transaction.objectStore(this.SNAPSHOT_STORE).clear();
            const clearMetadata = transaction.objectStore(this.METADATA_STORE).clear();

            transaction.oncomplete = () => resolve();
            transaction.onerror = () => reject(new Error(`Failed to clear storage: ${transaction.error}`));
        });
    }

    /**
     * Get storage quota information
     */
    public async getQuota(): Promise<QuotaInfo> {
        if ('storage' in navigator && 'estimate' in navigator.storage) {
            const estimate = await navigator.storage.estimate();
            const used = estimate.usage || 0;
            const available = estimate.quota || Infinity;

            return {
                used,
                available,
                percentUsed: available === Infinity ? 0 : (used / available) * 100,
            };
        }

        return {
            used: 0,
            available: Infinity,
            percentUsed: 0,
        };
    }

    /**
     * Compress snapshot data using CompressionService
     */
    private async compressSnapshot(snapshot: CanvasSnapshot): Promise<unknown> {
        const { compressed, stats } = await this.compressionService.compressSnapshot(snapshot);

        // Log compression ratio for monitoring
        console.debug(`Compressed snapshot ${snapshot.id}: ${stats.compressionRatio.toFixed(2)}x reduction`);

        return compressed;
    }

    /**
     * Decompress snapshot data using CompressionService
     */
    private async decompressSnapshot(compressed: unknown): Promise<CanvasSnapshot> {
        // Check if snapshot is compressed
        if (!compressed.metadata?.compressed && !compressed._compressed) {
            return compressed; // Not compressed, return as-is
        }

        // Use compression service if available
        if (compressed.metadata?.compressed) {
            return await this.compressionService.decompressSnapshot(compressed);
        }

        // Legacy compressed format - return as-is (minus metadata)
        const { _compressed, ...snapshot } = compressed;
        return snapshot;
    }

    /**
     * Close database connection
     */
    public close(): void {
        if (this.db) {
            this.db.close();
            this.db = null;
        }
    }
}

/**
 * LocalStorage adapter for backward compatibility
 */
export class LocalStorageAdapter implements StorageAdapter {
    private readonly KEY_PREFIX = 'canvas-snapshot-';

    public async init(): Promise<void> {
        // No initialization needed for localStorage
    }

    public async saveSnapshot(snapshot: CanvasSnapshot): Promise<void> {
        const key = this.KEY_PREFIX + snapshot.id;
        localStorage.setItem(key, JSON.stringify(snapshot));
    }

    public async loadSnapshot(id: string): Promise<CanvasSnapshot | null> {
        const key = this.KEY_PREFIX + id;
        const data = localStorage.getItem(key);
        return data ? JSON.parse(data) : null;
    }

    public async listSnapshots(projectId: string, canvasId: string): Promise<CanvasSnapshot[]> {
        const snapshots: CanvasSnapshot[] = [];

        for (let i = 0; i < localStorage.length; i++) {
            const key = localStorage.key(i);
            if (key?.startsWith(this.KEY_PREFIX)) {
                const data = localStorage.getItem(key);
                if (data) {
                    const snapshot = JSON.parse(data) as CanvasSnapshot;
                    if (snapshot.projectId === projectId && snapshot.canvasId === canvasId) {
                        snapshots.push(snapshot);
                    }
                }
            }
        }

        // Sort by timestamp descending
        snapshots.sort((a, b) => b.timestamp - a.timestamp);
        return snapshots;
    }

    public async deleteSnapshot(id: string): Promise<void> {
        const key = this.KEY_PREFIX + id;
        localStorage.removeItem(key);
    }

    public async clear(): Promise<void> {
        const keys: string[] = [];
        for (let i = 0; i < localStorage.length; i++) {
            const key = localStorage.key(i);
            if (key?.startsWith(this.KEY_PREFIX)) {
                keys.push(key);
            }
        }
        keys.forEach(key => localStorage.removeItem(key));
    }

    public async getQuota(): Promise<QuotaInfo> {
        // localStorage has ~5-10MB limit, but we can't query it precisely
        return {
            used: 0,
            available: 5 * 1024 * 1024, // Assume 5MB
            percentUsed: 0,
        };
    }
}
