/**
 * Tests for PHR Contract Visualizer (YAPPC-T02)
 */

import { describe, it, expect } from 'vitest';
import { createPhrContractVisualizer, type PhrRouteContract } from './phr-contract-visualizer';

describe('PhrContractVisualizer', () => {
  it('visualizes complete PHR contract with all fields', () => {
    const contract: PhrRouteContract = {
      product: 'phr',
      version: '1.0.0',
      schemaVersion: '1.0.0',
      routes: [
        {
          path: '/dashboard',
          label: 'Dashboard',
          group: 'care',
          stability: 'stable',
          minimumRole: 'patient',
          surface: ['web', 'mobile'],
          i18nKey: 'phr.routes.dashboard.label',
          descriptionI18nKey: 'phr.routes.dashboard.description',
          accessibility: { ariaLabel: true, keyboardNav: true },
          apiEndpoint: '/api/v1/dashboard',
          policyId: 'phr.dashboard.view',
          testId: 'phr-dashboard-view-001',
        },
      ],
    };

    const visualizer = createPhrContractVisualizer();
    const result = visualizer.visualize(contract);

    expect(result.contract.product).toBe('phr');
    expect(result.contract.routeCount).toBe(1);
    expect(result.routes).toHaveLength(1);
    expect(result.routes[0].overallStatus).toBe('complete');
    expect(result.summary.completeRoutes).toBe(1);
  });

  it('identifies partial implementation status', () => {
    const contract: PhrRouteContract = {
      product: 'phr',
      version: '1.0.0',
      routes: [
        {
          path: '/records',
          label: 'Records',
          group: 'care',
          stability: 'stable',
          minimumRole: 'patient',
          surface: ['web'],
          i18nKey: 'phr.routes.records.label',
          apiEndpoint: '/api/v1/records',
          policyId: 'phr.records.view',
          testId: undefined,
        },
      ],
    };

    const visualizer = createPhrContractVisualizer();
    const result = visualizer.visualize(contract);

    expect(result.routes[0].overallStatus).toBe('partial');
    expect(result.routes[0].test.status).toBe('missing');
  });

  it('identifies blocked routes', () => {
    const contract: PhrRouteContract = {
      product: 'phr',
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

    const visualizer = createPhrContractVisualizer();
    const result = visualizer.visualize(contract);

    expect(result.routes[0].overallStatus).toBe('blocked');
    expect(result.summary.blockedRoutes).toBe(1);
  });

  it('calculates coverage percentages correctly', () => {
    const contract: PhrRouteContract = {
      product: 'phr',
      version: '1.0.0',
      routes: [
        {
          path: '/dashboard',
          label: 'Dashboard',
          group: 'care',
          stability: 'stable',
          minimumRole: 'patient',
          surface: ['web'],
          i18nKey: 'phr.routes.dashboard.label',
          apiEndpoint: '/api/v1/dashboard',
          policyId: 'phr.dashboard.view',
          testId: 'phr-dashboard-view-001',
        },
        {
          path: '/records',
          label: 'Records',
          group: 'care',
          stability: 'stable',
          minimumRole: 'patient',
          surface: ['web'],
          apiEndpoint: '/api/v1/records',
          policyId: 'phr.records.view',
          testId: undefined,
        },
      ],
    };

    const visualizer = createPhrContractVisualizer();
    const result = visualizer.visualize(contract);

    expect(result.summary.pageCoverage).toBe(100);
    expect(result.summary.apiCoverage).toBe(100);
    expect(result.summary.policyCoverage).toBe(100);
    expect(result.summary.testCoverage).toBe(50);
  });

  it('handles metadata fallback for apiEndpoint, policyId, testId', () => {
    const contract: PhrRouteContract = {
      product: 'phr',
      version: '1.0.0',
      routes: [
        {
          path: '/dashboard',
          label: 'Dashboard',
          group: 'care',
          stability: 'stable',
          minimumRole: 'patient',
          surface: ['web'],
          metadata: {
            apiEndpoint: '/api/v1/dashboard',
            policyId: 'phr.dashboard.view',
            testId: 'phr-dashboard-view-001',
          },
        },
      ],
    };

    const visualizer = createPhrContractVisualizer();
    const result = visualizer.visualize(contract);

    expect(result.routes[0].api.endpoint).toBe('/api/v1/dashboard');
    expect(result.routes[0].policy.policyId).toBe('phr.dashboard.view');
    expect(result.routes[0].test.testId).toBe('phr-dashboard-view-001');
  });
});
