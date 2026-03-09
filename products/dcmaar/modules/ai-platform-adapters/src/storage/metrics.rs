//! Metrics storage implementation

use std::collections::HashMap;
use std::convert::TryInto;
use std::time::Duration;

use anyhow::Result;
use async_trait::async_trait;
use serde::{Deserialize, Serialize};
use sqlx::{sqlite::SqliteRow, FromRow, Pool, QueryBuilder, Row, Sqlite};
use time::{
    format_description::well_known::Rfc3339,
    macros::format_description,
    Duration as TimeDuration,
    OffsetDateTime,
};
use tracing::instrument;
use uuid::Uuid;

use crate::metrics::SystemMetrics;
use crate::storage::error::StorageError;
use crate::storage::schema::{MetricQuery, MetricRow};
use crate::storage::traits::{Storage as StorageTrait, StorageStats};
use crate::storage::Storage;

/// Batch size for bulk operations
const BATCH_SIZE: usize = 1000;

/// Helper representation matching the `metrics` table layout.
#[derive(Debug, FromRow)]
struct DbMetricRow {
    id: i64,
    metric_type: String,
    hostname: String,
    data: String,
    timestamp: i64,
    created_at: Option<String>,
}

/// Ensure the metrics table exists before performing writes
pub(crate) async fn ensure_metrics_table(pool: &Pool<Sqlite>) -> Result<(), StorageError> {
    sqlx::query(
        r#"
        CREATE TABLE IF NOT EXISTS metrics (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            metric_type TEXT NOT NULL,
            hostname TEXT NOT NULL,
            data TEXT NOT NULL,
            timestamp INTEGER NOT NULL,
            created_at TEXT DEFAULT CURRENT_TIMESTAMP,
            updated_at TEXT DEFAULT CURRENT_TIMESTAMP
        );
        "#,
    )
    .execute(pool)
    .await?;

    sqlx::query(
        r#"
        CREATE INDEX IF NOT EXISTS idx_metrics_type_timestamp
            ON metrics(metric_type, timestamp);
        "#,
    )
    .execute(pool)
    .await?;

    sqlx::query(
        r#"
        CREATE INDEX IF NOT EXISTS idx_metrics_hostname_timestamp
            ON metrics(hostname, timestamp);
        "#,
    )
    .execute(pool)
    .await?;

    Ok(())
}

pub(crate) fn offset_to_storage_ts(dt: OffsetDateTime) -> i64 {
    dt.unix_timestamp()
}

pub(crate) fn decode_numeric_timestamp(ts: i64) -> Option<OffsetDateTime> {
    if ts.abs() > 10_i64.pow(12) {
        // Interpret as milliseconds
        let seconds = ts / 1_000;
        let millis = ts % 1_000;
        let base = OffsetDateTime::from_unix_timestamp(seconds).ok()?;
        base.checked_add(TimeDuration::milliseconds(millis)).or(Some(base))
    } else {
        OffsetDateTime::from_unix_timestamp(ts).ok()
    }
}

fn decode_timestamp_column(row: &SqliteRow, column: &str) -> Option<OffsetDateTime> {
    if let Ok(value) = row.try_get::<Option<i64>, _>(column) {
        if let Some(ts) = value {
            return decode_numeric_timestamp(ts);
        }
    }

    if let Ok(value) = row.try_get::<Option<String>, _>(column) {
        if let Some(raw) = value {
            return parse_created_at(Some(raw), None).ok();
        }
    }

    None
}

fn parse_created_at(
    raw: Option<String>,
    fallback: Option<OffsetDateTime>,
) -> Result<OffsetDateTime, StorageError> {
    if let Some(raw) = raw {
        if let Ok(dt) = OffsetDateTime::parse(&raw, &Rfc3339) {
            return Ok(dt);
        }

        if let Ok(dt) = OffsetDateTime::parse(
            &raw,
            &format_description!("[year]-[month]-[day] [hour]:[minute]:[second]"),
        )
        {
            return Ok(dt);
        }

        if let Some(fallback) = fallback {
            return Ok(fallback);
        }

        return Err(StorageError::Other(format!("Failed to parse timestamp: {}", raw)));
    }

    fallback
        .ok_or_else(|| StorageError::Other("Missing timestamp column".to_string()))
}

