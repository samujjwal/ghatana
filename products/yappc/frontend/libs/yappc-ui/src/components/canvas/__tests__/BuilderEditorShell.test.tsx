/**
 * BuilderEditorShell tests
 *
 * Tests focus on:
 * - Shell renders children and the status bar by default
 * - Status bar suppressed when `hideStatusBar` is true
 * - Autosave indicator reflects atom state (idle, pending, saving, saved, error)
 * - AI review indicator is hidden when count is 0, visible with count > 0
 * - Collab indicator shows offline / reconnecting / connected variants
 * - Preview mode indicator hidden when null, labeled correctly for each mode
 */

import React from 'react';
import { render, screen } from '@testing-library/react';
import { describe, it, expect, beforeEach } from 'vitest';
import { createStore, Provider } from 'jotai';

import {
  builderAutosaveStatusAtom,
  builderAIPendingReviewCountAtom,
  builderPreviewModeAtom,
  builderCollabSessionAtom,
  type AutosaveStatus,
  type BuilderCollabSession,
} from '@yappc/state';

import { BuilderEditorShell } from '../BuilderEditorShell';

// ─── Helpers ─────────────────────────────────────────────────────────────────

function renderShell(
  overrides: {
    autosave?: AutosaveStatus;
    aiCount?: number;
    previewMode?: 'trusted-local' | 'semi-trusted' | 'untrusted-sandbox' | null;
    collab?: BuilderCollabSession | null;
    hideStatusBar?: boolean;
  } = {},
) {
  const store = createStore();
  if (overrides.autosave !== undefined) {
    store.set(builderAutosaveStatusAtom, overrides.autosave);
  }
  if (overrides.aiCount !== undefined) {
    store.set(builderAIPendingReviewCountAtom, overrides.aiCount);
  }
  if (overrides.previewMode !== undefined) {
    store.set(builderPreviewModeAtom, overrides.previewMode);
  }
  if (overrides.collab !== undefined) {
    store.set(builderCollabSessionAtom, overrides.collab);
  }

  return render(
    <Provider store={store}>
      <BuilderEditorShell hideStatusBar={overrides.hideStatusBar}>
        <div data-testid="editor-surface">canvas</div>
      </BuilderEditorShell>
    </Provider>,
  );
}

// ─── Tests ───────────────────────────────────────────────────────────────────

describe('BuilderEditorShell', () => {
  describe('shell structure', () => {
    it('renders children', () => {
      renderShell();
      expect(screen.getByTestId('editor-surface')).toBeInTheDocument();
    });

    it('renders the status bar by default', () => {
      renderShell();
      expect(screen.getByRole('status')).toBeInTheDocument();
    });

    it('hides the status bar when hideStatusBar is true', () => {
      renderShell({ hideStatusBar: true });
      expect(screen.queryByRole('status')).not.toBeInTheDocument();
    });

    it('applies the outer shell data-testid', () => {
      renderShell();
      expect(screen.getByTestId('builder-editor-shell')).toBeInTheDocument();
    });
  });

  describe('autosave indicator', () => {
    it.each<[AutosaveStatus, string]>([
      ['idle', 'Saved'],
      ['saved', 'Saved'],
      ['pending', 'Unsaved changes'],
      ['saving', 'Saving…'],
      ['error', 'Save failed'],
    ])('shows "%s" label for status %s', (status, expectedLabel) => {
      renderShell({ autosave: status });
      expect(screen.getByText(expectedLabel)).toBeInTheDocument();
    });
  });

  describe('AI review indicator', () => {
    it('is hidden when count is 0', () => {
      renderShell({ aiCount: 0 });
      expect(screen.queryByText(/pending/)).not.toBeInTheDocument();
    });

    it('shows count when > 0', () => {
      renderShell({ aiCount: 3 });
      expect(screen.getByText('3 pending')).toBeInTheDocument();
    });

    it('shows singular label for count 1', () => {
      renderShell({ aiCount: 1 });
      expect(screen.getByText('1 pending')).toBeInTheDocument();
    });
  });

  describe('collab indicator', () => {
    it('shows Offline when collab session is null', () => {
      renderShell({ collab: null });
      expect(screen.getByText('Offline')).toBeInTheDocument();
    });

    it('shows Reconnecting when session is not connected', () => {
      const session: BuilderCollabSession = {
        sessionId: 's1',
        participantCount: 2,
        connected: false,
      };
      renderShell({ collab: session });
      expect(screen.getByText('Reconnecting')).toBeInTheDocument();
    });

    it('shows participant count when connected', () => {
      const session: BuilderCollabSession = {
        sessionId: 's1',
        participantCount: 4,
        connected: true,
      };
      renderShell({ collab: session });
      expect(screen.getByText('4')).toBeInTheDocument();
    });
  });

  describe('preview mode indicator', () => {
    it('is hidden when preview mode is null', () => {
      renderShell({ previewMode: null });
      expect(screen.queryByText(/Trusted|Semi-trusted|Sandboxed/)).not.toBeInTheDocument();
    });

    it.each<['trusted-local' | 'semi-trusted' | 'untrusted-sandbox', string]>([
      ['trusted-local', 'Trusted (local)'],
      ['semi-trusted', 'Semi-trusted'],
      ['untrusted-sandbox', 'Sandboxed'],
    ])('labels %s mode as "%s"', (mode, expectedLabel) => {
      renderShell({ previewMode: mode });
      expect(screen.getByText(expectedLabel)).toBeInTheDocument();
    });
  });
});
