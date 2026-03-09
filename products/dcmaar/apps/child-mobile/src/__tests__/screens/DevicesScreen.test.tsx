import React from 'react';
import { RefreshControl } from 'react-native';
import { render, screen, act } from '@testing-library/react-native';

import DevicesScreen from '@/screens/DevicesScreen';
import { deviceListFixture } from '../fixtures/device.fixtures';

jest.mock('@/hooks/useApi', () => ({
  useDevices: jest.fn(),
}));

jest.mock('@/utils/format', () => ({
  formatRelativeTime: jest.fn(() => '5 minutes ago'),
}));

const mockedUseDevices = jest.requireMock('@/hooks/useApi').useDevices as jest.Mock;

describe('DevicesScreen', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('renders loading view while devices fetching', () => {
    mockedUseDevices.mockReturnValue({ data: undefined, isLoading: true, refetch: jest.fn() });

    render(<DevicesScreen />);

    expect(screen.getByText('Loading devices...')).toBeTruthy();
  });

  it('renders empty state when no devices', () => {
    mockedUseDevices.mockReturnValue({ data: [], isLoading: false, refetch: jest.fn() });

    render(<DevicesScreen />);

    expect(screen.getByText('No Devices')).toBeTruthy();
    expect(screen.getByText('Add a device to get started')).toBeTruthy();
  });

  it('lists devices with status and metadata', () => {
    mockedUseDevices.mockReturnValue({
      data: deviceListFixture,
      isLoading: false,
      refetch: jest.fn(),
    });

    render(<DevicesScreen />);

    expect(screen.getByText('Child Tablet')).toBeTruthy();
    expect(screen.getAllByText("Jamie").length).toBeGreaterThan(0);
    expect(screen.getAllByText(/5 minutes ago/).length).toBeGreaterThan(0);
  });

  it('invokes refetch on pull to refresh', async () => {
    const refetch = jest.fn().mockResolvedValue(undefined);
    mockedUseDevices.mockReturnValue({ data: deviceListFixture, isLoading: false, refetch });

    const { UNSAFE_getByType } = render(<DevicesScreen />);

    await act(async () => {
      await UNSAFE_getByType(RefreshControl).props.onRefresh();
    });

    expect(refetch).toHaveBeenCalled();
  });
});
