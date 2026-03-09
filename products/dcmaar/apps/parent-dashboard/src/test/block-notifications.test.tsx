/* eslint-disable @typescript-eslint/no-explicit-any */
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import { BlockNotifications } from '../components/BlockNotifications';
import { renderWithDashboardProviders } from './utils/renderWithProviders';
import { websocketService } from '../services/websocket.service';

// Mock the services
vi.mock('../services/websocket.service', () => ({
  websocketService: {
    on: vi.fn(() => () => {}),
  },
}));

// NOTE: Do NOT mock Jotai - use renderWithDashboardProviders which properly wraps in JotaiProvider

describe('BlockNotifications Component', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  // Helper to render component with providers
  const renderComponent = () =>
    renderWithDashboardProviders(<BlockNotifications />, { withRouter: false });

  it('should render with no events message', () => {
    renderComponent();

    expect(screen.getByText('Block Event Notifications')).toBeInTheDocument();
    expect(screen.getByText(/no block events yet/i)).toBeInTheDocument();
  });

  it('should display statistics cards', () => {
    renderComponent();

    expect(screen.getByText('Total Blocks')).toBeInTheDocument();
    expect(screen.getByText('Affected Devices')).toBeInTheDocument();
    expect(screen.getByText('Recent Activity')).toBeInTheDocument();
  });

  it('should render filter inputs', () => {
    renderComponent();

    expect(screen.getByPlaceholderText(/filter by policy id/i)).toBeInTheDocument();
    expect(screen.getByPlaceholderText(/filter by device id/i)).toBeInTheDocument();
    expect(screen.getByPlaceholderText(/filter by reason/i)).toBeInTheDocument();
  });

  it('should render block history table headers', () => {
    // Mock websocket to provide block events so table renders
    const mockEvent = {
      id: 'event-1',
      type: 'content-blocked',
      item: 'facebook.com',
      reason: 'Social media policy',
      deviceId: 'device-1',
      policyId: 'policy-1',
      timestamp: Date.now(),
    };

    // Trigger websocket event
    const mockOn = vi.fn((event, callback) => {
      if (event === 'block-event') {
        callback(mockEvent);
      }
      return vi.fn(); // return unsubscribe function
    });

    vi.mocked(websocketService.on).mockImplementation(mockOn);

    renderComponent();

    // Wait for event to be processed and table to render
    waitFor(() => {
      expect(screen.getByText('Item Blocked')).toBeInTheDocument();
      expect(screen.getByText('Reason')).toBeInTheDocument();
      expect(screen.getByText('Device')).toBeInTheDocument();
      expect(screen.getByText('Policy')).toBeInTheDocument();
      expect(screen.getByText('Time')).toBeInTheDocument();
    });
  });

  it('should show initial statistics as zero', () => {
    renderComponent();

    // Total Blocks should be 0
    const zeroElements = screen.getAllByText('0');
    expect(zeroElements.length).toBeGreaterThanOrEqual(1);
  });

  it('should show Quiet status when no events', () => {
    renderWithDashboardProviders(<BlockNotifications />, { withRouter: false });

    expect(screen.getByText('Quiet')).toBeInTheDocument();
  });
});
