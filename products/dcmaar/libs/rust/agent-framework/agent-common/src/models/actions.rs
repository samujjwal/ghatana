//! Action data models and types.

use crate::types::{Metadata, Priority, ResourceId, Status, Timestamp};
use serde::{Deserialize, Serialize};
use std::collections::HashMap;

/// An action to be executed
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Action {
    /// Unique identifier for this action
    pub id: ResourceId,
    
    /// Action type
    pub action_type: String,
    
    /// Action name/title
    pub name: String,
    
    /// Action description
    pub description: Option<String>,
    
    /// Action priority
    pub priority: Priority,
    
    /// Action status
    pub status: Status,
    
    /// Timestamp when the action was created
    pub created_at: Timestamp,
    
    /// Timestamp when the action was last updated
    pub updated_at: Timestamp,
    
    /// Timestamp when the action was executed (if applicable)
    pub executed_at: Option<Timestamp>,
    
    /// Timestamp when the action completed (if applicable)
    pub completed_at: Option<Timestamp>,
    
    /// Action input parameters
    pub input: serde_json::Value,
    
    /// Action output/result
    pub output: Option<serde_json::Value>,
    
    /// Error message if action failed
    pub error: Option<String>,
    
    /// Labels/tags for the action
    pub labels: HashMap<String, String>,
    
    /// Additional metadata
    pub metadata: Metadata,
    
    /// Retry configuration
    pub retry_config: Option<RetryConfig>,
    
    /// Timeout in seconds
    pub timeout_secs: Option<u64>,
}

/// Action type classification
#[derive(Debug, Clone, PartialEq, Eq, Hash, Serialize, Deserialize)]
pub enum ActionType {
    /// Remediation action
    Remediation,
    /// Alert action
    Alert,
    /// Notification action
    Notification,
    /// Automation action
    Automation,
    /// Webhook action
    Webhook,
    /// Script execution action
    Script,
    /// Custom action type
    Custom(String),
}

impl std::fmt::Display for ActionType {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            Self::Remediation => write!(f, "remediation"),
            Self::Alert => write!(f, "alert"),
            Self::Notification => write!(f, "notification"),
            Self::Automation => write!(f, "automation"),
            Self::Webhook => write!(f, "webhook"),
            Self::Script => write!(f, "script"),
            Self::Custom(s) => write!(f, "{}", s),
        }
    }
}

/// Retry configuration for actions
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct RetryConfig {
    /// Maximum number of retry attempts
    pub max_attempts: u32,
    
    /// Initial delay between retries in seconds
    pub initial_delay_secs: u64,
    
    /// Maximum delay between retries in seconds
    pub max_delay_secs: u64,
    
    /// Backoff multiplier
    pub backoff_multiplier: f64,
    
    /// Whether to use exponential backoff
    pub exponential_backoff: bool,
}

impl Default for RetryConfig {
    fn default() -> Self {
        Self {
            max_attempts: 3,
            initial_delay_secs: 1,
            max_delay_secs: 60,
            backoff_multiplier: 2.0,
            exponential_backoff: true,
        }
    }
}

/// Action execution request
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ActionExecutionRequest {
    /// Action type to execute
    pub action_type: String,
    
    /// Action input parameters
    pub input: serde_json::Value,
    
    /// Priority for execution
    pub priority: Priority,
    
    /// Labels/tags
    pub labels: HashMap<String, String>,
    
    /// Retry configuration
    pub retry_config: Option<RetryConfig>,
    
    /// Timeout in seconds
    pub timeout_secs: Option<u64>,
    
    /// Whether to execute asynchronously
    pub async_execution: bool,
}

/// Action execution result
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ActionExecutionResult {
    /// Action ID
    pub action_id: ResourceId,
    
    /// Execution status
    pub status: Status,
    
    /// Output data
    pub output: Option<serde_json::Value>,
    
    /// Error message if failed
    pub error: Option<String>,
    
    /// Execution duration in milliseconds
    pub duration_ms: u64,
    
    /// Number of retry attempts made
    pub retry_attempts: u32,
}

/// Action query parameters
#[derive(Debug, Clone, Serialize, Deserialize)]
#[derive(Default)]
pub struct ActionQuery {
    /// Action type filter
    pub action_type: Option<String>,
    
    /// Status filter
    pub status: Option<Status>,
    
    /// Priority filter
    pub priority: Option<Priority>,
    
    /// Label filters
    pub labels: HashMap<String, String>,
    
    /// Start time for range query
    pub start_time: Option<Timestamp>,
    
    /// End time for range query
    pub end_time: Option<Timestamp>,
    
    /// Search text in name/description
    pub search_text: Option<String>,
}


/// Action configuration
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ActionConfig {
    /// Action type
    pub action_type: String,
    
    /// Whether the action is enabled
    pub enabled: bool,
    
    /// Default priority
    pub default_priority: Priority,
    
    /// Default timeout in seconds
    pub default_timeout_secs: u64,
    
    /// Default retry configuration
    pub default_retry_config: RetryConfig,
    
    /// Action-specific configuration
    pub config: serde_json::Value,
}

impl Default for ActionConfig {
    fn default() -> Self {
        Self {
            action_type: String::new(),
            enabled: true,
            default_priority: Priority::Normal,
            default_timeout_secs: 300,
            default_retry_config: RetryConfig::default(),
            config: serde_json::json!({}),
        }
    }
}

/// Action schedule configuration
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ActionSchedule {
    /// Schedule ID
    pub id: ResourceId,
    
    /// Action type to execute
    pub action_type: String,
    
    /// Action input
    pub input: serde_json::Value,
    
    /// Cron expression for scheduling
    pub cron_expression: String,
    
    /// Whether the schedule is enabled
    pub enabled: bool,
    
    /// Next execution time
    pub next_execution: Option<Timestamp>,
    
    /// Last execution time
    pub last_execution: Option<Timestamp>,
}

#[cfg(test)]
mod tests {
    use super::*;
    use chrono::Utc;
    use uuid::Uuid;

    #[test]
    fn test_action_creation() {
        let action = Action {
            id: Uuid::new_v4(),
            action_type: "remediation.restart".to_string(),
            name: "Restart Service".to_string(),
            description: Some("Restart the failed service".to_string()),
            priority: Priority::High,
            status: Status::Pending,
            created_at: Utc::now(),
            updated_at: Utc::now(),
            executed_at: None,
            completed_at: None,
            input: serde_json::json!({"service": "web-server"}),
            output: None,
            error: None,
            labels: HashMap::new(),
            metadata: HashMap::new(),
            retry_config: Some(RetryConfig::default()),
            timeout_secs: Some(300),
        };
        
        assert_eq!(action.action_type, "remediation.restart");
        assert_eq!(action.priority, Priority::High);
    }

    #[test]
    fn test_retry_config_default() {
        let config = RetryConfig::default();
        assert_eq!(config.max_attempts, 3);
        assert!(config.exponential_backoff);
    }

    #[test]
    fn test_action_type_display() {
        assert_eq!(ActionType::Remediation.to_string(), "remediation");
        assert_eq!(ActionType::Custom("test".to_string()).to_string(), "test");
    }
}
