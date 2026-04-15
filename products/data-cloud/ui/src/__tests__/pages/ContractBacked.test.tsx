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
import {
  CollectionSchema,
  PaginatedCollectionResponseSchema,
  CreateCollectionRequestSchema,
  UpdateCollectionRequestSchema,
  WorkflowSchema,
  PaginatedWorkflowResponseSchema,
  CreateWorkflowRequestSchema,
  ExecutionSchema,
  StorageProfileSchema,
  StorageProfileMetricsSchema,
  ConnectorSchema,
  ConnectorTypeSchema,
  AnalyticsQuerySchema,
  AnalyticsResultSchema,
  PaginatedAnalyticsResultSchema,
  EventSchema,
  AppendEventRequestSchema,
  AppendEventResponseSchema,
  EventQueryRequestSchema,
  PaginatedEventResponseSchema,
  CreateStorageProfileRequestSchema,
  UpdateStorageProfileRequestSchema,
  CreateConnectorRequestSchema,
  UpdateConnectorRequestSchema,
} from '../../contracts/schemas';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const canonicalOpenApi = readFileSync(
  path.resolve(__dirname, '../../../../api/openapi.yaml'),
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
            tenantId: 'default',
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
  });

  describe('Analytics contracts', () => {
    it('analytics query payload passes contract validation', () => {
      const query = AnalyticsQuerySchema.parse({
        metric: 'entity_count',
        dimensions: ['collection', 'tenant'],
        filters: { tenantId: 'default', status: 'active' },
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

      expect(createCollection.tags).toContain('crm');
      expect(updateCollection.status).toBe('active');
      expect(createWorkflow.name).toBe('Daily Sync');
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
            description: 'Workflow list payload',
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
        eventTypes: ['entity.created'],
        offset: 100,
        limit: 50,
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
        eventId: 'evt-contract-2',
        timestamp: '2026-04-14T11:01:00Z',
      });

      const paginated = PaginatedEventResponseSchema.parse({
        items: [event],
        total: 1,
        page: 1,
        pageSize: 50,
        hasMore: false,
        nextOffset: 129,
      });

      expect(appendRequest.type).toBe('entity.created');
      expect(eventQuery.offset).toBe(100);
      expect(eventQuery.eventTypes).toEqual(['entity.created']);
      expect(appendResponse.offset).toBe(129);
      expect(paginated.items[0]?.type).toBe('entity.created');
    });
  });

  describe('Canonical OpenAPI alignment', () => {
    it('contains the reconciled frontend-backed routes', () => {
      const expectedPaths = [
        '/api/v1/entities/{collection}',
        '/api/v1/entities/{collection}/{id}',
        '/api/v1/entities/{collection}/search',
        '/api/v1/entities/{collection}/validate',
        '/api/v1/entities/{collection}/suggest',
        '/api/v1/entities/{collection}/anomalies',
        '/api/v1/pipelines',
        '/api/v1/analytics/suggest',
        '/api/v1/agents/catalog',
        '/api/v1/agents/catalog/{id}',
        '/api/v1/brain/explain',
        '/api/v1/brain/workspace',
        '/api/v1/brain/stats',
        '/api/v1/learning/status',
        '/api/v1/governance/privacy/pii-fields',
        '/api/v1/governance/compliance/summary',
        '/api/v1/plugins',
        '/api/v1/collections/{id}/cost-report',
        '/metrics',
      ];

      expectedPaths.forEach((routePath) => {
        expect(canonicalOpenApi).toContain(routePath);
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
        '/api/v1/executions/',
        '/api/v1/agents/register',
        '/api/v1/agents/events/stream',
        '/api/brain/agents',
        '/api/brain/interventions',
        '/api/v1/workflow-templates',
        '/api/v1/pipelines/templates',
        '/api/v1/pipelines/suggestions',
        '/api/v1/pipelines/validate',
        '/api/v1/lineage/',
        '/api/v1/lineage/{datasetId}',
        '/api/v1/lineage/{datasetId}/impact',
      ];

      unsupportedPaths.forEach((routePath) => {
        expect(canonicalOpenApi).not.toContain(routePath);
      });
    });
  });
});
