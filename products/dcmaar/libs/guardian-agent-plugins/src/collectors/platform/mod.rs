//! Platform abstraction layer for system-specific data collection
//!
//! Provides a unified trait-based interface for collecting system information
//! across Windows, macOS, and Linux platforms.
//!
//! # Architecture
//!
//! The platform abstraction layer consists of:
//! - `PlatformCollector` trait: Unified interface for platform-specific collectors
//! - `PlatformDetector`: Automatic platform detection at runtime
//! - Platform-specific implementations: Windows, macOS, Linux
//!
//! # Usage
//!
//! ```ignore
//! use guardian_plugins::collectors::platform::PlatformDetector;
//!
//! let collector = PlatformDetector::detect()?;
//! let system_info = collector.collect_system_info()?;
//! let processes = collector.collect_processes()?;
//! ```

use crate::connector::Event;
use crate::types::{ProcessInfo, SystemInfo};
use std::sync::Arc;
use thiserror::Error;

#[cfg(target_os = "linux")]
pub mod linux;
#[cfg(target_os = "macos")]
pub mod macos;
#[cfg(target_os = "windows")]
pub mod windows;

/// Security event collected from the platform
#[derive(Debug, Clone, serde::Serialize, serde::Deserialize, PartialEq)]
pub struct SecurityEvent {
    /// Event timestamp (ISO 8601)
    pub timestamp: String,
    /// Event type (e.g., "process_created", "file_accessed", "network_connection")
    pub event_type: String,
    /// Event severity: "low", "medium", "high", "critical"
    pub severity: String,
    /// Event description
    pub description: String,
    /// Associated process ID (if applicable)
    pub process_id: Option<u32>,
    /// Associated user name (if applicable)
    pub user: Option<String>,
    /// Additional event details
    pub details: std::collections::HashMap<String, String>,
}

/// Network information for the system
#[derive(Debug, Clone, serde::Serialize, serde::Deserialize, PartialEq)]
pub struct NetworkInfo {
    /// Number of network adapters
    pub adapter_count: u32,
    /// Primary IPv4 address
    pub ipv4_address: Option<String>,
    /// Primary IPv6 address
    pub ipv6_address: Option<String>,
    /// DNS servers
    pub dns_servers: Vec<String>,
    /// Active connections count
    pub active_connections: u32,
    /// Bytes sent
    pub bytes_sent: u64,
    /// Bytes received
    pub bytes_received: u64,
}

/// Platform detection error
#[derive(Error, Debug, Clone, PartialEq)]
pub enum PlatformError {
    #[error("Failed to collect system information: {0}")]
    SystemInfoError(String),

    #[error("Failed to collect process information: {0}")]
    ProcessError(String),

    #[error("Failed to collect security events: {0}")]
    SecurityEventError(String),

    #[error("Failed to collect network information: {0}")]
    NetworkError(String),

    #[error("Permission denied: {0}")]
    PermissionDenied(String),

    #[error("Platform not supported: {0}")]
    UnsupportedPlatform(String),

    #[error("Failed to initialize platform collector: {0}")]
    InitializationError(String),

    #[error("Administrative privileges required")]
    AdminPrivilegesRequired,
}

/// Unified trait for platform-specific data collection
///
/// Implementations exist for Windows, macOS, and Linux platforms.
pub trait PlatformCollector: Send + Sync {
    /// Collect system information (OS, CPU, RAM, disk, network)
    fn collect_system_info(&self) -> Result<SystemInfo, PlatformError>;

    /// Collect process information for all running processes
    fn collect_processes(&self) -> Result<Vec<ProcessInfo>, PlatformError>;

    /// Collect security events from the platform
    fn collect_security_events(&self) -> Result<Vec<SecurityEvent>, PlatformError>;

    /// Collect network information
    fn collect_network_info(&self) -> Result<NetworkInfo, PlatformError>;

    /// Check if running with administrative privileges
    fn is_admin(&self) -> Result<bool, PlatformError>;

    /// Get platform name (e.g., "windows", "macos", "linux")
    fn platform_name(&self) -> &'static str;

    /// Get platform version
    fn platform_version(&self) -> Result<String, PlatformError>;
}

/// Automatic platform detector - selects appropriate implementation at runtime
pub struct PlatformDetector;

