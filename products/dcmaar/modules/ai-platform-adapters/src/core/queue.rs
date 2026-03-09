//! Durable event queue implementation using SQLite/WAL with encryption.
//!
//! This module provides a persistent, encrypted queue for events with:
//! - Idempotent enqueue/dequeue operations
//! - Size/time watermarks for backpressure
//! - AES-GCM encryption at rest
//! - Zstd compression for efficient storage
//! - Graceful drain/flush operations

use anyhow::{Context, Result};
use async_trait::async_trait;
use serde::{Deserialize, Serialize};
use sqlx::{
    sqlite::{SqliteConnectOptions, SqliteJournalMode, SqlitePool, SqlitePoolOptions},
    Executor, Row,
};
use std::{
    path::Path,
    str::FromStr,
    sync::Arc,
    time::{Duration, SystemTime, UNIX_EPOCH},
};
use tokio::sync::{mpsc, Mutex, Notify, RwLock};
use tracing::{debug, error, info, instrument, warn};
use zstd::stream::encode_all;

use crate::security::secrets_store::SecretsStore;

/// Queue item status
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub enum QueueItemStatus {
    /// Item is pending processing
    Pending,
    /// Item is in-flight (being processed)
    InFlight,
    /// Item has been processed successfully
    Processed,
    /// Item processing failed and may be retried
    Failed,
}

impl From<&str> for QueueItemStatus {
    fn from(s: &str) -> Self {
        match s {
            "pending" => QueueItemStatus::Pending,
            "in_flight" => QueueItemStatus::InFlight,
            "processed" => QueueItemStatus::Processed,
            "failed" => QueueItemStatus::Failed,
            _ => QueueItemStatus::Pending,
        }
    }
}

impl From<QueueItemStatus> for &'static str {
    fn from(status: QueueItemStatus) -> Self {
        match status {
            QueueItemStatus::Pending => "pending",
            QueueItemStatus::InFlight => "in_flight",
            QueueItemStatus::Processed => "processed",
            QueueItemStatus::Failed => "failed",
        }
    }
}

/// Queue item with metadata
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct QueueItem {
    /// Unique ID for the item
    pub id: String,
    /// Deduplication key (if any)
    pub dedup_key: Option<String>,
    /// Item payload (serialized event)
    pub payload: Vec<u8>,
    /// Item status
    pub status: QueueItemStatus,
    /// Number of processing attempts
    pub attempts: u32,
    /// Timestamp when the item was enqueued
    pub enqueued_at: u64,
    /// Timestamp when the item was last updated
    pub updated_at: u64,
    /// Timestamp when the item should be processed (for delayed processing)
    pub process_after: u64,
}

/// Queue configuration
#[derive(Debug, Clone)]
pub struct QueueConfig {
    /// Path to the queue database file
    pub db_path: String,
    /// Maximum queue size in bytes
    pub max_size_bytes: u64,
    /// High watermark percentage (0-100) for backpressure
    pub high_watermark_percent: u8,
    /// Low watermark percentage (0-100) for backpressure
    pub low_watermark_percent: u8,
    /// Maximum number of database connections
    pub max_connections: u32,
    /// Whether to encrypt the queue
    pub encrypt: bool,
    /// Maximum retention time for processed items (seconds)
    pub retention_secs: u64,
    /// Compression level (1-22, 0 for no compression)
    pub compression_level: u32,
}

impl Default for QueueConfig {
    fn default() -> Self {
        Self {
            db_path: "data/queue.db".to_string(),
            max_size_bytes: 512 * 1024 * 1024, // 512 MB
            high_watermark_percent: 80,
            low_watermark_percent: 60,
            max_connections: 5,
            encrypt: true,
            retention_secs: 86400, // 1 day
            compression_level: 3,   // Default zstd level
        }
    }
}

/// Queue state
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum QueueState {
    /// Queue is accepting new items
    Open,
    /// Queue is not accepting new items due to backpressure
    Backpressure,
    /// Queue is draining (processing existing items but not accepting new ones)
    Draining,
    /// Queue is flushing (processing all items as quickly as possible)
    Flushing,
    /// Queue is closed
    Closed,
}

