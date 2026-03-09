/**
 * @fileoverview IndexedDB Local Sink Preset
 *
 * Standard configuration for persisting events to IndexedDB.
 */

import type { SinkPreset } from '../types';

/**
 * Standard IndexedDB sink preset
 *
 * Balanced configuration for local event storage.
 * Suitable for most use cases with reasonable retention and performance.
 */
export const indexedDBStandardPreset: SinkPreset = {
  id: 'indexeddb-standard',
  name: 'Standard IndexedDB Storage',
  description: 'Balanced local storage with 7-day retention',
  config: {
    type: 'indexeddb',
    dbName: 'dcmaar-events',
    storeName: 'events',
    batchSize: 50,
    flushIntervalMs: 5000,
    maxEvents: 10000,
    retentionDays: 7,
    compression: false,
    autoCleanup: true,
    cleanupIntervalMs: 3600000, // 1 hour
  },
  tags: ['indexeddb', 'local', 'storage', 'standard'],
  compatibility: {
    agent: false,
    desktop: false,
    extension: true,
  },
  version: '1.0.0',
};

/**
 * High-performance IndexedDB sink preset
 *
 * Optimized for high-throughput scenarios.
 * Larger batches, shorter retention, aggressive cleanup.
 */
export const indexedDBHighPerformancePreset: SinkPreset = {
  id: 'indexeddb-high-performance',
  name: 'High-Performance IndexedDB Storage',
  description: 'Optimized for high-throughput with 3-day retention',
  config: {
    type: 'indexeddb',
    dbName: 'dcmaar-events-perf',
    storeName: 'events',
    batchSize: 100,
    flushIntervalMs: 2000,
    maxEvents: 5000,
    retentionDays: 3,
    compression: true,
    autoCleanup: true,
    cleanupIntervalMs: 1800000, // 30 minutes
  },
  tags: ['indexeddb', 'local', 'storage', 'performance'],
  compatibility: {
    agent: false,
    desktop: false,
    extension: true,
  },
  version: '1.0.0',
};

/**
 * Long-term IndexedDB sink preset
 *
 * Extended retention for historical analysis.
 * Suitable for debugging and long-term monitoring.
 */
export const indexedDBLongTermPreset: SinkPreset = {
  id: 'indexeddb-long-term',
  name: 'Long-Term IndexedDB Storage',
  description: 'Extended 30-day retention for historical analysis',
  config: {
    type: 'indexeddb',
    dbName: 'dcmaar-events-archive',
    storeName: 'events',
    batchSize: 50,
    flushIntervalMs: 10000,
    maxEvents: 50000,
    retentionDays: 30,
    compression: true,
    autoCleanup: true,
    cleanupIntervalMs: 86400000, // 24 hours
  },
  tags: ['indexeddb', 'local', 'storage', 'archive'],
  compatibility: {
    agent: false,
    desktop: false,
    extension: true,
  },
  version: '1.0.0',
};

/**
 * Debug IndexedDB sink preset
 *
 * Minimal batching for immediate visibility.
 * Suitable for development and debugging.
 */
export const indexedDBDebugPreset: SinkPreset = {
  id: 'indexeddb-debug',
  name: 'Debug IndexedDB Storage',
  description: 'Minimal batching for immediate visibility',
  config: {
    type: 'indexeddb',
    dbName: 'dcmaar-events-debug',
    storeName: 'events',
    batchSize: 1,
    flushIntervalMs: 100,
    maxEvents: 1000,
    retentionDays: 1,
    compression: false,
    autoCleanup: true,
    cleanupIntervalMs: 300000, // 5 minutes
  },
  tags: ['indexeddb', 'local', 'storage', 'debug'],
  compatibility: {
    agent: false,
    desktop: false,
    extension: true,
  },
  version: '1.0.0',
};
