//! Network monitoring and connection statistics
//!
//! Provides per-connection metrics including:
//! - TCP/UDP connection statistics
//! - Protocol detection and analysis
//! - DNS resolution tracking
//! - Port and service identification
//! - Connection state monitoring

pub mod linux;
pub mod macos;
pub mod windows;

use chrono::{DateTime, Utc};
use serde::{Deserialize, Serialize};
use thiserror::Error;

/// Network monitoring error types
#[derive(Error, Debug)]
pub enum NetworkError {
    #[error("No network adapters detected")]
    NoAdaptersDetected,

    #[error("Network detection error: {0}")]
    DetectionError(String),

    #[error("Connection query error: {0}")]
    QueryError(String),

    #[error("Socket operation error: {0}")]
    SocketError(String),

    #[error("DNS resolution error: {0}")]
    DnsError(String),

    #[error("Permission denied for network inspection")]
    PermissionDenied,

    #[error("Unsupported platform")]
    UnsupportedPlatform,

    #[error("Invalid connection state")]
    InvalidConnectionState,

    #[error("Protocol analysis error: {0}")]
    ProtocolError(String),
}

pub type NetworkResult<T> = Result<T, NetworkError>;

/// Network protocol types
#[derive(Debug, Clone, Copy, Serialize, Deserialize, PartialEq, Eq, Hash)]
pub enum Protocol {
    TCP,
    UDP,
    QUIC,
    ICMP,
    Other,
}

impl std::fmt::Display for Protocol {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            Protocol::TCP => write!(f, "TCP"),
            Protocol::UDP => write!(f, "UDP"),
            Protocol::QUIC => write!(f, "QUIC"),
            Protocol::ICMP => write!(f, "ICMP"),
            Protocol::Other => write!(f, "Other"),
        }
    }
}

/// Connection state
#[derive(Debug, Clone, Copy, Serialize, Deserialize, PartialEq, Eq, Hash, PartialOrd, Ord)]
pub enum ConnectionState {
    Established,
    SynSent,
    SynReceived,
    FinWait1,
    FinWait2,
    TimeWait,
    Closed,
    CloseWait,
    LastAck,
    Listen,
    Closing,
    Unknown,
}

/// Network connection information
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct NetworkConnection {
    /// Unique connection ID
    pub connection_id: String,
    /// Protocol (TCP, UDP, QUIC)
    pub protocol: Protocol,
    /// Local IP address
    pub local_ip: String,
    /// Local port
    pub local_port: u16,
    /// Remote IP address
    pub remote_ip: String,
    /// Remote port
    pub remote_port: u16,
    /// Connection state
    pub state: ConnectionState,
    /// Process ID associated with connection
    pub pid: Option<u32>,
    /// Hostname if resolved
    pub hostname: Option<String>,
}

/// Network statistics for a connection
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct NetworkMetrics {
    /// Connection ID
    pub connection_id: String,
    /// Bytes sent
    pub bytes_sent: u64,
    /// Bytes received
    pub bytes_received: u64,
    /// Packets sent
    pub packets_sent: u64,
    /// Packets received
    pub packets_received: u64,
    /// Connection duration in seconds
    pub duration_secs: u64,
    /// Latency in milliseconds (if available)
    pub latency_ms: Option<f32>,
    /// Packet loss percentage (if available)
    pub packet_loss_percent: Option<f32>,
    /// Measurement timestamp
    pub measured_at: DateTime<Utc>,
}

/// Network interface information
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct NetworkInterface {
    /// Interface name (eth0, en0, etc.)
    pub name: String,
    /// MAC address
    pub mac_address: String,
    /// IPv4 addresses
    pub ipv4_addresses: Vec<String>,
    /// IPv6 addresses
    pub ipv6_addresses: Vec<String>,
    /// Is interface up
    pub is_up: bool,
    /// Is loopback interface
    pub is_loopback: bool,
}

/// DNS resolution entry
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct DnsResolution {
    /// Hostname queried
    pub hostname: String,
    /// Resolved IP address
    pub ip_address: String,
    /// Resolution timestamp
    pub resolved_at: DateTime<Utc>,
    /// TTL in seconds
    pub ttl_secs: Option<u32>,
}

