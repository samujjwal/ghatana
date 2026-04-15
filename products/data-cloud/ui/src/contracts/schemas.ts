/**
 * API Contract Schemas
 *
 * Single source of truth for API response/request shapes.
 * Used by contract tests to validate correctness and by MSW handlers
 * to guarantee mock data matches real API contracts.
 *
 * @doc.type config
 * @doc.purpose Zod schemas defining frontend-backend API contracts
 * @doc.layer frontend
 * @doc.pattern Contract / Schema
 */

import { z } from 'zod';

export const ApiMetaSchema = z.object({
  requestId: z.string(),
  tenantId: z.string(),
  timestamp: z.string(),
  apiVersion: z.string(),
});

export const ApiAiMetaSchema = z.object({
  confidence: z.number(),
  model: z.string(),
  reasons: z.array(z.string()),
  fallback: z.boolean(),
});

export function apiEnvelopeSchema<T extends z.ZodTypeAny>(dataSchema: T) {
  return z.object({
    data: dataSchema,
    meta: ApiMetaSchema,
    ai: ApiAiMetaSchema.optional(),
  });
}

// ---------------------------------------------------------------------------
// Collections
// ---------------------------------------------------------------------------

export const CollectionSchema = z.object({
  id: z.string(),
  name: z.string(),
  description: z.string(),
  schemaType: z.enum(['entity', 'event', 'timeseries', 'graph', 'document']),
  status: z.enum(['active', 'draft', 'archived', 'processing']),
  entityCount: z.number(),
  schema: z.record(z.string(), z.unknown()),
  tags: z.array(z.string()),
  createdAt: z.string(),
  updatedAt: z.string(),
  createdBy: z.string(),
});

export const PaginatedCollectionResponseSchema = z.object({
  items: z.array(CollectionSchema),
  total: z.number(),
  page: z.number(),
  pageSize: z.number(),
  hasMore: z.boolean(),
});

export const CreateCollectionRequestSchema = z.object({
  name: z.string().min(1),
  description: z.string(),
  schemaType: z.enum(['entity', 'event', 'timeseries', 'graph', 'document']),
  schema: z.record(z.string(), z.unknown()),
  tags: z.array(z.string()).optional(),
});

export const UpdateCollectionRequestSchema = z.object({
  name: z.string().min(1).optional(),
  description: z.string().optional(),
  schema: z.record(z.string(), z.unknown()).optional(),
  tags: z.array(z.string()).optional(),
  status: z.enum(['active', 'draft', 'archived', 'processing']).optional(),
});

export const CollectionEntitySchema = z.object({
  id: z.string(),
  data: z.object({
    name: z.unknown().optional(),
    description: z.unknown().optional(),
    schemaType: z.unknown().optional(),
    status: z.unknown().optional(),
    isActive: z.unknown().optional(),
    entityCount: z.unknown().optional(),
    schema: z.object({
      fields: z.unknown().optional(),
    }).catchall(z.unknown()).optional(),
    tags: z.unknown().optional(),
    createdBy: z.unknown().optional(),
    permission: z.unknown().optional(),
    applications: z.unknown().optional(),
    createdAt: z.unknown().optional(),
    updatedAt: z.unknown().optional(),
  }).catchall(z.unknown()),
  createdAt: z.string().optional(),
  updatedAt: z.string().optional(),
});

export const CollectionEntityListResponseSchema = z.object({
  entities: z.array(CollectionEntitySchema),
  count: z.number().optional(),
});

export const EntityValidationViolationSchema = z.object({
  field: z.string(),
  message: z.string(),
  code: z.string().optional(),
});

export const EntityValidationResponseSchema = z.object({
  valid: z.boolean(),
  violations: z.array(EntityValidationViolationSchema),
});

export const BatchEntityValidationRequestSchema = z.object({
  entities: z.array(z.record(z.string(), z.unknown())).min(1).max(1000),
});

export const BatchEntityValidationResultSchema = z.object({
  index: z.number().int().nonnegative(),
  valid: z.boolean(),
  violations: z.array(EntityValidationViolationSchema),
});

export const BatchEntityValidationResponseSchema = z.object({
  allValid: z.boolean(),
  results: z.array(BatchEntityValidationResultSchema),
});

export const FeatureSchema = z.object({
  id: z.string(),
  name: z.string(),
  description: z.string().optional(),
  dataType: z.string(),
  version: z.string(),
  tags: z.array(z.string()).optional(),
  createdAt: z.string(),
  updatedAt: z.string(),
});

// ---------------------------------------------------------------------------
// Workflows
// ---------------------------------------------------------------------------

export const PipelineNodeSchema = z.object({
  id: z.string(),
  type: z.string(),
  label: z.string(),
  position: z.object({
    x: z.number(),
    y: z.number(),
  }),
  data: z.record(z.string(), z.unknown()),
});

export const PipelineEdgeSchema = z.object({
  id: z.string(),
  source: z.string(),
  target: z.string(),
  label: z.string().optional(),
});

export const PipelineSchema = z.object({
  id: z.string(),
  tenantId: z.string().optional(),
  name: z.string().optional(),
  description: z.string().optional(),
  status: z.string().optional(),
  nodes: z.array(PipelineNodeSchema).optional(),
  edges: z.array(PipelineEdgeSchema).optional(),
  schedule: z.string().optional(),
  tags: z.array(z.string()).optional(),
  createdAt: z.string().optional(),
  updatedAt: z.string().optional(),
  createdBy: z.string().optional(),
  lastExecutedAt: z.string().optional(),
}).catchall(z.unknown());

export const PipelineListResponseSchema = z.object({
  tenantId: z.string(),
  pipelines: z.array(PipelineSchema),
  count: z.number(),
  timestamp: z.string(),
});

export const PipelineMutationRequestSchema = z.record(z.string(), z.unknown());

export const PipelineOptimisationHintSchema = z.object({
  type: z.enum(['redundancy', 'parallelisation', 'error_handling', 'data_quality', 'performance', 'cost']),
  title: z.string(),
  description: z.string(),
  confidence: z.number(),
  impact: z.enum(['high', 'medium', 'low']),
  fallback: z.boolean(),
});

