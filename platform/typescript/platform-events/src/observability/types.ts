/**
 * @fileoverview Observability types for spans, metrics, traces, and audit records.
 */

import type { CorrelationId, SessionId } from '../events/base.js';
import type { AIAutonomyLevel, AIApprovalState } from '../ai/types.js';

/** Reference to a distributed tracing span. */
export interface SpanRef {
  readonly traceId: string;
  readonly spanId: string;
  readonly parentSpanId?: string;
  readonly operationName: string;
  readonly startTime: string;
  readonly endTime?: string;
  readonly status: 'unset' | 'ok' | 'error';
  readonly attributes: Record<string, string | number | boolean>;
}

/** Metric schema definitions. */
export interface MetricSchema {
  readonly name: string;
  readonly description: string;
  readonly type: 'counter' | 'gauge' | 'histogram' | 'summary';
  readonly unit?: string;
  readonly labels: readonly string[];
}

/** Metric family for grouping related metrics. */
export interface MetricFamily {
  readonly familyName: string;
  readonly description: string;
  readonly metrics: readonly MetricSchema[];
  readonly appliesTo: readonly string[]; // component or system names
}

/** Trace context for propagation across boundaries. */
export interface TraceContext {
  readonly traceId: string;
  readonly spanId: string;
  readonly traceFlags: number;
  readonly traceState?: string;
}

/** Audit record for compliance and security auditing. */
export interface AuditRecord {
  readonly auditId: string;
  readonly timestamp: string;
  readonly correlationId: CorrelationId;
  readonly sessionId: SessionId;
  readonly actor: {
    readonly type: 'user' | 'ai' | 'system';
    readonly id: string;
    readonly role?: string;
  };
  readonly action: {
    readonly type: string;
    readonly target: string;
    readonly targetType: 'component' | 'document' | 'canvas' | 'builder' | 'system';
    readonly details: Record<string, unknown>;
  };
  readonly result: {
    readonly success: boolean;
    readonly errorCode?: string;
    readonly errorMessage?: string;
  };
  readonly context: {
    readonly ipAddress?: string;
    readonly userAgent?: string;
    readonly tenantId?: string;
    readonly workspaceId?: string;
  };
  readonly compliance: {
    readonly gdprRelevant: boolean;
    readonly piiInvolved: boolean;
    readonly dataClassification: 'PUBLIC' | 'INTERNAL' | 'SENSITIVE' | 'CREDENTIALS' | 'REGULATED';
    readonly retentionDays: number;
  };
  readonly rollback: {
    readonly canRollback: boolean;
    readonly rollbackWindow?: number; // milliseconds
    readonly rollbackId?: string;
  };
}

/** Canvas telemetry event types. */
export interface CanvasTelemetryEvent {
  readonly eventType:
    | 'viewport.changed'
    | 'selection.changed'
    | 'node.created'
    | 'node.updated'
    | 'node.deleted'
    | 'edge.created'
    | 'edge.updated'
    | 'edge.deleted'
    | 'layout.applied'
    | 'import.completed'
    | 'export.completed'
    | 'render.failed'
    | 'performance.sampled';
  readonly nodeCount: number;
  readonly edgeCount: number;
  readonly durationMs?: number;
  readonly metadata?: Record<string, unknown>;
}

/** Builder telemetry event types. */
export interface BuilderTelemetryEvent {
  readonly eventType:
    | 'document.loaded'
    | 'component.inserted'
    | 'component.moved'
    | 'component.resized'
    | 'component.configured'
    | 'pattern.applied'
    | 'preview.started'
    | 'preview.updated'
    | 'preview.failed'
    | 'codegen.completed'
    | 'codegen.failed';
  readonly componentCount: number;
  readonly operationDurationMs: number;
  readonly documentSize?: number; // bytes
}

