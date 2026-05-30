/**
 * Tests for PHR API Client Generator (YAPPC-T04)
 */

import { describe, it, expect } from 'vitest';
import { createPhrApiClientGenerator, type PhrRouteContract } from './phr-api-client-generator';

describe('PhrApiClientGenerator', () => {
  it('generates API client skeletons for routes with API endpoints', () => {
    const contract: PhrRouteContract = {
      product: 'phr',
      version: '1.0.0',
      routes: [
        {
          path: '/dashboard',
          label: 'Dashboard',
          apiEndpoint: '/api/v1/dashboard',
          policyId: 'phr.dashboard.view',
          testId: 'phr-dashboard-view-001',
        },
      ],
    };

    const generator = createPhrApiClientGenerator();
    const clients = generator.generateApiClientSkeletons(contract);

    expect(clients).toHaveLength(1);
    expect(clients[0].functionName).toBe('dashboard');
    expect(clients[0].filePath).toBe('src/api/generated/dashboard.ts');
    expect(clients[0].code).toContain('export async function dashboard');
    expect(clients[0].code).toContain('phrFetch');
  });

  it('skips routes without API endpoints', () => {
    const contract: PhrRouteContract = {
      product: 'phr',
      version: '1.0.0',
      routes: [
        {
          path: '/settings',
          label: 'Settings',
        },
      ],
    };

    const generator = createPhrApiClientGenerator();
    const clients = generator.generateApiClientSkeletons(contract);

    expect(clients).toHaveLength(0);
  });

  it('infers HTTP method from endpoint pattern', () => {
    const contract: PhrRouteContract = {
      product: 'phr',
      version: '1.0.0',
      routes: [
        {
          path: '/records/create',
          label: 'Create Record',
          apiEndpoint: '/api/v1/records/create',
          policyId: 'phr.records.create',
        },
      ],
    };

    const generator = createPhrApiClientGenerator();
    const clients = generator.generateApiClientSkeletons(contract);

    expect(clients[0].code).toContain("method: 'POST'");
  });

  it('handles metadata fallback for apiEndpoint and policyId', () => {
    const contract: PhrRouteContract = {
      product: 'phr',
      version: '1.0.0',
      routes: [
        {
          path: '/documents',
          label: 'Documents',
          metadata: {
            apiEndpoint: '/api/v1/documents',
            policyId: 'phr.documents.view',
          },
        },
      ],
    };

    const generator = createPhrApiClientGenerator();
    const clients = generator.generateApiClientSkeletons(contract);

    expect(clients).toHaveLength(1);
    expect(clients[0].code).toContain('/api/v1/documents');
    expect(clients[0].code).toContain('phr.documents.view');
  });

  it('generates correct function names from endpoints', () => {
    const contract: PhrRouteContract = {
      product: 'phr',
      version: '1.0.0',
      routes: [
        { path: '/patient-profile', label: 'Profile', apiEndpoint: '/api/v1/patient-profile', policyId: 'phr.profile.view' },
        { path: '/care-plans', label: 'Care Plans', apiEndpoint: '/api/v1/care-plans', policyId: 'phr.careplans.view' },
      ],
    };

    const generator = createPhrApiClientGenerator();
    const clients = generator.generateApiClientSkeletons(contract);

    expect(clients[0].functionName).toBe('patientProfile');
    expect(clients[1].functionName).toBe('carePlans');
  });
});
