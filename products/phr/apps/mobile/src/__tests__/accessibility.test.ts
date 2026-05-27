/**
 * Mobile accessibility tests for PHR mobile app.
 *
 * Verifies that tabs, buttons, and forms are accessible with proper
 * accessibility labels, roles, and states.
 */

import React from 'react';
import { render, fireEvent } from '@testing-library/react-native';
import App from '../App';

// Mock biometric auth
jest.mock('../services/biometricAuth', () => ({
  authenticateBiometric: jest.fn(),
}));

// Mock mobile API
jest.mock('../services/phrMobileApi', () => ({
  fetchMobileDashboard: jest.fn(),
  loginMobile: jest.fn(),
  logoutMobile: jest.fn(),
  syncOfflineDashboard: jest.fn(),
}));

// Mock session store
jest.mock('../services/mobileSessionStore', () => ({
  loadMobileSession: jest.fn(),
  saveMobileSession: jest.fn(),
  clearMobileSession: jest.fn(),
}));

// Mock push notifications
jest.mock('../services/pushNotifications', () => ({
  registerForPushNotificationsAsync: jest.fn(),
}));

// Mock SecureStore
jest.mock('expo-secure-store', () => ({
  getItemAsync: jest.fn(),
  setItemAsync: jest.fn(),
  deleteItemAsync: jest.fn(),
}));

// Mock AsyncStorage
jest.mock('@react-native-async-storage/async-storage', () => ({
  getItem: jest.fn(),
  setItem: jest.fn(),
  removeItem: jest.fn(),
  getAllKeys: jest.fn(),
}));

// Mock NetInfo
jest.mock('@react-native-community/netinfo', () => ({
  addEventListener: jest.fn(() => ({ remove: jest.fn() })),
  fetch: jest.fn(),
}));

describe('Mobile accessibility', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    const { loadMobileSession } = require('../services/mobileSessionStore');
    loadMobileSession.mockResolvedValue(null);
  });

  it('bottom tab bar has accessible roles and labels', async () => {
    const { getByRole, getByLabelText } = render(<App />);

    // Wait for app to render
    await new Promise(resolve => setTimeout(resolve, 100));

    // Check for tab bar navigation role
    const tabBar = getByRole('tabbar');
    expect(tabBar).toBeTruthy();

    // Check for accessible tab buttons
    const dashboardTab = getByLabelText(/dashboard/i);
    const recordsTab = getByLabelText(/records/i);
    const consentsTab = getByLabelText(/consents/i);
    const alertsTab = getByLabelText(/alerts/i);
    const emergencyTab = getByLabelText(/emergency/i);

    expect(dashboardTab).toBeTruthy();
    expect(recordsTab).toBeTruthy();
    expect(consentsTab).toBeTruthy();
    expect(alertsTab).toBeTruthy();
    expect(emergencyTab).toBeTruthy();
  });

  it('emergency button has accessible label and role', async () => {
    const { getByRole, getByLabelText } = render(<App />);

    await new Promise(resolve => setTimeout(resolve, 100));

    const emergencyButton = getByRole('button');
    expect(emergencyButton).toBeTruthy();

    // Emergency button should have a descriptive label
    const emergencyLabel = getByLabelText(/emergency/i);
    expect(emergencyLabel).toBeTruthy();
  });

  it('form inputs have accessible labels', async () => {
    const { getByLabelText } = render(<App />);

    await new Promise(resolve => setTimeout(resolve, 100));

    // Check for login form inputs with labels
    const nationalIdInput = getByLabelText(/national id/i);
    const passwordInput = getByLabelText(/password/i);

    expect(nationalIdInput).toBeTruthy();
    expect(passwordInput).toBeTruthy();
  });

  it('buttons have accessible roles and labels', async () => {
    const { getByRole, getByLabelText } = render(<App />);

    await new Promise(resolve => setTimeout(resolve, 100));

    // Check for sign-in button
    const signInButton = getByRole('button', { name: /sign in/i });
    expect(signInButton).toBeTruthy();

    // Check for demo account link
    const demoLink = getByRole('link', { name: /demo/i });
    expect(demoLink).toBeTruthy();
  });

  it('tab buttons indicate selected state', async () => {
    const { getByRole } = render(<App />);

    await new Promise(resolve => setTimeout(resolve, 100));

    const tabBar = getByRole('tabbar');
    const tabs = tabBar.findAllByRole('button');

    // At least one tab should be selected
    const selectedTab = tabs.find(tab => 
      tab.props.accessibilityState?.selected === true
    );
    expect(selectedTab).toBeTruthy();
  });

  it('emergency tab has high priority accessibility hint', async () => {
    const { getByLabelText } = render(<App />);

    await new Promise(resolve => setTimeout(resolve, 100));

    const emergencyTab = getByLabelText(/emergency/i);
    expect(emergencyTab).toBeTruthy();

    // Emergency tab should have accessibility hint
    expect(emergencyTab.props.accessibilityHint).toBeTruthy();
  });
});
