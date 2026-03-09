/**
 * Telemetry batching utilities with adaptive sizing
 */

import { randomUUID } from 'node:crypto';
import { createEnvelope } from './utils';
import type { BridgeEnvelope, BridgeDirection, TelemetryPayload } from './types';

export interface BatchConfig {
  maxBatchSize: number;
  minBatchSize: number;
  maxBatchSizeBytes: number;
  flushIntervalMs: number;
  adaptiveThresholdMs: number; // Latency threshold for adaptive sizing
}

export const DEFAULT_BATCH_CONFIG: BatchConfig = {
  maxBatchSize: 100,
  minBatchSize: 1,
  maxBatchSizeBytes: 1024 * 1024, // 1MB
  flushIntervalMs: 5000, // 5 seconds
  adaptiveThresholdMs: 100, // Adjust batch size if latency > 100ms
};

/**
 * Create a telemetry envelope with batched data
 */
export function createTelemetryEnvelope(
  data: Record<string, unknown>,
  direction: BridgeDirection,
  options?: {
    alerts?: Record<string, unknown>[];
    meta?: Record<string, unknown>;
    estimatedSizeBytes?: number;
  }
): BridgeEnvelope<TelemetryPayload> {
  const payload: TelemetryPayload = {
    batchId: randomUUID(),
    collectedAt: new Date().toISOString(),
    data,
    alerts: options?.alerts,
    meta: options?.meta,
    estimatedSizeBytes: options?.estimatedSizeBytes,
  };

  return createEnvelope({
    payload,
    direction,
    kind: 'telemetry',
    metadata: {
      bridgeVersion: '1.0.0',
    },
  });
}

/**
 * Adaptive telemetry batcher with backpressure handling
 */
export class TelemetryBatcher {
  private queue: Array<Record<string, unknown>> = [];
  private currentBatchSize: number;
  private flushTimer?: NodeJS.Timeout;
  private lastFlushLatency = 0;

  constructor(
    private config: BatchConfig,
    private onFlush: (envelope: BridgeEnvelope<TelemetryPayload>) => Promise<void>
  ) {
    this.currentBatchSize = config.maxBatchSize;
  }

  /**
   * Add telemetry data to the batch
   */
  async add(data: Record<string, unknown>): Promise<void> {
    this.queue.push(data);

    // Flush if batch size reached
    if (this.queue.length >= this.currentBatchSize) {
      await this.flush();
    } else {
      // Schedule flush if not already scheduled
      this.scheduleFlush();
    }
  }

  /**
   * Flush current batch immediately
   */
  async flush(): Promise<void> {
    if (this.queue.length === 0) {
      return;
    }

    // Clear scheduled flush
    if (this.flushTimer) {
      clearTimeout(this.flushTimer);
      this.flushTimer = undefined;
    }

    const batch = this.queue.splice(0, this.currentBatchSize);
    const startTime = Date.now();

    try {
      const envelope = createTelemetryEnvelope(
        { items: batch },
        'extension→desktop',
        {
          meta: {
            batchSize: batch.length,
            adaptiveBatchSize: this.currentBatchSize,
          },
          estimatedSizeBytes: this.estimateBatchSize(batch),
        }
      );

      await this.onFlush(envelope);

      // Record latency and adjust batch size
      this.lastFlushLatency = Date.now() - startTime;
      this.adjustBatchSize(this.lastFlushLatency);
    } catch (error) {
      // Re-queue failed items at the front
      this.queue.unshift(...batch);
      throw error;
    }
  }

  /**
   * Get current batch statistics
   */
  getStats() {
    return {
      queueLength: this.queue.length,
      currentBatchSize: this.currentBatchSize,
      lastFlushLatency: this.lastFlushLatency,
    };
  }

  /**
   * Stop the batcher and flush remaining items
   */
  async stop(): Promise<void> {
    if (this.flushTimer) {
      clearTimeout(this.flushTimer);
      this.flushTimer = undefined;
    }

    if (this.queue.length > 0) {
      await this.flush();
    }
  }

  /**
   * Adjust batch size based on observed latency (adaptive backpressure)
   */
  private adjustBatchSize(currentLatency: number): void {
    if (currentLatency > this.config.adaptiveThresholdMs) {
      // Reduce batch size by 20% if latency is high
      this.currentBatchSize = Math.max(
        this.config.minBatchSize,
        Math.floor(this.currentBatchSize * 0.8)
      );
    } else if (currentLatency < this.config.adaptiveThresholdMs / 2) {
      // Increase batch size by 20% if latency is low
      this.currentBatchSize = Math.min(
        this.config.maxBatchSize,
        Math.ceil(this.currentBatchSize * 1.2)
      );
    }
  }

  /**
   * Estimate batch size in bytes
   */
  private estimateBatchSize(batch: Array<Record<string, unknown>>): number {
    try {
      return new Blob([JSON.stringify(batch)]).size;
    } catch {
      // Fallback rough estimate
      return batch.length * 1024; // Assume 1KB per item
    }
  }

  /**
   * Schedule a flush after the configured interval
   */
  private scheduleFlush(): void {
    if (this.flushTimer) {
      return; // Already scheduled
    }

    this.flushTimer = setTimeout(() => {
      this.flush().catch((error) => {
        console.error('Failed to flush telemetry batch:', error);
      });
    }, this.config.flushIntervalMs);
  }
}
