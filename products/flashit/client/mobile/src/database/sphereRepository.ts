/**
 * FlashIt Mobile - Sphere Repository
 *
 * CRUD operations for spheres with offline support.
 *
 * @doc.type repository
 * @doc.purpose Sphere data access layer
 * @doc.layer product
 * @doc.pattern Repository
 */

import { database, SphereRecord } from './database';
import { v4 as uuid } from 'uuid';

/**
 * Create sphere input.
 */
export interface CreateSphereInput {
  userId: string;
  name: string;
  description?: string;
  color?: string;
  icon?: string;
  isDefault?: boolean;
}

/**
 * Update sphere input.
 */
export interface UpdateSphereInput {
  name?: string;
  description?: string;
  color?: string;
  icon?: string;
}

/**
 * Sphere with parsed fields.
 */
export interface Sphere {
  id: string;
  userId: string;
  name: string;
  description: string | null;
  color: string | null;
  icon: string | null;
  isDefault: boolean;
  isShared: boolean;
  memberCount: number;
  momentCount: number;
  createdAt: string;
  updatedAt: string;
  serverId: string | null;
  syncStatus: 'synced' | 'pending' | 'error';
}

/**
 * Convert database row to Sphere.
 */
function rowToSphere(row: any): Sphere {
  return {
    id: row.id,
    userId: row.user_id,
    name: row.name,
    description: row.description,
    color: row.color,
    icon: row.icon,
    isDefault: row.is_default === 1,
    isShared: row.is_shared === 1,
    memberCount: row.member_count,
    momentCount: row.moment_count,
    createdAt: row.created_at,
    updatedAt: row.updated_at,
    serverId: row.server_id,
    syncStatus: row.sync_status,
  };
}

/**
 * Sphere Repository class.
 */
