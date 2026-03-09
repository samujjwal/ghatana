/**
 * Adapter manager orchestrates source/sink lifecycle and workspace bundles.
 * Central coordinator for all adapter operations.
 */

import type {
  TelemetrySource,
  ControlSink,
  WorkspaceBundle,
  SourceContext,
  SinkContext,
  TelemetrySnapshot,
} from './types';
import { createLogger, type LoggerConfig } from './logger';
import { createTracer, type TracerConfig } from './tracer';
import { createQueue, type QueueConfig } from './queue';
import { createKeyring, type KeyringConfig } from './keyring';
import { createAuditChain, type AuditChainConfig } from './auditChain';
import { createPolicyEngine, type PolicyEngineConfig } from './policyEngine';
import { createSchemaMigrator } from './schemaMigration';

export interface AdapterManagerConfig {
  workspaceId: string;
  bundle: WorkspaceBundle;
  logger?: LoggerConfig;
  tracer?: TracerConfig;
  queue?: QueueConfig;
  audit?: AuditChainConfig;
  policy?: PolicyEngineConfig;
}

export class AdapterManager {
  private config: AdapterManagerConfig;
  private source?: TelemetrySource;
  private sinks: ControlSink[] = [];
  private sourceContext?: SourceContext;
  private sinkContext?: SinkContext;
  private migrator = createSchemaMigrator();

  constructor(config: AdapterManagerConfig) {
    this.config = config;
  }

  async init(): Promise<void> {
    // Verify bundle signature
    const keyring = createKeyring({
      keys: this.config.bundle.keyring,
    });

    const valid = await keyring.verify(
      this.config.bundle,
      this.config.bundle.signature,
      this.config.bundle.keyring[0]?.kid,
    );

    if (!valid) {
      throw new Error('Invalid workspace bundle signature');
    }

    // Initialize contexts
    const logger = createLogger({
      level: 'info',
      workspaceId: this.config.workspaceId,
      ...this.config.logger,
    });

    const tracer = createTracer({
      serviceName: 'adapter-manager',
      enabled: true,
      ...this.config.tracer,
    });

    const queue = await createQueue({
      dbName: `workspace-${this.config.workspaceId}`,
      storeName: 'commands',
      maxSizeMB: this.config.bundle.policies.maxQueueSizeMB ?? 100,
      ...this.config.queue,
    });

    this.sourceContext = {
      workspaceId: this.config.workspaceId,
      keyring,
      logger,
      tracer,
    };

    this.sinkContext = {
      workspaceId: this.config.workspaceId,
      keyring,
      queue,
      logger,
      tracer,
    };

    // Initialize adapters
    await this.initializeAdapters();

    logger.info('AdapterManager initialized', {
      sourceCount: this.config.bundle.sources.length,
      sinkCount: this.config.bundle.sinks.length,
    });
  }

  async getSnapshot(): Promise<TelemetrySnapshot> {
    if (!this.source) {
      throw new Error('No source adapter initialized');
    }

    const span = this.sinkContext?.tracer.startSpan('AdapterManager.getSnapshot');

    try {
      const snapshot = await this.source.getInitialSnapshot();
      span?.setStatus({ code: 'ok' });
      return snapshot;
    } catch (error) {
      span?.setStatus({ code: 'error', message: (error as Error).message });
      throw error;
    } finally {
      span?.end();
    }
  }

  async executeCommand(command: any): Promise<void> {
    if (this.sinks.length === 0) {
      throw new Error('No sink adapters initialized');
    }

    // Enqueue to all sinks
    for (const sink of this.sinks) {
      await sink.enqueue(command as any);
    }
  }

  async flush(): Promise<void> {
    const promises = this.sinks.map((sink) => sink.flush());
    await Promise.all(promises);
  }

  async healthCheck(): Promise<Record<string, any>> {
    const sourceHealth = this.source
      ? await this.source.healthCheck?.()
      : undefined;

    const sinkHealths = await Promise.all(
      this.sinks.map(async (sink, i) => ({
        index: i,
        kind: sink.kind,
        health: await sink.healthCheck?.(),
      })),
    );

    return {
      source: sourceHealth,
      sinks: sinkHealths,
    };
  }

  async close(): Promise<void> {
    await this.source?.close?.();

    const promises = this.sinks.map((sink) => sink.close?.());
    await Promise.all(promises);
  }

  private async initializeAdapters(): Promise<void> {
    // Initialize source (use first configured source)
    if (this.config.bundle.sources.length > 0 && this.sourceContext) {
      const sourceConfig = this.config.bundle.sources[0];
      this.source = await this.createSource(sourceConfig.type, sourceConfig.options);
      await this.source.init(this.sourceContext);
    }

    // Initialize sinks
    if (this.sinkContext) {
      for (const sinkConfig of this.config.bundle.sinks) {
        const sink = await this.createSink(sinkConfig.type, sinkConfig.options);
        await sink.init(this.sinkContext);
        this.sinks.push(sink);
      }
    }
  }

  private async createSource(type: string, options: Record<string, unknown>): Promise<TelemetrySource> {
    const { adapterFactory } = await import('./adapterFactory');
    return adapterFactory.createSource({ type, options });
  }

  private async createSink(type: string, options: Record<string, unknown>): Promise<ControlSink> {
    const { adapterFactory } = await import('./adapterFactory');
    return adapterFactory.createSink({ type, options });
  }
}

export const createAdapterManager = async (
  config: AdapterManagerConfig,
): Promise<AdapterManager> => {
  const manager = new AdapterManager(config);
  await manager.init();
  return manager;
};
