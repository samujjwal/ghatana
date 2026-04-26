const PUBLIC_PATHS = new Set(['/health', '/live', '/ready']);

const PUBLIC_AUTH_PATH_SUFFIXES = new Set([
  '/auth/login',
  '/auth/register',
  '/auth/refresh',
  '/auth/forgot-password',
  '/auth/reset-password',
  '/auth/verify-email',
]);

export function isPublicPath(rawPath: string): boolean {
  const path = rawPath.split('?')[0];
  if (PUBLIC_PATHS.has(path)) {
    return true;
  }

  for (const suffix of PUBLIC_AUTH_PATH_SUFFIXES) {
    if (path.endsWith(suffix)) {
      return true;
    }
  }

  return false;
}
