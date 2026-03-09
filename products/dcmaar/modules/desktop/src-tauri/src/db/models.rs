// Database models matching the schema
// Follows WSRF-ARCH-001 (contracts first)

use serde::{Deserialize, Serialize};
use sqlx::FromRow;

#[derive(Debug, Clone, Serialize, Deserialize, FromRow)]
pub struct Metric {
    pub id: i64,
    pub metric_id: String,
    pub name: String,
    pub value: f64,
    pub metric_type: String,
    pub unit: Option<String>,
    pub labels: Option<String>, // JSON
    pub timestamp: i64,
    pub source: String,
    pub tenant_id: String,
    pub device_id: String,
    pub session_id: String,
    pub schema_version: String,
    pub metadata: Option<String>, // JSON
    pub created_at: i64,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct NewMetric {
    pub metric_id: String,
    pub name: String,
    pub value: f64,
    pub metric_type: String,
    pub unit: Option<String>,
    pub labels: Option<String>,
    pub timestamp: i64,
    pub source: String,
    pub tenant_id: String,
    pub device_id: String,
    pub session_id: String,
    pub schema_version: String,
    pub metadata: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize, FromRow)]
pub struct Event {
    pub id: i64,
    pub event_id: String,
    pub event_type: String,
    pub activity_type: String,
    pub severity: String,
    pub source: String,
    pub application: Option<String>,
    pub window_title: Option<String>,
    pub duration_ms: Option<i64>,
    pub data: Option<Vec<u8>>, // Binary
    pub metadata: Option<String>, // JSON
    pub timestamp: i64,
    pub tenant_id: String,
    pub device_id: String,
    pub session_id: String,
    pub schema_version: String,
    pub processed: bool,
    pub created_at: i64,
    pub updated_at: i64,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct NewEvent {
    pub event_id: String,
    pub event_type: String,
    pub activity_type: String,
    pub severity: String,
    pub source: String,
    pub application: Option<String>,
    pub window_title: Option<String>,
    pub duration_ms: Option<i64>,
    pub data: Option<Vec<u8>>,
    pub metadata: Option<String>,
    pub timestamp: i64,
    pub tenant_id: String,
    pub device_id: String,
    pub session_id: String,
    pub schema_version: String,
}

#[derive(Debug, Clone, Serialize, Deserialize, FromRow)]
pub struct Action {
    pub id: i64,
    pub action_id: String,
    pub action_type: String,
    pub status: String,
    pub command: String,
    pub args: Option<String>, // JSON array
    pub working_dir: Option<String>,
    pub env: Option<String>, // JSON object
    pub timeout_ms: Option<i64>,
    pub exit_code: Option<i32>,
    pub stdout: Option<String>,
    pub stderr: Option<String>,
    pub error: Option<String>,
    pub duration_ms: Option<i64>,
    pub started_at: Option<i64>,
    pub completed_at: Option<i64>,
    pub metadata: Option<String>, // JSON
    pub created_at: i64,
    pub updated_at: i64,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct NewAction {
    pub action_id: String,
    pub action_type: String,
    pub status: String,
    pub command: String,
    pub args: Option<String>,
    pub working_dir: Option<String>,
    pub env: Option<String>,
    pub timeout_ms: Option<i64>,
    pub metadata: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize, FromRow)]
pub struct AgentConfig {
    pub id: i64,
    pub agent_id: String,
    pub version: String,
    pub config: String, // JSON
    pub is_active: bool,
    pub created_at: i64,
    pub updated_at: i64,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct NewAgentConfig {
    pub agent_id: String,
    pub version: String,
    pub config: String,
}

#[derive(Debug, Clone, Serialize, Deserialize, FromRow)]
pub struct SyncState {
    pub id: i64,
    pub source: String,
    pub last_sync_at: i64,
    pub last_metric_timestamp: Option<i64>,
    pub last_event_timestamp: Option<i64>,
    pub status: String,
    pub error_message: Option<String>,
    pub metadata: Option<String>, // JSON
    pub created_at: i64,
    pub updated_at: i64,
}

#[derive(Debug, Clone, Serialize, Deserialize, FromRow)]
pub struct OperationQueue {
    pub id: i64,
    pub operation_type: String,
    pub operation_data: String, // JSON
    pub retry_count: i32,
    pub max_retries: i32,
    pub next_retry_at: Option<i64>,
    pub last_error: Option<String>,
    pub status: String,
    pub created_at: i64,
    pub updated_at: i64,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct NewOperationQueue {
    pub operation_type: String,
    pub operation_data: String,
    pub max_retries: i32,
}

#[derive(Debug, Clone, Serialize, Deserialize, FromRow)]
pub struct AuditLog {
    pub id: i64,
    pub event_type: String,
    pub actor: String,
    pub action: String,
    pub resource: Option<String>,
    pub result: String,
    pub details: Option<String>, // JSON
    pub ip_address: Option<String>,
    pub user_agent: Option<String>,
    pub timestamp: i64,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct NewAuditLog {
    pub event_type: String,
    pub actor: String,
    pub action: String,
    pub resource: Option<String>,
    pub result: String,
    pub details: Option<String>,
    pub ip_address: Option<String>,
    pub user_agent: Option<String>,
}

// Query filters
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct MetricFilter {
    pub name: Option<String>,
    pub source: Option<String>,
    pub start_time: Option<i64>,
    pub end_time: Option<i64>,
    pub limit: Option<i64>,
    pub offset: Option<i64>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct EventFilter {
    pub event_type: Option<String>,
    pub activity_type: Option<String>,
    pub severity: Option<String>,
    pub source: Option<String>,
    pub start_time: Option<i64>,
    pub end_time: Option<i64>,
    pub processed: Option<bool>,
    pub limit: Option<i64>,
    pub offset: Option<i64>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ActionFilter {
    pub action_type: Option<String>,
    pub status: Option<String>,
    pub start_time: Option<i64>,
    pub end_time: Option<i64>,
    pub limit: Option<i64>,
    pub offset: Option<i64>,
}
