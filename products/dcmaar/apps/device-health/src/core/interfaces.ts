import type { TransportConfig } from '../connectors/schemas/transport';
import type { RedactionConfig } from '../shared/privacy/redaction';

export type JsonPrimitive = string | number | boolean | null;
export type JsonValue = JsonPrimitive | JsonValue[] | { [key: string]: JsonValue };
export type MetricPriority = 'low' | 'medium' | 'high' | 'critical';

export interface MetricItem {
  type: string;
  timestamp: number;
  priority: MetricPriority;
  payload: Record<string, JsonValue>;
  tags?: string[];
}

export interface BatchEnvelope {
  id: string;
  seq: number;
  createdAt: number;
  priorBatchId?: string;
  items: MetricItem[];
  signature?: string;
  nonce: string;
  meta: {
    source: string;
    sink: string;
    version: string;
  };
}

export type Ack =
  | { ok: true; batchId: string; receivedAt: number }
  | { ok: false; error: string; retryable?: boolean; batchId?: string };

export interface RuntimeContext {
  readonly clock: () => number;
  readonly random: () => number;
  readonly logger: {
    info: (...args: unknown[]) => void;
    warn: (...args: unknown[]) => void;
    error: (...args: unknown[]) => void;
    debug?: (...args: unknown[]) => void;
  };
}

export interface Signed<TPayload> {
  payload: TPayload;
  jws: string;
  kid: string;
}

export type SignedConfig = Signed<ExtensionConfig>;
export type SignedCommand = Signed<CommandEnvelope>;

export interface CommandEnvelope {
  type: 'flush' | 'pause' | 'resume' | 'setSampling' | 'updateConfig';
  value?: unknown;
  issuedAt: number;
}

export interface MonitoringConfig {
  enabled: boolean;
  captureCoreWebVitals: boolean;
  captureResourceTimings: boolean;
  captureLongTasks: boolean;
  captureInteractions: boolean;
  captureSaasContext: boolean;
  capturePaintTimings: boolean;
  captureScreenshots: boolean;
  maxSessionDuration: number;
  flushIntervalMs: number;
  heartbeatIntervalMs: number;
  heartbeatMetricName: string;
}

export interface IdentityConfig {
  tenantId: string;
  environment: string;
}

export interface UiConfig {
  theme: 'light' | 'dark' | 'auto';
  showNotifications: boolean;
  compactMode: boolean;
}

export interface ExtensionConfig {
  source: {
    type: string;
    options: Record<string, JsonValue>;
  };
  sink: {
    type: string;
    options: Record<string, JsonValue>;
  };
  privacy: RedactionConfig;
  transport: TransportConfig;
  monitoring: MonitoringConfig;
  identity: IdentityConfig;
  ui: UiConfig;
  batch: {
    size: number;
    flushMs: number;
    maxInFlight: number;
  };
  storage: {
    encryptAtRest: boolean;
    keyAlias: string;
    maxSpoolMB: number;
    retentionHours: number;
  };
  security: {
    cspLevel: 'strict';
    allowlistHosts: string[];
    certPins?: string[];
  };
  telemetry: {
    enableHealthProbe: boolean;
    logLevel: 'warn' | 'info' | 'debug';
  };
  version: string;
}

export interface QueueStorage {
  enqueue(batch: BatchEnvelope): Promise<void>;
  peek(): Promise<BatchEnvelope | null>;
  pop(): Promise<void>;
  size(): Promise<number>;
  reset(): Promise<void>;
}
