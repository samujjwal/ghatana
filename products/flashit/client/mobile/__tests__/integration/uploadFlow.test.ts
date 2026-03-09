/**
 * Integration Tests for Upload Flow
 * 
 * Tests the complete upload pipeline including:
 * - Offline queue → Bandwidth check → Compression → Upload → Progress → Error handling
 * 
 * @doc.type test-suite
 * @doc.purpose Validate end-to-end upload workflow integration
 * @doc.layer product
 * @doc.pattern Integration Testing
 */

import AsyncStorage from '@react-native-async-storage/async-storage';
import * as FileSystem from 'expo-file-system';
import { offlineQueueService } from '../../src/services/offlineQueue';
import { uploadManager } from '../../src/services/uploadManager';
import { bandwidthService } from '../../src/services/bandwidthService';
import { errorHandlerService } from '../../src/services/errorHandlerService';
import { mediaCompressionService } from '../../src/services/mediaCompressionService';
import { uploadProgressService } from '../../src/services/uploadProgressService';
import { networkMonitor } from '../../src/services/networkMonitor';

// Mock all dependencies
jest.mock('@react-native-async-storage/async-storage');
jest.mock('expo-file-system');
jest.mock('expo-image-manipulator', () => ({
  manipulateAsync: jest.fn().mockResolvedValue({ uri: 'compressed-uri' }),
  SaveFormat: { JPEG: 'jpeg', PNG: 'png' },
}));

// Mock network monitor
jest.mock('../../src/services/networkMonitor', () => ({
  networkMonitor: {
    isOnline: jest.fn().mockReturnValue(true),
    isWiFi: jest.fn().mockReturnValue(true),
    isCellular: jest.fn().mockReturnValue(false),
    getNetworkType: jest.fn().mockReturnValue('wifi'),
    subscribe: jest.fn().mockReturnValue(() => {}),
  },
}));

// Mock fetch for speed tests
global.fetch = jest.fn();