/// Queue metrics
#[derive(Debug, Clone, Default)]
pub struct QueueMetrics {
    /// Total number of items in the queue
    pub total_items: u64,
    /// Number of pending items
    pub pending_items: u64,
    /// Number of in-flight items
    pub in_flight_items: u64,
    /// Number of processed items
    pub processed_items: u64,
    /// Number of failed items
    pub failed_items: u64,
    /// Total size of the queue in bytes
    pub size_bytes: u64,
    /// Current queue state
    pub state: QueueState,
    /// Number of items dropped due to backpressure
    pub dropped_items: u64,
    /// Average compression ratio
    pub compression_ratio: f64,
}

/// Trait for queue operations
#[async_trait]
pub trait Queue: Send + Sync {
    /// Initialize the queue
    async fn init(&self) -> Result<()>;
    /// Enqueue an item
    async fn enqueue(&self, item: QueueItem) -> Result<()>;
    /// Dequeue an item
    async fn dequeue(&self) -> Result<Option<QueueItem>>;
    /// Mark an item as processed
    async fn mark_processed(&self, id: &str) -> Result<()>;
    /// Mark an item as failed
    async fn mark_failed(&self, id: &str) -> Result<()>;
    /// Get queue metrics
    async fn metrics(&self) -> Result<QueueMetrics>;
    /// Drain the queue (process existing items but don't accept new ones)
    async fn drain(&self) -> Result<()>;
    /// Flush the queue (process all items as quickly as possible)
    async fn flush(&self) -> Result<()>;
    /// Close the queue
    async fn close(&self) -> Result<()>;
}

/// Durable queue implementation using SQLite/WAL
pub struct DurableQueue {
    /// Queue configuration
    config: QueueConfig,
    /// SQLite connection pool
    pool: Arc<SqlitePool>,
    /// Queue state
    state: Arc<RwLock<QueueState>>,
    /// Secrets store for encryption
    secrets: Option<Arc<SecretsStore>>,
    /// Notify when queue state changes
    state_change: Arc<Notify>,
    /// Metrics
    metrics: Arc<RwLock<QueueMetrics>>,
    /// Dropped items counter
    dropped_items: Arc<std::sync::atomic::AtomicU64>,
}

impl DurableQueue {
    /// Create a new durable queue with the given configuration
    pub async fn new(config: QueueConfig, secrets: Option<Arc<SecretsStore>>) -> Result<Arc<Self>> {
        // Create database directory if it doesn't exist
        if let Some(parent) = Path::new(&config.db_path).parent() {
            if !parent.exists() {
                tokio::fs::create_dir_all(parent)
                    .await
                    .context("Failed to create queue database directory")?;
            }
        }

        // Configure SQLite connection options
        let options = SqliteConnectOptions::from_str(&format!("sqlite:{}", config.db_path))?;
        let options = options
            .create_if_missing(true)
            .journal_mode(SqliteJournalMode::Wal) // Use WAL mode for better concurrency
            .synchronous(sqlx::sqlite::SqliteSynchronous::Normal) // Balance between safety and performance
            .busy_timeout(Duration::from_secs(30))
            .foreign_keys(true);

        // Create connection pool
        let pool = SqlitePoolOptions::new()
            .max_connections(config.max_connections)
            .connect_with(options)
            .await
            .context("Failed to create SQLite connection pool")?;

        let queue = Arc::new(Self {
            config,
            pool: Arc::new(pool),
            state: Arc::new(RwLock::new(QueueState::Open)),
            secrets,
            state_change: Arc::new(Notify::new()),
            metrics: Arc::new(RwLock::new(QueueMetrics::default())),
            dropped_items: Arc::new(std::sync::atomic::AtomicU64::new(0)),
        });

        // Initialize the queue
        queue.init().await?;

        // Start background tasks
        queue.start_background_tasks();

        Ok(queue)
    }

    /// Start background tasks for queue maintenance
    fn start_background_tasks(&self) {
        let queue = Arc::new(self.clone());

        // Start metrics updater
        tokio::spawn(async move {
            let queue = queue.clone();
            loop {
                tokio::time::sleep(Duration::from_secs(10)).await;
                if let Err(e) = queue.update_metrics().await {
                    error!("Failed to update queue metrics: {}", e);
                }

                // Check if we need to clean up old processed items
                if let Err(e) = queue.cleanup_processed_items().await {
                    error!("Failed to clean up processed items: {}", e);
                }

                // Check if we need to adjust state based on watermarks
                if let Err(e) = queue.check_watermarks().await {
                    error!("Failed to check watermarks: {}", e);
                }
            }
        });
    }

