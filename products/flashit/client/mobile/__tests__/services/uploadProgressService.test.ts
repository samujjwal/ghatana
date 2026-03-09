import { uploadProgressService, UploadProgress } from '../../src/services/uploadProgressService';
import { networkMonitor } from '../../src/services/networkMonitor';

// Mock networkMonitor
jest.mock('../../src/services/networkMonitor', () => ({
  networkMonitor: {
    getNetworkType: jest.fn().mockReturnValue('wifi'),
    getCellularGeneration: jest.fn().mockReturnValue(null),
    isOnline: jest.fn().mockReturnValue(true),
    subscribe: jest.fn().mockReturnValue(() => {}),
  },
}));

describe('UploadProgressService', () => {
  beforeEach(() => {
    uploadProgressService.clear();
    jest.clearAllMocks();
  });

  describe('startTracking', () => {
    it('should start tracking an upload', () => {
      uploadProgressService.startTracking(
        'test-id-1',
        'photo.jpg',
        'image',
        1024 * 1024 // 1MB
      );

      const progress = uploadProgressService.getProgress('test-id-1');
      expect(progress).toBeDefined();
      expect(progress?.id).toBe('test-id-1');
      expect(progress?.fileName).toBe('photo.jpg');
      expect(progress?.fileType).toBe('image');
      expect(progress?.fileSizeBytes).toBe(1024 * 1024);
      expect(progress?.uploadedBytes).toBe(0);
      expect(progress?.progress).toBe(0);
      expect(progress?.status).toBe('queued');
      expect(progress?.isPaused).toBe(false);
    });

    it('should calculate initial ETA based on network type', () => {
      uploadProgressService.startTracking('test-id-2', 'video.mp4', 'video', 50 * 1024 * 1024);

      const progress = uploadProgressService.getProgress('test-id-2');
      expect(progress?.estimatedTimeRemainingMs).toBeGreaterThan(0);
    });

    it('should track multiple uploads', () => {
      uploadProgressService.startTracking('id-1', 'file1.jpg', 'image', 1024);
      uploadProgressService.startTracking('id-2', 'file2.mp4', 'video', 2048);
      uploadProgressService.startTracking('id-3', 'file3.m4a', 'audio', 512);

      const all = uploadProgressService.getAllProgress();
      expect(all.length).toBe(3);
    });
  });

  describe('updateProgress', () => {
    it('should update progress correctly', () => {
      uploadProgressService.startTracking('test-id', 'file.jpg', 'image', 1000);
      uploadProgressService.updateProgress('test-id', 500);

      const progress = uploadProgressService.getProgress('test-id');
      expect(progress?.uploadedBytes).toBe(500);
      expect(progress?.progress).toBe(50);
    });

    it('should set status to uploading when progress > 0', () => {
      uploadProgressService.startTracking('test-id', 'file.jpg', 'image', 1000);
      uploadProgressService.updateProgress('test-id', 100);

      const progress = uploadProgressService.getProgress('test-id');
      expect(progress?.status).toBe('uploading');
    });

    it('should calculate ETA as progress increases', () => {
      uploadProgressService.startTracking('test-id', 'file.jpg', 'image', 1000);
      
      const initialProgress = uploadProgressService.getProgress('test-id');
      const initialETA = initialProgress?.estimatedTimeRemainingMs ?? 0;
      
      uploadProgressService.updateProgress('test-id', 800);
      
      const updatedProgress = uploadProgressService.getProgress('test-id');
      expect(updatedProgress?.estimatedTimeRemainingMs).toBeLessThan(initialETA);
    });

    it('should handle 100% progress', () => {
      uploadProgressService.startTracking('test-id', 'file.jpg', 'image', 1000);
      uploadProgressService.updateProgress('test-id', 1000);

      const progress = uploadProgressService.getProgress('test-id');
      expect(progress?.progress).toBe(100);
    });
  });

  describe('setStatus', () => {
    it('should update status to completed', () => {
      uploadProgressService.startTracking('test-id', 'file.jpg', 'image', 1000);
      uploadProgressService.setStatus('test-id', 'completed');

      const progress = uploadProgressService.getProgress('test-id');
      expect(progress?.status).toBe('completed');
      expect(progress?.progress).toBe(100);
      expect(progress?.estimatedTimeRemainingMs).toBe(0);
    });

    it('should update status to failed with error', () => {
      uploadProgressService.startTracking('test-id', 'file.jpg', 'image', 1000);
      uploadProgressService.setStatus('test-id', 'failed', 'Network error');

      const progress = uploadProgressService.getProgress('test-id');
      expect(progress?.status).toBe('failed');
      expect(progress?.error).toBe('Network error');
    });

    it('should update status to compressing', () => {
      uploadProgressService.startTracking('test-id', 'file.jpg', 'image', 1000);
      uploadProgressService.setStatus('test-id', 'compressing');

      const progress = uploadProgressService.getProgress('test-id');
      expect(progress?.status).toBe('compressing');
    });
  });

  describe('pauseUpload / resumeUpload', () => {
    it('should pause an upload', () => {
      uploadProgressService.startTracking('test-id', 'file.jpg', 'image', 1000);
      uploadProgressService.updateProgress('test-id', 500);
      uploadProgressService.pauseUpload('test-id');

      const progress = uploadProgressService.getProgress('test-id');
      expect(progress?.isPaused).toBe(true);
      expect(progress?.status).toBe('paused');
    });

    it('should resume a paused upload', () => {
      uploadProgressService.startTracking('test-id', 'file.jpg', 'image', 1000);
      uploadProgressService.updateProgress('test-id', 500);
      uploadProgressService.pauseUpload('test-id');
      uploadProgressService.resumeUpload('test-id');

      const progress = uploadProgressService.getProgress('test-id');
      expect(progress?.isPaused).toBe(false);
      expect(progress?.status).toBe('uploading');
    });

    it('should not pause completed uploads', () => {
      uploadProgressService.startTracking('test-id', 'file.jpg', 'image', 1000);
      uploadProgressService.setStatus('test-id', 'completed');
      uploadProgressService.pauseUpload('test-id');

      const progress = uploadProgressService.getProgress('test-id');
      expect(progress?.status).toBe('completed');
      expect(progress?.isPaused).toBe(false);
    });
  });

  describe('stopTracking', () => {
    it('should stop tracking an upload', () => {
      uploadProgressService.startTracking('test-id', 'file.jpg', 'image', 1000);
      uploadProgressService.stopTracking('test-id');

      const progress = uploadProgressService.getProgress('test-id');
      expect(progress).toBeUndefined();
    });
  });

  describe('getStats', () => {
    it('should return correct statistics', () => {
      uploadProgressService.startTracking('id-1', 'file1.jpg', 'image', 1000);
      uploadProgressService.startTracking('id-2', 'file2.mp4', 'video', 2000);
      uploadProgressService.startTracking('id-3', 'file3.m4a', 'audio', 500);

      uploadProgressService.setStatus('id-1', 'completed');
      uploadProgressService.setStatus('id-3', 'failed', 'Error');

      const stats = uploadProgressService.getStats();
      expect(stats.totalFiles).toBe(3);
      expect(stats.completedFiles).toBe(1);
      expect(stats.failedFiles).toBe(1);
      expect(stats.totalBytes).toBe(3500);
    });

    it('should calculate overall progress correctly', () => {
      uploadProgressService.startTracking('id-1', 'file1.jpg', 'image', 1000);
      uploadProgressService.startTracking('id-2', 'file2.jpg', 'image', 1000);
      
      uploadProgressService.updateProgress('id-1', 500); // 50%
      uploadProgressService.updateProgress('id-2', 500); // 50%

      const stats = uploadProgressService.getStats();
      expect(stats.overallProgress).toBe(50);
    });
  });

  describe('subscribe', () => {
    it('should call listener immediately with current state', () => {
      const listener = jest.fn();
      uploadProgressService.startTracking('test-id', 'file.jpg', 'image', 1000);
      
      uploadProgressService.subscribe(listener);
      expect(listener).toHaveBeenCalled();
    });

    it('should notify listeners on progress update', () => {
      const listener = jest.fn();
      uploadProgressService.startTracking('test-id', 'file.jpg', 'image', 1000);
      uploadProgressService.subscribe(listener);
      
      uploadProgressService.updateProgress('test-id', 500);
      
      expect(listener).toHaveBeenCalledTimes(2); // Initial + update
    });

    it('should allow unsubscribing', () => {
      const listener = jest.fn();
      const unsubscribe = uploadProgressService.subscribe(listener);
      unsubscribe();
      
      uploadProgressService.startTracking('new-id', 'file.jpg', 'image', 1000);
      
      // Listener should not be called after unsubscribe
      expect(listener).toHaveBeenCalledTimes(1); // Only initial call
    });
  });

  describe('formatTimeRemaining', () => {
    it('should format seconds correctly', () => {
      expect(uploadProgressService.formatTimeRemaining(30000)).toBe('30s');
    });

    it('should format minutes correctly', () => {
      expect(uploadProgressService.formatTimeRemaining(120000)).toBe('2m');
    });

    it('should format minutes and seconds', () => {
      expect(uploadProgressService.formatTimeRemaining(150000)).toBe('2m 30s');
    });

    it('should format hours correctly', () => {
      expect(uploadProgressService.formatTimeRemaining(3600000)).toBe('1h');
    });

    it('should format hours and minutes', () => {
      expect(uploadProgressService.formatTimeRemaining(5400000)).toBe('1h 30m');
    });

    it('should handle zero', () => {
      expect(uploadProgressService.formatTimeRemaining(0)).toBe('Complete');
    });

    it('should handle very small values', () => {
      expect(uploadProgressService.formatTimeRemaining(500)).toBe('Less than 1s');
    });
  });

  describe('formatSpeed', () => {
    it('should format bytes per second', () => {
      expect(uploadProgressService.formatSpeed(500)).toBe('500 B/s');
    });

    it('should format kilobytes per second', () => {
      expect(uploadProgressService.formatSpeed(1536)).toBe('1.5 KB/s');
    });

    it('should format megabytes per second', () => {
      expect(uploadProgressService.formatSpeed(5242880)).toBe('5.0 MB/s');
    });
  });

  describe('formatFileSize', () => {
    it('should format bytes', () => {
      expect(uploadProgressService.formatFileSize(500)).toBe('500 B');
    });

    it('should format kilobytes', () => {
      expect(uploadProgressService.formatFileSize(1536)).toBe('1.5 KB');
    });

    it('should format megabytes', () => {
      expect(uploadProgressService.formatFileSize(5242880)).toBe('5.0 MB');
    });

    it('should format gigabytes', () => {
      expect(uploadProgressService.formatFileSize(2147483648)).toBe('2.00 GB');
    });
  });

  describe('getNetworkBadge', () => {
    it('should return WiFi badge when on WiFi', () => {
      (networkMonitor.getNetworkType as jest.Mock).mockReturnValue('wifi');
      (networkMonitor.isOnline as jest.Mock).mockReturnValue(true);

      const badge = uploadProgressService.getNetworkBadge();
      expect(badge.label).toBe('WiFi');
      expect(badge.color).toBe('#34c759');
    });

    it('should return Offline badge when not connected', () => {
      (networkMonitor.isOnline as jest.Mock).mockReturnValue(false);

      const badge = uploadProgressService.getNetworkBadge();
      expect(badge.label).toBe('Offline');
      expect(badge.color).toBe('#ff3b30');
    });

    it('should return 4G badge on cellular', () => {
      (networkMonitor.getNetworkType as jest.Mock).mockReturnValue('cellular');
      (networkMonitor.getCellularGeneration as jest.Mock).mockReturnValue('4g');
      (networkMonitor.isOnline as jest.Mock).mockReturnValue(true);

      const badge = uploadProgressService.getNetworkBadge();
      expect(badge.label).toBe('4G');
      expect(badge.color).toBe('#007aff');
    });

    it('should return 5G badge on 5G network', () => {
      (networkMonitor.getNetworkType as jest.Mock).mockReturnValue('cellular');
      (networkMonitor.getCellularGeneration as jest.Mock).mockReturnValue('5g');
      (networkMonitor.isOnline as jest.Mock).mockReturnValue(true);

      const badge = uploadProgressService.getNetworkBadge();
      expect(badge.label).toBe('5G');
      expect(badge.color).toBe('#34c759');
    });

    it('should return 2G badge with warning color', () => {
      (networkMonitor.getNetworkType as jest.Mock).mockReturnValue('cellular');
      (networkMonitor.getCellularGeneration as jest.Mock).mockReturnValue('2g');
      (networkMonitor.isOnline as jest.Mock).mockReturnValue(true);

      const badge = uploadProgressService.getNetworkBadge();
      expect(badge.label).toBe('2G');
      expect(badge.color).toBe('#ff3b30');
    });
  });

  describe('clear', () => {
    it('should clear all tracking data', () => {
      uploadProgressService.startTracking('id-1', 'file1.jpg', 'image', 1000);
      uploadProgressService.startTracking('id-2', 'file2.jpg', 'image', 1000);
      
      uploadProgressService.clear();
      
      expect(uploadProgressService.getAllProgress().length).toBe(0);
    });
  });
});
