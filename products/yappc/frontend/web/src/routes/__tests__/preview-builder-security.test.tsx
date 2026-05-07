import { render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import BuilderPreviewRoute from '../preview-builder';
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
