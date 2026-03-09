/**
 * Heartbeat scheduling and monitoring utilities
 */

import { createEnvelope } from './utils';
import type { BridgeEnvelope, BridgeDirection, HeartbeatPayload } from './types';

export interface HeartbeatConfig {
  intervalMs: number;
  timeoutMs: number;
  maxMissed: number;
}

export const DEFAULT_HEARTBEAT_CONFIG: HeartbeatConfig = {
  intervalMs: 30_000, // 30 seconds
  timeoutMs: 5_000, // 5 seconds
  maxMissed: 3, // Allow 3 missed heartbeats before considering connection dead
};

/**
 * Create a heartbeat envelope
 */
export function createHeartbeatEnvelope(
  sequence: number,
  direction: BridgeDirection,
  options?: {
    status?: string;
    meta?: Record<string, unknown>;
    correlationId?: string;
  }
): BridgeEnvelope<HeartbeatPayload> {
  const payload: HeartbeatPayload = {
    sequence,
    reportedAt: new Date().toISOString(),
    status: options?.status,
    meta: options?.meta,
  };

  return createEnvelope({
    payload,
    direction,
    kind: 'heartbeat',
    correlationId: options?.correlationId,
    metadata: {
      bridgeVersion: '1.0.0',
    },
  });
}

/**
 * Heartbeat scheduler for managing periodic heartbeat emission
 */
export class HeartbeatScheduler {
  private sequence = 0;
  private intervalId?: NodeJS.Timeout;
  private lastSent?: Date;
  private lastReceived?: Date;
  private missedCount = 0;

  constructor(
    private config: HeartbeatConfig,
    private onHeartbeat: (envelope: BridgeEnvelope<HeartbeatPayload>) => void | Promise<void>,
    private onTimeout?: () => void | Promise<void>
  ) {}

  /**
   * Start sending heartbeats
   */
  start(direction: BridgeDirection): void {
    if (this.intervalId) {
      return; // Already started
    }

    this.sequence = 0;
    this.missedCount = 0;

    this.intervalId = setInterval(() => {
      this.sendHeartbeat(direction);
    }, this.config.intervalMs);

    // Send initial heartbeat immediately
    this.sendHeartbeat(direction);
  }

  /**
   * Stop sending heartbeats
   */
  stop(): void {
    if (this.intervalId) {
      clearInterval(this.intervalId);
      this.intervalId = undefined;
    }
  }

  /**
   * Record receipt of a heartbeat
   */
  recordReceived(): void {
    this.lastReceived = new Date();
    this.missedCount = 0;
  }

  /**
   * Check if connection is alive based on heartbeat timing
   */
  isAlive(): boolean {
    if (!this.lastReceived) {
      return false;
    }

    const elapsed = Date.now() - this.lastReceived.getTime();
    return elapsed < this.config.intervalMs * this.config.maxMissed;
  }

  /**
   * Get current heartbeat statistics
   */
  getStats() {
    return {
      sequence: this.sequence,
      lastSent: this.lastSent,
      lastReceived: this.lastReceived,
      missedCount: this.missedCount,
      isAlive: this.isAlive(),
    };
  }

  private async sendHeartbeat(direction: BridgeDirection): Promise<void> {
    this.sequence++;
    this.lastSent = new Date();

    const envelope = createHeartbeatEnvelope(this.sequence, direction, {
      status: this.isAlive() ? 'alive' : 'degraded',
      meta: {
        missedCount: this.missedCount,
      },
    });

    try {
      await this.onHeartbeat(envelope);
    } catch (_error) {
      this.missedCount++;

      if (this.missedCount >= this.config.maxMissed && this.onTimeout) {
        await this.onTimeout();
      }
    }
  }
}
