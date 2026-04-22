import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import {
  PRESET_VIEWPORTS,
  createSandboxProfile,
  PreviewHostService,
} from '../protocol.js';
import type {
  SandboxProfile,
  HostToPreviewMessage,
  PreviewToHostMessage,
  Viewport,
} from '../protocol.js';

// ============================================================================
// Helpers
// ============================================================================

function makeIframe(): { iframe: HTMLIFrameElement; postMessage: ReturnType<typeof vi.fn> } {
  const postMessage = vi.fn<[unknown, string], void>();
  const iframe = {
    contentWindow: { postMessage } as unknown as Window,
  } as unknown as HTMLIFrameElement;
  return { iframe, postMessage };
}

function makeProfile(
  overrides: Partial<SandboxProfile> & { trustedOrigins?: string[] } = {},
): SandboxProfile {
  return createSandboxProfile({
    id: 'test-profile',
    name: 'Test Profile',
    trustedOrigins: ['https://preview.ghatana.dev'],
    ...overrides,
  });
}

function dispatchMessageToService(
  iframe: HTMLIFrameElement,
  data: PreviewToHostMessage,
): void {
  const event = new MessageEvent('message', {
    data,
    source: iframe.contentWindow ?? undefined,
  });
  window.dispatchEvent(event);
}

// ============================================================================
// PRESET_VIEWPORTS
// ============================================================================

describe('PRESET_VIEWPORTS', () => {
  it('has mobile viewport at 375px wide', () => {
    expect(PRESET_VIEWPORTS.mobile.width).toBe(375);
    expect(PRESET_VIEWPORTS.mobile.height).toBe(812);
    expect(PRESET_VIEWPORTS.mobile.devicePixelRatio).toBe(3);
  });

  it('has tablet viewport at 768px wide', () => {
    expect(PRESET_VIEWPORTS.tablet.width).toBe(768);
    expect(PRESET_VIEWPORTS.tablet.height).toBe(1024);
  });

  it('has desktop viewport at 1440px wide', () => {
    expect(PRESET_VIEWPORTS.desktop.width).toBe(1440);
    expect(PRESET_VIEWPORTS.desktop.height).toBe(900);
    expect(PRESET_VIEWPORTS.desktop.devicePixelRatio).toBe(1);
  });

  it('has desktop-xl viewport at 1920px wide', () => {
    expect(PRESET_VIEWPORTS['desktop-xl'].width).toBe(1920);
    expect(PRESET_VIEWPORTS['desktop-xl'].height).toBe(1080);
  });

  it('has label on every preset', () => {
    for (const [key, viewport] of Object.entries(PRESET_VIEWPORTS)) {
      expect(typeof viewport.label).toBe('string');
      expect(viewport.label.length).toBeGreaterThan(0);
      expect(viewport.label, `viewport ${key} label`).toBeTruthy();
    }
  });

  it('all presets satisfy Viewport interface shape', () => {
    for (const viewport of Object.values(PRESET_VIEWPORTS)) {
      const v = viewport as Viewport;
      expect(typeof v.width).toBe('number');
      expect(typeof v.height).toBe('number');
      expect(typeof v.devicePixelRatio).toBe('number');
      expect(typeof v.label).toBe('string');
    }
  });
});

// ============================================================================
// createSandboxProfile
// ============================================================================

