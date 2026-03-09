/**
 * Version negotiation and compatibility checking utilities
 */

import { BRIDGE_PROTOCOL_VERSION, MIN_SUPPORTED_VERSION, SUPPORTED_VERSIONS, type HandshakePayload } from './schema';

export interface VersionCheckResult {
  compatible: boolean;
  reason?: string;
  negotiatedVersion?: string;
}

/**
 * Check if a version string is supported
 */
export function isVersionSupported(version: string): boolean {
  return (SUPPORTED_VERSIONS as readonly string[]).includes(version);
}

/**
 * Compare two semver strings
 * @returns -1 if a < b, 0 if a === b, 1 if a > b
 */
export function compareVersions(a: string, b: string): number {
  const aParts = a.split('.').map(Number);
  const bParts = b.split('.').map(Number);

  for (let i = 0; i < 3; i++) {
    if (aParts[i] > bParts[i]) return 1;
    if (aParts[i] < bParts[i]) return -1;
  }

  return 0;
}

/**
 * Check if client version meets minimum requirements
 */
export function meetsMinimumVersion(clientVersion: string): boolean {
  return compareVersions(clientVersion, MIN_SUPPORTED_VERSION) >= 0;
}

/**
 * Validate handshake and negotiate protocol version
 */
export function validateHandshake(handshake: HandshakePayload): VersionCheckResult {
  const { version } = handshake;

  // Check if version meets minimum requirement
  if (!meetsMinimumVersion(version)) {
    return {
      compatible: false,
      reason: `Version ${version} is below minimum supported version ${MIN_SUPPORTED_VERSION}`,
    };
  }

  // Check if exact version is supported
  if (!isVersionSupported(version)) {
    return {
      compatible: false,
      reason: `Version ${version} is not in supported versions: ${SUPPORTED_VERSIONS.join(', ')}`,
    };
  }

  return {
    compatible: true,
    negotiatedVersion: version,
  };
}

/**
 * Create a handshake payload for the current protocol version
 */
export function createHandshakePayload(options?: {
  capabilities?: Record<string, boolean>;
  clientId?: string;
}): HandshakePayload {
  return {
    version: BRIDGE_PROTOCOL_VERSION,
    capabilities: options?.capabilities,
    clientId: options?.clientId,
    timestamp: new Date().toISOString(),
  };
}

export { BRIDGE_PROTOCOL_VERSION, MIN_SUPPORTED_VERSION, SUPPORTED_VERSIONS };
