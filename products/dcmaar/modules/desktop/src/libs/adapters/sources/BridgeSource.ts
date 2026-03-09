import type { TelemetrySource, SourceContext, TelemetrySnapshot, HealthStatus } from '../types';
import type { TelemetryPayload } from '../../../lib/bridge-protocol-local';
import type { BridgeClientOptions, BridgeClientLike } from '../../bridge/bridgeClient';
import { getBridgeClient } from '../../bridge/bridgeClient';
import type { AgentStatus, MetricsResponse } from '../../adminClient';

export interface BridgeSourceOptions extends BridgeClientOptions {
  client?: BridgeClientLike;
  initialSnapshot?: TelemetrySnapshot;
}

export class BridgeSource implements TelemetrySource {
  readonly kind = 'bridge' as const;

  private ctx?: SourceContext;
  private client: BridgeClientLike;
  private latestSnapshot?: TelemetrySnapshot;
  private unsubscribe?: () => void;
  private options: BridgeSourceOptions;

  constructor(options: BridgeSourceOptions = {}) {
    const { client, ...clientOptions } = options;
    this.client = client ?? getBridgeClient(clientOptions);
    this.options = clientOptions;
    this.latestSnapshot = options.initialSnapshot;
  }

  async init(ctx: SourceContext): Promise<void> {
    this.ctx = ctx;
    await this.client.connect();
    this.unsubscribe = this.client.addTelemetryListener((payload) => {
      const snapshot = this.buildSnapshot(payload);
      this.latestSnapshot = snapshot;
      ctx.logger.debug?.('BridgeSource received telemetry', {
        batchId: payload.batchId,
        collectedAt: payload.collectedAt,
      });
    });
  }

  async getInitialSnapshot(): Promise<TelemetrySnapshot> {
    if (!this.latestSnapshot) {
      this.latestSnapshot = {
        version: 'bridge-initial',
        collectedAt: new Date().toISOString(),
        agents: [],
      };
    }
    return this.latestSnapshot;
  }

  async subscribe(emit: (update: TelemetrySnapshot) => void): Promise<() => void> {
    await this.client.connect();
    const release = this.client.addTelemetryListener((payload) => {
      const snapshot = this.buildSnapshot(payload);
      this.latestSnapshot = snapshot;
      emit(snapshot);
    });
    return () => {
      release();
    };
  }

  async healthCheck(): Promise<HealthStatus> {
    const started = Date.now();
    try {
      await this.client.connect();
      return {
        healthy: true,
        lastCheck: new Date().toISOString(),
        latencyMs: Date.now() - started,
      };
    } catch (error) {
      return {
        healthy: false,
        lastCheck: new Date().toISOString(),
        error: error instanceof Error ? error.message : String(error),
      };
    }
  }

  async close(): Promise<void> {
    this.unsubscribe?.();
  }

  private buildSnapshot(payload: TelemetryPayload): TelemetrySnapshot {
    const data = (payload.data ?? {}) as Partial<TelemetrySnapshot> & {
      agents?: AgentStatus[];
      metrics?: MetricsResponse;
      metadata?: Record<string, unknown>;
    };

    const snapshot: TelemetrySnapshot = {
      version: typeof data.version === 'string' ? data.version : (payload.batchId ?? 'bridge-unknown'),
      collectedAt: typeof data.collectedAt === 'string' ? data.collectedAt : (payload.collectedAt ?? new Date().toISOString()),
      agents: Array.isArray(data.agents) ? data.agents : [],
      metrics: data.metrics,
      // @ts-ignore - alerts type compatibility
      alerts: payload.alerts,
      metadata: {
        ...(data.metadata ?? {}),
        ...((payload as any).meta ?? (payload as any).metadata ?? {}),
        bridgeBatchId: payload.batchId,
      },
    };

    if (!snapshot.metrics && typeof data === 'object') {
      snapshot.metrics = (data as any).metrics;
    }

    return snapshot;
  }
}

export const createBridgeSource = (options: BridgeSourceOptions = {}): TelemetrySource => {
  return new BridgeSource(options);
};
