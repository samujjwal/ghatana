/**
 * FlashIt Mobile - SQLite Database Schema
 *
 * Schema definitions for offline moment storage, search, and sync.
 * Based on tutorputor-mobile SQLite patterns for consistency.
 *
 * @doc.type schema
 * @doc.purpose SQLite database schema for React Native
 * @doc.layer product
 * @doc.pattern DatabaseSchema
 */

-- ============================================
-- Database Version: 1
-- Created: 2025-01-01
-- ============================================

-- Enable foreign keys
PRAGMA foreign_keys = ON;

-- ============================================
-- CORE TABLES
-- ============================================

-- Moments table - core content storage
CREATE TABLE IF NOT EXISTS moments (
  id TEXT PRIMARY KEY,
  user_id TEXT NOT NULL,
  sphere_id TEXT,
  content TEXT,
  content_type TEXT NOT NULL DEFAULT 'text',
  emotion TEXT,
  energy_level REAL,
  tags TEXT NOT NULL DEFAULT '[]', -- JSON array
  media_urls TEXT NOT NULL DEFAULT '[]', -- JSON array
  transcription TEXT,
  transcription_status TEXT DEFAULT 'none',
  location_lat REAL,
  location_lng REAL,
  location_name TEXT,
  is_private INTEGER NOT NULL DEFAULT 1,
  is_pinned INTEGER NOT NULL DEFAULT 0,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  server_id TEXT, -- ID from backend after sync
  sync_status TEXT NOT NULL DEFAULT 'pending',
  sync_error TEXT,
  local_version INTEGER NOT NULL DEFAULT 1,
  server_version INTEGER DEFAULT 0
);

-- Spheres table - organizational containers
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

-- Media files table - local media storage tracking
CREATE TABLE IF NOT EXISTS media_files (
  id TEXT PRIMARY KEY,
  moment_id TEXT,
  file_type TEXT NOT NULL, -- 'image', 'video', 'audio'
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

-- Sync queue table - pending operations
CREATE TABLE IF NOT EXISTS sync_queue (
  id TEXT PRIMARY KEY,
  operation_type TEXT NOT NULL, -- 'create', 'update', 'delete'
  entity_type TEXT NOT NULL, -- 'moment', 'sphere', 'media'
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

-- Settings table - user preferences
CREATE TABLE IF NOT EXISTS settings (
  key TEXT PRIMARY KEY,
  value TEXT NOT NULL,
  updated_at TEXT NOT NULL
);

-- Search index table - FTS5 virtual table for full-text search
CREATE VIRTUAL TABLE IF NOT EXISTS moments_fts USING fts5(
  id,
  content,
  transcription,
  tags,
  emotion,
  content='moments',
  content_rowid='rowid'
);

-- ============================================
-- INDEXES
-- ============================================

-- Moments indexes
CREATE INDEX IF NOT EXISTS idx_moments_user ON moments(user_id);
CREATE INDEX IF NOT EXISTS idx_moments_sphere ON moments(sphere_id);
CREATE INDEX IF NOT EXISTS idx_moments_sync ON moments(sync_status);
CREATE INDEX IF NOT EXISTS idx_moments_created ON moments(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_moments_emotion ON moments(emotion);
CREATE INDEX IF NOT EXISTS idx_moments_content_type ON moments(content_type);

-- Spheres indexes
CREATE INDEX IF NOT EXISTS idx_spheres_user ON spheres(user_id);
CREATE INDEX IF NOT EXISTS idx_spheres_sync ON spheres(sync_status);

-- Media files indexes
CREATE INDEX IF NOT EXISTS idx_media_moment ON media_files(moment_id);
CREATE INDEX IF NOT EXISTS idx_media_upload ON media_files(upload_status);

-- Sync queue indexes
CREATE INDEX IF NOT EXISTS idx_sync_priority ON sync_queue(priority DESC, created_at);
CREATE INDEX IF NOT EXISTS idx_sync_entity ON sync_queue(entity_type, entity_id);
CREATE INDEX IF NOT EXISTS idx_sync_retry ON sync_queue(next_retry_at);

-- ============================================
-- TRIGGERS
-- ============================================

-- Trigger to keep FTS index in sync with moments table
CREATE TRIGGER IF NOT EXISTS moments_ai AFTER INSERT ON moments BEGIN
  INSERT INTO moments_fts(id, content, transcription, tags, emotion)
  VALUES (NEW.id, NEW.content, NEW.transcription, NEW.tags, NEW.emotion);
END;

CREATE TRIGGER IF NOT EXISTS moments_ad AFTER DELETE ON moments BEGIN
  INSERT INTO moments_fts(moments_fts, id, content, transcription, tags, emotion)
  VALUES ('delete', OLD.id, OLD.content, OLD.transcription, OLD.tags, OLD.emotion);
END;

CREATE TRIGGER IF NOT EXISTS moments_au AFTER UPDATE ON moments BEGIN
  INSERT INTO moments_fts(moments_fts, id, content, transcription, tags, emotion)
  VALUES ('delete', OLD.id, OLD.content, OLD.transcription, OLD.tags, OLD.emotion);
  INSERT INTO moments_fts(id, content, transcription, tags, emotion)
  VALUES (NEW.id, NEW.content, NEW.transcription, NEW.tags, NEW.emotion);
END;

-- Trigger to update moment_count in spheres
CREATE TRIGGER IF NOT EXISTS sphere_moment_count_insert AFTER INSERT ON moments
WHEN NEW.sphere_id IS NOT NULL BEGIN
  UPDATE spheres SET moment_count = moment_count + 1 WHERE id = NEW.sphere_id;
END;

CREATE TRIGGER IF NOT EXISTS sphere_moment_count_delete AFTER DELETE ON moments
WHEN OLD.sphere_id IS NOT NULL BEGIN
  UPDATE spheres SET moment_count = moment_count - 1 WHERE id = OLD.sphere_id;
END;

-- ============================================
-- INITIAL DATA
-- ============================================

-- Default settings
INSERT OR IGNORE INTO settings (key, value, updated_at) VALUES
  ('db_version', '1', datetime('now')),
  ('last_sync_at', '', datetime('now')),
  ('wifi_only_upload', 'false', datetime('now')),
  ('compression_quality', 'medium', datetime('now')),
  ('auto_backup', 'true', datetime('now'));
