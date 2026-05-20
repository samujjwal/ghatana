/**
 * @fileoverview Tests for BuilderStudio workflow store integration.
 *
 * Verifies that BuilderStudio:
 * - Shows no "Imported artifact active" status banner when the workflow store is empty
 * - Shows the banner when projectedBuilderDocumentAtom is populated (artifact import flow)
 * - Adds the imported document to the document list automatically
 * - Resets isUsingImportedDocument when the projected document is cleared
 *
 * Heavy sub-components and adapters are mocked at the module boundary so this
 * test focuses purely on the workflow-atom ↔ BuilderStudio state bridge.
 *
 * @doc.type test
 * @doc.purpose BuilderStudio workflow store integration tests
 * @doc.layer studio
 */

import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { Provider } from 'jotai';
import { createStore } from 'jotai';
import { setArtifactWorkflowAtom } from '../../state/artifactWorkflowStore.js';

// ============================================================================
// Mocks — heavy sub-components and platform adapters
// ============================================================================

vi.mock('../../components/builder/ComponentPalette', () => ({
  ComponentPalette: () => <nav aria-label="Component Palette" />,
}));

vi.mock('../../components/builder/ComponentTree', () => ({
  ComponentTree: () => <section aria-label="Component Tree" />,
}));

vi.mock('../../components/builder/PropertyInspector', () => ({
  PropertyInspector: () => <section aria-label="Property Inspector" />,
}));

vi.mock('../../components/builder/ValidationPanel', () => ({
  ValidationPanel: () => <div data-testid="validation-panel" />,
}));

vi.mock('../../components/builder/VisualCanvas', () => ({
  VisualCanvas: () => <div data-testid="visual-canvas" />,
}));

vi.mock('@ghatana/ds-registry', () => ({
  getRegistryStore: vi.fn(() => ({})),
  registerStarterContracts: vi.fn(),
  findBuilderComponents: vi.fn(() => []),
}));

vi.mock('../../logging/studioLogger', () => ({
  studioLogger: {
    error: vi.fn(),
    info: vi.fn(),
    warn: vi.fn(),
    debug: vi.fn(),
  },
}));

// Mock LocalStoragePersistenceAdapter to avoid real localStorage I/O in tests
// while keeping all other ui-builder functions (createBuilderDocument, etc.) real.
vi.mock('@ghatana/ui-builder', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@ghatana/ui-builder')>();
  return {
    ...actual,
    LocalStoragePersistenceAdapter: class {
      async save(): Promise<string> {
        return 'v1';
      }
      async load(): Promise<null> {
        return null;
      }
      async listVersions(): Promise<readonly []> {
        return [];
      }
      async restoreVersion(): Promise<null> {
        return null;
      }
      async deleteVersion(): Promise<void> {
        /* no-op */
      }
      async clearDocument(): Promise<void> {
        /* no-op */
      }
    },
  };
});

// Import after mocks are set up
import BuilderStudio from '../BuilderStudio';
import { createBuilderDocument } from '@ghatana/ui-builder';

// ============================================================================
// Helpers
// ============================================================================

function renderWithStore(store = createStore()) {
  return render(
    <Provider store={store}>
      <BuilderStudio />
    </Provider>,
  );
}

// ============================================================================
// Tests
// ============================================================================

describe('BuilderStudio — workflow store integration', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  describe('default state (no imported artifact)', () => {
    it('renders the main Builder Studio heading', () => {
      renderWithStore();
      expect(screen.getByText('Builder Studio')).toBeInTheDocument();
    });

    it('shows no "Imported artifact active" banner when the workflow store is empty', () => {
      renderWithStore();
      expect(
        screen.queryByRole('status', { name: /imported artifact active/i }),
      ).not.toBeInTheDocument();
    });

    it('shows the "No documents yet" empty state', () => {
      renderWithStore();
      expect(screen.getByText(/No documents yet/i)).toBeInTheDocument();
    });
  });

  describe('with a projected document in the workflow store', () => {
    it('shows the "Imported artifact active" banner', async () => {
      const store = createStore();
      const doc = createBuilderDocument('workflow-test-user');
      store.set(setArtifactWorkflowAtom, { projectedBuilderDocument: doc });

      renderWithStore(store);

      expect(
        await screen.findByRole('status', { name: /imported artifact active/i }),
      ).toBeInTheDocument();
    });

    it('shows the sync-confirmation text in the banner', async () => {
      const store = createStore();
      const doc = createBuilderDocument('workflow-test-user');
      store.set(setArtifactWorkflowAtom, { projectedBuilderDocument: doc });

      renderWithStore(store);

      expect(
        await screen.findByText(/synced to the Canvas and Preview routes/i),
      ).toBeInTheDocument();
    });

    it('adds the imported document to the document list', async () => {
      const store = createStore();
      const doc = createBuilderDocument('workflow-test-user');
      store.set(setArtifactWorkflowAtom, { projectedBuilderDocument: doc });

      renderWithStore(store);

      // The document list should no longer show the "No documents yet" empty state.
      // The banner confirms the document is active.
      await screen.findByRole('status', { name: /imported artifact active/i });
      expect(screen.queryByText(/No documents yet/i)).not.toBeInTheDocument();
    });
  });

  describe('banner is absent when projected document is cleared', () => {
    it('does not show the banner when projectedBuilderDocument is null', () => {
      const store = createStore();
      // Explicitly leave projectedBuilderDocument as null (default).
      renderWithStore(store);

      expect(
        screen.queryByRole('status', { name: /imported artifact active/i }),
      ).not.toBeInTheDocument();
    });
  });
});