export const PipelineOptimisationHintsResponseSchema = z.object({
  pipelineId: z.string(),
  hints: z.array(PipelineOptimisationHintSchema),
  generatedAt: z.string(),
  modelVersion: z.string().optional(),
});

export const WorkflowSchema = z.object({
  id: z.string(),
  name: z.string(),
  description: z.string(),
  status: z.enum(['active', 'inactive', 'draft']),
  executionCount: z.number(),
  lastExecutedAt: z.string().optional(),
  createdAt: z.string(),
  updatedAt: z.string(),
});

export const PaginatedWorkflowResponseSchema = z.object({
  items: z.array(WorkflowSchema),
  total: z.number(),
  page: z.number(),
  pageSize: z.number(),
  hasMore: z.boolean(),
});

export const CreateWorkflowRequestSchema = z.object({
  name: z.string().min(1),
  description: z.string(),
  definition: z.record(z.string(), z.unknown()).optional(),
});

export const ExecutionSchema = z.object({
  id: z.string(),
  workflowId: z.string(),
  status: z.enum(['pending', 'running', 'completed', 'failed', 'cancelled']),
  startedAt: z.string(),
  completedAt: z.string().optional(),
  duration: z.number().optional(),
  input: z.record(z.string(), z.unknown()).optional(),
  output: z.record(z.string(), z.unknown()).optional(),
  error: z.string().optional(),
});

export const CheckpointSchema = z.object({
  id: z.string(),
  data: z.record(z.string(), z.unknown()),
  tenantId: z.string(),
});

export const CheckpointListResponseSchema = z.object({
  tenantId: z.string(),
  checkpoints: z.array(CheckpointSchema),
  count: z.number(),
  timestamp: z.string(),
});

export const CheckpointSaveResponseSchema = z.object({
  id: z.string(),
  tenantId: z.string(),
  savedAt: z.string(),
});

// ---------------------------------------------------------------------------
// Data Fabric — Storage Profiles
// ---------------------------------------------------------------------------

export const StorageProfileSchema = z.object({
  id: z.string(),
  name: z.string(),
  type: z.string(),
  isDefault: z.boolean(),
  status: z.string(),
  config: z.record(z.string(), z.unknown()),
  createdAt: z.string(),
  updatedAt: z.string(),
});

export const StorageProfileMetricsSchema = z.object({
  storageUsedBytes: z.number(),
  storageTotalBytes: z.number(),
  readOpsPerSec: z.number(),
  writeOpsPerSec: z.number(),
  latencyP99Ms: z.number(),
  lastUpdated: z.string(),
});

export const CollectionCostReportTierSchema = z.object({
  tier: z.enum(['HOT', 'WARM', 'COLD']),
  sizeGb: z.number(),
  costDccPerDay: z.number(),
  backend: z.string(),
});

export const CollectionCostReportSchema = z.object({
  collectionId: z.string(),
  tenantId: z.string(),
  totalSizeGb: z.number(),
  totalCostDccPerDay: z.number(),
  currency: z.string(),
  tiers: z.array(CollectionCostReportTierSchema),
  note: z.string(),
});

export const MigrateCollectionResultSchema = z.object({
  collection: z.string(),
  targetTier: z.enum(['WARM', 'COLD']),
  status: z.enum(['SCHEDULED', 'COMPLETED']),
  eventsMigrated: z.number(),
});

export const PluginViewSchema = z.object({
  id: z.string(),
  displayName: z.string(),
  version: z.string(),
  status: z.enum(['enabled', 'disabled']),
  supportedRecordTypes: z.array(z.string()),
});

export const PluginListResponseSchema = z.object({
  plugins: z.array(PluginViewSchema),
  total: z.number(),
});

// ---------------------------------------------------------------------------
// Data Fabric — Connectors
// ---------------------------------------------------------------------------

export const ConnectorSchema = z.object({
  id: z.string(),
  name: z.string(),
  type: z.string(),
  storageProfileId: z.string(),
  status: z.string(),
  config: z.record(z.string(), z.unknown()),
  createdAt: z.string(),
  updatedAt: z.string(),
});

// ---------------------------------------------------------------------------
// Reports
// ---------------------------------------------------------------------------

export const ReportResultSchema = z.object({
  reportId: z.string(),
  reportName: z.string(),
  format: z.string(),
  rowCount: z.number(),
  contentType: z.string(),
  executionTimeMs: z.number(),
  generatedAt: z.string(),
  body: z.unknown().optional(),
  rows: z.array(z.record(z.string(), z.unknown())).optional(),
});

export const ReportListSchema = z.object({
  reports: z.record(z.string(), z.string()),
  count: z.number(),
});

// ---------------------------------------------------------------------------
// Models
// ---------------------------------------------------------------------------

export const AiModelSchema = z.object({
  id: z.string(),
  tenantId: z.string(),
  name: z.string(),
  version: z.string(),
  framework: z.string(),
  deploymentStatus: z.string(),
  trainingMetrics: z.unknown().optional(),
  createdAt: z.string(),
  updatedAt: z.string(),
  deployedAt: z.string().optional(),
});

export const AiModelListSchema = z.object({
  models: z.array(AiModelSchema),
  count: z.number(),
});

// ---------------------------------------------------------------------------
// Voice and capabilities
// ---------------------------------------------------------------------------

export const CapabilityRegistryDataSchema = z.object({
  capabilities: z.record(z.string(), z.unknown()),
  generatedAt: z.string(),
});

export const CapabilityRegistryEnvelopeSchema = apiEnvelopeSchema(CapabilityRegistryDataSchema);

export const PiiFieldRegistryDataSchema = z.object({
  globalFields: z.array(z.string()),
  tenantFields: z.array(z.string()),
  effectiveCount: z.number(),
});

export const PiiFieldRegistryEnvelopeSchema = apiEnvelopeSchema(PiiFieldRegistryDataSchema);

export const ComplianceSummaryDataSchema = z.object({
  tenantId: z.string(),
  collectionsTotal: z.number(),
  collectionsClassified: z.number(),
  collectionsUnclassified: z.number(),
  piiFieldsRegistered: z.number(),
  legalHoldsActive: z.number(),
  retentionExpirationsIn30Days: z.number(),
  lastAuditAt: z.string(),
  auditEventsIn30Days: z.number(),
  authFailuresIn30Days: z.number(),
  redactionsIn30Days: z.number(),
  purgesIn30Days: z.number(),
  recentAuditEvents: z.array(z.record(z.string(), z.unknown())),
  complianceStatus: z.enum(['COMPLIANT', 'NEEDS_CLASSIFICATION', 'REVIEW_REQUIRED']),
  generatedAt: z.string(),
});

