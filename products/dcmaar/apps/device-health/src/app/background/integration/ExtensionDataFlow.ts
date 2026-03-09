/**
 * @fileoverview Extension Data Flow Integration
 *
 * Integrates OOB source, pipeline, and sink for complete data flow.
 * This is the main integration point for the extension's data processing.
 *
 * @module app/background/integration/ExtensionDataFlow
 */

import type { Event } from '@ghatana/dcmaar-connectors';
import { BrowserEventSource, type BrowserEventSourceConfig } from '../../../connectors/sources/BrowserEventSource';
import { IndexedDBSink, type IndexedDBSinkConfig } from '../../../connectors/sinks/IndexedDBSink';
import { EventPipeline, type PipelineConfig } from '../pipeline/EventPipeline';
import { ComprehensiveMonitor } from '../monitoring/ComprehensiveMonitor';

/**
 * Data flow configuration
 */
export interface DataFlowConfig {
  /** Source configuration */
  source: BrowserEventSourceConfig;

  /** Sink configuration */
  sink: IndexedDBSinkConfig;

  /** Pipeline configuration (optional) */
  pipeline?: PipelineConfig;

  /** Enable monitoring */
  monitoring?: boolean;
}

/**
 * Data flow statistics
 */
export interface DataFlowStats {
  source: ReturnType<BrowserEventSource['getStats']>;
  sink: Awaited<ReturnType<IndexedDBSink['getStats']>>;
  pipeline?: ReturnType<EventPipeline['getStats']>;
  monitor?: {
    health: Awaited<ReturnType<ComprehensiveMonitor['checkHealth']>>;
    performance: Awaited<ReturnType<ComprehensiveMonitor['analyzePerformance']>>;
  };
}

/**
 * Extension Data Flow
 *
 * Manages the complete data flow from source through pipeline to sink.
 * Includes monitoring and error handling.
 */
export class ExtensionDataFlow {
  private source: BrowserEventSource;
  private sink: IndexedDBSink;
  private pipeline: EventPipeline | null = null;
  private monitor: ComprehensiveMonitor | null = null;
  private running = false;

  constructor(private config: DataFlowConfig) {
    this.source = new BrowserEventSource(config.source);
    this.sink = new IndexedDBSink(config.sink);

    if (config.pipeline) {
      this.pipeline = new EventPipeline(config.pipeline);
    }

    if (config.monitoring) {
      this.monitor = new ComprehensiveMonitor({
        healthCheckInterval: 30000,
        performanceInterval: 60000,
        anomalyDetection: true,
      });
    }
  }

  /**
   * Start data flow
   */
  async start(): Promise<void> {
    if (this.running) {
      return;
    }

    try {
      // Initialize sink
      await this.sink.initialize();

      // Initialize pipeline if configured
      if (this.pipeline) {
        await this.pipeline.initialize();
      }

      // Register source data handler
      this.source.onData((event) => void this.handleEvent(event));
      this.source.onError((error) => this.handleError(error));

      // Start source
      await this.source.start();

      // Start monitoring
      if (this.monitor) {
        this.monitor.start();
      }

      this.running = true;
    } catch (error) {
      throw new Error(`Failed to start data flow: ${error instanceof Error ? error.message : String(error)}`);
    }
  }

  /**
   * Stop data flow
   */
  async stop(): Promise<void> {
    if (!this.running) {
      return;
    }

    try {
      // Stop source
      await this.source.stop();

      // Stop monitoring
      if (this.monitor) {
        this.monitor.stop();
      }

      // Flush sink
      await this.sink.flush();

      // Destroy pipeline
      if (this.pipeline) {
        await this.pipeline.destroy();
      }

      // Destroy sink
      await this.sink.destroy();

      this.running = false;
    } catch (error) {
      throw new Error(`Failed to stop data flow: ${error instanceof Error ? error.message : String(error)}`);
    }
  }

  /**
   * Handle event from source
   */
  private async handleEvent(event: Event): Promise<void> {
    const startTime = Date.now();
    let processedEvent: Event | Event[] | null = event;

    try {
      // Process through pipeline if configured
      if (this.pipeline) {
        processedEvent = await this.pipeline.process(event);
      }

      // Send to sink
      if (processedEvent) {
        if (Array.isArray(processedEvent)) {
          for (const evt of processedEvent) {
            await this.sink.process(evt);
          }
        } else {
          await this.sink.process(processedEvent);
        }
      }

      // Record metrics
      const latency = Date.now() - startTime;
      if (this.monitor) {
        this.monitor.recordEvent(latency, false);
      }

    } catch (error) {
      const latency = Date.now() - startTime;
      if (this.monitor) {
        this.monitor.recordEvent(latency, true);
      }

      console.error('[ExtensionDataFlow] Error processing event:', error);
    }
  }

  /**
   * Handle error from source
   */
  private handleError(error: Error): void {
    console.error('[ExtensionDataFlow] Source error:', error);
  }

  /**
   * Get data flow statistics
   */
  async getStats(): Promise<DataFlowStats> {
    const stats: DataFlowStats = {
      source: this.source.getStats(),
      sink: await this.sink.getStats(),
    };

    if (this.pipeline) {
      stats.pipeline = this.pipeline.getStats();
    }

    if (this.monitor) {
      stats.monitor = {
        health: await this.monitor.checkHealth(),
        performance: await this.monitor.analyzePerformance(),
      };
    }

    return stats;
  }

  /**
   * Query events from sink
   */
  async queryEvents(filter: Parameters<IndexedDBSink['query']>[0]): Promise<Event[]> {
    return this.sink.query(filter);
  }

  /**
   * Export events from sink
   */
  async exportEvents(filter?: Parameters<IndexedDBSink['query']>[0]): Promise<string> {
    return this.sink.export(filter);
  }

  /**
   * Get health report
   */
  async getHealthReport() {
    if (!this.monitor) {
      throw new Error('Monitoring not enabled');
    }
    return this.monitor.checkHealth();
  }

  /**
   * Get performance report
   */
  async getPerformanceReport() {
    if (!this.monitor) {
      throw new Error('Monitoring not enabled');
    }
    return this.monitor.analyzePerformance();
  }

  /**
   * Get usage report
   */
  getUsageReport() {
    if (!this.monitor) {
      throw new Error('Monitoring not enabled');
    }
    return this.monitor.getUsageReport();
  }

  /**
   * Track usage event
   */
  trackUsage(feature: string, action: string, metadata?: Record<string, any>): void {
    if (this.monitor) {
      this.monitor.trackUsage({
        feature,
        action,
        timestamp: Date.now(),
        metadata,
      });
    }
  }
}
