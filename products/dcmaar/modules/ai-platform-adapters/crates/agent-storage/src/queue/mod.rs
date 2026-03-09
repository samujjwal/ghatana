//! Durable queue implementation for the DCMaar agent.

use std::{
    collections::VecDeque,
    marker::PhantomData,
    sync::Arc,
    time::{Duration, SystemTime, UNIX_EPOCH},
};

use serde::{de::DeserializeOwned, Serialize};
use sqlx::{
    sqlite::{SqliteConnectOptions, SqlitePoolOptions},
    Pool, Sqlite, Acquire,
};
use thiserror::Error;
use tokio::sync::{Mutex, RwLock};
use tracing::{error, info};

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

    /// Queue is closed
    #[error("Queue is closed")]
    QueueClosed,

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
    pool: Pool<Sqlite>,
    config: QueueConfig,
    state: Arc<RwLock<QueueState>>,
    buffer: Arc<Mutex<VecDeque<Vec<u8>>>>,
    compression: Option<Compression>,
    encryption: Option<Encryption>,
    metrics: Option<QueueMetricsHandle>,
    shutdown_tx: tokio::sync::watch::Sender<bool>,
    shutdown_rx: Arc<tokio::sync::watch::Receiver<bool>>,
    _phantom: PhantomData<T>,
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
                .map_err(|e| QueueError::Other(e.to_string()))?)
        } else {
            None
        };

        let encryption = if config.enable_encryption {
            if let Some(ref key) = config.encryption_key {
                Some(Encryption::new(key.as_bytes())
                    .map_err(|e| QueueError::Other(e.to_string()))?)
            } else {
                return Err(QueueError::Config("Encryption enabled but no key provided".to_string()));
            }
        } else {
            None
        };

        let state = Arc::new(RwLock::new(QueueState::new()));
        let metrics = if config.enable_metrics {
            Some(QueueMetricsHandle::new())
        } else {
            None
        };

        let (shutdown_tx, shutdown_rx) = tokio::sync::watch::channel(false);
        let shutdown_rx = Arc::new(shutdown_rx);

        let queue = Self {
            pool,
            config,
            state,
            buffer: Arc::new(Mutex::new(VecDeque::new())),
            compression,
            encryption,
            metrics,
            shutdown_tx,
            shutdown_rx,
            _phantom: PhantomData,
        };

        // Start background task
        let bg_queue = queue.create_background_task();
        let shutdown_rx_clone = queue.shutdown_rx.clone();
        tokio::spawn(bg_queue.run_background_task(shutdown_rx_clone));

        Ok(queue)
    }

    fn create_background_task(&self) -> BackgroundTaskQueue<T> {
        BackgroundTaskQueue {
            pool: self.pool.clone(),
            buffer: self.buffer.clone(),
            state: self.state.clone(),
            config: self.config.clone(),
            compression: self.compression.clone(),
            encryption: self.encryption.clone(),
            metrics: self.metrics.clone(),
            _phantom: PhantomData,
        }
    }

    async fn init_database(config: &QueueConfig) -> Result<Pool<Sqlite>> {
        if let Some(parent) = config.database_path.parent() {
            if !parent.exists() {
                std::fs::create_dir_all(parent)
                    .map_err(|e| QueueError::Config(format!("Failed to create database directory: {}", e)))?;
            }
        }

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

    /// Enqueue an item into the queue
    pub async fn enqueue(&self, item: T) -> Result<()> {
        let serialized = serde_json::to_vec(&item)?;
        let mut data = serialized;

        // Apply compression if enabled
        if let Some(ref compression) = self.compression {
            data = compression.compress(&data)
                .map_err(|e| QueueError::Other(e.to_string()))?;
        }

        // Apply encryption if enabled
        if let Some(ref encryption) = self.encryption {
            data = encryption.encrypt(&data)
                .map_err(|e| QueueError::Other(e.to_string()))?;
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
        let mut conn = self.pool.acquire().await?;
        let mut tx = conn.begin().await?;

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
                    .map_err(|e| QueueError::Other(e.to_string()))?;
            }

            if let Some(ref compression) = self.compression {
                data = compression.decompress(&data)
                    .map_err(|e| QueueError::Other(e.to_string()))?;
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
        *self.shutdown_rx.borrow()
    }

    /// Shutdown the queue gracefully
    pub async fn shutdown(&self) -> Result<()> {
        let _ = self.shutdown_tx.send(true);
        info!("Queue shutdown initiated");
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
    _phantom: PhantomData<T>,
}

impl<T> BackgroundTaskQueue<T> {
    async fn run_background_task(self, shutdown_rx: Arc<tokio::sync::watch::Receiver<bool>>) {
        let flush_interval = Duration::from_secs(self.config.flush_interval_secs);
        let mut shutdown_rx = (*shutdown_rx).clone();
        
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
            _phantom: PhantomData,
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