export const ComplianceSummaryEnvelopeSchema = apiEnvelopeSchema(ComplianceSummaryDataSchema);

export const AgentCapabilitySchema = z.object({
  id: z.string(),
  name: z.string(),
  description: z.string(),
  version: z.string(),
  inputSchema: z.record(z.string(), z.unknown()).optional(),
  outputSchema: z.record(z.string(), z.unknown()).optional(),
});

export const AgentCatalogEntrySchema = z.object({
  id: z.string().optional(),
  agentId: z.string().optional(),
  name: z.string().optional(),
  description: z.string().optional(),
  version: z.string().optional(),
  tenantId: z.string().optional(),
  status: z.string().optional(),
  capabilities: z.array(AgentCapabilitySchema).optional(),
  registeredAt: z.string().optional(),
  updatedAt: z.string().optional(),
  endpoint: z.string().optional(),
  metadata: z.record(z.string(), z.unknown()).optional(),
});

export const AgentCatalogListSchema = z.array(AgentCatalogEntrySchema);

export const VoiceIntentCatalogEntrySchema = z.object({
  name: z.string(),
  description: z.string(),
  httpMethod: z.string(),
  pathTemplate: z.string(),
  requiredParams: z.array(z.string()),
  optionalParams: z.array(z.string()),
  sensitivity: z.string(),
});

export const VoiceIntentCatalogDataSchema = z.object({
  intents: z.array(VoiceIntentCatalogEntrySchema),
  count: z.number(),
});

export const VoiceIntentCatalogEnvelopeSchema = apiEnvelopeSchema(VoiceIntentCatalogDataSchema);

export const VoiceIntentClassificationDataSchema = z.object({
  confidence: z.number(),
  matched: z.boolean(),
  intent: z.string().optional(),
  description: z.string().optional(),
  extractedParams: z.record(z.string(), z.string()).optional(),
  requiresConfirmation: z.boolean().optional(),
});

export const VoiceIntentClassificationEnvelopeSchema = apiEnvelopeSchema(VoiceIntentClassificationDataSchema);

export const VoiceIntentPreviewDataSchema = z.object({
  intentName: z.string(),
  executed: z.boolean(),
  matched: z.boolean(),
  confidence: z.number().optional(),
});

export const VoiceIntentPreviewEnvelopeSchema = apiEnvelopeSchema(VoiceIntentPreviewDataSchema);

export const VoiceIntentConfirmationDataSchema = z.object({
  requiresConfirmation: z.literal(true),
  resolvedIntent: z.string(),
  resolvedPath: z.string(),
  confidence: z.number(),
  message: z.string(),
});

export const VoiceIntentConfirmationEnvelopeSchema = apiEnvelopeSchema(VoiceIntentConfirmationDataSchema);

export const VoiceIntentExecutionDataSchema = z.object({
  executed: z.literal(true),
  intentName: z.string(),
  httpMethod: z.string(),
  resolvedPath: z.string(),
  parameters: z.record(z.string(), z.string()),
  sensitivity: z.string(),
  description: z.string(),
  speechSummary: z.string(),
  audioBase64: z.string().optional(),
});

export const VoiceIntentExecutionEnvelopeSchema = apiEnvelopeSchema(VoiceIntentExecutionDataSchema);

// ---------------------------------------------------------------------------
// Paginated generic
// ---------------------------------------------------------------------------

export function paginatedSchema<T extends z.ZodTypeAny>(itemSchema: T) {
  return z.object({
    items: z.array(itemSchema),
    total: z.number(),
    page: z.number(),
    pageSize: z.number(),
    hasMore: z.boolean(),
  });
}

// ---------------------------------------------------------------------------
// Analytics
// ---------------------------------------------------------------------------

export const AnalyticsQuerySchema = z.object({
  metric: z.string().min(1),
  dimensions: z.array(z.string()).optional(),
  filters: z.record(z.string(), z.string()).optional(),
  startTime: z.string(),
  endTime: z.string(),
  granularity: z.enum(['minute', 'hour', 'day', 'week', 'month']),
  limit: z.number().int().positive().optional(),
});

export const AnalyticsDataPointSchema = z.object({
  timestamp: z.string(),
  value: z.number(),
  dimensions: z.record(z.string(), z.string()).optional(),
});

export const AnalyticsResultSchema = z.object({
  metric: z.string(),
  unit: z.string().optional(),
  dataPoints: z.array(AnalyticsDataPointSchema),
  aggregation: z.object({
    min: z.number(),
    max: z.number(),
    avg: z.number(),
    sum: z.number(),
    count: z.number(),
  }),
  queryDurationMs: z.number(),
});

export const PaginatedAnalyticsResultSchema = z.object({
  results: z.array(AnalyticsResultSchema),
  total: z.number(),
  page: z.number(),
  pageSize: z.number(),
  hasMore: z.boolean(),
});

export const AnalyticsSqlQueryRequestSchema = z.object({
  query: z.string().min(1),
  parameters: z.record(z.string(), z.unknown()).optional(),
});

export const AnalyticsExplainRequestSchema = z.object({
  query: z.string().min(1),
  parameters: z.record(z.string(), z.unknown()).optional(),
});

export const AnalyticsSqlQueryResponseSchema = z.object({
  queryId: z.string(),
  queryType: z.string(),
  rowCount: z.number(),
  columnCount: z.number(),
  rows: z.array(z.record(z.string(), z.unknown())),
  executionTimeMs: z.number(),
  optimized: z.boolean(),
  timestamp: z.string(),
  warning: z.string().optional(),
});

export const AnalyticsExplainResponseSchema = z.object({
  queryId: z.string(),
  queryType: z.string(),
  dataSources: z.array(z.string()),
  estimatedCost: z.number(),
  optimized: z.boolean(),
  explain: z.boolean(),
  timestamp: z.string(),
});

