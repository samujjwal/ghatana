import { describe, it, expect, beforeEach, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import { PolicyManagement } from '../components/PolicyManagement';
import { renderWithDashboardProviders } from './utils/renderWithProviders';

type FetchResponse = {
  ok: boolean;
  json: () => Promise<unknown>;
};

globalThis.fetch = vi.fn() as unknown as typeof fetch;

const renderComponent = () =>
  renderWithDashboardProviders(<PolicyManagement />, { withRouter: false });

const mockFetch = (data: unknown, overrides: Partial<FetchResponse> = {}) => {
  vi.mocked(globalThis.fetch).mockResolvedValue({
    ok: true,
    json: async () => data,
    ...overrides,
  } as FetchResponse as unknown as Response);
};

describe('PolicyManagement Component', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.setItem('token', 'test-token');
  });

  it('should render policy management title', async () => {
    mockFetch([]);
    renderComponent();

    await waitFor(() => {
      expect(screen.getByText('Policy Management')).toBeInTheDocument();
    });
  });

  it('should display create policy button', async () => {
    mockFetch([]);
    renderComponent();

    await waitFor(() => {
      expect(screen.getByText('Create Policy')).toBeInTheDocument();
    });
  });

  it('should display search input', async () => {
    mockFetch([]);
    renderComponent();

    await waitFor(() => {
      expect(screen.getByPlaceholderText('Search policies...')).toBeInTheDocument();
    });
  });

  it('should display type filter dropdown', async () => {
    mockFetch([]);
    renderComponent();

    await waitFor(() => {
      const select = screen.getByRole('combobox');
      expect(select).toBeInTheDocument();
    });
  });

  it('should display all four statistics cards', async () => {
    mockFetch([]);
    renderComponent();

    await waitFor(() => {
      expect(screen.getByText('Total Policies')).toBeInTheDocument();
      expect(screen.getByText('Time Limits')).toBeInTheDocument();
      expect(screen.getByText('Content Filters')).toBeInTheDocument();
      expect(screen.getByText('App Blocks')).toBeInTheDocument();
    });
  });

  it('should show empty state when no policies', async () => {
    mockFetch([]);
    renderComponent();

    await waitFor(() => {
      expect(screen.getByText(/No policies found/)).toBeInTheDocument();
    });
  });

  it('should display policy cards when policies exist', async () => {
    const mockPolicies = [
      {
        id: 'policy-1',
        name: 'School Time Limit',
        type: 'time-limit',
        restrictions: { maxUsageMinutes: 60 },
        deviceIds: ['device-1'],
        createdAt: '2025-11-03T10:00:00Z',
        updatedAt: '2025-11-03T10:00:00Z',
      },
    ];

    mockFetch(mockPolicies);
    renderComponent();

    await waitFor(() => {
      expect(screen.getByText('School Time Limit')).toBeInTheDocument();
    });
  });

  it('should display edit and delete buttons for each policy', async () => {
    const mockPolicies = [
      {
        id: 'policy-1',
        name: 'Test Policy',
        type: 'time-limit',
        restrictions: { maxUsageMinutes: 60 },
        deviceIds: ['device-1'],
        createdAt: '2025-11-03T10:00:00Z',
        updatedAt: '2025-11-03T10:00:00Z',
      },
    ];

    mockFetch(mockPolicies);
    renderComponent();

    await waitFor(() => {
      expect(screen.getByText('Edit')).toBeInTheDocument();
      expect(screen.getByText('Delete')).toBeInTheDocument();
    });
  });
});
