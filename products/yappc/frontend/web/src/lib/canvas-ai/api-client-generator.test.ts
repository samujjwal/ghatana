import { describe, expect, it } from 'vitest';
import { createApiClientGenerator, type ProductRouteContract } from './api-client-generator';

describe('ApiClientGenerator', () => {
  it('generates API client skeletons for routes with API endpoints', () => {
    const contract: ProductRouteContract = {
      product: 'sample-product',
      version: '1.0.0',
      routes: [
        {
          path: '/dashboard',
          label: 'Dashboard',
          apiEndpoint: '/api/v1/dashboard',
          policyId: 'sample.dashboard.view',
          testId: 'sample-dashboard-view-001',
        },
      ],
    };

    const generator = createApiClientGenerator();
    const clients = generator.generateApiClientSkeletons(contract);

    expect(clients).toHaveLength(1);
    expect(clients[0].functionName).toBe('dashboard');
    expect(clients[0].filePath).toBe('src/api/generated/dashboard.ts');
    expect(clients[0].code).toContain('export async function dashboard');
    expect(clients[0].code).toContain('productFetch');
    expect(clients[0].code).not.toContain(['p', 'h', 'r', 'Fetch'].join(''));
  });

  it('skips routes without API endpoints', () => {
    const contract: ProductRouteContract = {
      product: 'sample-product',
      version: '1.0.0',
      routes: [
        {
          path: '/settings',
          label: 'Settings',
        },
      ],
    };

    const generator = createApiClientGenerator();
    const clients = generator.generateApiClientSkeletons(contract);

    expect(clients).toHaveLength(0);
  });

  it('infers HTTP method from endpoint pattern', () => {
    const contract: ProductRouteContract = {
      product: 'sample-product',
      version: '1.0.0',
      routes: [
        {
          path: '/records/create',
          label: 'Create Record',
          apiEndpoint: '/api/v1/records/create',
          policyId: 'sample.records.create',
        },
      ],
    };

    const generator = createApiClientGenerator();
    const clients = generator.generateApiClientSkeletons(contract);

    expect(clients[0].code).toContain("method: 'POST'");
  });

  it('handles metadata fallback for apiEndpoint and policyId', () => {
    const contract: ProductRouteContract = {
      product: 'sample-product',
      version: '1.0.0',
      routes: [
        {
          path: '/documents',
          label: 'Documents',
          metadata: {
            apiEndpoint: '/api/v1/documents',
            policyId: 'sample.documents.view',
          },
        },
      ],
    };

    const generator = createApiClientGenerator();
    const clients = generator.generateApiClientSkeletons(contract);

    expect(clients).toHaveLength(1);
    expect(clients[0].code).toContain('/api/v1/documents');
    expect(clients[0].code).toContain('sample.documents.view');
  });

  it('generates correct function names from endpoints', () => {
    const contract: ProductRouteContract = {
      product: 'sample-product',
      version: '1.0.0',
      routes: [
        { path: '/member-profile', label: 'Profile', apiEndpoint: '/api/v1/member-profile', policyId: 'sample.profile.view' },
        { path: '/work-plans', label: 'Work Plans', apiEndpoint: '/api/v1/work-plans', policyId: 'sample.workplans.view' },
      ],
    };

    const generator = createApiClientGenerator();
    const clients = generator.generateApiClientSkeletons(contract);

    expect(clients[0].functionName).toBe('memberProfile');
    expect(clients[1].functionName).toBe('workPlans');
  });
});
