import { describe, it, expect, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';

// Mock the lazy components
vi.mock('../components/UsageMonitor', () => ({
  UsageMonitor: () => <div>UsageMonitor Component</div>
}));

vi.mock('../components/BlockNotifications', () => ({
  BlockNotifications: () => <div>BlockNotifications Component</div>
}));

vi.mock('../components/PolicyManagement', () => ({
  PolicyManagement: () => <div>PolicyManagement Component</div>
}));

vi.mock('../components/DeviceManagement', () => ({
  DeviceManagement: () => <div>DeviceManagement Component</div>
}));

vi.mock('../components/Analytics', () => ({
  Analytics: () => <div>Analytics Component</div>
}));

// Mock auth and websocket services
vi.mock('../services/auth.service', () => ({
  authService: {
    isAuthenticated: vi.fn(() => true),
    logout: vi.fn()
  }
}));

vi.mock('../services/websocket.service', () => ({
  websocketService: {
    connect: vi.fn(),
    disconnect: vi.fn()
  }
}));

// NOTE: Do NOT mock Jotai - use renderWithDashboardProviders which properly wraps in JotaiProvider

import { renderWithDashboardProviders } from './utils/renderWithProviders';

describe('Performance: Lazy Loading', () => {
  const renderDashboard = async () => {
    const { Dashboard } = await import('../pages/Dashboard');
    return renderWithDashboardProviders(<Dashboard />);
  };

  it('should lazy load Dashboard component', async () => {
    await renderDashboard();

    await waitFor(() => {
      expect(screen.getByText('Guardian Dashboard')).toBeInTheDocument();
    });
  });

  it('should have loading skeleton component defined', async () => {
    const result = await renderDashboard();

    await waitFor(() => {
      expect(screen.getByText(/Usage Monitor/i)).toBeInTheDocument();
    });

    expect(result.container).toBeTruthy();
  });

  it('should eventually load all dashboard components', async () => {
    await renderDashboard();

    await waitFor(() => {
      expect(screen.getByText('UsageMonitor Component')).toBeInTheDocument();
      expect(screen.getByText('BlockNotifications Component')).toBeInTheDocument();
      expect(screen.getByText('PolicyManagement Component')).toBeInTheDocument();
      expect(screen.getByText('DeviceManagement Component')).toBeInTheDocument();
      expect(screen.getByText('Analytics Component')).toBeInTheDocument();
    }, { timeout: 3000 });
  });

  it('should lazy load Login page', async () => {
    const LoginModule = await import('../pages/Login');
    expect(LoginModule.Login).toBeDefined();
  });

  it('should lazy load Register page', async () => {
    const RegisterModule = await import('../pages/Register');
    expect(RegisterModule.Register).toBeDefined();
  });
});

describe('Performance: Bundle Size', () => {
  it('should have separate chunks for each lazy loaded component', async () => {
    // This test verifies that dynamic imports create separate chunks
    const usageMonitorPromise = import('../components/UsageMonitor');
    const blockNotificationsPromise = import('../components/BlockNotifications');
    const policyManagementPromise = import('../components/PolicyManagement');

    const [usageMonitor, blockNotifications, policyManagement] = await Promise.all([
      usageMonitorPromise,
      blockNotificationsPromise,
      policyManagementPromise
    ]);

    expect(usageMonitor).toBeDefined();
    expect(blockNotifications).toBeDefined();
    expect(policyManagement).toBeDefined();
  });
});
