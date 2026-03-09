/**
 * Production build configuration for DCMAAR extension
 */

export interface BuildConfig {
  NODE_ENV: 'development' | 'production' | 'test';
  ENABLE_TEST_HOOKS: boolean;
  ENABLE_DEBUG_LOGGING: boolean;
  ENABLE_INTENT_DEMO: boolean;
  LOG_LEVEL: string;
}

/**
 * Get build-time configuration
 */
export function getBuildConfig(): BuildConfig {
  // Prefer Vite env when available (import.meta.env), fall back to process.env for Node/tests.
  const viteEnv =
    (import.meta as unknown as { env?: Record<string, string | boolean | undefined> }).env || {};
  const pe =
    (typeof process !== 'undefined' ? (process.env as Record<string, string | undefined>) : {}) ||
    {};

  // Resolve NODE_ENV/MODE
  const mode = (viteEnv.MODE as string | undefined) || pe.MODE || pe.NODE_ENV || 'development';
  const nodeEnv = (
    mode === 'production' || mode === 'development' || mode === 'test' ? mode : 'development'
  ) as BuildConfig['NODE_ENV'];
  const isProduction = nodeEnv === 'production' || (!!viteEnv.PROD && viteEnv.PROD !== 'false');
  const isTest = nodeEnv === 'test';

  // Read feature flags from Vite env first, then process.env as fallback
  const VITE_ENABLE_TEST_HOOKS =
    (viteEnv.VITE_ENABLE_TEST_HOOKS as string | undefined) ?? pe.VITE_ENABLE_TEST_HOOKS;
  const DCMAAR_ENABLE_DEBUG =
    (viteEnv.DCMAAR_ENABLE_DEBUG as string | undefined) ?? pe.DCMAAR_ENABLE_DEBUG;
  const DCMAAR_ENABLE_INTENT_DEMO =
    (viteEnv.DCMAAR_ENABLE_INTENT_DEMO as string | undefined) ?? pe.DCMAAR_ENABLE_INTENT_DEMO;
  const DCMAAR_LOG_LEVEL = (viteEnv.DCMAAR_LOG_LEVEL as string | undefined) ?? pe.DCMAAR_LOG_LEVEL;

  return {
    NODE_ENV: nodeEnv,
    // Allow explicit VITE_ENABLE_TEST_HOOKS=true to force-enable hooks; otherwise
    // enable in non-production (dev/test) builds.
    ENABLE_TEST_HOOKS:
      VITE_ENABLE_TEST_HOOKS === 'true' ||
      (!isProduction && (isTest || VITE_ENABLE_TEST_HOOKS === 'true')),
    // Debug logging disabled in production unless explicitly enabled
    ENABLE_DEBUG_LOGGING: !isProduction || DCMAAR_ENABLE_DEBUG === 'true',
    // Intent demo disabled in production unless explicitly enabled
    ENABLE_INTENT_DEMO: !isProduction && DCMAAR_ENABLE_INTENT_DEMO === 'true',
    // Log level
    LOG_LEVEL: DCMAAR_LOG_LEVEL || (isProduction ? 'error' : 'info'),
  };
}

/**
 * Tree-shakeable guards for development-only code
 */
const __MODE__ =
  ((import.meta as unknown as { env?: Record<string, string | boolean | undefined> }).env?.MODE as
    | string
    | undefined) ||
  (typeof process !== 'undefined' ? process.env.NODE_ENV : undefined) ||
  'development';
export const __DEV__ = __MODE__ !== 'production';
export const __TEST__ = __MODE__ === 'test';
export const __PROD__ = __MODE__ === 'production';

/**
 * Build-time feature flags
 */
export const FEATURES = {
  TEST_HOOKS: getBuildConfig().ENABLE_TEST_HOOKS,
  DEBUG_LOGGING: getBuildConfig().ENABLE_DEBUG_LOGGING,
  INTENT_DEMO: getBuildConfig().ENABLE_INTENT_DEMO,
} as const;
