/**
 * FlashIt Mobile - SQLite Database Manager
 *
 * Database initialization, migrations, and connection management.
 * Reuses patterns from tutorputor-mobile SQLiteStorage.
 *
 * @doc.type module
 * @doc.purpose SQLite database manager for React Native
 * @doc.layer product
 * @doc.pattern Singleton
 */

import * as SQLite from 'expo-sqlite';
import * as FileSystem from 'expo-file-system/legacy';

// Database configuration
const DB_NAME = 'flashit.db';
const DB_VERSION = 1;

/**
 * Moment record from database.
 */
export interface MomentRecord {
  id: string;
  userId: string;
  sphereId: string | null;
  content: string | null;
  contentType: 'text' | 'voice' | 'image' | 'video';
  emotion: string | null;
  energyLevel: number | null;
  tags: string[];
  mediaUrls: string[];
  transcription: string | null;
  transcriptionStatus: 'none' | 'pending' | 'completed' | 'failed';
  locationLat: number | null;
  locationLng: number | null;
  locationName: string | null;
  isPrivate: boolean;
  isPinned: boolean;
  createdAt: string;
  updatedAt: string;
  serverId: string | null;
  syncStatus: 'synced' | 'pending' | 'error';
  syncError: string | null;
  localVersion: number;
  serverVersion: number;
}

/**
 * Sphere record from database.
 */
export interface SphereRecord {
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
 * Media file record from database.
 */
export interface MediaFileRecord {
  id: string;
  momentId: string | null;
  fileType: 'image' | 'video' | 'audio';
  localUri: string;
  remoteUrl: string | null;
  fileSize: number;
  mimeType: string;
  width: number | null;
  height: number | null;
  durationMs: number | null;
  thumbnailUri: string | null;
  compressionStatus: 'none' | 'pending' | 'completed' | 'failed';
  uploadStatus: 'pending' | 'uploading' | 'completed' | 'failed';
  uploadProgress: number;
  createdAt: string;
}

/**
 * Sync queue record.
 */
export interface SyncQueueRecord {
  id: string;
  operationType: 'create' | 'update' | 'delete';
  entityType: 'moment' | 'sphere' | 'media';
  entityId: string;
  payloadJson: string;
  priority: number;
  retryCount: number;
  maxRetries: number;
  nextRetryAt: string | null;
  lastError: string | null;
  idempotencyKey: string;
  createdAt: string;
  updatedAt: string;
}

/**
 * Database query result row.
 */
type Row = Record<string, any>;

/**
 * Database migration.
 */
interface Migration {
  version: number;
  name: string;
  up: string;
}

/**
 * SQLite Database Manager.
 */
class DatabaseManager {
  private db: SQLite.SQLiteDatabase | null = null;
  private initPromise: Promise<void> | null = null;
  private listeners: Set<() => void> = new Set();

  /**
   * Initialize the database.
   */
  async init(): Promise<void> {
    if (this.initPromise) return this.initPromise;
    if (this.db) return;

    this.initPromise = this.initializeDatabase();
    await this.initPromise;
  }

  private async initializeDatabase(): Promise<void> {
    try {
      // Open database
      this.db = await SQLite.openDatabaseAsync(DB_NAME);

      // Enable foreign keys
      await this.db.execAsync('PRAGMA foreign_keys = ON;');

      // Create tables
      await this.createTables();

      // Run migrations
      await this.runMigrations();

      console.log('[Database] Initialized successfully');
    } catch (error) {
      console.error('[Database] Failed to initialize:', error);
      throw error;
    }
  }

