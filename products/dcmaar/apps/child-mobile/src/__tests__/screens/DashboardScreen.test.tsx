import React from 'react';
import { RefreshControl } from 'react-native';
import { render, screen, act } from '@testing-library/react-native';

import DashboardScreen from '@/screens/DashboardScreen';
import { deviceListFixture } from '../fixtures/device.fixtures';
import { alertsFixture } from '../fixtures/alert.fixtures';

jest.mock('@/hooks/useApi', () => ({
  useDevices: jest.fn(),
  useAlerts: jest.fn(),
}));

const mockedUseDevices = jest.requireMock('@/hooks/useApi').useDevices as jest.Mock;
const mockedUseAlerts = jest.requireMock('@/hooks/useApi').useAlerts as jest.Mock;

describe('DashboardScreen', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('renders stats cards with device and alert counts', () => {
    mockedUseDevices.mockReturnValue({
      data: deviceListFixture,
      isLoading: false,
      refetch: jest.fn(),
    });
    mockedUseAlerts.mockReturnValue({
      data: alertsFixture,
      isLoading: false,
      refetch: jest.fn(),
    });

    render(<DashboardScreen />);

    expect(screen.getByText('Total Devices')).toBeTruthy();
    expect(screen.getByText(String(deviceListFixture.length))).toBeTruthy();
    expect(screen.getByText('Active Alerts')).toBeTruthy();
  });

  it('shows loading state when queries loading', () => {
    mockedUseDevices.mockReturnValue({ data: undefined, isLoading: true, refetch: jest.fn() });
    mockedUseAlerts.mockReturnValue({ data: undefined, isLoading: true, refetch: jest.fn() });

    render(<DashboardScreen />);

    expect(screen.getByText('Loading...')).toBeTruthy();
  });

  it('triggers refresh to refetch devices and alerts', async () => {
    const refetchDevices = jest.fn().mockResolvedValue(undefined);
    const refetchAlerts = jest.fn().mockResolvedValue(undefined);
    mockedUseDevices.mockReturnValue({
      data: deviceListFixture,
      isLoading: false,
      refetch: refetchDevices,
    });
    mockedUseAlerts.mockReturnValue({
      data: alertsFixture,
      isLoading: false,
      refetch: refetchAlerts,
    });

    const { UNSAFE_getByType } = render(<DashboardScreen />);

    await act(async () => {
      await UNSAFE_getByType(RefreshControl).props.onRefresh();
    });

    expect(refetchDevices).toHaveBeenCalled();
    expect(refetchAlerts).toHaveBeenCalled();
  });
});