export const AnalyticsSuggestQuerySchema = z.object({
  name: z.string(),
  template: z.string(),
  explanation: z.string(),
});

export const AnalyticsSuggestResponseSchema = z.object({
  data: z.object({
    queries: z.array(AnalyticsSuggestQuerySchema),
  }),
  ai: z.object({
    confidence: z.number().optional(),
    fallback: z.boolean().optional(),
  }).optional(),
});

export const AnomalyDetectionRequestSchema = z.object({
  collectionName: z.string().min(1),
  timeRange: z.object({
    start: z.string(),
    end: z.string(),
  }).optional(),
  metrics: z.array(z.string()).optional(),
});

export const DetectedAnomalySchema = z.object({
  id: z.string(),
  type: z.enum(['spike', 'drop', 'pattern_change', 'outlier', 'missing_data']),
  severity: z.enum(['critical', 'warning', 'info']),
  metric: z.string(),
  timestamp: z.string(),
  value: z.number(),
  expectedValue: z.number(),
  deviation: z.number(),
  description: z.string(),
  suggestedAction: z.string().optional(),
});

export const SearchResultSchema = z.object({
  entityId: z.string(),
  collectionId: z.string(),
  score: z.number(),
  highlights: z.record(z.string(), z.array(z.string())).optional(),
  data: z.record(z.string(), z.unknown()),
});

export const SimilarEntityMatchSchema = z.object({
  id: z.string(),
  collection: z.string(),
  score: z.number(),
  data: z.record(z.string(), z.unknown()),
});

export const SimilarEntitiesResponseSchema = z.object({
  collection: z.string(),
  entityId: z.string(),
  matches: z.array(SimilarEntityMatchSchema),
  count: z.number(),
  requestId: z.string(),
});

export const CollectionRagRequestSchema = z.object({
  question: z.string().min(1),
  k: z.number().int().positive().optional(),
});

export const CollectionRagContextItemSchema = z.object({
  id: z.string(),
  score: z.number(),
  data: z.record(z.string(), z.unknown()),
  excerpt: z.string(),
});

export const CollectionRagResponseSchema = z.object({
  collection: z.string(),
  question: z.string(),
  answer: z.string(),
  context: z.array(CollectionRagContextItemSchema),
  requestId: z.string(),
});

export const AnomalyQueryItemSchema = z.object({
  eventId: z.string(),
  eventType: z.string(),
  timestamp: z.string(),
  collection: z.string(),
  tenantId: z.string(),
  anomalyPayload: z.string(),
});

export const AnomalyQueryResponseSchema = z.object({
  tenantId: z.string(),
  collection: z.string().nullable().optional(),
  since: z.string(),
  count: z.number(),
  anomalies: z.array(AnomalyQueryItemSchema),
});

export const DataProductSchemaFieldSchema = z.object({
  name: z.string(),
  types: z.array(z.string()),
});

export const DataProductSchemaInfoSchema = z.object({
  fields: z.array(DataProductSchemaFieldSchema),
  sampleCount: z.number(),
});

export const DataProductAccessSchema = z.object({
  visibility: z.string(),
  allowedSubscribers: z.array(z.string()),
});

export const DataProductSlaSchema = z.object({
  freshnessSeconds: z.number(),
  completenessTarget: z.number(),
  accuracyTarget: z.number(),
});

export const DataProductQualitySchema = z.object({
  completeness: z.number(),
  freshnessLagSeconds: z.number(),
  sampleSize: z.number(),
  measuredAt: z.string(),
});

export const DataProductLineageSummarySchema = z.object({
  upstream: z.array(z.string()),
  downstream: z.array(z.string()),
  impactLevel: z.string(),
});

export const DataProductDescriptorSchema = z.object({
  id: z.string(),
  tenantId: z.string(),
  name: z.string(),
  collection: z.string(),
  description: z.string(),
  publishedAt: z.string(),
  schema: DataProductSchemaInfoSchema,
  governance: z.record(z.string(), z.unknown()),
  access: DataProductAccessSchema,
  sla: DataProductSlaSchema,
  quality: DataProductQualitySchema,
  qualityStatus: z.string(),
  lineage: DataProductLineageSummarySchema,
});

export const PublishDataProductRequestSchema = z.object({
  productId: z.string().optional(),
  collection: z.string().min(1),
  name: z.string().min(1),
  description: z.string().optional(),
  governance: z.record(z.string(), z.unknown()).optional(),
  access: DataProductAccessSchema.optional(),
  sla: DataProductSlaSchema.optional(),
});

export const PublishDataProductResponseSchema = z.object({
  productId: z.string(),
  collection: z.string(),
  name: z.string(),
  descriptor: DataProductDescriptorSchema,
  requestId: z.string(),
  publishedAt: z.string(),
});

export const DataProductListResponseSchema = z.object({
  items: z.array(DataProductDescriptorSchema),
  count: z.number(),
  requestId: z.string(),
  timestamp: z.string(),
});

export const DataProductSubscriptionRequestSchema = z.object({
  consumerId: z.string().optional(),
});

export const DataProductSubscriptionResponseSchema = z.object({
  subscriptionId: z.string(),
  productId: z.string(),
  consumerId: z.string(),
  status: z.string(),
  requestId: z.string(),
});

export const LineageNodeSchema = z.object({
  id: z.string(),
  type: z.string(),
  name: z.string(),
  role: z.string(),
  metadata: z.record(z.string(), z.unknown()),
});

export const LineageEdgeSchema = z.object({
  source: z.string(),
  target: z.string(),
  type: z.string(),
});

export const LineageDagResponseSchema = z.object({
  collection: z.string(),
  tenantId: z.string(),
  direction: z.string(),
  timestamp: z.string(),
  dag: z.object({
    nodes: z.array(LineageNodeSchema),
    edges: z.array(LineageEdgeSchema),
  }),
  upstreamCount: z.number(),
  downstreamCount: z.number(),
});

export const LineageImpactResponseSchema = z.object({
  collection: z.string(),
  tenantId: z.string(),
  impactLevel: z.string(),
  affectedCount: z.number(),
  affectedCollections: z.array(z.string()),
  timestamp: z.string(),
});

// ---------------------------------------------------------------------------
// Events
// ---------------------------------------------------------------------------

