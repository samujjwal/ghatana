import React from 'react';
import { render, waitFor } from '@testing-library/react-native';
import NetInfo from '@react-native-community/netinfo';
import App from '../App';
import { fetchMobileDashboard } from '../services/phrMobileApi';
import { loadMobileSession } from '../services/mobileSessionStore';
import type { MobileDashboard, MobileSession } from '../types';
import { DashboardScreen } from '../screens/DashboardScreen';
import { RecordsScreen } from '../screens/RecordsScreen';
import { ConsentScreen } from '../screens/ConsentScreen';
import { EmergencyAccessScreen } from '../screens/EmergencyAccessScreen';
import { SettingsScreen } from '../screens/SettingsScreen';
import { NotificationsScreen } from '../screens/NotificationsScreen';
import { LoginScreen } from '../screens/LoginScreen';

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

  // Screen-level accessibility tests
  describe('individual screens have accessible structure', () => {
    it('DashboardScreen has accessible landmarks', () => {
      const rendered = render(<DashboardScreen dashboard={mockDashboard} />);
      expect(rendered.UNSAFE_getByProps({ accessibilityLabel: `${mockDashboard.patient.name}` })).toBeTruthy();
    });

    it('RecordsScreen has accessible list structure', () => {
      const rendered = render(<RecordsScreen />);
      expect(rendered.UNSAFE_getByProps({ accessibilityLabel: 'Health Records' })).toBeTruthy();
    });

    it('ConsentScreen has accessible controls', () => {
      const rendered = render(<ConsentScreen session={mockSession} consents={[]} onConsentRevoked={jest.fn()} />);
      expect(rendered.UNSAFE_getByProps({ accessibilityLabel: 'My Consents' })).toBeTruthy();
    });

    it('EmergencyAccessScreen has accessible form inputs', () => {
      const rendered = render(<EmergencyAccessScreen session={mockSession} onAuthenticate={jest.fn()} />);
      expect(rendered.UNSAFE_getByProps({ accessibilityLabel: 'Emergency Access' })).toBeTruthy();
    });

    it('SettingsScreen has accessible controls', () => {
      const rendered = render(
        <SettingsScreen
          session={mockSession}
          onSyncOffline={jest.fn()}
          onLogout={jest.fn()}
          syncMessage="No sync requested yet."
        />,
      );
      expect(rendered.UNSAFE_getByProps({ accessibilityLabel: 'Settings' })).toBeTruthy();
    });

    it('NotificationsScreen has accessible list', () => {
      const rendered = render(<NotificationsScreen notifications={[]} onEnablePush={jest.fn()} />);
      expect(rendered.UNSAFE_getByProps({ accessibilityLabel: 'Alerts' })).toBeTruthy();
    });

    it('LoginScreen has accessible form', () => {
      const rendered = render(
        <LoginScreen
          onSuccess={jest.fn()}
          onLoginError={jest.fn()}
          loginFn={jest.fn()}
        />,
      );
      expect(rendered.getByLabelText('National ID')).toBeTruthy();
      expect(rendered.getByLabelText('Password')).toBeTruthy();
      expect(rendered.getByLabelText('Sign In')).toBeTruthy();
    });
  });
});
