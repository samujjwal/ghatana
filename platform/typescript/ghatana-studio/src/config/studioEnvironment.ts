export interface StudioEnvironmentConfig {
  readonly version: string;
  readonly docsUrl: string;
}

const STUDIO_VERSION_ENV = 'VITE_STUDIO_VERSION';
const STUDIO_DOCS_URL_ENV = 'VITE_STUDIO_DOCS_URL';

export const DEFAULT_STUDIO_VERSION = 'dev';
export const DEFAULT_STUDIO_DOCS_URL = 'https://docs.ghatana.dev';

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
  };
}

export const STUDIO_ENVIRONMENT_CONFIG = resolveStudioEnvironmentConfig();
