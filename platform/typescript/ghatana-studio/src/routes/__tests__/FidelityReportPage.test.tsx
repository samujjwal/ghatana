/**
 * @fileoverview Tests for FidelityReportPage.
 *
 * Verifies that:
 * - The page shows a fallback message when no report is available
 * - The page renders a report from the Jotai workflow store (preferred path)
 * - The page falls back to React Router location state (legacy path)
 * - Score, critical/warning/info counts, and loss-point rows are rendered
 * - Navigation back button calls navigate(-1)
 *
 * @doc.type test
 * @doc.purpose Fidelity report page route tests
 * @doc.layer studio
 */

import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { Provider } from 'jotai';
import { createStore } from 'jotai';
import type { FidelityReport } from '@ghatana/artifact-contracts';
import {
  createPerfectFidelityReport,
  computeFidelityReport,
} from '@ghatana/artifact-contracts';
import { setArtifactWorkflowAtom } from '../../state/artifactWorkflowStore.js';
import FidelityReportPage from '../FidelityReportPage';

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
      <FidelityReportPage />
    </Provider>,
  );
}

function makeReport(score: number): FidelityReport {
  if (score >= 1) return createPerfectFidelityReport('test-scope');
  return computeFidelityReport(
    [
      { code: 'CRITICAL_001', description: 'Unresolved import', severity: 'critical', confidenceImpact: 0.1 },
      { code: 'WARN_001', description: 'Type narrowing dropped', severity: 'warning', confidenceImpact: 0.1 },
      { code: 'INFO_001', description: 'Comment stripped', severity: 'info', confidenceImpact: 0.1 },
    ],
    'test-scope',
  );
}

// ============================================================================
// Tests
// ============================================================================

describe('FidelityReportPage', () => {
  beforeEach(() => {
    navigateMock.mockReset();
    locationStateMock = {};
  });

  describe('empty state', () => {
    it('shows a no-report message and an Import link when no store report and no router state', () => {
      renderWithStore();
      expect(screen.getByText('Fidelity Report')).toBeInTheDocument();
      expect(screen.getByText(/No fidelity report data available/)).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /Import/i })).toBeInTheDocument();
    });

    it('clicking the import link navigates to /import', () => {
      renderWithStore();
      fireEvent.click(screen.getByRole('button', { name: /Import/i }));
      expect(navigateMock).toHaveBeenCalledWith('/import');
    });
  });

  describe('workflow store (preferred path)', () => {
    it('renders the fidelity score as a percentage from the store report', () => {
      const store = createStore();
      const report = createPerfectFidelityReport('scope-1');
      store.set(setArtifactWorkflowAtom, { fidelityReport: report });

      renderWithStore(store);

      expect(screen.getByText('100%')).toBeInTheDocument();
      expect(screen.getByText('Overall fidelity score')).toBeInTheDocument();
    });

    it('shows zero loss points for a perfect report', () => {
      const store = createStore();
      store.set(setArtifactWorkflowAtom, { fidelityReport: createPerfectFidelityReport('s') });
      renderWithStore(store);
      // For a perfect report, critical/warning/info counts are all 0
      expect(screen.getAllByText('0').length).toBeGreaterThan(0);
    });

    it('renders critical, warning, info counts from a degraded report', () => {
      const store = createStore();
      const report = makeReport(0.5);
      store.set(setArtifactWorkflowAtom, { fidelityReport: report });
      renderWithStore(store);

      // The score for our 3-loss-point report is < 100%
      expect(screen.queryByText('100%')).not.toBeInTheDocument();
      // Critical, warning, info counts should render (each is 1)
      expect(screen.getAllByText('1').length).toBeGreaterThanOrEqual(1);
    });

    it('renders each loss point row', () => {
      const store = createStore();
      const report = makeReport(0.5);
      store.set(setArtifactWorkflowAtom, { fidelityReport: report });
      renderWithStore(store);

      expect(screen.getByText('Unresolved import')).toBeInTheDocument();
      expect(screen.getByText('Type narrowing dropped')).toBeInTheDocument();
      expect(screen.getByText('Comment stripped')).toBeInTheDocument();
    });

    it('renders the scopeId in the pipeline label', () => {
      const store = createStore();
      const report = createPerfectFidelityReport('my-scope');
      store.set(setArtifactWorkflowAtom, { fidelityReport: report });
      renderWithStore(store);

      expect(screen.getByText('my-scope')).toBeInTheDocument();
    });
  });

  describe('router-state fallback (legacy path)', () => {
    it('renders the report from router state when the store is empty', () => {
      const report = createPerfectFidelityReport('legacy-scope');
      locationStateMock = { report };

      renderWithStore(); // fresh store — no workflow data
      expect(screen.getByText('100%')).toBeInTheDocument();
    });

    it('prefers the store report over router state when both are present', () => {
      const store = createStore();
      const storeReport = createPerfectFidelityReport('store-scope');
      store.set(setArtifactWorkflowAtom, { fidelityReport: storeReport });

      // Router state with a different scopeId
      locationStateMock = { report: makeReport(0.5) };

      renderWithStore(store);
      // Should show store score (100%) not router state score (something else)
      expect(screen.getByText('100%')).toBeInTheDocument();
      expect(screen.getByText('store-scope')).toBeInTheDocument();
    });
  });

  describe('back navigation', () => {
    it('calls navigate(-1) when the Back button is clicked', () => {
      const store = createStore();
      store.set(setArtifactWorkflowAtom, { fidelityReport: createPerfectFidelityReport('s') });
      renderWithStore(store);

      fireEvent.click(screen.getByRole('button', { name: /← Back/i }));
      expect(navigateMock).toHaveBeenCalledWith(-1);
    });
  });
});
