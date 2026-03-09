import AsyncStorage from '@react-native-async-storage/async-storage';
import { networkMonitor } from './networkMonitor';
import { uploadProgressService } from './uploadProgressService';

/**
 * Bandwidth Estimation Service
 * 
 * @doc.type service
 * @doc.purpose Estimate network bandwidth and adapt upload behavior
 * @doc.layer product
 * @doc.pattern Service
 * 
 * Features:
 * - Speed test on app start
 * - Adaptive quality selection based on measured speed
 * - WiFi-only enforcement from settings
 * - Network change handling (pause/resume uploads)
 * - Historical speed tracking for better estimates
 */

const STORAGE_KEYS = {
  LAST_SPEED_TEST: '@bandwidth_lastSpeedTest',
  WIFI_SPEED_HISTORY: '@bandwidth_wifiHistory',
  CELLULAR_SPEED_HISTORY: '@bandwidth_cellularHistory',
  WIFI_ONLY_UPLOAD: '@settings_wifiOnlyUpload',
};

// Speed test configuration
const SPEED_TEST_URL = 'https://httpbin.org/bytes/102400'; // 100KB test file
const SPEED_TEST_SIZE_BYTES = 102400;
const SPEED_TEST_TIMEOUT_MS = 30000;
const SPEED_TEST_MIN_INTERVAL_MS = 60 * 60 * 1000; // 1 hour between tests
const MAX_SPEED_HISTORY = 10;

// Bandwidth thresholds (bytes per second)
const BANDWIDTH_THRESHOLDS = {
  excellent: 5_000_000, // 5 MB/s (40 Mbps)
  good: 1_250_000,      // 1.25 MB/s (10 Mbps)
  fair: 500_000,        // 500 KB/s (4 Mbps)
  poor: 125_000,        // 125 KB/s (1 Mbps)
  veryPoor: 50_000,     // 50 KB/s (400 Kbps)
};

export type BandwidthQuality = 'excellent' | 'good' | 'fair' | 'poor' | 'veryPoor' | 'unknown';
export type CompressionQuality = 'high' | 'medium' | 'low';

export interface SpeedTestResult {
  speedBps: number;
  speedMbps: number;
  quality: BandwidthQuality;
  timestamp: number;
  networkType: 'wifi' | 'cellular' | 'unknown';
  testDurationMs: number;
}

export interface BandwidthState {
  currentSpeedBps: number;
  estimatedSpeedBps: number;
  quality: BandwidthQuality;
  lastTestTimestamp: number | null;
  isTestRunning: boolean;
  recommendedQuality: CompressionQuality;
}

/**
 * Bandwidth Estimation Service
 */
class BandwidthEstimationService {
  private state: BandwidthState = {
    currentSpeedBps: 0,
    estimatedSpeedBps: 0,
    quality: 'unknown',
    lastTestTimestamp: null,
    isTestRunning: false,
    recommendedQuality: 'medium',
  };

  private listeners: Set<(state: BandwidthState) => void> = new Set();
  private networkUnsubscribe: (() => void) | null = null;
  private pausedUploads: Set<string> = new Set();

  /**
   * Initialize bandwidth monitoring
   */
  async init(): Promise<void> {
    // Load last test results
    await this.loadStoredState();

    // Subscribe to network changes
    this.networkUnsubscribe = networkMonitor.subscribe(async (networkState) => {
      await this.handleNetworkChange(networkState);
    });

    // Run initial speed test if needed
    await this.runSpeedTestIfNeeded();
  }

  /**
   * Cleanup resources
   */
  cleanup(): void {
    if (this.networkUnsubscribe) {
      this.networkUnsubscribe();
      this.networkUnsubscribe = null;
    }
  }

  /**
   * Run speed test if needed (based on time and network change)
   */
  async runSpeedTestIfNeeded(): Promise<SpeedTestResult | null> {
    const now = Date.now();
    const lastTest = this.state.lastTestTimestamp;

    // Skip if tested recently
    if (lastTest && (now - lastTest) < SPEED_TEST_MIN_INTERVAL_MS) {
      console.log('[Bandwidth] Skipping speed test - tested recently');
      return null;
    }

    // Skip if offline
    if (!networkMonitor.isOnline()) {
      console.log('[Bandwidth] Skipping speed test - offline');
      return null;
    }

    return this.runSpeedTest();
  }

