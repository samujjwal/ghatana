/**
 * GeneratedCodeQualityGate tests (F-Y016 / AI-Y5)
 *
 * Verify that the Accept button is disabled while checks are pending/running/failed,
 * and only enabled when all three pass.
 */

import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import React from 'react';

import { GeneratedCodeQualityGate } from '../GeneratedCodeQualityGate';

// ── Mocks ──────────────────────────────────────────────────────────────────────

vi.mock('@/lib/http', () => ({
  parseJsonResponse: vi.fn((r) => Promise.resolve(r)),
}));

vi.mock('@/lib/utils', () => ({ cn: (...c: string[]) => c.filter(Boolean).join(' ') }));

// Stub fetch at the global level
const mockFetch = vi.hoisted(() => vi.fn());
vi.stubGlobal('fetch', mockFetch);

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

const allPassedQuality = {
  artifactId: 'art-1',
  compile: { status: 'PASSED' as const },
  lint: { status: 'PASSED' as const },
  test: { status: 'PASSED' as const },
  allPassed: true,
};

const failedQuality = {
  artifactId: 'art-1',
  compile: { status: 'PASSED' as const },
  lint: { status: 'FAILED' as const, detail: 'no-console violation', remediationSuggestion: 'Remove console.log' },
  test: { status: 'FAILED' as const, detail: '2 tests failed' },
  allPassed: false,
};

const pendingQuality = {
  artifactId: 'art-1',
  compile: { status: 'RUNNING' as const },
  lint: { status: 'PENDING' as const },
  test: { status: 'PENDING' as const },
  allPassed: false,
};

// ── Tests ─────────────────────────────────────────────────────────────────────

describe('GeneratedCodeQualityGate', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('shows loading state while fetching', async () => {
    mockFetch.mockReturnValue(new Promise(() => undefined)); // never resolves
    const qc = makeQC();
    render(
      <GeneratedCodeQualityGate artifactId="art-1" onAccept={vi.fn()} />,
      { wrapper: wrapper(qc) }
    );
    expect(await screen.findByTestId('quality-loading')).toBeInTheDocument();
  });

  it('Accept button is disabled when checks are pending', async () => {
    mockFetch.mockResolvedValue(pendingQuality);
    const qc = makeQC();
    render(
      <GeneratedCodeQualityGate artifactId="art-1" onAccept={vi.fn()} />,
      { wrapper: wrapper(qc) }
    );
    const btn = await screen.findByTestId('btn-accept');
    expect(btn).toBeDisabled();
    expect(screen.getByTestId('check-compile')).toBeInTheDocument();
  });

  it('Accept button is disabled when lint or test fails', async () => {
    mockFetch.mockResolvedValue(failedQuality);
    const qc = makeQC();
    render(
      <GeneratedCodeQualityGate artifactId="art-1" onAccept={vi.fn()} />,
      { wrapper: wrapper(qc) }
    );
    const btn = await screen.findByTestId('btn-accept');
    expect(btn).toBeDisabled();
    expect(screen.getByText('no-console violation')).toBeInTheDocument();
    expect(screen.getByText('Remove console.log')).toBeInTheDocument();
  });

  it('Accept button is enabled and calls onAccept when all checks pass', async () => {
    mockFetch.mockResolvedValue(allPassedQuality);
    const onAccept = vi.fn();
    const qc = makeQC();
    render(
      <GeneratedCodeQualityGate artifactId="art-1" onAccept={onAccept} />,
      { wrapper: wrapper(qc) }
    );
    const btn = await screen.findByTestId('btn-accept');
    expect(btn).not.toBeDisabled();
    fireEvent.click(btn);
    expect(onAccept).toHaveBeenCalledTimes(1);
  });

  it('shows check rows for compile, lint, test', async () => {
    mockFetch.mockResolvedValue(allPassedQuality);
    const qc = makeQC();
    render(
      <GeneratedCodeQualityGate artifactId="art-1" onAccept={vi.fn()} />,
      { wrapper: wrapper(qc) }
    );
    await screen.findByTestId('btn-accept');
    expect(screen.getByTestId('check-compile')).toBeInTheDocument();
    expect(screen.getByTestId('check-lint')).toBeInTheDocument();
    expect(screen.getByTestId('check-test')).toBeInTheDocument();
  });

  it('shows error state when fetch fails', async () => {
    mockFetch.mockRejectedValue(new Error('network'));
    const qc = makeQC();
    render(
      <GeneratedCodeQualityGate artifactId="art-1" onAccept={vi.fn()} />,
      { wrapper: wrapper(qc) }
    );
    expect(await screen.findByTestId('quality-error')).toBeInTheDocument();
    const btn = screen.getByTestId('btn-accept');
    expect(btn).toBeDisabled();
  });

  it('refresh button is present', async () => {
    mockFetch.mockResolvedValue(allPassedQuality);
    const qc = makeQC();
    render(
      <GeneratedCodeQualityGate artifactId="art-1" onAccept={vi.fn()} />,
      { wrapper: wrapper(qc) }
    );
    await screen.findByTestId('btn-accept');
    expect(screen.getByTestId('btn-refresh-quality')).toBeInTheDocument();
  });
});
