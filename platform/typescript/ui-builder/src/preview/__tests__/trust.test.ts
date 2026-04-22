import { describe, it, expect, vi } from 'vitest';
import {
  isTrustedOrigin,
  isPreviewProtocolMessage,
  createSafeMessageHandler,
  buildPreviewCSP,
  createDeviceControlState,
  applyOrientation,
  createFallbackState,
  withPreviewTelemetry,
  noopPreviewTelemetrySink,
  resolvePreviewMode,
  getPreviewCapabilities,
  resolvePreviewExecutionPolicy,
} from '../trust.js';
import type {
  SandboxProfile,
  PreviewTelemetrySink,
  PreviewMode,
  RuntimeMode,
} from '../trust.js';
import { createSandboxProfile } from '../protocol.js';

function makeProfile(trustedOrigins: string[] = []): SandboxProfile {
  return createSandboxProfile({
    id: 'test',
    name: 'Test',
    trustedOrigins,
  });
}

function makeMessageEvent(origin: string, data: unknown): MessageEvent {
  return new MessageEvent('message', { origin, data });
}

// ============================================================================
// isTrustedOrigin
// ============================================================================

describe('isTrustedOrigin', () => {
  it('returns true for a listed origin', () => {
    const profile = makeProfile(['https://preview.ghatana.dev']);
    const event = makeMessageEvent('https://preview.ghatana.dev', {});
    expect(isTrustedOrigin(event, profile)).toBe(true);
  });

  it('returns false for an unlisted origin', () => {
    const profile = makeProfile(['https://preview.ghatana.dev']);
    const event = makeMessageEvent('https://evil.com', {});
    expect(isTrustedOrigin(event, profile)).toBe(false);
  });

  it('returns false for null origin (sandboxed iframe)', () => {
    const profile = makeProfile(['https://preview.ghatana.dev']);
    const event = makeMessageEvent('null', {});
    expect(isTrustedOrigin(event, profile)).toBe(false);
  });

  it('returns false when trusted origins list is empty', () => {
    const profile = makeProfile([]);
    const event = makeMessageEvent('https://anything.com', {});
    expect(isTrustedOrigin(event, profile)).toBe(false);
  });
});

// ============================================================================
// isPreviewProtocolMessage
// ============================================================================

describe('isPreviewProtocolMessage', () => {
  it('returns true for a valid protocol message', () => {
    expect(isPreviewProtocolMessage({ type: 'READY', version: '1' })).toBe(true);
  });

  it('returns false for non-object', () => {
    expect(isPreviewProtocolMessage('string')).toBe(false);
  });

  it('returns false for null', () => {
    expect(isPreviewProtocolMessage(null)).toBe(false);
  });

  it('returns false for object without type', () => {
    expect(isPreviewProtocolMessage({ foo: 'bar' })).toBe(false);
  });

  it('returns false when type is not a string', () => {
    expect(isPreviewProtocolMessage({ type: 42 })).toBe(false);
  });
});

// ============================================================================
// createSafeMessageHandler
// ============================================================================

describe('createSafeMessageHandler', () => {
  it('calls handler for trusted origin with valid message', () => {
    const profile = makeProfile(['https://preview.ghatana.dev']);
    const handler = vi.fn();
    const safeHandler = createSafeMessageHandler(profile, handler);
    const event = makeMessageEvent('https://preview.ghatana.dev', { type: 'PONG', correlationId: 'c1' });
    safeHandler(event);
    expect(handler).toHaveBeenCalledOnce();
  });

  it('drops message from untrusted origin', () => {
    const profile = makeProfile(['https://preview.ghatana.dev']);
    const handler = vi.fn();
    const onUntrusted = vi.fn();
    const safeHandler = createSafeMessageHandler(profile, handler, onUntrusted);
    const event = makeMessageEvent('https://evil.com', { type: 'PONG', correlationId: 'c1' });
    safeHandler(event);
    expect(handler).not.toHaveBeenCalled();
    expect(onUntrusted).toHaveBeenCalledOnce();
  });

  it('drops malformed message from trusted origin', () => {
    const profile = makeProfile(['https://preview.ghatana.dev']);
    const handler = vi.fn();
    const safeHandler = createSafeMessageHandler(profile, handler);
    const event = makeMessageEvent('https://preview.ghatana.dev', 'raw-string');
    safeHandler(event);
    expect(handler).not.toHaveBeenCalled();
  });
});

