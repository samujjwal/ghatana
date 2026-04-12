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
} from '../trust.js';
import type { SandboxProfile, PreviewTelemetrySink } from '../trust.js';
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
