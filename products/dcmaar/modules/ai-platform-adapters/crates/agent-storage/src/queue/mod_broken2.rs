//! Durable queue implementation for the DCMaar agent.
//!
//! This module provides a durable, persistent queue implementation using SQLite
//! with WAL (Write-Ahead Logging) for better concurrency and performance.
//! It supports optional compression and encryption of queue items.

#![warn(missing_docs)]
#![forbid(unsafe_code)]

use std::{
    collections::VecDeque,
    sync::{
        Arc,
    },
    time::{Duration, SystemTime, UNIX_EPOCH},
};

use async_trait::async_trait;
use serde::{de::DeserializeOwned, Serialize};
use sqlx::{
    sqlite::{SqliteConnectOptions, SqlitePool, SqlitePoolOptions},
    Pool, Sqlite, Acquire,
};
use thiserror::Error;
use tokio::{
    sync::{Mutex, RwLock},
};
use tracing::{error, info, warn};

use crate::error::StorageError;

mod compression;
mod encryption;
mod metrics;
mod schema;

pub use metrics::QueueMetrics;
pub use schema::{QueueConfig, QueueItem, QueueItemStatus, QueueState};

use self::{
    compression::Compression,
    encryption::Encryption,
    metrics::QueueMetricsHandle,
    schema::QueueItemRecord,
};

