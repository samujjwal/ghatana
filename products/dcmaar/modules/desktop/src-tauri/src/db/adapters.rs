//! Storage adapters bridging desktop DB models with agent-common storage traits
//!
//! This module provides adapters that implement agent-common storage traits
//! while working with the desktop service's existing database schema.

use dcmaar_agent_common::{
    error::{Error, Result},
    models::{
        Metric as CommonMetric, MetricValue, MetricType, MetricQuery,
        Event as CommonEvent, EventQuery,
        Action as CommonAction, ActionQuery,
    },
    storage::{MetricsStore, EventsStore, ActionsStore, ConfigStore},
    types::{Pagination, PaginatedResponse, Status, Priority},
};
use async_trait::async_trait;
use sqlx::SqlitePool;
use std::collections::HashMap;
use chrono::{DateTime, Utc};
use uuid::Uuid;

use super::models::{Metric as DbMetric, Event as DbEvent, Action as DbAction};

/// Desktop storage adapter implementing agent-common storage traits
#[derive(Clone)]
pub struct DesktopStorageAdapter {
    pool: SqlitePool,
}

impl DesktopStorageAdapter {
    fn parse_timestamp(ts: i64) -> Result<DateTime<Utc>> {
        DateTime::from_timestamp(ts, 0)
            .ok_or_else(|| Error::storage("Invalid timestamp"))
    }

    fn parse_optional_timestamp(ts: Option<i64>) -> Result<Option<DateTime<Utc>>> {
        ts.map(|value| Self::parse_timestamp(value)).transpose()
    }

    fn db_action_to_common(db_action: &DbAction) -> Result<CommonAction> {
        let created_at = Self::parse_timestamp(db_action.created_at)?;

        Ok(CommonAction {
            id: Uuid::parse_str(&db_action.action_id)
                .map_err(|e| Error::storage(format!("Invalid UUID: {}", e)))?,
            action_type: db_action.action_type.clone(),
            name: db_action.command.clone(),
            description: None,
            priority: Priority::Normal,
            status: serde_json::from_str(&format!("\"{}\"", db_action.status.to_lowercase()))?,
            created_at,
            updated_at: Self::parse_timestamp(db_action.updated_at)?,
            executed_at: Self::parse_optional_timestamp(db_action.started_at)?,
            completed_at: Self::parse_optional_timestamp(db_action.completed_at)?,
            input: db_action
                .args
                .as_ref()
                .and_then(|a| serde_json::from_str(a).ok())
                .unwrap_or(serde_json::json!({})),
            output: db_action
                .stdout
                .as_ref()
                .and_then(|s| serde_json::from_str(s).ok()),
            error: db_action.error.clone(),
            labels: HashMap::new(),
            metadata: db_action
                .metadata
                .as_ref()
                .and_then(|m| serde_json::from_str(m).ok())
                .unwrap_or_default(),
            retry_config: None,
            timeout_secs: db_action.timeout_ms.map(|t| (t / 1000) as u64),
        })
    }

    /// Create a new desktop storage adapter
    pub fn new(pool: SqlitePool) -> Self {
        Self { pool }
    }

    /// Convert desktop DB metric to agent-common metric
    fn db_metric_to_common(db_metric: &DbMetric) -> Result<CommonMetric> {
        let labels: HashMap<String, String> = db_metric
            .labels
            .as_ref()
            .and_then(|l| serde_json::from_str(l).ok())
            .unwrap_or_default();

        let metadata: HashMap<String, String> = db_metric
            .metadata
            .as_ref()
            .and_then(|m| serde_json::from_str(m).ok())
            .unwrap_or_default();

        let metric_type = match db_metric.metric_type.as_str() {
            "counter" => MetricType::Counter,
            "gauge" => MetricType::Gauge,
            "histogram" => MetricType::Histogram,
            "summary" => MetricType::Summary,
            "timer" => MetricType::Timer,
            "set" => MetricType::Set,
            _ => MetricType::Gauge,
        };

        let value = match metric_type {
            MetricType::Counter | MetricType::Timer | MetricType::Set => {
                MetricValue::Int(db_metric.value as i64)
            }
            _ => MetricValue::Float(db_metric.value),
        };

        Ok(CommonMetric {
            id: Uuid::parse_str(&db_metric.metric_id)
                .map_err(|e| Error::storage(format!("Invalid UUID: {}", e)))?,
            name: db_metric.name.clone(),
            value,
            metric_type,
            timestamp: DateTime::from_timestamp(db_metric.timestamp, 0)
                .ok_or_else(|| Error::storage("Invalid timestamp"))?,
            source: db_metric.source.clone(),
            labels,
            metadata,
        })
    }

