import { describe, it, expect, beforeEach, vi } from 'vitest';
import { screen } from '@testing-library/react';
import { UsageMonitor } from '../components/UsageMonitor';
import { renderWithDashboardProviders } from './utils/renderWithProviders';

// Mock the services - must be at top level before any usage
vi.mock('../services/websocket.service', () => ({
  websocketService: {
    on: vi.fn(() => () => {}), // Return unsubscribe function
  },
}));

// NOTE: Do NOT mock Jotai - let the real JotaiProvider handle state
// The renderWithDashboardProviders function already wraps components in JotaiProvider

const renderComponent = () =>
  renderWithDashboardProviders(<UsageMonitor />, { withRouter: false });

describe('UsageMonitor Component', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('should render with no events message', () => {
    renderComponent();

    expect(screen.getByText('Real-Time Usage Monitoring')).toBeInTheDocument();
    expect(screen.getByText(/no usage events yet/i)).toBeInTheDocument();
  });

  it('should display statistics cards', () => {
    renderComponent();

    expect(screen.getByText('Total Sessions')).toBeInTheDocument();
    expect(screen.getByText('Total Duration')).toBeInTheDocument();
    expect(screen.getByText('Avg Session')).toBeInTheDocument();
  });

  it('should render filter inputs', () => {
    renderComponent();

    expect(screen.getByPlaceholderText(/filter by child id/i)).toBeInTheDocument();
    expect(screen.getByPlaceholderText(/filter by device id/i)).toBeInTheDocument();
    expect(screen.getByPlaceholderText(/filter by app\/website/i)).toBeInTheDocument();
  });

  it('should show initial statistics as zero', () => {
    renderComponent();

    // Total Sessions should be 0
    expect(screen.getByText('0')).toBeInTheDocument();
    // Total Duration and Avg Session should be 0m
    const minuteElements = screen.getAllByText('0m');
    expect(minuteElements.length).toBeGreaterThanOrEqual(2);
  });
});
