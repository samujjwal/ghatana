// Event repository implementation
// Follows WSRF-DES-003 (WAL/queue pattern) and reuse-first principle

use anyhow::Result;
use sqlx::{SqlitePool, QueryBuilder};
use crate::db::models::{Event, NewEvent, EventFilter};

pub struct EventRepository {
    pool: SqlitePool,
}

impl EventRepository {
    pub fn new(pool: SqlitePool) -> Self {
        Self { pool }
    }

    /// Insert a new event
    pub async fn create(&self, event: NewEvent) -> Result<i64> {
        let result = sqlx::query(
            r#"
            INSERT INTO events (
                event_id, event_type, activity_type, severity, source,
                application, window_title, duration_ms, data, metadata,
                timestamp, tenant_id, device_id, session_id, schema_version
            )
            VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12, ?13, ?14, ?15)
            "#,
        )
        .bind(event.event_id)
        .bind(event.event_type)
        .bind(event.activity_type)
        .bind(event.severity)
        .bind(event.source)
        .bind(event.application)
        .bind(event.window_title)
        .bind(event.duration_ms)
        .bind(event.data)
        .bind(event.metadata)
        .bind(event.timestamp)
        .bind(event.tenant_id)
        .bind(event.device_id)
        .bind(event.session_id)
        .bind(event.schema_version)
        .execute(&self.pool)
        .await?;