    /// Update queue metrics
    async fn update_metrics(&self) -> Result<()> {
        let mut metrics = QueueMetrics::default();

        // Get queue state
        metrics.state = *self.state.read().await;

        // Get item counts by status
        let counts = sqlx::query(
            r#"
            SELECT status, COUNT(*) as count, SUM(LENGTH(payload)) as size
            FROM queue_items
            GROUP BY status
            "#,
        )
        .fetch_all(&*self.pool)
        .await?;

        // Update metrics from query results
        for row in counts {
            let status: String = row.get("status");
            let count: i64 = row.get("count");
            let size: Option<i64> = row.try_get("size").ok();

            match status.as_str() {
                "pending" => metrics.pending_items = count as u64,
                "in_flight" => metrics.in_flight_items = count as u64,
                "processed" => metrics.processed_items = count as u64,
                "failed" => metrics.failed_items = count as u64,
                _ => {}
            }

            if let Some(s) = size {
                metrics.size_bytes += s as u64;
            }
        }

        // Calculate total items
        metrics.total_items = metrics.pending_items + metrics.in_flight_items + 
                             metrics.processed_items + metrics.failed_items;

        // Get dropped items count
        metrics.dropped_items = self.dropped_items.load(std::sync::atomic::Ordering::Relaxed);

        // Get compression ratio if available
        if metrics.total_items > 0 {
            let ratio = sqlx::query(
                r#"
                SELECT AVG(original_size * 1.0 / LENGTH(payload)) as ratio
                FROM queue_items
                WHERE original_size > 0
                "#,
            )
            .fetch_optional(&*self.pool)
            .await?;

            if let Some(row) = ratio {
                metrics.compression_ratio = row.get::<f64, _>("ratio");
            }
        }

        // Update metrics
        *self.metrics.write().await = metrics;

        // Export metrics to Prometheus
        metrics::gauge!("agent.queue.depth", metrics.total_items as f64);
        metrics::gauge!("agent.queue.bytes", metrics.size_bytes as f64);
        metrics::counter!("agent.queue.dropped_total", metrics.dropped_items as u64);
        metrics::gauge!("agent.queue.compress_ratio", metrics.compression_ratio);

        Ok(())
    }

    /// Clean up processed items based on retention policy
    async fn cleanup_processed_items(&self) -> Result<()> {
        if self.config.retention_secs == 0 {
            return Ok(());
        }

        let now = SystemTime::now().duration_since(UNIX_EPOCH)?.as_secs();
        let cutoff = now - self.config.retention_secs;

        let result = sqlx::query(
            r#"
            DELETE FROM queue_items
            WHERE status = 'processed' AND updated_at < ?
            "#,
        )
        .bind(cutoff as i64)
        .execute(&*self.pool)
        .await?;

        if result.rows_affected() > 0 {
            debug!(
                "Cleaned up {} processed items older than {} seconds",
                result.rows_affected(),
                self.config.retention_secs
            );
        }

        Ok(())
    }

    /// Check watermarks and adjust queue state
    async fn check_watermarks(&self) -> Result<()> {
        let metrics = self.metrics.read().await.clone();
        let current_state = *self.state.read().await;

        // Skip if we're not in Open or Backpressure state
        if current_state != QueueState::Open && current_state != QueueState::Backpressure {
            return Ok(());
        }

        let high_watermark = (self.config.max_size_bytes * self.config.high_watermark_percent as u64) / 100;
        let low_watermark = (self.config.max_size_bytes * self.config.low_watermark_percent as u64) / 100;

        let new_state = if metrics.size_bytes >= high_watermark {
            QueueState::Backpressure
        } else if metrics.size_bytes <= low_watermark {
            QueueState::Open
        } else {
            current_state
        };

        if new_state != current_state {
            info!(
                "Queue state changed from {:?} to {:?} (size: {} bytes, high: {}, low: {})",
                current_state,
                new_state,
                metrics.size_bytes,
                high_watermark,
                low_watermark
            );

            *self.state.write().await = new_state;
            self.state_change.notify_waiters();
        }

        Ok(())
    }

    /// Compress data using zstd
    fn compress_data(&self, data: &[u8]) -> Result<(Vec<u8>, usize)> {
        if self.config.compression_level == 0 || data.len() < 1024 {
            // Don't compress small payloads or if compression is disabled
            return Ok((data.to_vec(), data.len()));
        }

        let original_size = data.len();
        let compressed = encode_all(
            data,
            self.config.compression_level as i32,
        )?;

        Ok((compressed, original_size))
    }