// ============================================================================
// buildPreviewCSP
// ============================================================================

describe('buildPreviewCSP', () => {
  it('includes allow-scripts in sandbox tokens by default', () => {
    const profile = makeProfile();
    const csp = buildPreviewCSP(profile);
    expect(csp.sandbox).toContain('allow-scripts');
  });

  it('excludes allow-scripts when allowScripts=false', () => {
    const profile = makeProfile();
    const csp = buildPreviewCSP(profile, { allowScripts: false });
    expect(csp.sandbox).not.toContain('allow-scripts');
  });

  it('includes frame-ancestors none in CSP', () => {
    const profile = makeProfile();
    const csp = buildPreviewCSP(profile);
    expect(csp.contentSecurityPolicy).toContain("frame-ancestors 'none'");
  });

  it('lists trusted origins in script-src', () => {
    const profile = makeProfile(['https://preview.ghatana.dev']);
    const csp = buildPreviewCSP(profile);
    expect(csp.contentSecurityPolicy).toContain('https://preview.ghatana.dev');
  });
});

// ============================================================================
// DeviceControls
// ============================================================================

describe('createDeviceControlState', () => {
  it('defaults to desktop', () => {
    const state = createDeviceControlState();
    expect(state.deviceType).toBe('desktop');
    expect(state.viewport.width).toBe(1440);
  });

  it('uses mobile viewport for mobile device type', () => {
    const state = createDeviceControlState({ deviceType: 'mobile' });
    expect(state.viewport.width).toBe(375);
  });
});

describe('applyOrientation', () => {
  it('swaps width/height when switching to landscape', () => {
    const state = createDeviceControlState({ deviceType: 'mobile' });
    const landscape = applyOrientation(state, 'landscape');
    expect(landscape.viewport.width).toBe(state.viewport.height);
    expect(landscape.viewport.height).toBe(state.viewport.width);
  });

  it('is a no-op when orientation is unchanged', () => {
    const state = createDeviceControlState();
    const same = applyOrientation(state, 'portrait');
    expect(same).toBe(state);
  });
});

// ============================================================================
// createFallbackState
// ============================================================================

describe('createFallbackState', () => {
  it('creates a loading fallback that is retryable', () => {
    const fb = createFallbackState('loading', 'Loading preview...');
    expect(fb.kind).toBe('loading');
    expect(fb.retryable).toBe(true);
  });

  it('creates unsupported-component fallback that is NOT retryable by default', () => {
    const fb = createFallbackState('unsupported-component', 'Not supported');
    expect(fb.retryable).toBe(false);
  });

  it('allows retryable override', () => {
    const fb = createFallbackState('unsupported-component', 'Not supported', { retryable: true });
    expect(fb.retryable).toBe(true);
  });
});

// ============================================================================
// withPreviewTelemetry
// ============================================================================

describe('withPreviewTelemetry', () => {
  it('emits success event on resolved operation', async () => {
    const emitted: unknown[] = [];
    const sink: PreviewTelemetrySink = {
      emit: (e) => emitted.push(e),
      flush: async () => undefined,
    };

    const result = await withPreviewTelemetry(sink, 'preview.mount.completed', 'corr-1', async () => 42);
    expect(result).toBe(42);
    expect(emitted).toHaveLength(1);
    const ev = emitted[0] as { success: boolean; kind: string; correlationId: string };
    expect(ev.success).toBe(true);
    expect(ev.kind).toBe('preview.mount.completed');
    expect(ev.correlationId).toBe('corr-1');
  });

  it('emits failure event and rethrows on rejection', async () => {
    const emitted: unknown[] = [];
    const sink: PreviewTelemetrySink = {
      emit: (e) => emitted.push(e),
      flush: async () => undefined,
    };

    await expect(
      withPreviewTelemetry(sink, 'preview.mount.failed', 'corr-2', async () => {
        throw new TypeError('Timeout');
      }),
    ).rejects.toThrow('Timeout');

    const ev = emitted[0] as { success: boolean; errorCode: string };
    expect(ev.success).toBe(false);
    expect(ev.errorCode).toBe('TypeError');
  });

  it('noopPreviewTelemetrySink does not throw', async () => {
    await expect(
      withPreviewTelemetry(noopPreviewTelemetrySink, 'preview.teardown', undefined, async () => 'ok'),
    ).resolves.toBe('ok');
  });
});

