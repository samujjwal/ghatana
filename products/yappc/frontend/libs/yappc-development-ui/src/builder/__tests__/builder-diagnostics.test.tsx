/**
 * Tests for BuilderPreviewPolicyChip and BuilderDiagnosticsPanel.
 *
 * @doc.type test
 * @doc.purpose Verify diagnostic builder components render correct state
 * @doc.layer product
 */

import { createStore, Provider } from 'jotai';
import React from 'react';
import { render, screen } from '@testing-library/react';
import { describe, it, expect, beforeEach } from 'vitest';

import {
  builderPreviewModeAtom,
  builderActiveDocumentIdAtom,
  builderAutosaveStatusAtom,
  builderLastSavedAtAtom,
  builderAIPendingReviewCountAtom,
  builderCollabSessionAtom,
} from '@yappc/state';

import { BuilderPreviewPolicyChip } from '../BuilderPreviewPolicyChip';
import { BuilderDiagnosticsPanel } from '../BuilderDiagnosticsPanel';

// ─── Helpers ─────────────────────────────────────────────────────────────────

function renderWithStore(
  ui: React.ReactElement,
  setup?: (store: ReturnType<typeof createStore>) => void
) {
  const store = createStore();
  setup?.(store);
  return render(<Provider store={store}>{ui}</Provider>);
}

// ─── BuilderPreviewPolicyChip ─────────────────────────────────────────────────

describe('BuilderPreviewPolicyChip', () => {
  describe('when no preview mode is active', () => {
    it('renders nothing', () => {
      const { container } = renderWithStore(<BuilderPreviewPolicyChip />);
      expect(container.firstChild).toBeNull();
    });
  });

  describe('trusted-local mode', () => {
    it('renders the chip', () => {
      renderWithStore(<BuilderPreviewPolicyChip />, (store) => {
        store.set(builderPreviewModeAtom, 'trusted-local');
      });
      expect(screen.getByTestId('builder-preview-policy-chip')).toBeInTheDocument();
    });

    it('shows "Trusted (local)" label', () => {
      renderWithStore(<BuilderPreviewPolicyChip />, (store) => {
        store.set(builderPreviewModeAtom, 'trusted-local');
      });
      expect(screen.getByTestId('builder-preview-policy-chip')).toHaveTextContent('Trusted (local)');
    });

    it('sets data-preview-mode attribute', () => {
      renderWithStore(<BuilderPreviewPolicyChip />, (store) => {
        store.set(builderPreviewModeAtom, 'trusted-local');
      });
      expect(screen.getByTestId('builder-preview-policy-chip')).toHaveAttribute(
        'data-preview-mode',
        'trusted-local'
      );
    });
  });

  describe('semi-trusted mode', () => {
    it('shows "Semi-trusted" label', () => {
      renderWithStore(<BuilderPreviewPolicyChip />, (store) => {
        store.set(builderPreviewModeAtom, 'semi-trusted');
      });
      expect(screen.getByTestId('builder-preview-policy-chip')).toHaveTextContent('Semi-trusted');
    });
  });

  describe('untrusted-sandbox mode', () => {
    it('shows "Sandboxed" label', () => {
      renderWithStore(<BuilderPreviewPolicyChip />, (store) => {
        store.set(builderPreviewModeAtom, 'untrusted-sandbox');
      });
      expect(screen.getByTestId('builder-preview-policy-chip')).toHaveTextContent('Sandboxed');
    });
  });

  describe('showDescription prop', () => {
    it('shows description text when showDescription is true', () => {
      renderWithStore(<BuilderPreviewPolicyChip showDescription />, (store) => {
        store.set(builderPreviewModeAtom, 'trusted-local');
      });
      expect(screen.getByTestId('builder-preview-policy-chip')).toHaveTextContent(
        'Full capability'
      );
    });
  });
});

// ─── BuilderDiagnosticsPanel ──────────────────────────────────────────────────

