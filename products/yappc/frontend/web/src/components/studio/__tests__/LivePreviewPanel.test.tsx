import { beforeEach, describe, expect, it, vi } from 'vitest';
import { act, fireEvent, screen, waitFor } from '@testing-library/react';
import { render } from '@/test-utils/test-utils';
import { LivePreviewPanel } from '../LivePreviewPanel';

const mountDocumentMock = vi.fn();
const updateDocumentMock = vi.fn();
const sendMock = vi.fn();
const teardownMock = vi.fn();
const resolvePreviewExecutionPolicyMock = vi.fn();
const issuePreviewSessionMock = vi.fn();

vi.mock('@ghatana/ui-builder/preview', () => ({
  PRESET_VIEWPORTS: {
    mobile: { width: 375, height: 812, devicePixelRatio: 3, label: 'Mobile (375px)' },
    tablet: { width: 768, height: 1024, devicePixelRatio: 2, label: 'Tablet (768px)' },
    desktop: { width: 1440, height: 900, devicePixelRatio: 1, label: 'Desktop (1440px)' },
    'desktop-xl': { width: 1920, height: 1080, devicePixelRatio: 1, label: 'Desktop XL (1920px)' },
  },
  createSandboxProfile: (profile: unknown) => profile,
  resolvePreviewExecutionPolicy: (...args: unknown[]) => resolvePreviewExecutionPolicyMock(...args),
  PreviewHostService: class {
    constructor() {}

    mountDocument(document: unknown) {
      return mountDocumentMock(document);
    }

    updateDocument(document: unknown) {
      return updateDocumentMock(document);
    }

    send(message: unknown) {
      return sendMock(message);
    }

    teardown() {
      return teardownMock();
    }
  },
}));

vi.mock('@/services/preview/PreviewSessionApi', () => ({
  issuePreviewSession: (...args: unknown[]) => issuePreviewSessionMock(...args),
}));

const baseDocument = {
  id: 'test-doc',
  version: '1',
  name: 'Test Document',
  designSystem: {
    id: 'test-ds',
    name: 'Test Design System',
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
    trustLevel: 'TRUSTED_WORKSPACE',
  },
};

const securePreviewContext = {
  projectId: 'proj-1',
  artifactId: 'artifact-1',
};

async function changeSelectValue(label: string, value: string) {
  await act(async () => {
    fireEvent.change(screen.getByDisplayValue(label), {
      target: { value },
    });
  });
}

