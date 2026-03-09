//! SQLite storage implementations.
//!
//! This module provides SQLite-based implementations of the storage traits.

#[cfg(feature = "storage")]
use sqlx::{sqlite::SqliteRow, Pool, Row, Sqlite, SqlitePool};

use crate::{
    error::{Error, Result},
    models::{
        Action, ActionQuery, Event, EventQuery, Metric, MetricQuery, MetricType,
        RetryConfig,
    },
    types::{PaginatedResponse, Pagination, ResourceId, Timestamp, Metadata, Priority, Status},
};
use async_trait::async_trait;
use serde::de::DeserializeOwned;
use serde_json::Value;
use uuid::Uuid;
use std::collections::HashMap;

use super::{
    ActionsStore, AuditEntry, AuditQuery, AuditStore, ConfigStore, EventsStore, MetricsStore,
    PluginMetadata, PluginStore, SecretStore,
};

/// SQLite-based storage implementation
#[derive(Clone, Debug)]
pub struct SqliteStorage {
    #[cfg(feature = "storage")]
    pool: Pool<Sqlite>,
}

impl SqliteStorage {
    fn map_action_row(row: SqliteRow) -> Result<Action> {
        let id_str: String = row.try_get("id")?;
        let priority_str: String = row.try_get("priority")?;
        let status_str: String = row.try_get("status")?;
        let input_json: String = row.try_get("input")?;
        let output_json: Option<String> = row.try_get("output")?;
        let labels_json: String = row.try_get("labels")?;
        let metadata_json: String = row.try_get("metadata")?;
        let retry_config_json: Option<String> = row.try_get("retry_config")?;
        let timeout_secs: Option<i64> = row.try_get("timeout_secs")?;

        Ok(Action {
            id: Uuid::parse_str(&id_str).map_err(|e| Error::storage(e.to_string()))?,
            action_type: row.try_get("action_type")?,
            name: row.try_get("name")?,
            description: row.try_get("description")?,
            priority: Self::deserialize_enum::<Priority>(&priority_str, "priority")?,
            status: Self::deserialize_enum::<Status>(&status_str, "status")?,
            created_at: row.try_get::<Timestamp, _>("created_at")?,
            updated_at: row.try_get::<Timestamp, _>("updated_at")?,
            executed_at: row.try_get::<Option<Timestamp>, _>("executed_at")?,
            completed_at: row.try_get::<Option<Timestamp>, _>("completed_at")?,
            input: Self::parse_json::<Value>(&input_json, "input")?,
            output: output_json
                .map(|o| Self::parse_json::<Value>(&o, "output"))
                .transpose()?,
            error: row.try_get("error")?,
            labels: Self::parse_json::<HashMap<String, String>>(&labels_json, "labels")?,
            metadata: Self::parse_json::<Metadata>(&metadata_json, "metadata")?,
            retry_config: retry_config_json
                .map(|r| Self::parse_json::<RetryConfig>(&r, "retry_config"))
                .transpose()?,
            timeout_secs: timeout_secs.map(|t| t as u64),
        })
    }

    fn parse_json<T>(raw: &str, field: &str) -> Result<T>
    where
        T: DeserializeOwned,
    {
        serde_json::from_str(raw)
            .map_err(|e| Error::storage(format!("Failed to parse {}: {}", field, e)))
    }

    fn deserialize_enum<T>(raw: &str, field: &str) -> Result<T>
    where
        T: DeserializeOwned,
    {
        Self::parse_json::<T>(&format!("\"{}\"", raw.to_lowercase()), field)
    }
    /// Create a new SQLite storage instance
    #[cfg(feature = "storage")]
    pub async fn new(database_url: &str) -> Result<Self> {
        let pool = SqlitePool::connect(database_url)
            .await
            .map_err(|e| Error::storage(format!("Failed to connect to database: {}", e)))?;
        
        Ok(Self { pool })
    }
    
