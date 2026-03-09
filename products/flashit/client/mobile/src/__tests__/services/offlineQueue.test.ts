/**
 * @jest-environment node
 */

import AsyncStorage from '@react-native-async-storage/async-storage';
import { offlineQueueService, QueueItem, QueueItemStatus } from '../../services/offlineQueue';

// Mock AsyncStorage
jest.mock('@react-native-async-storage/async-storage', () => ({
  getItem: jest.fn(),
  setItem: jest.fn(),
  removeItem: jest.fn(),
  clear: jest.fn(),
}));

describe('OfflineQueueService', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    // Reset queue state
    (AsyncStorage.getItem as jest.Mock).mockResolvedValue(null);
  });

  describe('enqueue', () => {
    it('should add item to queue with pending status', async () => {
      const item: Omit<QueueItem, 'id' | 'status' | 'createdAt' | 'updatedAt'> = {
        type: 'audio',
        uri: 'file:///test/audio.m4a',
        metadata: {
          sphereId: 'sphere-123',
          tags: ['test'],
          retryCount: 0,
        },
      };

      const queuedItem = await offlineQueueService.enqueue(item);

      expect(queuedItem.id).toBeDefined();
      expect(queuedItem.status).toBe('pending');
      expect(queuedItem.type).toBe('audio');
      expect(queuedItem.uri).toBe('file:///test/audio.m4a');
      expect(AsyncStorage.setItem).toHaveBeenCalled();
    });

    it('should generate unique IDs for each item', async () => {
      const item1 = await offlineQueueService.enqueue({
        type: 'image',
        uri: 'file:///test/image1.jpg',
        metadata: { sphereId: 'sphere-1', tags: [], retryCount: 0 },
      });

      const item2 = await offlineQueueService.enqueue({
        type: 'image',
        uri: 'file:///test/image2.jpg',
        metadata: { sphereId: 'sphere-1', tags: [], retryCount: 0 },
      });

      expect(item1.id).not.toBe(item2.id);
    });

    it('should persist queue to AsyncStorage', async () => {
      await offlineQueueService.enqueue({
        type: 'video',
        uri: 'file:///test/video.mp4',
        metadata: { sphereId: 'sphere-1', tags: [], retryCount: 0 },
      });

      expect(AsyncStorage.setItem).toHaveBeenCalledWith(
        'offline_queue',
        expect.any(String)
      );
    });
  });

  describe('getQueue', () => {
    it('should return empty array when queue is empty', async () => {
      (AsyncStorage.getItem as jest.Mock).mockResolvedValue(null);

      const queue = await offlineQueueService.getQueue();

      expect(queue).toEqual([]);
    });

    it('should return parsed queue from AsyncStorage', async () => {
      const mockQueue: QueueItem[] = [
        {
          id: '1',
          type: 'audio',
          uri: 'file:///test/audio.m4a',
          status: 'pending',
          metadata: { sphereId: 'sphere-1', tags: [], retryCount: 0 },
          createdAt: new Date().toISOString(),
          updatedAt: new Date().toISOString(),
        },
      ];

      (AsyncStorage.getItem as jest.Mock).mockResolvedValue(JSON.stringify(mockQueue));

      const queue = await offlineQueueService.getQueue();

      expect(queue).toEqual(mockQueue);
    });

    it('should handle corrupted queue data gracefully', async () => {
      (AsyncStorage.getItem as jest.Mock).mockResolvedValue('invalid json');

      const queue = await offlineQueueService.getQueue();

      expect(queue).toEqual([]);
    });
  });

  describe('getPendingItems', () => {
    it('should return only pending items', async () => {
      const mockQueue: QueueItem[] = [
        {
          id: '1',
          type: 'audio',
          uri: 'file:///test/audio1.m4a',
          status: 'pending',
          metadata: { sphereId: 'sphere-1', tags: [], retryCount: 0 },
          createdAt: new Date().toISOString(),
          updatedAt: new Date().toISOString(),
        },
        {
          id: '2',
          type: 'audio',
          uri: 'file:///test/audio2.m4a',
          status: 'uploading',
          metadata: { sphereId: 'sphere-1', tags: [], retryCount: 0 },
          createdAt: new Date().toISOString(),
          updatedAt: new Date().toISOString(),
        },
        {
          id: '3',
          type: 'audio',
          uri: 'file:///test/audio3.m4a',
          status: 'pending',
          metadata: { sphereId: 'sphere-1', tags: [], retryCount: 0 },
          createdAt: new Date().toISOString(),
          updatedAt: new Date().toISOString(),
        },
      ];

      (AsyncStorage.getItem as jest.Mock).mockResolvedValue(JSON.stringify(mockQueue));

      const pendingItems = await offlineQueueService.getPendingItems();

      expect(pendingItems).toHaveLength(2);
      expect(pendingItems.every(item => item.status === 'pending')).toBe(true);
    });
  });

  describe('updateItemStatus', () => {
    it('should update item status and persist', async () => {
      const mockQueue: QueueItem[] = [
        {
          id: '1',
          type: 'audio',
          uri: 'file:///test/audio.m4a',
          status: 'pending',
          metadata: { sphereId: 'sphere-1', tags: [], retryCount: 0 },
          createdAt: new Date().toISOString(),
          updatedAt: new Date().toISOString(),
        },
      ];

      (AsyncStorage.getItem as jest.Mock).mockResolvedValue(JSON.stringify(mockQueue));

      await offlineQueueService.updateItemStatus('1', 'uploading');

      expect(AsyncStorage.setItem).toHaveBeenCalled();
      const savedQueue = JSON.parse((AsyncStorage.setItem as jest.Mock).mock.calls[0][1]);
      expect(savedQueue[0].status).toBe('uploading');
    });

    it('should update retry count when status is failed', async () => {
      const mockQueue: QueueItem[] = [
        {
          id: '1',
          type: 'audio',
          uri: 'file:///test/audio.m4a',
          status: 'uploading',
          metadata: { sphereId: 'sphere-1', tags: [], retryCount: 0 },
          createdAt: new Date().toISOString(),
          updatedAt: new Date().toISOString(),
        },
      ];

      (AsyncStorage.getItem as jest.Mock).mockResolvedValue(JSON.stringify(mockQueue));

      await offlineQueueService.updateItemStatus('1', 'failed', 'Network error');

      const savedQueue = JSON.parse((AsyncStorage.setItem as jest.Mock).mock.calls[0][1]);
      expect(savedQueue[0].metadata.retryCount).toBe(1);
      expect(savedQueue[0].metadata.error).toBe('Network error');
    });

    it('should not update if item not found', async () => {
      (AsyncStorage.getItem as jest.Mock).mockResolvedValue(JSON.stringify([]));

      await offlineQueueService.updateItemStatus('non-existent', 'uploading');

      expect(AsyncStorage.setItem).not.toHaveBeenCalled();
    });
  });

  describe('removeItem', () => {
    it('should remove item from queue', async () => {
      const mockQueue: QueueItem[] = [
        {
          id: '1',
          type: 'audio',
          uri: 'file:///test/audio1.m4a',
          status: 'pending',
          metadata: { sphereId: 'sphere-1', tags: [], retryCount: 0 },
          createdAt: new Date().toISOString(),
          updatedAt: new Date().toISOString(),
        },
        {
          id: '2',
          type: 'audio',
          uri: 'file:///test/audio2.m4a',
          status: 'pending',
          metadata: { sphereId: 'sphere-1', tags: [], retryCount: 0 },
          createdAt: new Date().toISOString(),
          updatedAt: new Date().toISOString(),
        },
      ];

      (AsyncStorage.getItem as jest.Mock).mockResolvedValue(JSON.stringify(mockQueue));

      await offlineQueueService.removeItem('1');

      const savedQueue = JSON.parse((AsyncStorage.setItem as jest.Mock).mock.calls[0][1]);
      expect(savedQueue).toHaveLength(1);
      expect(savedQueue[0].id).toBe('2');
    });
  });

  describe('getQueueStats', () => {
    it('should return correct statistics', async () => {
      const mockQueue: QueueItem[] = [
        {
          id: '1',
          type: 'audio',
          uri: 'file:///test/audio1.m4a',
          status: 'pending',
          metadata: { sphereId: 'sphere-1', tags: [], retryCount: 0 },
          createdAt: new Date().toISOString(),
          updatedAt: new Date().toISOString(),
        },
        {
          id: '2',
          type: 'audio',
          uri: 'file:///test/audio2.m4a',
          status: 'uploading',
          metadata: { sphereId: 'sphere-1', tags: [], retryCount: 0 },
          createdAt: new Date().toISOString(),
          updatedAt: new Date().toISOString(),
        },
        {
          id: '3',
          type: 'audio',
          uri: 'file:///test/audio3.m4a',
          status: 'failed',
          metadata: { sphereId: 'sphere-1', tags: [], retryCount: 2 },
          createdAt: new Date().toISOString(),
          updatedAt: new Date().toISOString(),
        },
        {
          id: '4',
          type: 'audio',
          uri: 'file:///test/audio4.m4a',
          status: 'completed',
          metadata: { sphereId: 'sphere-1', tags: [], retryCount: 0 },
          createdAt: new Date().toISOString(),
          updatedAt: new Date().toISOString(),
        },
      ];

      (AsyncStorage.getItem as jest.Mock).mockResolvedValue(JSON.stringify(mockQueue));

      const stats = await offlineQueueService.getQueueStats();

      expect(stats.total).toBe(4);
      expect(stats.pending).toBe(1);
      expect(stats.uploading).toBe(1);
      expect(stats.failed).toBe(1);
      expect(stats.completed).toBe(1);
    });
  });

  describe('clearCompleted', () => {
    it('should remove all completed items', async () => {
      const mockQueue: QueueItem[] = [
        {
          id: '1',
          type: 'audio',
          uri: 'file:///test/audio1.m4a',
          status: 'completed',
          metadata: { sphereId: 'sphere-1', tags: [], retryCount: 0 },
          createdAt: new Date().toISOString(),
          updatedAt: new Date().toISOString(),
        },
        {
          id: '2',
          type: 'audio',
          uri: 'file:///test/audio2.m4a',
          status: 'pending',
          metadata: { sphereId: 'sphere-1', tags: [], retryCount: 0 },
          createdAt: new Date().toISOString(),
          updatedAt: new Date().toISOString(),
        },
        {
          id: '3',
          type: 'audio',
          uri: 'file:///test/audio3.m4a',
          status: 'completed',
          metadata: { sphereId: 'sphere-1', tags: [], retryCount: 0 },
          createdAt: new Date().toISOString(),
          updatedAt: new Date().toISOString(),
        },
      ];

      (AsyncStorage.getItem as jest.Mock).mockResolvedValue(JSON.stringify(mockQueue));

      await offlineQueueService.clearCompleted();

      const savedQueue = JSON.parse((AsyncStorage.setItem as jest.Mock).mock.calls[0][1]);
      expect(savedQueue).toHaveLength(1);
      expect(savedQueue[0].status).toBe('pending');
    });
  });

  describe('clearAll', () => {
    it('should remove all items from queue', async () => {
      await offlineQueueService.clearAll();

      expect(AsyncStorage.removeItem).toHaveBeenCalledWith('offline_queue');
    });
  });
});
