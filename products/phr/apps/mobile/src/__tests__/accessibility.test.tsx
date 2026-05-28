import React from 'react';
import { render, waitFor } from '@testing-library/react-native';
import App from '../App';
import { fetchMobileDashboard } from '../services/phrMobileApi';
import { loadMobileSession } from '../services/mobileSessionStore';
import type { MobileDashboard, MobileSession } from '../types';

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

jest.mock('@react-native-community/netinfo', () => ({
  addEventListener: jest.fn(() => ({ remove: jest.fn() })),
  fetch: jest.fn(),
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
  records: [
    {
      id: 'record-1',
      title: 'Blood Pressure',
      summary: 'Normal reading',
      fhirPreview: '{"resourceType":"Observation"}',
    },
  ],
  consents: [],
  notifications: [],
};

function renderedText(rendered: { toJSON: () => unknown }): string {
  return JSON.stringify(rendered.toJSON());
}

function mockAuthenticatedApp(): void {
  (loadMobileSession as jest.MockedFunction<typeof loadMobileSession>).mockResolvedValue(mockSession);
  (fetchMobileDashboard as jest.MockedFunction<typeof fetchMobileDashboard>).mockResolvedValue(mockDashboard);
}

describe('Mobile accessibility', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('bottom tab bar exposes tab semantics and selected state', async () => {
    mockAuthenticatedApp();
    const rendered = render(<App />);

    await waitFor(() => expect(rendered.getByLabelText('Home')).toBeTruthy());

    expect(rendered.getByLabelText('Records')).toBeTruthy();
    expect(rendered.getByLabelText('Consents')).toBeTruthy();
    expect(rendered.getByLabelText('Alerts')).toBeTruthy();
    expect(rendered.getByLabelText('Emergency')).toBeTruthy();
    expect(rendered.getByLabelText('Settings')).toBeTruthy();
    expect(rendered.UNSAFE_getByProps({ accessibilityLabel: 'Home' }).props.accessibilityState).toEqual({
      selected: true,
    });
  });

  it('emergency tab has an accessible label and tab role', async () => {
    mockAuthenticatedApp();
    const rendered = render(<App />);

    await waitFor(() => expect(rendered.getByLabelText('Emergency')).toBeTruthy());
    const emergencyTab = rendered.UNSAFE_getByProps({ accessibilityLabel: 'Emergency' });

    expect(emergencyTab.props.accessibilityState).toEqual({ selected: false });
    expect(emergencyTab.props.accessibilityHint).toBe('Request audited emergency access.');
  });

  it('login form inputs and sign-in button are screen-reader labelled', async () => {
    (loadMobileSession as jest.MockedFunction<typeof loadMobileSession>).mockResolvedValue(null);
    const rendered = render(<App />);

    await waitFor(() => {
      expect(rendered.getByLabelText('National ID')).toBeTruthy();
      expect(rendered.getByLabelText('Password')).toBeTruthy();
      expect(rendered.getByLabelText('Sign In')).toBeTruthy();
    });
  });

  it('authenticated tab content changes when a tab is activated', async () => {
    mockAuthenticatedApp();
    const rendered = render(<App />);
    await waitFor(() => expect(rendered.getByLabelText('Records')).toBeTruthy());
    const recordsTab = rendered.UNSAFE_getByProps({ accessibilityLabel: 'Records' });

    const onPress = recordsTab.props.onPress ?? recordsTab.props.onClick;
    if (typeof onPress !== 'function') {
      throw new Error('Expected records tab to expose a press handler.');
    }
    onPress();

    await waitFor(() => expect(renderedText(rendered)).toContain('Blood Pressure'));
  });
});
