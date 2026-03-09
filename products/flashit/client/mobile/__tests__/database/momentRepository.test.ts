/**
 * Tests for Moment Repository
 * @doc.type test-suite
 */

import { momentRepository } from '../../src/database/momentRepository';
import { database } from '../../src/database/database';

// Mock database
jest.mock('../../src/database/database', () => ({
  database: {
    init: jest.fn().mockResolvedValue(undefined),
    execute: jest.fn().mockResolvedValue(undefined),
    query: jest.fn().mockResolvedValue([]),
    queryOne: jest.fn().mockResolvedValue(null),
    subscribe: jest.fn().mockReturnValue(() => {}),
  },
}));

// Mock uuid
jest.mock('uuid', () => ({
  v4: jest.fn().mockReturnValue('test-uuid'),
}));

describe('MomentRepository', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  describe('create', () => {
    it('should create a moment with all required fields', async () => {
      const mockRow = {
        id: 'test-uuid',
        user_id: 'user-1',
        sphere_id: 'sphere-1',
        content: 'Test content',
        content_type: 'text',
        emotion: 'happy',
        energy_level: 0.8,
        tags: '["tag1","tag2"]',
        media_urls: '[]',
        transcription: null,
        transcription_status: 'none',
        location_lat: null,
        location_lng: null,
        location_name: null,
        is_private: 1,
        is_pinned: 0,
        created_at: '2025-01-01T00:00:00.000Z',
        updated_at: '2025-01-01T00:00:00.000Z',
        server_id: null,
        sync_status: 'pending',
        sync_error: null,
        local_version: 1,
        server_version: 0,
      };

      (database.queryOne as jest.Mock).mockResolvedValueOnce(mockRow);

      const result = await momentRepository.create({
        userId: 'user-1',
        sphereId: 'sphere-1',
        content: 'Test content',
        contentType: 'text',
        emotion: 'happy',
        energyLevel: 0.8,
        tags: ['tag1', 'tag2'],
      });

      expect(database.execute).toHaveBeenCalledTimes(2); // Insert + queue sync
      expect(result.id).toBe('test-uuid');
      expect(result.content).toBe('Test content');
      expect(result.tags).toEqual(['tag1', 'tag2']);
    });

    it('should queue a sync operation after creation', async () => {
      const mockRow = {
        id: 'test-uuid',
        user_id: 'user-1',
        content: 'Test',
        content_type: 'text',
        tags: '[]',
        media_urls: '[]',
        is_private: 1,
        is_pinned: 0,
        created_at: '2025-01-01T00:00:00.000Z',
        updated_at: '2025-01-01T00:00:00.000Z',
        sync_status: 'pending',
        local_version: 1,
        server_version: 0,
      };

      (database.queryOne as jest.Mock).mockResolvedValueOnce(mockRow);

      await momentRepository.create({
        userId: 'user-1',
        content: 'Test',
        contentType: 'text',
      });

      // Should insert into sync_queue
      const syncQueueCall = (database.execute as jest.Mock).mock.calls.find(
        (call) => call[0].includes('sync_queue')
      );
      expect(syncQueueCall).toBeTruthy();
    });
  });

  describe('getById', () => {
    it('should return null for non-existent moment', async () => {
      (database.queryOne as jest.Mock).mockResolvedValueOnce(null);

      const result = await momentRepository.getById('non-existent');

      expect(result).toBeNull();
    });

    it('should parse JSON fields correctly', async () => {
      (database.queryOne as jest.Mock).mockResolvedValueOnce({
        id: 'moment-1',
        user_id: 'user-1',
        content: 'Test',
        content_type: 'text',
        tags: '["tag1","tag2","tag3"]',
        media_urls: '["url1","url2"]',
        is_private: 0,
        is_pinned: 1,
        created_at: '2025-01-01T00:00:00.000Z',
        updated_at: '2025-01-01T00:00:00.000Z',
        sync_status: 'synced',
        local_version: 1,
        server_version: 1,
      });

      const result = await momentRepository.getById('moment-1');

      expect(result).not.toBeNull();
      expect(result!.tags).toEqual(['tag1', 'tag2', 'tag3']);
      expect(result!.mediaUrls).toEqual(['url1', 'url2']);
      expect(result!.isPrivate).toBe(false);
      expect(result!.isPinned).toBe(true);
    });
  });

  describe('getAll', () => {
    it('should filter by userId', async () => {
      await momentRepository.getAll({ userId: 'user-1' });

      expect(database.query).toHaveBeenCalledWith(
        expect.stringContaining('user_id = ?'),
        expect.arrayContaining(['user-1'])
      );
    });

    it('should filter by sphereId', async () => {
      await momentRepository.getAll({ sphereId: 'sphere-1' });

      expect(database.query).toHaveBeenCalledWith(
        expect.stringContaining('sphere_id = ?'),
        expect.arrayContaining(['sphere-1'])
      );
    });

    it('should filter by contentType', async () => {
      await momentRepository.getAll({ contentType: 'voice' });

      expect(database.query).toHaveBeenCalledWith(
        expect.stringContaining('content_type = ?'),
        expect.arrayContaining(['voice'])
      );
    });

    it('should filter by date range', async () => {
      await momentRepository.getAll({
        fromDate: '2025-01-01',
        toDate: '2025-01-31',
      });

      expect(database.query).toHaveBeenCalledWith(
        expect.stringContaining('created_at >= ?'),
        expect.arrayContaining(['2025-01-01'])
      );
      expect(database.query).toHaveBeenCalledWith(
        expect.stringContaining('created_at <= ?'),
        expect.arrayContaining(['2025-01-31'])
      );
    });

    it('should apply limit and offset', async () => {
      await momentRepository.getAll({ limit: 10, offset: 20 });

      expect(database.query).toHaveBeenCalledWith(
        expect.stringContaining('LIMIT ?'),
        expect.arrayContaining([10, 20])
      );
    });
  });

  describe('update', () => {
    it('should return null for non-existent moment', async () => {
      (database.queryOne as jest.Mock).mockResolvedValueOnce(null);

      const result = await momentRepository.update('non-existent', { content: 'Updated' });

      expect(result).toBeNull();
      expect(database.execute).not.toHaveBeenCalled();
    });

    it('should update only provided fields', async () => {
      const mockRow = {
        id: 'moment-1',
        user_id: 'user-1',
        content: 'Original',
        content_type: 'text',
        emotion: 'neutral',
        tags: '[]',
        media_urls: '[]',
        is_private: 1,
        is_pinned: 0,
        created_at: '2025-01-01T00:00:00.000Z',
        updated_at: '2025-01-01T00:00:00.000Z',
        sync_status: 'synced',
        local_version: 1,
        server_version: 1,
      };

      (database.queryOne as jest.Mock)
        .mockResolvedValueOnce(mockRow) // First call for existing check
        .mockResolvedValueOnce({ ...mockRow, content: 'Updated' }); // Second call for return

      await momentRepository.update('moment-1', { content: 'Updated' });

      expect(database.execute).toHaveBeenCalledWith(
        expect.stringContaining('UPDATE moments'),
        expect.arrayContaining(['Updated'])
      );
    });

    it('should queue sync operation after update', async () => {
      const mockRow = {
        id: 'moment-1',
        user_id: 'user-1',
        content: 'Test',
        content_type: 'text',
        tags: '[]',
        media_urls: '[]',
        is_private: 1,
        is_pinned: 0,
        created_at: '2025-01-01T00:00:00.000Z',
        updated_at: '2025-01-01T00:00:00.000Z',
        sync_status: 'synced',
        local_version: 1,
        server_version: 1,
      };

      (database.queryOne as jest.Mock)
        .mockResolvedValueOnce(mockRow)
        .mockResolvedValueOnce(mockRow);

      await momentRepository.update('moment-1', { isPinned: true });

      const syncQueueCall = (database.execute as jest.Mock).mock.calls.find(
        (call) => call[0].includes('sync_queue')
      );
      expect(syncQueueCall).toBeTruthy();
    });
  });

  describe('delete', () => {
    it('should return false for non-existent moment', async () => {
      (database.queryOne as jest.Mock).mockResolvedValueOnce(null);

      const result = await momentRepository.delete('non-existent');

      expect(result).toBe(false);
    });

    it('should delete moment and queue sync if synced', async () => {
      (database.queryOne as jest.Mock).mockResolvedValueOnce({
        id: 'moment-1',
        user_id: 'user-1',
        content: 'Test',
        content_type: 'text',
        tags: '[]',
        media_urls: '[]',
        is_private: 1,
        is_pinned: 0,
        created_at: '2025-01-01T00:00:00.000Z',
        updated_at: '2025-01-01T00:00:00.000Z',
        server_id: 'server-moment-1',
        sync_status: 'synced',
        local_version: 1,
        server_version: 1,
      });

      const result = await momentRepository.delete('moment-1');

      expect(result).toBe(true);
      expect(database.execute).toHaveBeenCalledWith(
        'DELETE FROM moments WHERE id = ?',
        ['moment-1']
      );
    });
  });

  describe('search', () => {
    it('should search content, transcription, and tags', async () => {
      await momentRepository.search('test query');

      expect(database.query).toHaveBeenCalledWith(
        expect.stringContaining('content LIKE ?'),
        expect.arrayContaining(['%test query%'])
      );
      expect(database.query).toHaveBeenCalledWith(
        expect.stringContaining('transcription LIKE ?'),
        expect.any(Array)
      );
    });

    it('should apply user filter to search', async () => {
      await momentRepository.search('test', { userId: 'user-1' });

      expect(database.query).toHaveBeenCalledWith(
        expect.stringContaining('user_id = ?'),
        expect.arrayContaining(['user-1'])
      );
    });
  });

  describe('getPendingSync', () => {
    it('should return moments with pending sync status', async () => {
      (database.query as jest.Mock).mockResolvedValueOnce([
        {
          id: 'moment-1',
          user_id: 'user-1',
          content: 'Test',
          content_type: 'text',
          tags: '[]',
          media_urls: '[]',
          is_private: 1,
          is_pinned: 0,
          created_at: '2025-01-01T00:00:00.000Z',
          updated_at: '2025-01-01T00:00:00.000Z',
          sync_status: 'pending',
          local_version: 1,
          server_version: 0,
        },
      ]);

      const result = await momentRepository.getPendingSync();

      expect(result).toHaveLength(1);
      expect(result[0].syncStatus).toBe('pending');
    });
  });

  describe('markSynced', () => {
    it('should update sync status to synced', async () => {
      await momentRepository.markSynced('moment-1', 'server-id-1', 2);

      expect(database.execute).toHaveBeenCalledWith(
        expect.stringContaining("sync_status = 'synced'"),
        expect.arrayContaining(['server-id-1', 2, 'moment-1'])
      );
    });
  });

  describe('getCountBySyncStatus', () => {
    it('should return counts grouped by sync status', async () => {
      (database.query as jest.Mock).mockResolvedValueOnce([
        { sync_status: 'synced', count: 10 },
        { sync_status: 'pending', count: 5 },
        { sync_status: 'error', count: 2 },
      ]);

      const result = await momentRepository.getCountBySyncStatus();

      expect(result.synced).toBe(10);
      expect(result.pending).toBe(5);
      expect(result.error).toBe(2);
    });
  });

  describe('importFromServer', () => {
    it('should create new moment from server data', async () => {
      (database.queryOne as jest.Mock)
        .mockResolvedValueOnce(null) // No existing moment
        .mockResolvedValueOnce({
          id: 'test-uuid',
          user_id: 'user-1',
          content: 'Server content',
          content_type: 'text',
          tags: '[]',
          media_urls: '[]',
          is_private: 1,
          is_pinned: 0,
          created_at: '2025-01-01T00:00:00.000Z',
          updated_at: '2025-01-01T00:00:00.000Z',
          server_id: 'server-moment-1',
          sync_status: 'synced',
          local_version: 1,
          server_version: 1,
        });

      const result = await momentRepository.importFromServer({
        id: 'server-moment-1',
        userId: 'user-1',
        content: 'Server content',
        contentType: 'text',
        createdAt: '2025-01-01T00:00:00.000Z',
        version: 1,
      });

      expect(result.serverId).toBe('server-moment-1');
      expect(result.syncStatus).toBe('synced');
    });

    it('should update existing moment if server version is newer', async () => {
      (database.queryOne as jest.Mock).mockResolvedValueOnce({
        id: 'local-moment-1',
        server_version: 1,
      });

      await momentRepository.importFromServer({
        id: 'server-moment-1',
        userId: 'user-1',
        content: 'Updated content',
        version: 2,
      });

      expect(database.execute).toHaveBeenCalledWith(
        expect.stringContaining('UPDATE moments'),
        expect.any(Array)
      );
    });
  });
});