export const EventSchema = z.object({
  id: z.string().optional(),
  tenantId: z.string().optional(),
  type: z.string().min(1),
  payload: z.record(z.string(), z.unknown()),
  headers: z.record(z.string(), z.string()).optional(),
  offset: z.number(),
  timestamp: z.string(),
  source: z.string().optional(),
  correlationId: z.string().optional(),
  schemaVersion: z.string().optional(),
});

export const AppendEventRequestSchema = z.object({
  type: z.string().min(1),
  payload: z.record(z.string(), z.unknown()),
  headers: z.record(z.string(), z.string()).optional(),
  source: z.string().optional(),
  correlationId: z.string().optional(),
  schemaVersion: z.string().optional(),
});

export const AppendEventResponseSchema = z.object({
  offset: z.number(),
  type: z.string().min(1),
  timestamp: z.string(),
});

export const EventQueryRequestSchema = z.object({
  tenantId: z.string().optional(),
  type: z.string().optional(),
  limit: z.number().int().positive().max(10000).default(100),
  from: z.number().int().nonnegative().optional(),
});

export const EventQueryResponseSchema = z.object({
  events: z.array(EventSchema),
  count: z.number(),
  fromOffset: z.number(),
  nextOffset: z.number(),
  tenantId: z.string(),
  timestamp: z.string(),
});

// ---------------------------------------------------------------------------
// Memory Plane
// ---------------------------------------------------------------------------

export const MemoryTypeSchema = z.enum(['EPISODIC', 'SEMANTIC', 'PROCEDURAL', 'PREFERENCE']);

export const MemoryItemSchema = z.object({
  id: z.string(),
  tenantId: z.string(),
  agentId: z.string(),
  type: MemoryTypeSchema,
  content: z.string(),
  tags: z.array(z.string()),
  salience: z.number(),
  createdAt: z.string(),
  expiresAt: z.string().optional(),
  metadata: z.record(z.string(), z.unknown()),
});

export const MemoryStoreRequestSchema = z.object({
  type: z.enum(['episodic', 'semantic', 'procedural', 'preference']),
  content: z.string().min(1),
  ttlSeconds: z.number().int().positive().optional(),
  tags: z.array(z.string()).optional(),
  salience: z.number().optional(),
  metadata: z.record(z.string(), z.unknown()).optional(),
});

export const MemoryRootListResponseSchema = z.object({
  items: z.array(MemoryItemSchema),
  total: z.number(),
  tenantId: z.string(),
  agentId: z.string(),
  type: z.string(),
  query: z.string(),
  timestamp: z.string(),
});

export const AgentMemorySummaryResponseSchema = z.object({
  agentId: z.string(),
  tenantId: z.string(),
  total: z.number(),
  items: z.array(MemoryItemSchema),
  contextWindowSize: z.number(),
  byType: z.object({
    episodic: z.number(),
    semantic: z.number(),
    procedural: z.number(),
    preference: z.number(),
    other: z.number(),
  }),
  timestamp: z.string(),
});

export const MemoryTierResponseSchema = z.object({
  agentId: z.string(),
  tenantId: z.string(),
  tier: z.string(),
  items: z.array(MemoryItemSchema),
  count: z.number(),
  offset: z.number(),
  limit: z.number(),
  timestamp: z.string(),
});

export const MemorySearchRequestSchema = z.object({
  query: z.string().optional(),
  type: z.union([MemoryTypeSchema, z.enum(['episodic', 'semantic', 'procedural', 'preference'])]).optional(),
  limit: z.number().int().positive().optional(),
});

export const MemorySearchResponseSchema = z.object({
  agentId: z.string(),
  tenantId: z.string(),
  query: z.string(),
  results: z.array(MemoryItemSchema),
  count: z.number(),
  timestamp: z.string(),
});

export const MemoryDeleteResponseSchema = z.object({
  deleted: z.boolean(),
  memoryId: z.string(),
  agentId: z.string(),
  tenantId: z.string(),
  timestamp: z.string(),
});

export const MemoryRetainRequestSchema = z.object({
  retainUntilEpoch: z.number().int().nonnegative().optional(),
  reason: z.string().optional(),
});

export const MemoryRetainResponseSchema = z.object({
  retained: z.boolean(),
  memoryId: z.string(),
  agentId: z.string(),
  tenantId: z.string(),
  reason: z.string(),
  timestamp: z.string(),
});

export const AutonomyLogSchema = z.object({
  id: z.string(),
  actionType: z.string(),
  tenantId: z.string(),
  level: z.enum(['SUGGEST', 'CONFIRM', 'NOTIFY', 'AUTONOMOUS']),
  decision: z.enum(['ALLOWED', 'BLOCKED', 'ADVISORY']),
  confidence: z.number(),
  context: z.record(z.string(), z.unknown()).optional(),
  timestamp: z.string(),
});

export const AutonomyLogsResponseSchema = z.object({
  logs: z.array(AutonomyLogSchema),
  count: z.number(),
  globalOverride: z.string(),
  timestamp: z.string(),
});

export const AutonomyDomainStateSchema = z.object({
  actionType: z.string(),
  tenantId: z.string(),
  currentLevel: z.enum(['SUGGEST', 'CONFIRM', 'NOTIFY', 'AUTONOMOUS']),
  effectiveMaxLevel: z.enum(['SUGGEST', 'CONFIRM', 'NOTIFY', 'AUTONOMOUS']).optional(),
  confidence: z.number().optional(),
  lastActionAt: z.string().optional(),
});

export const AutonomyStateResponseSchema = z.object({
  domain: z.string(),
  state: AutonomyDomainStateSchema,
  timestamp: z.string(),
});

export const AutonomyLevelOverrideRequestSchema = z.object({
  level: z.enum(['SUGGEST', 'CONFIRM', 'NOTIFY', 'AUTONOMOUS']),
  reason: z.string().min(1),
});

export const AutonomyLevelOverrideResponseSchema = z.object({
  globalLevel: z.string(),
  affectedDomains: z.number(),
  timestamp: z.string(),
  reason: z.string(),
});

export const AutonomyLevelStatusSchema = z.object({
  globalOverride: z.string(),
  shutoffActive: z.boolean(),
  domainCount: z.number(),
});

