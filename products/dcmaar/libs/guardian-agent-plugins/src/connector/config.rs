//! Connector configuration
//!
//! Handles configuration loading from TOML files for the Guardian API connector

use serde::{Deserialize, Serialize};
use std::path::{Path, PathBuf};
use tracing::{debug, info};

use super::{ConnectorError, Result};

/// Connector configuration
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ConnectorConfig {
    /// Backend API URL
    pub backend_url: String,

    /// Device ID for this agent
    pub device_id: String,

    /// Device certificate path (PEM format)
    pub cert_path: PathBuf,

    /// Device private key path (PEM format)
    pub key_path: PathBuf,

    /// CA certificate for server verification (PEM format)
    pub ca_cert_path: PathBuf,

    /// JWT authentication token
    pub jwt_token: Option<String>,

    /// Number of retry attempts for failed requests
    pub retry_attempts: u32,

    /// Batch size for event uploads
    pub batch_size: usize,

    /// Event upload interval in seconds
    pub upload_interval_secs: u64,

    /// Policy sync interval in seconds
    pub policy_sync_interval_secs: u64,

    /// Request timeout in seconds
    pub request_timeout_secs: u64,

    /// Enable certificate pinning
    pub enable_cert_pinning: bool,

    /// Certificate fingerprint for pinning (SHA256)
    pub cert_fingerprint: Option<String>,

    /// Whether to verify server certificate
    pub verify_ssl: bool,
}

impl ConnectorConfig {
    /// Load configuration from TOML file
    pub fn from_file<P: AsRef<Path>>(path: P) -> Result<Self> {
        info!("Loading connector config from: {:?}", path.as_ref());

        let content = std::fs::read_to_string(path).map_err(|e| {
            ConnectorError::ConfigError(format!("Failed to read config file: {}", e))
        })?;

        Self::from_str(&content)
    }

    /// Parse configuration from TOML string
    pub fn from_str(content: &str) -> Result<Self> {
        let config: ConnectorConfig = toml::from_str(content)
            .map_err(|e| ConnectorError::ConfigError(format!("Failed to parse TOML: {}", e)))?;

        config.validate()?;
        debug!("Connector config loaded successfully");
        Ok(config)
    }

    /// Validate configuration
    fn validate(&self) -> Result<()> {
        if self.backend_url.is_empty() {
            return Err(ConnectorError::ConfigError(
                "backend_url is required".to_string(),
            ));
        }

        if !self.backend_url.starts_with("https://") && !self.backend_url.starts_with("http://") {
            return Err(ConnectorError::ConfigError(
                "backend_url must be http:// or https://".to_string(),
            ));
        }

        if self.device_id.is_empty() {
            return Err(ConnectorError::ConfigError(
                "device_id is required".to_string(),
            ));
        }

        if self.batch_size == 0 {
            return Err(ConnectorError::ConfigError(
                "batch_size must be > 0".to_string(),
            ));
        }

        if self.retry_attempts == 0 {
            return Err(ConnectorError::ConfigError(
                "retry_attempts must be > 0".to_string(),
            ));
        }

        Ok(())
    }

    /// Generate example TOML configuration
    pub fn example_toml() -> &'static str {
        r#"
[connector]
backend_url = "https://api.guardian.example.com"
device_id = "device-123"
cert_path = "/etc/guardian/device.pem"
key_path = "/etc/guardian/device.key"
ca_cert_path = "/etc/guardian/ca.pem"
jwt_token = "eyJ0eXAiOiJKV1QiLCJhbGc..."
retry_attempts = 3
batch_size = 100
upload_interval_secs = 300
policy_sync_interval_secs = 3600
request_timeout_secs = 30
enable_cert_pinning = true
cert_fingerprint = "sha256/..."
verify_ssl = true
"#
    }

    /// Save configuration to file
    pub fn save<P: AsRef<Path>>(&self, path: P) -> Result<()> {
        info!("Saving connector config to: {:?}", path.as_ref());

        let content = toml::to_string_pretty(self).map_err(|e| {
            ConnectorError::ConfigError(format!("Failed to serialize config: {}", e))
        })?;

        std::fs::write(path, content).map_err(|e| {
            ConnectorError::ConfigError(format!("Failed to write config file: {}", e))
        })?;

        Ok(())
    }
}

impl Default for ConnectorConfig {
    fn default() -> Self {
        Self {
            backend_url: "https://api.guardian.example.com".to_string(),
            device_id: "device-test".to_string(),
            cert_path: PathBuf::from("/etc/guardian/device.pem"),
            key_path: PathBuf::from("/etc/guardian/device.key"),
            ca_cert_path: PathBuf::from("/etc/guardian/ca.pem"),
            jwt_token: None,
            retry_attempts: 3,
            batch_size: 100,
            upload_interval_secs: 300,
            policy_sync_interval_secs: 3600,
            request_timeout_secs: 30,
            enable_cert_pinning: true,
            cert_fingerprint: None,
            verify_ssl: true,
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_default_config() {
        let config = ConnectorConfig::default();
        assert_eq!(config.device_id, "device-test");
        assert_eq!(config.batch_size, 100);
        assert_eq!(config.retry_attempts, 3);
    }

    #[test]
    fn test_config_serialization() {
        let config = ConnectorConfig::default();
        let toml = toml::to_string(&config).unwrap();
        assert!(toml.contains("backend_url"));
        assert!(toml.contains("device_id"));
    }

    #[test]
    fn test_config_deserialization() {
        let toml_str = r#"
backend_url = "https://api.example.com"
device_id = "device-123"
cert_path = "/etc/guardian/device.pem"
key_path = "/etc/guardian/device.key"
ca_cert_path = "/etc/guardian/ca.pem"
retry_attempts = 3
batch_size = 100
upload_interval_secs = 300
policy_sync_interval_secs = 3600
request_timeout_secs = 30
enable_cert_pinning = true
verify_ssl = true
"#;

        let config = ConnectorConfig::from_str(toml_str).unwrap();
        assert_eq!(config.device_id, "device-123");
        assert_eq!(config.batch_size, 100);
    }

    #[test]
    fn test_config_validation_empty_url() {
        let mut config = ConnectorConfig::default();
        config.backend_url = String::new();
        assert!(config.validate().is_err());
    }

    #[test]
    fn test_config_validation_invalid_url() {
        let mut config = ConnectorConfig::default();
        config.backend_url = "ftp://example.com".to_string();
        assert!(config.validate().is_err());
    }

    #[test]
    fn test_config_validation_zero_batch_size() {
        let mut config = ConnectorConfig::default();
        config.batch_size = 0;
        assert!(config.validate().is_err());
    }
}