    /// Convert agent-common metric to desktop DB format
    fn common_metric_to_db_values(
        metric: &CommonMetric,
    ) -> Result<(String, String, f64, String, String, String, i64, String)> {
        let value = match &metric.value {
            MetricValue::Int(v) => *v as f64,
            MetricValue::Float(v) => *v,
            MetricValue::Bool(v) => if *v { 1.0 } else { 0.0 },
            MetricValue::String(v) => v.parse().unwrap_or(0.0),
            MetricValue::Distribution(dist) => dist.avg,
        };

        let metric_type_str = match metric.metric_type {
            MetricType::Counter => "counter",
            MetricType::Gauge => "gauge",
            MetricType::Histogram => "histogram",
            MetricType::Summary => "summary",
            MetricType::Timer => "timer",
            MetricType::Set => "set",
        }
        .to_string();

        let labels = serde_json::to_string(&metric.labels)
            .map_err(|e| Error::storage(format!("Failed to serialize labels: {}", e)))?;
        let metadata = serde_json::to_string(&metric.metadata)
            .map_err(|e| Error::storage(format!("Failed to serialize metadata: {}", e)))?;

        Ok((
            metric.id.to_string(),
            metric.name.clone(),
            value,
            metric_type_str,
            labels,
            metadata,
            metric.timestamp.timestamp(),
            metric.source.clone(),
        ))
    }
}

#[async_trait]
impl MetricsStore for DesktopStorageAdapter {
    async fn store_metric(&self, metric: &CommonMetric) -> Result<()> {
        let (metric_id, name, value, metric_type, labels, metadata, timestamp, source) =
            Self::common_metric_to_db_values(metric)?;

        sqlx::query(
            r#"
            INSERT INTO metrics (
                metric_id, name, value, metric_type, unit, labels,
                timestamp, source, tenant_id, device_id, session_id,
                schema_version, metadata
            )
            VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12, ?13)
            "#,
        )
        .bind(metric_id)
        .bind(name)
        .bind(value)
        .bind(metric_type)
        .bind(None::<String>) // unit
        .bind(labels)
        .bind(timestamp)
        .bind(source)
        .bind("default") // tenant_id
        .bind("default") // device_id
        .bind("default") // session_id
        .bind("1.0") // schema_version
        .bind(metadata)
        .execute(&self.pool)
        .await
        .map_err(|e| Error::storage(format!("Failed to store metric: {}", e)))?;

