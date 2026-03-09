//! Common traits for storage components

use std::collections::HashMap;
use std::time::Duration;

use anyhow::Result;
use async_trait::async_trait;
use serde::{Deserialize, Serialize};
use time::OffsetDateTime;

/// Common storage operations for all storage types
#[async_trait]
pub trait Storage: Send + Sync + 'static {
    /// Initialize the storage
    async fn init(&self) -> Result<()>;
    
    /// Check if the storage is healthy
    async fn health_check(&self) -> Result<bool>;
    
    /// Get storage statistics
    async fn get_stats(&self) -> Result<StorageStats>;
    
    /// Clean up old data based on retention policy
    async fn cleanup(&self, retention: Duration) -> Result<u64>;
}

/// Storage statistics
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct StorageStats {
    /// Total number of records
    pub total_records: i64,
    /// Oldest record timestamp
    pub oldest_timestamp: Option<OffsetDateTime>,
    /// Newest record timestamp
    pub newest_timestamp: Option<OffsetDateTime>,
    /// Total size in bytes
    pub total_size_bytes: i64,
    /// Additional statistics specific to the storage type
    pub additional: HashMap<String, serde_json::Value>,
}

/// Query options for pagination
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PaginationOptions {
    /// Maximum number of records to return
    pub limit: Option<i64>,
    /// Number of records to skip
    pub offset: Option<i64>,
}

/// Sort direction
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub enum SortDirection {
    /// Ascending order
    Asc,
    /// Descending order
    Desc,
}

/// Sort options
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SortOptions {
    /// Field to sort by
    pub field: String,
    /// Sort direction
    pub direction: SortDirection,
}

/// Time range filter
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct TimeRange {
    /// Start time (inclusive)
    pub start: Option<OffsetDateTime>,
    /// End time (exclusive)
    pub end: Option<OffsetDateTime>,
}

/// Aggregation function
#[derive(Debug, Clone, Serialize, Deserialize)]
pub enum AggregationFunction {
    /// Count
    Count,
    /// Minimum
    Min(String),
    /// Maximum
    Max(String),
    /// Average
    Avg(String),
    /// Sum
    Sum(String),
}

/// Base query options
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct QueryOptions {
    /// Pagination options
    pub pagination: Option<PaginationOptions>,
    /// Sort options
    pub sort: Option<Vec<SortOptions>>,
    /// Time range filter
    pub time_range: Option<TimeRange>,
    /// Aggregation function
    pub aggregation: Option<AggregationFunction>,
}

/// Helper functions for timestamp handling
pub mod timestamp {
    use super::*;
    use time::format_description::well_known::Rfc3339;
    use time::macros::format_description;
    
    /// Convert OffsetDateTime to storage timestamp (Unix seconds)
    pub fn offset_to_storage_ts(dt: OffsetDateTime) -> i64 {
        dt.unix_timestamp()
    }
    
    /// Convert storage timestamp to OffsetDateTime
    pub fn storage_ts_to_offset(ts: i64) -> Result<OffsetDateTime> {
        OffsetDateTime::from_unix_timestamp(ts)
            .map_err(|e| anyhow::anyhow!("Invalid timestamp: {}", e))
    }
    
    /// Parse timestamp from various formats
    pub fn parse_timestamp(value: &str) -> Result<OffsetDateTime> {
        // Try RFC3339
        if let Ok(dt) = OffsetDateTime::parse(value, &Rfc3339) {
            return Ok(dt);
        }
        
        // Try SQL timestamp format
            if let Ok(dt) = OffsetDateTime::parse(
                value,
                &format_description!("[year]-[month]-[day] [hour]:[minute]:[second]"),
            ) {
                return Ok(dt);
            }

            // Fallback: try to parse as "YYYY-MM-DD HH:MM:SS" using PrimitiveDateTime
            if let Ok(pd) = time::PrimitiveDateTime::parse(value, &format_description!("[year]-[month]-[day] [hour]:[minute]:[second]")) {
                return Ok(pd.assume_utc());
            }
        
        // Try Unix timestamp
        if let Ok(ts) = value.parse::<i64>() {
            return storage_ts_to_offset(ts);
        }
        
        Err(anyhow::anyhow!("Failed to parse timestamp: {}", value))
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    
    #[test]
    fn test_timestamp_conversion() {
        let now = OffsetDateTime::now_utc();
        let ts = timestamp::offset_to_storage_ts(now);
        let dt = timestamp::storage_ts_to_offset(ts).unwrap();
        
        // We lose precision in the conversion, so we can only check that the seconds match
        assert_eq!(now.unix_timestamp(), dt.unix_timestamp());
    }
    
    #[test]
    fn test_parse_timestamp() {
        // RFC3339
        let dt = timestamp::parse_timestamp("2023-01-01T12:00:00Z").unwrap();
        assert_eq!(dt.year(), 2023);
        assert_eq!(dt.month(), time::Month::January);
        assert_eq!(dt.day(), 1);
        assert_eq!(dt.hour(), 12);
        
        // SQL format
        let dt = timestamp::parse_timestamp("2023-01-01 12:00:00").unwrap();
        assert_eq!(dt.year(), 2023);
        assert_eq!(dt.month(), time::Month::January);
        assert_eq!(dt.day(), 1);
        assert_eq!(dt.hour(), 12);
        
        // Unix timestamp
        let dt = timestamp::parse_timestamp("1672574400").unwrap();
        assert_eq!(dt.year(), 2023);
        assert_eq!(dt.month(), time::Month::January);
        assert_eq!(dt.day(), 1);
        assert_eq!(dt.hour(), 12);
    }
}
