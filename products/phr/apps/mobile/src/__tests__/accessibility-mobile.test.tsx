import React from 'react';
import { render, waitFor } from '@testing-library/react-native';
import NetInfo from '@react-native-community/netinfo';
import App from '../App';
import { fetchMobileDashboard } from '../services/phrMobileApi';
import { loadMobileSession } from '../services/mobileSessionStore';
import type { MobileDashboard, MobileSession } from '../types';

jest.mock('@react-native-community/netinfo', () => ({
  addEventListener: jest.fn(() => jest.fn()),
}));

jest.mock('../services/biometricAuth', () => ({
  authenticateBiometric: jest.fn(),
}));

jest.mock('../services/phrMobileApi', () => ({
  fetchMobileDashboard: jest.fn(),
  loginMobile: jest.fn(),
  logoutMobile: jest.fn(),
  syncOfflineDashboard: jest.fn(),
}));

jest.mock('../services/mobileSessionStore', () => ({
  loadMobileSession: jest.fn(),
  saveMobileSession: jest.fn(),
  clearMobileSession: jest.fn(),
}));

jest.mock('../services/pushNotifications', () => ({
  registerForPushNotificationsAsync: jest.fn(),
}));

const mockSession: MobileSession = {
  principalId: 'test-principal',
  tenantId: 'test-tenant',
  role: 'patient',
  name: 'Test User',
  expiresAt: '2026-12-31T23:59:59Z',
};

const mockDashboard: MobileDashboard = {
  patient: { id: '1', name: 'Test', age: 30, bloodType: 'O+', district: 'Kathmandu' },
  records: [],
  consents: [],
  notifications: [],
};

function renderedText(rendered: { toJSON: () => unknown }): string {
  return JSON.stringify(rendered.toJSON());
}

function authenticateApp(): void {
  (loadMobileSession as jest.MockedFunction<typeof loadMobileSession>).mockResolvedValue(mockSession);
  (fetchMobileDashboard as jest.MockedFunction<typeof fetchMobileDashboard>).mockResolvedValue(mockDashboard);
}

describe('Mobile accessibility smoke', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    (NetInfo.addEventListener as jest.MockedFunction<typeof NetInfo.addEventListener>).mockImplementation(() => jest.fn());
  });

  it('uses tabbar and tab roles for authenticated navigation', async () => {
    authenticateApp();
    const rendered = render(<App />);

    await waitFor(() => expect(rendered.getByLabelText('Home')).toBeTruthy());

    expect(rendered.UNSAFE_getByProps({ accessibilityLabel: 'Home' }).props.accessibilityHint).toBe(
      'Show the mobile health dashboard.',
    );
    expect(rendered.UNSAFE_getByProps({ accessibilityLabel: 'Records' }).props.accessibilityHint).toBe(
      'Open the offline health record list.',
    );
    expect(rendered.UNSAFE_getByProps({ accessibilityLabel: 'Emergency' }).props.accessibilityHint).toBe(
      'Request audited emergency access.',
    );
  });

  it('marks the active tab through accessibility state', async () => {
    authenticateApp();
    const rendered = render(<App />);

    await waitFor(() => expect(rendered.getByLabelText('Home')).toBeTruthy());

    expect(rendered.UNSAFE_getByProps({ accessibilityLabel: 'Home' }).props.accessibilityState).toEqual({
      selected: true,
    });
    expect(rendered.UNSAFE_getByProps({ accessibilityLabel: 'Records' }).props.accessibilityState).toEqual({
      selected: false,
    });
  });

  it('announces offline connectivity as an alert', async () => {
    authenticateApp();
    (NetInfo.addEventListener as jest.MockedFunction<typeof NetInfo.addEventListener>).mockImplementation((callback) => {
      callback({ isConnected: false } as Parameters<typeof callback>[0]);
      return jest.fn();
    });

    const rendered = render(<App />);

    await waitFor(() => expect(rendered.UNSAFE_getByProps({ accessibilityRole: 'alert' })).toBeTruthy());
    expect(renderedText(rendered)).toContain('You are offline.');
  });

  it('shows a readable restoring-session state while session restore is pending', () => {
    (loadMobileSession as jest.MockedFunction<typeof loadMobileSession>).mockImplementation(
      () => new Promise<MobileSession | null>(() => undefined),
    );

    const rendered = render(<App />);

    expect(renderedText(rendered)).toContain('Restoring session');
  });

  it('exposes login input labels and the submit button to assistive tech', async () => {
    (loadMobileSession as jest.MockedFunction<typeof loadMobileSession>).mockResolvedValue(null);
    const rendered = render(<App />);

    await waitFor(() => {
      expect(rendered.getByLabelText('National ID')).toBeTruthy();
      expect(rendered.getByLabelText('Password')).toBeTruthy();
      expect(rendered.getByLabelText('Sign In')).toBeTruthy();
    });
  });

  it('exposes dashboard load errors as alerts with an accessible retry action', async () => {
    (loadMobileSession as jest.MockedFunction<typeof loadMobileSession>).mockResolvedValue(mockSession);
    (fetchMobileDashboard as jest.MockedFunction<typeof fetchMobileDashboard>).mockRejectedValue(
      new Error('Dashboard failed'),
    );

    const rendered = render(<App />);

    await waitFor(() => expect(rendered.UNSAFE_getByProps({ accessibilityRole: 'alert' })).toBeTruthy());
    expect(rendered.getByLabelText('Retry')).toBeTruthy();
    expect(renderedText(rendered)).toContain('Dashboard failed');
  });
});
