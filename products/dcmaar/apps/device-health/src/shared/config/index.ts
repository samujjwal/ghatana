import type { ExtensionConfig } from '../../core/interfaces';
import { DEFAULT_REDACTION_CONFIG } from '../privacy/redaction';
export type { ExtensionConfig } from '../../core/interfaces';

export type MonitoringConfig = ExtensionConfig['monitoring'];
export type IdentityConfig = ExtensionConfig['identity'];

export const CONFIG_STORAGE_KEY = 'dcmaar_extension_config';

export const DEFAULT_CONFIG: ExtensionConfig = {
  source: {
    type: 'inline',
    options: {},
  },
  sink: {
    type: 'file',
    options: {
      storageKey: 'dcmaar:fileSink:metrics',
      ackStorageKey: 'dcmaar:fileSink:acks',
      encryptAtRest: true,
    },
  },
  privacy: { ...DEFAULT_REDACTION_CONFIG },
  transport: {
    url: 'wss://localhost:8443/metrics',
    protocol: 'wss',
    timeout: 10_000,
    retries: 5,
  },
  monitoring: {
    enabled: true,
    captureCoreWebVitals: true,
    captureResourceTimings: true,
    captureLongTasks: true,
    captureInteractions: true,
    captureSaasContext: true,
    capturePaintTimings: true,
    captureScreenshots: false,
    maxSessionDuration: 60,
    flushIntervalMs: 30_000,
    heartbeatIntervalMs: 60_000,
    heartbeatMetricName: 'dcmaar.heartbeat',
  },
  identity: {
    tenantId: 'dcmaar-default',
    environment: 'production',
  },
  ui: {
    theme: 'auto',
    showNotifications: true,
    compactMode: false,
  },
  batch: {
    size: 20,
    flushMs: 5_000,
    maxInFlight: 3,
  },
  storage: {
    encryptAtRest: true,
    keyAlias: 'default',
    maxSpoolMB: 512,
    retentionHours: 72,
  },
  security: {
    cspLevel: 'strict',
    allowlistHosts: [],
  },
  telemetry: {
    enableHealthProbe: true,
    logLevel: 'info',
  },
  version: '1.0.0',
};

function isPlainObject(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

export function mergeConfig<T extends object>(base: T, overrides?: Partial<T>): T {
  if (!overrides) {
    return { ...(base as Record<string, unknown>) } as T;
  }

  const result: Record<string, unknown> = { ...(base as Record<string, unknown>) };

  for (const [key, value] of Object.entries(overrides)) {
    if (value === undefined) {
      continue;
    }

    const existing = result[key];

    if (Array.isArray(existing) && Array.isArray(value)) {
      result[key] = [...value];
      continue;
    }

    if (isPlainObject(existing) && isPlainObject(value)) {
      result[key] = mergeConfig(existing, value as Record<string, unknown>);
      continue;
    }

    result[key] = value as unknown;
  }

  return result as T;
}

export function createDefaultConfig(): ExtensionConfig {
  return JSON.parse(JSON.stringify(DEFAULT_CONFIG)) as ExtensionConfig;
}
