import React from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

import { axe, toHaveNoViolations } from '../../../__tests__/accessibility/axe-vitest-shim';
import { ProductFamilyControlPlanePage } from '../ProductFamilyControlPlanePage';

expect.extend(toHaveNoViolations);

vi.mock('../../../clients/productFamilyClient', () => ({
  getReleaseReadiness: vi.fn(async (productKey: string) => ({
    productKey,
    status: 'READY',
    verdict: 'READY',
    gateStatus: ['all gates passed'],
    blockers: [],
    evidenceRefs: ['evidence://release/1'],
    foundationReadiness: ['kernel: required-production'],
    docTruthWarnings: [],
    connectorGates: ['connector approval evidence present'],
    approvalGates: ['campaign approval policy passed'],
    aiActionGates: ['AI action audit trail present'],
    traceId: 'trace-1',
    updatedAt: '2026-05-23T00:00:00.000Z',
  })),
  listProductAssets: vi.fn(async () => ({
    status: 'READY',
    assets: [
      {
        assetId: 'asset-1',
        type: 'module',
        sourceProduct: 'sample-product',
        displayName: 'Reusable approval module',
        domain: 'workflow',
        paths: [],
        maturity: 'candidate',
        reuseMode: 'reference',
        dependencies: [],
        tests: [],
        productUsage: [],
        owner: 'platform',
        promotionTarget: '',
        promotionState: 'candidate',
        compatibility: {},
      },
    ],
    appliedFilters: {},
    warnings: [],
  })),
  listDocTruthWarnings: vi.fn(async () => ({ status: 'READY', warnings: [] })),
  listGuidedReuse: vi.fn(async (targetProduct: string) => ({
    targetProduct,
    status: 'READY',
    recommendations: [{ assetId: 'asset-1', reason: 'compatible reusable asset' }],
  })),
  getKernelTimeline: vi.fn(async (productUnitId: string) => ({
    productUnitId,
    status: 'READY',
    timeline: [{ stage: 'verify', evidenceRef: 'evidence://kernel/1', traceId: 'trace-1' }],
    rollbackVisibility: { rollbackAvailable: true, executedBy: 'kernel' },
  })),
  promoteProductAsset: vi.fn(async () => ({
    status: 'PROMOTED',
    asset: {},
    promotion: {},
  })),
}));

describe('ProductFamilyControlPlanePage accessibility', () => {
  it('has no axe violations in the default Sample Product cockpit view', async () => {
    const queryClient = new QueryClient({
      defaultOptions: {
        queries: { retry: false },
        mutations: { retry: false },
      },
    });

    const { container, findByText } = render(
      <QueryClientProvider client={queryClient}>
        <ProductFamilyControlPlanePage />
      </QueryClientProvider>,
    );

    await findByText('Sample Product Release Readiness Cockpit');

    const results = await axe(container);
    expect(results).toHaveNoViolations();
  });
});