    /// Run database migrations
    #[cfg(feature = "storage")]
    pub async fn migrate(&self) -> Result<()> {
        sqlx::migrate!("./migrations")
            .run(&self.pool)
            .await
            .map_err(|e| Error::storage(format!("Migration failed: {}", e)))?;
        
        Ok(())
    }
}

#[cfg(feature = "storage")]
#[async_trait]
impl MetricsStore for SqliteStorage {
    async fn store_metric(&self, metric: &Metric) -> Result<()> {
        let labels_json = serde_json::to_string(&metric.labels)?;
        let metadata_json = serde_json::to_string(&metric.metadata)?;
        let value_json = serde_json::to_string(&metric.value)?;
        
        sqlx::query(
            r#"
            INSERT INTO metrics (id, name, value, metric_type, timestamp, source, labels, metadata)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            "#,
        )
        .bind(metric.id.to_string())
        .bind(&metric.name)
        .bind(value_json)
        .bind(format!("{:?}", metric.metric_type))
        .bind(metric.timestamp)
        .bind(&metric.source)
        .bind(labels_json)
        .bind(metadata_json)
        .execute(&self.pool)
        .await
        .map_err(|e| Error::storage(format!("Failed to store metric: {}", e)))?;
        
        Ok(())
    }
    
    async fn store_metrics(&self, metrics: &[Metric]) -> Result<()> {
        for metric in metrics {
            self.store_metric(metric).await?;
        }
        Ok(())
    }
    
    async fn query_metrics(
        &self,
        query: &MetricQuery,
        pagination: &Pagination,
    ) -> Result<PaginatedResponse<Metric>> {
        let mut sql = String::from(
            "SELECT id, name, value, metric_type, timestamp, source, labels, metadata FROM metrics WHERE 1=1",
        );
        let mut count_sql = String::from("SELECT COUNT(*) FROM metrics WHERE 1=1");

        if let Some(ref pattern) = query.name_pattern {
            let clause = format!(" AND name LIKE '{}'", pattern.replace('*', "%"));
            sql.push_str(&clause);
            count_sql.push_str(&clause);
        }

        if let Some(ref source) = query.source {
            let clause = format!(" AND source = '{}'", source);
            sql.push_str(&clause);
            count_sql.push_str(&clause);
        }

        // Add pagination
        sql.push_str(" ORDER BY timestamp DESC");
        let offset = (pagination.page - 1) * pagination.page_size;
        sql.push_str(&format!(" LIMIT {} OFFSET {}", pagination.page_size, offset));

        // Get total count
        let total: (i64,) = sqlx::query_as(&count_sql)
            .fetch_one(&self.pool)
            .await
            .map_err(|e| Error::storage(format!("Failed to count metrics: {}", e)))?;

        type MetricRow = (
            String,
            String,
            String,
            String,
            chrono::DateTime<chrono::Utc>,
            String,
            String,
            String,
        );

        let rows: Vec<MetricRow> = sqlx::query_as(&sql)
            .fetch_all(&self.pool)
            .await
            .map_err(|e| Error::storage(format!("Failed to query metrics: {}", e)))?;

        let items = rows
            .into_iter()
            .map(
                |(id, name, value_json, metric_type, timestamp, source, labels_json, metadata_json)| {
                    Ok(Metric {
                        id: Uuid::parse_str(&id).map_err(|e| Error::storage(e.to_string()))?,
                        name,
                        value: serde_json::from_str(&value_json)?,
                        metric_type: match metric_type.as_str() {
                            "Counter" => MetricType::Counter,
                            "Gauge" => MetricType::Gauge,
                            "Histogram" => MetricType::Histogram,
                            "Summary" => MetricType::Summary,
                            "Timer" => MetricType::Timer,
                            "Set" => MetricType::Set,
                            other => {
                                return Err(Error::storage(format!(
                                    "Unknown metric type: {}",
                                    other
                                )))
                            }
                        },
                        timestamp,
                        source,
                        labels: serde_json::from_str(&labels_json)?,
                        metadata: serde_json::from_str(&metadata_json)?,
                    })
                },
            )
            .collect::<Result<Vec<_>>>()?;

        let total = total.0 as usize;
        let total_pages = if pagination.page_size == 0 {
            0
        } else {
            total.div_ceil(pagination.page_size)
        };

        Ok(PaginatedResponse {
            items,
            total,
            page: pagination.page,
            page_size: pagination.page_size,
            total_pages,
        })
    }
    async fn get_metric(&self, id: ResourceId) -> Result<Option<Metric>> {
        let row: Option<(String, String, String, String, chrono::DateTime<chrono::Utc>, String, String, String)> = 
            sqlx::query_as("SELECT id, name, value, metric_type, timestamp, source, labels, metadata FROM metrics WHERE id = ?")
            .bind(id.to_string())
            .fetch_optional(&self.pool)
            .await
            .map_err(|e| Error::storage(format!("Failed to get metric: {}", e)))?;
        
        match row {
            Some((id, name, value_json, metric_type, timestamp, source, labels_json, metadata_json)) => {
                Ok(Some(Metric {
                    id: Uuid::parse_str(&id).map_err(|e| Error::storage(e.to_string()))?,
                    name,
                    value: serde_json::from_str(&value_json)?,
                    metric_type: match metric_type.as_str() {
                        "Counter" => MetricType::Counter,
                        "Gauge" => MetricType::Gauge,
                        "Histogram" => MetricType::Histogram,
                        "Summary" => MetricType::Summary,
                        "Timer" => MetricType::Timer,
                        "Set" => MetricType::Set,
                        _ => return Err(Error::storage(format!("Unknown metric type: {}", metric_type))),
                    },
                    timestamp,
                    source,
                    labels: serde_json::from_str(&labels_json)?,
                    metadata: serde_json::from_str(&metadata_json)?,
                }))
            },
            None => Ok(None),
        }
    }
    
