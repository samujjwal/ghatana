/**
 * @fileoverview Preview trust, sandboxing, device controls, fallback UX, and preview telemetry.
 *
 * Provides:
 * - Origin trust validation (guards postMessage handlers)
 * - CSP directive builder for sandbox iframes
 * - Device control state and helpers
 * - Fallback UX message types for error/loading/unsupported states
 * - Preview telemetry event types and sink
 */

import type { SandboxProfile, PreviewToHostMessage, Viewport } from './protocol.js';

type BuilderDocument = import('../core/types.js').BuilderDocument;
type ComponentContract = import('@ghatana/ds-schema').ComponentContract;
type ComponentPreviewRestrictions = import('@ghatana/ds-schema').ComponentPreviewRestrictions;
type TrustLevel = import('@ghatana/platform-events').TrustLevel;

const TRUST_LEVEL_ORDER = [
  'UNTRUSTED',
  'IMPORTED_REVIEW_REQUIRED',
  'GENERATED_TRUSTED',
  'TRUSTED_WORKSPACE',
] as const satisfies readonly TrustLevel[];

const PREVIEW_MINIMUM_TRUST_TO_PLATFORM_TRUST = {
  untrusted: 'UNTRUSTED',
  'semi-trusted': 'IMPORTED_REVIEW_REQUIRED',
  'trusted-controlled': 'GENERATED_TRUSTED',
  'trusted-local': 'TRUSTED_WORKSPACE',
} as const satisfies Record<
  ComponentPreviewRestrictions['minimumTrustLevel'],
  TrustLevel
>;

// ============================================================================
// Origin Trust Validation
// ============================================================================

/**
 * Validates that a postMessage event originates from a trusted origin
 * listed in the sandbox profile.
 *
 * IMPORTANT: Always validate origin before processing any message from an iframe.
 */
export function isTrustedOrigin(
  event: MessageEvent,
  profile: SandboxProfile,
): boolean {
  if (!event.origin || event.origin === 'null') return false;
  return profile.trustedOrigins.includes(event.origin);
}

/**
 * Validates that a postMessage has the expected structure for our protocol.
 * Returns true if the message looks like a PreviewToHostMessage.
 */
export function isPreviewProtocolMessage(data: unknown): data is PreviewToHostMessage {
  return (
    typeof data === 'object' &&
    data !== null &&
    'type' in data &&
    typeof (data as Record<string, unknown>)['type'] === 'string'
  );
}

/**
 * Safe postMessage handler factory. Wraps a message handler with origin and
 * protocol validation. Silently drops untrusted or malformed messages.
 */
export function createSafeMessageHandler(
  profile: SandboxProfile,
  handler: (message: PreviewToHostMessage) => void,
  onUntrusted?: (event: MessageEvent) => void,
): (event: MessageEvent) => void {
  return (event: MessageEvent): void => {
    if (!isTrustedOrigin(event, profile)) {
      onUntrusted?.(event);
      return;
    }
    if (!isPreviewProtocolMessage(event.data)) {
      return;
    }
    handler(event.data);
  };
}

// ============================================================================
// CSP Builder
// ============================================================================

export interface PreviewCSPDirectives {
  /** allow-scripts, allow-same-origin, etc. */
  readonly sandbox: readonly string[];
  /** Values for default-src, script-src, style-src, img-src, etc. */
  readonly contentSecurityPolicy: string;
}

export interface PreviewExecutionPolicy {
  readonly profile: SandboxProfile;
  readonly trustLevel: TrustLevel;
  readonly sandbox: readonly string[];
  readonly contentSecurityPolicy: string;
  readonly diagnostics: readonly string[];
  readonly fallbackState?: PreviewFallbackState;
}

/**
 * Builds a restrictive Content-Security-Policy and iframe sandbox attribute
 * for a given sandbox profile.
 */