// ============================================================================
// resolvePreviewMode
// ============================================================================

describe('resolvePreviewMode', () => {
  it('maps TRUSTED_WORKSPACE to trusted-local in authoring mode', () => {
    const mode = resolvePreviewMode('TRUSTED_WORKSPACE', 'authoring');
    expect(mode).toBe<PreviewMode>('trusted-local');
  });

  it('maps GENERATED_TRUSTED to trusted-controlled in authoring mode', () => {
    const mode = resolvePreviewMode('GENERATED_TRUSTED', 'authoring');
    expect(mode).toBe<PreviewMode>('trusted-controlled');
  });

  it('maps IMPORTED_REVIEW_REQUIRED to semi-trusted in authoring mode', () => {
    const mode = resolvePreviewMode('IMPORTED_REVIEW_REQUIRED', 'authoring');
    expect(mode).toBe<PreviewMode>('semi-trusted');
  });

  it('maps UNTRUSTED to untrusted', () => {
    const mode = resolvePreviewMode('UNTRUSTED', 'authoring');
    expect(mode).toBe<PreviewMode>('untrusted');
  });

  it('caps TRUSTED_WORKSPACE to trusted-controlled in production mode', () => {
    const mode = resolvePreviewMode('TRUSTED_WORKSPACE', 'production');
    expect(mode).toBe<PreviewMode>('trusted-controlled');
  });

  it('caps TRUSTED_WORKSPACE to trusted-controlled in demo mode', () => {
    const mode = resolvePreviewMode('TRUSTED_WORKSPACE', 'demo');
    expect(mode).toBe<PreviewMode>('trusted-controlled');
  });

  it('does NOT cap GENERATED_TRUSTED in production mode (already controlled)', () => {
    const mode = resolvePreviewMode('GENERATED_TRUSTED', 'production');
    expect(mode).toBe<PreviewMode>('trusted-controlled');
  });

  it('does NOT cap anything in staging mode (staging = authoring rules)', () => {
    const mode = resolvePreviewMode('TRUSTED_WORKSPACE', 'staging');
    expect(mode).toBe<PreviewMode>('trusted-local');
  });

  it('defaults to authoring mode when runtimeMode is omitted', () => {
    const mode = resolvePreviewMode('TRUSTED_WORKSPACE');
    expect(mode).toBe<PreviewMode>('trusted-local');
  });
});

// ============================================================================
// getPreviewCapabilities
// ============================================================================

describe('getPreviewCapabilities', () => {
  it('untrusted mode allows nothing', () => {
    const caps = getPreviewCapabilities('untrusted');
    expect(caps.allowScripts).toBe(false);
    expect(caps.allowForms).toBe(false);
    expect(caps.allowSameOrigin).toBe(false);
    expect(caps.allowNetwork).toBe(false);
    expect(caps.allowStorage).toBe(false);
  });

  it('semi-trusted mode allows scripts only', () => {
    const caps = getPreviewCapabilities('semi-trusted');
    expect(caps.allowScripts).toBe(true);
    expect(caps.allowForms).toBe(false);
    expect(caps.allowSameOrigin).toBe(false);
    expect(caps.allowNetwork).toBe(false);
  });

  it('trusted-controlled allows scripts and forms', () => {
    const caps = getPreviewCapabilities('trusted-controlled');
    expect(caps.allowScripts).toBe(true);
    expect(caps.allowForms).toBe(true);
    expect(caps.allowSameOrigin).toBe(false);
    expect(caps.allowNetwork).toBe(false);
  });

  it('trusted-local allows everything', () => {
    const caps = getPreviewCapabilities('trusted-local');
    expect(caps.allowScripts).toBe(true);
    expect(caps.allowForms).toBe(true);
    expect(caps.allowSameOrigin).toBe(true);
    expect(caps.allowNetwork).toBe(true);
    expect(caps.allowStorage).toBe(true);
  });
});

// ============================================================================
// resolvePreviewExecutionPolicy
// ============================================================================