    async fn delete_old_metrics(&self, before: chrono::DateTime<chrono::Utc>) -> Result<u64> {
        let result = sqlx::query("DELETE FROM metrics WHERE timestamp < ?")
            .bind(before)
            .execute(&self.pool)
            .await
            .map_err(|e| Error::storage(format!("Failed to delete old metrics: {}", e)))?;
        
        Ok(result.rows_affected())
    }
    
    async fn count_metrics(&self, query: &MetricQuery) -> Result<u64> {
        let mut sql = String::from("SELECT COUNT(*) FROM metrics WHERE 1=1");
        
        if let Some(ref pattern) = query.name_pattern {
            sql.push_str(&format!(" AND name LIKE '{}'", pattern.replace('*', "%")));
        }
        
        if let Some(ref source) = query.source {
            sql.push_str(&format!(" AND source = '{}'", source));
        }
        
        if let Some(ref start) = query.start_time {
            sql.push_str(&format!(" AND timestamp >= '{}'", start.to_rfc3339()));
        }
        
        if let Some(ref end) = query.end_time {
            sql.push_str(&format!(" AND timestamp <= '{}'", end.to_rfc3339()));
        }
        
        let count: (i64,) = sqlx::query_as(&sql)
            .fetch_one(&self.pool)
            .await
            .map_err(|e| Error::storage(format!("Failed to count metrics: {}", e)))?;
        
        Ok(count.0 as u64)
    }
}

