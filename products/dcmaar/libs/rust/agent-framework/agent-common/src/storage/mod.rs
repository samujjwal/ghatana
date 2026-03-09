//! Storage trait abstractions and implementations.
//!
//! This module provides storage interfaces for metrics, events, actions, and configuration.

use crate::{
    error::Result,
    models::{Action, ActionQuery, Event, EventQuery, Metric, MetricQuery},
    types::{PaginatedResponse, Pagination, ResourceId},
};
use async_trait::async_trait;

pub mod sqlite;

/// Storage trait for metrics
#[async_trait]
pub trait MetricsStore: Send + Sync {
    /// Store a metric
    async fn store_metric(&self, metric: &Metric) -> Result<()>;
    
    /// Store multiple metrics in a batch
    async fn store_metrics(&self, metrics: &[Metric]) -> Result<()>;
    
    /// Query metrics
    async fn query_metrics(
        &self,
        query: &MetricQuery,
        pagination: &Pagination,
    ) -> Result<PaginatedResponse<Metric>>;
    
    /// Get a metric by ID
    async fn get_metric(&self, id: ResourceId) -> Result<Option<Metric>>;
    
    /// Delete metrics older than the specified timestamp
    async fn delete_old_metrics(&self, before: chrono::DateTime<chrono::Utc>) -> Result<u64>;
    
    /// Get metric count
    async fn count_metrics(&self, query: &MetricQuery) -> Result<u64>;
}

/// Storage trait for events
#[async_trait]
pub trait EventsStore: Send + Sync {
    /// Store an event
    async fn store_event(&self, event: &Event) -> Result<()>;
    
    /// Store multiple events in a batch
    async fn store_events(&self, events: &[Event]) -> Result<()>;
    
    /// Query events
    async fn query_events(
        &self,
        query: &EventQuery,
        pagination: &Pagination,
    ) -> Result<PaginatedResponse<Event>>;
    
    /// Get an event by ID
    async fn get_event(&self, id: ResourceId) -> Result<Option<Event>>;
    
    /// Update event status
    async fn update_event_status(&self, id: ResourceId, status: crate::types::Status) -> Result<()>;
    
    /// Delete events older than the specified timestamp
    async fn delete_old_events(&self, before: chrono::DateTime<chrono::Utc>) -> Result<u64>;
    
    /// Get event count
    async fn count_events(&self, query: &EventQuery) -> Result<u64>;
}

/// Storage trait for actions
#[async_trait]
pub trait ActionsStore: Send + Sync {
    /// Store an action
    async fn store_action(&self, action: &Action) -> Result<()>;
    
    /// Query actions
    async fn query_actions(
        &self,
        query: &ActionQuery,
        pagination: &Pagination,
    ) -> Result<PaginatedResponse<Action>>;
    
    /// Get an action by ID
    async fn get_action(&self, id: ResourceId) -> Result<Option<Action>>;
    
    /// Update action status
    async fn update_action_status(&self, id: ResourceId, status: crate::types::Status) -> Result<()>;
    
    /// Update action with execution result
    async fn update_action_result(
        &self,
        id: ResourceId,
        status: crate::types::Status,
        output: Option<serde_json::Value>,
        error: Option<String>,
    ) -> Result<()>;
    
    /// Delete actions older than the specified timestamp
    async fn delete_old_actions(&self, before: chrono::DateTime<chrono::Utc>) -> Result<u64>;
    
    /// Get action count
    async fn count_actions(&self, query: &ActionQuery) -> Result<u64>;
}

/// Storage trait for configuration
#[async_trait]
pub trait ConfigStore: Send + Sync {
    /// Get configuration value by key
    async fn get_config(&self, key: &str) -> Result<Option<String>>;
    
    /// Set configuration value
    async fn set_config(&self, key: &str, value: &str) -> Result<()>;
    
    /// Delete configuration value
    async fn delete_config(&self, key: &str) -> Result<()>;
    
    /// List all configuration keys
    async fn list_config_keys(&self) -> Result<Vec<String>>;
    
    /// Get all configuration as a map
    async fn get_all_config(&self) -> Result<std::collections::HashMap<String, String>>;
}

