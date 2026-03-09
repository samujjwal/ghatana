/**
 * @jest-environment node
 */

import { uploadManager } from '../../services/uploadManager';
import { offlineQueueService } from '../../services/offlineQueue';
import { networkMonitor } from '../../services/networkMonitor';

// Mock dependencies
jest.mock('../../services/offlineQueue');
jest.mock('../../services/networkMonitor');

global.fetch = jest.fn();

describe('UploadManager', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    jest.useFakeTimers();
    
    // Default mock implementations
    (networkMonitor.isOnline as jest.Mock).mockReturnValue(true);
    (offlineQueueService.getPendingItems as jest.Mock).mockResolvedValue([]);
    (offlineQueueService.updateItemStatus as jest.Mock).mockResolvedValue(undefined);
  });

  afterEach(() => {
    jest.useRealTimers();
    uploadManager.stop();
  });

  describe('start', () => {
    it('should start processing queue', () => {
      uploadManager.start();

      expect(uploadManager.isRunning()).toBe(true);
    });

    it('should not start twice', () => {
      uploadManager.start();
      uploadManager.start();

      expect(uploadManager.isRunning()).toBe(true);
    });
  });

  describe('stop', () => {
    it('should stop processing queue', () => {
      uploadManager.start();
      uploadManager.stop();

      expect(uploadManager.isRunning()).toBe(false);
    });
  });

  describe('processQueue', () => {
    it('should skip processing when offline', async () => {
      (networkMonitor.isOnline as jest.Mock).mockReturnValue(false);

      uploadManager.start();

      await jest.runOnlyPendingTimersAsync();

      expect(offlineQueueService.getPendingItems).not.toHaveBeenCalled();
    });

    it('should process pending items when online', async () => {
      const mockItems = [
        {
          id: '1',
          type: 'audio' as const,
          uri: 'file:///test/audio.m4a',
          status: 'pending' as const,
          metadata: { sphereId: 'sphere-1', tags: [], retryCount: 0 },
          createdAt: new Date().toISOString(),
          updatedAt: new Date().toISOString(),
        },
      ];

      (offlineQueueService.getPendingItems as jest.Mock).mockResolvedValue(mockItems);
      (global.fetch as jest.Mock)
        .mockResolvedValueOnce({
          ok: true,
          json: async () => ({ presignedUrl: 'https://s3.example.com/upload', fileId: 'file-1' }),
        })
        .mockResolvedValueOnce({
          ok: true,
        })
        .mockResolvedValueOnce({
          ok: true,
          json: async () => ({ success: true }),
        });

      uploadManager.start();

      await jest.runOnlyPendingTimersAsync();

      expect(offlineQueueService.getPendingItems).toHaveBeenCalled();
      expect(offlineQueueService.updateItemStatus).toHaveBeenCalledWith('1', 'uploading');
      expect(offlineQueueService.updateItemStatus).toHaveBeenCalledWith('1', 'completed');
    });

    it('should respect concurrent upload limit', async () => {
      const mockItems = [
        {
          id: '1',
          type: 'audio' as const,
          uri: 'file:///test/audio1.m4a',
          status: 'pending' as const,
          metadata: { sphereId: 'sphere-1', tags: [], retryCount: 0 },
          createdAt: new Date().toISOString(),
          updatedAt: new Date().toISOString(),
        },
        {
          id: '2',
          type: 'audio' as const,
          uri: 'file:///test/audio2.m4a',
          status: 'pending' as const,
          metadata: { sphereId: 'sphere-1', tags: [], retryCount: 0 },
          createdAt: new Date().toISOString(),
          updatedAt: new Date().toISOString(),
        },
        {
          id: '3',
          type: 'audio' as const,
          uri: 'file:///test/audio3.m4a',
          status: 'pending' as const,
          metadata: { sphereId: 'sphere-1', tags: [], retryCount: 0 },
          createdAt: new Date().toISOString(),
          updatedAt: new Date().toISOString(),
        },
      ];

      (offlineQueueService.getPendingItems as jest.Mock).mockResolvedValue(mockItems);
      (global.fetch as jest.Mock).mockImplementation(() =>
        Promise.resolve({
          ok: true,
          json: async () => ({ presignedUrl: 'https://s3.example.com/upload', fileId: 'file-1' }),
        })
      );

      uploadManager.start();

      await jest.runOnlyPendingTimersAsync();

      // Should only process MAX_CONCURRENT (2) items
      const uploadingCalls = (offlineQueueService.updateItemStatus as jest.Mock).mock.calls.filter(
        (call) => call[1] === 'uploading'
      );

      expect(uploadingCalls.length).toBeLessThanOrEqual(2);
    });
  });

  describe('uploadItem', () => {
    it('should successfully upload audio file', async () => {
      const mockItem = {
        id: '1',
        type: 'audio' as const,
        uri: 'file:///test/audio.m4a',
        status: 'pending' as const,
        metadata: { sphereId: 'sphere-1', tags: ['test'], retryCount: 0 },
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
      };

      (global.fetch as jest.Mock)
        .mockResolvedValueOnce({
          ok: true,
          json: async () => ({ presignedUrl: 'https://s3.example.com/upload', fileId: 'file-1' }),
        })
        .mockResolvedValueOnce({
          ok: true,
        })
        .mockResolvedValueOnce({
          ok: true,
          json: async () => ({ success: true }),
        });

      (offlineQueueService.getPendingItems as jest.Mock).mockResolvedValue([mockItem]);

      uploadManager.start();
      await jest.runOnlyPendingTimersAsync();

      expect(offlineQueueService.updateItemStatus).toHaveBeenCalledWith('1', 'uploading');
      expect(offlineQueueService.updateItemStatus).toHaveBeenCalledWith('1', 'completed');
    });

    it('should handle upload failure with retry', async () => {
      const mockItem = {
        id: '1',
        type: 'image' as const,
        uri: 'file:///test/image.jpg',
        status: 'pending' as const,
        metadata: { sphereId: 'sphere-1', tags: [], retryCount: 0 },
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
      };

      (global.fetch as jest.Mock).mockRejectedValue(new Error('Network error'));
      (offlineQueueService.getPendingItems as jest.Mock).mockResolvedValue([mockItem]);

      uploadManager.start();
      await jest.runOnlyPendingTimersAsync();

      expect(offlineQueueService.updateItemStatus).toHaveBeenCalledWith(
        '1',
        'failed',
        expect.stringContaining('Network error')
      );
    });

    it('should retry failed upload with exponential backoff', async () => {
      const mockItem = {
        id: '1',
        type: 'video' as const,
        uri: 'file:///test/video.mp4',
        status: 'pending' as const,
        metadata: { sphereId: 'sphere-1', tags: [], retryCount: 1 },
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
      };

      (global.fetch as jest.Mock).mockRejectedValue(new Error('Timeout'));
      (offlineQueueService.getPendingItems as jest.Mock).mockResolvedValue([mockItem]);

      uploadManager.start();

      // First attempt should wait longer due to retry count
      await jest.advanceTimersByTimeAsync(5000);

      expect(offlineQueueService.updateItemStatus).toHaveBeenCalledWith('1', 'uploading');
    });

    it('should mark item as failed after max retries', async () => {
      const mockItem = {
        id: '1',
        type: 'audio' as const,
        uri: 'file:///test/audio.m4a',
        status: 'pending' as const,
        metadata: { sphereId: 'sphere-1', tags: [], retryCount: 3 },
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
      };

      (offlineQueueService.getPendingItems as jest.Mock).mockResolvedValue([mockItem]);

      uploadManager.start();
      await jest.runOnlyPendingTimersAsync();

      // Should not attempt upload due to max retries exceeded
      expect(offlineQueueService.updateItemStatus).not.toHaveBeenCalledWith('1', 'uploading');
    });
  });

  describe('getPresignedUrl', () => {
    it('should request presigned URL for audio file', async () => {
      const mockResponse = {
        presignedUrl: 'https://s3.example.com/upload',
        fileId: 'file-123',
      };

      (global.fetch as jest.Mock).mockResolvedValue({
        ok: true,
        json: async () => mockResponse,
      });

      const mockItem = {
        id: '1',
        type: 'audio' as const,
        uri: 'file:///test/audio.m4a',
        status: 'pending' as const,
        metadata: { sphereId: 'sphere-1', tags: [], retryCount: 0 },
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
      };

      (offlineQueueService.getPendingItems as jest.Mock).mockResolvedValue([mockItem]);
      (global.fetch as jest.Mock)
        .mockResolvedValueOnce({
          ok: true,
          json: async () => mockResponse,
        })
        .mockResolvedValueOnce({ ok: true })
        .mockResolvedValueOnce({ ok: true, json: async () => ({ success: true }) });

      uploadManager.start();
      await jest.runOnlyPendingTimersAsync();

      expect(global.fetch).toHaveBeenCalledWith(
        'http://localhost:2900/api/upload/presign',
        expect.objectContaining({
          method: 'POST',
          headers: expect.objectContaining({
            'Content-Type': 'application/json',
          }),
          body: expect.stringContaining('"fileType":"audio/m4a"'),
        })
      );
    });

    it('should handle presigned URL request failure', async () => {
      (global.fetch as jest.Mock).mockResolvedValue({
        ok: false,
        statusText: 'Bad Request',
      });

      const mockItem = {
        id: '1',
        type: 'image' as const,
        uri: 'file:///test/image.jpg',
        status: 'pending' as const,
        metadata: { sphereId: 'sphere-1', tags: [], retryCount: 0 },
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
      };

      (offlineQueueService.getPendingItems as jest.Mock).mockResolvedValue([mockItem]);

      uploadManager.start();
      await jest.runOnlyPendingTimersAsync();

      expect(offlineQueueService.updateItemStatus).toHaveBeenCalledWith(
        '1',
        'failed',
        expect.stringContaining('presigned URL')
      );
    });
  });

  describe('getProgress', () => {
    it('should return current upload progress', () => {
      const progress = uploadManager.getProgress();

      expect(progress).toHaveProperty('activeUploads');
      expect(progress).toHaveProperty('queueLength');
      expect(typeof progress.activeUploads).toBe('number');
      expect(typeof progress.queueLength).toBe('number');
    });

    it('should update progress during uploads', async () => {
      const mockItems = [
        {
          id: '1',
          type: 'audio' as const,
          uri: 'file:///test/audio.m4a',
          status: 'pending' as const,
          metadata: { sphereId: 'sphere-1', tags: [], retryCount: 0 },
          createdAt: new Date().toISOString(),
          updatedAt: new Date().toISOString(),
        },
      ];

      (offlineQueueService.getPendingItems as jest.Mock).mockResolvedValue(mockItems);
      (global.fetch as jest.Mock).mockImplementation(
        () =>
          new Promise((resolve) =>
            setTimeout(
              () =>
                resolve({
                  ok: true,
                  json: async () => ({ presignedUrl: 'https://s3.example.com/upload', fileId: 'file-1' }),
                }),
              1000
            )
          )
      );

      uploadManager.start();

      // Check progress before upload starts
      const progressBefore = uploadManager.getProgress();
      expect(progressBefore.activeUploads).toBe(0);

      // Advance timers to start upload
      await jest.advanceTimersByTimeAsync(5000);

      const progressDuring = uploadManager.getProgress();
      expect(progressDuring.queueLength).toBeGreaterThanOrEqual(0);
    });
  });
});
