/**
 * PostmortemDraftPanel tests (AI-Y14)
 */

import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import React from 'react';

import { PostmortemDraftPanel } from '../PostmortemDraftPanel';
import type { PostmortemDraftResult } from '../PostmortemDraftPanel';

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

const sampleResult: PostmortemDraftResult = {
  incidentId: 'inc-1',
  draft: {
    title: 'Postmortem: API gateway latency spike 2026-04-27',
    severity: 'P2',
    summary: 'Elevated API latency for 45 min impacting 15% of users.',
    rootCause: 'Database connection pool saturation under burst traffic.',
    contributingFactors: ['No autoscaling configured', 'Missing rate limit'],
    timeline: [
      { time: '12:00 UTC', event: 'Spike detected by monitoring' },
      { time: '12:45 UTC', event: 'Connection pool flushed; latency recovered' },
    ],
    actionItems: [
      { id: 'a1', description: 'Add connection pool autoscaling', owner: 'Platform' },
    ],
    source: 'model',
    confidence: 0.79,
  },
};

// ── Tests ──────────────────────────────────────────────────────────────────────

describe('PostmortemDraftPanel (AI-Y14)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('shows generate button in idle state', () => {
    render(<PostmortemDraftPanel incidentId="inc-1" />);
    expect(screen.getByTestId('postmortem-generate-btn')).toBeInTheDocument();
  });

  it('shows loading state while generating', async () => {
    mockFetch.mockReturnValue(new Promise(() => {}));

    render(<PostmortemDraftPanel incidentId="inc-1" />);
    fireEvent.click(screen.getByTestId('postmortem-generate-btn'));

    expect(await screen.findByTestId('postmortem-loading')).toBeInTheDocument();
  });

  it('renders draft after successful generation', async () => {
    mockFetch.mockReturnValue(jsonOk(sampleResult));

    render(<PostmortemDraftPanel incidentId="inc-1" />);
    fireEvent.click(screen.getByTestId('postmortem-generate-btn'));

    expect(await screen.findByTestId('postmortem-draft-result')).toBeInTheDocument();
    expect(screen.getByTestId('postmortem-title')).toHaveTextContent(
      'Postmortem: API gateway latency spike 2026-04-27'
    );
    expect(screen.getByTestId('postmortem-timeline-0')).toBeInTheDocument();
    expect(screen.getByTestId('postmortem-action-a1')).toBeInTheDocument();
  });

  it('calls onAccept when accept button clicked', async () => {
    mockFetch.mockReturnValue(jsonOk(sampleResult));
    const onAccept = vi.fn();

    render(<PostmortemDraftPanel incidentId="inc-1" onAccept={onAccept} />);
    fireEvent.click(screen.getByTestId('postmortem-generate-btn'));

    await screen.findByTestId('postmortem-accept-btn');
    fireEvent.click(screen.getByTestId('postmortem-accept-btn'));
    expect(onAccept).toHaveBeenCalledWith(sampleResult.draft);
  });

  it('shows error state on fetch failure', async () => {
    mockFetch.mockReturnValue(Promise.resolve({ ok: false, status: 500, json: () => Promise.resolve({}) }));

    render(<PostmortemDraftPanel incidentId="inc-1" />);
    fireEvent.click(screen.getByTestId('postmortem-generate-btn'));

    expect(await screen.findByTestId('postmortem-error')).toBeInTheDocument();
  });

  it('allows retry after error', async () => {
    mockFetch.mockReturnValue(Promise.resolve({ ok: false, status: 500, json: () => Promise.resolve({}) }));

    render(<PostmortemDraftPanel incidentId="inc-1" />);
    fireEvent.click(screen.getByTestId('postmortem-generate-btn'));

    await screen.findByTestId('postmortem-error');
    fireEvent.click(screen.getByText('Try again'));
    await waitFor(() => expect(screen.getByTestId('postmortem-generate-btn')).toBeInTheDocument());
  });

  it('renders nothing when incidentId is empty', () => {
    const { container } = render(<PostmortemDraftPanel incidentId="" />);
    expect(container.firstChild).toBeNull();
  });
});
