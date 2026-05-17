export interface StudioEnvironmentConfig {
  readonly version: string;
  readonly docsUrl: string;
  readonly pilotDefaultProductUnitId: string;
}

const STUDIO_VERSION_ENV = 'VITE_STUDIO_VERSION';
const STUDIO_DOCS_URL_ENV = 'VITE_STUDIO_DOCS_URL';
const STUDIO_PILOT_DEFAULT_PRODUCT_UNIT_ENV = 'VITE_STUDIO_PILOT_DEFAULT_PRODUCT_UNIT_ID';

export const DEFAULT_STUDIO_VERSION = 'dev';
export const DEFAULT_STUDIO_DOCS_URL = 'https://docs.ghatana.dev';
export const DEFAULT_STUDIO_PILOT_PRODUCT_UNIT_ID = 'digital-marketing';

export function readStudioEnvironment(
  name: string,
  fallback: string,
  env: Readonly<Record<string, unknown>> | undefined =
    (import.meta as ImportMeta & { readonly env?: Record<string, unknown> }).env,
): string {
  const value = env?.[name];
  return typeof value === 'string' && value.trim().length > 0 ? value : fallback;
}

export function resolveStudioEnvironmentConfig(
  env: Readonly<Record<string, unknown>> | undefined =
    (import.meta as ImportMeta & { readonly env?: Record<string, unknown> }).env,
): StudioEnvironmentConfig {
  return {
    version: readStudioEnvironment(STUDIO_VERSION_ENV, DEFAULT_STUDIO_VERSION, env),
    docsUrl: readStudioEnvironment(STUDIO_DOCS_URL_ENV, DEFAULT_STUDIO_DOCS_URL, env),
    pilotDefaultProductUnitId: readStudioEnvironment(
      STUDIO_PILOT_DEFAULT_PRODUCT_UNIT_ENV,
      DEFAULT_STUDIO_PILOT_PRODUCT_UNIT_ID,
      env,
    ),
  };
}

export const STUDIO_ENVIRONMENT_CONFIG = resolveStudioEnvironmentConfig();
