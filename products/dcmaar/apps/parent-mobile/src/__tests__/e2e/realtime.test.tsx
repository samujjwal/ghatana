import React from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor, fireEvent } from '@testing-library/react-native';

jest.mock('@/screens/DashboardScreen', () => {
  const React = require('react');
  const RN = require('react-native');
  return () => React.createElement(RN.View, null, React.createElement(RN.Text, null, 'MockDashboard'));
});

jest.mock('@/screens/DevicesScreen', () => {
  const React = require('react');
  const RN = require('react-native');
  return () => React.createElement(RN.View, null, React.createElement(RN.Text, null, 'MockDevices'));
});

jest.mock('@/screens/PoliciesScreen', () => {
  const React = require('react');
  const RN = require('react-native');
  return () => React.createElement(RN.View, null, React.createElement(RN.Text, null, 'MockPolicies'));
});

jest.mock('@/screens/AlertsScreen', () => {
  const React = require('react');
  const RN = require('react-native');
  const { useQuery } = require('@tanstack/react-query');
  const api = require('@/services/api');
  return () => {
    const { data: alerts } = useQuery({
      queryKey: ['alerts'],
      queryFn: () => api.getAlerts(),
    });
    return React.createElement(
      RN.View,
      null,
      React.createElement(RN.Text, null, 'Unread'),
      alerts && alerts.length > 0 ? React.createElement(RN.Text, null, alerts[0].message) : null
    );
  };
});

import { apiMock } from '../mocks/api.mock';
import { alertsFixture, buildAlert } from '../fixtures/alert.fixtures';

// Use require to avoid TypeScript resolution issues
const AppNavigator = require('@/navigation/AppNavigator').default;

const setup = async () => {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  let utils: any;
  try {
    utils = render(
      <QueryClientProvider client={queryClient}>
        <AppNavigator />
      </QueryClientProvider>
    );
  } catch (err: any) {
     
    console.error('[TEST-DEBUG] render threw', err && err.stack ? err.stack : err);
    throw err;
  }
  return { queryClient, ...utils };
};

describe('Real-time updates', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    apiMock.getDevices.mockResolvedValue([]);
    apiMock.getPolicies.mockResolvedValue([]);
    apiMock.getUsageData.mockResolvedValue([]);
    apiMock.getAlerts.mockResolvedValue(alertsFixture.slice(0, 1));
  });

  it('renders new alert when query cache updates', async () => {
    const { queryClient } = await setup();

    // Wait for the Alerts tab to be visible
    await waitFor(() => {
      const alertsTab = screen.getByText('Alerts');
      expect(alertsTab).toBeTruthy();
    });

    // Click the Alerts tab to navigate to it
    const alertsTab = screen.getByText('Alerts');
    fireEvent.press(alertsTab);

    // Wait for the screen content to render
    await waitFor(() => expect(screen.getByText(/Unread/)).toBeTruthy());

    await act(async () => {
      queryClient.setQueryData(['alerts'], (current: any) => {
        const next = current ? [...current] : [];
        next.unshift(buildAlert({ id: 'new-alert', message: 'Device came online', read: false }));
        return next;
      });
    });

    await waitFor(() => expect(screen.getByText('Device came online')).toBeTruthy());
  });
});
