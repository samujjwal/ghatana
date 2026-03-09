//! Persistent storage for metrics and events

mod error;
mod lock;
pub mod compression;
pub mod compressed_storage;
pub mod metrics;
pub mod metrics_query;
pub mod monitoring;
pub mod schema;
pub mod timeseries;
pub mod traits;
pub mod query;
// mod migrations; // disabled until migrations directory is added

use std::path::Path;
use std::time::Duration;

use anyhow::Result;
use sqlx::{sqlite::SqliteConnectOptions, SqlitePool};

pub use error::StorageError;
pub use lock::DbLock;
pub use metrics::MetricsStorage;
pub use metrics_query::AggregatedMetric;
pub use monitoring::{StorageMonitor, MonitoringConfig, MonitoringStatus, MonitoringMetrics, MonitoringThresholds};
pub use schema::{EventQuery, EventRow, MetricQuery, MetricRow};
pub use timeseries::{TimeSeriesPoint, TimeSeriesQueryOptions, TimeSeriesStats, TimeSeriesUtils, TimeSeriesAggregation, DownsampleInterval};
pub use traits::{Storage as StorageTrait, StorageStats, QueryOptions, PaginationOptions, SortDirection, SortOptions, TimeRange, AggregationFunction};
pub use query::{MetricsQueryBuilder, PaginatedResult, PaginationInfo};
pub use compression::{CompressionAlgorithm, CompressionLevel, CompressionConfig, CompressionUtils, compress, decompress, compress_object, decompress_object};
pub use compressed_storage::{CompressedStorage, CompressibleStorage};

/// Default database lock timeout
const DEFAULT_LOCK_TIMEOUT: Duration = Duration::from_secs(30);

/// Database connection pool wrapper with cross-process locking
#[derive(Clone)]
pub struct Storage {
    pool: SqlitePool,
    lock: DbLock,
}

impl Storage {
    /// Create a new storage instance with an in-memory database
    pub async fn memory() -> Result<Self> {
        let pool = SqlitePool::connect(":memory:").await?;
        // Migrations disabled for now
        // Self::run_migrations(&pool).await?;
        let lock = DbLock::new(pool.clone(), DEFAULT_LOCK_TIMEOUT);
        Ok(Self { pool, lock })
    }

    /// Create a new storage instance with a file-based database
    pub async fn file<P: AsRef<Path>>(path: P) -> Result<Self> {
        let options = SqliteConnectOptions::new()
            .filename(path)
            .create_if_missing(true)
            .journal_mode(sqlx::sqlite::SqliteJournalMode::Wal);

        let pool = SqlitePool::connect_with(options).await?;
        // Migrations disabled for now
        // Self::run_migrations(&pool).await?;
        let lock = DbLock::new(pool.clone(), DEFAULT_LOCK_TIMEOUT);
        Ok(Self { pool, lock })
    }

    /// Run database migrations
    #[allow(dead_code)]
    async fn run_migrations(_pool: &SqlitePool) -> Result<()> {
        // Disabled until migrations are added
        Ok(())
    }

    /// Get a reference to the SQLite connection pool
    ///
    /// # Safety
    /// This bypasses the lock mechanism. Use with_lock() for safe access.
    pub fn pool(&self) -> &SqlitePool {
        &self.pool
    }

    /// Execute a function with an exclusive lock on the database
    ///
    /// This ensures safe concurrent access to the database from multiple processes.
    pub async fn with_lock<F, Fut, T, E>(&self, f: F) -> Result<T, E>
    where
        F: FnOnce(&SqlitePool) -> Fut + Send + 'static,
        Fut: std::future::Future<Output = Result<T, E>> + Send + 'static,
        T: Send + 'static,
        E: From<sqlx::Error> + Send + 'static,
    {
        self.lock.with_lock(f).await
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use tempfile::tempdir;

    #[tokio::test]
    async fn test_memory_storage() -> Result<()> {
        let storage = Storage::memory().await?;
        assert!(!storage.pool().is_closed());
        Ok(())
    }

    #[tokio::test]
    async fn test_file_storage() -> Result<()> {
        let temp_dir = tempdir()?;
        let db_path = temp_dir.path().join("test.db");

        let storage = Storage::file(&db_path).await?;
        assert!(!storage.pool().is_closed());
        assert!(db_path.exists());

        Ok(())
    }
}