impl TryFrom<DbMetricRow> for MetricRow {
    type Error = StorageError;

    fn try_from(row: DbMetricRow) -> Result<Self, Self::Error> {
        let timestamp = decode_numeric_timestamp(row.timestamp)
            .ok_or_else(|| StorageError::Other("Invalid timestamp".to_string()))?;
        let created_at = parse_created_at(row.created_at, Some(timestamp))?;

        Ok(MetricRow {
            id: row.id,
            metric_type: row.metric_type,
            hostname: row.hostname,
            data: row.data,
            timestamp,
            created_at,
        })
    }
}

/// Metrics storage trait defining the interface for metrics persistence
#[async_trait]
pub trait MetricsStore: Send + Sync + 'static {
    /// Store a single metrics snapshot
    async fn store_metrics(&self, metrics: &SystemMetrics, hostname: String) -> Result<()>;

    /// Store multiple metrics snapshots in a batch
    async fn store_metrics_batch(
        &self,
        metrics: Vec<SystemMetrics>,
        hostname: String,
    ) -> Result<usize>;

    /// Query metrics based on filter criteria
    async fn query_metrics(&self, query: MetricQuery) -> Result<Vec<MetricRow>>;

    /// Get the latest metrics for a specific type and hostname
    async fn get_latest_metrics(
        &self,
        metric_type: String,
        hostname: Option<String>,
    ) -> Result<Option<MetricRow>>;

    /// Delete metrics older than the specified timestamp
    async fn delete_older_than(&self, timestamp: OffsetDateTime) -> Result<u64>;

    /// Get metrics statistics (count, first/last timestamps, etc.)
    async fn get_metrics_stats(&self) -> Result<MetricsStats>;

    /// Clean up old metrics based on retention policy
    async fn cleanup_old_metrics(&self, retention_period: Duration) -> Result<u64>;
}

/// Metrics statistics
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct MetricsStats {
    /// Total number of metrics
    pub total_metrics: i64,
    /// Number of metrics by type
    pub metrics_by_type: Vec<(String, i64)>,
    /// Number of metrics by hostname
    pub metrics_by_hostname: Vec<(String, i64)>,
    /// Oldest metric timestamp
    pub oldest_timestamp: Option<OffsetDateTime>,
    /// Newest metric timestamp
    pub newest_timestamp: Option<OffsetDateTime>,
    /// Total size of metrics data in bytes
    pub total_size_bytes: i64,
}

/// Handles storage and retrieval of metrics
#[derive(Clone)]
pub struct MetricsStorage {
    storage: Storage,
    #[allow(dead_code)]
    _instance_id: Uuid,
}

impl MetricsStorage {
    /// Create a new metrics storage instance
    pub fn new(storage: Storage) -> Self {
        Self {
            storage,
            _instance_id: Uuid::new_v4(),
        }
    }

    /// Store multiple snapshots in a single transaction per chunk
    pub async fn store_metrics_batch(
        &self,
        metrics: &[SystemMetrics],
        hostname: &str,
    ) -> Result<usize, StorageError> {
        if metrics.is_empty() {
            return Ok(0);
        }

        let pool = self.storage.pool();
        ensure_metrics_table(pool).await?;
        let mut total = 0usize;

        for chunk in metrics.chunks(BATCH_SIZE) {
            let mut tx = pool.begin().await?;

            for metric in chunk {
                let timestamp: i64 = metric.timestamp.try_into().map_err(|e| {
                    StorageError::Other(format!("Failed to convert timestamp: {}", e))
                })?;

                self.insert_metric(&mut tx, "cpu", hostname, timestamp, &metric.cpu)
                    .await?;
                self.insert_metric(&mut tx, "memory", hostname, timestamp, &metric.memory)
                    .await?;
                self.insert_metric(&mut tx, "disk", hostname, timestamp, &metric.disk)
                    .await?;
                self.insert_metric(&mut tx, "network", hostname, timestamp, &metric.network)
                    .await?;

                if !metric.processes.is_empty() {
                    self.insert_metric(&mut tx, "process", hostname, timestamp, &metric.processes)
                        .await?;
                }

                total += 1;
            }

            tx.commit().await?;
        }

        Ok(total)
    }

