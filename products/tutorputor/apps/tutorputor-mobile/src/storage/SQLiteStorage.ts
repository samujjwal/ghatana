/**
 * TutorPutor Mobile - SQLite Database
 *
 * SQLite storage for structured data like progress and downloaded modules.
 * Provides better querying capabilities than key-value storage.
 *
 * @doc.type module
 * @doc.purpose SQLite storage for React Native
 * @doc.layer product
 * @doc.pattern Repository
 */

import SQLite, { SQLiteDatabase } from 'react-native-sqlite-storage';

// Enable promise-based API
SQLite.enablePromise(true);

/**
 * Database schema version.
 */
const DB_VERSION = 1;
const DB_NAME = 'tutorputor.db';

/**
 * Progress record for a module.
 */
export interface ProgressRecord {
  moduleId: string;
  progress: number;
  currentLesson: number;
  completedLessons: string[];
  quizScores: Record<string, number>;
  timeSpentMs: number;
  updatedAt: string;
  syncStatus: 'synced' | 'pending' | 'error';
}

/**
 * Downloaded module record.
 */
export interface ModuleRecord {
  id: string;
  title: string;
  description: string;
  category: string;
  grade: number;
  lessonsJson: string; // JSON stringified lessons
  quizzesJson: string; // JSON stringified quizzes
  downloadedAt: string;
  totalSizeBytes: number;
  version: string;
}

/**
 * Pending mutation record.
 */
export interface MutationRecord {
  id: string;
  type: string;
  payloadJson: string;
  createdAt: string;
  retryCount: number;
  maxRetries: number;
  lastError: string | null;
  idempotencyKey: string;
}

/**
 * SQLite database manager.
 */
class SQLiteDatabase {
  private db: SQLiteDatabase | null = null;
  private initPromise: Promise<void> | null = null;

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
      this.db = await SQLite.openDatabase({
        name: DB_NAME,
        location: 'default',
      });

