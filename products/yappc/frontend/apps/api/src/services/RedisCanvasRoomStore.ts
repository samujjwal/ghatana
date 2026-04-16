/**
 * Redis-backed Canvas Room Store
 *
 * Replaces in-memory storage with Redis for horizontal scaling support.
 * Canvas room state, presence, and collaboration data are persisted in Redis
 * with configurable TTL for automatic cleanup.
 *
 * @doc.type class
 * @doc.purpose Redis-backed storage for canvas collaboration rooms
 * @doc.layer infrastructure
 * @doc.pattern Repository
 */
import Redis from 'ioredis';

export interface CanvasRoomData {
  projectId: string;
  collaborators: Record<string, CollaboratorInfo>;
  lastActivity: number;
}

export interface CollaboratorInfo {
  id: string;
  name: string;
  email: string;
  color: string;
  cursor: { x: number; y: number } | null;
  selectedNodeIds: string[];
  lastActive: number;
}

export class RedisCanvasRoomStore {
  private redis: Redis;
  private readonly keyPrefix = 'yappc:canvas:room:';
  private readonly defaultTtl: number = 3600; // 1 hour in seconds

  constructor() {
    const redisUrl = process.env.REDIS_URL || 'redis://localhost:6379';
    this.redis = new Redis(redisUrl, {
      maxRetriesPerRequest: 3,
      retryDelayOnFailover: 100,
      lazyConnect: true,
    });

    this.redis.on('error', (err) => {
      console.error('[RedisCanvasRoomStore] Redis connection error:', err);
    });

    this.redis.on('connect', () => {
      console.info('[RedisCanvasRoomStore] Redis connected');
    });
  }

  /**
   * Initialize Redis connection
   */
  async connect(): Promise<void> {
    await this.redis.connect();
  }

  /**
   * Close Redis connection
   */
  async disconnect(): Promise<void> {
    await this.redis.quit();
  }

  /**
   * Get canvas room data by project ID
   */
  async getRoom(projectId: string): Promise<CanvasRoomData | null> {
    const key = this.getRoomKey(projectId);
    const data = await this.redis.get(key);
    
    if (!data) {
      return null;
    }

    try {
      return JSON.parse(data) as CanvasRoomData;
    } catch (err) {
      console.error(`[RedisCanvasRoomStore] Failed to parse room data for ${projectId}:`, err);
      return null;
    }
  }

  /**
   * Set canvas room data with TTL
   */
  async setRoom(projectId: string, data: CanvasRoomData, ttl?: number): Promise<void> {
    const key = this.getRoomKey(projectId);
    const serialized = JSON.stringify(data);
    const expiry = ttl ?? this.defaultTtl;
    
    await this.redis.setex(key, expiry, serialized);
  }

  /**
   * Update or add a collaborator to a room
   */
  async addCollaborator(projectId: string, collaborator: CollaboratorInfo): Promise<void> {
    const room = await this.getRoom(projectId);
    
    if (!room) {
      // Create new room if it doesn't exist
      const newRoom: CanvasRoomData = {
        projectId,
        collaborators: { [collaborator.id]: collaborator },
        lastActivity: Date.now(),
      };
      await this.setRoom(projectId, newRoom);
    } else {
      // Update existing room
      room.collaborators[collaborator.id] = collaborator;
      room.lastActivity = Date.now();
      await this.setRoom(projectId, room);
    }
  }

  /**
   * Remove a collaborator from a room
   */
  async removeCollaborator(projectId: string, collaboratorId: string): Promise<void> {
    const room = await this.getRoom(projectId);
    
    if (room) {
      delete room.collaborators[collaboratorId];
      room.lastActivity = Date.now();
      
      if (Object.keys(room.collaborators).length === 0) {
        // Delete room if no collaborators left
        await this.deleteRoom(projectId);
      } else {
        await this.setRoom(projectId, room);
      }
    }
  }

  /**
   * Update collaborator cursor position
   */
  async updateCursor(
    projectId: string,
    collaboratorId: string,
    cursor: { x: number; y: number }
  ): Promise<void> {
    const room = await this.getRoom(projectId);
    
    if (room && room.collaborators[collaboratorId]) {
      room.collaborators[collaboratorId].cursor = cursor;
      room.collaborators[collaboratorId].lastActive = Date.now();
      room.lastActivity = Date.now();
      await this.setRoom(projectId, room);
    }
  }

  /**
   * Update collaborator selection
   */
  async updateSelection(
    projectId: string,
    collaboratorId: string,
    selectedNodeIds: string[]
  ): Promise<void> {
    const room = await this.getRoom(projectId);
    
    if (room && room.collaborators[collaboratorId]) {
      room.collaborators[collaboratorId].selectedNodeIds = selectedNodeIds;
      room.collaborators[collaboratorId].lastActive = Date.now();
      room.lastActivity = Date.now();
      await this.setRoom(projectId, room);
    }
  }

  /**
   * Delete a room
   */
  async deleteRoom(projectId: string): Promise<void> {
    const key = this.getRoomKey(projectId);
    await this.redis.del(key);
  }

  /**
   * Get all active rooms (for cleanup/debugging)
   */
  async getAllRooms(): Promise<CanvasRoomData[]> {
    const pattern = `${this.keyPrefix}*`;
    const keys = await this.redis.keys(pattern);
    
    if (keys.length === 0) {
      return [];
    }

    const rooms: CanvasRoomData[] = [];
    for (const key of keys) {
      const data = await this.redis.get(key);
      if (data) {
        try {
          rooms.push(JSON.parse(data) as CanvasRoomData);
        } catch (err) {
          console.error(`[RedisCanvasRoomStore] Failed to parse room data:`, err);
        }
      }
    }
    
    return rooms;
  }

  /**
   * Clean up inactive rooms older than specified milliseconds
   */
  async cleanupInactiveRooms(maxAgeMs: number): Promise<number> {
    const rooms = await this.getAllRooms();
    const now = Date.now();
    let deleted = 0;

    for (const room of rooms) {
      if (now - room.lastActivity > maxAgeMs) {
        await this.deleteRoom(room.projectId);
        deleted++;
      }
    }

    return deleted;
  }

  /**
   * Check if Redis is connected and healthy
   */
  async healthCheck(): Promise<boolean> {
    try {
      await this.redis.ping();
      return true;
    } catch (err) {
      console.error('[RedisCanvasRoomStore] Health check failed:', err);
      return false;
    }
  }

  private getRoomKey(projectId: string): string {
    return `${this.keyPrefix}${projectId}`;
  }
}

// Export singleton instance
export const redisCanvasRoomStore = new RedisCanvasRoomStore();
