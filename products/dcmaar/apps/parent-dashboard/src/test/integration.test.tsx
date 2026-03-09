import { describe, it, expect, beforeEach, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import { Dashboard } from '../pages/Dashboard';
import { renderWithDashboardProviders } from './utils/renderWithProviders';

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

// Mock Jotai stores
vi.mock('../stores/authStore', () => ({
  userAtom: { key: 'userAtom' },
  isAuthenticatedAtom: { key: 'isAuthenticatedAtom' },
}));

vi.mock('../stores/eventsStore', () => ({
  wsConnectedAtom: { key: 'wsConnectedAtom' },
}));

// Mock Jotai hooks with proper returns
vi.mock('jotai', async (importOriginal) => {
  const actual = (await importOriginal()) as typeof import('jotai');
  return {
    ...actual,
    useAtomValue: vi.fn((atom: any) => {
      // Return appropriate mock data based on atom key
      const atomKey = atom.key || atom.toString?.();
      if (atomKey?.includes?.('userAtom')) return { email: 'test@example.com' };
      if (atomKey?.includes?.('isAuthenticatedAtom')) return true;
      if (atomKey?.includes?.('wsConnectedAtom')) return true;
      return null;
    }),
    useAtom: vi.fn(() => [null, vi.fn()]),
  };
});

// Mock fetch
globalThis.fetch = vi.fn();

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
    renderWithDashboardProviders(<Dashboard />);

    await waitFor(() => {
      expect(screen.getByText('Guardian Dashboard')).toBeInTheDocument();
      expect(screen.getByText('Real-Time Usage Monitoring')).toBeInTheDocument();
      expect(screen.getByText('Block Event Notifications')).toBeInTheDocument();
      expect(screen.getByText('Policy Management')).toBeInTheDocument();
      expect(screen.getByText('Device Management')).toBeInTheDocument();
      expect(screen.getByText('Analytics & Insights')).toBeInTheDocument();
    });
  });

  it('should show WebSocket connected status', async () => {
    renderWithDashboardProviders(<Dashboard />);

    await waitFor(() => {
      expect(screen.getByText('Connected')).toBeInTheDocument();
    });
  });

  it('should display logout button', async () => {
    renderWithDashboardProviders(<Dashboard />);

    await waitFor(() => {
      expect(screen.getByText('Logout')).toBeInTheDocument();
    });
  });

  it('should display week 3 progress checklist', async () => {
    renderWithDashboardProviders(<Dashboard />);

    await waitFor(() => {
      expect(screen.getByText(/Day 1: Authentication/)).toBeInTheDocument();
      expect(screen.getByText(/Day 2: Real-time Usage/)).toBeInTheDocument();
      expect(screen.getByText(/Day 3: Block Event/)).toBeInTheDocument();
      expect(screen.getByText(/Day 4: Policy Management/)).toBeInTheDocument();
      expect(screen.getByText(/Day 5: Device Management/)).toBeInTheDocument();
      expect(screen.getByText(/Day 6: Analytics/)).toBeInTheDocument();
    });
  });
});
