/**
 * NetInfo offline behavior tests for PHR mobile app.
 *
 * Verifies that the app correctly handles network state changes and displays
 * the offline banner when connectivity is lost.
 */

import React from 'react';
import { render, waitFor } from '@testing-library/react-native';
import NetInfo from '@react-native-community/netinfo';
import App from '../App';

// Mock NetInfo
jest.mock('@react-native-community/netinfo', () => ({
  addEventListener: jest.fn(),
  fetch: jest.fn(),
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

describe('App offline behavior', () => {
  let mockNetInfoCallback: ((state: { isConnected: boolean | null }) => void) | null = null;

  beforeEach(() => {
    jest.clearAllMocks();
    
    // Mock NetInfo to capture the callback
    (NetInfo.addEventListener as jest.Mock).mockImplementation((callback) => {
      mockNetInfoCallback = callback;
      return { remove: jest.fn() };
    });

    // Default session restored state
    const { loadMobileSession } = require('../services/mobileSessionStore');
    loadMobileSession.mockResolvedValue(null);
  });

  it('displays offline banner when network is disconnected', async () => {
    // Simulate initial connected state
    if (mockNetInfoCallback) {
      mockNetInfoCallback({ isConnected: true });
    }

    const { queryByText } = render(<App />);

    // Wait for session restore to complete
    await waitFor(() => {
      expect(queryByText('You are offline. Some features may be unavailable.')).toBeNull();
    });

    // Simulate network disconnection
    if (mockNetInfoCallback) {
      mockNetInfoCallback({ isConnected: false });
    }

    // Offline banner should appear
    await waitFor(() => {
      expect(queryByText('You are offline. Some features may be unavailable.')).toBeTruthy();
    });
  });

  it('hides offline banner when network is reconnected', async () => {
    // Start with disconnected state
    if (mockNetInfoCallback) {
      mockNetInfoCallback({ isConnected: false });
    }

    const { queryByText } = render(<App />);

    // Wait for session restore to complete
    await waitFor(() => {
      expect(queryByText('You are offline. Some features may be unavailable.')).toBeTruthy();
    });

    // Simulate network reconnection
    if (mockNetInfoCallback) {
      mockNetInfoCallback({ isConnected: true });
    }

    // Offline banner should disappear
    await waitFor(() => {
      expect(queryByText('You are offline. Some features may be unavailable.')).toBeNull();
    });
  });

  it('defaults to connected state when NetInfo returns null', async () => {
    // Simulate null/unknown state
    if (mockNetInfoCallback) {
      mockNetInfoCallback({ isConnected: null });
    }

    const { queryByText } = render(<App />);

    // Wait for session restore to complete
    await waitFor(() => {
      expect(queryByText('You are offline. Some features may be unavailable.')).toBeNull();
    });
  });

  it('registers NetInfo listener on mount', () => {
    render(<App />);

    expect(NetInfo.addEventListener).toHaveBeenCalledTimes(1);
  });

  it('removes NetInfo listener on unmount', () => {
    const { unmount } = render(<App />);

    const mockRemove = jest.fn();
    (NetInfo.addEventListener as jest.Mock).mockReturnValue({ remove: mockRemove });

    unmount();

    expect(mockRemove).toHaveBeenCalledTimes(1);
  });

  it('handles rapid network state changes gracefully', async () => {
    const { queryByText } = render(<App />);

    // Rapid state changes
    if (mockNetInfoCallback) {
      mockNetInfoCallback({ isConnected: true });
      mockNetInfoCallback({ isConnected: false });
      mockNetInfoCallback({ isConnected: true });
      mockNetInfoCallback({ isConnected: false });
    }

    // Final state should be offline
    await waitFor(() => {
      expect(queryByText('You are offline. Some features may be unavailable.')).toBeTruthy();
    });
  });
});
