/**
 * Tests for PHR Page Skeleton Generator (YAPPC-T03)
 */

import { describe, it, expect } from 'vitest';
import { createPhrPageSkeletonGenerator } from './phr-page-skeleton-generator';

describe('PhrPageSkeletonGenerator', () => {
  it('generates page skeletons for web routes', () => {
    const contract = {
      product: 'phr',
      version: '1.0.0',
      roleOrder: { viewer: 0, patient: 1, caregiver: 2, clinician: 3, admin: 4 },
      routes: [
        {
          path: '/dashboard',
          label: 'Dashboard',
          description: 'Patient dashboard',
          group: 'care',
          minimumRole: 'patient',
          surface: ['web'],
          i18nKey: 'phr.routes.dashboard.label',
          descriptionI18nKey: 'phr.routes.dashboard.description',
        },
      ],
    };

    const generator = createPhrPageSkeletonGenerator();
    const skeletons = generator.generatePageSkeletons(contract);

    expect(skeletons).toHaveLength(1);
    expect(skeletons[0].componentName).toBe('Dashboard');
    expect(skeletons[0].filePath).toBe('src/pages/Dashboard.tsx');
    expect(skeletons[0].code).toContain('Dashboard Page');
    expect(skeletons[0].code).toContain('useTranslation');
  });

  it('skips mobile-only routes', () => {
    const contract = {
      product: 'phr',
      version: '1.0.0',
      roleOrder: { viewer: 0, patient: 1, caregiver: 2, clinician: 3, admin: 4 },
      routes: [
        {
          path: '/mobile/dashboard',
          label: 'Mobile Dashboard',
          group: 'care',
          minimumRole: 'patient',
          surface: ['mobile'],
        },
      ],
    };

    const generator = createPhrPageSkeletonGenerator();
    const skeletons = generator.generatePageSkeletons(contract);

    expect(skeletons).toHaveLength(0);
  });

  it('skips blocked routes', () => {
    const contract = {
      product: 'phr',
      version: '1.0.0',
      roleOrder: { viewer: 0, patient: 1, caregiver: 2, clinician: 3, admin: 4 },
      routes: [
        {
          path: '/admin',
          label: 'Admin',
          group: 'admin',
          minimumRole: 'admin',
          surface: ['web'],
          stability: 'blocked',
        },
      ],
    };

    const generator = createPhrPageSkeletonGenerator();
    const skeletons = generator.generatePageSkeletons(contract);

    expect(skeletons).toHaveLength(0);
  });

  it('includes accessibility attributes when present', () => {
    const contract = {
      product: 'phr',
      version: '1.0.0',
      roleOrder: { viewer: 0, patient: 1, caregiver: 2, clinician: 3, admin: 4 },
      routes: [
        {
          path: '/records',
          label: 'Records',
          group: 'care',
          minimumRole: 'patient',
          surface: ['web'],
          accessibility: { ariaLabel: true, keyboardNav: true },
        },
      ],
    };

    const generator = createPhrPageSkeletonGenerator();
    const skeletons = generator.generatePageSkeletons(contract);

    expect(skeletons[0].code).toContain('aria-label');
    expect(skeletons[0].dependencies).toContain('@ghatana/design-system');
  });

  it('generates correct component names from paths', () => {
    const contract = {
      product: 'phr',
      version: '1.0.0',
      roleOrder: { viewer: 0, patient: 1, caregiver: 2, clinician: 3, admin: 4 },
      routes: [
        { path: '/patient-records', label: 'Records', group: 'care', minimumRole: 'patient', surface: ['web'] },
        { path: '/care-plans', label: 'Care Plans', group: 'care', minimumRole: 'patient', surface: ['web'] },
      ],
    };

    const generator = createPhrPageSkeletonGenerator();
    const skeletons = generator.generatePageSkeletons(contract);

    expect(skeletons[0].componentName).toBe('PatientRecords');
    expect(skeletons[1].componentName).toBe('CarePlans');
  });
});
