/**
 * BuilderPreviewRoute tests.
 *
 * Verifies that the standalone /preview/builder route:
 *  1. Announces READY to the host on mount.
 *  2. Renders document rootNodes when a MOUNT_DOCUMENT message is received.
 *  3. Sends MOUNTED after receiving MOUNT_DOCUMENT.
 *  4. Sends UPDATED after receiving UPDATE_DOCUMENT.
 *  5. Responds to PING with PONG.
 *  6. Clears the canvas on TEARDOWN.
 */

import { beforeEach, describe, it, expect, vi, afterEach } from 'vitest';
import { render, screen, act, waitFor } from '@testing-library/react';
import BuilderPreviewRoute from '../preview-builder';
import type {
  HostToPreviewMessage,
  MountDocumentMessage,
  UpdateDocumentMessage,
  MountedMessage,
  ReadyMessage,
  UpdatedMessage,
  PongMessage,
} from '@ghatana/ui-builder/preview';
import type { BuilderDocument } from '@ghatana/ui-builder';

vi.mock('../../services/preview/PreviewSessionApi', () => ({
  validatePreviewSessionToken: vi.fn(async () => ({ valid: true })),
}));

// ---- Mock ComponentRenderer to avoid pulling in the full React tree ----
vi.mock('../../components/canvas/page/ComponentRenderer', () => ({
  ComponentRenderer: ({ nodeId }: { nodeId: string }) => (
    <div data-testid={`node-${nodeId}`} />
  ),
}));

// ---- Helpers ----

function makeDocument(rootNodes: string[] = []): BuilderDocument {
  return {
    id: 'doc-1',
    version: '1',
    name: 'Preview Doc',
    designSystem: {
      id: 'ghatana-ds-v1',
      name: 'Ghatana Design System',
      version: '1.0.0',
      tokenSetIds: [],
      componentContracts: [],
      themeId: 'default',
    },
    rootNodes,
    nodes: new Map(),
    metadata: {
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
      trustLevel: 'GENERATED_TRUSTED',
    },
  };
}

/**
 * Dispatch a message from the "parent" window (simulated as window itself in
 * jsdom where window.parent === window).
 */
function dispatchFromParent(message: HostToPreviewMessage): void {
  const expectedOrigin = document.referrer
    ? new URL(document.referrer).origin
    : window.location.origin;
  const event = new MessageEvent('message', {
    data: message,
    source: window.parent,
    origin: expectedOrigin,
  });
  window.dispatchEvent(event);
}

async function waitForPreviewReady(postMessageSpy: ReturnType<typeof vi.fn>): Promise<void> {
  await waitFor(() => {
    expect(postMessageSpy).toHaveBeenCalledWith(
      expect.objectContaining<Partial<ReadyMessage>>({ type: 'READY' }),
      expect.any(String),
    );
  });
}

