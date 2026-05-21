/**
 * Contract-Backed Route Tests
 *
 * Validates that MSW mock responses flowing through UI routes
 * conform to the shared contract schemas. This bridges the gap
 * between "mock-driven" tests and "contract-driven" tests:
 *
 * 1. MSW handlers serve mock data → validated against Zod schemas
 * 2. Pages render using that data → verified via RTL
 * 3. If schemas change, both contract tests AND these route tests break
 *
 * @doc.type test
 * @doc.purpose Contract-backed route integration tests
 * @doc.layer frontend
 */

import { describe, it, expect, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import React from 'react';
import { http, HttpResponse } from 'msw';
import { readFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { server } from '../../mocks/server';
import { TestWrapper } from '../test-utils/wrapper';
import { TEST_TENANT_ID } from '@/__tests__/test-utils/tenants';
import {
  AiModelListSchema,
  AiModelSchema,
  AgentCatalogEntrySchema,
  AgentCatalogListSchema,
  CapabilityRegistryEnvelopeSchema,
  ComplianceSummaryEnvelopeSchema,
  CollectionEntityListResponseSchema,
  CollectionSchema,
  PipelineListResponseSchema,
  PipelineMutationRequestSchema,
  PipelineOptimisationHintsResponseSchema,
  PaginatedCollectionResponseSchema,
  EntityValidationResponseSchema,
  BatchEntityValidationRequestSchema,
  BatchEntityValidationResponseSchema,
  PiiFieldRegistryEnvelopeSchema,
  BrainAttentionThresholdsSchema,
  BrainAttentionThresholdUpdateResponseSchema,
  BrainElevationResultSchema,
  BrainPatternListResponseSchema,
  BrainPatternMatchResponseSchema,
  BrainRuntimeStatsSchema,
  BrainSalienceResponseSchema,
  BrainWorkspaceStatusSchema,
  LearningStatusResponseSchema,
  CreateCollectionRequestSchema,
  UpdateCollectionRequestSchema,
  FeatureSchema,
  WorkflowSchema,
  PaginatedWorkflowResponseSchema,
  CreateWorkflowRequestSchema,
  ExecutionSchema,
  StorageProfileSchema,
  StorageProfileMetricsSchema,
  CollectionCostReportSchema,
  MigrateCollectionResultSchema,
  PluginViewSchema,
  PluginListResponseSchema,
  ConnectorSchema,
  ConnectorTypeSchema,
  ReportListSchema,
  ReportResultSchema,
  AnalyticsQuerySchema,
  AnalyticsResultSchema,
  PaginatedAnalyticsResultSchema,
  AnalyticsExplainRequestSchema,
  AnalyticsExplainResponseSchema,
  AnalyticsSqlQueryRequestSchema,
  AnalyticsSqlQueryResponseSchema,
  AnalyticsSuggestResponseSchema,
  AnomalyDetectionRequestSchema,
  DetectedAnomalySchema,
  SearchResultSchema,
  SimilarEntitiesResponseSchema,
  CollectionContextResponseSchema,
  CollectionRagRequestSchema,
  CollectionRagResponseSchema,
  AnomalyQueryResponseSchema,
  PublishDataProductRequestSchema,
  PublishDataProductResponseSchema,
  DataProductListResponseSchema,
  DataProductSubscriptionRequestSchema,
  DataProductSubscriptionResponseSchema,
  LineageDagResponseSchema,
  LineageImpactResponseSchema,
  CheckpointSchema,
  CheckpointListResponseSchema,
  CheckpointSaveResponseSchema,
  EventSchema,
  AppendEventRequestSchema,
  AppendEventResponseSchema,
  EventQueryRequestSchema,
  EventQueryResponseSchema,
  MemoryItemSchema,
  MemoryStoreRequestSchema,
  MemoryRootListResponseSchema,
  AgentMemorySummaryResponseSchema,
  MemoryTierResponseSchema,
  MemorySearchRequestSchema,
  MemorySearchResponseSchema,
  MemoryDeleteResponseSchema,
  MemoryRetainRequestSchema,
  MemoryRetainResponseSchema,
  LearningSignalSchema,
  AutonomyLevelOverrideRequestSchema,
  AutonomyLevelOverrideResponseSchema,
  AutonomyLevelStatusSchema,
  AutonomyLogsResponseSchema,
  AutonomyStateResponseSchema,
  ContextResponseSchema,
  UpsertContextRequestSchema,
  UpsertContextResponseSchema,
  ContextSnapshotSchema,
  CreateStorageProfileRequestSchema,
  UpdateStorageProfileRequestSchema,
  CreateConnectorRequestSchema,
  UpdateConnectorRequestSchema,
  VoiceIntentCatalogEnvelopeSchema,
  VoiceIntentClassificationEnvelopeSchema,
  VoiceIntentConfirmationEnvelopeSchema,
  VoiceIntentExecutionEnvelopeSchema,
  VoiceIntentPreviewEnvelopeSchema,
} from '../../contracts/schemas';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const canonicalOpenApi = readFileSync(
  path.resolve(__dirname, '../../../../../contracts/openapi/data-cloud.yaml'),
  'utf8',
);
const actionPlaneOpenApi = readFileSync(
  path.resolve(__dirname, '../../../../../contracts/openapi/action-plane.yaml'),
  'utf8',
);

// ---------------------------------------------------------------------------
// Test: Collections route with contract-validated data
// ---------------------------------------------------------------------------

describe('Contract-Backed Route Tests', () => {
  describe('Collections Page — contract compliance', () => {
    // Contract-validated seed data
    const contractCollection = CollectionSchema.parse({
      id: 'contract-col-1',
      name: 'Contract Test Collection',
      description: 'Verified against Zod contract',
      schemaType: 'entity',
      status: 'active',
      entityCount: 42,
      schema: { fields: [] },
      tags: ['contract-test'],
      createdAt: '2024-06-01T00:00:00Z',
      updatedAt: '2024-06-01T00:00:00Z',
      createdBy: 'contract-runner',
    });

    const contractPaginatedResponse = PaginatedCollectionResponseSchema.parse({
      items: [contractCollection],
      total: 1,
      page: 1,
      pageSize: 20,
      hasMore: false,
    });

    it('should render collection data that passes contract validation', async () => {
      // Override MSW with entity-format response at the real backend route.
      // collectionsApi.list() now calls /api/v1/entities/dc_collections (Option A
      // mapping, DATA_CLOUD_REMEDIATION_IMPLEMENTATION_PLAN Phase 2).
      server.use(
        http.get('/api/v1/entities/dc_collections', () =>
          HttpResponse.json({
            entities: [
              {
                id: contractCollection.id,
                collection: 'dc_collections',
                data: {
                  name: contractCollection.name,
                  description: contractCollection.description,
                  schemaType: contractCollection.schemaType,
                  status: contractCollection.status,
                  entityCount: contractCollection.entityCount,
                  schema: contractCollection.schema,
                  tags: contractCollection.tags,
                  createdBy: contractCollection.createdBy,
                },
                version: 1,
                createdAt: contractCollection.createdAt,
                updatedAt: contractCollection.updatedAt,
              },
            ],
            count: 1,
            tenantId: TEST_TENANT_ID,
            timestamp: new Date().toISOString(),
          })
        )
      );

      // Dynamically import to avoid circular/static issues
      const { default: DataExplorer } = await import(
        '../../pages/DataExplorer'
      );

      render(<DataExplorer />, { wrapper: TestWrapper });

      await waitFor(
        () => {
          expect(
            screen.getByText(/Contract Test Collection/i)
          ).toBeInTheDocument();
        },
        { timeout: 3000 }
      );

      // The response was .parse()'d — if schema drifts, this test fails at
      // the parse() call above, not silently at render time.
    });

    it('contract schema rejects response missing required fields', () => {
      const incomplete = {
        id: 'col-bad',
        name: 'Incomplete Collection',
        // missing description, schemaType, status, etc.
      };

      const result = CollectionSchema.safeParse(incomplete);
      expect(result.success).toBe(false);
    });

    it('canonical dc_collections entity payloads pass contract validation', () => {
      const response = CollectionEntityListResponseSchema.parse({
        entities: [
          {
            id: 'contract-col-1',
            data: {
              name: 'Contract Test Collection',
              description: 'Verified against the raw entity wrapper contract',
              schemaType: 'entity',
              status: 'active',
              entityCount: 42,
              schema: {
                fields: [{ name: 'id', type: 'string', required: true }],
              },
              tags: ['contract-test'],
              createdBy: 'contract-runner',
            },
            createdAt: '2026-04-15T07:00:00Z',
            updatedAt: '2026-04-15T07:05:00Z',
          },
        ],
        count: 1,
      });

      expect(response.entities[0]?.data.name).toBe('Contract Test Collection');
      expect(response.count).toBe(1);
    });

    it('canonical pipeline payloads pass contract validation', () => {
      const response = PipelineListResponseSchema.parse({
        tenantId: 'tenant-a',
        pipelines: [
          {
            id: 'wf-1',
            tenantId: 'tenant-a',
            name: 'Daily Sync',
            description: 'Launcher pipeline registry payload',
            status: 'active',
            nodes: [
              {
                id: 'extract',
                type: 'source',
                label: 'Extract',
                position: { x: 0, y: 0 },
                data: {},
              },
            ],
            edges: [
              {
                id: 'edge-1',
                source: 'extract',
                target: 'load',
                label: 'flows-to',
              },
            ],
            schedule: '0 0 * * *',
            tags: ['daily', 'sync'],
            createdAt: '2026-04-14T08:00:00Z',
            updatedAt: '2026-04-14T09:00:00Z',
            createdBy: 'contract-runner',
            lastExecutedAt: '2026-04-14T09:30:00Z',
          },
        ],
        count: 1,
        timestamp: '2026-04-14T09:31:00Z',
      });

      expect(response.pipelines[0]?.name).toBe('Daily Sync');
      expect(response.count).toBe(1);
    });

    it('canonical pipeline optimisation hint payloads pass contract validation', () => {
      const response = PipelineOptimisationHintsResponseSchema.parse({
        pipelineId: 'wf-contract-1',
        hints: [
          {
            type: 'performance',
            title: 'Reduce repeated enrichment lookups',
            description: 'Cache repeated upstream calls to reduce end-to-end latency.',
            confidence: 0.92,
            impact: 'high',
            fallback: false,
          },
        ],
        generatedAt: '2026-04-14T10:35:00Z',
      });

      expect(response.hints[0]?.type).toBe('performance');
      expect(response.pipelineId).toBe('wf-contract-1');
    });
  });

  describe('Data Fabric — contract compliance', () => {
    it('storage profile data passes contract validation', () => {
      const profile = StorageProfileSchema.parse({
        id: 'sp-contract-1',
        name: 'Contract RocksDB',
        type: 'rocksdb',
        isDefault: true,
        status: 'healthy',
        config: { path: '/data/rocksdb' },
        createdAt: '2024-01-01T00:00:00Z',
        updatedAt: '2024-06-01T00:00:00Z',
      });

      expect(profile.id).toBe('sp-contract-1');
      expect(profile.isDefault).toBe(true);
    });

    it('connector data passes contract validation', () => {
      const connector = ConnectorSchema.parse({
        id: 'dc-contract-1',
        name: 'Contract JDBC',
        type: 'jdbc',
        storageProfileId: 'sp-contract-1',
        status: 'active',
        config: { url: 'jdbc:postgresql://host/db' },
        createdAt: '2024-02-01T00:00:00Z',
        updatedAt: '2024-06-01T00:00:00Z',
      });

      expect(connector.type).toBe('jdbc');
    });

    it('storage profile metrics data pass contract validation', () => {
      const metrics = StorageProfileMetricsSchema.parse({
        storageUsedBytes: 5_242_880,
        storageTotalBytes: 10_485_760,
        readOpsPerSec: 32,
        writeOpsPerSec: 14,
        latencyP99Ms: 18.7,
        lastUpdated: '2026-04-14T12:15:00Z',
      });

      expect(metrics.latencyP99Ms).toBe(18.7);
      expect(metrics.storageUsedBytes).toBeLessThan(metrics.storageTotalBytes);
    });

    it('collection cost report and tier migration payloads pass contract validation', () => {
      const costReport = CollectionCostReportSchema.parse({
        collectionId: 'orders',
        tenantId: 'tenant-alpha',
        totalSizeGb: 12,
        totalCostDccPerDay: 24,
        currency: 'DCC',
        tiers: [
          {
            tier: 'HOT',
            sizeGb: 12,
            costDccPerDay: 24,
            backend: 'postgres',
          },
        ],
        note: 'Derived from canonical launcher cost report',
      });

      const migration = MigrateCollectionResultSchema.parse({
        collection: 'orders',
        targetTier: 'WARM',
        status: 'SCHEDULED',
        eventsMigrated: 100,
      });

      expect(costReport.tiers[0]?.tier).toBe('HOT');
      expect(costReport.totalCostDccPerDay).toBe(24);
      expect(migration.targetTier).toBe('WARM');
      expect(migration.eventsMigrated).toBe(100);
    });
  });

  describe('Reports and models contracts', () => {
    it('report generation and cached report payloads pass contract validation', () => {
      const generated = ReportResultSchema.parse({
        reportId: 'report-123',
        reportName: 'daily-entity-summary',
        format: 'JSON',
        rowCount: 2,
        contentType: 'application/json',
        executionTimeMs: 42,
        generatedAt: '2026-04-15T10:00:00Z',
        rows: [
          { collection: 'orders', count: 12 },
          { collection: 'customers', count: 4 },
        ],
      });

      const cached = ReportListSchema.parse({
        reports: {
          'report-123': 'daily-entity-summary',
          'report-456': 'compliance-overview',
        },
        count: 2,
      });

      expect(generated.reportId).toBe('report-123');
      expect(cached.count).toBe(2);
    });

    it('model registry payloads pass contract validation', () => {
      const model = AiModelSchema.parse({
        id: '3c6cdb6d-14d4-4d8b-8ec8-5e6e1d26ab0c',
        tenantId: 'tenant-alpha',
        name: 'quality-scorer',
        version: '2.1.0',
        framework: 'xgboost',
        deploymentStatus: 'PRODUCTION',
        trainingMetrics: { auc: 0.94 },
        createdAt: '2026-04-10T09:00:00Z',
        updatedAt: '2026-04-15T09:00:00Z',
        deployedAt: '2026-04-15T09:30:00Z',
      });

      const listing = AiModelListSchema.parse({
        models: [model],
        count: 1,
      });

      expect(listing.models[0]?.name).toBe('quality-scorer');
      expect(listing.count).toBe(1);
    });

    it('feature-store payloads pass contract validation', () => {
      const feature = FeatureSchema.parse({
        id: 'feature-1',
        name: 'engagement_score',
        description: 'Computed engagement score',
        dataType: 'FLOAT',
        version: '1.0.0',
        tags: ['ml', 'customer'],
        createdAt: '2026-04-15T08:00:00Z',
        updatedAt: '2026-04-15T08:10:00Z',
      });

      expect(feature.name).toBe('engagement_score');
      expect(feature.tags).toContain('ml');
    });

    it('plugin payloads pass contract validation', () => {
      const plugin = PluginViewSchema.parse({
        id: 'plugin-orders',
        displayName: 'Orders Connector',
        version: '1.2.3',
        status: 'enabled',
        supportedRecordTypes: ['Entity', 'Metric'],
      });

      const listing = PluginListResponseSchema.parse({
        plugins: [plugin],
        total: 1,
      });

      expect(listing.plugins[0]?.displayName).toBe('Orders Connector');
      expect(listing.total).toBe(1);
    });
  });

  describe('Runtime truth and voice contracts', () => {
    it('runtime surface registry envelope passes contract validation', () => {
      const envelope = CapabilityRegistryEnvelopeSchema.parse({
        data: {
          capabilities: {
            analytics: 'ACTIVE',
            trino: 'NOT_CONFIGURED',
            voice: 'DEGRADED',
          },
          generatedAt: '2026-04-15T10:15:00Z',
        },
        meta: {
          requestId: 'req-123',
          tenantId: 'tenant-alpha',
          timestamp: '2026-04-15T10:15:00Z',
          apiVersion: 'v1',
        },
      });

      expect(envelope.data.capabilities.analytics).toBe('ACTIVE');
    });

    it('governance envelopes pass contract validation', () => {
      const piiRegistry = PiiFieldRegistryEnvelopeSchema.parse({
        data: {
          globalFields: ['email'],
          tenantFields: ['ssn'],
          effectiveCount: 2,
        },
        meta: {
          requestId: 'req-pii',
          tenantId: 'tenant-alpha',
          timestamp: '2026-04-15T10:16:00Z',
          apiVersion: 'v1',
        },
      });

      const complianceSummary = ComplianceSummaryEnvelopeSchema.parse({
        data: {
          tenantId: 'tenant-alpha',
          collectionsTotal: 12,
          collectionsClassified: 9,
          collectionsUnclassified: 3,
          piiFieldsRegistered: 2,
          legalHoldsActive: 1,
          retentionExpirationsIn30Days: 2,
          lastAuditAt: '2026-04-15T08:00:00Z',
          auditEventsIn30Days: 18,
          authFailuresIn30Days: 1,
          redactionsIn30Days: 4,
          purgesIn30Days: 1,
          recentAuditEvents: [
            {
              id: 'evt-1',
              timestamp: '2026-04-15T08:00:00Z',
              userId: 'auditor-1',
              action: 'PII_SCAN',
            },
          ],
          complianceStatus: 'REVIEW_REQUIRED',
          generatedAt: '2026-04-15T08:05:00Z',
        },
        meta: {
          requestId: 'req-summary',
          tenantId: 'tenant-alpha',
          timestamp: '2026-04-15T08:05:00Z',
          apiVersion: 'v1',
        },
      });

      expect(piiRegistry.data.effectiveCount).toBe(2);
      expect(complianceSummary.data.complianceStatus).toBe('REVIEW_REQUIRED');
    });

    it('voice catalog and classify envelopes pass contract validation', () => {
      const catalog = VoiceIntentCatalogEnvelopeSchema.parse({
        data: {
          intents: [
            {
              name: 'query_entities',
              description: 'Query entities in a collection',
              httpMethod: 'GET',
              pathTemplate: '/api/v1/entities/:collection/search',
              requiredParams: ['collection'],
              optionalParams: ['query'],
              sensitivity: 'LOW',
            },
          ],
          count: 1,
        },
        meta: {
          requestId: 'req-voice-catalog',
          tenantId: 'tenant-alpha',
          timestamp: '2026-04-15T10:20:00Z',
          apiVersion: 'v1',
        },
      });

      const classified = VoiceIntentClassificationEnvelopeSchema.parse({
        data: {
          intent: 'query_entities',
          description: 'Query entities in a collection',
          confidence: 0.82,
          extractedParams: { collection: 'orders' },
          matched: true,
          requiresConfirmation: false,
        },
        ai: {
          confidence: 0.82,
          model: 'voice-classifier',
          reasons: ['llm'],
          fallback: false,
        },
        meta: {
          requestId: 'req-voice-classify',
          tenantId: 'tenant-alpha',
          timestamp: '2026-04-15T10:21:00Z',
          apiVersion: 'v1',
        },
      });

      const unmatched = VoiceIntentPreviewEnvelopeSchema.parse({
        data: {
          intentName: '',
          executed: false,
          matched: false,
          confidence: 0,
        },
        meta: {
          requestId: 'req-voice-preview',
          tenantId: 'tenant-alpha',
          timestamp: '2026-04-15T10:22:00Z',
          apiVersion: 'v1',
        },
      });

      expect(catalog.data.count).toBe(1);
      expect(classified.data.intent).toBe('query_entities');
      expect(unmatched.data.executed).toBe(false);
    });

    it('agent catalog payloads pass contract validation', () => {
      const entry = AgentCatalogEntrySchema.parse({
        id: 'catalog-1',
        name: 'Catalog Agent',
        description: 'Launcher-exposed agent',
        version: '1.0.0',
        tenantId: 'tenant-a',
        status: 'ACTIVE',
        capabilities: [
          {
            id: 'cap-1',
            name: 'Search',
            description: 'Searches catalog data',
            version: '1.0.0',
          },
        ],
        registeredAt: '2026-04-15T10:00:00Z',
        updatedAt: '2026-04-15T10:05:00Z',
      });

      const listing = AgentCatalogListSchema.parse([entry]);

      expect(listing[0]?.name).toBe('Catalog Agent');
      expect(listing[0]?.capabilities?.[0]?.name).toBe('Search');
    });

    it('entity search results pass contract validation', () => {
      const results = [
        SearchResultSchema.parse({
          entityId: 'entity-123',
          collectionId: 'orders',
          score: 0.98,
          highlights: {
            description: ['matching order for premium customer'],
          },
          data: {
            orderId: 'order-123',
            customerId: 'cust-9',
            total: 149.5,
          },
        }),
      ];

      expect(results[0]?.collectionId).toBe('orders');
      expect(results[0]?.highlights?.description?.[0]).toContain('premium');
    });

    it('voice confirmation and execution envelopes pass contract validation', () => {
      const confirmation = VoiceIntentConfirmationEnvelopeSchema.parse({
        data: {
          requiresConfirmation: true,
          resolvedIntent: 'delete_entity',
          resolvedPath: '/api/v1/entities/orders/entity-42',
          confidence: 0.41,
          message: 'Low confidence resolution — please confirm',
        },
        ai: {
          confidence: 0.41,
          model: 'voice-classifier',
          reasons: ['keyword-heuristic'],
          fallback: true,
        },
        meta: {
          requestId: 'req-voice-confirm',
          tenantId: 'tenant-alpha',
          timestamp: '2026-04-15T10:23:00Z',
          apiVersion: 'v1',
        },
      });

      const execution = VoiceIntentExecutionEnvelopeSchema.parse({
        data: {
          executed: true,
          intentName: 'list_models',
          httpMethod: 'GET',
          resolvedPath: '/api/v1/models',
          parameters: {},
          sensitivity: 'LOW',
          description: 'List registered models',
          speechSummary: 'Listing ML models.',
        },
        ai: {
          confidence: 0.88,
          model: 'voice-gateway',
          reasons: ['llm-classified'],
          fallback: false,
        },
        meta: {
          requestId: 'req-voice-exec',
          tenantId: 'tenant-alpha',
          timestamp: '2026-04-15T10:24:00Z',
          apiVersion: 'v1',
        },
      });

      expect(confirmation.data.resolvedIntent).toBe('delete_entity');
      expect(execution.data.intentName).toBe('list_models');
    });
  });

  describe('Analytics contracts', () => {
    it('analytics query payload passes contract validation', () => {
      const query = AnalyticsQuerySchema.parse({
        metric: 'entity_count',
        dimensions: ['collection', 'tenant'],
        filters: { tenantId: TEST_TENANT_ID, status: 'active' },
        startTime: '2026-04-01T00:00:00Z',
        endTime: '2026-04-14T23:59:59Z',
        granularity: 'day',
        limit: 30,
      });

      expect(query.metric).toBe('entity_count');
      expect(query.granularity).toBe('day');
    });

    it('analytics result payloads pass contract validation', () => {
      const result = AnalyticsResultSchema.parse({
        metric: 'query_latency_ms',
        unit: 'ms',
        dataPoints: [
          {
            timestamp: '2026-04-14T12:00:00Z',
            value: 123.4,
            dimensions: { queryType: 'analytics' },
          },
        ],
        aggregation: {
          min: 123.4,
          max: 123.4,
          avg: 123.4,
          sum: 123.4,
          count: 1,
        },
        queryDurationMs: 18,
      });

      const paginated = PaginatedAnalyticsResultSchema.parse({
        results: [result],
        total: 1,
        page: 1,
        pageSize: 25,
        hasMore: false,
      });

      expect(paginated.results[0]?.metric).toBe('query_latency_ms');
      expect(paginated.results[0]?.aggregation.count).toBe(1);
    });

    it('analytics query schema rejects invalid metric and granularity', () => {
      const invalid = AnalyticsQuerySchema.safeParse({
        metric: '',
        startTime: '2026-04-01T00:00:00Z',
        endTime: '2026-04-14T23:59:59Z',
        granularity: 'quarter',
      });

      expect(invalid.success).toBe(false);
    });

    it('canonical analytics SQL query and suggest payloads pass contract validation', () => {
      const request = AnalyticsSqlQueryRequestSchema.parse({
        query: 'SELECT COUNT(*) AS total FROM events',
        parameters: { limit: 10 },
      });

      const response = AnalyticsSqlQueryResponseSchema.parse({
        queryId: 'query-1',
        queryType: 'ANALYTICS',
        rowCount: 1,
        columnCount: 1,
        rows: [{ total: 42 }],
        executionTimeMs: 18,
        optimized: true,
        timestamp: '2026-04-14T13:05:00Z',
      });

      const federated = AnalyticsSqlQueryResponseSchema.parse({
        queryId: 'query-fed-1',
        queryType: 'FEDERATED_FALLBACK',
        rowCount: 1,
        columnCount: 1,
        rows: [{ region: 'global' }],
        executionTimeMs: 32,
        optimized: true,
        timestamp: '2026-04-14T13:06:00Z',
        warning: 'Trino not configured — query executed via local analytics engine.',
      });

      const suggest = AnalyticsSuggestResponseSchema.parse({
        data: {
          queries: [
            {
              name: 'Cache repeated revenue queries',
              template: 'SELECT day, SUM(total) FROM revenue GROUP BY day',
              explanation: 'Frequent revenue lookups can be served faster from a cached aggregate.',
            },
          ],
        },
        ai: {
          confidence: 0.88,
          fallback: false,
        },
      });

      expect(request.query).toContain('COUNT');
      expect(response.rowCount).toBe(1);
      expect(federated.warning).toContain('Trino');
      expect(suggest.data.queries[0]?.name).toContain('Cache');
    });

    it('canonical analytics explain payloads pass contract validation', () => {
      const request = AnalyticsExplainRequestSchema.parse({
        query: 'SELECT region, COUNT(*) FROM orders GROUP BY region',
        parameters: { tenantId: 'tenant-alpha' },
      });

      const response = AnalyticsExplainResponseSchema.parse({
        queryId: 'explain-1',
        queryType: 'SQL',
        dataSources: ['orders'],
        estimatedCost: 12.75,
        optimized: true,
        explain: true,
        timestamp: '2026-04-15T13:10:00Z',
      });

      expect(request.query).toContain('GROUP BY');
      expect(response.explain).toBe(true);
      expect(response.dataSources[0]).toBe('orders');
    });

    it('canonical anomaly detection payloads pass contract validation', () => {
      const request = AnomalyDetectionRequestSchema.parse({
        collectionName: 'orders',
        timeRange: {
          start: '2026-04-01T00:00:00Z',
          end: '2026-04-15T00:00:00Z',
        },
        metrics: ['order_count', 'revenue'],
      });

      const anomaly = DetectedAnomalySchema.parse({
        id: 'anomaly-1',
        type: 'spike',
        severity: 'warning',
        metric: 'order_count',
        timestamp: '2026-04-15T09:00:00Z',
        value: 1420,
        expectedValue: 940,
        deviation: 480,
        description: 'Order volume exceeded the rolling seven-day baseline.',
        suggestedAction: 'Inspect recent campaign launches and upstream retries.',
      });

      expect(request.collectionName).toBe('orders');
      expect(request.metrics).toContain('revenue');
      expect(anomaly.type).toBe('spike');
      expect(anomaly.suggestedAction).toContain('campaign');
    });

    it('semantic similarity, RAG, anomaly query, and data product payloads pass contract validation', () => {
      const similar = SimilarEntitiesResponseSchema.parse({
        collection: 'orders',
        entityId: 'order-1',
        matches: [
          {
            id: 'order-2',
            collection: 'orders',
            score: 0.92,
            data: { status: 'processing', total: 149.99 },
          },
        ],
        count: 1,
        requestId: 'semantic-req-1',
      });

      const ragRequest = CollectionRagRequestSchema.parse({
        question: 'Why did order processing spike this morning?',
        k: 3,
      });

      const ragResponse = CollectionRagResponseSchema.parse({
        collection: 'orders',
        question: ragRequest.question,
        answer: 'Grounded answer for the question based on the three closest matching orders.',
        context: [
          {
            id: 'order-9',
            score: 0.89,
            data: { channel: 'campaign', retryCount: 2 },
            excerpt: 'Orders from the campaign channel retried after an upstream timeout.',
          },
        ],
        requestId: 'rag-req-1',
      });

      const anomalyQuery = AnomalyQueryResponseSchema.parse({
        tenantId: 'tenant-alpha',
        collection: 'orders',
        since: '2026-04-14T00:00:00Z',
        count: 1,
        anomalies: [
          {
            eventId: 'evt-anomaly-1',
            eventType: 'ANOMALY_DETECTED',
            timestamp: '2026-04-15T09:00:00Z',
            collection: 'orders',
            tenantId: 'tenant-alpha',
            anomalyPayload: '{"metric":"order_count","score":3.7}',
          },
        ],
      });

      const publishRequest = PublishDataProductRequestSchema.parse({
        collection: 'orders',
        name: 'Orders Gold Product',
        description: 'Curated order data for downstream analytics.',
        governance: { owner: 'finance' },
        access: {
          visibility: 'PRIVATE',
          allowedSubscribers: ['tenant-alpha-consumer'],
        },
        sla: {
          freshnessSeconds: 300,
          completenessTarget: 0.98,
          accuracyTarget: 0.995,
        },
      });

      const publishResponse = PublishDataProductResponseSchema.parse({
        productId: 'product-1',
        collection: publishRequest.collection,
        name: publishRequest.name,
        descriptor: {
          id: 'product-1',
          tenantId: 'tenant-alpha',
          name: publishRequest.name,
          collection: publishRequest.collection,
          description: publishRequest.description,
          publishedAt: '2026-04-15T13:20:00Z',
          schema: {
            fields: [
              { name: 'order_id', types: ['string'] },
              { name: 'total', types: ['number'] },
            ],
            sampleCount: 25,
          },
          governance: { owner: 'finance' },
          access: {
            visibility: 'PRIVATE',
            allowedSubscribers: ['tenant-alpha-consumer'],
          },
          sla: {
            freshnessSeconds: 300,
            completenessTarget: 0.98,
            accuracyTarget: 0.995,
          },
          quality: {
            completeness: 0.99,
            freshnessLagSeconds: 180,
            sampleSize: 25,
            measuredAt: '2026-04-15T13:19:00Z',
          },
          qualityStatus: 'HEALTHY',
          lineage: {
            upstream: ['orders_raw'],
            downstream: ['orders_dashboard'],
            impactLevel: 'LOW',
          },
        },
        requestId: 'data-product-req-1',
        publishedAt: '2026-04-15T13:20:00Z',
      });

      const dataProducts = DataProductListResponseSchema.parse({
        items: [publishResponse.descriptor],
        count: 1,
        requestId: 'data-product-req-2',
        timestamp: '2026-04-15T13:21:00Z',
      });

      const subscribeRequest = DataProductSubscriptionRequestSchema.parse({
        consumerId: 'tenant-alpha-consumer',
      });

      const subscribeResponse = DataProductSubscriptionResponseSchema.parse({
        subscriptionId: 'sub-1',
        productId: publishResponse.productId,
        consumerId: subscribeRequest.consumerId,
        status: 'ACTIVE',
        requestId: 'data-product-req-3',
      });

      expect(similar.matches[0]?.id).toBe('order-2');
      expect(ragResponse.context[0]?.excerpt).toContain('campaign');
      expect(anomalyQuery.anomalies[0]?.eventType).toBe('ANOMALY_DETECTED');
      expect(dataProducts.items[0]?.qualityStatus).toBe('HEALTHY');
      expect(subscribeResponse.status).toBe('ACTIVE');
    });
  });

  describe('Context layer contracts', () => {
    it('context layer request and response payloads pass contract validation', () => {
      const context = ContextResponseSchema.parse({
        tenantId: 'tenant-alpha',
        entries: {
          'feature.dark-mode': true,
          locale: 'en-US',
        },
        count: 2,
        version: 8,
        requestId: 'ctx-req-1',
      });

      const upsertRequest = UpsertContextRequestSchema.parse({
        entries: {
          timezone: 'UTC',
          'preferences.compact': false,
        },
      });

      const upsertResponse = UpsertContextResponseSchema.parse({
        tenantId: 'tenant-alpha',
        upserted: 2,
        version: 9,
        updatedAt: '2026-04-15T13:15:00Z',
        requestId: 'ctx-req-2',
      });

      const snapshot = ContextSnapshotSchema.parse({
        tenantId: 'tenant-alpha',
        version: 9,
        count: 3,
        createdAt: '2026-04-15T12:00:00Z',
        snapshotAt: '2026-04-15T13:16:00Z',
        entries: {
          locale: 'en-US',
          timezone: 'UTC',
          'feature.dark-mode': true,
        },
        requestId: 'ctx-req-3',
      });

      expect(context.entries.locale).toBe('en-US');
      expect(upsertRequest.entries.timezone).toBe('UTC');
      expect(upsertResponse.version).toBe(9);
      expect(snapshot.count).toBe(3);
    });

    it('collection context payloads pass contract validation', () => {
      const response = CollectionContextResponseSchema.parse({
        collection: 'orders',
        tenantId: 'tenant-alpha',
        requestId: 'ctx-orders-1',
        generatedAt: '2026-04-18T14:00:00Z',
        generationTimeMs: 18,
        schema: {
          fields: [
            { name: 'orderId', type: 'string', required: true },
            { name: 'email', type: 'string', required: false },
          ],
        },
        lineage: {
          upstream: ['raw_orders'],
          downstream: ['invoice_snapshots'],
        },
        governance: {
          retentionTier: 'compliance',
          complianceStatus: 'active',
          piiFields: ['email'],
        },
        freshness: {
          sampledAt: '2026-04-18T14:00:00Z',
          lastEntityUpdatedAt: '2026-04-18T13:59:00Z',
        },
        statisticalProfile: {
          entityCount: 42,
          sampleSize: 10,
          nullRates: { email: 0.1, orderId: 0 },
          topValues: {
            email: [{ value: 'null', count: 1 }],
            orderId: [{ value: 'order-1', count: 2 }],
          },
        },
        relationshipDepth: 3,
        relationships: [
          { id: 'edge-1', source: 'orders', target: 'customers', type: 'BELONGS_TO', depth: 1 },
        ],
      });

      expect(response.collection).toBe('orders');
      expect(response.schema.fields[0]?.name).toBe('orderId');
      expect(response.lineage.upstream).toContain('raw_orders');
      expect(response.relationshipDepth).toBe(3);
    });
  });

  describe('Lineage contracts', () => {
    it('canonical lineage graph and impact payloads pass contract validation', () => {
      const lineageGraph = LineageDagResponseSchema.parse({
        collection: 'orders',
        tenantId: 'tenant-alpha',
        direction: 'BOTH',
        timestamp: '2026-04-15T12:00:00Z',
        dag: {
          nodes: [
            { id: 'orders', type: 'DATASET', name: 'orders', role: 'root', metadata: {} },
            { id: 'orders_raw', type: 'DATASET', name: 'orders_raw', role: 'upstream', metadata: {} },
          ],
          edges: [
            { source: 'orders_raw', target: 'orders', type: 'DERIVES_FROM' },
          ],
        },
        upstreamCount: 1,
        downstreamCount: 0,
      });

      const lineageImpact = LineageImpactResponseSchema.parse({
        collection: 'orders',
        tenantId: 'tenant-alpha',
        impactLevel: 'MEDIUM',
        affectedCount: 1,
        affectedCollections: ['orders_dashboard'],
        timestamp: '2026-04-15T12:01:00Z',
      });

      expect(lineageGraph.dag.nodes[0]?.id).toBe('orders');
      expect(lineageImpact.affectedCollections[0]).toBe('orders_dashboard');
    });
  });

  describe('Request payload contracts', () => {
    it('collection and workflow request payloads pass contract validation', () => {
      const createCollection = CreateCollectionRequestSchema.parse({
        name: 'Customer Profiles',
        description: 'Canonical customer entity collection',
        schemaType: 'entity',
        schema: {
          fields: [
            { name: 'id', type: 'string', required: true },
            { name: 'email', type: 'string', required: false },
          ],
        },
        tags: ['crm', 'gold'],
      });

      const updateCollection = UpdateCollectionRequestSchema.parse({
        description: 'Updated collection description',
        status: 'active',
      });

      const createWorkflow = CreateWorkflowRequestSchema.parse({
        name: 'Daily Sync',
        description: 'Synchronize upstream customer data each day',
        definition: {
          version: 1,
          steps: [{ id: 'extract', type: 'source' }, { id: 'load', type: 'sink' }],
        },
      });

      const mutatePipeline = PipelineMutationRequestSchema.parse({
        name: 'Daily Sync',
        description: 'Synchronize upstream customer data each day',
        nodes: [{ id: 'extract', type: 'source', label: 'Extract', position: { x: 0, y: 0 }, data: {} }],
        edges: [],
        tags: ['daily'],
      });

      expect(createCollection.tags).toContain('crm');
      expect(updateCollection.status).toBe('active');
      expect(createWorkflow.name).toBe('Daily Sync');
      expect(mutatePipeline.name).toBe('Daily Sync');
    });

    it('entity validation request and response payloads pass contract validation', () => {
      const validationResponse = EntityValidationResponseSchema.parse({
        valid: false,
        violations: [
          {
            field: 'email',
            message: 'Must be a valid email address.',
            code: 'INVALID_EMAIL',
          },
        ],
      });

      const batchValidationRequest = BatchEntityValidationRequestSchema.parse({
        entities: [
          { id: '1', email: 'invalid' },
          { id: '2', email: 'valid@example.com' },
        ],
      });

      const batchValidationResponse = BatchEntityValidationResponseSchema.parse({
        allValid: false,
        results: [
          {
            index: 0,
            valid: false,
            violations: [
              {
                field: 'email',
                message: 'Must be a valid email address.',
                code: 'INVALID_EMAIL',
              },
            ],
          },
          {
            index: 1,
            valid: true,
            violations: [],
          },
        ],
      });

      expect(validationResponse.valid).toBe(false);
      expect(validationResponse.violations[0]?.field).toBe('email');
      expect(batchValidationRequest.entities).toHaveLength(2);
      expect(batchValidationResponse.results[1]?.valid).toBe(true);
    });

    it('storage profile and connector request payloads pass contract validation', () => {
      const createStorageProfile = CreateStorageProfileRequestSchema.parse({
        name: 'Primary Blob Tier',
        type: 'azure-blob',
        config: { container: 'raw-zone', accountName: 'ghatanadata' },
        isDefault: true,
      });

      const updateStorageProfile = UpdateStorageProfileRequestSchema.parse({
        status: 'maintenance',
        config: { container: 'warm-zone' },
      });

      const createConnector = CreateConnectorRequestSchema.parse({
        name: 'Orders JDBC',
        type: ConnectorTypeSchema.parse('jdbc'),
        storageProfileId: 'sp-001',
        config: { url: 'jdbc:postgresql://orders.internal/orders' },
      });

      const updateConnector = UpdateConnectorRequestSchema.parse({
        status: 'inactive',
        name: 'Orders JDBC Secondary',
      });

      expect(createStorageProfile.type).toBe('azure-blob');
      expect(updateStorageProfile.status).toBe('maintenance');
      expect(createConnector.type).toBe('jdbc');
      expect(updateConnector.status).toBe('inactive');
    });

    it('request schemas reject invalid names, types, and statuses', () => {
      const invalidCollection = CreateCollectionRequestSchema.safeParse({
        name: '',
        description: 'Missing valid name',
        schemaType: 'entity',
        schema: {},
      });

      const invalidStorageProfile = CreateStorageProfileRequestSchema.safeParse({
        name: 'Bad Profile',
        type: 'filesystem',
        config: {},
      });

      const invalidConnectorUpdate = UpdateConnectorRequestSchema.safeParse({
        status: 'paused',
      });

      expect(invalidCollection.success).toBe(false);
      expect(invalidStorageProfile.success).toBe(false);
      expect(invalidConnectorUpdate.success).toBe(false);
    });
  });

  describe('Workflow and event contracts', () => {
    it('workflow and execution data pass contract validation', () => {
      const workflow = WorkflowSchema.parse({
        id: 'wf-contract-1',
        name: 'Contract Workflow',
        description: 'Validated workflow payload',
        status: 'active',
        executionCount: 7,
        lastExecutedAt: '2026-04-14T10:30:00Z',
        createdAt: '2026-04-01T00:00:00Z',
        updatedAt: '2026-04-14T10:30:00Z',
      });

      const execution = ExecutionSchema.parse({
        id: 'exec-contract-1',
        workflowId: workflow.id,
        status: 'completed',
        startedAt: '2026-04-14T10:29:00Z',
        completedAt: '2026-04-14T10:30:00Z',
        duration: 60000,
        input: { source: 'contract-test' },
        output: { rowsProcessed: 42 },
      });

      expect(execution.workflowId).toBe(workflow.id);
      expect(workflow.status).toBe('active');
    });

    it('paginated workflow data passes contract validation', () => {
      const paginated = PaginatedWorkflowResponseSchema.parse({
        items: [
          {
            id: 'wf-contract-2',
            name: 'Contract Workflow 2',
            description: 'Pipeline list payload',
            status: 'draft',
            executionCount: 0,
            createdAt: '2026-04-10T00:00:00Z',
            updatedAt: '2026-04-14T00:00:00Z',
          },
        ],
        total: 1,
        page: 1,
        pageSize: 20,
        hasMore: false,
      });

      expect(paginated.items[0]?.status).toBe('draft');
      expect(paginated.total).toBe(1);
    });

    it('event query and append response data pass contract validation', () => {
      const appendRequest = AppendEventRequestSchema.parse({
        type: 'entity.created',
        payload: { entityId: 'customer-1' },
        headers: { source: 'contract-test' },
        source: 'ui-test',
        correlationId: 'corr-1',
        schemaVersion: 'v1',
      });

      const eventQuery = EventQueryRequestSchema.parse({
        type: 'entity.created',
        from: 100,
        limit: 50,
        tenantId: 'tenant-alpha',
      });

      const event = EventSchema.parse({
        id: 'evt-contract-1',
        tenantId: 'tenant-alpha',
        type: 'entity.created',
        payload: { entityId: 'customer-1' },
        headers: { source: 'contract-test' },
        offset: 128,
        timestamp: '2026-04-14T11:00:00Z',
        source: 'ui-test',
        correlationId: 'corr-1',
        schemaVersion: 'v1',
      });

      const appendResponse = AppendEventResponseSchema.parse({
        offset: 129,
        type: 'entity.created',
        timestamp: '2026-04-14T11:01:00Z',
      });

      const queryResponse = EventQueryResponseSchema.parse({
        events: [event],
        count: 1,
        fromOffset: 100,
        nextOffset: 129,
        tenantId: 'tenant-alpha',
        timestamp: '2026-04-14T11:01:30Z',
      });

      expect(appendRequest.type).toBe('entity.created');
      expect(eventQuery.from).toBe(100);
      expect(eventQuery.type).toBe('entity.created');
      expect(appendResponse.offset).toBe(129);
      expect(queryResponse.events[0]?.type).toBe('entity.created');
    });
  });

  describe('Checkpoint and memory contracts', () => {
    it('checkpoint payloads pass contract validation', () => {
      const checkpoint = CheckpointSchema.parse({
        id: 'cp-1',
        data: { state: 'running', offset: 42 },
        tenantId: 'tenant-alpha',
      });

      const checkpointList = CheckpointListResponseSchema.parse({
        tenantId: 'tenant-alpha',
        checkpoints: [checkpoint],
        count: 1,
        timestamp: '2026-04-14T12:00:00Z',
      });

      const saveResponse = CheckpointSaveResponseSchema.parse({
        id: 'cp-1',
        tenantId: 'tenant-alpha',
        savedAt: '2026-04-14T12:01:00Z',
      });

      expect(checkpointList.count).toBe(1);
      expect(saveResponse.id).toBe('cp-1');
    });

    it('memory payloads pass contract validation', () => {
      const storeRequest = MemoryStoreRequestSchema.parse({
        type: 'episodic',
        content: 'Remember the failed deployment and rollback plan.',
        ttlSeconds: 3600,
        tags: ['ops', 'incident'],
        salience: 0.91,
        metadata: { source: 'contract-test' },
      });

      const item = MemoryItemSchema.parse({
        id: 'mem-1',
        tenantId: 'tenant-alpha',
        agentId: 'agent-1',
        type: 'EPISODIC',
        content: 'Remember the failed deployment and rollback plan.',
        tags: ['ops', 'incident'],
        salience: 0.91,
        createdAt: '2026-04-14T12:05:00Z',
        expiresAt: '2026-04-15T12:05:00Z',
        metadata: { source: 'contract-test' },
      });

      const rootList = MemoryRootListResponseSchema.parse({
        items: [item],
        total: 1,
        tenantId: 'tenant-alpha',
        agentId: 'agent-1',
        type: 'ALL',
        query: '',
        timestamp: '2026-04-14T12:06:00Z',
      });

      const summary = AgentMemorySummaryResponseSchema.parse({
        agentId: 'agent-1',
        tenantId: 'tenant-alpha',
        total: 1,
        items: [item],
        contextWindowSize: 1,
        byType: {
          episodic: 1,
          semantic: 0,
          procedural: 0,
          preference: 0,
          other: 0,
        },
        timestamp: '2026-04-14T12:06:00Z',
      });

      const tier = MemoryTierResponseSchema.parse({
        agentId: 'agent-1',
        tenantId: 'tenant-alpha',
        tier: 'episodic',
        items: [item],
        count: 1,
        offset: 0,
        limit: 100,
        timestamp: '2026-04-14T12:06:00Z',
      });

      const searchRequest = MemorySearchRequestSchema.parse({
        query: 'rollback',
        type: 'episodic',
        limit: 10,
      });

      const searchResponse = MemorySearchResponseSchema.parse({
        agentId: 'agent-1',
        tenantId: 'tenant-alpha',
        query: 'rollback',
        results: [item],
        count: 1,
        timestamp: '2026-04-14T12:07:00Z',
      });

      const deleteResponse = MemoryDeleteResponseSchema.parse({
        deleted: true,
        memoryId: 'mem-1',
        agentId: 'agent-1',
        tenantId: 'tenant-alpha',
        timestamp: '2026-04-14T12:08:00Z',
      });

      const retainRequest = MemoryRetainRequestSchema.parse({
        retainUntilEpoch: 1_776_163_200_000,
        reason: 'case-hold',
      });

      const retainResponse = MemoryRetainResponseSchema.parse({
        retained: true,
        memoryId: 'mem-1',
        agentId: 'agent-1',
        tenantId: 'tenant-alpha',
        reason: 'case-hold',
        timestamp: '2026-04-14T12:09:00Z',
      });

      expect(storeRequest.type).toBe('episodic');
      expect(rootList.items[0]?.type).toBe('EPISODIC');
      expect(summary.byType.episodic).toBe(1);
      expect(tier.count).toBe(1);
      expect(searchRequest.limit).toBe(10);
      expect(searchResponse.results[0]?.id).toBe('mem-1');
      expect(deleteResponse.deleted).toBe(true);
      expect(retainRequest.reason).toBe('case-hold');
      expect(retainResponse.retained).toBe(true);
    });
  });

  describe('Canonical OpenAPI alignment', () => {
    it('contains the reconciled frontend-backed routes', () => {
      const expectedPaths = [
        '/api/v1/entities/{collection}',
        '/api/v1/entities/{collection}/{id}',
        '/api/v1/entities/{collection}/search',
        '/api/v1/entities/{collection}/similar',
        '/api/v1/entities/{collection}/validate',
        '/api/v1/entities/{collection}/validate/batch',
        '/api/v1/entities/{collection}/suggest',
        '/api/v1/entities/{collection}/anomalies',
        '/api/v1/anomalies',
        '/api/v1/alerts',
        '/api/v1/alerts/{alertId}/acknowledge',
        '/api/v1/alerts/{alertId}/resolve',
        '/api/v1/alerts/groups',
        '/api/v1/alerts/groups/{groupId}/resolve',
        '/api/v1/alerts/suggestions',
        '/api/v1/alerts/suggestions/{suggestionId}/apply',
        '/api/v1/alerts/rules',
        '/api/v1/alerts/rules/{ruleId}',
        '/api/v1/alerts/stream',
        '/api/v1/analytics/query',
        '/api/v1/analytics/suggest',
        '/api/v1/queries/federated',
        '/api/v1/events',
        '/api/v1/lineage/{collection}',
        '/api/v1/lineage/{collection}/impact',
        '/api/v1/brain/explain',
        '/api/v1/brain/workspace',
        '/api/v1/brain/stats',
        '/api/v1/brain/attention/elevate',
        '/api/v1/brain/attention/thresholds',
        '/api/v1/brain/patterns',
        '/api/v1/brain/patterns/match',
        '/api/v1/brain/salience/{itemId}',
        '/api/v1/context',
        '/api/v1/context/{collection}',
        '/api/v1/context/{collection}/rag',
        '/api/v1/context/keys/{key}',
        '/api/v1/context/snapshot',
        '/mcp/v1/tools',
        '/api/v1/governance/privacy/pii-fields',
        '/api/v1/governance/compliance/summary',
        '/api/v1/data-products',
        '/api/v1/data-products/{productId}/subscribe',
        '/api/v1/reports',
        '/api/v1/reports/{reportId}',
        '/api/v1/features',
        '/api/v1/features/{entityId}',
        '/api/v1/models',
        '/api/v1/models/{modelName}',
        '/api/v1/models/{modelName}/promote',
        '/api/v1/voice/intent',
        '/api/v1/voice/intents',
        '/api/v1/voice/intent/classify',
        '/api/v1/surfaces',
        '/api/v1/surfaces/schema',
        // DC-P1.12: Removed compatibility /api/v1/capabilities endpoints
        '/api/v1/collections/{id}/cost-report',
        '/api/v1/collections/{id}/migrate',
        '/metrics',
      ];

      expectedPaths.forEach((routePath) => {
        expect(canonicalOpenApi).toContain(routePath);
      });
    });

    it('contains canonical Action Plane routes in action-plane.yaml only', () => {
      const expectedActionPaths = [
        '/api/v1/action/pipelines',
        '/api/v1/action/pipelines/draft',
        '/api/v1/action/pipelines/{pipelineId}',
        '/api/v1/action/pipelines/{pipelineId}/optimise-hint',
        '/api/v1/action/pipelines/{pipelineId}/execute',
        '/api/v1/action/executions/{executionId}',
        '/api/v1/action/executions/{executionId}/logs',
        '/api/v1/action/learning/trigger',
        '/api/v1/action/learning/status',
        '/api/v1/action/plugins',
        '/api/v1/action/plugins/{id}',
        '/api/v1/action/plugins/{id}/enable',
        '/api/v1/action/plugins/{id}/disable',
        '/api/v1/action/plugins/{id}/upgrade',
      ];

      expectedActionPaths.forEach((routePath) => {
        expect(actionPlaneOpenApi).toContain(routePath);
        expect(canonicalOpenApi).not.toContain(`${routePath.replace('/action', '')}:`);
      });
    });

    it('does not advertise unsupported lineage API paths', () => {
      const unsupportedPaths = [
        '/schema/suggest',
        '/dc/entities',
        '/dc/schemas',
        '/dc/fabric/metrics',
        '/api/metrics',
        '/api/metrics/available',
        '/api/dashboards',
        '/api/health/detailed',
        '/api/events/recent',
        '/api/plugins',
        '/api/plugins/installed',
        '/api/plugins/marketplace',
        '/api/v1/agents/register',
        '/api/v1/agents/events/stream',
        '/api/brain/agents',
        '/api/brain/interventions',
        '/api/v1/workflow-templates',
        '/api/v1/pipelines/templates',
        '/api/v1/pipelines/suggestions',
        '/api/v1/pipelines/validate',
        '/api/v1/lineage/{datasetId}',
        '/api/v1/lineage/{datasetId}/impact',
      ];

      unsupportedPaths.forEach((routePath) => {
        expect(canonicalOpenApi).not.toContain(routePath);
      });
    });

    it('keeps the canonical event contract aligned with the launcher response shape', () => {
      expect(canonicalOpenApi).toContain('AppendEventRequest:');
      expect(canonicalOpenApi).toContain('AppendEventResponse:');
      expect(canonicalOpenApi).toContain('EventQueryResponse:');
      expect(canonicalOpenApi).toContain('required: [type, payload]');
      expect(canonicalOpenApi).toContain('required: [offset, type, timestamp]');
      expect(canonicalOpenApi).toContain('$ref: "#/components/schemas/AppendEventRequest"');
      expect(canonicalOpenApi).toContain('$ref: "#/components/schemas/AppendEventResponse"');
      expect(canonicalOpenApi).toContain('$ref: "#/components/schemas/EventQueryResponse"');
      expect(canonicalOpenApi).toContain('/api/v1/events/{offset}:');
      expect(canonicalOpenApi).toContain('name: from');
      expect(canonicalOpenApi).toContain('name: type');
    });

    it('keeps canonical pipeline paths in the Action Plane contract', () => {
      expect(actionPlaneOpenApi).toContain('/api/v1/action/pipelines:');
      expect(actionPlaneOpenApi).toContain('/api/v1/action/pipelines/{pipelineId}:');
      expect(actionPlaneOpenApi).toContain('/api/v1/action/pipelines/{pipelineId}/execute:');
      expect(actionPlaneOpenApi).toContain('x-ghatana-required-access: OPERATOR');
      expect(canonicalOpenApi).not.toContain('/api/v1/pipelines:');
      expect(canonicalOpenApi).not.toContain('/api/v1/pipelines/{pipelineId}:');
    });

    it('keeps checkpoint in Data Cloud and memory in Action Plane ownership', () => {
      expect(canonicalOpenApi).toContain('Checkpoint:');
      expect(canonicalOpenApi).toContain('CheckpointListResponse:');
      expect(canonicalOpenApi).toContain('CheckpointSaveResponse:');
      expect(actionPlaneOpenApi).toContain('/api/v1/action/memory:');
      expect(actionPlaneOpenApi).toContain('/api/v1/action/memory/{agentId}/search:');
      expect(actionPlaneOpenApi).toContain('/api/v1/action/memory/{agentId}/{memoryId}/retain:');
      expect(canonicalOpenApi).not.toContain('/api/v1/memory:');
      expect(canonicalOpenApi).toContain('$ref: "#/components/schemas/CheckpointListResponse"');
    });

    it('keeps the canonical brain contracts aligned with the launcher response shape', () => {
      expect(canonicalOpenApi).toContain('BrainRuntimeStats:');
      expect(canonicalOpenApi).toContain('BrainWorkspaceStatus:');
      expect(canonicalOpenApi).toContain('BrainAttentionThresholds:');
      expect(canonicalOpenApi).toContain('BrainAttentionThresholdUpdateResponse:');
      expect(canonicalOpenApi).toContain('BrainElevationResult:');
      expect(canonicalOpenApi).toContain('BrainPatternListResponse:');
      expect(canonicalOpenApi).toContain('BrainPatternMatchResponse:');
      expect(canonicalOpenApi).toContain('BrainSalienceResponse:');
      expect(canonicalOpenApi).toContain('$ref: "#/components/schemas/BrainRuntimeStats"');
      expect(canonicalOpenApi).toContain('$ref: "#/components/schemas/BrainWorkspaceStatus"');
      expect(canonicalOpenApi).toContain('$ref: "#/components/schemas/BrainAttentionThresholds"');
      expect(canonicalOpenApi).toContain('$ref: "#/components/schemas/BrainPatternListResponse"');
      expect(canonicalOpenApi).toContain('$ref: "#/components/schemas/BrainSalienceResponse"');
      expect(actionPlaneOpenApi).toContain('/api/v1/action/learning/status:');
    });

    it('keeps the canonical analytics SQL and suggestion contracts aligned with the launcher response shape', () => {
      expect(canonicalOpenApi).toContain('AnalyticsSqlQueryRequest:');
      expect(canonicalOpenApi).toContain('AnalyticsSqlQueryResponse:');
      expect(canonicalOpenApi).toContain('AnalyticsSuggestResponse:');
      expect(canonicalOpenApi).toContain('AnalyticsSuggestQuery:');
      expect(canonicalOpenApi).toContain('LineageDagResponse:');
      expect(canonicalOpenApi).toContain('LineageImpactResponse:');
      expect(canonicalOpenApi).toContain('$ref: "#/components/schemas/AnalyticsSqlQueryRequest"');
      expect(canonicalOpenApi).toContain('$ref: "#/components/schemas/AnalyticsSqlQueryResponse"');
      expect(canonicalOpenApi).toContain('$ref: "#/components/schemas/AnalyticsSuggestResponse"');
      expect(canonicalOpenApi).toContain('$ref: "#/components/schemas/LineageDagResponse"');
      expect(canonicalOpenApi).toContain('$ref: "#/components/schemas/LineageImpactResponse"');
      expect(canonicalOpenApi).toContain('/api/v1/queries/federated:');
      expect(canonicalOpenApi).toContain('/api/v1/analytics/explain:');
      expect(canonicalOpenApi).toContain('/api/v1/entities/{collection}/anomalies:');
      expect(canonicalOpenApi).toContain('/api/v1/anomalies:');
      expect(canonicalOpenApi).toContain('AnomalyQueryResponse:');
      expect(canonicalOpenApi).toContain('$ref: "#/components/schemas/AnomalyQueryResponse"');
    });

    it('keeps the canonical reports, models, voice, and runtime-truth routes visible to contract coverage', () => {
      expect(canonicalOpenApi).toContain('/api/v1/reports:');
      expect(canonicalOpenApi).toContain('/api/v1/reports/{reportId}:');
      expect(canonicalOpenApi).toContain('/api/v1/models:');
      expect(canonicalOpenApi).toContain('/api/v1/models/{modelName}:');
      expect(canonicalOpenApi).toContain('/api/v1/models/{modelName}/promote:');
      expect(canonicalOpenApi).toContain('/api/v1/features:');
      expect(canonicalOpenApi).toContain('/api/v1/voice/intent:');
      expect(canonicalOpenApi).toContain('/api/v1/voice/intents:');
      expect(canonicalOpenApi).toContain('/api/v1/voice/intent/classify:');
      expect(canonicalOpenApi).toContain('/api/v1/surfaces:');
      expect(canonicalOpenApi).toContain('/api/v1/surfaces/schema:');
      // DC-P1.12: Removed compatibility /api/v1/capabilities endpoints
      expect(canonicalOpenApi).toContain('VoiceIntentRequest:');
      expect(canonicalOpenApi).toContain('VoiceIntentClassificationRequest:');
    });

    it('keeps the canonical context routes visible to contract coverage', () => {
      expect(canonicalOpenApi).toContain('/api/v1/context:');
      expect(canonicalOpenApi).toContain('/api/v1/context/{collection}:');
      expect(canonicalOpenApi).toContain('/api/v1/context/{collection}/rag:');
      expect(canonicalOpenApi).toContain('/api/v1/context/keys/{key}:');
      expect(canonicalOpenApi).toContain('/api/v1/context/snapshot:');
      expect(canonicalOpenApi).toContain('/mcp/v1/tools:');
      expect(canonicalOpenApi).toContain('CollectionContextResponse:');
      expect(canonicalOpenApi).toContain('McpToolRegistryResponse:');
      expect(canonicalOpenApi).toContain('McpToolCallRequest:');
      expect(canonicalOpenApi).toContain('McpToolCallResponse:');
      expect(canonicalOpenApi).toContain('CollectionRagRequest:');
      expect(canonicalOpenApi).toContain('CollectionRagResponse:');
      expect(canonicalOpenApi).toContain('$ref: "#/components/schemas/CollectionContextResponse"');
      expect(canonicalOpenApi).toContain('$ref: "#/components/schemas/McpToolRegistryResponse"');
      expect(canonicalOpenApi).toContain('$ref: "#/components/schemas/McpToolCallRequest"');
      expect(canonicalOpenApi).toContain('$ref: "#/components/schemas/McpToolCallResponse"');
      expect(canonicalOpenApi).toContain('$ref: "#/components/schemas/CollectionRagRequest"');
      expect(canonicalOpenApi).toContain('$ref: "#/components/schemas/CollectionRagResponse"');
    });

    it('keeps the canonical semantic search and data product routes visible to contract coverage', () => {
      expect(canonicalOpenApi).toContain('/api/v1/entities/{collection}/similar:');
      expect(canonicalOpenApi).toContain('/api/v1/data-products:');
      expect(canonicalOpenApi).toContain('/api/v1/data-products/{productId}/subscribe:');
      expect(canonicalOpenApi).toContain('SimilarEntitiesResponse:');
      expect(canonicalOpenApi).toContain('PublishDataProductRequest:');
      expect(canonicalOpenApi).toContain('PublishDataProductResponse:');
      expect(canonicalOpenApi).toContain('DataProductListResponse:');
      expect(canonicalOpenApi).toContain('DataProductSubscriptionResponse:');
      expect(canonicalOpenApi).toContain('$ref: "#/components/schemas/SimilarEntitiesResponse"');
      expect(canonicalOpenApi).toContain('$ref: "#/components/schemas/PublishDataProductRequest"');
      expect(canonicalOpenApi).toContain('$ref: "#/components/schemas/PublishDataProductResponse"');
      expect(canonicalOpenApi).toContain('$ref: "#/components/schemas/DataProductListResponse"');
      expect(canonicalOpenApi).toContain('$ref: "#/components/schemas/DataProductSubscriptionResponse"');
    });
  });

  describe('Brain schemas', () => {
    it('validates canonical brain launcher payloads', () => {
      const stats = BrainRuntimeStatsSchema.parse({
        totalRecordsProcessed: 482,
        activePatterns: 7,
        activeRules: 12,
        hotTierRecords: 43,
        warmTierRecords: 128,
        avgProcessingTimeMs: 18.4,
        uptimeSeconds: 86400,
        tenantId: 'tenant-alpha',
        timestamp: '2026-04-14T12:30:00Z',
      });

      const workspace = BrainWorkspaceStatusSchema.parse({
        status: 'active',
        brainId: 'brain-1',
        note: 'Detailed spotlight items available via GET /api/v1/brain/stats',
        timestamp: '2026-04-14T12:31:00Z',
      });

      const thresholds = BrainAttentionThresholdsSchema.parse({
        elevationThreshold: 0.71,
        emergencyThreshold: 0.93,
        salienceThreshold: 0.64,
        totalProcessed: 100,
        elevatedCount: 15,
        emergencyCount: 2,
        elevationRate: 0.15,
        timestamp: '2026-04-14T12:32:00Z',
      });

      const thresholdUpdate = BrainAttentionThresholdUpdateResponseSchema.parse({
        acknowledged: true,
        note: 'Restart required for threshold changes.',
        timestamp: '2026-04-14T12:33:00Z',
      });

      const elevation = BrainElevationResultSchema.parse({
        elevated: true,
        emergency: true,
        action: 'ELEVATED',
        recordId: '7f1db913-c80f-4c2f-9a72-ff59d6bcf1cb',
        reason: 'manual-ui-elevation',
        tenantId: 'tenant-alpha',
        timestamp: '2026-04-14T12:34:00Z',
      });

      const patternList = BrainPatternListResponseSchema.parse({
        patterns: [
          {
            id: 'pattern-1',
            name: 'Morning Spike',
            type: 'TEMPORAL',
            description: 'Weekly morning traffic spike',
            confidence: 0.86,
            observations: 14,
            discoveredAt: '2026-04-01T09:00:00Z',
            updatedAt: '2026-04-14T09:00:00Z',
          },
        ],
        count: 1,
        limit: 100,
        tenantId: 'tenant-alpha',
        timestamp: '2026-04-14T12:35:00Z',
      });

      const matchResponse = BrainPatternMatchResponseSchema.parse({
        recordId: 'record-1',
        matches: [
          {
            patternId: 'pattern-1',
            patternName: 'Morning Spike',
            score: 0.88,
            confidence: 0.86,
            explanation: 'Matches the known weekly batch spike.',
          },
        ],
        count: 1,
        tenantId: 'tenant-alpha',
        timestamp: '2026-04-14T12:36:00Z',
      });

      const salience = BrainSalienceResponseSchema.parse({
        itemId: 'spot-1',
        salienceScore: 0.91,
        isHigh: true,
        isEmergency: false,
        priority: 2,
        summary: 'Quality regression detected',
        tenantId: 'tenant-alpha',
        spotlightedAt: '2026-04-14T12:37:00Z',
        timestamp: '2026-04-14T12:38:00Z',
      });

      expect(stats.activePatterns).toBe(7);
      expect(workspace.status).toBe('active');
      expect(thresholds.salienceThreshold).toBe(0.64);
      expect(thresholdUpdate.acknowledged).toBe(true);
      expect(elevation.emergency).toBe(true);
      expect(patternList.patterns[0]?.id).toBe('pattern-1');
      expect(matchResponse.matches[0]?.score).toBe(0.88);
      expect(salience.isHigh).toBe(true);
    });

    it('validates canonical autonomy payloads', () => {
      const logs = AutonomyLogsResponseSchema.parse({
        logs: [
          {
            id: 'log-1',
            actionType: 'ingestion',
            tenantId: 'tenant-alpha',
            level: 'NOTIFY',
            decision: 'ALLOWED',
            confidence: 0.91,
            context: { source: 'contract-test' },
            timestamp: '2026-04-14T12:00:00Z',
          },
        ],
        count: 1,
        globalOverride: 'NONE',
        timestamp: '2026-04-14T12:06:00Z',
      });

      const state = AutonomyStateResponseSchema.parse({
        domain: 'optimization',
        state: {
          actionType: 'optimization',
          tenantId: 'tenant-alpha',
          currentLevel: 'NOTIFY',
          effectiveMaxLevel: 'AUTONOMOUS',
          confidence: 0.73,
          lastActionAt: '2026-04-14T12:10:00Z',
        },
        timestamp: '2026-04-14T12:11:00Z',
      });

      expect(logs.logs[0]?.level).toBe('NOTIFY');
      expect(state.state.currentLevel).toBe('NOTIFY');
    });

    it('validates canonical autonomy override payloads', () => {
      const request = AutonomyLevelOverrideRequestSchema.parse({
        level: 'CONFIRM',
        reason: 'Require operator review',
      });

      const response = AutonomyLevelOverrideResponseSchema.parse({
        globalLevel: 'CONFIRM',
        affectedDomains: 5,
        timestamp: '2026-04-15T12:30:00Z',
        reason: 'Require operator review',
      });

      const status = AutonomyLevelStatusSchema.parse({
        globalOverride: 'CONFIRM',
        shutoffActive: false,
        domainCount: 5,
      });

      expect(request.level).toBe('CONFIRM');
      expect(response.affectedDomains).toBe(5);
      expect(status.domainCount).toBe(5);
    });

    it('validates canonical learning status payloads', () => {
      const learningStatus = LearningStatusResponseSchema.parse({
        running: false,
        lastRunTime: '2026-04-14T12:00:00Z',
        nextScheduledRun: '2026-04-14T13:00:00Z',
        intervalMinutes: 60,
        pendingReviews: 2,
        lastResult: {
          status: 'COMPLETED',
          tenantId: 'tenant-alpha',
          manual: false,
          durationMs: 1200,
          patternsDiscovered: 3,
          patternsUpdated: 1,
          recordsAnalyzed: 24,
          ranAt: '2026-04-14T12:00:00Z',
        },
        timestamp: '2026-04-14T12:01:00Z',
      });

      expect(learningStatus.pendingReviews).toBe(2);
      expect(learningStatus.lastResult?.patternsDiscovered).toBe(3);
    });

    it('validates canonical learning-trigger payloads', () => {
      const learningSignal = LearningSignalSchema.parse({
        id: 'feedback-1',
        timestamp: '2026-04-14T12:27:00Z',
        signalType: 'manual-feedback',
        impact: 0.35,
        status: 'PROCESSED',
        affectedComponents: ['brain', 'feedback-widget'],
      });

      expect(learningSignal.status).toBe('PROCESSED');
      expect(learningSignal.affectedComponents).toContain('brain');
    });
  });
});
