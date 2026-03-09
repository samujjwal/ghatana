/**
 * FlashIt Mobile - Moment Repository
 *
 * CRUD operations for moments with offline support and sync tracking.
 * Follows repository pattern for clean data access.
 *
 * @doc.type repository
 * @doc.purpose Moment data access layer
 * @doc.layer product
 * @doc.pattern Repository
 */

import { database, MomentRecord } from './database';
import { v4 as uuid } from 'uuid';

/**
 * Create moment input.
 */
export interface CreateMomentInput {
  userId: string;
  sphereId?: string;
  content?: string;
  contentType: 'text' | 'voice' | 'image' | 'video';
  emotion?: string;
  energyLevel?: number;
  tags?: string[];
  mediaUrls?: string[];
  transcription?: string;
  locationLat?: number;
  locationLng?: number;
  locationName?: string;
  isPrivate?: boolean;
}

/**
 * Update moment input.
 */
export interface UpdateMomentInput {
  content?: string;
  emotion?: string;
  energyLevel?: number;
  tags?: string[];
  transcription?: string;
  transcriptionStatus?: 'none' | 'pending' | 'completed' | 'failed';
  isPrivate?: boolean;
  isPinned?: boolean;
}

/**
 * Moment filter options.
 */
export interface MomentFilterOptions {
  userId?: string;
  sphereId?: string;
  contentType?: 'text' | 'voice' | 'image' | 'video';
  emotion?: string;
  syncStatus?: 'synced' | 'pending' | 'error';
  fromDate?: string;
  toDate?: string;
  isPinned?: boolean;
  search?: string;
  limit?: number;
  offset?: number;
}

/**
 * Moment with parsed JSON fields.
 */
export interface Moment extends Omit<MomentRecord, 'tags' | 'mediaUrls'> {
  tags: string[];
  mediaUrls: string[];
}

/**
 * Convert database row to Moment.
 */
function rowToMoment(row: any): Moment {
  return {
    id: row.id,
    userId: row.user_id,
    sphereId: row.sphere_id,
    content: row.content,
    contentType: row.content_type,
    emotion: row.emotion,
    energyLevel: row.energy_level,
    tags: JSON.parse(row.tags || '[]'),
    mediaUrls: JSON.parse(row.media_urls || '[]'),
    transcription: row.transcription,
    transcriptionStatus: row.transcription_status,
    locationLat: row.location_lat,
    locationLng: row.location_lng,
    locationName: row.location_name,
    isPrivate: row.is_private === 1,
    isPinned: row.is_pinned === 1,
    createdAt: row.created_at,
    updatedAt: row.updated_at,
    serverId: row.server_id,
    syncStatus: row.sync_status,
    syncError: row.sync_error,
    localVersion: row.local_version,
    serverVersion: row.server_version,
  };
}

/**
 * Moment Repository class.
 */