describe('BuilderPreviewRoute', () => {
  const session = 'server-issued-preview-token';
  const expectedOrigin = document.referrer
    ? new URL(document.referrer).origin
    : window.location.origin;

  beforeEach(() => {
    vi.stubEnv('VITE_PREVIEW_SESSION_SECRET', 'test-secret');
    window.history.replaceState({}, '', `/preview/builder?session=${session}`);
  });

  afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllEnvs();
  });

  it('posts READY to window.parent on mount', async () => {
    const postMessageSpy = vi.spyOn(window, 'postMessage');

    render(<BuilderPreviewRoute />);

    await waitFor(() => {
      expect(postMessageSpy).toHaveBeenCalledWith(
        expect.objectContaining<Partial<ReadyMessage>>({ type: 'READY' }),
        expectedOrigin,
      );
    });
  });

  it('renders the waiting state before any document is mounted', () => {
    render(<BuilderPreviewRoute />);
    expect(screen.getByText('Waiting for document…')).toBeInTheDocument();
  });

  it('renders nodes after MOUNT_DOCUMENT and posts MOUNTED', async () => {
    const postMessageSpy = vi.spyOn(window, 'postMessage');
    render(<BuilderPreviewRoute />);
    await waitForPreviewReady(postMessageSpy);

    const mountMsg: MountDocumentMessage = {
      type: 'MOUNT_DOCUMENT',
      document: makeDocument(['node-abc']),
      sandbox: {
        id: 'test-sandbox',
        name: 'Test',
        viewport: { width: 1440, height: 900, devicePixelRatio: 1, label: 'Desktop' },
        theme: 'default',
        locale: 'en-US',
        featureFlags: {},
        trustedOrigins: [window.location.origin],
      },
      correlationId: 'corr-1',
    };

    await act(async () => {
      await Promise.resolve();
      dispatchFromParent(mountMsg);
      // Allow the microtask (Promise.resolve().then) to flush
      await Promise.resolve();
    });

    expect(screen.getByTestId('node-node-abc')).toBeInTheDocument();

    expect(postMessageSpy).toHaveBeenCalledWith(
      expect.objectContaining<Partial<MountedMessage>>({
        type: 'MOUNTED',
        correlationId: 'corr-1',
      }),
      expectedOrigin,
    );
  });

  it('posts UPDATED after UPDATE_DOCUMENT', async () => {
    const postMessageSpy = vi.spyOn(window, 'postMessage');
    render(<BuilderPreviewRoute />);
    await waitForPreviewReady(postMessageSpy);

    const mountMsg: MountDocumentMessage = {
      type: 'MOUNT_DOCUMENT',
      document: makeDocument(['node-abc']),
      sandbox: {
        id: 'test-sandbox',
        name: 'Test',
        viewport: { width: 1440, height: 900, devicePixelRatio: 1, label: 'Desktop' },
        theme: 'default',
        locale: 'en-US',
        featureFlags: {},
        trustedOrigins: [window.location.origin],
      },
      correlationId: 'corr-1',
    };

    const updateMsg: UpdateDocumentMessage = {
      type: 'UPDATE_DOCUMENT',
      document: makeDocument(['node-xyz']),
      correlationId: 'corr-2',
    };

    await act(async () => {
      await Promise.resolve();
      dispatchFromParent(mountMsg);
      await Promise.resolve();
    });

    await act(async () => {
      dispatchFromParent(updateMsg);
      await Promise.resolve();
    });

    expect(screen.getByTestId('node-node-xyz')).toBeInTheDocument();

    expect(postMessageSpy).toHaveBeenCalledWith(
      expect.objectContaining<Partial<UpdatedMessage>>({
        type: 'UPDATED',
        correlationId: 'corr-2',
      }),
      expectedOrigin,
    );
  });

  it('responds to PING with PONG', async () => {
    const postMessageSpy = vi.spyOn(window, 'postMessage');
    render(<BuilderPreviewRoute />);
    await waitForPreviewReady(postMessageSpy);

    await act(async () => {
      await Promise.resolve();
      dispatchFromParent({ type: 'PING', correlationId: 'ping-1' });
    });

    expect(postMessageSpy).toHaveBeenCalledWith(
      expect.objectContaining<Partial<PongMessage>>({
        type: 'PONG',
        correlationId: 'ping-1',
      }),
      expectedOrigin,
    );
  });

  it('clears the canvas on TEARDOWN', async () => {
    const postMessageSpy = vi.spyOn(window, 'postMessage');
    render(<BuilderPreviewRoute />);
    await waitForPreviewReady(postMessageSpy);

    await act(async () => {
      await Promise.resolve();
      dispatchFromParent({
        type: 'MOUNT_DOCUMENT',
        document: makeDocument(['node-abc']),
        sandbox: {
          id: 'test-sandbox',
          name: 'Test',
          viewport: { width: 1440, height: 900, devicePixelRatio: 1, label: 'Desktop' },
          theme: 'default',
          locale: 'en-US',
          featureFlags: {},
          trustedOrigins: [window.location.origin],
        },
        correlationId: 'corr-1',
      });
      await Promise.resolve();
    });

    expect(screen.getByTestId('node-node-abc')).toBeInTheDocument();

    await act(async () => {
      dispatchFromParent({ type: 'TEARDOWN', correlationId: 'corr-2' });
    });

    expect(screen.queryByTestId('node-node-abc')).not.toBeInTheDocument();
    expect(screen.getByText('Waiting for document…')).toBeInTheDocument();
  });

  it('applies viewport, theme, and locale messages to the runtime shell', async () => {
    const postMessageSpy = vi.spyOn(window, 'postMessage');
    render(<BuilderPreviewRoute />);
    await waitForPreviewReady(postMessageSpy);

    await act(async () => {
      await Promise.resolve();
      dispatchFromParent({
        type: 'MOUNT_DOCUMENT',
        document: makeDocument(['node-abc']),
        sandbox: {
          id: 'test-sandbox',
          name: 'Test',
          viewport: { width: 1440, height: 900, devicePixelRatio: 1, label: 'Desktop' },
          theme: 'default',
          locale: 'en-US',
          featureFlags: {},
          trustedOrigins: [window.location.origin],
        },
        correlationId: 'corr-1',
      });
      await Promise.resolve();
    });

    await act(async () => {
      dispatchFromParent({
        type: 'SET_VIEWPORT',
        viewport: { width: 375, height: 812, devicePixelRatio: 3, label: 'Mobile' },
        correlationId: 'viewport-1',
      });
      dispatchFromParent({
        type: 'SET_THEME',
        theme: 'contrast',
        correlationId: 'theme-1',
      });
      dispatchFromParent({
        type: 'SET_LOCALE',
        locale: 'ar-SA',
        correlationId: 'locale-1',
      });
      await Promise.resolve();
    });

    const shell = screen.getByTestId('preview-runtime-shell');
    const viewport = screen.getByTestId('preview-runtime-viewport');

    expect(shell).toHaveAttribute('data-preview-theme', 'contrast');
    expect(shell).toHaveAttribute('data-preview-locale', 'ar-SA');
    expect(shell).toHaveAttribute('data-preview-viewport-width', '375');
    expect(shell).toHaveAttribute('dir', 'rtl');
    expect(shell).toHaveStyle({ backgroundColor: 'rgb(0, 0, 0)' });
    expect(viewport).toHaveStyle({
      maxWidth: '375px',
      minHeight: '812px',
      outline: '2px solid #facc15',
    });
  });
});
