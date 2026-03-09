import { metricsApi } from '../services/api/index';

export const ADMIN_QUERY_KEYS = {
  metrics: ['admin', 'metrics'] as const,
  status: ['admin', 'status'] as const,
  config: ['admin', 'config'] as const,
  health: ['admin', 'health'] as const,
};

type MetricType = 'counter' | 'gauge' | 'histogram';

export interface MetricDefinition {
  name: string;
  value: number;
  type: MetricType;
  labels?: Record<string, string>;
}

export interface MetricsResponse {
  timestamp: number;
  metrics: Record<string, MetricDefinition>;
}

export interface QueueStatus {
  depth: number;
  size: number;
  watermarkHigh: number;
  watermarkLow: number;
}

export interface ExporterStatus {
  state: 'healthy' | 'degraded' | 'failed' | 'active' | 'inactive' | 'error';
  circuitBreakerState: 'open' | 'closed' | 'half_open';
  lastSuccess?: string;
  lastError?: string;
}

export interface PluginStatus {
  status: 'healthy' | 'degraded' | 'failed' | 'active' | 'inactive' | 'error';
  version: string;
  lastHeartbeat: string;
}

export interface AgentStatus {
  version: string;
  uptime: number;
  queue: QueueStatus;
  exporters: Record<string, ExporterStatus>;
  plugins: Record<string, PluginStatus>;
}

export interface ConfigDiffEntry {
  path: string;
  oldValue: unknown;
  newValue: unknown;
}

export interface ConfigDiff {
  added: string[];
  removed: string[];
  modified: ConfigDiffEntry[];
}

export interface AgentConfig {
  queue: {
    maxSize: number;
    watermarkHigh: number;
    watermarkLow: number;
    encryption: boolean;
  };
}

const mockMetrics: MetricsResponse = {
  timestamp: Date.now(),
  metrics: {
    'ingest.throughput': { name: 'ingest.throughput', value: 1250, type: 'counter', labels: { unit: 'events/s' } },
    'ingest.error_rate': { name: 'ingest.error_rate', value: 5, type: 'gauge', labels: { unit: 'errors/s' } },
    'agent.queue_depth': { name: 'agent.queue_depth', value: 320, type: 'gauge' },
  },
};

const mockStatus: AgentStatus = {
  version: '1.0.0',
  uptime: 42_000,
  queue: {
    depth: 320,
    size: 1000,
    watermarkHigh: 850,
    watermarkLow: 150,
  },
  exporters: {
    elastic: {
      state: 'healthy',
      circuitBreakerState: 'closed',
      lastSuccess: new Date().toISOString(),
      lastError: undefined,
    },
    splunk: {
      state: 'degraded',
      circuitBreakerState: 'half_open',
      lastSuccess: new Date(Date.now() - 120_000).toISOString(),
      lastError: 'Timeout communicating with collector',
    },
  },
  plugins: {
    sys_metrics: {
      status: 'healthy',
      version: '0.9.3',
      lastHeartbeat: new Date().toISOString(),
    },
    log_parser: {
      status: 'active',
      version: '0.4.1',
      lastHeartbeat: new Date(Date.now() - 30_000).toISOString(),
    },
  },
};

let currentConfig: AgentConfig = {
  queue: {
    maxSize: 10_000,
    watermarkHigh: 8_000,
    watermarkLow: 2_000,
    encryption: false,
  },
};

const cloneConfig = (config: AgentConfig): AgentConfig => ({
  queue: { ...config.queue },
});

const mergeConfig = (base: AgentConfig, update: Partial<AgentConfig>): AgentConfig => ({
  queue: {
    ...base.queue,
    ...update.queue,
  },
});

const computeConfigDiff = (before: AgentConfig, after: AgentConfig): ConfigDiff => {
  const added: string[] = [];
  const removed: string[] = [];
  const modified: ConfigDiffEntry[] = [];

  const fields: Array<keyof AgentConfig['queue']> = ['maxSize', 'watermarkHigh', 'watermarkLow', 'encryption'];
  for (const field of fields) {
    const path = `queue.${field}`;
    const prevValue = before.queue[field];
    const nextValue = after.queue[field];

    if (prevValue === undefined && nextValue !== undefined) {
      added.push(path);
    } else if (prevValue !== undefined && nextValue === undefined) {
      removed.push(path);
    } else if (prevValue !== nextValue) {
      modified.push({ path, oldValue: prevValue, newValue: nextValue });
    }
  }

  return { added, removed, modified };
};

const coerceMetrics = (raw: unknown): Record<string, MetricDefinition> => {
  if (!raw || typeof raw !== 'object') {
    return {};
  }

  return Object.entries(raw as Record<string, any>).reduce<Record<string, MetricDefinition>>((acc, [name, value]) => {
    if (value && typeof value === 'object' && typeof value.value === 'number') {
      acc[name] = {
        name,
        value: value.value,
        type: (value.type as MetricType) ?? 'gauge',
        labels: value.labels && typeof value.labels === 'object' ? (value.labels as Record<string, string>) : undefined,
      };
    }
    return acc;
  }, {});
};

export const adminClient = {
  async getMetrics(): Promise<MetricsResponse> {
    try {
      const response = await metricsApi.getMetrics();
      const metrics = coerceMetrics((response as any)?.data?.metrics);
      if (Object.keys(metrics).length > 0) {
        return {
          timestamp: Date.now(),
          metrics,
        };
      }
    } catch (error) {
      console.warn('adminClient.getMetrics fell back to mock data:', error);
    }

    return {
      timestamp: Date.now(),
      metrics: { ...mockMetrics.metrics },
    };
  },

  async getStatus(): Promise<AgentStatus> {
    // Backend wiring is not available yet, so return deterministic mock data.
    return {
      ...mockStatus,
      queue: { ...mockStatus.queue },
      exporters: { ...mockStatus.exporters },
      plugins: { ...mockStatus.plugins },
    };
  },

  async getConfig(): Promise<AgentConfig> {
    return cloneConfig(currentConfig);
  },

  async previewConfigDiff(update: Partial<AgentConfig>): Promise<ConfigDiff> {
    const candidate = mergeConfig(currentConfig, update);
    return computeConfigDiff(currentConfig, candidate);
  },

  async updateConfig(update: Partial<AgentConfig>): Promise<void> {
    currentConfig = mergeConfig(currentConfig, update);
  },

  async healthCheck(): Promise<boolean> {
    try {
      await metricsApi.getMetrics({ limit: 1 });
      return true;
    } catch (error) {
      console.warn('adminClient.healthCheck failed – treating as unhealthy:', error);
      return false;
    }
  },

  getCircuitBreakerState(): 'open' | 'closed' | 'half_open' {
    return mockStatus.exporters.elastic.circuitBreakerState;
  },
};