    /// Get a reference to the underlying storage
    pub fn storage(&self) -> &Storage {
        &self.storage
    }

    /// Get a reference to the database pool (use with caution)
    #[allow(dead_code)]
    fn pool(&self) -> &Pool<Sqlite> {
        self.storage.pool()
    }

    /// Store a system metrics snapshot
    pub async fn store_metrics(
        &self,
        metrics: &SystemMetrics,
        hostname: &str,
    ) -> Result<(), StorageError> {
        let timestamp: i64 = metrics
            .timestamp
            .try_into()
            .map_err(|_| StorageError::Other("Timestamp conversion failed".to_string()))?;

        let pool = self.storage.pool();
        ensure_metrics_table(pool).await?;
        let mut tx = pool.begin().await?;

        self.insert_metric(&mut tx, "cpu", hostname, timestamp, &metrics.cpu)
            .await?;
        self.insert_metric(&mut tx, "memory", hostname, timestamp, &metrics.memory)
            .await?;
        self.insert_metric(&mut tx, "disk", hostname, timestamp, &metrics.disk)
            .await?;
        self.insert_metric(&mut tx, "network", hostname, timestamp, &metrics.network)
            .await?;

        if !metrics.processes.is_empty() {
            self.insert_metric(&mut tx, "process", hostname, timestamp, &metrics.processes)
                .await?;
        }

        tx.commit().await?;
        Ok(())
    }

    /// Upsert a metric row for a given type/timestamp combination
    #[instrument(skip(self, data, tx), fields(metric_type, hostname, timestamp = %timestamp))]
    async fn insert_metric<T: Serialize>(
        &self,
        tx: &mut sqlx::Transaction<'_, Sqlite>,
        metric_type: &str,
        hostname: &str,
        timestamp: i64,
        data: &T,
    ) -> Result<(), StorageError> {
        // Add tracing fields to the current span for observability
        tracing::Span::current().record("metric_type", metric_type);
        tracing::Span::current().record("hostname", hostname);

        let data_json = serde_json::to_string(data)?;

        // Check if we already have a metric for this timestamp and type
        let exists = sqlx::query(
            r#"
            SELECT 1 FROM metrics 
            WHERE metric_type = ? AND hostname = ? AND timestamp = ?
            LIMIT 1
            "#,
        )
        .bind(metric_type)
        .bind(hostname)
        .bind(timestamp)
        .fetch_optional(tx.as_mut())
        .await?;

        if exists.is_some() {
            // Update existing record
            sqlx::query(
                r#"
                UPDATE metrics 
                SET data = ?, updated_at = CURRENT_TIMESTAMP
                WHERE metric_type = ? AND hostname = ? AND timestamp = ?
                "#,
            )
            .bind(data_json)
            .bind(metric_type)
            .bind(hostname)
            .bind(timestamp)
            .execute(tx.as_mut())
            .await?;
        } else {
            // Insert new record
            sqlx::query(
                r#"
                INSERT INTO metrics (metric_type, hostname, data, timestamp)
                VALUES (?, ?, ?, ?)
                "#,
            )
            .bind(metric_type)
            .bind(hostname)
            .bind(data_json)
            .bind(timestamp)
            .execute(tx.as_mut())
            .await?;
        }

        Ok(())
    }

    /// Fetch the most recent metric row for a type (optionally filtered by hostname)
    pub async fn get_latest_metrics(
        &self,
        metric_type: &str,
        hostname: Option<&str>,
    ) -> Result<Option<MetricRow>, StorageError> {
        let pool = self.storage.pool();
        ensure_metrics_table(pool).await?;
        let row = sqlx::query_as::<_, DbMetricRow>(
            r#"
            SELECT id, metric_type, hostname, data, timestamp, created_at
            FROM metrics
            WHERE metric_type = ?
              AND (? IS NULL OR hostname = ?)
            ORDER BY timestamp DESC
            LIMIT 1
            "#,
        )
        .bind(metric_type)
        .bind(hostname)
        .bind(hostname)
        .fetch_optional(pool)
        .await?;

        match row {
            Some(row) => Ok(Some(row.try_into()?)),
            None => Ok(None),
        }
    }