describe('createSandboxProfile', () => {
  it('applies required fields', () => {
    const profile = createSandboxProfile({ id: 'p1', name: 'P1' });
    expect(profile.id).toBe('p1');
    expect(profile.name).toBe('P1');
  });

  it('uses desktop viewport by default', () => {
    const profile = createSandboxProfile({ id: 'p1', name: 'P1' });
    expect(profile.viewport).toEqual(PRESET_VIEWPORTS.desktop);
  });

  it('uses en-US locale by default', () => {
    const profile = createSandboxProfile({ id: 'p1', name: 'P1' });
    expect(profile.locale).toBe('en-US');
  });

  it('uses default theme by default', () => {
    const profile = createSandboxProfile({ id: 'p1', name: 'P1' });
    expect(profile.theme).toBe('default');
  });

  it('has empty trustedOrigins by default', () => {
    const profile = createSandboxProfile({ id: 'p1', name: 'P1' });
    expect(profile.trustedOrigins).toEqual([]);
  });

  it('has empty featureFlags by default', () => {
    const profile = createSandboxProfile({ id: 'p1', name: 'P1' });
    expect(profile.featureFlags).toEqual({});
  });

  it('applies overrides', () => {
    const profile = createSandboxProfile({
      id: 'p2',
      name: 'P2',
      locale: 'de-DE',
      trustedOrigins: ['https://app.example.com'],
    });
    expect(profile.locale).toBe('de-DE');
    expect(profile.trustedOrigins).toEqual(['https://app.example.com']);
  });

  it('overrides viewport when provided', () => {
    const viewport: Viewport = { width: 800, height: 600, devicePixelRatio: 1, label: 'Custom' };
    const profile = createSandboxProfile({ id: 'p3', name: 'P3', viewport });
    expect(profile.viewport).toEqual(viewport);
  });
});

// ============================================================================
// PreviewHostService — message dispatch
// ============================================================================

