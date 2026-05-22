import { describe, expect, it } from 'vitest';

import {
  DEFAULT_STUDIO_PILOT_PRODUCT_UNIT_ID,
  DEFAULT_STUDIO_DOCS_URL,
  DEFAULT_STUDIO_VERSION,
  DEFAULT_STUDIO_DEPLOYMENT_PROFILE,
  isProductionStudioProfile,
  readStudioEnvironment,
  resolveStudioEnvironmentConfig,
} from '../studioEnvironment';

describe('studioEnvironment', () => {
  it('returns fallback values when environment variables are missing', () => {
    const config = resolveStudioEnvironmentConfig(undefined);

    expect(config.version).toBe(DEFAULT_STUDIO_VERSION);
    expect(config.docsUrl).toBe(DEFAULT_STUDIO_DOCS_URL);
    expect(config.pilotDefaultProductUnitId).toBe(DEFAULT_STUDIO_PILOT_PRODUCT_UNIT_ID);
    expect(config.deploymentProfile).toBe(DEFAULT_STUDIO_DEPLOYMENT_PROFILE);
  });

  it('uses configured values when environment variables are present and non-empty', () => {
    const config = resolveStudioEnvironmentConfig({
      VITE_STUDIO_VERSION: '1.2.3',
      VITE_STUDIO_DOCS_URL: 'https://docs.example.com/studio',
      VITE_STUDIO_PILOT_DEFAULT_PRODUCT_UNIT_ID: 'finance',
      VITE_STUDIO_DEPLOYMENT_PROFILE: 'staging',
    });

    expect(config.version).toBe('1.2.3');
    expect(config.docsUrl).toBe('https://docs.example.com/studio');
    expect(config.pilotDefaultProductUnitId).toBe('finance');
    expect(config.deploymentProfile).toBe('staging');
  });

  it('treats empty string values as missing and falls back safely', () => {
    const version = readStudioEnvironment('VITE_STUDIO_VERSION', DEFAULT_STUDIO_VERSION, {
      VITE_STUDIO_VERSION: '   ',
    });

    expect(version).toBe(DEFAULT_STUDIO_VERSION);
  });

  it('detects the production deployment profile', () => {
    expect(isProductionStudioProfile({ VITE_STUDIO_DEPLOYMENT_PROFILE: 'production' })).toBe(true);
    expect(isProductionStudioProfile({ VITE_STUDIO_DEPLOYMENT_PROFILE: 'staging' })).toBe(false);
  });

  it('rejects unknown deployment profiles', () => {
    expect(() =>
      resolveStudioEnvironmentConfig({ VITE_STUDIO_DEPLOYMENT_PROFILE: 'qa' }),
    ).toThrow(/must be one of/);
  });
});