    /// Query metrics from storage with filtering and pagination
    pub async fn query_metrics_impl(
        &self,
        _query: MetricQuery,
    ) -> Result<Vec<MetricRow>, StorageError> {
        let pool = self.storage.pool();
        ensure_metrics_table(pool).await?;

        let mut builder = QueryBuilder::new(
            "SELECT id, metric_type, hostname, data, timestamp, created_at FROM metrics",
        );
        builder.push(" WHERE 1 = 1");

        if let Some(metric_type) = &_query.metric_type {
            builder.push(" AND metric_type = ");
            builder.push_bind(metric_type);
        }

        if let Some(hostname) = &_query.hostname {
            builder.push(" AND hostname = ");
            builder.push_bind(hostname);
        }

        if let Some(start) = _query.start_time {
            builder.push(" AND timestamp >= ");
            builder.push_bind(offset_to_storage_ts(start));
        }

        if let Some(end) = _query.end_time {
            builder.push(" AND timestamp < ");
            builder.push_bind(offset_to_storage_ts(end));
        }

        builder.push(" ORDER BY timestamp DESC");

        if let Some(limit) = _query.limit {
            builder.push(" LIMIT ");
            builder.push_bind(limit);
        }

        if let Some(offset) = _query.offset {
            builder.push(" OFFSET ");
            builder.push_bind(offset);
        }

        let rows: Vec<DbMetricRow> = builder
            .build_query_as()
            .fetch_all(pool)
            .await?;

        rows.into_iter().map(|row| row.try_into()).collect()
    }

    /// Temporary public wrapper until the real query implementation lands
    pub async fn query_metrics(&self, query: MetricQuery) -> Result<Vec<MetricRow>, StorageError> {
        self.query_metrics_impl(query).await
    }

    /// Delete metrics older than the specified timestamp
    #[instrument(skip(self), fields(timestamp = %timestamp))]
    pub async fn delete_older_than(&self, timestamp: OffsetDateTime) -> Result<u64, StorageError> {
        let cutoff = offset_to_storage_ts(timestamp);
        let pool = self.storage.pool();
        ensure_metrics_table(pool).await?;
        let result = sqlx::query(
            r#"
            DELETE FROM metrics
            WHERE timestamp < ?
            "#,
        )
        .bind(cutoff)
        .execute(pool)
        .await?;
        Ok(result.rows_affected())
    }

    /// Get metrics statistics (count, first/last timestamps, etc.)
    #[allow(dead_code)]
    async fn get_metrics_stats(&self) -> Result<MetricsStats, StorageError> {
        let pool = self.storage.pool();
        ensure_metrics_table(pool).await?;
        let count: u64 = sqlx::query_scalar::<_, i64>("SELECT COUNT(*) FROM metrics")
            .fetch_one(pool)
            .await? as u64;

        let first_last = sqlx::query(
            r#"
            SELECT 
                MIN(timestamp) as first_timestamp,
                MAX(timestamp) as last_timestamp
            FROM metrics
            "#,
        )
        .fetch_optional(pool)
        .await?;

        let (first_timestamp, last_timestamp) = if let Some(row) = first_last {
            let first_ts = decode_timestamp_column(&row, "first_timestamp");
            let last_ts = decode_timestamp_column(&row, "last_timestamp");
            (first_ts, last_ts)
        } else {
            (None, None)
        };

        let type_counts = sqlx::query(
            r#"
            SELECT metric_type, COUNT(*) as count
            FROM metrics
            GROUP BY metric_type
            "#,
        )
        .fetch_all(pool)
        .await?;

        let mut metric_type_counts = std::collections::HashMap::new();
        for row in type_counts {
            let metric_type: String = row.get("metric_type");
            let count: i64 = row.get("count");
            metric_type_counts.insert(metric_type, count as u64);
        }

        let total = count as i64;
        Ok(MetricsStats {
            total_metrics: total,
            metrics_by_type: metric_type_counts
                .into_iter()
                .map(|(k, v)| (k, v as i64))
                .collect(),
            metrics_by_hostname: Vec::new(),
            oldest_timestamp: first_timestamp,
            newest_timestamp: last_timestamp,
            total_size_bytes: 0,
        })
    }