/** AI telemetry event for tracking AI operations. */
export interface AITelemetryEvent {
  readonly operationId: string;
  readonly operationType: string;
  readonly modelId?: string;
  readonly inputTokens?: number;
  readonly outputTokens?: number;
  readonly latencyMs: number;
  readonly confidence: number;
  readonly autonomyLevel: AIAutonomyLevel;
  readonly approvalState: AIApprovalState;
  readonly wasAccepted: boolean;
  readonly wasOverridden: boolean;
}

/** Creates a span reference. */
export function createSpanRef(
  traceId: string,
  spanId: string,
  operationName: string
): SpanRef {
  return {
    traceId,
    spanId,
    operationName,
    startTime: new Date().toISOString(),
    status: 'unset',
    attributes: {},
  };
}

/** Creates an audit record. */
export function createAuditRecord(
  correlationId: CorrelationId,
  sessionId: SessionId,
  actor: AuditRecord['actor'],
  action: AuditRecord['action'],
  result: AuditRecord['result'],
  options: {
    context?: Partial<AuditRecord['context']>;
    compliance?: Partial<AuditRecord['compliance']>;
    rollback?: Partial<AuditRecord['rollback']>;
  } = {}
): AuditRecord {
  const now = new Date().toISOString();

  return {
    auditId: generateUUID(),
    timestamp: now,
    correlationId,
    sessionId,
    actor,
    action,
    result,
    context: {
      ...options.context,
    },
    compliance: {
      gdprRelevant: false,
      piiInvolved: false,
      dataClassification: 'INTERNAL',
      retentionDays: 90,
      ...options.compliance,
    },
    rollback: {
      canRollback: false,
      ...options.rollback,
    },
  };
}

/** Required metric families for platform observability. */
export const REQUIRED_METRIC_FAMILIES: MetricFamily[] = [
  {
    familyName: 'canvas.performance',
    description: 'Canvas rendering and interaction performance metrics',
    metrics: [
      {
        name: 'canvas.fps',
        description: 'Frames per second during canvas rendering',
        type: 'gauge',
        unit: 'fps',
        labels: ['canvas_mode', 'product'],
      },
      {
        name: 'canvas.render_time_ms',
        description: 'Time to complete a canvas render pass',
        type: 'histogram',
        unit: 'ms',
        labels: ['canvas_mode', 'node_count_range'],
      },
      {
        name: 'canvas.interaction_latency_ms',
        description: 'Latency from user input to visual update',
        type: 'histogram',
        unit: 'ms',
        labels: ['interaction_type'],
      },
    ],
    appliesTo: ['canvas', 'flow-canvas', 'hybrid-canvas'],
  },
  {
    familyName: 'builder.operations',
    description: 'Builder operation metrics',
    metrics: [
      {
        name: 'builder.operations_total',
        description: 'Total number of builder operations',
        type: 'counter',
        labels: ['operation_type', 'result'],
      },
      {
        name: 'builder.codegen_duration_ms',
        description: 'Code generation duration',
        type: 'histogram',
        unit: 'ms',
        labels: ['target', 'fidelity'],
      },
      {
        name: 'builder.sync_conflicts_total',
        description: 'Number of sync conflicts detected',
        type: 'counter',
        labels: ['source', 'target'],
      },
    ],
    appliesTo: ['ui-builder'],
  },
  {
    familyName: 'ai.operations',
    description: 'AI operation metrics',
    metrics: [
      {
        name: 'ai.suggestions_total',
        description: 'Total AI suggestions generated',
        type: 'counter',
        labels: ['surface', 'kind', 'autonomy_level'],
      },
      {
        name: 'ai.suggestion_acceptance_rate',
        description: 'Rate of accepted vs rejected suggestions',
        type: 'gauge',
        labels: ['surface', 'kind'],
      },
      {
        name: 'ai.override_count_total',
        description: 'Total user overrides of AI actions',
        type: 'counter',
        labels: ['surface', 'action_type'],
      },
    ],
    appliesTo: ['canvas', 'builder', 'design-system'],
  },
];

/** Generates a UUID v4. */
function generateUUID(): string {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    const v = c === 'x' ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}
