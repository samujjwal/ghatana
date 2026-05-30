/**
 * Tests for PHR Test Generator (YAPPC-T06)
 */

import { describe, it, expect } from 'vitest';
import { createPhrTestGenerator, type PhrRouteContract } from './phr-test-generator';

describe('PhrTestGenerator', () => {
  it('generates test skeletons for routes with test IDs', () => {
    const contract: PhrRouteContract = {
      product: 'phr',
      version: '1.0.0',
      routes: [
        {
          path: '/dashboard',
          label: 'Dashboard',
          testId: 'phr-dashboard-view-001',
          apiEndpoint: '/api/v1/dashboard',
          policyId: 'phr.dashboard.view',
        },
      ],
    };

    const generator = createPhrTestGenerator();
    const tests = generator.generateTestSkeletons(contract);

    expect(tests).toHaveLength(2); // Frontend + backend
    expect(tests[0].testType).toBe('frontend');
    expect(tests[1].testType).toBe('backend');
    expect(tests[0].testName).toBe('PhrDashboardView001');
    expect(tests[0].code).toContain('describe');
  });

  it('skips routes without test IDs', () => {
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

    const generator = createPhrTestGenerator();
    const tests = generator.generateTestSkeletons(contract);

    expect(tests).toHaveLength(0);
  });

  it('generates only frontend test for routes without API endpoints', () => {
    const contract: PhrRouteContract = {
      product: 'phr',
      version: '1.0.0',
      routes: [
        {
          path: '/profile',
          label: 'Profile',
          testId: 'phr-profile-view-001',
        },
      ],
    };

    const generator = createPhrTestGenerator();
    const tests = generator.generateTestSkeletons(contract);

    expect(tests).toHaveLength(1);
    expect(tests[0].testType).toBe('frontend');
  });

  it('handles metadata fallback for testId, apiEndpoint, and policyId', () => {
    const contract: PhrRouteContract = {
      product: 'phr',
      version: '1.0.0',
      routes: [
        {
          path: '/documents',
          label: 'Documents',
          metadata: {
            testId: 'phr-documents-view-001',
            apiEndpoint: '/api/v1/documents',
            policyId: 'phr.documents.view',
          },
        },
      ],
    };

    const generator = createPhrTestGenerator();
    const tests = generator.generateTestSkeletons(contract);

    expect(tests).toHaveLength(2);
    expect(tests[0].code).toContain('phr-documents-view-001');
    expect(tests[1].code).toContain('/api/v1/documents');
    expect(tests[1].code).toContain('phr.documents.view');
  });

  it('generates correct test names from test IDs', () => {
    const contract: PhrRouteContract = {
      product: 'phr',
      version: '1.0.0',
      routes: [
        { path: '/records', label: 'Records', testId: 'phr-records-view-001' },
        { path: '/care-plans', label: 'Care Plans', testId: 'phr-careplans-view-001' },
      ],
    };

    const generator = createPhrTestGenerator();
    const tests = generator.generateTestSkeletons(contract);

    expect(tests[0].testName).toBe('PhrRecordsView001');
    expect(tests[2].testName).toBe('PhrCareplansView001');
  });
});
