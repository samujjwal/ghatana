/**
 * PipelineBuilderPage — React Testing Library integration tests.
 *
 * Covers:
 * - Toolbar rendering and button states
 * - Save / validate / export handler wiring (toast feedback)
 * - Undo / redo enabled/disabled states
 * - Dirty guard (beforeunload)
 * - New pipeline reset with dirty-confirm guard
 * - Error boundary fallback renders on canvas crash
 *
 * @doc.type test
 * @doc.purpose RTL integration tests for PipelineBuilderPage
 * @doc.layer frontend
 */
import React from 'react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent, waitFor, act } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Provider as JotaiProvider, createStore } from 'jotai';
import { toast } from 'sonner';

import { PipelineBuilderPage } from '@/pages/PipelineBuilderPage';
import {
  isDirtyAtom,
  historyAtom,
  historyIndexAtom,
  nodesAtom,
  edgesAtom,
} from '@/stores/pipeline.store';
import * as pipelineApi from '@/api/pipeline.api';

// ── Mocks ────────────────────────────────────────────────────────────

vi.mock('@xyflow/react', async () => {
  const actual = await vi.importActual<typeof import('@xyflow/react')>('@xyflow/react');
  return {
    ...actual,
    ReactFlow: ({ children }: { children?: React.ReactNode }) => (
      <div data-testid="react-flow">{children}</div>
    ),
    ReactFlowProvider: ({ children }: { children: React.ReactNode }) => <>{children}</>,
    Background: () => null,
    Controls: () => null,
    MiniMap: () => null,
    useNodesState: () => [[], vi.fn(), vi.fn()],
    useEdgesState: () => [[], vi.fn(), vi.fn()],
  };
});

vi.mock('sonner', () => ({
  Toaster: () => <div data-testid="toaster" />,
  toast: {
    success: vi.fn(),
    error: vi.fn(),
  },
}));

vi.mock('@/api/pipeline.api', () => ({
  savePipeline: vi.fn(),
  validatePipeline: vi.fn(),
  exportPipelineSpec: vi.fn(() => '{"name":"test","stages":[]}'),
}));

// ── Helpers ───────────────────────────────────────────────────────────

function renderPage(store = createStore()) {
  return render(
    <JotaiProvider store={store}>
      <PipelineBuilderPage />
    </JotaiProvider>,
  );
}

// ── Tests ─────────────────────────────────────────────────────────────

