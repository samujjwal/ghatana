//! Events storage and helpers (Capability 1 minimal)
//! Stores anomaly events; future enhancements will generalize taxonomy.

use serde::{Serialize, Deserialize};
use sqlx::{SqlitePool, FromRow, Executor};
use time::OffsetDateTime;

use crate::policy::{PolicyEngineStatistics, RouterDecision};

/// Lightweight representation of a new event to be stored.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct NewEvent {
    /// The event type (eg. "anomaly", "heartbeat").
    pub event_type: String,
    /// Severity level as a freeform string (eg. "low", "high").
    pub severity: String,
    /// Human-readable message describing the event.
    pub message: String,
    /// Optional JSON-encoded additional data.
    pub data: Option<String>,
    /// Event timestamp (UTC).
    pub timestamp: OffsetDateTime,
}

/// Representation of a stored event row returned from the database.
#[derive(Debug, Clone, FromRow, Serialize, Deserialize)]
pub struct StoredEvent {
    /// Autoincremented primary key assigned by the database.
    pub id: i64,
    /// The event type (eg. "anomaly").
    pub event_type: String,
    /// Severity string recorded at insertion time.
    pub severity: String,
    /// Human-readable message.
    pub message: String,
    /// Optional JSON-encoded additional data attached to the event.
    pub data: Option<String>,
    /// Event timestamp (UTC) as stored.
    pub timestamp: OffsetDateTime,
    /// Insertion timestamp recorded by the system.
    pub created_at: OffsetDateTime,
}

#[derive(Clone)]
/// Storage helper for persisting and querying events using SQLite.
pub struct EventsStorage { pool: SqlitePool }

impl EventsStorage {
    /// Create a new EventsStorage backed by the given SQLite pool.
    ///
    /// This helper exposes simple CRUD and query operations over the
    /// `events` table used for anomaly/event persistence.
    pub fn new(pool: SqlitePool) -> Self { Self { pool } }

    /// Create a new `EventsStorage` bound to the provided SQLite connection pool.
    ///
    /// The returned storage helper can ensure schema, insert events and perform
    /// simple queries against the events table.
    pub fn with_pool(pool: SqlitePool) -> Self { Self { pool } }

    /// Ensure the events table schema exists (idempotent).
    pub async fn ensure_schema(&self) -> anyhow::Result<()> {
        // Using INTEGER PRIMARY KEY AUTOINCREMENT for stable row ids.
        self.pool.execute(r#"
            CREATE TABLE IF NOT EXISTS events (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                event_type TEXT NOT NULL,
                severity TEXT NOT NULL,
                message TEXT NOT NULL,
                data TEXT NULL,
                timestamp INTEGER NOT NULL,
                created_at INTEGER NOT NULL
            )
        "#).await?;
        Ok(())
    }

    /// Insert a `NewEvent` into the events table and return the new row id.
    ///
    /// Returns the SQLite last-insert-rowid on success.
    pub async fn insert(&self, evt: NewEvent) -> anyhow::Result<i64> {
        let res = sqlx::query(
            r#"INSERT INTO events (event_type, severity, message, data, timestamp, created_at)
               VALUES (?1, ?2, ?3, ?4, ?5, ?6)"#,
        )
        .bind(evt.event_type)
        .bind(evt.severity)
        .bind(evt.message)
        .bind(evt.data)
        .bind(evt.timestamp.unix_timestamp())
        .bind(OffsetDateTime::now_utc().unix_timestamp())
        .execute(&self.pool)
        .await?;
        Ok(res.last_insert_rowid())
    }

    /// List most recent events (ordered descending by timestamp) limited by `limit`.
    pub async fn list_recent(&self, limit: i64) -> anyhow::Result<Vec<StoredEvent>> {
        let rows = sqlx::query_as::<_, StoredEvent>(
            r#"SELECT id, event_type, severity, message, data, 
                  timestamp as timestamp, 
                  created_at as created_at 
               FROM events ORDER BY timestamp DESC LIMIT ?1"#,
        )
        .bind(limit)
        .fetch_all(&self.pool)
        .await?;
        Ok(rows)
    }

    /// Query events by optional type and time range.
    pub async fn query(
        &self,
        event_type: Option<&str>,
        start_ts: Option<i64>,
        end_ts: Option<i64>,
        limit: Option<i64>
    ) -> anyhow::Result<Vec<StoredEvent>> {
    // Query helper: returns a vector of `StoredEvent` that match the
    // optional filters. If `limit` is None, all matching rows are returned.
        // Build dynamic SQL (simple approach acceptable for internal use)
        let mut sql = "SELECT id, event_type, severity, message, data, datetime(timestamp, 'unixepoch') as timestamp, datetime(created_at, 'unixepoch') as created_at FROM events".to_string();
        let mut clauses = Vec::new();
        if event_type.is_some() { clauses.push("event_type = ?"); }
        if start_ts.is_some() { clauses.push("timestamp >= ?"); }
        if end_ts.is_some() { clauses.push("timestamp < ?"); }
        if !clauses.is_empty() { sql.push_str(" WHERE "); sql.push_str(&clauses.join(" AND ")); }
        sql.push_str(" ORDER BY timestamp DESC");
        if limit.is_some() { sql.push_str(" LIMIT ?"); }
        let mut q = sqlx::query_as::<_, StoredEvent>(&sql);
        if let Some(et) = event_type { q = q.bind(et); }
        if let Some(st) = start_ts { q = q.bind(st); }
        if let Some(en) = end_ts { q = q.bind(en); }
        if let Some(lim) = limit { q = q.bind(lim); }
        let rows = q.fetch_all(&self.pool).await?;
        Ok(rows)
    }
}

/// Map anomaly z-score to severity level following the plan's requirements
pub fn anomaly_severity_from_score(z_score: f64, threshold: f64) -> &'static str {
    let abs_score = z_score.abs();
    if abs_score >= threshold * 2.0 {
        "critical"
    } else if abs_score >= threshold * 1.5 {
        "high" 
    } else if abs_score >= threshold * 1.2 {
        "medium"
    } else {
        "low"
    }
}

