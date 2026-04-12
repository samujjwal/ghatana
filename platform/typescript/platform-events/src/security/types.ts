/**
 * @fileoverview Security types and trust model for the platform.
 */

/** Trust levels for content and components. */
export type TrustLevel =
  | 'TRUSTED_WORKSPACE'
  | 'GENERATED_TRUSTED'
  | 'IMPORTED_REVIEW_REQUIRED'
  | 'UNTRUSTED';

/** All valid trust levels. */
export const TRUST_LEVELS: readonly TrustLevel[] = [
  'TRUSTED_WORKSPACE',
  'GENERATED_TRUSTED',
  'IMPORTED_REVIEW_REQUIRED',
  'UNTRUSTED',
] as const;

/** Validates a trust level. */
export function isValidTrustLevel(level: string): level is TrustLevel {
  return TRUST_LEVELS.includes(level as TrustLevel);
}

/** Security policy for sandboxing and capability grants. */
export interface SecurityPolicy {
  readonly sandboxLevel: 'none' | 'iframe' | 'strict-iframe' | 'no-execution';
  readonly allowedResourceTypes: readonly string[];
  readonly inlineScriptRestrictions: 'allow' | 'block' | 'hash-required';
  readonly cspDirectives?: Record<string, readonly string[]>;
  readonly capabilityGrants: readonly string[];
  readonly networkAccess: 'none' | 'same-origin' | 'cors' | 'full';
  readonly storageAccess: 'none' | 'session' | 'persistent';
}

/** Metadata attached to components and documents for security. */
export interface SecurityMetadata {
  readonly trustLevel: TrustLevel;
  readonly sandboxLevel: string;
  readonly allowedResources: readonly string[];
  readonly inlineScriptRestrictions: 'allow' | 'block' | 'hash-required';
  readonly contentSecurityPolicy?: string;
}

/** Preview security profile with iframe sandbox configurations. */
export interface PreviewSecurityProfile {
  readonly trustLevel: TrustLevel;
  readonly iframeSandbox: string;
  readonly allowScripts: boolean;
  readonly allowSameOrigin: boolean;
  readonly allowForms: boolean;
  readonly allowPopups: boolean;
  readonly allowPopupsToEscapeSandbox: boolean;
  readonly allowDownloads: boolean;
  readonly requiredCsp?: string;
}

/** Sandbox profiles mapped by trust level. */
export const SANDBOX_PROFILES: Record<TrustLevel, PreviewSecurityProfile> = {
  TRUSTED_WORKSPACE: {
    trustLevel: 'TRUSTED_WORKSPACE',
    iframeSandbox: 'allow-scripts allow-same-origin allow-forms allow-popups allow-popups-to-escape-sandbox allow-downloads',
    allowScripts: true,
    allowSameOrigin: true,
    allowForms: true,
    allowPopups: true,
    allowPopupsToEscapeSandbox: true,
    allowDownloads: true,
  },
  GENERATED_TRUSTED: {
    trustLevel: 'GENERATED_TRUSTED',
    iframeSandbox: 'allow-scripts allow-forms',
    allowScripts: true,
    allowSameOrigin: false,
    allowForms: true,
    allowPopups: false,
    allowPopupsToEscapeSandbox: false,
    allowDownloads: false,
  },
  IMPORTED_REVIEW_REQUIRED: {
    trustLevel: 'IMPORTED_REVIEW_REQUIRED',
    iframeSandbox: 'allow-scripts',
    allowScripts: true,
    allowSameOrigin: false,
    allowForms: false,
    allowPopups: false,
    allowPopupsToEscapeSandbox: false,
    allowDownloads: false,
  },
  UNTRUSTED: {
    trustLevel: 'UNTRUSTED',
    iframeSandbox: '', // No permissions
    allowScripts: false,
    allowSameOrigin: false,
    allowForms: false,
    allowPopups: false,
    allowPopupsToEscapeSandbox: false,
    allowDownloads: false,
    requiredCsp: "default-src 'none'; script-src 'none';",
  },
};

/** Creates a default security policy. */
export function createDefaultSecurityPolicy(): SecurityPolicy {
  return {
    sandboxLevel: 'iframe',
    allowedResourceTypes: ['script', 'style', 'image', 'font'],
    inlineScriptRestrictions: 'hash-required',
    capabilityGrants: [],
    networkAccess: 'same-origin',
    storageAccess: 'none',
  };
}

/** Gets the sandbox profile for a trust level. */
export function getSandboxProfile(trustLevel: TrustLevel): PreviewSecurityProfile {
  return SANDBOX_PROFILES[trustLevel] ?? SANDBOX_PROFILES.UNTRUSTED;
}

/** Validates if a trust level transition is allowed. */
export function isTrustLevelTransitionAllowed(
  from: TrustLevel,
  to: TrustLevel
): boolean {
  // Cannot downgrade from TRUSTED_WORKSPACE without explicit user action
  if (from === 'TRUSTED_WORKSPACE' && to !== 'TRUSTED_WORKSPACE') {
    return false;
  }
  // Can always upgrade trust level
  const levels: readonly TrustLevel[] = ['UNTRUSTED', 'IMPORTED_REVIEW_REQUIRED', 'GENERATED_TRUSTED', 'TRUSTED_WORKSPACE'];
  const fromIndex = levels.indexOf(from);
  const toIndex = levels.indexOf(to);
  return toIndex >= fromIndex;
}
