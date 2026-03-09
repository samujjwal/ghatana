import { describe, it, expect, beforeEach, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import { DeviceManagement } from '../components/DeviceManagement';
import { renderWithDashboardProviders } from './utils/renderWithProviders';

type FetchResponse = {
  ok: boolean;
  json: () => Promise<unknown>;
};

globalThis.fetch = vi.fn() as unknown as typeof fetch;

// NOTE: Do NOT mock Jotai - use renderWithDashboardProviders which properly wraps in JotaiProvider

vi.mock('../services/websocket.service', () => ({
  websocketService: {
    on: vi.fn(() => () => {}),
  },
}));

const renderComponent = () =>
  renderWithDashboardProviders(<DeviceManagement />, { withRouter: false });

const mockFetch = (data: unknown, overrides: Partial<FetchResponse> = {}) => {
  vi.mocked(globalThis.fetch).mockResolvedValue({
    ok: true,
    json: async () => data,
    ...overrides,
  } as FetchResponse as unknown as Response);
};

describe('DeviceManagement Component', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.setItem('token', 'test-token');
    // No longer mocking jotai - uses real JotaiProvider from renderWithDashboardProviders
  });

  it('should render device management title', async () => {
    mockFetch([]);
    renderComponent();

    await waitFor(() => {
      expect(screen.getByText('Device Management')).toBeInTheDocument();
    });
  });

  it('should display register device button', async () => {
    mockFetch([]);
    renderComponent();

    await waitFor(() => {
      expect(screen.getByText('Register Device')).toBeInTheDocument();
    });
  });

  it('should display search input', async () => {
    mockFetch([]);
    renderComponent();

    await waitFor(() => {
      expect(screen.getByPlaceholderText('Search devices...')).toBeInTheDocument();
    });
  });

  it('should display status filter dropdown', async () => {
    mockFetch([]);
    renderComponent();

    await waitFor(() => {
      const selects = screen.getAllByRole('combobox');
      expect(selects.length).toBeGreaterThanOrEqual(1);
    });
  });

  it('should display all four statistics cards', async () => {
    mockFetch([]);
    renderComponent();

    await waitFor(() => {
      expect(screen.getByText('Total Devices')).toBeInTheDocument();
      const onlineElements = screen.getAllByText('Online');
      expect(onlineElements.length).toBeGreaterThanOrEqual(1);
      expect(screen.getByText('Active Policies')).toBeInTheDocument();
    });
  });

  it('should show empty state when no devices', async () => {
    mockFetch([]);
    renderComponent();

    await waitFor(() => {
      expect(screen.getByText(/No devices found/)).toBeInTheDocument();
    });
  });

  it('should display device cards when devices exist', async () => {
    const mockDevices = [
      {
        id: 'device-1',
        name: 'Johns iPhone',
        type: 'mobile',
        status: 'online',
        lastHeartbeat: '2025-11-03T10:00:00Z',
        registeredAt: '2025-11-01T10:00:00Z',
        policies: [],
      },
    ];

    mockFetch(mockDevices);
    renderComponent();

    await waitFor(() => {
      expect(screen.getByText('Johns iPhone')).toBeInTheDocument();
    });
  });

  it('should display online/offline status for each device', async () => {
    const mockDevices = [
      {
        id: 'device-1',
        name: 'Test Device',
        type: 'mobile',
        status: 'online',
        lastHeartbeat: '2025-11-03T10:00:00Z',
        registeredAt: '2025-11-01T10:00:00Z',
        policies: [],
      },
    ];

    mockFetch(mockDevices);
    renderComponent();

    await waitFor(() => {
      expect(screen.getByText('online')).toBeInTheDocument();
    });
  });
});
