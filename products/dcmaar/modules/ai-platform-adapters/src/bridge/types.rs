//! Type definitions for Rust-TypeScript bridge communication
//!
//! These types must match the TypeScript definitions in:
//! `apps/agent/src-ts/connectors/types.ts`

use serde::{Deserialize, Serialize};
use std::collections::HashMap;

/// Message sent from Rust to TypeScript connectors
///
/// # Example
///
/// ```rust
/// use crate::bridge::BridgeMessage;
/// use serde_json::json;
///
/// let msg = BridgeMessage {
///     id: "msg-123".to_string(),
///     event_type: "metric".to_string(),
///     payload: json!({"cpu": 75.5, "memory": 8192}),
///     timestamp: 1698432000000,
///     metadata: Some({
///         let mut map = std::collections::HashMap::new();
///         map.insert("source".to_string(), json!("system"));
///         map
///     }),
/// };
/// ```
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct BridgeMessage {
    /// Unique identifier for this message
    pub id: String,

    /// Type of event (e.g., "metric", "log", "trace")
    pub event_type: String,

    /// Event payload (arbitrary JSON)
    pub payload: serde_json::Value,

    /// Unix timestamp in milliseconds
    pub timestamp: u64,

    /// Optional metadata
    #[serde(skip_serializing_if = "Option::is_none")]
    pub metadata: Option<HashMap<String, serde_json::Value>>,
}

impl BridgeMessage {
    /// Create a new bridge message
    pub fn new(
        id: impl Into<String>,
        event_type: impl Into<String>,
        payload: serde_json::Value,
    ) -> Self {
        Self {
            id: id.into(),
            event_type: event_type.into(),
            payload,
            timestamp: chrono::Utc::now().timestamp_millis() as u64,
            metadata: None,
        }
    }

    /// Add metadata to the message
    pub fn with_metadata(mut self, metadata: HashMap<String, serde_json::Value>) -> Self {
        self.metadata = Some(metadata);
        self
    }

    /// Add a single metadata field
    pub fn add_metadata(mut self, key: impl Into<String>, value: serde_json::Value) -> Self {
        self.metadata
            .get_or_insert_with(HashMap::new)
            .insert(key.into(), value);
        self
    }
}

/// Response from TypeScript back to Rust
///
/// # Example
///
/// ```rust
/// use crate::bridge::BridgeResponse;
/// use serde_json::json;
///
/// let response = BridgeResponse {
///     id: "msg-123".to_string(),
///     success: true,
///     error: None,
///     data: Some(json!({"processed": true})),
/// };
/// ```
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct BridgeResponse {
    /// Message ID this response corresponds to
    pub id: String,

    /// Whether the operation was successful
    pub success: bool,

    /// Error message if success is false
    #[serde(skip_serializing_if = "Option::is_none")]
    pub error: Option<String>,

    /// Optional response data
    #[serde(skip_serializing_if = "Option::is_none")]
    pub data: Option<serde_json::Value>,
}

impl BridgeResponse {
    /// Create a successful response
    pub fn success(id: impl Into<String>) -> Self {
        Self {
            id: id.into(),
            success: true,
            error: None,
            data: None,
        }
    }

    /// Create a successful response with data
    pub fn success_with_data(id: impl Into<String>, data: serde_json::Value) -> Self {
        Self {
            id: id.into(),
            success: true,
            error: None,
            data: Some(data),
        }
    }

    /// Create an error response
    pub fn error(id: impl Into<String>, error: impl Into<String>) -> Self {
        Self {
            id: id.into(),
            success: false,
            error: Some(error.into()),
            data: None,
        }
    }
}

/// Configuration for connector bridge
///
/// # Example
///
/// ```rust
/// use crate::bridge::ConnectorBridgeConfig;
///
/// let config = ConnectorBridgeConfig {
///     enabled: true,
///     ipc_channel: "dcmaar-agent-bridge".to_string(),
///     timeout_ms: 5000,
///     max_retries: 3,
/// };
/// ```
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ConnectorBridgeConfig {
    /// Whether the bridge is enabled
    pub enabled: bool,

    /// IPC channel name for communication
    pub ipc_channel: String,

    /// Timeout in milliseconds for responses
    pub timeout_ms: u64,

    /// Maximum number of retries for failed messages
    pub max_retries: u32,
}

impl Default for ConnectorBridgeConfig {
    fn default() -> Self {
        Self {
            enabled: false,
            ipc_channel: "dcmaar-agent-bridge".to_string(),
            timeout_ms: 5000,
            max_retries: 3,
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use serde_json::json;

    #[test]
    fn test_bridge_message_creation() {
        let msg = BridgeMessage::new("test-123", "metric", json!({"value": 42}));
        assert_eq!(msg.id, "test-123");
        assert_eq!(msg.event_type, "metric");
        assert_eq!(msg.payload, json!({"value": 42}));
        assert!(msg.timestamp > 0);
    }

    #[test]
    fn test_bridge_message_with_metadata() {
        let mut metadata = HashMap::new();
        metadata.insert("source".to_string(), json!("test"));

        let msg = BridgeMessage::new("test-123", "metric", json!({"value": 42}))
            .with_metadata(metadata);

        assert!(msg.metadata.is_some());
        assert_eq!(
            msg.metadata.unwrap().get("source").unwrap(),
            &json!("test")
        );
    }

    #[test]
    fn test_bridge_response_success() {
        let resp = BridgeResponse::success("test-123");
        assert_eq!(resp.id, "test-123");
        assert!(resp.success);
        assert!(resp.error.is_none());
    }

    #[test]
    fn test_bridge_response_error() {
        let resp = BridgeResponse::error("test-123", "Connection failed");
        assert_eq!(resp.id, "test-123");
        assert!(!resp.success);
        assert_eq!(resp.error.unwrap(), "Connection failed");
    }

    #[test]
    fn test_config_default() {
        let config = ConnectorBridgeConfig::default();
        assert!(!config.enabled);
        assert_eq!(config.ipc_channel, "dcmaar-agent-bridge");
        assert_eq!(config.timeout_ms, 5000);
        assert_eq!(config.max_retries, 3);
    }
}
