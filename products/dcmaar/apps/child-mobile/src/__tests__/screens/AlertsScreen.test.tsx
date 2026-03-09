import React from 'react';
import { RefreshControl } from 'react-native';
import { render, screen, fireEvent, act } from '@testing-library/react-native';

import AlertsScreen from '@/screens/AlertsScreen';
import { alertsFixture, buildAlert } from '../fixtures/alert.fixtures';

jest.mock('@/hooks/useApi', () => ({
  useAlerts: jest.fn(),
  useMarkAlertRead: jest.fn(),
}));

jest.mock('@/utils/format', () => ({
  formatRelativeTime: jest.fn(() => '2 minutes ago'),
}));

const mockedUseAlerts = jest.requireMock('@/hooks/useApi').useAlerts as jest.Mock;
const mockedUseMarkAlertRead = jest.requireMock('@/hooks/useApi').useMarkAlertRead as jest.Mock;

describe('AlertsScreen', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('shows loading state', () => {
    mockedUseAlerts.mockReturnValue({ data: undefined, isLoading: true, refetch: jest.fn() });
    mockedUseMarkAlertRead.mockReturnValue({ mutate: jest.fn() });

    render(<AlertsScreen />);

    expect(screen.getByText('Loading alerts...')).toBeTruthy();
  });

  it('renders empty state when no alerts', () => {
    mockedUseAlerts.mockReturnValue({ data: [], isLoading: false, refetch: jest.fn() });
    mockedUseMarkAlertRead.mockReturnValue({ mutate: jest.fn() });

    render(<AlertsScreen />);

    expect(screen.getByText('All Clear!')).toBeTruthy();
    expect(screen.getByText('You have no alerts at this time')).toBeTruthy();
  });

  it('displays unread and read alerts', () => {
    const alerts = [alertsFixture[0], buildAlert({ id: 'read-alert', read: true })];
    mockedUseAlerts.mockReturnValue({ data: alerts, isLoading: false, refetch: jest.fn() });
    mockedUseMarkAlertRead.mockReturnValue({ mutate: jest.fn() });

    render(<AlertsScreen />);

    expect(screen.getAllByText(/Unread/).length).toBeGreaterThan(0);
    expect(screen.getAllByText(/Read/).length).toBeGreaterThan(0);
    expect(screen.getAllByText(alerts[0].message).length).toBeGreaterThan(0);
  });

  it('marks alert as read when button pressed', () => {
    const mutate = jest.fn();
    mockedUseAlerts.mockReturnValue({ data: alertsFixture, isLoading: false, refetch: jest.fn() });
    mockedUseMarkAlertRead.mockReturnValue({ mutate });

    render(<AlertsScreen />);

    const markButtons = screen.getAllByText('Mark as Read');
    expect(markButtons.length).toBeGreaterThan(0);
    fireEvent.press(markButtons[0]);

    expect(mutate).toHaveBeenCalledWith(alertsFixture[0].id);
  });

  it('triggers refresh control', async () => {
    const refetch = jest.fn().mockResolvedValue(undefined);
    mockedUseAlerts.mockReturnValue({ data: alertsFixture, isLoading: false, refetch });
    mockedUseMarkAlertRead.mockReturnValue({ mutate: jest.fn() });

    const { UNSAFE_getByType } = render(<AlertsScreen />);

    await act(async () => {
      await UNSAFE_getByType(RefreshControl).props.onRefresh();
    });

    expect(refetch).toHaveBeenCalled();
  });
});