        Ok(())
    }

    async fn store_metrics(&self, metrics: &[CommonMetric]) -> Result<()> {
        let mut tx = self.pool.begin().await
            .map_err(|e| Error::storage(format!("Failed to begin transaction: {}", e)))?;

        for metric in metrics {
            let (metric_id, name, value, metric_type, labels, metadata, timestamp, source) =
                Self::common_metric_to_db_values(metric)?;

            sqlx::query(
                r#"
                INSERT INTO metrics (
                    metric_id, name, value, metric_type, unit, labels,
                    timestamp, source, tenant_id, device_id, session_id,
                    schema_version, metadata
                )
                VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12, ?13)
                "#,
            )
            .bind(metric_id)
            .bind(name)
            .bind(value)
            .bind(metric_type)
            .bind(None::<String>)
            .bind(labels)
            .bind(timestamp)
            .bind(source)
            .bind("default")
            .bind("default")
            .bind("default")
            .bind("1.0")
            .bind(metadata)
            .execute(&mut *tx)
            .await
            .map_err(|e| Error::storage(format!("Failed to store metric: {}", e)))?;
        }

        tx.commit().await
            .map_err(|e| Error::storage(format!("Failed to commit transaction: {}", e)))?;

        Ok(())
    }

    async fn query_metrics(
        &self,
        query: &MetricQuery,
        pagination: &Pagination,
    ) -> Result<PaginatedResponse<CommonMetric>> {
        let mut sql = String::from("SELECT * FROM metrics WHERE 1=1");
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

        // Get total count
        let total: (i64,) = sqlx::query_as(&count_sql)
            .fetch_one(&self.pool)
            .await
            .map_err(|e| Error::storage(format!("Failed to count metrics: {}", e)))?;

        // Add pagination
        sql.push_str(&format!(" ORDER BY timestamp DESC LIMIT {} OFFSET {}",
            pagination.limit(), pagination.offset()));

        // Execute query
        let rows: Vec<DbMetric> = sqlx::query_as(&sql)
            .fetch_all(&self.pool)
            .await
            .map_err(|e| Error::storage(format!("Failed to query metrics: {}", e)))?;

        let items: Result<Vec<CommonMetric>> = rows.iter()
            .map(Self::db_metric_to_common)
            .collect();

        Ok(PaginatedResponse::new(
            items?,
            total.0 as usize,
            pagination.page,
            pagination.page_size,
        ))
    }

    async fn get_metric(&self, id: uuid::Uuid) -> Result<Option<CommonMetric>> {
        let row: Option<DbMetric> = sqlx::query_as("SELECT * FROM metrics WHERE metric_id = ?")
            .bind(id.to_string())
            .fetch_optional(&self.pool)
            .await
            .map_err(|e| Error::storage(format!("Failed to get metric: {}", e)))?;

        match row {
            Some(db_metric) => Ok(Some(Self::db_metric_to_common(&db_metric)?)),
            None => Ok(None),
        }
    }

    async fn delete_old_metrics(&self, before: DateTime<Utc>) -> Result<u64> {
        let result = sqlx::query("DELETE FROM metrics WHERE timestamp < ?")
            .bind(before.timestamp())
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

        let count: (i64,) = sqlx::query_as(&sql)
            .fetch_one(&self.pool)
            .await
            .map_err(|e| Error::storage(format!("Failed to count metrics: {}", e)))?;

        Ok(count.0 as u64)
    }
}

// EventsStore implementation
#[async_trait]
impl EventsStore for DesktopStorageAdapter {
    async fn store_event(&self, event: &CommonEvent) -> Result<()> {
        let payload_bytes = serde_json::to_vec(&event.payload)?;
        let metadata_json = serde_json::to_string(&event.metadata)?;

        sqlx::query(
            r#"
            INSERT INTO events (
                event_id, event_type, activity_type, severity, source,
                application, window_title, duration_ms, data, metadata,
                timestamp, tenant_id, device_id, session_id, schema_version, processed
            )
            VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12, ?13, ?14, ?15, ?16)
            "#,
        )
        .bind(event.id.to_string())
        .bind(&event.event_type)
        .bind(&event.event_type) // activity_type = event_type for now
        .bind(format!("{:?}", event.severity))
        .bind(&event.source)
        .bind(event.resource.as_ref())
        .bind(event.description.as_ref())
        .bind(None::<i64>) // duration_ms
        .bind(payload_bytes)
        .bind(metadata_json)
        .bind(event.timestamp.timestamp())
        .bind("default")
        .bind("default")
        .bind("default")
        .bind("1.0")
        .bind(false)
        .execute(&self.pool)
        .await
        .map_err(|e| Error::storage(format!("Failed to store event: {}", e)))?;