        Ok(result.last_insert_rowid())
    }

    /// Batch insert events
    pub async fn create_batch(&self, events: Vec<NewEvent>) -> Result<usize> {
        let mut tx = self.pool.begin().await?;
        let mut count = 0;

        for event in events {
            sqlx::query(
                r#"
                INSERT INTO events (
                    event_id, event_type, activity_type, severity, source,
                    application, window_title, duration_ms, data, metadata,
                    timestamp, tenant_id, device_id, session_id, schema_version
                )
                VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12, ?13, ?14, ?15)
                "#,
            )
            .bind(event.event_id)
            .bind(event.event_type)
            .bind(event.activity_type)
            .bind(event.severity)
            .bind(event.source)
            .bind(event.application)
            .bind(event.window_title)
            .bind(event.duration_ms)
            .bind(event.data)
            .bind(event.metadata)
            .bind(event.timestamp)
            .bind(event.tenant_id)
            .bind(event.device_id)
            .bind(event.session_id)
            .bind(event.schema_version)
            .execute(&mut *tx)
            .await?;
            count += 1;
        }

        tx.commit().await?;
        Ok(count)
    }

    /// Find event by ID
    pub async fn find_by_id(&self, id: i64) -> Result<Option<Event>> {
        let event = sqlx::query_as::<_, Event>(
            r#"
            SELECT * FROM events WHERE id = ?1
            "#,
        )
        .bind(id)
        .fetch_optional(&self.pool)
        .await?;

        Ok(event)
    }

    /// Find event by event_id
    pub async fn find_by_event_id(&self, event_id: &str) -> Result<Option<Event>> {
        let event = sqlx::query_as::<_, Event>(
            r#"
            SELECT * FROM events WHERE event_id = ?1
            "#,
        )
        .bind(event_id)
        .fetch_optional(&self.pool)
        .await?;

        Ok(event)
    }

    /// List events with filters
    pub async fn list(&self, filter: EventFilter) -> Result<Vec<Event>> {
        let mut query = QueryBuilder::new("SELECT * FROM events WHERE 1=1");

        if let Some(event_type) = &filter.event_type {
            query.push(" AND event_type = ");
            query.push_bind(event_type);
        }

        if let Some(activity_type) = &filter.activity_type {
            query.push(" AND activity_type = ");
            query.push_bind(activity_type);
        }

        if let Some(severity) = &filter.severity {
            query.push(" AND severity = ");
            query.push_bind(severity);
        }

        if let Some(source) = &filter.source {
            query.push(" AND source = ");
            query.push_bind(source);
        }

        if let Some(start_time) = filter.start_time {
            query.push(" AND timestamp >= ");
            query.push_bind(start_time);
        }

        if let Some(end_time) = filter.end_time {
            query.push(" AND timestamp <= ");
            query.push_bind(end_time);
        }

        if let Some(processed) = filter.processed {
            query.push(" AND processed = ");
            query.push_bind(processed);
        }

        query.push(" ORDER BY timestamp DESC");

        if let Some(limit) = filter.limit {
            query.push(" LIMIT ");
            query.push_bind(limit);
        }

        if let Some(offset) = filter.offset {
            query.push(" OFFSET ");
            query.push_bind(offset);
        }

        let events = query
            .build_query_as::<Event>()
            .fetch_all(&self.pool)
            .await?;

        Ok(events)
    }

    /// Count events with filters
    pub async fn count(&self, filter: EventFilter) -> Result<i64> {
        let mut query = QueryBuilder::new("SELECT COUNT(*) as count FROM events WHERE 1=1");

        if let Some(event_type) = &filter.event_type {
            query.push(" AND event_type = ");
            query.push_bind(event_type);
        }

        if let Some(activity_type) = &filter.activity_type {
            query.push(" AND activity_type = ");
            query.push_bind(activity_type);
        }

        if let Some(severity) = &filter.severity {
            query.push(" AND severity = ");
            query.push_bind(severity);
        }

        if let Some(source) = &filter.source {
            query.push(" AND source = ");
            query.push_bind(source);
        }

        if let Some(start_time) = filter.start_time {
            query.push(" AND timestamp >= ");
            query.push_bind(start_time);
        }

        if let Some(end_time) = filter.end_time {
            query.push(" AND timestamp <= ");
            query.push_bind(end_time);
        }

        if let Some(processed) = filter.processed {
            query.push(" AND processed = ");
            query.push_bind(processed);
        }

        let result: (i64,) = query
            .build_query_as()
            .fetch_one(&self.pool)
            .await?;

        Ok(result.0)
    }

    /// Mark event as processed
    pub async fn mark_processed(&self, event_id: &str) -> Result<bool> {
        let result = sqlx::query(
            r#"
            UPDATE events SET processed = 1 WHERE event_id = ?1
            "#,
        )
        .bind(event_id)
        .execute(&self.pool)
        .await?;

        Ok(result.rows_affected() > 0)
    }

    /// Mark multiple events as processed
    pub async fn mark_batch_processed(&self, event_ids: Vec<String>) -> Result<usize> {
        let mut tx = self.pool.begin().await?;
        let mut count = 0;

        for event_id in event_ids {
            let result = sqlx::query(
                r#"
                UPDATE events SET processed = 1 WHERE event_id = ?1
                "#,
            )
            .bind(event_id)
            .execute(&mut *tx)
            .await?;
            
            if result.rows_affected() > 0 {
                count += 1;
            }
        }

        tx.commit().await?;
        Ok(count)
    }

    /// Delete old events based on retention policy
    pub async fn delete_older_than(&self, timestamp: i64) -> Result<u64> {
        let result = sqlx::query(
            r#"
            DELETE FROM events WHERE timestamp < ?1
            "#,
        )
        .bind(timestamp)
        .execute(&self.pool)
        .await?;

        Ok(result.rows_affected())
    }

    /// Get unprocessed events
    pub async fn get_unprocessed(&self, limit: i64) -> Result<Vec<Event>> {
        let events = sqlx::query_as::<_, Event>(
            r#"
            SELECT * FROM events 
            WHERE processed = 0 
            ORDER BY timestamp ASC 
            LIMIT ?1
            "#,
        )
        .bind(limit)
        .fetch_all(&self.pool)
        .await?;

        Ok(events)
    }

    /// Get events by severity
    pub async fn get_by_severity(&self, severity: &str, limit: i64) -> Result<Vec<Event>> {
        let events = sqlx::query_as::<_, Event>(
            r#"
            SELECT * FROM events 
            WHERE severity = ?1 
            ORDER BY timestamp DESC 
            LIMIT ?2
            "#,
        )
        .bind(severity)
        .bind(limit)
        .fetch_all(&self.pool)
        .await?;

        Ok(events)
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use uuid::Uuid;

    async fn setup_test_db() -> SqlitePool {
        let pool = SqlitePool::connect(":memory:").await.unwrap();
        sqlx::migrate!("./migrations").run(&pool).await.unwrap();
        pool
    }

    #[tokio::test]
    async fn test_create_event() {
        let pool = setup_test_db().await;
        let repo = EventRepository::new(pool);

        let event = NewEvent {
            event_id: Uuid::new_v4().to_string(),
            event_type: "USER".to_string(),
            activity_type: "ACTIVITY_USER_INTERACTION".to_string(),
            severity: "SEVERITY_INFO".to_string(),
            source: "desktop".to_string(),
            application: Some("browser".to_string()),
            window_title: None,
            duration_ms: Some(100),
            data: None,
            metadata: None,
            timestamp: chrono::Utc::now().timestamp_millis(),
            tenant_id: "tenant1".to_string(),
            device_id: "device1".to_string(),
            session_id: "session1".to_string(),
            schema_version: "1.0.0".to_string(),
        };

        let id = repo.create(event).await.unwrap();
        assert!(id > 0);
    }

    #[tokio::test]
    async fn test_mark_processed() {
        let pool = setup_test_db().await;
        let repo = EventRepository::new(pool);

        let event_id = Uuid::new_v4().to_string();
        let event = NewEvent {
            event_id: event_id.clone(),
            event_type: "USER".to_string(),
            activity_type: "ACTIVITY_USER_INTERACTION".to_string(),
            severity: "SEVERITY_INFO".to_string(),
            source: "desktop".to_string(),
            application: None,
            window_title: None,
            duration_ms: None,
            data: None,
            metadata: None,
            timestamp: chrono::Utc::now().timestamp_millis(),
            tenant_id: "tenant1".to_string(),
            device_id: "device1".to_string(),
            session_id: "session1".to_string(),
            schema_version: "1.0.0".to_string(),
        };

        repo.create(event).await.unwrap();
        
        let marked = repo.mark_processed(&event_id).await.unwrap();
        assert!(marked);

        let found = repo.find_by_event_id(&event_id).await.unwrap().unwrap();
        assert!(found.processed);
    }
}
