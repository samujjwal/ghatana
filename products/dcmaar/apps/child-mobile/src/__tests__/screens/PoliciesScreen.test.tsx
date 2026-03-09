import React from 'react';
import { RefreshControl } from 'react-native';
import { render, screen, act } from '@testing-library/react-native';

import PoliciesScreen from '@/screens/PoliciesScreen';
import { policiesFixture } from '../fixtures/policy.fixtures';
import { deviceListFixture } from '../fixtures/device.fixtures';

jest.mock('@/hooks/useApi', () => ({
  usePolicies: jest.fn(),
  useDevices: jest.fn(),
}));

const mockedUsePolicies = jest.requireMock('@/hooks/useApi').usePolicies as jest.Mock;
const mockedUseDevices = jest.requireMock('@/hooks/useApi').useDevices as jest.Mock;

describe('PoliciesScreen', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockedUseDevices.mockReturnValue({ data: deviceListFixture, isLoading: false });
  });

  it('shows loading state', () => {
    mockedUsePolicies.mockReturnValue({ data: undefined, isLoading: true, refetch: jest.fn() });

    render(<PoliciesScreen />);

    expect(screen.getByText('Loading policies...')).toBeTruthy();
  });

  it('renders empty state when no policies', () => {
    mockedUsePolicies.mockReturnValue({ data: [], isLoading: false, refetch: jest.fn() });

    render(<PoliciesScreen />);

    expect(screen.getByText('No Policies')).toBeTruthy();
    expect(screen.getByText('Create a policy to manage device usage')).toBeTruthy();
  });

  it('displays policies with device association', () => {
    mockedUsePolicies.mockReturnValue({ data: policiesFixture, isLoading: false, refetch: jest.fn() });

    render(<PoliciesScreen />);

    expect(screen.getByText('Homework Mode')).toBeTruthy();
    expect(screen.getAllByText(/Screen Time Limit/).length).toBeGreaterThan(0);
  });

  it('refresh control triggers refetch', async () => {
    const refetch = jest.fn().mockResolvedValue(undefined);
    mockedUsePolicies.mockReturnValue({ data: policiesFixture, isLoading: false, refetch });

    const { UNSAFE_getByType } = render(<PoliciesScreen />);

    await act(async () => {
      await UNSAFE_getByType(RefreshControl).props.onRefresh();
    });

    expect(refetch).toHaveBeenCalled();
  });
});
