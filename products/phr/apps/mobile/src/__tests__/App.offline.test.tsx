/**
 * NetInfo offline behavior tests for PHR mobile app.
 *
 * Verifies that the app correctly handles network state changes and displays
 * the offline banner when connectivity is lost.
 */

import React from 'react';
import { act, render, waitFor } from '@testing-library/react-native';
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
  const offlineBannerText = 'You are offline. Some features may be unavailable.';

  function renderedText(rendered: { toJSON: () => unknown }): string {
    return JSON.stringify(rendered.toJSON());
  }

  function emitNetworkState(state: { isConnected: boolean | null }): void {
    if (mockNetInfoCallback) {
      act(() => {
        mockNetInfoCallback?.(state);
      });
    }
  }

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
    emitNetworkState({ isConnected: true });

    const rendered = render(<App />);

    // Wait for session restore to complete
    await waitFor(() => {
      expect(renderedText(rendered)).not.toContain(offlineBannerText);
    });

    // Simulate network disconnection
    emitNetworkState({ isConnected: false });

    // Offline banner should appear
    await waitFor(() => {
      expect(renderedText(rendered)).toContain(offlineBannerText);
    });
  });

  it('hides offline banner when network is reconnected', async () => {
    // Start with disconnected state
    emitNetworkState({ isConnected: false });

    const rendered = render(<App />);
    emitNetworkState({ isConnected: false });

    // Wait for session restore to complete
    await waitFor(() => {
      expect(renderedText(rendered)).toContain(offlineBannerText);
    });

    // Simulate network reconnection
    emitNetworkState({ isConnected: true });

    // Offline banner should disappear
    await waitFor(() => {
      expect(renderedText(rendered)).not.toContain(offlineBannerText);
    });
  });

  it('defaults to connected state when NetInfo returns null', async () => {
    // Simulate null/unknown state
    emitNetworkState({ isConnected: null });

    const rendered = render(<App />);

    // Wait for session restore to complete
    await waitFor(() => {
      expect(renderedText(rendered)).not.toContain(offlineBannerText);
    });
  });

  it('registers NetInfo listener on mount', () => {
    render(<App />);

    expect(NetInfo.addEventListener).toHaveBeenCalledTimes(1);
  });

  it('removes NetInfo listener on unmount', () => {
    const { unmount } = render(<App />);

    const mockRemove = jest.fn();
    const subscription = (NetInfo.addEventListener as jest.Mock).mock.results[0]?.value as
      | { remove: () => void }
      | undefined;
    if (subscription) {
      subscription.remove = mockRemove;
    }

    unmount();

    expect(mockRemove).toHaveBeenCalledTimes(1);
  });

  it('handles rapid network state changes gracefully', async () => {
    const rendered = render(<App />);

    // Rapid state changes
    emitNetworkState({ isConnected: true });
    emitNetworkState({ isConnected: false });
    emitNetworkState({ isConnected: true });
    emitNetworkState({ isConnected: false });

    // Final state should be offline
    await waitFor(() => {
      expect(renderedText(rendered)).toContain(offlineBannerText);
    });
  });
});
