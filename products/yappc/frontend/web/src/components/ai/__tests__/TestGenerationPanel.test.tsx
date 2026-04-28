/**
 * TestGenerationPanel tests (AI-Y13)
 */

import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import React from 'react';

import { TestGenerationPanel } from '../TestGenerationPanel';
import type { TestGenerationResult } from '../TestGenerationPanel';

// ── Mock fetch ─────────────────────────────────────────────────────────────────

const mockFetch = vi.fn();
vi.stubGlobal('fetch', mockFetch);

function jsonOk(data: unknown) {
  return Promise.resolve({
    ok: true,
    status: 200,
    json: () => Promise.resolve(data),
  } as Response);
}

const sampleResult: TestGenerationResult = {
  runId: 'run-1',
  language: 'TypeScript',
  framework: 'vitest',
  tests: [
    { id: 't1', name: 'should return 200 for valid input', code: 'it("...", () => {})' },
    { id: 't2', name: 'should return 400 for missing field', code: 'it("...", () => {})' },
  ],
  source: 'model',
  confidence: 0.76,
};

// ── Tests ──────────────────────────────────────────────────────────────────────

describe('TestGenerationPanel (AI-Y13)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders generate button in idle state', () => {
    render(<TestGenerationPanel runId="run-1" />);
    expect(screen.getByTestId('test-gen-btn')).toBeInTheDocument();
  });

  it('shows loading state while generating', async () => {
    mockFetch.mockReturnValue(new Promise(() => {}));

    render(<TestGenerationPanel runId="run-1" />);
    fireEvent.click(screen.getByTestId('test-gen-btn'));

    expect(await screen.findByTestId('test-gen-loading')).toBeInTheDocument();
  });

  it('renders generated tests after successful fetch', async () => {
    mockFetch.mockReturnValue(jsonOk(sampleResult));

    render(<TestGenerationPanel runId="run-1" />);
    fireEvent.click(screen.getByTestId('test-gen-btn'));

    expect(await screen.findByTestId('test-gen-result')).toBeInTheDocument();
    expect(screen.getByTestId('test-case-t1')).toBeInTheDocument();
    expect(screen.getByTestId('test-case-t2')).toBeInTheDocument();
  });

  it('calls onAccept when accept button clicked', async () => {
    mockFetch.mockReturnValue(jsonOk(sampleResult));
    const onAccept = vi.fn();

    render(<TestGenerationPanel runId="run-1" onAccept={onAccept} />);
    fireEvent.click(screen.getByTestId('test-gen-btn'));

    await screen.findByTestId('test-gen-accept-btn');
    fireEvent.click(screen.getByTestId('test-gen-accept-btn'));
    expect(onAccept).toHaveBeenCalledWith(sampleResult);
  });

  it('shows error state on fetch failure', async () => {
    mockFetch.mockReturnValue(Promise.resolve({ ok: false, status: 500, json: () => Promise.resolve({}) }));

    render(<TestGenerationPanel runId="run-1" />);
    fireEvent.click(screen.getByTestId('test-gen-btn'));

    expect(await screen.findByTestId('test-gen-error')).toBeInTheDocument();
  });

  it('allows retry after error', async () => {
    mockFetch.mockReturnValue(Promise.resolve({ ok: false, status: 500, json: () => Promise.resolve({}) }));

    render(<TestGenerationPanel runId="run-1" />);
    fireEvent.click(screen.getByTestId('test-gen-btn'));

    await screen.findByTestId('test-gen-error');
    fireEvent.click(screen.getByText('Try again'));
    await waitFor(() => expect(screen.getByTestId('test-gen-btn')).toBeInTheDocument());
  });

  it('renders nothing when runId is empty', () => {
    const { container } = render(<TestGenerationPanel runId="" />);
    expect(container.firstChild).toBeNull();
  });
});
