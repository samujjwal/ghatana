/**
 * Tests for ContextExplorerPage.
 *
 * @doc.type test
 * @doc.purpose RTL coverage for the collection-scoped context explorer page
 * @doc.layer frontend
 */

import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { readFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import React from 'react';
import { TestWrapper } from '../test-utils/wrapper';
import { TEST_TENANT_ID } from '@/__tests__/test-utils/tenants';

vi.mock('../../lib/api/collections', () => ({
  collectionsApi: {
    list: vi.fn(),
  },
}));

vi.mock('../../lib/api/context', () => ({
  getCollectionContext: vi.fn(),
}));

vi.mock('../../api/lineage.service', () => ({
  lineageService: {
    getLineage: vi.fn(),
  },
}));

vi.mock('../../components/lineage/LineageGraph', () => ({
  LineageGraph: ({ rootNode }: { rootNode?: string }) => (
    <div data-testid="lineage-graph">lineage-root:{rootNode}</div>
  ),
}));

import { collectionsApi } from '../../lib/api/collections';
import { getCollectionContext } from '../../lib/api/context';
import { lineageService } from '../../api/lineage.service';
import { ContextExplorerPage } from '../../pages/ContextExplorerPage';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const routesSource = readFileSync(path.resolve(__dirname, '../../routes.tsx'), 'utf8');

describe('ContextExplorerPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();

    vi.mocked(collectionsApi.list).mockResolvedValue({
      items: [
        {
          id: 'orders',
          name: 'orders',
          description: 'Order facts',
          schemaType: 'entity',
          status: 'active',
          isActive: true,
          entityCount: 2,
          schema: { fields: [{ name: 'orderId', type: 'string' }] },
          tags: ['sales'],
          createdAt: '2026-04-18T08:00:00Z',
          updatedAt: '2026-04-18T08:30:00Z',
          createdBy: 'system',
        },
        {
          id: 'customers',
          name: 'customers',
          description: 'Customer profile graph',
          schemaType: 'graph',
          status: 'active',
          isActive: true,
          entityCount: 15,
          schema: { fields: [{ name: 'customerId', type: 'string' }] },
          tags: ['crm'],
          createdAt: '2026-04-18T08:00:00Z',
          updatedAt: '2026-04-18T08:30:00Z',
          createdBy: 'system',
        },
      ],
      total: 2,
      page: 1,
      pageSize: 24,
      hasMore: false,
    });

    vi.mocked(getCollectionContext).mockResolvedValue({
      collection: 'orders',
      tenantId: TEST_TENANT_ID,
      requestId: 'req-context-page',
      generatedAt: '2026-04-18T09:00:00Z',
      generationTimeMs: 17,
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
        policyReason: 'Contains customer communications',
      },
      freshness: {
        sampledAt: '2026-04-18T09:00:00Z',
        lastEntityUpdatedAt: '2026-04-18T08:59:00Z',
        lastEntityCreatedAt: '2026-04-18T08:57:00Z',
      },
      statisticalProfile: {
        entityCount: 2,
        sampleSize: 2,
        nullRates: { email: 0.5 },
        topValues: {
          customerId: [{ value: 'cust-1', count: 2 }],
        },
      },
      relationshipDepth: 3,
      relationships: [
        { id: 'edge-1', source: 'orders', target: 'customers', type: 'BELONGS_TO', depth: 1 },
        { id: 'edge-2', source: 'customers', target: 'regions', type: 'OPERATES_IN', depth: 3 },
      ],
    });

    vi.mocked(lineageService.getLineage).mockResolvedValue({
      nodes: [{ id: 'orders', type: 'DATASET', name: 'orders', metadata: {} }],
      edges: [],
      rootNode: 'orders',
    });
  });

  it('renders the context explorer route in the main router', () => {
    expect(routesSource).toContain('ContextExplorerPage');
    expect(routesSource).toContain("path: 'context'");
  });

  it('renders collection context details from the unified API', async () => {
    render(<ContextExplorerPage />, { wrapper: TestWrapper });

    await waitFor(() => {
      expect(screen.getByText('Context Explorer')).toBeDefined();
      expect(screen.getByText('Contains customer communications')).toBeDefined();
      expect(screen.getByText('Depth 3')).toBeDefined();
    });

    expect(screen.getByText('compliance')).toBeDefined();
    expect(screen.getByText('lineage-root:orders')).toBeDefined();
    expect(vi.mocked(getCollectionContext)).toHaveBeenCalledWith('orders', { depth: 1 });
  });

  it('refetches context with the selected traversal depth', async () => {
    render(<ContextExplorerPage />, { wrapper: TestWrapper });

    await waitFor(() => expect(screen.getByText('1 hop')).toBeDefined());
    fireEvent.click(screen.getByRole('button', { name: '3 hops' }));

    await waitFor(() => {
      expect(vi.mocked(getCollectionContext)).toHaveBeenLastCalledWith('orders', { depth: 3 });
      expect(vi.mocked(lineageService.getLineage)).toHaveBeenLastCalledWith('orders', 'BOTH', 3);
    });
  });
});