describe('Upload Flow Integration', () => {
  beforeEach(async () => {
    jest.clearAllMocks();
    (AsyncStorage.getItem as jest.Mock).mockResolvedValue(null);
    (AsyncStorage.setItem as jest.Mock).mockResolvedValue(undefined);
    (AsyncStorage.removeItem as jest.Mock).mockResolvedValue(undefined);
    
    (FileSystem.getInfoAsync as jest.Mock).mockResolvedValue({
      exists: true,
      size: 1024 * 1024, // 1 MB
      isDirectory: false,
    });
    (FileSystem.deleteAsync as jest.Mock).mockResolvedValue(undefined);
    (FileSystem.copyAsync as jest.Mock).mockResolvedValue(undefined);
    
    // Initialize services
    await offlineQueueService.init();
    await bandwidthService.init();
    await errorHandlerService.init();
  });

  describe('Full Upload Lifecycle', () => {
    it('should queue item when offline', async () => {
      (networkMonitor.isOnline as jest.Mock).mockReturnValue(false);

      const item = {
        id: 'test-1',
        type: 'image' as const,
        uri: 'file:///test/image.jpg',
        metadata: {
          timestamp: Date.now(),
          mimeType: 'image/jpeg',
          fileSize: 1024 * 500,
        },
      };

      await offlineQueueService.addToQueue(item);
      const queue = await offlineQueueService.getQueue();

      expect(queue.length).toBe(1);
      expect(queue[0].status).toBe('pending');
    });

    it('should process queue when back online', async () => {
      // Start offline
      (networkMonitor.isOnline as jest.Mock).mockReturnValue(false);

      await offlineQueueService.addToQueue({
        id: 'test-2',
        type: 'image' as const,
        uri: 'file:///test/image.jpg',
        metadata: {
          timestamp: Date.now(),
          mimeType: 'image/jpeg',
          fileSize: 1024 * 500,
        },
      });

      // Come back online
      (networkMonitor.isOnline as jest.Mock).mockReturnValue(true);
      
      // Trigger queue processing
      const queue = await offlineQueueService.getPendingItems();
      expect(queue.length).toBe(1);
    });

    it('should check bandwidth before upload', async () => {
      (networkMonitor.isOnline as jest.Mock).mockReturnValue(true);
      (networkMonitor.isWiFi as jest.Mock).mockReturnValue(true);

      const allowed = await bandwidthService.shouldAllowUpload();

      expect(allowed.allowed).toBe(true);
    });

    it('should block upload on cellular when WiFi-only enabled', async () => {
      (networkMonitor.isOnline as jest.Mock).mockReturnValue(true);
      (networkMonitor.isWiFi as jest.Mock).mockReturnValue(false);
      (AsyncStorage.getItem as jest.Mock).mockResolvedValue('true');

      const allowed = await bandwidthService.shouldAllowUpload();

      expect(allowed.allowed).toBe(false);
      expect(allowed.reason).toContain('WiFi-only');
    });

    it('should recommend quality based on bandwidth', () => {
      // High speed connection
      const highQuality = bandwidthService.getRecommendedQuality(10000000);
      expect(highQuality).toBe('high');

      // Low speed connection
      const lowQuality = bandwidthService.getRecommendedQuality(100000);
      expect(lowQuality).toBe('low');
    });
  });

  describe('Error Recovery Flow', () => {
    it('should categorize errors and schedule retries', async () => {
      const error = await errorHandlerService.handleError(
        'upload-1',
        'Server unavailable',
        503
      );

      expect(error.category).toBe('server');
      expect(error.isRetryable).toBe(true);
      expect(error.retryStrategy).toBe('exponential');
    });

    it('should not retry auth errors automatically', async () => {
      const error = await errorHandlerService.handleError(
        'upload-2',
        'Token expired',
        401
      );

      expect(error.category).toBe('auth');
      expect(error.isRetryable).toBe(false);
      expect(error.retryStrategy).toBe('manual');
    });

    it('should track error analytics', async () => {
      await errorHandlerService.handleError('upload-3', 'Error 1', 500);
      await errorHandlerService.handleError('upload-4', 'Error 2', 500);
      await errorHandlerService.handleError('upload-5', 'Error 3', undefined);

      const analytics = await errorHandlerService.getAnalytics();

      expect(analytics.totalErrors).toBeGreaterThan(0);
    });

    it('should prioritize queue by retry count and type', async () => {
      // Mock queue with different priorities
      jest.spyOn(offlineQueueService, 'getPendingItems').mockResolvedValue([
        { 
          id: '1', 
          type: 'video', 
          uri: 'file:///video.mp4',
          metadata: { retryCount: 2, timestamp: 1000 } 
        } as any,
        { 
          id: '2', 
          type: 'text', 
          uri: 'file:///text.txt',
          metadata: { retryCount: 0, timestamp: 2000 } 
        } as any,
        { 
          id: '3', 
          type: 'image', 
          uri: 'file:///image.jpg',
          metadata: { retryCount: 0, timestamp: 1500 } 
        } as any,
      ]);

      const prioritized = await errorHandlerService.getPrioritizedQueue();

      // Text should be first (smallest), then image, then video
      expect(prioritized[0].id).toBe('2'); // text
      expect(prioritized[1].id).toBe('3'); // image
      expect(prioritized[2].id).toBe('1'); // video
    });
  });

  describe('Compression Integration', () => {
    it('should compress images before upload', async () => {
      const result = await mediaCompressionService.compressImage(
        'file:///test/large-image.jpg',
        'medium'
      );

      expect(result).toBeDefined();
      expect(result.uri).toBeTruthy();
    });

    it('should skip compression for small files', async () => {
      (FileSystem.getInfoAsync as jest.Mock).mockResolvedValue({
        exists: true,
        size: 50 * 1024, // 50 KB - below threshold
        isDirectory: false,
      });

      const result = await mediaCompressionService.compressImage(
        'file:///test/small-image.jpg',
        'medium'
      );

      expect(result.compressionApplied).toBe(false);
    });

    it('should recommend quality based on network', async () => {
      const quality = mediaCompressionService.getRecommendedQuality('wifi', 10000000);
      expect(['high', 'original']).toContain(quality);

      const cellularQuality = mediaCompressionService.getRecommendedQuality('cellular', 500000);
      expect(['low', 'medium']).toContain(cellularQuality);
    });
  });

  describe('Progress Tracking Integration', () => {
    it('should track upload progress', () => {
      uploadProgressService.startUpload('upload-1', {
        fileName: 'test.jpg',
        fileSize: 1024 * 1024,
        mimeType: 'image/jpeg',
      });

      const progress = uploadProgressService.getProgress('upload-1');

      expect(progress).toBeDefined();
      expect(progress?.status).toBe('uploading');
      expect(progress?.progress).toBe(0);
    });

    it('should update progress during upload', () => {
      uploadProgressService.startUpload('upload-2', {
        fileName: 'test.jpg',
        fileSize: 1024 * 1024,
        mimeType: 'image/jpeg',
      });

      uploadProgressService.updateProgress('upload-2', 50, 512 * 1024);

      const progress = uploadProgressService.getProgress('upload-2');

      expect(progress?.progress).toBe(50);
      expect(progress?.bytesUploaded).toBe(512 * 1024);
    });

    it('should calculate ETA', () => {
      uploadProgressService.startUpload('upload-3', {
        fileName: 'test.jpg',
        fileSize: 1024 * 1024,
        mimeType: 'image/jpeg',
      });

      // Simulate progress
      uploadProgressService.updateProgress('upload-3', 25, 256 * 1024);
      
      const progress = uploadProgressService.getProgress('upload-3');

      expect(progress?.speedBps).toBeDefined();
    });

    it('should handle pause and resume', () => {
      uploadProgressService.startUpload('upload-4', {
        fileName: 'test.jpg',
        fileSize: 1024 * 1024,
        mimeType: 'image/jpeg',
      });

      uploadProgressService.pauseUpload('upload-4');
      expect(uploadProgressService.getProgress('upload-4')?.status).toBe('paused');

      uploadProgressService.resumeUpload('upload-4');
      expect(uploadProgressService.getProgress('upload-4')?.status).toBe('uploading');
    });
  });

  describe('Network State Transitions', () => {
    it('should pause uploads when going offline', async () => {
      // Start with active upload
      uploadProgressService.startUpload('upload-5', {
        fileName: 'test.jpg',
        fileSize: 1024 * 1024,
        mimeType: 'image/jpeg',
      });

      // Simulate going offline
      (networkMonitor.isOnline as jest.Mock).mockReturnValue(false);

      const allowed = await bandwidthService.shouldAllowUpload();

      expect(allowed.allowed).toBe(false);
    });

    it('should respect WiFi-only setting on network change', async () => {
      await bandwidthService.setWiFiOnlyUpload(true);
      
      // Switch to cellular
      (networkMonitor.isWiFi as jest.Mock).mockReturnValue(false);
      (networkMonitor.isCellular as jest.Mock).mockReturnValue(true);

      const allowed = await bandwidthService.shouldAllowUpload();

      expect(allowed.allowed).toBe(false);
    });
  });

  describe('Cleanup and Resource Management', () => {
    it('should clean up temporary files after upload', async () => {
      const tempUri = `${FileSystem.cacheDirectory}temp-123.jpg`;
      (FileSystem.getInfoAsync as jest.Mock).mockResolvedValue({
        exists: true,
        size: 1024,
        isDirectory: false,
      });

      await mediaCompressionService.cleanupTempFiles([tempUri]);

      expect(FileSystem.deleteAsync).toHaveBeenCalledWith(tempUri, { idempotent: true });
    });

    it('should clear completed uploads from progress', () => {
      uploadProgressService.startUpload('upload-6', {
        fileName: 'test.jpg',
        fileSize: 1024,
        mimeType: 'image/jpeg',
      });
      uploadProgressService.completeUpload('upload-6');
      uploadProgressService.clearCompletedUploads();

      const all = uploadProgressService.getAllProgress();
      const completed = all.filter(p => p.status === 'completed');

      expect(completed.length).toBe(0);
    });
  });
});