export function buildPreviewCSP(
  profile: SandboxProfile,
  options: {
    allowScripts?: boolean;
    allowForms?: boolean;
    allowSameOrigin?: boolean;
    allowNetwork?: boolean;
  } = {},
): PreviewCSPDirectives {
  const sandboxTokens: string[] = [];
  if (options.allowSameOrigin !== false) sandboxTokens.push('allow-same-origin');
  if (options.allowScripts !== false) sandboxTokens.push('allow-scripts');
  if (options.allowForms) sandboxTokens.push('allow-forms');

  const trustedSrcs = profile.trustedOrigins.length > 0
    ? profile.trustedOrigins.join(' ')
    : "'none'";

  const csp = [
    `default-src 'self' ${trustedSrcs}`,
    `script-src 'self' ${trustedSrcs} 'unsafe-inline'`,
    `style-src 'self' ${trustedSrcs} 'unsafe-inline'`,
    `img-src 'self' data: blob: ${trustedSrcs}`,
    `font-src 'self' ${trustedSrcs}`,
    options.allowNetwork === false
      ? `connect-src 'none'`
      : `connect-src 'self' ${trustedSrcs}`,
    `frame-ancestors 'none'`,
  ].join('; ');

  return {
    sandbox: sandboxTokens,
    contentSecurityPolicy: csp,
  };
}

function getTrustRank(level: TrustLevel): number {
  return TRUST_LEVEL_ORDER.indexOf(level);
}

function getDocumentTrustLevel(document: BuilderDocument | undefined): TrustLevel {
  return document?.metadata.trustLevel ?? 'GENERATED_TRUSTED';
}

function getComponentRestrictions(
  document: BuilderDocument | undefined,
): readonly ComponentPreviewRestrictions[] {
  return (document?.designSystem.componentContracts ?? [])
    .flatMap((contract: ComponentContract) => (contract.preview ? [contract.preview] : []));
}

function getRequiredTrustLevel(
  restrictions: readonly ComponentPreviewRestrictions[],
): TrustLevel {
  return restrictions.reduce<TrustLevel>((requiredLevel, restriction) => {
    const candidate = PREVIEW_MINIMUM_TRUST_TO_PLATFORM_TRUST[
      restriction.minimumTrustLevel
    ];
    return getTrustRank(candidate) > getTrustRank(requiredLevel)
      ? candidate
      : requiredLevel;
  }, 'UNTRUSTED');
}

function formatTrustLevel(level: TrustLevel): string {
  return level.toLowerCase().replaceAll('_', ' ');
}

export function resolvePreviewExecutionPolicy(
  document: BuilderDocument | undefined,
  profile: SandboxProfile,
  runtimeMode: RuntimeMode = 'authoring',
): PreviewExecutionPolicy {
  const restrictions = getComponentRestrictions(document);
  const documentTrustLevel = getDocumentTrustLevel(document);
  const requiredTrustLevel = getRequiredTrustLevel(restrictions);
  const diagnostics: string[] = [];
  const requiresNetwork = restrictions.some((restriction) => restriction.requiresNetwork);
  const requiresStorage = restrictions.some((restriction) => restriction.requiresStorage);
  const requiresConsent = restrictions.some((restriction) => restriction.requiresConsent);
  const trustRequirementViolated =
    getTrustRank(documentTrustLevel) < getTrustRank(requiredTrustLevel);

  if (requiresNetwork) {
    diagnostics.push('Preview requires network access.');
  }
  if (requiresStorage) {
    diagnostics.push('Preview requires browser storage access.');
  }
  if (requiresConsent) {
    diagnostics.push('Preview includes consent-sensitive behavior.');
  }

  const effectiveTrustLevel: TrustLevel = trustRequirementViolated
    ? 'UNTRUSTED'
    : documentTrustLevel;

  const previewMode = resolvePreviewMode(effectiveTrustLevel, runtimeMode);
  const capabilities = getPreviewCapabilities(previewMode);
  const csp = buildPreviewCSP(profile, {
    allowScripts: capabilities.allowScripts,
    allowForms: capabilities.allowForms,
    allowSameOrigin: capabilities.allowSameOrigin,
    allowNetwork: requiresNetwork && capabilities.allowNetwork,
  });

  return {
    profile,
    trustLevel: effectiveTrustLevel,
    sandbox: csp.sandbox,
    contentSecurityPolicy: csp.contentSecurityPolicy,
    diagnostics,
    fallbackState: trustRequirementViolated
      ? createFallbackState(
          'sandbox-blocked',
          `Preview requires ${formatTrustLevel(requiredTrustLevel)} trust, but this document is ${formatTrustLevel(documentTrustLevel)}.`,
          { retryable: false },
        )
      : undefined,
  };
}

