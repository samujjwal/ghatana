/**
 * Tests for Product Mobile Screen Generator (YAPPC-T08)
 */

import { describe, it, expect } from 'vitest';
import { createMobileScreenGenerator, type ProductRouteContract } from './mobile-screen-generator';

describe('MobileScreenGenerator', () => {
  it('generates mobile screens for mobile routes', () => {
    const contract: ProductRouteContract = {
      product: 'sample-product',
      version: '1.0.0',
      routes: [
        {
          path: '/mobile/dashboard',
          label: 'Mobile Dashboard',
          description: 'Patient mobile dashboard',
          group: 'care',
          minimumRole: 'patient',
          surface: ['mobile'],
          i18nKey: 'sample.routes.mobileDashboard.label',
          descriptionI18nKey: 'sample.routes.mobileDashboard.description',
        },
      ],
    };

    const generator = createMobileScreenGenerator();
    const screens = generator.generateMobileScreens(contract);

    expect(screens).toHaveLength(1);
    expect(screens[0].screenName).toBe('MobileDashboard');
    expect(screens[0].filePath).toBe('src/screens/MobileDashboard.tsx');
    expect(screens[0].code).toContain('useSecureSession');
    expect(screens[0].code).toContain('useTranslation');
    expect(screens[0].code).toContain('accessible={true}');
  });

  it('generates screens for routes starting with /mobile/', () => {
    const contract: ProductRouteContract = {
      product: 'sample-product',
      version: '1.0.0',
      routes: [
        {
          path: '/mobile/records',
          label: 'Mobile Records',
          group: 'care',
          minimumRole: 'patient',
          surface: ['web'],
        },
      ],
    };

    const generator = createMobileScreenGenerator();
    const screens = generator.generateMobileScreens(contract);

    expect(screens).toHaveLength(1);
  });

  it('skips non-mobile routes', () => {
    const contract: ProductRouteContract = {
      product: 'sample-product',
      version: '1.0.0',
      routes: [
        {
          path: '/dashboard',
          label: 'Dashboard',
          group: 'care',
          minimumRole: 'patient',
          surface: ['web'],
        },
      ],
    };

    const generator = createMobileScreenGenerator();
    const screens = generator.generateMobileScreens(contract);

    expect(screens).toHaveLength(0);
  });

  it('includes secure session validation', () => {
    const contract: ProductRouteContract = {
      product: 'sample-product',
      version: '1.0.0',
      routes: [
        {
          path: '/mobile/profile',
          label: 'Mobile Profile',
          minimumRole: 'patient',
          surface: ['mobile'],
        },
      ],
    };

    const generator = createMobileScreenGenerator();
    const screens = generator.generateMobileScreens(contract);

    expect(screens[0].code).toContain('useSecureSession');
    expect(screens[0].code).toContain('isSessionValid');
    expect(screens[0].code).toContain('sessionExpired');
  });

  it('includes role-based access control', () => {
    const contract: ProductRouteContract = {
      product: 'sample-product',
      version: '1.0.0',
      routes: [
        {
          path: '/mobile/admin',
          label: 'Mobile Admin',
          minimumRole: 'admin',
          surface: ['mobile'],
        },
      ],
    };

    const generator = createMobileScreenGenerator();
    const screens = generator.generateMobileScreens(contract);

    expect(screens[0].code).toContain('hasRole');
    expect(screens[0].code).toContain('admin');
    expect(screens[0].code).toContain('accessDenied');
  });

  it('includes default accessibility when not specified', () => {
    const contract: ProductRouteContract = {
      product: 'sample-product',
      version: '1.0.0',
      routes: [
        {
          path: '/mobile/dashboard',
          label: 'Mobile Dashboard',
          minimumRole: 'patient',
          surface: ['mobile'],
        },
      ],
    };

    const generator = createMobileScreenGenerator();
    const screens = generator.generateMobileScreens(contract);

    expect(screens[0].code).toContain('accessible={true}');
    expect(screens[0].code).toContain('accessibilityRole="main"');
  });

  it('generates correct screen names from paths', () => {
    const contract: ProductRouteContract = {
      product: 'sample-product',
      version: '1.0.0',
      routes: [
        { path: '/mobile/patient-records', label: 'Records', minimumRole: 'patient', surface: ['mobile'] },
        { path: '/mobile/care-plans', label: 'Care Plans', minimumRole: 'patient', surface: ['mobile'] },
      ],
    };

    const generator = createMobileScreenGenerator();
    const screens = generator.generateMobileScreens(contract);

    expect(screens[0].screenName).toBe('MobilePatientRecords');
    expect(screens[1].screenName).toBe('MobileCarePlans');
  });
});
