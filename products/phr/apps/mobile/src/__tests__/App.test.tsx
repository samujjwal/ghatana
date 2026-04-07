import React from 'react';
import { fireEvent, render, screen } from '@testing-library/react-native';
import App from '../App';

jest.mock('../services/phrMobileApi', () => ({
  fetchMobileDashboard: jest.fn(async () => require('../data/mockData').mobileDashboard),
  syncOfflineDashboard: jest.fn(async () => 'Offline cache refreshed'),
}));

jest.mock('../services/pushNotifications', () => ({
  registerForPushNotificationsAsync: jest.fn(async () => 'ExponentPushToken[test-token]'),
}));

jest.mock('../services/biometricAuth', () => ({
  authenticateBiometric: jest.fn(async () => true),
}));

describe('PHR mobile app', () => {
  it('moves from login to mobile dashboard', async () => {
    render(<App />);

    fireEvent.press(await screen.findByText('Continue with demo account'));

    expect(await screen.findByText('PHR Nepal mobile')).toBeTruthy();
    expect(screen.getByText('Aarati Shrestha')).toBeTruthy();
  });
});