// ============================================================================
// Preview Modes and Runtime Modes
// ============================================================================

/**
 * Explicit preview capability tiers, derived from the document trust level and
 * the product/runtime mode.  Consumers should treat these as opaque modes and
 * derive iframe sandbox/CSP settings from them via `getPreviewCapabilities`.
 */
export type PreviewMode =
  | 'untrusted'          // No scripts, no storage, strictly sandboxed
  | 'semi-trusted'       // Scripts allowed, no same-origin, no storage
  | 'trusted-controlled' // Scripts + forms, no same-origin
  | 'trusted-local';     // Full capabilities, same-origin allowed

/**
 * Runtime contexts in which a preview can be rendered.
 * The runtime mode participates in the trust downgrade logic: production and
 * demo modes enforce a maximum of 'trusted-controlled' even for
 * TRUSTED_WORKSPACE documents.
 */
export type RuntimeMode = 'authoring' | 'staging' | 'production' | 'demo';

/** Capability set derived from a `PreviewMode`. */
export interface PreviewCapabilities {
  readonly allowScripts: boolean;
  readonly allowForms: boolean;
  readonly allowSameOrigin: boolean;
  readonly allowNetwork: boolean;
  readonly allowStorage: boolean;
}

/** Returns the capability set for a given preview mode. */
export function getPreviewCapabilities(mode: PreviewMode): PreviewCapabilities {
  switch (mode) {
    case 'untrusted':
      return {
        allowScripts: false,
        allowForms: false,
        allowSameOrigin: false,
        allowNetwork: false,
        allowStorage: false,
      };
    case 'semi-trusted':
      return {
        allowScripts: true,
        allowForms: false,
        allowSameOrigin: false,
        allowNetwork: false,
        allowStorage: false,
      };
    case 'trusted-controlled':
      return {
        allowScripts: true,
        allowForms: true,
        allowSameOrigin: false,
        allowNetwork: false,
        allowStorage: false,
      };
    case 'trusted-local':
      return {
        allowScripts: true,
        allowForms: true,
        allowSameOrigin: true,
        allowNetwork: true,
        allowStorage: true,
      };
  }
}

/**
 * Derives the effective `PreviewMode` from a platform trust level and a
 * product runtime mode.
 *
 * `production` and `demo` runtime modes cap the mode at `'trusted-controlled'`
 * to prevent same-origin access or storage leakage in live environments.
 */
export function resolvePreviewMode(
  trustLevel: TrustLevel,
  runtimeMode: RuntimeMode = 'authoring',
): PreviewMode {
  let mode: PreviewMode;
  switch (trustLevel) {
    case 'TRUSTED_WORKSPACE':
      mode = 'trusted-local';
      break;
    case 'GENERATED_TRUSTED':
      mode = 'trusted-controlled';
      break;
    case 'IMPORTED_REVIEW_REQUIRED':
      mode = 'semi-trusted';
      break;
    case 'UNTRUSTED':
    default:
      mode = 'untrusted';
  }

  // Production and demo modes enforce a cap of 'trusted-controlled'.
  if ((runtimeMode === 'production' || runtimeMode === 'demo') && mode === 'trusted-local') {
    return 'trusted-controlled';
  }

  return mode;
}



export type DeviceType = 'desktop' | 'tablet' | 'mobile';
export type ThemeMode = 'light' | 'dark' | 'system';
export type Orientation = 'portrait' | 'landscape';

