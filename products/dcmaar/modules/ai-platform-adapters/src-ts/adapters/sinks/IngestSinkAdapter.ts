/**
 * Ingest Sink Adapter
 *
 * Routes events from TypeScript connectors to Rust IngestService.
 * This is the primary bridge between the TypeScript connector layer
 * and the native Rust processing pipeline.
 */

import { BaseConnector, type IConnector } from '@ghatana/dcmaar-connectors';
import type { SinkAdapter } from '../types';
import { Logger } from '../../utils/logger';
import {
  createNativeBridge,
  getLastBridgeError,
  parseBridgeStats,
  type NativeBridge,
} from '../../native/bridge';

/**
 * Sink adapter that routes events to Rust IngestService
 *
 * This adapter creates connectors that send events through the
 * Rust bridge to the IngestService for processing.
 */
export class IngestSinkAdapter implements SinkAdapter {
  readonly type = 'ingest';
  private logger: Logger;

  constructor() {
    this.logger = new Logger('IngestSinkAdapter');
  }

  async create(config: unknown): Promise<IConnector> {
    this.logger.debug('Creating IngestSink connector', { id: config.id });
    return new IngestSinkConnector(config);
  }
}

/**
 * Ingest Sink Connector
 *
 * Sends events to Rust IngestService via the bridge.
 * Supports batching and configuration for optimal performance.
 */
class IngestSinkConnector extends BaseConnector<unknown> {
  private logger: Logger;
  private bridge: NativeBridge | null = null;
  private bridgeEnabled = false;
  private batchSize: number;
  private batchInterval: number;
  private batch: unknown[] = [];
  private batchTimer: NodeJS.Timeout | null = null;

  constructor(config: unknown) {
    super(config);
    this.logger = new Logger('IngestSinkConnector');

    // Get configuration from metadata
    const metadata = config.metadata || {};
    this.batchSize = metadata.batchSize || 100;
    this.batchInterval = metadata.batchInterval || 5000; // 5 seconds

    try {
      this.bridge = createNativeBridge();
      if (this.bridge) {
        const version = this.bridge.getVersion();
        this.bridgeEnabled = true;
        this.logger.info('Native ingest bridge initialised', { version });
      } else if (getLastBridgeError()) {
        this.logger.warn('Native ingest bridge unavailable', {
          error: getLastBridgeError(),
        });
      }
    } catch (error) {
      this.bridge = null;
      this.bridgeEnabled = false;
      this.logger.error('Failed to initialise native ingest bridge', { error });
    }
  }

  protected async _connect(): Promise<void> {
    this.logger.info('IngestSink connecting', {
      id: this._config.id,
      batchSize: this.batchSize,
      batchInterval: this.batchInterval,
    });

    if (this.bridge && this.bridgeEnabled) {
      try {
        const healthy = await this.bridge.healthCheck();
        if (!healthy) {
          this.bridgeEnabled = false;
          this.logger.warn('Native ingest bridge health check failed; reverting to log-only mode');
        }
      } catch (error) {
        this.bridgeEnabled = false;
        this.logger.error('Native ingest bridge health check error; reverting to log-only mode', {
          error,
        });
      }
    }

    this.logger.info('IngestSink connected', { id: this._config.id });
  }

  protected async _disconnect(): Promise<void> {
    this.logger.info('IngestSink disconnecting', { id: this._config.id });

    // Flush any pending batch
    if (this.batch.length > 0) {
      await this.flush();
    }

    // Clear batch timer
    if (this.batchTimer) {
      clearTimeout(this.batchTimer);
      this.batchTimer = null;
    }

    this.bridge = null;
    this.bridgeEnabled = false;
    this.logger.info('IngestSink disconnected', { id: this._config.id });
  }

  async send(data: unknown, _options?: Record<string, any>): Promise<void> {
    this.logger.debug('IngestSink received event', {
      id: this._config.id,
      eventType: data.type,
      batchSize: this.batch.length,
    });

    // Add to batch
    this.batch.push(data);

    // Start batch timer if not already running
    if (!this.batchTimer && this.batchInterval > 0) {
      this.batchTimer = setTimeout(() => {
        this.flush().catch((error) => {
          this.logger.error('Failed to flush batch', { error });
        });
      }, this.batchInterval);
    }

    // Flush if batch is full
    if (this.batch.length >= this.batchSize) {
      await this.flush();
    }
  }

  /**
   * Flush the current batch to IngestService
   */
  private async flush(): Promise<void> {
    if (this.batch.length === 0) {
      return;
    }

    const batchToSend = [...this.batch];
    this.batch = [];

    // Clear batch timer
    if (this.batchTimer) {
      clearTimeout(this.batchTimer);
      this.batchTimer = null;
    }

    this.logger.debug('Flushing batch to IngestService', {
      id: this._config.id,
      count: batchToSend.length,
    });

    try {
      if (this.bridge && this.bridgeEnabled) {
        const count = await this.bridge.submitBatch(JSON.stringify(batchToSend));
        this.logger.info('Batch submitted to IngestService', {
          id: this._config.id,
          count,
          mode: 'native',
        });
      } else {
        this.logger.info('Batch ready for IngestService (native bridge disabled)', {
          id: this._config.id,
          count: batchToSend.length,
          sample: batchToSend[0],
        });
      }
    } catch (error) {
      this.logger.error('Failed to send batch to IngestService', {
        id: this._config.id,
        count: batchToSend.length,
        error,
      });
      if (this.bridgeEnabled) {
        this.bridgeEnabled = false;
        // Requeue events so they can be retried when bridge recovers
        this.batch.unshift(...batchToSend);
      }
      throw error;
    }
  }

  async getStats(): Promise<unknown> {
    if (this.bridge && this.bridgeEnabled) {
      const stats = await parseBridgeStats(this.bridge);
      return {
        bridgeEnabled: true,
        native: stats,
        pending: this.batch.length,
      };
    }

    return {
      bridgeEnabled: false,
      pending: this.batch.length,
    };
  }
}
