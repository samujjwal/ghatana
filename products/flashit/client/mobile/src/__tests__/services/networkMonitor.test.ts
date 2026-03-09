/**
 * @jest-environment node
 */

import NetInfo, { NetInfoState, NetInfoStateType } from '@react-native-community/netinfo';
import { networkMonitor } from '../../services/networkMonitor';

// Mock NetInfo
jest.mock('@react-native-community/netinfo', () => ({
  addEventListener: jest.fn(),
  fetch: jest.fn(),
}));

describe('NetworkMonitor', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  describe('initialization', () => {
    it('should initialize with disconnected state', () => {
      const state = networkMonitor.getState();

      expect(state.isConnected).toBe(false);
      expect(state.isInternetReachable).toBe(false);
      expect(state.type).toBe('unknown');
    });
  });

  describe('start', () => {
    it('should add NetInfo event listener', () => {
      networkMonitor.start();

      expect(NetInfo.addEventListener).toHaveBeenCalled();
    });

    it('should fetch initial network state', async () => {
      const mockState: NetInfoState = {
        type: 'wifi' as NetInfoStateType,
        isConnected: true,
        isInternetReachable: true,
        details: null,
        isWifiEnabled: true,
      };

      (NetInfo.fetch as jest.Mock).mockResolvedValue(mockState);

      await networkMonitor.start();

      expect(NetInfo.fetch).toHaveBeenCalled();
    });

    it('should update state when network changes', () => {
      let stateChangeHandler: ((state: NetInfoState) => void) | null = null;

      (NetInfo.addEventListener as jest.Mock).mockImplementation((handler) => {
        stateChangeHandler = handler;
        return jest.fn(); // unsubscribe function
      });

      networkMonitor.start();

      const newState: NetInfoState = {
        type: 'wifi' as NetInfoStateType,
        isConnected: true,
        isInternetReachable: true,
        details: null,
        isWifiEnabled: true,
      };

      stateChangeHandler?.(newState);

      const currentState = networkMonitor.getState();
      expect(currentState.isConnected).toBe(true);
      expect(currentState.type).toBe('wifi');
    });
  });

  describe('stop', () => {
    it('should remove NetInfo event listener', () => {
      const unsubscribeMock = jest.fn();
      (NetInfo.addEventListener as jest.Mock).mockReturnValue(unsubscribeMock);

      networkMonitor.start();
      networkMonitor.stop();

      expect(unsubscribeMock).toHaveBeenCalled();
    });
  });

  describe('subscribe', () => {
    it('should call subscriber when network state changes', () => {
      let stateChangeHandler: ((state: NetInfoState) => void) | null = null;

      (NetInfo.addEventListener as jest.Mock).mockImplementation((handler) => {
        stateChangeHandler = handler;
        return jest.fn();
      });

      networkMonitor.start();

      const subscriber = jest.fn();
      networkMonitor.subscribe(subscriber);

      const newState: NetInfoState = {
        type: 'cellular' as NetInfoStateType,
        isConnected: true,
        isInternetReachable: true,
        details: {
          cellularGeneration: '4g',
          carrier: 'Test Carrier',
          isConnectionExpensive: true,
        },
        isWifiEnabled: false,
      };

      stateChangeHandler?.(newState);

      expect(subscriber).toHaveBeenCalledWith({
        isConnected: true,
        isInternetReachable: true,
        type: 'cellular',
      });
    });

    it('should return unsubscribe function', () => {
      const subscriber = jest.fn();
      const unsubscribe = networkMonitor.subscribe(subscriber);

      expect(typeof unsubscribe).toBe('function');

      unsubscribe();

      // Subscriber should not be called after unsubscribe
      let stateChangeHandler: ((state: NetInfoState) => void) | null = null;
      (NetInfo.addEventListener as jest.Mock).mockImplementation((handler) => {
        stateChangeHandler = handler;
        return jest.fn();
      });

      networkMonitor.start();

      const newState: NetInfoState = {
        type: 'wifi' as NetInfoStateType,
        isConnected: true,
        isInternetReachable: true,
        details: null,
        isWifiEnabled: true,
      };

      stateChangeHandler?.(newState);

      expect(subscriber).not.toHaveBeenCalled();
    });
  });

  describe('isOnline', () => {
    it('should return true when connected with internet', () => {
      let stateChangeHandler: ((state: NetInfoState) => void) | null = null;

      (NetInfo.addEventListener as jest.Mock).mockImplementation((handler) => {
        stateChangeHandler = handler;
        return jest.fn();
      });

      networkMonitor.start();

      const newState: NetInfoState = {
        type: 'wifi' as NetInfoStateType,
        isConnected: true,
        isInternetReachable: true,
        details: null,
        isWifiEnabled: true,
      };

      stateChangeHandler?.(newState);

      expect(networkMonitor.isOnline()).toBe(true);
    });

    it('should return false when not connected', () => {
      let stateChangeHandler: ((state: NetInfoState) => void) | null = null;

      (NetInfo.addEventListener as jest.Mock).mockImplementation((handler) => {
        stateChangeHandler = handler;
        return jest.fn();
      });

      networkMonitor.start();

      const newState: NetInfoState = {
        type: 'none' as NetInfoStateType,
        isConnected: false,
        isInternetReachable: false,
        details: null,
        isWifiEnabled: false,
      };

      stateChangeHandler?.(newState);

      expect(networkMonitor.isOnline()).toBe(false);
    });
  });

  describe('isWiFi', () => {
    it('should return true when connected via WiFi', () => {
      let stateChangeHandler: ((state: NetInfoState) => void) | null = null;

      (NetInfo.addEventListener as jest.Mock).mockImplementation((handler) => {
        stateChangeHandler = handler;
        return jest.fn();
      });

      networkMonitor.start();

      const newState: NetInfoState = {
        type: 'wifi' as NetInfoStateType,
        isConnected: true,
        isInternetReachable: true,
        details: null,
        isWifiEnabled: true,
      };

      stateChangeHandler?.(newState);

      expect(networkMonitor.isWiFi()).toBe(true);
    });

    it('should return false when not on WiFi', () => {
      let stateChangeHandler: ((state: NetInfoState) => void) | null = null;

      (NetInfo.addEventListener as jest.Mock).mockImplementation((handler) => {
        stateChangeHandler = handler;
        return jest.fn();
      });

      networkMonitor.start();

      const newState: NetInfoState = {
        type: 'cellular' as NetInfoStateType,
        isConnected: true,
        isInternetReachable: true,
        details: {
          cellularGeneration: '4g',
          carrier: 'Test Carrier',
          isConnectionExpensive: true,
        },
        isWifiEnabled: false,
      };

      stateChangeHandler?.(newState);

      expect(networkMonitor.isWiFi()).toBe(false);
    });
  });

  describe('isCellular', () => {
    it('should return true when connected via cellular', () => {
      let stateChangeHandler: ((state: NetInfoState) => void) | null = null;

      (NetInfo.addEventListener as jest.Mock).mockImplementation((handler) => {
        stateChangeHandler = handler;
        return jest.fn();
      });

      networkMonitor.start();

      const newState: NetInfoState = {
        type: 'cellular' as NetInfoStateType,
        isConnected: true,
        isInternetReachable: true,
        details: {
          cellularGeneration: '4g',
          carrier: 'Test Carrier',
          isConnectionExpensive: true,
        },
        isWifiEnabled: false,
      };

      stateChangeHandler?.(newState);

      expect(networkMonitor.isCellular()).toBe(true);
    });

    it('should return false when not on cellular', () => {
      let stateChangeHandler: ((state: NetInfoState) => void) | null = null;

      (NetInfo.addEventListener as jest.Mock).mockImplementation((handler) => {
        stateChangeHandler = handler;
        return jest.fn();
      });

      networkMonitor.start();

      const newState: NetInfoState = {
        type: 'wifi' as NetInfoStateType,
        isConnected: true,
        isInternetReachable: true,
        details: null,
        isWifiEnabled: true,
      };

      stateChangeHandler?.(newState);

      expect(networkMonitor.isCellular()).toBe(false);
    });
  });

  describe('getState', () => {
    it('should return current network state', () => {
      let stateChangeHandler: ((state: NetInfoState) => void) | null = null;

      (NetInfo.addEventListener as jest.Mock).mockImplementation((handler) => {
        stateChangeHandler = handler;
        return jest.fn();
      });

      networkMonitor.start();

      const newState: NetInfoState = {
        type: 'wifi' as NetInfoStateType,
        isConnected: true,
        isInternetReachable: true,
        details: null,
        isWifiEnabled: true,
      };

      stateChangeHandler?.(newState);

      const currentState = networkMonitor.getState();

      expect(currentState).toEqual({
        isConnected: true,
        isInternetReachable: true,
        type: 'wifi',
      });
    });
  });
});
