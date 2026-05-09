import { render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import BuilderPreviewRoute from '../preview-builder';
import { PREVIEW_BUILDER_RESPONSE_HEADERS, headers } from '../preview-builder';
import type { HostToPreviewMessage } from '@ghatana/ui-builder/preview';
import { validatePreviewSessionToken } from '../../services/preview/PreviewSessionApi';

vi.mock('../../services/preview/PreviewSessionApi', () => ({
  validatePreviewSessionToken: vi.fn(async () => ({ valid: true })),
}));

describe('BuilderPreviewRoute security', () => {
  beforeEach(() => {
    vi.stubEnv('VITE_PREVIEW_SESSION_SECRET', 'test-secret');
    window.history.replaceState({}, '', '/preview/builder?session=server-issued-preview-token');
    vi.mocked(validatePreviewSessionToken).mockResolvedValue({ valid: true });
  });

  it('sends messages with explicit origin (never wildcard)', async () => {
    const postMessageSpy = vi.spyOn(window, 'postMessage');

    render(<BuilderPreviewRoute />);

    await waitFor(() => {
      expect(postMessageSpy).toHaveBeenCalledWith(
        expect.objectContaining({ type: 'READY' }),
        window.location.origin,
      );
    });

    const origins = postMessageSpy.mock.calls.map(([, origin]) => origin);
    expect(origins).not.toContain('*');
  });

  it('exports strict same-origin response headers for the preview document', () => {
    expect(headers()).toBe(PREVIEW_BUILDER_RESPONSE_HEADERS);
    expect(PREVIEW_BUILDER_RESPONSE_HEADERS['X-Frame-Options']).toBe('SAMEORIGIN');
    expect(PREVIEW_BUILDER_RESPONSE_HEADERS['X-Content-Type-Options']).toBe('nosniff');
    expect(PREVIEW_BUILDER_RESPONSE_HEADERS['Referrer-Policy']).toBe('no-referrer');
    expect(PREVIEW_BUILDER_RESPONSE_HEADERS['Cross-Origin-Resource-Policy']).toBe('same-origin');
    expect(PREVIEW_BUILDER_RESPONSE_HEADERS['Permissions-Policy']).toContain('camera=()');
    expect(PREVIEW_BUILDER_RESPONSE_HEADERS['Content-Security-Policy']).toContain("frame-ancestors 'self'");
    expect(PREVIEW_BUILDER_RESPONSE_HEADERS['Content-Security-Policy']).toContain("object-src 'none'");
    expect(PREVIEW_BUILDER_RESPONSE_HEADERS['Content-Security-Policy']).not.toContain('unsafe-eval');
  });

  it('rejects spoofed origin messages', async () => {
    const postMessageSpy = vi.spyOn(window, 'postMessage');

    render(<BuilderPreviewRoute />);

    await Promise.resolve();

    const message: HostToPreviewMessage = {
      type: 'MOUNT_DOCUMENT',
      document: {
        id: 'doc-1',
        version: '1',
        name: 'Doc',
        designSystem: {
          id: 'ds-1',
          name: 'DS',
          version: '1.0.0',
          tokenSetIds: [],
          componentContracts: [],
          themeId: 'default',
        },
        rootNodes: [],
        nodes: new Map(),
        metadata: {
          createdAt: new Date().toISOString(),
          updatedAt: new Date().toISOString(),
          trustLevel: 'GENERATED_TRUSTED',
        },
      },
      sandbox: {
        id: 'sandbox',
        name: 'Sandbox',
        viewport: { width: 1280, height: 720, devicePixelRatio: 1, label: 'Desktop' },
        theme: 'default',
        locale: 'en-US',
        featureFlags: {},
        trustedOrigins: [window.location.origin],
      },
      correlationId: 'corr-1',
    };

    window.dispatchEvent(
      new MessageEvent('message', {
        source: window,
        origin: 'https://evil.example.com',
        data: message,
      }),
    );

    await Promise.resolve();

    expect(postMessageSpy).not.toHaveBeenCalledWith(
      expect.objectContaining({ type: 'MOUNTED' }),
      expect.any(String),
    );
  });

  it('blocks the runtime when the preview session is invalid', async () => {
    vi.mocked(validatePreviewSessionToken).mockResolvedValue({
      valid: false,
      reason: 'Session expired',
    });
    const postMessageSpy = vi.spyOn(window, 'postMessage');

    render(<BuilderPreviewRoute />);

    expect(await screen.findByText('Preview access denied')).toBeInTheDocument();
    expect(screen.getByText('Session expired')).toBeInTheDocument();
    expect(postMessageSpy).not.toHaveBeenCalledWith(
      expect.objectContaining({ type: 'READY' }),
      expect.any(String),
    );
  });

  it('allows mode=dev only when explicit dev preview mode is enabled', async () => {
    vi.stubEnv('VITE_FEATURE_PREVIEW_DEV_MODE', 'true');
    window.history.replaceState({}, '', '/preview/builder?mode=dev');
    const postMessageSpy = vi.spyOn(window, 'postMessage');

    render(<BuilderPreviewRoute />);

    await waitFor(() => {
      expect(postMessageSpy).toHaveBeenCalledWith(
        expect.objectContaining({ type: 'READY' }),
        window.location.origin,
      );
    });
    expect(validatePreviewSessionToken).not.toHaveBeenCalled();
  });

  it('denies mode=dev when explicit dev preview mode is disabled', async () => {
    vi.stubEnv('VITE_FEATURE_PREVIEW_DEV_MODE', 'false');
    window.history.replaceState({}, '', '/preview/builder?mode=dev');
    const postMessageSpy = vi.spyOn(window, 'postMessage');

    render(<BuilderPreviewRoute />);

    expect(await screen.findByText('Preview access denied')).toBeInTheDocument();
    expect(postMessageSpy).not.toHaveBeenCalledWith(
      expect.objectContaining({ type: 'READY' }),
      expect.any(String),
    );
  });

  it('rejects malformed mount documents with a runtime error message', async () => {
    const postMessageSpy = vi.spyOn(window, 'postMessage');

    render(<BuilderPreviewRoute />);

    await waitFor(() => {
      expect(postMessageSpy).toHaveBeenCalledWith(
        expect.objectContaining({ type: 'READY' }),
        window.location.origin,
      );
    });

    const malformedMessage = {
      type: 'MOUNT_DOCUMENT',
      document: {
        id: 'doc-1',
      },
      sandbox: {
        id: 'sandbox',
        name: 'Sandbox',
        viewport: { width: 1280, height: 720, devicePixelRatio: 1, label: 'Desktop' },
        theme: 'default',
        locale: 'en-US',
        featureFlags: {},
        trustedOrigins: [window.location.origin],
      },
      correlationId: 'corr-invalid-doc',
    } as unknown as HostToPreviewMessage;

    window.dispatchEvent(
      new MessageEvent('message', {
        source: window,
        origin: window.location.origin,
        data: malformedMessage,
      }),
    );

    await waitFor(() => {
      expect(postMessageSpy).toHaveBeenCalledWith(
        expect.objectContaining({
          type: 'ERROR',
          correlationId: 'corr-invalid-doc',
          code: 'INVALID_PREVIEW_DOCUMENT',
        }),
        window.location.origin,
      );
    });
    expect(screen.getByText('Waiting for document…')).toBeInTheDocument();
  });
});