    /// Encrypt data if secrets store is available
    async fn encrypt_data(&self, data: &[u8]) -> Result<Vec<u8>> {
        if !self.config.encrypt || self.secrets.is_none() {
            return Ok(data.to_vec());
        }

        let secrets = self.secrets.as_ref().unwrap();
        secrets.encrypt(data).await
    }

    /// Decrypt data if secrets store is available
    async fn decrypt_data(&self, data: &[u8]) -> Result<Vec<u8>> {
        if !self.config.encrypt || self.secrets.is_none() {
            return Ok(data.to_vec());
        }

        let secrets = self.secrets.as_ref().unwrap();
        secrets.decrypt(data).await
    }

    /// Generate a unique ID for queue items
    fn generate_id() -> String {
        use rand::Rng;
        let mut rng = rand::thread_rng();
        let random_bytes: [u8; 16] = rng.gen();
        hex::encode(random_bytes)
    }
}

#[async_trait]
impl Queue for DurableQueue {
    async fn init(&self) -> Result<()> {
        // Create tables if they don't exist
        sqlx::query(
            r#"
            CREATE TABLE IF NOT EXISTS queue_items (
                id TEXT PRIMARY KEY,
                dedup_key TEXT,
                payload BLOB NOT NULL,
                status TEXT NOT NULL,
                attempts INTEGER NOT NULL DEFAULT 0,
                enqueued_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                process_after INTEGER NOT NULL,
                original_size INTEGER NOT NULL DEFAULT 0
            );
            CREATE INDEX IF NOT EXISTS idx_queue_items_status ON queue_items(status);
            CREATE INDEX IF NOT EXISTS idx_queue_items_dedup ON queue_items(dedup_key) WHERE dedup_key IS NOT NULL;
            CREATE INDEX IF NOT EXISTS idx_queue_items_process_after ON queue_items(process_after) WHERE status = 'pending';
            "#,
        )
        .execute(&*self.pool)
        .await
        .context("Failed to create queue tables")?;

        // Update metrics
        self.update_metrics().await?;

        info!("Queue initialized successfully");
        Ok(())
    }

    async fn enqueue(&self, mut item: QueueItem) -> Result<()> {
        // Check queue state
        let state = *self.state.read().await;
        if state == QueueState::Closed || state == QueueState::Draining || state == QueueState::Backpressure {
            // Increment dropped counter
            self.dropped_items.fetch_add(1, std::sync::atomic::Ordering::Relaxed);
            return Err(anyhow::anyhow!("Queue is not accepting new items (state: {:?})", state));
        }

        // Check for duplicate if dedup_key is provided
        if let Some(dedup_key) = &item.dedup_key {
            let existing = sqlx::query(
                r#"
                SELECT id FROM queue_items
                WHERE dedup_key = ? AND status IN ('pending', 'in_flight')
                LIMIT 1
                "#,
            )
            .bind(dedup_key)
            .fetch_optional(&*self.pool)
            .await?;

            if existing.is_some() {
                debug!("Skipping duplicate item with dedup_key: {}", dedup_key);
                return Ok(()); // Silently succeed for idempotent behavior
            }
        }

        // Generate ID if not provided
        if item.id.is_empty() {
            item.id = Self::generate_id();
        }

        // Set timestamps if not provided
        let now = SystemTime::now().duration_since(UNIX_EPOCH)?.as_secs();
        if item.enqueued_at == 0 {
            item.enqueued_at = now;
        }
        if item.updated_at == 0 {
            item.updated_at = now;
        }
        if item.process_after == 0 {
            item.process_after = now;
        }

        // Compress and encrypt payload
        let (compressed_data, original_size) = self.compress_data(&item.payload)?;
        let encrypted_data = self.encrypt_data(&compressed_data).await?;

        // Insert into database
        sqlx::query(
            r#"
            INSERT INTO queue_items
            (id, dedup_key, payload, status, attempts, enqueued_at, updated_at, process_after, original_size)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            "#,
        )
        .bind(&item.id)
        .bind(&item.dedup_key)
        .bind(&encrypted_data)
        .bind(Into::<&str>::into(item.status))
        .bind(item.attempts as i32)
        .bind(item.enqueued_at as i64)
        .bind(item.updated_at as i64)
        .bind(item.process_after as i64)
        .bind(original_size as i64)
        .execute(&*self.pool)
        .await
        .context("Failed to insert queue item")?;

        debug!("Enqueued item with id: {}", item.id);
        Ok(())
    }

