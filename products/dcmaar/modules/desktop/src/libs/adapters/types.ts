/**
 * Core adapter interfaces for pluggable telemetry sources and control sinks.
 * Enforces strict contracts for standalone desktop operation.
 */

import type { AgentStatus, MetricsResponse } from '../adminClient';

// ============================================================================
// Telemetry Source Types
// ============================================================================

export interface TelemetrySnapshot {
  version: string;
  collectedAt: string;
  agents: AgentStatus[];
  metrics?: MetricsResponse;
  alerts?: AlertEnvelope[];
  metadata?: Record<string, unknown>;
}

export interface AlertEnvelope {
  id: string;
  severity: 'info' | 'warning' | 'error' | 'critical';
  message: string;
  timestamp: string;
  source?: string;
}

export interface SourceContext {
  workspaceId: string;
  keyring: KeyringService;
  logger: Logger;
  tracer: Tracer;
}

export interface TelemetrySource {
  readonly kind: 'mock' | 'file' | 'daemon' | 'http' | 'grpc' | 'bridge' | 'custom';
  
  init(ctx: SourceContext): Promise<void>;
  getInitialSnapshot(): Promise<TelemetrySnapshot>;
  subscribe?(emit: (update: TelemetrySnapshot) => void): Promise<() => void>;
  healthCheck?(): Promise<HealthStatus>;
  close?(): Promise<void>;
}

// ============================================================================
// Control Sink Types
// ============================================================================

export interface ControlCommand {
  id: string;
  category: 'config' | 'action' | 'policy' | 'script';
  payload: unknown;
  metadata: CommandMetadata;
  signature?: string;
}

export interface CommandMetadata {
  issuedBy: string;
  issuedAt: string;
  priority: 'low' | 'medium' | 'high' | 'urgent';
  tags?: string[];
  encrypted?: boolean;
  auditLogged?: boolean;
}

export interface SinkContext {
  workspaceId: string;
  keyring: KeyringService;
  queue: QueueService;
  logger: Logger;
  tracer: Tracer;
}

export interface ControlSink {
  readonly kind: 'mock' | 'file' | 'daemon' | 'http' | 'grpc' | 'mq' | 'bridge' | 'custom';
  
  init(ctx: SinkContext): Promise<void>;
  enqueue(command: ControlCommand): Promise<void>;
  flush(): Promise<SinkAck[]>;
  healthCheck?(): Promise<HealthStatus>;
  close?(): Promise<void>;
}

export interface SinkAck {
  ok: boolean;
  commandId: string;
  deliveredAt?: string;
  error?: string;
}

// ============================================================================
// Health & Observability
// ============================================================================

export interface HealthStatus {
  healthy: boolean;
  lastCheck: string;
  latencyMs?: number;
  error?: string;
  details?: Record<string, unknown>;
}

export interface AdapterMetrics {
  totalRequests: number;
  successCount: number;
  errorCount: number;
  avgLatencyMs: number;
  queueDepth?: number;
  lastActivity?: string;
}

// ============================================================================
// Supporting Services
// ============================================================================

export interface KeyringService {
  verify(payload: unknown, signature: string, kid: string): Promise<boolean>;
  sign(payload: unknown, kid: string): Promise<string>;
  getPublicKey(kid: string): Promise<string | null>;
  listKeys(): Promise<KeyInfo[]>;
}

export interface KeyInfo {
  kid: string;
  algorithm: string;
  revoked: boolean;
  createdAt: string;
}

export interface QueueService {
  enqueue(item: ControlCommand): Promise<void>;
  peek(): Promise<ControlCommand | null>;
  dequeue(): Promise<ControlCommand | null>;
  size(): Promise<number>;
  compact(): Promise<number>;
}

export interface Logger {
  debug(message: string, meta?: Record<string, unknown>): void;
  info(message: string, meta?: Record<string, unknown>): void;
  warn(message: string, meta?: Record<string, unknown>): void;
  error(message: string, error?: Error, meta?: Record<string, unknown>): void;
}

export interface Tracer {
  startSpan(name: string, attributes?: Record<string, unknown>): Span;
}

export interface Span {
  setAttribute(key: string, value: unknown): void;
  setStatus(status: { code: 'ok' | 'error'; message?: string }): void;
  end(): void;
}

// ============================================================================
// Workspace Bundle
// ============================================================================

export interface WorkspaceBundle {
  workspaceVersion: string;
  createdAt: string;
  sources: SourceConfig[];
  sinks: SinkConfig[];
  rbac: RBACConfig;
  keyring: KeyInfo[];
  policies: PolicyConfig;
  signature: string;
}

export interface SourceConfig {
  type: string;
  options: Record<string, unknown>;
}

export interface SinkConfig {
  type: string;
  priority?: number;
  options: Record<string, unknown>;
}

export interface RBACConfig {
  role: string;
  modules: string[];
}

export interface PolicyConfig {
  allowRemote: boolean;
  requireMTLS: boolean;
  maxQueueSizeMB?: number;
  retentionDays?: number;
}