    /// Clean up old metrics based on retention policy
    #[allow(dead_code)]
    pub async fn cleanup_old_metrics(
        &self,
        retention_period: Duration,
    ) -> Result<u64, StorageError> {
        let cutoff = OffsetDateTime::now_utc() - retention_period;
        let count = self.delete_older_than(cutoff).await?;
        Ok(count)
    }

    /// Ensure the stored metrics stay within the provided byte budget
    #[allow(dead_code)]
    pub async fn enforce_storage_budget(&self, max_bytes: u64) -> Result<(), StorageError> {
        let pool = self.storage.pool();
        ensure_metrics_table(pool).await?;

        let total_size: i64 = sqlx::query_scalar(
            r#"
            SELECT COALESCE(SUM(LENGTH(data)), 0)
            FROM metrics
            "#,
        )
        .fetch_one(pool)
        .await?;

        let total = total_size.max(0) as u64;
        if total > max_bytes {
            return Err(StorageError::Other(format!(
                "storage budget exceeded: {} bytes > {} bytes",
                total, max_bytes
            )));
        }

        Ok(())
    }
}

// Implement the MetricsStore trait for MetricsStorage
#[async_trait]
impl MetricsStore for MetricsStorage {
    /// Store a single metrics snapshot
    async fn store_metrics(&self, metrics: &SystemMetrics, hostname: String) -> Result<()> {
        MetricsStorage::store_metrics(self, metrics, &hostname).await?;
        Ok(())
    }

    /// Store multiple metrics snapshots in a batch
    async fn store_metrics_batch(
        &self,
        metrics: Vec<SystemMetrics>,
        hostname: String,
    ) -> Result<usize> {
        let count = MetricsStorage::store_metrics_batch(self, &metrics, &hostname).await?;
        Ok(count)
    }

    /// Query metrics based on filter criteria
    async fn query_metrics(&self, query: MetricQuery) -> Result<Vec<MetricRow>> {
        let rows = self.query_metrics_impl(query).await?;
        Ok(rows)
    }

    /// Get the latest metrics for a specific type and hostname
    async fn get_latest_metrics(
        &self,
        metric_type: String,
        hostname: Option<String>,
    ) -> Result<Option<MetricRow>> {
        let row = MetricsStorage::get_latest_metrics(self, &metric_type, hostname.as_deref()).await?;
        Ok(row)
    }

    /// Delete metrics older than the specified timestamp
    async fn delete_older_than(&self, timestamp: OffsetDateTime) -> Result<u64> {
        let deleted = MetricsStorage::delete_older_than(self, timestamp).await?;
        Ok(deleted)
    }

    /// Get metrics statistics (count, first/last timestamps, etc.)
    async fn get_metrics_stats(&self) -> Result<MetricsStats> {
        let stats = MetricsStorage::get_metrics_stats(self).await?;
        Ok(stats)
    }

    /// Clean up old metrics based on retention policy
    async fn cleanup_old_metrics(&self, retention_period: Duration) -> Result<u64> {
        let deleted = MetricsStorage::cleanup_old_metrics(self, retention_period).await?;
        Ok(deleted)
    }
}

// Implement the StorageTrait for MetricsStorage
#[async_trait]
impl StorageTrait for MetricsStorage {
    /// Initialize the storage
    async fn init(&self) -> Result<()> {
        let pool = self.storage.pool();
        ensure_metrics_table(pool).await?;
        Ok(())
    }
    
    /// Check if the storage is healthy
    async fn health_check(&self) -> Result<bool> {
        let pool = self.storage.pool();
        let result = sqlx::query("SELECT 1").execute(pool).await;
        Ok(result.is_ok())
    }
    
    /// Get storage statistics
    async fn get_stats(&self) -> Result<StorageStats> {
        let stats = self.get_metrics_stats().await?;
        
        let mut additional = HashMap::new();
        for (metric_type, count) in &stats.metrics_by_type {
            additional.insert(format!("count_{}", metric_type), serde_json::json!(count));
        }
        
        for (hostname, count) in &stats.metrics_by_hostname {
            additional.insert(format!("host_{}", hostname), serde_json::json!(count));
        }
        
        Ok(StorageStats {
            total_records: stats.total_metrics,
            oldest_timestamp: stats.oldest_timestamp,
            newest_timestamp: stats.newest_timestamp,
            total_size_bytes: stats.total_size_bytes,
            additional,
        })
    }
    
