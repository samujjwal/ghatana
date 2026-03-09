import AsyncStorage from '@react-native-async-storage/async-storage';
import { bandwidthService, SpeedTestResult, BandwidthState } from '../../src/services/bandwidthService';
import { networkMonitor } from '../../src/services/networkMonitor';
import { uploadProgressService } from '../../src/services/uploadProgressService';

// Mock dependencies
jest.mock('@react-native-async-storage/async-storage');
jest.mock('../../src/services/networkMonitor', () => ({
  networkMonitor: {
    isOnline: jest.fn().mockReturnValue(true),
    isWiFi: jest.fn().mockReturnValue(true),
    isCellular: jest.fn().mockReturnValue(false),
    getNetworkType: jest.fn().mockReturnValue('wifi'),
    getCellularGeneration: jest.fn().mockReturnValue(null),
    subscribe: jest.fn().mockReturnValue(() => {}),
  },
}));
jest.mock('../../src/services/uploadProgressService', () => ({
  uploadProgressService: {
    getAllProgress: jest.fn().mockReturnValue([]),
    pauseUpload: jest.fn(),
    resumeUpload: jest.fn(),
  },
}));

// Mock fetch
global.fetch = jest.fn();

describe('BandwidthEstimationService', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    (AsyncStorage.getItem as jest.Mock).mockResolvedValue(null);
    (AsyncStorage.setItem as jest.Mock).mockResolvedValue(undefined);
  });

  describe('init', () => {
    it('should initialize and load stored state', async () => {
      const storedState = {
        timestamp: Date.now(),
        speedBps: 5000000,
        networkType: 'wifi',
      };
      (AsyncStorage.getItem as jest.Mock).mockResolvedValue(JSON.stringify(storedState));

      await bandwidthService.init();

      expect(AsyncStorage.getItem).toHaveBeenCalled();
    });

    it('should subscribe to network changes', async () => {
      await bandwidthService.init();
      expect(networkMonitor.subscribe).toHaveBeenCalled();
    });
  });

  describe('runSpeedTest', () => {
    it('should run a speed test and return result', async () => {
      const mockBlob = { size: 102400 };
      (global.fetch as jest.Mock).mockResolvedValue({
        ok: true,
        blob: () => Promise.resolve(mockBlob),
      });

      const result = await bandwidthService.runSpeedTest();

      expect(result).toBeDefined();
      expect(result.speedBps).toBeGreaterThan(0);
      expect(result.networkType).toBe('wifi');
      expect(result.quality).toBeDefined();
    });

    it('should store speed test result', async () => {
      const mockBlob = { size: 102400 };
      (global.fetch as jest.Mock).mockResolvedValue({
        ok: true,
        blob: () => Promise.resolve(mockBlob),
      });

      await bandwidthService.runSpeedTest();

      expect(AsyncStorage.setItem).toHaveBeenCalled();
    });

    it('should handle speed test failure gracefully', async () => {
      (global.fetch as jest.Mock).mockRejectedValue(new Error('Network error'));

      const result = await bandwidthService.runSpeedTest();

      expect(result).toBeDefined();
      expect(result.speedBps).toBeGreaterThan(0); // Should return estimated speed
    });

    it('should not run concurrent speed tests', async () => {
      const mockBlob = { size: 102400 };
      (global.fetch as jest.Mock).mockImplementation(() =>
        new Promise(resolve =>
          setTimeout(() => resolve({ ok: true, blob: () => Promise.resolve(mockBlob) }), 100)
        )
      );

      // Start two tests simultaneously
      const promise1 = bandwidthService.runSpeedTest();
      const promise2 = bandwidthService.runSpeedTest();

      await Promise.all([promise1, promise2]);

      // Fetch should only be called once
      expect(global.fetch).toHaveBeenCalledTimes(1);
    });
  });

  describe('getRecommendedQuality', () => {
    it('should recommend high quality for fast connections', () => {
      const quality = bandwidthService.getRecommendedQuality(5000000); // 5 MB/s
      expect(quality).toBe('high');
    });

    it('should recommend medium quality for moderate connections', () => {
      const quality = bandwidthService.getRecommendedQuality(750000); // 750 KB/s
      expect(quality).toBe('medium');
    });

    it('should recommend low quality for slow connections', () => {
      const quality = bandwidthService.getRecommendedQuality(100000); // 100 KB/s
      expect(quality).toBe('low');
    });
  });

  describe('shouldAllowUpload', () => {
    it('should allow upload when online on WiFi', async () => {
      (networkMonitor.isOnline as jest.Mock).mockReturnValue(true);
      (networkMonitor.isWiFi as jest.Mock).mockReturnValue(true);

      const result = await bandwidthService.shouldAllowUpload();

      expect(result.allowed).toBe(true);
    });

    it('should not allow upload when offline', async () => {
      (networkMonitor.isOnline as jest.Mock).mockReturnValue(false);

      const result = await bandwidthService.shouldAllowUpload();

      expect(result.allowed).toBe(false);
      expect(result.reason).toContain('No internet');
    });

    it('should not allow upload on cellular when WiFi-only enabled', async () => {
      (networkMonitor.isOnline as jest.Mock).mockReturnValue(true);
      (networkMonitor.isWiFi as jest.Mock).mockReturnValue(false);
      (AsyncStorage.getItem as jest.Mock).mockResolvedValue('true');

      const result = await bandwidthService.shouldAllowUpload();

      expect(result.allowed).toBe(false);
      expect(result.reason).toContain('WiFi-only');
    });

    it('should allow upload on cellular when WiFi-only disabled', async () => {
      (networkMonitor.isOnline as jest.Mock).mockReturnValue(true);
      (networkMonitor.isWiFi as jest.Mock).mockReturnValue(false);
      (AsyncStorage.getItem as jest.Mock).mockResolvedValue('false');

      const result = await bandwidthService.shouldAllowUpload();

      expect(result.allowed).toBe(true);
    });
  });

  describe('setWiFiOnlyUpload', () => {
    it('should store WiFi-only preference', async () => {
      await bandwidthService.setWiFiOnlyUpload(true);

      expect(AsyncStorage.setItem).toHaveBeenCalledWith(
        '@settings_wifiOnlyUpload',
        'true'
      );
    });

    it('should pause uploads when enabling WiFi-only on cellular', async () => {
      (networkMonitor.isWiFi as jest.Mock).mockReturnValue(false);
      (uploadProgressService.getAllProgress as jest.Mock).mockReturnValue([
        { id: '1', status: 'uploading' },
        { id: '2', status: 'queued' },
      ]);

      await bandwidthService.setWiFiOnlyUpload(true);

      expect(uploadProgressService.pauseUpload).toHaveBeenCalledTimes(2);
    });
  });

  describe('formatSpeed', () => {
    it('should format speed in Mbps for fast connections', () => {
      const formatted = bandwidthService.formatSpeed(5000000); // 5 MB/s = 40 Mbps
      expect(formatted).toContain('Mbps');
    });

    it('should format speed in Kbps for slow connections', () => {
      const formatted = bandwidthService.formatSpeed(50000); // 50 KB/s = 400 Kbps
      expect(formatted).toContain('Kbps');
    });
  });

  describe('getQualityBadge', () => {
    it('should return badge info for current quality', () => {
      const badge = bandwidthService.getQualityBadge();

      expect(badge).toHaveProperty('label');
      expect(badge).toHaveProperty('color');
      expect(badge).toHaveProperty('icon');
    });
  });

  describe('subscribe', () => {
    it('should notify listeners of state changes', async () => {
      const listener = jest.fn();
      const unsubscribe = bandwidthService.subscribe(listener);

      expect(listener).toHaveBeenCalled();
      
      unsubscribe();
    });

    it('should allow unsubscribing', () => {
      const listener = jest.fn();
      const unsubscribe = bandwidthService.subscribe(listener);
      
      unsubscribe();
      
      // Listener should have been called once (on subscribe)
      expect(listener).toHaveBeenCalledTimes(1);
    });
  });
});