    async fn dequeue(&self) -> Result<Option<QueueItem>> {
        // Check queue state
        let state = *self.state.read().await;
        if state == QueueState::Closed {
            return Err(anyhow::anyhow!("Queue is closed"));
        }

        let now = SystemTime::now().duration_since(UNIX_EPOCH)?.as_secs() as i64;

        // Use a transaction to ensure atomicity
        let mut tx = self.pool.begin().await?;

        // Get the next pending item that's ready to process
        let row = sqlx::query(
            r#"
            SELECT id, dedup_key, payload, status, attempts, enqueued_at, updated_at, process_after
            FROM queue_items
            WHERE status = 'pending' AND process_after <= ?
            ORDER BY process_after ASC
            LIMIT 1
            "#,
        )
        .bind(now)
        .fetch_optional(&mut tx)
        .await?;

        let item = match row {
            Some(row) => {
                let id: String = row.get("id");
                let dedup_key: Option<String> = row.get("dedup_key");
                let payload: Vec<u8> = row.get("payload");
                let status: String = row.get("status");
                let attempts: i32 = row.get("attempts");
                let enqueued_at: i64 = row.get("enqueued_at");
                let updated_at: i64 = row.get("updated_at");
                let process_after: i64 = row.get("process_after");

                // Update the item status to in_flight
                sqlx::query(
                    r#"
                    UPDATE queue_items
                    SET status = 'in_flight', attempts = attempts + 1, updated_at = ?
                    WHERE id = ?
                    "#,
                )
                .bind(now)
                .bind(&id)
                .execute(&mut tx)
                .await
                .context("Failed to update queue item status")?;

                // Decrypt the payload
                let decrypted_data = self.decrypt_data(&payload).await?;

                Some(QueueItem {
                    id,
                    dedup_key,
                    payload: decrypted_data,
                    status: QueueItemStatus::from(status.as_str()),
                    attempts: (attempts + 1) as u32, // Include the current attempt
                    enqueued_at: enqueued_at as u64,
                    updated_at: now as u64,
                    process_after: process_after as u64,
                })
            }
            None => None,
        };

        // Commit the transaction
        tx.commit().await?;

        if item.is_some() {
            debug!("Dequeued item with id: {}", item.as_ref().unwrap().id);
        }

        Ok(item)
    }

    async fn mark_processed(&self, id: &str) -> Result<()> {
        let now = SystemTime::now().duration_since(UNIX_EPOCH)?.as_secs() as i64;

        let result = sqlx::query(
            r#"
            UPDATE queue_items
            SET status = 'processed', updated_at = ?
            WHERE id = ? AND status = 'in_flight'
            "#,
        )
        .bind(now)
        .bind(id)
        .execute(&*self.pool)
        .await?;

        if result.rows_affected() == 0 {
            return Err(anyhow::anyhow!("Item not found or not in-flight: {}", id));
        }

        debug!("Marked item as processed: {}", id);
        Ok(())
    }

    async fn mark_failed(&self, id: &str) -> Result<()> {
        let now = SystemTime::now().duration_since(UNIX_EPOCH)?.as_secs() as i64;

        let result = sqlx::query(
            r#"
            UPDATE queue_items
            SET status = 'failed', updated_at = ?
            WHERE id = ? AND status = 'in_flight'
            "#,
        )
        .bind(now)
        .bind(id)
        .execute(&*self.pool)
        .await?;

        if result.rows_affected() == 0 {
            return Err(anyhow::anyhow!("Item not found or not in-flight: {}", id));
        }

        debug!("Marked item as failed: {}", id);
        Ok(())
    }

    async fn metrics(&self) -> Result<QueueMetrics> {
        Ok(self.metrics.read().await.clone())
    }

    async fn drain(&self) -> Result<()> {
        info!("Draining queue");
        *self.state.write().await = QueueState::Draining;
        self.state_change.notify_waiters();
        Ok(())
    }