/// Network collector interface
pub trait NetworkCollector: Send + Sync {
    /// Get all network interfaces
    fn get_interfaces(&self) -> NetworkResult<Vec<NetworkInterface>>;

    /// Get active network connections
    fn get_connections(&self) -> NetworkResult<Vec<NetworkConnection>>;

    /// Get statistics for a specific connection
    fn get_connection_metrics(&self, connection_id: &str) -> NetworkResult<NetworkMetrics>;

    /// Resolve hostname to IP
    fn resolve_hostname(&self, hostname: &str) -> NetworkResult<DnsResolution>;

    /// Check if network monitoring is available
    fn is_available(&self) -> bool;

    /// Get active connection count
    fn connection_count(&self) -> u32;

    /// Identify service by port
    fn identify_service(&self, port: u16) -> Option<String>;
}

/// Detector for automatic platform selection
pub struct NetworkDetector;

impl NetworkDetector {
    /// Detect and return platform-specific collector
    pub fn detect() -> NetworkResult<std::sync::Arc<dyn NetworkCollector>> {
        #[cfg(target_os = "windows")]
        {
            Ok(std::sync::Arc::new(windows::WindowsNetworkCollector::new()))
        }

        #[cfg(target_os = "macos")]
        {
            Ok(std::sync::Arc::new(macos::MacosNetworkCollector::new()))
        }

        #[cfg(target_os = "linux")]
        {
            Ok(std::sync::Arc::new(linux::LinuxNetworkCollector::new()))
        }

        #[cfg(not(any(target_os = "windows", target_os = "macos", target_os = "linux")))]
        {
            Err(NetworkError::UnsupportedPlatform)
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_protocol_display() {
        assert_eq!(Protocol::TCP.to_string(), "TCP");
        assert_eq!(Protocol::UDP.to_string(), "UDP");
    }

    #[test]
    fn test_network_connection_creation() {
        let conn = NetworkConnection {
            connection_id: "conn-1".to_string(),
            protocol: Protocol::TCP,
            local_ip: "127.0.0.1".to_string(),
            local_port: 8080,
            remote_ip: "192.168.1.1".to_string(),
            remote_port: 443,
            state: ConnectionState::Established,
            pid: Some(1234),
            hostname: Some("example.com".to_string()),
        };

        assert_eq!(conn.protocol, Protocol::TCP);
        assert_eq!(conn.state, ConnectionState::Established);
    }

    #[test]
    fn test_network_metrics_creation() {
        let metrics = NetworkMetrics {
            connection_id: "conn-1".to_string(),
            bytes_sent: 1024,
            bytes_received: 2048,
            packets_sent: 10,
            packets_received: 15,
            duration_secs: 300,
            latency_ms: Some(25.5),
            packet_loss_percent: Some(0.5),
            measured_at: Utc::now(),
        };

        assert_eq!(metrics.bytes_sent, 1024);
        assert_eq!(metrics.bytes_received, 2048);
    }

    #[test]
    fn test_network_interface_creation() {
        let iface = NetworkInterface {
            name: "eth0".to_string(),
            mac_address: "00:11:22:33:44:55".to_string(),
            ipv4_addresses: vec!["192.168.1.1".to_string()],
            ipv6_addresses: vec![],
            is_up: true,
            is_loopback: false,
        };

        assert_eq!(iface.name, "eth0");
        assert!(iface.is_up);
        assert!(!iface.is_loopback);
    }

    #[test]
    fn test_dns_resolution_creation() {
        let dns = DnsResolution {
            hostname: "example.com".to_string(),
            ip_address: "93.184.216.34".to_string(),
            resolved_at: Utc::now(),
            ttl_secs: Some(3600),
        };

        assert_eq!(dns.hostname, "example.com");
        assert_eq!(dns.ip_address, "93.184.216.34");
    }

    #[test]
    fn test_connection_state_variants() {
        let states = vec![
            ConnectionState::Established,
            ConnectionState::Listen,
            ConnectionState::Closed,
            ConnectionState::TimeWait,
        ];

        assert_eq!(states.len(), 4);
    }

    #[test]
    fn test_protocol_variants() {
        let protocols = vec![Protocol::TCP, Protocol::UDP, Protocol::QUIC, Protocol::ICMP];

        assert_eq!(protocols.len(), 4);
    }
}