export interface DeviceControlState {
  readonly deviceType: DeviceType;
  readonly viewport: Viewport;
  readonly themeMode: ThemeMode;
  readonly orientation: Orientation;
  readonly scale: number;
}

const DEVICE_DEFAULTS: Record<DeviceType, Viewport> = {
  desktop: { width: 1440, height: 900, devicePixelRatio: 1, label: 'Desktop' },
  tablet: { width: 768, height: 1024, devicePixelRatio: 2, label: 'Tablet' },
  mobile: { width: 375, height: 812, devicePixelRatio: 3, label: 'Mobile' },
};

export function createDeviceControlState(
  overrides: Partial<DeviceControlState> = {},
): DeviceControlState {
  const deviceType: DeviceType = overrides.deviceType ?? 'desktop';
  return {
    deviceType,
    viewport: overrides.viewport ?? DEVICE_DEFAULTS[deviceType],
    themeMode: overrides.themeMode ?? 'light',
    orientation: overrides.orientation ?? 'portrait',
    scale: overrides.scale ?? 1,
  };
}

/** Swap width/height for orientation change. */
export function applyOrientation(
  state: DeviceControlState,
  orientation: Orientation,
): DeviceControlState {
  if (state.orientation === orientation) return state;
  return {
    ...state,
    orientation,
    viewport: {
      ...state.viewport,
      width: state.viewport.height,
      height: state.viewport.width,
    },
  };
}

// ============================================================================
// Fallback UX Message Types
// ============================================================================

export type PreviewFallbackKind =
  | 'loading'
  | 'error'
  | 'unsupported-component'
  | 'contract-missing'
  | 'preview-timeout'
  | 'sandbox-blocked';

export interface PreviewFallbackState {
  readonly kind: PreviewFallbackKind;
  readonly message: string;
  readonly componentName?: string;
  readonly retryable: boolean;
}

export function createFallbackState(
  kind: PreviewFallbackKind,
  message: string,
  options: { componentName?: string; retryable?: boolean } = {},
): PreviewFallbackState {
  return {
    kind,
    message,
    componentName: options.componentName,
    retryable: options.retryable ?? kind !== 'unsupported-component',
  };
}

// ============================================================================
// Preview Telemetry
// ============================================================================

export type PreviewTelemetryEventKind =
  | 'preview.mount.started'
  | 'preview.mount.completed'
  | 'preview.mount.failed'
  | 'preview.update.started'
  | 'preview.update.completed'
  | 'preview.update.failed'
  | 'preview.teardown'
  | 'preview.untrusted-origin'
  | 'preview.device.changed'
  | 'preview.theme.changed';

export interface PreviewTelemetryEvent {
  readonly kind: PreviewTelemetryEventKind;
  readonly timestamp: number;
  readonly correlationId?: string;
  readonly durationMs?: number;
  readonly success: boolean;
  readonly errorCode?: string;
  readonly deviceType?: DeviceType;
  readonly themeMode?: ThemeMode;
}

export interface PreviewTelemetrySink {
  emit(event: PreviewTelemetryEvent): void;
  flush(): Promise<void>;
}

export const noopPreviewTelemetrySink: PreviewTelemetrySink = {
  emit: () => undefined,
  flush: () => Promise.resolve(),
};

export async function withPreviewTelemetry<T>(
  sink: PreviewTelemetrySink,
  kind: PreviewTelemetryEventKind,
  correlationId: string | undefined,
  fn: () => Promise<T>,
): Promise<T> {
  const start = Date.now();
  try {
    const result = await fn();
    sink.emit({ kind, timestamp: start, correlationId, durationMs: Date.now() - start, success: true });
    return result;
  } catch (err: unknown) {
    sink.emit({
      kind,
      timestamp: start,
      correlationId,
      durationMs: Date.now() - start,
      success: false,
      errorCode: err instanceof Error ? err.name : 'UNKNOWN',
    });
    throw err;
  }
}
