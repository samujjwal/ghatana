//! Schema definitions for the durable queue

use serde::{Deserialize, Serialize};
use std::path::PathBuf;
use std::time::SystemTime;

/// Status of a queue item
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize, sqlx::Type)]
#[sqlx(type_name = "TEXT")]
pub enum QueueItemStatus {
    /// Item is pending processing
    Pending,
    /// Item is being processed
    Processing,
    /// Item has been processed successfully
    Completed,
    /// Item processing failed and will be retried
    Failed,
    /// Item processing failed permanently
    DeadLetter,
}

impl std::fmt::Display for QueueItemStatus {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            QueueItemStatus::Pending => write!(f, "PENDING"),
            QueueItemStatus::Processing => write!(f, "PROCESSING"),
            QueueItemStatus::Completed => write!(f, "COMPLETED"),
            QueueItemStatus::Failed => write!(f, "FAILED"),
            QueueItemStatus::DeadLetter => write!(f, "DEAD_LETTER"),
        }
    }
}

impl std::str::FromStr for QueueItemStatus {
    type Err = String;

    fn from_str(s: &str) -> Result<Self, Self::Err> {
        match s {
            "PENDING" => Ok(QueueItemStatus::Pending),
            "PROCESSING" => Ok(QueueItemStatus::Processing),
            "COMPLETED" => Ok(QueueItemStatus::Completed),
            "FAILED" => Ok(QueueItemStatus::Failed),
            "DEAD_LETTER" => Ok(QueueItemStatus::DeadLetter),
            _ => Err(format!("Invalid QueueItemStatus: {}", s)),
        }
    }
}

/// Configuration for the durable queue
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct QueueConfig {
    /// Path to the SQLite database file
    pub database_path: PathBuf,
    
    /// Maximum number of database connections in the pool
    pub max_connections: u32,
    
    /// Maximum number of items to buffer in memory before flushing to disk
    pub max_buffer_size: usize,
    
    /// Number of items to include in each batch when flushing to disk
    pub batch_size: usize,
    
    /// Maximum number of items to process in a single batch
    pub max_batch_size: usize,
    
    /// Interval in seconds between automatic flushes
    pub flush_interval_secs: u64,
    
    /// Whether to enable compression of queue items
    pub enable_compression: bool,
    
    /// Compression level (1-22, higher = better compression but slower)
    pub compression_level: i32,
    
    /// Whether to enable encryption of queue items
    pub enable_encryption: bool,
    
    /// Encryption key (must be 16, 24, or 32 bytes for AES-128, AES-192, or AES-256)
    pub encryption_key: Option<String>,
    
    /// Whether to enable metrics collection
    pub enable_metrics: bool,
    
    /// High watermark for queue size (bytes). When exceeded, backpressure is applied.
    pub high_watermark_bytes: Option<u64>,
    
    /// Low watermark for queue size (bytes). When below this, backpressure is released.
    pub low_watermark_bytes: Option<u64>,
    
    /// Maximum time an item can stay in the queue (seconds). Older items will be dropped.
    pub max_item_age_secs: Option<u64>,
    
    /// Maximum number of items in the queue. When exceeded, oldest items are dropped.
    pub max_queue_size: Option<usize>,
    
    /// Whether to drop items when queue is full (false = block producers)
    pub drop_when_full: bool,
}

impl Default for QueueConfig {
    fn default() -> Self {
        Self {
            database_path: PathBuf::from("queue.db"),
            max_connections: 5,
            max_buffer_size: 1000,
            batch_size: 100,
            max_batch_size: 1000,
            high_watermark_bytes: Some(100 * 1024 * 1024), // 100MB
            low_watermark_bytes: Some(50 * 1024 * 1024),   // 50MB
            max_item_age_secs: Some(7 * 24 * 60 * 60),     // 1 week
            max_queue_size: Some(100_000),                 // 100k items
            drop_when_full: false,
            flush_interval_secs: 1,
            enable_compression: true,
            compression_level: 3,
            enable_encryption: false,
            encryption_key: None,
            enable_metrics: true,
        }
    }
}

/// State of the queue
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct QueueState {
    /// Number of items currently in the queue
    pub item_count: usize,
    
    /// Total size of all items in the queue in bytes
    pub total_size_bytes: u64,
    
    /// Number of items processed successfully
    pub processed_count: u64,
    
    /// Number of items that failed processing
    pub error_count: u64,
    
    /// Number of items dropped due to queue being full
    pub dropped_count: u64,
    
    /// Number of items currently being processed
    pub processing_count: usize,
    
    /// Timestamp when the queue was created
    pub created_at: std::time::SystemTime,
    
    /// Timestamp of the last item enqueued
    pub last_enqueued_at: Option<std::time::SystemTime>,
    
    /// Timestamp of the last item dequeued
    pub last_dequeued_at: Option<std::time::SystemTime>,
    
    /// Timestamp of the last flush to disk
    pub last_flush_at: Option<std::time::SystemTime>,
    
    /// Whether the queue is currently full (above high watermark)
    pub is_full: bool,
    
    /// Whether the queue is currently in backpressure mode
    pub backpressure_active: bool,
}

impl QueueState {
    /// Create a new QueueState with default values
    pub fn new() -> Self {
        Self {
            item_count: 0,
            total_size_bytes: 0,
            processed_count: 0,
            error_count: 0,
            dropped_count: 0,
            processing_count: 0,
            created_at: std::time::SystemTime::now(),
            last_enqueued_at: None,
            last_dequeued_at: None,
            last_flush_at: None,
            is_full: false,
            backpressure_active: false,
        }
    }

