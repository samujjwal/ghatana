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
import type { EvidencePack, FidelityReport } from '@ghatana/artifact-contracts';
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

function makeEvidencePackWithGeneratedValidation(report: FidelityReport): EvidencePack {
  const validationResult = {
    targetId: 'model-1',
    passed: false,
    findings: [
      {
        code: 'TS2304',
        message: "Cannot find name 'Button'.",
        severity: 'error' as const,
        category: 'typescript' as const,
      },
    ],
    errorCount: 1,
    warningCount: 0,
    infoCount: 0,
    validatedAt: '2024-01-01T00:00:00.000Z',
  };

  return {
    evidenceId: 'evidence-validation',
    createdAt: '2024-01-01T00:00:00.000Z',
    modelId: 'model-1',
    label: 'Generated validation evidence',
    stage: 'round-trip',
    fidelity: report,
    residuals: {
      totalCount: 0,
      blockingCount: 0,
      islands: [],
      canCompileWithResiduals: true,
    },
    validationResult,
    generatedValidationEvidence: {
      targetId: 'model-1',
      passed: false,
      pipeline: validationResult,
      typeScriptDiagnostics: validationResult.findings,
      stages: [
        {
          stageId: 'typecheck',
          status: 'failed',
          summary: 'TypeScript typecheck reported one finding.',
          findings: validationResult.findings,
        },
        {
          stageId: 'build',
          status: 'not-run',
          summary: 'Build was not run for this evidence pack.',
          findings: [],
        },
      ],
      artifacts: [
        {
          label: 'src/App.tsx',
          relativePath: 'src/App.tsx',
          contentType: 'text/tsx',
        },
      ],
      validatedAt: '2024-01-01T00:00:00.000Z',
    },
    reviewStatus: 'needs-revision',
  };
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

    it('renders a round-trip diff summary when available', () => {
      const store = createStore();
      const report = createPerfectFidelityReport('diff-scope');
      store.set(setArtifactWorkflowAtom, {
        fidelityReport: report,
        roundTripDiffReport: {
          reportId: 'diff-report',
          modelId: 'model-1',
          diffs: [{
            diffId: 'src/App.tsx->src/App.tsx',
            originalPath: 'src/App.tsx',
            generatedPath: 'src/App.tsx',
            semanticallyEquivalent: true,
            hunks: [],
            addedLines: 0,
            removedLines: 0,
            unchangedLines: 10,
            diffedAt: '2024-01-01T00:00:00.000Z',
          }],
          fidelity: report,
          residuals: {
            totalCount: 0,
            blockingCount: 0,
            islands: [],
            canCompileWithResiduals: true,
          },
          isLossless: true,
          generatedAt: '2024-01-01T00:00:00.000Z',
        },
      });

      renderWithStore(store);

      expect(screen.getByText('Round-trip diff')).toBeInTheDocument();
      expect(screen.getByText('Semantic match')).toBeInTheDocument();
      expect(screen.getByText('src/App.tsx')).toBeInTheDocument();
    });

    it('supports diff triage actions and hunk expansion', () => {
      const store = createStore();
      const report = createPerfectFidelityReport('diff-scope');
      store.set(setArtifactWorkflowAtom, {
        fidelityReport: report,
        roundTripDiffReport: {
          reportId: 'triage-report',
          modelId: 'model-2',
          diffs: [{
            diffId: 'src/Button.tsx->src/Button.tsx',
            originalPath: 'src/Button.tsx',
            generatedPath: 'src/Button.tsx',
            semanticallyEquivalent: false,
            hunks: [{
              kind: 'changed',
              originalStart: 3,
              generatedStart: 3,
              lineCount: 2,
              originalSnippet: 'return <button>{label}</button>;',
              generatedSnippet: 'return <button type="button">{label}</button>;',
            }],
            addedLines: 1,
            removedLines: 1,
            unchangedLines: 5,
            diffedAt: '2024-01-01T00:00:00.000Z',
          }],
          fidelity: report,
          residuals: {
            totalCount: 0,
            blockingCount: 0,
            islands: [],
            canCompileWithResiduals: true,
          },
          isLossless: false,
          generatedAt: '2024-01-01T00:00:00.000Z',
        },
      });

      renderWithStore(store);

      expect(screen.getByText('Review required')).toBeInTheDocument();
      fireEvent.click(screen.getByRole('button', { name: 'Accept diff' }));
      expect(screen.getByText('Triage: accepted')).toBeInTheDocument();

      fireEvent.click(screen.getByText('View diff hunks (1)'));
      expect(screen.getByText('return <button>{label}</button>;')).toBeInTheDocument();
      expect(screen.getByText('return <button type="button">{label}</button>;')).toBeInTheDocument();
    });

    it('renders generated validation evidence from the evidence pack', () => {
      const store = createStore();
      const report = createPerfectFidelityReport('validation-scope');
      store.set(setArtifactWorkflowAtom, {
        fidelityReport: report,
        evidencePack: makeEvidencePackWithGeneratedValidation(report),
      });

      renderWithStore(store);

      expect(screen.getByTestId('evidence-validation-results')).toBeInTheDocument();
      expect(screen.getByText('Generated validation evidence')).toBeInTheDocument();
      expect(screen.getByText('Requires review')).toBeInTheDocument();
      expect(screen.getByText('typecheck')).toBeInTheDocument();
      expect(screen.getByText('build')).toBeInTheDocument();
      expect(screen.getByText('TS2304')).toBeInTheDocument();
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
