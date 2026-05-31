import { describe, expect, it } from 'vitest';
import { createProductContractVisualizer, type ProductRouteContract } from './product-contract-visualizer';

describe('ProductContractVisualizer', () => {
  it('visualizes complete product contract with all fields', () => {
    const contract: ProductRouteContract = {
      product: 'sample-product',
      version: '1.0.0',
      schemaVersion: '1.0.0',
      routes: [
        {
          path: '/dashboard',
          label: 'Dashboard',
          group: 'workspace',
          stability: 'stable',
          minimumRole: 'member',
          surface: ['web', 'mobile'],
          i18nKey: 'sample.routes.dashboard.label',
          descriptionI18nKey: 'sample.routes.dashboard.description',
          accessibility: { ariaLabel: true, keyboardNav: true },
          apiEndpoint: '/api/v1/dashboard',
          policyId: 'sample.dashboard.view',
          testId: 'sample-dashboard-view-001',
        },
      ],
    };

    const visualizer = createProductContractVisualizer();
    const result = visualizer.visualize(contract);

    expect(result.contract.product).toBe('sample-product');
    expect(result.contract.routeCount).toBe(1);
    expect(result.routes).toHaveLength(1);
    expect(result.routes[0].overallStatus).toBe('complete');
    expect(result.summary.completeRoutes).toBe(1);
  });

  it('identifies partial implementation status', () => {
    const contract: ProductRouteContract = {
      product: 'sample-product',
      version: '1.0.0',
      routes: [
        {
          path: '/records',
          label: 'Records',
          group: 'workspace',
          stability: 'stable',
          minimumRole: 'member',
          surface: ['web'],
          i18nKey: 'sample.routes.records.label',
          apiEndpoint: '/api/v1/records',
          policyId: 'sample.records.view',
        },
      ],
    };

    const visualizer = createProductContractVisualizer();
    const result = visualizer.visualize(contract);

    expect(result.routes[0].overallStatus).toBe('partial');
    expect(result.routes[0].test.status).toBe('missing');
  });

  it('identifies blocked routes', () => {
    const contract: ProductRouteContract = {
      product: 'sample-product',
      version: '1.0.0',
      routes: [
        {
          path: '/admin',
          label: 'Admin',
          group: 'admin',
          stability: 'blocked',
          minimumRole: 'admin',
        },
      ],
    };

    const visualizer = createProductContractVisualizer();
    const result = visualizer.visualize(contract);

    expect(result.routes[0].overallStatus).toBe('blocked');
    expect(result.summary.blockedRoutes).toBe(1);
  });

  it('calculates coverage percentages correctly', () => {
    const contract: ProductRouteContract = {
      product: 'sample-product',
      version: '1.0.0',
      routes: [
        {
          path: '/dashboard',
          label: 'Dashboard',
          group: 'workspace',
          stability: 'stable',
          minimumRole: 'member',
          surface: ['web'],
          i18nKey: 'sample.routes.dashboard.label',
          apiEndpoint: '/api/v1/dashboard',
          policyId: 'sample.dashboard.view',
          testId: 'sample-dashboard-view-001',
        },
        {
          path: '/records',
          label: 'Records',
          group: 'workspace',
          stability: 'stable',
          minimumRole: 'member',
          surface: ['web'],
          apiEndpoint: '/api/v1/records',
          policyId: 'sample.records.view',
        },
      ],
    };

    const visualizer = createProductContractVisualizer();
    const result = visualizer.visualize(contract);

    expect(result.summary.pageCoverage).toBe(100);
    expect(result.summary.apiCoverage).toBe(100);
    expect(result.summary.policyCoverage).toBe(100);
    expect(result.summary.testCoverage).toBe(50);
  });

  it('handles metadata fallback for apiEndpoint, policyId, testId', () => {
    const contract: ProductRouteContract = {
      product: 'sample-product',
      version: '1.0.0',
      routes: [
        {
          path: '/dashboard',
          label: 'Dashboard',
          group: 'workspace',
          stability: 'stable',
          minimumRole: 'member',
          surface: ['web'],
          metadata: {
            apiEndpoint: '/api/v1/dashboard',
            policyId: 'sample.dashboard.view',
            testId: 'sample-dashboard-view-001',
          },
        },
      ],
    };

    const visualizer = createProductContractVisualizer();
    const result = visualizer.visualize(contract);

    expect(result.routes[0].api.endpoint).toBe('/api/v1/dashboard');
    expect(result.routes[0].policy.policyId).toBe('sample.dashboard.view');
    expect(result.routes[0].test.testId).toBe('sample-dashboard-view-001');
  });
});