/// Errors that can occur during queue operations
#[derive(Error, Debug)]
pub enum QueueError {
    /// Database error
    #[error("Database error: {0}")]
    Database(#[from] sqlx::Error),
    
    /// Item not found in the queue
    #[error("Item with id {0} not found")]
    ItemNotFound(i64),
    
    /// Serialization/deserialization error
    #[error("Serialization error: {0}")]
    Serialization(#[from] serde_json::Error),
    
    /// Time conversion error
    #[error("Time conversion error: {0}")]
    TimeError(#[from] std::time::SystemTimeError),
    
    /// Queue is full and drop_when_full is true
    #[error("Queue is full and drop_when_full is enabled")]
    QueueFullDrop,
    
    /// Queue is full and backpressure is active
    #[error("Queue is full and backpressure is active")]
    BackpressureActive,
    
    /// Invalid configuration
    #[error("Invalid configuration: {0}")]
    InvalidConfig(String),
    
    /// Compression error
    #[error("Compression error: {0}")]
    Compression(String),
    
    /// Encryption error
    #[error("Encryption error: {0}")]
    Encryption(String),

    /// Queue is closed
    #[error("Queue is closed")]
    QueueClosed,

    /// Queue is full
    #[error("Queue is full: {0}")]
    QueueFull(String),

    /// Operation timed out
    #[error("Operation timed out: {0}")]
    Timeout(String),

    /// Configuration error
    #[error("Configuration error: {0}")]
    Config(String),

    /// Migration error
    #[error("Migration error: {0}")]
    Migration(#[from] sqlx::migrate::MigrateError),

    /// Other error
    #[error("Queue error: {0}")]
    Other(String),
}

impl From<QueueError> for StorageError {
    fn from(err: QueueError) -> Self {
        StorageError::Queue(err.to_string())
    }
}

/// Result type for queue operations
pub type Result<T> = std::result::Result<T, QueueError>;

/// A durable, persistent queue implementation using SQLite
pub struct DurableQueue<T> {
    /// Queue configuration
    config: QueueConfig,
    
    /// Database connection pool
    pool: Pool<Sqlite>,
    
    /// Queue state
    state: Arc<RwLock<QueueState>>,
    
    /// In-memory buffer for items waiting to be persisted
    buffer: Arc<Mutex<VecDeque<Vec<u8>>>>,
    
    /// Compression handler
    compression: Option<Compression>,
    
    /// Encryption handler
    encryption: Option<Encryption>,
    
    /// Metrics collector
    metrics: Option<QueueMetricsHandle>,
    
    /// Background task handle
    bg_task: Mutex<Option<tokio::task::JoinHandle<()>>>,
    
    /// Shutdown signal sender
    shutdown_tx: tokio::sync::watch::Sender<bool>,
    
    /// Shutdown signal receiver
    shutdown_rx: Arc<tokio::sync::watch::Receiver<bool>>,
    
    /// Flag indicating if the queue is shutting down
    is_shutting_down: Arc<std::sync::atomic::AtomicBool>,
}

impl<T> DurableQueue<T>
where
    T: Serialize + DeserializeOwned + Send + Sync + 'static,
{
    /// Create a new durable queue with the given configuration
    pub async fn new(config: QueueConfig) -> Result<Self> {
        let pool = Self::init_database(&config).await?;

        let compression = if config.enable_compression {
            Some(Compression::new(config.compression_level)
                .map_err(|e| QueueError::Compression(e.to_string()))?)
        } else {
            None
        };

        let encryption = if config.enable_encryption {
            if let Some(ref key) = config.encryption_key {
                Some(Encryption::new(key.as_bytes())
                    .map_err(|e| QueueError::Encryption(e.to_string()))?)
            } else {
                return Err(QueueError::Config("Encryption enabled but no key provided".to_string()));
            }
        } else {
            None
        };

        let state = Arc::new(RwLock::new(Self::load_state(&pool, &config).await?));

        let metrics = if config.enable_metrics {
            Some(QueueMetricsHandle::new())
        } else {
            None
        };

        let (shutdown_tx, shutdown_rx) = tokio::sync::watch::channel(false);
        let shutdown_rx = Arc::new(shutdown_rx);

        let queue = Self {
            config,
            pool,
            state,
            buffer: Arc::new(Mutex::new(VecDeque::new())),
            compression,
            encryption,
            metrics,
            bg_task: Mutex::new(None),
            shutdown_tx,
            shutdown_rx,
            is_shutting_down: Arc::new(std::sync::atomic::AtomicBool::new(false)),
        };

        // Start background task
        let bg_queue = queue.create_background_task_queue();
        let bg_handle = tokio::spawn(bg_queue.run_background_task(queue.shutdown_rx.clone()));
        *queue.bg_task.lock().await = Some(bg_handle);

        Ok(queue)
    }

    /// Create a background task queue (simplified approach)
    fn create_background_task_queue(&self) -> BackgroundTaskQueue<T> {
        BackgroundTaskQueue {
            pool: self.pool.clone(),
            buffer: self.buffer.clone(),
            state: self.state.clone(),
            config: self.config.clone(),
            compression: self.compression.clone(),
            encryption: self.encryption.clone(),
            metrics: self.metrics.clone(),
        }
    }

    async fn init_database(config: &QueueConfig) -> Result<Pool<Sqlite>> {
        // Ensure the directory exists
        if let Some(parent) = config.database_path.parent() {
            if !parent.exists() {
                std::fs::create_dir_all(parent)
                    .map_err(|e| QueueError::Config(format!("Failed to create database directory: {}", e)))?;
            }
        }

        let database_url = format!("sqlite:{}", config.database_path.display());
        let options = SqliteConnectOptions::new()
            .filename(&config.database_path)
            .create_if_missing(true)
            .journal_mode(sqlx::sqlite::SqliteJournalMode::Wal)
            .synchronous(sqlx::sqlite::SqliteSynchronous::Normal)
            .foreign_keys(true);

        let pool = SqlitePoolOptions::new()
            .max_connections(config.max_connections)
            .connect_with(options)
            .await?;

        Self::create_tables(&pool).await?;
        Ok(pool)
    }

    async fn create_tables(pool: &Pool<Sqlite>) -> Result<()> {
        sqlx::query(
            r#"
            CREATE TABLE IF NOT EXISTS queue_items (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                data BLOB NOT NULL,
                status TEXT NOT NULL DEFAULT 'PENDING',
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                retry_count INTEGER NOT NULL DEFAULT 0,
                last_error TEXT
            );

            CREATE INDEX IF NOT EXISTS idx_queue_items_status ON queue_items(status);
            CREATE INDEX IF NOT EXISTS idx_queue_items_created_at ON queue_items(created_at);

            CREATE TABLE IF NOT EXISTS queue_metadata (
                key TEXT PRIMARY KEY,
                value TEXT NOT NULL,
                updated_at INTEGER NOT NULL
            );
            "#,
        )
        .execute(pool)
        .await?;

        Ok(())
    }

    async fn load_state(_pool: &Pool<Sqlite>, _config: &QueueConfig) -> Result<QueueState> {
        // For now, return a default state
        // In a full implementation, this would load state from the database
        Ok(QueueState::new())
    }

    /// Enqueue an item into the queue
    pub async fn enqueue(&self, item: T) -> Result<()> {
        if self.is_shutting_down() {
            return Err(QueueError::QueueClosed);
        }

        let serialized = serde_json::to_vec(&item)?;
        let mut data = serialized;

        // Apply compression if enabled
        if let Some(ref compression) = self.compression {
            data = compression.compress(&data)
                .map_err(|e| QueueError::Compression(e.to_string()))?;
        }

        // Apply encryption if enabled
        if let Some(ref encryption) = self.encryption {
            data = encryption.encrypt(&data)
                .map_err(|e| QueueError::Encryption(e.to_string()))?;
        }

        // Add to buffer
        self.buffer.lock().await.push_back(data);

        // Update metrics
        if let Some(ref metrics) = self.metrics {
            metrics.increment_enqueued();
        }

        Ok(())
    }

    /// Dequeue an item from the queue
    pub async fn dequeue(&self) -> Result<Option<T>> {
        if self.is_shutting_down() {
            return Err(QueueError::QueueClosed);
        }

        let mut conn = self.pool.acquire().await?;
        let tx = conn.begin().await?;

        let record = sqlx::query_as::<_, QueueItemRecord>(
            "SELECT * FROM queue_items WHERE status = 'PENDING' ORDER BY created_at LIMIT 1",
        )
        .fetch_optional(&mut *tx)
        .await?;

        if let Some(record) = record {
            // Mark as processing
            sqlx::query("UPDATE queue_items SET status = 'PROCESSING', updated_at = ? WHERE id = ?")
                .bind(SystemTime::now().duration_since(UNIX_EPOCH)?.as_secs() as i64)
                .bind(record.id)
                .execute(&mut *tx)
                .await?;

            tx.commit().await?;

            // Decrypt and decompress data
            let mut data = record.data;

            if let Some(ref encryption) = self.encryption {
                data = encryption.decrypt(&data)
                    .map_err(|e| QueueError::Encryption(e.to_string()))?;
            }

            if let Some(ref compression) = self.compression {
                data = compression.decompress(&data)
                    .map_err(|e| QueueError::Compression(e.to_string()))?;
            }

            let item: T = serde_json::from_slice(&data)?;

            // Update metrics
            if let Some(ref metrics) = self.metrics {
                metrics.increment_dequeued();
            }

            Ok(Some(item))
        } else {
            Ok(None)
        }
    }

    /// Mark an item as completed
    pub async fn complete(&self, id: i64) -> Result<()> {
        sqlx::query("UPDATE queue_items SET status = 'COMPLETED', updated_at = ? WHERE id = ?")
            .bind(SystemTime::now().duration_since(UNIX_EPOCH)?.as_secs() as i64)
            .bind(id)
            .execute(&self.pool)
            .await?;

        Ok(())
    }

    /// Get queue metrics
    pub async fn metrics(&self) -> Option<QueueMetrics> {
        self.metrics.as_ref().map(|m| m.snapshot())
    }

    /// Get queue state
    pub async fn state(&self) -> QueueState {
        self.state.read().await.clone()
    }

    /// Check if the queue is shutting down
    pub fn is_shutting_down(&self) -> bool {
        self.is_shutting_down.load(std::sync::atomic::Ordering::Relaxed)
    }

    /// Shutdown the queue gracefully
    pub async fn shutdown(&self) -> Result<()> {
        self.is_shutting_down.store(true, std::sync::atomic::Ordering::Relaxed);
        
        // Signal shutdown
        let _ = self.shutdown_tx.send(true);
        
        // Wait for background task to finish
        if let Some(handle) = self.bg_task.lock().await.take() {
            let _ = handle.await;
        }
        
        // Flush remaining buffer
        // (Implementation would go here)
        
        Ok(())
    }
}

/// Background task queue for handling periodic operations
struct BackgroundTaskQueue<T> {
    pool: Pool<Sqlite>,
    buffer: Arc<Mutex<VecDeque<Vec<u8>>>>,
    state: Arc<RwLock<QueueState>>,
    config: QueueConfig,
    compression: Option<Compression>,
    encryption: Option<Encryption>,
    metrics: Option<QueueMetricsHandle>,
}

impl<T> BackgroundTaskQueue<T> {
    async fn run_background_task(self, mut shutdown_rx: Arc<tokio::sync::watch::Receiver<bool>>) {
        let flush_interval = Duration::from_secs(self.config.flush_interval_secs);
        
        loop {
            tokio::select! {
                _ = tokio::time::sleep(flush_interval) => {
                    if let Err(e) = self.flush_buffer().await {
                        error!("Failed to flush buffer: {}", e);
                    }
                }
                _ = shutdown_rx.changed() => {
                    info!("Background task shutting down");
                    break;
                }
            }
        }
    }

    async fn flush_buffer(&self) -> Result<()> {
        let mut buffer = self.buffer.lock().await;
        if buffer.is_empty() {
            return Ok(());
        }

        let batch_size = self.config.batch_size.min(buffer.len());
        let items: Vec<_> = buffer.drain(0..batch_size).collect();
        drop(buffer);

        if !items.is_empty() {
            self.save_batch(&items).await?;
        }

        Ok(())
    }

    async fn save_batch(&self, items: &[Vec<u8>]) -> Result<()> {
        let mut tx = self.pool.begin().await?;
        let now = SystemTime::now().duration_since(UNIX_EPOCH)?.as_secs() as i64;

        for item_data in items {
            sqlx::query("INSERT INTO queue_items (data, status, created_at, updated_at) VALUES (?, ?, ?, ?)")
                .bind(item_data)
                .bind("PENDING")
                .bind(now)
                .bind(now)
                .execute(&mut *tx)
                .await?;
        }

        tx.commit().await?;
        Ok(())
    }
}

/// Clone is needed for background task
impl<T> Clone for BackgroundTaskQueue<T> {
    fn clone(&self) -> Self {
        Self {
            pool: self.pool.clone(),
            buffer: self.buffer.clone(),
            state: self.state.clone(),
            config: self.config.clone(),
            compression: self.compression.clone(),
            encryption: self.encryption.clone(),
            metrics: self.metrics.clone(),
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use tempfile::tempdir;
    use serde::{Deserialize, Serialize};

    #[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
    struct TestItem {
        id: u64,
        message: String,
    }

    #[tokio::test]
    async fn test_queue_basic_operations() -> Result<()> {
        let temp_dir = tempdir().unwrap();
        let db_path = temp_dir.path().join("test_queue.db");
        
        let config = QueueConfig {
            database_path: db_path,
            enable_compression: false,
            enable_encryption: false,
            enable_metrics: true,
            ..Default::default()
        };

        let queue = DurableQueue::<TestItem>::new(config).await?;
        
        let test_item = TestItem {
            id: 1,
            message: "Test message".to_string(),
        };

        // Test enqueue
        queue.enqueue(test_item.clone()).await?;
        
        // Test dequeue
        let dequeued = queue.dequeue().await?;
        assert_eq!(dequeued, Some(test_item));
        
        // Test empty queue
        let empty = queue.dequeue().await?;
        assert_eq!(empty, None);

        queue.shutdown().await?;
        Ok(())
    }
}