  /**
   * Run a bandwidth speed test
   */
  async runSpeedTest(): Promise<SpeedTestResult> {
    if (this.state.isTestRunning) {
      console.log('[Bandwidth] Speed test already running');
      return this.createResult(this.state.currentSpeedBps);
    }

    this.state.isTestRunning = true;
    this.notifyListeners();

    const startTime = Date.now();
    const networkType = networkMonitor.getNetworkType();

    try {
      const controller = new AbortController();
      const timeoutId = setTimeout(() => controller.abort(), SPEED_TEST_TIMEOUT_MS);

      const response = await fetch(SPEED_TEST_URL, {
        method: 'GET',
        signal: controller.signal,
        cache: 'no-store',
      });

      clearTimeout(timeoutId);

      if (!response.ok) {
        throw new Error(`Speed test failed: ${response.status}`);
      }

      // Read the response to measure download time
      const blob = await response.blob();
      const endTime = Date.now();
      const durationMs = endTime - startTime;
      const durationSeconds = durationMs / 1000;

      // Calculate speed (use actual blob size if available)
      const bytesDownloaded = blob.size || SPEED_TEST_SIZE_BYTES;
      const speedBps = bytesDownloaded / durationSeconds;

      // Store result
      await this.storeSpeedResult(speedBps, networkType);

      // Update state
      const result = this.createResult(speedBps, durationMs, networkType);
      this.state.currentSpeedBps = speedBps;
      this.state.estimatedSpeedBps = await this.getAverageSpeed(networkType);
      this.state.quality = result.quality;
      this.state.lastTestTimestamp = Date.now();
      this.state.recommendedQuality = this.getRecommendedQuality(speedBps);
      this.state.isTestRunning = false;

      // Persist last test timestamp
      await AsyncStorage.setItem(
        STORAGE_KEYS.LAST_SPEED_TEST,
        JSON.stringify({
          timestamp: this.state.lastTestTimestamp,
          speedBps,
          networkType,
        })
      );

      this.notifyListeners();
      console.log(`[Bandwidth] Speed test complete: ${result.speedMbps.toFixed(2)} Mbps (${result.quality})`);

      return result;
    } catch (error) {
      console.error('[Bandwidth] Speed test failed:', error);
      this.state.isTestRunning = false;
      this.notifyListeners();

      // Return estimated speed based on network type
      const estimatedSpeed = await this.getEstimatedSpeed();
      return this.createResult(estimatedSpeed);
    }
  }

  /**
   * Get recommended compression quality based on bandwidth
   */
  getRecommendedQuality(speedBps?: number): CompressionQuality {
    const speed = speedBps ?? this.state.estimatedSpeedBps;

    if (speed >= BANDWIDTH_THRESHOLDS.good) {
      return 'high';
    } else if (speed >= BANDWIDTH_THRESHOLDS.fair) {
      return 'medium';
    } else {
      return 'low';
    }
  }

  /**
   * Check if upload should proceed based on settings and network
   */
  async shouldAllowUpload(): Promise<{
    allowed: boolean;
    reason?: string;
  }> {
    // Check if online
    if (!networkMonitor.isOnline()) {
      return { allowed: false, reason: 'No internet connection' };
    }

    // Check WiFi-only setting
    const wifiOnlyRaw = await AsyncStorage.getItem(STORAGE_KEYS.WIFI_ONLY_UPLOAD);
    const wifiOnly = wifiOnlyRaw !== null ? JSON.parse(wifiOnlyRaw) : false;

    if (wifiOnly && !networkMonitor.isWiFi()) {
      return {
        allowed: false,
        reason: 'WiFi-only mode enabled. Connect to WiFi to upload.',
      };
    }

    // Check for very poor connection
    if (this.state.quality === 'veryPoor') {
      return {
        allowed: true,
        reason: 'Very slow connection detected. Uploads may take longer.',
      };
    }

    return { allowed: true };
  }