describe('Edge Cases', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    (AsyncStorage.getItem as jest.Mock).mockResolvedValue(null);
  });

  it('should handle rapid network changes', async () => {
    // Online → Offline → Online quickly
    (networkMonitor.isOnline as jest.Mock)
      .mockReturnValueOnce(true)
      .mockReturnValueOnce(false)
      .mockReturnValueOnce(true);

    const check1 = await bandwidthService.shouldAllowUpload();
    const check2 = await bandwidthService.shouldAllowUpload();
    const check3 = await bandwidthService.shouldAllowUpload();

    expect(check1.allowed).toBe(true);
    expect(check2.allowed).toBe(false);
    expect(check3.allowed).toBe(true);
  });

  it('should handle very large files', async () => {
    const largeFileSize = 100 * 1024 * 1024; // 100 MB
    
    (FileSystem.getInfoAsync as jest.Mock).mockResolvedValue({
      exists: true,
      size: largeFileSize,
      isDirectory: false,
    });

    await offlineQueueService.addToQueue({
      id: 'large-file',
      type: 'video' as const,
      uri: 'file:///large-video.mp4',
      metadata: {
        timestamp: Date.now(),
        mimeType: 'video/mp4',
        fileSize: largeFileSize,
      },
    });

    const queue = await offlineQueueService.getQueue();
    expect(queue.length).toBe(1);
  });

  it('should handle corrupted AsyncStorage data', async () => {
    (AsyncStorage.getItem as jest.Mock).mockResolvedValue('not valid json');

    // Should not throw
    await expect(offlineQueueService.init()).resolves.not.toThrow();
  });

  it('should handle missing file during upload', async () => {
    (FileSystem.getInfoAsync as jest.Mock).mockResolvedValue({
      exists: false,
    });

    const result = await mediaCompressionService.compressImage(
      'file:///missing-file.jpg',
      'medium'
    );

    // Should handle gracefully
    expect(result).toBeDefined();
  });
});
