//! Time-series optimizations for SQLite storage
//!
//! This module provides optimizations for storing and querying time-series data
//! in SQLite, focusing on efficient storage and retrieval for a thin client.

use anyhow::Result;
use sqlx::{Pool, QueryBuilder, Row, Sqlite};
use time::OffsetDateTime;
use tracing::{debug, instrument};

use crate::storage::error::StorageError;

/// Time-series data point
#[derive(Debug, Clone)]
pub struct TimeSeriesPoint {
    /// Timestamp
    pub timestamp: OffsetDateTime,
    /// Value
    pub value: f64,
    /// Tags (optional)
    pub tags: Option<serde_json::Value>,
}

/// Time-series aggregation type
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum TimeSeriesAggregation {
    /// No aggregation (raw data)
    None,
    /// Average values over the interval
    Average,
    /// Sum values over the interval
    Sum,
    /// Minimum value in the interval
    Min,
    /// Maximum value in the interval
    Max,
    /// Count of values in the interval
    Count,
}

/// Time-series downsampling interval
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum DownsampleInterval {
    /// No downsampling (raw data)
    None,
    /// Minute interval
    Minute,
    /// Hour interval
    Hour,
    /// Day interval
    Day,
    /// Week interval
    Week,
    /// Month interval
    Month,
    /// Custom interval in seconds
    Custom(u64),
}

impl DownsampleInterval {
    /// Get the interval duration in seconds
    pub fn as_seconds(&self) -> Option<u64> {
        match self {
            DownsampleInterval::None => None,
            DownsampleInterval::Minute => Some(60),
            DownsampleInterval::Hour => Some(3600),
            DownsampleInterval::Day => Some(86400),
            DownsampleInterval::Week => Some(604800),
            DownsampleInterval::Month => Some(2592000), // 30 days
            DownsampleInterval::Custom(seconds) => Some(*seconds),
        }
    }
    
    /// Get the SQL function to use for downsampling
    pub fn sql_function(&self) -> Option<&'static str> {
        match self {
            DownsampleInterval::None => None,
            _ => Some("strftime"),
        }
    }
    
    /// Get the SQL format string for the interval
    pub fn sql_format(&self) -> Option<&'static str> {
        match self {
            DownsampleInterval::None => None,
            DownsampleInterval::Minute => Some("%Y-%m-%d %H:%M:00"),
            DownsampleInterval::Hour => Some("%Y-%m-%d %H:00:00"),
            DownsampleInterval::Day => Some("%Y-%m-%d 00:00:00"),
            DownsampleInterval::Week => Some("%Y-%W 00:00:00"),
            DownsampleInterval::Month => Some("%Y-%m-01 00:00:00"),
            DownsampleInterval::Custom(_) => Some("%Y-%m-%d %H:%M:%S"),
        }
    }
}

/// Time-series query options
#[derive(Debug, Clone)]
pub struct TimeSeriesQueryOptions {
    /// Start time (inclusive)
    pub start_time: Option<OffsetDateTime>,
    /// End time (exclusive)
    pub end_time: Option<OffsetDateTime>,
    /// Maximum number of points to return
    pub limit: Option<i64>,
    /// Number of points to skip
    pub offset: Option<i64>,
    /// Downsampling interval
    pub downsample: DownsampleInterval,
    /// Aggregation function
    pub aggregation: TimeSeriesAggregation,
    /// Filter by tags
    pub tags: Option<serde_json::Value>,
}

impl Default for TimeSeriesQueryOptions {
    fn default() -> Self {
        Self {
            start_time: None,
            end_time: None,
            limit: None,
            offset: None,
            downsample: DownsampleInterval::None,
            aggregation: TimeSeriesAggregation::None,
            tags: None,
        }
    }
}

/// Time-series storage utilities for SQLite
pub struct TimeSeriesUtils;

impl TimeSeriesUtils {
    /// Ensure the time-series table exists
    #[instrument(skip(pool))]
    pub async fn ensure_table(
        pool: &Pool<Sqlite>,
        table_name: &str,
    ) -> Result<(), StorageError> {
        sqlx::query(&format!(
            r#"
            CREATE TABLE IF NOT EXISTS {} (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                timestamp INTEGER NOT NULL,
                value REAL NOT NULL,
                tags TEXT,
                created_at TEXT DEFAULT CURRENT_TIMESTAMP
            );
            "#,
            table_name
        ))
        .execute(pool)
        .await?;

        // Create index on timestamp for efficient time-based queries
        sqlx::query(&format!(
            r#"
            CREATE INDEX IF NOT EXISTS idx_{}_timestamp
                ON {}(timestamp);
            "#,
            table_name, table_name
        ))
        .execute(pool)
        .await?;

        Ok(())
    }