        Ok(())
    }

    async fn store_events(&self, events: &[CommonEvent]) -> Result<()> {
        let mut tx = self.pool.begin().await
            .map_err(|e| Error::storage(format!("Failed to begin transaction: {}", e)))?;

        for event in events {
            let payload_bytes = serde_json::to_vec(&event.payload)?;
            let metadata_json = serde_json::to_string(&event.metadata)?;

            sqlx::query(
                r#"
                INSERT INTO events (
                    event_id, event_type, activity_type, severity, source,
                    application, window_title, duration_ms, data, metadata,
                    timestamp, tenant_id, device_id, session_id, schema_version, processed
                )
                VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12, ?13, ?14, ?15, ?16)
                "#,
            )
            .bind(event.id.to_string())
            .bind(&event.event_type)
            .bind(&event.event_type)
            .bind(format!("{:?}", event.severity))
            .bind(&event.source)
            .bind(event.resource.as_ref())
            .bind(event.description.as_ref())
            .bind(None::<i64>)
            .bind(payload_bytes)
            .bind(metadata_json)
            .bind(event.timestamp.timestamp())
            .bind("default")
            .bind("default")
            .bind("default")
            .bind("1.0")
            .bind(false)
            .execute(&mut *tx)
            .await
            .map_err(|e| Error::storage(format!("Failed to store event: {}", e)))?;
        }

        tx.commit().await
            .map_err(|e| Error::storage(format!("Failed to commit transaction: {}", e)))?;

        Ok(())
    }

    async fn query_events(
        &self,
        query: &EventQuery,
        pagination: &Pagination,
    ) -> Result<PaginatedResponse<CommonEvent>> {
        let mut sql = String::from("SELECT * FROM events WHERE 1=1");
        let mut count_sql = String::from("SELECT COUNT(*) FROM events WHERE 1=1");

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

        // Get total count
        let total: (i64,) = sqlx::query_as(&count_sql)
            .fetch_one(&self.pool)
            .await
            .map_err(|e| Error::storage(format!("Failed to count events: {}", e)))?;

        // Add pagination
        sql.push_str(&format!(" ORDER BY timestamp DESC LIMIT {} OFFSET {}",
            pagination.limit(), pagination.offset()));

        // Execute query
        let rows: Vec<DbEvent> = sqlx::query_as(&sql)
            .fetch_all(&self.pool)
            .await
            .map_err(|e| Error::storage(format!("Failed to query events: {}", e)))?;

        let items: Result<Vec<CommonEvent>> = rows.iter()
            .map(|db_event| {
                Ok(CommonEvent {
                    id: Uuid::parse_str(&db_event.event_id)
                        .map_err(|e| Error::storage(format!("Invalid UUID: {}", e)))?,
                    event_type: db_event.event_type.clone(),
                    title: db_event.window_title.clone().unwrap_or_default(),
                    description: db_event.window_title.clone(),
                    severity: serde_json::from_str(&format!("\"{}\"", db_event.severity.to_lowercase()))?,
                    priority: Priority::Normal, // Default
                    status: if db_event.processed { Status::Success } else { Status::Pending },
                    timestamp: DateTime::from_timestamp(db_event.timestamp, 0)
                        .ok_or_else(|| Error::storage("Invalid timestamp"))?,
                    source: db_event.source.clone(),
                    resource: db_event.application.clone(),
                    payload: db_event.metadata.as_ref()
                        .and_then(|m| serde_json::from_str(m).ok())
                        .unwrap_or(serde_json::json!({})),
                    labels: HashMap::new(),
                    metadata: db_event.metadata.as_ref()
                        .and_then(|m| serde_json::from_str(m).ok())
                        .unwrap_or_default(),
                    related_events: vec![],
                })
            })
            .collect();

        Ok(PaginatedResponse::new(
            items?,
            total.0 as usize,
            pagination.page,
            pagination.page_size,
        ))
    }

    async fn get_event(&self, id: uuid::Uuid) -> Result<Option<CommonEvent>> {
        let row: Option<DbEvent> = sqlx::query_as("SELECT * FROM events WHERE event_id = ?")
            .bind(id.to_string())
            .fetch_optional(&self.pool)
            .await
            .map_err(|e| Error::storage(format!("Failed to get event: {}", e)))?;

        match row {
            Some(db_event) => {
                Ok(Some(CommonEvent {
                    id: Uuid::parse_str(&db_event.event_id)
                        .map_err(|e| Error::storage(format!("Invalid UUID: {}", e)))?,
                    event_type: db_event.event_type.clone(),
                    title: db_event.window_title.clone().unwrap_or_default(),
                    description: db_event.window_title.clone(),
                    severity: serde_json::from_str(&format!("\"{}\"", db_event.severity.to_lowercase()))?,
                    priority: Priority::Normal,
                    status: if db_event.processed { Status::Success } else { Status::Pending },
                    timestamp: DateTime::from_timestamp(db_event.timestamp, 0)
                        .ok_or_else(|| Error::storage("Invalid timestamp"))?,
                    source: db_event.source.clone(),
                    resource: db_event.application.clone(),
                    payload: db_event.metadata.as_ref()
                        .and_then(|m| serde_json::from_str(m).ok())
                        .unwrap_or(serde_json::json!({})),
                    labels: HashMap::new(),
                    metadata: db_event.metadata.as_ref()
                        .and_then(|m| serde_json::from_str(m).ok())
                        .unwrap_or_default(),
                    related_events: vec![],
                }))
            },
            None => Ok(None),
        }
    }

    async fn update_event_status(&self, id: uuid::Uuid, status: Status) -> Result<()> {
        let processed = matches!(status, Status::Success | Status::Failed);
        
        sqlx::query("UPDATE events SET processed = ?, updated_at = ? WHERE event_id = ?")
            .bind(processed)
            .bind(Utc::now().timestamp())
            .bind(id.to_string())
            .execute(&self.pool)
            .await
            .map_err(|e| Error::storage(format!("Failed to update event status: {}", e)))?;

        Ok(())
    }

    async fn delete_old_events(&self, before: DateTime<Utc>) -> Result<u64> {
        let result = sqlx::query("DELETE FROM events WHERE timestamp < ?")
            .bind(before.timestamp())
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

        let count: (i64,) = sqlx::query_as(&sql)
            .fetch_one(&self.pool)
            .await
            .map_err(|e| Error::storage(format!("Failed to count events: {}", e)))?;

        Ok(count.0 as u64)
    }
}