#[cfg(feature = "storage")]
#[async_trait]
impl EventsStore for SqliteStorage {
    async fn store_event(&self, event: &Event) -> Result<()> {
        let labels_json = serde_json::to_string(&event.labels)?;
        let metadata_json = serde_json::to_string(&event.metadata)?;
        let payload_json = serde_json::to_string(&event.payload)?;
        let related_events_json = serde_json::to_string(&event.related_events)?;
        
        sqlx::query(
            r#"
            INSERT INTO events (
                id, event_type, title, description, severity, priority, status,
                timestamp, source, resource, payload, labels, metadata, related_events
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            "#,
        )
        .bind(event.id.to_string())
        .bind(&event.event_type)
        .bind(&event.title)
        .bind(&event.description)
        .bind(format!("{:?}", event.severity))
        .bind(format!("{:?}", event.priority))
        .bind(format!("{:?}", event.status))
        .bind(event.timestamp)
        .bind(&event.source)
        .bind(&event.resource)
        .bind(payload_json)
        .bind(labels_json)
        .bind(metadata_json)
        .bind(related_events_json)
        .execute(&self.pool)
        .await
        .map_err(|e| Error::storage(format!("Failed to store event: {}", e)))?;
        
        Ok(())
    }
    
    async fn store_events(&self, events: &[Event]) -> Result<()> {
        for event in events {
            self.store_event(event).await?;
        }
        Ok(())
    }
    
    async fn query_events(
        &self,
        query: &EventQuery,
        pagination: &Pagination,
    ) -> Result<PaginatedResponse<Event>> {
        let mut sql = String::from("SELECT id, event_type, title, description, severity, priority, status, timestamp, source, resource, payload, labels, metadata, related_events FROM events WHERE 1=1");
        let mut count_sql = String::from("SELECT COUNT(*) FROM events WHERE 1=1");
        
        // Build WHERE clause
        if let Some(ref event_type) = query.event_type {
            let clause = format!(" AND event_type = '{}'", event_type);
            sql.push_str(&clause);
            count_sql.push_str(&clause);
        }
        
        if let Some(ref source) = query.source {
            let clause = format!(" AND source = '{}'", source);
            sql.push_str(&clause);
            count_sql.push_str(&clause);
        }
        
        if let Some(ref resource) = query.resource {
            let clause = format!(" AND resource = '{}'", resource);
            sql.push_str(&clause);
            count_sql.push_str(&clause);
        }
        
        if let Some(ref severity) = query.severity {
            let clause = format!(" AND severity = '{:?}'", severity);
            sql.push_str(&clause);
            count_sql.push_str(&clause);
        }
        
        if let Some(ref status) = query.status {
            let clause = format!(" AND status = '{:?}'", status);
            sql.push_str(&clause);
            count_sql.push_str(&clause);
        }
        
        if let Some(ref start) = query.start_time {
            let clause = format!(" AND timestamp >= '{}'", start.to_rfc3339());
            sql.push_str(&clause);
            count_sql.push_str(&clause);
        }
        
        if let Some(ref end) = query.end_time {
            let clause = format!(" AND timestamp <= '{}'", end.to_rfc3339());
            sql.push_str(&clause);
            count_sql.push_str(&clause);
        }
        
        // Get total count
        let total: (i64,) = sqlx::query_as(&count_sql)
            .fetch_one(&self.pool)
            .await
            .map_err(|e| Error::storage(format!("Failed to count events: {}", e)))?;
        
        // Add pagination
        sql.push_str(&format!(" ORDER BY timestamp DESC LIMIT {} OFFSET {}", 
            pagination.limit(), pagination.offset()));
        
        // Execute query
        let rows: Vec<(String, String, String, Option<String>, String, String, String, chrono::DateTime<chrono::Utc>, String, Option<String>, String, String, String, String)> = 
            sqlx::query_as(&sql)
            .fetch_all(&self.pool)
            .await
            .map_err(|e| Error::storage(format!("Failed to query events: {}", e)))?;
        
        let items: Result<Vec<Event>> = rows.into_iter().map(|(id, event_type, title, description, severity_str, priority_str, status_str, timestamp, source, resource, payload_json, labels_json, metadata_json, related_json)| {
            Ok(Event {
                id: Uuid::parse_str(&id).map_err(|e| Error::storage(e.to_string()))?,
                event_type,
                title,
                description,
                severity: serde_json::from_str(&format!("\"{}\"", severity_str.to_lowercase()))?,
                priority: serde_json::from_str(&format!("\"{}\"", priority_str.to_lowercase()))?,
                status: serde_json::from_str(&format!("\"{}\"", status_str.to_lowercase()))?,
                timestamp,
                source,
                resource,
                payload: serde_json::from_str(&payload_json)?,
                labels: serde_json::from_str(&labels_json)?,
                metadata: serde_json::from_str(&metadata_json)?,
                related_events: serde_json::from_str(&related_json)?,
            })
        }).collect();
        
        Ok(PaginatedResponse::new(
            items?,
            total.0 as usize,
            pagination.page,
            pagination.page_size,
        ))
    }
    
    async fn get_event(&self, id: ResourceId) -> Result<Option<Event>> {
        let row: Option<(String, String, String, Option<String>, String, String, String, chrono::DateTime<chrono::Utc>, String, Option<String>, String, String, String, String)> = 
            sqlx::query_as("SELECT id, event_type, title, description, severity, priority, status, timestamp, source, resource, payload, labels, metadata, related_events FROM events WHERE id = ?")
            .bind(id.to_string())
            .fetch_optional(&self.pool)
            .await
            .map_err(|e| Error::storage(format!("Failed to get event: {}", e)))?;
        
        match row {
            Some((id, event_type, title, description, severity_str, priority_str, status_str, timestamp, source, resource, payload_json, labels_json, metadata_json, related_json)) => {
                Ok(Some(Event {
                    id: Uuid::parse_str(&id).map_err(|e| Error::storage(e.to_string()))?,
                    event_type,
                    title,
                    description,
                    severity: serde_json::from_str(&format!("\"{}\"", severity_str.to_lowercase()))?,
                    priority: serde_json::from_str(&format!("\"{}\"", priority_str.to_lowercase()))?,
                    status: serde_json::from_str(&format!("\"{}\"", status_str.to_lowercase()))?,
                    timestamp,
                    source,
                    resource,
                    payload: serde_json::from_str(&payload_json)?,
                    labels: serde_json::from_str(&labels_json)?,
                    metadata: serde_json::from_str(&metadata_json)?,
                    related_events: serde_json::from_str(&related_json)?,
                }))
            },
            None => Ok(None),
        }
    }
    
    async fn update_event_status(
        &self,
        id: ResourceId,
        status: Status,
    ) -> Result<()> {
        sqlx::query("UPDATE events SET status = ? WHERE id = ?")
            .bind(format!("{:?}", status))
            .bind(id.to_string())
            .execute(&self.pool)
            .await
            .map_err(|e| Error::storage(format!("Failed to update event status: {}", e)))?;
        
        Ok(())
    }
    
    async fn delete_old_events(&self, before: chrono::DateTime<chrono::Utc>) -> Result<u64> {
        let result = sqlx::query("DELETE FROM events WHERE timestamp < ?")
            .bind(before)
            .execute(&self.pool)
            .await
            .map_err(|e| Error::storage(format!("Failed to delete old events: {}", e)))?;
        
        Ok(result.rows_affected())
    }
    
    async fn count_events(&self, query: &EventQuery) -> Result<u64> {
        let mut sql = String::from("SELECT COUNT(*) FROM events WHERE 1=1");
        
        if let Some(ref event_type) = query.event_type {
            sql.push_str(&format!(" AND event_type = '{}'", event_type));
        }
        
        if let Some(ref source) = query.source {
            sql.push_str(&format!(" AND source = '{}'", source));
        }
        
        if let Some(ref severity) = query.severity {
            sql.push_str(&format!(" AND severity = '{:?}'", severity));
        }
        
        if let Some(ref status) = query.status {
            sql.push_str(&format!(" AND status = '{:?}'", status));
        }
        
        if let Some(ref start) = query.start_time {
            sql.push_str(&format!(" AND timestamp >= '{}'", start.to_rfc3339()));
        }
        
        if let Some(ref end) = query.end_time {
            sql.push_str(&format!(" AND timestamp <= '{}'", end.to_rfc3339()));
        }
        
        let count: (i64,) = sqlx::query_as(&sql)
            .fetch_one(&self.pool)
            .await
            .map_err(|e| Error::storage(format!("Failed to count events: {}", e)))?;
        
        Ok(count.0 as u64)
    }
}

#[cfg(feature = "storage")]
#[async_trait]
impl ActionsStore for SqliteStorage {
    async fn store_action(&self, action: &Action) -> Result<()> {
        let labels_json = serde_json::to_string(&action.labels)?;
        let metadata_json = serde_json::to_string(&action.metadata)?;
        let input_json = serde_json::to_string(&action.input)?;
        let output_json = action.output.as_ref().map(serde_json::to_string).transpose()?;
        let retry_config_json = action.retry_config.as_ref().map(serde_json::to_string).transpose()?;
        
        sqlx::query(
            r#"
            INSERT INTO actions (
                id, action_type, name, description, priority, status,
                created_at, updated_at, executed_at, completed_at,
                input, output, error, labels, metadata, retry_config, timeout_secs
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            "#,
        )
        .bind(action.id.to_string())
        .bind(&action.action_type)
        .bind(&action.name)
        .bind(&action.description)
        .bind(format!("{:?}", action.priority))
        .bind(format!("{:?}", action.status))
        .bind(action.created_at)
        .bind(action.updated_at)
        .bind(action.executed_at)
        .bind(action.completed_at)
        .bind(input_json)
        .bind(output_json)
        .bind(&action.error)
        .bind(labels_json)
        .bind(metadata_json)
        .bind(retry_config_json)
        .bind(action.timeout_secs.map(|t| t as i64))
        .execute(&self.pool)
        .await
        .map_err(|e| Error::storage(format!("Failed to store action: {}", e)))?;
        
        Ok(())
    }
    
    async fn query_actions(
        &self,
        query: &ActionQuery,
        pagination: &Pagination,
    ) -> Result<PaginatedResponse<Action>> {
        let mut sql = String::from("SELECT id, action_type, name, description, priority, status, created_at, updated_at, executed_at, completed_at, input, output, error, labels, metadata, retry_config, timeout_secs FROM actions WHERE 1=1");
        let mut count_sql = String::from("SELECT COUNT(*) FROM actions WHERE 1=1");
        
        // Build WHERE clause
        if let Some(ref action_type) = query.action_type {
            let clause = format!(" AND action_type = '{}'", action_type);
            sql.push_str(&clause);
            count_sql.push_str(&clause);
        }
        
        if let Some(ref status) = query.status {
            let clause = format!(" AND status = '{:?}'", status);
            sql.push_str(&clause);
            count_sql.push_str(&clause);
        }
        
        if let Some(ref priority) = query.priority {
            let clause = format!(" AND priority = '{:?}'", priority);
            sql.push_str(&clause);
            count_sql.push_str(&clause);
        }
        
        if let Some(ref start) = query.start_time {
            let clause = format!(" AND created_at >= '{}'", start.to_rfc3339());
            sql.push_str(&clause);
            count_sql.push_str(&clause);
        }
        
        if let Some(ref end) = query.end_time {
            let clause = format!(" AND created_at <= '{}'", end.to_rfc3339());
            sql.push_str(&clause);
            count_sql.push_str(&clause);
        }
        
        // Get total count
        let total: (i64,) = sqlx::query_as(&count_sql)
            .fetch_one(&self.pool)
            .await
            .map_err(|e| Error::storage(format!("Failed to count actions: {}", e)))?;
        
        // Add pagination
        sql.push_str(&format!(" ORDER BY created_at DESC LIMIT {} OFFSET {}", 
            pagination.limit(), pagination.offset()));
        
        // Execute query - manually extract fields to avoid tuple size limit
        let rows = sqlx::query(&sql)
            .fetch_all(&self.pool)
            .await
            .map_err(|e| Error::storage(format!("Failed to query actions: {}", e)))?;
        
        let items: Result<Vec<Action>> = rows
            .into_iter()
            .map(Self::map_action_row)
            .collect();
        
        Ok(PaginatedResponse::new(
            items?,
            total.0 as usize,
            pagination.page,
            pagination.page_size,
        ))
    }
    
    async fn get_action(&self, id: ResourceId) -> Result<Option<Action>> {
        let row = sqlx::query("SELECT id, action_type, name, description, priority, status, created_at, updated_at, executed_at, completed_at, input, output, error, labels, metadata, retry_config, timeout_secs FROM actions WHERE id = ?")
            .bind(id.to_string())
            .fetch_optional(&self.pool)
            .await
            .map_err(|e| Error::storage(format!("Failed to get action: {}", e)))?;
        
        match row {
            Some(row) => Ok(Some(Self::map_action_row(row)?)),
            None => Ok(None),
        }
    }
    
    async fn update_action_status(
        &self,
        id: ResourceId,
        status: Status,
    ) -> Result<()> {
        sqlx::query("UPDATE actions SET status = ?, updated_at = ? WHERE id = ?")
            .bind(format!("{:?}", status))
            .bind(chrono::Utc::now())
            .bind(id.to_string())
            .execute(&self.pool)
            .await
            .map_err(|e| Error::storage(format!("Failed to update action status: {}", e)))?;
        
        Ok(())
    }
    
    async fn update_action_result(
        &self,
        id: ResourceId,
        status: Status,
        output: Option<Value>,
        error: Option<String>,
    ) -> Result<()> {
        let output_json = output.map(|o| serde_json::to_string(&o)).transpose()?;
        
        sqlx::query("UPDATE actions SET status = ?, output = ?, error = ?, completed_at = ?, updated_at = ? WHERE id = ?")
            .bind(format!("{:?}", status))
            .bind(output_json)
            .bind(error)
            .bind(chrono::Utc::now())
            .bind(chrono::Utc::now())
            .bind(id.to_string())
            .execute(&self.pool)
            .await
            .map_err(|e| Error::storage(format!("Failed to update action result: {}", e)))?;
        
        Ok(())
    }
    
    async fn delete_old_actions(&self, before: chrono::DateTime<chrono::Utc>) -> Result<u64> {
        let result = sqlx::query("DELETE FROM actions WHERE created_at < ?")
            .bind(before)
            .execute(&self.pool)
            .await
            .map_err(|e| Error::storage(format!("Failed to delete old actions: {}", e)))?;
        
        Ok(result.rows_affected())
    }
    
    async fn count_actions(&self, query: &ActionQuery) -> Result<u64> {
        let mut sql = String::from("SELECT COUNT(*) FROM actions WHERE 1=1");
        
        if let Some(ref action_type) = query.action_type {
            sql.push_str(&format!(" AND action_type = '{}'", action_type));
        }
        
        if let Some(ref status) = query.status {
            sql.push_str(&format!(" AND status = '{:?}'", status));
        }
        
        if let Some(ref priority) = query.priority {
            sql.push_str(&format!(" AND priority = '{:?}'", priority));
        }
        
        if let Some(ref start) = query.start_time {
            sql.push_str(&format!(" AND created_at >= '{}'", start.to_rfc3339()));
        }
        
        if let Some(ref end) = query.end_time {
            sql.push_str(&format!(" AND created_at <= '{}'", end.to_rfc3339()));
        }
        
        let count: (i64,) = sqlx::query_as(&sql)
            .fetch_one(&self.pool)
            .await
            .map_err(|e| Error::storage(format!("Failed to count actions: {}", e)))?;
        
        Ok(count.0 as u64)
    }
}

#[cfg(feature = "storage")]
#[async_trait]
impl ConfigStore for SqliteStorage {
    async fn get_config(&self, key: &str) -> Result<Option<String>> {
        let result: Option<(String,)> = sqlx::query_as("SELECT value FROM config WHERE key = ?")
            .bind(key)
            .fetch_optional(&self.pool)
            .await
            .map_err(|e| Error::storage(format!("Failed to get config: {}", e)))?;
        
        Ok(result.map(|(value,)| value))
    }
    
