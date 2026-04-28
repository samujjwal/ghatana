/**
 * WebSocketDegradedBanner tests (C-Y5)
 */

import { render, screen, fireEvent } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import React from 'react';

import { WebSocketDegradedBanner } from '../WebSocketDegradedBanner';

// ── Mocks ──────────────────────────────────────────────────────────────────────

vi.mock('@/hooks/useWebSocketHealth', () => ({
  useWebSocketHealth: vi.fn(() => ({ health: 'ok', reconnectAttempt: 0, statusDetail: 'connected' })),
}));

vi.mock('@/contexts/WebSocketContext', () => ({ WebSocketContext: {} }));

// ── Tests ─────────────────────────────────────────────────────────────────────

describe('WebSocketDegradedBanner', () => {
  it('renders nothing when health is ok', () => {
    const { container } = render(<WebSocketDegradedBanner healthOverride="ok" />);
    expect(container).toBeEmptyDOMElement();
  });

  it('renders nothing when health is idle', () => {
    const { container } = render(<WebSocketDegradedBanner healthOverride="idle" />);
    expect(container).toBeEmptyDOMElement();
  });

  it('shows yellow degraded banner when health is degraded', () => {
    render(<WebSocketDegradedBanner healthOverride="degraded" reconnectAttemptOverride={2} />);
    const banner = screen.getByTestId('ws-degraded-banner');
    expect(banner).toBeInTheDocument();
    expect(banner.getAttribute('data-health')).toBe('degraded');
    expect(screen.getByText(/attempt 2/)).toBeInTheDocument();
  });

  it('shows red down banner when health is down', () => {
    render(<WebSocketDegradedBanner healthOverride="down" />);
    const banner = screen.getByTestId('ws-degraded-banner');
    expect(banner).toBeInTheDocument();
    expect(banner.getAttribute('data-health')).toBe('down');
    expect(screen.getByText(/unavailable/i)).toBeInTheDocument();
  });

  it('dismisses when user clicks dismiss button', () => {
    render(<WebSocketDegradedBanner healthOverride="degraded" />);
    const btn = screen.getByTestId('btn-dismiss-ws-banner');
    fireEvent.click(btn);
    expect(screen.queryByTestId('ws-degraded-banner')).not.toBeInTheDocument();
  });
});
