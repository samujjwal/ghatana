import NetInfo, { NetInfoState } from '@react-native-community/netinfo';
import { atom } from 'jotai';

export interface NetworkState {
  isConnected: boolean;
  isInternetReachable: boolean;
  type: string | null;
  details: any;
}

/**
 * Network Monitor Service
 * 
 * @doc.type service
 * @doc.purpose Monitor network connectivity state
 * @doc.layer product
 * @doc.pattern Service
 */
class NetworkMonitorService {
  private listeners: Set<(state: NetworkState) => void> = new Set();
  private currentState: NetworkState = {
    isConnected: false,
    isInternetReachable: false,
    type: null,
    details: null,
  };

  /**
   * Initialize network monitoring
   */
  init(): () => void {
    const unsubscribe = NetInfo.addEventListener((state: NetInfoState) => {
      this.currentState = {
        isConnected: state.isConnected ?? false,
        isInternetReachable: state.isInternetReachable ?? false,
        type: state.type,
        details: state.details,
      };

      this.notifyListeners();
    });

    // Get initial state
    NetInfo.fetch().then((state) => {
      this.currentState = {
        isConnected: state.isConnected ?? false,
        isInternetReachable: state.isInternetReachable ?? false,
        type: state.type,
        details: state.details,
      };
      this.notifyListeners();
    });

    return unsubscribe;
  }

  /**
   * Get current network state
   */
  getState(): NetworkState {
    return this.currentState;
  }

  /**
   * Check if online
   */
  isOnline(): boolean {
    return this.currentState.isConnected && (this.currentState.isInternetReachable ?? true);
  }

  /**
   * Check if on WiFi
   */
  isWiFi(): boolean {
    return this.currentState.type === 'wifi';
  }

  /**
   * Check if on cellular
   */
  isCellular(): boolean {
    return this.currentState.type === 'cellular';
  }

  /**
   * Get network type for compression/upload decisions
   */
  getNetworkType(): 'wifi' | 'cellular' | 'unknown' {
    if (this.currentState.type === 'wifi') return 'wifi';
    if (this.currentState.type === 'cellular') return 'cellular';
    return 'unknown';
  }

  /**
   * Get cellular generation (2g, 3g, 4g, 5g)
   */
  getCellularGeneration(): '2g' | '3g' | '4g' | '5g' | null {
    if (this.currentState.type !== 'cellular' || !this.currentState.details) {
      return null;
    }
    const gen = this.currentState.details.cellularGeneration?.toLowerCase();
    if (gen === '2g' || gen === '3g' || gen === '4g' || gen === '5g') {
      return gen;
    }
    return '4g'; // Default assumption
  }

  /**
   * Get human-readable network label for UI
   */
  getNetworkLabel(): string {
    if (!this.currentState.isConnected) return 'Offline';
    if (this.currentState.type === 'wifi') return 'WiFi';
    if (this.currentState.type === 'cellular') {
      const gen = this.getCellularGeneration();
      return gen ? gen.toUpperCase() : 'Cellular';
    }
    return 'Unknown';
  }

  /**
   * Subscribe to network changes
   */
  subscribe(listener: (state: NetworkState) => void): () => void {
    this.listeners.add(listener);
    // Immediately call with current state
    listener(this.currentState);

    return () => {
      this.listeners.delete(listener);
    };
  }

  /**
   * Notify all listeners
   */
  private notifyListeners(): void {
    this.listeners.forEach((listener) => listener(this.currentState));
  }
}

export const networkMonitor = new NetworkMonitorService();

// Jotai atom for network state
export const networkStateAtom = atom<NetworkState>({
  isConnected: false,
  isInternetReachable: false,
  type: null,
  details: null,
});
