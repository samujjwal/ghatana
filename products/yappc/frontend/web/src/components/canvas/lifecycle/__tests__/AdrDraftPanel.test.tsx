/**
 * AdrDraftPanel tests (AI-Y12)
 */

import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import React from 'react';

import { AdrDraftPanel } from '../AdrDraftPanel';
import type { AdrDraftResult } from '../AdrDraftPanel';

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

const sampleResult: AdrDraftResult = {
  nodeId: 'node-1',
  draft: {
    title: 'Use PostgreSQL for primary data store',
    status: 'Proposed',
    context: 'We need a reliable relational DB.',
    decision: 'Adopt PostgreSQL 15.',
    consequences: 'Teams must manage DB migrations.',
    source: 'model',
    confidence: 0.82,
  },
};

// ── Tests ──────────────────────────────────────────────────────────────────────

describe('AdrDraftPanel (AI-Y12)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders generate button in idle state', () => {
    render(<AdrDraftPanel nodeId="node-1" />);
    expect(screen.getByTestId('adr-generate-btn')).toBeInTheDocument();
  });

  it('shows loading state while generating', async () => {
    mockFetch.mockReturnValue(new Promise(() => {}));

    render(<AdrDraftPanel nodeId="node-1" />);
    fireEvent.click(screen.getByTestId('adr-generate-btn'));

    expect(await screen.findByTestId('adr-loading')).toBeInTheDocument();
  });

  it('renders draft result after successful generation', async () => {
    mockFetch.mockReturnValue(jsonOk(sampleResult));

    render(<AdrDraftPanel nodeId="node-1" />);
    fireEvent.click(screen.getByTestId('adr-generate-btn'));

    expect(await screen.findByTestId('adr-draft-result')).toBeInTheDocument();
    expect(screen.getByTestId('adr-draft-title')).toHaveTextContent(
      'Use PostgreSQL for primary data store'
    );
  });

  it('calls onAcceptDraft when accept button is clicked', async () => {
    mockFetch.mockReturnValue(jsonOk(sampleResult));
    const onAccept = vi.fn();

    render(<AdrDraftPanel nodeId="node-1" onAcceptDraft={onAccept} />);
    fireEvent.click(screen.getByTestId('adr-generate-btn'));

    await screen.findByTestId('adr-accept-btn');
    fireEvent.click(screen.getByTestId('adr-accept-btn'));
    expect(onAccept).toHaveBeenCalledWith(sampleResult.draft);
  });

  it('shows error state on fetch failure', async () => {
    mockFetch.mockReturnValue(Promise.resolve({ ok: false, status: 500, json: () => Promise.resolve({}) }));

    render(<AdrDraftPanel nodeId="node-1" />);
    fireEvent.click(screen.getByTestId('adr-generate-btn'));

    expect(await screen.findByTestId('adr-error')).toBeInTheDocument();
  });

  it('allows regeneration after error', async () => {
    mockFetch.mockReturnValue(Promise.resolve({ ok: false, status: 500, json: () => Promise.resolve({}) }));

    render(<AdrDraftPanel nodeId="node-1" />);
    fireEvent.click(screen.getByTestId('adr-generate-btn'));

    await screen.findByTestId('adr-error');
    fireEvent.click(screen.getByText('Try again'));
    await waitFor(() => expect(screen.getByTestId('adr-generate-btn')).toBeInTheDocument());
  });

  it('renders nothing when nodeId is empty', () => {
    const { container } = render(<AdrDraftPanel nodeId="" />);
    expect(container.firstChild).toBeNull();
  });
});
