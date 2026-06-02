import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { writeFileSync, mkdirSync, rmSync, existsSync } from 'fs';
import { join } from 'path';
import { execSync } from 'child_process';

const TEST_DIR = join(process.cwd(), '.tmp-i18n-test');

describe('check-route-i18n-keys', () => {
  beforeEach(() => {
    if (existsSync(TEST_DIR)) {
      rmSync(TEST_DIR, { recursive: true, force: true });
    }
    mkdirSync(TEST_DIR, { recursive: true });
  });

  afterEach(() => {
    if (existsSync(TEST_DIR)) {
      rmSync(TEST_DIR, { recursive: true, force: true });
    }
  });

  it('should pass when all stable routes have i18n keys in all locales', () => {
    const routeContract = {
      schemaVersion: '1.0.0',
      product: 'phr',
      routes: [
        {
          path: '/dashboard',
          label: 'Dashboard',
          description: 'Patient dashboard',
          group: 'care',
          minimumRole: 'patient',
          personas: ['patient'],
          tiers: ['core'],
          actions: ['view-dashboard'],
          cards: ['patient-summary'],
          stability: 'stable',
          i18nKey: 'phr.routes.dashboard.label',
          descriptionI18nKey: 'phr.routes.dashboard.description',
        },
      ],
    };

    const enLocale = {
      'phr.routes.dashboard.label': 'Dashboard',
      'phr.routes.dashboard.description': 'Patient dashboard',
    };

    const neLocale = {
      'phr.routes.dashboard.label': 'ड्यासबोर्ड',
      'phr.routes.dashboard.description': 'रोगी ड्यासबोर्ड',
    };

    const configDir = join(TEST_DIR, 'config');
    const localesDir = join(TEST_DIR, 'locales');
    mkdirSync(configDir, { recursive: true });
    mkdirSync(join(localesDir, 'en'), { recursive: true });
    mkdirSync(join(localesDir, 'ne'), { recursive: true });

    writeFileSync(join(configDir, 'phr-route-contract.json'), JSON.stringify(routeContract));
    writeFileSync(join(localesDir, 'en', 'common.json'), JSON.stringify(enLocale));
    writeFileSync(join(localesDir, 'ne', 'common.json'), JSON.stringify(neLocale));

    const scriptPath = join(process.cwd(), 'scripts/check-route-i18n-keys.mjs');
    const result = execSync(`node ${scriptPath}`, {
      cwd: TEST_DIR,
      env: { ...process.env, REPO_ROOT: TEST_DIR },
      stdio: 'pipe',
    }).toString();

    expect(result).toContain('✅ All stable routes have valid i18n keys');
  });

  it('should fail when stable route is missing i18nKey', () => {
    const routeContract = {
      schemaVersion: '1.0.0',
      product: 'phr',
      routes: [
        {
          path: '/dashboard',
          label: 'Dashboard',
          description: 'Patient dashboard',
          group: 'care',
          minimumRole: 'patient',
          personas: ['patient'],
          tiers: ['core'],
          actions: ['view-dashboard'],
          cards: ['patient-summary'],
          stability: 'stable',
        },
      ],
    };

    const enLocale = {};

    const configDir = join(TEST_DIR, 'config');
    const localesDir = join(TEST_DIR, 'locales');
    mkdirSync(configDir, { recursive: true });
    mkdirSync(join(localesDir, 'en'), { recursive: true });

    writeFileSync(join(configDir, 'phr-route-contract.json'), JSON.stringify(routeContract));
    writeFileSync(join(localesDir, 'en', 'common.json'), JSON.stringify(enLocale));

    const scriptPath = join(process.cwd(), 'scripts/check-route-i18n-keys.mjs');
    expect(() => {
      execSync(`node ${scriptPath}`, {
        cwd: TEST_DIR,
        env: { ...process.env, REPO_ROOT: TEST_DIR },
        stdio: 'pipe',
      });
    }).toThrow();
  });

  it('should fail when i18nKey is missing from locale file', () => {
    const routeContract = {
      schemaVersion: '1.0.0',
      product: 'phr',
      routes: [
        {
          path: '/dashboard',
          label: 'Dashboard',
          description: 'Patient dashboard',
          group: 'care',
          minimumRole: 'patient',
          personas: ['patient'],
          tiers: ['core'],
          actions: ['view-dashboard'],
          cards: ['patient-summary'],
          stability: 'stable',
          i18nKey: 'phr.routes.dashboard.label',
          descriptionI18nKey: 'phr.routes.dashboard.description',
        },
      ],
    };

    const enLocale = {};

    const configDir = join(TEST_DIR, 'config');
    const localesDir = join(TEST_DIR, 'locales');
    mkdirSync(configDir, { recursive: true });
    mkdirSync(join(localesDir, 'en'), { recursive: true });

    writeFileSync(join(configDir, 'phr-route-contract.json'), JSON.stringify(routeContract));
    writeFileSync(join(localesDir, 'en', 'common.json'), JSON.stringify(enLocale));

    const scriptPath = join(process.cwd(), 'scripts/check-route-i18n-keys.mjs');
    expect(() => {
      execSync(`node ${scriptPath}`, {
        cwd: TEST_DIR,
        env: { ...process.env, REPO_ROOT: TEST_DIR },
        stdio: 'pipe',
      });
    }).toThrow();
  });

  it('should skip non-stable routes', () => {
    const routeContract = {
      schemaVersion: '1.0.0',
      product: 'phr',
      routes: [
        {
          path: '/experimental',
          label: 'Experimental',
          description: 'Experimental feature',
          group: 'care',
          minimumRole: 'patient',
          personas: ['patient'],
          tiers: ['core'],
          actions: ['view-experimental'],
          cards: [],
          stability: 'experimental',
        },
      ],
    };

    const enLocale = {};

    const configDir = join(TEST_DIR, 'config');
    const localesDir = join(TEST_DIR, 'locales');
    mkdirSync(configDir, { recursive: true });
    mkdirSync(join(localesDir, 'en'), { recursive: true });

    writeFileSync(join(configDir, 'phr-route-contract.json'), JSON.stringify(routeContract));
    writeFileSync(join(localesDir, 'en', 'common.json'), JSON.stringify(enLocale));

    const scriptPath = join(process.cwd(), 'scripts/check-route-i18n-keys.mjs');
    const result = execSync(`node ${scriptPath}`, {
      cwd: TEST_DIR,
      env: { ...process.env, REPO_ROOT: TEST_DIR },
      stdio: 'pipe',
    }).toString();

    expect(result).toContain('✅ All stable routes have valid i18n keys');
  });
});