  private async createTables(): Promise<void> {
    if (!this.db) return;

    await this.db.execAsync(`
      -- Moments table
      CREATE TABLE IF NOT EXISTS moments (
        id TEXT PRIMARY KEY,
        user_id TEXT NOT NULL,
        sphere_id TEXT,
        content TEXT,
        content_type TEXT NOT NULL DEFAULT 'text',
        emotion TEXT,
        energy_level REAL,
        tags TEXT NOT NULL DEFAULT '[]',
        media_urls TEXT NOT NULL DEFAULT '[]',
        transcription TEXT,
        transcription_status TEXT DEFAULT 'none',
        location_lat REAL,
        location_lng REAL,
        location_name TEXT,
        is_private INTEGER NOT NULL DEFAULT 1,
        is_pinned INTEGER NOT NULL DEFAULT 0,
        created_at TEXT NOT NULL,
        updated_at TEXT NOT NULL,
        server_id TEXT,
        sync_status TEXT NOT NULL DEFAULT 'pending',
        sync_error TEXT,
        local_version INTEGER NOT NULL DEFAULT 1,
        server_version INTEGER DEFAULT 0
      );

      -- Spheres table
      CREATE TABLE IF NOT EXISTS spheres (
        id TEXT PRIMARY KEY,
        user_id TEXT NOT NULL,
        name TEXT NOT NULL,
        description TEXT,
        color TEXT,
        icon TEXT,
        is_default INTEGER NOT NULL DEFAULT 0,
        is_shared INTEGER NOT NULL DEFAULT 0,
        member_count INTEGER NOT NULL DEFAULT 1,
        moment_count INTEGER NOT NULL DEFAULT 0,
        created_at TEXT NOT NULL,
        updated_at TEXT NOT NULL,
        server_id TEXT,
        sync_status TEXT NOT NULL DEFAULT 'pending'
      );

      -- Media files table
      CREATE TABLE IF NOT EXISTS media_files (
        id TEXT PRIMARY KEY,
        moment_id TEXT,
        file_type TEXT NOT NULL,
        local_uri TEXT NOT NULL,
        remote_url TEXT,
        file_size INTEGER NOT NULL,
        mime_type TEXT NOT NULL,
        width INTEGER,
        height INTEGER,
        duration_ms INTEGER,
        thumbnail_uri TEXT,
        compression_status TEXT DEFAULT 'none',
        upload_status TEXT NOT NULL DEFAULT 'pending',
        upload_progress REAL DEFAULT 0,
        created_at TEXT NOT NULL,
        FOREIGN KEY (moment_id) REFERENCES moments(id) ON DELETE CASCADE
      );

      -- Sync queue table
      CREATE TABLE IF NOT EXISTS sync_queue (
        id TEXT PRIMARY KEY,
        operation_type TEXT NOT NULL,
        entity_type TEXT NOT NULL,
        entity_id TEXT NOT NULL,
        payload_json TEXT NOT NULL,
        priority INTEGER NOT NULL DEFAULT 0,
        retry_count INTEGER NOT NULL DEFAULT 0,
        max_retries INTEGER NOT NULL DEFAULT 5,
        next_retry_at TEXT,
        last_error TEXT,
        idempotency_key TEXT NOT NULL UNIQUE,
        created_at TEXT NOT NULL,
        updated_at TEXT NOT NULL
      );

      -- Settings table
      CREATE TABLE IF NOT EXISTS settings (
        key TEXT PRIMARY KEY,
        value TEXT NOT NULL,
        updated_at TEXT NOT NULL
      );

      -- Indexes
      CREATE INDEX IF NOT EXISTS idx_moments_user ON moments(user_id);
      CREATE INDEX IF NOT EXISTS idx_moments_sphere ON moments(sphere_id);
      CREATE INDEX IF NOT EXISTS idx_moments_sync ON moments(sync_status);
      CREATE INDEX IF NOT EXISTS idx_moments_created ON moments(created_at DESC);
      CREATE INDEX IF NOT EXISTS idx_moments_emotion ON moments(emotion);
      CREATE INDEX IF NOT EXISTS idx_spheres_user ON spheres(user_id);
      CREATE INDEX IF NOT EXISTS idx_media_moment ON media_files(moment_id);
      CREATE INDEX IF NOT EXISTS idx_media_upload ON media_files(upload_status);
      CREATE INDEX IF NOT EXISTS idx_sync_priority ON sync_queue(priority DESC, created_at);
    `);
  }