    async fn flush(&self) -> Result<()> {
        info!("Flushing queue");
        *self.state.write().await = QueueState::Flushing;
        self.state_change.notify_waiters();

        // Reset all in-flight items to pending to ensure they get processed
        let now = SystemTime::now().duration_since(UNIX_EPOCH)?.as_secs() as i64;
        let result = sqlx::query(
            r#"
            UPDATE queue_items
            SET status = 'pending', process_after = ?, updated_at = ?
            WHERE status = 'in_flight'
            "#,
        )
        .bind(now)
        .bind(now)
        .execute(&*self.pool)
        .await?;

        if result.rows_affected() > 0 {
            info!("Reset {} in-flight items to pending during flush", result.rows_affected());
        }

        Ok(())
    }

    async fn close(&self) -> Result<()> {
        info!("Closing queue");
        *self.state.write().await = QueueState::Closed;
        self.state_change.notify_waiters();
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use tempfile::tempdir;

    #[tokio::test]
    async fn test_queue_lifecycle() -> Result<()> {
        let temp_dir = tempdir()?;
        let db_path = temp_dir.path().join("test_queue.db");

        let config = QueueConfig {
            db_path: db_path.to_string_lossy().to_string(),
            max_size_bytes: 10 * 1024 * 1024, // 10 MB
            high_watermark_percent: 80,
            low_watermark_percent: 60,
            max_connections: 5,
            encrypt: false, // No encryption for tests
            retention_secs: 3600,
            compression_level: 0, // No compression for tests
        };

        // Create queue
        let queue = DurableQueue::new(config, None).await?;

        // Enqueue an item
        let item = QueueItem {
            id: "".to_string(), // Will be generated
            dedup_key: Some("test-key".to_string()),
            payload: b"test payload".to_vec(),
            status: QueueItemStatus::Pending,
            attempts: 0,
            enqueued_at: 0, // Will be set
            updated_at: 0, // Will be set
            process_after: 0, // Will be set
        };

        queue.enqueue(item.clone()).await?;

        // Test deduplication
        queue.enqueue(item).await?;

        // Dequeue the item
        let dequeued = queue.dequeue().await?;
        assert!(dequeued.is_some());
        let dequeued = dequeued.unwrap();
        assert_eq!(dequeued.dedup_key, Some("test-key".to_string()));
        assert_eq!(dequeued.payload, b"test payload".to_vec());
        assert_eq!(dequeued.status, QueueItemStatus::InFlight);
        assert_eq!(dequeued.attempts, 1);

        // Mark as processed
        queue.mark_processed(&dequeued.id).await?;

        // Queue should be empty now
        let empty = queue.dequeue().await?;
        assert!(empty.is_none());

        // Check metrics
        let metrics = queue.metrics().await?;
        assert_eq!(metrics.total_items, 1);
        assert_eq!(metrics.pending_items, 0);
        assert_eq!(metrics.in_flight_items, 0);
        assert_eq!(metrics.processed_items, 1);

        // Close the queue
        queue.close().await?;

        Ok(())
    }

    #[tokio::test]
    async fn test_queue_backpressure() -> Result<()> {
        let temp_dir = tempdir()?;
        let db_path = temp_dir.path().join("test_backpressure.db");

        let config = QueueConfig {
            db_path: db_path.to_string_lossy().to_string(),
            max_size_bytes: 100, // Very small for testing
            high_watermark_percent: 80,
            low_watermark_percent: 60,
            max_connections: 5,
            encrypt: false,
            retention_secs: 3600,
            compression_level: 0,
        };

        let queue = DurableQueue::new(config, None).await?;

        // Enqueue enough items to trigger backpressure
        let large_payload = vec![0u8; 90]; // 90 bytes
        let item = QueueItem {
            id: "".to_string(),
            dedup_key: None,
            payload: large_payload,
            status: QueueItemStatus::Pending,
            attempts: 0,
            enqueued_at: 0,
            updated_at: 0,
            process_after: 0,
        };

        queue.enqueue(item).await?;

        // Force metrics update and watermark check
        queue.update_metrics().await?;
        queue.check_watermarks().await?;

        // Queue should be in backpressure state
        let state = *queue.state.read().await;
        assert_eq!(state, QueueState::Backpressure);

        // Trying to enqueue more should fail
        let result = queue.enqueue(QueueItem {
            id: "".to_string(),
            dedup_key: None,
            payload: vec![0u8; 10],
            status: QueueItemStatus::Pending,
            attempts: 0,
            enqueued_at: 0,
            updated_at: 0,
            process_after: 0,
        }).await;

        assert!(result.is_err());

        Ok(())
    }
}
