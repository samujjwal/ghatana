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

/**
 * Builds a restrictive Content-Security-Policy and iframe sandbox attribute
 * for a given sandbox profile.
 */
export function buildPreviewCSP(
  profile: SandboxProfile,
  options: { allowScripts?: boolean; allowForms?: boolean } = {},
): PreviewCSPDirectives {
  const sandboxTokens = ['allow-same-origin'];
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
    `connect-src 'self' ${trustedSrcs}`,
    `frame-ancestors 'none'`,
  ].join('; ');

  return {
    sandbox: sandboxTokens,
    contentSecurityPolicy: csp,
  };
}

// ============================================================================
// Device Controls
// ============================================================================

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