class SphereRepository {
  /**
   * Create a new sphere.
   */
  async create(input: CreateSphereInput): Promise<Sphere> {
    const id = uuid();
    const now = new Date().toISOString();

    await database.execute(
      `INSERT INTO spheres (
        id, user_id, name, description, color, icon,
        is_default, is_shared, member_count, moment_count,
        created_at, updated_at, sync_status
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
      [
        id,
        input.userId,
        input.name,
        input.description || null,
        input.color || null,
        input.icon || null,
        input.isDefault ? 1 : 0,
        0,
        1,
        0,
        now,
        now,
        'pending',
      ]
    );

    await this.queueSync('create', id, input);
    return this.getById(id) as Promise<Sphere>;
  }

  /**
   * Get a sphere by ID.
   */
  async getById(id: string): Promise<Sphere | null> {
    const row = await database.queryOne('SELECT * FROM spheres WHERE id = ?', [id]);
    return row ? rowToSphere(row) : null;
  }

  /**
   * Get all spheres for a user.
   */
  async getAllForUser(userId: string): Promise<Sphere[]> {
    const rows = await database.query(
      'SELECT * FROM spheres WHERE user_id = ? ORDER BY is_default DESC, name',
      [userId]
    );
    return rows.map(rowToSphere);
  }

  /**
   * Get default sphere for a user.
   */
  async getDefault(userId: string): Promise<Sphere | null> {
    const row = await database.queryOne(
      'SELECT * FROM spheres WHERE user_id = ? AND is_default = 1',
      [userId]
    );
    return row ? rowToSphere(row) : null;
  }

  /**
   * Update a sphere.
   */
  async update(id: string, input: UpdateSphereInput): Promise<Sphere | null> {
    const sphere = await this.getById(id);
    if (!sphere) return null;

    const updates: string[] = ['updated_at = ?', 'sync_status = ?'];
    const params: any[] = [new Date().toISOString(), 'pending'];

    if (input.name !== undefined) {
      updates.push('name = ?');
      params.push(input.name);
    }

    if (input.description !== undefined) {
      updates.push('description = ?');
      params.push(input.description);
    }

    if (input.color !== undefined) {
      updates.push('color = ?');
      params.push(input.color);
    }

    if (input.icon !== undefined) {
      updates.push('icon = ?');
      params.push(input.icon);
    }

    params.push(id);

    await database.execute(
      `UPDATE spheres SET ${updates.join(', ')} WHERE id = ?`,
      params
    );

    await this.queueSync('update', id, input);
    return this.getById(id);
  }

  /**
   * Delete a sphere.
   */
  async delete(id: string): Promise<boolean> {
    const sphere = await this.getById(id);
    if (!sphere || sphere.isDefault) return false;

    await database.execute('DELETE FROM spheres WHERE id = ?', [id]);

    if (sphere.serverId) {
      await this.queueSync('delete', id, { serverId: sphere.serverId });
    }

    return true;
  }

  /**
   * Set a sphere as default.
   */
  async setDefault(userId: string, sphereId: string): Promise<void> {
    await database.execute(
      'UPDATE spheres SET is_default = 0 WHERE user_id = ?',
      [userId]
    );
    await database.execute(
      'UPDATE spheres SET is_default = 1, sync_status = ? WHERE id = ?',
      ['pending', sphereId]
    );
  }

  /**
   * Mark sphere as synced.
   */
  async markSynced(id: string, serverId: string): Promise<void> {
    await database.execute(
      `UPDATE spheres SET sync_status = 'synced', server_id = ? WHERE id = ?`,
      [serverId, id]
    );
  }

  /**
   * Get spheres pending sync.
   */
  async getPendingSync(): Promise<Sphere[]> {
    const rows = await database.query(
      "SELECT * FROM spheres WHERE sync_status = 'pending'"
    );
    return rows.map(rowToSphere);
  }

  /**
   * Import sphere from server.
   */
  async importFromServer(serverSphere: any): Promise<Sphere> {
    const existing = await database.queryOne<any>(
      'SELECT * FROM spheres WHERE server_id = ?',
      [serverSphere.id]
    );

    if (existing) {
      await database.execute(
        `UPDATE spheres SET
          name = ?, description = ?, color = ?, icon = ?,
          is_shared = ?, member_count = ?, moment_count = ?,
          updated_at = ?, sync_status = 'synced'
        WHERE id = ?`,
        [
          serverSphere.name,
          serverSphere.description || null,
          serverSphere.color || null,
          serverSphere.icon || null,
          serverSphere.isShared ? 1 : 0,
          serverSphere.memberCount || 1,
          serverSphere.momentCount || 0,
          serverSphere.updatedAt || new Date().toISOString(),
          existing.id,
        ]
      );
      return this.getById(existing.id) as Promise<Sphere>;
    }

    const id = uuid();
    const now = new Date().toISOString();

    await database.execute(
      `INSERT INTO spheres (
        id, user_id, name, description, color, icon,
        is_default, is_shared, member_count, moment_count,
        created_at, updated_at, server_id, sync_status
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
      [
        id,
        serverSphere.userId,
        serverSphere.name,
        serverSphere.description || null,
        serverSphere.color || null,
        serverSphere.icon || null,
        serverSphere.isDefault ? 1 : 0,
        serverSphere.isShared ? 1 : 0,
        serverSphere.memberCount || 1,
        serverSphere.momentCount || 0,
        serverSphere.createdAt || now,
        serverSphere.updatedAt || now,
        serverSphere.id,
        'synced',
      ]
    );

    return this.getById(id) as Promise<Sphere>;
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
    const idempotencyKey = `${operationType}-sphere-${entityId}-${Date.now()}`;

    await database.execute(
      `INSERT INTO sync_queue (
        id, operation_type, entity_type, entity_id, payload_json,
        priority, retry_count, max_retries, idempotency_key, created_at, updated_at
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
      [
        id,
        operationType,
        'sphere',
        entityId,
        JSON.stringify(payload),
        2, // Spheres have higher priority than moments
        0,
        5,
        idempotencyKey,
        now,
        now,
      ]
    );
  }
}

// Export singleton instance
export const sphereRepository = new SphereRepository();
export default sphereRepository;
