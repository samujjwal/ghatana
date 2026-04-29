/**
 * RefactorSuggestionPanel tests (F-Y017 / AI-Y4)
 *
 * Verify simulate-then-apply lifecycle: loading, empty state,
 * suggestion rows, simulate button, diff viewer, apply, undo, close.
 */

import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

import { RefactorSuggestionPanel } from '../RefactorSuggestionPanel';

// ── Mocks ──────────────────────────────────────────────────────────────────────

vi.mock('@/services/ai/refactoringSuggestionsApi', () => ({
  listRefactorSuggestions: vi.fn(),
  simulateRefactorSuggestion: vi.fn(),
  applyRefactorSuggestion: vi.fn(),
  undoRefactorSuggestion: vi.fn(),
}));

vi.mock('@/lib/utils', () => ({ cn: (...c: string[]) => c.filter(Boolean).join(' ') }));

import {
  listRefactorSuggestions,
  simulateRefactorSuggestion,
  applyRefactorSuggestion,
  undoRefactorSuggestion,
} from '@/services/ai/refactoringSuggestionsApi';

const mockedList = vi.mocked(listRefactorSuggestions);
const mockedSimulate = vi.mocked(simulateRefactorSuggestion);
const mockedApply = vi.mocked(applyRefactorSuggestion);
const mockedUndo = vi.mocked(undoRefactorSuggestion);

// ── Helpers ────────────────────────────────────────────────────────────────────

function makeQC() {
  return new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
}