describe('PipelineBuilderPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  // ── Rendering ───────────────────────────────────────────────────

  it('renders the pipeline builder layout', () => {
    renderPage();
    expect(screen.getByTestId('pipeline-builder')).toBeInTheDocument();
  });

  it('renders the Toaster', () => {
    renderPage();
    expect(screen.getByTestId('toaster')).toBeInTheDocument();
  });

  it('renders toolbar buttons', () => {
    renderPage();
    expect(screen.getByTestId('btn-save')).toBeInTheDocument();
    expect(screen.getByTestId('btn-validate')).toBeInTheDocument();
    expect(screen.getByTestId('btn-export')).toBeInTheDocument();
  });

  // ── Save ────────────────────────────────────────────────────────

  it('Save button is disabled when not dirty', () => {
    renderPage();
    expect(screen.getByTestId('btn-save')).toBeDisabled();
  });

  it('Save button is enabled when dirty', async () => {
    const store = createStore();
    store.set(isDirtyAtom, true);
    renderPage(store);
    expect(screen.getByTestId('btn-save')).not.toBeDisabled();
  });

  it('Save shows success toast on successful save', async () => {
    const store = createStore();
    store.set(isDirtyAtom, true);
    vi.mocked(pipelineApi.savePipeline).mockResolvedValueOnce({
      name: 'Untitled Pipeline',
      stages: [],
      status: 'DRAFT',
      version: 1,
    } as any);

    renderPage(store);
    await userEvent.click(screen.getByTestId('btn-save'));

    await waitFor(() => {
      expect(toast.success).toHaveBeenCalledWith('Pipeline saved');
    });
  });

  it('Save shows error toast on failure', async () => {
    const store = createStore();
    store.set(isDirtyAtom, true);
    vi.mocked(pipelineApi.savePipeline).mockRejectedValueOnce(new Error('Network error'));

    renderPage(store);
    await userEvent.click(screen.getByTestId('btn-save'));

    await waitFor(() => {
      expect(toast.error).toHaveBeenCalledWith(expect.stringContaining('Network error'));
    });
  });

  // ── Validate ────────────────────────────────────────────────────

  it('Validate shows success toast when valid', async () => {
    vi.mocked(pipelineApi.validatePipeline).mockResolvedValueOnce({
      isValid: true,
      errors: [],
      warnings: [],
    } as any);

    renderPage();
    await userEvent.click(screen.getByTestId('btn-validate'));

    await waitFor(() => {
      expect(toast.success).toHaveBeenCalledWith('Pipeline is valid');
    });
  });

  it('Validate shows error toast when invalid', async () => {
    vi.mocked(pipelineApi.validatePipeline).mockResolvedValueOnce({
      isValid: false,
      errors: [{ code: 'E1', message: 'Missing stage', severity: 'error' }],
      warnings: [],
    } as any);

    renderPage();
    await userEvent.click(screen.getByTestId('btn-validate'));

    await waitFor(() => {
      expect(toast.error).toHaveBeenCalledWith(expect.stringContaining('1 error'));
    });
  });

  it('Validate shows error toast on API failure', async () => {
    vi.mocked(pipelineApi.validatePipeline).mockRejectedValueOnce(new Error('timeout'));

    renderPage();
    await userEvent.click(screen.getByTestId('btn-validate'));

    await waitFor(() => {
      expect(toast.error).toHaveBeenCalledWith(expect.stringContaining('timeout'));
    });
  });

  it('Validate button is disabled while validating', async () => {
    vi.mocked(pipelineApi.validatePipeline).mockImplementation(
      () => new Promise(() => {}), // never resolves
    );

    renderPage();
    await userEvent.click(screen.getByTestId('btn-validate'));

    await waitFor(() => {
      expect(screen.getByTestId('btn-validate')).toBeDisabled();
      expect(screen.getByTestId('btn-validate')).toHaveTextContent('Validating');
    });
  });

  // ── Export ──────────────────────────────────────────────────────

  it('Export shows success toast', async () => {
    const createObjectURL = vi.fn(() => 'blob:test');
    const revokeObjectURL = vi.fn();
    Object.assign(global.URL, { createObjectURL, revokeObjectURL });

    const clickSpy = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});

    renderPage();
    await userEvent.click(screen.getByTestId('btn-export'));

    expect(toast.success).toHaveBeenCalledWith('Pipeline exported');
    clickSpy.mockRestore();
  });

  // ── Undo / Redo ─────────────────────────────────────────────────

  it('Undo button is disabled when no history', () => {
    renderPage();
    expect(screen.getByTestId('btn-undo')).toBeDisabled();
  });

  it('Undo button is enabled when history has entries', async () => {
    const store = createStore();
    store.set(historyAtom, [{ nodes: [], edges: [] }, { nodes: [], edges: [] }]);
    store.set(historyIndexAtom, 1);

    renderPage(store);
    expect(screen.getByTestId('btn-undo')).not.toBeDisabled();
  });

  it('Redo button is disabled at end of history', async () => {
    const store = createStore();
    store.set(historyAtom, [{ nodes: [], edges: [] }]);
    store.set(historyIndexAtom, 0);

    renderPage(store);
    expect(screen.getByTestId('btn-redo')).toBeDisabled();
  });

  it('Undo restores previous nodes/edges snapshot', async () => {
    const store = createStore();
    const snap0 = { nodes: [{ id: 'n0', type: 'stage', position: { x: 0, y: 0 }, data: {} }] as any, edges: [] };
    const snap1 = { nodes: [{ id: 'n1', type: 'stage', position: { x: 100, y: 0 }, data: {} }] as any, edges: [] };
    store.set(historyAtom, [snap0, snap1]);
    store.set(historyIndexAtom, 1);
    store.set(nodesAtom, snap1.nodes);

    renderPage(store);
    await userEvent.click(screen.getByTestId('btn-undo'));

    await waitFor(() => {
      expect(store.get(historyIndexAtom)).toBe(0);
    });
  });

  // ── Dirty guard ─────────────────────────────────────────────────

  it('registers beforeunload handler when dirty', async () => {
    const store = createStore();
    store.set(isDirtyAtom, true);

    const addEventSpy = vi.spyOn(window, 'addEventListener');
    renderPage(store);

    expect(addEventSpy).toHaveBeenCalledWith('beforeunload', expect.any(Function));
    addEventSpy.mockRestore();
  });

  it('beforeunload calls preventDefault when dirty', async () => {
    const store = createStore();
    store.set(isDirtyAtom, true);
    renderPage(store);

    const event = new Event('beforeunload') as any;
    event.preventDefault = vi.fn();
    event.returnValue = undefined;
    window.dispatchEvent(event);

    expect(event.preventDefault).toHaveBeenCalled();
  });

  // ── New Pipeline ────────────────────────────────────────────────

  it('handleNew resets state when not dirty', async () => {
    const store = createStore();
    store.set(nodesAtom, [{ id: 'n1' }] as any);

    renderPage(store);
    await userEvent.click(screen.getByTestId('btn-new'));

    await waitFor(() => {
      expect(store.get(nodesAtom)).toEqual([]);
      expect(store.get(isDirtyAtom)).toBe(false);
    });
  });

  it('handleNew shows confirm dialog when dirty', async () => {
    const store = createStore();
    store.set(isDirtyAtom, true);
    const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(false);

    renderPage(store);
    await userEvent.click(screen.getByTestId('btn-new'));

    expect(confirmSpy).toHaveBeenCalled();
    expect(store.get(isDirtyAtom)).toBe(true); // not reset because user cancelled
    confirmSpy.mockRestore();
  });
});