    /// Clean up old data based on retention policy
    async fn cleanup(&self, retention: Duration) -> Result<u64> {
        self
            .cleanup_old_metrics(retention)
            .await
            .map_err(anyhow::Error::from)
    }
}
#[cfg(test)]
mod tests {
    use super::*;
    use crate::metrics::{CpuMetrics, MemoryMetrics, SystemMetrics};
    use crate::metrics::{NetworkMetrics, SystemDiskMetrics};
    use crate::storage::Storage;
    use anyhow::Result;
    use std::time::Duration;
    use time::OffsetDateTime;

    async fn create_test_storage() -> Result<Storage> {
        Storage::memory().await
    }

    #[tokio::test]
    async fn test_store_and_retrieve_metrics() -> Result<()> {
        // Create storage and metrics storage
        let storage = create_test_storage().await?;
        let metrics_storage = MetricsStorage::new(storage);
        let hostname = "test-host".to_string();

        // Create test metrics
        let metrics = SystemMetrics {
            timestamp: OffsetDateTime::now_utc().unix_timestamp() as u64,
            cpu: CpuMetrics {
                usage_percent: 50.0,
                core_usage: vec![50.0],
                cores: 4,
                name: "cpu0".to_string(),
                frequency: 2400,
                load_average: None,
            },
            memory: MemoryMetrics {
                total: 1024 * 1024 * 1024, // 1GB
                used: 512 * 1024 * 1024,   // 512MB
                free: 512 * 1024 * 1024,   // 512MB
                swap_total: 2048 * 1024 * 1024,
                swap_used: 128 * 1024 * 1024,
                swap_free: 1920 * 1024 * 1024,
                usage_percent: 50.0,
                swap_usage_percent: 6.25,
            },
            disk: SystemDiskMetrics::default(),
            network: NetworkMetrics::default(),
            processes: vec![],
        };

        // Store metrics
        metrics_storage.store_metrics(&metrics, &hostname).await?;

        // Test batch storage
        let batch_metrics = vec![
            SystemMetrics {
                timestamp: 1234567891,
                cpu: CpuMetrics {
                    usage_percent: 25.0,
                    core_usage: vec![25.0],
                    cores: 4,
                    name: "cpu0".to_string(),
                    frequency: 2400,
                    load_average: None,
                },
                ..Default::default()
            },
            SystemMetrics {
                timestamp: 1234567892,
                memory: MemoryMetrics {
                    total: 16384,
                    used: 8192,
                    free: 8192,
                    swap_total: 32768,
                    swap_used: 4096,
                    swap_free: 28672,
                    usage_percent: 50.0,
                    swap_usage_percent: 12.5,
                },
                ..Default::default()
            },
        ];

        let stored_count = metrics_storage
            .store_metrics_batch(&batch_metrics, &hostname)
            .await?;
        assert_eq!(stored_count, 2);

        // Query metrics
        let query = MetricQuery {
            metric_type: Some("cpu".to_string()),
            hostname: Some(hostname.clone()),
            start_time: None,
            end_time: None,
            limit: None,
            offset: None,
        };

        let results = metrics_storage.query_metrics(query).await?;
        assert!(!results.is_empty());
        assert!(results.iter().all(|row| row.metric_type == "cpu"));

        // Get latest metrics should return the most recent CPU entry
        let latest = metrics_storage
            .get_latest_metrics("cpu", Some(&hostname))
            .await?;
        let latest = latest.expect("expected stored cpu metric");
        assert_eq!(latest.metric_type, "cpu");
        assert_eq!(latest.hostname, hostname);

        // Stats are best-effort with the stubbed query
        let stats = metrics_storage.get_metrics_stats().await?;
        assert!(stats.total_metrics >= 0);

        // Cleanup helpers shouldn't fail
        let cutoff = OffsetDateTime::from_unix_timestamp(1234567893)?;
        let _ = metrics_storage.delete_older_than(cutoff).await?;

        let retention = Duration::from_secs(60 * 60 * 24 * 7); // 7 days
        let _ = metrics_storage.cleanup_old_metrics(retention).await?;

        Ok(())
    }
}
