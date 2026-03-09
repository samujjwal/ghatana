//! Event data models and types.

use crate::types::{Metadata, Priority, ResourceId, Severity, Status, Timestamp};
use serde::{Deserialize, Serialize};
use std::collections::HashMap;

/// An event in the system
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Event {
    /// Unique identifier for this event
    pub id: ResourceId,
    
    /// Event type/category
    pub event_type: String,
    
    /// Event title/summary
    pub title: String,
    
    /// Event description
    pub description: Option<String>,
    
    /// Event severity
    pub severity: Severity,
    
    /// Event priority
    pub priority: Priority,
    
    /// Event status
    pub status: Status,
    
    /// Timestamp when the event occurred
    pub timestamp: Timestamp,
    
    /// Source of the event (service, component, etc.)
    pub source: String,
    
    /// Resource affected by the event
    pub resource: Option<String>,
    
    /// Event payload/data
    pub payload: serde_json::Value,
    
    /// Labels/tags for the event
    pub labels: HashMap<String, String>,
    
    /// Additional metadata
    pub metadata: Metadata,
    
    /// Related event IDs
    pub related_events: Vec<ResourceId>,
}

/// Event type classification
#[derive(Debug, Clone, PartialEq, Eq, Hash, Serialize, Deserialize)]
pub enum EventType {
    /// System event
    System,
    /// Application event
    Application,
    /// Security event
    Security,
    /// Audit event
    Audit,
    /// Alert event
    Alert,
    /// Metric threshold event
    MetricThreshold,
    /// Custom event type
    Custom(String),
}

impl std::fmt::Display for EventType {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            Self::System => write!(f, "system"),
            Self::Application => write!(f, "application"),
            Self::Security => write!(f, "security"),
            Self::Audit => write!(f, "audit"),
            Self::Alert => write!(f, "alert"),
            Self::MetricThreshold => write!(f, "metric_threshold"),
            Self::Custom(s) => write!(f, "{}", s),
        }
    }
}

/// Event query parameters
#[derive(Debug, Clone, Serialize, Deserialize)]
#[derive(Default)]
pub struct EventQuery {
    /// Event type filter
    pub event_type: Option<String>,
    
    /// Source filter
    pub source: Option<String>,
    
    /// Resource filter
    pub resource: Option<String>,
    
    /// Severity filter
    pub severity: Option<Severity>,
    
    /// Priority filter
    pub priority: Option<Priority>,
    
    /// Status filter
    pub status: Option<Status>,
    
    /// Label filters
    pub labels: HashMap<String, String>,
    
    /// Start time for range query
    pub start_time: Option<Timestamp>,
    
    /// End time for range query
    pub end_time: Option<Timestamp>,
    
    /// Search text in title/description
    pub search_text: Option<String>,
}


/// Event subscription configuration
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct EventSubscription {
    /// Subscription ID
    pub id: ResourceId,
    
    /// Event types to subscribe to
    pub event_types: Vec<String>,
    
    /// Severity threshold (minimum severity to receive)
    pub min_severity: Severity,
    
    /// Label filters
    pub label_filters: HashMap<String, String>,
    
    /// Callback URL or handler
    pub handler: String,
    
    /// Whether the subscription is active
    pub active: bool,
}

/// Event stream configuration
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct EventStreamConfig {
    /// Stream name
    pub name: String,
    
    /// Buffer size
    pub buffer_size: usize,
    
    /// Event types to include
    pub event_types: Vec<String>,
    
    /// Minimum severity
    pub min_severity: Severity,
    
    /// Whether to persist events
    pub persist: bool,
}

impl Default for EventStreamConfig {
    fn default() -> Self {
        Self {
            name: "default".to_string(),
            buffer_size: 1000,
            event_types: vec![],
            min_severity: Severity::Info,
            persist: true,
        }
    }
}

/// Event correlation rule
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct EventCorrelation {
    /// Rule ID
    pub id: ResourceId,
    
    /// Rule name
    pub name: String,
    
    /// Event types to correlate
    pub event_types: Vec<String>,
    
    /// Time window in seconds
    pub time_window_secs: u64,
    
    /// Correlation conditions
    pub conditions: Vec<CorrelationCondition>,
    
    /// Action to take when correlation matches
    pub action: String,
}

/// Correlation condition
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CorrelationCondition {
    /// Field to check
    pub field: String,
    
    /// Operator
    pub operator: ConditionOperator,
    
    /// Value to compare against
    pub value: serde_json::Value,
}

/// Condition operator
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum ConditionOperator {
    /// Equal
    Eq,
    /// Not equal
    Ne,
    /// Greater than
    Gt,
    /// Greater than or equal
    Gte,
    /// Less than
    Lt,
    /// Less than or equal
    Lte,
    /// Contains
    Contains,
    /// Matches regex
    Matches,
}

#[cfg(test)]
mod tests {
    use super::*;
    use chrono::Utc;
    use uuid::Uuid;

    #[test]
    fn test_event_creation() {
        let event = Event {
            id: Uuid::new_v4(),
            event_type: "system.startup".to_string(),
            title: "System Started".to_string(),
            description: Some("System has started successfully".to_string()),
            severity: Severity::Info,
            priority: Priority::Normal,
            status: Status::Success,
            timestamp: Utc::now(),
            source: "system".to_string(),
            resource: None,
            payload: serde_json::json!({}),
            labels: HashMap::new(),
            metadata: HashMap::new(),
            related_events: vec![],
        };
        
        assert_eq!(event.event_type, "system.startup");
        assert_eq!(event.severity, Severity::Info);
    }

    #[test]
    fn test_event_type_display() {
        assert_eq!(EventType::System.to_string(), "system");
        assert_eq!(EventType::Custom("test".to_string()).to_string(), "test");
    }
}
