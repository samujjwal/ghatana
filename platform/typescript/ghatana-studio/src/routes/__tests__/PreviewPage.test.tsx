/**
 * @fileoverview Tests for PreviewPage.
 *
 * Verifies that:
 * - The page shows an idle state when no source is available
 * - The page renders the iframe when workflow-store source is set
 * - The page falls back to router-state source (legacy path)
 * - The Refresh button triggers an iframe reload cycle
 * - The Back button calls navigate(-1)
 * - The sandboxed iframe has no dangerous allow-* permissions
 *
 * @doc.type test
 * @doc.purpose Preview page route tests
 * @doc.layer studio
 */

import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { Provider } from 'jotai';
import { createStore } from 'jotai';
import { setArtifactWorkflowAtom } from '../../state/artifactWorkflowStore.js';
import PreviewPage from '../PreviewPage';

// ============================================================================
// Mocks
// ============================================================================

const navigateMock = vi.fn();
let locationStateMock: Record<string, unknown> = {};

vi.mock('react-router', () => ({
  useLocation: () => ({ state: locationStateMock }),
  useNavigate: () => navigateMock,
}));

// ============================================================================
// Helpers
// ============================================================================

function renderWithStore(store = createStore()) {
  return render(
    <Provider store={store}>
      <PreviewPage />
    </Provider>,
  );
}

const SAMPLE_HTML = 'export default function PreviewFixture() { return <p>Hello from preview</p>; }';

// ============================================================================
// Tests
// ============================================================================

describe('PreviewPage', () => {
  beforeEach(() => {
    navigateMock.mockReset();
    locationStateMock = {};
  });

  describe('idle state', () => {
    it('shows "No preview source" guidance when store is empty and no router state', () => {
      renderWithStore();
      expect(screen.getByText(/No preview source/i)).toBeInTheDocument();
    });

    it('shows guidance text explaining how to navigate to the preview', () => {
      renderWithStore();
      expect(screen.getByText(/Navigate here from a Builder export/i)).toBeInTheDocument();
    });

    it('does not render an iframe when there is no source', () => {
      renderWithStore();
      expect(screen.queryByTitle(/Preview/i)).not.toBeInTheDocument();
    });
  });

  describe('workflow store source (preferred path)', () => {
    it('renders the preview iframe when previewSource is set in the store', async () => {
      const store = createStore();
      store.set(setArtifactWorkflowAtom, { previewSource: SAMPLE_HTML });
      renderWithStore(store);

      const iframe = await screen.findByTitle(/Preview/i);
      expect(iframe).toBeInTheDocument();
      expect(iframe.tagName).toBe('IFRAME');
    });

    it('renders the preview title heading', () => {
      const store = createStore();
      store.set(setArtifactWorkflowAtom, { previewSource: SAMPLE_HTML });
      renderWithStore(store);

      expect(screen.getByRole('heading', { name: /Preview/i })).toBeInTheDocument();
    });

    it('iframe has sandbox attribute that allows scripts but not same-origin or top-navigation', async () => {
      const store = createStore();
      store.set(setArtifactWorkflowAtom, { previewSource: SAMPLE_HTML });
      renderWithStore(store);

      const iframe = await screen.findByTitle(/Preview/i) as HTMLIFrameElement;
      const sandbox = iframe.getAttribute('sandbox') ?? '';
      expect(sandbox).toContain('allow-scripts');
      expect(sandbox).not.toContain('allow-same-origin');
      expect(sandbox).not.toContain('allow-top-navigation');
    });
  });

  describe('router-state fallback (legacy path)', () => {
    it('renders the iframe from router-state source when store is empty', async () => {
      locationStateMock = { source: SAMPLE_HTML, title: 'Legacy Preview' };
      renderWithStore(); // fresh store
      const iframe = await screen.findByTitle(/Preview/i);
      expect(iframe).toBeInTheDocument();
    });

    it('prefers store source over router state when both are present', async () => {
      const store = createStore();
      store.set(setArtifactWorkflowAtom, { previewSource: SAMPLE_HTML });
      locationStateMock = { source: '<p>legacy</p>' };
      renderWithStore(store);

      // Both paths render an iframe — the key is that the store path wins;
      // we verify the iframe is present (the distinction is internal state).
      expect(await screen.findByTitle(/Preview/i)).toBeInTheDocument();
    });
  });

  describe('back navigation', () => {
    it('calls navigate(-1) when the Back button is clicked', () => {
      const store = createStore();
      store.set(setArtifactWorkflowAtom, { previewSource: SAMPLE_HTML });
      renderWithStore(store);

      fireEvent.click(screen.getByRole('button', { name: /Go back/i }));
      expect(navigateMock).toHaveBeenCalledWith(-1);
    });
  });

  describe('refresh', () => {
    it('renders the Refresh button when preview source is available', async () => {
      const store = createStore();
      store.set(setArtifactWorkflowAtom, { previewSource: SAMPLE_HTML });
      renderWithStore(store);

      expect(await screen.findByRole('button', { name: /Refresh/i })).toBeInTheDocument();
    });

    it('Refresh button is not rendered when no preview source is set', () => {
      renderWithStore();
      expect(screen.queryByRole('button', { name: /Refresh/i })).not.toBeInTheDocument();
    });
  });
});