/// Storage trait for audit logs
#[async_trait]
pub trait AuditStore: Send + Sync {
    /// Store an audit entry
    async fn store_audit_entry(&self, entry: &AuditEntry) -> Result<()>;
    
    /// Query audit entries
    async fn query_audit_entries(
        &self,
        query: &AuditQuery,
        pagination: &Pagination,
    ) -> Result<PaginatedResponse<AuditEntry>>;
    
    /// Get audit entry count
    async fn count_audit_entries(&self, query: &AuditQuery) -> Result<u64>;
}

/// Audit log entry
#[derive(Debug, Clone, serde::Serialize, serde::Deserialize)]
pub struct AuditEntry {
    /// Entry ID
    pub id: ResourceId,
    
    /// Timestamp
    pub timestamp: chrono::DateTime<chrono::Utc>,
    
    /// Actor (user, service, etc.)
    pub actor: String,
    
    /// Action performed
    pub action: String,
    
    /// Resource affected
    pub resource: Option<String>,
    
    /// Result of the action
    pub result: String,
    
    /// Additional details
    pub details: serde_json::Value,
}

/// Audit query parameters
#[derive(Debug, Clone, Default, serde::Serialize, serde::Deserialize)]
pub struct AuditQuery {
    /// Actor filter
    pub actor: Option<String>,
    
    /// Action filter
    pub action: Option<String>,
    
    /// Resource filter
    pub resource: Option<String>,
    
    /// Start time
    pub start_time: Option<chrono::DateTime<chrono::Utc>>,
    
    /// End time
    pub end_time: Option<chrono::DateTime<chrono::Utc>>,
}

/// Storage trait for plugin metadata
#[async_trait]
pub trait PluginStore: Send + Sync {
    /// Store plugin metadata
    async fn store_plugin(&self, plugin: &PluginMetadata) -> Result<()>;
    
    /// Get plugin metadata by ID
    async fn get_plugin(&self, id: &str) -> Result<Option<PluginMetadata>>;
    
    /// List all plugins
    async fn list_plugins(&self) -> Result<Vec<PluginMetadata>>;
    
    /// Delete plugin metadata
    async fn delete_plugin(&self, id: &str) -> Result<()>;
    
    /// Update plugin status
    async fn update_plugin_status(&self, id: &str, enabled: bool) -> Result<()>;
}

/// Plugin metadata
#[derive(Debug, Clone, serde::Serialize, serde::Deserialize)]
pub struct PluginMetadata {
    /// Plugin ID
    pub id: String,
    
    /// Plugin name
    pub name: String,
    
    /// Plugin version
    pub version: String,
    
    /// Plugin type
    pub plugin_type: String,
    
    /// Whether the plugin is enabled
    pub enabled: bool,
    
    /// Plugin configuration
    pub config: serde_json::Value,
    
    /// Plugin file path
    pub file_path: Option<String>,
    
    /// Created timestamp
    pub created_at: chrono::DateTime<chrono::Utc>,
    
    /// Updated timestamp
    pub updated_at: chrono::DateTime<chrono::Utc>,
}

/// Storage trait for secrets
#[async_trait]
pub trait SecretStore: Send + Sync {
    /// Store a secret
    async fn store_secret(&self, key: &str, value: &[u8]) -> Result<()>;
    
    /// Get a secret
    async fn get_secret(&self, key: &str) -> Result<Option<Vec<u8>>>;
    
    /// Delete a secret
    async fn delete_secret(&self, key: &str) -> Result<()>;
    
    /// List secret keys (not values)
    async fn list_secret_keys(&self) -> Result<Vec<String>>;
}

#[cfg(test)]
mod tests {
    use super::*;

    // Test that traits are object-safe
    #[test]
    fn test_trait_object_safety() {
        fn _assert_metrics_store(_: &dyn MetricsStore) {}
        fn _assert_events_store(_: &dyn EventsStore) {}
        fn _assert_actions_store(_: &dyn ActionsStore) {}
        fn _assert_config_store(_: &dyn ConfigStore) {}
        fn _assert_audit_store(_: &dyn AuditStore) {}
        fn _assert_plugin_store(_: &dyn PluginStore) {}
        fn _assert_secret_store(_: &dyn SecretStore) {}
    }
}