// ---------------------------------------------------------------------------
// Brain
// ---------------------------------------------------------------------------

export const BrainRuntimeStatsSchema = z.object({
  totalRecordsProcessed: z.number(),
  activePatterns: z.number(),
  activeRules: z.number(),
  hotTierRecords: z.number(),
  warmTierRecords: z.number(),
  avgProcessingTimeMs: z.number(),
  uptimeSeconds: z.number(),
  tenantId: z.string(),
  timestamp: z.string(),
});

export const BrainWorkspaceStatusSchema = z.object({
  status: z.string(),
  brainId: z.string(),
  note: z.string(),
  timestamp: z.string(),
});

export const BrainAttentionThresholdsSchema = z.object({
  elevationThreshold: z.number(),
  emergencyThreshold: z.number(),
  salienceThreshold: z.number(),
  totalProcessed: z.number().optional(),
  elevatedCount: z.number().optional(),
  emergencyCount: z.number().optional(),
  elevationRate: z.number().optional(),
  source: z.string().optional(),
  timestamp: z.string(),
});

export const BrainAttentionThresholdUpdateResponseSchema = z.object({
  acknowledged: z.boolean(),
  note: z.string(),
  timestamp: z.string(),
});

export const BrainElevationResultSchema = z.object({
  elevated: z.boolean(),
  emergency: z.boolean(),
  action: z.string(),
  recordId: z.string(),
  reason: z.string(),
  tenantId: z.string(),
  timestamp: z.string(),
});

export const BrainPatternSchema = z.object({
  id: z.string(),
  name: z.string(),
  type: z.string(),
  description: z.string(),
  confidence: z.number(),
  observations: z.number(),
  discoveredAt: z.string(),
  updatedAt: z.string(),
});

export const BrainPatternListResponseSchema = z.object({
  patterns: z.array(BrainPatternSchema),
  count: z.number(),
  limit: z.number(),
  tenantId: z.string(),
  timestamp: z.string(),
});

export const BrainPatternMatchSchema = z.object({
  patternId: z.string().optional(),
  patternName: z.string().optional(),
  score: z.number(),
  confidence: z.number(),
  explanation: z.string(),
});

export const BrainPatternMatchResponseSchema = z.object({
  recordId: z.string(),
  matches: z.array(BrainPatternMatchSchema),
  count: z.number(),
  tenantId: z.string(),
  timestamp: z.string(),
});

export const BrainSalienceResponseSchema = z.object({
  itemId: z.string(),
  salienceScore: z.number(),
  isHigh: z.boolean(),
  isEmergency: z.boolean(),
  priority: z.number(),
  summary: z.string(),
  tenantId: z.string(),
  spotlightedAt: z.string(),
  timestamp: z.string(),
});

export const LearningLastResultSchema = z.object({
  status: z.string(),
  tenantId: z.string().optional(),
  manual: z.boolean().optional(),
  durationMs: z.number().optional(),
  patternsDiscovered: z.number().optional(),
  patternsUpdated: z.number().optional(),
  recordsAnalyzed: z.number().optional(),
  ranAt: z.string().optional(),
});

export const LearningSignalSchema = z.object({
  id: z.string(),
  timestamp: z.string(),
  signalType: z.string(),
  impact: z.number(),
  status: z.enum(['PENDING', 'PROCESSED', 'APPLIED']),
  affectedComponents: z.array(z.string()),
});

export const LearningStatusResponseSchema = z.object({
  running: z.boolean(),
  lastRunTime: z.string(),
  nextScheduledRun: z.string(),
  intervalMinutes: z.number(),
  pendingReviews: z.number(),
  lastResult: LearningLastResultSchema.optional(),
  timestamp: z.string(),
});

// ---------------------------------------------------------------------------
// Context Layer
// ---------------------------------------------------------------------------

export const ContextEntriesSchema = z.record(z.string(), z.unknown());

export const ContextResponseSchema = z.object({
  tenantId: z.string(),
  entries: ContextEntriesSchema,
  count: z.number(),
  version: z.number(),
  requestId: z.string(),
});

export const UpsertContextRequestSchema = z.object({
  entries: ContextEntriesSchema,
});

export const UpsertContextResponseSchema = z.object({
  tenantId: z.string(),
  upserted: z.number(),
  version: z.number(),
  updatedAt: z.string(),
  requestId: z.string(),
});

export const ContextSnapshotSchema = z.object({
  tenantId: z.string(),
  version: z.number(),
  count: z.number(),
  createdAt: z.string(),
  snapshotAt: z.string(),
  entries: ContextEntriesSchema,
  requestId: z.string(),
});

// ---------------------------------------------------------------------------
// Storage Profile — Create / Update requests
// ---------------------------------------------------------------------------

export const CreateStorageProfileRequestSchema = z.object({
  name: z.string().min(1),
  type: z.enum(['postgresql', 'timescaledb', 'clickhouse', 's3', 'gcs', 'azure-blob', 'in-memory']),
  config: z.record(z.string(), z.unknown()),
  isDefault: z.boolean().optional(),
});

export const UpdateStorageProfileRequestSchema = z.object({
  name: z.string().min(1).optional(),
  config: z.record(z.string(), z.unknown()).optional(),
  isDefault: z.boolean().optional(),
  status: z.enum(['active', 'inactive', 'maintenance']).optional(),
});

// ---------------------------------------------------------------------------
// Connectors — Create / Update requests
// ---------------------------------------------------------------------------

export const ConnectorTypeSchema = z.enum([
  'kafka', 'rabbitmq', 'sqs', 'pubsub', 'http-webhook', 'grpc', 'jdbc', 'custom',
]);

export const CreateConnectorRequestSchema = z.object({
  name: z.string().min(1),
  type: ConnectorTypeSchema,
  storageProfileId: z.string(),
  config: z.record(z.string(), z.unknown()),
});

export const UpdateConnectorRequestSchema = z.object({
  name: z.string().min(1).optional(),
  config: z.record(z.string(), z.unknown()).optional(),
  status: z.enum(['active', 'inactive', 'error']).optional(),
});

// ---------------------------------------------------------------------------
// Type exports
// ---------------------------------------------------------------------------

