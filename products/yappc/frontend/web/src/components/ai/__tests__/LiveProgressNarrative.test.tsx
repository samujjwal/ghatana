/**
 * LiveProgressNarrative tests (AI-Y6)
 */

import { render, screen, act, waitFor } from '@testing-library/react';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import React from 'react';

import { LiveProgressNarrative } from '../LiveProgressNarrative';

// ── Mock EventSource ───────────────────────────────────────────────────────────

type MessageHandler = (e: MessageEvent) => void;
type EventHandler = () => void;

let mockInstance: MockEventSourceClass | null = null;

class MockEventSourceClass {
  url: string;
  withCredentials: boolean;
  onmessage: MessageHandler | null = null;
  onerror: EventHandler | null = null;
  close = vi.fn();
  private doneHandlers: EventHandler[] = [];

  constructor(url: string, opts?: { withCredentials?: boolean }) {
    this.url = url;
    this.withCredentials = opts?.withCredentials ?? false;
    mockInstance = this;
  }

  addEventListener(type: string, handler: EventHandler) {
    if (type === 'done') this.doneHandlers.push(handler);
  }

  _triggerDone() {
    this.doneHandlers.forEach((h) => h());
  }
}

vi.stubGlobal('EventSource', MockEventSourceClass);

// ── Helpers ────────────────────────────────────────────────────────────────────

function stubSseUrl(runId: string) {
  return `/mock-sse/${runId}`;
}

// ── Tests ─────────────────────────────────────────────────────────────────────

describe('LiveProgressNarrative', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockInstance = null;
  });

  it('shows waiting state initially while streaming', () => {
    render(<LiveProgressNarrative runId="run-1" buildSseUrl={stubSseUrl} />);
    expect(screen.getByTestId('live-progress-narrative')).toBeInTheDocument();
    expect(screen.getByTestId('narrative-waiting')).toBeInTheDocument();
  });

  it('appends events as they arrive', () => {
    render(<LiveProgressNarrative runId="run-1" buildSseUrl={stubSseUrl} />);

    act(() => {
      mockInstance?.onmessage?.(
        new MessageEvent('message', {
          data: JSON.stringify({ id: 'evt-1', text: 'Analysing requirements…', timestamp: new Date().toISOString() }),
        })
      );
    });

    expect(screen.getByText('Analysing requirements…')).toBeInTheDocument();
  });

  it('shows "Run summary" header after done event', async () => {
    render(<LiveProgressNarrative runId="run-1" buildSseUrl={stubSseUrl} />);

    act(() => {
      mockInstance?.onmessage?.(
        new MessageEvent('message', {
          data: JSON.stringify({ id: 'evt-1', text: 'Step 1 complete', timestamp: new Date().toISOString() }),
        })
      );
        mockInstance?._triggerDone();
    });

    await waitFor(() => {
      expect(screen.getByText('Run summary')).toBeInTheDocument();
    });
  });

  it('calls onNarrativeComplete with joined text when done', async () => {
    const onComplete = vi.fn();
    render(
      <LiveProgressNarrative runId="run-1" buildSseUrl={stubSseUrl} onNarrativeComplete={onComplete} />
    );

    act(() => {
      mockInstance?.onmessage?.(
        new MessageEvent('message', {
          data: JSON.stringify({ id: 'e1', text: 'Line A', timestamp: new Date().toISOString() }),
        })
      );
      mockInstance?.onmessage?.(
        new MessageEvent('message', {
          data: JSON.stringify({ id: 'e2', text: 'Line B', timestamp: new Date().toISOString() }),
        })
      );
        mockInstance?._triggerDone();
    });

    await waitFor(() => {
      expect(onComplete).toHaveBeenCalledWith('Line A\nLine B');
    });
  });

  it('shows error state when SSE errors', () => {
    render(<LiveProgressNarrative runId="run-1" buildSseUrl={stubSseUrl} />);
    act(() => {
      mockInstance?.onerror?.();
    });
    expect(screen.getByTestId('narrative-error')).toBeInTheDocument();
  });
});
