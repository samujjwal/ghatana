import React from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, fireEvent, waitFor, act } from '@testing-library/react-native';

jest.mock('@/screens/DashboardScreen', () => {
  const React = require('react');
  const { View, Text } = require('react-native');
  return () => React.createElement(View, null, React.createElement(Text, null, 'Total Devices'));
});
jest.mock('@/screens/DevicesScreen', () => {
  const React = require('react');
  const { View, Text } = require('react-native');
  return () => React.createElement(View, null, React.createElement(Text, null, 'Child Tablet'));
});
jest.mock('@/screens/PoliciesScreen', () => {
  const React = require('react');
  const { View, Text } = require('react-native');
  return () => React.createElement(View, null, React.createElement(Text, null, 'Homework Mode'));
});
jest.mock('@/screens/AlertsScreen', () => {
  const React = require('react');
  const { View, Text } = require('react-native');
  return () => React.createElement(View, null, React.createElement(Text, null, 'Unread'));
});

// Use require to avoid TypeScript resolution issues
const AppNavigator = require('@/navigation/AppNavigator').default;

import { apiMock } from '../mocks/api.mock';
import { deviceListFixture } from '../fixtures/device.fixtures';
import { alertsFixture } from '../fixtures/alert.fixtures';
import { policiesFixture } from '../fixtures/policy.fixtures';

const renderNavigator = async () => {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });

  let utils: any;
  try {
    utils = render(
      <QueryClientProvider client={queryClient}>
        <AppNavigator />
      </QueryClientProvider>
    );
  } catch (err: any) {
    // eslint-disable-next-line no-console
    console.error('[TEST-DEBUG] render threw', err && err.stack ? err.stack : err);
    throw err;
  }

  return { queryClient, ...utils };
};

describe('Parent mobile user journey', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    apiMock.getDevices.mockResolvedValue(deviceListFixture);
    apiMock.getAlerts.mockResolvedValue(alertsFixture);
    apiMock.getPolicies.mockResolvedValue(policiesFixture);
    apiMock.getUsageData.mockResolvedValue([]);
  });

  it('navigates across dashboard, devices, policies, and alerts', async () => {
    await renderNavigator();

    await waitFor(() => expect(screen.getAllByText('Total Devices').length).toBeGreaterThan(0));

    // Get tab buttons by their text labels
    const dashboardTab = screen.getByText('Dashboard');
    const devicesTab = screen.getByText('Devices');
    const policiesTab = screen.getByText('Policies');
    const alertsTab = screen.getByText('Alerts');

    // Click Devices tab
    fireEvent.press(devicesTab);
    await waitFor(() => expect(screen.getAllByText('Child Tablet').length).toBeGreaterThan(0));

    // Click Policies tab
    fireEvent.press(policiesTab);
    await waitFor(() => expect(screen.getAllByText('Homework Mode').length).toBeGreaterThan(0));

    // Click Alerts tab
    fireEvent.press(alertsTab);
    await waitFor(() => expect(screen.getAllByText(/Unread/).length).toBeGreaterThan(0));
  });
});