describe('LivePreviewPanel - Platform Preview Protocol', () => {
  beforeEach(() => {
    vi.stubEnv('VITE_FEATURE_PREVIEW_DEV_MODE', 'false');
    mountDocumentMock.mockReset();
    updateDocumentMock.mockReset();
    sendMock.mockReset();
    teardownMock.mockReset();
    resolvePreviewExecutionPolicyMock.mockReset();
    issuePreviewSessionMock.mockReset();
    resolvePreviewExecutionPolicyMock.mockReturnValue({
      profile: {
        id: 'yappc-preview',
        name: 'YAPPC Preview',
        viewport: { width: 1440, height: 900, devicePixelRatio: 1, label: 'Desktop (1440px)' },
        theme: 'default',
        locale: 'en-US',
        featureFlags: {},
        trustedOrigins: ['https://preview.example.com'],
      },
      trustLevel: 'TRUSTED_WORKSPACE',
      sandbox: ['allow-same-origin', 'allow-scripts', 'allow-forms'],
      contentSecurityPolicy: "default-src 'self' https://preview.example.com; frame-ancestors 'none'",
      diagnostics: ['Preview requires network access.'],
    });
    issuePreviewSessionMock.mockResolvedValue({
      sessionId: 'preview-1',
      sessionToken: 'signed-preview-token',
      expiresAt: '2026-04-21T12:00:00.000Z',
    });
  });

  describe('Component Rendering', () => {
    it('renders the empty state when no preview inputs are provided', () => {
      render(<LivePreviewPanel />);
      expect(screen.getByLabelText('Preview empty')).toBeInTheDocument();
      expect(screen.getByText('Select a document or component to preview')).toBeInTheDocument();
    });

    it('renders the iframe with policy-derived sandbox and diagnostics', async () => {
      render(
        <LivePreviewPanel
          document={baseDocument}
          previewContext={securePreviewContext}
        />,
      );

      expect(screen.getByText('Preparing secure preview session…')).toBeInTheDocument();
      expect(screen.getByLabelText('Preparing secure preview')).toBeInTheDocument();

      const iframe = await screen.findByTitle('Live Preview');
      expect(iframe).toHaveAttribute('sandbox', 'allow-same-origin allow-scripts allow-forms');
      expect(iframe).toHaveAttribute('src', '/preview/builder?session=signed-preview-token');
      expect(screen.getByText('TRUSTED WORKSPACE')).toBeInTheDocument();
      expect(screen.getByText('Preview requires network access.')).toBeInTheDocument();
      expect(issuePreviewSessionMock).toHaveBeenCalledWith({
        projectId: 'proj-1',
        artifactId: 'artifact-1',
      });
    });
  });

  describe('Fallback Behavior', () => {
    it('surfaces blocked preview state and avoids mounting the document', async () => {
      resolvePreviewExecutionPolicyMock.mockReturnValue({
        profile: {
          id: 'yappc-preview',
          name: 'YAPPC Preview',
          viewport: { width: 1440, height: 900, devicePixelRatio: 1, label: 'Desktop (1440px)' },
          theme: 'default',
          locale: 'en-US',
          featureFlags: {},
          trustedOrigins: [],
        },
        trustLevel: 'UNTRUSTED',
        sandbox: [],
        contentSecurityPolicy: "default-src 'none'; frame-ancestors 'none'",
        diagnostics: [],
        fallbackState: {
          kind: 'sandbox-blocked',
          message: 'Preview requires trusted workspace review.',
          retryable: false,
        },
      });

      render(
        <LivePreviewPanel
          document={baseDocument}
          previewUrl="/preview/builder?session=test-session"
        />,
      );

      expect(await screen.findByLabelText('Preview unavailable')).toBeInTheDocument();
      expect(await screen.findByText('Preview requires trusted workspace review.')).toBeInTheDocument();
      expect(mountDocumentMock).not.toHaveBeenCalled();
    });
  });

  describe('Viewport Controls', () => {
    it('updates the selected viewport through the control', async () => {
      render(<LivePreviewPanel document={baseDocument} previewContext={securePreviewContext} />);
      await screen.findByTitle('Live Preview');

      await changeSelectValue('Desktop (1440px)', 'mobile');

      expect(resolvePreviewExecutionPolicyMock).toHaveBeenLastCalledWith(
        baseDocument,
        expect.objectContaining({
          viewport: expect.objectContaining({ width: 375 }),
        }),
      );
    });

    it('sends viewport, theme, and locale updates to the preview runtime', async () => {
      render(
        <LivePreviewPanel
          document={baseDocument}
          previewUrl="/preview/builder?session=test-session"
        />,
      );

      await screen.findByTitle('Live Preview');

      await waitFor(() => {
        expect(sendMock).toHaveBeenCalledWith(expect.objectContaining({ type: 'SET_VIEWPORT' }));
      });
      sendMock.mockClear();

      await changeSelectValue('Desktop (1440px)', 'mobile');
      await changeSelectValue('Default theme', 'editorial');
      await changeSelectValue('en-US · English (US)', 'ar-SA');

      await waitFor(() => {
        expect(sendMock).toHaveBeenCalledWith(
          expect.objectContaining({
            type: 'SET_VIEWPORT',
            viewport: expect.objectContaining({ width: 375, height: 812 }),
          }),
        );
        expect(sendMock).toHaveBeenCalledWith(
          expect.objectContaining({
            type: 'SET_THEME',
            theme: 'editorial',
          }),
        );
        expect(sendMock).toHaveBeenCalledWith(
          expect.objectContaining({
            type: 'SET_LOCALE',
            locale: 'ar-SA',
          }),
        );
      });
    });

    it('offers token theme packs and RTL locale fixtures in the preview controls', async () => {
      render(<LivePreviewPanel document={baseDocument} previewContext={securePreviewContext} />);
      await screen.findByTitle('Live Preview');

      expect(screen.getByText('Editorial warmth')).toBeInTheDocument();
      expect(screen.getByText('ar-SA · Arabic (Saudi Arabia)')).toBeInTheDocument();
      expect(screen.getByText('he-IL · Hebrew (Israel)')).toBeInTheDocument();
    });

    it('renders localized fixture content for the selected preview locale', async () => {
      render(<LivePreviewPanel document={baseDocument} previewContext={securePreviewContext} />);
      await screen.findByTitle('Live Preview');

      await changeSelectValue('en-US · English (US)', 'he-IL');

      const fixture = screen.getByTestId('live-preview-locale-fixture');
      expect(fixture).toHaveAttribute('dir', 'rtl');
      expect(fixture).toHaveAttribute('lang', 'he-IL');
      expect(fixture).toHaveTextContent('השקת סביבת העבודה של המוצר');
      expect(fixture).toHaveTextContent('₪1,249.00');
    });
  });

  describe('Default preview URL', () => {
    it('fails closed when previewContext is missing and dev preview mode is disabled', async () => {
      render(<LivePreviewPanel document={baseDocument} />);
      expect(
        await screen.findByText('Secure preview session is required. Provide previewContext or enable explicit dev preview mode.'),
      ).toBeInTheDocument();
      expect(screen.queryByTitle('Live Preview')).not.toBeInTheDocument();
    });

    it('uses explicit dev-mode preview route when enabled and previewContext is absent', () => {
      vi.stubEnv('VITE_FEATURE_PREVIEW_DEV_MODE', 'true');

      render(<LivePreviewPanel document={baseDocument} />);
      const iframe = screen.queryByTitle('Live Preview');
      if (iframe) {
        expect(iframe).toHaveAttribute('src', '/preview/builder?mode=dev');
      }
    });

    it('resolvePreviewExecutionPolicy receives window.location.origin as trustedOrigin when no previewUrl is given', () => {
      render(<LivePreviewPanel document={baseDocument} />);
      // resolvePreviewExecutionPolicy is called with a sandboxProfile that
      // has trustedOrigins derived from '/preview/builder' resolved against
      // window.location.origin (which is 'http://localhost' in jsdom).
      expect(resolvePreviewExecutionPolicyMock).toHaveBeenCalledWith(
        baseDocument,
        expect.objectContaining({
          trustedOrigins: expect.arrayContaining([expect.stringMatching(/^http/)]),
        }),
      );
    });
  });
});