#[async_trait]
impl ActionsStore for DesktopStorageAdapter {
    async fn store_action(&self, action: &CommonAction) -> Result<()> {
        let input_json = serde_json::to_string(&action.input)?;
        let output_json = action
            .output
            .as_ref()
            .map(|o| serde_json::to_string(o))
            .transpose()?;
        let labels_json = serde_json::to_string(&action.labels)?;
        let metadata_json = serde_json::to_string(&action.metadata)?;
        sqlx::query(
            r#"
            INSERT INTO actions (
                action_id, action_type, status, command, args, working_dir,
                env, timeout_ms, exit_code, stdout, stderr, error,
                duration_ms, timestamp, tenant_id, device_id, session_id, schema_version
            )
            VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12, ?13, ?14, ?15, ?16, ?17, ?18)
            "#,
        )
        .bind(action.id.to_string())
        .bind(&action.action_type)
        .bind(format!("{:?}", action.status))
        .bind(&action.name) // command = name
        .bind(input_json) // args = input
        .bind(None::<String>) // working_dir
        .bind(None::<String>) // env
        .bind(action.timeout_secs.map(|t| t as i64 * 1000)) // timeout_ms
        .bind(None::<i32>) // exit_code
        .bind(output_json.as_ref()) // stdout = output
        .bind(None::<String>) // stderr
        .bind(&action.error) // error
        .bind(None::<i64>) // duration_ms
        .bind(action.created_at.timestamp())
        .bind("default")
        .bind("default")
        .bind("default")
        .bind("1.0")
        .bind(labels_json)
        .bind(metadata_json)
        .execute(&self.pool)
        .await
        .map_err(|e| Error::storage(format!("Failed to store action: {}", e)))?;

