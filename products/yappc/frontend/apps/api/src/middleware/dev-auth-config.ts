type EnvironmentMap = NodeJS.ProcessEnv;

export function isDevelopmentEnvironment(
  env: EnvironmentMap = process.env
): boolean {
  return (env.NODE_ENV ?? 'development') === 'development';
}

export function isContinuousIntegrationEnvironment(
  env: EnvironmentMap = process.env
): boolean {
  return env.CI === 'true';
}

export function isDevAuthBypassEnabled(
  env: EnvironmentMap = process.env
): boolean {
  return (
    isDevelopmentEnvironment(env) &&
    !isContinuousIntegrationEnvironment(env) &&
    env.ENABLE_DEV_AUTH_BYPASS === 'true'
  );
}

export function assertDevAuthBypassAllowed(
  env: EnvironmentMap = process.env
): void {
  if (env.ENABLE_DEV_AUTH_BYPASS !== 'true') {
    return;
  }

  if (!isDevelopmentEnvironment(env)) {
    throw new Error(
      'ENABLE_DEV_AUTH_BYPASS may only be enabled when NODE_ENV=development'
    );
  }

  if (isContinuousIntegrationEnvironment(env)) {
    throw new Error('ENABLE_DEV_AUTH_BYPASS may not run in CI');
  }
}