impl PlatformDetector {
    /// Detect platform and return appropriate collector
    ///
    /// # Returns
    ///
    /// Returns an Arc-wrapped PlatformCollector implementation for the current OS.
    ///
    /// # Errors
    ///
    /// Returns `PlatformError::UnsupportedPlatform` if the current OS is not supported.
    ///
    /// # Examples
    ///
    /// ```ignore
    /// let collector = PlatformDetector::detect()?;
    /// let system_info = collector.collect_system_info()?;
    /// ```
    pub fn detect() -> Result<Arc<dyn PlatformCollector>, PlatformError> {
        #[cfg(target_os = "windows")]
        {
            windows::WindowsCollector::new().map(|c| Arc::new(c) as Arc<dyn PlatformCollector>)
        }

        #[cfg(target_os = "macos")]
        {
            macos::MacosCollector::new().map(|c| Arc::new(c) as Arc<dyn PlatformCollector>)
        }

        #[cfg(target_os = "linux")]
        {
            linux::LinuxCollector::new().map(|c| Arc::new(c) as Arc<dyn PlatformCollector>)
        }

        #[cfg(not(any(target_os = "windows", target_os = "macos", target_os = "linux")))]
        {
            Err(PlatformError::UnsupportedPlatform(
                "Only Windows, macOS, and Linux are supported".to_string(),
            ))
        }
    }

    /// Get current platform name
    pub fn platform_name() -> &'static str {
        #[cfg(target_os = "windows")]
        {
            "windows"
        }
        #[cfg(target_os = "macos")]
        {
            "macos"
        }
        #[cfg(target_os = "linux")]
        {
            "linux"
        }
        #[cfg(not(any(target_os = "windows", target_os = "macos", target_os = "linux")))]
        {
            "unsupported"
        }
    }
}

/// Convert security events to connector events for upload
///
/// # Examples
///
/// ```ignore
/// let security_events = collector.collect_security_events()?;
/// let connector_events = security_events_to_events(security_events, "device-123")?;
/// ```
pub fn security_events_to_events(
    events: Vec<SecurityEvent>,
    device_id: &str,
) -> Result<Vec<Event>, PlatformError> {
    use uuid::Uuid;

    events
        .into_iter()
        .map(|event| {
            let event_json = serde_json::to_value(&event)
                .map_err(|e| PlatformError::SecurityEventError(e.to_string()))?;

            let timestamp = chrono::DateTime::parse_from_rfc3339(&event.timestamp)
                .map(|dt| dt.with_timezone(&chrono::Utc))
                .unwrap_or_else(|_| chrono::Utc::now());

            Ok(Event {
                event_id: Uuid::new_v4().to_string(),
                event_type: event.event_type.clone(),
                timestamp,
                device_id: device_id.to_string(),
                data: event_json,
            })
        })
        .collect()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_platform_detector_detects_current_platform() {
        let detector = PlatformDetector::detect();
        assert!(detector.is_ok());

        let collector = detector.unwrap();
        assert!(!collector.platform_name().is_empty());
    }

    #[test]
    fn test_platform_detector_name() {
        let name = PlatformDetector::platform_name();
        assert!(
            name == "windows" || name == "macos" || name == "linux",
            "Platform name should be one of: windows, macos, linux"
        );
    }

    #[test]
    fn test_security_event_serialization() {
        let event = SecurityEvent {
            timestamp: "2025-01-15T10:00:00Z".to_string(),
            event_type: "process_created".to_string(),
            severity: "medium".to_string(),
            description: "Test event".to_string(),
            process_id: Some(1234),
            user: Some("test_user".to_string()),
            details: Default::default(),
        };

        let json = serde_json::to_string(&event).unwrap();
        let restored: SecurityEvent = serde_json::from_str(&json).unwrap();
        assert_eq!(event, restored);
    }

    #[test]
    fn test_network_info_serialization() {
        let network = NetworkInfo {
            adapter_count: 2,
            ipv4_address: Some("192.168.1.100".to_string()),
            ipv6_address: Some("::1".to_string()),
            dns_servers: vec!["8.8.8.8".to_string()],
            active_connections: 10,
            bytes_sent: 1_000_000,
            bytes_received: 5_000_000,
        };

        let json = serde_json::to_string(&network).unwrap();
        let restored: NetworkInfo = serde_json::from_str(&json).unwrap();
        assert_eq!(network, restored);
    }

    #[test]
    fn test_platform_error_display() {
        let error = PlatformError::SystemInfoError("Test error".to_string());
        assert!(error.to_string().contains("system information"));
    }
}