  /**
   * Get all migrations.
   */
  private getMigrations(): Migration[] {
    return [
      {
        version: 1,
        name: 'initial_schema',
        up: `
          -- Initial schema created in createTables
          INSERT OR IGNORE INTO settings (key, value, updated_at) VALUES
            ('db_version', '1', datetime('now')),
            ('last_sync_at', '', datetime('now'));
        `,
      },
      // Future migrations go here
      // {
      //   version: 2,
      //   name: 'add_fts_search',
      //   up: `CREATE VIRTUAL TABLE IF NOT EXISTS moments_fts USING fts5(...);`,
      // },
    ];
  }

  /**
   * Run pending migrations.
   */
  private async runMigrations(): Promise<void> {
    if (!this.db) return;

    const currentVersion = await this.getSetting('db_version');
    const version = currentVersion ? parseInt(currentVersion, 10) : 0;

    const migrations = this.getMigrations().filter((m) => m.version > version);

    for (const migration of migrations) {
      console.log(`[Database] Running migration: ${migration.name}`);
      await this.db.execAsync(migration.up);
      await this.setSetting('db_version', migration.version.toString());
    }
  }

  /**
   * Get a setting value.
   */
  async getSetting(key: string): Promise<string | null> {
    if (!this.db) await this.init();

    const result = await this.db!.getFirstAsync<{ value: string }>(
      'SELECT value FROM settings WHERE key = ?',
      [key]
    );
    return result?.value ?? null;
  }

  /**
   * Set a setting value.
   */
  async setSetting(key: string, value: string): Promise<void> {
    if (!this.db) await this.init();

    await this.db!.runAsync(
      `INSERT OR REPLACE INTO settings (key, value, updated_at) VALUES (?, ?, datetime('now'))`,
      [key, value]
    );
  }

  /**
   * Execute a raw SQL query.
   */
  async execute(sql: string, params: any[] = []): Promise<void> {
    if (!this.db) await this.init();
    await this.db!.runAsync(sql, params);
    this.notifyListeners();
  }

  /**
   * Query rows from the database.
   */
  async query<T = Row>(sql: string, params: any[] = []): Promise<T[]> {
    if (!this.db) await this.init();
    return this.db!.getAllAsync<T>(sql, params);
  }

  /**
   * Query a single row.
   */
  async queryOne<T = Row>(sql: string, params: any[] = []): Promise<T | null> {
    if (!this.db) await this.init();
    return this.db!.getFirstAsync<T>(sql, params);
  }

  /**
   * Subscribe to database changes.
   */
  subscribe(listener: () => void): () => void {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }

  private notifyListeners(): void {
    this.listeners.forEach((listener) => listener());
  }

  /**
   * Close the database connection.
   */
  async close(): Promise<void> {
    if (this.db) {
      await this.db.closeAsync();
      this.db = null;
      this.initPromise = null;
    }
  }

  /**
   * Get database file path.
   */
  getDatabasePath(): string {
    return `${FileSystem.documentDirectory}SQLite/${DB_NAME}`;
  }

  /**
   * Get database size in bytes.
   */
  async getDatabaseSize(): Promise<number> {
    try {
      const info = await FileSystem.getInfoAsync(this.getDatabasePath());
      return info.exists ? (info as any).size || 0 : 0;
    } catch {
      return 0;
    }
  }

  /**
   * Backup database to a file.
   */
  async backup(backupPath: string): Promise<void> {
    const sourcePath = this.getDatabasePath();
    await FileSystem.copyAsync({ from: sourcePath, to: backupPath });
    console.log(`[Database] Backed up to: ${backupPath}`);
  }

  /**
   * Restore database from a backup.
   */
  async restore(backupPath: string): Promise<void> {
    await this.close();
    const targetPath = this.getDatabasePath();
    await FileSystem.copyAsync({ from: backupPath, to: targetPath });
    await this.init();
    console.log(`[Database] Restored from: ${backupPath}`);
  }

  /**
   * Delete all data (for logout/reset).
   */
  async deleteAllData(): Promise<void> {
    if (!this.db) await this.init();

    await this.db!.execAsync(`
      DELETE FROM sync_queue;
      DELETE FROM media_files;
      DELETE FROM moments;
      DELETE FROM spheres;
      DELETE FROM settings WHERE key NOT IN ('db_version');
    `);

    this.notifyListeners();
    console.log('[Database] All data deleted');
  }
}

// Export singleton instance
export const database = new DatabaseManager();
export default database;