export type Collection = z.infer<typeof CollectionSchema>;
export type PaginatedCollectionResponse = z.infer<typeof PaginatedCollectionResponseSchema>;
export type CollectionEntity = z.infer<typeof CollectionEntitySchema>;
export type CollectionEntityListResponse = z.infer<typeof CollectionEntityListResponseSchema>;
export type PipelineNode = z.infer<typeof PipelineNodeSchema>;
export type PipelineEdge = z.infer<typeof PipelineEdgeSchema>;
export type Pipeline = z.infer<typeof PipelineSchema>;
export type PipelineListResponse = z.infer<typeof PipelineListResponseSchema>;
export type PipelineMutationRequest = z.infer<typeof PipelineMutationRequestSchema>;
export type PipelineOptimisationHint = z.infer<typeof PipelineOptimisationHintSchema>;
export type PipelineOptimisationHintsResponse = z.infer<typeof PipelineOptimisationHintsResponseSchema>;
export type Workflow = z.infer<typeof WorkflowSchema>;
export type PaginatedWorkflowResponse = z.infer<typeof PaginatedWorkflowResponseSchema>;
export type Execution = z.infer<typeof ExecutionSchema>;
export type Checkpoint = z.infer<typeof CheckpointSchema>;
export type CheckpointListResponse = z.infer<typeof CheckpointListResponseSchema>;
export type CheckpointSaveResponse = z.infer<typeof CheckpointSaveResponseSchema>;
export type StorageProfile = z.infer<typeof StorageProfileSchema>;
export type StorageProfileMetrics = z.infer<typeof StorageProfileMetricsSchema>;
export type CollectionCostReportTier = z.infer<typeof CollectionCostReportTierSchema>;
export type CollectionCostReport = z.infer<typeof CollectionCostReportSchema>;
export type MigrateCollectionResult = z.infer<typeof MigrateCollectionResultSchema>;
export type PluginView = z.infer<typeof PluginViewSchema>;
export type PluginListResponse = z.infer<typeof PluginListResponseSchema>;
export type Connector = z.infer<typeof ConnectorSchema>;
export type ReportResult = z.infer<typeof ReportResultSchema>;
export type ReportList = z.infer<typeof ReportListSchema>;
export type AiModel = z.infer<typeof AiModelSchema>;
export type AiModelList = z.infer<typeof AiModelListSchema>;
export type CapabilityRegistryData = z.infer<typeof CapabilityRegistryDataSchema>;
export type CapabilityRegistryEnvelope = z.infer<typeof CapabilityRegistryEnvelopeSchema>;
export type PiiFieldRegistryData = z.infer<typeof PiiFieldRegistryDataSchema>;
export type PiiFieldRegistryEnvelope = z.infer<typeof PiiFieldRegistryEnvelopeSchema>;
export type ComplianceSummaryData = z.infer<typeof ComplianceSummaryDataSchema>;
export type ComplianceSummaryEnvelope = z.infer<typeof ComplianceSummaryEnvelopeSchema>;
export type AgentCapability = z.infer<typeof AgentCapabilitySchema>;
export type AgentCatalogEntry = z.infer<typeof AgentCatalogEntrySchema>;
export type AgentCatalogList = z.infer<typeof AgentCatalogListSchema>;
export type VoiceIntentCatalogEntry = z.infer<typeof VoiceIntentCatalogEntrySchema>;
export type VoiceIntentCatalogData = z.infer<typeof VoiceIntentCatalogDataSchema>;
export type VoiceIntentCatalogEnvelope = z.infer<typeof VoiceIntentCatalogEnvelopeSchema>;
export type VoiceIntentClassificationData = z.infer<typeof VoiceIntentClassificationDataSchema>;
export type VoiceIntentClassificationEnvelope = z.infer<typeof VoiceIntentClassificationEnvelopeSchema>;
export type VoiceIntentPreviewData = z.infer<typeof VoiceIntentPreviewDataSchema>;
export type VoiceIntentPreviewEnvelope = z.infer<typeof VoiceIntentPreviewEnvelopeSchema>;
export type VoiceIntentConfirmationData = z.infer<typeof VoiceIntentConfirmationDataSchema>;
export type VoiceIntentConfirmationEnvelope = z.infer<typeof VoiceIntentConfirmationEnvelopeSchema>;
export type VoiceIntentExecutionData = z.infer<typeof VoiceIntentExecutionDataSchema>;
export type VoiceIntentExecutionEnvelope = z.infer<typeof VoiceIntentExecutionEnvelopeSchema>;
export type AnalyticsQuery = z.infer<typeof AnalyticsQuerySchema>;
export type AnalyticsDataPoint = z.infer<typeof AnalyticsDataPointSchema>;
export type AnalyticsResult = z.infer<typeof AnalyticsResultSchema>;
export type PaginatedAnalyticsResult = z.infer<typeof PaginatedAnalyticsResultSchema>;
export type AnalyticsExplainRequest = z.infer<typeof AnalyticsExplainRequestSchema>;
export type AnalyticsExplainResponse = z.infer<typeof AnalyticsExplainResponseSchema>;
export type AnalyticsSqlQueryRequest = z.infer<typeof AnalyticsSqlQueryRequestSchema>;
export type AnalyticsSqlQueryResponse = z.infer<typeof AnalyticsSqlQueryResponseSchema>;
export type AnalyticsSuggestQuery = z.infer<typeof AnalyticsSuggestQuerySchema>;
export type AnalyticsSuggestResponse = z.infer<typeof AnalyticsSuggestResponseSchema>;
export type AnomalyDetectionRequest = z.infer<typeof AnomalyDetectionRequestSchema>;
export type DetectedAnomaly = z.infer<typeof DetectedAnomalySchema>;
export type SearchResult = z.infer<typeof SearchResultSchema>;
export type SimilarEntityMatch = z.infer<typeof SimilarEntityMatchSchema>;
export type SimilarEntitiesResponse = z.infer<typeof SimilarEntitiesResponseSchema>;
export type CollectionRagRequest = z.infer<typeof CollectionRagRequestSchema>;
export type CollectionRagContextItem = z.infer<typeof CollectionRagContextItemSchema>;
export type CollectionRagResponse = z.infer<typeof CollectionRagResponseSchema>;
export type AnomalyQueryItem = z.infer<typeof AnomalyQueryItemSchema>;
export type AnomalyQueryResponse = z.infer<typeof AnomalyQueryResponseSchema>;
export type DataProductSchemaField = z.infer<typeof DataProductSchemaFieldSchema>;
export type DataProductSchemaInfo = z.infer<typeof DataProductSchemaInfoSchema>;
export type DataProductAccess = z.infer<typeof DataProductAccessSchema>;
export type DataProductSla = z.infer<typeof DataProductSlaSchema>;
export type DataProductQuality = z.infer<typeof DataProductQualitySchema>;
export type DataProductLineageSummary = z.infer<typeof DataProductLineageSummarySchema>;
export type DataProductDescriptor = z.infer<typeof DataProductDescriptorSchema>;
export type PublishDataProductRequest = z.infer<typeof PublishDataProductRequestSchema>;
export type PublishDataProductResponse = z.infer<typeof PublishDataProductResponseSchema>;
export type DataProductListResponse = z.infer<typeof DataProductListResponseSchema>;
export type DataProductSubscriptionRequest = z.infer<typeof DataProductSubscriptionRequestSchema>;
export type DataProductSubscriptionResponse = z.infer<typeof DataProductSubscriptionResponseSchema>;
export type EntityValidationViolation = z.infer<typeof EntityValidationViolationSchema>;
export type EntityValidationResponse = z.infer<typeof EntityValidationResponseSchema>;
export type BatchEntityValidationRequest = z.infer<typeof BatchEntityValidationRequestSchema>;
export type BatchEntityValidationResult = z.infer<typeof BatchEntityValidationResultSchema>;
export type BatchEntityValidationResponse = z.infer<typeof BatchEntityValidationResponseSchema>;
export type Feature = z.infer<typeof FeatureSchema>;
export type LineageNodeContract = z.infer<typeof LineageNodeSchema>;
export type LineageEdgeContract = z.infer<typeof LineageEdgeSchema>;
export type LineageDagResponse = z.infer<typeof LineageDagResponseSchema>;
export type LineageImpactResponse = z.infer<typeof LineageImpactResponseSchema>;
export type ContextEntries = z.infer<typeof ContextEntriesSchema>;
export type ContextResponse = z.infer<typeof ContextResponseSchema>;
export type UpsertContextRequest = z.infer<typeof UpsertContextRequestSchema>;
export type UpsertContextResponse = z.infer<typeof UpsertContextResponseSchema>;
export type ContextSnapshot = z.infer<typeof ContextSnapshotSchema>;
export type Event = z.infer<typeof EventSchema>;
export type AppendEventRequest = z.infer<typeof AppendEventRequestSchema>;
export type AppendEventResponse = z.infer<typeof AppendEventResponseSchema>;
export type EventQueryRequest = z.infer<typeof EventQueryRequestSchema>;
export type EventQueryResponse = z.infer<typeof EventQueryResponseSchema>;
export type MemoryType = z.infer<typeof MemoryTypeSchema>;
export type MemoryItem = z.infer<typeof MemoryItemSchema>;
export type MemoryStoreRequest = z.infer<typeof MemoryStoreRequestSchema>;
export type MemoryRootListResponse = z.infer<typeof MemoryRootListResponseSchema>;
export type AgentMemorySummaryResponse = z.infer<typeof AgentMemorySummaryResponseSchema>;
export type MemoryTierResponse = z.infer<typeof MemoryTierResponseSchema>;
export type MemorySearchRequest = z.infer<typeof MemorySearchRequestSchema>;
export type MemorySearchResponse = z.infer<typeof MemorySearchResponseSchema>;
export type MemoryDeleteResponse = z.infer<typeof MemoryDeleteResponseSchema>;
export type MemoryRetainRequest = z.infer<typeof MemoryRetainRequestSchema>;
export type MemoryRetainResponse = z.infer<typeof MemoryRetainResponseSchema>;
export type AutonomyLog = z.infer<typeof AutonomyLogSchema>;
export type AutonomyLogsResponse = z.infer<typeof AutonomyLogsResponseSchema>;
export type AutonomyDomainState = z.infer<typeof AutonomyDomainStateSchema>;
export type AutonomyStateResponse = z.infer<typeof AutonomyStateResponseSchema>;
export type AutonomyLevelOverrideRequest = z.infer<typeof AutonomyLevelOverrideRequestSchema>;
export type AutonomyLevelOverrideResponse = z.infer<typeof AutonomyLevelOverrideResponseSchema>;
export type AutonomyLevelStatus = z.infer<typeof AutonomyLevelStatusSchema>;
export type BrainRuntimeStats = z.infer<typeof BrainRuntimeStatsSchema>;
export type BrainWorkspaceStatus = z.infer<typeof BrainWorkspaceStatusSchema>;
export type BrainAttentionThresholds = z.infer<typeof BrainAttentionThresholdsSchema>;
export type BrainAttentionThresholdUpdateResponse = z.infer<typeof BrainAttentionThresholdUpdateResponseSchema>;
export type BrainElevationResult = z.infer<typeof BrainElevationResultSchema>;
export type BrainPattern = z.infer<typeof BrainPatternSchema>;
export type BrainPatternListResponse = z.infer<typeof BrainPatternListResponseSchema>;
export type BrainPatternMatch = z.infer<typeof BrainPatternMatchSchema>;
export type BrainPatternMatchResponse = z.infer<typeof BrainPatternMatchResponseSchema>;
export type BrainSalienceResponse = z.infer<typeof BrainSalienceResponseSchema>;
export type LearningLastResult = z.infer<typeof LearningLastResultSchema>;
export type LearningSignal = z.infer<typeof LearningSignalSchema>;
export type LearningStatusResponse = z.infer<typeof LearningStatusResponseSchema>;
export type CreateStorageProfileRequest = z.infer<typeof CreateStorageProfileRequestSchema>;
export type UpdateStorageProfileRequest = z.infer<typeof UpdateStorageProfileRequestSchema>;
export type ConnectorType = z.infer<typeof ConnectorTypeSchema>;
export type CreateConnectorRequest = z.infer<typeof CreateConnectorRequestSchema>;
export type UpdateConnectorRequest = z.infer<typeof UpdateConnectorRequestSchema>;
