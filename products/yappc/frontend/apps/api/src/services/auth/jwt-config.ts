export interface JwtRuntimeConfig {
  accessTokenSecret: string;
  refreshTokenSecret: string;
  accessTokenExpiry: string;
  refreshTokenExpiry: string;
}

type EnvironmentMap = NodeJS.ProcessEnv;

function requireSecret(
  name: 'JWT_ACCESS_SECRET' | 'JWT_REFRESH_SECRET',
  env: EnvironmentMap
): string {
  const value = env[name];
  if (!value) {
    throw new Error(`${name} is required`);
  }

  return value;
}

export function getJwtAccessSecret(env: EnvironmentMap = process.env): string {
  return requireSecret('JWT_ACCESS_SECRET', env);
}

export function getJwtRefreshSecret(env: EnvironmentMap = process.env): string {
  return requireSecret('JWT_REFRESH_SECRET', env);
}

export function getJwtRuntimeConfig(
  env: EnvironmentMap = process.env
): JwtRuntimeConfig {
  return {
    accessTokenSecret: getJwtAccessSecret(env),
    refreshTokenSecret: getJwtRefreshSecret(env),
    accessTokenExpiry: env.JWT_ACCESS_EXPIRY || '15m',
    refreshTokenExpiry: env.JWT_REFRESH_EXPIRY || '7d',
  };
}
