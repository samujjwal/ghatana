import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import { LivePreviewPanel } from '../LivePreviewPanel';

const mountDocumentMock = vi.fn();
const updateDocumentMock = vi.fn();
const sendMock = vi.fn();
const teardownMock = vi.fn();
const resolvePreviewExecutionPolicyMock = vi.fn();

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

describe('LivePreviewPanel - Platform Preview Protocol', () => {
  beforeEach(() => {
    mountDocumentMock.mockReset();
    updateDocumentMock.mockReset();
    sendMock.mockReset();
    teardownMock.mockReset();
    resolvePreviewExecutionPolicyMock.mockReset();
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
  });

  describe('Component Rendering', () => {
    it('renders the empty state when no preview inputs are provided', () => {
      render(<LivePreviewPanel />);
      expect(screen.getByText('Select a document or component to preview')).toBeInTheDocument();
    });

    it('renders the iframe with policy-derived sandbox and diagnostics', () => {
      render(
        <LivePreviewPanel
          document={baseDocument}
          previewUrl="https://preview.example.com/frame"
        />,
      );

      const iframe = screen.getByTitle('Live Preview');
      expect(iframe).toHaveAttribute('sandbox', 'allow-same-origin allow-scripts allow-forms');
      expect(iframe).toHaveAttribute(
        'csp',
        "default-src 'self' https://preview.example.com; frame-ancestors 'none'",
      );
      expect(screen.getByText('TRUSTED WORKSPACE')).toBeInTheDocument();
      expect(screen.getByText('Preview requires network access.')).toBeInTheDocument();
      expect(mountDocumentMock).toHaveBeenCalledWith(baseDocument);
    });
  });

  describe('Fallback Behavior', () => {
    it('surfaces blocked preview state and avoids mounting the document', () => {
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

      render(<LivePreviewPanel document={baseDocument} />);

      expect(screen.getByText('Preview requires trusted workspace review.')).toBeInTheDocument();
      expect(mountDocumentMock).not.toHaveBeenCalled();
    });
  });

  describe('Viewport Controls', () => {
    it('updates the selected viewport through the control', () => {
      render(<LivePreviewPanel document={baseDocument} />);

      fireEvent.change(screen.getByDisplayValue('Desktop (1440px)'), {
        target: { value: 'mobile' },
      });

      expect(resolvePreviewExecutionPolicyMock).toHaveBeenLastCalledWith(
        baseDocument,
        expect.objectContaining({
          viewport: expect.objectContaining({ width: 375 }),
        }),
      );
    });
  });

  describe('Default preview URL', () => {
    it('uses /preview/builder as the iframe src when previewUrl is not provided', () => {
      render(<LivePreviewPanel document={baseDocument} />);
      const iframe = screen.queryByTitle('Live Preview');
      if (iframe) {
        expect(iframe).toHaveAttribute('src', '/preview/builder');
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