class MomentRepository {
  /**
   * Create a new moment.
   */
  async create(input: CreateMomentInput): Promise<Moment> {
    const id = uuid();
    const now = new Date().toISOString();
    const tags = JSON.stringify(input.tags || []);
    const mediaUrls = JSON.stringify(input.mediaUrls || []);

    await database.execute(
      `INSERT INTO moments (
        id, user_id, sphere_id, content, content_type,
        emotion, energy_level, tags, media_urls, transcription,
        transcription_status, location_lat, location_lng, location_name,
        is_private, is_pinned, created_at, updated_at,
        sync_status, local_version, server_version
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
      [
        id,
        input.userId,
        input.sphereId || null,
        input.content || null,
        input.contentType,
        input.emotion || null,
        input.energyLevel || null,
        tags,
        mediaUrls,
        input.transcription || null,
        input.transcription ? 'completed' : 'none',
        input.locationLat || null,
        input.locationLng || null,
        input.locationName || null,
        input.isPrivate !== false ? 1 : 0,
        0,
        now,
        now,
        'pending',
        1,
        0,
      ]
    );

    // Queue sync operation
    await this.queueSync('create', id, input);

    return this.getById(id) as Promise<Moment>;
  }

  /**
   * Get a moment by ID.
   */
  async getById(id: string): Promise<Moment | null> {
    const row = await database.queryOne(
      'SELECT * FROM moments WHERE id = ?',
      [id]
    );
    return row ? rowToMoment(row) : null;
  }

  /**
   * Get all moments with optional filters.
   */
  async getAll(options: MomentFilterOptions = {}): Promise<Moment[]> {
    const conditions: string[] = [];
    const params: any[] = [];

    if (options.userId) {
      conditions.push('user_id = ?');
      params.push(options.userId);
    }

    if (options.sphereId) {
      conditions.push('sphere_id = ?');
      params.push(options.sphereId);
    }

    if (options.contentType) {
      conditions.push('content_type = ?');
      params.push(options.contentType);
    }

    if (options.emotion) {
      conditions.push('emotion = ?');
      params.push(options.emotion);
    }

    if (options.syncStatus) {
      conditions.push('sync_status = ?');
      params.push(options.syncStatus);
    }

    if (options.fromDate) {
      conditions.push('created_at >= ?');
      params.push(options.fromDate);
    }

    if (options.toDate) {
      conditions.push('created_at <= ?');
      params.push(options.toDate);
    }

    if (options.isPinned !== undefined) {
      conditions.push('is_pinned = ?');
      params.push(options.isPinned ? 1 : 0);
    }

    let sql = 'SELECT * FROM moments';
    if (conditions.length > 0) {
      sql += ' WHERE ' + conditions.join(' AND ');
    }

    sql += ' ORDER BY created_at DESC';

    if (options.limit) {
      sql += ' LIMIT ?';
      params.push(options.limit);
    }

    if (options.offset) {
      sql += ' OFFSET ?';
      params.push(options.offset);
    }

    const rows = await database.query(sql, params);
    return rows.map(rowToMoment);
  }

  /**
   * Update a moment.
   */
  async update(id: string, input: UpdateMomentInput): Promise<Moment | null> {
    const moment = await this.getById(id);
    if (!moment) return null;

    const updates: string[] = ['updated_at = ?', 'local_version = local_version + 1', 'sync_status = ?'];
    const params: any[] = [new Date().toISOString(), 'pending'];

    if (input.content !== undefined) {
      updates.push('content = ?');
      params.push(input.content);
    }

    if (input.emotion !== undefined) {
      updates.push('emotion = ?');
      params.push(input.emotion);
    }

    if (input.energyLevel !== undefined) {
      updates.push('energy_level = ?');
      params.push(input.energyLevel);
    }

    if (input.tags !== undefined) {
      updates.push('tags = ?');
      params.push(JSON.stringify(input.tags));
    }

    if (input.transcription !== undefined) {
      updates.push('transcription = ?');
      params.push(input.transcription);
    }

    if (input.transcriptionStatus !== undefined) {
      updates.push('transcription_status = ?');
      params.push(input.transcriptionStatus);
    }

    if (input.isPrivate !== undefined) {
      updates.push('is_private = ?');
      params.push(input.isPrivate ? 1 : 0);
    }

    if (input.isPinned !== undefined) {
      updates.push('is_pinned = ?');
      params.push(input.isPinned ? 1 : 0);
    }

    params.push(id);

    await database.execute(
      `UPDATE moments SET ${updates.join(', ')} WHERE id = ?`,
      params
    );

    // Queue sync operation
    await this.queueSync('update', id, input);

    return this.getById(id);
  }

  /**
   * Delete a moment.
   */
  async delete(id: string): Promise<boolean> {
    const moment = await this.getById(id);
    if (!moment) return false;

    await database.execute('DELETE FROM moments WHERE id = ?', [id]);

    // Queue sync operation if it was synced
    if (moment.serverId) {
      await this.queueSync('delete', id, { serverId: moment.serverId });
    }

    return true;
  }

  /**
   * Search moments using full-text search.
   */
  async search(query: string, options: MomentFilterOptions = {}): Promise<Moment[]> {
    // For now, use LIKE-based search
    // TODO: Implement FTS5 for better performance
    const searchTerm = `%${query}%`;
    const conditions: string[] = ['(content LIKE ? OR transcription LIKE ? OR tags LIKE ?)'];
    const params: any[] = [searchTerm, searchTerm, searchTerm];

    if (options.userId) {
      conditions.push('user_id = ?');
      params.push(options.userId);
    }

    if (options.sphereId) {
      conditions.push('sphere_id = ?');
      params.push(options.sphereId);
    }

    let sql = `SELECT * FROM moments WHERE ${conditions.join(' AND ')} ORDER BY created_at DESC`;

    if (options.limit) {
      sql += ' LIMIT ?';
      params.push(options.limit);
    }

    const rows = await database.query(sql, params);
    return rows.map(rowToMoment);
  }

  /**
   * Get moments pending sync.
   */
  async getPendingSync(): Promise<Moment[]> {
    const rows = await database.query(
      "SELECT * FROM moments WHERE sync_status = 'pending' ORDER BY created_at"
    );
    return rows.map(rowToMoment);
  }

  /**
   * Mark moment as synced.
   */
  async markSynced(id: string, serverId: string, serverVersion: number): Promise<void> {
    await database.execute(
      `UPDATE moments SET sync_status = 'synced', server_id = ?, server_version = ?, sync_error = NULL WHERE id = ?`,
      [serverId, serverVersion, id]
    );
  }

  /**
   * Mark moment sync as failed.
   */
  async markSyncError(id: string, error: string): Promise<void> {
    await database.execute(
      `UPDATE moments SET sync_status = 'error', sync_error = ? WHERE id = ?`,
      [error, id]
    );
  }

  /**
   * Get moment count by sync status.
   */
  async getCountBySyncStatus(): Promise<Record<string, number>> {
    const rows = await database.query<{ sync_status: string; count: number }>(
      'SELECT sync_status, COUNT(*) as count FROM moments GROUP BY sync_status'
    );
    return rows.reduce(
      (acc, row) => ({ ...acc, [row.sync_status]: row.count }),
      {} as Record<string, number>
    );
  }

  /**
   * Get moment count by emotion.
   */
  async getCountByEmotion(): Promise<Record<string, number>> {
    const rows = await database.query<{ emotion: string; count: number }>(
      'SELECT emotion, COUNT(*) as count FROM moments WHERE emotion IS NOT NULL GROUP BY emotion'
    );
    return rows.reduce(
      (acc, row) => ({ ...acc, [row.emotion]: row.count }),
      {} as Record<string, number>
    );
  }

  /**
   * Get recent moments.
   */
  async getRecent(limit: number = 10): Promise<Moment[]> {
    return this.getAll({ limit });
  }

  /**
   * Get pinned moments.
   */
  async getPinned(userId: string): Promise<Moment[]> {
    return this.getAll({ userId, isPinned: true });
  }

  /**
   * Queue a sync operation.
   */
  private async queueSync(
    operationType: 'create' | 'update' | 'delete',
    entityId: string,
    payload: any
  ): Promise<void> {
    const id = uuid();
    const now = new Date().toISOString();
    const idempotencyKey = `${operationType}-moment-${entityId}-${Date.now()}`;

    await database.execute(
      `INSERT INTO sync_queue (
        id, operation_type, entity_type, entity_id, payload_json,
        priority, retry_count, max_retries, idempotency_key, created_at, updated_at
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
      [
        id,
        operationType,
        'moment',
        entityId,
        JSON.stringify(payload),
        operationType === 'delete' ? 0 : 1, // Deletes have lower priority
        0,
        5,
        idempotencyKey,
        now,
        now,
      ]
    );
  }

  /**
   * Import moment from server (for sync).
   */
  async importFromServer(serverMoment: any): Promise<Moment> {
    const existingByServerId = await database.queryOne<any>(
      'SELECT * FROM moments WHERE server_id = ?',
      [serverMoment.id]
    );

    if (existingByServerId) {
      // Update existing moment if server version is newer
      if (serverMoment.version > existingByServerId.server_version) {
        await this.updateFromServer(existingByServerId.id, serverMoment);
      }
      return this.getById(existingByServerId.id) as Promise<Moment>;
    }

    // Create new moment from server
    const id = uuid();
    const now = new Date().toISOString();

    await database.execute(
      `INSERT INTO moments (
        id, user_id, sphere_id, content, content_type,
        emotion, energy_level, tags, media_urls, transcription,
        transcription_status, is_private, created_at, updated_at,
        server_id, sync_status, local_version, server_version
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
      [
        id,
        serverMoment.userId,
        serverMoment.sphereId || null,
        serverMoment.content || null,
        serverMoment.contentType || 'text',
        serverMoment.emotion || null,
        serverMoment.energyLevel || null,
        JSON.stringify(serverMoment.tags || []),
        JSON.stringify(serverMoment.mediaUrls || []),
        serverMoment.transcription || null,
        serverMoment.transcriptionStatus || 'none',
        serverMoment.isPrivate !== false ? 1 : 0,
        serverMoment.createdAt || now,
        serverMoment.updatedAt || now,
        serverMoment.id,
        'synced',
        1,
        serverMoment.version || 1,
      ]
    );

    return this.getById(id) as Promise<Moment>;
  }

  /**
   * Update moment from server data.
   */
  private async updateFromServer(localId: string, serverMoment: any): Promise<void> {
    await database.execute(
      `UPDATE moments SET
        content = ?, emotion = ?, energy_level = ?, tags = ?,
        transcription = ?, transcription_status = ?, is_private = ?,
        updated_at = ?, sync_status = 'synced', server_version = ?
      WHERE id = ?`,
      [
        serverMoment.content || null,
        serverMoment.emotion || null,
        serverMoment.energyLevel || null,
        JSON.stringify(serverMoment.tags || []),
        serverMoment.transcription || null,
        serverMoment.transcriptionStatus || 'none',
        serverMoment.isPrivate !== false ? 1 : 0,
        serverMoment.updatedAt || new Date().toISOString(),
        serverMoment.version || 1,
        localId,
      ]
    );
  }
}

// Export singleton instance
export const momentRepository = new MomentRepository();
export default momentRepository;