        Ok(())
    }

    async fn query_actions(
        &self,
        query: &ActionQuery,
        pagination: &Pagination,
    ) -> Result<PaginatedResponse<CommonAction>> {
        let mut sql = String::from("SELECT * FROM actions WHERE 1=1");
        let mut count_sql = String::from("SELECT COUNT(*) FROM actions WHERE 1=1");

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

        // Get total count
        let total: (i64,) = sqlx::query_as(&count_sql)
            .fetch_one(&self.pool)
            .await
            .map_err(|e| Error::storage(format!("Failed to count actions: {}", e)))?;

        // Add pagination
        sql.push_str(&format!(" ORDER BY created_at DESC LIMIT {} OFFSET {}",
            pagination.limit(), pagination.offset()));

        // Execute query
        let rows: Vec<DbAction> = sqlx::query_as(&sql)
            .fetch_all(&self.pool)
            .await
            .map_err(|e| Error::storage(format!("Failed to query actions: {}", e)))?;

        let items: Result<Vec<CommonAction>> = rows.iter().map(Self::db_action_to_common).collect();

        Ok(PaginatedResponse::new(
            items?,
            total.0 as usize,
            pagination.page,
            pagination.page_size,
        ))
    }

    async fn get_action(&self, id: uuid::Uuid) -> Result<Option<CommonAction>> {
        let row: Option<DbAction> = sqlx::query_as("SELECT * FROM actions WHERE action_id = ?")
            .bind(id.to_string())
            .fetch_optional(&self.pool)
            .await
            .map_err(|e| Error::storage(format!("Failed to get action: {}", e)))?;

        match row {
            Some(db_action) => Self::db_action_to_common(&db_action).map(Some),
            None => Ok(None),
        }
    }

    async fn update_action_status(&self, id: uuid::Uuid, status: Status) -> Result<()> {
        sqlx::query("UPDATE actions SET status = ?, updated_at = ? WHERE action_id = ?")
            .bind(format!("{:?}", status))
            .bind(Utc::now().timestamp())
            .bind(id.to_string())
            .execute(&self.pool)
            .await
            .map_err(|e| Error::storage(format!("Failed to update action status: {}", e)))?;

        Ok(())
    }

    async fn update_action_result(
        &self,
        id: uuid::Uuid,
        status: Status,
        output: Option<serde_json::Value>,
        error: Option<String>,
    ) -> Result<()> {
        let output_json = output.map(|o| serde_json::to_string(&o)).transpose()?;

        sqlx::query("UPDATE actions SET status = ?, stdout = ?, error = ?, updated_at = ? WHERE action_id = ?")
            .bind(format!("{:?}", status))
            .bind(output_json)
            .bind(error)
            .bind(Utc::now().timestamp())
            .bind(id.to_string())
            .execute(&self.pool)
            .await
            .map_err(|e| Error::storage(format!("Failed to update action result: {}", e)))?;

        Ok(())
    }

    async fn delete_old_actions(&self, before: DateTime<Utc>) -> Result<u64> {
        let result = sqlx::query("DELETE FROM actions WHERE timestamp < ?")
            .bind(before.timestamp())
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

        let count: (i64,) = sqlx::query_as(&sql)
            .fetch_one(&self.pool)
            .await
            .map_err(|e| Error::storage(format!("Failed to count actions: {}", e)))?;

        Ok(count.0 as u64)
    }
}

#[async_trait]
impl ConfigStore for DesktopStorageAdapter {
    async fn get_config(&self, key: &str) -> Result<Option<String>> {
        let row: Option<(String,)> = sqlx::query_as("SELECT value FROM config WHERE key = ?")
            .bind(key)
            .fetch_optional(&self.pool)
            .await
            .map_err(|e| Error::storage(format!("Failed to get config: {}", e)))?;

        Ok(row.map(|(value,)| value))
    }

    async fn set_config(&self, key: &str, value: &str) -> Result<()> {
        sqlx::query(
            r#"
            INSERT INTO config (key, value)
            VALUES (?1, ?2)
            ON CONFLICT(key) DO UPDATE SET value = ?2, updated_at = CURRENT_TIMESTAMP
            "#,
        )
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
        let rows: Vec<(String,)> = sqlx::query_as("SELECT key FROM config ORDER BY key")
            .fetch_all(&self.pool)
            .await
            .map_err(|e| Error::storage(format!("Failed to list config keys: {}", e)))?;

        Ok(rows.into_iter().map(|(key,)| key).collect())
    }

    async fn get_all_config(&self) -> Result<HashMap<String, String>> {
        let rows: Vec<(String, String)> = sqlx::query_as("SELECT key, value FROM config")
            .fetch_all(&self.pool)
            .await
            .map_err(|e| Error::storage(format!("Failed to get all config: {}", e)))?;

        Ok(rows.into_iter().collect())
    }
}
