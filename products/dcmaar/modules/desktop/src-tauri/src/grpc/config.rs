// gRPC client configuration
// Follows WSRF-ARCH-003 (zero-trust transport) and WSRF-API-002 (constraints)

use serde::{Deserialize, Serialize};
use std::time::Duration;

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct GrpcConfig {
    /// Agent endpoint URL
    pub endpoint: String,
    
    /// Connection timeout in milliseconds (default: 5000ms)
    #[serde(default = "default_connect_timeout")]
    pub connect_timeout_ms: u64,
    
    /// Request timeout in milliseconds (default: 10000ms, max per WSRF-API-002)
    #[serde(default = "default_request_timeout")]
    pub request_timeout_ms: u64,
    
    /// Enable TLS
    #[serde(default = "default_true")]
    pub tls_enabled: bool,
    
    /// TLS CA certificate path
    pub tls_ca_path: Option<String>,
    
    /// TLS client certificate path (for mTLS)
    pub tls_cert_path: Option<String>,
    
    /// TLS client key path (for mTLS)
    pub tls_key_path: Option<String>,
    
    /// Maximum message size in bytes (default: 4MB per WSRF-API-002)
    #[serde(default = "default_max_message_size")]
    pub max_message_size: usize,
    
    /// Maximum retry attempts
    #[serde(default = "default_max_retries")]
    pub max_retries: u32,
    
    /// Initial retry backoff in milliseconds
    #[serde(default = "default_initial_backoff")]
    pub initial_backoff_ms: u64,
    
    /// Maximum retry backoff in milliseconds
    #[serde(default = "default_max_backoff")]
    pub max_backoff_ms: u64,
    
    /// Keep-alive interval in seconds
    #[serde(default = "default_keepalive_interval")]
    pub keepalive_interval_secs: u64,
    
    /// Keep-alive timeout in seconds
    #[serde(default = "default_keepalive_timeout")]
    pub keepalive_timeout_secs: u64,
}

impl Default for GrpcConfig {
    fn default() -> Self {
        Self {
            endpoint: "http://localhost:50051".to_string(),
            connect_timeout_ms: default_connect_timeout(),
            request_timeout_ms: default_request_timeout(),
            tls_enabled: true,
            tls_ca_path: None,
            tls_cert_path: None,
            tls_key_path: None,
            max_message_size: default_max_message_size(),
            max_retries: default_max_retries(),
            initial_backoff_ms: default_initial_backoff(),
            max_backoff_ms: default_max_backoff(),
            keepalive_interval_secs: default_keepalive_interval(),
            keepalive_timeout_secs: default_keepalive_timeout(),
        }
    }
}

impl GrpcConfig {
    pub fn connect_timeout(&self) -> Duration {
        Duration::from_millis(self.connect_timeout_ms)
    }

    pub fn request_timeout(&self) -> Duration {
        Duration::from_millis(self.request_timeout_ms)
    }

    pub fn initial_backoff(&self) -> Duration {
        Duration::from_millis(self.initial_backoff_ms)
    }

    pub fn max_backoff(&self) -> Duration {
        Duration::from_millis(self.max_backoff_ms)
    }

    pub fn keepalive_interval(&self) -> Duration {
        Duration::from_secs(self.keepalive_interval_secs)
    }

    pub fn keepalive_timeout(&self) -> Duration {
        Duration::from_secs(self.keepalive_timeout_secs)
    }

    /// Validate configuration
    pub fn validate(&self) -> Result<(), String> {
        if self.endpoint.is_empty() {
            return Err("endpoint cannot be empty".to_string());
        }

        if self.request_timeout_ms > 10_000 {
            return Err("request_timeout_ms cannot exceed 10000ms (WSRF-API-002)".to_string());
        }

        if self.max_message_size > 4 * 1024 * 1024 {
            return Err("max_message_size cannot exceed 4MB (WSRF-API-002)".to_string());
        }

        if self.tls_enabled {
            if self.tls_cert_path.is_some() && self.tls_key_path.is_none() {
                return Err("tls_key_path required when tls_cert_path is set".to_string());
            }
            if self.tls_key_path.is_some() && self.tls_cert_path.is_none() {
                return Err("tls_cert_path required when tls_key_path is set".to_string());
            }
        }

        Ok(())
    }
}

fn default_connect_timeout() -> u64 {
    5000
}

fn default_request_timeout() -> u64 {
    10000 // Max per WSRF-API-002
}

fn default_true() -> bool {
    true
}

fn default_max_message_size() -> usize {
    4 * 1024 * 1024 // 4MB per WSRF-API-002
}

fn default_max_retries() -> u32 {
    3
}

fn default_initial_backoff() -> u64 {
    100
}

fn default_max_backoff() -> u64 {
    5000
}

fn default_keepalive_interval() -> u64 {
    60
}

fn default_keepalive_timeout() -> u64 {
    20
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_default_config() {
        let config = GrpcConfig::default();
        assert_eq!(config.request_timeout_ms, 10000);
        assert_eq!(config.max_message_size, 4 * 1024 * 1024);
        assert!(config.validate().is_ok());
    }

    #[test]
    fn test_invalid_timeout() {
        let mut config = GrpcConfig::default();
        config.request_timeout_ms = 15000;
        assert!(config.validate().is_err());
    }

    #[test]
    fn test_invalid_message_size() {
        let mut config = GrpcConfig::default();
        config.max_message_size = 5 * 1024 * 1024;
        assert!(config.validate().is_err());
    }
}