describe('PreviewHostService', () => {
  let iframe: HTMLIFrameElement;
  let postMessage: ReturnType<typeof vi.fn>;
  let profile: SandboxProfile;

  beforeEach(() => {
    const result = makeIframe();
    iframe = result.iframe;
    postMessage = result.postMessage;
    profile = makeProfile();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('creates without throwing', () => {
    expect(() => new PreviewHostService(iframe, profile)).not.toThrow();
  });

  it('registers a window message listener on construction', () => {
    const addEventListenerSpy = vi.spyOn(window, 'addEventListener');
    new PreviewHostService(iframe, profile);
    expect(addEventListenerSpy).toHaveBeenCalledWith('message', expect.any(Function));
  });

  it('send() posts to iframe contentWindow with first trusted origin', () => {
    const service = new PreviewHostService(iframe, profile);
    const msg: HostToPreviewMessage = { type: 'PING', correlationId: 'c1' };
    service.send(msg);
    expect(postMessage).toHaveBeenCalledWith(msg, 'https://preview.ghatana.dev');
  });

  it('send() falls back to * when trustedOrigins is empty', () => {
    const emptyProfile = makeProfile({ trustedOrigins: [] });
    const service = new PreviewHostService(iframe, emptyProfile);
    const msg: HostToPreviewMessage = { type: 'PING', correlationId: 'c2' };
    service.send(msg);
    expect(postMessage).toHaveBeenCalledWith(msg, '*');
  });

  it('onMessage registers and returns an unsubscribe function', () => {
    const service = new PreviewHostService(iframe, profile);
    const handler = vi.fn<[PreviewToHostMessage], void>();
    const unsub = service.onMessage(handler);
    expect(typeof unsub).toBe('function');

    const msg: PreviewToHostMessage = { type: 'READY', version: '1.0.0' };
    dispatchMessageToService(iframe, msg);
    expect(handler).toHaveBeenCalledWith(msg);
    expect(handler).toHaveBeenCalledTimes(1);
  });

  it('onMessage unsubscribe stops future calls', () => {
    const service = new PreviewHostService(iframe, profile);
    const handler = vi.fn<[PreviewToHostMessage], void>();
    const unsub = service.onMessage(handler);

    unsub();

    const msg: PreviewToHostMessage = { type: 'PONG', correlationId: 'c3' };
    dispatchMessageToService(iframe, msg);
    expect(handler).not.toHaveBeenCalled();
  });

  it('mount() sends MOUNT_DOCUMENT with document and sandbox', async () => {
    const service = new PreviewHostService(iframe, profile);
    // Provide a minimal valid BuilderDocument structure
    const doc = { id: 'doc-1' } as Parameters<typeof service.mount>[0];
    await service.mount(doc, profile);
    expect(postMessage).toHaveBeenCalledWith(
      expect.objectContaining({
        type: 'MOUNT_DOCUMENT',
        document: doc,
        sandbox: profile,
        correlationId: expect.any(String),
      }),
      expect.any(String),
    );
  });

  it('update() sends UPDATE_DOCUMENT with document', async () => {
    const service = new PreviewHostService(iframe, profile);
    const doc = { id: 'doc-2' } as Parameters<typeof service.update>[0];
    await service.update(doc);
    expect(postMessage).toHaveBeenCalledWith(
      expect.objectContaining({
        type: 'UPDATE_DOCUMENT',
        document: doc,
        correlationId: expect.any(String),
      }),
      expect.any(String),
    );
  });

  it('teardown() sends TEARDOWN and removes the window listener', async () => {
    const removeEventListenerSpy = vi.spyOn(window, 'removeEventListener');
    const service = new PreviewHostService(iframe, profile);
    await service.teardown();

    expect(postMessage).toHaveBeenCalledWith(
      expect.objectContaining({ type: 'TEARDOWN', correlationId: expect.any(String) }),
      expect.any(String),
    );
    expect(removeEventListenerSpy).toHaveBeenCalledWith('message', expect.any(Function));
  });

  it('teardown() clears all onMessage handlers', async () => {
    const service = new PreviewHostService(iframe, profile);
    const handler = vi.fn<[PreviewToHostMessage], void>();
    service.onMessage(handler);

    await service.teardown();

    const msg: PreviewToHostMessage = { type: 'PONG', correlationId: 'c4' };
    dispatchMessageToService(iframe, msg);
    expect(handler).not.toHaveBeenCalled();
  });

  it('callbacks.onReady fires when READY message arrives', () => {
    const onReady = vi.fn();
    new PreviewHostService(iframe, profile, { onReady });

    const msg: PreviewToHostMessage = { type: 'READY', version: '1.0.0' };
    dispatchMessageToService(iframe, msg);
    expect(onReady).toHaveBeenCalledWith(msg);
  });

  it('callbacks.onMounted fires when MOUNTED message arrives', () => {
    const onMounted = vi.fn();
    new PreviewHostService(iframe, profile, { onMounted });

    const msg: PreviewToHostMessage = { type: 'MOUNTED', correlationId: 'c5', durationMs: 42 };
    dispatchMessageToService(iframe, msg);
    expect(onMounted).toHaveBeenCalledWith(msg);
  });

  it('callbacks.onError fires when ERROR message arrives', () => {
    const onError = vi.fn();
    new PreviewHostService(iframe, profile, { onError });

    const msg: PreviewToHostMessage = {
      type: 'ERROR',
      correlationId: 'c6',
      code: 'RENDER_FAILED',
      message: 'Component threw during render',
    };
    dispatchMessageToService(iframe, msg);
    expect(onError).toHaveBeenCalledWith(msg);
  });

  it('ignores messages from unknown sources', () => {
    const service = new PreviewHostService(iframe, profile);
    const handler = vi.fn<[PreviewToHostMessage], void>();
    service.onMessage(handler);

    // Dispatch from null source (not from our iframe)
    const event = new MessageEvent('message', {
      data: { type: 'READY', version: '1' } satisfies PreviewToHostMessage,
      source: null,
    });
    window.dispatchEvent(event);
    expect(handler).not.toHaveBeenCalled();
  });

  it('ignores malformed message objects', () => {
    const service = new PreviewHostService(iframe, profile);
    const handler = vi.fn<[PreviewToHostMessage], void>();
    service.onMessage(handler);

    const event = new MessageEvent('message', {
      data: 'not-an-object',
      source: iframe.contentWindow ?? undefined,
    });
    window.dispatchEvent(event);
    expect(handler).not.toHaveBeenCalled();
  });

  it('generates a unique correlationId per operation', async () => {
    const service = new PreviewHostService(iframe, profile);
    const doc = { id: 'doc-3' } as Parameters<typeof service.mount>[0];
    await service.mount(doc, profile);
    await service.update(doc);

    const calls = postMessage.mock.calls as Array<[Record<string, unknown>, string]>;
    const mountCorrelation = calls.find(([msg]) => msg['type'] === 'MOUNT_DOCUMENT')?.[0]?.['correlationId'];
    const updateCorrelation = calls.find(([msg]) => msg['type'] === 'UPDATE_DOCUMENT')?.[0]?.['correlationId'];
    expect(mountCorrelation).not.toBe(updateCorrelation);
  });
});