  /**
   * Handle network state changes
   */
  private async handleNetworkChange(networkState: { isConnected: boolean; type: string | null }): Promise<void> {
    const wasOnline = this.state.currentSpeedBps > 0;
    const isOnline = networkState.isConnected;

    console.log(`[Bandwidth] Network changed: ${networkState.type}, connected: ${isOnline}`);

    // Handle going offline
    if (wasOnline && !isOnline) {
      console.log('[Bandwidth] Gone offline - pausing all uploads');
      await this.pauseAllUploads('Network disconnected');
      this.state.quality = 'unknown';
      this.state.currentSpeedBps = 0;
      this.notifyListeners();
      return;
    }

    // Handle coming online
    if (!wasOnline && isOnline) {
      console.log('[Bandwidth] Come online - running speed test');
      await this.runSpeedTest();
      await this.resumePausedUploads();
      return;
    }

    // Handle network type change (WiFi <-> Cellular)
    if (isOnline) {
      const wifiOnlyRaw = await AsyncStorage.getItem(STORAGE_KEYS.WIFI_ONLY_UPLOAD);
      const wifiOnly = wifiOnlyRaw !== null ? JSON.parse(wifiOnlyRaw) : false;

      if (wifiOnly && networkState.type === 'cellular') {
        console.log('[Bandwidth] Switched to cellular with WiFi-only mode - pausing uploads');
        await this.pauseAllUploads('Switched to cellular network (WiFi-only mode)');
      } else if (wifiOnly && networkState.type === 'wifi') {
        console.log('[Bandwidth] Switched to WiFi - resuming uploads');
        await this.resumePausedUploads();
      }

      // Update estimated speed for new network type
      const networkType = networkMonitor.getNetworkType();
      this.state.estimatedSpeedBps = await this.getAverageSpeed(networkType);
      this.state.recommendedQuality = this.getRecommendedQuality();
      this.notifyListeners();
    }
  }

  /**
   * Pause all active uploads
   */
  private async pauseAllUploads(reason: string): Promise<void> {
    const activeUploads = uploadProgressService.getAllProgress();

    for (const upload of activeUploads) {
      if (upload.status === 'uploading' || upload.status === 'queued') {
        uploadProgressService.pauseUpload(upload.id);
        this.pausedUploads.add(upload.id);
      }
    }

    console.log(`[Bandwidth] Paused ${this.pausedUploads.size} uploads: ${reason}`);
  }

  /**
   * Resume previously paused uploads
   */
  private async resumePausedUploads(): Promise<void> {
    for (const uploadId of this.pausedUploads) {
      uploadProgressService.resumeUpload(uploadId);
    }

    console.log(`[Bandwidth] Resumed ${this.pausedUploads.size} uploads`);
    this.pausedUploads.clear();
  }

  /**
   * Get current bandwidth state
   */
  getState(): BandwidthState {
    return { ...this.state };
  }

  /**
   * Subscribe to state changes
   */
  subscribe(listener: (state: BandwidthState) => void): () => void {
    this.listeners.add(listener);
    listener(this.state);
    return () => {
      this.listeners.delete(listener);
    };
  }

  /**
   * Store speed result for averaging
   */
  private async storeSpeedResult(speedBps: number, networkType: 'wifi' | 'cellular' | 'unknown'): Promise<void> {
    const key = networkType === 'wifi'
      ? STORAGE_KEYS.WIFI_SPEED_HISTORY
      : STORAGE_KEYS.CELLULAR_SPEED_HISTORY;

    try {
      const historyRaw = await AsyncStorage.getItem(key);
      const history: number[] = historyRaw ? JSON.parse(historyRaw) : [];

      history.push(speedBps);

      // Keep only last N results
      while (history.length > MAX_SPEED_HISTORY) {
        history.shift();
      }

      await AsyncStorage.setItem(key, JSON.stringify(history));
    } catch (error) {
      console.error('[Bandwidth] Failed to store speed result:', error);
    }
  }

  /**
   * Get average speed for network type
   */
  private async getAverageSpeed(networkType: 'wifi' | 'cellular' | 'unknown'): Promise<number> {
    const key = networkType === 'wifi'
      ? STORAGE_KEYS.WIFI_SPEED_HISTORY
      : STORAGE_KEYS.CELLULAR_SPEED_HISTORY;

    try {
      const historyRaw = await AsyncStorage.getItem(key);
      const history: number[] = historyRaw ? JSON.parse(historyRaw) : [];

      if (history.length === 0) {
        return this.getDefaultSpeed(networkType);
      }

      const sum = history.reduce((a, b) => a + b, 0);
      return sum / history.length;
    } catch (error) {
      return this.getDefaultSpeed(networkType);
    }
  }

  /**
   * Get default speed estimate based on network type
   */
  private getDefaultSpeed(networkType: 'wifi' | 'cellular' | 'unknown'): number {
    switch (networkType) {
      case 'wifi':
        return 6_250_000; // 50 Mbps
      case 'cellular':
        const gen = networkMonitor.getCellularGeneration();
        switch (gen) {
          case '5g': return 12_500_000; // 100 Mbps
          case '4g': return 1_250_000;  // 10 Mbps
          case '3g': return 125_000;    // 1 Mbps
          case '2g': return 25_000;     // 200 Kbps
          default: return 1_250_000;    // Default 4G
        }
      default:
        return 625_000; // 5 Mbps conservative
    }
  }