    async fn set_config(&self, key: &str, value: &str) -> Result<()> {
        sqlx::query("INSERT OR REPLACE INTO config (key, value) VALUES (?, ?)")
            .bind(key)
            .bind(value)
            .execute(&self.pool)
            .await
            .map_err(|e| Error::storage(format!("Failed to set config: {}", e)))?;
        
        Ok(())
    }
    
    async fn delete_config(&self, key: &str) -> Result<()> {
        sqlx::query("DELETE FROM config WHERE key = ?")
            .bind(key)
            .execute(&self.pool)
            .await
            .map_err(|e| Error::storage(format!("Failed to delete config: {}", e)))?;
        
        Ok(())
    }
    
    async fn list_config_keys(&self) -> Result<Vec<String>> {
        let keys: Vec<(String,)> = sqlx::query_as("SELECT key FROM config")
            .fetch_all(&self.pool)
            .await
            .map_err(|e| Error::storage(format!("Failed to list config keys: {}", e)))?;
        
        Ok(keys.into_iter().map(|(key,)| key).collect())
    }
    
    async fn get_all_config(&self) -> Result<HashMap<String, String>> {
        let rows: Vec<(String, String)> = sqlx::query_as("SELECT key, value FROM config")
            .fetch_all(&self.pool)
            .await
            .map_err(|e| Error::storage(format!("Failed to get all config: {}", e)))?;
        
        Ok(rows.into_iter().collect())
    }
}

#[cfg(feature = "storage")]
#[async_trait]
impl AuditStore for SqliteStorage {
    async fn store_audit_entry(&self, _entry: &AuditEntry) -> Result<()> {
        // TODO: Implement
        Err(Error::message("Not implemented"))
    }
    
