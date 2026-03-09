//! Backend API connector module
//!
//! Handles communication with the Guardian backend API for device registration,
//! policy synchronization, event upload, and other backend operations.

use chrono::{DateTime, Utc};
use serde::{Deserialize, Serialize};
use std::collections::HashMap;

pub mod auth;
pub mod client;
pub mod config;
pub mod event_upload;
pub mod policy_sync;
pub mod registration;

pub use auth::{TokenClaims, TokenManager, TokenMetadata, TokenStorage};
pub use client::GuardianApiConnector;
pub use config::ConnectorConfig;
pub use event_upload::EventUploadManager;
pub use policy_sync::PolicySyncManager;
pub use registration::DeviceRegistrationManager;

/// Connector error types
#[derive(Debug, Clone)]
pub enum ConnectorError {
    /// Connection error
    ConnectionError(String),
    /// Request failed
    RequestFailed(String),
    /// Authentication error
    AuthenticationError(String),
    /// Authorization error
    AuthorizationError(String),
    /// Certificate error
    CertificateError(String),
    /// Invalid configuration
    ConfigError(String),
    /// Resource not found
    ResourceNotFound(String),
    /// Server error
    ServerError(String),
    /// Serialization error
    SerializationError(String),
    /// Device registration error
    RegistrationError(String),
    /// Policy sync error
    PolicySyncError(String),
    /// Event upload error
    EventUploadError(String),
    /// Token management error
    TokenError(String),
}

impl std::fmt::Display for ConnectorError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            Self::ConnectionError(e) => write!(f, "Connection error: {}", e),
            Self::RequestFailed(e) => write!(f, "Request failed: {}", e),
            Self::AuthenticationError(e) => write!(f, "Authentication error: {}", e),
            Self::AuthorizationError(e) => write!(f, "Authorization error: {}", e),
            Self::CertificateError(e) => write!(f, "Certificate error: {}", e),
            Self::ConfigError(e) => write!(f, "Configuration error: {}", e),
            Self::ResourceNotFound(e) => write!(f, "Resource not found: {}", e),
            Self::ServerError(e) => write!(f, "Server error: {}", e),
            Self::SerializationError(e) => write!(f, "Serialization error: {}", e),
            Self::RegistrationError(e) => write!(f, "Registration error: {}", e),
            Self::PolicySyncError(e) => write!(f, "Policy sync error: {}", e),
            Self::EventUploadError(e) => write!(f, "Event upload error: {}", e),
            Self::TokenError(e) => write!(f, "Token error: {}", e),
        }
    }
}

impl std::error::Error for ConnectorError {}

/// Result type for connector operations
pub type Result<T> = std::result::Result<T, ConnectorError>;

/// Event data for backend submission
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Event {
    /// Unique event identifier
    pub event_id: String,
    /// Event type/classification
    pub event_type: String,
    /// Event timestamp
    pub timestamp: DateTime<Utc>,
    /// Device ID that generated event
    pub device_id: String,
    /// Event payload data
    pub data: serde_json::Value,
}

/// Device registration request
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct DeviceRegistrationRequest {
    /// Unique device identifier
    pub device_id: String,
    /// Human-readable device name
    pub device_name: String,
    /// Operating system name
    pub os: String,
    /// Operating system version
    pub os_version: String,
    /// Guardian agent version
    pub agent_version: String,
    /// Public key for device (PEM format)
    pub public_key: String,
}

/// Device registration response
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct DeviceRegistrationResponse {
    /// Device token for authentication
    pub device_token: String,
    /// Certificate for device
    pub certificate: String,
    /// Certificate expiration time
    pub expires_at: DateTime<Utc>,
    /// Backend configuration
    pub config: HashMap<String, String>,
}

/// Policy synchronization request
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PolicySyncRequest {
    /// Device ID
    pub device_id: String,
    /// Authentication token
    pub token: String,
    /// Last known policy version
    pub last_sync_version: Option<u64>,
}

/// Policy synchronization response
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PolicySyncResponse {
    /// Current policies
    pub policies: Vec<String>,
    /// Current policy version
    pub version: u64,
    /// Whether policies have changed
    pub changed: bool,
}

/// Event upload request
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct EventUploadRequest {
    /// Device ID
    pub device_id: String,
    /// Authentication token
    pub token: String,
    /// Events to upload
    pub events: Vec<Event>,
}

/// Event upload response
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct EventUploadResponse {
    /// Number of events accepted
    pub events_accepted: usize,
    /// Number of events rejected
    pub events_rejected: usize,
    /// Server timestamp
    pub server_timestamp: DateTime<Utc>,
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_connector_error_display() {
        let err = ConnectorError::ConnectionError("test".to_string());
        assert!(err.to_string().contains("Connection error"));
    }

    #[test]
    fn test_device_registration_request_serialization() {
        let req = DeviceRegistrationRequest {
            device_id: "test".to_string(),
            device_name: "Test".to_string(),
            os: "Linux".to_string(),
            os_version: "5.10".to_string(),
            agent_version: "1.0".to_string(),
            public_key: "key".to_string(),
        };

        let json = serde_json::to_string(&req).unwrap();
        assert!(json.contains("test"));
    }
}