    /// Store a time-series data point
    #[instrument(skip(pool, point))]
    pub async fn store_point(
        pool: &Pool<Sqlite>,
        table_name: &str,
        point: &TimeSeriesPoint,
    ) -> Result<(), StorageError> {
        let timestamp = point.timestamp.unix_timestamp();
        let tags_json = point
            .tags
            .as_ref()
            .map(serde_json::to_string)
            .transpose()?;

        sqlx::query(&format!(
            r#"
            INSERT INTO {} (timestamp, value, tags)
            VALUES (?, ?, ?)
            "#,
            table_name
        ))
        .bind(timestamp)
        .bind(point.value)
        .bind(tags_json)
        .execute(pool)
        .await?;

        Ok(())
    }

    /// Store multiple time-series data points in a batch
    #[instrument(skip(pool, points))]
    pub async fn store_points_batch(
        pool: &Pool<Sqlite>,
        table_name: &str,
        points: &[TimeSeriesPoint],
    ) -> Result<usize, StorageError> {
        if points.is_empty() {
            return Ok(0);
        }

        let mut rows = Vec::with_capacity(points.len());
        for point in points {
            let timestamp = point.timestamp.unix_timestamp();
            let tags_json = point
                .tags
                .as_ref()
                .map(serde_json::to_string)
                .transpose()?;
            rows.push((timestamp, point.value, tags_json));
        }

        let mut builder = QueryBuilder::new(format!(
            "INSERT INTO {} (timestamp, value, tags) ",
            table_name
        ));

        builder.push_values(rows.iter(), |mut b, row| {
            b.push_bind(row.0);
            b.push_bind(row.1);
            b.push_bind(row.2.as_deref());
        });

        let result = builder.build().execute(pool).await?;

        Ok(result.rows_affected() as usize)
    }

    /// Query time-series data with optional downsampling and aggregation
    #[instrument(skip(pool, options))]
    pub async fn query_points(
        pool: &Pool<Sqlite>,
        table_name: &str,
        options: &TimeSeriesQueryOptions,
    ) -> Result<Vec<TimeSeriesPoint>, StorageError> {
        let mut builder = QueryBuilder::new("SELECT ");

        // Handle downsampling and aggregation
        if options.downsample == DownsampleInterval::None {
            builder.push("timestamp, value, tags");
        } else {
            // Apply downsampling with strftime
            let interval_fmt = options.downsample.sql_format().unwrap();
            
            builder.push("strftime('");
            builder.push(interval_fmt);
            builder.push("', datetime(timestamp, 'unixepoch')) as ts_group, ");
            
            // Apply aggregation function
            let agg_expr = match options.aggregation {
                TimeSeriesAggregation::None => "AVG(value)", // Default to AVG if no specific aggregation
                TimeSeriesAggregation::Average => "AVG(value)",
                TimeSeriesAggregation::Sum => "SUM(value)",
                TimeSeriesAggregation::Min => "MIN(value)",
                TimeSeriesAggregation::Max => "MAX(value)",
                TimeSeriesAggregation::Count => "COUNT(value)",
            };

            builder.push(agg_expr);
            builder.push(" as value, NULL as tags");
        }

        // From clause
        builder.push(" FROM ");
        builder.push(table_name);

        // Where clause
        builder.push(" WHERE 1=1");

        if let Some(start) = options.start_time {
            builder.push(" AND timestamp >= ");
            builder.push_bind(start.unix_timestamp());
        }

        if let Some(end) = options.end_time {
            builder.push(" AND timestamp < ");
            builder.push_bind(end.unix_timestamp());
        }

        if let Some(tags) = &options.tags {
            // Simple tag filtering - in a real implementation, you'd want more sophisticated JSON filtering
            builder.push(" AND tags LIKE ");
            builder.push_bind(format!("%{}%", tags));
        }

        // Group by for downsampling
        if options.downsample != DownsampleInterval::None {
            builder.push(" GROUP BY ts_group");
        }

        // Order by
        builder.push(" ORDER BY timestamp ASC");

        // Limit and offset
        if let Some(limit) = options.limit {
            builder.push(" LIMIT ");
            builder.push_bind(limit);
        }

        if let Some(offset) = options.offset {
            builder.push(" OFFSET ");
            builder.push_bind(offset);
        }

        let query = builder.build();
        let rows = query.fetch_all(pool).await?;

        let mut points = Vec::with_capacity(rows.len());
        for row in rows {
            let timestamp = if options.downsample == DownsampleInterval::None {
                let ts: i64 = row.get("timestamp");
                OffsetDateTime::from_unix_timestamp(ts)
                    .map_err(|e| StorageError::Other(format!("Invalid timestamp: {}", e)))?
            } else {
                let ts_str: String = row.get("ts_group");
                // Parse the timestamp from the formatted string
                // First try RFC3339, then SQL-like, then PrimitiveDateTime fallback
                let parsed = OffsetDateTime::parse(&ts_str, &time::format_description::well_known::Rfc3339)
                    .or_else(|_| {
                        let format = time::macros::format_description!("[year]-[month]-[day] [hour]:[minute]:[second]");
                        OffsetDateTime::parse(&ts_str, &format)
                    })
                    .or_else(|_| {
                        // Fallback: try PrimitiveDateTime then assume UTC
                        time::PrimitiveDateTime::parse(&ts_str, &time::macros::format_description!("[year]-[month]-[day] [hour]:[minute]:[second]")).map(|pd| pd.assume_utc())
                    })
                    .map_err(|e| StorageError::Other(format!("Failed to parse timestamp: {}", e)))?;

                parsed
            };

            let value: f64 = row.get("value");
            let tags: Option<String> = row.try_get("tags").ok();
            // Some SQLite rows may store empty string or NULL for tags; treat empty as None
            let tags_json = match tags {
                Some(t) if !t.trim().is_empty() => serde_json::from_str(&t)
                    .map(Some)
                    .map_err(|e| StorageError::Other(format!("Failed to parse tags: {}", e)))?,
                _ => None,
            };

            points.push(TimeSeriesPoint {
                timestamp,
                value,
                tags: tags_json,
            });
        }

        Ok(points)
    }

