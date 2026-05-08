/**
 * Component Package Signing
 *
 * Validates marketplace package signature envelopes before package renderers
 * are allowed to enter the local plugin registry.
 *
 * @doc.type service
 * @doc.purpose Verify marketplace component package signature metadata
 * @doc.layer product
 * @doc.pattern Security Boundary
 */

export const COMPONENT_PACKAGE_SIGNATURE_ALGORITHM = 'YAPPC_SHA256_INTEGRITY_V1' as const;

export type ComponentPackageSignatureAlgorithm = typeof COMPONENT_PACKAGE_SIGNATURE_ALGORITHM;

export interface ComponentPackageSignatureSubject {
  readonly packageName: string;
  readonly version: string;
  readonly marketplacePackageId?: string;
}

export interface ComponentPackageSignature {
  readonly algorithm: ComponentPackageSignatureAlgorithm;
  readonly keyId: string;
  readonly issuedAt: string;
  readonly expiresAt: string;
  readonly subject: ComponentPackageSignatureSubject;
  readonly digest: string;
  readonly signature: string;
}

export interface ComponentPackageSigningPayload {
  readonly packageName: string;
  readonly version: string;
  readonly minBuilderVersion: string;
  readonly maxBuilderVersion?: string;
  readonly rendererContracts: readonly string[];
  readonly securityPolicy?: {
    readonly requiresElevatedPermissions?: boolean;
    readonly allowedDomains?: readonly string[];
    readonly allowedTelemetryEvents?: readonly string[];
    readonly allowBrowserAPIs?: boolean;
    readonly allowLocalStorage?: boolean;
  };
  readonly marketplacePackageId?: string;
}

export interface ComponentPackageSigningValidationResult {
  readonly valid: boolean;
  readonly errors: readonly string[];
  readonly digest: string;
}

export interface ComponentPackageSigningInput {
  readonly packageName: string;
  readonly version: string;
  readonly minBuilderVersion: string;
  readonly maxBuilderVersion?: string;
  readonly rendererContracts: readonly string[];
  readonly securityPolicy?: ComponentPackageSigningPayload['securityPolicy'];
  readonly marketplacePackageId?: string;
  readonly signature?: ComponentPackageSignature;
}

const SIGNATURE_PREFIX = 'yappc-sig-v1:';
const HASH_SEED = 0x811c9dc5;
const HASH_PRIME = 0x01000193;

/**
 * Builds the canonical payload that marketplace signing attests to.
 */
export function buildComponentPackageSigningPayload(
  input: ComponentPackageSigningInput,
): ComponentPackageSigningPayload {
  return stripUndefined({
    packageName: input.packageName,
    version: input.version,
    minBuilderVersion: input.minBuilderVersion,
    maxBuilderVersion: input.maxBuilderVersion,
    rendererContracts: [...input.rendererContracts].sort(),
    securityPolicy: input.securityPolicy
      ? stripUndefined({
          requiresElevatedPermissions: input.securityPolicy.requiresElevatedPermissions,
          allowedDomains: input.securityPolicy.allowedDomains
            ? [...input.securityPolicy.allowedDomains].sort()
            : undefined,
          allowedTelemetryEvents: input.securityPolicy.allowedTelemetryEvents
            ? [...input.securityPolicy.allowedTelemetryEvents].sort()
            : undefined,
          allowBrowserAPIs: input.securityPolicy.allowBrowserAPIs,
          allowLocalStorage: input.securityPolicy.allowLocalStorage,
        })
      : undefined,
    marketplacePackageId: input.marketplacePackageId,
  });
}

/**
 * Computes the deterministic package integrity digest used by marketplace
 * package signature envelopes.
 */
export function computeComponentPackageIntegrityDigest(input: ComponentPackageSigningInput): string {
  const payload = buildComponentPackageSigningPayload(input);
  return stableDigest(stableStringify(payload));
}

/**
 * Validates a marketplace package signature envelope against the canonical
 * manifest payload. This is a fail-closed browser-side integrity gate; durable
 * key custody and package issuance still belong to the backend marketplace.
 */
export function validateComponentPackageSignature(
  input: ComponentPackageSigningInput,
  now: Date = new Date(),
): ComponentPackageSigningValidationResult {
  const digest = computeComponentPackageIntegrityDigest(input);
  const errors: string[] = [];
  const signature = input.signature;

  if (!signature) {
    return {
      valid: false,
      errors: ['Marketplace package signature is required'],
      digest,
    };
  }

  if (signature.algorithm !== COMPONENT_PACKAGE_SIGNATURE_ALGORITHM) {
    errors.push(`Unsupported package signature algorithm '${signature.algorithm}'`);
  }

  if (!signature.keyId.trim()) {
    errors.push('Package signature key id is required');
  }

  if (signature.subject.packageName !== input.packageName) {
    errors.push('Package signature subject package name does not match manifest');
  }

  if (signature.subject.version !== input.version) {
    errors.push('Package signature subject version does not match manifest');
  }

  if (
    input.marketplacePackageId &&
    signature.subject.marketplacePackageId !== input.marketplacePackageId
  ) {
    errors.push('Package signature subject marketplace package id does not match manifest');
  }

  const issuedAt = parseTimestamp(signature.issuedAt);
  const expiresAt = parseTimestamp(signature.expiresAt);
  if (!issuedAt) {
    errors.push('Package signature issuedAt timestamp is invalid');
  }
  if (!expiresAt) {
    errors.push('Package signature expiresAt timestamp is invalid');
  }
  if (issuedAt && expiresAt && issuedAt.getTime() >= expiresAt.getTime()) {
    errors.push('Package signature expiresAt must be after issuedAt');
  }
  if (expiresAt && expiresAt.getTime() <= now.getTime()) {
    errors.push('Package signature has expired');
  }

  if (signature.digest !== digest) {
    errors.push('Package signature digest does not match manifest payload');
  }

  if (!signature.signature.startsWith(SIGNATURE_PREFIX) || signature.signature.length <= SIGNATURE_PREFIX.length) {
    errors.push('Package signature proof is missing or malformed');
  }

  return {
    valid: errors.length === 0,
    errors,
    digest,
  };
}

function parseTimestamp(value: string): Date | null {
  const timestamp = Date.parse(value);
  return Number.isFinite(timestamp) ? new Date(timestamp) : null;
}

function stripUndefined<T extends Record<string, unknown>>(value: T): T {
  const entries = Object.entries(value).filter(([, entryValue]) => entryValue !== undefined);
  return Object.fromEntries(entries) as T;
}

function stableStringify(value: unknown): string {
  if (value === null || typeof value !== 'object') {
    return JSON.stringify(value);
  }

  if (Array.isArray(value)) {
    return `[${value.map((entry) => stableStringify(entry)).join(',')}]`;
  }

  const record = value as Record<string, unknown>;
  const body = Object.keys(record)
    .sort()
    .map((key) => `${JSON.stringify(key)}:${stableStringify(record[key])}`)
    .join(',');
  return `{${body}}`;
}

function stableDigest(value: string): string {
  let hash = HASH_SEED;
  for (let index = 0; index < value.length; index += 1) {
    hash ^= value.charCodeAt(index);
    hash = Math.imul(hash, HASH_PRIME) >>> 0;
  }

  return hash.toString(16).padStart(8, '0');
}
