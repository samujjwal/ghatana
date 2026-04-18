import type { FastifyReply, FastifyRequest } from 'fastify';

export const CURRENT_API_VERSION = '1';
const SUPPORTED_API_VERSIONS = new Set<string>(['1', 'v1']);
const VERSION_HEADER = 'x-api-version';
const COMPATIBILITY_HEADER = 'x-api-compatibility';

function normalizeVersion(rawVersion: string): string {
  return rawVersion.trim().toLowerCase();
}

export function isVersionedApiPath(url: string): boolean {
  return url.startsWith('/api') || url.startsWith('/v1');
}

export function applyVersionHeaders(reply: FastifyReply): void {
  reply.header(VERSION_HEADER, CURRENT_API_VERSION);
  reply.header(COMPATIBILITY_HEADER, 'v1');
  reply.header('deprecation', 'false');
}

export function resolveRequestedVersion(request: FastifyRequest): string | null {
  const xApiVersion = request.headers[VERSION_HEADER];
  if (typeof xApiVersion === 'string' && xApiVersion.trim().length > 0) {
    return normalizeVersion(xApiVersion);
  }

  const acceptVersion = request.headers['accept-version'];
  if (typeof acceptVersion === 'string' && acceptVersion.trim().length > 0) {
    return normalizeVersion(acceptVersion);
  }

  return null;
}

export function isSupportedVersion(requestedVersion: string): boolean {
  return SUPPORTED_API_VERSIONS.has(requestedVersion);
}

export function buildVersionErrorBody(requestedVersion: string): Record<string, unknown> {
  return {
    error: 'Unsupported API version',
    requestedVersion,
    supportedVersions: ['v1'],
    currentVersion: 'v1',
  };
}
