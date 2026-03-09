//! Database schema definitions

use serde::{Deserialize, Serialize};
use sqlx::FromRow;
use time::OffsetDateTime;

/// Represents a stored metric entry
#[derive(Debug, Clone, FromRow, Serialize, Deserialize)]
pub struct MetricRow {
    /// Unique identifier
    pub id: i64,

    /// Metric type (e.g., "cpu", "memory", "disk")
    pub metric_type: String,

    /// Hostname where the metric was collected
    pub hostname: String,

    /// JSON-encoded metric data
    pub data: String,

    /// Timestamp when the metric was collected
    pub timestamp: OffsetDateTime,

    /// Timestamp when the record was created
    pub created_at: OffsetDateTime,
}

/// Represents a stored event entry
#[derive(Debug, Clone, FromRow, Serialize, Deserialize)]
pub struct EventRow {
    /// Unique identifier
    pub id: i64,

    /// Event type (e.g., "alert", "status_change")
    pub event_type: String,

    /// Event severity level
    pub severity: String,

    /// Event message
    pub message: String,

    /// JSON-encoded event data
    pub data: Option<String>,

    /// Timestamp when the event occurred
    pub timestamp: OffsetDateTime,

    /// Timestamp when the record was created
    pub created_at: OffsetDateTime,
}

/// Query parameters for filtering metrics
#[derive(Debug, Clone, Default)]
pub struct MetricQuery {
    /// Filter by metric type
    pub metric_type: Option<String>,

    /// Filter by hostname
    pub hostname: Option<String>,

    /// Start timestamp (inclusive)
    pub start_time: Option<OffsetDateTime>,

    /// End timestamp (exclusive)
    pub end_time: Option<OffsetDateTime>,

    /// Maximum number of results to return
    pub limit: Option<i64>,

    /// Offset for pagination
    pub offset: Option<i64>,
}

/// Query parameters for filtering events
#[derive(Debug, Clone, Default)]
pub struct EventQuery {
    /// Filter by event type
    pub event_type: Option<String>,

    /// Filter by severity level
    pub severity: Option<String>,

    /// Start timestamp (inclusive)
    pub start_time: Option<OffsetDateTime>,

    /// End timestamp (exclusive)
    pub end_time: Option<OffsetDateTime>,

    /// Maximum number of results to return
    pub limit: Option<i64>,

    /// Offset for pagination
    pub offset: Option<i64>,
}

#[cfg(test)]
mod tests {
    use super::*;
    use time::OffsetDateTime;

    #[test]
    fn test_metric_row_serialization() {
        let now = OffsetDateTime::now_utc();
        let row = MetricRow {
            id: 1,
            metric_type: "cpu".to_string(),
            hostname: "test-host".to_string(),
            data: r#"{"usage": 42.5}"#.to_string(),
            timestamp: now,
            created_at: now,
        };

        let json = serde_json::to_string(&row).unwrap();
        let deserialized: MetricRow = serde_json::from_str(&json).unwrap();

        assert_eq!(row.id, deserialized.id);
        assert_eq!(row.metric_type, deserialized.metric_type);
        assert_eq!(row.hostname, deserialized.hostname);
        assert_eq!(row.data, deserialized.data);
    }

    #[test]
    fn test_event_row_serialization() {
        let now = OffsetDateTime::now_utc();
        let row = EventRow {
            id: 1,
            event_type: "alert".to_string(),
            severity: "high".to_string(),
            message: "CPU usage too high".to_string(),
            data: Some(r#"{"metric": "cpu.usage", "value": 95.0}"#.to_string()),
            timestamp: now,
            created_at: now,
        };

        let json = serde_json::to_string(&row).unwrap();
        let deserialized: EventRow = serde_json::from_str(&json).unwrap();

        assert_eq!(row.id, deserialized.id);
        assert_eq!(row.event_type, deserialized.event_type);
        assert_eq!(row.severity, deserialized.severity);
        assert_eq!(row.message, deserialized.message);
        assert_eq!(row.data, deserialized.data);
    }
}
