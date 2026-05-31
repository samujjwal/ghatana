import { describe, expect, it } from 'vitest';
import {
  analyzeKernelProductGaps,
  createDeploymentPlan,
  createKernelProductLifecyclePlan,
  proposeEnhancements,
  type KernelProductContract,
} from './kernel-product-accelerator';

const sampleContract: KernelProductContract = {
  product: 'sample-product',
  version: '1.0.0',
  pluginDependencies: ['policy', 'observability'],
  routes: [
    {
      path: '/overview',
      label: 'Overview',
      stability: 'stable',
      surface: ['web'],
      apiEndpoint: '/api/v1/overview',
      policyId: 'sample.overview.read',
      i18nKey: 'route.overview.label',
      descriptionI18nKey: 'route.overview.description',
    },
    {
      path: '/mobile/detail',
      label: 'Detail',
      stability: 'stable',
      surface: ['mobile'],
      apiEndpoint: '/api/v1/detail',
      metadata: { cachePolicy: 'unsafe' },
    },
    {
      path: '/admin-preview',
      label: 'Admin Preview',
      stability: 'hidden',
      apiEndpoint: '/api/v1/admin-preview',
    },
  ],
};

describe('kernel product accelerator', () => {
  it('models generic Kernel lifecycle stages', () => {
    const plan = createKernelProductLifecyclePlan(sampleContract);

    expect(plan.map((stage) => stage.stage)).toEqual([
      'ideation',
      'requirements',
      'design',
      'contract',
      'scaffold',
      'implementation',
      'validation',
      'deployment',
      'enhancement',
    ]);
  });

  it('analyzes generic product contract gaps without product-specific categories', () => {
    const gaps = analyzeKernelProductGaps(sampleContract);

    expect(gaps.map((gap) => gap.category)).toEqual([
      'api-missing-policy',
      'mobile-route-missing-screen',
      'raw-i18n',
      'unsafe-storage',
      'api-missing-policy',
      'hidden-route-mounted',
    ]);
  });

  it('creates deployment and enhancement plans from generic Kernel inputs', () => {
    expect(createDeploymentPlan(sampleContract)).toHaveLength(3);
    expect(proposeEnhancements([
      { checkId: 'route-contract', pass: true },
      { checkId: 'mobile-route-missing-screen', pass: false, violations: ['missing screen key'] },
    ])).toEqual([
      {
        sourceCheckId: 'mobile-route-missing-screen',
        priority: 'medium',
        title: 'Resolve failing Kernel validation: mobile-route-missing-screen',
        nextAction: 'missing screen key',
      },
    ]);
  });
});