    /// Check if the queue is full based on the provided configuration
    pub fn is_full(&self, config: &QueueConfig) -> bool {
        // Check if we've exceeded the maximum number of items
        if let Some(max_size) = config.max_queue_size {
            if self.item_count >= max_size {
                return true;
            }
        }

        // Check if we've exceeded the high watermark for size
        if let Some(high_watermark) = config.high_watermark_bytes {
            if self.total_size_bytes >= high_watermark {
                return true;
            }
        }

        false
    }

    /// Check if the queue is below the low watermark
    pub fn is_below_low_watermark(&self, config: &QueueConfig) -> bool {
        if let Some(low_watermark) = config.low_watermark_bytes {
            self.total_size_bytes < low_watermark
        } else {
            // If no low watermark is set, consider it always below
            true
        }
    }

    /// Get the current queue utilization as a percentage (0.0 to 1.0)
    pub fn utilization(&self, config: &QueueConfig) -> f64 {
        let size_util = if let Some(high_watermark) = config.high_watermark_bytes {
            (self.total_size_bytes as f64 / high_watermark as f64).min(1.0)
        } else {
            0.0
        };

        let count_util = if let Some(max_size) = config.max_queue_size {
            (self.item_count as f64 / max_size as f64).min(1.0)
        } else {
            0.0
        };

        size_util.max(count_util)
    }
}

impl Default for QueueState {
    fn default() -> Self {
        Self::new()
    }
}

/// An item in the queue
#[derive(Debug, Clone)]
pub struct QueueItem {
    /// Unique identifier for the item
    pub id: i64,
    
    /// Serialized and optionally compressed/encrypted item data
    pub data: Vec<u8>,
    
    /// Current status of the item
    pub status: QueueItemStatus,
    
    /// When the item was created
    pub created_at: SystemTime,
    
    /// When the item was last updated
    pub updated_at: SystemTime,
    
    /// Number of times processing has been attempted
    pub retry_count: u32,
    
    /// Last error message (if any)
    pub last_error: Option<String>,
}

/// Database representation of a queue item
#[allow(dead_code)] // Database record fields used by sqlx but not directly accessed
#[derive(Debug, sqlx::FromRow)]
pub struct QueueItemRecord {
    /// Unique identifier
    pub id: i64,
    
    /// Serialized and optionally compressed/encrypted item data
    pub data: Vec<u8>,
    
    /// Current status
    pub status: String,
    
    /// When the item was created (UNIX timestamp)
    pub created_at: i64,
    
    /// When the item was last updated (UNIX timestamp)
    pub updated_at: i64,
    
    /// Number of times processing has been attempted
    pub retry_count: i32,
    
    /// Last error message (if any)
    pub last_error: Option<String>,
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::str::FromStr;
    
    #[test]
    fn test_queue_item_status_serialization() {
        let status = QueueItemStatus::Pending;
        assert_eq!(status.to_string(), "PENDING");
        assert_eq!(QueueItemStatus::from_str("PENDING").unwrap(), status);
        
        let status = QueueItemStatus::Processing;
        assert_eq!(status.to_string(), "PROCESSING");
        assert_eq!(QueueItemStatus::from_str("PROCESSING").unwrap(), status);
        
        let status = QueueItemStatus::Completed;
        assert_eq!(status.to_string(), "COMPLETED");
        assert_eq!(QueueItemStatus::from_str("COMPLETED").unwrap(), status);
        
        let status = QueueItemStatus::Failed;
        assert_eq!(status.to_string(), "FAILED");
        assert_eq!(QueueItemStatus::from_str("FAILED").unwrap(), status);
        
        let status = QueueItemStatus::DeadLetter;
        assert_eq!(status.to_string(), "DEAD_LETTER");
        assert_eq!(QueueItemStatus::from_str("DEAD_LETTER").unwrap(), status);
        
        assert!(QueueItemStatus::from_str("INVALID").is_err());
    }
    
    #[test]
    fn test_queue_config_default() {
        let config = QueueConfig::default();
        assert_eq!(config.database_path, PathBuf::from("queue.db"));
        assert_eq!(config.max_connections, 5);
        assert_eq!(config.max_buffer_size, 1000);
        assert_eq!(config.batch_size, 100);
        assert_eq!(config.max_batch_size, 1000);
        assert_eq!(config.flush_interval_secs, 1);
        assert!(config.enable_compression);
        assert_eq!(config.compression_level, 3);
        assert!(!config.enable_encryption);
        assert!(config.encryption_key.is_none());
        assert!(config.enable_metrics);
    }
    
    #[test]
    fn test_queue_state_default() {
        let state = QueueState::default();
        assert_eq!(state.item_count, 0);
        assert_eq!(state.total_size_bytes, 0);
        assert_eq!(state.processed_count, 0);
        assert_eq!(state.error_count, 0);
        assert_eq!(state.dropped_count, 0);
        assert_eq!(state.processing_count, 0);
        assert!(state.last_enqueued_at.is_none());
        assert!(state.last_dequeued_at.is_none());
        assert!(state.last_flush_at.is_none());
        assert!(!state.is_full);
        assert!(!state.backpressure_active);
    }
}