  /**
   * Get estimated speed (from history or defaults)
   */
  private async getEstimatedSpeed(): Promise<number> {
    const networkType = networkMonitor.getNetworkType();
    return this.getAverageSpeed(networkType);
  }

  /**
   * Load stored state
   */
  private async loadStoredState(): Promise<void> {
    try {
      const lastTestRaw = await AsyncStorage.getItem(STORAGE_KEYS.LAST_SPEED_TEST);
      if (lastTestRaw) {
        const lastTest = JSON.parse(lastTestRaw);
        this.state.lastTestTimestamp = lastTest.timestamp;
        this.state.currentSpeedBps = lastTest.speedBps;
        this.state.quality = this.getBandwidthQuality(lastTest.speedBps);
        this.state.recommendedQuality = this.getRecommendedQuality(lastTest.speedBps);
      }

      // Load estimated speed
      const networkType = networkMonitor.getNetworkType();
      this.state.estimatedSpeedBps = await this.getAverageSpeed(networkType);
    } catch (error) {
      console.error('[Bandwidth] Failed to load stored state:', error);
    }
  }

  /**
   * Get bandwidth quality classification
   */
  private getBandwidthQuality(speedBps: number): BandwidthQuality {
    if (speedBps >= BANDWIDTH_THRESHOLDS.excellent) return 'excellent';
    if (speedBps >= BANDWIDTH_THRESHOLDS.good) return 'good';
    if (speedBps >= BANDWIDTH_THRESHOLDS.fair) return 'fair';
    if (speedBps >= BANDWIDTH_THRESHOLDS.poor) return 'poor';
    if (speedBps >= BANDWIDTH_THRESHOLDS.veryPoor) return 'veryPoor';
    return 'unknown';
  }

  /**
   * Create speed test result object
   */
  private createResult(
    speedBps: number,
    durationMs: number = 0,
    networkType: 'wifi' | 'cellular' | 'unknown' = networkMonitor.getNetworkType()
  ): SpeedTestResult {
    return {
      speedBps,
      speedMbps: (speedBps * 8) / 1_000_000,
      quality: this.getBandwidthQuality(speedBps),
      timestamp: Date.now(),
      networkType,
      testDurationMs: durationMs,
    };
  }

  /**
   * Notify all listeners
   */
  private notifyListeners(): void {
    this.listeners.forEach(listener => listener(this.state));
  }

  /**
   * Format speed for display
   */
  formatSpeed(speedBps: number): string {
    const speedMbps = (speedBps * 8) / 1_000_000;
    if (speedMbps >= 1) {
      return `${speedMbps.toFixed(1)} Mbps`;
    }
    const speedKbps = (speedBps * 8) / 1_000;
    return `${speedKbps.toFixed(0)} Kbps`;
  }

  /**
   * Get quality badge info for UI
   */
  getQualityBadge(): { label: string; color: string; icon: string } {
    switch (this.state.quality) {
      case 'excellent':
        return { label: 'Excellent', color: '#34c759', icon: '🚀' };
      case 'good':
        return { label: 'Good', color: '#007aff', icon: '✓' };
      case 'fair':
        return { label: 'Fair', color: '#ff9500', icon: '~' };
      case 'poor':
        return { label: 'Poor', color: '#ff6b00', icon: '!' };
      case 'veryPoor':
        return { label: 'Very Slow', color: '#ff3b30', icon: '⚠️' };
      default:
        return { label: 'Unknown', color: '#888', icon: '?' };
    }
  }

  /**
   * Set WiFi-only upload preference
   */
  async setWiFiOnlyUpload(enabled: boolean): Promise<void> {
    await AsyncStorage.setItem(STORAGE_KEYS.WIFI_ONLY_UPLOAD, JSON.stringify(enabled));

    // If enabled and not on WiFi, pause uploads
    if (enabled && !networkMonitor.isWiFi()) {
      await this.pauseAllUploads('WiFi-only mode enabled');
    } else if (!enabled) {
      // If disabled, resume any paused uploads
      await this.resumePausedUploads();
    }
  }

  /**
   * Get WiFi-only upload preference
   */
  async getWiFiOnlyUpload(): Promise<boolean> {
    const value = await AsyncStorage.getItem(STORAGE_KEYS.WIFI_ONLY_UPLOAD);
    return value !== null ? JSON.parse(value) : false;
  }
}

// Singleton instance
export const bandwidthService = new BandwidthEstimationService();
