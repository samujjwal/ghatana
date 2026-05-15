import { describe, it, expect, beforeEach, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Dashboard } from '../pages/Dashboard';
import { renderWithDashboardProviders } from './utils/renderWithProviders';
import { isAuthenticatedAtom, userAtom } from '../stores/authStore';
import { wsConnectedAtom } from '../stores/eventsStore';

// Mock lazy loaded components to speed up tests
vi.mock('../components/UsageMonitor', () => ({
  UsageMonitor: () => <div>Real-Time Usage Monitoring</div>,
}));

vi.mock('../components/BlockNotifications', () => ({
  BlockNotifications: () => <div>Block Event Notifications</div>,
}));

vi.mock('../components/PolicyManagement', () => ({
  PolicyManagement: () => <div>Policy Management</div>,
}));

vi.mock('../components/DeviceManagement', () => ({
  DeviceManagement: () => <div>Device Management</div>,
}));

vi.mock('../components/Analytics', () => ({
  Analytics: () => <div>Analytics & Insights</div>,
}));

// Mock WebSocket service
vi.mock('../services/websocket.service', () => ({
  websocketService: {
    connect: vi.fn(),
    disconnect: vi.fn(),
    on: vi.fn(() => () => {}),
  },
}));

// Mock auth service
vi.mock('../services/auth.service', () => ({
  authService: {
    isAuthenticated: vi.fn(() => true),
    logout: vi.fn(),
  },
}));

// Mock fetch
globalThis.fetch = vi.fn();

function renderDashboard(): void {
  renderWithDashboardProviders(<Dashboard />, {
    initializeStore: (store) => {
      store.set(isAuthenticatedAtom, true);
      store.set(userAtom, {
        id: 'user-123',
        email: 'test@example.com',
        role: 'parent',
      });
      store.set(wsConnectedAtom, true);
    },
  });
}

describe('Dashboard Integration Tests', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.setItem('token', 'test-token');
    
    // Mock successful fetch responses
    vi.mocked(globalThis.fetch).mockResolvedValue({
      ok: true,
      json: async () => [],
    } as Response);
  });

  it('should render dashboard components', async () => {
    renderDashboard();

    await waitFor(() => {
      expect(screen.getAllByText('Guardian')[0]).toBeInTheDocument();
      expect(screen.getByText('Real-Time Usage Monitoring')).toBeInTheDocument();
      expect(screen.getByText('Block Event Notifications')).toBeInTheDocument();
      expect(screen.getByText('Policy Management')).toBeInTheDocument();
      expect(screen.getByText('Device Management')).toBeInTheDocument();
      expect(screen.getByText('Analytics & Insights')).toBeInTheDocument();
    });
  });

  it('should show WebSocket connected status', async () => {
    renderDashboard();

    await waitFor(() => {
      expect(screen.getByText('Connected')).toBeInTheDocument();
    });
  });

  it('should display logout button', async () => {
    const user = userEvent.setup();
    renderDashboard();

    await user.click(screen.getByRole('button', { name: /open user menu/i }));
    expect(screen.getByRole('menuitem', { name: /logout/i })).toBeInTheDocument();
  });

  it('should display component migration progress checklist', async () => {
    renderDashboard();

    await waitFor(() => {
      expect(screen.getByText(/Batch 1: DynamicForm/)).toBeInTheDocument();
      expect(screen.getByText(/Batch 2: ActivityFeed/)).toBeInTheDocument();
      expect(screen.getByText(/Batch 3: StatsDashboard/)).toBeInTheDocument();
      expect(screen.getByText(/Batch 4: DashboardLayout/)).toBeInTheDocument();
      expect(screen.getByText(/Migration Complete: 100%/)).toBeInTheDocument();
    });
  });
});
