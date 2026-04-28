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

interface MockEventSourceInstance {
  onmessage: MessageHandler | null;
  onerror: EventHandler | null;
  addEventListener: (type: string, handler: EventHandler) => void;
  close: () => void;
  url: string;
  withCredentials: boolean;
}

let mockInstance: MockEventSourceInstance | null = null;
const MockEventSource = vi.fn().mockImplementation(
  (url: string, opts?: { withCredentials?: boolean }): MockEventSourceInstance => {
    const doneHandlers: EventHandler[] = [];
    const instance: MockEventSourceInstance = {
      url,
      withCredentials: opts?.withCredentials ?? false,
      onmessage: null,
      onerror: null,
      addEventListener: (type: string, handler: EventHandler) => {
        if (type === 'done') doneHandlers.push(handler);
      },
      close: vi.fn(),
    };
    // Expose done triggering for test control
    (instance as MockEventSourceInstance & { _triggerDone: () => void })._triggerDone = () => {
      doneHandlers.forEach((h) => h());
    };
    mockInstance = instance;
    return instance;
  }
);

vi.stubGlobal('EventSource', MockEventSource);

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
      (mockInstance as MockEventSourceInstance & { _triggerDone: () => void })._triggerDone();
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
      (mockInstance as MockEventSourceInstance & { _triggerDone: () => void })._triggerDone();
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
