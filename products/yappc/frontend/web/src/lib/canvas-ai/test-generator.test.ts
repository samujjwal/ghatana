/**
 * Tests for Product Test Generator (YAPPC-T06)
 */

import { describe, it, expect } from 'vitest';
import { createTestGenerator, type ProductRouteContract } from './test-generator';

describe('TestGenerator', () => {
  it('generates test skeletons for routes with test IDs', () => {
    const contract: ProductRouteContract = {
      product: 'sample-product',
      version: '1.0.0',
      routes: [
        {
          path: '/dashboard',
          label: 'Dashboard',
          testId: 'sample-dashboard-view-001',
          apiEndpoint: '/api/v1/dashboard',
          policyId: 'sample.dashboard.view',
        },
      ],
    };

    const generator = createTestGenerator();
    const tests = generator.generateTestSkeletons(contract);

    expect(tests).toHaveLength(2); // Frontend + backend
    expect(tests[0].testType).toBe('frontend');
    expect(tests[1].testType).toBe('backend');
    expect(tests[0].testName).toBe('SampleDashboardView001');
    expect(tests[0].code).toContain('describe');
  });

  it('skips routes without test IDs', () => {
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

    const generator = createTestGenerator();
    const tests = generator.generateTestSkeletons(contract);

    expect(tests).toHaveLength(0);
  });

  it('generates only frontend test for routes without API endpoints', () => {
    const contract: ProductRouteContract = {
      product: 'sample-product',
      version: '1.0.0',
      routes: [
        {
          path: '/profile',
          label: 'Profile',
          testId: 'sample-profile-view-001',
        },
      ],
    };

    const generator = createTestGenerator();
    const tests = generator.generateTestSkeletons(contract);

    expect(tests).toHaveLength(1);
    expect(tests[0].testType).toBe('frontend');
  });

  it('handles metadata fallback for testId, apiEndpoint, and policyId', () => {
    const contract: ProductRouteContract = {
      product: 'sample-product',
      version: '1.0.0',
      routes: [
        {
          path: '/documents',
          label: 'Documents',
          metadata: {
            testId: 'sample-documents-view-001',
            apiEndpoint: '/api/v1/documents',
            policyId: 'sample.documents.view',
          },
        },
      ],
    };

    const generator = createTestGenerator();
    const tests = generator.generateTestSkeletons(contract);

    expect(tests).toHaveLength(2);
    expect(tests[0].code).toContain('sample-documents-view-001');
    expect(tests[1].code).toContain('/api/v1/documents');
    expect(tests[1].code).toContain('sample.documents.view');
  });

  it('generates correct test names from test IDs', () => {
    const contract: ProductRouteContract = {
      product: 'sample-product',
      version: '1.0.0',
      routes: [
        { path: '/records', label: 'Records', testId: 'sample-records-view-001' },
        { path: '/care-plans', label: 'Care Plans', testId: 'sample-careplans-view-001' },
      ],
    };

    const generator = createTestGenerator();
    const tests = generator.generateTestSkeletons(contract);

    expect(tests[0].testName).toBe('SampleRecordsView001');
    expect(tests[1].testName).toBe('SampleCareplansView001');
  });
});
