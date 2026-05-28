import React from 'react';
import { render, waitFor } from '@testing-library/react-native';
import NetInfo from '@react-native-community/netinfo';
import App from '../App';
import { setLocale } from '../i18n/phrMobileI18n';
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
  patient: { id: '1', name: 'Test User', age: 30, bloodType: 'O+', district: 'Kathmandu' },
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

describe('Pseudo-locale tests', () => {
  beforeEach(async () => {
    jest.clearAllMocks();
    await setLocale('en-XA');
    (NetInfo.addEventListener as jest.MockedFunction<typeof NetInfo.addEventListener>).mockImplementation(() => jest.fn());
  });

  afterEach(async () => {
    await setLocale('en');
  });

  it('renders authenticated tab labels with pseudo-localized strings', async () => {
    authenticateApp();
    const rendered = render(<App />);

    await waitFor(() => {
      const text = renderedText(rendered);
      expect(text).toContain('[Hoomee]');
      expect(text).toContain('[Reecoords]');
      expect(text).toContain('[Coonseents]');
    });
  });

  it('does not render raw English tab labels in pseudo-locale mode', async () => {
    authenticateApp();
    const rendered = render(<App />);

    await waitFor(() => expect(renderedText(rendered)).toContain('[Hoomee]'));
    const text = renderedText(rendered);

    expect(text).not.toContain('"Home"');
    expect(text).not.toContain('"Records"');
    expect(text).not.toContain('"Consents"');
    expect(text).not.toContain('"Emergency"');
    expect(text).not.toContain('"Settings"');
  });

  it('renders restoring-session text with pseudo-localization', () => {
    (loadMobileSession as jest.MockedFunction<typeof loadMobileSession>).mockImplementation(
      () => new Promise<MobileSession | null>(() => undefined),
    );

    const rendered = render(<App />);

    expect(renderedText(rendered)).toContain('[Reestooriing seessiioon');
  });

  it('renders the offline banner with pseudo-localization', async () => {
    authenticateApp();
    (NetInfo.addEventListener as jest.MockedFunction<typeof NetInfo.addEventListener>).mockImplementation((callback) => {
      callback({ isConnected: false } as Parameters<typeof callback>[0]);
      return jest.fn();
    });

    const rendered = render(<App />);

    await waitFor(() => expect(renderedText(rendered)).toContain('[Yoouu aaree ooffliinee.'));
  });
});