describe('resolvePreviewExecutionPolicy', () => {
  it('trusted-workspace document in authoring mode gets full sandbox capabilities', () => {
    const profile = makeProfile(['https://preview.ghatana.dev']);
    // BuilderDocument stub with TRUSTED_WORKSPACE trust level
    const doc = {
      metadata: { trustLevel: 'TRUSTED_WORKSPACE' },
      designSystem: { componentContracts: [] },
    } as unknown as Parameters<typeof resolvePreviewExecutionPolicy>[0];

    const policy = resolvePreviewExecutionPolicy(doc, profile, 'authoring');

    expect(policy.trustLevel).toBe('TRUSTED_WORKSPACE');
    expect(policy.sandbox).toContain('allow-scripts');
    expect(policy.sandbox).toContain('allow-same-origin');
    expect(policy.fallbackState).toBeUndefined();
    expect(policy.diagnostics).toHaveLength(0);
  });

  it('UNTRUSTED document gets no scripts and no same-origin', () => {
    const profile = makeProfile();
    const doc = {
      metadata: { trustLevel: 'UNTRUSTED' },
      designSystem: { componentContracts: [] },
    } as unknown as Parameters<typeof resolvePreviewExecutionPolicy>[0];

    const policy = resolvePreviewExecutionPolicy(doc, profile, 'authoring');

    expect(policy.trustLevel).toBe('UNTRUSTED');
    expect(policy.sandbox).not.toContain('allow-scripts');
    expect(policy.sandbox).not.toContain('allow-same-origin');
    expect(policy.fallbackState).toBeUndefined();
  });

  it('TRUSTED_WORKSPACE in production mode is capped at trusted-controlled (no same-origin)', () => {
    const profile = makeProfile();
    const doc = {
      metadata: { trustLevel: 'TRUSTED_WORKSPACE' },
      designSystem: { componentContracts: [] },
    } as unknown as Parameters<typeof resolvePreviewExecutionPolicy>[0];

    const policy = resolvePreviewExecutionPolicy(doc, profile, 'production');

    expect(policy.trustLevel).toBe('TRUSTED_WORKSPACE');
    expect(policy.sandbox).toContain('allow-scripts');
    expect(policy.sandbox).not.toContain('allow-same-origin');
  });

  it('undefined document defaults to GENERATED_TRUSTED trust level', () => {
    const profile = makeProfile();
    const policy = resolvePreviewExecutionPolicy(undefined, profile, 'authoring');

    expect(policy.trustLevel).toBe('GENERATED_TRUSTED');
    expect(policy.sandbox).toContain('allow-scripts');
    expect(policy.fallbackState).toBeUndefined();
  });

  it('trust requirement violation produces sandbox-blocked fallback', () => {
    const profile = makeProfile();
    // Document is UNTRUSTED but a component requires TRUSTED_WORKSPACE level
    const doc = {
      metadata: { trustLevel: 'UNTRUSTED' },
      designSystem: {
        componentContracts: [
          {
            preview: {
              minimumTrustLevel: 'trusted-local',
              requiresNetwork: false,
              requiresStorage: false,
              requiresConsent: false,
            },
          },
        ],
      },
    } as unknown as Parameters<typeof resolvePreviewExecutionPolicy>[0];

    const policy = resolvePreviewExecutionPolicy(doc, profile, 'authoring');

    expect(policy.fallbackState).toBeDefined();
    expect(policy.fallbackState?.kind).toBe('sandbox-blocked');
    expect(policy.fallbackState?.retryable).toBe(false);
    // Effective trust level drops to UNTRUSTED
    expect(policy.trustLevel).toBe('UNTRUSTED');
  });

  it('network-requiring component adds network diagnostic message', () => {
    const profile = makeProfile();
    const doc = {
      metadata: { trustLevel: 'GENERATED_TRUSTED' },
      designSystem: {
        componentContracts: [
          {
            preview: {
              minimumTrustLevel: 'semi-trusted',
              requiresNetwork: true,
              requiresStorage: false,
              requiresConsent: false,
            },
          },
        ],
      },
    } as unknown as Parameters<typeof resolvePreviewExecutionPolicy>[0];

    const policy = resolvePreviewExecutionPolicy(doc, profile, 'authoring');

    expect(policy.diagnostics).toContain('Preview requires network access.');
  });
});