      await this.createTables();
      await this.migrate();
    } catch (error) {
      console.error('[SQLite] Failed to initialize database:', error);
      throw error;
    }
  }

  private async createTables(): Promise<void> {
    if (!this.db) return;

    // Progress table
    await this.db.executeSql(`
      CREATE TABLE IF NOT EXISTS progress (
        module_id TEXT PRIMARY KEY,
        progress REAL NOT NULL DEFAULT 0,
        current_lesson INTEGER NOT NULL DEFAULT 0,
        completed_lessons TEXT NOT NULL DEFAULT '[]',
        quiz_scores TEXT NOT NULL DEFAULT '{}',
        time_spent_ms INTEGER NOT NULL DEFAULT 0,
        updated_at TEXT NOT NULL,
        sync_status TEXT NOT NULL DEFAULT 'synced'
      )
    `);

    // Modules table
    await this.db.executeSql(`
      CREATE TABLE IF NOT EXISTS modules (
        id TEXT PRIMARY KEY,
        title TEXT NOT NULL,
        description TEXT,
        category TEXT NOT NULL,
        grade INTEGER NOT NULL,
        lessons_json TEXT NOT NULL,
        quizzes_json TEXT NOT NULL,
        downloaded_at TEXT NOT NULL,
        total_size_bytes INTEGER NOT NULL DEFAULT 0,
        version TEXT NOT NULL
      )
    `);

    // Mutations table
    await this.db.executeSql(`
      CREATE TABLE IF NOT EXISTS mutations (
        id TEXT PRIMARY KEY,
        type TEXT NOT NULL,
        payload_json TEXT NOT NULL,
        created_at TEXT NOT NULL,
        retry_count INTEGER NOT NULL DEFAULT 0,
        max_retries INTEGER NOT NULL DEFAULT 3,
        last_error TEXT,
        idempotency_key TEXT NOT NULL UNIQUE
      )
    `);

    // Create indexes
    await this.db.executeSql(
      'CREATE INDEX IF NOT EXISTS idx_progress_sync ON progress(sync_status)'
    );
    await this.db.executeSql(
      'CREATE INDEX IF NOT EXISTS idx_modules_category ON modules(category)'
    );
    await this.db.executeSql(
      'CREATE INDEX IF NOT EXISTS idx_mutations_created ON mutations(created_at)'
    );
  }

  private async migrate(): Promise<void> {
    // Future migrations go here
    // Check current version and apply migrations as needed
  }

  // =========================================================================
  // Progress Operations
  // =========================================================================

  async getProgress(moduleId: string): Promise<ProgressRecord | null> {
    if (!this.db) await this.init();

    const [results] = await this.db!.executeSql(
      'SELECT * FROM progress WHERE module_id = ?',
      [moduleId]
    );

    if (results.rows.length === 0) return null;

    const row = results.rows.item(0);
    return {
      moduleId: row.module_id,
      progress: row.progress,
      currentLesson: row.current_lesson,
      completedLessons: JSON.parse(row.completed_lessons),
      quizScores: JSON.parse(row.quiz_scores),
      timeSpentMs: row.time_spent_ms,
      updatedAt: row.updated_at,
      syncStatus: row.sync_status,
    };
  }

  async saveProgress(record: ProgressRecord): Promise<void> {
    if (!this.db) await this.init();

    await this.db!.executeSql(
      `INSERT OR REPLACE INTO progress 
       (module_id, progress, current_lesson, completed_lessons, quiz_scores, time_spent_ms, updated_at, sync_status)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?)`,
      [
        record.moduleId,
        record.progress,
        record.currentLesson,
        JSON.stringify(record.completedLessons),
        JSON.stringify(record.quizScores),
        record.timeSpentMs,
        record.updatedAt,
        record.syncStatus,
      ]
    );
  }

  async getPendingProgress(): Promise<ProgressRecord[]> {
    if (!this.db) await this.init();

    const [results] = await this.db!.executeSql(
      "SELECT * FROM progress WHERE sync_status = 'pending'"
    );

    const records: ProgressRecord[] = [];
    for (let i = 0; i < results.rows.length; i++) {
      const row = results.rows.item(i);
      records.push({
        moduleId: row.module_id,
        progress: row.progress,
        currentLesson: row.current_lesson,
        completedLessons: JSON.parse(row.completed_lessons),
        quizScores: JSON.parse(row.quiz_scores),
        timeSpentMs: row.time_spent_ms,
        updatedAt: row.updated_at,
        syncStatus: row.sync_status,
      });
    }

    return records;
  }

  async markProgressSynced(moduleId: string): Promise<void> {
    if (!this.db) await this.init();

    await this.db!.executeSql(
      "UPDATE progress SET sync_status = 'synced' WHERE module_id = ?",
      [moduleId]
    );
  }

  // =========================================================================
  // Module Operations
  // =========================================================================

  async getModule(moduleId: string): Promise<ModuleRecord | null> {
    if (!this.db) await this.init();

    const [results] = await this.db!.executeSql(
      'SELECT * FROM modules WHERE id = ?',
      [moduleId]
    );

    if (results.rows.length === 0) return null;

    const row = results.rows.item(0);
    return this.rowToModuleRecord(row);
  }

  async getAllModules(): Promise<ModuleRecord[]> {
    if (!this.db) await this.init();

    const [results] = await this.db!.executeSql('SELECT * FROM modules');

    const records: ModuleRecord[] = [];
    for (let i = 0; i < results.rows.length; i++) {
      records.push(this.rowToModuleRecord(results.rows.item(i)));
    }

    return records;
  }

  async saveModule(record: ModuleRecord): Promise<void> {
    if (!this.db) await this.init();

    await this.db!.executeSql(
      `INSERT OR REPLACE INTO modules 
       (id, title, description, category, grade, lessons_json, quizzes_json, downloaded_at, total_size_bytes, version)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
      [
        record.id,
        record.title,
        record.description,
        record.category,
        record.grade,
        record.lessonsJson,
        record.quizzesJson,
        record.downloadedAt,
        record.totalSizeBytes,
        record.version,
      ]
    );
  }

  async deleteModule(moduleId: string): Promise<void> {
    if (!this.db) await this.init();

    await this.db!.executeSql('DELETE FROM modules WHERE id = ?', [moduleId]);
  }

  private rowToModuleRecord(row: any): ModuleRecord {
    return {
      id: row.id,
      title: row.title,
      description: row.description,
      category: row.category,
      grade: row.grade,
      lessonsJson: row.lessons_json,
      quizzesJson: row.quizzes_json,
      downloadedAt: row.downloaded_at,
      totalSizeBytes: row.total_size_bytes,
      version: row.version,
    };
  }

  // =========================================================================
  // Mutation Queue Operations
  // =========================================================================

  async queueMutation(
    type: string,
    payload: unknown,
    idempotencyKey?: string
  ): Promise<string> {
    if (!this.db) await this.init();

    const id = `${Date.now()}-${Math.random().toString(36).slice(2)}`;
    const key = idempotencyKey ?? `${type}-${Date.now()}`;

    await this.db!.executeSql(
      `INSERT OR IGNORE INTO mutations 
       (id, type, payload_json, created_at, retry_count, max_retries, idempotency_key)
       VALUES (?, ?, ?, ?, 0, 3, ?)`,
      [id, type, JSON.stringify(payload), new Date().toISOString(), key]
    );

    return id;
  }

  async getPendingMutations(): Promise<MutationRecord[]> {
    if (!this.db) await this.init();

    const [results] = await this.db!.executeSql(
      'SELECT * FROM mutations ORDER BY created_at ASC'
    );

    const records: MutationRecord[] = [];
    for (let i = 0; i < results.rows.length; i++) {
      const row = results.rows.item(i);
      records.push({
        id: row.id,
        type: row.type,
        payloadJson: row.payload_json,
        createdAt: row.created_at,
        retryCount: row.retry_count,
        maxRetries: row.max_retries,
        lastError: row.last_error,
        idempotencyKey: row.idempotency_key,
      });
    }

    return records;
  }

  async removeMutation(mutationId: string): Promise<void> {
    if (!this.db) await this.init();

    await this.db!.executeSql('DELETE FROM mutations WHERE id = ?', [mutationId]);
  }

  async markMutationFailed(mutationId: string, error: string): Promise<void> {
    if (!this.db) await this.init();

    await this.db!.executeSql(
      'UPDATE mutations SET retry_count = retry_count + 1, last_error = ? WHERE id = ?',
      [error, mutationId]
    );
  }

  async getMutationCount(): Promise<number> {
    if (!this.db) await this.init();

    const [results] = await this.db!.executeSql(
      'SELECT COUNT(*) as count FROM mutations'
    );

    return results.rows.item(0).count;
  }

  // =========================================================================
  // Database Management
  // =========================================================================

  async close(): Promise<void> {
    if (this.db) {
      await this.db.close();
      this.db = null;
      this.initPromise = null;
    }
  }

  async getDatabaseSize(): Promise<number> {
    // Approximate size calculation
    if (!this.db) await this.init();

    const tables = ['progress', 'modules', 'mutations'];
    let totalSize = 0;

    for (const table of tables) {
      const [results] = await this.db!.executeSql(
        `SELECT * FROM ${table}`
      );
      
      for (let i = 0; i < results.rows.length; i++) {
        const row = results.rows.item(i);
        totalSize += JSON.stringify(row).length;
      }
    }

    return totalSize;
  }
}

/**
 * Singleton database instance.
 */
export const database = new SQLiteDatabase();