function wrapper(qc: QueryClient) {
  return ({ children }: { children: React.ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  );
}

const baseSuggestion = {
  id: 'sug-1',
  designId: 'design-42',
  title: 'Extract service boundary',
  rationale: 'Transport leaks into domain',
  confidence: 0.87,
  affectedFiles: ['src/services/foo.ts'],
  status: 'PENDING' as const,
  createdAt: '2026-04-27T10:00:00Z',
};

const simulateResult = {
  suggestionId: 'sug-1',
  diff: [
    {
      path: 'src/services/foo.ts',
      diff: '-import { fetch } from "node-fetch"\n+import { httpClient } from "@/lib/http"',
      linesAdded: 1,
      linesRemoved: 1,
    },
  ],
  estimatedRiskLevel: 'LOW' as const,
  canApply: true,
  warnings: [],
};

// ── Tests ─────────────────────────────────────────────────────────────────────

describe('RefactorSuggestionPanel', () => {
  it('renders nothing when closed', () => {
    const qc = makeQC();
    render(
      <RefactorSuggestionPanel designId="design-42" open={false} onClose={vi.fn()} />,
      { wrapper: wrapper(qc) }
    );
    expect(screen.queryByTestId('refactor-suggestion-panel')).not.toBeInTheDocument();
  });

  it('shows loading spinner while fetching', async () => {
    mockedList.mockReturnValue(new Promise(() => undefined)); // never resolves
    const qc = makeQC();
    render(
      <RefactorSuggestionPanel designId="design-42" open onClose={vi.fn()} />,
      { wrapper: wrapper(qc) }
    );
    expect(await screen.findByTestId('loading-spinner')).toBeInTheDocument();
  });

  it('shows empty state when no suggestions', async () => {
    mockedList.mockResolvedValue({ designId: 'design-42', suggestions: [] });
    const qc = makeQC();
    render(
      <RefactorSuggestionPanel designId="design-42" open onClose={vi.fn()} />,
      { wrapper: wrapper(qc) }
    );
    expect(await screen.findByTestId('empty-state')).toBeInTheDocument();
  });

  it('renders suggestion row with title and rationale', async () => {
    mockedList.mockResolvedValue({ designId: 'design-42', suggestions: [baseSuggestion] });
    const qc = makeQC();
    render(
      <RefactorSuggestionPanel designId="design-42" open onClose={vi.fn()} />,
      { wrapper: wrapper(qc) }
    );
    expect(await screen.findByTestId('suggestion-row-sug-1')).toBeInTheDocument();
    expect(screen.getByText('Extract service boundary')).toBeInTheDocument();
  });

  it('expands row and shows Simulate button', async () => {
    mockedList.mockResolvedValue({ designId: 'design-42', suggestions: [baseSuggestion] });
    const qc = makeQC();
    render(
      <RefactorSuggestionPanel designId="design-42" open onClose={vi.fn()} />,
      { wrapper: wrapper(qc) }
    );
    await screen.findByTestId('suggestion-row-sug-1');
    // expand the row
    fireEvent.click(screen.getByTestId('btn-expand-sug-1'));
    expect(screen.getByTestId('btn-simulate-sug-1')).toBeInTheDocument();
  });

  it('calls simulateRefactorSuggestion and shows diff viewer', async () => {
    mockedList.mockResolvedValue({ designId: 'design-42', suggestions: [baseSuggestion] });
    mockedSimulate.mockResolvedValue(simulateResult);
    const qc = makeQC();
    render(
      <RefactorSuggestionPanel designId="design-42" open onClose={vi.fn()} />,
      { wrapper: wrapper(qc) }
    );
    await screen.findByTestId('suggestion-row-sug-1');
    // expand
    fireEvent.click(screen.getByTestId('btn-expand-sug-1'));
    // simulate
    fireEvent.click(screen.getByTestId('btn-simulate-sug-1'));
    await waitFor(() =>
      expect(mockedSimulate).toHaveBeenCalledWith('design-42', 'sug-1')
    );
    expect(await screen.findByTestId('diff-viewer')).toBeInTheDocument();
    expect(screen.getAllByText('src/services/foo.ts').length).toBeGreaterThan(0);
  });

  it('shows Apply button after simulate and calls applyRefactorSuggestion', async () => {
    const simulatedSuggestion = { ...baseSuggestion, status: 'SIMULATED' as const };
    mockedList.mockResolvedValue({ designId: 'design-42', suggestions: [simulatedSuggestion] });
    mockedApply.mockResolvedValue({
      suggestionId: 'sug-1',
      appliedAt: '2026-04-27T11:00:00Z',
      affectedFiles: ['src/services/foo.ts'],
    });
    const qc = makeQC();
    render(
      <RefactorSuggestionPanel designId="design-42" open onClose={vi.fn()} />,
      { wrapper: wrapper(qc) }
    );
    await screen.findByTestId('suggestion-row-sug-1');
    // expand
    fireEvent.click(screen.getByTestId('btn-expand-sug-1'));
    fireEvent.click(screen.getByTestId('btn-apply-sug-1'));
    await waitFor(() =>
      expect(mockedApply).toHaveBeenCalledWith('design-42', 'sug-1')
    );
  });

  it('shows Undo button for applied suggestions', async () => {
    const appliedSuggestion = { ...baseSuggestion, status: 'APPLIED' as const, appliedAt: '2026-04-27T11:00:00Z' };
    mockedList.mockResolvedValue({ designId: 'design-42', suggestions: [appliedSuggestion] });
    mockedUndo.mockResolvedValue({
      suggestionId: 'sug-1',
      undoneAt: '2026-04-27T12:00:00Z',
      restoredFiles: ['src/services/foo.ts'],
    });
    const qc = makeQC();
    render(
      <RefactorSuggestionPanel designId="design-42" open onClose={vi.fn()} />,
      { wrapper: wrapper(qc) }
    );
    await screen.findByTestId('suggestion-row-sug-1');
    // expand
    fireEvent.click(screen.getByTestId('btn-expand-sug-1'));
    expect(screen.getByTestId('btn-undo-sug-1')).toBeInTheDocument();
    fireEvent.click(screen.getByTestId('btn-undo-sug-1'));
    await waitFor(() =>
      expect(mockedUndo).toHaveBeenCalledWith('design-42', 'sug-1')
    );
  });

  it('calls onClose when close button clicked', async () => {
    mockedList.mockResolvedValue({ designId: 'design-42', suggestions: [] });
    const onClose = vi.fn();
    const qc = makeQC();
    render(
      <RefactorSuggestionPanel designId="design-42" open onClose={onClose} />,
      { wrapper: wrapper(qc) }
    );
    await screen.findByTestId('empty-state');
    fireEvent.click(screen.getByTestId('btn-close-refactor-panel'));
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it('shows error message when fetch fails', async () => {
    mockedList.mockRejectedValue(new Error('network error'));
    const qc = makeQC();
    render(
      <RefactorSuggestionPanel designId="design-42" open onClose={vi.fn()} />,
      { wrapper: wrapper(qc) }
    );
    expect(await screen.findByTestId('error-message')).toBeInTheDocument();
  });
});
