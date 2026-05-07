/**
 * AEP Pipeline Builder — Domain Types.
 *
 * Maps the Java domain model (PipelineSpec, PipelineStageSpec, AgentSpec,
 * ConnectorSpec) to TypeScript for the visual pipeline editor.
 *
 * @doc.type types
 * @doc.purpose AEP pipeline domain types
 * @doc.layer frontend
 */

// ─── Stage & Agent Enums ─────────────────────────────────────────────

export type StageKind =
  | 'ingestion'
  | 'validation'
  | 'transformation'
  | 'enrichment'
  | 'analysis'
  | 'persistence'
  | 'custom';

export type ConnectorDirection = 'INGRESS' | 'EGRESS' | 'BIDIRECTIONAL';

export type PatternType = 'CUSTOM' | 'ANOMALY' | 'THRESHOLD' | 'SEQUENCE' | 'CORRELATION';

export type PipelineStatus = 'DRAFT' | 'VALID' | 'PUBLISHED' | 'RUNNING' | 'FAILED' | 'ARCHIVED';

export type AgentRole =
  | 'ingester'
  | 'validator'
  | 'mapper'
  | 'enricher'
  | 'analyzer'
  | 'aggregator'
  | 'deduplicator'
  | 'persister'
  | 'custom';

// ─── I/O Specs ───────────────────────────────────────────────────────

export interface IOSpec {
  name: string;
  format: string;
  schema?: string;
  description?: string;
}

// ─── Agent Spec (mirrors Java AgentSpec) ─────────────────────────────

export interface AgentSpec {
  id: string;
  agent: string;
  role?: AgentRole | string;
  description?: string;
  inputsSpec?: IOSpec[];
  outputsSpec?: IOSpec[];
  config?: Record<string, unknown>;
}

// ─── Connector Spec (mirrors Java ConnectorSpec) ─────────────────────

export interface ConnectorSpec {
  id: string;
  type: string;
  direction: ConnectorDirection;
  config?: Record<string, unknown>;
  encoding?: string;
  description?: string;
}

// ─── Pipeline Stage (mirrors Java PipelineStageSpec) ─────────────────

export interface PipelineStageSpec {
  name: string;
  kind?: StageKind;
  workflow: AgentSpec[];
  connectorIds?: string[];
  connectors?: ConnectorSpec[];
  description?: string;
}

// ─── Pipeline (mirrors Java PipelineSpec) ────────────────────────────

export interface PipelineSpec {
  id?: string;
  name: string;
  description?: string;
  tenantId?: string;
  status?: PipelineStatus;
  version?: number;
  stages: PipelineStageSpec[];
  tags?: string[];
  createdAt?: string;
  updatedAt?: string;
  createdBy?: string;
}

// ─── ReactFlow Node Data ─────────────────────────────────────────────

export interface StageNodeData extends Record<string, unknown> {
  label: string;
  kind: StageKind;
  agents: AgentSpec[];
  connectorIds?: string[];
  connectors?: ConnectorSpec[];
  description?: string;
  /** Runtime badge: number of agents in this stage */
  agentCount: number;
}

export interface ConnectorNodeData extends Record<string, unknown> {
  label: string;
  connectorId: string;
  type: string;
  direction: ConnectorDirection;
  encoding?: string;
  config?: Record<string, unknown>;
}

export interface AgentNodeData {
  label: string;
  agentId: string;
  agentClass: string;
  role?: AgentRole | string;
  inputsSpec?: IOSpec[];
  outputsSpec?: IOSpec[];
  config?: Record<string, unknown>;
}

// ─── Validation ──────────────────────────────────────────────────────

export interface PipelineValidationError {
  code: string;
  message: string;
  severity: 'error' | 'warning';
  nodeId?: string;
  stageIndex?: number;
  suggestion?: string;
}

export interface PipelineValidationResult {
  isValid: boolean;
  errors: PipelineValidationError[];
  warnings: PipelineValidationError[];
}

// ─── Palette Items ───────────────────────────────────────────────────

export interface PaletteItem {
  id: string;
  label: string;
  kind: StageKind;
  icon: string;
  description: string;
  defaultAgents?: AgentSpec[];
}

export const STAGE_PALETTE: PaletteItem[] = [
  {
    id: 'ingestion',
    label: 'Ingestion',
    kind: 'ingestion',
    icon: 'download',
    description: 'Receive events from external sources (Kafka, HTTP, etc.)',
    defaultAgents: [
      { id: 'ingest-1', agent: 'DataIngestionAgent', role: 'ingester' },
    ],
  },
  {
    id: 'validation',
    label: 'Validation',
    kind: 'validation',
    icon: 'shield-check',
    description: 'Validate events against schemas and business rules',
    defaultAgents: [
      { id: 'validate-1', agent: 'SchemaValidatorAgent', role: 'validator' },
    ],
  },
  {
    id: 'transformation',
    label: 'Transformation',
    kind: 'transformation',
    icon: 'shuffle',
    description: 'Transform, map, and normalize event data',
    defaultAgents: [
      { id: 'transform-1', agent: 'DataMapperAgent', role: 'mapper' },
    ],
  },
  {
    id: 'enrichment',
    label: 'Enrichment',
    kind: 'enrichment',
    icon: 'sparkles',
    description: 'Add external context, lookups, and metadata',
    defaultAgents: [
      { id: 'enrich-1', agent: 'EnricherAgent', role: 'enricher' },
    ],
  },
  {
    id: 'analysis',
    label: 'Analysis',
    kind: 'analysis',
    icon: 'bar-chart-3',
    description: 'Detect anomalies, aggregate, and extract insights',
    defaultAgents: [
      { id: 'analyze-1', agent: 'AnalyzerAgent', role: 'analyzer' },
    ],
  },
  {
    id: 'persistence',
    label: 'Persistence',
    kind: 'persistence',
    icon: 'database',
    description: 'Store processed events to sinks (DB, file, stream)',
    defaultAgents: [
      { id: 'persist-1', agent: 'DatabaseWriterAgent', role: 'persister' },
    ],
  },
  {
    id: 'custom',
    label: 'Custom Stage',
    kind: 'custom',
    icon: 'puzzle',
    description: 'User-defined processing stage',
    defaultAgents: [],
  },
];

// ─── Connector Palette ───────────────────────────────────────────────

export interface ConnectorPaletteItem {
  id: string;
  label: string;
  type: string;
  direction: ConnectorDirection;
  icon: string;
  description: string;
}

export const CONNECTOR_PALETTE: ConnectorPaletteItem[] = [
  {
    id: 'kafka-source',
    label: 'Kafka Source',
    type: 'kafka',
    direction: 'INGRESS',
    icon: 'radio',
    description: 'Consume events from Apache Kafka topics',
  },
  {
    id: 'http-source',
    label: 'HTTP Source',
    type: 'http',
    direction: 'INGRESS',
    icon: 'globe',
    description: 'Receive events via HTTP webhooks',
  },
  {
    id: 'kafka-sink',
    label: 'Kafka Sink',
    type: 'kafka',
    direction: 'EGRESS',
    icon: 'send',
    description: 'Publish processed events to Kafka',
  },
  {
    id: 'database-sink',
    label: 'Database Sink',
    type: 'jdbc',
    direction: 'EGRESS',
    icon: 'database',
    description: 'Write events to relational database',
  },
  {
    id: 'datacloud-sink',
    label: 'Data-Cloud Sink',
    type: 'datacloud',
    direction: 'EGRESS',
    icon: 'cloud',
    description: 'Persist events into Data-Cloud collections',
  },
];