    /// Delete time-series data older than the specified timestamp
    #[instrument(skip(pool))]
    pub async fn delete_older_than(
        pool: &Pool<Sqlite>,
        table_name: &str,
        timestamp: OffsetDateTime,
    ) -> Result<u64, StorageError> {
        let ts = timestamp.unix_timestamp();
        let result = sqlx::query(&format!(
            r#"
            DELETE FROM {}
            WHERE timestamp < ?
            "#,
            table_name
        ))
        .bind(ts)
        .execute(pool)
        .await?;

        Ok(result.rows_affected())
    }

    /// Optimize storage by downsampling old data
    #[instrument(skip(_pool))]
    pub async fn downsample_old_data(
        _pool: &Pool<Sqlite>,
        table_name: &str,
        older_than: OffsetDateTime,
        interval: DownsampleInterval,
        aggregation: TimeSeriesAggregation,
    ) -> Result<u64, StorageError> {
        // This is a more complex operation that would:
        // 1. Create a temporary table with downsampled data
        // 2. Delete the original data
        // 3. Insert the downsampled data
        // 4. Drop the temporary table
        
        // For simplicity, we'll just log that this would happen
        debug!(
            "Would downsample data in {} older than {} with interval {:?} and aggregation {:?}",
            table_name, older_than, interval, aggregation
        );
        
        // In a real implementation, you'd execute the downsampling SQL here
        
        Ok(0) // Return 0 rows affected for now
    }

    /// Get statistics about the time-series data
    #[instrument(skip(pool))]
    pub async fn get_stats(
        pool: &Pool<Sqlite>,
        table_name: &str,
    ) -> Result<TimeSeriesStats, StorageError> {
        let count: i64 = sqlx::query_scalar(&format!("SELECT COUNT(*) FROM {}", table_name))
            .fetch_one(pool)
            .await?;

        let min_max = sqlx::query(&format!(
            r#"
            SELECT 
                MIN(timestamp) as min_timestamp,
                MAX(timestamp) as max_timestamp,
                MIN(value) as min_value,
                MAX(value) as max_value,
                AVG(value) as avg_value
            FROM {}
            "#,
            table_name
        ))
        .fetch_optional(pool)
        .await?;

        let (min_ts, max_ts, min_val, max_val, avg_val) = if let Some(row) = min_max {
            let min_ts: Option<i64> = row.try_get("min_timestamp").ok();
            let max_ts: Option<i64> = row.try_get("max_timestamp").ok();
            let min_val: Option<f64> = row.try_get("min_value").ok();
            let max_val: Option<f64> = row.try_get("max_value").ok();
            let avg_val: Option<f64> = row.try_get("avg_value").ok();

            (
                min_ts.and_then(|ts| OffsetDateTime::from_unix_timestamp(ts).ok()),
                max_ts.and_then(|ts| OffsetDateTime::from_unix_timestamp(ts).ok()),
                min_val,
                max_val,
                avg_val,
            )
        } else {
            (None, None, None, None, None)
        };

        Ok(TimeSeriesStats {
            count,
            min_timestamp: min_ts,
            max_timestamp: max_ts,
            min_value: min_val,
            max_value: max_val,
            avg_value: avg_val,
        })
    }
}