    async fn query_audit_entries(
        &self,
        _query: &AuditQuery,
        _pagination: &Pagination,
    ) -> Result<PaginatedResponse<AuditEntry>> {
        // TODO: Implement
        Err(Error::message("Not implemented"))
    }
    
    async fn count_audit_entries(&self, _query: &AuditQuery) -> Result<u64> {
        // TODO: Implement
        Err(Error::message("Not implemented"))
    }
}

#[cfg(feature = "storage")]
#[async_trait]
impl PluginStore for SqliteStorage {
    async fn store_plugin(&self, _plugin: &PluginMetadata) -> Result<()> {
        // TODO: Implement
        Err(Error::message("Not implemented"))
    }
    
    async fn get_plugin(&self, _id: &str) -> Result<Option<PluginMetadata>> {
        // TODO: Implement
        Err(Error::message("Not implemented"))
    }
    
    async fn list_plugins(&self) -> Result<Vec<PluginMetadata>> {
        // TODO: Implement
        Err(Error::message("Not implemented"))
    }
    
    async fn delete_plugin(&self, _id: &str) -> Result<()> {
        // TODO: Implement
        Err(Error::message("Not implemented"))
    }
    
    async fn update_plugin_status(&self, _id: &str, _enabled: bool) -> Result<()> {
        // TODO: Implement
        Err(Error::message("Not implemented"))
    }
}

#[cfg(feature = "storage")]
#[async_trait]
impl SecretStore for SqliteStorage {
    async fn store_secret(&self, _key: &str, _value: &[u8]) -> Result<()> {
        // TODO: Implement with encryption
        Err(Error::message("Not implemented"))
    }
    
    async fn get_secret(&self, _key: &str) -> Result<Option<Vec<u8>>> {
        // TODO: Implement with decryption
        Err(Error::message("Not implemented"))
    }
    
    async fn delete_secret(&self, _key: &str) -> Result<()> {
        // TODO: Implement
        Err(Error::message("Not implemented"))
    }
    
    async fn list_secret_keys(&self) -> Result<Vec<String>> {
        // TODO: Implement
        Err(Error::message("Not implemented"))
    }
}