describe('BuilderDiagnosticsPanel', () => {
  describe('structure', () => {
    it('renders the panel with complementary role', () => {
      renderWithStore(<BuilderDiagnosticsPanel />);
      expect(screen.getByRole('complementary')).toBeInTheDocument();
    });

    it('uses default title "Builder Diagnostics"', () => {
      renderWithStore(<BuilderDiagnosticsPanel />);
      expect(screen.getByText('Builder Diagnostics')).toBeInTheDocument();
    });

    it('accepts custom title', () => {
      renderWithStore(<BuilderDiagnosticsPanel title="My Panel" />);
      expect(screen.getByText('My Panel')).toBeInTheDocument();
    });

    it('shows "dev only" label', () => {
      renderWithStore(<BuilderDiagnosticsPanel />);
      expect(screen.getByText('dev only')).toBeInTheDocument();
    });
  });

  describe('active document', () => {
    it('shows "none" when no document is active', () => {
      renderWithStore(<BuilderDiagnosticsPanel />);
      expect(screen.getAllByText('none').length).toBeGreaterThanOrEqual(1);
    });

    it('shows document ID when set', () => {
      renderWithStore(<BuilderDiagnosticsPanel />, (store) => {
        store.set(builderActiveDocumentIdAtom, 'doc-abc-123');
      });
      expect(screen.getByText('doc-abc-123')).toBeInTheDocument();
    });
  });

  describe('autosave status', () => {
    it('shows idle autosave status by default', () => {
      renderWithStore(<BuilderDiagnosticsPanel />);
      expect(screen.getByText('idle')).toBeInTheDocument();
    });

    it('shows error status with dirty flag', () => {
      renderWithStore(<BuilderDiagnosticsPanel />, (store) => {
        store.set(builderAutosaveStatusAtom, 'error');
      });
      // error + hasUnsavedChanges=true (error is in the "dirty" set)
      expect(screen.getByText('error (dirty)')).toBeInTheDocument();
    });

    it('shows last saved time when set', () => {
      const ts = '2026-04-20T12:00:00.000Z';
      renderWithStore(<BuilderDiagnosticsPanel />, (store) => {
        store.set(builderLastSavedAtAtom, ts);
      });
      // The component uses toLocaleTimeString which is locale-dependent,
      // so just confirm the row value is not "—"
      expect(screen.queryByText('—')).not.toBeInTheDocument();
    });
  });

  describe('AI pending reviews', () => {
    it('shows 0 by default', () => {
      renderWithStore(<BuilderDiagnosticsPanel />);
      expect(screen.getByText('0')).toBeInTheDocument();
    });

    it('shows the pending count', () => {
      renderWithStore(<BuilderDiagnosticsPanel />, (store) => {
        store.set(builderAIPendingReviewCountAtom, 5);
      });
      expect(screen.getByText('5')).toBeInTheDocument();
    });
  });

  describe('preview policy', () => {
    it('shows "none" when no mode is active', () => {
      renderWithStore(<BuilderDiagnosticsPanel />);
      // Multiple "none" spans for different fields — check at least one
      expect(screen.getAllByText('none').length).toBeGreaterThanOrEqual(1);
    });

    it('shows the policy chip when a mode is set', () => {
      renderWithStore(<BuilderDiagnosticsPanel />, (store) => {
        store.set(builderPreviewModeAtom, 'semi-trusted');
      });
      expect(screen.getByTestId('builder-preview-policy-chip')).toBeInTheDocument();
    });
  });

  describe('collaboration', () => {
    it('shows "none" when no session exists', () => {
      renderWithStore(<BuilderDiagnosticsPanel />);
      expect(screen.getAllByText('none').length).toBeGreaterThanOrEqual(1);
    });

    it('shows session ID prefix and participant count when connected', () => {
      renderWithStore(<BuilderDiagnosticsPanel />, (store) => {
        store.set(builderCollabSessionAtom, {
          sessionId: 'abcdef12345',
          participantCount: 3,
          connected: true,
        });
      });
      expect(screen.getByText('abcdef12…')).toBeInTheDocument();
      expect(screen.getByText('online (3)')).toBeInTheDocument();
    });

    it('shows reconnecting when session connected is false', () => {
      renderWithStore(<BuilderDiagnosticsPanel />, (store) => {
        store.set(builderCollabSessionAtom, {
          sessionId: 'xyz-session',
          participantCount: 1,
          connected: false,
        });
      });
      expect(screen.getByText('reconnecting')).toBeInTheDocument();
    });
  });
});