/// Time-series statistics
#[derive(Debug, Clone)]
pub struct TimeSeriesStats {
    /// Number of data points
    pub count: i64,
    /// Minimum timestamp
    pub min_timestamp: Option<OffsetDateTime>,
    /// Maximum timestamp
    pub max_timestamp: Option<OffsetDateTime>,
    /// Minimum value
    pub min_value: Option<f64>,
    /// Maximum value
    pub max_value: Option<f64>,
    /// Average value
    pub avg_value: Option<f64>,
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::storage::Storage;
    

    async fn create_test_db() -> Result<Pool<Sqlite>> {
        let storage = Storage::memory().await?;
        Ok(storage.pool().clone())
    }

    #[tokio::test]
    async fn test_time_series_basic() -> Result<()> {
        let pool = create_test_db().await?;
        let table_name = "test_timeseries";

        // Ensure table exists
        TimeSeriesUtils::ensure_table(&pool, table_name).await?;

        // Create test data
        let now = OffsetDateTime::now_utc();
        let points = vec![
            TimeSeriesPoint {
                timestamp: now - time::Duration::minutes(5),
                value: 10.0,
                tags: Some(serde_json::json!({"sensor": "temp", "location": "room1"})),
            },
            TimeSeriesPoint {
                timestamp: now - time::Duration::minutes(4),
                value: 11.0,
                tags: Some(serde_json::json!({"sensor": "temp", "location": "room1"})),
            },
            TimeSeriesPoint {
                timestamp: now - time::Duration::minutes(3),
                value: 12.0,
                tags: Some(serde_json::json!({"sensor": "temp", "location": "room1"})),
            },
            TimeSeriesPoint {
                timestamp: now - time::Duration::minutes(2),
                value: 13.0,
                tags: Some(serde_json::json!({"sensor": "temp", "location": "room1"})),
            },
            TimeSeriesPoint {
                timestamp: now - time::Duration::minutes(1),
                value: 14.0,
                tags: Some(serde_json::json!({"sensor": "temp", "location": "room1"})),
            },
        ];

        // Store points
        let count = TimeSeriesUtils::store_points_batch(&pool, table_name, &points).await?;
        assert_eq!(count, 5);

        // Query all points
        let options = TimeSeriesQueryOptions::default();
        let result = TimeSeriesUtils::query_points(&pool, table_name, &options).await?;
        assert_eq!(result.len(), 5);

        // Query with time range
        let options = TimeSeriesQueryOptions {
            start_time: Some(now - time::Duration::minutes(4)),
            end_time: Some(now - time::Duration::minutes(1)),
            ..Default::default()
        };
        let result = TimeSeriesUtils::query_points(&pool, table_name, &options).await?;
        assert_eq!(result.len(), 3);

        // Query with downsampling
        let options = TimeSeriesQueryOptions {
            downsample: DownsampleInterval::Minute,
            aggregation: TimeSeriesAggregation::Average,
            ..Default::default()
        };
        let result = TimeSeriesUtils::query_points(&pool, table_name, &options).await?;
        assert!(result.len() <= 5); // Should be downsampled to at most 5 points

        // Get stats
        let stats = TimeSeriesUtils::get_stats(&pool, table_name).await?;
        assert_eq!(stats.count, 5);
        assert!(stats.min_value.unwrap() <= 10.1);
        assert!(stats.max_value.unwrap() >= 13.9);

        // Delete older data
        let deleted = TimeSeriesUtils::delete_older_than(
            &pool,
            table_name,
            now - time::Duration::minutes(2),
        )
        .await?;
        assert_eq!(deleted, 3);

        // Verify deletion
        let options = TimeSeriesQueryOptions::default();
        let result = TimeSeriesUtils::query_points(&pool, table_name, &options).await?;
        assert_eq!(result.len(), 2);

        Ok(())
    }
}
