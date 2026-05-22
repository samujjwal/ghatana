export interface StudioEnvironmentConfig {
  readonly version: string;
  readonly docsUrl: string;
  readonly pilotDefaultProductUnitId: string;
  readonly deploymentProfile: StudioDeploymentProfile;
}

const STUDIO_VERSION_ENV = 'VITE_STUDIO_VERSION';
const STUDIO_DOCS_URL_ENV = 'VITE_STUDIO_DOCS_URL';
const STUDIO_PILOT_DEFAULT_PRODUCT_UNIT_ENV = 'VITE_STUDIO_PILOT_DEFAULT_PRODUCT_UNIT_ID';
const STUDIO_DEPLOYMENT_PROFILE_ENV = 'VITE_STUDIO_DEPLOYMENT_PROFILE';

export const DEFAULT_STUDIO_VERSION = 'dev';
export const DEFAULT_STUDIO_DOCS_URL = 'https://docs.ghatana.dev';
export const DEFAULT_STUDIO_PILOT_PRODUCT_UNIT_ID = 'digital-marketing';
export const DEFAULT_STUDIO_DEPLOYMENT_PROFILE = 'local';

export type StudioDeploymentProfile = 'local' | 'development' | 'staging' | 'production';

export class StudioProductionProfileError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'StudioProductionProfileError';
  }
}

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
    deploymentProfile: readStudioDeploymentProfile(env),
  };
}

export const STUDIO_ENVIRONMENT_CONFIG = resolveStudioEnvironmentConfig();

export function readStudioDeploymentProfile(
  env: Readonly<Record<string, unknown>> | undefined =
    (import.meta as ImportMeta & { readonly env?: Record<string, unknown> }).env,
): StudioDeploymentProfile {
  const raw = readStudioEnvironment(
    STUDIO_DEPLOYMENT_PROFILE_ENV,
    DEFAULT_STUDIO_DEPLOYMENT_PROFILE,
    env,
  ).toLowerCase();

  if (raw === 'local' || raw === 'development' || raw === 'staging' || raw === 'production') {
    return raw;
  }

  throw new StudioProductionProfileError(
    `${STUDIO_DEPLOYMENT_PROFILE_ENV} must be one of local, development, staging, or production.`,
  );
}

export function isProductionStudioProfile(
  env: Readonly<Record<string, unknown>> | undefined =
    (import.meta as ImportMeta & { readonly env?: Record<string, unknown> }).env,
): boolean {
  return readStudioDeploymentProfile(env) === 'production';
}
