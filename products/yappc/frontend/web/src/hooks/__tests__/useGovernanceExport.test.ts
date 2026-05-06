/**
 * useGovernanceExport Hook Tests
 *
 * @doc.type test
 * @doc.purpose Unit tests for useGovernanceExport hook
 * @doc.layer product
 * @doc.pattern Unit Test
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { useGovernanceExport } from '../useGovernanceExport';
import {
  GovernanceExportError,
} from '../../services/canvas/commands/GovernanceExportService';

// ─── Mocks ────────────────────────────────────────────────────────────────────

vi.mock('../../services/canvas/commands/GovernanceExportService', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../services/canvas/commands/GovernanceExportService')>();
  return {
    ...actual,
    downloadGovernanceExport: vi.fn(),
  };
});

import { downloadGovernanceExport } from '../../services/canvas/commands/GovernanceExportService';
const mockDownload = vi.mocked(downloadGovernanceExport);

// ─── Tests ────────────────────────────────────────────────────────────────────

describe('useGovernanceExport', () => {
  beforeEach(() => {
    mockDownload.mockReset();
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  it('initialises with no loading, no error, no result', () => {
    const { result } = renderHook(() => useGovernanceExport('art-001'));

    expect(result.current.isExporting).toBe(false);
    expect(result.current.exportError).toBeNull();
    expect(result.current.lastResult).toBeNull();
  });

  it('sets isExporting=true while export is in flight', async () => {
    let resolve: (value: { artifactId: string; format: 'json'; recordCount: number }) => void = () => undefined;
    mockDownload.mockReturnValue(new Promise((res) => { resolve = res; }));

    const { result } = renderHook(() => useGovernanceExport('art-001'));

    act(() => {
      void result.current.exportAudit('json');
    });

    expect(result.current.isExporting).toBe(true);

    await act(async () => {
      resolve({ artifactId: 'art-001', format: 'json', recordCount: 5 });
    });

    expect(result.current.isExporting).toBe(false);
  });

  it('sets lastResult on success', async () => {
    const mockResult = { artifactId: 'art-001', format: 'json' as const, recordCount: 10 };
    mockDownload.mockResolvedValue(mockResult);

    const { result } = renderHook(() => useGovernanceExport('art-001'));

    await act(async () => {
      await result.current.exportAudit('json');
    });

    expect(result.current.lastResult).toEqual(mockResult);
    expect(result.current.exportError).toBeNull();
  });

  it('sets exportError when GovernanceExportError is thrown', async () => {
    mockDownload.mockRejectedValue(new GovernanceExportError('Governance export failed: 403 Forbidden', 403));

    const { result } = renderHook(() => useGovernanceExport('art-001'));

    await act(async () => {
      await result.current.exportAudit('json');
    });

    expect(result.current.exportError).toBe('Governance export failed: 403 Forbidden');
    expect(result.current.lastResult).toBeNull();
    expect(result.current.isExporting).toBe(false);
  });

  it('sets generic error message for unexpected errors', async () => {
    mockDownload.mockRejectedValue(new Error('Network failure'));

    const { result } = renderHook(() => useGovernanceExport('art-001'));

    await act(async () => {
      await result.current.exportAudit('csv');
    });

    expect(result.current.exportError).toBe('Governance export failed. Please try again.');
  });

  it('clears error via clearError()', async () => {
    mockDownload.mockRejectedValue(new GovernanceExportError('Export failed: 500 Server Error'));

    const { result } = renderHook(() => useGovernanceExport('art-001'));

    await act(async () => {
      await result.current.exportAudit('json');
    });

    expect(result.current.exportError).not.toBeNull();

    act(() => {
      result.current.clearError();
    });

    expect(result.current.exportError).toBeNull();
  });

  it('does not call download when artifactId is null', async () => {
    const { result } = renderHook(() => useGovernanceExport(null));

    await act(async () => {
      await result.current.exportAudit('json');
    });

    expect(mockDownload).not.toHaveBeenCalled();
    expect(result.current.exportError).toBe('No artifact selected for export.');
  });

  it('does not call download when artifactId is undefined', async () => {
    const { result } = renderHook(() => useGovernanceExport(undefined));

    await act(async () => {
      await result.current.exportAudit('csv');
    });

    expect(mockDownload).not.toHaveBeenCalled();
    expect(result.current.exportError).toBe('No artifact selected for export.');
  });

  it('passes the correct format to downloadGovernanceExport', async () => {
    mockDownload.mockResolvedValue({ artifactId: 'art-002', format: 'csv', recordCount: 3 });

    const { result } = renderHook(() => useGovernanceExport('art-002'));

    await act(async () => {
      await result.current.exportAudit('csv');
    });

    expect(mockDownload).toHaveBeenCalledWith('art-002', 'csv');
  });

  it('re-exports when artifactId changes via a fresh call', async () => {
    mockDownload.mockResolvedValue({ artifactId: 'art-new', format: 'json', recordCount: 1 });

    const { result, rerender } = renderHook(
      ({ id }: { id: string | null }) => useGovernanceExport(id),
      { initialProps: { id: 'art-old' } }
    );

    rerender({ id: 'art-new' });

    await act(async () => {
      await result.current.exportAudit('json');
    });

    expect(mockDownload).toHaveBeenCalledWith('art-new', 'json');
  });
});
