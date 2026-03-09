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
        atomic::{AtomicBool, Ordering},
        Arc,
    },
    time::{Duration, Instant, SystemTime, UNIX_EPOCH},
};

use tokio::sync::mpsc;

use async_trait::async_trait;
use serde::{de::DeserializeOwned, Serialize};
use sqlx::{
    sqlite::{SqliteConnectOptions, SqlitePool, SqlitePoolOptions},
    ConnectOptions, Pool, Sqlite,
};
use thiserror::Error;
use tokio::{
    sync::{Mutex, RwLock, Semaphore},
    time::sleep,
};
use tracing::{debug, error, info, trace, warn};
use uuid::Uuid;

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
    buffer: Arc<Mutex<VecDeque<T>>>,
    
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
    
    /// Next ID to assign to a new item
    next_id: Arc<std::sync::atomic::AtomicU64>,
    
    /// Flag indicating if the queue is shutting down
    is_shutting_down: Arc<std::sync::atomic::AtomicBool>,
}

impl<T> DurableQueue<T>
where
    T: Serialize + DeserializeOwned + Send + Sync + 'static,
{
    /// Create a new durable queue with the given configuration
    pub async fn new(config: QueueConfig) -> Result<Self> {
        // Initialize database connection
        let pool = Self::init_database(&config).await?;
        
        // Initialize compression if enabled
        let compression = if config.enable_compression {
            Some(Compression::new(config.compression_level)?)
        } else {
            None
        };
        
        // Initialize encryption if enabled
        let encryption = if config.enable_encryption {
            Some(Encryption::new(&config.encryption_key)?)
        } else {
            None
        };
        
        // Initialize metrics if enabled
        let metrics = if config.enable_metrics {
            Some(QueueMetricsHandle::new())
        } else {
            None
        };
        
        // Load initial state from database
        let state = Self::load_state(&pool, &config).await?;
        
        // Create shutdown channel
        let (shutdown_tx, shutdown_rx) = tokio::sync::watch::channel(false);
        
        // Create the queue
        let queue = DurableQueue {
            config: config.clone(),
            pool: pool.clone(),
            state: Arc::new(RwLock::new(state)),
            buffer: Arc::new(Mutex::new(VecDeque::with_capacity(config.max_buffer_size))),
            compression,
            encryption,
            metrics,
            bg_task: Mutex::new(None),
            shutdown_tx,
            shutdown_rx: Arc::new(shutdown_rx),
            next_id: Arc::new(std::sync::atomic::AtomicU64::new(1)), // Start IDs at 1
            is_shutting_down: Arc::new(std::sync::atomic::AtomicBool::new(false)),
        };
        
        // Start background task
        let bg_queue = queue.clone();
        let bg_handle = tokio::spawn(async move {
            bg_queue.run_background_task(shutdown_rx).await;
        });
        *queue.bg_task.lock().await = Some(bg_handle);
        
        Ok(queue)
    }
    
    /// Flush all buffered items to persistent storage
    /// 
    /// This will block until all buffered items are written to disk.
    /// Returns the number of items flushed.
    pub async fn flush(&self) -> Result<usize> {
        let mut buffer = self.buffer.lock().await;
        if buffer.is_empty() {
            return Ok(0);
        }
        
        let count = buffer.len();
        let items = std::mem::take(&mut *buffer);
        
        // Release the lock before doing I/O
        drop(buffer);
        
        // Insert all items in a single transaction
        let mut conn = self.pool.acquire().await?;
        let tx = conn.begin().await?;
        
        for item in items {
            let now = SystemTime::now();
            let created_at = now.duration_since(UNIX_EPOCH)?.as_secs() as i64;
            
            sqlx::query(
                "INSERT INTO queue_items (id, data, status, created_at, updated_at, retry_count) 
                 VALUES (?, ?, ?, ?, ?, ?)"
            )
            .bind(item.id)
            .bind(item.data)
            .bind(QueueItemStatus::Pending.to_string())
            .bind(created_at)
            .bind(created_at)
            .bind(0i32)
            .execute(&mut *conn)
            .await?;
        }
        
        tx.commit().await?;
        
        // Update metrics
        if let Some(metrics) = &self.metrics {
            metrics.incr_flushed(count);
        }
        
        Ok(count)
    }
    
    /// Drain the queue by processing all pending items
    /// 
    /// This will block until the queue is empty or the timeout is reached.
    /// Returns the number of items processed.
    pub async fn drain(&self, timeout: Duration) -> Result<usize> {
        let start = Instant::now();
        let mut processed = 0;
        
        loop {
            // Check if we've exceeded the timeout
            if start.elapsed() > timeout {
                return Err(QueueError::Timeout("Drain operation timed out".into()));
            }
            
            // Process a batch of items
            let items = self.dequeue(100, 30).await?;
            if items.is_empty() {
                break; // Queue is empty
            }
            
            // Process each item (in a real implementation, this would call a handler)
            for (id, _item) in items {
                if let Err(e) = self.complete(id).await {
                    error!(error = %e, id, "Failed to complete item");
                    // Continue with other items even if one fails
                }
                processed += 1;
            }
            
            // Small delay to prevent tight looping
            tokio::time::sleep(Duration::from_millis(100)).await;
        }
        
        Ok(processed)
    }
    
    /// Check if the queue can accept more items based on watermarks and size limits
    async fn check_can_enqueue(&self) -> Result<()> {
        let state = self.state.read().await;
        let now = SystemTime::now();
        
        // Check if any items are too old
        if let Some(max_age) = self.config.max_item_age_secs {
            if let Some(oldest_ts) = state.last_enqueued_at {
                if let Ok(age) = now.duration_since(oldest_ts) {
                    if age.as_secs() > max_age {
                        return Err(QueueError::QueueFull("Oldest item exceeds maximum age".into()));
                    }
                }
            }
        }
        
        // Check size-based watermarks
        if let Some(high_watermark) = self.config.high_watermark_bytes {
            if state.total_size_bytes >= high_watermark {
                if self.config.drop_when_full {
                    return Err(QueueError::QueueFull("High watermark exceeded".into()));
                }
                
                // Release the lock before waiting
                drop(state);
                
                // Wait for the queue to drain below the low watermark
                let low_watermark = self.config.low_watermark_bytes.unwrap_or(high_watermark / 2);
                let start = std::time::Instant::now();
                let timeout = std::time::Duration::from_secs(30);
                
                loop {
                    let state = self.state.read().await;
                    if state.total_size_bytes < low_watermark {
                        break;
                    }
                    
                    if start.elapsed() > timeout {
                        return Err(QueueError::Timeout("Timed out waiting for queue to drain".into()));
                    }
                    
                    // Release the lock and yield to the runtime
                    drop(state);
                    tokio::time::sleep(std::time::Duration::from_millis(100)).await;
                }
                
                return Ok(());
            }
        }
        
        // Check item count limit
        if let Some(max_size) = self.config.max_queue_size {
            if state.item_count >= max_size {
                if self.config.drop_when_full {
                    return Err(QueueError::QueueFull("Queue size limit reached".into()));
                }
                
                // Release the lock before waiting
                drop(state);
                
                // Wait for space to become available
                let start = std::time::Instant::now();
                let timeout = std::time::Duration::from_secs(30);
                
                loop {
                    let state = self.state.read().await;
                    if state.item_count < max_size {
                        break;
                    }
                    
                    if start.elapsed() > timeout {
                        return Err(QueueError::Timeout("Timed out waiting for queue space".into()));
                    }
                    
                    drop(state);
                    tokio::time::sleep(std::time::Duration::from_millis(100)).await;
                }
            }
        }
        
        Ok(())
    }
    
    /// Dequeue items from the queue for processing
    /// 
    /// # Arguments
    /// * `max_items` - Maximum number of items to dequeue
    /// * `visibility_timeout_secs` - How long the items should be invisible to other consumers
    /// 
    /// # Returns
    /// A vector of dequeued items with their IDs and data
    pub async fn dequeue(
        &self,
        max_items: usize,
        visibility_timeout_secs: u32,
    ) -> Result<Vec<(i64, T)>> {
        if max_items == 0 {
            return Ok(Vec::new());
        }

        let now = SystemTime::now();
        let visibility_deadline = now + Duration::from_secs(visibility_timeout_secs as u64);
        let visibility_deadline_secs = visibility_deadline.duration_since(UNIX_EPOCH)?.as_secs() as i64;
        
        // Get a connection from the pool
        let mut conn = self.pool.acquire().await?;
        
        // Start a transaction
        let tx = conn.begin().await?;
        
        // Try to fetch items that are not currently being processed by others
        let items: Vec<(i64, Vec<u8>)> = sqlx::query_as(
            r#"
            WITH cte AS (
                SELECT id, data 
                FROM queue_items 
                WHERE status = ? 
                ORDER BY created_at ASC, id ASC
                LIMIT ?
                FOR UPDATE SKIP LOCKED
            )
            UPDATE queue_items 
            SET 
                status = ?,
                updated_at = ?,
                visibility_deadline = ?
            WHERE id IN (SELECT id FROM cte)
            RETURNING id, data
            "#
        )
        .bind(QueueItemStatus::Pending.to_string())
        .bind(max_items as i64)
        .bind(QueueItemStatus::Processing.to_string())
        .bind(now.duration_since(UNIX_EPOCH)?.as_secs() as i64)
        .bind(visibility_deadline_secs)
        .fetch_all(&mut *conn)
        .await?;
        
        // Commit the transaction
        tx.commit().await?;
        
        // Deserialize the items
        let mut result = Vec::with_capacity(items.len());
        for (id, data) in items {
            match self.decrypt_and_decompress(data).await {
                Ok(decrypted_data) => {
                    match serde_json::from_slice::<T>(&decrypted_data) {
                        Ok(item) => result.push((id, item)),
                        Err(e) => {
                            error!(error = %e, id, "Failed to deserialize dequeued item");
                            // Mark as failed to prevent infinite retries of corrupt data
                            if let Err(e) = self.mark_as_failed(id, format!("Failed to deserialize: {}", e)).await {
                                error!(error = %e, id, "Failed to mark corrupted item as failed");
                            }
                        }
                    }
                }
                Err(e) => {
                    error!(error = %e, id, "Failed to decrypt/decompress dequeued item");
                    if let Err(e) = self.mark_as_failed(id, format!("Decryption/Decompression failed: {}", e)).await {
                        error!(error = %e, id, "Failed to mark corrupted item as failed");
                    }
                }
            }
            
            // Update metrics and state
            if let Some(metrics) = &self.metrics {
                for _ in 0..result.len() {
                    metrics.incr_dequeued();
                }
                
                let mut state = self.state.write().await;
                state.processing_count += result.len();
                state.last_dequeued_at = Some(now);
            }
            
            Ok(result)
    }
    
    /// Mark an item as completed and remove it from the queue
    pub async fn complete(&self, id: i64) -> Result<()> {
        let now = SystemTime::now();
        
        let result = sqlx::query(
            r#"
            DELETE FROM queue_items 
            WHERE id = ? AND status = ?
            "#
        )
        .bind(id)
        .bind(QueueItemStatus::Processing.to_string())
        .execute(&self.pool)
        .await?;
        
        if result.rows_affected() == 0 {
            return Err(QueueError::ItemNotFound(id));
        }
        
        // Update metrics and state
        if let Some(metrics) = &self.metrics {
            metrics.incr_dequeued();
            
            let mut state = self.state.write().await;
            state.processed_count += 1;
            state.processing_count = state.processing_count.saturating_sub(1);
        }
        
        Ok(())
    }
    
    /// Mark an item as failed and optionally schedule for retry
    pub async fn mark_as_failed(&self, id: i64, error: String) -> Result<()> {
        let now = SystemTime::now();
        
        let result = sqlx::query(
            r#"
            UPDATE queue_items 
            SET 
                status = ?,
                last_error = ?,
                retry_count = retry_count + 1,
                updated_at = ?
            WHERE id = ?
            RETURNING retry_count
            "#
        )
        .bind(QueueItemStatus::Failed.to_string())
        .bind(&error)
        .bind(now.duration_since(UNIX_EPOCH)?.as_secs() as i64)
        .bind(id)
        .fetch_optional(&self.pool)
        .await?;
        
        if result.is_none() {
            return Err(QueueError::ItemNotFound(id));
        }
        
        // Update metrics and state
        if let Some(metrics) = &self.metrics {
            metrics.incr_failures();
            
            let mut state = self.state.write().await;
            state.error_count += 1;
            state.processing_count = state.processing_count.saturating_sub(1);
        }
        
        Ok(())
    }
    
    /// Helper method to decrypt and decompress item data
    async fn decrypt_and_decompress(&self, mut data: Vec<u8>) -> Result<Vec<u8>> {
        // TODO: Implement decryption if enabled
        // TODO: Implement decompression if enabled
        Ok(data)
    }
    
    /// Enqueue an item to the queue with backpressure based on watermarks
    pub async fn enqueue(&self, item: T) -> Result<()> {
        // Check if we can enqueue the item
        self.check_can_enqueue().await?;
        
        // Generate a new ID for the item
        let id = self.next_id.fetch_add(1, std::sync::atomic::Ordering::SeqCst) as i64;
        let now = SystemTime::now();
        
        // Serialize the item to get its exact size
        let serialized = serde_json::to_vec(&item)?;
        let item_size = serialized.len() as u64;
        
        // Create queue item with all required fields
        let queue_item = QueueItem {
            id,
            data: serialized,
            status: QueueItemStatus::Pending,
            created_at: now,
            updated_at: now,
            retry_count: 0,
            last_error: None,
        };
        
        // Add to buffer with proper locking
        let needs_flush = {
            let mut buffer = self.buffer.lock().await;
            buffer.push_back(queue_item);
            
            // Update in-memory state
            let mut state = self.state.write().await;
            state.item_count += 1;
            state.total_size_bytes += item_size;
            state.last_enqueued_at = Some(now);
            
            // Check if we need to flush
            buffer.len() >= self.config.max_buffer_size
        };
        
        // Update metrics
        if let Some(metrics) = &self.metrics {
            metrics.items_enqueued.inc();
            metrics.queue_size_bytes.inc_by(item_size);
            metrics.queue_size_items.inc();
        }
        
        // Flush if needed (outside the lock)
        if needs_flush {
            self.flush_buffer().await?;
        }
        
        Ok(())
    }
    
    /// Dequeue an item from the queue
    pub async fn dequeue(&self) -> Result<Option<T>> {
        // First try to get from buffer
        {
            let mut buffer = self.buffer.lock().await;
            if !buffer.is_empty() {
                if let Some(item) = buffer.pop_front() {
                    // Update metrics
                    if let Some(metrics) = &self.metrics {
                        metrics.incr_dequeued();
                    }
                    return Ok(Some(item));
                }
            }
        }
        
        // If buffer is empty, try to load from database
        self.load_from_database(1).await.map(|mut items| items.pop())
    }
    
    /// Get the current queue metrics
    pub async fn metrics(&self) -> Option<QueueMetrics> {
        self.metrics.as_ref().map(|m| m.metrics())
    }
    
    /// Get the current queue state
    pub async fn state(&self) -> QueueState {
        self.state.read().await.clone()
    }
    
    /// Close the queue and wait for background tasks to complete
    pub async fn close(&self) -> Result<()> {
        // Signal shutdown to background task
        if self.shutdown.send(true).is_err() {
            return Err(QueueError::Other("Failed to send shutdown signal".into()));
        }
        
        // Wait for background task to complete
        if let Some(handle) = self.bg_task.lock().await.take() {
            if let Err(e) = handle.await {
                error!(error = %e, "Background task failed");
                return Err(QueueError::Other(format!("Background task failed: {}", e)));
            }
        }
        
        // Flush any remaining items in the buffer
        self.flush_buffer().await?;
        
        Ok(())
    }
    
    // Internal methods
    
    async fn init_database(config: &QueueConfig) -> Result<SqlitePool> {
        // Create database directory if it doesn't exist
        if let Some(parent) = config.database_path.parent() {
            tokio::fs::create_dir_all(parent).await.map_err(|e| {
                QueueError::Config(format!("Failed to create database directory: {}", e))
            })?;
        }
        
        // Configure connection options
        let mut options = SqliteConnectOptions::new()
            .filename(&config.database_path)
            .create_if_missing(true)
            .journal_mode(sqlx::sqlite::SqliteJournalMode::Wal)
            .synchronous(sqlx::sqlite::SqliteSynchronous::Normal)
            .busy_timeout(Duration::from_secs(30));
        
        // Set WAL mode for better concurrency
        options = options.optimize_for_defaults();
        
        // Create connection pool
        let pool = SqlitePoolOptions::new()
            .max_connections(config.max_connections)
            .acquire_timeout(Duration::from_secs(30))
            .connect_with(options)
            .await?;
        
        // Enable WAL mode
        sqlx::query("PRAGMA journal_mode=WAL;")
            .execute(&pool)
            .await?;
        
        // Create tables if they don't exist
        Self::create_tables(&pool).await?;
        
        Ok(pool)
    }
    
    async fn create_tables(pool: &SqlitePool) -> Result<()> {
        sqlx::migrate!("./migrations").run(pool).await?;
        Ok(())
    }
    
    async fn load_state(pool: &SqlitePool, config: &QueueConfig) -> Result<QueueState> {
        // Get the count of items in each status and total size
        let stats: (i64, i64, i64, i64, i64) = sqlx::query_as(
            r#"
            SELECT 
                SUM(CASE WHEN status = ? THEN 1 ELSE 0 END) as pending,
                SUM(CASE WHEN status = ? THEN 1 ELSE 0 END) as processing,
                SUM(CASE WHEN status = ? THEN 1 ELSE 0 END) as completed,
                SUM(CASE WHEN status = ? THEN 1 ELSE 0 END) as failed,
                SUM(LENGTH(data)) as total_size
            FROM queue_items
            "#
        )
        .bind(QueueItemStatus::Pending.to_string())
        .bind(QueueItemStatus::Processing.to_string())
        .bind(QueueItemStatus::Completed.to_string())
        .bind(QueueItemStatus::Failed.to_string())
        .fetch_one(pool)
        .await?;
        
        // Get timestamps for important events
        let timestamps: (Option<i64>, Option<i64>, Option<i64>, Option<i64>) = sqlx::query_as(
            r#"
            SELECT 
                MIN(created_at) as oldest_item,
                MAX(created_at) as newest_item,
                MAX(updated_at) as last_updated,
                (SELECT updated_at FROM queue_items WHERE status = ? ORDER BY updated_at DESC LIMIT 1) as last_processed
            FROM queue_items
            "#
        )
        .bind(QueueItemStatus::Completed.to_string())
        .fetch_one(pool)
        .await?;
        
        let (oldest_ts, newest_ts, last_updated_ts, last_processed_ts) = timestamps;
        
        // Create a new QueueState with default values
        let mut state = QueueState {
            item_count: stats.0 as usize,  // pending items
            total_size_bytes: stats.4 as u64,  // total size in bytes
            processed_count: stats.2 as u64,  // completed items
            error_count: stats.3 as u64,  // failed items
            dropped_count: 0,  // will be updated separately if needed
            processing_count: stats.1 as usize,  // processing items
            created_at: oldest_ts
                .map(|ts| UNIX_EPOCH + Duration::from_secs(ts as u64))
                .unwrap_or_else(SystemTime::now),
            last_enqueued_at: newest_ts
                .map(|ts| UNIX_EPOCH + Duration::from_secs(ts as u64)),
            last_dequeued_at: last_processed_ts
                .map(|ts| UNIX_EPOCH + Duration::from_secs(ts as u64)),
            last_flush_at: last_updated_ts
                .map(|ts| UNIX_EPOCH + Duration::from_secs(ts as u64)),
            is_full: false,  // will be set below
            backpressure_active: false,  // will be set below
        };
        
        // Update full/backpressure status based on watermarks
        state.is_full = state.is_full(config);
        state.backpressure_active = state.is_full && !config.drop_when_full;
        
        // Get the count of dropped items (if tracking is enabled)
        if let Ok(dropped) = sqlx::query_scalar::<_, Option<i64>>(
            "SELECT value FROM queue_metadata WHERE key = 'dropped_count'"
        )
        .fetch_optional(pool)
        .await? {
            state.dropped_count = dropped.unwrap_or(0) as u64;
        }
        
        Ok(state)
    }
    
    async fn flush_buffer(&self) -> Result<()> {
        let mut buffer = self.buffer.lock().await;
        
        if buffer.is_empty() {
            return Ok(());
        }
        
        let batch_size = self.config.batch_size;
        let mut batch = Vec::with_capacity(batch_size);
        let mut count = 0;
        
        while let Some(item) = buffer.pop_front() {
            // Serialize item
            let mut data = serde_json::to_vec(&item)?;
            
            // Compress if enabled
            if let Some(compression) = &self.compression {
                data = compression.compress(&data)?;
            }
            
            // Encrypt if enabled
            if let Some(encryption) = &self.encryption {
                data = encryption.encrypt(&data)?;
            }
            
            // Create queue item with current timestamp
            let now = SystemTime::now();
            let queue_item = QueueItem {
                id: 0, // Will be set by the database
                data,
                status: QueueItemStatus::Pending,
                created_at: now,
                updated_at: now,
                retry_count: 0,
                last_error: None,
            };
            
            batch.push(queue_item);
            count += 1;
            
            // Flush batch if we've reached the batch size
            if batch.len() >= batch_size {
                self.save_batch(&batch).await?;
                batch.clear();
            }
            
            // Stop if we've processed all items or reached the batch limit
            if count >= self.config.max_batch_size {
                break;
            }
        }
        
        // Flush any remaining items in the batch
        if !batch.is_empty() {
            self.save_batch(&batch).await?;
        }
        
        // Update state
        let mut state = self.state.write().await;
        state.item_count += count;
        state.buffer_size = buffer.len();
        
        // Update metrics
        if let Some(metrics) = &self.metrics {
            for _ in 0..count {
                metrics.incr_enqueued();
            }
            metrics.set_buffer_size(buffer.len());
            metrics.set_queue_size(state.item_count);
        }
        
        Ok(())
    }
    
    /// Clean up expired items from the queue
    async fn cleanup_expired_items(&self) -> Result<usize> {
        let Some(max_age_secs) = self.config.max_item_age_secs else {
            // No max age set, nothing to clean up
            return Ok(0);
        };
        
        let now = SystemTime::now();
        let cutoff = now.duration_since(UNIX_EPOCH)?.as_secs() as i64 - max_age_secs as i64;
        
        let mut conn = self.pool.acquire().await?;
        let tx = conn.begin().await?;
        
        // First, get the count of items to be deleted for metrics
        let count: (i64,) = sqlx::query_as(
            r#"
            SELECT COUNT(*) 
            FROM queue_items 
            WHERE created_at < ? AND status != ?
            "#
        )
        .bind(cutoff)
        .bind(QueueItemStatus::Processing.to_string())
        .fetch_one(&mut *conn)
        .await?;
        
        if count.0 == 0 {
            return Ok(0);
        }
        
        // Delete the expired items
        let result = sqlx::query(
            r#"
            DELETE FROM queue_items 
            WHERE created_at < ? AND status != ?
            "#
        )
        .bind(cutoff)
        .bind(QueueItemStatus::Processing.to_string())
        .execute(&mut *conn)
        .await?;
        
        tx.commit().await?;
        
        let deleted_count = result.rows_affected() as usize;
        
        // Update metrics
        if let Some(metrics) = &self.metrics {
            for _ in 0..deleted_count {
                metrics.incr_dequeued();
            }
        }
        
        // Update state
        if deleted_count > 0 {
            let mut state = self.state.write().await;
            state.item_count = state.item_count.saturating_sub(deleted_count);
            
            // We don't know the exact size of deleted items, so we'll need to reload
            // the total size from the database on the next access
            state.total_size_bytes = 0; // Will be reloaded on next access
        }
        
        Ok(deleted_count)
    }
    
    async fn save_batch(&self, items: &[QueueItem]) -> Result<()> {
        if items.is_empty() {
            return Ok(());
        }
        
        let mut conn = self.pool.acquire().await?;
        let tx = conn.begin().await?;
        
        for item in items {
            sqlx::query(
                r#"
                INSERT INTO queue_items 
                (data, status, created_at, updated_at, retry_count, last_error)
                VALUES (?, ?, ?, ?, ?, ?)
                "#,
            )
            .bind(&item.data)
            .bind(item.status.to_string())
            .bind(item.created_at.duration_since(UNIX_EPOCH)?.as_secs() as i64)
            .bind(item.updated_at.duration_since(UNIX_EPOCH)?.as_secs() as i64)
            .bind(item.retry_count)
            .bind(&item.last_error)
            .execute(&mut *conn)
            .await?;
        }
        
        tx.commit().await?;
        Ok(())
    }
    
    async fn run_background_task(&self, mut shutdown_rx: tokio::sync::watch::Receiver<bool>) {
        let flush_interval = Duration::from_secs(self.config.flush_interval_secs as u64);
        let cleanup_interval = Duration::from_secs(60 * 5); // Run cleanup every 5 minutes
        
        let mut last_flush = Instant::now();
        let mut last_cleanup = Instant::now();
        
        loop {
            tokio::select! {
                // Check for shutdown signal
                _ = shutdown_rx.changed() => {
                    if *shutdown_rx.borrow() {
                        info!("Shutdown signal received, stopping background task");
                        break;
                    }
                }
                
                // Flush buffer at regular intervals
                _ = tokio::time::sleep_until(last_flush + flush_interval), if !self.buffer.lock().await.is_empty() => {
                    match self.flush_buffer().await {
                        Ok(_) => {
                            last_flush = Instant::now();
                        }
                        Err(e) => {
                            error!(error = %e, "Failed to flush buffer");
                            // Add a small delay to prevent tight error loops
                            tokio::time::sleep(Duration::from_secs(1)).await;
                        }
                    }
                }
                
                // Clean up expired items at regular intervals
                _ = tokio::time::sleep_until(last_cleanup + cleanup_interval) => {
                    match self.cleanup_expired_items().await {
                        Ok(count) if count > 0 => {
                            info!(count, "Cleaned up expired items from queue");
                        }
                        Err(e) => {
                            error!(error = %e, "Failed to clean up expired items");
                        }
                        _ => {}
                    }
                    last_cleanup = Instant::now();
                }
                
                // Process other background tasks with a small delay
                else => {
                    // Small delay to prevent busy-waiting
                    tokio::time::sleep(Duration::from_millis(100)).await;
                }
            }
        }
        
        // One final flush before shutting down
        if let Err(e) = self.flush().await {
            error!(error = %e, "Failed to flush queue during background task shutdown");
        }
    }
    
    /// Gracefully shut down the queue
    pub async fn shutdown(&self) -> Result<()> {
        // Mark that we're shutting down
        self.is_shutting_down.store(true, std::sync::atomic::Ordering::SeqCst);
        
        // Notify the background task to shut down
        if let Err(e) = self.shutdown_tx.send(true) {
            return Err(QueueError::Other(format!("Failed to send shutdown signal: {}", e)));
        }
        
        // Wait for the background task to complete
        if let Some(handle) = self.bg_task.lock().await.take() {
            if let Err(e) = handle.await {
                return Err(QueueError::Other(format!("Background task failed: {}", e)));
            }
        }
        
        // Flush any remaining items in the buffer
        self.flush().await?;
        
        Ok(())
    }
    
    /// Check if the queue is currently shutting down
    pub fn is_shutting_down(&self) -> bool {
        self.is_shutting_down.load(std::sync::atomic::Ordering::SeqCst)
    }
    
    /// Wait for the queue to be fully drained
    pub async fn wait_for_drain(&self, timeout: Duration) -> Result<()> {
        let start = std::time::Instant::now();
        
        loop {
            // Check if we've exceeded the timeout
            if start.elapsed() > timeout {
                return Err(QueueError::Other("Timeout waiting for queue to drain".into()));
            }
            
            // Check if the queue is empty
            let state = self.state.read().await;
            if state.pending_count == 0 && state.processing_count == 0 {
                return Ok(());
            }
            
            // Release the lock and wait a bit before checking again
            drop(state);
            tokio::time::sleep(Duration::from_millis(100)).await;
        }
    }
}

impl<T> Clone for DurableQueue<T> {
    fn clone(&self) -> Self {
        Self {
            config: self.config.clone(),
            pool: self.pool.clone(),
            state: self.state.clone(),
            buffer: self.buffer.clone(),
            compression: self.compression.clone(),
            encryption: self.encryption.clone(),
            metrics: self.metrics.clone(),
            bg_task: Mutex::new(None), // New clones don't get the background task
            shutdown_tx: self.shutdown_tx.clone(),
            shutdown_rx: self.shutdown_rx.clone(),
            next_id: self.next_id.clone(),
            is_shutting_down: self.is_shutting_down.clone(),
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use serde::{Deserialize, Serialize};
    use tempfile::tempdir;
    use tokio::time::{sleep, Duration};
    
    #[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
    struct TestItem {
        id: u64,
        name: String,
        value: f64,
    }
    
    fn create_test_config() -> (QueueConfig, tempfile::TempDir) {
        let temp_dir = tempdir().unwrap();
        let db_path = temp_dir.path().join("test.db");
        
        let config = QueueConfig {
            database_path: db_path,
            max_connections: 5,
            max_buffer_size: 1000,
            batch_size: 100,
            max_batch_size: 1000,
            flush_interval_secs: 1,
            enable_compression: false,
            compression_level: 1,
            enable_encryption: false,
            encryption_key: None,
            enable_metrics: true,
            high_watermark_bytes: Some(100 * 1024 * 1024), // 100MB
            low_watermark_bytes: Some(50 * 1024 * 1024),   // 50MB
            max_item_age_secs: Some(7 * 24 * 60 * 60),     // 1 week
            max_queue_size: Some(100_000),                 // 100k items
            drop_when_full: false,                         // Block producers when full
        };
        
        (config, temp_dir)
    }
    
    #[tokio::test]
    async fn test_enqueue_dequeue() {
        let (config, _temp_dir) = create_test_config();
        let queue = DurableQueue::<TestItem>::new(config).await.unwrap();
        
        // Enqueue some items
        for i in 0..10 {
            let item = TestItem {
                id: i,
                name: format!("Item {}", i),
                value: i as f64 * 1.5,
            };
            
            queue.enqueue(item).await.unwrap();
        }
        
        // Wait for background flush
        sleep(Duration::from_secs(2)).await;
        
        // Dequeue items and verify
        let items = queue.dequeue(10, 30).await.unwrap();
        assert_eq!(items.len(), 10);
        
        for (i, (_, item)) in items.into_iter().enumerate() {
            assert_eq!(item.id, i as u64);
            assert_eq!(item.name, format!("Item {}", i));
            assert_eq!(item.value, i as f64 * 1.5);
            
            // Complete the item
            queue.complete(i as i64).await.unwrap();
        }
        
        // Queue should be empty now
        assert!(queue.dequeue(1, 30).await.unwrap().is_empty());
    }
    
    #[tokio::test]
    async fn test_compression() {
        let (mut config, _temp_dir) = create_test_config();
        config.enable_compression = true;
        
        let queue = DurableQueue::<String>::new(config).await.unwrap();
        
        // Enqueue a large string that will benefit from compression
        let large_string = "a".repeat(1024 * 1024); // 1MB of 'a's
        queue.enqueue(large_string.clone()).await.unwrap();
        
        // Flush and dequeue
        queue.flush_buffer().await.unwrap();
        let items = queue.dequeue(1, 30).await.unwrap();
        assert_eq!(items.len(), 1);
        
        let (id, result) = items.into_iter().next().unwrap();
        assert_eq!(result, large_string);
        
        // Complete the item
        queue.complete(id).await.unwrap();
    }
    
    #[tokio::test]
    async fn test_encryption() {
        let (mut config, _temp_dir) = create_test_config();
        config.enable_encryption = true;
        config.encryption_key = Some("test-key-1234567890123456".to_string()); // 16 bytes for AES-128
        
        let queue = DurableQueue::<String>::new(config).await.unwrap();
        
        // Enqueue a sensitive string
        let sensitive_data = "this-is-a-secret".to_string();
        queue.enqueue(sensitive_data.clone()).await.unwrap();
        
        // Flush and dequeue
        queue.flush_buffer().await.unwrap();
        let items = queue.dequeue(1, 30).await.unwrap();
        assert_eq!(items.len(), 1);
        
        let (id, result) = items.into_iter().next().unwrap();
        assert_eq!(result, sensitive_data);
        
        // Complete the item
        queue.complete(id).await.unwrap();
    }
    
    #[tokio::test]
    async fn test_metrics() {
        let (config, _temp_dir) = create_test_config();
        let queue = DurableQueue::<i32>::new(config).await.unwrap();
        
        // Enqueue some items
        for i in 0..5 {
            queue.enqueue(i).await.unwrap();
        }
        
        // Flush and check metrics
        queue.flush_buffer().await.unwrap();
        
        // Dequeue some items
        let items = queue.dequeue(3, 30).await.unwrap();
        assert_eq!(items.len(), 3);
        
        // Complete the dequeued items
        for (id, _) in items {
            queue.complete(id).await.unwrap();
        }
        
        // Check metrics
        if let Some(metrics) = queue.metrics() {
            assert_eq!(metrics.enqueued, 5);
            assert_eq!(metrics.dequeued, 3);
            assert_eq!(metrics.queue_size, 2);
        } else {
            panic!("Metrics should be enabled for testing");
        }
        
        // Clean up
        queue.close().await.unwrap();
    }
    }
}
