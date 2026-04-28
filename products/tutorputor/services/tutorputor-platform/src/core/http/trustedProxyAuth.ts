import type { FastifyRequest } from "fastify";

const PRIVATE_IPV4_RANGES = [
  /^127\./,
  /^10\./,
  /^192\.168\./,
  /^172\.(1[6-9]|2\d|3[0-1])\./,
];

function normalizeIpAddress(ipAddress: string | undefined): string {
  if (!ipAddress) {
    return "";
  }

  if (ipAddress.startsWith("::ffff:")) {
    return ipAddress.slice(7);
  }

  return ipAddress;
}

function getTrustedProxySecretHeader(request: FastifyRequest): string | null {
  const headerValue = request.headers["x-trusted-proxy-secret"];
  if (!headerValue) {
    return null;
  }

  return Array.isArray(headerValue) ? headerValue[0] ?? null : headerValue;
}

function getHeaderValue(
  request: FastifyRequest,
  headerName:
    | 'x-tenant-id'
    | 'x-user-id'
    | 'x-user-role'
    | 'authorization',
): string | null {
  const headerValue = request.headers[headerName];
  if (!headerValue) {
    return null;
  }

  return Array.isArray(headerValue) ? headerValue[0] ?? null : headerValue;
}

/**
 * Asserts that trusted-proxy auth is never active in production.
 * Call once at startup (before any request is accepted).
 * Throws if the combination of NODE_ENV=production + TRUST_PROXY_AUTH_HEADERS=true
 * is detected, since that opens an impersonation surface on the external network.
 */
export function assertTrustedProxyNotEnabledInProduction(): void {
  if (
    process.env.NODE_ENV === "production" &&
    process.env.TRUST_PROXY_AUTH_HEADERS === "true"
  ) {
    throw new Error(
      "[security] TRUST_PROXY_AUTH_HEADERS=true is not permitted in production. " +
        "Remove the environment variable or set NODE_ENV to a non-production value. " +
        "All production services must authenticate via cryptographic JWT.",
    );
  }
}

export function isTrustedProxyAuthEnabled(): boolean {
  // Trusted proxy auth must be explicitly enabled via configuration
  // This prevents accidental bypass of JWT authentication
  return process.env.TRUST_PROXY_AUTH_HEADERS === "true";
}

export function hasTrustedProxySecret(request: FastifyRequest): boolean {
  const configuredSecret = process.env.TRUST_PROXY_AUTH_SHARED_SECRET;

  // Secret must be configured for trusted proxy auth to work
  if (!configuredSecret) {
    return false;
  }

  return getTrustedProxySecretHeader(request) === configuredSecret;
}

export function hasTrustedProxyIdentityHeaders(request: FastifyRequest): boolean {
  if (getHeaderValue(request, 'authorization')) {
    return false;
  }

  return (
    (getHeaderValue(request, 'x-tenant-id')?.length ?? 0) > 0 &&
    (getHeaderValue(request, 'x-user-id')?.length ?? 0) > 0 &&
    (getHeaderValue(request, 'x-user-role')?.length ?? 0) > 0
  );
}

export function isPrivateOrLoopbackIp(ipAddress: string | undefined): boolean {
  const normalizedIp = normalizeIpAddress(ipAddress);
  if (!normalizedIp) {
    return false;
  }

  return (
    normalizedIp === "::1" ||
    normalizedIp.toLowerCase() === "localhost" ||
    PRIVATE_IPV4_RANGES.some((pattern) => pattern.test(normalizedIp))
  );
}

export function canUseTrustedProxyAuth(request: FastifyRequest): boolean {
  return (
    isTrustedProxyAuthEnabled() &&
    isPrivateOrLoopbackIp(request.ip) &&
    hasTrustedProxyIdentityHeaders(request) &&
    hasTrustedProxySecret(request)
  );
}