/// Create NewEvent from anomaly with proper severity mapping
pub fn new_event_from_anomaly(
    host_id: &str,
    metric: &str, 
    value: f64,
    z_score: f64,
    threshold: f64,
    timestamp: time::OffsetDateTime
) -> NewEvent {
    let severity = anomaly_severity_from_score(z_score, threshold);
    let message = format!("{} anomaly detected: value={:.2}, z-score={:.2}", metric, value, z_score);
    let data = Some(serde_json::json!({
        "host_id": host_id,
        "metric": metric,
        "value": value,
        "z_score": z_score,
        "threshold": threshold
    }).to_string());
    
    NewEvent {
        event_type: "anomaly".to_string(),
        severity: severity.to_string(),
        message,
        data,
        timestamp,
    }
}

/// Create NewEvent from a router decision for telemetry and diagnostics.
pub fn new_event_from_router_decision(
    component: &str,
    decision: &RouterDecision,
) -> NewEvent {
    let severity = if !decision.allowed { "high" } else { "info" };

    let message = if !decision.allowed {
        format!(
            "Router denied event for component {} with {} violation(s)",
            component,
            decision.violations.len(),
        )
    } else if decision.destinations.is_empty() {
        format!(
            "Router allowed event for component {} with no explicit destinations",
            component,
        )
    } else {
        format!(
            "Router routed event for component {} to {} destination(s)",
            component,
            decision.destinations.len(),
        )
    };

    let data = Some(
        serde_json::json!({
            "component": component,
            "allowed": decision.allowed,
            "destinations": decision.destinations,
            "violations": decision.violations,
            "metadata": decision.metadata,
            "evaluation_duration_ms": decision.evaluation_duration.as_millis(),
        })
        .to_string(),
    );

    NewEvent {
        event_type: "router.decision".to_string(),
        severity: severity.to_string(),
        message,
        data,
        timestamp: OffsetDateTime::now_utc(),
    }
}

/// Create NewEvent from router / policy engine health statistics.
pub fn new_event_from_router_health(stats: &PolicyEngineStatistics) -> NewEvent {
    let severity = "info";
    let message = format!(
        "Router health: total_policies={}, enabled_policies={}, total_violations={}",
        stats.total_policies,
        stats.enabled_policies,
        stats.total_violations,
    );

    let data = Some(
        serde_json::json!({
            "total_policies": stats.total_policies,
            "enabled_policies": stats.enabled_policies,
            "cache_size": stats.cache_size,
            "total_violations": stats.total_violations,
            "cache_hit_rate": stats.cache_hit_rate,
        })
        .to_string(),
    );

    NewEvent {
        event_type: "router.health".to_string(),
        severity: severity.to_string(),
        message,
        data,
        timestamp: OffsetDateTime::now_utc(),
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use sqlx::SqlitePool;
    use time::OffsetDateTime;

    #[tokio::test]
    async fn test_events_storage_insert() {
        let pool = SqlitePool::connect(":memory:").await.expect("sqlite memory db");
        let storage = EventsStorage::new(pool);
        storage.ensure_schema().await.expect("schema");
        let evt = NewEvent {
            event_type: "anomaly".into(),
            severity: "high".into(),
            message: "cpu spike".into(),
            data: Some("{\"metric\":\"cpu_usage\",\"value\":95.0}".into()),
            timestamp: OffsetDateTime::now_utc(),
        };
        let id = storage.insert(evt).await.expect("insert");
        assert!(id > 0);
    }

    #[tokio::test]
    async fn test_query_and_list_recent() {
        let pool = SqlitePool::connect(":memory:").await.expect("sqlite memory db");
        let storage = EventsStorage::new(pool);
        storage.ensure_schema().await.expect("schema");
        
        // Insert test events
        let now = OffsetDateTime::now_utc();
        let evt1 = NewEvent {
            event_type: "anomaly".into(),
            severity: "high".into(),
            message: "cpu spike".into(),
            data: Some("{\"metric\":\"cpu_usage\"}".into()),
            timestamp: now - time::Duration::seconds(60),
        };
        let evt2 = NewEvent {
            event_type: "normal".into(),
            severity: "low".into(),
            message: "routine check".into(),
            data: None,
            timestamp: now,
        };
        
        storage.insert(evt1).await.expect("insert 1");
        storage.insert(evt2).await.expect("insert 2");
        
        // Test list_recent
        let recent = storage.list_recent(10).await.expect("list recent");
        assert_eq!(recent.len(), 2);
        assert_eq!(recent[0].event_type, "normal"); // Most recent first
        
        // Test query by type
        let anomalies = storage.query(Some("anomaly"), None, None, None).await.expect("query anomalies");
        assert_eq!(anomalies.len(), 1);
        assert_eq!(anomalies[0].event_type, "anomaly");
    }
    
    #[test]
    fn test_severity_mapping() {
        let threshold = 3.0;
        assert_eq!(anomaly_severity_from_score(7.0, threshold), "critical"); // >= 6.0
        assert_eq!(anomaly_severity_from_score(5.0, threshold), "high");     // >= 4.5
        assert_eq!(anomaly_severity_from_score(4.0, threshold), "medium");   // >= 3.6
        assert_eq!(anomaly_severity_from_score(3.0, threshold), "low");      // >= 3.0
    }
}
